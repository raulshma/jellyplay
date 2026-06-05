package com.raulshma.jellyplay.feature.library.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.feature.library.FavoritesScreen
import com.raulshma.jellyplay.feature.library.LibraryScreen

fun EntryProviderScope<NavKey>.librarySection(navigator: Navigator) {
    entry<Route.Library> {
        LibraryScreen(onItemClick = { navigator.navigate(Route.MediaDetail(it)) })
    }

    entry<Route.Favorites> {
        FavoritesScreen(
            onItemClick = { itemId -> navigator.navigate(Route.MediaDetail(itemId)) },
            onBack = { navigator.goBack() },
        )
    }
}
