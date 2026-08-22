package com.raulshma.jellyplay.core.model

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

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

        assertTrue(
item.isPlayed,
"expected isPlayed=true at 96% resume",
)
        assertNull(
item.playbackPositionTicks,
"position should be null when treated as watched",
)
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
        assertEquals(
item.seriesId,
"series-1",
)
        assertEquals(
item.seasonId,
"season-1",
)
        assertEquals(1, item.episodeNumber)
        assertEquals(
item.seriesName,
"Test Series",
)
        assertTrue(item.isPlayed)
    }

    @Test
    fun toMediaItem_zeroRuntimeDoesNotNormalize() {
        // Guard against divide-by-zero / spurious watched at rt == 0.
        val item = episode(runTimeTicks = 0L, playbackPositionTicks = 1L, isPlayed = false).toMediaItem()

        assertFalse(item.isPlayed)
    }
}
