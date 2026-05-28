package com.raulshma.jellyplay.feature.downloads.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.feature.downloads.DownloadsScreen
import com.raulshma.jellyplay.feature.downloads.OfflineLibraryScreen
import com.raulshma.jellyplay.feature.downloads.OfflinePlayerScreen
import com.raulshma.jellyplay.feature.downloads.OfflineSeriesScreen

fun EntryProviderScope<NavKey>.downloadsSection(
    navigator: Navigator,
) {
    entry<Route.Downloads> {
        DownloadsScreen(
            onItemClick = { itemId -> navigator.navigate(Route.MediaDetail(itemId)) },
            onPlayOffline = { filePath, title ->
                navigator.navigate(Route.OfflinePlayer(filePath, title))
            },
            onBack = { navigator.goBack() },
        )
    }
    entry<Route.OfflinePlayer> { key ->
        OfflinePlayerScreen(
            filePath = key.filePath,
            title = key.title,
            onBack = { navigator.goBack() },
        )
    }
    entry<Route.OfflineLibrary> {
        OfflineLibraryScreen(
            onItemClick = { seriesId ->
                navigator.navigate(Route.OfflineSeries(seriesId))
            },
            onPlayOffline = { filePath, title ->
                navigator.navigate(Route.OfflinePlayer(filePath, title))
            },
            onBack = { navigator.goBack() },
        )
    }
    entry<Route.OfflineSeries> { key ->
        OfflineSeriesScreen(
            seriesId = key.seriesId,
            onPlayOffline = { filePath, title ->
                navigator.navigate(Route.OfflinePlayer(filePath, title))
            },
            onBack = { navigator.goBack() },
        )
    }
}
