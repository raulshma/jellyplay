package com.raulshma.jellyplay.feature.search.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.isPhotoType
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.feature.search.SearchScreen

private fun Navigator.navigatePhotoAware(itemId: String, mediaType: MediaType, parentId: String?, itemName: String = "") {
    when (mediaType) {
        MediaType.PHOTO_FOLDER -> navigate(Route.PhotoAlbum(parentId = itemId, folderName = itemName))
        MediaType.PHOTO -> navigate(Route.PhotoViewer(itemId, parentId))
        else -> navigate(Route.MediaDetail(itemId))
    }
}

fun EntryProviderScope<NavKey>.searchSection(
    navigator: Navigator,
) {
    entry<Route.Search> {
        SearchScreen(
            onItemClick = { itemId, mediaType, parentId, itemName ->
                navigator.navigatePhotoAware(itemId, mediaType, parentId, itemName)
            },
            onNavigate = { route -> navigator.navigate(route) },
        )
    }
}
