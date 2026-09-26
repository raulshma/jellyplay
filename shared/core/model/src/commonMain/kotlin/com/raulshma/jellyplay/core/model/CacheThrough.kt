package com.raulshma.jellyplay.core.model

/**
 * The ONE guarded cache-through read: hit-check → (optional force) → fetch →
 * epoch-guarded memoise-write.
 *
 * This exact shape used to exist twice, hand-copied across modules with drifted
 * homes — `IdentityCacheFetch.fetchThrough` (core:data, behind the
 * `getOrFetch` family) and `HomeSectionsFetcher.cachedHomeSubCall`
 * (core:network, behind the home sub-call caches) — because each sat below the
 * layer that could own the twin. Both depend on `core:model`, so the guard
 * lives here beside [TtlCache], exactly the precedent that promoted
 * [TtlCache] itself out of those two modules.
 *
 * The piece worth concentrating is the WRITE GUARD, and it is subtle enough to
 * state once: the epoch is captured AFTER the miss, BEFORE the fetch, and the
 * write lands only if the epoch is unchanged at completion — a stale fetch
 * that raced an invalidation (the discover-row dice roll's invalidate/seed
 * pair; the detail-cache invalidation ladder) is still RETURNED to its caller
 * but never pinned into the cache for the full TTL. A null [currentEpoch]
 * means an unguarded write, the plain memoise. Callers inject the epoch as a
 * read, never own it: guard sites share one epoch with their invalidation
 * stream so a single invalidation stall-guards every in-flight writer.
 *
 * Semantics deliberately NOT unified (each consumer's force nuance, preserved
 * by construction):
 *  - This helper's `force` SKIPS the cache read but does NOT evict — a failed
 *    forced fetch leaves the previous entry to serve the next plain read (the
 *    network layer's sub-call policy).
 *  - core:data's `getOrFetch` EVICTS before delegating with force — a failed
 *    forced fetch leaves nothing behind (its documented
 *    invalidate-then-read sequence). That one-line preamble stays at this
 *    call site rather than becoming a knob here.
 *
 * Not single-flight, same caveat as [TtlCache.getOrPut]: concurrent callers
 * may both observe a miss and run [fetch]; the last successful write wins.
 * Only successful results are memoised — a throw or a failure propagates
 * WITHOUT a put. [onFetched] is the one write-path hook (the home-sections
 * SWR persist's seam): it runs after the put on the FETCH path only, never on
 * a hit, so a hit cannot slide a persisted snapshot's timestamp forward.
 *
 * [hitOf] is the one read-path hook: the heterogeneous-cache adapter's hit
 * predicate (`TtlCache<Any>.getOrFetchTyped` passes `it as? V`, so a stored
 * entry of a foreign type reads as a miss and refetches instead of being
 * handed back as [V]). Null (the default) accepts any stored entry as a hit.
 */
suspend fun <V : Any> TtlCache<V>.cacheThrough(
    identity: CacheIdentity,
    key: String,
    force: Boolean = false,
    currentEpoch: (() -> Any?)? = null,
    onFetched: (suspend (V) -> Unit)? = null,
    hitOf: ((V?) -> V?)? = null,
    fetch: suspend () -> Result<V>,
): Result<V> {
    if (!force) {
        val read = get(identity, key)
        val hit = if (hitOf == null) read else hitOf(read)
        hit?.let { return Result.success(it) }
    }
    val epochAtStart = currentEpoch?.invoke()
    val result = fetch()
    if (currentEpoch == null || currentEpoch() == epochAtStart) {
        result.getOrNull()?.let { value ->
            put(identity, key, value)
            onFetched?.invoke(value)
        }
    }
    return result
}
