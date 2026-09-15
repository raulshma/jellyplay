package com.raulshma.jellyplay.feature.book

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs

/**
 * Live zoom/pan state of one paged-reader tile, extracted from the
 * composable file so its invariants are jvmTest-pinnable:
 *  - `raster` is the bitmap's render multiplier vs the base fit width
 *    (1 = the fitted base bitmap); `zoom` is the graphicsLayer scale ON
 *    TOP of that.
 *  - The VISUAL scale the user sees is [visual] — at raster > 1 the bitmap
 *    displays 1:1 (ContentScale.None), so its intrinsic pixels already
 *    carry `raster`× of the zoom.
 *  - Pan is clamped to ±(visual − 1) × tile / 2 per axis: the page can
 *    never be dragged fully off-screen, and it pans freely inside its
 *    overflow at each scale.
 *
 * The re-raster settle decision ([pageRasterTarget]) and the gesture-claim
 * rule ([shouldClaimZoomGesture]) ride beside the state as pure functions —
 * the tile composable keeps only gesture plumbing and effects.
 */
internal class PageZoomState {
    var zoom by mutableFloatStateOf(1f)
    var raster by mutableFloatStateOf(1f)
    var pan by mutableStateOf(Offset.Zero)
    var tileSize by mutableStateOf(IntSize.Zero)

    val visual: Float get() = if (raster > 1f) raster * zoom else zoom

    fun setVisual(value: Float) {
        val r = raster
        zoom = (value / if (r > 1f) r else 1f).coerceIn(0.05f, 20f)
        clampPan()
    }

    fun clampPan() {
        val maxX = (visual - 1f) * tileSize.width / 2f
        val maxY = (visual - 1f) * tileSize.height / 2f
        pan = Offset(pan.x.coerceIn(-maxX, maxX), pan.y.coerceIn(-maxY, maxY))
    }

    fun reset() {
        zoom = 1f
        raster = 1f
        pan = Offset.Zero
    }
}

/**
 * The settle → re-raster decision: once the visual scale has been still for
 * the settle window, the page re-renders at the visual scale capped by the
 * raster ceiling ([MAX_PAGE_RASTER_SCALE]) and the graphicsLayer scale
 * resets to 1× carrying the sharper bitmap 1:1 — no visual jump. Returns
 * the retarget raster, or null to KEEP the current one: a target within
 * `0.05` of the current raster (or at/below the base) is not worth a
 * re-render.
 */
internal fun pageRasterTarget(visual: Float, currentRaster: Float): Float? {
    if (visual <= 1.01f && currentRaster <= 1f) return null
    val target = visual.coerceIn(1f, MAX_PAGE_RASTER_SCALE)
    return target.takeIf { abs(it - currentRaster) >= 0.05f }
}

/** Pinch ceiling — above the 3× re-raster cap the scaled bitmap is kept. */
internal const val MAX_VISUAL_ZOOM_SCALE = 6f

/**
 * Whether the tile's transform gesture CLAIMS the pointer stream: two
 * fingers, or any finger while zoomed — a single finger at 1× stays free
 * for the pager's swipe (tap zones own single taps).
 */
internal fun shouldClaimZoomGesture(pointerCount: Int, visual: Float): Boolean =
    pointerCount >= 2 || visual > 1.01f
