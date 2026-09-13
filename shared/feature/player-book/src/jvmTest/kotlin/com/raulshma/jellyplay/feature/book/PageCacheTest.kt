package com.raulshma.jellyplay.feature.book

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PageCacheTest {

    /** Value fake — the eviction policy is index math, the page type is irrelevant. */
    private fun page(value: Int): String = "page-$value"

    private fun key(page: Int, width: Int = 1080): PageCacheKey = PageCacheKey(page, width)

    @Test
    fun getReturnsWhatWasPut() {
        val cache = PageCache<String>()
        val page = page(1)
        cache.put(key(3), page)
        assertEquals(page, cache[key(3)])
    }

    @Test
    fun getMissingPageIsNull() {
        val cache = PageCache<String>()
        assertNull(cache[key(0)])
    }

    @Test
    fun putReplacesThePagesPreviousWidth() {
        val cache = PageCache<String>()
        cache.put(key(3, 1080), "base")
        cache.put(key(3, 2160), "zoomed")
        // One width per page — a zoom re-raster must not stack full-resolution
        // bitmaps inside the window.
        assertFalse(cache.keys.contains(key(3, 1080)))
        assertEquals(setOf(key(3, 2160)), cache.keys)
        // The dropped width re-renders on the next request (a plain miss).
        assertNull(cache[key(3, 1080)])
        assertEquals("zoomed", cache[key(3, 2160)])
    }

    @Test
    fun putSameKeyTwiceKeepsASingleEntry() {
        val cache = PageCache<String>()
        cache.put(key(3, 1080), "first")
        cache.put(key(3, 1080), "second")
        assertEquals(1, cache.keys.size)
        assertEquals("second", cache[key(3, 1080)])
    }

    @Test
    fun putEvictsOutsideWindow() {
        val cache = PageCache<String>()
        cache.put(key(0), page(0))
        cache.put(key(1), page(1))
        cache.put(key(5), page(5))
        cache.onPageChanged(5)
        // Window [3, 7] — page 5 only.
        assertNull(cache[key(0)])
        assertNull(cache[key(1)])
        assertNotNull(cache[key(5)])
    }

    @Test
    fun onPageChangedKeepsTwoPagesOnEachSide() {
        val cache = PageCache<String>()
        for (page in 0..9) cache.put(key(page), page(page))
        cache.onPageChanged(5)
        assertEquals((3..7).map(::key).toSet(), cache.keys)
    }

    @Test
    fun windowShrinksAtBookEdges() {
        val cache = PageCache<String>()
        for (page in 0..9) cache.put(key(page), page(page))
        cache.onPageChanged(0)
        assertEquals((0..2).map(::key).toSet(), cache.keys)

        // A jump evicts the old window; the pager re-renders the new
        // neighborhood before the next re-anchor.
        for (page in 7..9) cache.put(key(page), page(page))
        cache.onPageChanged(9)
        assertEquals((7..9).map(::key).toSet(), cache.keys)
    }

    @Test
    fun zoomedRasterOfInWindowPageSurvivesReAnchor() {
        val cache = PageCache<String>()
        cache.put(key(4, 2160), "zoomed")
        cache.onPageChanged(5)
        // Window [3, 7] — the zoom re-raster of an in-window page survives.
        assertEquals(setOf(key(4, 2160)), cache.keys)
    }

    @Test
    fun stragglingPutSurvivesUntilPageChange() {
        val cache = PageCache<String>()
        cache.onPageChanged(10)
        cache.put(key(0), page(0))
        cache.put(key(10), page(10))
        // Eviction anchors on page change only — the straggler survives the put.
        assertNotNull(cache[key(0)])
        assertNotNull(cache[key(10)])
        cache.onPageChanged(10)
        assertNull(cache[key(0)])
        assertNotNull(cache[key(10)])
    }

    @Test
    fun clearEmptiesEverything() {
        val cache = PageCache<String>()
        cache.put(key(1), page(1))
        cache.put(key(2, 2160), page(2))
        cache.clear()
        assertTrue(cache.keys.isEmpty())
    }

    @Test
    fun customWindowIsHonoured() {
        val cache = PageCache<String>(window = 1)
        for (page in 0..4) cache.put(key(page), page(page))
        cache.onPageChanged(2)
        assertEquals((1..3).map(::key).toSet(), cache.keys)
    }
}
