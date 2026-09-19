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

/**
 * The [LibraryScreen] wiring every library entry shares — the tab
 * ([Route.Library]), the browse deep-link ([Route.LibraryBrowse]) and the
 * section deep-link ([Route.LibrarySection]) used to hand-copy this block
 * byte-for-byte. The `HomeCallbacks` precedent, bundle shape: the
 * navigator-derived lambdas are written once and remembered per navigator
 * lifetime so the screen subtree sees one stable instance instead of fresh
 * lambda allocations on every recomposition.
 */
private class LibraryEntryCallbacks(
    val onItemClick: (itemId: String, mediaType: MediaType, parentId: String?, itemName: String) -> Unit,
    val onSmartPlaylistsClick: () -> Unit,
    val onMoodPlaylistsClick: () -> Unit,
    val onPlaylistsClick: () -> Unit,
    val onOpenDownloadDetail: (itemId: String, openDownloadSheet: Boolean) -> Unit,
    val onBack: () -> Unit,
)

/** Renders [LibraryScreen] over the shared wiring; only the section entry adds [sectionContext] (and with it a back affordance — the tab and browse entries are top-level, no back). */
@Composable
private fun LibraryEntry(
    navigator: Navigator,
    sectionContext: LibrarySectionContext? = null,
) {
    val callbacks = remember(navigator) {
        LibraryEntryCallbacks(
            onItemClick = { itemId, mediaType, parentId, itemName ->
                navigator.navigatePhotoAware(itemId, mediaType, parentId, itemName)
            },
            onSmartPlaylistsClick = { navigator.navigate(Route.SmartPlaylists) },
            onMoodPlaylistsClick = { navigator.navigate(Route.MoodPlaylists) },
            onPlaylistsClick = { navigator.navigate(Route.Playlists) },
            onOpenDownloadDetail = { itemId, openDownloadSheet ->
                navigator.navigate(Route.MediaDetail(itemId, openDownloadSheet))
            },
            onBack = { navigator.goBack() },
        )
    }
    LibraryScreen(
        onItemClick = callbacks.onItemClick,
        onSmartPlaylistsClick = callbacks.onSmartPlaylistsClick,
        onMoodPlaylistsClick = callbacks.onMoodPlaylistsClick,
        onPlaylistsClick = callbacks.onPlaylistsClick,
        onOpenDownloadDetail = callbacks.onOpenDownloadDetail,
        sectionContext = sectionContext,
        onBack = callbacks.onBack.takeIf { sectionContext != null },
    )
}

fun EntryProviderScope<NavKey>.librarySection(navigator: Navigator) {
    entry<Route.Library> {
        LibraryEntry(navigator)
    }

    entry<Route.LibraryBrowse> {
        LibraryEntry(navigator)
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
        LibraryEntry(navigator, sectionContext)
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
