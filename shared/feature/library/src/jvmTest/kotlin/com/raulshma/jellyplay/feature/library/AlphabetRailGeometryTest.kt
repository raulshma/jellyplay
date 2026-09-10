package com.raulshma.jellyplay.feature.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the alphabet rail's extracted geometry core (see [AlphabetRailGeometry]):
 * the gaussian fisheye lens edges, the pixel→letter mapping including the
 * `#`/misc bucket, and the rail-end + degenerate-row clamps. Pure math — no
 * Compose, no dispatcher.
 */
class AlphabetRailGeometryTest {

    /** 5 rail letters (the screen feeds `jumpIndexByLetter`'s key order, `#` included), 10 px rows. */
    private val letters = listOf('#', 'A', 'B', 'M', 'Z')
    private val geometry = AlphabetRailGeometry(letters, rowPx = 10f)

    @Test
    fun fisheye_is_flat_without_a_touch() {
        letters.indices.forEach { index ->
            assertEquals(1f, geometry.fisheyeScaleAt(index, touchIndex = null))
        }
    }

    @Test
    fun fisheye_peaks_at_the_touched_letter() {
        assertEquals(2.5f, geometry.fisheyeScaleAt(index = 2, touchIndex = 2f))
    }

    @Test
    fun fisheye_tapers_with_distance_and_never_dips_below_one() {
        val near = geometry.fisheyeScaleAt(index = 1, touchIndex = 0f)
        val mid = geometry.fisheyeScaleAt(index = 2, touchIndex = 0f)
        val far = geometry.fisheyeScaleAt(index = 4, touchIndex = 0f)
        assertTrue(near > mid)
        assertTrue(mid > 1f)
        // Gaussian tail four letters out is essentially flat again.
        assertTrue(far > 1f && far < 1.02f)
        // Fractional touch positions sit between the integer bell curves.
        assertTrue(geometry.fisheyeScaleAt(index = 0, touchIndex = 0.4f) < 2.5f)
    }

    @Test
    fun index_maps_pixels_to_fractional_letter_positions() {
        assertEquals(0f, geometry.indexAt(0f))
        assertEquals(1.5f, geometry.indexAt(15f))
        assertEquals(2f, geometry.indexAt(20f))
    }

    @Test
    fun jump_target_folds_pixels_to_letters_including_the_misc_bucket() {
        assertEquals('#', geometry.letterAt(5f))
        assertEquals('M', geometry.letterAt(35f))
    }

    @Test
    fun index_clamps_at_both_rail_ends() {
        assertEquals(0f, geometry.indexAt(-100f))
        assertEquals(letters.lastIndex.toFloat(), geometry.indexAt(1000f))
        assertEquals(letters.first(), geometry.letterAt(-1f))
        assertEquals(letters.last(), geometry.letterAt(1000f))
    }

    @Test
    fun zero_row_height_degenerates_to_the_first_letter() {
        val flat = AlphabetRailGeometry(letters, rowPx = 0f)
        assertEquals(0f, flat.indexAt(123f))
        assertEquals(letters.first(), flat.letterAt(123f))
    }
}
