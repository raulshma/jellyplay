package com.raulshma.jellyplay.feature.library.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.isPhotoType
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.feature.library.FavoritesScreen
import com.raulshma.jellyplay.feature.library.LibraryScreen
import com.raulshma.jellyplay.feature.library.PhotoAlbumScreen
import com.raulshma.jellyplay.feature.library.PhotoViewerScreen
import com.raulshma.jellyplay.feature.library.StudioDetailScreen

private fun Navigator.navigatePhotoAware(
    itemId: String,
    mediaType: MediaType,
    parentId: String?,
    itemName: String = "",
) {
    when (mediaType) {
        MediaType.PHOTO_FOLDER -> navigate(Route.PhotoAlbum(parentId = itemId, folderName = itemName))
        MediaType.PHOTO -> navigate(Route.PhotoViewer(itemId, parentId))
        else -> navigate(Route.MediaDetail(itemId))
    }
}

fun EntryProviderScope<NavKey>.librarySection(navigator: Navigator) {
    entry<Route.Library> {
        LibraryScreen(
            onItemClick = { itemId, mediaType, parentId, itemName ->
                navigator.navigatePhotoAware(itemId, mediaType, parentId, itemName)
            },
        )
    }

    entry<Route.Favorites> {
        FavoritesScreen(
            onItemClick = { itemId, mediaType, parentId, itemName ->
                navigator.navigatePhotoAware(itemId, mediaType, parentId, itemName)
            },
            onBack = { navigator.goBack() },
        )
    }

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

    entry<Route.StudioDetail> { key ->
        StudioDetailScreen(
            studioName = key.studioName,
            onItemClick = { itemId ->
                navigator.navigate(Route.MediaDetail(itemId))
            },
            onBack = { navigator.goBack() },
        )
    }
}
