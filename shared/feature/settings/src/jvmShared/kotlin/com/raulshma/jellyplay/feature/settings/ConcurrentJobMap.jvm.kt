package com.raulshma.jellyplay.feature.settings

import java.util.concurrent.ConcurrentHashMap

internal actual fun <K : Any, V : Any> newConcurrentJobMap(): MutableMap<K, V> = ConcurrentHashMap()

internal actual fun <K : Any, V : Any> removeEntryIfCurrent(map: MutableMap<K, V>, key: K, expected: V) {
    map.remove(key, expected)
}
