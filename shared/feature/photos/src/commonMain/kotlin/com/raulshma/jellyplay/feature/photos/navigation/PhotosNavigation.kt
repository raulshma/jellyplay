package com.raulshma.jellyplay.feature.photos.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.feature.photos.PhotoAlbumScreen
import com.raulshma.jellyplay.feature.photos.PhotoViewerScreen

/**
 * The photo suite's nav entries (extracted verbatim from feature/library's
 * librarySection at the photo-suite move — the two pushes the library/
 * favorites grids' navigatePhotoAware lands on). Route.PhotoAlbum /
 * Route.PhotoViewer stay core:ui NavKey keys, unchanged; appSections
 * registers this builder right after librarySection.
 */
fun EntryProviderScope<NavKey>.photosSection(navigator: Navigator) {
    entry<Route.PhotoAlbum> { key ->
        PhotoAlbumScreen(
            parentId = key.parentId,
            folderName = key.folderName,
            onPhotoClick = { itemId, parentId ->
                navigator.navigate(Route.PhotoViewer(itemId, parentId))
            },
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
