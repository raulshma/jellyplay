package com.raulshma.jellyplay.feature.book

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the time-remaining estimate both labels share (chapter and whole
 * book): epub.js location pages are 1024-char units, so words-per-page =
 * 1024 / 5.5 and minutes = pages × words-per-page ÷ the user's wpm.
 * Round-half-up keeps labels stable.
 */
class ReaderEstimatesTest {

    @Test
    fun `default reading speed yields the documented rate`() {
        // 1024 / 5.5 ≈ 186.2 words per location page; at 238 wpm a page is
        // ≈ 0.78 min → 12 pages ≈ 9.4 → 9 minutes.
        assertEquals(9, locationPagesMinutesRemaining(remainingPages = 12, wpm = 238))
    }

    @Test
    fun `faster readers finish chapters sooner`() {
        assertEquals(2, locationPagesMinutesRemaining(remainingPages = 12, wpm = 1000))
    }

    @Test
    fun `rounding is half-up and stable`() {
        // 3 pages × 186.18 words / 238 wpm ≈ 2.345 → 2; the same input must
        // always resolve identically.
        assertEquals(2, locationPagesMinutesRemaining(3, 238))
        assertEquals(2, locationPagesMinutesRemaining(3, 238))
        // 0.5-exact band input: 186.1818.../238 × 1 page ≈ 0.782 → 1 when ≥ .5 only.
        assertEquals(1, locationPagesMinutesRemaining(1, 238))
    }

    @Test
    fun `zero or negative pages report nothing left`() {
        assertEquals(0, locationPagesMinutesRemaining(0, 238))
        assertEquals(0, locationPagesMinutesRemaining(-3, 238))
    }

    @Test
    fun `a non-positive wpm degrades to one word per minute instead of dividing by zero`() {
        // 3 × 186.1818... = 558.545 → half-up → 559.
        assertEquals(559, locationPagesMinutesRemaining(remainingPages = 3, wpm = 0))
    }
}
