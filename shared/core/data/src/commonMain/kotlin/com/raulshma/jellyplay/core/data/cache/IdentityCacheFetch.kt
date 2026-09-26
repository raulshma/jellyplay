package com.raulshma.jellyplay.core.data.cache

import com.raulshma.jellyplay.core.model.CacheIdentity
import com.raulshma.jellyplay.core.model.TtlCache
import com.raulshma.jellyplay.core.model.cacheThrough

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
 * entry — before the force-evict and the cache read. For the nine
 * `MediaRepositoryImpl` sites that is the hand-copied sequence verbatim; the
 * `SeerrRepositoryImpl` twin re-read its identity at put time, so the write
 * now keys under the entry identity — neutralized in practice because
 * identity transitions clear the caches wholesale (see
 * `SessionIdentityProvider`), and it is the intended semantics: a write can
 * never land under a different identity than its own read. Single-flight
 * reads with the same doctrine (plus a cancellation ladder) remain
 * `SingleFlightFetcher`'s job; this module is the plain, non-flight sibling.
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
 *    the migrated sites' real behaviour and are preserved. The shape also
 *    carries the epoch-guard dimension as an optional `currentEpoch`
 *    parameter (delegated to the shared engine's write guard) for the one
 *    site that combines force with an invalidation it must not race: the
 *    home-sections read, whose dice-roll invalidation must stall-guard an
 *    in-flight fetch from re-pinning the pre-roll payload.
 *  - **Epoch-guarded write** — [getOrFetchGuarded]: the write lands only if
 *    the epoch is unchanged across the fetch — a stale fetch that raced an
 *    invalidation is still returned to its caller but never pinned into the
 *    cache for the full TTL. The capture-then-compare choreography itself is
 *    [com.raulshma.jellyplay.core.model.cacheThrough]'s (one engine, shared
 *    with the network layer's home sub-call caches); the epoch is injected as
 *    a read (`() -> Long`), not owned: guard sites share one epoch with the
 *    invalidation stream (e.g. `MediaRepositoryImpl.detailCacheEpoch`, the
 *    same epoch `SingleFlightFetcher` guards writes with), so a single
 *    invalidation stall-guards every writer.
 *
 * [getOrFetchTyped] is the plain shape over a heterogeneous `TtlCache<Any>`
 * (one cache, many value types under disjoint key prefixes): the hit-check
 * carries the `as? V` cast, so a hit of an unexpected type is a miss and
 * refetches — the exact semantics of the `SeerrRepositoryImpl` twin this
 * module replaced.
 *
 * All functions are behaviour-preserving ports per site (same eviction
 * order, same write guards), with the one identity-timing nuance recorded
 * above. `onFetched` on [getOrFetch]
 * is the one write-path hook that exists (the home-sections SWR persist): it
 * runs after the cache put on the FETCH path only — never on a cache hit, so
 * a hit cannot slide the persisted snapshot's timestamp forward.
 */

/**
 * Serves [key] for the identity supplied by [identity] through this cache:
 * cached hit → success; miss → run [fetch], store the success, return the
 * result. With `force = true` the entry is evicted before the read (the
 * freshness lever). [onFetched], when supplied, runs after the put on the
 * fetch path only (never on a hit) — the home-sections SWR persist's seam.
 * [currentEpoch], when supplied, guards the write exactly as in
 * [getOrFetchGuarded]: a fetch that raced an epoch bump returns its result
 * but stores nothing.
 *
 * The miss path IS [com.raulshma.jellyplay.core.model.cacheThrough] — the
 * one guarded cache-through engine shared with the network layer's home
 * sub-call caches. This module adds only the two data-layer preambles the
 * engine deliberately does not own: the once-at-entry suspend identity read,
 * and force's evict-before-delegate (see the engine's KDoc for why the two
 * force nuances are kept apart).
 */
suspend fun <V : Any> TtlCache<V>.getOrFetch(
    identity: suspend () -> CacheIdentity,
    key: String,
    force: Boolean = false,
    onFetched: (suspend (V) -> Unit)? = null,
    currentEpoch: (() -> Long)? = null,
    fetch: suspend () -> Result<V>,
): Result<V> {
    val startIdentity = identity()
    // Evict BEFORE delegating: a failed forced fetch must leave nothing
    // behind (the invalidate-then-read sequence this shape documents) —
    // the engine's bare force would keep the evicted entry on failure.
    if (force) remove(startIdentity, key)
    return cacheThrough(startIdentity, key, force = force, currentEpoch = currentEpoch, onFetched = onFetched, fetch = fetch)
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
    return cacheThrough(startIdentity, key, currentEpoch = currentEpoch, onFetched = null, fetch = fetch)
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
    noinline fetch: suspend () -> Result<V>,
): Result<V> {
    val startIdentity = identity()
    // The cache is keyed on Any, so the funnel cast is erased at runtime —
    // safe because hitOf re-checks the stored type on every read (a foreign
    // entry reads as a miss; the fetch overwrites it).
    @Suppress("UNCHECKED_CAST")
    return (this as TtlCache<V>).cacheThrough(
        identity = startIdentity,
        key = key,
        currentEpoch = null,
        onFetched = null,
        hitOf = { it as? V },
        fetch = fetch,
    )
}
