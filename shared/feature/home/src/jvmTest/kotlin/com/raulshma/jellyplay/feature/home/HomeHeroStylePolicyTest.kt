package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.core.model.HomeHeroStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins [homeHeroStyleDecision] — the hero presentation fold. The CAROUSEL
 * path is the regression bar: its output must be the zero-argument
 * [HomeHeroStyleDecision] default instance, byte-for-byte the shipped hero,
 * on every form factor and pool size.
 */
class HomeHeroStylePolicyTest {

    private val decision = HomeHeroStyleDecision()

    // -- CAROUSEL: the shipped behavior, byte-for-byte ------------------------

    @Test
    fun `carousel is the default decision on touch`() {
        assertEquals(decision, homeHeroStyleDecision(HomeHeroStyle.CAROUSEL, isTv = false, slideshowIntervalSec = 8, candidateCount = 3))
    }

    @Test
    fun `carousel is the default decision on tv`() {
        assertEquals(decision, homeHeroStyleDecision(HomeHeroStyle.CAROUSEL, isTv = true, slideshowIntervalSec = 8, candidateCount = 3))
    }

    @Test
    fun `carousel never auto-advances and never slides regardless of pool or interval`() {
        val decision = homeHeroStyleDecision(HomeHeroStyle.CAROUSEL, isTv = false, slideshowIntervalSec = 60, candidateCount = 1)
        assertNull(decision.autoAdvanceMs)
        assertFalse(decision.isSlider)
        assertTrue(decision.fullBleed)
        assertEquals(HomeHeroChromeVisibility.STANDARD, decision.chromeVisibility)
    }

    // -- SLIDESHOW ------------------------------------------------------------

    @Test
    fun `slideshow is full bleed with minimal chrome and no slider`() {
        val decision = homeHeroStyleDecision(HomeHeroStyle.SLIDESHOW, isTv = false, slideshowIntervalSec = 8, candidateCount = 3)
        assertTrue(decision.fullBleed)
        assertEquals(HomeHeroChromeVisibility.MINIMAL, decision.chromeVisibility)
        assertFalse(decision.isSlider)
    }

    @Test
    fun `slideshow auto-advance carries the pref interval in ms`() {
        assertEquals(8_000L, homeHeroStyleDecision(HomeHeroStyle.SLIDESHOW, isTv = false, slideshowIntervalSec = 8, candidateCount = 3).autoAdvanceMs)
        assertEquals(30_000L, homeHeroStyleDecision(HomeHeroStyle.SLIDESHOW, isTv = false, slideshowIntervalSec = 30, candidateCount = 3).autoAdvanceMs)
    }

    @Test
    fun `slideshow clamps an out-of-range interval to the store's write range`() {
        assertEquals(3_000L, homeHeroStyleDecision(HomeHeroStyle.SLIDESHOW, isTv = false, slideshowIntervalSec = 0, candidateCount = 3).autoAdvanceMs)
        assertEquals(60_000L, homeHeroStyleDecision(HomeHeroStyle.SLIDESHOW, isTv = false, slideshowIntervalSec = 999, candidateCount = 3).autoAdvanceMs)
    }

    @Test
    fun `slideshow with a single candidate has nothing to advance to`() {
        assertNull(homeHeroStyleDecision(HomeHeroStyle.SLIDESHOW, isTv = false, slideshowIntervalSec = 8, candidateCount = 1).autoAdvanceMs)
        assertNull(homeHeroStyleDecision(HomeHeroStyle.SLIDESHOW, isTv = false, slideshowIntervalSec = 8, candidateCount = 0).autoAdvanceMs)
    }

    @Test
    fun `slideshow keeps the interval on tv - the controller's tv rotation-off gate still holds`() {
        // The policy stays form-factor honest (the interval arms wherever the
        // style means it to); the TV suppression lives in the controller's
        // initialAutoRotateEnabled gate, not here — this test pins that the
        // policy doesn't second-guess it.
        assertEquals(8_000L, homeHeroStyleDecision(HomeHeroStyle.SLIDESHOW, isTv = true, slideshowIntervalSec = 8, candidateCount = 3).autoAdvanceMs)
    }

    // -- DETAILED -------------------------------------------------------------

    @Test
    fun `detailed is the contained rich spotlight`() {
        val decision = homeHeroStyleDecision(HomeHeroStyle.DETAILED, isTv = false, slideshowIntervalSec = 8, candidateCount = 3)
        assertFalse(decision.fullBleed)
        assertEquals(HomeHeroChromeVisibility.RICH, decision.chromeVisibility)
        assertNull(decision.autoAdvanceMs)
        assertFalse(decision.isSlider)
    }

    @Test
    fun `detailed keeps the shipped rotation cadence`() {
        assertNull(homeHeroStyleDecision(HomeHeroStyle.DETAILED, isTv = true, slideshowIntervalSec = 8, candidateCount = 3).autoAdvanceMs)
    }

    // -- TV_SLIDER ------------------------------------------------------------

    @Test
    fun `tv slider is a full bleed standard-chrome slider on tv with a pageable pool`() {
        val decision = homeHeroStyleDecision(HomeHeroStyle.TV_SLIDER, isTv = true, slideshowIntervalSec = 8, candidateCount = 3)
        assertTrue(decision.isSlider)
        assertTrue(decision.fullBleed)
        assertEquals(HomeHeroChromeVisibility.STANDARD, decision.chromeVisibility)
        assertNull(decision.autoAdvanceMs)
    }

    @Test
    fun `tv slider folds back to the carousel flags on touch`() {
        assertEquals(decision, homeHeroStyleDecision(HomeHeroStyle.TV_SLIDER, isTv = false, slideshowIntervalSec = 8, candidateCount = 3))
    }

    @Test
    fun `tv slider folds back to the carousel flags when the pool cannot page`() {
        assertEquals(decision, homeHeroStyleDecision(HomeHeroStyle.TV_SLIDER, isTv = true, slideshowIntervalSec = 8, candidateCount = 1))
        assertEquals(decision, homeHeroStyleDecision(HomeHeroStyle.TV_SLIDER, isTv = true, slideshowIntervalSec = 8, candidateCount = 0))
    }

    // -- the default instance IS the carousel output --------------------------

    @Test
    fun `the default decision instance renders the shipped hero`() {
        assertTrue(decision.fullBleed)
        assertEquals(HomeHeroChromeVisibility.STANDARD, decision.chromeVisibility)
        assertNull(decision.autoAdvanceMs)
        assertFalse(decision.isSlider)
    }
}
