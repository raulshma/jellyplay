package com.raulshma.jellyplay.core.data.concurrency

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Focused suite for [StaleReadGroup] — the arm/consume/re-arm/reset ladder
 * behind the #157 lazy-staleness rule, plus the [staleAwareRead]
 * choreography the group owns (absorbed from `MediaRepositoryImpl`'s former
 * private helper, so the two bug classes that shaped it — a consumed marker
 * dying with a failed fetch, and a consumed marker not propagating its
 * force past the repository boundary — are now pinned directly on the
 * module instead of only through the repository suites). The marker section
 * ports every assertion of the former `AnnouncedStalenessTest` verbatim
 * (the marker is the same primitive); the registry section pins the
 * announce fan-out's group sets. No mocks anywhere: the group is a
 * synchronous primitive around one [java.util.concurrent.atomic.AtomicBoolean],
 * and its one concurrency property (an announce racing a consume is never
 * swallowed, a burst of concurrent arms collapses to one consume) is pinned
 * with a plain latch-released thread fan-out.
 */
class StaleReadGroupTest {

    // ── arm / consume (the one-shot core) ───────────────────────────────

    @Test
    fun `consume returns false when the marker was never armed`() {
        val group = StaleReadGroup()

        assertFalse(group.consume())
    }

    @Test
    fun `arm then consume returns true exactly once`() {
        val group = StaleReadGroup()

        group.arm()

        assertTrue(group.consume(), "the first read after an announce must see the staleness")
        assertFalse(group.consume(), "the marker is one-shot: the second read serves cache")
    }

    @Test
    fun `arm is idempotent - a burst of announces collapses to one consume`() {
        val group = StaleReadGroup()

        group.arm()
        group.arm()
        group.arm()

        assertTrue(group.consume())
        assertFalse(group.consume())
    }

    // ── the announce/consume race ───────────────────────────────────────

    @Test
    fun `an announce racing a consuming read re-arms the marker for the next read`() {
        // getAndSet semantics: an arm that lands AFTER the consume's atomic
        // swap is preserved — the racing announce must force the NEXT read,
        // not vanish into the one already in flight.
        val group = StaleReadGroup()

        group.arm()
        assertTrue(group.consume())
        group.arm() // the racing announce

        assertTrue(group.consume(), "the racing announce re-armed the marker")
        assertFalse(group.consume())
    }

    // ── rearm (the failed-consuming-read recovery) ──────────────────────

    @Test
    fun `rearm restores the one-shot after a consume`() {
        val group = StaleReadGroup()

        group.arm()
        assertTrue(group.consume())
        group.rearm() // the consuming read's fetch failed

        assertTrue(group.consume(), "the next read retries the force after the failed one")
        assertFalse(group.consume())
    }

    @Test
    fun `rearm without a prior consume still arms`() {
        // rearm() is only MEANINGFUL post-consume, but it must simply set the
        // marker back — a defensive rearm on a non-consumed marker behaves
        // like an arm, never as a no-op that swallows an announced state.
        val group = StaleReadGroup()

        group.rearm()

        assertTrue(group.consume())
    }

    // ── reset (the identity-switch reaction) ────────────────────────────

    @Test
    fun `reset clears an armed marker`() {
        val group = StaleReadGroup()

        group.arm()
        group.reset() // identity switch: the previous user's writes armed it

        assertFalse(group.consume(), "the next user's first read must not inherit the previous user's announce")
    }

    @Test
    fun `reset also clears a re-armed marker`() {
        val group = StaleReadGroup()

        group.arm()
        assertTrue(group.consume())
        group.rearm()
        group.reset()

        assertFalse(group.consume())
    }

    // ── concurrent arms collapse to one consume ─────────────────────────

    @Test
    fun `concurrent arms collapse to exactly one stale consume`() {
        // The marker's whole point is that a burst of announces (an outbox
        // drain naming dozens of flips) forces ONE refetch — pinned here with
        // a latch-released fan-out so every arm races the consumes.
        val group = StaleReadGroup()
        val threads = 8
        val armsPerThread = 1_000
        val startGate = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(threads + 1)
        try {
            repeat(threads) {
                pool.execute {
                    startGate.await()
                    repeat(armsPerThread) { group.arm() }
                }
            }
            val consumer = pool.submit<Int> {
                startGate.await()
                var staleReads = 0
                repeat(threads * armsPerThread) { if (group.consume()) staleReads++ }
                staleReads
            }
            startGate.countDown()
            val staleReads = consumer.get()

            assertTrue(staleReads >= 1, "at least one consume must observe the announced staleness")
            assertFalse(group.consume(), "after the burst drains, the marker is not stale")
        } finally {
            pool.shutdownNow()
        }
    }

    // ── staleAwareRead (the choreography the group owns) ────────────────

    /** Every effectiveForce the read lambda saw, one entry per call. */
    private fun recordedRead(flags: MutableList<Boolean>): suspend (Boolean) -> Result<Int> =
        { effectiveForce ->
            flags.add(effectiveForce)
            Result.success(effectiveForce.hashCode())
        }

    @Test
    fun `an announce makes the next read force the fetch`() = runTest {
        // announce → the next read's fetch bypasses the cached payload: the
        // read lambda receives effectiveForce = true even though the caller
        // passed force = false.
        val group = StaleReadGroup()
        val flags = mutableListOf<Boolean>()

        group.arm()
        group.staleAwareRead(force = false, read = recordedRead(flags))
        group.staleAwareRead(force = false, read = recordedRead(flags))

        assertEquals(listOf(true, false), flags, "the announce forced exactly the first read")
    }

    @Test
    fun `a successful consuming read consumes the marker`() = runTest {
        // One announce buys ONE forced read: after the consuming read
        // succeeds, the marker is spent and the next read serves cache.
        val group = StaleReadGroup()
        val flags = mutableListOf<Boolean>()

        group.arm()
        group.staleAwareRead(force = false, read = recordedRead(flags))
        group.staleAwareRead(force = false, read = recordedRead(flags))
        group.staleAwareRead(force = false, read = recordedRead(flags))

        assertEquals(listOf(true, false, false), flags)
    }

    @Test
    fun `a failed fetch re-arms the marker - the next read refetches`() = runTest {
        // 1ba22d962: a consumed marker must not die with the read that spent
        // it. The failing shape here is a RETURNED Result.failure — the read
        // produced nothing, so the marker comes back and the next read
        // retries the force.
        val group = StaleReadGroup()
        val flags = mutableListOf<Boolean>()

        group.arm()
        val failed = group.staleAwareRead<Int>(force = false) { effectiveForce ->
            flags.add(effectiveForce)
            Result.failure(RuntimeException("offline blip"))
        }
        assertTrue(failed.isFailure)
        group.staleAwareRead(force = false, read = recordedRead(flags))

        assertEquals(listOf(true, true), flags, "the failed consuming read re-armed the marker")
    }

    @Test
    fun `a thrown failure re-arms the marker - cancellation does not lose the staleness`() = runTest {
        // The THROWN failure shape (which covers the caller's own
        // cancellation — the read coroutine unwound before any Result
        // existed): the exception must propagate untouched AND the marker
        // must come back, or a screen navigated away mid-fetch would strand
        // the pre-announce payload until the next announce or the TTL.
        val group = StaleReadGroup()
        val flags = mutableListOf<Boolean>()

        group.arm()
        var propagated: Throwable? = null
        try {
            group.staleAwareRead<Int>(force = false) { effectiveForce ->
                flags.add(effectiveForce)
                throw CancellationException("caller navigated away mid-fetch")
            }
        } catch (expected: CancellationException) {
            propagated = expected
        }
        // A synthetic cancellation thrown BY the read lambda, caught here
        // before it can reach the test coroutine's cancellation machinery.
        assertTrue(propagated != null, "the thrown failure must propagate to the caller")
        group.staleAwareRead(force = false, read = recordedRead(flags))

        assertEquals(listOf(true, true), flags, "the cancelled consuming read re-armed the marker")
    }

    @Test
    fun `a manual force consumes the marker - bypasses but does not strand it`() = runTest {
        // The manual lever is at least as fresh as the announce, so the
        // forced read consumes the marker too — leaving it armed would only
        // buy one redundant forced read later.
        val group = StaleReadGroup()
        val flags = mutableListOf<Boolean>()

        group.arm()
        group.staleAwareRead(force = true, read = recordedRead(flags))
        group.staleAwareRead(force = false, read = recordedRead(flags))

        assertEquals(listOf(true, false), flags, "the manually forced read spent the marker")
    }

    @Test
    fun `a manual force without an announce consumes nothing`() = runTest {
        // No announce → nothing to spend: the forced read still hands the
        // read its lever, and the next non-forced read is plain.
        val group = StaleReadGroup()
        val flags = mutableListOf<Boolean>()

        group.staleAwareRead(force = true, read = recordedRead(flags))
        group.staleAwareRead(force = false, read = recordedRead(flags))

        assertEquals(listOf(true, false), flags)
        assertFalse(group.consume(), "no announce ever armed the marker")
    }

    @Test
    fun `a failed manually-forced read re-arms an armed marker`() = runTest {
        // force=true does NOT suppress the re-arm: the marker was consumed
        // (stalenessConsumed), so the failed read must restore it regardless
        // of which lever forced the fetch.
        val group = StaleReadGroup()
        val flags = mutableListOf<Boolean>()

        group.arm()
        val failed = group.staleAwareRead<Int>(force = true) { effectiveForce ->
            flags.add(effectiveForce)
            Result.failure(RuntimeException("offline blip"))
        }
        assertTrue(failed.isFailure)
        group.staleAwareRead(force = false, read = recordedRead(flags))

        assertEquals(listOf(true, true), flags, "the failed forced read re-armed the consumed marker")
    }

    // ── StaleReadGroups (the announce fan-out) ──────────────────────────

    @Test
    fun `announceUserDataWrite arms only the riders`() {
        // The gap-group channel: every user-data write/invalidation arms the
        // riders, never the plain groups (home sections must not pay a
        // forced refetch for a write that failed or never confirmed).
        val registry = StaleReadGroups()
        val plain = registry.register()
        val rider = registry.register(ridesUserDataWrite = true)

        registry.announceUserDataWrite()

        assertTrue(rider.consume(), "the rider armed")
        assertFalse(plain.consume(), "the plain group must arm only on a confirmed announce")
    }

    @Test
    fun `announceConfirmedWrite arms every group`() {
        // The confirmed-own-write channel is the superset: home sections and
        // the riders all stale.
        val registry = StaleReadGroups()
        val plain = registry.register()
        val rider = registry.register(ridesUserDataWrite = true)

        registry.announceConfirmedWrite()

        assertTrue(plain.consume(), "the confirmed write armed the plain group")
        assertTrue(rider.consume(), "the confirmed write also armed the rider")
    }

    @Test
    fun `resetAll clears every marker - armed or spent`() {
        // The identity-switch reaction: every marker was armed by the
        // PREVIOUS user's writes, so all of them reset together.
        val registry = StaleReadGroups()
        val plain = registry.register()
        val rider = registry.register(ridesUserDataWrite = true)

        registry.announceConfirmedWrite()
        assertTrue(plain.consume()) // spend the plain marker; the rider stays armed
        registry.resetAll()

        assertFalse(plain.consume(), "a spent marker stays spent")
        assertFalse(rider.consume(), "an armed marker must not survive the identity switch")
    }
}
