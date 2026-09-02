package com.raulshma.jellyplay.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Pins the invariants of [formatFixed]:
 *  - Fixed-precision output with exactly [decimals] fraction digits, zero-padded,
 *    always using '.' (locale-independent — safe to embed in MPV command strings).
 *  - HALF_UP rounding for non-negative values: an exact x.x5 boundary at the
 *    kept precision rounds AWAY from zero (matching String.format's RoundingMode.HALF_UP).
 *  - Negative halves round away from zero too (the impl mirrors the HALF_UP sign
 *    logic for the only kind of values the call sites pass).
 *  - `decimals` is required to be non-negative (IllegalArgumentException otherwise).
 *
 * Quirk pinned as-is: the implementation always appends a fractional part, even at
 * `decimals == 0` (e.g. "3.0" rather than "%.0f"'s "3"). No current call site uses
 * decimals == 0 (they pass 1, 2 or 6), so the exact rendered output at 0 is pinned
 * here to make any future change a conscious one.
 *
 * Rounding-boundary cases deliberately use binary-exact inputs (0.5, 1.25, 1.125,
 * 123456.75, ...) so the expectations don't depend on decimal-literal representation
 * error (e.g. 1.005 is NOT binary-exact and would NOT round up — never assert on it).
 */
class NumberFormatterTest {

    @Test
    fun decimalsSweep_zeroPadsFractionAcrossSupportedRange() {
        // POW10 covers decimals 0..6; every tier must emit exactly N fraction digits.
        assertEquals("2.0", formatFixed(1.5, 0))
        assertEquals("1.5", formatFixed(1.5, 1))
        assertEquals("1.50", formatFixed(1.5, 2))
        assertEquals("1.500", formatFixed(1.5, 3))
        assertEquals("1.5000", formatFixed(1.5, 4))
        assertEquals("1.50000", formatFixed(1.5, 5))
        assertEquals("1.500000", formatFixed(1.5, 6))
    }

    @Test
    fun halfUpBoundaries_roundAwayFromZero_forNonNegativeValues() {
        // x.x5 at the kept precision must round UP (HALF_UP), not to-even.
        assertEquals("1.0", formatFixed(0.5, 0))
        assertEquals("2.0", formatFixed(1.5, 0))
        assertEquals("3.0", formatFixed(2.5, 0))
        assertEquals("1.3", formatFixed(1.25, 1)) // binary-exact 1.25 -> 12.5 -> HALF_UP
        assertEquals("1.13", formatFixed(1.125, 2)) // binary-exact 1.125 -> 112.5 -> HALF_UP
        assertEquals("0.3", formatFixed(0.25, 1))
    }

    @Test
    fun valuesJustBelowAndAboveBoundary_roundToNearest() {
        assertEquals("1.2", formatFixed(1.24, 1))
        assertEquals("1.3", formatFixed(1.26, 1))
        assertEquals("1.12", formatFixed(1.124, 2))
        assertEquals("0.4", formatFixed(0.4, 1))
        assertEquals("0.6", formatFixed(0.6, 1))
    }

    @Test
    fun zeroFormatsWithoutSign() {
        assertEquals("0.0", formatFixed(0.0, 0))
        assertEquals("0.00", formatFixed(0.0, 2))
    }

    @Test
    fun subUnitValues_keepLeadingZeroIntegerPart() {
        assertEquals("0.5", formatFixed(0.5, 1))
        assertEquals("0.25", formatFixed(0.25, 2))
        assertEquals("0.04", formatFixed(0.04, 2))
    }

    @Test
    fun largeValues_keepFullIntegerPart() {
        // 123456.75 is binary-exact; 2dp rounds the .75 up to .8 at 1dp (HALF_UP on 7.5 tenths).
        assertEquals("123456.75", formatFixed(123456.75, 2))
        assertEquals("123456.8", formatFixed(123456.75, 1))
        assertEquals("987655.0", formatFixed(987654.5, 0))
        assertEquals("1000000.00", formatFixed(1_000_000.0, 2))
    }

    @Test
    fun negativeValues_roundAwayFromZero_onHalves() {
        // The impl's sign split makes negative halves round away from zero
        // (same direction as RoundingMode.HALF_UP's "round away from zero").
        assertEquals("-1.3", formatFixed(-1.25, 1))
        assertEquals("-1.2", formatFixed(-1.24, 1))
        assertEquals("-1.3", formatFixed(-1.26, 1))
        assertEquals("-2.0", formatFixed(-1.5, 0))
        assertEquals("-3.0", formatFixed(-2.5, 0))
        // Magnitudes below half a unit truncate to the zero integer part (no "-0").
        assertEquals("0.0", formatFixed(-0.4, 0))
        assertEquals("-1.0", formatFixed(-0.6, 0))
    }

    @Test
    fun negativeDecimals_throwsIllegalArgument() {
        val error = assertFailsWith<IllegalArgumentException> {
            formatFixed(1.0, -1)
        }
        assertEquals("decimals must be non-negative, was -1", error.message)
    }
}
