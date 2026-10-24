package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.TrickplayInfo
import com.raulshma.jellyplay.core.model.TrickplayMode

/**
 * Pure decision logic behind the trickplay preview's presentation modes (#7):
 * which surface a [TrickplayMode] renders at a call site, which sprite-tile
 * window the FILMSTRIP shows, and where the surface anchors. Extracted from
 * composition so the tile math is JVM-testable ([TrickplayOverlayPolicyTest]);
 * the composables stay thin renderers over these decisions. No Compose types
 * in these signatures.
 *
 * The two call sites (the seek-scrub overlay and the gesture-swipe preview)
 * share ONE presentation fold — a mode flip or a manifest change re-renders
 * both sites through the same policy, so they cannot diverge.
 */

/** How many adjacent sprite tiles the FILMSTRIP renders (odd → a centered window). */
internal const val FILMSTRIP_VISIBLE_TILES = 5

/**
 * The rendered trickplay surface, as data:
 *  - [Bubble] — the legacy floating card (sprite crop + duration + progress
 *    bar). The BUBBLE mode, and the fallback whenever the presentation needs
 *    a manifest the session does not have.
 *  - [Filmstrip] — a horizontal strip of adjacent tiles centered on the seek
 *    position ([tileIndices], contiguous, clamped to the manifest's tile
 *    count).
 *  - [Fullscreen] — the current tile scaled over the whole player surface
 *    with a position/duration caption.
 */
internal sealed interface TrickplayPresentation {
    data object Bubble : TrickplayPresentation
    data class Filmstrip(val tileIndices: List<Int>) : TrickplayPresentation
    data object Fullscreen : TrickplayPresentation
}

/**
 * Where the rendered surface anchors within the player box — the screen maps
 * each anchor to its alignment + clearance (the values stay at the call sites;
 * this type keeps the policy Compose-free).
 */
internal enum class TrickplayAnchor {
    /** The legacy floating bubble, above the bottom controls. */
    FLOATING_BUBBLE,

    /** The filmstrip, anchored at the seek-bar line (below the bubble's float). */
    SEEK_BAR_LINE,

    /** Full-screen preview filling the player surface. */
    FULL_SURFACE,
}

/**
 * Sprite-tile index for a playback position — the SAME floor-and-clamp fold
 * `TrickplayManager.getThumbnail` applies (interval floors at 1 against
 * malformed manifests; the index clamps into the manifest's count). Pure so
 * the filmstrip's window math and the manager's tile lookups can never agree
 * to disagree about which tile a position means.
 */
internal fun trickplayTileIndex(positionMs: Long, intervalMs: Int, thumbnailCount: Int): Int {
    if (thumbnailCount <= 0) return 0
    val interval = intervalMs.coerceAtLeast(1)
    val index = (positionMs / interval.toLong()).toInt()
    return index.coerceIn(0, thumbnailCount - 1)
}

/**
 * The FILMSTRIP's tile window: [visible] contiguous indices centered on
 * [centerIndex], pinned inside `[0, tileCount)` (the center's leading edge
 * never slides the window past either end; a manifest smaller than the window
 * renders all of its tiles). Empty when there is nothing to render
 * (no manifest, or a non-positive [visible]).
 */
internal fun filmstripWindow(centerIndex: Int, tileCount: Int, visible: Int = FILMSTRIP_VISIBLE_TILES): IntRange {
    if (tileCount <= 0 || visible <= 0) return IntRange.EMPTY
    val clampedCenter = centerIndex.coerceIn(0, tileCount - 1)
    val start = (clampedCenter - visible / 2).coerceIn(0, (tileCount - visible).coerceAtLeast(0))
    val endInclusive = (start + visible - 1).coerceAtMost(tileCount - 1)
    return start..endInclusive
}

/**
 * The ONE presentation fold both trickplay sites render through. A missing
 * manifest ([thumbnailCount] <= 0 or [intervalMs] <= 0) degrades every mode
 * to [TrickplayPresentation.Bubble] — the bubble's placeholder already
 * renders a legible position readout without tiles, and the filmstrip/full-
 * screen surfaces would render nothing meaningful (the gesture site's gate
 * deliberately does NOT require a manifest, so this is the reachable path
 * there, not a defensive dead branch).
 */
internal fun trickplayPresentation(
    mode: TrickplayMode,
    positionMs: Long,
    intervalMs: Int,
    thumbnailCount: Int,
    visible: Int = FILMSTRIP_VISIBLE_TILES,
): TrickplayPresentation {
    val hasManifest = intervalMs > 0 && thumbnailCount > 0
    return when {
        !hasManifest -> TrickplayPresentation.Bubble
        mode == TrickplayMode.FILMSTRIP -> TrickplayPresentation.Filmstrip(
            filmstripWindow(
                centerIndex = trickplayTileIndex(positionMs, intervalMs, thumbnailCount),
                tileCount = thumbnailCount,
                visible = visible,
            ).toList(),
        )
        mode == TrickplayMode.FULLSCREEN -> TrickplayPresentation.Fullscreen
        else -> TrickplayPresentation.Bubble
    }
}

/**
 * Where the surface anchors for a mode. [TrickplayMode.BUBBLE] floats above
 * the controls; FILMSTRIP sits at the seek-bar line; FULLSCREEN fills the
 * surface. The manifest-missing fallback rides the bubble's anchor with it
 * (the two fallbacks move together).
 */
internal fun trickplayAnchor(mode: TrickplayMode, hasTrickplayInfo: Boolean): TrickplayAnchor = when {
    mode == TrickplayMode.FULLSCREEN && hasTrickplayInfo -> TrickplayAnchor.FULL_SURFACE
    mode == TrickplayMode.FILMSTRIP && hasTrickplayInfo -> TrickplayAnchor.SEEK_BAR_LINE
    else -> TrickplayAnchor.FLOATING_BUBBLE
}

/**
 * The per-tile display aspect from the manifest's thumbnail dimensions
 * (width/height are per-tile; tileWidth/tileHeight are the sprite-grid
 * counts). Falls back to 16:9 for a malformed/absent manifest — the
 * placeholder tile never divides by zero.
 */
internal fun trickplayTileAspect(info: TrickplayInfo?): Float {
    // One null-check + width check up front; the height read then carries a
    // non-null receiver (no per-field safe-call chains).
    val width = info?.width?.takeIf { it > 0 } ?: return 16f / 9f
    val height = info.height?.takeIf { it > 0 } ?: return 16f / 9f
    return width.toFloat() / height.toFloat()
}
