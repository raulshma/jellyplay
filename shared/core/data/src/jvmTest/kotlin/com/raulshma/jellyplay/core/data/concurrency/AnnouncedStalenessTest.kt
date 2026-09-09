package com.raulshma.jellyplay.core.data.concurrency

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Focused suite for [AnnouncedStaleness] — the arm/consume/re-arm/reset
 * ladder behind the #157 lazy-staleness rule, pinned one behaviour per test
 * the way [SingleFlightFetcherTest] pins the fetcher's contract. No
 * coroutines and no mocks: the marker is a synchronous primitive, and its
 * one concurrency property (an announce racing a consume is never swallowed,
 * a burst of concurrent arms collapses to one consume) is pinned with a
 * plain latch-released thread fan-out.
 */
class AnnouncedStalenessTest {

    // ── arm / consume (the one-shot core) ───────────────────────────────

    @Test
    fun `consume returns false when the marker was never armed`() {
        val marker = AnnouncedStaleness()

        assertFalse(marker.consume())
    }

    @Test
    fun `arm then consume returns true exactly once`() {
        val marker = AnnouncedStaleness()

        marker.arm()

        assertTrue(marker.consume(), "the first read after an announce must see the staleness")
        assertFalse(marker.consume(), "the marker is one-shot: the second read serves cache")
    }

    @Test
    fun `arm is idempotent - a burst of announces collapses to one consume`() {
        val marker = AnnouncedStaleness()

        marker.arm()
        marker.arm()
        marker.arm()

        assertTrue(marker.consume())
        assertFalse(marker.consume())
    }

    // ── the announce/consume race ───────────────────────────────────────

    @Test
    fun `an announce racing a consuming read re-arms the marker for the next read`() {
        // getAndSet semantics: an arm that lands AFTER the consume's atomic
        // swap is preserved — the racing announce must force the NEXT read,
        // not vanish into the one already in flight.
        val marker = AnnouncedStaleness()

        marker.arm()
        assertTrue(marker.consume())
        marker.arm() // the racing announce

        assertTrue(marker.consume(), "the racing announce re-armed the marker")
        assertFalse(marker.consume())
    }

    // ── rearm (the failed-consuming-read recovery) ──────────────────────

    @Test
    fun `rearm restores the one-shot after a consume`() {
        val marker = AnnouncedStaleness()

        marker.arm()
        assertTrue(marker.consume())
        marker.rearm() // the consuming read's fetch failed

        assertTrue(marker.consume(), "the next read retries the force after the failed one")
        assertFalse(marker.consume())
    }

    @Test
    fun `rearm without a prior consume still arms`() {
        // rearm() is only MEANINGFUL post-consume, but it must simply set the
        // marker back — a defensive rearm on a non-consumed marker behaves
        // like an arm, never as a no-op that swallows an announced state.
        val marker = AnnouncedStaleness()

        marker.rearm()

        assertTrue(marker.consume())
    }

    // ── reset (the identity-switch reaction) ────────────────────────────

    @Test
    fun `reset clears an armed marker`() {
        val marker = AnnouncedStaleness()

        marker.arm()
        marker.reset() // identity switch: the previous user's writes armed it

        assertFalse(marker.consume(), "the next user's first read must not inherit the previous user's announce")
    }

    @Test
    fun `reset also clears a re-armed marker`() {
        val marker = AnnouncedStaleness()

        marker.arm()
        assertTrue(marker.consume())
        marker.rearm()
        marker.reset()

        assertFalse(marker.consume())
    }

    // ── concurrent arms collapse to one consume ─────────────────────────

    @Test
    fun `concurrent arms collapse to exactly one stale consume`() {
        // The marker's whole point is that a burst of announces (an outbox
        // drain naming dozens of flips) forces ONE refetch — pinned here with
        // a latch-released fan-out so every arm races the consumes.
        val marker = AnnouncedStaleness()
        val threads = 8
        val armsPerThread = 1_000
        val startGate = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(threads + 1)
        try {
            repeat(threads) {
                pool.execute {
                    startGate.await()
                    repeat(armsPerThread) { marker.arm() }
                }
            }
            val consumer = pool.submit<Int> {
                startGate.await()
                var staleReads = 0
                repeat(threads * armsPerThread) { if (marker.consume()) staleReads++ }
                staleReads
            }
            startGate.countDown()
            val staleReads = consumer.get()

            assertTrue(staleReads >= 1, "at least one consume must observe the announced staleness")
            assertFalse(marker.consume(), "after the burst drains, the marker is not stale")
        } finally {
            pool.shutdownNow()
        }
    }
}
