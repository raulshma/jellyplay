package com.raulshma.jellyplay.feature.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the choreography [ReorderableOrderedListState] wraps around
 * [ReorderState] — the ~40-line dance previously hand-copied at the three
 * reorderable-list sites (Appearance home sections, Appearance newsletter
 * sections, Navigation Customization Group), now owned once:
 *
 *  - **Resync semantic (the majority LaunchedEffect pattern):** a stored
 *    emission applies to the mirror only while NO drag is in flight; one
 *    arriving mid-gesture is ignored (not queued) — the drag's final order
 *    wins, is persisted at drag end, and its own fresh emission then resyncs
 *    the idle holder.
 *  - **Write-on-diff persist at drag end only:** a sub-threshold drag (order
 *    unchanged) persists nothing; a crossing gesture persists the dragged
 *    final order in one write. The diff base is the last seeded order.
 *  - **Mirror follows the store when idle:** an idle resync replaces the
 *    mirror wholesale and the next gesture operates on it.
 *
 * [ReorderableOrderedListState] is plain snapshot-state Kotlin (no active
 * composition needed), so the semantics are driven directly here.
 */
class ReorderableOrderedListTest {

    private enum class Row { A, B, C }

    private fun holder(
        stored: List<Row>,
        persisted: MutableList<List<Row>> = mutableListOf(),
    ): ReorderableOrderedListState<Row> =
        ReorderableOrderedListState<Row> { persisted += it }.apply {
            resync(stored)
            // Pre-measure uniform heights so drags swap past the 100px midpoints.
            listOf(Row.A, Row.B, Row.C).forEach { recordHeight(it, 100) }
        }

    // ── Write-on-diff persist at drag end ──

    @Test
    fun `sub-threshold drag persists nothing`() {
        val persisted = mutableListOf<List<Row>>()
        val h = holder(listOf(Row.A, Row.B, Row.C), persisted)

        h.onDragStart(Row.A)
        assertFalse(h.onDrag(Row.A, 99f), "99px must not cross the 100px midpoint")
        h.onDragEnd()

        assertTrue(persisted.isEmpty(), "order never changed → no write")
        assertEquals(listOf(Row.A, Row.B, Row.C), h.items.toList())
    }

    @Test
    fun `a crossing drag persists the dragged final order in one write`() {
        val persisted = mutableListOf<List<Row>>()
        val h = holder(listOf(Row.A, Row.B, Row.C), persisted)

        h.onDragStart(Row.A)
        assertTrue(h.onDrag(Row.A, 350f))
        assertEquals(listOf(Row.B, Row.C, Row.A), h.items.toList(), "mirror resyncs on each crossing")
        h.onDragEnd()

        assertEquals(listOf(listOf(Row.B, Row.C, Row.A)), persisted, "one write, at drag end")
    }

    @Test
    fun `drag end with the order already equal to the seed persists nothing`() {
        val persisted = mutableListOf<List<Row>>()
        val h = holder(listOf(Row.A, Row.B, Row.C), persisted)

        h.onDragStart(Row.A)
        assertTrue(h.onDrag(Row.A, 101f))
        h.onDragEnd()
        assertEquals(1, persisted.size)

        // A second gesture that ends where it started writes nothing.
        h.onDragStart(Row.A)
        assertFalse(h.onDrag(Row.A, 60f))
        h.onDragEnd()
        assertEquals(1, persisted.size)
    }

    // ── Mid-drag store emission: ignored, not queued ──

    @Test
    fun `store emission mid-drag does not touch the mirror and the drag end persists the dragged order`() {
        val persisted = mutableListOf<List<Row>>()
        val h = holder(listOf(Row.A, Row.B, Row.C), persisted)

        h.onDragStart(Row.C)
        assertTrue(h.onDrag(Row.C, -150f))
        assertEquals(listOf(Row.A, Row.C, Row.B), h.items.toList())

        // An emission arriving mid-gesture (e.g. a remote write) is ignored…
        h.resync(listOf(Row.B, Row.A, Row.C))
        assertEquals(listOf(Row.A, Row.C, Row.B), h.items.toList(), "drag in flight: mirror untouched")

        // …and the drag's final order wins at drag end.
        h.onDragEnd()
        assertEquals(listOf(listOf(Row.A, Row.C, Row.B)), persisted)
    }

    @Test
    fun `isDragging is reported while the gesture is in flight`() {
        val h = holder(listOf(Row.A, Row.B, Row.C))
        assertFalse(h.isDragging)
        h.onDragStart(Row.A)
        assertTrue(h.isDragging)
        h.onDragEnd()
        assertFalse(h.isDragging)
    }

    // ── Mirror follows the store when idle ──

    @Test
    fun `idle resync replaces the mirror wholesale and the next gesture operates on it`() {
        val h = holder(listOf(Row.A, Row.B, Row.C))

        h.resync(listOf(Row.C, Row.B, Row.A))
        assertEquals(listOf(Row.C, Row.B, Row.A), h.items.toList())

        h.onDragStart(Row.C)
        assertTrue(h.onDrag(Row.C, 150f), "150 crosses B's midpoint from the resynced order")
        assertEquals(listOf(Row.B, Row.C, Row.A), h.items.toList())
        h.onDragEnd()
    }

    @Test
    fun `the persisted write's own emission resyncs the idle holder to the same order`() {
        val persisted = mutableListOf<List<Row>>()
        val h = holder(listOf(Row.A, Row.B, Row.C), persisted)

        h.onDragStart(Row.A)
        assertTrue(h.onDrag(Row.A, 150f))
        h.onDragEnd()
        val written = persisted.single()

        // The store echoes the write back; the idle holder adopts it (no visible change).
        h.resync(written)
        assertEquals(written, h.items.toList())
    }
}
