package com.raulshma.jellyplay.feature.library

/**
 * Compose-free geometry core for the library screen's alphabet "jump to letter"
 * rail (the HeatmapGridModel precedent), extracted from
 * [LibraryScreen.AlphabetJumpRail]: the pixel→letter-index mapping with its
 * rail-end clamps, the gaussian fisheye lens, and the tap/drag jump-target
 * fold. All inputs and outputs are plain numbers — the rail composable keeps
 * only dp/px conversion, drawing, and pointer-input wiring, and the whole
 * model is deterministically testable.
 *
 * @param letters the rail's letters in display order (`#` for non A–Z names).
 * @param rowPx the fixed per-letter row height in px — the rail is exactly
 *   `letters.size * rowPx` tall and every index derives from it.
 */
internal class AlphabetRailGeometry(
    private val letters: List<Char>,
    private val rowPx: Float,
) {
    /** Last valid letter index (rail end), never negative even for an empty rail. */
    private val maxIndex: Int = letters.lastIndex.coerceAtLeast(0)

    /**
     * Fractional letter-index position of pixel [y] on the rail (e.g. 3.4 =
     * between the 4th and 5th letters), clamped to the rail's ends. Tracking
     * the fractional position — not just the integer letter under the finger —
     * is what lets the fisheye bell-curve glide smoothly with the finger.
     */
    fun indexAt(y: Float): Float =
        if (rowPx <= 0f) 0f else (y / rowPx).coerceIn(0f, maxIndex.toFloat())

    /** Discrete letter at pixel [y] — the tap/drag jump target. */
    fun letterAt(y: Float): Char = letters[indexAt(y).toInt()]

    /**
     * Gaussian fisheye scale for the letter at [index] given the fractional
     * finger position [touchIndex] (null = finger not on the rail → no lens).
     * Pure function — safe to call from the draw phase (graphicsLayer lambda)
     * so the bell-curve glides with the finger without invalidating
     * composition.
     */
    fun fisheyeScaleAt(index: Int, touchIndex: Float?): Float {
        if (touchIndex == null) return 1f
        val d = index - touchIndex
        val g = kotlin.math.exp(-(d * d) / (2 * FISHEYE_SIGMA_SQ))
        return 1f + (FISHEYE_PEAK - 1f) * g
    }

    companion object {
        /** Peak scale of the letter directly under the finger (fisheye lens). */
        private const val FISHEYE_PEAK = 2.5f

        /** Gaussian sigma² for the fisheye falloff — smaller = tighter bell curve. */
        private const val FISHEYE_SIGMA_SQ = 1.6f
    }
}
