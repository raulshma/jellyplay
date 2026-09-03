package com.raulshma.jellyplay.core.ui.components

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pins the entry-count LRU semantics of the internal [DominantColorLruCache]
 * backing `rememberDominantColor` (the color-classifier itself is a platform
 * expect and untested here):
 *
 *  - `get` of a missing key is null and leaves the order untouched;
 *  - inserting past `maxSize` evicts the least-recently-used entry — reads
 *    promote a key to most-recently-used, so a read "canary" survives churn
 *    that evicts unread keys;
 *  - re-putting an existing key promotes and replaces it without growing the
 *    cache (and retires nothing else);
 *  - `maxSize = 0` behaves as an always-empty cache: the loop removes the
 *    just-inserted entry (which is simultaneously eldest and the key) before
 *    breaking.
 */
class DominantColorLruCacheTest {

    @Test
    fun get_missingKey_isNullAndLeavesOrderUntouched() {
        val cache = DominantColorLruCache(maxSize = 2)
        cache.put("a", Color.Red)

        assertNull(cache.get("missing"))

        assertEquals(Color.Red, cache.get("a"))
    }

    @Test
    fun insertingPastCapacity_evictsLeastRecentlyUsed() {
        val cache = DominantColorLruCache(maxSize = 2)
        cache.put("a", Color.Red)
        cache.put("b", Color.Green)
        cache.put("c", Color.Blue)

        assertNull(cache.get("a"), "eldest entry must be evicted")
        assertEquals(Color.Green, cache.get("b"))
        assertEquals(Color.Blue, cache.get("c"))
    }

    @Test
    fun reads_promoteKeysSoReadCanarySurvivesChurn() {
        val cache = DominantColorLruCache(maxSize = 2)
        cache.put("canary", Color.Red)
        cache.put("b", Color.Green)

        // Touch the canary so "b" becomes the eldest.
        assertEquals(Color.Red, cache.get("canary"))

        cache.put("c", Color.Blue)

        assertEquals(Color.Red, cache.get("canary"), "read key must not be evicted")
        assertNull(cache.get("b"), "unread key is the eviction victim")
        assertEquals(Color.Blue, cache.get("c"))
    }

    @Test
    fun rePut_existingKey_replacesAndPromotesWithoutGrowth() {
        val cache = DominantColorLruCache(maxSize = 2)
        cache.put("a", Color.Red)
        cache.put("b", Color.Green)

        cache.put("a", Color.White) // replace + promote; size stays at capacity

        assertEquals(Color.White, cache.get("a"))
        assertEquals(Color.Green, cache.get("b"))
    }

    @Test
    fun rePut_promotedKey_causesTheOtherEntryToBeEvicted() {
        val cache = DominantColorLruCache(maxSize = 2)
        cache.put("a", Color.Red)
        cache.put("b", Color.Green)
        cache.put("a", Color.White) // a is MRU now, b eldest

        cache.put("c", Color.Blue) // evicts b

        assertNull(cache.get("b"))
        assertEquals(Color.White, cache.get("a"))
        assertEquals(Color.Blue, cache.get("c"))
    }

    @Test
    fun zeroCapacity_isAlwaysEmpty() {
        val cache = DominantColorLruCache(maxSize = 0)

        cache.put("a", Color.Red)

        assertNull(cache.get("a"), "maxSize=0 must not retain entries")
    }
}
