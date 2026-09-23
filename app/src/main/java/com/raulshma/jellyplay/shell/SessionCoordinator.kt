package com.raulshma.jellyplay.shell

import android.content.Context
import com.raulshma.jellyplay.core.data.network.ServerHealthMonitor
import com.raulshma.jellyplay.core.data.remote.RemoteControlReceiver
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.RealtimeConnection
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalStore
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.ServerHealth
import com.raulshma.jellyplay.feature.shell.RealtimeSessionController
import com.raulshma.jellyplay.startup.CacheMaintenanceInitializer
import com.raulshma.jellyplay.widget.ContinueWatchingWidget
import com.raulshma.jellyplay.widget.WidgetWorkScheduler
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
 * the Android-only authenticated-state fan-out (server health, widgets,
 * cache maintenance, library folders — hung off the shared controller's
 * `onConnected` hook), and full teardown on logout / session revocation. The
 * realtime core itself — WebSocket connect on the auth edge, remote-control
 * receiver start/stop, endpoint selection, capabilities re-posting on every
 * socket reconnect — is the shared [RealtimeSessionController]
 * (shared/feature/shell), constructed per lifecycle with clientName
 * "JellyPlay".
 *
 * Interface is one state triple plus start/stop commands —
 * [isRestoring], [isAuthenticated], [libraryFolders] and [serverHealth] are
 * the flows the shell renders; [start] begins the lifecycle on the caller's
 * scope (the activity-scoped ViewModel's), [logout] /
 * [revokeServerSession] end it. Everything else stays private to this module.
 */
class SessionCoordinator(
    private val context: Context,
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
     * The shared realtime core (socket connect, receiver, capabilities
     * re-arm), rebuilt per [start]: [RealtimeSessionController.stop] on the
     * previous occupant first, so a lifecycle restart (activity-state loss
     * rebuilt the ViewModel) never duplicates its collectors — the same
     * cancel-then-replace discipline [RestartableJob] applies to the
     * collectors below.
     */
    private var realtimeSession: RealtimeSessionController? = null

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
        realtimeSession?.stop()
        realtimeSession = RealtimeSessionController(
            scope = scope,
            isAuthenticated = authRepository.isAuthenticated,
            currentServer = authRepository.currentServer,
            currentUser = authRepository.currentUser,
            clientName = "JellyPlay",
            serverUrl = realtimeConnection::serverUrl,
            isConnected = realtimeConnection.isConnected,
            reconnects = realtimeConnection.reconnects,
            connect = realtimeConnection::connect,
            disconnect = realtimeConnection::disconnect,
            startReceiver = remoteControlReceiver::start,
            stopReceiver = remoteControlReceiver::stop,
            ensureDeviceId = serverIdentityStore::ensureDeviceId,
            onReconnect = { authRepository.postCapabilities() },
            onConnected = { server, user ->
                // The Android-only authenticated fan-out, delivered through
                // the shared controller: fires on the controller's auth-true
                // edge, after the connect + receiver arm, with the same
                // resolved (server, user) pair the credentials used — the
                // exact arm this class used to duplicate by re-reading the
                // flows in the collector below. Auth-edge work only: socket
                // reconnects re-post capabilities but never re-fire this.
                serverHealthMonitor.startMonitoring(server.address)
                scope.launch {
                    widgetWorkScheduler.refreshLibraryNow()
                }
                scope.launch {
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
            },
        )
        lifecycleJob.launchIn(scope) {
            launch {
                restoreSession()
                onSessionRestored()
            }
            launch {
                // Mirror + teardown only: everything authenticated-state
                // positive rides the controller's onConnected above.
                authRepository.isAuthenticated.collect { isAuth ->
                    _isAuthenticated.value = isAuth
                    if (!isAuth) {
                        serverHealthMonitor.stopMonitoring()
                        _libraryFolders.value = emptyList()
                    }
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
                    // authenticated flag should already be true. Wait on the
                    // COORDINATOR'S OWN mirror — the flag the shell renders
                    // from — not the repository flow: resuming off the mirror
                    // write itself guarantees the release below cannot land
                    // while the shell still composes the signed-out auth host
                    // (SignedOutAuthHost). (Measured
                    // on device: the repository flow's flip resumed this
                    // coroutine up to ~10ms ahead of the mirror collector —
                    // a (isRestoring=false, isAuthenticated=false) frame that
                    // flashed the server list over Home.) The timeout caps
                    // the wait so a corrupted flow can't hang the splash
                    // gate and onSessionRestored forever.
                    withTimeoutOrNull(AUTH_CONFIRMATION_TIMEOUT_MS) {
                        _isAuthenticated.first { it }
                    }
                }
            }
        }
        _isRestoring.value = false
    }

    private companion object {
        // The mirror flip trails the restore by ~10 ms, so this exists purely
        // to bound the corrupted-flow case — not to outlast a slow cold start.
        // A pathological stall past 2.5 s trades one possible frame of auth
        // flash for un-blocking the splash instead of pinning it for seconds.
        const val AUTH_CONFIRMATION_TIMEOUT_MS = 2_500L
    }
}
