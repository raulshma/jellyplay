package com.raulshma.jellyplay.core.data.concurrency

import com.raulshma.jellyplay.core.model.CacheIdentity
import com.raulshma.jellyplay.core.model.TtlCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext

/**
 * Single-flight, epoch-guarded reads over a [TtlCache]: concurrent callers
 * for the same key share one in-flight fetch, and a fetch that races an
 * invalidation is returned to its callers but never written back into the
 * cache.
 *
 * Extracted from the pattern `MediaRepositoryImpl.getMediaDetail`
 * and `EpisodeCatalogueImpl` each hand-rolled (a `Mutex`-guarded in-flight
 * `Deferred` map + an `AtomicLong` epoch + a cancellation ladder). The
 * contract below is that pattern's load-bearing semantics — changing any
 * bullet changes behavior the detail/catalogue test suites pin:
 *
 *  - **Caller-scope fetch.** The fetch runs inside `coroutineScope`, so it
 *    inherits the calling coroutine's dispatcher — never a fixed background
 *    scope. This is what lets `runTest`'s virtual-time dispatcher drive the
 *    fetch in tests.
 *  - **Fast path.** A cached hit returns without entering `coroutineScope`,
 *    avoiding scope-creation overhead on the common cached read.
 *  - **Re-check under the lock.** The cache is read again inside the mutex
 *    so a fetch that completed while this caller waited for the lock is
 *    consumed exactly once.
 *  - **Epoch-guarded write.** The fetch captures [epoch] at flight start
 *    (under the lock) and receives it as its `epochAtStart` argument — the
 *    fetcher writes the result to the cache only if the epoch is unchanged
 *    by completion. Otherwise a slow fetch could re-insert a pre-mutation
 *    snapshot after a concurrent invalidation, pinning stale data for the
 *    full TTL.
 *  - **Cancellation ladder.** The in-flight `Deferred` is a child of its
 *    originator's scope: if the originator is cancelled (e.g. navigated
 *    away mid-fetch), the `Deferred` is cancelled and every concurrent
 *    awaiter would fail too. An awaiter whose `await()` throws
 *    [CancellationException] re-throws only when its own [Job] is cancelled;
 *    otherwise the interruption came from the originator and the awaiter
 *    re-fetches on its own (still-alive) scope with a fresh epoch capture
 *    and a fresh identity capture (the await-time re-read
 *    `EpisodeCatalogueImpl.awaitFlight` had; a session switch that landed
 *    mid-flight must not write the retry's result under the stale identity).
 *  - **Flight-key scope.** In-flight entries are keyed by
 *    `(identity, flightKey)` — a caller under a different [CacheIdentity]
 *    never joins another identity's flight, matching the cache's
 *    wrong-identity-is-a-guaranteed-miss doctrine. [flightKey] defaults to
 *    [key]; pass a distinct one when the same key is fetched through
 *    different transports that must not share results (e.g. an online vs
 *    offline load of one series).
 *
 * The epoch is injected, not owned: callers whose non-flight code paths
 * guard their own cache writes against the same invalidation stream
 * (e.g. `MediaRepositoryImpl.getAlbumTracks`) share one `AtomicLong` with
 * the fetcher so a single invalidation stall-guards every writer.
 */
class SingleFlightFetcher<T : Any>(
    private val cache: TtlCache<T>,
    private val epoch: AtomicLong,
) {

    private val inFlightMutex = Mutex()
    private val inFlight = mutableMapOf<Pair<CacheIdentity, String>, Deferred<Result<T>>>()

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
    ): Result<T> {
        val startIdentity = identity()
        cache.get(startIdentity, key)?.let { return Result.success(it) }
        return coroutineScope {
            val deferred: Deferred<Result<T>> = inFlightMutex.withLock {
                // Re-check under the lock: a concurrent completion may have
                // populated the cache between the unlocked read above and here.
                cache.get(startIdentity, key)?.let { return@coroutineScope Result.success(it) }
                val flightId = startIdentity to flightKey
                inFlight.getOrPut(flightId) {
                    val epochAtStart = epoch.get()
                    // async on the current coroutineScope so the fetch runs on
                    // the caller's dispatcher (not a fixed background scope).
                    async {
                        try {
                            fetch(epochAtStart).also { result ->
                                if (epoch.get() == epochAtStart) {
                                    result.getOrNull()?.let { cache.put(startIdentity, key, it) }
                                }
                            }
                        } finally {
                            // Clear the in-flight marker. Guarded so a
                            // concurrent awaiter that already grabbed the
                            // Deferred still sees the completed value, but a
                            // later caller re-fetches.
                            inFlightMutex.withLock { inFlight.remove(flightId) }
                        }
                    }
                }
            }
            try {
                deferred.await()
            } catch (ce: CancellationException) {
                val job = coroutineContext[Job]
                // Re-throw only if THIS caller was itself cancelled; otherwise
                // the originator was cancelled and a fresh fetch on this
                // still-alive caller is safe — under a re-read identity and a
                // fresh epoch capture.
                if (job?.isCancelled == true) throw ce
                val retryIdentity = identity()
                val epochAtRetry = epoch.get()
                fetch(epochAtRetry).also { result ->
                    if (epoch.get() == epochAtRetry) {
                        result.getOrNull()?.let { cache.put(retryIdentity, key, it) }
                    }
                }
            }
        }
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
