package com.raulshma.jellyplay.core.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the raw access-order LRU contract of [AccessOrderLruMap] (declared
 * internal in commonMain `UiCaches.kt`; the jvmShared half of that file only
 * supplies the `synchronized` [withUiLock] actual):
 *  - `get` of an EXISTING key re-appends it as most-recently-used,
 *  - `put` of an existing key promotes it to MRU too (JVM LinkedHashMap
 *    access-order parity, including `afterNodeAccess` on update),
 *  - [AccessOrderLruMap.removeEldestOrNull] pops the least-recently-used entry
 *    as a key/value pair, null when empty,
 *  - a missing-key `get` leaves the order untouched, and
 *  - the map never auto-evicts — eviction policy (byte budget vs count)
 *    belongs to the caller.
 *
 * [com.raulshma.jellyplay.core.ui.image.BlurHashCacheAccountingTest] already
 * covers the byte-budget eviction at the BlurHashCache level; these tests pin
 * the ordering primitive beneath it and do not duplicate that coverage.
 */
class UiCachesTest {

    @Test
    fun removeEldest_withoutReads_isInsertionOrder() {
        val map = AccessOrderLruMap<String, Int>()
        map.put("a", 1)
        map.put("b", 2)
        map.put("c", 3)

        assertEquals("a" to 1, map.removeEldestOrNull())
        assertEquals("b" to 2, map.removeEldestOrNull())
        assertEquals("c" to 3, map.removeEldestOrNull())
        assertNull(map.removeEldestOrNull())
    }

    @Test
    fun get_ofExistingKey_refreshesRecency() {
        val map = AccessOrderLruMap<String, Int>()
        map.put("a", 1)
        map.put("b", 2)

        // Reading 'a' makes it the most-recently-used entry, so 'b' is now the
        // one eviction pops first.
        assertEquals(1, map["a"])

        assertEquals("b" to 2, map.removeEldestOrNull())
        assertEquals("a" to 1, map.removeEldestOrNull())
        assertNull(map.removeEldestOrNull())
    }

    @Test
    fun put_ofExistingKey_promotesToMruWithValueReplacement() {
        val map = AccessOrderLruMap<String, Int>()
        map.put("a", 1)
        map.put("b", 2)

        // Re-placing 'a' is an access: MRU promotion (JVM afterNodeAccess
        // parity) AND the value is replaced.
        map.put("a", 10)
        assertEquals(10, map["a"])
        assertEquals(2, map.size, "re-put must not grow the map")

        assertEquals("b" to 2, map.removeEldestOrNull())
        assertEquals("a" to 10, map.removeEldestOrNull())
    }

    @Test
    fun get_ofMissingKey_leavesOrderUntouched() {
        val map = AccessOrderLruMap<String, Int>()
        map.put("a", 1)
        map.put("b", 2)

        assertNull(map["zz"])
        assertEquals("a" to 1, map.removeEldestOrNull(), "a failed lookup must not reshuffle the order")
        assertEquals("b" to 2, map.removeEldestOrNull())
    }

    @Test
    fun emptyMap_removeEldestIsNull() {
        val map = AccessOrderLruMap<String, Int>()

        assertNull(map.removeEldestOrNull())
        assertEquals(0, map.size)
    }

    @Test
    fun get_ofEmptyMap_isNull() {
        assertNull(AccessOrderLruMap<String, Int>()["any"])
    }

    @Test
    fun mapNeverAutoEvicts_capacityBelongsToTheCaller() {
        // The map is a plain ordered store: inserting past any imagined
        // capacity keeps every entry until the caller drains via
        // removeEldestOrNull (exactly what the byte-budget loops do).
        val map = AccessOrderLruMap<Int, Int>()
        repeat(10) { map.put(it, it * it) }

        assertEquals(10, map.size)
        assertEquals(0 to 0, map.removeEldestOrNull())
        assertEquals(9, map.size)
    }

    @Test
    fun mixedReadsAndWrites_evictInTrueAccessOrder() {
        val map = AccessOrderLruMap<String, String>()
        map.put("a", "A")
        map.put("b", "B")
        map.put("c", "C")
        map["a"]        // a → MRU
        map.put("b", "B2") // b → MRU (now a, c, b)
        assertEquals("C", map["c"]) // c → MRU (now a, b, c)

        assertEquals("a" to "A", map.removeEldestOrNull())
        assertEquals("b" to "B2", map.removeEldestOrNull())
        assertEquals("c" to "C", map.removeEldestOrNull())
        assertTrue(map.size == 0)
    }
}
