package com.raulshma.jellyplay.navigation

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.raulshma.jellyplay.MainViewModel
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.navigation.TOP_LEVEL_ROUTES
import com.raulshma.jellyplay.core.ui.navigation.rememberNavigationState
import com.raulshma.jellyplay.feature.auth.navigation.authSection
import com.raulshma.jellyplay.feature.details.navigation.detailsSection
import com.raulshma.jellyplay.feature.downloads.navigation.downloadsSection
import com.raulshma.jellyplay.feature.home.navigation.homeSection
import com.raulshma.jellyplay.feature.library.navigation.librarySection
import com.raulshma.jellyplay.feature.player.audio.navigation.audioPlayerSection
import com.raulshma.jellyplay.feature.player.video.navigation.videoPlayerSection
import com.raulshma.jellyplay.feature.search.navigation.searchSection
import com.raulshma.jellyplay.feature.settings.navigation.settingsSection

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
            MainContent(
                onLogout = { viewModel.logout() },
            )
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
) {
    val topLevelRoutes = TOP_LEVEL_ROUTES.keys
    val navigationState = rememberNavigationState(
        startRoute = Route.Home,
        topLevelRoutes = topLevelRoutes,
    )
    val navigator = Navigator(navigationState)
    val currentTopLevel by navigationState.topLevelRoute

    Scaffold(
        bottomBar = {
            NavigationBar {
                TOP_LEVEL_ROUTES.forEach { (route, label) ->
                    NavigationBarItem(
                        selected = route == currentTopLevel,
                        onClick = { navigator.navigate(route) },
                        icon = {
                            when (route) {
                                Route.Home -> Icon(Icons.Default.Home, contentDescription = label)
                                Route.Library -> Icon(Icons.Default.LibraryMusic, contentDescription = label)
                                Route.Search -> Icon(Icons.Default.Search, contentDescription = label)
                                else -> {}
                            }
                        },
                        label = { Text(label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        val currentBackStack = navigationState.backStacks[currentTopLevel] ?: return@Scaffold

        NavDisplay(
            backStack = currentBackStack,
            onBack = { navigator.goBack() },
            modifier = Modifier.padding(innerPadding),
            entryProvider = entryProvider {
                homeSection(navigator)
                librarySection(navigator)
                searchSection(navigator)
                detailsSection(navigator)
                videoPlayerSection(navigator)
                audioPlayerSection(navigator)
                downloadsSection(navigator)
                settingsSection(navigator, onLogout)
            },
        )
    }
}
