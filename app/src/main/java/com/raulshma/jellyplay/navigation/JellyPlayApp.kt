package com.raulshma.jellyplay.navigation

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.core.spring
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import com.raulshma.jellyplay.R
import com.raulshma.jellyplay.core.designsystem.theme.AlphaEasing
import com.raulshma.jellyplay.core.designsystem.theme.Dimensions
import com.raulshma.jellyplay.core.designsystem.theme.FancyTransitionEasing
import com.raulshma.jellyplay.core.designsystem.theme.PointToPointEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.tv.material3.MaterialTheme as TvMaterial3Theme
import androidx.tv.material3.darkColorScheme as tvDarkColorScheme
import androidx.tv.material3.Icon as TvIcon
import androidx.tv.material3.Text as TvText
import androidx.tv.material3.LocalContentColor as TvLocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.graphics.Color

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.PaddingValues
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.roundToInt
import com.raulshma.jellyplay.MainActivity
import com.raulshma.jellyplay.MainViewModel
import com.raulshma.jellyplay.core.model.MainPreferences
import com.raulshma.jellyplay.core.model.isExperimentalEnabled
import com.raulshma.jellyplay.core.data.playback.AudioPlaybackManager
import com.raulshma.jellyplay.core.model.ExperimentalFeature
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.navigation.components.ExpressiveFloatingNavigationBar
import com.raulshma.jellyplay.navigation.components.MoreToggleIcon
import com.raulshma.jellyplay.navigation.playbackhost.HostDecision
import com.raulshma.jellyplay.navigation.playbackhost.PlaybackHostRouter
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.LocalJellyPlayUi
import com.raulshma.jellyplay.core.ui.adaptive.WindowSizeClass
import com.raulshma.jellyplay.core.ui.adaptive.rememberJellyPlayUiEnvironment
import com.raulshma.jellyplay.core.ui.adaptive.rememberAdaptiveInfo
import com.raulshma.jellyplay.core.designsystem.theme.TvTypography
import com.raulshma.jellyplay.core.designsystem.theme.LocalIsSynthwave
import com.raulshma.jellyplay.core.designsystem.theme.LocalIsSoothingTheme
import com.raulshma.jellyplay.core.designsystem.theme.LocalIsMonochromeTheme
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.cardBorder
import com.raulshma.jellyplay.core.designsystem.theme.containerTint
import com.raulshma.jellyplay.core.designsystem.theme.shadowElevation
import com.raulshma.jellyplay.core.designsystem.theme.tonalElevation
import com.raulshma.jellyplay.core.ui.components.LocalNavigationBarColor
import com.raulshma.jellyplay.core.ui.components.MiniPlayer
import com.raulshma.jellyplay.core.ui.components.clearFloatingNav
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.core.ui.components.LocalPerformanceMode
import com.raulshma.jellyplay.core.ui.components.LocalFloatingNavVisibility
import com.raulshma.jellyplay.core.ui.feedback.LocalUserMessageBus
import com.raulshma.jellyplay.core.ui.feedback.UserMessage
import com.raulshma.jellyplay.core.ui.feedback.resolve
import com.raulshma.jellyplay.core.ui.animation.DefaultNavTransitionPolicy
import com.raulshma.jellyplay.core.ui.animation.NavDirection
import com.raulshma.jellyplay.core.ui.animation.NavTransitionContext
import com.raulshma.jellyplay.core.ui.animation.isReducedMotion
import com.raulshma.jellyplay.core.ui.animation.toTransition
import com.raulshma.jellyplay.core.ui.navigation.ALL_TOP_LEVEL_ROUTE_KEYS
import com.raulshma.jellyplay.core.ui.navigation.MUSIC_TOP_LEVEL_ROUTES
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.navigation.VIDEO_TOP_LEVEL_ROUTES
import com.raulshma.jellyplay.core.ui.navigation.rememberNavigationState
import com.raulshma.jellyplay.core.ui.navigation.SHORTCUTS_NAV_KEY
import com.raulshma.jellyplay.core.ui.navigation.toNavRouteClass
import com.raulshma.jellyplay.core.ui.tv.TvScaffold
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.LocalTvTypography
import com.raulshma.jellyplay.core.ui.tv.isTv
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key

import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import com.raulshma.jellyplay.feature.auth.navigation.authSection
import com.raulshma.jellyplay.feature.admin.navigation.adminSection
import com.raulshma.jellyplay.feature.details.navigation.detailsSection
import com.raulshma.jellyplay.feature.downloads.navigation.downloadsSection
import com.raulshma.jellyplay.feature.editor.navigation.editorSection
import com.raulshma.jellyplay.feature.home.navigation.homeSection
import com.raulshma.jellyplay.feature.insights.navigation.insightsSection
import com.raulshma.jellyplay.feature.library.navigation.librarySection
import com.raulshma.jellyplay.feature.livetv.navigation.liveTvSection
import com.raulshma.jellyplay.feature.music.navigation.musicSection
import com.raulshma.jellyplay.feature.music.musichome.MusicHomeScreen
import com.raulshma.jellyplay.feature.player.audio.navigation.audioPlayerSection
import com.raulshma.jellyplay.feature.player.live.navigation.livePlayerSection
import com.raulshma.jellyplay.feature.search.navigation.searchSection
import com.raulshma.jellyplay.feature.settings.navigation.settingsSection
import com.raulshma.jellyplay.feature.syncplay.navigation.syncPlaySection
import com.raulshma.jellyplay.feature.onboarding.navigation.onboardingSection
import com.raulshma.jellyplay.feature.newsletter.navigation.newsletterSection
import com.raulshma.jellyplay.feature.requests.navigation.requestsSection
import com.raulshma.jellyplay.feature.arrqueue.navigation.arrQueueSection
import com.raulshma.jellyplay.feature.calendar.navigation.calendarSection
import com.raulshma.jellyplay.feature.shortcuts.navigation.shortcutsSection
import com.raulshma.jellyplay.feature.subtitle.tester.navigation.subtitleTesterSection
import com.raulshma.jellyplay.update.AppUpdateSheet
import kotlinx.coroutines.launch
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

private const val ExitConfirmationTimeoutMs = 2000L

@Composable
fun JellyPlayApp(
    viewModel: MainViewModel,
) {
    val isRestoring by viewModel.isRestoring.collectAsStateWithLifecycle()
    val isAuthenticated by viewModel.isAuthenticated.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val isTv = context.isTv()

    LaunchedEffect(Unit) {
        if (isTv && isAuthenticated && !preferences.onboardingCompleted) {
            viewModel.appRuntimeStateStore.setOnboardingCompleted(true)
        }
    }

    when {
        isRestoring -> {}
        isAuthenticated && !preferences.onboardingCompleted && !isTv -> {
            OnboardingContent(
                onComplete = {},
                viewModel = viewModel,
            )
        }
        isAuthenticated -> {
            // "Surprise Me" launcher-shortcut controller. Built
            // once so the StateFlow reference is stable across recompositions.
            val surpriseController = remember(viewModel) {
                com.raulshma.jellyplay.core.ui.components.SurpriseLaunchController(
                    armed = viewModel.surpriseOnLaunch,
                    consume = { viewModel.consumeSurpriseOnLaunch() },
                )
            }
            CompositionLocalProvider(
                com.raulshma.jellyplay.core.ui.components.LocalNetworkStatus provides viewModel.networkMonitor.networkStatus,
                com.raulshma.jellyplay.core.ui.components.LocalServerHealth provides viewModel.serverHealth,
                com.raulshma.jellyplay.core.ui.components.LocalSurpriseOnLaunch provides surpriseController,
            ) {
                MainContent(
                    onLogout = { revoke ->
                        if (revoke) {
                            viewModel.revokeServerSession()
                        } else {
                            viewModel.logout()
                        }
                    },
                    viewModel = viewModel,
                    preferences = preferences,
                )
            }
        }
        else -> {
            AuthContent(
                onAuthenticated = {},
            )
        }
    }

    // In-app self-update sheet. Rendered at the root so it overlays every
    // screen (auth, onboarding, main). Stays hidden while Idle; the launch-time
    // check in MainViewModel flips it to UpdateAvailable when a newer build
    // exists. Keep this after the `when` so the sheet sits above all content.
    UpdateSheetOverlay(viewModel)
}

/**
 * Collects [MainViewModel.updateState] and shows the [AppUpdateSheet] when an
 * update flow is active. Centralized here so the launch-time auto-check and
 * any manual check (Settings) drive the same single sheet.
 */
@Composable
private fun UpdateSheetOverlay(viewModel: MainViewModel) {
    val state by viewModel.updateState.collectAsStateWithLifecycle()
    val autoDownloadEnabled by viewModel.selfUpdateDownloadEnabled.collectAsStateWithLifecycle()
    if (state !is com.raulshma.jellyplay.update.UpdateState.Idle) {
        val context = LocalContext.current
        AppUpdateSheet(
            state = state,
            autoDownloadEnabled = autoDownloadEnabled,
            onAutoDownloadToggle = { enabled ->
                viewModel.setSelfUpdateDownloadEnabled(enabled)
                // Turning it ON while an update is already available should also
                // start downloading the shown update immediately.
                val available = state as? com.raulshma.jellyplay.update.UpdateState.UpdateAvailable
                if (enabled && available != null) {
                    viewModel.startUpdateDownload(available.info)
                }
            },
            onDownload = { info -> viewModel.startUpdateDownload(info) },
            onInstall = { intent ->
                runCatching { context.startActivity(intent) }
            },
            onRedownload = { viewModel.redownloadUpdate() },
            onCancel = { viewModel.dismissUpdate() },
            onDismiss = { viewModel.dismissUpdate() },
            buildInstallIntent = {
                // Only valid in the Downloaded state, where the file is held.
                val downloaded = state as? com.raulshma.jellyplay.update.UpdateState.Downloaded
                viewModel.buildInstallIntent(downloaded?.file ?: java.io.File(""))
            },
        )
    }
}

@Composable
private fun AuthContent(
    onAuthenticated: () -> Unit,
) {
    val navigationState = rememberNavigationState(
        startRoute = Route.ServerList,
        topLevelRoutes = setOf(Route.ServerList),
    )
    val navigator = Navigator(navigationState)

    val saveableStateHolder = rememberSaveableStateHolder()
    val entryDecorator = rememberSaveableStateHolderNavEntryDecorator<NavKey>(saveableStateHolder)

    NavDisplay(
        backStack = navigationState.backStacks.values.first(),
        onBack = { navigator.goBack() },
        entryDecorators = listOf(entryDecorator),
        entryProvider = entryProvider {
            authSection(navigator, onAuthenticated)
        },
    )
}

@Composable
private fun OnboardingContent(
    onComplete: () -> Unit,
    viewModel: MainViewModel,
) {
    com.raulshma.jellyplay.feature.onboarding.OnboardingScreen(
        onComplete = onComplete,
    )
}

@Composable
private fun MainContent(
    onLogout: (Boolean) -> Unit,
    viewModel: MainViewModel,
    preferences: MainPreferences,
) {
    val homeMode = preferences.homeMode
    val isSynthwave = com.raulshma.jellyplay.core.designsystem.theme.LocalIsSynthwave.current
    val isSoothing = com.raulshma.jellyplay.core.designsystem.theme.LocalIsSoothingTheme.current
    val isMonochrome = com.raulshma.jellyplay.core.designsystem.theme.LocalIsMonochromeTheme.current

    // `true` once per fresh ViewModel (state-loss restore): process death,
    // "Don't keep activities", or low-memory eviction — every case where the
    // player's in-memory state is gone but the saveable nav back stack
    // survives. Config-change recreate reuses the VM, so this reads false and
    // rotation/locale keep the player. Captured in `remember` so it is stable
    // for this Activity's lifetime; rememberNavigationState runs the strip at
    // most once.
    val stripPlayerRoutesOnRestore = remember { viewModel.consumeStateLossRestore() }

    val navigationState = rememberNavigationState(
        startRoute = Route.Home,
        topLevelRoutes = ALL_TOP_LEVEL_ROUTE_KEYS,
        stripPlayerRoutesOnRestore = stripPlayerRoutesOnRestore,
    )
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var pendingExternalLaunch by remember { mutableStateOf<com.raulshma.jellyplay.ExternalPlayerLaunch?>(null) }
    val externalPlayerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result: ActivityResult ->
        val launch = pendingExternalLaunch
        pendingExternalLaunch = null
        if (launch != null) {
            val finalMs = result.data?.let { data ->
                val pos = data.extras?.get("position") ?: data.extras?.get("positionMs")
                val ms = when (pos) {
                    is Number -> pos.toLong()
                    else -> -1L
                }
                ms.takeIf { it >= 0 }
            }
            val finalTicks = finalMs?.let { it * 10_000 } ?: -1L
            viewModel.reportExternalPlaybackStopped(launch, finalTicks)
        }
    }
    val navigator = Navigator(navigationState, navigateFilter = { route ->
        // Thin executing adapter for PlaybackHostRouter — the single owner of
        // the "which host mounts playback" decision. ExternalPlayer → the
        // app-level ActivityResultLauncher below (its returned position is
        // credited via reportExternalPlaybackStopped, so Continue Watching
        // advances for regular videos and Live TV channels); DedicatedActivity
        // → PlayerActivity (system PiP floats over this browse UI; back-stack
        // choreography: shared taskAffinity, singleTask). Both return false so
        // the route never enters an in-nav back stack. InNav/NotPlayback →
        // true, the Navigator pushes normally.
        when (val decision = PlaybackHostRouter.decide(route, preferences.preferredPlayer)) {
            is HostDecision.ExternalPlayer -> {
                scope.launch {
                    val launch = viewModel.buildExternalPlayerLaunch(
                        itemId = decision.itemId,
                        mediaSourceId = decision.mediaSourceId,
                        startPositionTicks = decision.startPositionTicks,
                    ) ?: return@launch
                    viewModel.reportExternalPlaybackStart(launch)
                    pendingExternalLaunch = launch
                    val chooser = Intent.createChooser(launch.intent, "Open with…")
                    runCatching { externalPlayerLauncher.launch(chooser) }
                        .onFailure {
                            pendingExternalLaunch = null
                            viewModel.userMessageBus.error(
                                com.raulshma.jellyplay.core.ui.feedback.uiTextOf(
                                    com.raulshma.jellyplay.core.ui.R.string.msg_no_video_player_found,
                                ),
                            )
                        }
                }
                false
            }
            is HostDecision.DedicatedActivity -> {
                context.startActivity(decision.args.buildIntent(context))
                false
            }
            HostDecision.InNav, HostDecision.NotPlayback -> true
        }
    })
    val currentTopLevel by navigationState.topLevelRoute
    val currentRoute = navigator.currentRoute()

    val isPlayerScreen = currentRoute is Route.VideoPlayer ||
            currentRoute is Route.LiveTvChannelPlayer

    val isAudioPlayerScreen = currentRoute is Route.AudioPlayer

    // A full-screen route may sit below the top of the back stack (e.g. the
    // video player with the subtitle tester pushed on top of it). Keep the
    // full-screen layout branch active while *any* full-screen route is on the
    // current stack: switching the branch mid-round-trip re-registers the
    // player's NavKey in a second NavDisplay subtree against the shared
    // SaveableStateHolder, crashing with "Key VideoPlayer(...) was used
    // multiple times" on the back-pop. isPlayerScreen/isAudioPlayerScreen stay
    // top-only since they only drive chrome styling.
    val currentBackStack = navigationState.backStacks[navigationState.topLevelRoute.value]
    val isFullScreenRoute = currentBackStack?.any { it is Route && it.isFullScreen } ?: false

    // App-wide offline state. Library + Live TV have no offline fallback (they
    // always fetch live and degrade to a dead-end ErrorScreen), so while offline
    // they are hidden from the floating nav. Home is the offline hub, Search has
    // an offline-results path, Shortcuts are device-local, and MusicBrowse's
    // home surfaces the downloaded music library — all stay visible.
    val offlineMode by viewModel.offlineMode.collectAsStateWithLifecycle()
    val isGoingOnline by viewModel.isGoingOnline.collectAsStateWithLifecycle()
    val downloadCount by viewModel.activeDownloadCount.collectAsStateWithLifecycle()
    val isOffline = offlineMode != com.raulshma.jellyplay.core.model.OfflineMode.ONLINE

    // Memoize the route filter+reorder so it only re-runs when homeMode /
    // hiddenNavItems / navItemOrder / offline actually change. MainContent
    // recomposes frequently (it reads audioTitle/artist/... for the mini
    // player), so the previous eager `when{}` allocated a fresh LinkedHashMap +
    // intermediate entry lists + KClass.simpleName lookups on every
    // recomposition.
    val activeTopLevelRoutes: LinkedHashMap<Route, String> by remember(
        homeMode,
        preferences.hiddenNavItems,
        preferences.navItemOrder,
        isOffline,
    ) {
        derivedStateOf {
            when (homeMode) {
                HomeMode.VIDEO -> VIDEO_TOP_LEVEL_ROUTES
                HomeMode.MUSIC -> MUSIC_TOP_LEVEL_ROUTES
            }.let { routes ->
                val hidden = preferences.hiddenNavItems
                val order = preferences.navItemOrder
                // Server-bound destinations with no offline fallback. Hidden
                // by simpleName to stay consistent with the user hidden-item
                // filter below (also keyed on simpleName).
                val offlineHidden = if (isOffline) {
                    setOf(Route.Library::class.simpleName, Route.LiveTv::class.simpleName)
                } else {
                    emptySet()
                }
                val filtered = routes.filterKeys { route ->
                    route::class.simpleName !in hidden && route::class.simpleName !in offlineHidden
                }
                if (order.isEmpty()) {
                    LinkedHashMap(filtered)
                } else {
                    val ordered = linkedMapOf<Route, String>()
                    for (name in order) {
                        val entry = filtered.entries.find { it.key::class.simpleName == name }
                        if (entry != null) ordered[entry.key] = entry.value
                    }
                    for (entry in filtered) {
                        if (entry.key::class.simpleName !in order) {
                            ordered[entry.key] = entry.value
                        }
                    }
                    ordered
                }
            }
        }
    }

    val onModeChange: (HomeMode) -> Unit = { mode ->
        scope.launch { viewModel.homeDiscoveryStore.setHomeMode(mode) }
    }

    val audioPlaybackManager: AudioPlaybackManager = viewModel.audioPlaybackManager
    val isAudioPlaying by audioPlaybackManager.isPlaying.collectAsStateWithLifecycle()
    val audioItemId by audioPlaybackManager.currentPlayingItemId.collectAsStateWithLifecycle()
    val audioTitle by audioPlaybackManager.title.collectAsStateWithLifecycle()
    val audioArtist by audioPlaybackManager.artist.collectAsStateWithLifecycle()
    val audioArtworkUrl by audioPlaybackManager.albumArtUrl.collectAsStateWithLifecycle()
    val libraryFolders by viewModel.libraryFolders.collectAsStateWithLifecycle()
    var isMiniPlayerDismissed by remember { mutableStateOf(false) }
    val showMiniPlayer by remember {
        derivedStateOf { audioItemId != null && !isFullScreenRoute && !isMiniPlayerDismissed }
    }

    LaunchedEffect(audioItemId) {
        if (audioItemId != null) {
            isMiniPlayerDismissed = false
        }
    }

    val pendingRoute by viewModel.pendingRoute.collectAsStateWithLifecycle()
    LaunchedEffect(pendingRoute) {
        pendingRoute?.let { route ->
            if (ALL_TOP_LEVEL_ROUTE_KEYS.contains(route)) {
                navigationState.topLevelRoute.value = route
            } else {
                navigator.navigate(route)
            }
            viewModel.consumePendingRoute()
        }
    }

    // Consume remote "Play" / "Playstate" / "GeneralCommand" navigation requests
    // emitted by the WebSocket receiver.
    LaunchedEffect(viewModel.remoteNavigationBridge) {
        viewModel.remoteNavigationBridge.targets.collect { target ->
            when (target) {
                is com.raulshma.jellyplay.core.data.remote.NavigationTarget.ClosePlayer -> {
                    // Pop any active player entries from every back stack so the
                    // player UI actually disappears (not just hidden behind a tab
                    // switch). This matches Jellyfin web's "Stop" semantics.
                    navigationState.backStacks.values.forEach { stack ->
                        while (stack.isNotEmpty()) {
                            val last = stack.last()
                            if (last is Route.VideoPlayer ||
                                last is Route.AudioPlayer ||
                                last is Route.LiveTvChannelPlayer
                            ) {
                                stack.removeLastOrNull()
                            } else {
                                break
                            }
                        }
                    }
                }
                else -> navigator.navigate(
                    when (target) {
                        is com.raulshma.jellyplay.core.data.remote.NavigationTarget.OpenVideoPlayer -> Route.VideoPlayer(
                            itemId = target.itemId,
                            mediaSourceId = target.mediaSourceId,
                            startPositionTicks = target.startPositionTicks,
                            audioStreamIndex = target.audioStreamIndex,
                            subtitleStreamIndex = target.subtitleStreamIndex,
                        )
                        is com.raulshma.jellyplay.core.data.remote.NavigationTarget.OpenAudioPlayer -> Route.AudioPlayer(target.itemId)
                        is com.raulshma.jellyplay.core.data.remote.NavigationTarget.OpenMediaDetail -> Route.MediaDetail(target.itemId)
                        else -> Route.Home
                    }
                )
            }
        }
    }

    // SyncPlay auto-open: a joined group started playing (or switched items)
    // while no player is on top of any back stack → open the video player.
    // When a player IS already open, its SyncPlayBridge drives the item load
    // in place — pushing another VideoPlayer route here would stack duplicate
    // player screens.
    LaunchedEffect(Unit) {
        viewModel.syncPlayOpenRequests.collect { request ->
            val playerOpen = navigationState.backStacks.values.any { stack ->
                stack.lastOrNull() is Route.VideoPlayer
            }
            if (!playerOpen) {
                navigator.navigate(
                    Route.VideoPlayer(
                        itemId = request.itemId,
                        startPositionTicks = request.startPositionTicks,
                    )
                )
            }
        }
    }

    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    val nowPlayingTemplate = stringResource(R.string.snackbar_now_playing)
    androidx.compose.runtime.LaunchedEffect(viewModel.remoteControlReceiver) {
        viewModel.remoteControlReceiver.playEvents.collect { event ->
            val title = event.title.ifBlank { event.itemId }
            snackbarHostState.showSnackbar(
                message = nowPlayingTemplate.format(title),
                withDismissAction = true,
            )
        }
    }

    val navBarColorState = remember { mutableStateOf<Color?>(null) }
    val animatedNavBarColor by animateColorAsState(
        targetValue = navBarColorState.value ?: MaterialTheme.colorScheme.surfaceContainer,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "navBarColor",
    )

    val isTv = context.isTv()

    // Press-and-hold "peek" preview (Instagram-style). Remembered once at the
    // root and provided via LocalMediaPreviewController; the overlay collects it
    // below. Purely ephemeral UI state, so no DI/ViewModel involvement. The
    // whole feature is dormant unless the user opts in under Experimental
    // settings (off by default).
    val peekEnabled = preferences.isExperimentalEnabled(ExperimentalFeature.MEDIA_CARD_PEEK)
    val mediaPreviewController = remember {
        com.raulshma.jellyplay.core.ui.preview.MediaPreviewController()
    }
    val mediaPreviewState by mediaPreviewController.state.collectAsStateWithLifecycle()
    // Blur the live content behind the peek overlay. Skipped on TV (no peek), in
    // performance mode (Modifier.blur over the full tree is GPU-costly), and when
    // the feature is disabled — in which case mediaPreviewState is always null.
    val previewBlur by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (peekEnabled && !isTv && !preferences.performanceMode && mediaPreviewState != null) 14f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "previewBackdropBlur",
    )
    val previewBlurModifier =
        if (previewBlur > 0.5f) Modifier.blur(previewBlur.dp) else Modifier

    // Single root collector for app-wide one-shot messages.
    // Phone renders a Snackbar (accessible, dismissible, localizable); TV keeps
    // a system Toast since the TV layout has no root SnackbarHost. Either way,
    // emission is now centralized through UserMessageBus instead of scattered
    // Toast.makeText calls across modules.
    val userMessageBus = viewModel.userMessageBus
    androidx.compose.runtime.LaunchedEffect(userMessageBus, isTv) {
        userMessageBus.messages.collect { message ->
            val resolvedText = message.text.resolve(context)
            if (isTv) {
                android.widget.Toast.makeText(
                    context,
                    resolvedText,
                    if (message is UserMessage.Error) android.widget.Toast.LENGTH_LONG
                    else android.widget.Toast.LENGTH_SHORT,
                ).show()
            } else {
                snackbarHostState.showSnackbar(
                    message = resolvedText,
                    withDismissAction = true,
                    duration = if (message is UserMessage.Error) {
                        androidx.compose.material3.SnackbarDuration.Long
                    } else {
                        androidx.compose.material3.SnackbarDuration.Short
                    },
                )
            }
        }
    }

    val adaptiveInfo = rememberAdaptiveInfo()
    val uiEnvironment = rememberJellyPlayUiEnvironment(
        adaptiveInfo = adaptiveInfo,
        isTv = isTv,
    )

    val tvTypography = if (isTv) TvTypography else null

    val bottomNavHeight = Dimensions.floatingNavHeight // Canonical floating nav-bar height
    val bottomNavHeightPx = with(LocalDensity.current) { bottomNavHeight.toPx() }
    val bottomNavOffsetHeightPx = remember { mutableFloatStateOf(0f) }
    val isBottomNavVisibleState = remember { mutableStateOf(true) }
    var isBottomNavVisible by isBottomNavVisibleState

    val animatedBottomNavOffset by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isBottomNavVisible) 0f else -bottomNavHeightPx * 2,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "bottomNavOffset"
    )

    LaunchedEffect(animatedBottomNavOffset) {
        bottomNavOffsetHeightPx.floatValue = animatedBottomNavOffset
    }

    // Stable getter for the floating-nav offset. Reading
    // `bottomNavOffsetHeightPx.floatValue` here would force `MainContent` to
    // recompose on every animation frame of the nav-bar slide (which re-runs
    // the whole TV/Phone/FullScreen branch dispatch). Exposing a `() -> Float`
    // instead lets leaf consumers read the value inside Modifier.offset { … }
    // (layout phase) — no recomposition at all, just relayout.
    val floatingNavOffset: () -> Float = remember(bottomNavOffsetHeightPx) {
        { bottomNavOffsetHeightPx.floatValue }
    }

    // Stable lambdas for CompositionLocalProvider — `provides` compares by ==,
    // so a fresh lambda per recomposition forces downstream invalidation even
    // when the captured state hasn't changed. MainContent recomposes often
    // (audio metadata, nav color, mini-player), so hoist these out.
    val consumeSearchQuery: () -> Unit = remember(viewModel) {
        { viewModel.consumePendingSearchQuery() }
    }

    CompositionLocalProvider(
        LocalTvMode provides isTv,
        LocalAdaptiveInfo provides adaptiveInfo,
        LocalJellyPlayUi provides uiEnvironment,
        LocalTvTypography provides tvTypography,
        LocalPerformanceMode provides preferences.performanceMode,
        com.raulshma.jellyplay.core.ui.feedback.LocalHapticsEnabled provides preferences.hapticsEnabled,
        LocalFloatingNavVisibility provides isBottomNavVisibleState,
        LocalUserMessageBus provides userMessageBus,
        com.raulshma.jellyplay.core.ui.preview.LocalMediaPreviewController provides mediaPreviewController,
        com.raulshma.jellyplay.core.ui.preview.LocalMediaPeekEnabled provides
            preferences.isExperimentalEnabled(ExperimentalFeature.MEDIA_CARD_PEEK),
        com.raulshma.jellyplay.core.ui.components.LocalCardDisplayPreferences provides com.raulshma.jellyplay.core.ui.components.CardDisplayPreferences(
            showUnwatchedBadge = preferences.showUnwatchedBadge,
            showWatchedCheckmark = preferences.showWatchedCheckmark,
            hideWatchedItems = preferences.hideWatchedItems,
        ),
    ) {
        val isExpanded = adaptiveInfo.windowSizeClass != WindowSizeClass.Compact
        val pendingSearchQuery by viewModel.pendingSearchQuery.collectAsStateWithLifecycle()

        @OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
        androidx.compose.animation.SharedTransitionLayout {
            CompositionLocalProvider(
                com.raulshma.jellyplay.core.ui.components.LocalSharedTransitionScope provides if (preferences.performanceMode) null else this,
                LocalNavigationBarColor provides navBarColorState,
                com.raulshma.jellyplay.core.ui.components.LocalFloatingNavOffset provides (if (!isExpanded && !isFullScreenRoute) floatingNavOffset else ({ 0f })),
                com.raulshma.jellyplay.feature.search.LocalPendingSearchQuery provides pendingSearchQuery,
                com.raulshma.jellyplay.feature.search.LocalConsumeSearchQuery provides consumeSearchQuery,
            ) {
            // Hoist the saveable-state holder above the isTv/isFullScreenRoute branches so that
            // navigation-entry saveable state (scroll position, form fields, etc.) survives
            // transitions between phone <-> TV <-> full-screen layouts. Previously each
            // MainNavDisplay call site created its own holder, so saveable state was lost on
            // every layout-branch switch (e.g. entering the player and back).
            val saveableStateHolder = rememberSaveableStateHolder()
            val entryDecorator = rememberSaveableStateHolderNavEntryDecorator<NavKey>(saveableStateHolder)
            // Hoist the audio-mini-player navigation callbacks so all three layout branches
            // (TvContent / PhoneContent / FullScreenContent) can share identical instances
            // instead of allocating fresh lambdas per call site.
            val onNowPlayingClick: () -> Unit = {
                audioItemId?.let { itemId -> navigator.navigate(Route.AudioPlayer(itemId)) }
            }
            val onAmbientClick: () -> Unit = {
                navigator.navigate(
                    Route.Ambient(
                        imageUrl = audioArtworkUrl,
                        title = audioTitle,
                        artist = audioArtist,
                    )
                )
            }

            Box(Modifier.fillMaxSize()) {
            // Wrap the live content (TV / Phone / FullScreen) in its own Box so
            // the peek overlay's backdrop blur applies to it only, leaving the
            // SnackbarHost and the overlay itself sharp.
            Box(Modifier.fillMaxSize().then(previewBlurModifier)) {
            // Hoist the TV drawer state above the isFullScreenRoute branch so it survives visiting a
            // full-screen route (e.g. the player) and back, instead of being recreated when
            // TvNavigationDrawer leaves and re-enters composition. Fully-qualified to keep the TV
            // DrawerState type distinct from any mobile-material3 names.
            val tvDrawerState = androidx.tv.material3.rememberDrawerState(androidx.tv.material3.DrawerValue.Closed)
            val tvDrawerListState = androidx.compose.foundation.lazy.rememberLazyListState()
            if (isTv && !isFullScreenRoute) {
                TvContent(
                    navigationState = navigationState,
                    currentTopLevel = currentTopLevel,
                    activeTopLevelRoutes = activeTopLevelRoutes,
                    navigator = navigator,
                    onLogout = onLogout,
                    homeMode = homeMode,
                    onModeChange = onModeChange,
                    saveableStateHolder = saveableStateHolder,
                    entryDecorator = entryDecorator,
                    onNowPlayingClick = onNowPlayingClick,
                    onAmbientClick = onAmbientClick,
                    tvDrawerState = tvDrawerState,
                    tvDrawerListState = tvDrawerListState,
                    libraryFolders = libraryFolders,
                    hiddenNavItems = preferences.hiddenNavItems,
                    nowPlayingTitle = audioTitle.takeIf { audioItemId != null },
                    nowPlayingEnabled = audioItemId != null,
                    showMiniPlayer = showMiniPlayer,
                    audioPlaybackManager = audioPlaybackManager,
                    isAudioPlaying = isAudioPlaying,
                    audioTitle = audioTitle,
                    audioArtist = audioArtist,
                    audioArtworkUrl = audioArtworkUrl,
                    onDismissMiniPlayer = { isMiniPlayerDismissed = true },                )
            } else {
                if (!isFullScreenRoute) {
                    // Wire the system/gesture back button to in-app navigation so back
                    // from a deep screen returns to the tab root. At a tab root, mirror
                    // the TV path: prompt with a toast and only exit on a second press
                    // within ExitConfirmationTimeoutMs. The full-screen player is
                    // excluded — it owns its own BackHandler.
                    var lastBackPressTime by remember { mutableLongStateOf(0L) }
                    BackHandler(enabled = true) {
                        if (!navigator.isAtTabRoot()) {
                            navigator.goBack()
                        } else {
                            val now = System.currentTimeMillis()
                            if (now - lastBackPressTime < ExitConfirmationTimeoutMs) {
                                lastBackPressTime = 0L
                                (context as? android.app.Activity)?.moveTaskToBack(true)
                            } else {
                                lastBackPressTime = now
                                android.widget.Toast.makeText(
                                    context,
                                    context.getString(R.string.press_back_again_to_exit),
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    }
                    PhoneContent(
                        navigationState = navigationState,
                        currentTopLevel = currentTopLevel,
                        activeTopLevelRoutes = activeTopLevelRoutes,
                        navigator = navigator,
                        onLogout = onLogout,
                        homeMode = homeMode,
                        onModeChange = onModeChange,
                        saveableStateHolder = saveableStateHolder,
                        entryDecorator = entryDecorator,
                        onNowPlayingClick = onNowPlayingClick,
                        onAmbientClick = onAmbientClick,
                        isAudioPlayerScreen = isAudioPlayerScreen,
                        isSynthwave = isSynthwave,
                        isExpanded = isExpanded,
                        isBottomNavVisibleState = isBottomNavVisibleState,
                        hideBottomNavOnScroll = preferences.hideBottomNavOnScroll,
                        bottomNavOffsetHeightPx = bottomNavOffsetHeightPx,
                        showMiniPlayer = showMiniPlayer,
                        audioPlaybackManager = audioPlaybackManager,
                        isAudioPlaying = isAudioPlaying,
                        audioItemId = audioItemId,
                        audioTitle = audioTitle,
                        audioArtist = audioArtist,
                        audioArtworkUrl = audioArtworkUrl,
                        onDismissMiniPlayer = { isMiniPlayerDismissed = true },                        animatedNavBarColor = animatedNavBarColor,
                        showNavBarLabels = preferences.navBarShowLabels,
                        offlineMode = offlineMode,
                        isGoingOnline = isGoingOnline,
                        downloadCount = downloadCount,
                        onSurpriseClick = {
                            // Switch to Home first so the hero controller is
                            // composed, then fire the surprise signal it collects.
                            navigationState.topLevelRoute.value = Route.Home
                            viewModel.requestSurprise()
                        },
                        onToggleOffline = { viewModel.toggleOfflineMode() },
                        surpriseRequests = viewModel.surpriseRequests,
                    )
                } else {
                    FullScreenContent(
                        navigationState = navigationState,
                        navigator = navigator,
                        onLogout = onLogout,
                        homeMode = homeMode,
                        onModeChange = onModeChange,
                        saveableStateHolder = saveableStateHolder,
                        entryDecorator = entryDecorator,
                        onNowPlayingClick = onNowPlayingClick,
                        onAmbientClick = onAmbientClick,                    )
                }
                }
            } // end inner blur Box
                com.raulshma.jellyplay.core.ui.components.JellyPlaySnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(androidx.compose.ui.Alignment.BottomCenter)
                        .padding(bottom = if (isFullScreenRoute) 16.dp else 96.dp),
                )
            }
            // Press-and-hold peek overlay — topmost. Only composed when the user
            // has enabled the experimental feature and on phone; on TV the
            // controller is never triggered, so this would render nothing anyway.
            if (peekEnabled && !isTv) {
                com.raulshma.jellyplay.core.ui.preview.MediaPreviewOverlay(
                    controller = mediaPreviewController,
                )
            }
            }
        }
    }
}

/**
 * TV form-factor layout:_TV Material3 theme + [TvNavigationDrawer] host wrapping a single
 * [MainNavDisplay]. Extracted from [MainContent] so the per-form-factor scaffolding stays
 * isolated and the orchestrator stays a clean when-branch picker.
 */
@Composable
private fun TvContent(
    navigationState: com.raulshma.jellyplay.core.ui.navigation.NavigationState,
    currentTopLevel: NavKey,
    activeTopLevelRoutes: LinkedHashMap<Route, String>,
    navigator: Navigator,
    onLogout: (Boolean) -> Unit,
    homeMode: HomeMode,
    onModeChange: (HomeMode) -> Unit,
    saveableStateHolder: androidx.compose.runtime.saveable.SaveableStateHolder,
    entryDecorator: NavEntryDecorator<NavKey>,
    onNowPlayingClick: () -> Unit,
    onAmbientClick: () -> Unit,
    tvDrawerState: androidx.tv.material3.DrawerState,
    tvDrawerListState: androidx.compose.foundation.lazy.LazyListState,
    libraryFolders: List<com.raulshma.jellyplay.core.model.LibraryFolder>,
    hiddenNavItems: Set<String> = emptySet(),
    nowPlayingTitle: String?,
    nowPlayingEnabled: Boolean,
    showMiniPlayer: Boolean,
    audioPlaybackManager: AudioPlaybackManager,
    isAudioPlaying: Boolean,
    audioTitle: String,
    audioArtist: String,
    audioArtworkUrl: String,
    onDismissMiniPlayer: () -> Unit,) {
    TvMaterial3Theme(
        colorScheme = tvDarkColorScheme(
            background = MaterialTheme.colorScheme.background,
            surface = MaterialTheme.colorScheme.surfaceContainer,
            onBackground = MaterialTheme.colorScheme.onSurface,
            onSurface = MaterialTheme.colorScheme.onSurface,
            primary = MaterialTheme.colorScheme.primary,
            onPrimary = MaterialTheme.colorScheme.onPrimary,
            secondary = MaterialTheme.colorScheme.secondary,
            onSecondary = MaterialTheme.colorScheme.onSecondary,
            border = MaterialTheme.colorScheme.outline,
            borderVariant = MaterialTheme.colorScheme.outlineVariant,
        )
    ) {
        TvScaffold {
            val tvCurrentRoute = navigationState.backStacks[currentTopLevel]?.lastOrNull()
            val tvIsSubPage = tvCurrentRoute != null && tvCurrentRoute !in activeTopLevelRoutes.keys

            // Hoist the nav-item list so it is only reallocated when the route
            // set changes, not on every MainContent recomposition (which fires
            // frequently — it reads audio title/artist/artwork for the mini-
            // player). Re-running .map{} here would allocate a fresh list + N
            // fresh TvNavItem instances each time, invalidating the drawer.
            // Shortcuts is no longer a top-level tab on phone (relocated to the
            // ⋮ overflow). The TV drawer has no overflow menu, so keep Shortcuts
            // reachable here as an explicit primary item.
            val shortcutsLabel = stringResource(R.string.menu_shortcuts)
            val primaryNavItems = remember(activeTopLevelRoutes, shortcutsLabel, hiddenNavItems) {
                val baseItems = activeTopLevelRoutes.entries.map { (route, label) ->
                    TvNavItem(
                        route = route,
                        label = label,
                        icon = routeToIcon(route),
                    )
                }
                if (SHORTCUTS_NAV_KEY !in hiddenNavItems) {
                    baseItems + TvNavItem(
                        route = Route.Shortcuts,
                        label = shortcutsLabel,
                        icon = routeToIcon(Route.Shortcuts),
                    )
                } else {
                    baseItems
                }
            }
            TvNavigationDrawer(
                primaryItems = primaryNavItems,
                libraryFolders = libraryFolders,
                currentTopLevel = currentTopLevel,
                isSubPage = tvIsSubPage,
                onNavigate = { navigator.navigate(it) },
                onBack = { navigator.goBack() },
                drawerState = tvDrawerState,
                drawerListState = tvDrawerListState,
                currentRoute = tvCurrentRoute,
                nowPlayingTitle = nowPlayingTitle,
                nowPlayingEnabled = nowPlayingEnabled,
                onNowPlayingClick = onNowPlayingClick,
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    MainNavDisplay(
                        navigationState = navigationState,
                        navigator = navigator,
                        onLogout = onLogout,
                        homeMode = homeMode,
                        onModeChange = onModeChange,
                        saveableStateHolder = saveableStateHolder,
                        entryDecorator = entryDecorator,
                        onNowPlayingClick = onNowPlayingClick,
                        onAmbientClick = onAmbientClick,
                    )
                    // TV mini-player transport: the drawer's "Now Playing" row only opens the
                    // full player, so without this overlay backgrounded audio has no D-pad-
                    // reachable pause/skip/close bar on TV. The components are already TV-aware
                    // (MiniPlayer applies tvFocusIndicator); this is the host
                    // wiring gap for TV navigation.
                    if (showMiniPlayer) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 8.dp),
                        ) {
                            MiniPlayer(
                                isVisible = true,
                                title = audioTitle,
                                artist = audioArtist,
                                artworkUri = audioArtworkUrl,
                                isPlaying = isAudioPlaying,
                                onClick = onNowPlayingClick,
                                onClose = {
                                    audioPlaybackManager.stopAndRelease()
                                    onDismissMiniPlayer()
                                },
                                onPlayPause = {
                                    audioPlaybackManager.togglePlayPause()
                                },
                                onSkipNext = {
                                    audioPlaybackManager.skipToNext()
                                },
                            )
                        }
                    }                }
            }
        }
    }
}

/**
 * Phone (and large-screen NavigationRail) layout: [NavigationSuiteScaffold] hosting
 * [MainNavDisplay] with floating mini-player(s) and the [ExpressiveFloatingNavigationBar].
 * All scroll-coupled bottom-nav state is owned here.
 */
@Composable
private fun PhoneContent(
    navigationState: com.raulshma.jellyplay.core.ui.navigation.NavigationState,
    currentTopLevel: NavKey,
    activeTopLevelRoutes: LinkedHashMap<Route, String>,
    navigator: Navigator,
    onLogout: (Boolean) -> Unit,
    homeMode: HomeMode,
    onModeChange: (HomeMode) -> Unit,
    saveableStateHolder: androidx.compose.runtime.saveable.SaveableStateHolder,
    entryDecorator: NavEntryDecorator<NavKey>,
    onNowPlayingClick: () -> Unit,
    onAmbientClick: () -> Unit,
    isAudioPlayerScreen: Boolean,
    isSynthwave: Boolean,
    isExpanded: Boolean,
    isBottomNavVisibleState: androidx.compose.runtime.MutableState<Boolean>,
    hideBottomNavOnScroll: Boolean,
    bottomNavOffsetHeightPx: androidx.compose.runtime.MutableFloatState,
    showMiniPlayer: Boolean,
    audioPlaybackManager: AudioPlaybackManager,
    isAudioPlaying: Boolean,
    audioItemId: String?,
    audioTitle: String,
    audioArtist: String,
    audioArtworkUrl: String,
    onDismissMiniPlayer: () -> Unit,    animatedNavBarColor: Color,
    showNavBarLabels: Boolean,
    offlineMode: com.raulshma.jellyplay.core.model.OfflineMode = com.raulshma.jellyplay.core.model.OfflineMode.ONLINE,
    isGoingOnline: Boolean = false,
    downloadCount: Int = 0,
    onSurpriseClick: () -> Unit = {},
    onToggleOffline: () -> Unit = {},
    surpriseRequests: kotlinx.coroutines.flow.Flow<Unit> = kotlinx.coroutines.flow.emptyFlow(),
) {
    val systemNavBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var isBottomNavVisible by isBottomNavVisibleState

    // Play On (cast-to-Jellyfin-session) lives at the app shell so the mini
    // transport persists across tabs. Owned by an activity-scoped VM.
    val playOnViewModel: com.raulshma.jellyplay.PlayOnViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    val playOnState by playOnViewModel.uiState.collectAsStateWithLifecycle()
    var showPlayOnSheet by remember { mutableStateOf(false) }
    val playOnContext = LocalContext.current
    val onPlayOnClick: () -> Unit = { showPlayOnSheet = true }
    // Current top-of-stack route — used to hide the persistent Play On mini bar
    // while its full-screen companion is open (avoid a bar floating over its own
    // expanded view).
    val currentRoute = navigationState.backStacks[navigationState.topLevelRoute.value]?.lastOrNull()
    val isPlayOnCompanionOpen = currentRoute is Route.PlayOnCompanion
    androidx.compose.runtime.LaunchedEffect(showPlayOnSheet) {
        if (showPlayOnSheet) playOnViewModel.startDiscovery(playOnContext)
    }

    // Global "More" overflow — a DotsVertical toggle docked in the nav bar
    // (both Classic and Expressive styles) that expands an animated list of
    // destinations upward, so Settings/Downloads/SyncPlay/Play On are reachable
    // from anywhere (#115). The expanded items render as an overlay above the
    // nav bar (see OverflowMenuItems / OverflowMenuScrim below).
    var isOverflowExpanded by remember { mutableStateOf(false) }
    val onOverflowToggle: (Boolean) -> Unit = { isOverflowExpanded = it }
    // Close the overflow on system/gesture back instead of navigating. Registered
    // only while the menu is open, so when it's disabled the global handler at
    // the call site (back = goBack / exit) takes over again. This also gives the
    // predictive-back system a real OnBackInvokedCallback for the overlay, which
    // stops the back-gesture preview indicator from freezing mid-screen when the
    // scrim's full-screen clickable intercepts the edge swipe (#115).
    BackHandler(enabled = isOverflowExpanded) { isOverflowExpanded = false }

    // When hide-on-scroll is disabled, keep the nav bar permanently visible
        //. The nestedScrollConnection is still constructed so its
        // identity stays stable, but it is only attached to the tree when the
        // setting is on.
        androidx.compose.runtime.LaunchedEffect(hideBottomNavOnScroll) {
            if (!hideBottomNavOnScroll) isBottomNavVisible = true
        }
        val nestedScrollConnection = remember {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    val delta = available.y
                    if (delta < -15f) {
                        isBottomNavVisible = false
                    } else if (delta > 15f) {
                        isBottomNavVisible = true
                    }
                    return Offset.Zero
                }
            }
        }

        NavigationSuiteScaffold(
            navigationSuiteType = if (!isExpanded) NavigationSuiteType.None else NavigationSuiteType.NavigationRail,
            navigationItems = {
                activeTopLevelRoutes.forEach { (route, label) ->
                    // Wrap in key(route) so per-item slot identity survives
                    // route reordering instead of being positional only.
                    androidx.compose.runtime.key(route) {
                        NavigationSuiteItem(
                            selected = route == currentTopLevel,
                            onClick = { navigator.navigate(route) },
                            icon = { NavIcon(route, label, selected = route == currentTopLevel) },
                            label = { Text(label) },
                        )
                    }
                }
                // Tablet NavigationRail has no floating nav bar, so the "More"
                // overflow toggle that phones carry in the nav bar is absent
                // here. Append it as a rail item so Settings/Downloads/SyncPlay/
                // Play On/Shortcuts/Surprise/Offline stay reachable (#115).
                // On phones navigationSuiteType is None, so this never renders.
                if (isExpanded) {
                    NavigationSuiteItem(
                        selected = isOverflowExpanded,
                        onClick = { onOverflowToggle(!isOverflowExpanded) },
                        icon = {
                            MoreToggleIcon(
                                isExpanded = isOverflowExpanded,
                                tint = androidx.compose.material3.LocalContentColor.current,
                                badgeCount = downloadCount,
                            )
                        },
                        label = { Text(stringResource(R.string.nav_more)) },
                    )
                }
            },
            navigationSuiteColors = NavigationSuiteDefaults.colors(
                navigationBarContainerColor = if (isAudioPlayerScreen) Color.Transparent else MaterialTheme.colorScheme.surface,
                navigationRailContainerColor = animatedNavBarColor,
            ),
        ) {
            val synthwaveBrush = remember {
                com.raulshma.jellyplay.core.designsystem.theme.synthwaveBackgroundBrush()
            }
            val appBackgroundModifier = if (isSynthwave) {
                Modifier.background(synthwaveBrush)
            } else {
                Modifier.background(MaterialTheme.colorScheme.background)
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(appBackgroundModifier)
                    .then(if (!isExpanded && hideBottomNavOnScroll) Modifier.nestedScroll(nestedScrollConnection) else Modifier)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    MainNavDisplay(
                        navigationState = navigationState,
                        navigator = navigator,
                        onLogout = onLogout,
                        homeMode = homeMode,
                        onModeChange = onModeChange,
                        saveableStateHolder = saveableStateHolder,
                        entryDecorator = entryDecorator,
                        onNowPlayingClick = onNowPlayingClick,
                        onAmbientClick = onAmbientClick,
                        onPlayOnClick = onPlayOnClick,
                        playOnStrategy = playOnViewModel.strategy,
                        surpriseRequests = surpriseRequests,
                    )
                }
                if (showMiniPlayer && isExpanded) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = systemNavBarBottom + 2.dp)
                    ) {
                        MiniPlayer(
                            isVisible = true,
                            title = audioTitle,
                            artist = audioArtist,
                            artworkUri = audioArtworkUrl,
                            isPlaying = isAudioPlaying,
                            onClick = onNowPlayingClick,
                            onClose = {
                                audioPlaybackManager.stopAndRelease()
                                onDismissMiniPlayer()
                            },
                            onPlayPause = {
                                audioPlaybackManager.togglePlayPause()
                            },
                            onSkipNext = {
                                audioPlaybackManager.skipToNext()
                            },
                        )
                    }
                }
                if (!isExpanded && showMiniPlayer) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = systemNavBarBottom + 60.dp)
                            .offset {
                                val maxOffset = Dimensions.floatingNavHeight.toPx()
                                val yOffset = (-bottomNavOffsetHeightPx.floatValue).coerceAtMost(maxOffset)
                                IntOffset(x = 0, y = yOffset.roundToInt())
                            }
                    ) {
                        MiniPlayer(
                            isVisible = true,
                            title = audioTitle,
                            artist = audioArtist,
                            artworkUri = audioArtworkUrl,
                            isPlaying = isAudioPlaying,
                            onClick = onNowPlayingClick,
                            onClose = {
                                audioPlaybackManager.stopAndRelease()
                                onDismissMiniPlayer()
                            },
                            onPlayPause = {
                                audioPlaybackManager.togglePlayPause()
                            },
                            onSkipNext = {
                                audioPlaybackManager.skipToNext()
                            },
                        )
                    }
                }                // Play On persistent transport bar — visible while a Jellyfin
                // remote session is active and the full-screen companion is not
                // already open. Sits above the floating nav bar.
                if (playOnState.isConnected && !isPlayOnCompanionOpen) {
                    com.raulshma.jellyplay.components.PlayOnMiniBar(
                        isVisible = playOnState.isConnected,
                        targetDeviceName = playOnState.targetDeviceName,
                        title = playOnState.title,
                        subtitle = playOnState.artist,
                        isPlaying = playOnState.isPlaying,
                        positionMs = playOnState.positionMs,
                        durationMs = playOnState.durationMs,
                        volume = playOnState.volume,
                        onPlayPause = {
                            if (playOnState.isPlaying) playOnViewModel.castPause() else playOnViewModel.castPlay()
                        },
                        onSeek = { playOnViewModel.castSeekTo(it) },
                        onVolume = { playOnViewModel.setCastVolume(it) },
                        onDisconnect = { playOnViewModel.disconnect(playOnContext) },
                        onExpand = { navigator.navigate(Route.PlayOnCompanion) },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = systemNavBarBottom + (if (!isExpanded) 72.dp else 8.dp)),
                    )
                }
                if (showPlayOnSheet) {
                    com.raulshma.jellyplay.components.PlayOnDeviceSheet(
                        devices = playOnState.devices,
                        onSelect = { device ->
                            playOnViewModel.connectAndFling(playOnContext, device)
                            showPlayOnSheet = false
                        },
                        onDismiss = {
                            playOnViewModel.stopDiscovery()
                            showPlayOnSheet = false
                        },
                    )
                }
                // Global "More" overflow — a DotsVertical toggle in the nav bar
                // (both Classic and Expressive styles) expands an animated list
                // of destinations upward, so Settings/Downloads/SyncPlay/Play On
                // are reachable from anywhere (#115). The items render as an
                // overlay above the nav bar (see OverflowMenuItems below).
                // On tablet the toggle lives in the NavigationRail (left edge),
                // so the pills dock top-start beside the rail and flow top-down;
                // on phone they dock bottom-end beside the floating nav toggle.
                if (isOverflowExpanded) {
                    com.raulshma.jellyplay.navigation.components.OverflowMenuScrim(
                        onDismiss = { isOverflowExpanded = false },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                    com.raulshma.jellyplay.navigation.components.OverflowMenuItems(
                        onSurpriseClick = {
                            isOverflowExpanded = false
                            onSurpriseClick()
                        },
                        onSyncPlayClick = {
                            isOverflowExpanded = false
                            navigator.navigate(Route.SyncPlay)
                        },
                        onDownloadsClick = {
                            isOverflowExpanded = false
                            navigator.navigate(Route.Downloads)
                        },
                        onToggleOffline = {
                            // Guard re-taps while the offline→online transition is in flight.
                            if (!isGoingOnline) onToggleOffline()
                        },
                        onPlayOnClick = {
                            isOverflowExpanded = false
                            onPlayOnClick()
                        },
                        onShortcutsClick = {
                            isOverflowExpanded = false
                            navigator.navigate(Route.Shortcuts)
                        },
                        onSettingsClick = {
                            isOverflowExpanded = false
                            navigator.navigate(Route.Settings)
                        },
                        offlineMode = offlineMode,
                        isGoingOnline = isGoingOnline,
                        downloadCount = downloadCount,
                        alignToStart = isExpanded,
                        modifier = if (isExpanded) {
                            // The scaffold body already starts beside the rail, so a small
                            // margin keeps the pills flush to the drawer. Anchor at the top
                            // (under the status bar) so the list flows top-down.
                            Modifier.align(Alignment.TopStart)
                                .padding(start = 12.dp, top = statusBarTop + 8.dp)
                        } else {
                            Modifier.align(Alignment.BottomEnd)
                                .clearFloatingNav(extraBottom = 0.dp)
                                .padding(end = 16.dp, bottom = 4.dp)
                        },
                    )
                }
                if (!isExpanded) {
                    val navBarModifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = systemNavBarBottom + 4.dp)
                        .padding(horizontal = 16.dp)
                        .offset { IntOffset(x = 0, y = -bottomNavOffsetHeightPx.floatValue.roundToInt()) }
                    ExpressiveFloatingNavigationBar(
                        routes = activeTopLevelRoutes,
                        currentTopLevel = currentTopLevel,
                        onNavigate = { navigator.navigate(it) },
                        showLabels = showNavBarLabels,
                        containerColor = animatedNavBarColor,
                        isOverflowExpanded = isOverflowExpanded,
                        onOverflowToggle = onOverflowToggle,
                        downloadCount = downloadCount,
                        modifier = navBarModifier,
                    )
                }
            }
        }
    }

/**
 * Full-screen layout (player / onboarding / ambient / photo viewer): bare [Box] with
 * [MainNavDisplay]. Deliberately
 * omits drawer / nav-bar / mini-player chrome.
 */
@Composable
private fun FullScreenContent(
    navigationState: com.raulshma.jellyplay.core.ui.navigation.NavigationState,
    navigator: Navigator,
    onLogout: (Boolean) -> Unit,
    homeMode: HomeMode,
    onModeChange: (HomeMode) -> Unit,
    saveableStateHolder: androidx.compose.runtime.saveable.SaveableStateHolder,
    entryDecorator: NavEntryDecorator<NavKey>,
    onNowPlayingClick: () -> Unit,
    onAmbientClick: () -> Unit,) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        MainNavDisplay(
            navigationState = navigationState,
            navigator = navigator,
            onLogout = onLogout,
            homeMode = homeMode,
            onModeChange = onModeChange,
            saveableStateHolder = saveableStateHolder,
            entryDecorator = entryDecorator,
            onNowPlayingClick = onNowPlayingClick,
            onAmbientClick = onAmbientClick,
        )    }
}

private fun routeToIcon(route: Route): ImageVector = when (route) {
    Route.Home -> Tabler.Outline.Home
    Route.Library -> Tabler.Outline.Stack2
    Route.Search -> Tabler.Outline.Search
    Route.LiveTv -> Tabler.Outline.DeviceTv
    Route.MusicBrowse -> Tabler.Outline.Disc
    Route.Shortcuts -> Tabler.Outline.Apps
    else -> Tabler.Outline.Home
}

@Composable
internal fun NavIcon(
    route: Route,
    label: String,
    selected: Boolean = false,
    tint: Color = androidx.compose.material3.LocalContentColor.current,
    iconSize: Dp = 24.dp,
) {
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (selected) 1.15f else 1.0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "iconScale",
    )
    Icon(
        imageVector = routeToIcon(route),
        contentDescription = label,
        tint = tint,
        modifier = androidx.compose.ui.Modifier
            .size(iconSize)
            .scale(scale),
    )
}

@Composable
private fun MainNavDisplay(
    navigationState: com.raulshma.jellyplay.core.ui.navigation.NavigationState,
    navigator: Navigator,
    onLogout: (Boolean) -> Unit,
    homeMode: HomeMode,
    onModeChange: (HomeMode) -> Unit,
    innerPadding: PaddingValues = PaddingValues(0.dp),
    saveableStateHolder: androidx.compose.runtime.saveable.SaveableStateHolder,
    entryDecorator: NavEntryDecorator<NavKey>,
    modifier: Modifier = Modifier,
    onNowPlayingClick: () -> Unit = {},
    onAmbientClick: () -> Unit = {},
    onPlayOnClick: () -> Unit = {},
    playOnStrategy: com.raulshma.jellyplay.core.data.cast.remote.JellyfinRemotePlayCastStrategy? = null,
    surpriseRequests: kotlinx.coroutines.flow.Flow<Unit> = kotlinx.coroutines.flow.emptyFlow(),
) {
    val currentBackStack = navigationState.backStacks[navigationState.topLevelRoute.value] ?: return

    val paddingDecorator = remember(innerPadding) {
        NavEntryDecorator<NavKey>(
            decorate = { entry ->
                // Full-screen hosts (players / ambient / onboarding / photo
                // viewer) bypass the mini-player bottom padding — typed
                // membership instead of the former contentKey.toString()
                // string matching. Known delta vs the string checks: Onboarding
                // and PhotoViewer also skip the padding now, which is correct
                // (no mini player is shown under either). SubtitleTester
                // intentionally keeps the padding (it overlays a fullscreen
                // host, and isFullScreen deliberately excludes it).
                val isFullScreenHost = (entry.contentKey as? Route)?.isFullScreen == true

                if (isFullScreenHost) {
                    entry.Content()
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = innerPadding.calculateBottomPadding())
                    ) {
                        entry.Content()
                    }
                }
            }
        )
    }

    // Read @Composable values ONCE in the composable body — the transition
    // spec lambdas below are NOT composable scopes and cannot call these.
    val motionScheme = MaterialTheme.motionScheme
    val reducedMotion = isReducedMotion()
    val navPolicy = DefaultNavTransitionPolicy

    /**
     * Resolve a [ContentTransform] for a route pair + direction. Plain (non-
     * composable) local fn: it only uses the [motionScheme]/[reducedMotion]
     * vals captured above, so it is safe to call from the non-composable spec
     * lambdas. Returns a [ContentTransform] so the spec lambdas can call it
     * directly.
     */
    fun resolveTransition(
        targetRoute: Route?,
        initialRoute: Route?,
        direction: NavDirection,
    ): ContentTransform {
        val context = NavTransitionContext(
            targetClass = targetRoute.toNavRouteClass,
            initialClass = initialRoute.toNavRouteClass,
            direction = direction,
            isReducedMotion = reducedMotion,
        )
        val kind = navPolicy.kind(context)
        val transition = kind.toTransition(motionScheme)
        return transition.enter togetherWith transition.exit
    }

    // Admin access-control state, collected once here (a @Composable context)
    // and threaded into the admin section as read lambdas so the navigation
    // entries — which are composed lazily — observe the latest value without
    // re-building the entry graph. MainViewModel is activity-scoped, so this
    // resolves to the same instance held by JellyPlayApp/MainActivity.
    val mainViewModel: MainViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    val isAdminState = mainViewModel.isAdmin.collectAsStateWithLifecycle()
    val isRefreshingAdminState = mainViewModel.isRefreshingAdmin.collectAsStateWithLifecycle()

    // Remember the entry provider graph so the ~25 section builders aren't
    // re-invoked (allocating fresh lambdas + entry objects) on every
    // MainNavDisplay recomposition. Re-key on the values it captures.
    val sharedEntryProvider = remember(
        navigator,
        homeMode,
        onModeChange,
        onNowPlayingClick,
        onAmbientClick,
        onLogout,
        onPlayOnClick,
        playOnStrategy,
    ) {
        entryProvider {
            homeSection(
                navigator = navigator,
                homeMode = homeMode,
                onModeChange = onModeChange,
                onPlayOnClick = onPlayOnClick,
                playOnStrategy = playOnStrategy,
                surpriseRequests = surpriseRequests,
                musicContent = {
                    MusicHomeScreen(
                        onItemClick = { itemId -> navigator.navigate(Route.MediaDetail(itemId)) },
                        onAlbumClick = { albumId -> navigator.navigate(Route.AlbumDetail(albumId)) },
                        onArtistsClick = { navigator.navigate(Route.Artists) },
                        onAlbumsClick = { navigator.navigate(Route.Albums) },
                        onTracksClick = { navigator.navigate(Route.Tracks) },
                        onGenresClick = { navigator.navigate(Route.Genres) },
                        onPlaylistsClick = { navigator.navigate(Route.Playlists) },
                        onNowPlayingClick = onNowPlayingClick,
                        onAmbientClick = onAmbientClick,
                    )
                },
            )
            librarySection(navigator)
            searchSection(navigator)
            liveTvSection(navigator)
            detailsSection(navigator)
            editorSection(navigator)
            livePlayerSection(navigator)
            audioPlayerSection(navigator)
            downloadsSection(navigator)
            authSection(navigator) { navigator.goBack() }
            settingsSection(
                navigator = navigator,
                onLogout = onLogout,
                onSetupWizard = { navigator.navigate(Route.Onboarding) },
                onCheckForUpdates = { mainViewModel.manualCheckForUpdate() },
            )
            adminSection(
                navigator = navigator,
                isAdmin = { isAdminState.value },
                isRefreshingAdmin = { isRefreshingAdminState.value },
                onRefreshAdmin = { mainViewModel.refreshAdminStatus() },
            )
            musicSection(navigator)
            syncPlaySection(navigator)
            onboardingSection { navigator.goBack() }
            newsletterSection(navigator)
            insightsSection(navigator)
            requestsSection(navigator)
            arrQueueSection(navigator)
            calendarSection(navigator)
            shortcutsSection(navigator)
            subtitleTesterSection(navigator)
            // Play On companion — full-screen remote-control surface reached by
            // tapping the persistent PlayOnMiniBar. Reuses the activity-scoped
            // PlayOnViewModel (same instance the mini bar holds), so state stays
            // in sync without threading the VM through params.
            entry<Route.PlayOnCompanion> {
                com.raulshma.jellyplay.components.PlayOnCompanionScreen(
                    onBack = { navigator.goBack() },
                )
            }
        }
    }

    NavDisplay(
        backStack = currentBackStack,
        onBack = { navigator.goBack() },
        entryDecorators = listOf(entryDecorator, paddingDecorator),
        transitionSpec = {
            val targetRoute = targetState.entries.lastOrNull()?.contentKey as? Route
            val initialRoute = initialState.entries.lastOrNull()?.contentKey as? Route
            resolveTransition(targetRoute, initialRoute, NavDirection.FORWARD)
        },
        popTransitionSpec = {
            val targetRoute = targetState.entries.lastOrNull()?.contentKey as? Route
            val initialRoute = initialState.entries.lastOrNull()?.contentKey as? Route
            resolveTransition(targetRoute, initialRoute, NavDirection.POP)
        },
        predictivePopTransitionSpec = { _ ->
            val targetRoute = targetState.entries.lastOrNull()?.contentKey as? Route
            val initialRoute = initialState.entries.lastOrNull()?.contentKey as? Route
            resolveTransition(targetRoute, initialRoute, NavDirection.PREDICTIVE_POP)
        },
        entryProvider = sharedEntryProvider,
        modifier = modifier,
    )
}
