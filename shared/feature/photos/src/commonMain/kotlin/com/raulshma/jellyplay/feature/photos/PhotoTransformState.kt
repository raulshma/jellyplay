package com.raulshma.jellyplay.feature.photos

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs

/**
 * Live pinch/pan transform of the photo viewer's image, extracted from
 * PhotoViewerScreen's composable (the `PageZoomState` idiom over in
 * player-book) so its invariants are jvmTest-pinnable:
 *  - `scale` is the graphicsLayer multiplier over the fitted image — the
 *    pinch band is [PHOTO_MIN_ZOOM]×..[PHOTO_MAX_ZOOM]×.
 *  - Pan (offset) is admitted ONLY while zoomed in past 1× ([PhotoGesturePolicy.acceptsPan]):
 *    a pinch that collapses back to the fitted scale resets the offset to
 *    zero, so the image can never sit displaced at 1×.
 *
 * The decision rules ride beside the state as pure functions
 * ([PhotoGesturePolicy]) — the screen's `pointerInput` keeps only gesture
 * plumbing (event → policy/state calls), no embedded math.
 */

/** Pinch band floor — below this the image is smaller than fitted and never pans. */
internal const val PHOTO_MIN_ZOOM = 0.5f

/** Pinch band ceiling. */
internal const val PHOTO_MAX_ZOOM = 5f

/**
 * Wall-clock window (ms) between two taps that reads as a double-tap; a tap
 * further apart is a fresh single tap.
 */
internal const val PHOTO_DOUBLE_TAP_WINDOW_MS = 300L

/** Horizontal drag (px, view space) a finished single-finger swipe needs to page. */
internal const val PHOTO_SWIPE_THRESHOLD_PX = 150f

/** Double-tap zoom-in target (the zoom-out arm is [PhotoTransformState.reset]). */
internal const val PHOTO_DOUBLE_TAP_ZOOM = 2.5f

/** Which page a finished swipe asks for — or that the drag was not a swipe. */
internal enum class SwipeDecision { PREVIOUS, NEXT, NONE }

/**
 * The photo viewer's pure gesture decisions. Every rule that used to be an
 * inline magic number inside the composable's gesture loop lives here so the
 * thresholds and their edge cases are pinned without a Robolectric harness.
 */
internal object PhotoGesturePolicy {

    /**
     * The scale after applying a pinch [factor]: multiplicative on the current
     * scale, clamped to the [PHOTO_MIN_ZOOM]..[PHOTO_MAX_ZOOM] band.
     */
    fun clampedPinchScale(currentScale: Float, factor: Float): Float =
        (currentScale * factor).coerceIn(PHOTO_MIN_ZOOM, PHOTO_MAX_ZOOM)

    /**
     * Pan admission gate: the image may be dragged only while zoomed in past
     * 1× — at the fitted scale there is no overflow to pan into.
     */
    fun acceptsPan(scale: Float): Boolean = scale > 1f

    /**
     * Swipe admission gate: a finished single-finger drag pages prev/next only
     * at the fitted scale — while zoomed in, that same drag pans the photo
     * instead (see [acceptsPan]).
     */
    fun acceptsSwipe(scale: Float): Boolean = scale <= 1f

    /**
     * Tap discrimination: a tap within [PHOTO_DOUBLE_TAP_WINDOW_MS] of the
     * previous one is the second tap of a double-tap. At exactly the window
     * edge it is NOT (strict `<`).
     */
    fun isDoubleTap(nowEpochMs: Long, lastTapEpochMs: Long): Boolean =
        nowEpochMs - lastTapEpochMs < PHOTO_DOUBLE_TAP_WINDOW_MS

    /**
     * The finished-drag → page-turn decision: the drag must exceed
     * [PHOTO_SWIPE_THRESHOLD_PX] horizontally AND be more horizontal than
     * vertical; rightward swipes go to the previous photo, leftward to the
     * next (content-coordinate drag deltas). At/under the threshold, or on a
     * vertically dominant drag, the decision is [SwipeDecision.NONE].
     */
    fun swipeDecision(dragDeltaX: Float, dragDeltaY: Float): SwipeDecision {
        if (abs(dragDeltaX) <= PHOTO_SWIPE_THRESHOLD_PX) return SwipeDecision.NONE
        if (abs(dragDeltaX) <= abs(dragDeltaY)) return SwipeDecision.NONE
        return if (dragDeltaX > 0f) SwipeDecision.PREVIOUS else SwipeDecision.NEXT
    }
}

/**
 * The transform state itself — Compose snapshot state so `graphicsLayer`
 * reads stay observable, but every mutation goes through these members (the
 * screen holds no parallel MutableFloatState mirrors anymore).
 */
internal class PhotoTransformState {
    var scale by mutableFloatStateOf(1f)
        private set
    var offsetX by mutableFloatStateOf(0f)
        private set
    var offsetY by mutableFloatStateOf(0f)
        private set

    /**
     * Applies one pinch frame: clamps the resulting scale into the band and
     * returns it (the caller decides pan admission with the NEW scale).
     */
    fun applyPinch(factor: Float): Float {
        scale = PhotoGesturePolicy.clampedPinchScale(scale, factor)
        return scale
    }

    /** Accumulates one pan frame (only meaningful past 1× — see the policy gate). */
    fun addPan(dx: Float, dy: Float) {
        offsetX += dx
        offsetY += dy
    }

    /** Drops the pan back to the centered fit — the pinch-collapse arm. */
    fun resetPan() {
        offsetX = 0f
        offsetY = 0f
    }

    /**
     * Resets to the identity transform — photo switch and the double-tap
     * zoom-out arm share this one semantics.
     */
    fun reset() {
        scale = 1f
        resetPan()
    }

    /**
     * Jumps straight to a target scale (the double-tap zoom-in arm; offsets
     * stay untouched — the invariant "scale ≤ 1 ⇒ offset 0" means they are
     * already centered).
     */
    fun zoomTo(target: Float) {
        scale = target
    }
}
