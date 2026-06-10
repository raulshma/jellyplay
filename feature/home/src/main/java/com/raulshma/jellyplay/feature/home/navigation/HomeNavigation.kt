package com.raulshma.jellyplay.feature.home.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.feature.home.HomeScreen

fun EntryProviderScope<NavKey>.homeSection(
    navigator: Navigator,
    homeMode: HomeMode = HomeMode.VIDEO,
    onModeChange: (HomeMode) -> Unit = {},
    musicContent: @Composable () -> Unit = {},
) {
    entry<Route.Home> {
        HomeScreen(
            onItemClick = { itemId -> navigator.navigate(Route.MediaDetail(itemId)) },
            onPlayClick = { itemId, mediaSourceId, startPosition ->
                navigator.navigate(Route.VideoPlayer(itemId, mediaSourceId, startPosition))
            },
            onSettingsClick = { navigator.navigate(Route.Settings) },
            onSyncPlayClick = { navigator.navigate(Route.SyncPlay) },
            onDownloadsClick = { navigator.navigate(Route.Downloads) },
            onOfflineLibraryClick = { navigator.navigate(Route.OfflineLibrary) },
            onSeerrItemClick = { tmdbId, mediaType ->
                navigator.navigate(Route.SeerrDetail(tmdbId, mediaType))
            },
            onSearchItemClick = { itemId -> navigator.navigate(Route.MediaDetail(itemId)) },
            onSearchSeerrClick = { tmdbId, mediaType ->
                navigator.navigate(Route.SeerrDetail(tmdbId, mediaType))
            },
            homeMode = homeMode,
            onModeChange = onModeChange,
            musicContent = musicContent,
            onNewsletterClick = { navigator.navigate(Route.Newsletter) },
            onServerManagementClick = { navigator.navigate(Route.ServerManagement()) },
            onUserManagementClick = { navigator.navigate(Route.UserManagement()) },
            onSeerrSettingsClick = { navigator.navigate(Route.SeerrSettings()) },
            onAdminDashboardClick = { navigator.navigate(Route.AdminDashboard) },
            onSetupWizardClick = { navigator.navigate(Route.Onboarding) },
            onFavoritesClick = { navigator.navigate(Route.Favorites) },
            onAboutClick = { navigator.navigate(Route.About) },
            onWatchProgressHeatmapClick = { navigator.navigate(Route.WatchProgressHeatmap) },
            onRequestsClick = { navigator.navigate(Route.Requests) },
        )
    }
}
