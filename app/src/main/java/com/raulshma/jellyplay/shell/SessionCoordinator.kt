package com.raulshma.jellyplay.shell

import android.content.Context
import com.raulshma.jellyplay.core.data.network.ServerHealthMonitor
import com.raulshma.jellyplay.core.data.remote.RemoteControlReceiver
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.RealtimeConnection
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalStore
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import com.raulshma.jellyplay.core.model.ConnectionCredentials
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.ServerHealth
import com.raulshma.jellyplay.startup.CacheMaintenanceInitializer
import com.raulshma.jellyplay.widget.ContinueWatchingWidget
import com.raulshma.jellyplay.widget.WidgetWorkScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Owns the app-shell session lifecycle behind a small seam: session restore,
 * the authenticated-state fan-out (server health, WebSocket, remote control,
 * widgets, cache maintenance, library folders), capabilities re-posting on
 * every socket reconnect, and full teardown on logout / session revocation.
 *
 * Interface is one state triple plus start/stop commands —
 * [isRestoring], [isAuthenticated], [libraryFolders] and [serverHealth] are
 * the flows the shell renders; [start] begins the lifecycle on the caller's
 * scope (the activity-scoped ViewModel's), [logout] /
 * [revokeServerSession] end it. Everything else (endpoint selection,
 * capability ordering) stays private to this module.
 */
@Singleton
class SessionCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository,
    private val realtimeConnection: RealtimeConnection,
    private val experimentalStore: ExperimentalStore,
    private val serverIdentityStore: ServerIdentityStore,
    private val serverHealthMonitor: ServerHealthMonitor,
    private val remoteControlReceiver: RemoteControlReceiver,
    private val widgetWorkScheduler: WidgetWorkScheduler,
    private val cacheMaintenanceInitializer: CacheMaintenanceInitializer,
    private val mediaRepository: MediaRepository,
) : ShellCoordinator() {
    private val _isRestoring = MutableStateFlow(true)
    val isRestoring: StateFlow<Boolean> = _isRestoring.asStateFlow()

    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    private val _libraryFolders = MutableStateFlow<List<LibraryFolder>>(emptyList())
    val libraryFolders: StateFlow<List<LibraryFolder>> = _libraryFolders.asStateFlow()

    val serverHealth: StateFlow<ServerHealth> = serverHealthMonitor.serverHealth

    /**
     * Begins the session lifecycle on [scope]. [onSessionRestored] fires once
     * restore completes (success or not) so dependent launch-time work — e.g.
     * the self-update check — can run after the UI is up without this module
     * knowing about updates. Safe to call again (e.g. after activity-state
     * loss rebuilt the ViewModel): [RestartableJob] cancels the previous
     * lifecycle job first, so collectors are never duplicated.
     */
    fun start(scope: CoroutineScope, onSessionRestored: () -> Unit = {}) {
        _isRestoring.value = true
        lifecycleJob.launchIn(scope) {
            launch {
                restoreSession()
                onSessionRestored()
            }
            launch {
                authRepository.isAuthenticated.collect { isAuth ->
                    _isAuthenticated.value = isAuth
                    if (isAuth) {
                        val server = authRepository.currentServer.first()
                        val user = authRepository.currentUser.first()
                        if (server != null && user != null) {
                            serverHealthMonitor.startMonitoring(server.address)
                            val deviceId = serverIdentityStore.ensureDeviceId()
                            val deviceName = ConnectionCredentials.deviceNameFor(user.name)
                            // Connect to the *active* endpoint — after address
                            // selection this is the reachable address (primary
                            // when available, else an alternate), avoiding a
                            // doomed first handshake against a dead primary.
                            realtimeConnection.connect(
                                ConnectionCredentials(
                                    serverAddress = realtimeConnection.serverUrl() ?: server.address,
                                    accessToken = user.accessToken,
                                    deviceId = deviceId,
                                    deviceName = deviceName,
                                    clientName = "JellyPlay",
                                )
                            )
                            // Capabilities must be posted *after* the server has a
                            // session for this device. The Jellyfin server computes
                            // a session's SupportsRemoteControl as:
                            // Capabilities?.SupportsMediaControl == true
                            // && an attached SessionController (the WebSocket)
                            // also reports SupportsMediaControl.
                            // POST /Sessions/Capabilities/Full resolves the session
                            // by deviceId and throws if none exists yet — which is
                            // the case if it races ahead of the WebSocket handshake.
                            // That exception was swallowed here, leaving Capabilities
                            // null and the device absent from every other client's
                            // "Play On" / cast list (incl. other JellyPlay clients).
                            // Drive it off the WebSocket's connected state instead,
                            // so it lands once the server session truly exists and
                            // is re-sent on every reconnect (the controller is gone
                            // after a socket drop and must be re-armed). The actual
                            // (re)posting is done by the connection collector below,
                            // which fires on every false→true transition for the
                            // life of the auth session.
                            remoteControlReceiver.start()
                            launch {
                                widgetWorkScheduler.refreshLibraryNow()
                            }
                            launch {
                                widgetWorkScheduler.refreshSeerrNow()
                            }
                            // Force every placed widget to re-read its cached data
                            // from the store. The Continue Watching widget has no
                            // worker of its own (data is pushed by HomeViewModel),
                            // so this is what makes it pick up freshly restored
                            // state on cold start; for Library/Seerr it surfaces
                            // any cached items while the worker run completes.
                            ContinueWatchingWidget.triggerUpdate(context)
                            // Best-effort cache maintenance — runs once after the
                            // first successful auth instead of a fragile startup
                            // delay.
                            cacheMaintenanceInitializer.cleanupOnce()
                            // Fetch library folders for the TV navigation drawer
                            refreshLibraryFolders()
                        }
                    } else {
                        serverHealthMonitor.stopMonitoring()
                        realtimeConnection.disconnect()
                        remoteControlReceiver.stop()
                        _libraryFolders.value = emptyList()
                    }
                }
            }
            launch {
                // Re-post capabilities on every WebSocket (re)connect. The server
                // drops the session's WebSocketController (and thus
                // SupportsRemoteControl) when the socket closes, so after a drop
                // the device disappears from other clients' "Play On" lists until
                // capabilities are re-armed. Gated on isAuthenticated so a stray
                // connect during teardown doesn't fire a stale POST.
                var lastConnected = realtimeConnection.isConnected.value
                realtimeConnection.isConnected.collect { connected ->
                    if (connected && !lastConnected && _isAuthenticated.value) {
                        launch {
                            runCatching { authRepository.postCapabilities() }
                        }
                    }
                    lastConnected = connected
                }
            }
        }
    }

    /**
     * Ends the session: stop remote control, then run the given sign-out.
     * Runs on [commandScope] so it still executes if called after the
     * lifecycle scope has been cancelled.
     */
    private fun endSession(signOut: suspend () -> Unit) {
        commandScope.launch {
            remoteControlReceiver.stop()
            signOut()
        }
    }

    /** Ends the session: stop remote control, then sign out. */
    fun logout() = endSession { authRepository.logout() }

    /** Ends the session and revokes the server session token. */
    fun revokeServerSession() = endSession { authRepository.revokeServerSession() }

    /** Re-fetches library folders for the TV navigation drawer. */
    fun refreshLibraryFolders() {
        commandScope.launch {
            mediaRepository.getLibraryFolders()
                .onSuccess { _libraryFolders.value = it }
        }
    }

    private suspend fun restoreSession() {
        coroutineScope {
            val authDeferred = async { authRepository.restoreSession() }
            val prefsDeferred = async { experimentalStore.experimental.first() }
            val result = authDeferred.await()
            prefsDeferred.await()
            if (result.isSuccess) {
                val server = authRepository.currentServer.first()
                val user = authRepository.currentUser.first()
                if (server != null && user != null) {
                    // Restore succeeded with a persisted server + user, so the
                    // authenticated flag should already be true. Cap the wait
                    // anyway: if it never flips (a corrupted combine/stateIn
                    // edge), the splash gate must still release and
                    // onSessionRestored must still fire instead of hanging
                    // forever.
                    withTimeoutOrNull(AUTH_CONFIRMATION_TIMEOUT_MS) {
                        authRepository.isAuthenticated.first { it }
                    }
                }
            }
        }
        _isRestoring.value = false
    }

    private companion object {
        const val AUTH_CONFIRMATION_TIMEOUT_MS = 10_000L
    }
}
