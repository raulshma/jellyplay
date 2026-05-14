package com.raulshma.jellyplay.navigation

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.raulshma.jellyplay.MainActivity
import com.raulshma.jellyplay.MainViewModel
import com.raulshma.jellyplay.core.data.playback.AudioPlaybackManager
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.ui.adaptive.AdaptiveLayout
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.WindowSizeClass
import com.raulshma.jellyplay.core.ui.adaptive.classifyWindow
import com.raulshma.jellyplay.core.ui.components.LocalNavigationBarColor
import com.raulshma.jellyplay.core.ui.components.LocalSharedTransitionScope
import com.raulshma.jellyplay.core.ui.components.MiniPlayer
import com.raulshma.jellyplay.core.ui.navigation.ALL_TOP_LEVEL_ROUTE_KEYS
import com.raulshma.jellyplay.core.ui.navigation.MUSIC_TOP_LEVEL_ROUTES
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.navigation.VIDEO_TOP_LEVEL_ROUTES
import com.raulshma.jellyplay.core.ui.navigation.rememberNavigationState
import com.raulshma.jellyplay.core.ui.tv.TvScaffold
import com.raulshma.jellyplay.core.ui.tv.isTvDevice
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.feature.auth.navigation.authSection
import com.raulshma.jellyplay.feature.details.navigation.detailsSection
import com.raulshma.jellyplay.feature.downloads.navigation.downloadsSection
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

    NavDisplay(
        backStack = navigationState.backStacks.values.first(),
        onBack = { navigator.goBack() },
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

    var lastNavigatedAt by remember { mutableStateOf(0L) }

    val audioPlaybackManager: AudioPlaybackManager = viewModel.audioPlaybackManager
    val isAudioPlaying by audioPlaybackManager.isPlaying.collectAsState()
    val audioItemId by audioPlaybackManager.currentPlayingItemId.collectAsState()
    val audioTitle by audioPlaybackManager.title.collectAsState()
    val audioArtist by audioPlaybackManager.artist.collectAsState()
    val audioArtworkUrl by audioPlaybackManager.albumArtUrl.collectAsState()
    val showMiniPlayer = isAudioPlaying && !isAudioPlayerScreen && !isPlayerScreen

    val videoMiniPlayerState = viewModel.videoMiniPlayerState
    val isVideoMiniMode by videoMiniPlayerState.isMiniMode.collectAsState()
    val videoMiniTitle by videoMiniPlayerState.title.collectAsState()
    val videoMiniSubtitle by videoMiniPlayerState.subtitle.collectAsState()
    val videoMiniIsPlaying by videoMiniPlayerState.isPlaying.collectAsState()
    val videoMiniItemId by videoMiniPlayerState.itemId.collectAsState()

    val context = LocalContext.current

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

    androidx.compose.runtime.LaunchedEffect(viewModel.syncPlayManager) {
        viewModel.syncPlayManager.commands.collect { command ->
            if (command is com.raulshma.jellyplay.core.data.syncplay.SyncPlayCommand.PlayQueueUpdate) {
                if (command.playingItemId.isBlank()) return@collect
                val route = navigator.currentRoute()
                val isPlayer = route is Route.VideoPlayer ||
                        route is Route.OfflinePlayer ||
                        route is Route.LiveTvChannelPlayer ||
                        route is Route.AudioPlayer
                if (!isPlayer) {
                    val now = System.currentTimeMillis()
                    if (now - lastNavigatedAt < 2000L) return@collect
                    lastNavigatedAt = now
                    navigator.navigate(Route.VideoPlayer(command.playingItemId, startPositionTicks = command.positionTicks))
                }
            }
        }
    }

    val navBarColorState = remember { mutableStateOf<Color?>(null) }
    val animatedNavBarColor by animateColorAsState(
        targetValue = navBarColorState.value ?: MaterialTheme.colorScheme.surfaceContainer,
        animationSpec = tween(400),
        label = "navBarColor",
    )

    val isTv = isTvDevice()

    val configuration = LocalConfiguration.current
    val adaptiveInfo = classifyWindow(configuration.screenWidthDp, configuration.screenHeightDp)

    CompositionLocalProvider(LocalAdaptiveInfo provides adaptiveInfo) {
        val isExpanded = adaptiveInfo.windowSizeClass != WindowSizeClass.Compact

        CompositionLocalProvider(LocalNavigationBarColor provides navBarColorState) {
            if (isTv && !isPlayerScreen) {
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
                    )
                }
            } else {
                Scaffold(
                    bottomBar = {
                        if (!isPlayerScreen && !isExpanded) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                MiniPlayer(
                                    isVisible = showMiniPlayer,
                                    title = audioTitle,
                                    artist = audioArtist,
                                    artworkUri = audioArtworkUrl,
                                    isPlaying = isAudioPlaying,
                                    onClick = {
                                        val itemId = audioItemId ?: return@MiniPlayer
                                        navigator.navigate(Route.AudioPlayer(itemId))
                                    },
                                    onStop = {
                                        audioPlaybackManager.stopAndRelease()
                                    },
                                    onPlayPause = {
                                        audioPlaybackManager.togglePlayPause()
                                    },
                                    onSkipNext = {
                                        audioPlaybackManager.skipToNext()
                                    },
                                )
                                NavigationBar(containerColor = animatedNavBarColor) {
                                    activeTopLevelRoutes.forEach { (route, label) ->
                                        NavigationBarItem(
                                            selected = route == currentTopLevel,
                                            onClick = { navigator.navigate(route) },
                                            icon = {
                                                NavIcon(route, label)
                                            },
                                            label = { Text(label) },
                                            colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                                                indicatorColor = Color.White.copy(alpha = 0.15f),
                                                selectedIconColor = Color.White,
                                                selectedTextColor = Color.White,
                                                unselectedIconColor = Color.White.copy(alpha = 0.6f),
                                                unselectedTextColor = Color.White.copy(alpha = 0.6f),
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                    },
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = innerPadding.calculateBottomPadding())
                    ) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            if (!isPlayerScreen && isExpanded) {
                                NavigationRail(
                                    containerColor = animatedNavBarColor,
                                ) {
                                    activeTopLevelRoutes.forEach { (route, label) ->
                                        NavigationRailItem(
                                            selected = route == currentTopLevel,
                                            onClick = { navigator.navigate(route) },
                                            icon = {
                                                NavIcon(route, label)
                                            },
                                            label = { Text(label) },
                                        )
                                    }
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                MainNavDisplay(
                                    navigationState = navigationState,
                                    navigator = navigator,
                                    onLogout = onLogout,
                                    homeMode = homeMode,
                                    onModeChange = onModeChange,
                                    enterPip = enterPip,
                                    enterVideoMiniMode = enterVideoMiniMode,
                                    modifier = Modifier.weight(1f),
                                )
                                if (showMiniPlayer && isExpanded) {
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
                                        onStop = {
                                            audioPlaybackManager.stopAndRelease()
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
                        }

                        if (isVideoMiniMode && !isPlayerScreen) {
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
) {
    Row(modifier = Modifier.fillMaxSize()) {
        if (!isPlayerScreen) {
            NavigationRail(
                containerColor = animatedNavBarColor,
                modifier = Modifier.padding(vertical = 24.dp),
            ) {
                Spacer(Modifier.height(24.dp))
                activeTopLevelRoutes.forEach { (route, label) ->
                    NavigationRailItem(
                        selected = route == currentTopLevel,
                        onClick = { navigator.navigate(route) },
                        icon = {
                            NavIcon(route, label)
                        },
                        label = {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        },
                    )
                }
            }
        }
        MainNavDisplay(
            navigationState = navigationState,
            navigator = navigator,
            onLogout = onLogout,
            homeMode = homeMode,
            onModeChange = onModeChange,
            enterPip = enterPip,
            enterVideoMiniMode = enterVideoMiniMode,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun NavIcon(route: Route, label: String) {
    when (route) {
        Route.Home -> Icon(Icons.Default.Home, contentDescription = label)
        Route.Library -> Icon(Icons.Default.LibraryMusic, contentDescription = label)
        Route.Search -> Icon(Icons.Default.Search, contentDescription = label)
        Route.LiveTv -> Icon(Icons.Default.LiveTv, contentDescription = label)
        Route.MusicBrowse -> Icon(Icons.Default.Album, contentDescription = label)
        else -> {}
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun MainNavDisplay(
    navigationState: com.raulshma.jellyplay.core.ui.navigation.NavigationState,
    navigator: Navigator,
    onLogout: () -> Unit,
    homeMode: HomeMode,
    onModeChange: (HomeMode) -> Unit,
    enterPip: () -> Unit,
    enterVideoMiniMode: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val currentBackStack = navigationState.backStacks[navigationState.topLevelRoute.value] ?: return

    SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedTransitionScope provides this@SharedTransitionLayout) {
            NavDisplay(
                backStack = currentBackStack,
                onBack = { navigator.goBack() },
                transitionSpec = {
                    val targetLast = targetState
                    val initialLast = initialState
                    val isModalRoute = targetLast is Route.Settings ||
                            targetLast is Route.Downloads ||
                            targetLast is Route.SyncPlay ||
                            targetLast is Route.SeerrSettings
                    val isModalPop = initialLast is Route.Settings ||
                            initialLast is Route.Downloads ||
                            initialLast is Route.SyncPlay ||
                            initialLast is Route.SeerrSettings
                    val isTabSwitch = targetLast is Route && initialLast is Route &&
                            ALL_TOP_LEVEL_ROUTE_KEYS.contains(targetLast as Route) &&
                            ALL_TOP_LEVEL_ROUTE_KEYS.contains(initialLast as Route)

                    when {
                        isModalRoute -> {
                            fadeIn(tween(250)) +
                                    slideInVertically(
                                        initialOffsetY = { it / 4 },
                                        animationSpec = tween(350, easing = FastOutSlowInEasing),
                                    ) togetherWith fadeOut(tween(180))
                        }
                        isModalPop -> {
                            fadeIn(tween(200)) togetherWith fadeOut(tween(200)) +
                                    slideOutVertically(
                                        targetOffsetY = { it / 4 },
                                        animationSpec = tween(280, easing = FastOutSlowInEasing),
                                    )
                        }
                        isTabSwitch -> {
                            fadeIn(tween(200)) togetherWith fadeOut(tween(200))
                        }
                        else -> {
                            fadeIn(
                                animationSpec = tween(320, easing = FastOutSlowInEasing),
                            ) + slideInHorizontally(
                                initialOffsetX = { it / 10 },
                                animationSpec = tween(360, easing = FastOutSlowInEasing),
                            ) + scaleIn(
                                initialScale = 0.985f,
                                animationSpec = tween(360, easing = FastOutSlowInEasing),
                            ) togetherWith fadeOut(
                                animationSpec = tween(180),
                            ) + slideOutHorizontally(
                                targetOffsetX = { -it / 18 },
                                animationSpec = tween(220, easing = FastOutSlowInEasing),
                            ) + scaleOut(
                                targetScale = 1.015f,
                                animationSpec = tween(220, easing = FastOutSlowInEasing),
                            )
                        }
                    }
                },
                popTransitionSpec = {
                    val initialLast = initialState
                    val isModalPop = initialLast is Route.Settings ||
                            initialLast is Route.Downloads ||
                            initialLast is Route.SyncPlay ||
                            initialLast is Route.SeerrSettings
                    if (isModalPop) {
                        fadeIn(tween(200)) togetherWith fadeOut(tween(200)) +
                                slideOutVertically(
                                    targetOffsetY = { it / 4 },
                                    animationSpec = tween(280, easing = FastOutSlowInEasing),
                                )
                    } else {
                        fadeIn(
                            animationSpec = tween(260, easing = FastOutSlowInEasing),
                        ) + slideInHorizontally(
                            initialOffsetX = { -it / 12 },
                            animationSpec = tween(300, easing = FastOutSlowInEasing),
                        ) + scaleIn(
                            initialScale = 1.015f,
                            animationSpec = tween(300, easing = FastOutSlowInEasing),
                        ) togetherWith fadeOut(
                            animationSpec = tween(220),
                        ) + slideOutHorizontally(
                            targetOffsetX = { it / 10 },
                            animationSpec = tween(280, easing = FastOutSlowInEasing),
                        ) + scaleOut(
                            targetScale = 0.985f,
                            animationSpec = tween(280, easing = FastOutSlowInEasing),
                        )
                    }
                },
                predictivePopTransitionSpec = { _ ->
                    fadeIn(
                        animationSpec = tween(260, easing = FastOutSlowInEasing),
                    ) + slideInHorizontally(
                        initialOffsetX = { -it / 12 },
                        animationSpec = tween(300, easing = FastOutSlowInEasing),
                    ) + scaleIn(
                        initialScale = 1.015f,
                        animationSpec = tween(300, easing = FastOutSlowInEasing),
                    ) togetherWith fadeOut(
                        animationSpec = tween(220),
                    ) + slideOutHorizontally(
                        targetOffsetX = { it / 10 },
                        animationSpec = tween(280, easing = FastOutSlowInEasing),
                    ) + scaleOut(
                        targetScale = 0.985f,
                        animationSpec = tween(280, easing = FastOutSlowInEasing),
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
    }
}
