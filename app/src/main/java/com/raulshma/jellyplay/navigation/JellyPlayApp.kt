package com.raulshma.jellyplay.navigation

import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.raulshma.jellyplay.MainViewModel
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.ui.navigation.ALL_TOP_LEVEL_ROUTE_KEYS
import com.raulshma.jellyplay.core.ui.navigation.LocalSharedTransitionScope
import com.raulshma.jellyplay.core.ui.navigation.MUSIC_TOP_LEVEL_ROUTES
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.navigation.VIDEO_TOP_LEVEL_ROUTES
import com.raulshma.jellyplay.core.ui.navigation.rememberNavigationState
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
    viewModel: MainViewModel = hiltViewModel(),
) {
    val isAuthenticated by viewModel.isAuthenticated.collectAsStateWithLifecycle()
    val isRestoring by viewModel.isRestoring.collectAsStateWithLifecycle()

    when {
        isRestoring -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
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

    val configuration = LocalConfiguration.current
    val isExpanded = configuration.screenWidthDp >= 600

    val activeTopLevelRoutes = when (homeMode) {
        HomeMode.VIDEO -> VIDEO_TOP_LEVEL_ROUTES
        HomeMode.MUSIC -> MUSIC_TOP_LEVEL_ROUTES
    }

    val scope = rememberCoroutineScope()
    val onModeChange: (HomeMode) -> Unit = { mode ->
        scope.launch { viewModel.preferencesStore.setHomeMode(mode) }
    }

    Scaffold(
        bottomBar = {
            if (!isPlayerScreen && !isExpanded) {
                NavigationBar {
                    activeTopLevelRoutes.forEach { (route, label) ->
                        NavigationBarItem(
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
        },
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            if (!isPlayerScreen && isExpanded) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
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
            MainNavDisplay(
                navigationState = navigationState,
                navigator = navigator,
                onLogout = onLogout,
                homeMode = homeMode,
                onModeChange = onModeChange,
                modifier = Modifier.weight(1f),
            )
        }
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

@Composable
private fun MainNavDisplay(
    navigationState: com.raulshma.jellyplay.core.ui.navigation.NavigationState,
    navigator: Navigator,
    onLogout: () -> Unit,
    homeMode: HomeMode,
    onModeChange: (HomeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentBackStack = navigationState.backStacks[navigationState.topLevelRoute.value] ?: return

    SharedTransitionLayout {
        androidx.compose.runtime.CompositionLocalProvider(
            LocalSharedTransitionScope provides this@SharedTransitionLayout
        ) {
            NavDisplay(
                backStack = currentBackStack,
                onBack = { navigator.goBack() },
                entryProvider =                 entryProvider {
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
                    videoPlayerSection(navigator)
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
