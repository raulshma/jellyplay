package com.raulshma.jellyplay.feature.book

import com.raulshma.jellyplay.core.ui.components.FisheyeRailMath
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the TOC tick rail's geometry core ([TocRailGeometry]) at its
 * feature-specific arms: the unclamped raw-row contract the drag
 * extrapolation consumes, the beyond-the-rail stepping with its full-TOC
 * clamps, and the delegation to the shared lens. The gaussian fisheye math
 * itself is pinned once in core:ui's `FisheyeRailMathTest`. Pure math — no
 * Compose, no dispatcher.
 */
class TocRailGeometryTest {

    /** 5 visible ticks (the ±2 window), 10 px rows, window entries 3..7 of a 20-tick TOC. */
    private val geometry = TocRailGeometry(windowSize = 5, rowPx = 10f)
    private val window = listOf(3, 4, 5, 6, 7)
    private val tocLastIndex = 19

    @Test
    fun fisheye_delegates_to_the_shared_lens() {
        assertEquals(FisheyeRailMath.fisheyeScaleAt(2, 2f), geometry.fisheyeScaleAt(2, 2f))
        // Not dragging → no lens, through the adapter too.
        assertEquals(1f, geometry.fisheyeScaleAt(1, touchRow = null))
    }

    @Test
    fun raw_row_stays_unclamped_for_the_extrapolation() {
        // The DELIBERATE delta from the alphabet rail: past-the-end raw rows
        // survive — the drag extrapolation needs the out-of-range values.
        assertEquals(-0.5f, geometry.rawRowAt(-5f))
        assertEquals(5.5f, geometry.rawRowAt(55f))
    }

    @Test
    fun window_row_clamps_into_the_rail() {
        assertEquals(0f, geometry.windowRowAt(-100f))
        assertEquals(1.5f, geometry.windowRowAt(15f))
        assertEquals(4f, geometry.windowRowAt(1000f))
    }

    @Test
    fun in_rail_rows_map_to_their_window_entries() {
        assertEquals(3, geometry.previewIndexAt(0f, window, tocLastIndex))
        assertEquals(5, geometry.previewIndexAt(25f, window, tocLastIndex))
        assertEquals(7, geometry.previewIndexAt(45f, window, tocLastIndex))
        // The second half of the LAST row is still the last row, not a step.
        assertEquals(7, geometry.previewIndexAt(49f, window, tocLastIndex))
    }

    @Test
    fun dragging_past_the_rail_keeps_stepping_one_chapter_per_row() {
        assertEquals(2, geometry.previewIndexAt(-10f, window, tocLastIndex))
        assertEquals(0, geometry.previewIndexAt(-25f, window, tocLastIndex))
        assertEquals(8, geometry.previewIndexAt(55f, window, tocLastIndex))
        assertEquals(9, geometry.previewIndexAt(65f, window, tocLastIndex))
    }

    @Test
    fun extrapolation_clamps_at_both_toc_ends() {
        assertEquals(0, geometry.previewIndexAt(-1000f, window, tocLastIndex))
        assertEquals(tocLastIndex, geometry.previewIndexAt(1000f, window, tocLastIndex))
    }

    @Test
    fun empty_window_degenerates_to_no_preview() {
        assertEquals(-1, geometry.previewIndexAt(25f, emptyList(), tocLastIndex))
    }

    @Test
    fun single_tick_window_still_scrubs() {
        val solo = TocRailGeometry(windowSize = 1, rowPx = 10f)
        assertEquals(4, solo.previewIndexAt(5f, listOf(4), tocLastIndex))
        assertEquals(5, solo.previewIndexAt(15f, listOf(4), tocLastIndex))
        assertEquals(3, solo.previewIndexAt(-5f, listOf(4), tocLastIndex))
        assertEquals(tocLastIndex, solo.previewIndexAt(1000f, listOf(4), tocLastIndex))
    }

    @Test
    fun zero_row_height_degenerates_to_the_first_window_entry() {
        val flat = TocRailGeometry(windowSize = 5, rowPx = 0f)
        assertEquals(3, flat.previewIndexAt(123f, window, tocLastIndex))
    }
}
