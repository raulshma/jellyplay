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
            onSettingsClick = { navigator.navigate(Route.Settings) },
            onSyncPlayClick = { navigator.navigate(Route.SyncPlay) },
            onDownloadsClick = { navigator.navigate(Route.Downloads) },
            homeMode = homeMode,
            onModeChange = onModeChange,
            musicContent = musicContent,
        )
    }
}
