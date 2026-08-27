package com.raulshma.jellyplay.core.model

/**
 * Tiny LRU + TTL cache used across layers (`core:network` HTTP clients,
 * `core:data` repositories) to memoise near-static server responses.
 *
 * Previously duplicated in `core:data` and `core:network` because each module
 * sits below the layer that owned the cache; both depend on `core:model`, so
 * the canonical implementation lives here and the duplicates were removed.
 *
 * Thread-safety regime (wave 15B promotion to commonMain): on the JVM targets
 * (android + desktop) the backing map is a `Collections.synchronizedMap`
 * wrapper — the exact historical shape — and [withMapMonitor] takes that
 * wrapper's monitor, so every compound section below is mutually exclusive
 * exactly as before. On wasmJs (single-threaded: no SharedArrayBuffer worker
 * threads in Kotlin/wasm today) the actuals are the plain LRU map and a
 * pass-through "lock", so the identical body runs lock-free. One JVM-only
 * footnote: `withMapMonitor` cannot be `inline` (expect functions may not be),
 * so each section now allocates its lambda where the inline `synchronized`
 * previously did not — a negligible cost next to the cache-miss network reads
 * this class guards, and observably identical semantics.
 *
 * The `get`-check-`put` sequence is *not* atomic across threads — concurrent
 * callers may both observe a miss and re-fetch. For idempotent network reads
 * this is acceptable (the last writer wins); do not rely on it for
 * compute-expensive single-flight.
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
 * @param clock   monotonic time source; defaults to [monotonicNowMillis]
 *                (`SystemClock.elapsedRealtime` on Android, `System.nanoTime`
 *                on desktop) so production behaviour is unchanged. Injectable
 *                for unit tests.
 */
class TtlCache<V>(
    private val maxSize: Int = DEFAULT_MAX_SIZE,
    private val ttlMs: Long = DEFAULT_TTL_MS,
    private val clock: () -> Long = ::monotonicNowMillis,
) {

    private val map: MutableMap<String, TtlEntry<V>> = ttlBackingMap(maxSize)

    fun get(key: String): V? = withMapMonitor(map) {
        val entry = map[key]
        when {
            // Synchronize the read-then-expire-check-then-remove sequence: a
            // plain `map[key]` followed by a conditional `map.remove` is a
            // compound operation, and `Collections.synchronizedMap` only
            // guards each call individually. Holding the monitor across the
            // pair makes expiry eviction atomic w.r.t. concurrent put/remove.
            entry == null -> null
            clock() - entry.fetchedAt >= ttlMs -> {
                map.remove(key)
                null
            }
            else -> entry.value
        }
    }

    /** Identity-aware [get]: a wrong identity yields a distinct key and so misses. */
    fun get(identity: CacheIdentity, key: String): V? = get(compositeKey(identity, key))

    fun put(key: String, value: V) {
        map[key] = TtlEntry(value, clock())
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
     * On the JVM this iterates a collection view of a
     * `Collections.synchronizedMap`, so the iteration must hold the map's
     * monitor — otherwise a concurrent `put` (`getSimilarItems` populating the
     * cache from the detail VM's scope) can throw
     * `ConcurrentModificationException` or corrupt the access-order links.
     */
    fun removeByKeyPrefix(prefix: String) {
        withMapMonitor(map) {
            // Portable removeIf (MutableCollection.removeIf is a JVM API):
            // same monitor-held iteration, same remove-as-you-scan order.
            val entryIterator = map.entries.iterator()
            while (entryIterator.hasNext()) {
                if (entryIterator.next().key.startsWith(prefix)) entryIterator.remove()
            }
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
        withMapMonitor(map) {
            val entryIterator = map.entries.iterator()
            while (entryIterator.hasNext()) {
                if (now - entryIterator.next().value.fetchedAt >= ttlMs) entryIterator.remove()
            }
        }
    }

    companion object {
        const val DEFAULT_MAX_SIZE = 30
        const val DEFAULT_TTL_MS = 2 * 60 * 1000L
    }
}

/** One memoised [TtlCache] entry: the value plus the monotonic write stamp. */
internal data class TtlEntry<V>(val value: V, val fetchedAt: Long)

/**
 * The [TtlCache] backing map. JVM actual: `Collections.synchronizedMap` over
 * [lruMapOf] — the exact historical construction, whose monitor is the
 * returned wrapper itself (which is what makes [withMapMonitor] sections
 * mutually exclusive against the wrapper's own per-call guards). wasmJs
 * actual: the plain [lruMapOf]; single-threaded target, nothing to exclude.
 */
internal expect fun <V> ttlBackingMap(maxSize: Int): MutableMap<String, TtlEntry<V>>

/**
 * Runs [block] while holding [monitor]'s lock (JVM actual: `synchronized`;
 * wasmJs actual: pass-through — see the [TtlCache] thread-safety KDoc).
 */
internal expect fun <R> withMapMonitor(monitor: Any, block: () -> R): R
