package com.raulshma.jellyplay.core.data.playback

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * Pins the download-transfer cap enforced by [DownloadConcurrencyLimiter]:
 *
 *  - a fresh limiter allows exactly [DownloadConcurrencyLimiter.DEFAULT_MAX]
 *    (3) simultaneous [DownloadConcurrencyLimiter.withPermit] blocks;
 *  - [DownloadConcurrencyLimiter.configure] coerces the requested count into
 *    1..6 and the new count is enforced (the "swap to a fresh semaphore"
 *    semantics: in-flight holders of the previous semaphore keep their
 *    permits, new acquisitions race for the new pool);
 *  - [DownloadConcurrencyLimiter.configure] with the current value is a no-op
 *    — it must not refresh exhausted permits;
 *  - observed concurrency is the number of simultaneous permit holders, so the
 *    assertions are deterministic under a single-threaded test scheduler.
 */
class DownloadConcurrencyLimiterTest {

    /** Runs [count] concurrent withPermit blocks, each holding its permit across a suspension. */
    private suspend fun DownloadConcurrencyLimiter.maxObservedConcurrency(
        count: Int,
        holdMs: Long = 1_000,
    ): Int = coroutineScope {
        val active = AtomicInteger(0)
        val maxObserved = AtomicInteger(0)
        (1..count).map {
            async {
                withPermit {
                    val now = active.incrementAndGet()
                    maxObserved.accumulateAndGet(now) { previous, candidate ->
                        maxOf(previous, candidate)
                    }
                    delay(holdMs)
                    active.decrementAndGet()
                }
            }
        }.awaitAll()
        maxObserved.get()
    }

    @Test
    fun `default limiter allows exactly DEFAULT_MAX simultaneous blocks`() = runTest {
        val limiter = DownloadConcurrencyLimiter()

        assertEquals(DownloadConcurrencyLimiter.DEFAULT_MAX, limiter.maxObservedConcurrency(count = 10))
        assertEquals(3, DownloadConcurrencyLimiter.DEFAULT_MAX)
    }

    @Test
    fun `configure coerces values below 1 up to 1`() = runTest {
        val limiter = DownloadConcurrencyLimiter()
        limiter.configure(0)

        assertEquals(1, limiter.maxObservedConcurrency(count = 5))
    }

    @Test
    fun `configure coerces values above 6 down to 6`() = runTest {
        val limiter = DownloadConcurrencyLimiter()
        limiter.configure(100)

        assertEquals(6, limiter.maxObservedConcurrency(count = 12))
    }

    @Test
    fun `configure takes effect for new acquisitions`() = runTest {
        val limiter = DownloadConcurrencyLimiter()
        limiter.configure(2)

        assertEquals(2, limiter.maxObservedConcurrency(count = 8))
    }

    @Test
    fun `configure swaps to a fresh pool so new acquisitions use the new limit`() = runTest {
        val limiter = DownloadConcurrencyLimiter()
        limiter.configure(1)

        val originalHolderFinished = AtomicBoolean(false)
        launch {
            limiter.withPermit {
                delay(10_000)
                originalHolderFinished.set(true)
            }
        }
        runCurrent() // the holder now owns the only permit of the old pool

        // Swap to a 5-permit pool: new acquisitions must proceed immediately —
        // they contend only with each other, not with the abandoned old permit.
        limiter.configure(5)
        val observed = limiter.maxObservedConcurrency(count = 5, holdMs = 1_000)

        assertEquals(5, observed)
        assertFalse(originalHolderFinished.get()) // virtual time only advanced 1s of the 10s hold
    }

    @Test
    fun `configure with the current value is a no-op and does not refresh spent permits`() = runTest {
        val limiter = DownloadConcurrencyLimiter()

        repeat(3) { launch { limiter.withPermit { delay(5_000) } } }
        runCurrent() // all three permits are held and the blocks are parked in delay

        limiter.configure(DownloadConcurrencyLimiter.DEFAULT_MAX) // same value → no swap

        val extraRan = AtomicBoolean(false)
        launch { limiter.withPermit { extraRan.set(true) } }
        runCurrent()

        // A swapped-in fresh semaphore would hand this waiter a permit now.
        assertFalse(extraRan.get())

        advanceUntilIdle()
        assertTrue(extraRan.get()) // it runs once the holders release
    }

    @Test
    fun `withPermit propagates the block result`() = runTest {
        val limiter = DownloadConcurrencyLimiter()

        val result = limiter.withPermit { "value" }

        assertEquals("value", result)
    }

    @Test
    fun `released permits are reusable across sequential batches`() = runTest {
        val limiter = DownloadConcurrencyLimiter()

        assertEquals(3, limiter.maxObservedConcurrency(count = 3))
        assertEquals(3, limiter.maxObservedConcurrency(count = 3))
    }
}
