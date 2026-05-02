package com.raulshma.jellyplay.feature.details.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.feature.details.MediaDetailScreen

fun EntryProviderScope<NavKey>.detailsSection(
    navigator: Navigator,
) {
    entry<Route.MediaDetail> { key ->
        MediaDetailScreen(
            itemId = key.itemId,
            onPlayClick = { itemId, mediaSourceId, startPosition ->
                navigator.navigate(
                    Route.VideoPlayer(itemId, mediaSourceId, startPosition)
                )
            },
            onItemClick = { itemId -> navigator.navigate(Route.MediaDetail(itemId)) },
            onBack = { navigator.goBack() },
        )
    }
}
