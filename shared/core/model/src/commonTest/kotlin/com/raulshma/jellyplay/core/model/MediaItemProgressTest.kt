package com.raulshma.jellyplay.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pins the [MediaItem.progressFraction] resume-math contract the detail,
 * newsletter, and episode-picker screens share: null/invalid ticks yield
 * null, the fraction clamps into 0..1, and the explicit [positionTicks]
 * override lets smart-play targets divide by the resolver's start position
 * instead of the saved playback position.
 */
class MediaItemProgressTest {

    private fun item(
        playbackPositionTicks: Long? = null,
        runTimeTicks: Long? = null,
    ) = MediaItem(
        id = "i",
        name = "n",
        mediaType = MediaType.MOVIE,
        runTimeTicks = runTimeTicks,
        playbackPositionTicks = playbackPositionTicks,
    )

    // ── null / zero handling ─────────────────────────────────────────────────

    @Test
    fun nullPosition_isNull() {
        assertNull(item(playbackPositionTicks = null, runTimeTicks = 100L).progressFraction())
    }

    @Test
    fun nullRuntime_isNull() {
        assertNull(item(playbackPositionTicks = 25L, runTimeTicks = null).progressFraction())
    }

    @Test
    fun zeroRuntime_isNull() {
        assertNull(item(playbackPositionTicks = 25L, runTimeTicks = 0L).progressFraction())
    }

    @Test
    fun negativeRuntime_isNull() {
        assertNull(item(playbackPositionTicks = 25L, runTimeTicks = -5L).progressFraction())
    }

    // ── clamping + normal fraction ───────────────────────────────────────────

    @Test
    fun zeroPosition_isZeroFraction_notNull() {
        assertEquals(0f, item(playbackPositionTicks = 0L, runTimeTicks = 100L).progressFraction())
    }

    @Test
    fun negativePosition_clampsToZero() {
        assertEquals(0f, item(playbackPositionTicks = -10L, runTimeTicks = 100L).progressFraction())
    }

    @Test
    fun positionBeyondRuntime_clampsToOne() {
        assertEquals(1f, item(playbackPositionTicks = 150L, runTimeTicks = 100L).progressFraction())
    }

    @Test
    fun positionAtRuntime_isExactlyOne() {
        assertEquals(1f, item(playbackPositionTicks = 100L, runTimeTicks = 100L).progressFraction())
    }

    @Test
    fun normalFraction_isPositionOverRuntime() {
        val fraction = item(playbackPositionTicks = 25L, runTimeTicks = 100L).progressFraction()
        assertEquals(0.25f, fraction!!, 0.0001f)
    }

    // ── explicit positionTicks override (smart-play targets) ─────────────────

    @Test
    fun explicitPosition_overridesSavedPlaybackPosition() {
        val episode = item(playbackPositionTicks = 90L, runTimeTicks = 100L)
        val fraction = episode.progressFraction(positionTicks = 25L)
        assertEquals(0.25f, fraction!!, 0.0001f)
    }

    @Test
    fun explicitNullPosition_isNull_evenWhenSavedPositionExists() {
        val episode = item(playbackPositionTicks = 90L, runTimeTicks = 100L)
        assertNull(episode.progressFraction(positionTicks = null))
    }
}
