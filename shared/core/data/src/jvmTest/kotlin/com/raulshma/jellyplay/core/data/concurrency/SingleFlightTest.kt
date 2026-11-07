package com.raulshma.jellyplay.core.data.concurrency

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Concurrency suite for [SingleFlight] — the cache-agnostic core under
 * [SingleFlightFetcher]: concurrent-join collapse, the generation veto (a
 * stale write rejected; eviction atomic with the veto), the per-flight
 * `mayStore` decision, and the cancellation ladder's re-entry (cancelled
 * originator vs cancelled awaiter; surviving awaiters collapsing into one
 * replacement flight; the key supplier re-read). MockK-free — the core
 * takes its epoch and its lambdas directly, so gates are plain
 * [CompletableDeferred]s inside the fetch lambdas and the "cache" is a
 * plain map touched only through the core's own sections. One core
 * instance per test: the in-flight map is per-instance state.
 */
class SingleFlightTest {

    private val epoch = AtomicLong(0L)

    // ── single-flight join ─────────────────────────────────────────────

    @Test
    fun `two concurrent callers join one flight and both receive the single fetch's result`() = runTest {
        val fetchStarted = CompletableDeferred<Unit>()
        val releaseFetch = CompletableDeferred<Unit>()
        val cache = mutableMapOf<String, String>()
        var fetches = 0
        val flight = SingleFlight<String, String>(epoch)

        val first = async {
            flight.getOrFetch(
                key = { "k" },
                readCached = { cache[it] },
                fetch = {
                    fetches++
                    fetchStarted.complete(Unit)
                    releaseFetch.await()
                    "v" to true
                },
                store = { k, v -> cache[k] = v },
            )
        }
        fetchStarted.await()
        val second = async {
            flight.getOrFetch(
                key = { "k" },
                readCached = { cache[it] },
                fetch = { fetches++; "v2" to true },
                store = { k, v -> cache[k] = v },
            )
        }

        releaseFetch.complete(Unit)
        assertEquals("v", first.await())
        assertEquals("v", second.await())
        assertEquals(1, fetches)
    }

    @Test
    fun `a later caller consumes the stored flight result without refetching`() = runTest {
        val cache = mutableMapOf<String, String>()
        var fetches = 0
        val flight = SingleFlight<String, String>(epoch)

        flight.getOrFetch(
            key = { "k" },
            readCached = { cache[it] },
            fetch = { fetches++; "v1" to true },
            store = { k, v -> cache[k] = v },
        )
        val second = flight.getOrFetch(
            key = { "k" },
            readCached = { cache[it] },
            fetch = { fetches++; "v2" to true },
            store = { k, v -> cache[k] = v },
        )

        assertEquals("v1", second)
        assertEquals(1, fetches)
    }

    // ── generation veto ────────────────────────────────────────────────

    @Test
    fun `fetch racing an invalidation returns its result but its write is vetoed`() = runTest {
        val fetchStarted = CompletableDeferred<Unit>()
        val releaseFetch = CompletableDeferred<Unit>()
        val cache = mutableMapOf<String, String>()
        var stores = 0
        var fetches = 0
        val flight = SingleFlight<String, String>(epoch)

        val racing = async {
            flight.getOrFetch(
                key = { "k" },
                readCached = { cache[it] },
                fetch = {
                    fetches++
                    fetchStarted.complete(Unit)
                    releaseFetch.await()
                    "stale" to true
                },
                store = { k, v -> stores++; cache[k] = v },
            )
        }
        fetchStarted.await()
        flight.invalidateAll { cache.clear() }
        releaseFetch.complete(Unit)

        assertEquals("stale", racing.await(), "the racing flight's result still reaches its caller")
        assertEquals(0, stores, "but its write-back must be generation-vetoed")
        assertNull(cache["k"])

        // The vetoed write left nothing behind: the next caller refetches.
        val fresh = flight.getOrFetch(
            key = { "k" },
            readCached = { cache[it] },
            fetch = { fetches++; "fresh" to true },
            store = { k, v -> cache[k] = v },
        )
        assertEquals("fresh", fresh)
        assertEquals(2, fetches)
    }

    // ── per-flight store decision ──────────────────────────────────────

    @Test
    fun `mayStore false returns the value to its callers without write-back`() = runTest {
        val cache = mutableMapOf<String, String>()
        var stores = 0
        val flight = SingleFlight<String, String>(epoch)

        val first = flight.getOrFetch(
            key = { "k" },
            readCached = { cache[it] },
            fetch = { "unstoreable" to false },
            store = { k, v -> stores++; cache[k] = v },
        )
        assertEquals("unstoreable", first, "the unstoreable result still reaches its caller")
        assertEquals(0, stores)
        assertNull(cache["k"])

        val second = flight.getOrFetch(
            key = { "k" },
            readCached = { cache[it] },
            fetch = { "fresh" to true },
            store = { k, v -> stores++; cache[k] = v },
        )
        assertEquals("fresh", second, "a mayStore=false flight must not be served from the cache")
        assertEquals(1, stores)
    }

    // ── cancellation ladder ────────────────────────────────────────────

    @Test
    fun `cancelled originator's surviving awaiter re-enters and completes on its own scope`() = runTest {
        val fetchStarted = CompletableDeferred<Unit>()
        val releaseFetch = CompletableDeferred<Unit>()
        val cache = mutableMapOf<String, String>()
        var fetches = 0
        val flight = SingleFlight<String, String>(epoch)

        val awaiterResult = coroutineScope {
            val originator = async {
                flight.getOrFetch(
                    key = { "k" },
                    readCached = { cache[it] },
                    fetch = {
                        fetches++
                        fetchStarted.complete(Unit)
                        releaseFetch.await()
                        "v" to true
                    },
                    store = { k, v -> cache[k] = v },
                )
            }
            val awaiter = async {
                flight.getOrFetch(
                    key = { "k" },
                    readCached = { cache[it] },
                    fetch = { fetches++; "v" to true },
                    store = { k, v -> cache[k] = v },
                )
            }

            fetchStarted.await()
            originator.cancel()
            releaseFetch.complete(Unit)

            awaiter.await()
        }

        assertTrue(fetches >= 2, "the re-entry must issue a replacement fetch")
        assertEquals("v", awaiterResult)
        assertEquals("v", cache["k"], "the replacement flight's store lands")
    }

    @Test
    fun `surviving awaiters of a cancelled originator collapse into one replacement flight`() = runTest {
        val fetchStarted = CompletableDeferred<Unit>()
        val releaseFetch = CompletableDeferred<Unit>()
        val cache = mutableMapOf<String, String>()
        var fetches = 0
        val flight = SingleFlight<String, String>(epoch)

        val results = coroutineScope {
            val originator = async {
                flight.getOrFetch(
                    key = { "k" },
                    readCached = { cache[it] },
                    fetch = {
                        fetches++
                        fetchStarted.complete(Unit)
                        releaseFetch.await()
                        "v" to true
                    },
                    store = { k, v -> cache[k] = v },
                )
            }
            val firstAwaiter = async {
                flight.getOrFetch(
                    key = { "k" },
                    readCached = { cache[it] },
                    fetch = { fetches++; "v" to true },
                    store = { k, v -> cache[k] = v },
                )
            }
            val secondAwaiter = async {
                flight.getOrFetch(
                    key = { "k" },
                    readCached = { cache[it] },
                    fetch = { fetches++; "v" to true },
                    store = { k, v -> cache[k] = v },
                )
            }

            fetchStarted.await()
            originator.cancel()
            releaseFetch.complete(Unit)

            listOf(firstAwaiter.await(), secondAwaiter.await())
        }

        assertEquals(listOf("v", "v"), results)
        // The re-entry goes back through the mutex and the in-flight map:
        // the first survivor becomes the replacement originator and the
        // second joins its flight — one replacement fetch, not one each.
        assertEquals(2, fetches)
    }

    @Test
    fun `ladder re-entry re-reads the key supplier and stores under the fresh key`() = runTest {
        val fetchStarted = CompletableDeferred<Unit>()
        val releaseFetch = CompletableDeferred<Unit>()
        val cache = mutableMapOf<String, String>()
        var currentKey = "stale"
        var fetches = 0
        val flight = SingleFlight<String, String>(epoch)

        val awaiterResult = coroutineScope {
            val originator = async {
                flight.getOrFetch(
                    key = { currentKey },
                    readCached = { cache[it] },
                    fetch = {
                        fetches++
                        fetchStarted.complete(Unit)
                        releaseFetch.await()
                        "v" to true
                    },
                    store = { k, v -> cache[k] = v },
                )
            }
            val awaiter = async {
                flight.getOrFetch(
                    key = { currentKey },
                    readCached = { cache[it] },
                    fetch = { fetches++; "v" to true },
                    store = { k, v -> cache[k] = v },
                )
            }

            fetchStarted.await()
            // The "session switch" lands mid-flight: the re-entry must not
            // store under the key captured at the dead flight's entry.
            currentKey = "fresh"
            originator.cancel()
            releaseFetch.complete(Unit)

            awaiter.await()
        }

        assertEquals("v", awaiterResult)
        assertEquals("v", cache["fresh"], "the replacement flight stores under the re-read key")
        assertNull(cache["stale"], "never under the entry-time key")
    }

    @Test
    fun `cancelled caller rethrows and triggers no replacement fetch`() = runTest {
        val fetchStarted = CompletableDeferred<Unit>()
        val releaseFetch = CompletableDeferred<Unit>()
        val cache = mutableMapOf<String, String>()
        var fetches = 0
        val flight = SingleFlight<String, String>(epoch)

        val caller = launch {
            flight.getOrFetch(
                key = { "k" },
                readCached = { cache[it] },
                fetch = {
                    fetches++
                    fetchStarted.complete(Unit)
                    releaseFetch.await()
                    "v" to true
                },
                store = { k, v -> cache[k] = v },
            )
        }
        fetchStarted.await()
        caller.cancelAndJoin()

        assertEquals(1, fetches, "a cancelled caller must not run the ladder's replacement flight")
        assertNull(cache["k"])

        // The in-flight entry was cleaned up; a later caller re-fetches.
        val later = flight.getOrFetch(
            key = { "k" },
            readCached = { cache[it] },
            fetch = { fetches++; "v2" to true },
            store = { k, v -> cache[k] = v },
        )
        assertEquals("v2", later)
        assertEquals(2, fetches)
    }

    @Test
    fun `a joiner whose flight died before its body started retries into a fresh flight`() = runTest {
        // Device-verified 2026-11-07 (audio prewarm hang): the in-flight
        // marker exists BEFORE the flight's async body ever runs, and a
        // cancellation landing while the async is still queued skips the body
        // and its cleanup finally entirely (its sibling loss — the cleanup
        // finally's withLock throwing CancellationException on a contended
        // lock — strands the marker the same way). The marker then holds a
        // dead Deferred that every subsequent joiner awaits, ladders on, and
        // re-joins — forever, silently. White-box: the stranding race itself
        // is a multithreaded scheduling window no single-threaded test can
        // close, so the marker is seeded directly — this pins the repair
        // contract, that the ladder SWEEPS a dead marker instead of spinning
        // on it. The delay in the key supplier bounds the buggy spin (each
        // dead-marker retry advances virtual time) so the red run fails on
        // this timeout instead of wedging the scheduler.
        val flight = SingleFlight<String, String>(AtomicLong(0L))
        @Suppress("UNCHECKED_CAST")
        val inFlight = SingleFlight::class.java.getDeclaredField("inFlight").apply {
            isAccessible = true
        }.get(flight) as MutableMap<String, Deferred<String>>
        val deadFlight = CompletableDeferred<String>()
        deadFlight.cancel(CancellationException("originator died before its body started"))
        inFlight["k"] = deadFlight

        val cache = mutableMapOf<String, String>()
        var fetches = 0

        val joiner = async {
            flight.getOrFetch(
                key = { delay(1); "k" },
                readCached = { cache[it] },
                fetch = { fetches++; "fresh" to true },
                store = { k, v -> cache[k] = v },
            )
        }
        val joined = runCatching { withTimeout(5_000) { joiner.await() } }
        joiner.cancelAndJoin()

        assertEquals(
            "fresh",
            joined.getOrThrow(),
            "the surviving joiner must sweep the dead marker and run its own replacement flight",
        )
        assertEquals(1, fetches, "the dead flight never fetched; only the replacement did")
        assertEquals("fresh", cache["k"], "the replacement flight's store lands")
    }

    @Test
    fun `a churn burst of dead flights retries flat — no StackOverflowError`() = runTest {
        // Device-verified 2026-11-07 (a section Play-all burst; the same
        // shape as the 2026-10-02 prewarm regression): a surviving caller
        // that rides out tens of thousands of dead originators must not
        // accumulate a coroutineScope per retry. The nested re-entry this
        // pins against overflowed a ~1 MB stack after roughly a thousand
        // retries; the flat loop iterates to 50_000 without growing the
        // stack. The single caller's fetch dies with a cancellation for the
        // first CHURN_ROUNDS rounds (each death reads as a cancelled
        // originator, not a failed fetch) and then completes.
        val churnRounds = 50_000
        var fetches = 0
        val flight = SingleFlight<String, String>(AtomicLong(0L))
        val cache = mutableMapOf<String, String>()

        val result = flight.getOrFetch(
            key = { "k" },
            readCached = { cache[it] },
            fetch = {
                val round = fetches++
                if (round < churnRounds) throw CancellationException("churn-$round")
                "done" to true
            },
            store = { k, v -> cache[k] = v },
        )

        assertEquals("done", result)
        assertEquals(churnRounds + 1, fetches, "every dead flight is retried, then the completing one runs")
        assertEquals("done", cache["k"], "the completing flight's store lands")
    }
}
