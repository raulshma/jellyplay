package com.raulshma.jellyplay.core.ui.navigation

import com.raulshma.jellyplay.core.model.MediaType

/**
 * Resolves the destination [Route] for an item click, branching on the item's
 * media type: PHOTO_FOLDER opens the folder's album, PHOTO opens the single
 * photo viewer, CHANNEL/LIVE_TV opens the live tv channel detail screen, and
 * anything else opens the media detail screen.
 */
fun resolveItemDestination(
    itemId: String,
    mediaType: MediaType,
    parentId: String?,
    itemName: String = "",
): Route = when (mediaType) {
    MediaType.PHOTO_FOLDER -> Route.PhotoAlbum(parentId = itemId, folderName = itemName)
    MediaType.PHOTO -> Route.PhotoViewer(itemId, parentId)
    MediaType.CHANNEL, MediaType.LIVE_TV ->
        Route.ChannelDetail(channelId = itemId, channelName = itemName)
    else -> Route.MediaDetail(itemId)
}

/**
 * Navigate to the photo-aware destination for an item. Centralized here so the
 * feature modules that route item clicks share a single implementation instead
 * of copy-pasting the `when` branch.
 */
fun Navigator.navigatePhotoAware(
    itemId: String,
    mediaType: MediaType,
    parentId: String?,
    itemName: String = "",
) {
    navigate(resolveItemDestination(itemId, mediaType, parentId, itemName))
}
