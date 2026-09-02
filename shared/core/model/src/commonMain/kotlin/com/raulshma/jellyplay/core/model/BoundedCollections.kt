package com.raulshma.jellyplay.core.model

/**
 * Access-order LRU map capped at [maxSize]: reads re-order to MRU and the
 * LRU entry is evicted on insert once the cap is exceeded.
 *
 * Factory for the `object : LinkedHashMap(…, 0.75f, true) { override fun
 * removeEldestEntry }` idiom previously duplicated per cache site (lyrics
 * offsets, subtitle previews, typefaces, empty-library fallbacks, TTL
 * entries). Not thread-safe by itself — confine access to one dispatcher
 * or synchronize externally, as each call site documents for its own regime.
 *
 * Promoted to commonMain in wave 15B when `core:data` grew a wasmJs target
 * (its commonMain code was already calling [lruMapOf]). Platform regimes:
 *  - JVM (android + desktop): the exact historical body —
 *    `java.util.LinkedHashMap(16, 0.75f, accessOrder = true)` with
 *    `removeEldestEntry`, byte-identical behavior.
 *  - wasmJs: `kotlin.collections.LinkedHashMap` has no access-order mode, so
 *    the actual is INSERTION-ORDER with eldest-insert eviction ( DOCUMENTED
 *    DEGRADE — a read no longer refreshes recency, so the cache keeps the
 *    first-inserted rather than the least-recently-read entries when at cap).
 *    Bounded display caches only; the eviction cap semantics are identical.
 */
expect fun <K, V> lruMapOf(maxSize: Int): MutableMap<K, V>

/**
 * Drops the oldest-inserted entries until the collection holds at most
 * [maxSize] elements. For insertion-ordered collections ([LinkedHashSet],
 * [LinkedHashMap] keys) "oldest" is insertion order — the dedup-id-set trim
 * idiom; behavior on unordered collections is unspecified.
 */
fun <T> MutableCollection<T>.trimToSize(maxSize: Int) {
    while (size > maxSize) {
        val iterator = iterator()
        iterator.next()
        iterator.remove()
    }
}
