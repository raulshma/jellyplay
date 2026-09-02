package com.raulshma.jellyplay.feature.library.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.model.LibrarySectionContext
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.navigation.navigatePhotoAware
import com.raulshma.jellyplay.feature.library.FavoritesScreen
import com.raulshma.jellyplay.feature.library.LibraryScreen
import com.raulshma.jellyplay.feature.library.PhotoAlbumScreen
import com.raulshma.jellyplay.feature.library.PhotoViewerScreen
import com.raulshma.jellyplay.feature.library.StudioDetailScreen

fun EntryProviderScope<NavKey>.librarySection(navigator: Navigator) {
    entry<Route.Library> {
        LibraryScreen(
            onItemClick = { itemId, mediaType, parentId, itemName ->
                navigator.navigatePhotoAware(itemId, mediaType, parentId, itemName)
            },
            onSmartPlaylistsClick = { navigator.navigate(Route.SmartPlaylists) },
            onMoodPlaylistsClick = { navigator.navigate(Route.MoodPlaylists) },
            onPlaylistsClick = { navigator.navigate(Route.Playlists) },
            onOpenDownloadDetail = { itemId, openDownloadSheet ->
                navigator.navigate(Route.MediaDetail(itemId, openDownloadSheet))
            },
        )
    }

    entry<Route.LibraryBrowse> {
        LibraryScreen(
            onItemClick = { itemId, mediaType, parentId, itemName ->
                navigator.navigatePhotoAware(itemId, mediaType, parentId, itemName)
            },
            onSmartPlaylistsClick = { navigator.navigate(Route.SmartPlaylists) },
            onMoodPlaylistsClick = { navigator.navigate(Route.MoodPlaylists) },
            onPlaylistsClick = { navigator.navigate(Route.Playlists) },
            onOpenDownloadDetail = { itemId, openDownloadSheet ->
                navigator.navigate(Route.MediaDetail(itemId, openDownloadSheet))
            },
        )
    }

    entry<Route.LibrarySection> { key ->
        // Decode the route's stringly-typed mediaTypes into the domain enum once,
        // at the navigation boundary, so the VM only sees the typed context.
        val sectionContext = remember(key) {
            LibrarySectionContext(
                title = key.title,
                parentId = key.parentId,
                collectionType = key.collectionType,
                sortBy = key.sortBy,
                mediaTypes = key.mediaTypes.mapNotNull { name ->
                    runCatching { MediaType.valueOf(name) }.getOrNull()
                },
                genre = key.genre,
                tag = key.tag,
            )
        }
        LibraryScreen(
            sectionContext = sectionContext,
            onBack = { navigator.goBack() },
            onItemClick = { itemId, mediaType, parentId, itemName ->
                navigator.navigatePhotoAware(itemId, mediaType, parentId, itemName)
            },
            onSmartPlaylistsClick = { navigator.navigate(Route.SmartPlaylists) },
            onMoodPlaylistsClick = { navigator.navigate(Route.MoodPlaylists) },
            onPlaylistsClick = { navigator.navigate(Route.Playlists) },
            onOpenDownloadDetail = { itemId, openDownloadSheet ->
                navigator.navigate(Route.MediaDetail(itemId, openDownloadSheet))
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
