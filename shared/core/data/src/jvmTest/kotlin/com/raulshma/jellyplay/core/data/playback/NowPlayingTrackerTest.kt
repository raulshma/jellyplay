package com.raulshma.jellyplay.core.data.playback

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.BeforeTest
import kotlin.test.Test

class NowPlayingTrackerTest {

    private lateinit var tracker: NowPlayingTracker

    @BeforeTest
    fun setUp() {
        tracker = NowPlayingTracker()
    }

    @Test
    fun initialValues_matchTheManagerPreExtractionDefaults() {
        assertNull(tracker.currentPlayingItemId.value)
        assertEquals("", tracker.title.value)
        assertEquals("", tracker.artist.value)
        assertNull(tracker.artistId.value)
        assertEquals("", tracker.album.value)
        assertEquals("", tracker.albumArtUrl.value)
    }

    @Test
    fun publishDetail_writesAllSixFields() {
        tracker.publishDetail(
            itemId = "item-1",
            title = "Track One",
            artist = "Artist A",
            artistId = "artist-a",
            album = "Album X",
            albumArtUrl = "https://example.com/art.jpg",
        )

        assertEquals("item-1", tracker.currentPlayingItemId.value)
        assertEquals("Track One", tracker.title.value)
        assertEquals("Artist A", tracker.artist.value)
        assertEquals("artist-a", tracker.artistId.value)
        assertEquals("Album X", tracker.album.value)
        assertEquals("https://example.com/art.jpg", tracker.albumArtUrl.value)
    }

    @Test
    fun publishQueueItem_writesFiveFieldsAndLeavesArtistIdNull() {
        tracker.publishQueueItem(queueItem(id = "item-2"))

        assertEquals("item-2", tracker.currentPlayingItemId.value)
        assertEquals("Nightcall", tracker.title.value)
        assertEquals("Kavinsky", tracker.artist.value)
        assertNull(tracker.artistId.value)
        assertEquals("OutRun", tracker.album.value)
        assertEquals("https://example.com/nightcall.jpg", tracker.albumArtUrl.value)
    }

    @Test
    fun publishQueueItem_leavesArtistIdAtItsPreviousDetailValue() {
        tracker.publishDetail(
            itemId = "item-1",
            title = "Track One",
            artist = "Artist A",
            artistId = "artist-a",
            album = "Album X",
            albumArtUrl = "https://example.com/art.jpg",
        )

        // The stale-artist scenario: a transition carries no artist id, so
        // the previous track's artistId survives it.
        tracker.publishQueueItem(queueItem(id = "item-2"))

        assertEquals("item-2", tracker.currentPlayingItemId.value)
        assertEquals("Kavinsky", tracker.artist.value)
        assertEquals("artist-a", tracker.artistId.value)
    }

    @Test
    fun publishQueueItem_coalescesNullAlbumAndImageUrlToEmptyStrings() {
        tracker.publishQueueItem(queueItem(id = "item-3", album = null, imageUrl = null))

        assertEquals("", tracker.album.value)
        assertEquals("", tracker.albumArtUrl.value)
    }

    @Test
    fun publishLocalFile_writesFourFieldsAndLeavesArtistIdAndAlbumArtUrl() {
        tracker.publishLocalFile(
            itemId = "item-4",
            title = "Cached Track",
            artist = "Offline Artist",
            album = "",
        )

        assertEquals("item-4", tracker.currentPlayingItemId.value)
        assertEquals("Cached Track", tracker.title.value)
        assertEquals("Offline Artist", tracker.artist.value)
        assertEquals("", tracker.album.value)
        assertNull(tracker.artistId.value)
        assertEquals("", tracker.albumArtUrl.value)
    }

    @Test
    fun publishLocalFile_leavesArtistIdAndAlbumArtUrlAtTheirPreviousDetailValues() {
        tracker.publishDetail(
            itemId = "item-1",
            title = "Track One",
            artist = "Artist A",
            artistId = "artist-a",
            album = "Album X",
            albumArtUrl = "https://example.com/art.jpg",
        )

        tracker.publishLocalFile(
            itemId = "item-4",
            title = "Cached Track",
            artist = "Offline Artist",
            album = "",
        )

        assertEquals("artist-a", tracker.artistId.value)
        assertEquals("https://example.com/art.jpg", tracker.albumArtUrl.value)
    }

    @Test
    fun clear_resetsFiveFieldsButLeavesArtistId() {
        tracker.publishDetail(
            itemId = "item-1",
            title = "Track One",
            artist = "Artist A",
            artistId = "artist-a",
            album = "Album X",
            albumArtUrl = "https://example.com/art.jpg",
        )

        tracker.clear()

        assertNull(tracker.currentPlayingItemId.value)
        assertEquals("", tracker.title.value)
        assertEquals("", tracker.artist.value)
        assertEquals("artist-a", tracker.artistId.value)
        assertEquals("", tracker.album.value)
        assertEquals("", tracker.albumArtUrl.value)
    }

    @Test
    fun exposedFlows_areTheSameInstancesAcrossPublishes() {
        val currentPlayingItemId = tracker.currentPlayingItemId
        val title = tracker.title
        val artist = tracker.artist
        val artistId = tracker.artistId
        val album = tracker.album
        val albumArtUrl = tracker.albumArtUrl

        tracker.publishDetail(
            itemId = "item-1",
            title = "Track One",
            artist = "Artist A",
            artistId = "artist-a",
            album = "Album X",
            albumArtUrl = "https://example.com/art.jpg",
        )
        tracker.publishQueueItem(queueItem(id = "item-2"))
        tracker.clear()

        assertSame(currentPlayingItemId, tracker.currentPlayingItemId)
        assertSame(title, tracker.title)
        assertSame(artist, tracker.artist)
        assertSame(artistId, tracker.artistId)
        assertSame(album, tracker.album)
        assertSame(albumArtUrl, tracker.albumArtUrl)
    }

    private fun queueItem(
        id: String,
        album: String? = "OutRun",
        imageUrl: String? = "https://example.com/nightcall.jpg",
    ): AudioQueueItem = AudioQueueItem(
        id = id,
        name = "Nightcall",
        artist = "Kavinsky",
        album = album,
        imageUrl = imageUrl,
        mediaSourceId = "media-source-1",
    )
}
