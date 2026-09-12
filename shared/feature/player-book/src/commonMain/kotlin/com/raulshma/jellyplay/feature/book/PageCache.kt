package com.raulshma.jellyplay.feature.book

/**
 * The reader's page cache: keeps only entries within [window] slots of the
 * [current] page (±2 by default) and evicts everything else when the page
 * changes — eviction re-anchors on [onPageChanged] only, so a straggling put
 * outside the window survives until the next re-anchor — a CBZ/PDF page is a full-resolution bitmap, so
 * an unbounded cache OOMs long books within a few dozen swipes. Generic and
 * platform-free (the unit test drives it with value fakes instead of real
 * bitmaps); the document back-ends own one [PageCache]<ImageBitmap> each.
 */
class PageCache<T : Any>(private val window: Int = DEFAULT_WINDOW) {

    private val cache = HashMap<Int, T>()

    /** The page the reader is on; the eviction anchor. */
    var current: Int = 0
        private set

    val keys: Set<Int> get() = cache.keys

    operator fun get(pageIndex: Int): T? = cache[pageIndex]

    fun put(pageIndex: Int, page: T) {
        cache[pageIndex] = page
    }

    /** Re-anchors the window on [page] and evicts pages outside the new window. */
    fun onPageChanged(page: Int) {
        current = page
        evict()
    }

    fun clear() = cache.clear()

    private fun evict() {
        val low = current - window
        val high = current + window
        cache.keys.removeAll { it < low || it > high }
    }

    companion object {
        /** ±2 pages around the current one. */
        const val DEFAULT_WINDOW = 2
    }
}
