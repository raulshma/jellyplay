package com.raulshma.jellyplay.feature.book

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PageCacheTest {

    /** Value fake — the eviction policy is index math, the page type is irrelevant. */
    private fun page(value: Int): String = "page-$value"

    @Test
    fun getReturnsWhatWasPut() {
        val cache = PageCache<String>()
        val page = page(1)
        cache.put(3, page)
        assertEquals(page, cache[3])
    }

    @Test
    fun getMissingPageIsNull() {
        val cache = PageCache<String>()
        assertNull(cache[0])
    }

    @Test
    fun putEvictsOutsideWindow() {
        val cache = PageCache<String>()
        cache.put(0, page(0))
        cache.put(1, page(1))
        cache.put(5, page(5))
        cache.onPageChanged(5)
        // Window [3, 7] — page 5 only.
        assertNull(cache[0])
        assertNull(cache[1])
        assertNotNull(cache[5])
    }

    @Test
    fun onPageChangedKeepsTwoPagesOnEachSide() {
        val cache = PageCache<String>()
        for (page in 0..9) cache.put(page, page(page))
        cache.onPageChanged(5)
        assertEquals(setOf(3, 4, 5, 6, 7), cache.keys)
    }

    @Test
    fun windowShrinksAtBookEdges() {
        val cache = PageCache<String>()
        for (page in 0..9) cache.put(page, page(page))
        cache.onPageChanged(0)
        assertEquals(setOf(0, 1, 2), cache.keys)

        // A jump evicts the old window; the pager re-renders the new
        // neighborhood before the next re-anchor.
        for (page in 7..9) cache.put(page, page(page))
        cache.onPageChanged(9)
        assertEquals(setOf(7, 8, 9), cache.keys)
    }

    @Test
    fun stragglingPutSurvivesUntilPageChange() {
        val cache = PageCache<String>()
        cache.onPageChanged(10)
        cache.put(0, page(0))
        cache.put(10, page(10))
        // Eviction anchors on page change only — the straggler survives the put.
        assertNotNull(cache[0])
        assertNotNull(cache[10])
        cache.onPageChanged(10)
        assertNull(cache[0])
        assertNotNull(cache[10])
    }

    @Test
    fun clearEmptiesEverything() {
        val cache = PageCache<String>()
        cache.put(1, page(1))
        cache.put(2, page(2))
        cache.clear()
        assertTrue(cache.keys.isEmpty())
    }

    @Test
    fun customWindowIsHonoured() {
        val cache = PageCache<String>(window = 1)
        for (page in 0..4) cache.put(page, page(page))
        cache.onPageChanged(2)
        assertEquals(setOf(1, 2, 3), cache.keys)
    }
}
