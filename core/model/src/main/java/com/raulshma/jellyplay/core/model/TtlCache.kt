package com.raulshma.jellyplay.core.model

import android.os.SystemClock
import java.util.Collections

/**
 * Tiny LRU + TTL cache used across layers (`core:network` HTTP clients,
 * `core:data` repositories) to memoise near-static server responses.
 *
 * Previously duplicated in `core:data` and `core:network` because each module
 * sits below the layer that owned the cache; both depend on `core:model`, so
 * the canonical implementation lives here and the duplicates were removed.
 *
 * Thread-safe via a [Collections.synchronizedMap] wrapper. The `get`-check-`put`
 * sequence is *not* atomic across threads — concurrent callers may both observe a
 * miss and re-fetch. For idempotent network reads this is acceptable (the
 * last writer wins); do not rely on it for compute-expensive single-flight.
 *
 * Prefer the [CacheIdentity]-aware overloads ([get], [put], [remove],
 * [removeByKeyPrefix]) for caches that hold user-scoped data: a wrong identity
 * is a guaranteed miss by construction, so a previous user's data can never be
 * served to the next within the TTL window. The bare-[String]-key overloads
 * remain for subsystems whose keys already prove their own scope (e.g. per-host
 * API clients with no cross-user sharing).
 *
 * @param maxSize LRU eviction threshold (entry-access-order, not insertion).
 * @param ttlMs   time-to-live in milliseconds; entries older than this are
 *                treated as misses on read and evicted lazily.
 * @param clock   monotonic time source; defaults to [SystemClock.elapsedRealtime]
 *                so production behaviour is unchanged. Injectable for unit tests
 *                (the JVM tests in `core:model` cannot call `SystemClock` without
 *                Robolectric).
 */
class TtlCache<V>(
    private val maxSize: Int = DEFAULT_MAX_SIZE,
    private val ttlMs: Long = DEFAULT_TTL_MS,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) {

    private data class Entry<V>(val value: V, val fetchedAt: Long)

    private val map: MutableMap<String, Entry<V>> =
        Collections.synchronizedMap(lruMapOf<String, Entry<V>>(maxSize))

    fun get(key: String): V? {
        // Synchronize the read-then-expire-check-then-remove sequence: a plain
        // `map[key]` followed by a conditional `map.remove` is a compound
        // operation, and `Collections.synchronizedMap` only guards each call
        // individually. Holding the monitor across the pair makes expiry
        // eviction atomic w.r.t. concurrent put/remove.
        synchronized(map) {
            val entry = map[key] ?: return null
            if (clock() - entry.fetchedAt >= ttlMs) {
                map.remove(key)
                return null
            }
            return entry.value
        }
    }

    /** Identity-aware [get]: a wrong identity yields a distinct key and so misses. */
    fun get(identity: CacheIdentity, key: String): V? = get(compositeKey(identity, key))

    fun put(key: String, value: V) {
        map[key] = Entry(value, clock())
    }

    /** Identity-aware [put]. */
    fun put(identity: CacheIdentity, key: String, value: V) {
        put(compositeKey(identity, key), value)
    }

    fun remove(key: String) {
        map.remove(key)
    }

    /** Identity-aware [remove]. */
    fun remove(identity: CacheIdentity, key: String) {
        remove(compositeKey(identity, key))
    }

    /**
     * Removes every entry whose key starts with [prefix]. Use when entries are
     * keyed by `prefix_$id_$suffix` (e.g. per-limit similar-items caches) and a
     * single logical invalidation must evict all suffix variants.
     *
     * Iterates a collection view of a [Collections.synchronizedMap], so the
     * iteration must hold the map's monitor — otherwise a concurrent `put`
     * (`getSimilarItems` populating the cache from the detail VM's scope) can
     * throw `ConcurrentModificationException` or corrupt the access-order links.
     */
    fun removeByKeyPrefix(prefix: String) {
        synchronized(map) {
            map.entries.removeIf { it.key.startsWith(prefix) }
        }
    }

    /**
     * Identity-aware [removeByKeyPrefix]: only entries under [identity] whose
     * key segment starts with [prefix] are evicted. A second identity's matching
     * entries are untouched.
     */
    fun removeByKeyPrefix(identity: CacheIdentity, prefix: String) {
        removeByKeyPrefix(compositeKey(identity, prefix))
    }

    private fun compositeKey(identity: CacheIdentity, key: String): String =
        "${identity.encoded}::$key"

    fun clear() {
        map.clear()
    }

    fun evictExpired() {
        val now = clock()
        synchronized(map) {
            map.entries.removeIf { now - it.value.fetchedAt >= ttlMs }
        }
    }

    companion object {
        const val DEFAULT_MAX_SIZE = 30
        const val DEFAULT_TTL_MS = 2 * 60 * 1000L
    }
}
