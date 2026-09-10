package com.raulshma.jellyplay.feature.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the threshold-swap drag-to-reorder policy of [ReorderState] — the
 * arithmetic extracted verbatim from the three inline `moveSection` /
 * `moveItem` closures (Appearance home sections, Appearance newsletter
 * sections, Navigation Customization Group):
 *
 *  - The swap threshold is the STRICT midpoint `(draggedHeight + neighbourHeight) / 2`
 *    — exactly half does not swap, one pixel more does.
 *  - The offset is accumulated BEFORE the dragged row's height is consulted,
 *    so a drag that arrives before `onSizeChanged` measured the row is not
 *    lost — it applies as soon as the height lands (surprising but real:
 *    the inline original did the same).
 *  - An unmeasured neighbour borrows the dragged row's height; an unmeasured
 *    dragged row freezes movement while still accumulating.
 *  - One `drag` call can cross several thresholds (a fast flick), clamping at
 *    the list ends.
 *  - `drag` returns `true` only when the order actually changed — the call
 *    sites' write-on-diff persistence stays silent for gestures that never
 *    crossed a threshold.
 */
class ReorderStateTest {

    private enum class Row { A, B, C, D }

    private fun state(
        order: List<Row>,
        heights: Map<Row, Int> = emptyMap(),
    ): ReorderState<Row> = ReorderState<Row>().apply {
        submitOrder(order)
        heights.forEach { (row, height) -> recordHeight(row, height) }
    }

    // ── Threshold arithmetic ──

    @Test
    fun `exactly the half threshold does not swap one pixel more does`() {
        val s = state(order = listOf(Row.A, Row.B, Row.C), heights = mapOf(Row.A to 100, Row.B to 100, Row.C to 100))
        s.beginDrag(Row.A)

        assertFalse(s.drag(Row.A, 100f), "offset == (100+100)/2 must not swap (strict >)")
        assertEquals(listOf(Row.A, Row.B, Row.C), s.order)

        assertTrue(s.drag(Row.A, 1f), "one pixel past the threshold must swap")
        assertEquals(listOf(Row.B, Row.A, Row.C), s.order)
    }

    @Test
    fun `asymmetric heights threshold at the midpoint of both rows`() {
        // dragged 80, neighbour unmeasured → borrows 80 → threshold (80+80)/2 = 80.
        val s = state(order = listOf(Row.A, Row.B, Row.C), heights = mapOf(Row.A to 80, Row.C to 80))
        s.beginDrag(Row.A)

        assertFalse(s.drag(Row.A, 80f))
        assertEquals(listOf(Row.A, Row.B, Row.C), s.order)

        assertTrue(s.drag(Row.A, 1f))
        assertEquals(listOf(Row.B, Row.A, Row.C), s.order)
    }

    // ── Multi-slot crossings ──

    @Test
    fun `one fast downward flick crosses several thresholds in a single drag call`() {
        val s = state(order = listOf(Row.A, Row.B, Row.C, Row.D), heights = mapOf(Row.A to 100, Row.B to 100, Row.C to 100, Row.D to 100))
        s.beginDrag(Row.A)

        assertTrue(s.drag(Row.A, 350f))

        // 350 crosses three consecutive 100px midpoints, charging 100 each: 350→250→150→50.
        assertEquals(listOf(Row.B, Row.C, Row.D, Row.A), s.order)
    }

    @Test
    fun `incremental deltas accumulate across drag calls of one gesture`() {
        val s = state(order = listOf(Row.A, Row.B, Row.C), heights = mapOf(Row.A to 100, Row.B to 100, Row.C to 100))
        s.beginDrag(Row.A)

        assertFalse(s.drag(Row.A, 60f), "first delta below threshold")
        assertTrue(s.drag(Row.A, 60f), "accumulated 120 now crosses 100")
        assertEquals(listOf(Row.B, Row.A, Row.C), s.order)
    }

    @Test
    fun `upward drag swaps against the previous row past its midpoint`() {
        val s = state(order = listOf(Row.A, Row.B, Row.C), heights = mapOf(Row.A to 100, Row.B to 100, Row.C to 100))
        s.beginDrag(Row.C)

        assertFalse(s.drag(Row.C, -100f), "exactly half must not swap upward either")
        assertTrue(s.drag(Row.C, -1f))
        assertEquals(listOf(Row.A, Row.C, Row.B), s.order)
    }

    @Test
    fun `one fast upward flick crosses several thresholds in a single drag call`() {
        val s = state(order = listOf(Row.A, Row.B, Row.C, Row.D), heights = mapOf(Row.A to 100, Row.B to 100, Row.C to 100, Row.D to 100))
        s.beginDrag(Row.D)

        assertTrue(s.drag(Row.D, -350f))
        assertEquals(listOf(Row.D, Row.A, Row.B, Row.C), s.order)
    }

    // ── List-end clamping ──

    @Test
    fun `the first row cannot move up and the last row cannot move down`() {
        val s = state(order = listOf(Row.A, Row.B), heights = mapOf(Row.A to 100, Row.B to 100))

        s.beginDrag(Row.A)
        assertFalse(s.drag(Row.A, -10_000f))
        assertEquals(listOf(Row.A, Row.B), s.order)
        s.endDrag()

        s.beginDrag(Row.B)
        assertFalse(s.drag(Row.B, 10_000f))
        assertEquals(listOf(Row.A, Row.B), s.order)
    }

    @Test
    fun `a huge downward drag clamps at the last slot instead of overflowing`() {
        val s = state(order = listOf(Row.A, Row.B, Row.C), heights = mapOf(Row.A to 100, Row.B to 100, Row.C to 100))
        s.beginDrag(Row.A)

        assertTrue(s.drag(Row.A, 10_000f))
        assertEquals(listOf(Row.B, Row.C, Row.A), s.order)
    }

    // ── Gesture guard ──

    @Test
    fun `deltas for a row other than the dragged one are ignored`() {
        val s = state(order = listOf(Row.A, Row.B, Row.C), heights = mapOf(Row.A to 100, Row.B to 100, Row.C to 100))
        s.beginDrag(Row.A)

        assertFalse(s.drag(Row.B, 500f))
        assertFalse(s.drag(Row.C, 500f))
        assertFalse(s.drag(Row.A, 60f), "the dragged row's offset only saw its own 60px")
        assertEquals(listOf(Row.A, Row.B, Row.C), s.order)
    }

    @Test
    fun `endDrag stops movement until the next beginDrag`() {
        val s = state(order = listOf(Row.A, Row.B), heights = mapOf(Row.A to 100, Row.B to 100))

        assertFalse(s.isDragging)
        s.beginDrag(Row.A)
        assertTrue(s.isDragging)
        s.endDrag()

        assertFalse(s.drag(Row.A, 999f), "no movement without an active gesture")
        assertEquals(listOf(Row.A, Row.B), s.order)
    }

    @Test
    fun `beginDrag restarts the offset at zero`() {
        val s = state(order = listOf(Row.A, Row.B), heights = mapOf(Row.A to 100, Row.B to 100))

        s.beginDrag(Row.A)
        s.drag(Row.A, 90f) // leftover offset 90
        s.endDrag()

        s.beginDrag(Row.A)
        assertFalse(s.drag(Row.A, 20f), "fresh gesture starts from 0, not the leftover 90")
        assertTrue(s.drag(Row.A, 90f), "accumulated 110 crosses 100 in the new gesture")
        assertEquals(listOf(Row.B, Row.A), s.order)
    }

    // ── Height fallbacks ──

    @Test
    fun `an unmeasured dragged row freezes movement but keeps accumulating the offset`() {
        val s = state(order = listOf(Row.A, Row.B, Row.C))
        s.beginDrag(Row.A)

        assertFalse(s.drag(Row.A, 500f), "no height for the dragged row → no swap")
        assertEquals(listOf(Row.A, Row.B, Row.C), s.order)

        // Height arrives late (onSizeChanged): the accumulated 500px applies immediately,
        // crossing every remaining midpoint down to the clamp.
        s.recordHeight(Row.A, 100)
        assertTrue(s.drag(Row.A, 0f))
        assertEquals(listOf(Row.B, Row.C, Row.A), s.order)
    }

    @Test
    fun `an unmeasured neighbour borrows the dragged row's height`() {
        // Only A measured: B's borrowed height makes the threshold (100+100)/2 = 100.
        val s = state(order = listOf(Row.A, Row.B), heights = mapOf(Row.A to 100))
        s.beginDrag(Row.A)

        assertFalse(s.drag(Row.A, 100f))
        assertTrue(s.drag(Row.A, 1f))
        assertEquals(listOf(Row.B, Row.A), s.order)
    }

    @Test
    fun `zero-height rows swap on any nonzero movement`() {
        val s = state(order = listOf(Row.A, Row.B), heights = mapOf(Row.A to 0, Row.B to 0))
        s.beginDrag(Row.A)

        assertFalse(s.drag(Row.A, 0f), "zero offset is not > the zero threshold")
        assertTrue(s.drag(Row.A, 1f), "threshold (0+0)/2 = 0 crossed by any positive offset")
        assertEquals(listOf(Row.B, Row.A), s.order)
    }

    // ── Order resync + persist signal ──

    @Test
    fun `submitOrder replaces the working list wholesale`() {
        val s = state(order = listOf(Row.A, Row.B, Row.C), heights = mapOf(Row.A to 100, Row.B to 100, Row.C to 100))
        s.beginDrag(Row.A)
        s.drag(Row.A, 350f)
        assertEquals(listOf(Row.B, Row.C, Row.A), s.order)
        s.endDrag()

        // The list-sync half: stored preference changed while not dragging.
        s.submitOrder(listOf(Row.C, Row.A, Row.B))
        assertEquals(listOf(Row.C, Row.A, Row.B), s.order)

        // And the next gesture operates on the submitted order.
        s.beginDrag(Row.C)
        assertTrue(s.drag(Row.C, 150f))
        assertEquals(listOf(Row.A, Row.C, Row.B), s.order)
    }

    @Test
    fun `drag reports true only when the order actually changed`() {
        val s = state(order = listOf(Row.A, Row.B), heights = mapOf(Row.A to 100, Row.B to 100))

        // A sub-threshold gesture: every drag false, order still == stored,
        // so the call site's write-on-diff persist must stay silent.
        s.beginDrag(Row.A)
        val subThresholdMoved = s.drag(Row.A, 99f)
        s.endDrag()
        assertFalse(subThresholdMoved)
        assertEquals(listOf(Row.A, Row.B), s.order)

        // A crossing gesture flips the signal (fresh gesture: full delta needed).
        s.beginDrag(Row.A)
        assertTrue(s.drag(Row.A, 101f))
        s.endDrag()
        assertEquals(listOf(Row.B, Row.A), s.order)
    }
}

/**
 * Pins [resolveOrder] — the stored-order vs known-items merge behind the
 * Navigation Customization Group's list seed: known entries keep their stored
 * position, known entries missing from storage are appended in default order,
 * unknown stored entries are dropped.
 */
class ResolveOrderTest {

    @Test
    fun `stored subset keeps its order and missing knowns append in default order`() {
        assertEquals(listOf("c", "a", "b"), resolveOrder(listOf("c", "a"), listOf("a", "b", "c")))
        assertEquals(listOf("b", "a", "c"), resolveOrder(listOf("b"), listOf("a", "b", "c")))
    }

    @Test
    fun `unknown stored entries are dropped`() {
        assertEquals(listOf("a", "c", "b"), resolveOrder(listOf("x", "a", "c"), listOf("a", "b", "c")))
    }

    @Test
    fun `empty storage falls back to the default order`() {
        assertEquals(listOf("a", "b", "c"), resolveOrder(emptyList(), listOf("a", "b", "c")))
    }

    @Test
    fun `a complete stored order passes through untouched`() {
        assertEquals(listOf("c", "b", "a"), resolveOrder(listOf("c", "b", "a"), listOf("a", "b", "c")))
    }
}
