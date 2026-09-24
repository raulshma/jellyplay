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
import com.raulshma.jellyplay.feature.shell.SessionRestore
import com.raulshma.jellyplay.startup.CacheMaintenanceInitializer
import com.raulshma.jellyplay.widget.ContinueWatchingWidget
import com.raulshma.jellyplay.widget.WidgetWorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Owns the app-shell session lifecycle behind a small seam: the Android-only
 * authenticated-state fan-out (server health, widgets, cache maintenance,
 * library folders — hung off the shared controller's `onConnected` hook) and
 * full teardown on logout / session revocation. The realtime core itself —
 * WebSocket connect on the auth edge, remote-control receiver start/stop,
 * endpoint selection, capabilities re-posting on every socket reconnect — is
 * the shared [RealtimeSessionController], built through its `create` factory
 * per lifecycle with clientName "JellyPlay"; session restore (restore call →
 * authenticated-mirror wait → bounded timeout → splash release) is the
 * shared [SessionRestore], with this class exposing its flows verbatim.
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
    private val _libraryFolders = MutableStateFlow<List<LibraryFolder>>(emptyList())
    val libraryFolders: StateFlow<List<LibraryFolder>> = _libraryFolders.asStateFlow()

    val serverHealth: StateFlow<ServerHealth> = serverHealthMonitor.serverHealth

    /**
     * The shared realtime core (socket connect, receiver, capabilities
     * re-arm), rebuilt per [start]: [RealtimeSessionController.stop] on the
     * previous occupant first, so a lifecycle restart (activity-state loss
     * rebuilt the ViewModel) never duplicates its collectors — the same
     * cancel-then-replace discipline the [RestartableJob] lifecycle slot and
     * [SessionRestore]'s mirror collector each apply to their own state.
     */
    private var realtimeSession: RealtimeSessionController? = null

    /**
     * The shared session-restore choreography (shared/feature/shell): owns
     * the restore call → authenticated-mirror wait → bounded timeout →
     * splash-release sequence and the mirror collector. The flows this class
     * exposes below are ITS state — one instance for this coordinator's
     * lifetime, so the state the activity and the shell render from is
     * stable across [start] rebuilds exactly as the former hand-rolled
     * fields were. The Android-only false-edge teardown rides the shared
     * mirror collector through `onAuthChange`.
     */
    private val sessionRestore by lazy {
        SessionRestore(
            authChanges = authRepository.isAuthenticated,
            restoreSession = authRepository::restoreSession,
            currentServer = authRepository.currentServer,
            currentUser = authRepository.currentUser,
            // Warms the preferences DataStore during the restore window, as
            // the former inline choreography did.
            warmup = { experimentalStore.experimental.first() },
            onAuthChange = { isAuth ->
                if (!isAuth) {
                    serverHealthMonitor.stopMonitoring()
                    _libraryFolders.value = emptyList()
                }
            },
        )
    }

    /** The splash gate (MainActivity's system splash keep-on-screen read). */
    val isRestoring: StateFlow<Boolean> get() = sessionRestore.isRestoring

    /** The authenticated flag the shell renders from — the shared mirror. */
    val isAuthenticated: StateFlow<Boolean> get() = sessionRestore.isAuthenticated

    /**
     * Begins the session lifecycle on [scope]. [onSessionRestored] fires once
     * restore completes (success or not) so dependent launch-time work — e.g.
     * the self-update check — can run after the UI is up without this module
     * knowing about updates. Safe to call again (e.g. after activity-state
     * loss rebuilt the ViewModel): [RestartableJob] cancels the previous
     * lifecycle job first, so collectors are never duplicated.
     */
    fun start(scope: CoroutineScope, onSessionRestored: () -> Unit = {}) {
        realtimeSession?.stop()
        realtimeSession = RealtimeSessionController.create(
            scope = scope,
            isAuthenticated = authRepository.isAuthenticated,
            currentServer = authRepository.currentServer,
            currentUser = authRepository.currentUser,
            clientName = "JellyPlay",
            realtimeConnection = realtimeConnection,
            remoteControlReceiver = remoteControlReceiver,
            serverIdentityStore = serverIdentityStore,
            onReconnect = { authRepository.postCapabilities() },
            onConnected = { server, user ->
                // The Android-only authenticated fan-out, delivered through
                // the shared controller: fires on the controller's auth-true
                // edge, after the connect + receiver arm, with the same
                // resolved (server, user) pair the credentials used — the
                // exact arm this class used to duplicate by re-reading the
                // flows in its own auth collector. Auth-edge work only:
                // socket reconnects re-post capabilities but never re-fire
                // this.
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
        // The splash gate must be up before any suspension so the splash can
        // never flash; restore()'s internal re-raise stays an idempotent safety.
        sessionRestore.markRestoring()
        lifecycleJob.launchIn(scope) {
            launch {
                sessionRestore.restore(scope)
                onSessionRestored()
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
}
