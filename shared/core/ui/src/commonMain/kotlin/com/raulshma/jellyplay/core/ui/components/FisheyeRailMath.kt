package com.raulshma.jellyplay.core.ui.components

import kotlin.math.exp

/**
 * The ONE fisheye-rail math surface, shared by the two rail geometry cores
 * that hand-copied it: the library screen's `AlphabetRailGeometry` (alphabet
 * jump rail) and the reader's `TocRailGeometry` (TOC tick rail). Owns the
 * fractional pixel→row mapping and the gaussian fisheye lens, with the lens
 * constants in ONE place — the single dial both rails respond to.
 *
 * Pure numbers in/out — no Compose, no state, deterministically testable.
 * Deliberately UNclamped at this level: the library clamps to its rail ends
 * at its own adapter, and the reader keeps a beyond-the-ends extrapolation
 * fold locally because drag-scrubbing needs out-of-range raw rows.
 */
object FisheyeRailMath {

    /** Peak scale of the row directly under the finger (fisheye lens). */
    const val FISHEYE_PEAK = 2.5f

    /** Gaussian sigma² for the fisheye falloff — smaller = tighter bell curve. */
    const val FISHEYE_SIGMA_SQ = 1.6f

    /**
     * Fractional row position of pixel [y] in [rowPx] units (e.g. 3.4 = between
     * the 4th and 5th rows); a degenerate `rowPx <= 0` collapses to row 0.
     * Tracking the fractional position — not just the integer row under the
     * finger — is what lets the fisheye bell curve glide smoothly.
     */
    fun rawRowAt(y: Float, rowPx: Float): Float =
        if (rowPx <= 0f) 0f else y / rowPx

    /**
     * Gaussian fisheye scale for the row at [index] given the fractional finger
     * row [touchRow] (null = finger not on the rail → no lens). Pure function —
     * safe to call from a draw-phase `graphicsLayer` lambda so the bell curve
     * glides with the finger without invalidating composition.
     */
    fun fisheyeScaleAt(index: Int, touchRow: Float?): Float {
        if (touchRow == null) return 1f
        val d = index - touchRow
        val g = exp(-(d * d) / (2 * FISHEYE_SIGMA_SQ))
        return 1f + (FISHEYE_PEAK - 1f) * g
    }
}
