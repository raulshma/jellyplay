package com.raulshma.jellyplay.core.data.concurrency

import com.raulshma.jellyplay.core.model.CacheIdentity
import com.raulshma.jellyplay.core.model.TtlCache
import java.util.concurrent.atomic.AtomicLong

/**
 * Single-flight, epoch-guarded reads over a [TtlCache]: concurrent callers
 * for the same key share one in-flight fetch, and a fetch that races an
 * invalidation is returned to its callers but never written back into the
 * cache.
 *
 * Thin adapter over [SingleFlight] — the cache-agnostic core (extracted
 * from the pattern `MediaRepositoryImpl.getMediaDetail` and
 * `EpisodeCatalogueImpl` each hand-rolled: a Mutex-guarded in-flight
 * Deferred map + an `AtomicLong` epoch + a cancellation ladder) that now
 * owns the in-flight map, the generation veto and the cancellation ladder;
 * the contract's load-bearing semantics live in [SingleFlight]'s KDoc. What
 * this adapter adds is the TtlCache/CacheIdentity machinery:
 *
 *  - **Flight-key scope.** In-flight entries are keyed by
 *    `(identity, flightKey)` — a caller under a different [CacheIdentity]
 *    never joins another identity's flight, matching the cache's
 *    wrong-identity-is-a-guaranteed-miss doctrine. [flightKey] defaults to
 *    [key]; pass a distinct one when the same key is fetched through
 *    different transports that must not share results (e.g. an online vs
 *    offline load of one series).
 *  - **Failure-aware store.** Only a successful [Result]'s value is written
 *    back to the cache — a failure reaches its callers but never the cache.
 *
 * The epoch is injected, not owned: callers whose non-flight code paths
 * guard their own cache writes against the same invalidation stream
 * (e.g. `MediaRepositoryImpl.getAlbumTracks`) share one `AtomicLong` with
 * the fetcher so a single invalidation stall-guards every writer. Such
 * external bumps (and [invalidate]/[invalidateAll] below) deliberately do
 * not hold the core's mutex — an epoch read is atomic, so any bump visible
 * to a store's veto read still rejects that store, and the caches are
 * internally synchronized so the eviction half needs no core lock.
 */
class SingleFlightFetcher<T : Any>(
    private val cache: TtlCache<T>,
    private val epoch: AtomicLong,
) {

    private val flight = SingleFlight<Pair<CacheIdentity, String>, Result<T>>(epoch)

    /**
     * Serves [key] for the identity supplied by [identity] from the cache, or
     * runs [fetch] exactly once for all concurrent callers. [identity] is a
     * supplier, not a captured value: it is read once at entry for the cache
     * read and flight join, and re-read on the cancellation-retry path so a
     * session switch that lands mid-flight can't write the retry's result
     * under the stale identity. [fetch] receives the epoch captured at
     * flight start; its result is written to the cache only if no
     * invalidation landed while it ran (and it succeeded).
     */
    suspend fun getOrFetch(
        identity: suspend () -> CacheIdentity,
        key: String,
        flightKey: String = key,
        fetch: suspend (epochAtStart: Long) -> Result<T>,
    ): Result<T> =
        getOrFetchStorable(identity, key, flightKey) { epochAtStart -> fetch(epochAtStart) to true }

    /**
     * [getOrFetch] with a per-flight store decision: [fetch] additionally
     * returns whether its result may be written back to the cache. This is
     * the exact, same-flight-only form of "serve the fallback to this caller
     * without letting it masquerade as a fresh read for the TTL" (a segments
     * fetch whose segments API failed must not cache its legacy fallback) —
     * unlike an epoch bump, `store = false` vetoes this flight's write and no
     * concurrent flight's. The epoch guard still applies on top of the flag.
     */
    suspend fun getOrFetchStorable(
        identity: suspend () -> CacheIdentity,
        key: String,
        flightKey: String = key,
        fetch: suspend (epochAtStart: Long) -> Pair<Result<T>, Boolean>,
    ): Result<T> {
        // One read seam for the fast path (lock-free TtlCache read) and the
        // locked re-check: the cache entry lives under (identity, key) — the
        // flight key never shapes the cache, only the flight join.
        fun read(flightId: Pair<CacheIdentity, String>): Result<T>? =
            cache.get(flightId.first, key)?.let { Result.success(it) }

        return flight.getOrFetch(
            key = { identity() to flightKey },
            fastRead = ::read,
            readCached = ::read,
            fetch = fetch,
            store = { flightId, result ->
                result.getOrNull()?.let { cache.put(flightId.first, key, it) }
            },
        )
    }

    /** Drops [key]'s cached entry for [identity] and bumps the epoch. */
    fun invalidate(identity: CacheIdentity, key: String) {
        epoch.incrementAndGet()
        cache.remove(identity, key)
    }

    /** Drops every cached entry (all identities) and bumps the epoch. */
    fun invalidateAll() {
        epoch.incrementAndGet()
        cache.clear()
    }
}
