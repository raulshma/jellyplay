package com.raulshma.jellyplay.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the shared offline-shelf rules — the type partition, the query-field
 * match and the finished-threshold read that home, library and downloads all
 * consume instead of declaring their own copies.
 */
class OfflineShelfTest {

    private fun item(
        id: String = "i1",
        mediaType: MediaType = MediaType.MOVIE,
        name: String = "Item",
        seriesName: String? = null,
        seasonName: String? = null,
        playedPercentage: Double = 0.0,
    ) = OfflineMediaItem(
        id = id,
        name = name,
        mediaType = mediaType,
        seriesName = seriesName,
        seasonName = seasonName,
        playedPercentage = playedPercentage,
    )

    @Test
    fun `video and music groups partition the top-level shelf types`() {
        assertEquals(OfflineMediaTypeGroup.VIDEO, item(mediaType = MediaType.MOVIE).typeGroup)
        assertEquals(OfflineMediaTypeGroup.VIDEO, item(mediaType = MediaType.SERIES).typeGroup)
        assertEquals(OfflineMediaTypeGroup.MUSIC, item(mediaType = MediaType.AUDIO).typeGroup)
        assertEquals(OfflineMediaTypeGroup.MUSIC, item(mediaType = MediaType.MUSIC).typeGroup)
        assertEquals(OfflineMediaTypeGroup.MUSIC, item(mediaType = MediaType.ALBUM).typeGroup)
        assertEquals(OfflineMediaTypeGroup.MUSIC, item(mediaType = MediaType.ARTIST).typeGroup)
        assertNull(item(mediaType = MediaType.PHOTO_FOLDER).typeGroup)
        assertNull(item(mediaType = MediaType.EPISODE).typeGroup)
    }

    @Test
    fun `query matches name series and season fields`() {
        val episode = item(name = "Pilot", seriesName = "The Show", seasonName = "Season 1")
        assertTrue(episode.matchesOfflineQuery("show"))
        assertTrue(episode.matchesOfflineQuery("SEASON"))
        assertTrue(episode.matchesOfflineQuery("pilot"))
        assertFalse(episode.matchesOfflineQuery("movie"))
    }

    @Test
    fun `finished threshold agrees with the display normalization`() {
        // playedPercentage is stored on a 0–100 scale (PlayedStateSync writes
        // position/runtime * 100), so the threshold is 95 on that scale.
        assertFalse(item(playedPercentage = 50.0).isFinishedOffline)
        assertFalse(item(playedPercentage = 94.9).isFinishedOffline)
        assertTrue(item(playedPercentage = 95.0).isFinishedOffline)
        assertTrue(item(playedPercentage = 100.0).isFinishedOffline)
    }
}
