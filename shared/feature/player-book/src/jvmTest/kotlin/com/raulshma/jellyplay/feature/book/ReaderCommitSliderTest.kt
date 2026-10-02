package com.raulshma.jellyplay.feature.book

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the commit-on-settle slider's settle folds ([CommitSlider] call sites
 * — ReaderCommitSlider.kt): the value math each of the three former
 * hand-rolled slider copies applied on `onValueChangeFinished`, now shared
 * pure functions.
 */
class ReaderCommitSliderTest {

    @Test
    fun `settings commits round then clamp into the band`() {
        assertEquals(105, committedInRange(104.6f, 80..200))
        assertEquals(104, committedInRange(104.4f, 80..200))
        // Below/above the band clamps — the drag cannot leave the range.
        assertEquals(80, committedInRange(79.4f, 80..200))
        assertEquals(80, committedInRange(-10f, 80..200))
        assertEquals(200, committedInRange(500f, 80..200))
    }

    @Test
    fun `brightness commits round and clamp into the percent band`() {
        assertEquals(33, committedBrightnessPct(33.4f))
        assertEquals(67, committedBrightnessPct(66.6f))
        // 100 renders no veil at all (BrightnessDimOverlay's early return).
        assertEquals(100, committedBrightnessPct(100f))
        assertEquals(0, committedBrightnessPct(-3f))
        assertEquals(100, committedBrightnessPct(250f))
    }

    @Test
    fun `page slider floors the 1-based position and lands 0-based`() {
        // Floor, not round: 1.9 sits on page 1 (rounding would jump to page 2).
        assertEquals(0, pagedSliderSeekTarget(1.9f, 100))
        assertEquals(1, pagedSliderSeekTarget(2.0f, 100))
        assertEquals(0, pagedSliderSeekTarget(1.0f, 100))
        assertEquals(98, pagedSliderSeekTarget(99.9f, 100))
        // Past the end clamps onto the last page.
        assertEquals(99, pagedSliderSeekTarget(150f, 100))
        // Degenerate 0-page book: clamped, not thrown.
        assertEquals(0, pagedSliderSeekTarget(1.0f, 0))
    }
}
