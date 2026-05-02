package com.raulshma.jellyplay.feature.home.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entry
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.feature.home.HomeScreen

fun EntryProviderScope<NavKey>.homeSection(
    navigator: Navigator,
) {
    entry<Route.Home> {
        HomeScreen(
            onItemClick = { itemId -> navigator.navigate(Route.MediaDetail(itemId)) },
        )
    }
}
