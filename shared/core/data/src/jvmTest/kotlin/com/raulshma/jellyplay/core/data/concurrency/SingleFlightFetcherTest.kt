package com.raulshma.jellyplay.core.data.concurrency

import com.raulshma.jellyplay.core.model.CacheIdentity
import com.raulshma.jellyplay.core.model.TtlCache
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
 * Concurrency suite for [SingleFlightFetcher], pinning the semantics ported
 * from the hand-rolled detail/catalogue patterns: single-flight collapse,
 * the cancellation ladder (cancelled originator vs cancelled awaiter), the
 * epoch-stale write guard, identity flight isolation, and TTL expiry.
 * MockK-free — the fetcher takes its cache, epoch and fetch lambda directly,
 * so gates are plain [CompletableDeferred]s inside the lambdas. One fetcher
 * instance per test: the in-flight map is per-instance state.
 */
class SingleFlightFetcherTest {

    private val identity = CacheIdentity.of("server-1", "user-1")
    private val otherIdentity = CacheIdentity.of("server-1", "user-2")
    private val epoch = AtomicLong(0L)
    private val cache = TtlCache<String>(maxSize = 10, ttlMs = 60_000L)

    // ── single-flight collapse ──────────────────────────────────────────

    @Test
    fun `concurrent misses collapse to a single fetch and all callers get the result`() = runTest {
        val fetchStarted = CompletableDeferred<Unit>()
        val releaseFetch = CompletableDeferred<Unit>()
        var fetches = 0
        val fetcher = SingleFlightFetcher(cache, epoch)

        val callers = (1..5).map {
            async {
                fetcher.getOrFetch(identity, "k") {
                    fetches++
                    fetchStarted.complete(Unit)
                    releaseFetch.await()
                    Result.success("v")
                }
            }
        }
        fetchStarted.await()
        releaseFetch.complete(Unit)

        callers.forEach { assertEquals("v", it.await().getOrNull()) }
        assertEquals(1, fetches)
    }

    @Test
    fun `cached hit does not fetch`() = runTest {
        var fetches = 0
        val fetcher = SingleFlightFetcher(cache, epoch)

        fetcher.getOrFetch(identity, "k") { fetches++; Result.success("v1") }
        val second = fetcher.getOrFetch(identity, "k") { fetches++; Result.success("v2") }

        assertEquals("v1", second.getOrNull())
        assertEquals(1, fetches)
    }

    @Test
    fun `distinct flight keys for the same key do not share a flight`() = runTest {
        val onlineStarted = CompletableDeferred<Unit>()
        val releaseOnline = CompletableDeferred<Unit>()
        var fetches = 0
        val fetcher = SingleFlightFetcher(cache, epoch)

        val online = async {
            fetcher.getOrFetch(identity, key = "k", flightKey = "online::k") {
                fetches++
                onlineStarted.complete(Unit)
                releaseOnline.await()
                Result.success("online")
            }
        }
        onlineStarted.await()
        val offline = async {
            fetcher.getOrFetch(identity, key = "k", flightKey = "offline::k") {
                fetches++
                Result.success("offline")
            }
        }

        assertEquals("offline", offline.await().getOrNull())
        releaseOnline.complete(Unit)
        assertEquals("online", online.await().getOrNull())
        assertEquals(2, fetches)
    }

    // ── cancellation ladder ─────────────────────────────────────────────

    @Test
    fun `cancelled originator lets a concurrent awaiter re-fetch on its own scope`() = runTest {
        val fetchStarted = CompletableDeferred<Unit>()
        val releaseFetch = CompletableDeferred<Unit>()
        var fetches = 0
        val fetcher = SingleFlightFetcher(cache, epoch)

        val awaiterResult = coroutineScope {
            val originator = async {
                fetcher.getOrFetch(identity, "k") {
                    fetches++
                    fetchStarted.complete(Unit)
                    releaseFetch.await()
                    Result.success("v")
                }
            }
            val awaiter = async {
                fetcher.getOrFetch(identity, "k") {
                    fetches++
                    fetchStarted.complete(Unit)
                    releaseFetch.await()
                    Result.success("v")
                }
            }

            fetchStarted.await()
            originator.cancel()
            releaseFetch.complete(Unit)

            awaiter.await()
        }

        assertTrue(awaiterResult.isSuccess, "awaiter must survive originator cancellation")
        assertEquals("v", awaiterResult.getOrNull())
        assertTrue(fetches >= 2, "the awaiter's re-fetch must issue a fresh network call")
        // The re-fetch on the surviving caller's scope caches its result.
        assertEquals("v", cache.get(identity, "k"))
    }

    @Test
    fun `cancelled awaiter rethrows and triggers no retry fetch`() = runTest {
        val fetchStarted = CompletableDeferred<Unit>()
        val releaseFetch = CompletableDeferred<Unit>()
        var fetches = 0
        val fetcher = SingleFlightFetcher(cache, epoch)

        val caller = launch {
            fetcher.getOrFetch(identity, "k") {
                fetches++
                fetchStarted.complete(Unit)
                releaseFetch.await()
                Result.success("v")
            }
        }
        fetchStarted.await()
        caller.cancelAndJoin()

        assertEquals(1, fetches, "a cancelled caller must not run the retry path's fetch")
        // The in-flight entry was cleaned up; a later caller re-fetches.
        val later = fetcher.getOrFetch(identity, "k") { fetches++; Result.success("v2") }
        assertEquals("v2", later.getOrNull())
        assertEquals(2, fetches)
    }

    // ── epoch-stale race ───────────────────────────────────────────────

    @Test
    fun `fetch racing an invalidation returns its result but does not cache it`() = runTest {
        val fetchStarted = CompletableDeferred<Unit>()
        val releaseFetch = CompletableDeferred<Unit>()
        var fetches = 0
        val fetcher = SingleFlightFetcher(cache, epoch)

        val first = async {
            fetcher.getOrFetch(identity, "k") {
                fetches++
                fetchStarted.complete(Unit)
                releaseFetch.await()
                Result.success("v1")
            }
        }
        fetchStarted.await()
        fetcher.invalidate(identity, "k")
        releaseFetch.complete(Unit)

        assertEquals("v1", first.await().getOrNull(), "the racing fetch's result is still returned")
        assertNull(cache.get(identity, "k"), "but must not be written back into the cache")

        val second = fetcher.getOrFetch(identity, "k") { fetches++; Result.success("v2") }
        assertEquals("v2", second.getOrNull())
        assertEquals(2, fetches)
    }

    @Test
    fun `invalidateAll evicts every entry and bumps the epoch`() = runTest {
        var fetches = 0
        val fetcher = SingleFlightFetcher(cache, epoch)

        fetcher.getOrFetch(identity, "k") { fetches++; Result.success("v") }
        fetcher.getOrFetch(otherIdentity, "k") { fetches++; Result.success("v-other") }
        fetcher.invalidateAll()

        assertNull(cache.get(identity, "k"))
        assertNull(cache.get(otherIdentity, "k"))
        assertEquals(1L, epoch.get())

        val reloaded = fetcher.getOrFetch(identity, "k") { fetches++; Result.success("v2") }
        assertEquals("v2", reloaded.getOrNull())
        assertEquals(3, fetches)
    }

    // ── identity isolation ─────────────────────────────────────────────

    @Test
    fun `different identities never share an in-flight flight`() = runTest {
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        var fetches = 0
        val fetcher = SingleFlightFetcher(cache, epoch)

        val first = async {
            fetcher.getOrFetch(identity, "k") {
                fetches++
                firstStarted.complete(Unit)
                releaseFirst.await()
                Result.success("mine")
            }
        }
        firstStarted.await()
        // A caller under a different identity must NOT join the first flight.
        val second = async {
            fetcher.getOrFetch(otherIdentity, "k") { fetches++; Result.success("theirs") }
        }

        assertEquals("theirs", second.await().getOrNull())
        releaseFirst.complete(Unit)
        assertEquals("mine", first.await().getOrNull())
        assertEquals(2, fetches)
    }

    // ── TTL expiry ─────────────────────────────────────────────────────

    @Test
    fun `expired entry is a miss and refetches`() = runTest {
        var now = 0L
        val ttlCache = TtlCache<String>(maxSize = 10, ttlMs = 100, clock = { now })
        var fetches = 0
        val fetcher = SingleFlightFetcher(ttlCache, epoch)

        fetcher.getOrFetch(identity, "k") { fetches++; Result.success("v1") }
        assertEquals("v1", ttlCache.get(identity, "k"))

        now = 101
        val second = fetcher.getOrFetch(identity, "k") { fetches++; Result.success("v2") }

        assertEquals("v2", second.getOrNull())
        assertEquals(2, fetches)
    }
}
