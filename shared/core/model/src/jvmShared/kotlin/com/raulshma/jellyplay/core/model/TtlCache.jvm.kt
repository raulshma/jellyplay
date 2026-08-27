package com.raulshma.jellyplay.core.model

import java.util.Collections

/**
 * JVM actuals for the [TtlCache] seam (android + desktop). The constructions
 * below are byte-identical to the pre-15B jvmShared body of [TtlCache]:
 *  - [ttlBackingMap]: `Collections.synchronizedMap` over the access-order
 *    [lruMapOf]. The wrapper's monitor is the wrapper itself, which is the
 *    exact lock the inline `synchronized(map)` blocks used to take.
 *  - [withMapMonitor]: `synchronized` on that wrapper — same monitor, so the
 *    compound sections are mutually exclusive exactly as before.
 *  - [lruMapOf]: the historical `LinkedHashMap(16, 0.75f, accessOrder = true)`
 *    + `removeEldestEntry` idiom, verbatim.
 */
internal actual fun <V> ttlBackingMap(maxSize: Int): MutableMap<String, TtlEntry<V>> =
    Collections.synchronizedMap(lruMapOf<String, TtlEntry<V>>(maxSize))

internal actual fun <R> withMapMonitor(monitor: Any, block: () -> R): R =
    synchronized(monitor) { block() }

actual fun <K, V> lruMapOf(maxSize: Int): MutableMap<K, V> =
    object : LinkedHashMap<K, V>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean =
            size > maxSize
    }
