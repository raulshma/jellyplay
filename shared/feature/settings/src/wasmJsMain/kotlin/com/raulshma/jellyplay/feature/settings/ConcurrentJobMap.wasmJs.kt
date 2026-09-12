package com.raulshma.jellyplay.feature.settings

/**
 * Web actual: a plain map plus a check-then-remove. The JS heap is
 * single-threaded — ConnectionProbe's callers (the UI thread) and the
 * invokeOnCompletion reaper run on the same event loop, so there is no
 * concurrent access to guard against and the check-then-remove cannot interleave.
 */
internal actual fun <K : Any, V : Any> newConcurrentJobMap(): MutableMap<K, V> = mutableMapOf()

internal actual fun <K : Any, V : Any> removeEntryIfCurrent(map: MutableMap<K, V>, key: K, expected: V) {
    if (map[key] === expected) map.remove(key)
}
