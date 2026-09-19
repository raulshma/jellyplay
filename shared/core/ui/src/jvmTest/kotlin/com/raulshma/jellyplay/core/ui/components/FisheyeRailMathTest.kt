package com.raulshma.jellyplay.core.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the shared fisheye-rail math (see [FisheyeRailMath]) — the gaussian
 * lens edges and the fractional pixel→row mapping — once for both consumers
 * (the library's alphabet jump rail and the reader's TOC tick rail, whose
 * suites keep only their clamping/extrapolation adapter arms). Pure math —
 * no Compose, no dispatcher.
 */
class FisheyeRailMathTest {

    @Test
    fun fisheye_is_flat_without_a_touch() {
        for (index in 0..4) {
            assertEquals(1f, FisheyeRailMath.fisheyeScaleAt(index, touchRow = null))
        }
    }

    @Test
    fun fisheye_peaks_at_the_touched_row() {
        assertEquals(FisheyeRailMath.FISHEYE_PEAK, FisheyeRailMath.fisheyeScaleAt(index = 2, touchRow = 2f))
    }

    @Test
    fun fisheye_tapers_with_distance_and_never_dips_below_one() {
        val near = FisheyeRailMath.fisheyeScaleAt(index = 1, touchRow = 0f)
        val mid = FisheyeRailMath.fisheyeScaleAt(index = 2, touchRow = 0f)
        val far = FisheyeRailMath.fisheyeScaleAt(index = 4, touchRow = 0f)
        assertTrue(near > mid)
        assertTrue(mid > 1f)
        // Gaussian tail four rows out is essentially flat again.
        assertTrue(far > 1f && far < 1.02f)
        // Fractional touch positions sit between the integer bell curves.
        assertTrue(FisheyeRailMath.fisheyeScaleAt(index = 0, touchRow = 0.4f) < FisheyeRailMath.FISHEYE_PEAK)
    }

    @Test
    fun raw_row_keeps_the_fraction_and_the_sign() {
        assertEquals(0f, FisheyeRailMath.rawRowAt(0f, rowPx = 10f))
        assertEquals(1.5f, FisheyeRailMath.rawRowAt(15f, rowPx = 10f))
        assertEquals(-0.5f, FisheyeRailMath.rawRowAt(-5f, rowPx = 10f))
        assertEquals(5.5f, FisheyeRailMath.rawRowAt(55f, rowPx = 10f))
    }

    @Test
    fun raw_row_is_unclamped_by_design() {
        // No rail-end coercion here: the library clamps at its adapter, and the
        // reader's drag extrapolation CONSUMES these out-of-range values.
        assertEquals(-7f, FisheyeRailMath.rawRowAt(-70f, rowPx = 10f))
        assertEquals(100f, FisheyeRailMath.rawRowAt(1000f, rowPx = 10f))
    }

    @Test
    fun zero_row_height_degenerates_to_row_zero() {
        assertEquals(0f, FisheyeRailMath.rawRowAt(123f, rowPx = 0f))
        assertEquals(0f, FisheyeRailMath.rawRowAt(123f, rowPx = -1f))
    }
}
