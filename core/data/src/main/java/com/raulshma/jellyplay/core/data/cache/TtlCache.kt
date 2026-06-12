package com.raulshma.jellyplay.core.data.cache

import android.os.SystemClock
import java.util.Collections

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

    fun remove(key: String) {
        map.remove(key)
    }

    fun clear() {
        map.clear()
    }

    fun evictExpired() {
        val now = elapsed()
        map.entries.removeIf { now - it.value.fetchedAt >= ttlMs }
    }

    private fun elapsed(): Long = SystemClock.elapsedRealtime()

    companion object {
        const val DEFAULT_MAX_SIZE = 30
        const val DEFAULT_TTL_MS = 2 * 60 * 1000L
    }
}
