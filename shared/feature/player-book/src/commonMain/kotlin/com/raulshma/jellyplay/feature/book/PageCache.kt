package com.raulshma.jellyplay.feature.book

/**
 * One cache slot: which page, rasterized at which pixel width. Zoom
 * re-raster renders the same page at different widths — the pair IS the
 * identity, not the page alone.
 */
data class PageCacheKey(val page: Int, val renderWidth: Int)

/**
 * The reader's page cache: keeps only entries within [window] slots of the
 * [current] page (±2 by default) and evicts everything else when the page
 * changes — eviction re-anchors on [onPageChanged] only, so a straggling put
 * outside the window survives until the next re-anchor — a CBZ/PDF page is a full-resolution bitmap, so
 * an unbounded cache OOMs long books within a few dozen swipes. Keys are
 * [PageCacheKey] pairs (page, render width) so a zoom re-raster coexists with
 * the window's base rasters; [put] keeps ONE width per page (a new width
 * replaces the page's previous entry), which bounds the cache at window-size
 * entries regardless of zoom traffic. Generic and platform-free (the unit
 * test drives it with value fakes instead of real bitmaps); the document
 * back-ends own one [PageCache]<ImageBitmap> each.
 */
class PageCache<T : Any>(private val window: Int = DEFAULT_WINDOW) {

    private val cache = HashMap<PageCacheKey, T>()

    /** The page the reader is on; the eviction anchor. */
    var current: Int = 0
        private set

    val keys: Set<PageCacheKey> get() = cache.keys

    operator fun get(key: PageCacheKey): T? = cache[key]

    /** Caches the raster; same-page different-width entries are replaced (one width per page). */
    fun put(key: PageCacheKey, page: T) {
        if (!cache.containsKey(key)) {
            cache.keys.removeAll { it.page == key.page }
        }
        cache[key] = page
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
        cache.keys.removeAll { it.page < low || it.page > high }
    }

    companion object {
        /** ±2 pages around the current one. */
        const val DEFAULT_WINDOW = 2
    }
}
