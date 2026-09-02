package com.raulshma.jellyplay.core.ui.navigation

import com.raulshma.jellyplay.core.model.MediaType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the pure item-click routing table [resolveItemDestination]:
 *  - PHOTO_FOLDER opens the folder's own album ([Route.PhotoAlbum]) — note the
 *    folder's id becomes the album's `parentId`;
 *  - PHOTO opens the full-screen single-photo viewer carrying both the photo
 *    id and its (nullable) parent;
 *  - CHANNEL / LIVE_TV both open the channel detail with the clicked id and
 *    name;
 *  - every other media type lands on [Route.MediaDetail] keyed by item id only.
 *
 * [Navigator.navigatePhotoAware] is a one-line composition of these two pieces
 * and is intentionally not covered here (Navigator is composition-bound).
 */
class PhotoRoutingTest {

    @Test
    fun photoFolder_opensAlbum_ofTheFolderItself() {
        val destination = resolveItemDestination(
            itemId = "folder-1",
            mediaType = MediaType.PHOTO_FOLDER,
            parentId = "library-root",
            itemName = "Vacation 2025",
        )

        assertEquals(Route.PhotoAlbum(parentId = "folder-1", folderName = "Vacation 2025"), destination)
    }

    @Test
    fun photoFolder_defaultItemName_isEmptyFolderName() {
        val destination = resolveItemDestination("folder-1", MediaType.PHOTO_FOLDER, parentId = null)

        assertEquals(Route.PhotoAlbum(parentId = "folder-1", folderName = ""), destination)
    }

    @Test
    fun photo_opensViewer_withPhotoIdAndParent() {
        val destination = resolveItemDestination(
            itemId = "photo-9",
            mediaType = MediaType.PHOTO,
            parentId = "album-3",
        )

        assertEquals(Route.PhotoViewer(itemId = "photo-9", parentId = "album-3"), destination)
    }

    @Test
    fun photo_withNullParent_keepsNullParentInViewer() {
        val destination = resolveItemDestination("photo-9", MediaType.PHOTO, parentId = null)

        assertEquals(Route.PhotoViewer(itemId = "photo-9", parentId = null), destination)
    }

    @Test
    fun channel_opensChannelDetail_withClickedIdAndName() {
        val destination = resolveItemDestination("ch-1", MediaType.CHANNEL, parentId = null, itemName = "News 24")

        assertEquals(Route.ChannelDetail(channelId = "ch-1", channelName = "News 24"), destination)
    }

    @Test
    fun liveTv_routesExactlyLikeChannel() {
        val destination = resolveItemDestination("ch-2", MediaType.LIVE_TV, parentId = null, itemName = "Sports")

        assertEquals(Route.ChannelDetail(channelId = "ch-2", channelName = "Sports"), destination)
        assertTrue(destination is Route.ChannelDetail)
    }

    @Test
    fun videoTypes_fallThroughToMediaDetail_keyedByIdOnly() {
        for (type in listOf(MediaType.MOVIE, MediaType.EPISODE, MediaType.SERIES)) {
            assertEquals(
                Route.MediaDetail(itemId = "item-$type"),
                resolveItemDestination("item-$type", type, parentId = "ignored-parent", itemName = "ignored name"),
                "mediaType $type must open plain MediaDetail",
            )
        }
    }

    @Test
    fun unknownType_fallsThroughToMediaDetail() {
        val destination = resolveItemDestination("item-x", MediaType.UNKNOWN, parentId = null)

        assertEquals(Route.MediaDetail(itemId = "item-x"), destination)
    }

    @Test
    fun photoViewer_isFullScreenDestination() {
        // Contract the shell's layout-branch picker relies on: the photo viewer
        // renders in the bare full-screen layout, no top-level chrome.
        assertTrue(Route.PhotoViewer("p", null).isFullScreen)
    }
}
