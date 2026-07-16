package com.raulshma.jellyplay.core.ui.tv.input

import androidx.compose.runtime.Stable

/**
 * Strategy that turns a D-pad key-repeat count into a seek-step multiplier.
 *
 * Consumed by [com.raulshma.jellyplay.core.ui.tv.components.DpadSeekState] to scale each
 * accumulation when a directional key is held. Implementations may take the content
 * [durationMs] into account so that long-form content can ramp up faster and to a higher
 * ceiling than short content — keeping long-form seeking practical with a D-pad.
 *
 * A [durationMs] of `0` or less (unknown / live) must yield a multiplier of `1` from any
 * implementation so that acceleration never engages without a reliable duration.
 *
 * Lives in `core:ui` (next to [DpadKey]) rather than `feature:player:video` because the
 * hold-to-seek state it feeds is a generic TV control reused across player surfaces.
 */
fun interface DpadSeekAcceleration {

    fun calculateMultiplier(repeatCount: Int, durationMs: Long): Float

    /**
     * Default step calc: the base step scaled by [calculateMultiplier].
     * Implementations rarely need to override this.
     */
    fun calculateStep(baseStepMs: Long, repeatCount: Int, durationMs: Long): Long =
        (baseStepMs * calculateMultiplier(repeatCount, durationMs)).toLong()

    companion object {
        /**
         * Duration-aware ramp — the default for TV hold-to-seek. Short content stays
         * fine-grained; long films ramp steeper and higher.
         */
        val Default: DpadSeekAcceleration = DurationAwareDpadSeekAcceleration()
    }
}

/** Shared defaults for the linear curve (extracted from the former `DpadRepeatAccelerator`). */
internal const val DEFAULT_ACCELERATION_FACTOR: Float = 0.1f
internal const val DEFAULT_MAX_MULTIPLIER: Float = 2.5f

/**
 * Linear, duration-agnostic ramp: `1 + repeatCount * factor`, capped at [maxMultiplier].
 *
 * Preserves the exact legacy behavior. Useful as a deterministic fallback and for tests
 * that need a predictable curve regardless of duration.
 */
@Stable
class LinearDpadSeekAcceleration(
    private val factor: Float = DEFAULT_ACCELERATION_FACTOR,
    private val maxMultiplier: Float = DEFAULT_MAX_MULTIPLIER,
) : DpadSeekAcceleration {

    init {
        require(factor >= 0f) { "factor must be non-negative" }
        require(maxMultiplier >= 1f) { "maxMultiplier must be >= 1" }
    }

    override fun calculateMultiplier(repeatCount: Int, durationMs: Long): Float =
        (1f + repeatCount.coerceAtLeast(0) * factor).coerceAtMost(maxMultiplier)

    companion object {
        val Default = LinearDpadSeekAcceleration()
    }
}

/**
 * Duration-aware ramp.
 *
 * The raw Android key-repeat count is divided by [repeatCountScale] because repeat cadence
 * varies by device; this stretches ramp-up to engage over multi-second holds rather than
 * sub-second bursts. The scaled count is then bucketed by content length (in minutes):
 * longer content escalates both faster and to a higher ceiling.
 *
 * Threshold table (raw `repeatCount`, i.e. *before* the `/3` scale is applied):
 *
 * | duration          | 1×           | → 2× / 3×    | → 4×         | → 6× / 10×   |
 * | ----------------- | ------------ | ------------ | ------------ | ------------ |
 * | < 30 min          | rc < 90      | rc ≥ 90      | —            | —            |
 * | 30–89 min         | rc < 39      | 39 ≤ rc <150 | 150 ≤ rc<225 | rc ≥ 225     |
 * | 90–149 min        | rc < 60      | 60 ≤ rc <120 | 120 ≤ rc<180 | rc ≥ 180     |
 * | ≥ 150 min         | rc < 60      | 60 ≤ rc <120 | 120 ≤ rc<180 | rc ≥ 180     |
 *
 * (max ceiling: 2× for shorts, 4× for medium, 6× for long, 10× for extra-long films.)
 */
@Stable
class DurationAwareDpadSeekAcceleration(
    private val repeatCountScale: Int = DEFAULT_REPEAT_COUNT_SCALE,
) : DpadSeekAcceleration {

    init {
        require(repeatCountScale >= 1) { "repeatCountScale must be >= 1" }
    }

    override fun calculateMultiplier(repeatCount: Int, durationMs: Long): Float {
        // No repeat, or no reliable duration (unknown / live): never accelerate.
        if (repeatCount <= 0 || durationMs <= 0L) return 1f

        // Normalize across device key-repeat cadences.
        val scaledRepeatCount = repeatCount / repeatCountScale
        if (scaledRepeatCount <= 0) return 1f

        val durationMinutes = durationMs / MS_PER_MINUTE
        return when {
            durationMinutes < SHORT_THRESHOLD_MIN -> {
                if (scaledRepeatCount < SHORT_TIER_2) 1 else 2
            }

            durationMinutes < MEDIUM_THRESHOLD_MIN -> {
                when {
                    scaledRepeatCount < MEDIUM_TIER_2 -> 1
                    scaledRepeatCount < MEDIUM_TIER_3 -> 2
                    scaledRepeatCount < MEDIUM_TIER_4 -> 3
                    else -> 4
                }
            }

            durationMinutes < LONG_THRESHOLD_MIN -> {
                when {
                    scaledRepeatCount < LONG_TIER_2 -> 1
                    scaledRepeatCount < LONG_TIER_3 -> 2
                    scaledRepeatCount < LONG_TIER_4 -> 4
                    else -> 6
                }
            }

            else -> {
                when {
                    scaledRepeatCount < XLONG_TIER_2 -> 1
                    scaledRepeatCount < XLONG_TIER_3 -> 3
                    scaledRepeatCount < XLONG_TIER_4 -> 6
                    else -> 10
                }
            }
        }.toFloat()
    }

    companion object {
        const val DEFAULT_REPEAT_COUNT_SCALE = 3

        private const val MS_PER_MINUTE = 60_000L

        // Duration buckets (minutes).
        private const val SHORT_THRESHOLD_MIN = 30L
        private const val MEDIUM_THRESHOLD_MIN = 90L
        private const val LONG_THRESHOLD_MIN = 150L

        // Scaled-repeat-count tier thresholds (i.e. raw count = threshold * repeatCountScale).
        private const val SHORT_TIER_2 = 30

        private const val MEDIUM_TIER_2 = 13
        private const val MEDIUM_TIER_3 = 50
        private const val MEDIUM_TIER_4 = 75

        private const val LONG_TIER_2 = 20
        private const val LONG_TIER_3 = 40
        private const val LONG_TIER_4 = 60

        private const val XLONG_TIER_2 = 20
        private const val XLONG_TIER_3 = 40
        private const val XLONG_TIER_4 = 60

        val Default = DurationAwareDpadSeekAcceleration()
    }
}
