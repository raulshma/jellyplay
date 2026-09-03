package com.raulshma.jellyplay.core.ui.components

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Mouse drag-to-scroll + wheel-scroll support for the desktop (and any
 * wheel-pointer) form factor.
 *
 * Compose's built-in `scrollable` treats drag gestures as touch-only, so on
 * desktop a mouse drag never scrolls a list, and a vertical wheel over a
 * horizontal list carries no consumable delta for the dominant-axis logic in
 * `MouseWheelScrollingLogic` — it always falls through to the enclosing
 * vertical list. The result: horizontal carousels were unscrollable with the
 * mouse and vertical lists were wheel-only. These two modifiers close that
 * gap by driving the same [ScrollableState] the built-in gestures use.
 *
 * Touch input is unaffected: the list's own `scrollable` consumes touch drags
 * in the Main pass before this (outer) detector completes its slop, so the
 * drag detector cancels, and the wheel handler only reacts to
 * [PointerEventType.Scroll], which touch never produces.
 */

/**
 * Lets the user scroll [state] by dragging with the mouse (or pen): press on
 * the list, move along [orientation] past touch slop, and the content scrolls
 * 1:1 with the pointer. Sub-slop movement still reaches item click handlers,
 * so card clicks behave exactly as before; a drag past slop consumes the
 * movement and cancels the would-be click, like native desktop apps.
 *
 * Uses the orientation-specific `detect*DragGestures` so a horizontal row only
 * claims horizontal-intent drags — a vertical drag started over a row falls
 * through to the enclosing page's vertical scroller.
 *
 * Deltas go through [ScrollableState.dispatchRawDelta] — NOT `scrollBy` — and
 * only when the list can scroll in the drag's direction. The raw path bypasses
 * nested scroll, so a downward drag at the top of a page can never drag out a
 * parent [androidx.compose.material3.pulltorefresh.PullToRefreshBox] indicator
 * (mouse dragging "pull to refresh" is a touch idiom, not a desktop one), and
 * the gate stops the drag dead at either end instead of feeding overscroll.
 */
fun Modifier.mouseDragToScroll(
    state: ScrollableState,
    orientation: Orientation,
): Modifier = pointerInput(state, orientation) {
    val onDrag: (PointerInputChange, Float) -> Unit = { change, dragAmount ->
        // Touch is owned by the list's native scrollable; taking it here
        // would double-scroll.
        if (change.type != PointerType.Touch && dragAmount != 0f) {
            val delta = -dragAmount
            val canScroll = if (delta < 0f) state.canScrollBackward else state.canScrollForward
            if (canScroll) state.dispatchRawDelta(delta)
        }
    }
    if (orientation == Orientation.Horizontal) {
        detectHorizontalDragGestures(onHorizontalDrag = onDrag)
    } else {
        detectVerticalDragGestures(onVerticalDrag = onDrag)
    }
}

/**
 * Scrolls a horizontal list with the vertical mouse wheel: one wheel unit maps
 * to ~10% of the row width (clamped to a quarter per event so browser
 * pixel-unit deltas and accelerated trackpad bursts can't jump pages), which
 * matches the feel of native desktop carousels.
 *
 * The event is consumed only when this list actually scrolled, so at either
 * end of the row the wheel falls through to the enclosing vertical list
 * instead of dead-ending — the same child-first negotiation the built-in
 * wheel logic performs.
 */
fun Modifier.mouseWheelToHorizontalScroll(
    state: ScrollableState,
): Modifier = pointerInput(state) {
    awaitEachGesture {
        while (true) {
            val event = awaitPointerEvent()
            if (event.type != PointerEventType.Scroll) continue
            val change = event.changes.firstOrNull() ?: continue
            if (change.isConsumed) continue
            val wheelUnits = change.scrollDelta.y
            if (wheelUnits == 0f) continue
            // Wheel-down yields negative units (AWT convention, negated by the
            // built-in scroll logic the same way) and must scroll forward.
            val maxPerEvent = size.width / 4f
            val scrollValue = (-wheelUnits * size.width / 10f)
                .coerceIn(-maxPerEvent, maxPerEvent)
            val canScroll = if (scrollValue > 0f) state.canScrollForward else state.canScrollBackward
            if (!canScroll) continue
            // Synchronous so the consume decision below lands within this
            // event's dispatch, before the parent's wheel handler runs.
            val consumed = state.dispatchRawDelta(scrollValue)
            if (consumed != 0f) change.consume()
        }
    }
}
