package com.raulshma.jellyplay.core.data.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TtlCacheTest {

    @Test
    fun get_emptyCache_returnsNull() {
        val cache = TtlCache<String>(maxSize = 10, ttlMs = 60_000L)
        assertNull(cache.get("key"))
    }

    @Test
    fun put_thenGet_returnsValue() {
        val cache = TtlCache<String>(maxSize = 10, ttlMs = 60_000L)
        cache.put("key", "value")
        assertEquals("value", cache.get("key"))
    }

    @Test
    fun remove_existingKey_removesEntry() {
        val cache = TtlCache<String>(maxSize = 10, ttlMs = 60_000L)
        cache.put("key", "value")
        cache.remove("key")
        assertNull(cache.get("key"))
    }

    @Test
    fun remove_nonExistingKey_noOp() {
        val cache = TtlCache<String>(maxSize = 10, ttlMs = 60_000L)
        cache.remove("nonexistent")
    }

    @Test
    fun clear_removesAllEntries() {
        val cache = TtlCache<String>(maxSize = 10, ttlMs = 60_000L)
        cache.put("key1", "value1")
        cache.put("key2", "value2")
        cache.clear()
        assertNull(cache.get("key1"))
        assertNull(cache.get("key2"))
    }

    @Test
    fun put_overwritesExistingKey() {
        val cache = TtlCache<String>(maxSize = 10, ttlMs = 60_000L)
        cache.put("key", "value1")
        cache.put("key", "value2")
        assertEquals("value2", cache.get("key"))
    }

    @Test
    fun put_exceedsMaxSize_evictsEldest() {
        val cache = TtlCache<String>(maxSize = 3, ttlMs = 60_000L)
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
        val cache = TtlCache<String>(maxSize = 3, ttlMs = 60_000L)
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
        val cache = TtlCache<String>(maxSize = 10, ttlMs = 0L)
        cache.put("key", "value")
        cache.evictExpired()
        assertNull(cache.get("key"))
    }
}
