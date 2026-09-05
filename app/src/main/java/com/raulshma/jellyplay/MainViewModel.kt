package com.raulshma.jellyplay

import android.content.Intent
import android.net.Uri
import com.raulshma.jellyplay.core.data.remote.RemoteControlReceiver
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.shortcuts.AppShortcutManager
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoveryStore
import com.raulshma.jellyplay.core.datastore.runtime.AppRuntimeStateStore
import com.raulshma.jellyplay.core.datastore.security.PinRateLimiter
import com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections
import com.raulshma.jellyplay.core.model.MainPreferences
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.feedback.UserMessageBus
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.deeplink.DeepLinkHandler
import com.raulshma.jellyplay.feature.shell.AdminRefreshGate
import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.playback.PlaybackSourceResolver
import com.raulshma.jellyplay.shell.SessionCoordinator
import com.raulshma.jellyplay.shell.SyncPlayOpenCoordinator
import com.raulshma.jellyplay.shell.UpdateCoordinator
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicBoolean

/**
 * App-shell ViewModel. Owns the shell-level signals the composables render —
 * pending routes, deep links, shortcuts, search, surprise-me, offline toggle,
 * preferences, the external-player reporting contract, one-shot messages — and
 * starts the cross-cutting shell coordinators on its scope.
 *
 * The coordinators themselves are the only service surface: session lifecycle
 * state ([SessionCoordinator.isRestoring] / [isAuthenticated] /
 * [libraryFolders] / [serverHealth]), logout, update checks, and SyncPlay opens
 * are all reached through [sessionCoordinator] / [updateCoordinator] /
 * [syncPlayOpenCoordinator] — never re-exported here. Stores and managers this
 * class merely wires (network monitor, message bus, audio playback, remote
 * navigation) are injected where they are consumed instead of exposed from
 * here.
 */
class MainViewModel(
    private val authRepository: AuthRepository,
    private val projections: PreferenceProjections,
    private val homeDiscoveryStore: HomeDiscoveryStore,
    private val appRuntimeStateStore: AppRuntimeStateStore,
    private val pinRateLimiter: PinRateLimiter,
    private val remoteControlReceiver: RemoteControlReceiver,
    private val appShortcutManager: AppShortcutManager,
    private val deepLinkHandler: DeepLinkHandler,
    private val playbackRepository: PlaybackRepository,
    private val downloadRepository: DownloadRepository,
    private val playbackSourceResolver: PlaybackSourceResolver,
    private val offlineModeManager: OfflineModeManager,
    private val userMessageBus: UserMessageBus,
    val sessionCoordinator: SessionCoordinator,
    val updateCoordinator: UpdateCoordinator,
    val syncPlayOpenCoordinator: SyncPlayOpenCoordinator,
) : JellyPlayViewModel() {

    /**
     * App-wide offline mode. Collected by [com.raulshma.jellyplay.navigation.JellyPlayApp]
     * so the floating nav can hide server-bound destinations (Library, Live TV)
     * that have no offline fallback and would otherwise land on a dead-end
     * [com.raulshma.jellyplay.core.ui.components.ErrorScreen].
     */
    val offlineMode = offlineModeManager.offlineMode

    /**
     * Count of downloads actively in flight (PENDING/QUEUED/DOWNLOADING/PAUSED).
     * Surfaced to the app-shell nav so the ⋮ "More" toggle and the "Downloads"
     * overflow item can badge themselves while transfers are running.
     */
    val activeDownloadCount = downloadRepository.getActiveDownloadCount()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), 0)

    /**
     * Mirrors Home's offline→online in-flight flag at the app shell so the
     * global nav overflow can show a spinner on "Go Online" (see #115). Cleared
     * by observing [offlineMode] settling back to ONLINE below.
     */
    private val _isGoingOnline = stateFlow(false)
    val isGoingOnline = _isGoingOnline.flow

    init {
        // Clear the going-online busy flag once we're actually back online.
        scope.launch {
            offlineMode.collect { if (it == com.raulshma.jellyplay.core.model.OfflineMode.ONLINE) _isGoingOnline.set(false) }
        }

        // Shell coordinators: session restore completes → run the launch-time
        // update check; the session, update, and SyncPlay-open collectors each
        // live inside their coordinator.
        sessionCoordinator.start(scope) {
            updateCoordinator.onSessionRestored()
        }
        updateCoordinator.start(scope)
        syncPlayOpenCoordinator.start(scope)

        // Server-pushed display messages surface as one-shot user messages.
        launch {
            remoteControlReceiver.displayMessages.collect { msg ->
                val text = if (msg.header.isNotBlank()) "${msg.header}\n${msg.text}" else msg.text
                if (text.isNotBlank()) {
                    userMessageBus.info(text)
                }
            }
        }

        appShortcutManager.observePlaybackForDynamicShortcuts()
    }

    /**
     * App-shell offline toggle for the global nav overflow (#115). Going online
     * is async (preference write → mode flip → network fetch); flip the busy
     * flag so the UI can show a spinner, mirroring [HomeViewModel]'s logic.
     */
    fun toggleOfflineMode() {
        val goingOnline = offlineMode.value != com.raulshma.jellyplay.core.model.OfflineMode.ONLINE
        if (goingOnline) _isGoingOnline.set(true)
        offlineModeManager.toggleManualOffline()
    }

    /** Persists the Home mode (Video / Music) switch from the app-shell nav. */
    fun setHomeMode(mode: com.raulshma.jellyplay.core.model.HomeMode) {
        scope.launch { homeDiscoveryStore.setHomeMode(mode) }
    }

    /** Marks onboarding completed (TV skips the phone onboarding flow). */
    fun markOnboardingCompleted() {
        scope.launch { appRuntimeStateStore.setOnboardingCompleted(true) }
    }

    /**
     * One-shot signal fired by the global nav overflow's "Surprise Me" entry
     * (#115). Home collects this and forwards it to its hero controller, since
     * the controller lives in Home's composition (it owns the hero LazyListState
     * + featured candidates) and can't be hoisted to the app shell.
     */
    private val _surpriseRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val surpriseRequests = _surpriseRequests.asSharedFlow()

    /** Fire the Surprise-Me signal (see [surpriseRequests]). */
    fun requestSurprise() {
        _surpriseRequests.tryEmit(Unit)
    }

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
     * The admin refresh dedupe policy (the shared [AdminRefreshGate]: 30 s
     * window + in-flight guard). The in-flight state itself stays here —
     * [MainViewModel.isRefreshingAdmin] is the flow the
     * [com.raulshma.jellyplay.feature.admin.navigation.AdminRouteContainer]
     * guard renders from.
     */
    private val adminRefreshGate = AdminRefreshGate(
        isRefreshInFlight = { _isRefreshingAdmin.value },
        nowMs = { System.currentTimeMillis() },
    )

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

    /**
     * Re-validates the current user's admin status against the server. Called
     * by [com.raulshma.jellyplay.feature.admin.navigation.AdminRouteContainer]
     * on entering any admin screen, but de-duplicated by the shared
     * [AdminRefreshGate] (at most once per 30 s window) so navigation between
     * admin screens doesn't hammer the server. Failures other than
     * access-denied are swallowed (the cached value is kept) — see
     * [AuthRepository.refreshCurrentUser].
     */
    fun refreshAdminStatus() {
        // Early-out synchronously (before launch) to guard against the window
        // where two admin entries compose simultaneously during a transition
        // and both fire LaunchedEffect. The in-flight flag serializes genuine
        // concurrent entries; the gate's timestamp bounds re-fetches to one
        // per window.
        if (!adminRefreshGate.shouldStart()) return
        launch {
            _isRefreshingAdmin.set(true)
            try {
                val result = authRepository.refreshCurrentUser()
                if (result.isSuccess) adminRefreshGate.onRefreshCompleted()
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
                    userMessageBus.info("No searchable content found in shared text")
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
        val jellyfinUrlMatch = JELLYFIN_MEDIA_URL_REGEX.find(text)
        if (jellyfinUrlMatch != null) {
            return SharedTextTarget.MediaDetail(jellyfinUrlMatch.groupValues[1])
        }
        val urlMatch = ANY_URL_REGEX.find(text)
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

    /**
     * Builds an [ExternalPlayerLaunch] for the given item, resolving either a
     * completed local download or the server stream URL. Works for both regular
     * videos ([Route.VideoPlayer]) and Live TV channels
     * ([Route.LiveTvChannelPlayer]) since the underlying repository calls
     * handle channel ids identically to the internal-engine path.
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
                withTimeout(5_000) {
                    playbackRepository.reportPlaybackStopped(
                        itemId = playerLaunch.itemId,
                        sessionId = playerLaunch.playSessionId,
                        positionTicks = positionTicks,
                    )
                }
            }
        }
    }

    private companion object {
        val JELLYFIN_MEDIA_URL_REGEX = Regex("""jellyfin://media/([a-f0-9-]+)""")
        val ANY_URL_REGEX = Regex("""https?://[^\s]+""")
    }
}

data class ExternalPlayerLaunch(
    val intent: Intent,
    val itemId: String,
    val startPositionTicks: Long,
    val playSessionId: String,
)
