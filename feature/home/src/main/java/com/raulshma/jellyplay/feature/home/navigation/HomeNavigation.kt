package com.raulshma.jellyplay.feature.home.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.isPhotoType
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.navigation.navigatePhotoAware
import com.raulshma.jellyplay.feature.home.HomeCallbacks
import com.raulshma.jellyplay.feature.home.HomeScreen

fun EntryProviderScope<NavKey>.homeSection(
    navigator: Navigator,
    homeMode: HomeMode = HomeMode.VIDEO,
    onModeChange: (HomeMode) -> Unit = {},
    onPlayOnClick: () -> Unit = {},
    playOnStrategy: com.raulshma.jellyplay.core.data.cast.remote.JellyfinRemotePlayCastStrategy? = null,
    musicContent: @Composable () -> Unit = {},
) {
    entry<Route.Home> {
        // Build the callbacks once per navigator lifetime so the HomeScreen subtree
        // sees a single stable `HomeCallbacks` instance (treated as @Immutable by
        // the Compose compiler) instead of fresh lambda allocations on every
        // recomposition — eliminates cascading recompositions of the home content.
        val callbacks = remember(navigator) {
            HomeCallbacks(
                onItemClick = { itemId, mediaType, parentId, itemName ->
                    navigator.navigatePhotoAware(itemId, mediaType, parentId, itemName)
                },
                onPlayClick = { itemId, mediaSourceId, startPosition, mediaType, parentId ->
                    if (mediaType.isPhotoType) {
                        navigator.navigatePhotoAware(itemId, mediaType, parentId, "")
                    } else if (playOnStrategy?.isConnected?.value == true) {
                        // "Play On" active: fling to the connected remote session
                        // instead of opening the local video player. Mirrors
                        // jellyfin-web's "remote is current player" routing.
                        playOnStrategy.loadMedia(
                            itemId = itemId,
                            startPositionMs = startPosition / 10_000,
                        )
                    } else {
                        navigator.navigate(Route.VideoPlayer(itemId, mediaSourceId, startPosition))
                    }
                },
                onSettingsClick = { navigator.navigate(Route.Settings) },
                onSyncPlayClick = { navigator.navigate(Route.SyncPlay) },
                onDownloadsClick = { navigator.navigate(Route.Downloads) },
                onPlayOnClick = onPlayOnClick,
                onOfflineLibraryClick = { navigator.navigate(Route.OfflineLibrary) },
                onOfflineItemClick = { itemId, mediaType ->
                    if (mediaType == MediaType.SERIES) {
                        navigator.navigate(Route.OfflineSeries(itemId))
                    } else {
                        navigator.navigate(Route.OfflineDetail(itemId))
                    }
                },
                onSeerrItemClick = { tmdbId, mediaType ->
                    navigator.navigate(Route.SeerrDetail(tmdbId, mediaType))
                },
                onModeChange = onModeChange,
                onSearchItemClick = { itemId -> navigator.navigate(Route.MediaDetail(itemId)) },
                onSearchSeerrClick = { tmdbId, mediaType ->
                    navigator.navigate(Route.SeerrDetail(tmdbId, mediaType))
                },
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
                onActivityQueueClick = { navigator.navigate(Route.ArrQueue) },
                onUpcomingClick = { navigator.navigate(Route.UpcomingCalendar) },
            )
        }
        HomeScreen(
            callbacks = callbacks,
            homeMode = homeMode,
            musicContent = musicContent,
        )
    }
}
