package com.raulshma.jellyplay.floating

import kotlin.math.abs

/**
 * Pure drag/tap state machine for [FloatingPlayerService]'s overlay touch
 * handler — extracted from the service-resident `attachDragHandler` closure
 * so the slop threshold, the drag-vs-tap decision and the one-relayout-per-
 * frame coalescing are executable and JVM-testable (the PipLifecyclePolicy
 * precedent: pure policy, the service keeps only the
 * `WindowManager.updateViewLayout` effects and the `postOnAnimation` post).
 *
 * One instance lives across gestures, exactly like the closure variables it
 * replaces. No Android types cross this file: the service feeds `params.x/y`
 * and `event.rawX/rawY` in and applies the returned commands out.
 *
 * **The rules (verbatim from the inline original):**
 * - **Slop** — a gesture becomes a drag once EITHER axis moves strictly more
 *   than [SLOP_PX] raw pixels from the down point (`>`, so exactly 10px is
 *   still a tap). Raw pixels, not density-scaled — the original compared
 *   `event.raw` deltas against a bare `10`. The flag latches for the gesture.
 * - **Move targets** — every move re-aims the window at
 *   `initial + (raw - initialRaw).toInt()`, truncation toward zero, relative
 *   to the DOWN point (not the previous move) — even while still sub-slop.
 * - **Coalescing** — at most one relayout per frame: the first move arms a
 *   frame ([Move.armFrame], the service posts the flush via
 *   `postOnAnimation` and calls [onFrameFlushed] when it fires); later moves
 *   only re-aim the window. [Up.flushFrame] drains an arm still pending at
 *   ACTION_UP so the last position is never lost.
 * - **Tap** — an UP that never crossed slop is a click; the service maps it
 *   to `performClick()` so the Compose controls still receive it.
 */
internal class OverlayDragPolicy {

    /** What the service must apply for one `ACTION_MOVE`. */
    data class Move(
        /** New `WindowManager.LayoutParams.x`. */
        val x: Int,
        /** New `WindowManager.LayoutParams.y`. */
        val y: Int,
        /** `true` when no frame is pending — post the `postOnAnimation` flush. */
        val armFrame: Boolean,
    )

    /** What the service must apply for one `ACTION_UP`. */
    data class Up(
        /** `true` when a frame is still pending — flush the layout now. */
        val flushFrame: Boolean,
        /** `true` when the gesture never crossed slop — `performClick()`. */
        val isTap: Boolean,
    )

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false
    private var layoutFramePending = false

    /**
     * `ACTION_DOWN` — capture the window and raw-touch anchors; not dragging
     * yet. Inherited invariant: a mid-gesture `layoutFramePending` is safe
     * across a fresh down because the posted flush drains through the shared
     * params object, not policy state — and the service's touch source never
     * emits `ACTION_CANCEL` (a bare `when`, no GestureDetector), so none is
     * modelled here.
     */
    fun onDown(windowX: Int, windowY: Int, touchRawX: Float, touchRawY: Float) {
        initialX = windowX
        initialY = windowY
        initialTouchX = touchRawX
        initialTouchY = touchRawY
        isDragging = false
    }

    /** `ACTION_MOVE` — re-aim the window and maybe arm the per-frame flush. */
    fun onMove(touchRawX: Float, touchRawY: Float): Move {
        val dx = touchRawX - initialTouchX
        val dy = touchRawY - initialTouchY
        if (abs(dx) > SLOP_PX || abs(dy) > SLOP_PX) isDragging = true
        val armFrame = !layoutFramePending
        if (armFrame) layoutFramePending = true
        return Move(
            x = initialX + dx.toInt(),
            y = initialY + dy.toInt(),
            armFrame = armFrame,
        )
    }

    /** `ACTION_UP` — drain a pending frame; a sub-slop gesture was a tap. */
    fun onUp(): Up {
        val flushFrame = layoutFramePending
        layoutFramePending = false
        return Up(flushFrame = flushFrame, isTap = !isDragging)
    }

    /** The service's `postOnAnimation` body ran — the next move may arm again. */
    fun onFrameFlushed() {
        layoutFramePending = false
    }

    private companion object {
        /** Raw-pixel drag slop — the inline original's bare `10`, unscaled. */
        const val SLOP_PX = 10
    }
}
