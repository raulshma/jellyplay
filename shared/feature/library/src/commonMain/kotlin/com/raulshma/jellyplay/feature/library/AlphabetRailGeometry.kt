package com.raulshma.jellyplay.feature.library

import com.raulshma.jellyplay.core.model.LibraryViewMode
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

/**
 * Which scroll state backs the alphabet rail for a view mode. Pure fold over
 * [LibraryViewMode] extracted from the rail's inline `when`s so the screen's
 * per-mode wiring (first-visible highlight + jump target) is testable:
 *
 *  - the active-letter highlight reads the LIST state in LIST mode and the
 *    GRID state for every other mode — including MASONRY, whose own staggered
 *    state is deliberately NOT read here (preserving the shipped behavior);
 *  - a jump scrolls the LIST state in LIST mode, the STAGGERED state in
 *    MASONRY mode, and the GRID state otherwise.
 *
 * Both maps are intentionally asymmetric (highlight ≠ scroll target for
 * MASONRY) — that asymmetry is the load-bearing behavior this type pins.
 */
internal enum class LibraryRailScrollTarget { LIST, GRID, STAGGERED }

/** The scroll state the rail should scroll for [viewMode] (see [LibraryRailScrollTarget]). */
internal fun libraryRailScrollTarget(viewMode: LibraryViewMode): LibraryRailScrollTarget =
    when (viewMode) {
        LibraryViewMode.LIST -> LibraryRailScrollTarget.LIST
        LibraryViewMode.MASONRY -> LibraryRailScrollTarget.STAGGERED
        else -> LibraryRailScrollTarget.GRID
    }

/**
 * The first-visible index the rail's active-letter highlight reads for
 * [viewMode]: LIST mode reads the list's index, every other mode reads the
 * grid's — MASONRY included (the staggered index is not consulted).
 */
internal fun libraryRailFirstVisibleItemIndex(
    viewMode: LibraryViewMode,
    listFirstVisibleItemIndex: Int,
    gridFirstVisibleItemIndex: Int,
): Int =
    when (viewMode) {
        LibraryViewMode.LIST -> listFirstVisibleItemIndex
        else -> gridFirstVisibleItemIndex
    }

/**
 * The rail's letter→index fold, extracted verbatim from the library screen's
 * inline `derivedStateOf`: a single pass over the loaded items records the
 * first index at which each normalized leading letter appears (`#` for every
 * non A–Z leading character). Jellyfin returns library items sorted by
 * SortName by default, so the first snapshot index per letter is stable
 * within the loaded pages — a local-only jump target (no NameStartsWith
 * server filter is plumbed through the data layer). Insertion order is the
 * letters' display order; [nameOf] keeps the fold generic over the item type
 * (the screen passes the paged snapshot, tests pass plain strings).
 */
internal fun <T> jumpIndexByLetter(items: List<T>, nameOf: (T) -> String?): Map<Char, Int> {
    val map = LinkedHashMap<Char, Int>()
    for (i in items.indices) {
        val key = nameOf(items[i])?.firstOrNull()
            ?.lowercaseChar()
            ?.takeIf { it in 'a'..'z' }
            ?: '#'
        if (key !in map) map[key] = i
    }
    return map
}
