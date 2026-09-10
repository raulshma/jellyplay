package com.raulshma.jellyplay.core.ui.components

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the shared hide-on-scroll visibility policy consumed by the home dock
 * (`HomeScreen.kt`'s `HomeTopDockScrim`, LazyListState feed) and the floating
 * bottom nav (`JellyPlayApp.kt`'s `PhoneContent`, NestedScrollConnection feed):
 *
 *  - the threshold is a strict per-emission dead zone on both feeds — small
 *    repeated deltas NEVER accumulate into a hide;
 *  - index-direction changes bypass the threshold entirely (dock feed only);
 *  - the at-top force applies only when enabled (dock ON, nav OFF);
 *  - the `canHide` gate vetoes any hide hint;
 *  - the first list emission only primes tracking — no decision.
 */
class ScrollDirectionVisibilityTest {

    // ── onScrollDelta (NestedScrollConnection feed — nav sign convention) ──

    @Test
    fun scrollDelta_pastThresholdInBothDirections_hidesThenShows() {
        val visibility = ScrollDirectionVisibility(thresholdPx = 15f, forceVisibleAtTop = false)

        // Negative delta = scrolling down / content moving up → hide (nav's
        // exact inequality direction).
        visibility.onScrollDelta(-20f)
        assertFalse(visibility.visible)

        visibility.onScrollDelta(20f)
        assertTrue(visibility.visible)
    }

    @Test
    fun scrollDelta_atExactThreshold_isIgnored() {
        val visibility = ScrollDirectionVisibility(thresholdPx = 15f, forceVisibleAtTop = false)

        visibility.onScrollDelta(-15f)
        visibility.onScrollDelta(15f)

        assertTrue(visibility.visible, "strict inequalities: exactly ±threshold must not flip")
    }

    @Test
    fun scrollDelta_belowThreshold_neverAccumulatesIntoHide() {
        val visibility = ScrollDirectionVisibility(thresholdPx = 15f, forceVisibleAtTop = false)

        // 50 × 10f = 500px of total downward scroll, far past the 15px
        // threshold — but each callback is judged on its own delta.
        repeat(50) { visibility.onScrollDelta(-10f) }

        assertTrue(visibility.visible)
    }

    // ── onListScrolled (LazyListState feed — dock semantics) ──

    @Test
    fun listOffset_pastThresholdInBothDirections_hidesThenShows() {
        val visibility = ScrollDirectionVisibility(thresholdPx = 15f, forceVisibleAtTop = true)
        visibility.prime(index = 0, offsetPx = 100f)

        visibility.onListScrolled(index = 0, offsetPx = 120f)
        assertFalse(visibility.visible)

        visibility.onListScrolled(index = 0, offsetPx = 100f)
        assertTrue(visibility.visible)
    }

    @Test
    fun listOffset_atExactThreshold_isIgnored() {
        val visibility = ScrollDirectionVisibility(thresholdPx = 15f, forceVisibleAtTop = true)
        visibility.prime(index = 0, offsetPx = 100f)

        visibility.onListScrolled(0, 115f)
        assertTrue(visibility.visible, "exactly +15 must not hide (strict >)")

        visibility.onListScrolled(0, 100f)
        assertTrue(visibility.visible, "exactly -15 must not show-decide (strict >)")
    }

    @Test
    fun listOffset_perEmissionComparison_neverAccumulates() {
        val visibility = ScrollDirectionVisibility(thresholdPx = 15f, forceVisibleAtTop = true)
        visibility.prime(index = 0, offsetPx = 0f)

        // 20 emissions × 10px = 200px of total scroll, but every step is
        // compared against the PREVIOUS emission only (10f each) — today's
        // per-emission semantics, not an accumulating anchor.
        repeat(20) { step -> visibility.onListScrolled(0, ((step + 1) * 10).toFloat()) }

        assertTrue(visibility.visible)
    }

    @Test
    fun listIndexChange_bypassesThreshold() {
        val visibility = ScrollDirectionVisibility(thresholdPx = 15f, forceVisibleAtTop = true)
        visibility.prime(index = 0, offsetPx = 0f)

        // Index advances with zero offset delta — hides anyway.
        visibility.onListScrolled(1, 0f)
        assertFalse(visibility.visible)

        // Index regresses — shows again, also without crossing the threshold.
        visibility.onListScrolled(0, 0f)
        assertTrue(visibility.visible)
    }

    // ── at-top force ──

    @Test
    fun atTopForce_whenEnabled_restoresVisibility() {
        val visibility = ScrollDirectionVisibility(thresholdPx = 15f, forceVisibleAtTop = true)
        visibility.prime(index = 0, offsetPx = 5f)
        visibility.onScrollDelta(-20f) // hide via the other feed — no list tracking involved
        assertFalse(visibility.visible)

        visibility.onListScrolled(0, 0f)
        assertTrue(visibility.visible, "index == 0 && offset == 0 must force visible")
    }

    @Test
    fun atTopForce_whenDisabled_topDoesNotRestoreVisibility() {
        val visibility = ScrollDirectionVisibility(thresholdPx = 15f, forceVisibleAtTop = false)
        visibility.prime(index = 0, offsetPx = 5f)
        visibility.onScrollDelta(-20f)
        assertFalse(visibility.visible)

        // At (0, 0) with the force off: index unchanged, 5px scroll-up is below
        // the threshold — nothing may flip the state (nav keeps NO at-top rule).
        visibility.onListScrolled(0, 0f)
        assertFalse(visibility.visible)
    }

    // ── canHide gate ──

    @Test
    fun canHideFalse_vetoesHideHints_onlyWhileClosed() {
        var allowed = true
        val visibility = ScrollDirectionVisibility(
            thresholdPx = 15f,
            forceVisibleAtTop = true,
            canHide = { allowed },
        )
        visibility.prime(index = 0, offsetPx = 0f)

        visibility.onListScrolled(1, 0f) // hide hint, gate open
        assertFalse(visibility.visible)

        allowed = false // gate closed (settings off / search focused)
        visibility.onScrollDelta(-20f) // hide hint — vetoed
        assertTrue(visibility.visible)

        allowed = true // gate reopens — the next hide hint lands
        visibility.onScrollDelta(-20f)
        assertFalse(visibility.visible)
    }

    // ── resetToVisible ──

    @Test
    fun resetToVisible_restoresFromHidden() {
        val visibility = ScrollDirectionVisibility(thresholdPx = 15f, forceVisibleAtTop = false)

        visibility.onScrollDelta(-20f)
        assertFalse(visibility.visible)

        visibility.resetToVisible()
        assertTrue(visibility.visible)
    }

    // ── first-emission priming ──

    @Test
    fun firstListEmission_primesWithoutDeciding() {
        val visibility = ScrollDirectionVisibility(thresholdPx = 15f, forceVisibleAtTop = true)

        // The very first emission would trip several rules (index jump + a
        // large offset) but must only record the tracking position.
        visibility.onListScrolled(3, 500f)
        assertTrue(visibility.visible)

        // The primed position is live: the next emission is judged against it.
        visibility.onListScrolled(3, 520f)
        assertFalse(visibility.visible)
    }

    @Test
    fun prime_armsTrackingWithoutDeciding() {
        val visibility = ScrollDirectionVisibility(thresholdPx = 15f, forceVisibleAtTop = true)

        visibility.prime(index = 0, offsetPx = 1000f)
        assertTrue(visibility.visible, "priming must not touch the visibility state")

        // 5px scroll-up from the primed offset: below threshold, no rule fires.
        visibility.onListScrolled(0, 995f)
        assertTrue(visibility.visible)
    }

    // ── state surface ──

    @Test
    fun visibleInitialState_isHonoredAndBackedByExposedState() {
        val hidden = ScrollDirectionVisibility(
            thresholdPx = 15f,
            forceVisibleAtTop = false,
            visibleInitialState = false,
        )

        assertFalse(hidden.visible)
        // The backing MutableState is the same instance the convenience
        // property reads — nav hands it to LocalFloatingNavVisibility.
        assertFalse(hidden.visibleState.value)

        hidden.resetToVisible()
        assertTrue(hidden.visibleState.value)
    }
}
