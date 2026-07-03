package com.raulshma.jellyplay.feature.downloads.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.feature.downloads.DownloadsScreen
import com.raulshma.jellyplay.feature.downloads.OfflineDetailScreen
import com.raulshma.jellyplay.feature.downloads.OfflineLibraryScreen
import com.raulshma.jellyplay.feature.downloads.OfflineSeriesScreen

fun EntryProviderScope<NavKey>.downloadsSection(
    navigator: Navigator,
) {
    entry<Route.Downloads> {
        DownloadsScreen(
            onItemClick = { itemId -> navigator.navigate(Route.MediaDetail(itemId)) },
            onPlayOffline = { itemId, mediaType ->
                val isAudio = mediaType == MediaType.AUDIO || mediaType == MediaType.MUSIC
                if (isAudio) {
                    navigator.navigate(Route.AudioPlayer(itemId))
                } else {
                    navigator.navigate(Route.VideoPlayer(itemId))
                }
            },
            onBack = { navigator.goBack() },
        )
    }
    entry<Route.OfflineLibrary> {
        OfflineLibraryScreen(
            onSeriesClick = { seriesId ->
                navigator.navigate(Route.OfflineSeries(seriesId))
            },
            onItemClick = { itemId ->
                navigator.navigate(Route.OfflineDetail(itemId))
            },
            onBack = { navigator.goBack() },
        )
    }
    entry<Route.OfflineSeries> { key ->
        OfflineSeriesScreen(
            seriesId = key.seriesId,
            onPlayOffline = { itemId ->
                navigator.navigate(Route.VideoPlayer(itemId))
            },
            onEpisodeDetail = { itemId ->
                navigator.navigate(Route.OfflineDetail(itemId))
            },
            onBack = { navigator.goBack() },
        )
    }
    entry<Route.OfflineDetail> { key ->
        OfflineDetailScreen(
            itemId = key.itemId,
            onPlayOffline = { itemId, mediaType ->
                val isAudio = mediaType == MediaType.AUDIO || mediaType == MediaType.MUSIC
                if (isAudio) {
                    navigator.navigate(Route.AudioPlayer(itemId))
                } else {
                    navigator.navigate(Route.VideoPlayer(itemId))
                }
            },
            onNavigateToSeries = { seriesId ->
                navigator.navigate(Route.OfflineSeries(seriesId))
            },
            onNavigateToDetail = { itemId ->
                navigator.navigate(Route.OfflineDetail(itemId))
            },
            onBack = { navigator.goBack() },
        )
    }
}
