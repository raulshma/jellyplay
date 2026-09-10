package com.raulshma.jellyplay.core.data.cache

import com.raulshma.jellyplay.core.model.CacheIdentity
import com.raulshma.jellyplay.core.model.TtlCache
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Concurrency-free semantics suite for the identity-keyed cache-through read
 * ([getOrFetch] / [getOrFetchGuarded] / [getOrFetchTyped]) — the choreography
 * ported verbatim out of `MediaRepositoryImpl`'s nine hand copies and
 * `SeerrRepositoryImpl`'s private `getCached`/`putCached` twin. MockK-free:
 * the fetch is a plain lambda and the identity supplier is a var-backed
 * closure standing in for `HomeSession.cacheIdentity()` — the same fake
 * HomeSession shape the sibling [com.raulshma.jellyplay.core.data.concurrency.SingleFlightFetcherTest]
 * uses. The fake clock drives [TtlCache]'s TTL exactly like the sibling's
 * expiry pin.
 */
class IdentityCacheFetchTest {

    private val identity = CacheIdentity.of("server-1", "user-1")
    private val otherIdentity = CacheIdentity.of("server-1", "user-2")

    /** Var-backed stand-in for the suspend identity read (HomeSession seam). */
    private var currentIdentity: CacheIdentity = identity
    private var identityReads = 0

    /** Fake monotonic clock driving [TtlCache]'s TTL contract. */
    private var now = 0L

    private lateinit var cache: TtlCache<String>

    @BeforeTest
    fun setup() {
        currentIdentity = identity
        identityReads = 0
        now = 0L
        cache = TtlCache(maxSize = 10, ttlMs = 60_000L, clock = { now })
    }

    private fun supplier(): suspend () -> CacheIdentity = {
        identityReads++
        currentIdentity
    }

    // ── hit / miss ──────────────────────────────────────────────────────

    @Test
    fun `miss fetches, stores on success, and returns the result`() = runTest {
        var fetches = 0

        val result = cache.getOrFetch(supplier(), "k") { fetches++; Result.success("v1") }

        assertTrue(result.isSuccess)
        assertEquals("v1", result.getOrNull())
        assertEquals(1, fetches)
        assertEquals("v1", cache.get(identity, "k"))
    }

    @Test
    fun `cached hit skips the fetch`() = runTest {
        var fetches = 0
        cache.getOrFetch(supplier(), "k") { fetches++; Result.success("v1") }

        val second = cache.getOrFetch(supplier(), "k") { fetches++; Result.success("v2") }

        assertEquals("v1", second.getOrNull(), "the cached value must win over the fetch")
        assertEquals(1, fetches)
    }

    @Test
    fun `identity supplier is read exactly once per call`() = runTest {
        cache.getOrFetch(supplier(), "k") { Result.success("v") }
        cache.getOrFetch(supplier(), "k") { Result.success("v") }

        assertEquals(2, identityReads, "one identity read per call, like the hand-copied sequence")
    }

    @Test
    fun `failed fetch is returned as-is and never stored`() = runTest {
        var fetches = 0

        val failure = cache.getOrFetch(supplier(), "k") {
            fetches++
            Result.failure(IllegalStateException("down"))
        }

        assertTrue(failure.isFailure)
        assertEquals(1, fetches)
        assertNull(cache.get(identity, "k"), "a failed fetch must not populate the cache")

        val retry = cache.getOrFetch(supplier(), "k") { fetches++; Result.success("v") }
        assertEquals("v", retry.getOrNull(), "the next call must retry, not serve the failure")
        assertEquals(2, fetches)
    }

    @Test
    fun `expired entry is a miss and refetches`() = runTest {
        var fetches = 0
        cache.getOrFetch(supplier(), "k") { fetches++; Result.success("v1") }

        now = 60_001L // past the TTL — lazily expired on read
        val second = cache.getOrFetch(supplier(), "k") { fetches++; Result.success("v2") }

        assertEquals("v2", second.getOrNull())
        assertEquals(2, fetches)
    }

    // ── force (the freshness lever) ─────────────────────────────────────

    @Test
    fun `force evicts before the read so a fresh fetch runs`() = runTest {
        var fetches = 0
        cache.getOrFetch(supplier(), "k") { fetches++; Result.success("v1") }

        val forced = cache.getOrFetch(supplier(), "k", force = true) { fetches++; Result.success("v2") }

        assertEquals("v2", forced.getOrNull())
        assertEquals(2, fetches, "force must bypass the cached entry")
        assertEquals("v2", cache.get(identity, "k"), "the fresh value replaces the evicted one")
    }

    @Test
    fun `no force serves the cached entry without fetching`() = runTest {
        var fetches = 0
        cache.getOrFetch(supplier(), "k") { fetches++; Result.success("v1") }

        val second = cache.getOrFetch(supplier(), "k", force = false) { fetches++; Result.success("v2") }

        assertEquals("v1", second.getOrNull())
        assertEquals(1, fetches)
    }

    // ── onFetched (the home-sections SWR persist seam) ──────────────────

    @Test
    fun `onFetched runs after the put on the fetch path only, never on a hit`() = runTest {
        val hookValues = mutableListOf<String>()
        var fetches = 0

        suspend fun load(): Result<String> = cache.getOrFetch(
            supplier(),
            "k",
            onFetched = { hookValues += it },
        ) { fetches++; Result.success("v$fetches") }

        load() // fetch path: put then hook
        load() // hit path: neither put nor hook

        assertEquals(listOf("v1"), hookValues, "the hook must not run on a cache hit")
        assertEquals(1, fetches)
    }

    // ── epoch-guarded write ─────────────────────────────────────────────

    @Test
    fun `guarded write stores when the epoch is unchanged`() = runTest {
        var epoch = 0L
        var fetches = 0

        val result = cache.getOrFetchGuarded(supplier(), "k", currentEpoch = { epoch }) {
            fetches++; Result.success("v1")
        }

        assertEquals("v1", result.getOrNull())
        assertEquals("v1", cache.get(identity, "k"))
        assertEquals(1, fetches)
    }

    @Test
    fun `guarded write discards a fetch that raced an invalidation`() = runTest {
        var epoch = 0L
        var fetches = 0

        val raced = cache.getOrFetchGuarded(supplier(), "k", currentEpoch = { epoch }) {
            fetches++
            epoch = 1L // the invalidation lands mid-fetch
            Result.success("stale")
        }

        assertEquals("stale", raced.getOrNull(), "the racing fetch's result is still returned")
        assertNull(cache.get(identity, "k"), "but must never be written into the cache")

        val fresh = cache.getOrFetchGuarded(supplier(), "k", currentEpoch = { epoch }) {
            fetches++; Result.success("fresh")
        }
        assertEquals("fresh", fresh.getOrNull())
        assertEquals(2, fetches, "the discarded write forces a refetch")
    }

    @Test
    fun `guarded hit short-circuits without touching the epoch`() = runTest {
        var epoch = 0L
        var epochReads = 0
        var fetches = 0
        cache.getOrFetchGuarded(supplier(), "k", currentEpoch = { epoch }) { fetches++; Result.success("v1") }

        val hit = cache.getOrFetchGuarded(supplier(), "k", currentEpoch = { epochReads++; epoch }) {
            fetches++; Result.success("v2")
        }

        assertEquals("v1", hit.getOrNull())
        assertEquals(1, fetches)
        assertEquals(0, epochReads, "the epoch is captured only after a miss, matching the ported order")
    }

    // ── identity isolation (the guaranteed-miss doctrine) ───────────────

    @Test
    fun `identity switch produces a guaranteed miss and writes under the new identity`() = runTest {
        var fetches = 0
        cache.getOrFetch(supplier(), "k") { fetches++; Result.success("user-1 data") }
        assertEquals("user-1 data", cache.get(identity, "k"))

        currentIdentity = otherIdentity
        val switched = cache.getOrFetch(supplier(), "k") { fetches++; Result.success("user-2 data") }

        assertEquals("user-2 data", switched.getOrNull(), "the previous identity's entry can never serve")
        assertEquals(2, fetches)
        assertEquals("user-2 data", cache.get(otherIdentity, "k"))
        assertEquals("user-1 data", cache.get(identity, "k"), "the old identity's entry is untouched by the read")
    }

    @Test
    fun `force evicts only the current identity's entry`() = runTest {
        var fetches = 0
        cache.getOrFetch(supplier(), "k") { fetches++; Result.success("user-1 data") }
        currentIdentity = otherIdentity
        cache.getOrFetch(supplier(), "k") { fetches++; Result.success("user-2 data") }

        val forced = cache.getOrFetch(supplier(), "k", force = true) { fetches++; Result.success("user-2 fresh") }

        assertEquals("user-2 fresh", forced.getOrNull())
        assertEquals("user-1 data", cache.get(identity, "k"), "the other identity's entry must survive the lever")
    }

    // ── typed read over a heterogeneous cache ───────────────────────────

    @Test
    fun `typed hit returns the cached value without fetching`() = runTest {
        val anyCache = TtlCache<Any>(maxSize = 10, ttlMs = 60_000L, clock = { now })
        var fetches = 0
        anyCache.put(identity, "movie_1", "a typed payload")

        val hit = anyCache.getOrFetchTyped<String>({ currentIdentity }, "movie_1") {
            fetches++; Result.success("fetched")
        }

        assertEquals("a typed payload", hit.getOrNull())
        assertEquals(0, fetches)
    }

    @Test
    fun `typed hit of a foreign type is a miss and refetches`() = runTest {
        // The Seerr twin's `as? T` fidelity: an entry that doesn't cast is
        // treated as absent, so the fetch runs and overwrites the entry.
        val anyCache = TtlCache<Any>(maxSize = 10, ttlMs = 60_000L, clock = { now })
        var fetches = 0
        anyCache.put(identity, "movie_1", 42)

        val refetched = anyCache.getOrFetchTyped<String>({ currentIdentity }, "movie_1") {
            fetches++; Result.success("fetched")
        }

        assertEquals("fetched", refetched.getOrNull())
        assertEquals(1, fetches)
        assertEquals("fetched", anyCache.get(identity, "movie_1"))
    }
}
