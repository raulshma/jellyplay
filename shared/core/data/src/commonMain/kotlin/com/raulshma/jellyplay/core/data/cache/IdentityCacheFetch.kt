package com.raulshma.jellyplay.core.data.cache

import com.raulshma.jellyplay.core.model.CacheIdentity
import com.raulshma.jellyplay.core.model.TtlCache

/**
 * The identity-keyed cache-through read — the deep form of the choreography
 * that was hand-copied at nine call sites in `MediaRepositoryImpl` and again
 * (as the private `getCached`/`putCached` twin) in `SeerrRepositoryImpl`:
 *
 * ```
 * read the identity            ← the per-site source, injected as a supplier
 * build the cache key          ← per-site
 * cache.get(identity, key) hit → return it
 * (miss) fetch                 ← per-site
 * put on success               ← guarded per the variant below
 * ```
 *
 * Per the identity-keyed-cache policy (CONTEXT.md, "Session identity"), every
 * get/put/remove goes through the [TtlCache] identity overloads, so a wrong
 * identity is a guaranteed miss by construction — no parallel invalidation
 * channel. The identity comes from the caller-supplied supplier; in
 * production that is `HomeSession.cacheIdentity()` (the suspend source-flow
 * read, so a fetch that starts right after a switch keys under the NEW
 * identity, not the lagging mirror). The supplier is read exactly once at
 * entry — before the force-evict and the cache read — matching the
 * hand-copied sequence this module replaced. Single-flight reads with the
 * same doctrine (plus a cancellation ladder) remain `SingleFlightFetcher`'s
 * job; this module is the plain, non-flight sibling.
 *
 * Exactly three shapes exist across the migrated sites, and that is all this
 * module supports — no combination knobs, no fourth variant:
 *
 *  - **Plain** — [getOrFetch] with the defaults: hit-check → fetch →
 *    put-on-success. A failed fetch is returned as-is and never stored.
 *  - **Force** — [getOrFetch]'s `force` parameter: the freshness lever. It
 *    evicts the entry BEFORE the hit-check (the invalidate-then-read sequence
 *    the home screen's manual refresh used to run by hand), then fetches.
 *    Sites without a lever at all (`getStudios`) take the `force = false`
 *    default; sites whose network call doesn't propagate the lever
 *    (`getLibraryFolders`) keep their fetch lambda as-is — both drifts are
 *    the migrated sites' real behaviour and are preserved.
 *  - **Epoch-guarded write** — [getOrFetchGuarded]: the epoch is captured
 *    AFTER the miss, before the fetch, and the write lands only if the epoch
 *    is unchanged at completion — a stale fetch that raced an invalidation is
 *    still returned to its caller but never pinned into the cache for the
 *    full TTL. The epoch is injected as a read (`() -> Long`), not owned:
 *    guard sites share one epoch with the invalidation stream (e.g.
 *    `MediaRepositoryImpl.detailCacheEpoch`, the same epoch
 *    `SingleFlightFetcher` guards writes with), so a single invalidation
 *    stall-guards every writer.
 *
 * [getOrFetchTyped] is the plain shape over a heterogeneous `TtlCache<Any>`
 * (one cache, many value types under disjoint key prefixes): the hit-check
 * carries the `as? V` cast, so a hit of an unexpected type is a miss and
 * refetches — the exact semantics of the `SeerrRepositoryImpl` twin this
 * module replaced.
 *
 * All functions are behaviour-preserving ports: same identity source per
 * site, same eviction order, same write guards. `onFetched` on [getOrFetch]
 * is the one write-path hook that exists (the home-sections SWR persist): it
 * runs after the cache put on the FETCH path only — never on a cache hit, so
 * a hit cannot slide the persisted snapshot's timestamp forward.
 */

/** The one miss-path engine the public shapes funnel into (file-private). */
private suspend fun <V : Any> TtlCache<V>.fetchThrough(
    identity: CacheIdentity,
    key: String,
    currentEpoch: (() -> Long)?,
    onFetched: (suspend (V) -> Unit)?,
    fetch: suspend () -> Result<V>,
): Result<V> {
    val epochAtStart = currentEpoch?.invoke()
    val result = fetch()
    // Write guard: an epoch bump mid-fetch means an invalidation landed while
    // this fetch was in flight — the (now stale) snapshot is returned to the
    // caller but not stored, else it would be pinned for the full TTL.
    if (currentEpoch == null || currentEpoch() == epochAtStart) {
        result.getOrNull()?.let { value ->
            put(identity, key, value)
            onFetched?.invoke(value)
        }
    }
    return result
}

/**
 * Serves [key] for the identity supplied by [identity] through this cache:
 * cached hit → success; miss → run [fetch], store the success, return the
 * result. With `force = true` the entry is evicted before the read (the
 * freshness lever). [onFetched], when supplied, runs after the put on the
 * fetch path only (never on a hit) — the home-sections SWR persist's seam.
 */
suspend fun <V : Any> TtlCache<V>.getOrFetch(
    identity: suspend () -> CacheIdentity,
    key: String,
    force: Boolean = false,
    onFetched: (suspend (V) -> Unit)? = null,
    fetch: suspend () -> Result<V>,
): Result<V> {
    val startIdentity = identity()
    if (force) remove(startIdentity, key)
    get(startIdentity, key)?.let { return Result.success(it) }
    return fetchThrough(startIdentity, key, currentEpoch = null, onFetched = onFetched, fetch = fetch)
}

/**
 * [getOrFetch] with an epoch-guarded write: the value is stored only if
 * [currentEpoch] returns the same reading before the fetch and after it —
 * a fetch that raced an invalidation is returned but never cached. No force
 * lever: no migrated site combines the two shapes.
 */
suspend fun <V : Any> TtlCache<V>.getOrFetchGuarded(
    identity: suspend () -> CacheIdentity,
    key: String,
    currentEpoch: () -> Long,
    fetch: suspend () -> Result<V>,
): Result<V> {
    val startIdentity = identity()
    get(startIdentity, key)?.let { return Result.success(it) }
    return fetchThrough(startIdentity, key, currentEpoch = currentEpoch, onFetched = null, fetch = fetch)
}

/**
 * [getOrFetch] for a heterogeneous `TtlCache<Any>` — one cache holding many
 * value types under disjoint key prefixes. The hit-check casts to [V]
 * (reified, a real runtime check): a hit of an unexpected type is treated as
 * a miss and refetched, exactly the `as? T` semantics of the
 * `SeerrRepositoryImpl` private twin this replaced. No force lever and no
 * write hook — the migrated sites are all plain.
 */
suspend inline fun <reified V : Any> TtlCache<Any>.getOrFetchTyped(
    identity: suspend () -> CacheIdentity,
    key: String,
    fetch: suspend () -> Result<V>,
): Result<V> {
    val startIdentity = identity()
    (get(startIdentity, key) as? V)?.let { return Result.success(it) }
    val result = fetch()
    result.getOrNull()?.let { put(startIdentity, key, it) }
    return result
}
