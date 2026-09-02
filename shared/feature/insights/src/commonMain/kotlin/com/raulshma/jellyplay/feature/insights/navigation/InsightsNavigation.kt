package com.raulshma.jellyplay.feature.insights.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.feature.insights.heatmap.WatchProgressHeatmapScreen

fun EntryProviderScope<NavKey>.insightsSection(
    navigator: Navigator,
) {
    entry<Route.WatchProgressHeatmap> {
        WatchProgressHeatmapScreen(
            onBack = { navigator.goBack() },
            onItemClick = { itemId -> navigator.navigate(Route.MediaDetail(itemId)) },
        )
    }
}
