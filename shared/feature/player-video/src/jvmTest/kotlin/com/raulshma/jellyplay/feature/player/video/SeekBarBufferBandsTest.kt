package com.raulshma.jellyplay.feature.player.video

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Matrix tests for the seek bar's buffered-band layout — the pure
 * range→fraction mapping behind [components.PlayerControls]' shaded bands.
 */
class SeekBarBufferBandsTest {

    private val duration = 1_000_000L

    @Test
    fun `bands map each range onto track fractions`() {
        assertEquals(
            listOf(
                SeekBarBufferBands.Band(0.1f, 0.2f),
                SeekBarBufferBands.Band(0.5f, 0.75f),
            ),
            SeekBarBufferBands.bands(listOf(100_000L..200_000L, 500_000L..750_000L), duration),
        )
    }

    @Test
    fun `bands shade nothing without a duration`() {
        assertEquals(emptyList(), SeekBarBufferBands.bands(listOf(0L..1_000L), 0L))
        assertEquals(emptyList(), SeekBarBufferBands.bands(listOf(0L..1_000L), -5L))
    }

    @Test
    fun `bands shade nothing for empty ranges`() {
        assertEquals(emptyList(), SeekBarBufferBands.bands(emptyList(), duration))
    }

    @Test
    fun `bands coerce out-of-bounds ranges into the track`() {
        assertEquals(
            listOf(SeekBarBufferBands.Band(0f, 1f)),
            SeekBarBufferBands.bands(listOf((-50_000L)..1_200_000L), duration),
        )
    }

    @Test
    fun `bands drop degenerate slivers`() {
        // Zero-width and inverted ranges never produce bands.
        assertEquals(emptyList(), SeekBarBufferBands.bands(listOf(100_000L..100_000L), duration))
        assertEquals(emptyList(), SeekBarBufferBands.bands(listOf(200_000L..100_000L), duration))
    }

    @Test
    fun `bands keep order stable across disjoint ranges`() {
        // Engines publish sorted+merged ranges; ordering is preserved as-is so
        // the draw loop and the published flow agree band-for-band.
        assertEquals(
            listOf(
                SeekBarBufferBands.Band(0f, 0.05f),
                SeekBarBufferBands.Band(0.9f, 1f),
            ),
            SeekBarBufferBands.bands(listOf(0L..50_000L, 900_000L..duration), duration),
        )
    }

    @Test
    fun `bands cover a full-buffer single range`() {
        assertEquals(
            listOf(SeekBarBufferBands.Band(0f, 1f)),
            SeekBarBufferBands.bands(listOf(0L..duration), duration),
        )
    }
}
