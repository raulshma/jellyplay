package com.raulshma.jellyplay.feature.library

import com.raulshma.jellyplay.core.ui.components.FisheyeRailMath

/**
 * Compose-free geometry core for the library screen's alphabet "jump to letter"
 * rail (the HeatmapGridModel precedent), extracted from
 * [LibraryScreen.AlphabetJumpRail]: the pixel→letter-index mapping with its
 * rail-end clamps, the gaussian fisheye lens, and the tap/drag jump-target
 * fold. All inputs and outputs are plain numbers — the rail composable keeps
 * only dp/px conversion, drawing, and pointer-input wiring, and the whole
 * model is deterministically testable.
 *
 * A thin adapter over core:ui's [FisheyeRailMath]: the fractional pixel→row
 * mapping and the gaussian lens (its `FISHEYE_PEAK`/`FISHEYE_SIGMA_SQ`
 * constants) live there once, shared with the reader's TOC tick rail; this
 * class keeps the alphabet-rail-specific arm — the clamp to the rail's ends
 * (`indexAt` never leaves `[0, lastIndex]`, unlike the reader's deliberate
 * beyond-the-ends extrapolation).
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
     * between the 4th and 5th letters), clamped to the rail's ends.
     */
    fun indexAt(y: Float): Float =
        FisheyeRailMath.rawRowAt(y, rowPx).coerceIn(0f, maxIndex.toFloat())

    /** Discrete letter at pixel [y] — the tap/drag jump target. */
    fun letterAt(y: Float): Char = letters[indexAt(y).toInt()]

    /**
     * Gaussian fisheye scale for the letter at [index] given the fractional
     * finger position [touchIndex] (null = finger not on the rail → no lens).
     * Delegates to [FisheyeRailMath] — pure, safe from the draw phase.
     */
    fun fisheyeScaleAt(index: Int, touchIndex: Float?): Float =
        FisheyeRailMath.fisheyeScaleAt(index, touchIndex)
}
