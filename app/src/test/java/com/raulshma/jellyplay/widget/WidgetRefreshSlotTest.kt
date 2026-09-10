package com.raulshma.jellyplay.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/**
 * Pure JVM tests for [claimRefreshSlot] — the manual-refresh cooldown slot
 * behind `WidgetWorkScheduler.refreshLibraryNow` / `refreshSeerrNow`.
 *
 * Pins the compareAndSet semantics the former get()-then-set() lacked: two
 * triggers inside the [COOLDOWN_MS] window cannot both claim (the loser's
 * CAS fails against the winner's fresh stamp and it is suppressed WITHOUT
 * restamping), while a claim after the window has expired succeeds again.
 */
class WidgetRefreshSlotTest {

    @Test
    fun `an empty slot accepts the first claim and stamps it`() {
        val slot = AtomicLong(0L)

        assertTrue(claimRefreshSlot(slot, nowMs = 1_000L))
        assertEquals(1_000L, slot.get())
    }

    @Test
    fun `a second claim inside the window is suppressed without restamping`() {
        val slot = AtomicLong(0L)
        assertTrue(claimRefreshSlot(slot, nowMs = 1_000L))

        assertFalse(claimRefreshSlot(slot, nowMs = 1_000L + COOLDOWN_MS - 1))

        // The suppressed claim must not extend the window either.
        assertEquals(1_000L, slot.get())
    }

    @Test
    fun `a claim after the cooldown expiry succeeds again`() {
        val slot = AtomicLong(0L)
        assertTrue(claimRefreshSlot(slot, nowMs = 1_000L))

        assertTrue(claimRefreshSlot(slot, nowMs = 1_000L + COOLDOWN_MS))

        assertEquals(1_000L + COOLDOWN_MS, slot.get())
    }

    @Test
    fun `a stale stamp from long ago does not block the claim`() {
        val slot = AtomicLong(1_000L)

        assertTrue(claimRefreshSlot(slot, nowMs = 1_000L + COOLDOWN_MS + 60_000L))
    }

    @Test
    fun `two racing claims inside the window let exactly one trigger through`() {
        val slot = AtomicLong(0L)
        val threads = 8
        val pool = Executors.newFixedThreadPool(threads)
        val ready = CountDownLatch(threads)
        val start = CountDownLatch(1)
        try {
            // All eight triggers fire on the same stale slot inside the same
            // window — the shape the get()-then-set() race lost.
            val futures = (0 until threads).map {
                pool.submit(
                    Callable {
                        ready.countDown()
                        start.await()
                        claimRefreshSlot(slot, nowMs = 42_000L)
                    },
                )
            }
            ready.await()
            start.countDown()
            val outcomes = futures.map { it.get() }

            assertEquals(1, outcomes.count { it })
            assertEquals(42_000L, slot.get())
        } finally {
            pool.shutdownNow()
        }
    }
}
