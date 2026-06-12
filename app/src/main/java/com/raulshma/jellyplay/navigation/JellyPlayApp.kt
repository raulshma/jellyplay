package com.raulshma.jellyplay.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
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
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.WindowSizeClass
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
import com.raulshma.jellyplay.core.ui.components.LocalPerformanceMode
import com.raulshma.jellyplay.core.ui.components.LocalFloatingNavVisibility
import com.raulshma.jellyplay.core.ui.navigation.ALL_TOP_LEVEL_ROUTE_KEYS
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
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

private val DETAIL_ROUTE_CLASS_NAMES: Set<String> = setOf(
    "MediaDetail",
    "MetadataEditor",
    "SeerrDetail",
    "PersonDetail",
    "MediaInfo",
    "CollectionDetail",
    "OfflineSeries",
    "ArtistDetail",
    "AlbumDetail",
    "SmartPlaylistDetail",
    "MoodPlaylistDetail",
    "PlaylistDetail",
    "GenreDetail",
    "NewsletterSectionList",
    "UserStatisticsDetail",
)

private fun isDetailRoute(route: Route): Boolean = when (route) {
    is Route.MediaDetail,
    is Route.MetadataEditor,
    is Route.SeerrDetail,
    is Route.PersonDetail,
    is Route.MediaInfo,
    is Route.CollectionDetail,
    is Route.OfflineSeries,
    is Route.ArtistDetail,
    is Route.AlbumDetail,
    is Route.SmartPlaylistDetail,
    is Route.MoodPlaylistDetail,
    is Route.PlaylistDetail,
    is Route.GenreDetail,
    is Route.NewsletterSectionList,
    is Route.UserStatisticsDetail -> true
    else -> false
}

private fun isDetailScene(scene: Scene<NavKey>): Boolean {
    val className = scene.entries.lastOrNull()?.contentKey?.toString()?.substringBefore('(')
        ?: return false
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
            ) {
                MainContent(
                    onLogout = { viewModel.logout() },
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
    onLogout: () -> Unit,
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
    val navigator = Navigator(navigationState, navigateFilter = { route ->
        if (route is Route.VideoPlayer && preferences.preferredPlayer == com.raulshma.jellyplay.core.model.PlayerType.EXTERNAL) {
            scope.launch { viewModel.launchExternalPlayer(route, context) }
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
            currentRoute is Route.Ambient || currentRoute is Route.Onboarding

    val activeTopLevelRoutes: LinkedHashMap<Route, String> = when (homeMode) {
        HomeMode.VIDEO -> VIDEO_TOP_LEVEL_ROUTES
        HomeMode.MUSIC -> MUSIC_TOP_LEVEL_ROUTES
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
                                last is Route.LiveTvChannelPlayer ||
                                last is Route.OfflinePlayer
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

    val snackbarHostState = androidx.compose.material3.SnackbarHostState()
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

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()

    val adaptiveInfo = rememberAdaptiveInfo()

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
        LocalTvTypography provides tvTypography,
        LocalPerformanceMode provides preferences.performanceMode,
        LocalFloatingNavVisibility provides isBottomNavVisibleState,
    ) {
        val isExpanded = adaptiveInfo.windowSizeClass != WindowSizeClass.Compact

        @OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
        androidx.compose.animation.SharedTransitionLayout {
            CompositionLocalProvider(
                com.raulshma.jellyplay.core.ui.components.LocalSharedTransitionScope provides if (preferences.performanceMode) null else this,
                LocalNavigationBarColor provides navBarColorState,
                com.raulshma.jellyplay.core.ui.components.LocalFloatingNavOffset provides (if (!isExpanded && !isFullScreenRoute) bottomNavOffsetHeightPx.floatValue else 0f)
            ) {
            Box(Modifier.fillMaxSize()) {
            if (isTv && !isFullScreenRoute) {
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
                    TvMainLayout(
                        isExpanded = true,
                        isPlayerScreen = false,
                        animatedNavBarColor = animatedNavBarColor,
                        activeTopLevelRoutes = activeTopLevelRoutes,
                        currentTopLevel = currentTopLevel,
                        navigator = navigator,
                        navigationState = navigationState,
                        onLogout = onLogout,
                        homeMode = homeMode,
                        onModeChange = onModeChange,
                        enterPip = enterPip,
                        enterVideoMiniMode = enterVideoMiniMode,
                        onBack = { navigator.goBack() },
                        onNowPlayingClick = {
                            val itemId = audioItemId ?: return@TvMainLayout
                            navigator.navigate(Route.AudioPlayer(itemId))
                        },
                        onAmbientClick = {
                            navigator.navigate(
                                Route.Ambient(
                                    imageUrl = audioArtworkUrl,
                                    title = audioTitle,
                                    artist = audioArtist,
                                )
                            )
                        },
                    )
                }
                }
            } else {
                val systemNavBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                val contentPadding = PaddingValues(0.dp)
                if (!isFullScreenRoute) {
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
                        val appBackgroundModifier = if (isSynthwave) {
                            Modifier.background(
                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                    colors = listOf(Color(0xFF0D061A), Color(0xFF1B0B3A))
                                )
                            )
                        } else {
                            Modifier.background(MaterialTheme.colorScheme.background)
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(appBackgroundModifier)
                                .then(if (!isExpanded) Modifier.nestedScroll(nestedScrollConnection) else Modifier)
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
                                    innerPadding = contentPadding,
                                    onNowPlayingClick = {
                                        val itemId = audioItemId ?: return@MainNavDisplay
                                        navigator.navigate(Route.AudioPlayer(itemId))
                                    },
                                    onAmbientClick = {
                                        navigator.navigate(
                                            Route.Ambient(
                                                imageUrl = audioArtworkUrl,
                                                title = audioTitle,
                                                artist = audioArtist,
                                            )
                                        )
                                    },
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
                                        onClick = {
                                            val itemId = audioItemId ?: return@MiniPlayer
                                            navigator.navigate(Route.AudioPlayer(itemId))
                                        },
                                        onClose = {
                                            audioPlaybackManager.stopAndRelease()
                                            isMiniPlayerDismissed = true
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
                                        onClick = {
                                            val itemId = audioItemId ?: return@MiniPlayer
                                            navigator.navigate(Route.AudioPlayer(itemId))
                                        },
                                        onClose = {
                                            audioPlaybackManager.stopAndRelease()
                                            isMiniPlayerDismissed = true
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
                                    engine = videoMiniPlayerState.engine,
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
                                                val maxOffset = 64.dp.toPx()
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
                                    showLabels = preferences.navBarShowLabels,
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
                } else {
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
                            innerPadding = contentPadding,
                            onNowPlayingClick = {
                                val itemId = audioItemId ?: return@MainNavDisplay
                                navigator.navigate(Route.AudioPlayer(itemId))
                            },
                            onAmbientClick = {
                                navigator.navigate(
                                    Route.Ambient(
                                        imageUrl = audioArtworkUrl,
                                        title = audioTitle,
                                        artist = audioArtist,
                                    )
                                )
                            },
                        )
                        if (isVideoMiniMode) {
                            VideoMiniPlayer(
                                isVisible = true,
                                engine = videoMiniPlayerState.engine,
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
                }
                androidx.compose.material3.SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(androidx.compose.ui.Alignment.BottomCenter)
                        .padding(bottom = if (isFullScreenRoute) 16.dp else 96.dp)
                )
            }
            }
        }
    }
}

@Composable
private fun TvMainLayout(
    isExpanded: Boolean,
    isPlayerScreen: Boolean,
    animatedNavBarColor: Color,
    activeTopLevelRoutes: LinkedHashMap<Route, String>,
    currentTopLevel: androidx.navigation3.runtime.NavKey,
    navigator: Navigator,
    navigationState: com.raulshma.jellyplay.core.ui.navigation.NavigationState,
    onLogout: () -> Unit,
    homeMode: HomeMode,
    onModeChange: (HomeMode) -> Unit,
    enterPip: () -> Unit,
    enterVideoMiniMode: () -> Unit,
    onBack: () -> Unit,
    onNowPlayingClick: () -> Unit = {},
    onAmbientClick: () -> Unit = {},
) {
    val contentFocusRequester = remember { FocusRequester() }
    val railFocusRequesters = remember(activeTopLevelRoutes.size) {
        List(activeTopLevelRoutes.size) { FocusRequester() }
    }
    var focusedRailIndex by remember { mutableStateOf(0) }

    val drawerState = androidx.tv.material3.rememberDrawerState(
        initialValue = androidx.tv.material3.DrawerValue.Closed
    )
    val currentRoute = navigationState.backStacks[currentTopLevel]?.lastOrNull()

    NavigationDrawer(
        drawerState = drawerState,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .onKeyEvent { keyEvent ->
                if (keyEvent.key == Key.Back && keyEvent.type == KeyEventType.KeyUp) {
                    onBack()
                    true
                } else false
            },
        drawerContent = { drawerValue ->
            val isClosed = drawerValue == androidx.tv.material3.DrawerValue.Closed
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(if (isClosed) 72.dp else 280.dp)
                    .padding(
                        start = 24.dp,
                        end = if (isClosed) 24.dp else 16.dp,
                        top = 8.dp,
                        bottom = 8.dp
                    )
                    .verticalScroll(rememberScrollState())
                    .focusRestorer()
                    .selectableGroup(),
            ) {
                    activeTopLevelRoutes.entries.toList().forEachIndexed { index, (route, label) ->
                        val isSelected = route == currentTopLevel
                        NavigationDrawerItem(
                            selected = isSelected,
                            onClick = {
                                navigator.navigate(route)
                                focusedRailIndex = index
                                drawerState.setValue(androidx.tv.material3.DrawerValue.Closed)
                            },
                            leadingContent = {
                                NavIcon(route, label, tint = TvLocalContentColor.current)
                            },
                            content = {
                                TvText(
                                    label,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            },
                            modifier = Modifier
                                .focusRequester(railFocusRequesters[index])
                                .onFocusChanged {
                                    if (it.isFocused || it.hasFocus) {
                                        focusedRailIndex = index
                                    }
                                },
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    )

                    NavigationDrawerItem(
                        selected = false,
                        onClick = {
                            drawerState.setValue(androidx.tv.material3.DrawerValue.Closed)
                            navigator.navigate(Route.Downloads)
                        },
                        leadingContent = {
                            TvIcon(Tabler.Outline.Download, contentDescription = null)
                        },
                        content = {
                            TvText("Downloads", style = MaterialTheme.typography.labelMedium)
                        },
                    )

                    NavigationDrawerItem(
                        selected = false,
                        onClick = {
                            drawerState.setValue(androidx.tv.material3.DrawerValue.Closed)
                            navigator.navigate(Route.Favorites)
                        },
                        leadingContent = {
                            TvIcon(Tabler.Outline.Heart, contentDescription = null)
                        },
                        content = {
                            TvText("Favorites", style = MaterialTheme.typography.labelMedium)
                        },
                    )

                    NavigationDrawerItem(
                        selected = false,
                        onClick = {
                            drawerState.setValue(androidx.tv.material3.DrawerValue.Closed)
                            navigator.navigate(Route.SyncPlay)
                        },
                        leadingContent = {
                            TvIcon(Tabler.Outline.Users, contentDescription = null)
                        },
                        content = {
                            TvText("SyncPlay", style = MaterialTheme.typography.labelMedium)
                        },
                    )

                    NavigationDrawerItem(
                        selected = false,
                        onClick = {
                            drawerState.setValue(androidx.tv.material3.DrawerValue.Closed)
                            navigator.navigate(Route.WatchProgressHeatmap)
                        },
                        leadingContent = {
                            TvIcon(Tabler.Outline.ChartBar, contentDescription = null)
                        },
                        content = {
                            TvText("Watch History", style = MaterialTheme.typography.labelMedium)
                        },
                    )

                    NavigationDrawerItem(
                        selected = false,
                        onClick = {
                            drawerState.setValue(androidx.tv.material3.DrawerValue.Closed)
                            navigator.navigate(Route.Requests)
                        },
                        leadingContent = {
                            TvIcon(Tabler.Outline.Inbox, contentDescription = null)
                        },
                        content = {
                            TvText("Requests", style = MaterialTheme.typography.labelMedium)
                        },
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    )

                    NavigationDrawerItem(
                        selected = false,
                        onClick = {
                            drawerState.setValue(androidx.tv.material3.DrawerValue.Closed)
                            navigator.navigate(Route.ServerManagement())
                        },
                        leadingContent = {
                            TvIcon(Tabler.Outline.Server, contentDescription = null)
                        },
                        content = {
                            TvText("Server Mgmt", style = MaterialTheme.typography.labelMedium)
                        },
                    )

                    NavigationDrawerItem(
                        selected = false,
                        onClick = {
                            drawerState.setValue(androidx.tv.material3.DrawerValue.Closed)
                            navigator.navigate(Route.UserManagement())
                        },
                        leadingContent = {
                            TvIcon(Tabler.Outline.User, contentDescription = null)
                        },
                        content = {
                            TvText("Switch User", style = MaterialTheme.typography.labelMedium)
                        },
                    )

                    NavigationDrawerItem(
                        selected = false,
                        onClick = {
                            drawerState.setValue(androidx.tv.material3.DrawerValue.Closed)
                            navigator.navigate(Route.AdminDashboard)
                        },
                        leadingContent = {
                            TvIcon(Tabler.Outline.Shield, contentDescription = null)
                        },
                        content = {
                            TvText("Admin", style = MaterialTheme.typography.labelMedium)
                        },
                    )

                    NavigationDrawerItem(
                        selected = false,
                        onClick = {
                            drawerState.setValue(androidx.tv.material3.DrawerValue.Closed)
                            navigator.navigate(Route.SeerrSettings())
                        },
                        leadingContent = {
                            TvIcon(Tabler.Outline.Puzzle, contentDescription = null)
                        },
                        content = {
                            TvText("Seerr", style = MaterialTheme.typography.labelMedium)
                        },
                    )

                    NavigationDrawerItem(
                        selected = false,
                        onClick = {
                            drawerState.setValue(androidx.tv.material3.DrawerValue.Closed)
                            navigator.navigate(Route.Settings)
                        },
                        leadingContent = {
                            TvIcon(Tabler.Outline.Settings, contentDescription = null)
                        },
                        content = {
                            TvText("Settings", style = MaterialTheme.typography.labelMedium)
                        },
                    )

                    NavigationDrawerItem(
                        selected = false,
                        onClick = {
                            drawerState.setValue(androidx.tv.material3.DrawerValue.Closed)
                            navigator.navigate(Route.Onboarding)
                        },
                        leadingContent = {
                            TvIcon(Tabler.Outline.Wand, contentDescription = null)
                        },
                        content = {
                            TvText("Setup Wizard", style = MaterialTheme.typography.labelMedium)
                        },
                    )

                    NavigationDrawerItem(
                        selected = false,
                        onClick = {
                            drawerState.setValue(androidx.tv.material3.DrawerValue.Closed)
                            navigator.navigate(Route.About)
                        },
                        leadingContent = {
                            TvIcon(Tabler.Outline.InfoCircle, contentDescription = null)
                        },
                        content = {
                            TvText("About", style = MaterialTheme.typography.labelMedium)
                        },
                    )
                }
            },
            content = {
                MainNavDisplay(
                    navigationState = navigationState,
                    navigator = navigator,
                    onLogout = onLogout,
                    homeMode = homeMode,
                    onModeChange = onModeChange,
                    enterPip = enterPip,
                    enterVideoMiniMode = enterVideoMiniMode,
                    modifier = Modifier
                        .focusRequester(contentFocusRequester)
                        .tvFocusRestorer(),
                    onNowPlayingClick = onNowPlayingClick,
                    onAmbientClick = onAmbientClick,
                )
            },
        )

    LaunchedEffect(currentRoute) {
        val initialIndex = activeTopLevelRoutes.keys.indexOf(currentTopLevel).coerceAtLeast(0)
        focusedRailIndex = initialIndex
        drawerState.setValue(androidx.tv.material3.DrawerValue.Closed)
        try { contentFocusRequester.requestFocus() } catch (_: Exception) { }
    }
}

@Composable
private fun NavIcon(route: Route, label: String, selected: Boolean = false, tint: Color = androidx.compose.material3.LocalContentColor.current) {
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (selected) 1.15f else 1.0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "iconScale"
    )
    
    val icon = when (route) {
        Route.Home -> Tabler.Outline.Home
        Route.Library -> Tabler.Outline.Music
        Route.Search -> Tabler.Outline.Search
        Route.LiveTv -> Tabler.Outline.DeviceTv
        Route.MusicBrowse -> Tabler.Outline.Disc
        Route.Shortcuts -> Tabler.Outline.Apps
        else -> Tabler.Outline.Home // Fallback
    }

    Icon(
        imageVector = icon,
        contentDescription = label,
        tint = tint,
        modifier = androidx.compose.ui.Modifier.scale(scale)
    )
}

@Composable
private fun MainNavDisplay(
    navigationState: com.raulshma.jellyplay.core.ui.navigation.NavigationState,
    navigator: Navigator,
    onLogout: () -> Unit,
    homeMode: HomeMode,
    onModeChange: (HomeMode) -> Unit,
    enterPip: () -> Unit,
    enterVideoMiniMode: () -> Unit = {},
    innerPadding: PaddingValues = PaddingValues(0.dp),
    modifier: Modifier = Modifier,
    onNowPlayingClick: () -> Unit = {},
    onAmbientClick: () -> Unit = {},
) {
    val currentBackStack = navigationState.backStacks[navigationState.topLevelRoute.value] ?: return

    val saveableStateHolder = rememberSaveableStateHolder()
    val entryDecorator = rememberSaveableStateHolderNavEntryDecorator<NavKey>(saveableStateHolder)

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

    NavDisplay(
        backStack = currentBackStack,
        onBack = { navigator.goBack() },
                entryDecorators = listOf(entryDecorator, paddingDecorator),
                transitionSpec = {
                    val targetLast = targetState
                    val initialLast = initialState
                    val targetRoute = targetLast.entries.lastOrNull()?.contentKey as? Route
                    val initialRoute = initialLast.entries.lastOrNull()?.contentKey as? Route
                    val isModalRoute = targetRoute == Route.Settings ||
                            targetRoute == Route.Downloads ||
                            targetRoute == Route.SyncPlay ||
                            targetRoute == Route.SeerrSettings ||
                            targetRoute == Route.AdminDashboard ||
                            targetRoute == Route.ScheduledTasks ||
                            targetRoute == Route.Devices ||
                            targetRoute == Route.Logs ||
                            targetRoute == Route.Requests
                    val isModalPop = initialRoute == Route.Settings ||
                            initialRoute == Route.Downloads ||
                            initialRoute == Route.SyncPlay ||
                            initialRoute == Route.SeerrSettings ||
                            initialRoute == Route.AdminDashboard ||
                            initialRoute == Route.ScheduledTasks ||
                            initialRoute == Route.Devices ||
                            initialRoute == Route.Logs ||
                            initialRoute == Route.Requests
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
                    val isModalPop = initialRoute == Route.Settings ||
                            initialRoute == Route.Downloads ||
                            initialRoute == Route.SyncPlay ||
                            initialRoute == Route.SeerrSettings ||
                            initialRoute == Route.AdminDashboard ||
                            initialRoute == Route.ScheduledTasks ||
                            initialRoute == Route.Devices ||
                            initialRoute == Route.Logs ||
                            initialRoute == Route.Requests
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
        entryProvider = entryProvider {
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
        },
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
                val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                Row(
                    modifier = Modifier
                        .animateContentSize(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec())
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
