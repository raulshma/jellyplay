package com.raulshma.jellyplay.core.data.concurrency

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
 * The cache-agnostic single-flight core: the mutex-guarded in-flight
 * [Deferred] map, the epoch-guarded write-back and the cancellation ladder
 * — the three pieces every single-flight memo needs, with everything
 * cache-shaped (a TtlCache, a CacheIdentity key grammar, a TTL) left to the
 * wrapper. [SingleFlightFetcher] is the TtlCache/CacheIdentity adapter; the
 * played-items memo in `WatchHistoryRepositoryImpl` is the plain-map one.
 * The split exists because that second consumer arrived as a hand-rolled
 * copy of the first (~45 lines: Mutex-guarded in-flight Deferred map,
 * generation counter, cancellation ladder — over a plain map, with no
 * identity), which is the proof the core was welded to its first cache.
 *
 * The contract — the load-bearing semantics the detail/catalogue suites and
 * `SingleFlightFetcherTest` pin; changing any bullet changes pinned
 * behavior:
 *
 *  - **Caller-scope fetch.** The fetch runs inside `coroutineScope`, so it
 *    inherits the calling coroutine's dispatcher — never a fixed background
 *    scope. This is what lets `runTest`'s virtual-time dispatcher drive the
 *    fetch in tests.
 *  - **Optional fast path.** A wrapper whose cache read is lock-free
 *    (internally synchronized, TtlCache-style) supplies [fastRead]: a hit
 *    returns before `coroutineScope` is even entered, avoiding
 *    scope-creation overhead on the common cached read. A wrapper over a
 *    plain map supplies none — its first read is the locked re-check below.
 *  - **Re-check under the lock.** [readCached] is evaluated inside the
 *    mutex, so a flight that completed while this caller waited for the
 *    lock is consumed exactly once.
 *  - **Epoch-guarded write.** The flight captures [epoch] at start (under
 *    the lock) and hands it to [fetch] as its `epochAtStart` argument; the
 *    result is stored only if [fetch] voted `mayStore` AND the epoch is
 *    unchanged by completion. Otherwise a slow fetch could re-insert a
 *    pre-mutation snapshot after a concurrent invalidation, pinning stale
 *    data for the memo's lifetime. The veto and [store] run as one
 *    mutex-held section, so an [invalidateAll] eviction cannot land between
 *    them: a racing write is either vetoed or wiped.
 *  - **Cancellation ladder.** The in-flight `Deferred` is a child of its
 *    originator's scope: if the originator is cancelled (e.g. navigated
 *    away mid-fetch), the `Deferred` is cancelled and every concurrent
 *    awaiter would fail too. An awaiter whose `await()` throws
 *    [CancellationException] re-throws only when its own [Job] is
 *    cancelled; otherwise the interruption came from the originator and the
 *    awaiter RE-ENTERS [getOrFetch] on its own (still-alive) scope: the
 *    [key] supplier is re-read (a session switch that landed mid-flight
 *    must not write the retry's result under the stale key — the
 *    await-time re-read `EpisodeCatalogueImpl.awaitFlight` had), a fresh
 *    epoch is captured at the new flight's start, and — because the
 *    re-entry goes back through the mutex and the in-flight map — several
 *    surviving awaiters collapse again: the first through the mutex becomes
 *    the new originator and the rest join its flight, instead of every
 *    orphaned awaiter firing its own full scan concurrently.
 *
 * The epoch is injected, not owned: [SingleFlightFetcher] shares its
 * owner's `AtomicLong` with non-flight guarded writers so a single
 * invalidation stall-guards every writer (external bumps don't hold the
 * core's mutex, but an epoch read is atomic — any bump visible to a
 * store's veto read still rejects that store); a self-contained memo owns
 * a private epoch and bumps it through [invalidateAll].
 *
 * jvmShared (not commonMain): the epoch is a `java.util.concurrent`
 * `AtomicLong`, and the consumers of this package ([SingleFlightFetcher],
 * the played-items memo) are jvmShared too.
 */
internal class SingleFlight<K : Any, V : Any>(
    private val epoch: AtomicLong,
) {

    private val mutex = Mutex()
    private val inFlight = mutableMapOf<K, Deferred<V>>()

    /**
     * Serves the key supplied by [key] through [readCached], or runs [fetch]
     * exactly once for all concurrent callers under one key. [key] is a
     * supplier, not a captured value: it is read once at entry for the
     * read-through and the flight join, and re-read on the ladder's
     * re-entry path so a key change that lands mid-flight (a session
     * switch) can't write the retry's result under the stale key. [fetch]
     * receives the epoch captured at flight start and returns its result
     * paired with a `mayStore` vote: the result reaches its callers either
     * way, but it is written back through [store] only if the vote allows
     * and no invalidation landed while it ran. The mutex-held lambdas
     * ([readCached], [store], [invalidateAll]'s `evict`) must be
     * non-suspending, fast, and never call back into this core.
     */
    suspend fun getOrFetch(
        key: suspend () -> K,
        fastRead: ((K) -> V?)? = null,
        readCached: (K) -> V?,
        fetch: suspend (epochAtStart: Long) -> Pair<V, Boolean>,
        store: (K, V) -> Unit,
    ): V {
        val startKey = key()
        fastRead?.invoke(startKey)?.let { return it }
        return coroutineScope {
            val deferred: Deferred<V> = mutex.withLock {
                // Re-check under the lock: a concurrent flight may have
                // completed and stored between the fast-path read above and
                // here — its result is consumed exactly once, from here.
                readCached(startKey)?.let { return@coroutineScope it }
                inFlight.getOrPut(startKey) {
                    val epochAtStart = epoch.get()
                    // async on the current coroutineScope so the fetch runs on
                    // the caller's dispatcher (not a fixed background scope).
                    async {
                        try {
                            val (value, mayStore) = fetch(epochAtStart)
                            if (mayStore) {
                                // Veto + store as one mutex-held section: an
                                // invalidateAll eviction cannot slip between
                                // them (see the class KDoc's write bullet).
                                mutex.withLock {
                                    if (epoch.get() == epochAtStart) {
                                        store(startKey, value)
                                    }
                                }
                            }
                            value
                        } finally {
                            // Clear the in-flight marker. Guarded so a
                            // concurrent awaiter that already grabbed the
                            // Deferred still sees the completed value, but a
                            // later caller re-fetches.
                            mutex.withLock { inFlight.remove(startKey) }
                        }
                    }
                }
            }
            try {
                deferred.await()
            } catch (ce: CancellationException) {
                val job = coroutineContext[Job]
                // Re-throw only if THIS caller was itself cancelled; otherwise
                // the originator was cancelled and a fresh flight on this
                // still-alive caller is safe — under a re-read key and a
                // fresh epoch capture.
                if (job?.isCancelled == true) throw ce
                getOrFetch(key, fastRead, readCached, fetch, store)
            }
        }
    }

    /**
     * One invalidation section: bumps the epoch and runs [evict] under the
     * same mutex as the write-guard, so a racing flight's store cannot land
     * between the two — it is either vetoed (its epoch capture no longer
     * matches) or already wiped by [evict]. In-flight flights are not
     * cancelled: each still returns its result to its callers; only its
     * write-back is lost.
     */
    suspend fun invalidateAll(evict: () -> Unit) {
        mutex.withLock {
            epoch.incrementAndGet()
            evict()
        }
    }
}
