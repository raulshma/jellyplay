package com.raulshma.jellyplay.core.network.subtitle

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Verifies [SubtitleRateLimiter.acquire] serializes calls: the lock is held
 * **across** [acquire]'s block (the network call), so concurrent acquires never
 * overlap in flight, and successive call *starts* are spaced at least
 * [minIntervalMs][SubtitleRateLimiter.minIntervalMs] apart.
 *
 * Spacing gate-passes alone (releasing the lock before the call) would let a
 * slow request overlap the next, tripping OpenSubtitles' 1 req/s ceiling — this
 * test guards against that regression.
 *
 * Uses small intervals + real wall-clock (matching the network module's test
 * style) so the suite stays sub-second.
 */
class SubtitleRateLimiterTest {

    @Test
    fun `concurrent calls do not overlap`() = runBlocking {
        // A block that simulates a slow network call. If two ever run at once,
        // inFlight.max() > 1 and the test fails.
        val limiter = SubtitleRateLimiter(minIntervalMs = 20L)
        val inFlight = AtomicInteger(0)
        val maxInFlight = AtomicInteger(0)

        suspend fun call() = limiter.acquire {
            inFlight.incrementAndGet()
            // Record the high-water mark, then sleep like a real request.
            maxInFlight.updateAndGet { cur -> maxOf(cur, inFlight.get()) }
            delay(30L)
            inFlight.decrementAndGet()
        }

        coroutineScope {
            val jobs = (1..4).map { async { call() } }
            jobs.forEach { it.await() }
        }

        assertEquals(0, inFlight.get(), "all calls completed")
        assertTrue(
            maxInFlight.get() <= 1,
            "calls overlapped in flight (max=$maxInFlight); the lock must be held across the call",
        )
    }

    @Test
    fun `successive call starts are spaced at least minIntervalMs`() = runBlocking {
        val minInterval = 50L
        val limiter = SubtitleRateLimiter(minIntervalMs = minInterval)
        val starts = mutableListOf<Long>()

        repeat(3) {
            limiter.acquire {
                starts += System.currentTimeMillis()
                delay(1L) // near-instant call body so spacing is the gate
            }
        }

        val gaps = starts.zipWithNext { a, b -> b - a }
        gaps.forEach { gap ->
            assertTrue(
                gap >= minInterval - 5L, // allow a small clock slop
                "gap $gap ms was below the $minInterval ms floor — spacing regressed",
            )
        }
        // Sanity: the limiter should not space instant calls absurdly far apart.
        gaps.forEach { gap -> assertFalse(gap > minInterval * 5, "gap $gap ms implausibly large") }
    }
}
