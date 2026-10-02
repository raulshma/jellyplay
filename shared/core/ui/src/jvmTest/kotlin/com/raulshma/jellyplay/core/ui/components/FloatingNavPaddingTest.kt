package com.raulshma.jellyplay.core.ui.components

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the floating-nav clearance arithmetic (the pure fold extracted from
 * [floatingNavClearanceDp] / [Modifier.clearFloatingNav]):
 *
 *  - presence-gating: where no floating nav is painted
 *    ([LocalFloatingNavPresent] false — auth host, TV, expanded layouts,
 *    full-screen routes, the desktop shell) the bar-height term collapses and
 *    only margin + system inset remain, so callers don't reserve space for a
 *    bar that never shows;
 *  - present: margin + one nav height + system inset;
 *  - the ride-up offset negates the nav's slide; the coerceAtMost ceiling
 *    only binds on a hypothetical downward push beyond one nav-height (the
 *    nav slide itself never exceeds that).
 */
class FloatingNavPaddingTest {

    @Test
    fun reservation_navPresent_includesBarHeight() {
        assertEquals(
            16.dp + 56.dp + 24.dp,
            floatingNavReservationDp(
                navPresent = true,
                navHeight = 56.dp,
                extraBottom = 16.dp,
                systemInset = 24.dp,
            ),
        )
    }

    @Test
    fun reservation_navAbsent_collapsesTheBarTerm() {
        assertEquals(
            16.dp + 24.dp,
            floatingNavReservationDp(
                navPresent = false,
                navHeight = 56.dp,
                extraBottom = 16.dp,
                systemInset = 24.dp,
            ),
        )
    }

    @Test
    fun reservation_zeroMarginAbsentNav_leavesOnlySystemInset() {
        // The downloads selection bar's call shape: extraBottom = 0.dp.
        assertEquals(
            24.dp,
            floatingNavReservationDp(
                navPresent = false,
                navHeight = 56.dp,
                extraBottom = 0.dp,
                systemInset = 24.dp,
            ),
        )
    }

    @Test
    fun rideUp_negatesTheSlide() {
        // Positive offset = bar slid down (hidden) → rider moves up by the
        // same amount (the clearFloatingNav KDoc's ride-up contract).
        assertEquals(-40f, navRideUpOffsetPx(navOffsetPx = 40f, navHeightPx = 56f))
        // Bar fully shown (offset 0): no ride. (Negation of 0f is -0f, which
        // IntOffset(0, -0) rounds to — assert with tolerance.)
        assertEquals(0f, navRideUpOffsetPx(navOffsetPx = 0f, navHeightPx = 56f), absoluteTolerance = 0f)
    }

    @Test
    fun rideUp_coerceAtMost_capsOnlyADownwardPush() {
        // The nav slide itself never exceeds one nav-height, so upward values
        // pass through untouched; the coerceAtMost ceiling only binds when a
        // raw offset would push the rider DOWN more than one nav-height
        // (negative raw offset beyond -navHeight).
        assertEquals(-120f, navRideUpOffsetPx(navOffsetPx = 120f, navHeightPx = 56f))
        assertEquals(56f, navRideUpOffsetPx(navOffsetPx = -120f, navHeightPx = 56f))
        assertEquals(4f, navRideUpOffsetPx(navOffsetPx = -4f, navHeightPx = 56f))
    }
}
