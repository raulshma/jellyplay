package com.raulshma.jellyplay.feature.book

import com.raulshma.jellyplay.core.datastore.reader.ReadingLayout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the spread-mode page↔slot mapping ([SpreadSlots]): the cover-alone
 * convention (page 0 solo, then (1,2), (3,4), …), the odd-page-count tail
 * that renders its lead page alone, clamping at every boundary, and the
 * SINGLE degenerate identities — progress ticks, bookmarks and TOC jumps all
 * cross this boundary, so the mapping is load-bearing.
 */
class SpreadSlotsTest {

    @Test
    fun `single layout maps every page to its own slot`() {
        assertEquals(5, SpreadSlots.slotCount(5, ReadingLayout.SINGLE))
        assertEquals(0, SpreadSlots.slotCount(0, ReadingLayout.SINGLE))
        assertEquals(3, SpreadSlots.firstPageOfSlot(3, 5, ReadingLayout.SINGLE))
        assertEquals(listOf(3), SpreadSlots.pagesOfSlot(3, 5, ReadingLayout.SINGLE))
        assertEquals(2, SpreadSlots.slotForPage(2, 5, ReadingLayout.SINGLE))
    }

    @Test
    fun `single layout clamps out-of-range slots and pages`() {
        assertEquals(4, SpreadSlots.firstPageOfSlot(99, 5, ReadingLayout.SINGLE))
        assertEquals(0, SpreadSlots.firstPageOfSlot(-1, 5, ReadingLayout.SINGLE))
        assertEquals(0, SpreadSlots.firstPageOfSlot(0, 0, ReadingLayout.SINGLE))
        assertEquals(emptyList(), SpreadSlots.pagesOfSlot(5, 5, ReadingLayout.SINGLE))
        assertEquals(4, SpreadSlots.slotForPage(99, 5, ReadingLayout.SINGLE))
    }

    @Test
    fun `double layout is cover alone then pairs`() {
        // 5 pages → 3 slots: (0), (1,2), (3,4).
        assertEquals(3, SpreadSlots.slotCount(5, ReadingLayout.DOUBLE))
        assertEquals(listOf(0), SpreadSlots.pagesOfSlot(0, 5, ReadingLayout.DOUBLE))
        assertEquals(listOf(1, 2), SpreadSlots.pagesOfSlot(1, 5, ReadingLayout.DOUBLE))
        assertEquals(listOf(3, 4), SpreadSlots.pagesOfSlot(2, 5, ReadingLayout.DOUBLE))
    }

    @Test
    fun `double layout renders the tail lead page alone on odd counts`() {
        // 4 pages → 3 slots: (0), (1,2), (3) — the last pair is a singleton.
        assertEquals(3, SpreadSlots.slotCount(4, ReadingLayout.DOUBLE))
        assertEquals(listOf(3), SpreadSlots.pagesOfSlot(2, 4, ReadingLayout.DOUBLE))
        // Even the 1-page book is just the cover slot.
        assertEquals(1, SpreadSlots.slotCount(1, ReadingLayout.DOUBLE))
        assertEquals(listOf(0), SpreadSlots.pagesOfSlot(0, 1, ReadingLayout.DOUBLE))
        assertEquals(0, SpreadSlots.slotCount(0, ReadingLayout.DOUBLE))
        assertEquals(emptyList(), SpreadSlots.pagesOfSlot(0, 0, ReadingLayout.DOUBLE))
    }

    @Test
    fun `double layout slot lookup follows odd pages own the pair`() {
        // Odd pages lead their pair slot; even pages share the previous one.
        assertEquals(0, SpreadSlots.slotForPage(0, 5, ReadingLayout.DOUBLE))
        assertEquals(1, SpreadSlots.slotForPage(1, 5, ReadingLayout.DOUBLE))
        assertEquals(1, SpreadSlots.slotForPage(2, 5, ReadingLayout.DOUBLE))
        assertEquals(2, SpreadSlots.slotForPage(3, 5, ReadingLayout.DOUBLE))
        assertEquals(2, SpreadSlots.slotForPage(4, 5, ReadingLayout.DOUBLE))
        assertEquals(0, SpreadSlots.slotForPage(-3, 5, ReadingLayout.DOUBLE))
        assertEquals(0, SpreadSlots.slotForPage(2, 0, ReadingLayout.DOUBLE))
    }

    @Test
    fun `double layout clamps first page lookups to the slot range`() {
        assertEquals(0, SpreadSlots.firstPageOfSlot(0, 5, ReadingLayout.DOUBLE))
        assertEquals(1, SpreadSlots.firstPageOfSlot(1, 5, ReadingLayout.DOUBLE))
        assertEquals(3, SpreadSlots.firstPageOfSlot(2, 5, ReadingLayout.DOUBLE))
        // Out-of-range slots clamp instead of producing phantom pages.
        assertEquals(3, SpreadSlots.firstPageOfSlot(99, 5, ReadingLayout.DOUBLE))
        assertEquals(0, SpreadSlots.firstPageOfSlot(-1, 5, ReadingLayout.DOUBLE))
        assertEquals(0, SpreadSlots.firstPageOfSlot(0, 0, ReadingLayout.DOUBLE))
        assertEquals(emptyList(), SpreadSlots.pagesOfSlot(99, 5, ReadingLayout.DOUBLE))
    }

    @Test
    fun `double layout page to slot to pages round-trips every page`() {
        val pageCount = 7
        for (page in 0 until pageCount) {
            val slot = SpreadSlots.slotForPage(page, pageCount, ReadingLayout.DOUBLE)
            assertTrue(page in SpreadSlots.pagesOfSlot(slot, pageCount, ReadingLayout.DOUBLE), "page $page lost round-tripping through slot $slot")
        }
        for (slot in 0 until SpreadSlots.slotCount(pageCount, ReadingLayout.DOUBLE)) {
            assertEquals(slot, SpreadSlots.slotForPage(SpreadSlots.firstPageOfSlot(slot, pageCount, ReadingLayout.DOUBLE), pageCount, ReadingLayout.DOUBLE))
        }
    }
}
