package com.raulshma.jellyplay.core.ui.tv.input

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

/**
 * Covers [LinearDpadSeekAcceleration] and [DurationAwareDpadSeekAcceleration], including the
 * default-adapter indirection on [DpadSeekAcceleration.Companion.Default].
 *
 * The duration-aware tier thresholds are exercised at their exact raw-repeat-count boundaries
 * (raw count / 3 = scaled count). Boundaries use strict `<`, so the value at the threshold
 * belongs to the *higher* tier.
 */
class DpadSeekAccelerationTest {

    // ----------------------------------------------------------------------
    // LinearDpadSeekAcceleration (preserves the former DpadRepeatAccelerator curve)
    // ----------------------------------------------------------------------

    private val linear = LinearDpadSeekAcceleration.Default
    private val linearDuration = 120_000L // duration is irrelevant to the linear strategy

    @Test
    fun linear_zeroRepeat_returnsBaseMultiplier() {
        assertEquals(1.0f, linear.calculateMultiplier(0, linearDuration), 0.001f)
    }

    @Test
    fun linear_oneRepeat_appliesAcceleration() {
        assertEquals(1.1f, linear.calculateMultiplier(1, linearDuration), 0.001f)
    }

    @Test
    fun linear_tenRepeats_doublesStep() {
        assertEquals(2.0f, linear.calculateMultiplier(10, linearDuration), 0.001f)
    }

    @Test
    fun linear_fifteenRepeats_capsAtMax() {
        assertEquals(2.5f, linear.calculateMultiplier(15, linearDuration), 0.001f)
    }

    @Test
    fun linear_hundredRepeats_capsAtMax() {
        assertEquals(2.5f, linear.calculateMultiplier(100, linearDuration), 0.001f)
    }

    @Test
    fun linear_calculateStep_baseTenThousand_repeatZero() {
        assertEquals(10_000L, linear.calculateStep(10_000L, 0, linearDuration))
    }

    @Test
    fun linear_calculateStep_baseTenThousand_repeatTen() {
        assertEquals(20_000L, linear.calculateStep(10_000L, 10, linearDuration))
    }

    @Test
    fun linear_calculateStep_baseTenThousand_repeatHundred_capped() {
        assertEquals(25_000L, linear.calculateStep(10_000L, 100, linearDuration))
    }

    @Test
    fun linear_negativeRepeat_isClampedToBase() {
        // Defensive: a negative repeat count should behave like zero, not accelerate backwards.
        assertEquals(1.0f, linear.calculateMultiplier(-5, linearDuration), 0.001f)
    }

    @Test
    fun linear_customParameters_respected() {
        val custom = LinearDpadSeekAcceleration(factor = 0.25f, maxMultiplier = 3.0f)
        assertEquals(1.0f, custom.calculateMultiplier(0, linearDuration), 0.001f)
        assertEquals(1.25f, custom.calculateMultiplier(1, linearDuration), 0.001f)
        assertEquals(2.5f, custom.calculateMultiplier(6, linearDuration), 0.001f)
        assertEquals(3.0f, custom.calculateMultiplier(10, linearDuration), 0.001f)
    }

    // ----------------------------------------------------------------------
    // DurationAwareDpadSeekAcceleration
    // ----------------------------------------------------------------------

    private val aware = DurationAwareDpadSeekAcceleration.Default

    @Test
    fun aware_zeroRepeat_returnsOne() {
        assertEquals(1f, aware.calculateMultiplier(0, 60 * 60_000L), 0.001f)
    }

    @Test
    fun aware_negativeRepeat_returnsOne() {
        assertEquals(1f, aware.calculateMultiplier(-10, 60 * 60_000L), 0.001f)
    }

    @Test
    fun aware_unknownDuration_returnsOne_evenAtHighRepeat() {
        assertEquals(1f, aware.calculateMultiplier(300, 0L), 0.001f)
        assertEquals(1f, aware.calculateMultiplier(300, -1L), 0.001f)
    }

    @Test
    fun aware_subScaleRepeat_returnsOne() {
        // repeatCount 1..2 divide by 3 to scaled 0 -> no acceleration yet.
        assertEquals(1f, aware.calculateMultiplier(1, 60 * 60_000L), 0.001f)
        assertEquals(1f, aware.calculateMultiplier(2, 60 * 60_000L), 0.001f)
    }

    // --- Short content (< 30 min): 1x -> 2x at scaled >= 30 (raw >= 90) ---

    @Test
    fun aware_shortContent_rampsToTwoAtThreshold() {
        val dur = 20 * 60_000L
        // Just below: scaled 29 (raw 87) -> 1x. At/above: scaled 30 (raw 90) -> 2x.
        assertEquals(1, aware.calculateMultiplier(89, dur).toInt())
        assertEquals(2, aware.calculateMultiplier(90, dur).toInt())
        // And never exceeds 2x for shorts, no matter how long the hold.
        assertEquals(2, aware.calculateMultiplier(3000, dur).toInt())
    }

    // --- Medium content (30..89 min): 1 -> 2 -> 3 -> 4 ---

    @Test
    fun aware_mediumContent_escalatesThroughAllTiers() {
        val dur = 60 * 60_000L
        // Tier boundaries (scaled -> raw): 13 -> 39, 50 -> 150, 75 -> 225.
        assertEquals(1, aware.calculateMultiplier(38, dur).toInt())
        assertEquals(2, aware.calculateMultiplier(39, dur).toInt())
        assertEquals(2, aware.calculateMultiplier(149, dur).toInt())
        assertEquals(3, aware.calculateMultiplier(150, dur).toInt())
        assertEquals(3, aware.calculateMultiplier(224, dur).toInt())
        assertEquals(4, aware.calculateMultiplier(225, dur).toInt())
        assertEquals(4, aware.calculateMultiplier(3000, dur).toInt()) // ceiling
    }

    // --- Long content (90..149 min): 1 -> 2 -> 4 -> 6 ---

    @Test
    fun aware_longContent_escalatesThroughAllTiers() {
        val dur = 120 * 60_000L // 2h
        // Tier boundaries (scaled -> raw): 20 -> 60, 40 -> 120, 60 -> 180.
        assertEquals(1, aware.calculateMultiplier(59, dur).toInt())
        assertEquals(2, aware.calculateMultiplier(60, dur).toInt())
        assertEquals(2, aware.calculateMultiplier(119, dur).toInt())
        assertEquals(4, aware.calculateMultiplier(120, dur).toInt())
        assertEquals(4, aware.calculateMultiplier(179, dur).toInt())
        assertEquals(6, aware.calculateMultiplier(180, dur).toInt())
        assertEquals(6, aware.calculateMultiplier(3000, dur).toInt()) // ceiling
    }

    // --- Extra-long content (>= 150 min): 1 -> 3 -> 6 -> 10 ---

    @Test
    fun aware_extraLongContent_escalatesThroughAllTiers() {
        val dur = 150 * 60_000L // 2.5h
        // Tier boundaries (scaled -> raw): 20 -> 60, 40 -> 120, 60 -> 180.
        assertEquals(1, aware.calculateMultiplier(59, dur).toInt())
        assertEquals(3, aware.calculateMultiplier(60, dur).toInt())
        assertEquals(3, aware.calculateMultiplier(119, dur).toInt())
        assertEquals(6, aware.calculateMultiplier(120, dur).toInt())
        assertEquals(6, aware.calculateMultiplier(179, dur).toInt())
        assertEquals(10, aware.calculateMultiplier(180, dur).toInt())
        assertEquals(10, aware.calculateMultiplier(3000, dur).toInt()) // ceiling
    }

    @Test
    fun aware_longerContent_reachesHigherMultiplierThanShorter_forSameRepeat() {
        // The headline behaviour: at raw repeatCount 120, a film seeks further than a clip.
        val short = aware.calculateMultiplier(120, 20 * 60_000L)
        val longFilm = aware.calculateMultiplier(120, 150 * 60_000L)
        assertTrue(longFilm > short, "long=$longFilm short=$short")
    }

    @Test
    fun aware_calculateStep_appliesMultiplierToBaseStep() {
        // Long film, raw repeatCount 180 -> 10x. 10s base -> 100s step.
        val step = aware.calculateStep(baseStepMs = 10_000L, repeatCount = 180, durationMs = 150 * 60_000L)
        assertEquals(100_000L, step)
    }

    @Test
    fun aware_customRepeatCountScale_respected() {
        // With scale = 1, the tier thresholds are reached at the raw (unscaled) counts.
        val scale1 = DurationAwareDpadSeekAcceleration(repeatCountScale = 1)
        val dur = 60 * 60_000L // medium
        // Medium tier-2 threshold is scaled 13; with scale=1 that's raw 13 (not 39).
        assertEquals(1, scale1.calculateMultiplier(12, dur).toInt())
        assertEquals(2, scale1.calculateMultiplier(13, dur).toInt())
    }

    @Test
    fun defaultAdapter_isDurationAware() {
        // The framework default must be the duration-aware ramp, not the legacy linear one.
        // Sanity-check via the duration-scaling property that only the aware strategy exhibits.
        val dur = 150 * 60_000L
        val rc = 180
        assertEquals(
            aware.calculateMultiplier(rc, dur),
            DpadSeekAcceleration.Default.calculateMultiplier(rc, dur),
            0.001f,
        )
    }
}
