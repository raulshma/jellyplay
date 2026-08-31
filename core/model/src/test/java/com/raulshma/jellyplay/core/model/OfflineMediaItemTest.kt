package com.raulshma.jellyplay.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the [OfflineMediaItem.toMediaItem] adapter, focusing on the
 * watched-state normalization that prevents offline episode cards from
 * showing "0m left" for items the server left with a high resume position
 * but `played = false`.
 */
class OfflineMediaItemTest {

    private val runtime = 10_000L * 10_000L // 10s in ticks (arbitrary; well under an hour)

    private fun episode(
        runTimeTicks: Long? = runtime,
        playbackPositionTicks: Long? = null,
        isPlayed: Boolean = false,
    ) = OfflineMediaItem(
        id = "ep-1",
        name = "Episode 1",
        mediaType = MediaType.EPISODE,
        runTimeTicks = runTimeTicks,
        playbackPositionTicks = playbackPositionTicks,
        isPlayed = isPlayed,
    )

    @Test
    fun toMediaItem_flagsWatchedWhenPositionAtOrAboveThreshold() {
        // 96% resume — above the 95% watched threshold.
        val pos = (runtime.toDouble() * 0.96).toLong()

        val item = episode(playbackPositionTicks = pos, isPlayed = false).toMediaItem()

        assertTrue("expected isPlayed=true at 96% resume", item.isPlayed)
        assertNull("position should be null when treated as watched", item.playbackPositionTicks)
    }

    @Test
    fun toMediaItem_keepsProgressWhenBelowThreshold() {
        val pos = (runtime.toDouble() * 0.50).toLong()

        val item = episode(playbackPositionTicks = pos, isPlayed = false).toMediaItem()

        assertFalse(item.isPlayed)
        assertEquals(pos, item.playbackPositionTicks)
    }

    @Test
    fun toMediaItem_respectsExplicitIsPlayedOverPosition() {
        // Explicitly played with a low resume position — still watched, and the
        // position is cleared so the card shows "Watched" not "Xm left".
        val pos = (runtime.toDouble() * 0.10).toLong()

        val item = episode(playbackPositionTicks = pos, isPlayed = true).toMediaItem()

        assertTrue(item.isPlayed)
        assertNull(item.playbackPositionTicks)
    }

    @Test
    fun toMediaItem_nullRuntimeLeavesRawStateUntouched() {
        // No runtime known — cannot compute a fraction, so do not normalize.
        val pos = 5_000_000L

        val item = episode(runTimeTicks = null, playbackPositionTicks = pos, isPlayed = false).toMediaItem()

        assertFalse(item.isPlayed)
        assertEquals(pos, item.playbackPositionTicks)
    }

    @Test
    fun toMediaItem_preservesRuntimeAndMetadataThroughNormalization() {
        val pos = (runtime.toDouble() * 0.97).toLong()

        val item = OfflineMediaItem(
            id = "ep-2",
            name = "Pilot",
            mediaType = MediaType.EPISODE,
            runTimeTicks = runtime,
            playbackPositionTicks = pos,
            isPlayed = false,
            seriesId = "series-1",
            seasonId = "season-1",
            seasonNumber = 1,
            episodeNumber = 1,
            seriesName = "Test Series",
        ).toMediaItem()

        // runTimeTicks must survive normalization so the card can still show
        // total runtime alongside the watched badge.
        assertEquals(runtime, item.runTimeTicks)
        assertEquals("series-1", item.seriesId)
        assertEquals("season-1", item.seasonId)
        assertEquals(1, item.episodeNumber)
        assertEquals("Test Series", item.seriesName)
        assertTrue(item.isPlayed)
    }

    @Test
    fun toMediaItem_zeroRuntimeDoesNotNormalize() {
        // Guard against divide-by-zero / spurious watched at rt == 0.
        val item = episode(runTimeTicks = 0L, playbackPositionTicks = 1L, isPlayed = false).toMediaItem()

        assertFalse(item.isPlayed)
    }

    @Test
    fun toMediaItem_mapsUnplayedEpisodeCountToBadgeCount() {
        val item = OfflineMediaItem(
            id = "series-1",
            name = "Test Series",
            mediaType = MediaType.SERIES,
            unplayedEpisodeCount = 5,
        ).toMediaItem()

        assertEquals(5, item.unplayedItemCount)
    }

    @Test
    fun toMediaItem_nullOrZeroUnplayedEpisodeCountStaysNull() {
        // Null (non-series rows, or every downloaded episode watched) and an
        // explicit 0 both suppress the badge — it only renders for a positive
        // count, matching the online cards.
        assertNull(episode().toMediaItem().unplayedItemCount)
        assertNull(episode().copy(unplayedEpisodeCount = 0).toMediaItem().unplayedItemCount)
    }
}
