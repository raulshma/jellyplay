package com.raulshma.jellyplay.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.awt.ComposeWindow
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Bolt
import com.composables.icons.tabler.outline.Calendar
import com.composables.icons.tabler.outline.DeviceTv
import com.composables.icons.tabler.outline.Disc
import com.composables.icons.tabler.outline.Download
import com.composables.icons.tabler.outline.Flame
import com.composables.icons.tabler.outline.Home
import com.composables.icons.tabler.outline.Library
import com.composables.icons.tabler.outline.Mail
import com.composables.icons.tabler.outline.Movie
import com.composables.icons.tabler.outline.Search
import com.composables.icons.tabler.outline.Settings
import com.composables.icons.tabler.outline.Shield
import com.composables.icons.tabler.outline.Stack
import com.composables.icons.tabler.outline.Users
import com.raulshma.jellyplay.core.data.network.NetworkMonitor
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.update.AppUpdateRepository
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoveryStore
import com.raulshma.jellyplay.core.datastore.navigation.NavigationStore
import com.raulshma.jellyplay.core.datastore.runtime.AppRuntimeStateStore
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.ServerHealth
import com.raulshma.jellyplay.core.ui.components.LocalNetworkStatus
import com.raulshma.jellyplay.core.ui.components.LocalPullToRefreshRegistry
import com.raulshma.jellyplay.core.ui.components.LocalServerHealth
import com.raulshma.jellyplay.core.ui.components.LocalSurpriseOnLaunch
import com.raulshma.jellyplay.core.ui.components.PullToRefreshRegistry
import com.raulshma.jellyplay.core.ui.components.SurpriseLaunchController
import com.raulshma.jellyplay.core.ui.navigation.NAV_DESTINATION_BY_ROUTE
import com.raulshma.jellyplay.core.ui.navigation.NavDestination
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.navigation.applyNavCustomization
import com.raulshma.jellyplay.core.ui.navigation.navKey
import com.raulshma.jellyplay.core.ui.navigation.rememberNavigationState
import com.raulshma.jellyplay.feature.player.video.DesktopPlayerKeyBridge
import com.raulshma.jellyplay.feature.player.video.DesktopVideoSurfaceBridge
import com.raulshma.jellyplay.feature.player.video.VideoPlayerScreen
import com.raulshma.jellyplay.feature.music.feedback.DesktopMusicMessageBus
import com.raulshma.jellyplay.feature.music.feedback.MusicMessageBus
import com.raulshma.jellyplay.feature.shell.navigation.ShellHostHooks
import com.raulshma.jellyplay.feature.shell.navigation.ShellSectionRegistry
import com.raulshma.jellyplay.feature.shell.navigation.shellEntryProvider
import com.raulshma.jellyplay.desktop.player.DesktopAudioQueueManager
import com.raulshma.jellyplay.desktop.player.MpvSoftwareSurfaceSupport
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import org.koin.compose.koinInject
import java.util.concurrent.atomic.AtomicReference

/**
 * Desktop nav root (Phase X "desktop nav v1"): session-gated shell over the
 * shared feature conveyor. Signed-out users get [DesktopSignedOutAuthHost]
 * (the shared auth section; wave 19A retired the legacy DesktopSignInPane
 * with its cut-list); a live session renders the NavigationRail + NavDisplay
 * scaffold below.
 *
 * What is deliberately NOT wired yet (each omission dead-ends in the
 * registration-ledger guard below, so a shared screen pushing the route sees
 * a snackbar instead of crashing NavDisplay with an unregistered entry):
 *  - LiveTvChannelPlayer — the live-TV surface has no desktop engine host;
 *  - SubtitleTester — androidMain-only, no commonMain section at all.
 *
 * The metadata editor went live with the wave 18B store promotion:
 * StreamingSubtitleStoreImpl moved to jvmShared with a desktop binding in
 * desktopDataModule (appdata-backed), so [editorSection] below renders
 * Route.MetadataEditor — the details screen's edit action opens the shared
 * EditorScreen (admin-gated, like Android).
 *
 * VIDEO went live with wave 9A on WINDOWS (SwingPanel/HWND mpv surface) and
 * with wave 12B wherever the mpv software-render surface smoke-passes
 * (DesktopSoftwareVideoPane, no child window); the per-session engine resolves
 * through PlayerEngineFactory (desktopPlayerModule); OSes with neither surface
 * story keep the dead-end guard.
 *
 * The AUDIO player went live with wave 9B real audio:
 * [audioPlayerSection] registers Route.AudioPlayer + Route.Ambient, so music
 * track clicks (every music screen pushes Route.AudioPlayer(trackId)) open
 * the now-playing screen over the real desktop audio core —
 * DesktopAudioQueueManager in desktopPlayerModule.
 *
 * Home went live with the wave 8B desktop wiring: the four WorkManager/
 * widget-backed HomeViewModel ctor deps (PlaybackSyncScheduler,
 * TvWatchNextScheduler, ContinueWatchingBroadcaster, LibrarySyncHook) gained
 * honest no-op desktop definitions in desktopDataModule, so [homeSection]
 * renders in the rail below (Video/Music mode persisted through
 * HomeDiscoveryStore like the Android shell).
 *
 * Details + the auth drill-ins went live with the details/auth conveyor
 * flips: [detailsSection] renders behind every shared screen that pushes a
 * detail route (search results, requests/calendar → SeerrDetail, person/
 * cast/collection drill-ins), and [authSection] backs the settings
 * Server/UserManagement pushes (AddServer/ServerList/Login/QuickConnect/
 * UserSelection). Since wave 19A the SAME section is the sign-in flow:
 * [DesktopSignedOutAuthHost] registers it while signed out, so desktop signs
 * in through the shared screens (Quick Connect, remembered-user picker,
 * add-server discovery included) — here the section only serves signed-in
 * server management.
 *
 * Settings + admin went live with the admin repositories' Koin flip (Wave
 * wB): AdminRepository/AdminStatisticsRepository are Koin singles in
 * dataJvmModule on both platforms, so [settingsSection] and [adminSection]
 * render below (the settings drill-ins SeerrSettings/ArrSettings included —
 * their Seerr/Arr/datastore ctor deps are all Koin-native).
 *
 * Music went live next (Wave wC) — browse-only at first, and fully playable
 * since wave 9B: the last unresolved music ctor dep (AudioQueueFacade) binds
 * to the shared DefaultAudioQueueFacade over the desktop
 * DesktopAudioQueueManager (audio-only MpvDesktopEngine behind it), so
 * [musicSection] renders below with its full browse/albums/artists/genres/
 * playlists cluster AND play/enqueue/instant-mix actions drive real playback.
 * Track clicks navigate to the live Route.AudioPlayer (registered by
 * [audioPlayerSection] above). Since wave 21B the music error-feedback seam
 * has a host here too: the shell snackbar collects DesktopMusicMessageBus's
 * relay (one surface shared with the dead-end guard below).
 *
 * First-run onboarding gate (wave 21B): once an authenticated session enters
 * [DesktopNavScaffold], a one-shot read of the persisted `onboarding_completed`
 * flag (AppRuntimeStateStore.isOnboardingCompleted) pushes Route.Onboarding
 * for a not-yet-onboarded user — the Android JellyPlayApp gate's order and
 * pref, so completion through the shared wizard never re-fires it.
 *
 * @param previousCrashLogPath non-null when the previous session wrote a crash
 *   report (DesktopCrashHandler marker consumed at boot); surfaced as a
 *   one-line note + log path in the About dialog — deliberately minimal, this
 *   is a diagnostics pointer, not an error UI.
 * @param windowRef the ComposeWindow handle (Main.kt's AWT ref), consumed
 *   ONLY by the wave-13B session harness (screenshots + key injection).
 *   The parameter is always supplied; the ref's CONTENT is null until the
 *   window is composed.
 */
@Composable
internal fun DesktopAppRoot(
    showAbout: Boolean,
    onDismissAbout: () -> Unit,
    previousCrashLogPath: String? = null,
    windowRef: AtomicReference<ComposeWindow?>? = null,
    // File→Refresh signal (Main.kt's MenuBar owns the item; Ctrl+R). The
    // scaffold dispatches each emission into LocalPullToRefreshRegistry, so it
    // refreshes whatever pull-to-refresh screen is active — not just Home —
    // and is silently dropped when the current screen has no refresh action.
    menuRefreshRequests: kotlinx.coroutines.flow.Flow<Unit> = kotlinx.coroutines.flow.emptyFlow(),
) {
    val authRepository: AuthRepository = koinInject()
    val isAuthenticated by authRepository.isAuthenticated.collectAsState(initial = false)

    // Wave 13B real-server E2E session harness (DesktopSessionHarness KDoc):
    // composes NOTHING unless jellyplay.harness.enabled=true — the host is a
    // bare LaunchedEffect, and it lives HERE (not in DesktopNavScaffold)
    // because the harness performs the login itself and must keep running
    // across the sign-in → scaffold composition swap.
    if (DesktopSessionHarness.requested()) {
        val engineRecorder: com.raulshma.jellyplay.desktop.player.EngineActivityRecorder = koinInject()
        DesktopSessionHarnessHost(
            authRepository = authRepository,
            windowRef = windowRef,
            engineRecorder = engineRecorder,
        )
    }

    // Wave 22F native-dialog harness (DesktopNativeDialogHarness KDoc): the
    // audit-F9 gate for the wave-20 AWT FileDialog flows — composes NOTHING
    // unless jellyplay.dialogpass.enabled=true. Server-free by design (the
    // settings backup round trip is local-prefs-only), so it needs no login
    // and no fixture.
    if (DesktopNativeDialogHarness.requested()) {
        DesktopNativeDialogHarnessHost()
    }

    // Session-restore probe: until it completes we cannot know whether a
    // persisted (server, user) pair exists, so hold on a neutral splash
    // instead of flashing the sign-in pane at every resuming session.
    var sessionRestoreDone by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        authRepository.restoreSession()
        sessionRestoreDone = true
    }

    when {
        !sessionRestoreDone -> SessionRestoreSplash()
        // Wave 19A: the signed-out gate is the SHARED auth flow now (see
        // DesktopSignedOutAuthHost) — the legacy DesktopSignInPane pane is
        // retired with its v1 cut-list.
        !isAuthenticated -> DesktopSignedOutAuthHost()
        else -> DesktopNavScaffold(menuRefreshRequests = menuRefreshRequests)
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = onDismissAbout,
            confirmButton = { TextButton(onClick = onDismissAbout) { Text("Close") } },
            title = { Text("JellyPlay") },
            text = {
                Column {
                    Text("KMP desktop shell (Phase X desktop nav v1). Android app unaffected.")
                    // Wave 10A crash scaffold: the previous session's crash
                    // marker, if any (Main.kt consumed it at boot). One line +
                    // the log path — no link, no error styling; users copy the
                    // path out of this text when filing a report.
                    if (previousCrashLogPath != null) {
                        Text(
                            "Previous session ended unexpectedly.\nCrash log: $previousCrashLogPath",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun SessionRestoreSplash() {
    Box(Modifier.fillMaxSize()) {
        Text("Restoring session…", Modifier.align(Alignment.Center))
    }
}

/**
 * The nav scaffold: rail on the left, NavDisplay on the right, snackbar for
 * the dead-end guard. One back stack per top-level route (the phone app's
 * tab pattern), Esc / Alt+Left mapped to [Navigator.goBack].
 */
@Composable
private fun DesktopNavScaffold(
    menuRefreshRequests: kotlinx.coroutines.flow.Flow<Unit> = kotlinx.coroutines.flow.emptyFlow(),
) {
    val navigation = rememberNavigationState(
        startRoute = Route.Home,
        topLevelRoutes = DESKTOP_TOP_LEVEL_ROUTES,
        savedStateConfiguration = desktopNavSavedStateConfiguration(),
    )
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // File→Refresh / Ctrl+R dispatch (see DesktopAppRoot KDoc): pull-to-refresh
    // screens self-register into this registry via PullToRefreshBox while they
    // are composed and enabled, so a menu refresh lands on the active screen's
    // own onRefresh — Library, Live TV, Music, Admin, Home, all of them — with
    // the same spinner/forced-fetch semantics as the gesture. No registration
    // (e.g. Settings is on top) → nothing to refresh; dropped silently.
    val refreshRegistry = remember { PullToRefreshRegistry() }
    LaunchedEffect(menuRefreshRequests, refreshRegistry) {
        menuRefreshRequests.collect { refreshRegistry.refreshActive() }
    }

    // Wave 13B session harness: publish the live back stack (nav3 is the
    // source of truth) so DesktopSessionHarness can push the player route and
    // assert pops after Esc injection. Provider form reads the CURRENT tab's
    // stack; attaching here is a lambda store, no behavior for normal boots.
    remember(navigation) {
        DesktopSessionHarness.attachBackStackProvider {
            navigation.backStacks[navigation.topLevelRoute.value]
        }
        true
    }

    // Wave 12B: wire the software-surface prober before any route guard reads
    // it (the Route.VideoPlayer entry registration below asks it while the
    // entry provider graph is built; the guard derives the same predicate
    // from the graph's ledger). The probe itself is lazy and cached
    // inside MpvSoftwareSurfaceSupport — the first VideoPlayer-guard read pays
    // the one-time libmpv/sw-context smoke test; any failure degrades to
    // "unsupported", never crashes boot.
    remember {
        DesktopVideoSurfaceBridge.registerSoftwareSurfaceProbe {
            MpvSoftwareSurfaceSupport.isSupported
        }
        true
    }

    // App-level composition locals the shared screens read. Network status is
    // LIVE (wave 21B): the flow comes from :core:data's DesktopNetworkMonitor
    // (desktopDataModule single — NetworkInterface probing with a 15 s
    // re-probe and a synchronous construction-time seed), so offline banners
    // now reflect real connectivity. Server health stays a static Unknown —
    // deliberate: the desktop shell performs no server health polling, and
    // only StudioDetailScreen reads LocalServerHealth today.
    val networkMonitor: NetworkMonitor = koinInject()
    val serverHealth = remember { MutableStateFlow(ServerHealth.Unknown) }

    // Home Video/Music mode — the Android shell persists this through
    // MainViewModel.setHomeMode; desktop writes the same HomeDiscoveryStore
    // slice so the pick survives restarts and matches the server-synced
    // default (VIDEO).
    val homeDiscoveryStore: HomeDiscoveryStore = koinInject()
    var homeMode by remember { mutableStateOf(HomeMode.VIDEO) }
    LaunchedEffect(homeDiscoveryStore) {
        homeDiscoveryStore.homeDiscovery.collect { slice -> homeMode = slice.homeMode }
    }
    // Bottom-nav customization (#152): the same NavigationStore the phone
    // settings write through drives which items this rail shows and in what
    // order (see the rail composition below).
    val navigationStore: NavigationStore = koinInject()
    val onHomeModeChange: (HomeMode) -> Unit = { mode ->
        homeMode = mode
        scope.launch { homeDiscoveryStore.setHomeMode(mode) }
    }

    // Live desktop audio core — the Home music pane's Now Playing / Ambient
    // cards read the current item + metadata from it (same source the tray
    // and title bar observe; flows are read at click time, not collected).
    val audioQueueManager: DesktopAudioQueueManager = koinInject()

    // Admin gate + logout — the Android shell's MainViewModel duties,
    // inlined for desktop (no desktop MainViewModel exists). isAdmin maps
    // the shared currentUser flow; refreshAdminStatus de-dupes to one
    // server call per 30 s with an in-flight flag, the same contract
    // AdminRouteContainer gets on Android (MainViewModel.refreshAdminStatus).
    val authRepository: AuthRepository = koinInject()
    var isAdmin by remember { mutableStateOf(false) }
    var isRefreshingAdmin by remember { mutableStateOf(false) }
    var lastAdminRefreshAt by remember { mutableStateOf(0L) }
    LaunchedEffect(authRepository) {
        authRepository.currentUser.collect { user -> isAdmin = user?.isAdmin == true }
    }
    val refreshAdminStatus = {
        val now = System.currentTimeMillis()
        if (!isRefreshingAdmin && now - lastAdminRefreshAt >= ADMIN_REFRESH_INTERVAL_MS) {
            isRefreshingAdmin = true
            scope.launch {
                try {
                    authRepository.refreshCurrentUser()
                    lastAdminRefreshAt = System.currentTimeMillis()
                } finally {
                    isRefreshingAdmin = false
                }
            }
        }
    }
    val onLogout: (Boolean) -> Unit = { revoke ->
        // Same semantics as the Android SessionCoordinator pair: revoke=true
        // also revokes the server session. isAuthenticated flips false and
        // DesktopAppRoot swaps in the signed-out auth host.
        scope.launch {
            if (revoke) authRepository.revokeServerSession() else authRepository.logout()
        }
    }

    // AppUpdate split (Wave xB): the About screen's "Check for updates" row.
    // Desktop has no self-update (the desktopDataModule version sentinel makes
    // isUpdateAvailable permanently false, so selectAsset can never offer an
    // Android APK), so a successful check always reads "up to date" — same
    // wording as the Android update sheet. The row itself is pref-gated by
    // selfUpdateCheckEnabled (default on).
    val appUpdateRepository: AppUpdateRepository = koinInject()
    val onCheckForUpdates: () -> Unit = {
        scope.launch {
            val result = appUpdateRepository.checkForUpdate()
            val message = result.getOrNull()?.let { info ->
                if (info.isUpdateAvailable) {
                    "Version ${info.latestVersion} is available; self-update is not supported on desktop yet."
                } else {
                    "You're up to date"
                }
            } ?: "Update check failed: ${result.exceptionOrNull()?.message ?: "unknown error"}"
            snackbarHostState.showSnackbar(message)
        }
    }

    // Dead-end guard (runtime safety, not polish): NavDisplay with an
    // unregistered top-of-stack entry is a crash hazard, and the shared
    // screens freely push routes that have no desktop section (see class
    // KDoc). The decision is DERIVED, not enumerated: sectionRegistry is the
    // shell-owned ledger the entry provider built below re-attaches on every
    // rebuild, so a route is a dead end exactly when no shellEntryProvider
    // section registered it — LiveTvChannelPlayer/SubtitleTester because
    // their builders are Android-only, VideoPlayer wherever the surface
    // probe fails (its registration flows through the same extraSections
    // slot). The former hand-kept three-route mirror is gone. Unregistered
    // routes surface as a snackbar — the desktop twin of the Android shell's
    // PlaybackHostRouter navigateFilter.
    val sectionRegistry = remember { ShellSectionRegistry() }
    val guardedNavigator = remember(navigation, sectionRegistry) {
        Navigator(navigation) { route ->
            if (sectionRegistry.isRegistered(route)) {
                true
            } else {
                val name = route::class.simpleName ?: route.toString()
                scope.launch {
                    snackbarHostState.showSnackbar("$name is not available on desktop yet.")
                }
                false
            }
        }
    }

    // First-run onboarding gate (wave 21B): the persisted
    // `onboarding_completed` flag is read ONCE per scaffold composition —
    // i.e. once per authenticated session entry — and a not-yet-onboarded
    // session gets the shared wizard pushed (the same Route.Onboarding the
    // Shortcuts entry and the settings "rerun setup" row open). The
    // isOnboardingCompleted() one-shot read (not the Eagerly-shared state
    // flow, whose seed is all-defaults before the prefs file lands) is what
    // keeps an already-onboarded user from seeing the wizard at every boot.
    // Completion flows back through the shared OnboardingViewModel — same
    // pref on both platforms — so the gate never re-fires for a completer.
    // isAuthenticated is structurally true here (this scaffold only composes
    // in DesktopAppRoot's authenticated branch); the parameter keeps the
    // pure decision honest against the Android gate it mirrors.
    val appRuntimeStateStore: AppRuntimeStateStore = koinInject()
    LaunchedEffect(appRuntimeStateStore) {
        val onboardingCompleted = appRuntimeStateStore.isOnboardingCompleted()
        desktopOnboardingGateRoute(
            isAuthenticated = true,
            onboardingCompleted = onboardingCompleted,
        )?.let(guardedNavigator::navigate)
    }

    // Music message-bus host (wave 21B): the desktop MusicMessageBus actual
    // is a buffering relay (DesktopMusicMessageBus, desktopMusicMessageBus
    // Module) — surface its error messages in the shell snackbar, the twin
    // of Android bridging the same seam into the app-wide UserMessageBus.
    // The is-check (not a cast) is the degrade path: a hypothetical custom
    // Koin binding for MusicMessageBus simply goes unhosted, never crashes.
    val musicMessageBus: MusicMessageBus = koinInject()
    if (musicMessageBus is DesktopMusicMessageBus) {
        LaunchedEffect(musicMessageBus) {
            musicMessageBus.messages.collect { message ->
                snackbarHostState.showSnackbar(message)
            }
        }
    }

    val currentTopLevel by navigation.topLevelRoute
    val backStack = checkNotNull(navigation.backStacks[currentTopLevel]) {
        "no back stack for top-level route $currentTopLevel"
    }

    // Shell-supplied surface behind the shared section graph (ShellHostHooks):
    // the now-playing/ambient lambdas read the desktop audio core
    // (DesktopAudioQueueManager) at click time, and the settings/admin seams
    // wrap the inlined MainViewModel duties above — the same values, same
    // lazy reads the old inline entryProvider captured. Remembered on the
    // values the hooks capture, so the graph rebuilds only when they change.
    val shellHost = remember(guardedNavigator, homeMode) {
        ShellHostHooks(
            homeMode = homeMode,
            onHomeModeChange = onHomeModeChange,
            onNowPlayingClick = {
                audioQueueManager.currentPlayingItemId.value?.let { itemId ->
                    guardedNavigator.navigate(Route.AudioPlayer(itemId))
                }
            },
            onAmbientClick = {
                guardedNavigator.navigate(
                    Route.Ambient(
                        imageUrl = audioQueueManager.albumArtUrl.value.ifEmpty { null },
                        title = audioQueueManager.title.value,
                        artist = audioQueueManager.artist.value,
                    ),
                )
            },
            onLogout = onLogout,
            onCheckForUpdates = onCheckForUpdates,
            // Lazy reads — admin refreshes don't rebuild the graph.
            isAdmin = { isAdmin },
            isRefreshingAdmin = { isRefreshingAdmin },
            onRefreshAdmin = { refreshAdminStatus() },
        )
    }

    // Remember the entry provider graph so the ~20 shared section builders
    // aren't re-invoked (allocating fresh lambdas + entry objects) on every
    // recomposition of this scaffold (same memoization the Android shell
    // applies). The graph — and with it the sectionRegistry the guard above
    // reads — is the shared appSections canonical order (nav3 resolves by
    // key, so the former per-shell ordering was never routing behaviour)
    // plus the one desktop-side registration below.
    val shellSections = remember(guardedNavigator, shellHost) {
        shellEntryProvider(
            navigator = guardedNavigator,
            host = shellHost,
            registry = sectionRegistry,
        ) {
            // …player-video, wave 9A conveyor — live where a surface story
            // exists: the commonMain VideoPlayerScreen renders the wave-12B
            // software-render pane wherever its probe smoke-passed (primary —
            // the video sits inside the compose tree, so controls and clicks
            // work), falling back to the SwingPanel/HWND mpv surface, and the
            // per-session engine resolves through PlayerEngineFactory
            // (desktopPlayerModule). OSes with neither story keep the
            // dead-end guard above. The subtitle-tester overlay stays
            // Android-only: its push target dead-ends in the guard above.
            if (DesktopVideoSurfaceBridge.isWindowsVideoSurfaceSupported ||
                DesktopVideoSurfaceBridge.isSoftwareVideoSurfaceSupported
            ) {
                entry<Route.VideoPlayer> { key ->
                    VideoPlayerScreen(
                        itemId = key.itemId,
                        mediaSourceId = key.mediaSourceId,
                        startPositionTicks = key.startPositionTicks,
                        subtitleStreamIndex = key.subtitleStreamIndex,
                        audioStreamIndex = key.audioStreamIndex,
                        onBack = { guardedNavigator.goBack() },
                    )
                }
            }
        }
    }

    Row(
        Modifier
            .fillMaxSize()
            // Back handling: Esc and Alt+Left pop the current stack when
            // there is anything to pop (nav3's predictive back is
            // Android-only; this is the whole desktop story).
            //
            // Wave 14E deterministic media-key delivery: this preview is the
            // TOPMOST key-input chain, so it receives EVERY key with or
            // without any Compose focus owner (the null-focus fallback; ESC
            // has worked here since wave 13B). When the video player route is
            // current, every non-back key is offered to the player screen's
            // OWN handler through DesktopPlayerKeyBridge — the screen stays
            // the single interpreter of media-key semantics (this shell never
            // decodes a media key), and the sink declines when the focused
            // dispatch chain owns the key or a sheet is open, so a key is
            // interpreted exactly once either way. This closes the wave-14D
            // flap gap: a SPACE/arrow/M/F/J/L pressed (or injected) while the
            // AWT/Compose focus shuffle left the player Box focus-less used
            // to die in this Row's fallback; now it reaches the player
            // deterministically.
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val isBack = event.key == Key.Escape ||
                    (event.key == Key.DirectionLeft && event.isAltPressed)
                if (isBack) {
                    if (backStack.size <= 1) {
                        false
                    } else {
                        guardedNavigator.goBack()
                        true
                    }
                } else {
                    backStack.lastOrNull() is Route.VideoPlayer &&
                        DesktopPlayerKeyBridge.deliver(event)
                }
            },
    ) {
        // Fullscreen routes (the video player) take the whole content area:
        // hide the rail while one is on top; Esc/back pops out of it.
        // nav3 keys are the base type; only our Route subclasses carry isFullScreen.
        val topRouteIsFullscreen = (backStack.lastOrNull() as? Route)?.isFullScreen == true
        if (!topRouteIsFullscreen) {
            NavigationRail(
                // No header: branding lives in the custom title bar
                // (DesktopTitleBar) — a second "JellyPlay" here duplicated it.
                header = null,
            ) {
                // The rail holds 15 destinations (~1100dp of items) — far more
                // than the default 800dp window height. NavigationRail's own
                // column never scrolls, so without this scrollable wrapper the
                // lower destinations (Requests…Admin) are clipped and
                // unreachable. weight(1f) keeps the header pinned and scrolls
                // only the item list.
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                // Breathing room now that the rail header is gone (the rail's
                // own vertical padding is only 4dp) and under the last item
                // when the list is scrolled to the bottom.
                Spacer(Modifier.height(12.dp))
                // Rail items come from the stored nav customization
                // (Appearance → Navigation): hidden items drop off, custom
                // order is honored, then the rest keep the default order.
                // Group spacers are preserved between consecutive items whose
                // group changes, so a custom order can interleave groups.
                val navPrefs by navigationStore.navigation.collectAsState()
                val railDescriptors = remember(navPrefs) {
                    applyNavCustomization(
                        DESKTOP_RAIL_ITEMS,
                        { it.route.navKey },
                        navPrefs.hiddenNavItems,
                        navPrefs.navItemOrder,
                    )
                }
                railDescriptors.forEachIndexed { index, descriptor ->
                    if (index > 0 && descriptor.railGroup != railDescriptors[index - 1].railGroup) {
                        Spacer(Modifier.height(12.dp))
                    }
                    DesktopRailItem(
                        descriptor.route,
                        descriptor.railLabel,
                        descriptor.icon,
                        currentTopLevel,
                        guardedNavigator,
                    )
                }
                Spacer(Modifier.height(12.dp))
                }
            }
        }

        // The shared HomeHeroController reads LocalSurpriseOnLaunch
        // unconditionally; Android arms it from the launcher-shortcut
        // ("Surprise Me") intent, a seam desktop has no equivalent of.
        // Provide a never-armed controller so the read resolves — desktop's
        // in-app "Surprise Me" path is the surpriseRequests flow parameter,
        // which never touches this local.
        val surpriseController = remember {
            SurpriseLaunchController(
                armed = MutableStateFlow(false),
                consume = {},
            )
        }
        CompositionLocalProvider(
            LocalNetworkStatus provides networkMonitor.networkStatus,
            LocalServerHealth provides serverHealth,
            LocalSurpriseOnLaunch provides surpriseController,
            LocalPullToRefreshRegistry provides refreshRegistry,
        ) {
            Box(Modifier.weight(1f).fillMaxHeight()) {
                NavDisplay(
                    backStack = backStack,
                    onBack = { guardedNavigator.goBack() },
                    entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
                    entryProvider = shellSections.entryProvider,
                )
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                )
            }
        }
    }
}

/**
 * The rail renders the shared [NavDestination] registry — label, icon and
 * group come from the one destination-facts table in core/ui (the former
 * per-shell `DesktopRailDescriptor` list is gone). Only the rail's OWN
 * display order stays here; it is per-shell policy, not a destination fact.
 */
private val DESKTOP_RAIL_ITEMS: List<NavDestination> = listOf(
    Route.Home,
    Route.Search,
    Route.Library,
    Route.LiveTv,
    Route.MusicBrowse,
    Route.Downloads,
    Route.Newsletter,
    Route.WatchProgressHeatmap,
    Route.SyncPlay,
    Route.Requests,
    Route.UpcomingCalendar,
    Route.ArrQueue,
    Route.Shortcuts,
    Route.Settings,
    Route.AdminDashboard,
).mapNotNull(NAV_DESTINATION_BY_ROUTE::get)

/** Rail + tab-switch destinations; Home is the start tab (same as the Android shell). */
private val DESKTOP_TOP_LEVEL_ROUTES: Set<Route> = DESKTOP_RAIL_ITEMS.map { it.route }.toSet()

/**
 * The saved-state configuration every desktop NavDisplay shares: the sealed
 * Route serializer is registered as the polymorphic NavKey default so
 * saved-state lookups resolve any Route subclass without enumerating ~100
 * leaves per lookup. Two consumers today — [DesktopNavScaffold] and
 * [DesktopSignedOutAuthHost].
 */
internal fun desktopNavSavedStateConfiguration(): SavedStateConfiguration =
    SavedStateConfiguration {
        serializersModule = SerializersModule {
            polymorphic(NavKey::class) {
                // The sealed Route hierarchy enumerates its leaves via
                // kotlin-reflect; new Route subclasses register themselves.
                for (leaf in Route::class.sealedSubclasses) {
                    @Suppress("UNCHECKED_CAST")
                    val leafClass = leaf as KClass<NavKey>
                    @Suppress("UNCHECKED_CAST")
                    val leafSerializer =
                        serializer(leaf.java as Class<NavKey>) as KSerializer<NavKey>
                    subclass(leafClass, leafSerializer)
                }
            }
        }
    }

@Composable
private fun DesktopRailItem(
    route: Route,
    label: String,
    icon: ImageVector,
    currentTopLevel: NavKey,
    navigator: Navigator,
) {
    NavigationRailItem(
        selected = currentTopLevel == route,
        onClick = { navigator.navigate(route) },
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label) },
    )
}


/** Admin-status re-validation window, matching MainViewModel's 30 s dedup. */
private const val ADMIN_REFRESH_INTERVAL_MS = 30_000L
