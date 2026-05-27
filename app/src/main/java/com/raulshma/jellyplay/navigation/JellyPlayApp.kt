package com.raulshma.jellyplay.navigation

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.tv.material3.NavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.MaterialTheme as TvMaterial3Theme
import androidx.tv.material3.darkColorScheme as tvDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.navigation3.ui.NavDisplay
import com.raulshma.jellyplay.MainActivity
import com.raulshma.jellyplay.MainViewModel
import com.raulshma.jellyplay.core.data.playback.AudioPlaybackManager
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.WindowSizeClass
import com.raulshma.jellyplay.core.ui.adaptive.rememberAdaptiveInfo
import com.raulshma.jellyplay.core.designsystem.theme.TvTypography
import com.raulshma.jellyplay.core.ui.components.LocalNavigationBarColor
import com.raulshma.jellyplay.core.ui.components.MiniPlayer
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
import com.raulshma.jellyplay.feature.details.navigation.detailsSection
import com.raulshma.jellyplay.feature.downloads.navigation.downloadsSection
import com.raulshma.jellyplay.feature.editor.navigation.editorSection
import com.raulshma.jellyplay.feature.home.navigation.homeSection
import com.raulshma.jellyplay.feature.library.navigation.librarySection
import com.raulshma.jellyplay.feature.livetv.navigation.liveTvSection
import com.raulshma.jellyplay.feature.music.navigation.musicSection
import com.raulshma.jellyplay.feature.music.musichome.MusicHomeScreen
import com.raulshma.jellyplay.feature.player.audio.navigation.audioPlayerSection
import com.raulshma.jellyplay.feature.player.video.navigation.videoPlayerSection
import com.raulshma.jellyplay.feature.search.navigation.searchSection
import com.raulshma.jellyplay.feature.settings.navigation.settingsSection
import com.raulshma.jellyplay.feature.syncplay.navigation.syncPlaySection
import kotlinx.coroutines.launch

@Composable
fun JellyPlayApp(
    viewModel: MainViewModel,
) {
    val isAuthenticated by viewModel.isAuthenticated.collectAsStateWithLifecycle()

    when {
        isAuthenticated -> {
            CompositionLocalProvider(
                com.raulshma.jellyplay.core.ui.components.LocalNetworkStatus provides viewModel.networkMonitor.networkStatus,
            ) {
                MainContent(
                    onLogout = { viewModel.logout() },
                    viewModel = viewModel,
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
private fun MainContent(
    onLogout: () -> Unit,
    viewModel: MainViewModel,
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val homeMode = preferences.homeMode

    val navigationState = rememberNavigationState(
        startRoute = Route.Home,
        topLevelRoutes = ALL_TOP_LEVEL_ROUTE_KEYS,
    )
    val navigator = Navigator(navigationState)
    val currentTopLevel by navigationState.topLevelRoute
    val currentRoute = navigator.currentRoute()

    val isPlayerScreen = currentRoute is Route.VideoPlayer ||
            currentRoute is Route.OfflinePlayer ||
            currentRoute is Route.LiveTvChannelPlayer

    val isAudioPlayerScreen = currentRoute is Route.AudioPlayer

    val activeTopLevelRoutes = when (homeMode) {
        HomeMode.VIDEO -> VIDEO_TOP_LEVEL_ROUTES
        HomeMode.MUSIC -> MUSIC_TOP_LEVEL_ROUTES
    }

    val scope = rememberCoroutineScope()
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
    val showMiniPlayer = audioItemId != null && !isAudioPlayerScreen && !isPlayerScreen && !isMiniPlayerDismissed

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

    val context = LocalContext.current

    LaunchedEffect(viewModel.navigationRequest) {
        viewModel.navigationRequest.collect { route ->
            if (ALL_TOP_LEVEL_ROUTE_KEYS.contains(route)) {
                navigationState.topLevelRoute.value = route
            } else {
                navigator.navigate(route)
            }
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

    val adaptiveInfo = rememberAdaptiveInfo()

    val tvTypography = if (isTv) TvTypography else null

    CompositionLocalProvider(
        LocalTvMode provides isTv,
        LocalAdaptiveInfo provides adaptiveInfo,
        LocalTvTypography provides tvTypography,
    ) {
        val isExpanded = adaptiveInfo.windowSizeClass != WindowSizeClass.Compact

        @OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
        androidx.compose.animation.SharedTransitionLayout {
            CompositionLocalProvider(
                com.raulshma.jellyplay.core.ui.components.LocalSharedTransitionScope provides this,
                LocalNavigationBarColor provides navBarColorState
            ) {
            if (isTv && !isPlayerScreen) {
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
                    )
                }
                }
            } else {
                val noPadding = PaddingValues(0.dp)
                if (!isPlayerScreen) {
                    NavigationSuiteScaffold(
                        navigationItems = {
                            activeTopLevelRoutes.forEach { (route, label) ->
                                NavigationSuiteItem(
                                    selected = route == currentTopLevel,
                                    onClick = { navigator.navigate(route) },
                                    icon = { NavIcon(route, label) },
                                    label = { Text(label) },
                                )
                            }
                        },
                        navigationSuiteColors = NavigationSuiteDefaults.colors(
                            navigationBarContainerColor = if (isAudioPlayerScreen) Color.Transparent else MaterialTheme.colorScheme.surface,
                            navigationRailContainerColor = animatedNavBarColor,
                        ),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background)
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
                                    innerPadding = noPadding,
                                )
                            }
                            if (showMiniPlayer && isExpanded) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 2.dp)
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
                                        .padding(bottom = 2.dp)
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
                                        .padding(end = 8.dp, bottom = 8.dp)
                                        .fillMaxWidth(0.45f),
                                )
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
                            innerPadding = noPadding,
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
                                    .fillMaxWidth(0.45f),
                            )
                        }
                    }
                }
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

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .onKeyEvent { keyEvent ->
                if (keyEvent.key == Key.Back && keyEvent.type == KeyEventType.KeyUp) {
                    onBack()
                    true
                } else false
            },
    ) {
        NavigationDrawer(
            drawerState = drawerState,
            modifier = Modifier.padding(vertical = 8.dp),
            drawerContent = {
                Column(
                    modifier = Modifier
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
                                NavIcon(route, label, tint = MaterialTheme.colorScheme.onSurface)
                            },
                            content = {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
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
                        .weight(1f)
                        .focusRequester(contentFocusRequester)
                        .tvFocusRestorer(),
                )
            },
        )
    }

    LaunchedEffect(currentRoute) {
        val initialIndex = activeTopLevelRoutes.keys.indexOf(currentTopLevel).coerceAtLeast(0)
        focusedRailIndex = initialIndex
        drawerState.setValue(androidx.tv.material3.DrawerValue.Closed)
        try { contentFocusRequester.requestFocus() } catch (_: Exception) { }
    }
}

@Composable
private fun NavIcon(route: Route, label: String, tint: Color = MaterialTheme.colorScheme.onSurface) {
    when (route) {
        Route.Home -> Icon(Icons.Default.Home, contentDescription = label, tint = tint)
        Route.Library -> Icon(Icons.Default.LibraryMusic, contentDescription = label, tint = tint)
        Route.Search -> Icon(Icons.Default.Search, contentDescription = label, tint = tint)
        Route.LiveTv -> Icon(Icons.Default.LiveTv, contentDescription = label, tint = tint)
        Route.MusicBrowse -> Icon(Icons.Default.Album, contentDescription = label, tint = tint)
        else -> {}
    }
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
                        contentKey.contains("OfflinePlayer") ||
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
                    val isModalRoute = targetLast == Route.Settings ||
                            targetLast == Route.Downloads ||
                            targetLast == Route.SyncPlay ||
                            targetLast == Route.SeerrSettings
                    val isModalPop = initialLast == Route.Settings ||
                            initialLast == Route.Downloads ||
                            initialLast == Route.SyncPlay ||
                            initialLast == Route.SeerrSettings
                    val isTabSwitch = targetLast is Route && initialLast is Route &&
                            ALL_TOP_LEVEL_ROUTE_KEYS.contains(targetLast as Route) &&
                            ALL_TOP_LEVEL_ROUTE_KEYS.contains(initialLast as Route)

                    when {
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
                    val initialLast = initialState
                    val isModalPop = initialLast == Route.Settings ||
                            initialLast == Route.Downloads ||
                            initialLast == Route.SyncPlay ||
                            initialLast == Route.SeerrSettings
                    if (isModalPop) {
                        fadeIn(fastEffects) togetherWith fadeOut(
                            fastEffects
                        ) + slideOutVertically(
                                targetOffsetY = { it / 4 },
                                animationSpec = defaultSpatialOffset,
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
                predictivePopTransitionSpec = { _ ->
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
                },
        entryProvider = entryProvider {
            homeSection(
                navigator = navigator,
                homeMode = homeMode,
                onModeChange = onModeChange,
                musicContent = {
                    MusicHomeScreen(
                        homeMode = homeMode,
                        onModeChange = onModeChange,
                        onItemClick = { itemId -> navigator.navigate(Route.MediaDetail(itemId)) },
                        onAlbumClick = { albumId -> navigator.navigate(Route.AlbumDetail(albumId)) },
                        onSettingsClick = { navigator.navigate(Route.Settings) },
                        onSyncPlayClick = { navigator.navigate(Route.SyncPlay) },
                        onDownloadsClick = { navigator.navigate(Route.Downloads) },
                        onArtistsClick = { navigator.navigate(Route.Artists) },
                        onAlbumsClick = { navigator.navigate(Route.Albums) },
                        onTracksClick = { navigator.navigate(Route.Tracks) },
                        onGenresClick = { navigator.navigate(Route.Genres) },
                        onPlaylistsClick = { navigator.navigate(Route.Playlists) },
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
            settingsSection(navigator, onLogout)
            musicSection(navigator)
            syncPlaySection(navigator)
        },
        modifier = modifier,
    )
}
