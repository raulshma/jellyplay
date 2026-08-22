package com.raulshma.jellyplay.core.data.playback

import kotlin.math.abs

/**
 * Pure, testable display-mode selection for refresh-rate / resolution matching,
 * modelled on Wholphin's `RefreshRateService.findDisplayMode` (itself ported
 * from jellyfin-androidtv).
 *
 * The previous [FrameRateMatcher] only matched the closest refresh rate at the
 * *current* resolution — so a 4K@24fps TV playing into a panel whose current
 * mode was 1080p@60 would stay at 1080p@60. This 5-tier strategy also handles
 * resolution switching and the 24↔60 (2.5×) cadence case:
 *
 *  1. Exact resolution + exact refresh rate.
 *  2. Next-highest resolution + exact refresh rate.
 *  3. Exact resolution + acceptable refresh rate.
 *  4. Next-highest resolution + acceptable refresh rate.
 *  5. Largest resolution available.
 *
 * Each tier is tried in order; the first non-empty result wins. This keeps the
 * preference exact-match when possible and only relaxes resolution/rate as
 * needed — the right tradeoff for avoiding an accidental quality downgrade.
 */
object RefreshRateMatcher {

    /**
     * A minimal display-mode projection. The real Android [android.view.Display.Mode]
     * carries more fields, but matching only needs these three plus an id to
     * pass back to the caller. Tests construct synthetic lists; the platform
     * adapter maps [DisplayMode] ↔ `android.view.Display.Mode`.
     */
    data class DisplayMode(
        val modeId: Int,
        val physicalWidth: Int,
        val physicalHeight: Int,
        val refreshRate: Float,
    )

    /**
     * Tolerance (Hz) within which a refresh rate is considered an "acceptable"
     * match for [targetFps]. ~0.5 Hz covers the typical 23.976 vs 24 / 59.94 vs
     * 60 drift without matching unrelated rates.
     */
    private const val RATE_TOLERANCE = 0.5f

    /**
     * Returns the best [DisplayMode] for [targetFps] content given the available
     * [modes] and the [current] mode, or `null` if none is suitable.
     *
     * [allowResolutionSwitch] gates tiers 2/4/5 (next-highest / largest
     * resolution). When `false` (FRAME_RATE_ONLY mode), only tiers 1 and 3 run,
     * matching the old single-resolution behaviour.
     */
    fun findDisplayMode(
        modes: List<DisplayMode>,
        current: DisplayMode,
        targetFps: Float,
        allowResolutionSwitch: Boolean,
    ): DisplayMode? {
        if (targetFps <= 0f || modes.isEmpty()) return null

        val targetW = current.physicalWidth
        val targetH = current.physicalHeight

        // 1. Exact resolution + exact refresh rate.
        bestRate(modes.atResolution(targetW, targetH), targetFps)?.let { return it }

        if (allowResolutionSwitch) {
            // 2. Next-highest resolution + exact refresh rate.
            bestRate(nextHigherResolutions(modes, targetW, targetH), targetFps)?.let { return it }
        }

        // 3. Exact resolution + acceptable refresh rate. `bestRate` already
        //    spans both exact-cadence and within-tolerance matches, so tier 3
        //    is a retry of tier 1 on the same candidates — a no-op unless the
        //    list mutated. Kept to preserve the 5-tier shape and the
        //    (cadence ∪ tolerance) "acceptable" contract at exact resolution.
        bestRate(modes.atResolution(targetW, targetH), targetFps)?.let { return it }

        if (allowResolutionSwitch) {
            // 4. Next-highest resolution + acceptable refresh rate.
            bestRate(nextHigherResolutions(modes, targetW, targetH), targetFps)?.let { return it }

            // 5. Largest resolution available.
            largestResolution(modes)?.let { return it }
        }

        return null
    }

    /** Modes whose dimensions exactly equal [w]×[h]. */
    private fun List<DisplayMode>.atResolution(w: Int, h: Int): List<DisplayMode> =
        filter { it.physicalWidth == w && it.physicalHeight == h }

    /**
     * Modes strictly larger than [w]×[h] (by total pixel count), smallest-first
     * so the "next-highest" (least overkill) is preferred.
     */
    private fun nextHigherResolutions(modes: List<DisplayMode>, w: Int, h: Int): List<DisplayMode> {
        val targetPixels = w * h
        return modes
            .filter { (it.physicalWidth * it.physicalHeight) > targetPixels }
            .sortedBy { it.physicalWidth * it.physicalHeight }
    }

    /**
     * The shared rate-selection ladder for tiers 1–4: prefer a judder-free
     * cadence match, then fall back to the closest rate within [RATE_TOLERANCE].
     * (The previous code duplicated this exact body as `exactRate` and
     * `acceptableRate`; collapsing it removes the [Duplicated Code] smell. The
     * `matchingCadence` / `withinTolerance` filters return plain [List]s, so
     * the earlier `?.closest` safe-calls were dead navigation — fixed here.)
     */
    private fun bestRate(candidates: List<DisplayMode>, targetFps: Float): DisplayMode? {
        if (candidates.isEmpty()) return null
        return candidates.matchingCadence(targetFps).closest(targetFps)
            ?: candidates.withinTolerance(targetFps).closest(targetFps)
    }

    private fun largestResolution(modes: List<DisplayMode>): DisplayMode? =
        modes.maxByOrNull { it.physicalWidth * it.physicalHeight }

    private fun List<DisplayMode>.closest(targetFps: Float): DisplayMode? =
        minByOrNull { abs(it.refreshRate - targetFps) }

    /**
     * Modes whose rate is a clean integer multiple of [targetFps] — the
     * 24→48/60/72/96/120 and 30→60/90/120 cases. These play back without judder
     * even though the rate isn't numerically equal, so they rank as exact-tier
     * matches. The 24↔60 (2.5×) cadence is included because many TVs only expose
     * a 60 Hz panel mode and 24 fps content still cadences cleanly at 3:2.
     */
    private fun List<DisplayMode>.matchingCadence(targetFps: Float): List<DisplayMode> =
        filter { frameRateMatches(it.refreshRate, targetFps) }

    private fun List<DisplayMode>.withinTolerance(targetFps: Float): List<DisplayMode> =
        filter { abs(it.refreshRate - targetFps) <= RATE_TOLERANCE }

    /**
     * True when [actual] and [target] are numerically equal, or [actual] is an
     * integer multiple of [target] (the judder-free cadence case), or the
     * 24↔60 (2.5×) special case.
     */
    fun frameRateMatches(actual: Float, target: Float): Boolean {
        if (target <= 0f) return false
        if (abs(actual - target) <= RATE_TOLERANCE) return true
        val ratio = actual / target
        // Integer multiples: 2×, 3×, 4×, 5×. Allow a small epsilon for 47.95 vs 48.
        val nearestInt = kotlin.math.round(ratio)
        if (nearestInt >= 2f && abs(ratio - nearestInt) <= 0.05f) return true
        // 24↔60 (2.5×) — common on panels without a native 24 Hz mode.
        if (abs(ratio - 2.5f) <= 0.05f) return true
        return false
    }
}
