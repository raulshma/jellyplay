package com.raulshma.jellyplay.feature.player.video.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entry
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.feature.player.video.VideoPlayerScreen

fun EntryProviderScope<NavKey>.videoPlayerSection(
    navigator: Navigator,
) {
    entry<Route.VideoPlayer> { key ->
        VideoPlayerScreen(
            itemId = key.itemId,
            mediaSourceId = key.mediaSourceId,
            startPositionTicks = key.startPositionTicks,
            onBack = { navigator.goBack() },
        )
    }
}
