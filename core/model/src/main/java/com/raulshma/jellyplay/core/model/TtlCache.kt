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
 * sequence is *not* atomic across threads — concurrent callers may both observe
 * a miss and re-fetch. For idempotent network reads this is acceptable (the
 * last writer wins); do not rely on it for compute-expensive single-flight.
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
        Collections.synchronizedMap(
            object : LinkedHashMap<String, Entry<V>>(16, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry<V>>?): Boolean =
                    size > maxSize
            }
        )

    fun get(key: String): V? {
        val entry = map[key] ?: return null
        if (clock() - entry.fetchedAt >= ttlMs) {
            map.remove(key)
            return null
        }
        return entry.value
    }

    fun put(key: String, value: V) {
        map[key] = Entry(value, clock())
    }

    fun remove(key: String) {
        map.remove(key)
    }

    fun clear() {
        map.clear()
    }

    fun evictExpired() {
        val now = clock()
        map.entries.removeIf { now - it.value.fetchedAt >= ttlMs }
    }

    companion object {
        const val DEFAULT_MAX_SIZE = 30
        const val DEFAULT_TTL_MS = 2 * 60 * 1000L
    }
}
