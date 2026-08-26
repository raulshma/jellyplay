package com.raulshma.jellyplay.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
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
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.update.AppUpdateRepository
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoveryStore
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.NetworkStatus
import com.raulshma.jellyplay.core.model.ServerHealth
import com.raulshma.jellyplay.core.ui.components.LocalNetworkStatus
import com.raulshma.jellyplay.core.ui.components.LocalServerHealth
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.navigation.rememberNavigationState
import com.raulshma.jellyplay.feature.admin.navigation.adminSection
import com.raulshma.jellyplay.feature.arrqueue.navigation.arrQueueSection
import com.raulshma.jellyplay.feature.auth.navigation.authSection
import com.raulshma.jellyplay.feature.calendar.navigation.calendarSection
import com.raulshma.jellyplay.feature.details.navigation.detailsSection
import com.raulshma.jellyplay.feature.downloads.navigation.downloadsSection
import com.raulshma.jellyplay.feature.home.navigation.homeSection
import com.raulshma.jellyplay.feature.insights.navigation.insightsSection
import com.raulshma.jellyplay.feature.library.navigation.librarySection
import com.raulshma.jellyplay.feature.livetv.navigation.liveTvSection
import com.raulshma.jellyplay.feature.music.navigation.musicSection
import com.raulshma.jellyplay.feature.player.audio.navigation.audioPlayerSection
import com.raulshma.jellyplay.feature.newsletter.navigation.newsletterSection
import com.raulshma.jellyplay.feature.onboarding.navigation.onboardingSection
import com.raulshma.jellyplay.feature.requests.navigation.requestsSection
import com.raulshma.jellyplay.feature.player.video.DesktopVideoSurfaceBridge
import com.raulshma.jellyplay.feature.player.video.VideoPlayerScreen
import com.raulshma.jellyplay.feature.search.navigation.searchSection
import com.raulshma.jellyplay.feature.settings.navigation.settingsSection
import com.raulshma.jellyplay.feature.shortcuts.navigation.shortcutsSection
import com.raulshma.jellyplay.feature.syncplay.navigation.syncPlaySection
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import org.koin.compose.koinInject

/**
 * Desktop nav root (Phase X "desktop nav v1"): session-gated shell over the
 * shared feature conveyor. Signed-out users get [DesktopSignInPane]; a live
 * session renders the NavigationRail + NavDisplay scaffold below.
 *
 * What is deliberately NOT wired yet (each omission is guarded by
 * [isDesktopDeadEndRoute] so a shared screen pushing the route shows a
 * snackbar instead of crashing NavDisplay with an unregistered entry):
 *  - LiveTvChannelPlayer — the live-TV surface has no desktop engine host;
 *  - SubtitleTester — androidMain-only, no commonMain section at all;
 *  - editor — latent (StreamingSubtitleStore has no desktop Koin def).
 *
 * VIDEO went live with wave 9A on WINDOWS: the commonMain VideoPlayerScreen
 * composes the SwingPanel/HWND mpv surface and the per-session engine resolves
 * through PlayerEngineFactory (desktopPlayerModule); non-Windows OSes keep the
 * dead-end guard until they have an embedding story.
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
 * UserSelection). Desktop still signs IN through DesktopSignInPane — the
 * auth section serves signed-in server management only.
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
 * [audioPlayerSection] above).
 *
 * @param previousCrashLogPath non-null when the previous session wrote a crash
 *   report (DesktopCrashHandler marker consumed at boot); surfaced as a
 *   one-line note + log path in the About dialog — deliberately minimal, this
 *   is a diagnostics pointer, not an error UI.
 */
@Composable
internal fun DesktopAppRoot(
    showAbout: Boolean,
    onDismissAbout: () -> Unit,
    previousCrashLogPath: String? = null,
) {
    val authRepository: AuthRepository = koinInject()
    val isAuthenticated by authRepository.isAuthenticated.collectAsState(initial = false)

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
        !isAuthenticated -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            DesktopSignInPane()
        }
        else -> DesktopNavScaffold()
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
private fun DesktopNavScaffold() {
    val navigation = rememberNavigationState(
        startRoute = Route.Search,
        topLevelRoutes = DESKTOP_TOP_LEVEL_ROUTES,
        savedStateConfiguration = SavedStateConfiguration {
            // The sealed Route serializer handles every leaf; registering it
            // as the polymorphic default lets NavKey-scope lookups resolve
            // any Route subclass without enumerating ~100 leaves.
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
        },
    )
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // App-level composition locals the shared screens read. Desktop v1
    // provisions static values: the JVM shell has no connectivity monitor
    // yet (assumed Online — the API client's own reachability/failover
    // probing remains the real gate), and the server-health banner stays
    // neutral (Unknown). Only StudioDetailScreen reads LocalServerHealth
    // today; several screens read LocalNetworkStatus for offline banners.
    val networkStatus = remember { MutableStateFlow(NetworkStatus.Online) }
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
    val onHomeModeChange: (HomeMode) -> Unit = { mode ->
        homeMode = mode
        scope.launch { homeDiscoveryStore.setHomeMode(mode) }
    }

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
        // DesktopAppRoot swaps in the sign-in pane.
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
    // KDoc). Intercept those here and surface them as a snackbar — the
    // desktop twin of the Android shell's PlaybackHostRouter navigateFilter.
    val guardedNavigator = remember(navigation) {
        Navigator(navigation) { route ->
            if (route.isDesktopDeadEndRoute()) {
                val name = route::class.simpleName ?: route.toString()
                scope.launch {
                    snackbarHostState.showSnackbar("$name is not available on desktop yet.")
                }
                false
            } else {
                true
            }
        }
    }

    val currentTopLevel by navigation.topLevelRoute
    val backStack = checkNotNull(navigation.backStacks[currentTopLevel]) {
        "no back stack for top-level route $currentTopLevel"
    }

    // Remember the entry provider graph so the ~12 section builders aren't
    // re-invoked (allocating fresh lambdas + entry objects) on every
    // recomposition of this scaffold (same memoization the Android shell
    // applies to its sharedEntryProvider).
    val entryProvider = remember(guardedNavigator, homeMode) {
        entryProvider {
            // Home, live since the wave 8B desktop wiring: every HomeViewModel
            // ctor dep resolves (the four WorkManager/widget seams are no-op
            // desktop defs in desktopDataModule; the data layer is Koin-native).
            // The music pane composes empty on desktop v1 — music browsing
            // lives in the Music rail section; the Play On redirect stays
            // null (Android shell cast surface). Home's process-lifecycle
            // refresher seam is a jvm no-op, so sections refresh on their own
            // flows (resumes, downloads, watch progress) rather than on a
            // process start/stop signal.
            homeSection(
                navigator = guardedNavigator,
                homeMode = homeMode,
                onModeChange = onHomeModeChange,
                onPlayOnClick = {},
                musicContent = {},
            )
            searchSection(guardedNavigator)
            librarySection(guardedNavigator)
            // Details, live since the details conveyor flip: every VM ctor
            // dep resolves on desktop (data layer Koin-native, platform seams
            // from desktopDetailsPlatformModule, AudioQueueFacade from the
            // wave-9B DefaultAudioQueueFacade binding). Details is drill-in
            // only — no rail entry; shared screens push
            // Route.MediaDetail/SeerrDetail/PersonDetail/etc. Play/edit pushes
            // stay guarded below (MetadataEditor); VideoPlayer is live on
            // Windows and the audio play push routes through the live
            // audioPlayerSection (wave 9B).
            detailsSection(guardedNavigator)
            // …player-video, wave 9A conveyor — live on WINDOWS only: the
            // commonMain VideoPlayerScreen renders its SwingPanel/HWND mpv
            // surface and the per-session MpvDesktopEngine resolves through
            // PlayerEngineFactory (desktopPlayerModule). Other OSes keep the
            // dead-end guard below (no libmpv embedding story there yet).
            // The subtitle-tester overlay stays Android-only: its push target
            // remains in the dead-end list.
            if (DesktopVideoSurfaceBridge.isWindowsVideoSurfaceSupported) {
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
            // Auth cluster drill-ins, live since the auth conveyor flip: the
            // settings Server/UserManagement screens push AddServer/ServerList
            // into these entries. Signed-OUT users still get DesktopSignInPane
            // (this scaffold only renders authenticated); onAuthenticated =
            // goBack, the Android wiring (JellyPlayApp).
            authSection(guardedNavigator) { guardedNavigator.goBack() }
            liveTvSection(guardedNavigator)
            // Music, live since Wave wC and fully playable since wave 9B: the
            // full section (browse/albums/artists/genres/mood+smart
            // playlists/playlist details) renders; play/enqueue/instant-mix
            // resolve AudioQueueFacade to the shared DefaultAudioQueueFacade
            // over the desktop DesktopAudioQueueManager (real playback).
            // Track clicks push Route.AudioPlayer — registered by the
            // audioPlayerSection entries below (which also own Route.Ambient,
            // the player's immersive overlay, and route ArtistDetail into the
            // live music section).
            musicSection(guardedNavigator)
            // Audio player, live since wave 9B real audio: Route.AudioPlayer
            // (the music track-click target) + Route.Ambient. The VM's whole
            // ctor graph resolves — queue/effects/engine over the
            // DesktopAudioQueueManager single, cast over the never-connected
            // desktop no-op, the rest from the shared data/datastore graph.
            audioPlayerSection(guardedNavigator)
            downloadsSection(guardedNavigator)
            syncPlaySection(guardedNavigator)
            newsletterSection(guardedNavigator)
            insightsSection(guardedNavigator)
            calendarSection(guardedNavigator)
            requestsSection(guardedNavigator)
            shortcutsSection(guardedNavigator)
            arrQueueSection(guardedNavigator)
            onboardingSection { guardedNavigator.goBack() }
            // Settings + admin, live since the admin repositories' Koin flip
            // (Wave wB) — every settings/admin VM ctor dep resolves on
            // desktop now (AdminRepository/AdminStatisticsRepository from
            // dataJvmModule, platform seams from desktopSettingsPlatform
            // Module). The About update-check row is live since the AppUpdate
            // split (Wave xB): it hits the real AppUpdateRepository single and
            // surfaces the result via the snackbar above.
            settingsSection(
                navigator = guardedNavigator,
                onLogout = onLogout,
                onSetupWizard = { guardedNavigator.navigate(Route.Onboarding) },
                onCheckForUpdates = onCheckForUpdates,
            )
            adminSection(
                navigator = guardedNavigator,
                isAdmin = { isAdmin },
                isRefreshingAdmin = { isRefreshingAdmin },
                onRefreshAdmin = { refreshAdminStatus() },
            )
        }
    }

    Row(
        Modifier
            .fillMaxSize()
            // Back handling: Esc and Alt+Left pop the current stack when
            // there is anything to pop (nav3's predictive back is
            // Android-only; this is the whole desktop story).
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val isBack = event.key == Key.Escape ||
                    (event.key == Key.DirectionLeft && event.isAltPressed)
                if (!isBack || backStack.size <= 1) {
                    false
                } else {
                    guardedNavigator.goBack()
                    true
                }
            },
    ) {
        // Fullscreen routes (the video player) take the whole content area:
        // hide the rail while one is on top; Esc/back pops out of it.
        // nav3 keys are the base type; only our Route subclasses carry isFullScreen.
        val topRouteIsFullscreen = (backStack.lastOrNull() as? Route)?.isFullScreen == true
        if (!topRouteIsFullscreen) {
            NavigationRail(
                header = {
                    Text(
                        "JellyPlay",
                        Modifier.padding(vertical = 16.dp),
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                    )
                },
            ) {
                DesktopRailItem(Route.Home, "Home", Tabler.Outline.Home, currentTopLevel, guardedNavigator)
                DesktopRailItem(Route.Search, "Search", Tabler.Outline.Search, currentTopLevel, guardedNavigator)
                DesktopRailItem(Route.Library, "Library", Tabler.Outline.Library, currentTopLevel, guardedNavigator)
                DesktopRailItem(Route.LiveTv, "Live TV", Tabler.Outline.DeviceTv, currentTopLevel, guardedNavigator)
                // Same icon the Android app's nav uses for Route.MusicBrowse
                // (JellyPlayApp.routeToIcon): Tabler.Outline.Disc.
                DesktopRailItem(Route.MusicBrowse, "Music", Tabler.Outline.Disc, currentTopLevel, guardedNavigator)
                DesktopRailItem(Route.Downloads, "Downloads", Tabler.Outline.Download, currentTopLevel, guardedNavigator)
                DesktopRailItem(Route.Newsletter, "Newsletter", Tabler.Outline.Mail, currentTopLevel, guardedNavigator)
                DesktopRailItem(Route.WatchProgressHeatmap, "Insights", Tabler.Outline.Flame, currentTopLevel, guardedNavigator)
                DesktopRailItem(Route.SyncPlay, "SyncPlay", Tabler.Outline.Users, currentTopLevel, guardedNavigator)

                Spacer(Modifier.height(12.dp))

                DesktopRailItem(Route.Requests, "Requests", Tabler.Outline.Movie, currentTopLevel, guardedNavigator)
                DesktopRailItem(Route.UpcomingCalendar, "Calendar", Tabler.Outline.Calendar, currentTopLevel, guardedNavigator)
                DesktopRailItem(Route.ArrQueue, "Arr Queue", Tabler.Outline.Stack, currentTopLevel, guardedNavigator)
                DesktopRailItem(Route.Shortcuts, "Shortcuts", Tabler.Outline.Bolt, currentTopLevel, guardedNavigator)

                Spacer(Modifier.height(12.dp))

                DesktopRailItem(Route.Settings, "Settings", Tabler.Outline.Settings, currentTopLevel, guardedNavigator)
                DesktopRailItem(Route.AdminDashboard, "Admin", Tabler.Outline.Shield, currentTopLevel, guardedNavigator)
            }
        }

        CompositionLocalProvider(
            LocalNetworkStatus provides networkStatus,
            LocalServerHealth provides serverHealth,
        ) {
            Box(Modifier.weight(1f).fillMaxHeight()) {
                NavDisplay(
                    backStack = backStack,
                    onBack = { guardedNavigator.goBack() },
                    entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
                    entryProvider = entryProvider,
                )
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                )
            }
        }
    }
}

/** Rail + tab-switch destinations; the start tab stays Search (Home is one click away). */
private val DESKTOP_TOP_LEVEL_ROUTES: Set<Route> = setOf(
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
)

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

/**
 * Routes pushed by the registered shared sections but backed by NO desktop
 * entry — navigation must swallow these instead of letting them reach
 * NavDisplay (unregistered top-of-stack = crash). Keep in sync with the
 * entryProvider block above: everything a registered section pushes must
 * either be registered itself or listed here. Route.VideoPlayer is
 * conditionally registered (Windows only) and guarded on the other OSes;
 * every branch of that when must mirror the entryProvider's conditions.
 */
private fun NavKey.isDesktopDeadEndRoute(): Boolean = when (this) {
    // Players. VideoPlayer is live since wave 9A on WINDOWS: its entry
    // composes the commonMain screen with the SwingPanel/HWND mpv surface and
    // is registered exactly under this same bridge predicate the entry above
    // registers under, so non-Windows OSes dead-end it.
    // Route.AudioPlayer left this list with wave 9B's real audio
    // (audioPlayerSection registers it; audio needs no surface host — the
    // audio-only engine runs with vo=null) and is now the music section's
    // track-click target.
    // Route.LiveTvChannelPlayer is pushed by livetv but has no desktop home,
    // so its clicks surface the snackbar instead.
    is Route.VideoPlayer ->
        !DesktopVideoSurfaceBridge.isWindowsVideoSurfaceSupported
    is Route.LiveTvChannelPlayer -> true
    // Pushed by MediaDetailScreen's edit action: the editor feature is
    // registered but latent on desktop (StreamingSubtitleStore has no
    // desktop Koin def) and has no desktop nav section.
    is Route.MetadataEditor -> true
    // LanguageSettings pushes this from the now-live settings cluster; the
    // subtitle-tester feature is androidMain-only (no commonMain section).
    Route.SubtitleTester -> true
    else -> false
}


/** Admin-status re-validation window, matching MainViewModel's 30 s dedup. */
private const val ADMIN_REFRESH_INTERVAL_MS = 30_000L
