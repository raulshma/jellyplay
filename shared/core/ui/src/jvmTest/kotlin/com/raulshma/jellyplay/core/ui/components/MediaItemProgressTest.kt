package com.raulshma.jellyplay.core.ui.components

import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pins the pure [MediaItem.progressFraction] contract consumed by every media
 * card's progress bar (the `remember*` memo wrapper is composition-bound):
 *
 *  - a null playback position OR a null / zero / negative runtime yields null
 *    (no bar rendered — the call site treats null as "hide");
 *  - otherwise the fraction is position/runtime clamped into 0f..1f, so
 *    over-run positions and negative positions can never over- or under-draw
 *    the bar.
 */
class MediaItemProgressTest {

    private fun item(
        positionTicks: Long?,
        runtimeTicks: Long?,
    ) = MediaItem(
        id = "m1",
        name = "Movie",
        mediaType = MediaType.MOVIE,
        runTimeTicks = runtimeTicks,
        playbackPositionTicks = positionTicks,
    )

    @Test
    fun nullPosition_yieldsNullEvenWithRuntime() {
        assertNull(item(positionTicks = null, runtimeTicks = 100L).progressFraction())
    }

    @Test
    fun nullRuntime_yieldsNullEvenWithPosition() {
        assertNull(item(positionTicks = 25L, runtimeTicks = null).progressFraction())
    }

    @Test
    fun zeroRuntime_yieldsNull() {
        assertNull(item(positionTicks = 25L, runtimeTicks = 0L).progressFraction())
    }

    @Test
    fun negativeRuntime_yieldsNull() {
        assertNull(item(positionTicks = 25L, runtimeTicks = -50L).progressFraction())
    }

    @Test
    fun zeroPosition_yieldsZeroFraction() {
        assertEquals(0f, item(positionTicks = 0L, runtimeTicks = 100L).progressFraction())
    }

    @Test
    fun midPosition_yieldsExactFraction() {
        val fraction = item(positionTicks = 25L, runtimeTicks = 100L).progressFraction()

        assertEquals(0.25f, fraction)
    }

    @Test
    fun positionBeyondRuntime_clampsToOne() {
        assertEquals(1f, item(positionTicks = 150L, runtimeTicks = 100L).progressFraction())
    }

    @Test
    fun negativePosition_clampsToZero() {
        assertEquals(0f, item(positionTicks = -10L, runtimeTicks = 100L).progressFraction())
    }

    @Test
    fun fullPosition_yieldsExactlyOne() {
        assertEquals(1f, item(positionTicks = 100L, runtimeTicks = 100L).progressFraction())
    }
}
