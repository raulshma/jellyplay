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
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import com.raulshma.jellyplay.core.designsystem.theme.AlphaEasing
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.tv.material3.NavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.PaddingValues
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.NavDisplay
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.roundToInt
import com.raulshma.jellyplay.MainActivity
import com.raulshma.jellyplay.MainViewModel
import com.raulshma.jellyplay.core.data.playback.AudioPlaybackManager
import com.raulshma.jellyplay.core.model.ExperimentalFeature
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.isExperimentalEnabled
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
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.core.ui.components.LocalPerformanceMode
import com.raulshma.jellyplay.core.ui.components.LocalFloatingNavVisibility
import com.raulshma.jellyplay.core.ui.feedback.LocalUserMessageBus
import com.raulshma.jellyplay.core.ui.feedback.UserMessage
import com.raulshma.jellyplay.core.ui.feedback.resolve
import com.raulshma.jellyplay.core.ui.navigation.ALL_TOP_LEVEL_ROUTE_KEYS
import com.raulshma.jellyplay.core.ui.navigation.DETAIL_ROUTE_CLASS_NAMES
import com.raulshma.jellyplay.core.ui.navigation.isDetail
import com.raulshma.jellyplay.core.ui.navigation.isModal
import com.raulshma.jellyplay.core.ui.navigation.MUSIC_TOP_LEVEL_ROUTES
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.navigation.VIDEO_TOP_LEVEL_ROUTES
import com.raulshma.jellyplay.core.ui.navigation.rememberNavigationState
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
import com.raulshma.jellyplay.feature.player.video.navigation.videoPlayerSection
import com.raulshma.jellyplay.feature.search.navigation.searchSection
import com.raulshma.jellyplay.feature.settings.navigation.settingsSection
import com.raulshma.jellyplay.feature.syncplay.navigation.syncPlaySection
import com.raulshma.jellyplay.feature.onboarding.navigation.onboardingSection
import com.raulshma.jellyplay.feature.newsletter.navigation.newsletterSection
import com.raulshma.jellyplay.feature.requests.navigation.requestsSection
import com.raulshma.jellyplay.feature.shortcuts.navigation.shortcutsSection
import kotlinx.coroutines.launch
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

internal val LocalDrawerOpener = androidx.compose.runtime.compositionLocalOf { {} }



private fun isDetailScene(scene: Scene<NavKey>): Boolean {
    // Prefer the typed Route.isDetail extension when the content key is a
    // Route (the common case). Fall back to class-name string matching for
    // any non-Route NavKey the host might encounter.
    val key = scene.entries.lastOrNull()?.contentKey ?: return false
    val route = key as? Route
    if (route != null) return route.isDetail
    val className = key.toString().substringBefore('(')
    return className in DETAIL_ROUTE_CLASS_NAMES
}

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
            viewModel.preferencesStore.setOnboardingCompleted(true)
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
            CompositionLocalProvider(
                com.raulshma.jellyplay.core.ui.components.LocalNetworkStatus provides viewModel.networkMonitor.networkStatus,
                com.raulshma.jellyplay.core.ui.components.LocalServerHealth provides viewModel.serverHealth,
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
    preferences: com.raulshma.jellyplay.core.model.UserPreferences,
) {
    val homeMode = preferences.homeMode
    val isSynthwave = com.raulshma.jellyplay.core.designsystem.theme.LocalIsSynthwave.current
    val isSoothing = com.raulshma.jellyplay.core.designsystem.theme.LocalIsSoothingTheme.current
    val isMonochrome = com.raulshma.jellyplay.core.designsystem.theme.LocalIsMonochromeTheme.current

    val navigationState = rememberNavigationState(
        startRoute = Route.Home,
        topLevelRoutes = ALL_TOP_LEVEL_ROUTE_KEYS,
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
        // When the preferred player is EXTERNAL, intercept navigation to *any*
        // video route and hand off to the app-level ActivityResultLauncher
        // below. That launcher reads the external player's returned position
        // and credits watched progress via reportExternalPlaybackStopped — so
        // the Continue Watching row advances for both regular videos and Live
        // TV channels. Returning false prevents the
        // in-app VideoPlayerScreen from composing for the external case.
        val isExternalPreferred = preferences.preferredPlayer ==
            com.raulshma.jellyplay.core.model.PlayerType.EXTERNAL
        val externalTarget = when {
            isExternalPreferred && route is Route.VideoPlayer ->
                ExternalLaunchTarget(route.itemId, route.mediaSourceId, route.startPositionTicks)
            isExternalPreferred && route is Route.LiveTvChannelPlayer ->
                ExternalLaunchTarget(route.channelId, null, 0L)
            else -> null
        }
        if (externalTarget != null) {
            scope.launch {
                val launch = viewModel.buildExternalPlayerLaunch(
                    itemId = externalTarget.itemId,
                    mediaSourceId = externalTarget.mediaSourceId,
                    startPositionTicks = externalTarget.startPositionTicks,
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
        } else {
            true
        }
    })
    val currentTopLevel by navigationState.topLevelRoute
    val currentRoute = navigator.currentRoute()

    val isPlayerScreen = currentRoute is Route.VideoPlayer ||
            currentRoute is Route.LiveTvChannelPlayer

    val isAudioPlayerScreen = currentRoute is Route.AudioPlayer

    val isFullScreenRoute = isPlayerScreen || isAudioPlayerScreen ||
            currentRoute is Route.Ambient || currentRoute is Route.Onboarding ||
            currentRoute is Route.PhotoViewer

    val activeTopLevelRoutes: LinkedHashMap<Route, String> = when (homeMode) {
        HomeMode.VIDEO -> VIDEO_TOP_LEVEL_ROUTES
        HomeMode.MUSIC -> MUSIC_TOP_LEVEL_ROUTES
    }.let { routes ->
        val hidden = preferences.hiddenNavItems
        val order = preferences.navItemOrder
        val filtered = routes.filterKeys { route ->
            route::class.simpleName !in hidden
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

    val onModeChange: (HomeMode) -> Unit = { mode ->
        scope.launch { viewModel.preferencesStore.setHomeMode(mode) }
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

    val videoMiniPlayerState = viewModel.videoMiniPlayerState
    val isVideoMiniMode by videoMiniPlayerState.isMiniMode.collectAsStateWithLifecycle()
    val videoMiniTitle by videoMiniPlayerState.title.collectAsStateWithLifecycle()
    val videoMiniSubtitle by videoMiniPlayerState.subtitle.collectAsStateWithLifecycle()
    val videoMiniIsPlaying by videoMiniPlayerState.isPlaying.collectAsStateWithLifecycle()
    val videoMiniItemId by videoMiniPlayerState.itemId.collectAsStateWithLifecycle()

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

    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    androidx.compose.runtime.LaunchedEffect(viewModel.remoteControlReceiver) {
        viewModel.remoteControlReceiver.playEvents.collect { event ->
            val title = event.title.ifBlank { event.itemId }
            snackbarHostState.showSnackbar(
                message = "Now playing: $title",
                withDismissAction = true,
            )
        }
    }

    val enterPip: () -> Unit = remember(context) {
        {
            (context as? MainActivity)?.enterPipMode()
        }
    }

    val enterVideoMiniMode: () -> Unit = remember(navigator) {
        {
            navigator.goBack()
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
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 220),
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

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()

    val adaptiveInfo = rememberAdaptiveInfo()
    val uiEnvironment = rememberJellyPlayUiEnvironment(
        adaptiveInfo = adaptiveInfo,
        isTv = isTv,
    )

    val tvTypography = if (isTv) TvTypography else null

    val bottomNavHeight = 80.dp // Approximate height
    val bottomNavHeightPx = with(LocalDensity.current) { bottomNavHeight.toPx() }
    val bottomNavOffsetHeightPx = remember { mutableFloatStateOf(0f) }
    val isBottomNavVisibleState = remember { mutableStateOf(true) }
    var isBottomNavVisible by isBottomNavVisibleState

    val animatedBottomNavOffset by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isBottomNavVisible) 0f else -bottomNavHeightPx * 2,
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = 300,
            easing = androidx.compose.animation.core.FastOutSlowInEasing
        ),
        label = "bottomNavOffset"
    )

    LaunchedEffect(animatedBottomNavOffset) {
        bottomNavOffsetHeightPx.floatValue = animatedBottomNavOffset
    }

    CompositionLocalProvider(
        LocalDrawerOpener provides { drawerScope.launch { drawerState.open() } },
        LocalTvMode provides isTv,
        LocalAdaptiveInfo provides adaptiveInfo,
        LocalJellyPlayUi provides uiEnvironment,
        LocalTvTypography provides tvTypography,
        LocalPerformanceMode provides preferences.performanceMode,
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
                com.raulshma.jellyplay.core.ui.components.LocalFloatingNavOffset provides (if (!isExpanded && !isFullScreenRoute) bottomNavOffsetHeightPx.floatValue else 0f),
                com.raulshma.jellyplay.feature.search.LocalPendingSearchQuery provides pendingSearchQuery,
                com.raulshma.jellyplay.feature.search.LocalConsumeSearchQuery provides { viewModel.consumePendingSearchQuery() },
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
            // TvNavigationDrawer leaves and re-enters composition. Fully-qualified to avoid clashing with the mobile
            // androidx.compose.material3 DrawerState used by LocalDrawerOpener below.
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
                    enterPip = enterPip,
                    enterVideoMiniMode = enterVideoMiniMode,
                    saveableStateHolder = saveableStateHolder,
                    entryDecorator = entryDecorator,
                    onNowPlayingClick = onNowPlayingClick,
                    onAmbientClick = onAmbientClick,
                    tvDrawerState = tvDrawerState,
                    tvDrawerListState = tvDrawerListState,
                    libraryFolders = libraryFolders,
                    nowPlayingTitle = audioTitle.takeIf { audioItemId != null },
                    nowPlayingEnabled = audioItemId != null,
                )
            } else {
                if (!isFullScreenRoute) {
                    // Wire the system/gesture back button to in-app navigation so back
                    // from a deep screen returns to the tab root before exiting the app
                    // (issue #62-I). At a tab root, fall through to the OS (exit). The
                    // full-screen player is excluded — it owns its own BackHandler.
                    BackHandler(enabled = !navigator.isAtTabRoot()) {
                        navigator.goBack()
                    }
                    PhoneContent(
                        navigationState = navigationState,
                        currentTopLevel = currentTopLevel,
                        activeTopLevelRoutes = activeTopLevelRoutes,
                        navigator = navigator,
                        onLogout = onLogout,
                        homeMode = homeMode,
                        onModeChange = onModeChange,
                        enterPip = enterPip,
                        enterVideoMiniMode = enterVideoMiniMode,
                        saveableStateHolder = saveableStateHolder,
                        entryDecorator = entryDecorator,
                        onNowPlayingClick = onNowPlayingClick,
                        onAmbientClick = onAmbientClick,
                        drawerState = drawerState,
                        drawerScope = drawerScope,
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
                        onDismissMiniPlayer = { isMiniPlayerDismissed = true },
                        isVideoMiniMode = isVideoMiniMode,
                        videoMiniPlayerState = videoMiniPlayerState,
                        videoMiniTitle = videoMiniTitle,
                        videoMiniSubtitle = videoMiniSubtitle,
                        videoMiniIsPlaying = videoMiniIsPlaying,
                        videoMiniItemId = videoMiniItemId,
                        animatedNavBarColor = animatedNavBarColor,
                        showNavBarLabels = preferences.navBarShowLabels,
                    )
                } else {
                    FullScreenContent(
                        navigationState = navigationState,
                        navigator = navigator,
                        onLogout = onLogout,
                        homeMode = homeMode,
                        onModeChange = onModeChange,
                        enterPip = enterPip,
                        enterVideoMiniMode = enterVideoMiniMode,
                        saveableStateHolder = saveableStateHolder,
                        entryDecorator = entryDecorator,
                        onNowPlayingClick = onNowPlayingClick,
                        onAmbientClick = onAmbientClick,
                        isVideoMiniMode = isVideoMiniMode,
                        videoMiniPlayerState = videoMiniPlayerState,
                        videoMiniTitle = videoMiniTitle,
                        videoMiniSubtitle = videoMiniSubtitle,
                        videoMiniIsPlaying = videoMiniIsPlaying,
                        videoMiniItemId = videoMiniItemId,
                    )
                }
                }
            } // end inner blur Box
                androidx.compose.material3.SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(androidx.compose.ui.Alignment.BottomCenter)
                        .padding(bottom = if (isFullScreenRoute) 16.dp else 96.dp)
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
    enterPip: () -> Unit,
    enterVideoMiniMode: () -> Unit,
    saveableStateHolder: androidx.compose.runtime.saveable.SaveableStateHolder,
    entryDecorator: NavEntryDecorator<NavKey>,
    onNowPlayingClick: () -> Unit,
    onAmbientClick: () -> Unit,
    tvDrawerState: androidx.tv.material3.DrawerState,
    tvDrawerListState: androidx.compose.foundation.lazy.LazyListState,
    libraryFolders: List<com.raulshma.jellyplay.core.model.LibraryFolder>,
    nowPlayingTitle: String?,
    nowPlayingEnabled: Boolean,
) {
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

            val primaryNavItems = activeTopLevelRoutes.entries.map { (route, label) ->
                TvNavItem(
                    route = route,
                    label = label,
                    icon = routeToIcon(route),
                )
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
                MainNavDisplay(
                    navigationState = navigationState,
                    navigator = navigator,
                    onLogout = onLogout,
                    homeMode = homeMode,
                    onModeChange = onModeChange,
                    enterPip = enterPip,
                    enterVideoMiniMode = enterVideoMiniMode,
                    saveableStateHolder = saveableStateHolder,
                    entryDecorator = entryDecorator,
                    onNowPlayingClick = onNowPlayingClick,
                    onAmbientClick = onAmbientClick,
                )
            }
        }
    }
}

/**
 * Phone (and large-screen NavigationRail) layout: hamburger [ModalNavigationDrawer] +
 * [NavigationSuiteScaffold] hosting [MainNavDisplay] with floating mini-player(s) and the
 * optional [FloatingNavigationBar]. All scroll-coupled bottom-nav state is owned here.
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
    enterPip: () -> Unit,
    enterVideoMiniMode: () -> Unit,
    saveableStateHolder: androidx.compose.runtime.saveable.SaveableStateHolder,
    entryDecorator: NavEntryDecorator<NavKey>,
    onNowPlayingClick: () -> Unit,
    onAmbientClick: () -> Unit,
    drawerState: androidx.compose.material3.DrawerState,
    drawerScope: kotlinx.coroutines.CoroutineScope,
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
    onDismissMiniPlayer: () -> Unit,
    isVideoMiniMode: Boolean,
    videoMiniPlayerState: com.raulshma.jellyplay.core.data.playback.VideoMiniPlayerState,
    videoMiniTitle: String,
    videoMiniSubtitle: String,
    videoMiniIsPlaying: Boolean,
    videoMiniItemId: String?,
    animatedNavBarColor: Color,
    showNavBarLabels: Boolean,
) {
    val systemNavBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var isBottomNavVisible by isBottomNavVisibleState

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(vertical = 48.dp),
                ) {
                    Text(
                        "JellyPlay",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    DrawerItem(
                        icon = Tabler.Outline.Inbox,
                        label = "Requests",
                        onClick = {
                            navigator.navigate(Route.Requests)
                            drawerScope.launch { drawerState.close() }
                        },
                    )
                    DrawerItem(
                        icon = Tabler.Outline.Settings,
                        label = "Settings",
                        onClick = {
                            navigator.navigate(Route.Settings)
                            drawerScope.launch { drawerState.close() }
                        },
                    )
                    DrawerItem(
                        icon = Tabler.Outline.InfoCircle,
                        label = "About",
                        onClick = {
                            navigator.navigate(Route.About)
                            drawerScope.launch { drawerState.close() }
                        },
                    )
                }
            }
        },
    ) {
        // When hide-on-scroll is disabled, keep the nav bar permanently visible
        // (issue #62-I). The nestedScrollConnection is still constructed so its
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
                    NavigationSuiteItem(
                        selected = route == currentTopLevel,
                        onClick = { navigator.navigate(route) },
                        icon = { NavIcon(route, label, selected = route == currentTopLevel) },
                        label = { Text(label) },
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
                        enterPip = enterPip,
                        enterVideoMiniMode = enterVideoMiniMode,
                        saveableStateHolder = saveableStateHolder,
                        entryDecorator = entryDecorator,
                        onNowPlayingClick = onNowPlayingClick,
                        onAmbientClick = onAmbientClick,
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
                                val maxOffset = 60.dp.toPx()
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
                }
                if (isVideoMiniMode) {
                    VideoMiniPlayer(
                        isVisible = true,
                        // The host app knows the engine is a video `MediaEngine` even though
                        // the cross-feature holder types it as the more general
                        // `RemotePlayableEngine`. Cast is safe because only the video
                        // engine ever enters mini mode.
                        engine = videoMiniPlayerState.engine as? com.raulshma.jellyplay.feature.player.video.engine.MediaEngine,
                        title = videoMiniTitle,
                        subtitle = videoMiniSubtitle,
                        isPlaying = videoMiniIsPlaying,
                        onClick = {
                            val itemId = videoMiniItemId ?: return@VideoMiniPlayer
                            navigator.navigate(Route.VideoPlayer(itemId))
                        },
                        onClose = {
                            videoMiniPlayerState.release()
                        },
                        onPlayPause = {
                            videoMiniPlayerState.togglePlayPause()
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 8.dp, bottom = systemNavBarBottom + (if (!isExpanded) 64.dp else 8.dp))
                            .fillMaxWidth(0.45f)
                            .offset {
                                if (!isExpanded) {
                                    val maxOffset = com.raulshma.jellyplay.core.designsystem.theme.Dimensions.floatingNavHeight.toPx()
                                    val yOffset = (-bottomNavOffsetHeightPx.floatValue).coerceAtMost(maxOffset)
                                    IntOffset(x = 0, y = yOffset.roundToInt())
                                } else {
                                    IntOffset.Zero
                                }
                            },
                    )
                }
                if (!isExpanded) {
                    FloatingNavigationBar(
                        routes = activeTopLevelRoutes,
                        currentTopLevel = currentTopLevel,
                        onNavigate = { navigator.navigate(it) },
                        showLabels = showNavBarLabels,
                        containerColor = animatedNavBarColor,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = systemNavBarBottom + 4.dp)
                            .padding(horizontal = 16.dp)
                            .offset { IntOffset(x = 0, y = -bottomNavOffsetHeightPx.floatValue.roundToInt()) }
                    )
                }
            }
        }
    }
}

/**
 * Full-screen layout (player / onboarding / ambient / photo viewer): bare [Box] with
 * [MainNavDisplay] and an optional picture-in-picture [VideoMiniPlayer]. Deliberately
 * omits drawer / nav-bar / mini-player chrome.
 */
@Composable
private fun FullScreenContent(
    navigationState: com.raulshma.jellyplay.core.ui.navigation.NavigationState,
    navigator: Navigator,
    onLogout: (Boolean) -> Unit,
    homeMode: HomeMode,
    onModeChange: (HomeMode) -> Unit,
    enterPip: () -> Unit,
    enterVideoMiniMode: () -> Unit,
    saveableStateHolder: androidx.compose.runtime.saveable.SaveableStateHolder,
    entryDecorator: NavEntryDecorator<NavKey>,
    onNowPlayingClick: () -> Unit,
    onAmbientClick: () -> Unit,
    isVideoMiniMode: Boolean,
    videoMiniPlayerState: com.raulshma.jellyplay.core.data.playback.VideoMiniPlayerState,
    videoMiniTitle: String,
    videoMiniSubtitle: String,
    videoMiniIsPlaying: Boolean,
    videoMiniItemId: String?,
) {
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
            enterPip = enterPip,
            enterVideoMiniMode = enterVideoMiniMode,
            saveableStateHolder = saveableStateHolder,
            entryDecorator = entryDecorator,
            onNowPlayingClick = onNowPlayingClick,
            onAmbientClick = onAmbientClick,
        )
        if (isVideoMiniMode) {
            VideoMiniPlayer(
                isVisible = true,
                engine = videoMiniPlayerState.engine as? com.raulshma.jellyplay.feature.player.video.engine.MediaEngine,
                title = videoMiniTitle,
                subtitle = videoMiniSubtitle,
                isPlaying = videoMiniIsPlaying,
                onClick = {
                    val itemId = videoMiniItemId ?: return@VideoMiniPlayer
                    navigator.navigate(Route.VideoPlayer(itemId))
                },
                onClose = {
                    videoMiniPlayerState.release()
                },
                onPlayPause = {
                    videoMiniPlayerState.togglePlayPause()
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 8.dp, bottom = 8.dp)
                    .fillMaxWidth(0.5f),
            )
        }
    }
}

/**
 * Resolved fields needed to build an [com.raulshma.jellyplay.ExternalPlayerLaunch]
 * from a navigable video route (regular video or Live TV channel). Used by the
 * `navigateFilter` to uniformly hand off to the app-level external-player
     * ActivityResultLauncher.
 */
private data class ExternalLaunchTarget(
    val itemId: String,
    val mediaSourceId: String?,
    val startPositionTicks: Long,
)

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
private fun NavIcon(route: Route, label: String, selected: Boolean = false, tint: Color = androidx.compose.material3.LocalContentColor.current) {
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (selected) 1.15f else 1.0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "iconScale",
    )
    Icon(
        imageVector = routeToIcon(route),
        contentDescription = label,
        tint = tint,
        modifier = androidx.compose.ui.Modifier.scale(scale),
    )
}

@Composable
private fun MainNavDisplay(
    navigationState: com.raulshma.jellyplay.core.ui.navigation.NavigationState,
    navigator: Navigator,
    onLogout: (Boolean) -> Unit,
    homeMode: HomeMode,
    onModeChange: (HomeMode) -> Unit,
    enterPip: () -> Unit,
    enterVideoMiniMode: () -> Unit = {},
    innerPadding: PaddingValues = PaddingValues(0.dp),
    saveableStateHolder: androidx.compose.runtime.saveable.SaveableStateHolder,
    entryDecorator: NavEntryDecorator<NavKey>,
    modifier: Modifier = Modifier,
    onNowPlayingClick: () -> Unit = {},
    onAmbientClick: () -> Unit = {},
) {
    val currentBackStack = navigationState.backStacks[navigationState.topLevelRoute.value] ?: return

    val paddingDecorator = remember(innerPadding) {
        NavEntryDecorator<NavKey>(
            decorate = { entry ->
                val contentKey = entry.contentKey.toString()
                val isPlayer = contentKey.contains("AudioPlayer") ||
                        contentKey.contains("VideoPlayer") ||
                        contentKey.contains("LiveTvChannelPlayer") ||
                        contentKey.contains("Ambient")

                if (isPlayer) {
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

    val motionScheme = MaterialTheme.motionScheme
    val defaultEffects = motionScheme.defaultEffectsSpec<Float>()
    val fastEffects = motionScheme.fastEffectsSpec<Float>()
    val defaultSpatial = motionScheme.defaultSpatialSpec<Float>()
    val defaultSpatialOffset = motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.IntOffset>()

    val sharedEntryProvider = entryProvider {
        homeSection(
            navigator = navigator,
            homeMode = homeMode,
            onModeChange = onModeChange,
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
        videoPlayerSection(navigator, onEnterPip = enterPip, onEnterMiniMode = enterVideoMiniMode)
        audioPlayerSection(navigator)
        downloadsSection(navigator)
        authSection(navigator) { navigator.goBack() }
        settingsSection(navigator, onLogout) { navigator.navigate(Route.Onboarding) }
        adminSection(navigator)
        musicSection(navigator)
        syncPlaySection(navigator)
        onboardingSection { navigator.goBack() }
        newsletterSection(navigator)
        insightsSection(navigator)
        requestsSection(navigator)
        shortcutsSection(navigator)
    }

    NavDisplay(
        backStack = currentBackStack,
        onBack = { navigator.goBack() },
        entryDecorators = listOf(entryDecorator, paddingDecorator),
        transitionSpec = {
            val targetLast = targetState
            val initialLast = initialState
            val targetRoute = targetLast.entries.lastOrNull()?.contentKey as? Route
            val initialRoute = initialLast.entries.lastOrNull()?.contentKey as? Route
            val isModalRoute = targetRoute?.isModal == true
            val isModalPop = initialRoute?.isModal == true
            val isTabSwitch = targetRoute != null && initialRoute != null &&
                    ALL_TOP_LEVEL_ROUTE_KEYS.contains(targetRoute) &&
                    ALL_TOP_LEVEL_ROUTE_KEYS.contains(initialRoute)
            val isAmbient = targetRoute is Route.Ambient || initialRoute is Route.Ambient

            when {
                isAmbient -> {
                    fadeIn(defaultEffects) togetherWith fadeOut(fastEffects)
                }
                isModalRoute -> {
                    fadeIn(
                        defaultEffects
                    ) + slideInVertically(
                        initialOffsetY = { it / 4 },
                        animationSpec = defaultSpatialOffset,
                    ) togetherWith fadeOut(
                        fastEffects
                    )
                }
                isModalPop -> {
                    fadeIn(fastEffects) togetherWith fadeOut(
                        fastEffects
                    ) + slideOutVertically(
                        targetOffsetY = { it / 4 },
                        animationSpec = defaultSpatialOffset,
                    )
                }
                isTabSwitch -> {
                    fadeIn(fastEffects) togetherWith fadeOut(
                        fastEffects
                    )
                }
                isDetailScene(targetLast) || isDetailScene(initialLast) -> {
                    fadeIn(
                        animationSpec = defaultEffects,
                    ) togetherWith fadeOut(
                        animationSpec = fastEffects,
                    )
                }
                else -> {
                    fadeIn(
                        animationSpec = defaultEffects,
                    ) + slideInHorizontally(
                        initialOffsetX = { it / 8 },
                        animationSpec = defaultSpatialOffset,
                    ) + scaleIn(
                        initialScale = 0.985f,
                        animationSpec = defaultSpatial,
                    ) togetherWith fadeOut(
                        animationSpec = fastEffects,
                    ) + slideOutHorizontally(
                        targetOffsetX = { -it / 18 },
                        animationSpec = defaultSpatialOffset,
                    ) + scaleOut(
                        targetScale = 1.015f,
                        animationSpec = defaultEffects,
                    )
                }
            }
        },
        popTransitionSpec = {
            val targetLast = targetState
            val initialLast = initialState
            val initialRoute = initialLast.entries.lastOrNull()?.contentKey as? Route
            val isModalPop = initialRoute?.isModal == true
            when {
                isModalPop -> {
                    fadeIn(fastEffects) togetherWith fadeOut(
                        fastEffects
                    ) + slideOutVertically(
                            targetOffsetY = { it / 4 },
                            animationSpec = defaultSpatialOffset,
                        )
                }
                isDetailScene(initialLast) || isDetailScene(targetLast) -> {
                    fadeIn(
                        animationSpec = defaultEffects,
                    ) togetherWith fadeOut(
                        animationSpec = defaultEffects,
                    )
                }
                else -> {
                    fadeIn(
                            animationSpec = defaultEffects,
                        ) + slideInHorizontally(
                            initialOffsetX = { -it / 12 },
                            animationSpec = defaultSpatialOffset,
                        ) + scaleIn(
                            initialScale = 1.015f,
                            animationSpec = defaultSpatial,
                        ) togetherWith fadeOut(
                            animationSpec = fastEffects,
                        ) + slideOutHorizontally(
                            targetOffsetX = { it / 10 },
                            animationSpec = defaultSpatialOffset,
                        ) + scaleOut(
                            targetScale = 0.985f,
                            animationSpec = defaultEffects,
                        )
                }
            }
        },
        predictivePopTransitionSpec = { _ ->
            val targetLast = targetState
            val initialLast = initialState
            if (isDetailScene(initialLast) || isDetailScene(targetLast)) {
                fadeIn(
                    animationSpec = defaultEffects,
                ) togetherWith fadeOut(
                    animationSpec = defaultEffects,
                )
            } else {
                fadeIn(
                    animationSpec = defaultEffects,
                ) + slideInHorizontally(
                    initialOffsetX = { -it / 12 },
                    animationSpec = defaultSpatialOffset,
                ) + scaleIn(
                    initialScale = 1.015f,
                    animationSpec = defaultSpatial,
                ) togetherWith fadeOut(
                    animationSpec = fastEffects,
                ) + slideOutHorizontally(
                    targetOffsetX = { it / 10 },
                    animationSpec = defaultSpatialOffset,
                ) + scaleOut(
                    targetScale = 0.985f,
                    animationSpec = defaultEffects,
                )
            }
        },
        entryProvider = sharedEntryProvider,
        modifier = modifier,
    )
}

@Composable
private fun FloatingNavigationBar(
    routes: Map<Route, String>,
    currentTopLevel: NavKey,
    onNavigate: (Route) -> Unit,
    showLabels: Boolean,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(percent = 50),
        color = containerColor.copy(alpha = 0.65f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .animateContentSize(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec())
                .padding(horizontal = 28.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            routes.forEach { (route, label) ->
                val selected = route == currentTopLevel
                val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                Row(
                    modifier = Modifier
                        .animateContentSize(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec())
                        .focusIndicator(androidx.compose.foundation.shape.CircleShape)
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null,
                            onClick = { onNavigate(route) }
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    NavIcon(route, label, selected = selected, tint = tint)
                    if (selected && showLabels) {
                        Text(
                            text = label,
                            color = tint,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ShapeCache.smooth12)
            .focusIndicator()
            .clickable { onClick() }
            .padding(horizontal = 28.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = label,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(16.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
