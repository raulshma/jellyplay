package com.raulshma.jellyplay.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure-JVM tests — `TtlCache` accepts an injectable clock so it does not depend
 * on `android.os.SystemClock` at test time (the `core:model` module has no
 * Robolectric dependency).
 */
class TtlCacheTest {

    private var fakeNowMs = 0L

    private fun newCache(maxSize: Int = 10, ttlMs: Long = 60_000L) =
        TtlCache<String>(maxSize = maxSize, ttlMs = ttlMs, clock = { fakeNowMs })

    @Test
    fun get_emptyCache_returnsNull() {
        val cache = newCache()
        assertNull(cache.get("key"))
    }

    @Test
    fun put_thenGet_returnsValue() {
        val cache = newCache()
        cache.put("key", "value")
        assertEquals("value", cache.get("key"))
    }

    @Test
    fun get_afterTtl_returnsNull() {
        val cache = newCache(ttlMs = 1_000L)
        cache.put("key", "value")
        fakeNowMs += 1_000L
        assertNull(cache.get("key"))
    }

    @Test
    fun get_beforeTtl_returnsValue() {
        val cache = newCache(ttlMs = 1_000L)
        cache.put("key", "value")
        fakeNowMs += 999L
        assertEquals("value", cache.get("key"))
    }

    @Test
    fun remove_existingKey_removesEntry() {
        val cache = newCache()
        cache.put("key", "value")
        cache.remove("key")
        assertNull(cache.get("key"))
    }

    @Test
    fun remove_nonExistingKey_noOp() {
        val cache = newCache()
        cache.remove("nonexistent")
    }

    @Test
    fun clear_removesAllEntries() {
        val cache = newCache()
        cache.put("key1", "value1")
        cache.put("key2", "value2")
        cache.clear()
        assertNull(cache.get("key1"))
        assertNull(cache.get("key2"))
    }

    @Test
    fun put_overwritesExistingKey() {
        val cache = newCache()
        cache.put("key", "value1")
        cache.put("key", "value2")
        assertEquals("value2", cache.get("key"))
    }

    @Test
    fun put_exceedsMaxSize_evictsEldest() {
        val cache = newCache(maxSize = 3)
        cache.put("key1", "value1")
        cache.put("key2", "value2")
        cache.put("key3", "value3")
        cache.put("key4", "value4")
        assertNull(cache.get("key1"))
        assertNotNull(cache.get("key2"))
        assertNotNull(cache.get("key3"))
        assertNotNull(cache.get("key4"))
    }

    @Test
    fun get_accessedKey_preventsEviction() {
        val cache = newCache(maxSize = 3)
        cache.put("key1", "value1")
        cache.put("key2", "value2")
        cache.put("key3", "value3")
        cache.get("key1")
        cache.put("key4", "value4")
        assertNotNull(cache.get("key1"))
        assertNull(cache.get("key2"))
    }

    @Test
    fun evictExpired_removesStaleEntries() {
        val cache = newCache(ttlMs = 1_000L)
        cache.put("key", "value")
        fakeNowMs += 1_000L
        cache.evictExpired()
        assertNull(cache.get("key"))
    }
}
