package com.raulshma.jellyplay.desktop

import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import com.raulshma.jellyplay.core.data.remote.RemoteControlReceiver
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.repository.RealtimeConnection
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import com.raulshma.jellyplay.core.model.ConnectionCredentials
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The desktop twin of the Android shell's `SessionCoordinator`
 * (the desktop receiver port): connects the realtime socket for the
 * authenticated session, posts session capabilities on every (re)connect,
 * and starts/stops the remote-control receiver with the auth state.
 *
 * Until this existed the desktop never opened the session WebSocket and
 * never posted capabilities — the device never appeared in other clients'
 * "Play On" lists and the remote-control receiver (jvmShared)
 * had no socket to listen to.
 *
 * Trimmed vs the Android original (declared): no server-health polling
 * (desktop has no monitor), no widget/cache/library-folder fan-out (Android
 * WorkManager/widget surfaces), no session-restore pass (DesktopAppRoot
 * already restores the session before the authenticated shell composes).
 * Logout teardown rides the same isAuthenticated false edge.
 */
class DesktopSessionCoordinator(
    private val authRepository: AuthRepository,
    private val realtimeConnection: RealtimeConnection,
    private val serverIdentityStore: ServerIdentityStore,
    private val remoteControlReceiver: RemoteControlReceiver,
) {

    private var lifecycleJob: Job? = null
    private var capabilitiesJob: Job? = null

    /**
     * Begins the session lifecycle on [scope]. Safe to call again — the
     * previous lifecycle jobs are cancelled first, so collectors are never
     * duplicated (the composition-scope effect in DesktopAppRoot restarts
     * on recomposition keys).
     */
    fun start(scope: CoroutineScope) {
        lifecycleJob?.cancel()
        capabilitiesJob?.cancel()
        lifecycleJob = scope.launch {
            authRepository.isAuthenticated.collect { isAuth ->
                if (isAuth) {
                    val server = authRepository.currentServer.first()
                    val user = authRepository.currentUser.first()
                    if (server != null && user != null) {
                        val deviceId = serverIdentityStore.ensureDeviceId()
                        val deviceName = ConnectionCredentials.deviceNameFor(user.name)
                        // Connect to the *active* endpoint (primary or the
                        // selected alternate after failover), mirroring the
                        // Android coordinator's doomed-first-handshake avoid.
                        realtimeConnection.connect(
                            ConnectionCredentials(
                                serverAddress = realtimeConnection.serverUrl() ?: server.address,
                                accessToken = user.accessToken,
                                deviceId = deviceId,
                                deviceName = deviceName,
                                clientName = "JellyPlay Desktop",
                            )
                        )
                        remoteControlReceiver.start()
                    }
                } else {
                    realtimeConnection.disconnect()
                    remoteControlReceiver.stop()
                }
            }
        }
        // Post capabilities on every WebSocket (re)connect. The server drops
        // the session's WebSocketController (and with it
        // SupportsRemoteControl) when the socket closes — after a drop the
        // device disappears from other clients' "Play On" lists until
        // capabilities are re-armed. The immediate post covers the first
        // connect AND an already-up socket; every subsequent drop+reopen
        // rides [RealtimeConnection.reconnects]. Gated on the authenticated
        // mirror so a stray connect during teardown never fires a stale POST
        // (same reasoning as the Android coordinator).
        capabilitiesJob = scope.launch {
            if (!realtimeConnection.isConnected.value) {
                realtimeConnection.isConnected.first { it }
            }
            postCapabilitiesIfAuthenticated()
            realtimeConnection.reconnects.collect {
                postCapabilitiesIfAuthenticated()
            }
        }
    }

    /** Stops the lifecycle collectors and the receiver (composition teardown). */
    fun stop() {
        lifecycleJob?.cancel()
        lifecycleJob = null
        capabilitiesJob?.cancel()
        capabilitiesJob = null
        remoteControlReceiver.stop()
    }

    private fun CoroutineScope.postCapabilitiesIfAuthenticated() {
        launch {
            if (authRepository.isAuthenticated.first()) {
                runCatchingRethrowingCancellation { authRepository.postCapabilities() }
            }
        }
    }
}
