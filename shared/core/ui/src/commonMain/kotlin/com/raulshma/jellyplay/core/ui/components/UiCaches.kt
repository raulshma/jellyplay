package com.raulshma.jellyplay.core.ui.components

/**
 * Mutual-exclusion seam for the UI module's hand-rolled caches. JVM targets
 * (android + desktop, via jvmShared) run [block] under `kotlin.synchronized`;
 * wasm/JS is single-threaded, so its actual is a pass-through.
 *
 * The [lock] receiver stays a plain per-cache `Any()` so call sites keep their
 * original structure and only swap the intrinsic for this seam.
 */
internal expect fun <T> withUiLock(lock: Any, block: () -> T): T

/**
 * Pure-Kotlin stand-in for the JVM idiom
 * `LinkedHashMap(capacity, loadFactor, accessOrder = true)` used by the UI
 * caches ([com.raulshma.jellyplay.core.ui.image.BlurHashCache] and
 * [DominantColorLruCache]). Storage is insertion-ordered on every target;
 * the wrapper reproduces the access-order links:
 *  - [get] of an existing key re-appends it as most-recently-used,
 *  - [put] inserts or updates with MRU promotion (JVM promotes updated keys
 *    too via afterNodeAccess), and
 *  - [removeEldestOrNull] pops the least-recently-used entry.
 *
 * Not thread-safe by itself — confine accesses to [withUiLock]. Eviction
 * policy (byte budget vs entry count) belongs to the caller, exactly like the
 * manual eldest-iteration loops it replaces.
 */
internal class AccessOrderLruMap<K, V> {

    private val map = LinkedHashMap<K, V>()

    val size: Int get() = map.size

    operator fun get(key: K): V? {
        val value = map.remove(key) ?: return null
        map[key] = value
        return value
    }

    fun put(key: K, value: V) {
        map.remove(key)
        map[key] = value
    }

    /** Removes and returns the least-recently-used entry, or null when empty. */
    fun removeEldestOrNull(): Pair<K, V>? {
        val eldest = map.entries.firstOrNull() ?: return null
        map.remove(eldest.key)
        return eldest.key to eldest.value
    }
}
