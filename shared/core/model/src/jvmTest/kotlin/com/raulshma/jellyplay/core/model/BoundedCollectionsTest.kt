package com.raulshma.jellyplay.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * JVM-lane tests for [lruMapOf]: the JVM/Android actual is the historical
 * `LinkedHashMap(16, 0.75f, accessOrder = true)` + `removeEldestEntry` idiom, so
 * these pin the ACCESS-ORDER semantics (a wasmJs actual would be insertion-ordered
 * and fail the read-refresh tests by documented degrade — hence this suite lives in
 * jvmTest, not commonTest):
 *  - The LRU (least-recently ACCESSED) entry is evicted on insert once the cap is
 *    exceeded — reads (and put-updates) refresh recency.
 *  - maxSize = 0 holds nothing: every insert evicts itself immediately.
 * [trimToSize] is common code pinned here alongside: it drops OLDEST-INSERTED
 * elements (iterator order) until the collection fits the cap, and is a no-op
 * within the cap.
 */
class BoundedCollectionsTest {

    @Test
    fun insertPastCap_evictsEldestInsertedEntry() {
        val map = lruMapOf<String, Int>(maxSize = 2)
        map["a"] = 1
        map["b"] = 2
        map["c"] = 3
        assertEquals(setOf("b", "c"), map.keys) // "a" was never touched again
        assertEquals(2, map.size)
    }

    @Test
    fun reads_refreshRecency_soTheReadEntrySurvivesTheNextInsert() {
        val map = lruMapOf<String, Int>(maxSize = 2)
        map["a"] = 1
        map["b"] = 2
        assertEquals(1, map["a"]) // read -> "a" becomes MRU, "b" becomes LRU
        map["c"] = 3
        assertTrue("a" in map, "read entry must survive eviction, evicted was: ${map.keys}")
        assertTrue("b" !in map, "unread entry must be evicted, survivors were: ${map.keys}")
        assertTrue("c" in map)
    }

    @Test
    fun putUpdateOnExistingKey_countsAsAccess() {
        val map = lruMapOf<String, Int>(maxSize = 2)
        map["a"] = 1
        map["b"] = 2
        map["a"] = 9 // update refreshes recency exactly like a read
        map["c"] = 3
        assertEquals(setOf("a", "c"), map.keys)
        assertEquals(9, map["a"])
    }

    @Test
    fun zeroCapacity_neverHoldsAnyEntry() {
        val map = lruMapOf<String, Int>(maxSize = 0)
        map["only"] = 1
        assertTrue(map.isEmpty(), "size-0 map must evict on every insert, held: ${map.keys}")
        assertNull(map["only"])
    }

    @Test
    fun trimToSize_dropsOldestInsertedUntilItFits() {
        val set = linkedSetOf(1, 2, 3, 4, 5)
        set.trimToSize(maxSize = 2)
        assertEquals(listOf(4, 5), set.toList()) // survivors keep insertion order

        val map = linkedMapOf("a" to 1, "b" to 2, "c" to 3)
        map.keys.trimToSize(maxSize = 1)
        assertEquals(listOf("c"), map.keys.toList())
    }

    @Test
    fun trimToSize_isNoOpWithinCap() {
        val set = linkedSetOf(1, 2)
        set.trimToSize(maxSize = 5)
        assertEquals(listOf(1, 2), set.toList())
    }

    @Test
    fun trimToSize_zeroCap_emptiesTheCollection() {
        val set = linkedSetOf("x", "y")
        set.trimToSize(maxSize = 0)
        assertTrue(set.isEmpty())
    }
}
