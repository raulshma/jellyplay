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

    // ── removeByKeyPrefix ───────────────────────────────────────────────────
    // Used by MediaRepositoryImpl to evict every `similar_${itemId}_$limit`
    // variant with one call. The matching is a plain String.startsWith, so the
    // contract pinned here is: drop all keys sharing the prefix, leave every
    // other key (including keys that merely contain the prefix as a substring).

    @Test
    fun removeByKeyPrefix_removesAllMatchingEntries() {
        val cache = newCache()
        cache.put("similar_item-1_12", "a")
        cache.put("similar_item-1_9", "b")
        cache.put("similar_item-1", "c")

        cache.removeByKeyPrefix("similar_item-1")

        assertNull(cache.get("similar_item-1_12"))
        assertNull(cache.get("similar_item-1_9"))
        assertNull(cache.get("similar_item-1"))
    }

    @Test
    fun removeByKeyPrefix_leavesNonMatchingEntriesIntact() {
        val cache = newCache()
        cache.put("similar_item-1_12", "keep-prefix-match")
        cache.put("similar_item-2_12", "different-item")
        // A key that merely contains the prefix as a substring must NOT be
        // removed — removeByKeyPrefix matches from the start of the key only.
        cache.put("xsimilar_item-1_12", "substring-not-prefix")

        cache.removeByKeyPrefix("similar_item-1")

        assertNull(cache.get("similar_item-1_12"))
        assertEquals("different-item", cache.get("similar_item-2_12"))
        assertEquals("substring-not-prefix", cache.get("xsimilar_item-1_12"))
    }

    @Test
    fun removeByKeyPrefix_withNoMatches_isNoOp() {
        val cache = newCache()
        cache.put("key1", "value1")
        cache.removeByKeyPrefix("nonexistent")
        assertEquals("value1", cache.get("key1"))
    }

    @Test
    fun removeByKeyPrefix_emptyPrefix_removesEverything() {
        val cache = newCache()
        cache.put("key1", "value1")
        cache.put("key2", "value2")

        cache.removeByKeyPrefix("")

        assertNull(cache.get("key1"))
        assertNull(cache.get("key2"))
    }

    // ── TTL refresh on overwrite ────────────────────────────────────────────
    // A re-put of an existing key must reset the entry's freshness window so a
    // long-lived-but-recently-refreshed entry is not evicted as stale.

    @Test
    fun put_overwritesAndRefreshesEntryTtl() {
        val cache = newCache(ttlMs = 1_000L)
        cache.put("key", "value1")
        // Advance near (but not past) the original entry's expiry.
        fakeNowMs += 900L
        // Overwrite resets the fetchedAt clock.
        cache.put("key", "value2")
        // Advance the same near-expiry delta again: a non-refreshed entry would
        // now be 1800ms old (> ttl), but the overwrite reset it to 900ms (< ttl).
        fakeNowMs += 900L
        assertEquals("value2", cache.get("key"))
    }

    @Test
    fun evictExpired_keepsFreshEntriesAndOnlyRemovesStale() {
        val cache = newCache(ttlMs = 1_000L)
        cache.put("fresh", "fresh-value")
        fakeNowMs += 500L
        cache.put("stale", "stale-value")
        fakeNowMs += 500L
        // "fresh" is now 1000ms old (>= ttl → stale); "stale" is 500ms old (fresh).
        cache.evictExpired()

        assertNull(cache.get("fresh"))
        assertEquals("stale-value", cache.get("stale"))
    }

    @Test
    fun get_doesNotMutateTtlOnRead() {
        // Reading an entry must NOT refresh its TTL (this is a TTL cache, not a
        // sliding-window cache). After ttlMs elapse the entry expires even if
        // it was read repeatedly in between.
        val cache = newCache(ttlMs = 1_000L)
        cache.put("key", "value")
        fakeNowMs += 500L
        assertEquals("value", cache.get("key")) // read mid-window
        fakeNowMs += 500L // total elapsed == ttl
        assertNull(cache.get("key"))
    }

    // ── Identity-keyed isolation ──────────────────────────────────────────
    // A different (serverId, userId) must be a guaranteed cache miss: the whole
    // point of threading CacheIdentity into the key is that a wrong identity
    // can never serve another identity's data within the TTL window.

    private val identityA = CacheIdentity.of("server-1", "user-A")
    private val identityB = CacheIdentity.of("server-1", "user-B")

    @Test
    fun get_identityAware_putThenGetSameIdentity_returnsValue() {
        val cache = newCache()
        cache.put(identityA, "key", "value")
        assertEquals("value", cache.get(identityA, "key"))
    }

    @Test
    fun get_identityAware_differentIdentity_returnsNull() {
        val cache = newCache()
        cache.put(identityA, "key", "value")
        assertNull(cache.get(identityB, "key"))
    }

    @Test
    fun get_identityAware_sameContentKeyUnderTwoIdentities_coexist() {
        val cache = newCache()
        cache.put(identityA, "key", "value-A")
        cache.put(identityB, "key", "value-B")
        assertEquals("value-A", cache.get(identityA, "key"))
        assertEquals("value-B", cache.get(identityB, "key"))
    }

    @Test
    fun remove_identityAware_onlyAffectsThatIdentity() {
        val cache = newCache()
        cache.put(identityA, "key", "value-A")
        cache.put(identityB, "key", "value-B")

        cache.remove(identityA, "key")

        assertNull(cache.get(identityA, "key"))
        assertEquals("value-B", cache.get(identityB, "key"))
    }

    @Test
    fun removeByKeyPrefix_identityAware_scopedToThatIdentity() {
        val cache = newCache()
        cache.put(identityA, "similar_item1_5", "a1")
        cache.put(identityA, "similar_item1_10", "a2")
        cache.put(identityB, "similar_item1_5", "b1")
        cache.put(identityB, "similar_item1_10", "b2")

        cache.removeByKeyPrefix(identityA, "similar_item1")

        assertNull(cache.get(identityA, "similar_item1_5"))
        assertNull(cache.get(identityA, "similar_item1_10"))
        assertEquals("b1", cache.get(identityB, "similar_item1_5"))
        assertEquals("b2", cache.get(identityB, "similar_item1_10"))
    }

    @Test
    fun get_identityAware_respectsTtlExpiry() {
        val cache = newCache(ttlMs = 1_000L)
        cache.put(identityA, "key", "value")
        fakeNowMs += 1_000L
        assertNull(cache.get(identityA, "key"))
    }

    @Test
    fun get_unknownIdentity_neverServesRealIdentityData() {
        val cache = newCache()
        cache.put(identityA, "key", "value")
        // Pre-login / post-logout identity can never read a real identity's entry.
        assertNull(cache.get(CacheIdentity.UNKNOWN, "key"))
    }
}
