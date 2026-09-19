package com.raulshma.jellyplay.core.data.concurrency

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
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
}
