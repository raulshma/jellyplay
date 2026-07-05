package com.raulshma.jellyplay.core.network.cache

import android.os.SystemClock
import java.util.Collections

/**
 * Tiny LRU + TTL cache used inside [core:network] clients (e.g.
 * `MediaInfoApiClientImpl`, `AdminApiClientImpl`) to memoise near-static
 * server responses that sit below the repository-layer caches in `core:data`.
 *
 * `core:network` cannot depend on `core:data` (the dependency direction is
 * inverted), so this is a focused duplicate of the `core:data` `TtlCache`
 * rather than a shared abstraction. Keep the two implementations in sync.
 */
class TtlCache<V>(
    private val maxSize: Int = DEFAULT_MAX_SIZE,
    private val ttlMs: Long = DEFAULT_TTL_MS,
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
        if (elapsed() - entry.fetchedAt >= ttlMs) {
            map.remove(key)
            return null
        }
        return entry.value
    }

    fun put(key: String, value: V) {
        map[key] = Entry(value, elapsed())
    }

    fun clear() {
        map.clear()
    }

    private fun elapsed(): Long = SystemClock.elapsedRealtime()

    companion object {
        const val DEFAULT_MAX_SIZE = 30
        const val DEFAULT_TTL_MS = 2 * 60 * 1000L
    }
}
