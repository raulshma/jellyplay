package com.raulshma.jellyplay.core.model

/**
 * wasmJs actuals for the [TtlCache]/[lruMapOf] seam. Kotlin/wasm is a
 * single-threaded target today (no SharedArrayBuffer worker threads), so:
 *  - [withMapMonitor] is a pass-through — there is no other thread to exclude,
 *    and `synchronized` does not exist on this target.
 *  - [ttlBackingMap] is the plain LRU map with no `synchronizedMap` wrapper.
 *  - [lruMapOf] is INSERTION-ORDER eldest-evicting, not access-order:
 *    `kotlin.collections.LinkedHashMap` on wasm has no access-order mode (the
 *    3-arg constructor and the `removeEldestEntry` hook are java.util APIs).
 *    DOCUMENTED DEGRADE — when at cap the cache keeps the first-inserted
 *    rather than the least-recently-read entries; the bounded size and
 *    TTL-miss behavior are identical.
 */
internal actual fun <V> ttlBackingMap(maxSize: Int): MutableMap<String, TtlEntry<V>> =
    lruMapOf(maxSize)

internal actual fun <R> withMapMonitor(monitor: Any, block: () -> R): R = block()

actual fun <K, V> lruMapOf(maxSize: Int): MutableMap<K, V> {
    val backing = LinkedHashMap<K, V>()
    // Interface delegation + one override: `put` is the only insertion path
    // the cache sites use, and it is where the JVM idiom hooks eviction
    // (removeEldestEntry fires post-insert; the while-loop below is the same
    // invariant — after a net-new insert the map holds at most maxSize rows,
    // eldest-INSERTED first out).
    return object : MutableMap<K, V> by backing {
        override fun put(key: K, value: V): V? {
            val previous = backing.put(key, value)
            if (previous == null) {
                while (backing.size > maxSize) {
                    backing.remove(backing.keys.first())
                }
            }
            return previous
        }
    }
}
