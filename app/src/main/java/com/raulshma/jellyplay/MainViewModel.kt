package com.raulshma.jellyplay

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import com.raulshma.jellyplay.core.data.network.NetworkMonitor
import com.raulshma.jellyplay.core.data.playback.AudioPlaybackManager
import com.raulshma.jellyplay.core.data.remote.RemoteControlReceiver
import com.raulshma.jellyplay.core.data.remote.RemoteNavigationBridge
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.shortcuts.AppShortcutManager
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalSlice
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import com.raulshma.jellyplay.core.datastore.security.PinRateLimiter
import com.raulshma.jellyplay.core.datastore.security.SecurityStore
import com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections
import com.raulshma.jellyplay.core.model.ExperimentalFeature
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.MainPreferences
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.feedback.UserMessageBus
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.deeplink.DeepLinkHandler
import com.raulshma.jellyplay.core.data.playback.VideoMiniPlayerState
import com.raulshma.jellyplay.core.data.update.AppUpdateRepository
import com.raulshma.jellyplay.core.data.update.PendingAppUpdate
import com.raulshma.jellyplay.update.UpdateState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import java.util.concurrent.atomic.AtomicBoolean

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository,
    private val projections: PreferenceProjections,
    private val experimentalStore: com.raulshma.jellyplay.core.datastore.experimental.ExperimentalStore,
    val homeDiscoveryStore: com.raulshma.jellyplay.core.datastore.home.HomeDiscoveryStore,
    val appRuntimeStateStore: com.raulshma.jellyplay.core.datastore.runtime.AppRuntimeStateStore,
    val serverIdentityStore: ServerIdentityStore,
    val pinRateLimiter: PinRateLimiter,
    val securityStore: SecurityStore,
    val networkMonitor: NetworkMonitor,
    val syncPlayManager: com.raulshma.jellyplay.core.data.syncplay.SyncPlayManager,
    val webSocketClient: com.raulshma.jellyplay.core.network.websocket.JellyfinWebSocketClient,
    private val apiClient: com.raulshma.jellyplay.core.network.JellyfinApiClient,
    val audioPlaybackManager: AudioPlaybackManager,
    val videoMiniPlayerState: VideoMiniPlayerState,
    val appShortcutManager: AppShortcutManager,
    val remoteControlReceiver: RemoteControlReceiver,
    val remoteNavigationBridge: RemoteNavigationBridge,
    private val deepLinkHandler: DeepLinkHandler,
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
    private val playbackSourceResolver: com.raulshma.jellyplay.core.data.playback.PlaybackSourceResolver,
    private val offlineModeManager: com.raulshma.jellyplay.core.data.offline.OfflineModeManager,
    val userMessageBus: UserMessageBus,
    private val serverHealthMonitor: com.raulshma.jellyplay.core.data.network.ServerHealthMonitor,
    val cacheManager: com.raulshma.jellyplay.core.data.cache.CacheManager,
    private val widgetWorkScheduler: com.raulshma.jellyplay.widget.WidgetWorkScheduler,
    private val cacheMaintenanceInitializer: com.raulshma.jellyplay.startup.CacheMaintenanceInitializer,
    private val appUpdateRepository: AppUpdateRepository,
) : JellyPlayViewModel() {

    val serverHealth = serverHealthMonitor.serverHealth

    /**
     * App-wide offline mode. Collected by [com.raulshma.jellyplay.navigation.JellyPlayApp]
     * so the floating nav can hide server-bound destinations (Library, Live TV)
     * that have no offline fallback and would otherwise land on a dead-end
     * [com.raulshma.jellyplay.core.ui.components.ErrorScreen].
     */
    val offlineMode = offlineModeManager.offlineMode

    private val _isRestoring = stateFlow(true)
    val isRestoring = _isRestoring.flow

    val isAuthenticated = authRepository.isAuthenticated
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), false)

    val isAdmin = authRepository.currentUser
        .map { it?.isAdmin == true }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), false)

    /**
     * True while a server admin-status refresh is in flight. Collected by the
     * [com.raulshma.jellyplay.feature.admin.navigation.AdminRouteContainer]
     * guard so it can show a brief loading state instead of flashing the
     * access-denied screen before the first refresh completes.
     */
    private val _isRefreshingAdmin = stateFlow(false)
    val isRefreshingAdmin = _isRefreshingAdmin.flow

    /**
     * Wall-clock millis of the last successful [refreshAdminStatus]. Prevents
     * every admin screen from re-fetching the policy on rapid back/forward
     * navigation within the admin area. Read/written only on the Main thread
     * (all callers run via [launch] on the viewModelScope's Main dispatcher),
     * so a plain non-volatile field is safe here.
     */
    private var lastAdminRefreshAt = 0L
    private val adminRefreshIntervalMs = 30_000L

    /**
     * After the user dismisses an update prompt, the launch-time auto-check
     * suppresses the *same* version for this long. Manual checks (Settings)
     * are unaffected. 24 hours.
     */
    private val dismissedUpdateSuppressMs = 24L * 60 * 60 * 1000

    /**
     * Preferences read by the app-shell composables (MainActivity +
     * JellyPlayApp). Built off [PreferenceProjections.mainPreferences] (which
     * covers all slice-owned fields) with the two runtime-only fields —
     * `pinLockoutUntilEpochMs` (from [PinRateLimiter]) and `onboardingCompleted`
     * (from [appRuntimeStateStore]) — merged in via a typed `combine`, since
     * neither lives in a preference slice.
     */
    val preferences = combine(
        projections.mainPreferences,
        pinRateLimiter.pinLockoutUntilEpochMs,
        appRuntimeStateStore.state,
    ) { prefs, pinLockout, runtime ->
        prefs.copy(
            pinLockoutUntilEpochMs = pinLockout,
            onboardingCompleted = runtime.onboardingCompleted,
        )
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), MainPreferences())

    private val _libraryFolders = stateFlow<List<LibraryFolder>>(emptyList())
    val libraryFolders = _libraryFolders.flow

    private val _pendingRoute = stateFlow<Route?>(null)
    val pendingRoute = _pendingRoute.flow

    /**
     * One-shot signal fired by the "Surprise Me" launcher shortcut. The Home
     * hero controller has no VM entry point, so the Home screen
     * observes this and flips its local `showSurprise` state on launch.
     */
    private val _surpriseOnLaunch = stateFlow(false)
    val surpriseOnLaunch = _surpriseOnLaunch.flow

    /**
     * `true` only on a fresh ViewModel construction — i.e. a restore *after
     * state loss*, not a config-change recreate (which reuses the same
     * ViewModel via `onRetainNonConfigurationInstance`). Covers every case
     * where the player's in-memory state is gone but the saveable Navigation 3
     * back stack round-trips: OS-killed process, "Don't keep activities", and
     * low-memory activity eviction. Consumed exactly once (first caller wins);
     * `MainContent` captures the result in `remember { }` so it holds for the
     * lifetime of this Activity's composition, while a config-change recreate
     * (same VM) re-reads `false` and keeps any player the user re-opened.
     * See [com.raulshma.jellyplay.core.ui.navigation.rememberNavigationState].
     */
    private val _isStateLossRestore = AtomicBoolean(true)
    fun consumeStateLossRestore(): Boolean = _isStateLossRestore.getAndSet(false)

    private val _pendingSearchQuery = stateFlow<String?>(null)
    val pendingSearchQuery = _pendingSearchQuery.flow

    private val _globalMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val globalMessage = _globalMessage.asSharedFlow()

    /**
     * In-app self-update state. Observed by the update sheet so the same
     * activity-scoped instance drives both the launch-time auto-check and any
     * manual check. Stays [UpdateState.Idle] (sheet hidden) until an update is
     * actually found or the user explicitly opens the flow.
     */
    private val _updateState = stateFlow<UpdateState>(UpdateState.Idle)
    val updateState = _updateState.flow

    /**
     * User's "download updates automatically" preference, mirrored from
     * [ExperimentalStore] so the update sheet can render + toggle it while a
     * flow is active without subscribing to the whole experimental slice.
     */
    private val _selfUpdateDownloadEnabled = stateFlow(false)
    val selfUpdateDownloadEnabled = _selfUpdateDownloadEnabled.flow

    init {
        launch {
            coroutineScope {
                val authDeferred = async { authRepository.restoreSession() }
                val prefsDeferred = async { experimentalStore.experimental.first() }
                val result = authDeferred.await()
                prefsDeferred.await()
                if (result.isSuccess) {
                    val server = authRepository.currentServer.first()
                    val user = authRepository.currentUser.first()
                    if (server != null && user != null) {
                        isAuthenticated.first { it }
                    }
                }
            }
            _isRestoring.set(false)
            // Best-effort app-update check once the UI is up. First restore any
            // update APK already downloaded but not yet installed (kept on disk
            // across restarts); only if there's nothing pending do we hit the
            // network. Gated by the "check for updates automatically" toggle so
            // that off-switch stays the single way to silence update UI on
            // launch — the file itself is still retained for a manual check.
            val experimental = experimentalStore.experimental.first()
            if (experimental.selfUpdateCheckEnabled) {
                val pending = runCatching { appUpdateRepository.getPendingUpdate() }.getOrNull()
                if (pending != null &&
                    !isUpdateRecentlyDismissed(pending.info.latestVersion, experimental)
                ) {
                    _updateState.set(UpdateState.Downloaded(pending.info, pending.apkFile))
                } else {
                    checkForAppUpdate()
                }
            }
        }

        launch {
            isAuthenticated.collect { isAuth ->
                if (isAuth) {
                    val server = authRepository.currentServer.first()
                    val user = authRepository.currentUser.first()
                    if (server != null && user != null) {
                        serverHealthMonitor.startMonitoring(server.address)
                        val deviceId = serverIdentityStore.ensureDeviceId()
                        val deviceName = buildDeviceName(user.name)
                        webSocketClient.connect(
                            serverAddress = server.address,
                            accessToken = user.accessToken,
                            device = deviceId,
                            deviceName = deviceName,
                            client = "JellyPlay",
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
                        com.raulshma.jellyplay.widget.ContinueWatchingWidget.triggerUpdate(context)
                        // Best-effort cache maintenance — runs once after the
                        // first successful auth instead of a fragile startup
                        // delay.
                        cacheMaintenanceInitializer.cleanupOnce()
                        // Fetch library folders for the TV navigation drawer
                        launch { refreshLibraryFolders() }
                    }
                } else {
                    serverHealthMonitor.stopMonitoring()
                    webSocketClient.disconnect()
                    remoteControlReceiver.stop()
                    _libraryFolders.set(emptyList())
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
            var lastConnected = webSocketClient.isConnected.value
            webSocketClient.isConnected.collect { connected ->
                if (connected && !lastConnected && isAuthenticated.value) {
                    launch {
                        runCatching { apiClient.postCapabilities() }
                    }
                }
                lastConnected = connected
            }
        }

        launch {
            remoteControlReceiver.displayMessages.collect { msg ->
                val text = if (msg.header.isNotBlank()) "${msg.header}\n${msg.text}" else msg.text
                if (text.isNotBlank()) {
                    _globalMessage.tryEmit(text)
                }
            }
        }

        appShortcutManager.observePlaybackForDynamicShortcuts()

        launch {
            experimentalStore.experimental.collect { prefs ->
                _selfUpdateDownloadEnabled.set(prefs.selfUpdateDownloadEnabled)
            }
        }
    }

    fun refreshLibraryFolders() {
        launch {
            mediaRepository.getLibraryFolders()
                .onSuccess { _libraryFolders.set(it) }
        }
    }

    /**
     * Checks GitHub Releases for a newer build, called once on launch after
     * session restore. Gated by `selfUpdateCheckEnabled`. Stays silent unless
     * an update is actually available — it never surfaces a sheet for an
     * up-to-date result. When the user's opted into auto-download, an available
     * update begins streaming immediately instead of prompting. Use
     * [manualCheckForUpdate] when the user wants feedback regardless of outcome.
     */
    fun checkForAppUpdate() {
        launch {
            val experimental = experimentalStore.experimental.first()
            if (!experimental.selfUpdateCheckEnabled) return@launch
            val result = appUpdateRepository.checkForUpdate(
                supportedAbis = android.os.Build.SUPPORTED_ABIS,
            )
            result.onSuccess { info ->
                if (!info.isUpdateAvailable) return@onSuccess // stay Idle.
                // Honor a prior dismissal: if the user dismissed this exact
                // version less than 24h ago, stay quiet on the launch auto-check.
                if (isUpdateRecentlyDismissed(info.latestVersion, experimental)) return@onSuccess
                // With auto-download enabled, skip the prompt and stream the APK
                // straight away (the sheet surfaces download progress/cancel).
                if (experimental.selfUpdateDownloadEnabled && info.downloadAssetUrl != null) {
                    startUpdateDownload(info)
                } else {
                    _updateState.set(UpdateState.UpdateAvailable(info))
                }
            }
        }
    }

    /**
     * True when [version] matches the last dismissed update and that dismissal
     * happened within [dismissedUpdateSuppressMs]. Manual checks ignore this.
     */
    private fun isUpdateRecentlyDismissed(
        version: String,
        experimental: ExperimentalSlice,
    ): Boolean {
        val dismissedVersion = experimental.dismissedUpdateVersion ?: return false
        if (dismissedVersion != version) return false
        val elapsed = System.currentTimeMillis() - experimental.dismissedUpdateAtMs
        return elapsed in 0..dismissedUpdateSuppressMs
    }

    /**
     * Manual, user-initiated check (from Settings). Always surfaces the result:
     * a sheet for an available update, or a "you're up to date" sheet (with a
     * link to view the current version's release notes) when none is. Bypasses
     * the auto-check preference, but still honors auto-download (an available
     * update begins streaming immediately when enabled).
     */
    fun manualCheckForUpdate() {
        launch {
            _updateState.set(UpdateState.Checking)
            val experimental = experimentalStore.experimental.first()
            val pending = runCatching { appUpdateRepository.getPendingUpdate() }.getOrNull()
            val result = appUpdateRepository.checkForUpdate(
                supportedAbis = android.os.Build.SUPPORTED_ABIS,
            )
            // Manual checks ignore the 24h dismissal — the user explicitly asked.
            // Always hit the network so a release published *after* the on-disk
            // APK was downloaded can still surface: when both are present, prefer
            // the newer version (ties keep the pending APK so its already-downloaded
            // bytes stay the install path). On network failure fall back to pending.
            val remote = result.getOrNull()
            val surface = pickUpdateToSurface(pending?.info, remote)
            when {
                // A newer version is available to download.
                surface != null && surface.isUpdateAvailable -> {
                    // If the chosen version is already on disk, show install-ready;
                    // otherwise prompt (or auto-download) as usual.
                    if (pending != null && surface.latestVersion == pending.info.latestVersion) {
                        _updateState.set(UpdateState.Downloaded(pending.info, pending.apkFile))
                    } else if (experimental.selfUpdateDownloadEnabled && surface.downloadAssetUrl != null) {
                        startUpdateDownload(surface)
                    } else {
                        _updateState.set(UpdateState.UpdateAvailable(surface))
                    }
                }
                // Up to date — show the result (with release notes).
                surface != null -> _updateState.set(UpdateState.NoUpdate(surface))
                // Network failed but a pending APK exists — fall back to it.
                pending != null ->
                    _updateState.set(UpdateState.Downloaded(pending.info, pending.apkFile))
                // Network failed, nothing pending.
                else -> _updateState.set(UpdateState.Error(result.exceptionOrNull()?.message ?: "Update check failed"))
            }
        }
    }

    /**
     * Picks the [AppUpdateInfo] to surface for a manual check: the pending
     * (on-disk) version, the freshly-fetched remote version, or null when the
     * remote failed *and* nothing is pending. When both exist, prefers the
     * newer version — ties keep the pending one so the already-downloaded APK
     * stays the install path instead of forcing a re-download.
     */
    private fun pickUpdateToSurface(
        pending: com.raulshma.jellyplay.core.model.AppUpdateInfo?,
        remote: com.raulshma.jellyplay.core.model.AppUpdateInfo?,
    ): com.raulshma.jellyplay.core.model.AppUpdateInfo? {
        if (remote == null) return pending
        if (pending == null) return remote
        return if (com.raulshma.jellyplay.core.network.github.GitHubReleasesApiImpl
                .compareVersions(remote.latestVersion, pending.latestVersion) > 0
        ) remote else pending
    }

    /**
     * Persists the "download updates automatically" preference. Also exposed
     * from the update sheet so the toggle takes effect from either place.
     */
    fun setSelfUpdateDownloadEnabled(enabled: Boolean) {
        _selfUpdateDownloadEnabled.set(enabled)
        launch { experimentalStore.setSelfUpdateDownloadEnabled(enabled) }
    }

    /**
     * Begins streaming the APK for the given update, reporting progress. Wipes
     * any previously-downloaded APK + sidecar first, so this is also the path
     * used by [redownloadUpdate] to overwrite an existing file.
     */
    fun startUpdateDownload(info: com.raulshma.jellyplay.core.model.AppUpdateInfo) {
        if (info.downloadAssetUrl == null) return
        launch {
            _updateState.set(UpdateState.Downloading(info, 0f, 0L, info.releaseSize))
            val result = appUpdateRepository.downloadApk(info) { fraction, read, total ->
                _updateState.set(UpdateState.Downloading(info, fraction, read, total))
            }
            result
                .onSuccess { file -> _updateState.set(UpdateState.Downloaded(info, file)) }
                .onFailure { _updateState.set(UpdateState.Error(it.message ?: "Download failed")) }
        }
    }

    /**
     * Re-downloads the update whose APK is already on disk (and shown as
     * [UpdateState.Downloaded]). Falls back to the current state's info; the
     * repository overwrites the existing file + sidecar via the normal
     * download path.
     */
    fun redownloadUpdate() {
        val state = _updateState.value
        val info = (state as? UpdateState.Downloaded)?.info
            ?: (state as? UpdateState.UpdateAvailable)?.info
            ?: return
        startUpdateDownload(info)
    }

    /** Builds and emits the system package-installer intent for the APK. */
    fun buildInstallIntent(file: java.io.File): android.content.Intent =
        appUpdateRepository.buildInstallIntent(file)

    /**
     * Hides the update sheet without changing download state. When dismissed
     * from an [UpdateState.UpdateAvailable] prompt or an install-ready
     * [UpdateState.Downloaded] sheet, stamps the version + time so the
     * launch-time auto-check / restore stays quiet for the same version for
     * 24h. The downloaded APK is retained on disk either way. Manual checks
     * still surface the result regardless of dismissal.
     */
    fun dismissUpdate() {
        val state = _updateState.value
        val dismissedVersion = when (state) {
            is UpdateState.UpdateAvailable -> state.info.latestVersion
            is UpdateState.Downloaded -> state.info.latestVersion
            else -> null
        }
        if (dismissedVersion != null) {
            launch {
                experimentalStore.setDismissedUpdate(dismissedVersion)
            }
        }
        _updateState.set(UpdateState.Idle)
    }

    /**
     * Re-validates the current user's admin status against the server. Called
     * by [com.raulshma.jellyplay.feature.admin.navigation.AdminRouteContainer]
     * on entering any admin screen, but de-duplicated to at most once per
     * [adminRefreshIntervalMs] so navigation between admin screens doesn't
     * hammer the server. Failures other than access-denied are swallowed
     * (the cached value is kept) — see [AuthRepository.refreshCurrentUser].
     */
    fun refreshAdminStatus() {
        // Early-out synchronously (before launch) to guard against the window
        // where two admin entries compose simultaneously during a transition
        // and both fire LaunchedEffect. The in-flight flag serializes genuine
        // concurrent entries; the timestamp bounds re-fetches to one per window.
        if (_isRefreshingAdmin.value) return
        val now = System.currentTimeMillis()
        if (now - lastAdminRefreshAt < adminRefreshIntervalMs) return
        launch {
            _isRefreshingAdmin.set(true)
            try {
                authRepository.refreshCurrentUser()
                lastAdminRefreshAt = System.currentTimeMillis()
            } finally {
                _isRefreshingAdmin.set(false)
            }
        }
    }

    fun handleShortcutIntent(intent: Intent) {
        val route = when (intent.action) {
            AppShortcutManager.ACTION_CONTINUE_WATCHING ->
                Route.NewsletterSectionList("CONTINUE_WATCHING")
            AppShortcutManager.ACTION_SEARCH -> Route.Search
            AppShortcutManager.ACTION_PLAY_MUSIC -> Route.MusicBrowse
            AppShortcutManager.ACTION_DOWNLOADS -> Route.Downloads
            AppShortcutManager.ACTION_PLAY_AUDIO -> {
                val itemId = intent.getStringExtra(AppShortcutManager.EXTRA_ITEM_ID)
                if (!itemId.isNullOrBlank()) Route.AudioPlayer(itemId) else null
            }
            // Static launcher shortcuts
            AppShortcutManager.ACTION_SETTINGS -> Route.Settings
            AppShortcutManager.ACTION_SURPRISE_ME -> {
                // Route home and arm the surprise signal the Home screen consumes.
                _surpriseOnLaunch.set(true)
                Route.Home
            }
            else -> null
        }
        if (route != null) {
            _pendingRoute.set(route)
        }
    }

    /** Clears the surprise-on-launch signal after the Home screen consumes it. */
    fun consumeSurpriseOnLaunch() {
        _surpriseOnLaunch.set(false)
    }

    fun handleDeepLink(intent: Intent) {
        val route = deepLinkHandler.parse(intent) ?: return
        _pendingRoute.set(route)
    }

    fun handleSharedText(sharedText: String) {
        launch {
            when (val target = parseSharedText(sharedText)) {
                SharedTextTarget.Empty -> {
                    _globalMessage.emit("No searchable content found in shared text")
                }
                is SharedTextTarget.Search -> {
                    _pendingSearchQuery.set(target.query)
                    _pendingRoute.set(Route.Search)
                }
                is SharedTextTarget.MediaDetail -> {
                    _pendingRoute.set(Route.MediaDetail(target.mediaId))
                }
            }
        }
    }

    private fun parseSharedText(text: String): SharedTextTarget {
        val jellyfinUrlMatch = Regex("""jellyfin://media/([a-f0-9-]+)""").find(text)
        if (jellyfinUrlMatch != null) {
            return SharedTextTarget.MediaDetail(jellyfinUrlMatch.groupValues[1])
        }
        val urlMatch = Regex("""https?://[^\s]+""").find(text)
        if (urlMatch != null) {
            return SharedTextTarget.Search(urlMatch.value)
        }
        return text.takeIf { it.isNotBlank() }
            ?.let(SharedTextTarget::Search)
            ?: SharedTextTarget.Empty
    }

    private sealed class SharedTextTarget {
        data object Empty : SharedTextTarget()
        data class Search(val query: String) : SharedTextTarget()
        data class MediaDetail(val mediaId: String) : SharedTextTarget()
    }

    fun consumePendingRoute() {
        _pendingRoute.set(null)
    }

    fun consumePendingSearchQuery() {
        _pendingSearchQuery.set(null)
    }

    fun handleSearchQuery(query: String) {
        _pendingSearchQuery.set(query)
    }

    fun logout() {
        launch {
            remoteControlReceiver.stop()
            authRepository.logout()
        }
    }

    fun revokeServerSession() {
        launch {
            remoteControlReceiver.stop()
            authRepository.revokeServerSession()
        }
    }

    private fun buildDeviceName(userName: String): String {
        val model = Build.MODEL.orEmpty().ifBlank { "Android" }
        val name = userName.take(20)
        val full = if (name.isNotBlank()) "JellyPlay on $model ($name)" else "JellyPlay on $model"
        return if (full.length > 60) full.take(60) else full
    }

    /**
     * Builds an [ExternalPlayerLaunch] for the given item, resolving either a
     * completed local download or the server stream URL. Works for both regular
     * videos ([Route.VideoPlayer]) and Live TV channels
     * ([Route.LiveTvChannelPlayer]) since [mediaRepository.getMediaDetail] /
     * [playbackRepository.getStreamUrl] handle channel ids identically to the
     * internal-engine path.
     *
     * The returned intent advertises `return_result`, so the app-level
     * `ActivityResultLauncher` in [com.raulshma.jellyplay.navigation.JellyPlayApp]
     * can read the external player's final position and credit watched progress
     * via [reportExternalPlaybackStopped].
     */
    suspend fun buildExternalPlayerLaunch(
        itemId: String,
        mediaSourceId: String?,
        startPositionTicks: Long,
    ): ExternalPlayerLaunch? {
        // The download-vs-stream fork lives once in PlaybackSourceResolver: a
        // completed download with an existing file resolves to a `file://` URI
        // (title from the offline item, falling back to the download name),
        // else the resolver fetches `getMediaDetail` and builds the stream URL.
        // The resolver silently falls back to streaming when a COMPLETED row's
        // file vanished — the historical MainViewModel disk-staleness behaviour.
        val resolved = playbackSourceResolver.resolvePlaybackSource(
            itemId = itemId,
            mediaSourceId = mediaSourceId,
            startPositionTicks = startPositionTicks,
        ) ?: return null

        val url = when (resolved) {
            is com.raulshma.jellyplay.core.data.playback.ResolvedPlaybackSource.Local -> resolved.uri
            is com.raulshma.jellyplay.core.data.playback.ResolvedPlaybackSource.Stream -> resolved.url
        }
        val title = resolved.title

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(url), "video/*")
            putExtra("title", title)
            putExtra("return_result", true)
            val startMs = startPositionTicks / 10_000
            if (startMs > 0) putExtra("position", startMs)
        }

        return ExternalPlayerLaunch(
            intent = intent,
            itemId = itemId,
            startPositionTicks = startPositionTicks,
            playSessionId = java.util.UUID.randomUUID().toString(),
        )
    }

    fun reportExternalPlaybackStart(playerLaunch: ExternalPlayerLaunch) {
        launch {
            runCatching {
                playbackRepository.reportPlaybackStart(
                    com.raulshma.jellyplay.core.model.PlaybackStartInfo(
                        itemId = playerLaunch.itemId,
                        sessionId = playerLaunch.playSessionId,
                        startPositionTicks = playerLaunch.startPositionTicks,
                    )
                )
            }
        }
    }

    fun reportExternalPlaybackStopped(playerLaunch: ExternalPlayerLaunch, finalPositionTicks: Long) {
        val positionTicks = if (finalPositionTicks > 0) finalPositionTicks else playerLaunch.startPositionTicks
        launch {
            runCatching {
                kotlinx.coroutines.withTimeout(5_000) {
                    playbackRepository.reportPlaybackStopped(
                        itemId = playerLaunch.itemId,
                        sessionId = playerLaunch.playSessionId,
                        positionTicks = positionTicks,
                    )
                }
            }
        }
    }
}

data class ExternalPlayerLaunch(
    val intent: Intent,
    val itemId: String,
    val startPositionTicks: Long,
    val playSessionId: String,
)
