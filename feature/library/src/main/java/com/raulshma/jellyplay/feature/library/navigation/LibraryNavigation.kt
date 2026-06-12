package com.raulshma.jellyplay.feature.library.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.feature.library.FavoritesScreen
import com.raulshma.jellyplay.feature.library.LibraryScreen
import com.raulshma.jellyplay.feature.library.PhotoViewerScreen

fun EntryProviderScope<NavKey>.librarySection(navigator: Navigator) {
    entry<Route.Library> {
        LibraryScreen(
            onItemClick = { itemId, mediaType, parentId ->
                if (mediaType == MediaType.PHOTO) {
                    navigator.navigate(Route.PhotoViewer(itemId, parentId))
                } else {
                    navigator.navigate(Route.MediaDetail(itemId))
                }
            },
        )
    }

    entry<Route.Favorites> {
        FavoritesScreen(
            onItemClick = { itemId -> navigator.navigate(Route.MediaDetail(itemId)) },
            onBack = { navigator.goBack() },
        )
    }

    entry<Route.PhotoViewer> { key ->
        PhotoViewerScreen(
            itemId = key.itemId,
            parentId = key.parentId,
            onBack = { navigator.goBack() },
        )
    }
}
