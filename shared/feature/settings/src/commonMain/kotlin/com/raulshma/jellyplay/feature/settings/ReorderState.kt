package com.raulshma.jellyplay.feature.settings

/**
 * The threshold-swap drag-to-reorder policy behind the settings reorderable
 * lists (Appearance → Home Screen Layout, Appearance → Newsletter sections,
 * Navigation Customization Group). Pure Kotlin — no Compose — so the gesture
 * composables stay thin forwarders and [ReorderStateTest] can pin the
 * arithmetic directly.
 *
 * **Why this exists.** All three screens used to inline the same ~35-line
 * `moveSection` / `moveItem` closure: accumulate the drag delta, then loop
 * while the offset crosses the midpoint between the dragged row and its
 * neighbour, `removeAt` + `add`-ing one slot per crossing and charging the
 * crossed neighbour's height against the offset. Three verbatim copies of
 * pixel arithmetic inside composable closures is exactly the shape that
 * drifts silently, so the loop lives here once.
 *
 * Semantics (pinned by [ReorderStateTest], kept byte-for-byte from the
 * inline originals):
 *  - Only the row passed to [beginDrag] moves; deltas for any other row are
 *    ignored.
 *  - The delta is accumulated into the offset BEFORE the dragged row's height
 *    is consulted, so a drag that arrives before the row's height was
 *    measured is not lost — it applies as soon as [recordHeight] lands.
 *  - Swap threshold is the strict midpoint `(draggedHeight + neighbourHeight) / 2`
 *    — exactly half does NOT swap. An unmeasured neighbour borrows the
 *    dragged row's height; an unmeasured dragged row freezes movement (but
 *    keeps accumulating).
 *  - One [drag] call can cross several thresholds (a fast flick), swapping
 *    one slot per crossing; at the list ends the loop clamps.
 *
 * The module is the source of truth for the ORDER only. Compose observes it
 * through a mirrored `mutableStateListOf` the call site re-syncs when [drag]
 * reports `true`, and the caller owns persistence (write-on-diff against the
 * stored preference at drag end) — the same shape the inline closures had.
 */
internal class ReorderState<T : Any> {

    private val workingOrder = mutableListOf<T>()
    private val heights = mutableMapOf<T, Int>()
    private var dragging: T? = null
    private var dragOffsetY = 0f

    /** The current row order; read-only view over the working list. */
    val order: List<T> get() = workingOrder

    /** Whether a drag gesture is in flight (started, not yet ended). */
    val isDragging: Boolean get() = dragging != null

    /**
     * Replaces the working order wholesale — the list-sync half of the
     * pattern: whenever the stored preference order changes and no drag is
     * in flight, the call site re-seeds both its mirror list and this state.
     */
    fun submitOrder(items: List<T>) {
        workingOrder.clear()
        workingOrder.addAll(items)
    }

    /** Records a row's laid-out pixel height (from `onSizeChanged`). */
    fun recordHeight(item: T, heightPx: Int) {
        heights[item] = heightPx
    }

    /** Starts a gesture on [item]; the pixel offset restarts at zero. */
    fun beginDrag(item: T) {
        dragging = item
        dragOffsetY = 0f
    }

    /**
     * Feeds one drag delta (pixels, +down/-up) for the row passed to
     * [beginDrag]. Returns whether the order changed — the call site's signal
     * to re-sync its observable mirror list from [order].
     */
    fun drag(item: T, deltaY: Float): Boolean {
        if (dragging != item) return false
        dragOffsetY += deltaY
        var changed = false
        while (true) {
            val currentIndex = workingOrder.indexOf(item)
            if (currentIndex == -1) return changed
            val draggedHeight = heights[item] ?: return changed

            if (dragOffsetY > 0f && currentIndex < workingOrder.lastIndex) {
                val nextItem = workingOrder[currentIndex + 1]
                val nextHeight = heights[nextItem] ?: draggedHeight
                val threshold = (draggedHeight + nextHeight) / 2f
                if (dragOffsetY > threshold) {
                    workingOrder.removeAt(currentIndex)
                    workingOrder.add(currentIndex + 1, item)
                    dragOffsetY -= nextHeight.toFloat()
                    changed = true
                    continue
                }
            }

            if (dragOffsetY < 0f && currentIndex > 0) {
                val prevItem = workingOrder[currentIndex - 1]
                val prevHeight = heights[prevItem] ?: draggedHeight
                val threshold = (draggedHeight + prevHeight) / 2f
                if (-dragOffsetY > threshold) {
                    workingOrder.removeAt(currentIndex)
                    workingOrder.add(currentIndex - 1, item)
                    dragOffsetY += prevHeight.toFloat()
                    changed = true
                    continue
                }
            }
            break
        }
        return changed
    }

    /** Ends the gesture; further [drag] calls are ignored until [beginDrag]. */
    fun endDrag() {
        dragging = null
    }
}
