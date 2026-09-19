package com.raulshma.jellyplay.feature.library

import com.raulshma.jellyplay.core.ui.components.FisheyeRailMath
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the alphabet rail's geometry core ([AlphabetRailGeometry]) at its
 * feature-specific arms: the rail-end + degenerate-row clamps, the
 * pixel→letter mapping including the `#`/misc bucket, and the delegation to
 * the shared lens. The gaussian fisheye math itself is pinned once in
 * core:ui's `FisheyeRailMathTest`. Pure math — no Compose, no dispatcher.
 */
class AlphabetRailGeometryTest {

    /** 5 rail letters (the screen feeds `jumpIndexByLetter`'s key order, `#` included), 10 px rows. */
    private val letters = listOf('#', 'A', 'B', 'M', 'Z')
    private val geometry = AlphabetRailGeometry(letters, rowPx = 10f)

    @Test
    fun fisheye_delegates_to_the_shared_lens() {
        assertEquals(FisheyeRailMath.fisheyeScaleAt(2, 2f), geometry.fisheyeScaleAt(2, 2f))
        // No touch → no lens, through the adapter too.
        assertEquals(1f, geometry.fisheyeScaleAt(1, touchIndex = null))
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
