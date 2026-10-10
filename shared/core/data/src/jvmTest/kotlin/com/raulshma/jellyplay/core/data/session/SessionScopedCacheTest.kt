package com.raulshma.jellyplay.core.data.session

import com.raulshma.jellyplay.core.model.CacheIdentity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins [SessionScopedCache] — the identity-cache chassis that absorbed the
 * TtlCache + epoch + SingleFlightFetcher + registry ritual — once, replacing
 * the per-site pins of the ritual itself (the segments identity-switch veto
 * shape lives on in PlaybackRepositoryImplTest against the real repository;
 * this suite pins the chassis's own contract):
 *  - identity-miss invalidation: a read under a different identity misses by
 *    construction and refetches;
 *  - wholesale clear on a non-SignedIn transition (and nothing on SignedIn);
 *  - epoch write-veto: a fetch racing an invalidation is returned to its
 *    caller but never written back;
 *  - single-flight collapse of concurrent callers;
 *  - the storable variant's per-flight store veto;
 *  - the injected-epoch group shape: one shared AtomicLong stall-guards
 *    every member's writers.
 */
class SessionScopedCacheTest {

    /** SessionIdentityProvider stub: a settable identity + a transition flow. */
    private class FakeIdentity : SessionIdentityProvider {
        var current: SessionIdentity? = null
        override val transitions = MutableSharedFlow<HomeSessionTransition>(extraBufferCapacity = 16)
        override suspend fun currentIdentity(): SessionIdentity? = current
        override fun currentIdentitySnapshot(): SessionIdentity? = current
        override suspend fun cacheIdentity(): CacheIdentity =
            current?.let { CacheIdentity.of(it.serverId, it.userId) } ?: CacheIdentity.UNKNOWN
        override fun cacheIdentitySnapshot(): CacheIdentity =
            current?.let { CacheIdentity.of(it.serverId, it.userId) } ?: CacheIdentity.UNKNOWN

        fun emit(transition: HomeSessionTransition) {
            transitions.tryEmit(transition)
        }
    }

    private val identity = FakeIdentity()

    /**
     * The registry collector runs on [TestScope.backgroundScope] (the test
     * scheduler), so `runCurrent()` deterministically advances transition →
     * reaction — the [SessionCacheRegistryTest] idiom. A Dispatchers.Default
     * scope would race the assertions.
     */
    private fun TestScope.buildRegistry(): SessionCacheRegistry =
        SessionCacheRegistry(identity, backgroundScope).also { runCurrent() }

    private fun <V : Any> cache(
        owner: String = "test-owner",
        epoch: java.util.concurrent.atomic.AtomicLong = java.util.concurrent.atomic.AtomicLong(0L),
        registry: SessionCacheRegistry,
    ) = SessionScopedCache<V>(owner, ttlMs = 60_000L, epoch = epoch, registry = registry) {
        identity.cacheIdentity()
    }

    private fun userSwitched(from: SessionIdentity) = HomeSessionTransition.UserSwitched(from)

    private fun session(serverId: String, userId: String) = SessionIdentity(serverId, userId)

    // ── identity-miss invalidation ──────────────────────────────────────

    @Test
    fun `a read under a different identity misses and refetches`() = runTest {
        val target = cache<String>(registry = buildRegistry())
        val fetches = AtomicInteger(0)

        identity.current = session("s1", "u1")
        target.getOrFetch("k") { Result.success("a").also { fetches.incrementAndGet() } }
        target.getOrFetch("k") { Result.success("b").also { fetches.incrementAndGet() } }
        assertEquals(1, fetches.get(), "same identity serves the cached entry")

        identity.current = session("s1", "u2")
        target.getOrFetch("k") { Result.success("c").also { fetches.incrementAndGet() } }
        assertEquals(2, fetches.get(), "a different identity is a guaranteed miss by construction")
    }

    // ── transition reaction (the ONE registered action) ─────────────────

    @Test
    fun `a non-SignedIn transition clears the cache wholesale`() = runTest {
        val target = cache<String>(registry = buildRegistry())
        val fetches = AtomicInteger(0)

        identity.current = session("s1", "u1")
        target.getOrFetch("k") { Result.success("v").also { fetches.incrementAndGet() } }
        assertEquals(1, fetches.get())

        // SignedIn is skipped by the registry wholesale — no previous identity.
        identity.current = session("s1", "u1")
        identity.emit(HomeSessionTransition.SignedIn)
        runCurrent()
        target.getOrFetch("k") { Result.success("v").also { fetches.incrementAndGet() } }
        assertEquals(1, fetches.get(), "SignedIn must not clear")

        identity.emit(userSwitched(session("s1", "u1")))
        runCurrent()
        target.getOrFetch("k") { Result.success("v").also { fetches.incrementAndGet() } }
        assertEquals(2, fetches.get(), "UserSwitched must clear the cache wholesale")
    }

    @Test
    fun `a fetch racing a transition is returned but its write-back is vetoed`() = runTest {
        val target = cache<String>(registry = buildRegistry())
        val fetches = AtomicInteger(0)
        val fetchStarted = CompletableDeferred<Unit>()
        val releaseFetch = CompletableDeferred<Unit>()

        identity.current = session("s1", "u1")
        val racingFlight = async {
            target.getOrFetch("k") {
                fetches.incrementAndGet()
                fetchStarted.complete(Unit)
                releaseFetch.await()
                Result.success("stale")
            }
        }
        fetchStarted.await()
        identity.emit(userSwitched(session("s1", "u1")))
        runCurrent()
        releaseFetch.complete(Unit)

        assertTrue(racingFlight.await().isSuccess, "the racing fetch's result is still returned to its caller")
        target.getOrFetch("k") { Result.success("fresh").also { fetches.incrementAndGet() } }
        assertEquals(2, fetches.get(), "the vetoed write-back must not pin the stale entry")
    }

    // ── invalidate / force ──────────────────────────────────────────────

    @Test
    fun `invalidate bumps the epoch so a racing fetch is not cached`() = runTest {
        val target = cache<String>(registry = buildRegistry())
        val fetches = AtomicInteger(0)
        val fetchStarted = CompletableDeferred<Unit>()
        val releaseFetch = CompletableDeferred<Unit>()

        val racingFlight = async {
            target.getOrFetch("k") {
                fetches.incrementAndGet()
                fetchStarted.complete(Unit)
                releaseFetch.await()
                Result.success("stale")
            }
        }
        fetchStarted.await()
        target.invalidate(identity.cacheIdentitySnapshot(), "k")
        releaseFetch.complete(Unit)
        assertTrue(racingFlight.await().isSuccess)

        target.getOrFetch("k") { Result.success("fresh").also { fetches.incrementAndGet() } }
        assertEquals(2, fetches.get(), "a fetch that raced the invalidation must not be cached")
    }

    @Test
    fun `force refetches and a failed forced fetch leaves nothing behind`() = runTest {
        val target = cache<String>(registry = buildRegistry())
        val fetches = AtomicInteger(0)

        target.getOrFetch("k") { Result.success("v1").also { fetches.incrementAndGet() } }
        target.getOrFetch("k", force = true) { Result.success("v2").also { fetches.incrementAndGet() } }
        assertEquals(2, fetches.get(), "force is the invalidate-then-read freshness lever")

        // A failed forced fetch must leave the (evicted) key empty — the next
        // plain read refetches.
        target.getOrFetch("k", force = true) { Result.failure(IllegalStateException("down")) }
        target.getOrFetch("k") { Result.success("v3").also { fetches.incrementAndGet() } }
        // Three fetches total: v1, the forced v2, and v3 — the failed forced
        // fetch left nothing behind, so the plain read refetched (and the
        // failed lambda itself, uncounted, never cached).
        assertEquals(3, fetches.get())
        assertEquals("v3", target.get(com.raulshma.jellyplay.core.model.CacheIdentity.UNKNOWN, "k"))
    }

    // ── single-flight ───────────────────────────────────────────────────

    @Test
    fun `concurrent callers collapse into one fetch`() = runTest {
        val target = cache<String>(registry = buildRegistry())
        val fetches = AtomicInteger(0)
        val fetchStarted = CompletableDeferred<Unit>()
        val releaseFetch = CompletableDeferred<Unit>()

        val (a, b) = coroutineScope {
            val a = async {
                target.getOrFetch("k") {
                    fetches.incrementAndGet()
                    fetchStarted.complete(Unit)
                    releaseFetch.await()
                    Result.success("shared")
                }
            }
            fetchStarted.await()
            val b = async { target.getOrFetch("k") { Result.success("never").also { fetches.incrementAndGet() } } }
            // Let the second caller register on the in-flight flight before release.
            delay(1)
            releaseFetch.complete(Unit)
            a to b
        }

        assertTrue(a.await().isSuccess)
        assertTrue(b.await().isSuccess)
        assertEquals(1, fetches.get(), "concurrent callers share one flight")
    }

    // ── getOrFetchStorable ──────────────────────────────────────────────

    @Test
    fun `a store-vetoed flight result is returned but never cached`() = runTest {
        val target = cache<String>(registry = buildRegistry())
        val fetches = AtomicInteger(0)

        val rejected = target.getOrFetchStorable("k") {
            fetches.incrementAndGet()
            Result.success("fallback") to false
        }
        assertTrue(rejected.isSuccess, "the store-vetoed result still reaches its caller")
        target.getOrFetch("k") { Result.success("fresh").also { fetches.incrementAndGet() } }
        assertEquals(2, fetches.get(), "store = false must leave the cache untouched")

        // A stored fetch caches on a MISSED key: evict first (the storable
        // read rides the same fast path as a plain read — a cached value
        // serves without fetching), then store=true lands "cachable".
        target.invalidate(com.raulshma.jellyplay.core.model.CacheIdentity.UNKNOWN, "k")
        val stored = target.getOrFetchStorable("k") {
            fetches.incrementAndGet()
            Result.success("cachable") to true
        }
        assertTrue(stored.isSuccess)
        // store = true cached "cachable": the next plain read SERVES it — no
        // fetch, so the counter stays at three.
        target.getOrFetch("k") { Result.success("never").also { fetches.incrementAndGet() } }
        assertEquals(3, fetches.get())
        assertEquals("cachable", target.get(com.raulshma.jellyplay.core.model.CacheIdentity.UNKNOWN, "k"))
    }

    // ── the injected-epoch group shape ──────────────────────────────────

    @Test
    fun `caches sharing one injected epoch veto each other's racing writes`() = runTest {
        val registry = buildRegistry()
        val sharedEpoch = java.util.concurrent.atomic.AtomicLong(0L)
        val memberA = cache<String>(owner = "group-a", epoch = sharedEpoch, registry = registry)
        val memberB = cache<String>(owner = "group-b", epoch = sharedEpoch, registry = registry)
        val fetches = AtomicInteger(0)
        val fetchStarted = CompletableDeferred<Unit>()
        val releaseFetch = CompletableDeferred<Unit>()

        val racingFlight = async {
            memberA.getOrFetch("k") {
                fetches.incrementAndGet()
                fetchStarted.complete(Unit)
                releaseFetch.await()
                Result.success("stale")
            }
        }
        fetchStarted.await()
        // The group's invalidation bumps the ONE epoch through a sibling.
        memberB.invalidateAll()
        releaseFetch.complete(Unit)
        assertTrue(racingFlight.await().isSuccess)

        memberA.getOrFetch("k") { Result.success("fresh").also { fetches.incrementAndGet() } }
        assertEquals(2, fetches.get(), "the sibling's bump must stall-guard this member's write-back")
    }
}
