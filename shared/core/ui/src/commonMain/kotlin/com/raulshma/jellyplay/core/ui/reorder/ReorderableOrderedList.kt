package com.raulshma.jellyplay.core.ui.reorder

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList

/**
 * One choreography owner for drag-to-reorder lists (settings Appearance →
 * Home Screen Layout, Appearance → Newsletter sections, Navigation
 * Customization Group, audio player queue sheet). [ReorderState] already owns
 * the threshold-swap arithmetic, but each call site still hand-copied the
 * ~40 lines AROUND it: a mirror `mutableStateListOf` seeded from the stored
 * order, a store-emission resync guarded by `!isDragging`, a write-on-diff
 * persist, and the drag callbacks that glue it all together — and the third
 * copy had already drifted (remember-keys reseeding the whole state instead
 * of the guarded resync). This holder owns the choreography once; the call
 * sites shrink to content (row slot + `onPersist = viewModel::setX`).
 *
 * Resync semantic (the majority pattern, pinned by
 * `ReorderableOrderedListTest`): a stored-order emission applies to the
 * mirror and the working order only while NO drag is in flight — an emission
 * that arrives mid-gesture is ignored, not queued; the gesture's final order
 * wins, is persisted on drag end (write-on-diff against the last seeded
 * order), and the persist's own fresh emission then resyncs the idle holder.
 * The holder state survives recompositions (`remember`); the first
 * composition seeds synchronously so the list never renders empty.
 *
 * Compose-observable half: [items] is the mirror the rows render from;
 * [ReorderState] stays unchanged as the order/policy source of truth.
 */
class ReorderableOrderedListState<T : Any>(
    private val onPersist: (List<T>) -> Unit,
) {

    private val reorder = ReorderState<T>()

    /**
     * The last order the holder was seeded/resynced with — the persist diff
     * base (a successful persist advances it to the written order, so a lost
     * store echo can never trigger a duplicate write).
     */
    private var seededOrder: List<T> = emptyList()

    /** The observable mirror list the rows render from. */
    val items: SnapshotStateList<T> = mutableStateListOf()

    /** Whether a drag gesture is in flight (started, not yet ended). */
    val isDragging: Boolean get() = reorder.isDragging

    /**
     * Applies a stored-order emission per the pinned semantic: the diff
     * base always advances to [order], but the mirror + working order are
     * updated only when no drag is in flight.
     */
    fun resync(order: List<T>) {
        seededOrder = order
        if (reorder.isDragging) return
        items.clear()
        items.addAll(order)
        reorder.submitOrder(order)
    }

    /** Records a row's laid-out pixel height (from `onSizeChanged`). */
    fun recordHeight(item: T, heightPx: Int) {
        reorder.recordHeight(item, heightPx)
    }

    /** Starts a gesture on [item]. */
    fun onDragStart(item: T) {
        reorder.beginDrag(item)
    }

    /**
     * Feeds one drag delta for [item]; when the crossing arithmetic reports an
     * order change, the mirror resyncs from the working order. Returns whether
     * the order changed.
     */
    fun onDrag(item: T, deltaY: Float): Boolean {
        if (reorder.drag(item, deltaY)) {
            items.clear()
            items.addAll(reorder.order)
            return true
        }
        return false
    }

    /** Ends the gesture and persists — the only write point, and only on diff. */
    fun onDragEnd() {
        reorder.endDrag()
        persist()
    }

    /** Write-on-diff: the dragged final order in one write, never per-swap. */
    fun persist() {
        val current = items.toList()
        if (current != seededOrder) {
            seededOrder = current
            onPersist(current)
        }
    }
}

/**
 * Remembers a [ReorderableOrderedListState] for [storedOrder], seeding it once
 * synchronously and resyncing on every later emission ([knownOrder] included
 * in the key, so the Navigation Customization Group's known-items merge —
 * `resolveOrder` — re-seeds when the available nav items change).
 */
@Composable
fun <T : Any> rememberReorderableOrderedList(
    storedOrder: List<T>,
    onPersist: (List<T>) -> Unit,
    knownOrder: List<T>? = null,
): ReorderableOrderedListState<T> {
    val state = remember { ReorderableOrderedListState<T>(onPersist) }

    // First composition: seed synchronously (the `remember { }` half of the
    // hand copies) so the list never renders a frame without its rows.
    remember(state, knownOrder) { state.resync(seedOrder(storedOrder, knownOrder)) }

    // Later store emissions: the guarded resync (majority LaunchedEffect copy).
    LaunchedEffect(storedOrder, knownOrder) {
        state.resync(seedOrder(storedOrder, knownOrder))
    }
    return state
}

/** Stored order verbatim, or merged against the known items when provided. */
private fun <T : Any> seedOrder(storedOrder: List<T>, knownOrder: List<T>?): List<T> =
    if (knownOrder == null) storedOrder else resolveOrder(storedOrder, knownOrder)

/**
 * Resolves a stored order against the [knownOrder]: known items in their
 * stored position, then any known items missing from the stored order in
 * their default [knownOrder] position. Unknown stored entries are dropped.
 * (Promoted verbatim from `feature/settings`, where the Navigation
 * Customization Group owned it.)
 */
fun <T> resolveOrder(storedOrder: List<T>, knownOrder: List<T>): List<T> {
    val ordered = storedOrder.filter { it in knownOrder }
    val missing = knownOrder.filter { it !in ordered }
    return ordered + missing
}
