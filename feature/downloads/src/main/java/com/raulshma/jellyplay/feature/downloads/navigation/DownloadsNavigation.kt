package com.raulshma.jellyplay.feature.downloads.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.feature.downloads.DownloadsScreen
import com.raulshma.jellyplay.feature.downloads.OfflineLibraryScreen

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
            // Both series and non-series offline items now open the unified
            // MediaDetail tree, which renders local/offline series detail
            // (including the batch-delete sheet) in place of the former
            // OfflineSeries / OfflineDetail routes.
            onSeriesClick = { seriesId ->
                navigator.navigate(Route.MediaDetail(seriesId))
            },
            onItemClick = { itemId ->
                navigator.navigate(Route.MediaDetail(itemId))
            },
            onBack = { navigator.goBack() },
        )
    }
}
