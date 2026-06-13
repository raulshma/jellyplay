package com.raulshma.jellyplay.core.ui.navigation

import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.isPhotoType

fun resolveItemDestination(
    itemId: String,
    mediaType: MediaType,
    parentId: String?,
    itemName: String = "",
): Route = when {
    mediaType.isPhotoType -> if (parentId != null) {
        Route.PhotoAlbum(parentId = parentId, folderName = "")
    } else {
        Route.PhotoViewer(itemId, parentId)
    }
    else -> Route.MediaDetail(itemId)
}
