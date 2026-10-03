package com.raulshma.jellyplay.core.concurrency

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class FetchGovernorTest {

    // ---- withDeadlineMs ----

    @Test
    fun `passes the value through when the block finishes inside the deadline`() = runTest {
        assertEquals(42, withDeadlineMs(1_000) { 42 })
        assertEquals("just-in", withDeadlineMs(1_000) { delay(999); "just-in" })
    }

    @Test
    fun `returns null once the deadline expires`() = runTest {
        assertNull(withDeadlineMs(1_000) { delay(2_000); "never" })
    }

    @Test
    fun `returns null on expiry spotted by an explicit virtual-time advance`() = runTest {
        val probe = async { withDeadlineMs(1_000) { delay(5_000); "never" } }
        advanceTimeBy(1_000)
        assertNull(probe.await())
        assertEquals(1_000L, currentTime, "expiry must land exactly on the deadline")
    }

    @Test
    fun `a CancellationException from the block propagates instead of degrading to null`() = runTest {
        val probe = async { withDeadlineMs(60_000) { throw CancellationExceptionIsh } }
        probe.join()
        assertTrue(probe.isCancelled, "must cancel the caller, never report the deadline as null")
    }

    @Test
    fun `outer cancellation cancels the caller parked inside the envelope`() = runTest {
        val cancelled = launch { withDeadlineMs(60_000) { while (true) delay(1_000) } }
        runCurrent()
        cancelled.cancelAndJoin()
        assertTrue(cancelled.isCancelled)
    }

    // ---- SingleFlight ----

    @Test
    fun `same-key callers serialize - bodies never overlap and order is preserved`() = runTest {
        val flight = SingleFlight()
        val log = mutableListOf<String>()
        var inFlight = 0
        var maxInFlight = 0
        val aInside = CompletableDeferred<Unit>()
        val releaseA = CompletableDeferred<Unit>()

        val a = launch {
            flight.inFlight("k") {
                log += "a"
                inFlight++
                maxInFlight = maxOf(maxInFlight, inFlight)
                aInside.complete(Unit)
                releaseA.await()
                inFlight--
            }
        }
        aInside.await() // a provably holds the key, parked mid-body

        val b = launch {
            flight.inFlight("k") {
                log += "b"
                inFlight++
                maxInFlight = maxOf(maxInFlight, inFlight)
                delay(10)
                inFlight--
            }
        }
        runCurrent() // b reaches the taken mutex and queues behind a

        releaseA.complete(Unit)
        a.join()
        b.join()
        assertEquals(listOf("a", "b"), log)
        assertEquals(1, maxInFlight, "same-key bodies overlapped")
    }

    @Test
    fun `different keys never block each other`() = runTest {
        val flight = SingleFlight()
        val aHolds = CompletableDeferred<Unit>()
        val releaseA = CompletableDeferred<Unit>()
        launch { flight.inFlight("a") { aHolds.complete(Unit); releaseA.await() } }
        aHolds.await()

        val bResult = CompletableDeferred<String>()
        launch { bResult.complete(flight.inFlight("b") { "b-ran" }) }
        // b completes while a is still parked inside its block:
        assertEquals("b-ran", bResult.await())
        assertTrue(releaseA.isActive, "a must have been parked the whole time")
        releaseA.complete(Unit)
    }

    @Test
    fun `a same-key caller queued behind the holder runs after it completes`() = runTest {
        val flight = SingleFlight()
        val holderInside = CompletableDeferred<Unit>()
        val releaseHolder = CompletableDeferred<Unit>()
        val holder = launch {
            flight.inFlight("k") { holderInside.complete(Unit); releaseHolder.await() }
        }
        holderInside.await()

        val waiter = async { flight.inFlight("k") { "waiter" } }
        runCurrent() // waiter queued
        releaseHolder.complete(Unit)

        assertEquals("waiter", waiter.await())
        holder.join()
    }

    @Test
    fun `cancelling a queued waiter does not poison the key`() = runTest {
        val flight = SingleFlight()
        val log = mutableListOf<String>()
        val holderInside = CompletableDeferred<Unit>()
        val releaseHolder = CompletableDeferred<Unit>()
        val holder = launch {
            flight.inFlight("k") {
                log += "holder"
                holderInside.complete(Unit)
                releaseHolder.await()
            }
        }
        holderInside.await()

        val waiter = launch { flight.inFlight("k") { log += "waiter" } }
        runCurrent() // waiter queued behind the holder
        waiter.cancel() // dies QUEUED, never having held the mutex
        releaseHolder.complete(Unit)

        // The key is still acquirable and the cancelled waiter never ran:
        assertEquals("after", flight.inFlight("k") { log += "after"; "after" })
        assertEquals(listOf("holder", "after"), log)
    }

    @Test
    fun `a failing block releases the key for the next caller`() = runTest {
        val flight = SingleFlight()
        val boom = IllegalStateException("boom")
        val caught = runCatching { flight.inFlight("k") { throw boom } }.exceptionOrNull()
        assertSame(boom, caught, "the block's exception must propagate unchanged")
        // The next caller is not parked behind the failed pass:
        assertEquals("ok", flight.inFlight("k") { "ok" })
    }

    // ---- boundedPoll ----

    @Test
    fun `succeeds on the first round and passes the round number to the attempt`() = runTest {
        val rounds = mutableListOf<Int>()
        assertEquals("v", boundedPoll(PollSpec(intervalMs = 100)) { round ->
            rounds += round
            if (round == 1) "v" else null
        })
        assertEquals(listOf(1), rounds)
    }

    @Test
    fun `succeeds on a later round, receiving the round number`() = runTest {
        val rounds = mutableListOf<Int>()
        val result = boundedPoll(PollSpec(intervalMs = 100, maxAttempts = 5)) { round ->
            rounds += round
            if (round < 3) null else "third"
        }
        assertEquals("third", result)
        assertEquals(listOf(1, 2, 3), rounds)
        assertEquals(200L, currentTime, "the winning answer must return immediately, no trailing delay")
    }

    @Test
    fun `a null attempt consumes the round and the next fires only after the interval`() = runTest {
        val stamps = mutableListOf<Long>()
        assertNull(
            boundedPoll(PollSpec(intervalMs = 100, maxAttempts = 2)) { stamps += currentTime; null },
        )
        assertEquals(listOf(0L, 100L), stamps, "attempt 2 must wait out the full interval")
        assertEquals(100L, currentTime, "no gap may trail the final attempt")
    }

    @Test
    fun `maxAttempts caps the rounds`() = runTest {
        val rounds = mutableListOf<Int>()
        assertNull(boundedPoll(PollSpec(intervalMs = 50, maxAttempts = 3)) { rounds += it; null })
        assertEquals(listOf(1, 2, 3), rounds)
        assertEquals(100L, currentTime, "two gaps, none trailing the last attempt")
    }

    @Test
    fun `envelope expiry yields null mid-poll`() = runTest {
        val rounds = mutableListOf<Int>()
        assertNull(
            boundedPoll(PollSpec(intervalMs = 100, envelopeMs = 150)) { rounds += it; null },
        )
        // Round 3 was due at t=200; the envelope fired first at t=150:
        assertEquals(listOf(1, 2), rounds)
        assertEquals(150L, currentTime)
    }

    @Test
    fun `envelope expiry cancels a hung attempt and yields null`() = runTest {
        assertNull(
            boundedPoll(PollSpec(intervalMs = 100, envelopeMs = 500)) { delay(10_000); 1 },
        )
        assertEquals(500L, currentTime)
    }

    @Test
    fun `backoff grows the gap multiplicatively and caps at maxIntervalMs`() = runTest {
        val stamps = mutableListOf<Long>()
        assertNull(
            boundedPoll(
                PollSpec(intervalMs = 100, maxAttempts = 4, backoffFactor = 2.0, maxIntervalMs = 250),
            ) { stamps += currentTime; null },
        )
        // Gaps: 100 (100 * 2^0), 200 (100 * 2^1), 250 (400 capped):
        assertEquals(listOf(0L, 100L, 300L, 550L), stamps)
    }

    @Test
    fun `the default maxIntervalMs keeps a fixed cadence even with a backoff factor`() = runTest {
        val stamps = mutableListOf<Long>()
        assertNull(
            boundedPoll(PollSpec(intervalMs = 100, maxAttempts = 3, backoffFactor = 2.0)) {
                stamps += currentTime; null
            },
        )
        // Every gap capped back down to intervalMs:
        assertEquals(listOf(0L, 100L, 200L), stamps)
    }

    @Test
    fun `jitter stays within bounds and reproduces with a pinned Random`() = runTest {
        val stamps = mutableListOf<Long>()
        val start0 = currentTime
        assertNull(
            boundedPoll(PollSpec(intervalMs = 100, maxAttempts = 4, jitterMs = 50, random = SteppedRandom())) {
                stamps += currentTime - start0; null
            },
        )
        // SteppedRandom yields 0, 10, 20 - the exact cadence must be reproducible:
        assertEquals(listOf(0L, 100L, 210L, 330L), stamps)
        val gaps = stamps.zipWithNext { a, b -> b - a }
        assertTrue(gaps.all { it in 100L until 150L }, "gap outside interval..interval+jitter: $gaps")
        assertTrue(gaps.any { it > 100L }, "jitter never moved a gap - assertion is vacuous")

        // Same pinned Random, same cadence — measured relative to the run's
        // own start (the virtual clock carries over inside one runTest):
        val again = mutableListOf<Long>()
        val start1 = currentTime
        assertNull(
            boundedPoll(PollSpec(intervalMs = 100, maxAttempts = 4, jitterMs = 50, random = SteppedRandom())) {
                again += currentTime - start1; null
            },
        )
        assertEquals(stamps, again, "the same pinned Random must reproduce the cadence")
    }

    @Test
    fun `a CancellationException from inside the attempt propagates through the envelope`() = runTest {
        val probe = async {
            boundedPoll(PollSpec(intervalMs = 100, envelopeMs = 60_000)) { throw CancellationExceptionIsh }
        }
        probe.join()
        assertTrue(probe.isCancelled, "must cancel the caller, never surface as a null poll")
    }

    @Test
    fun `cancellation during the gap propagates`() = runTest {
        val rounds = mutableListOf<Int>()
        val cancelled = launch {
            boundedPoll(PollSpec(intervalMs = 5_000, maxAttempts = 10)) { rounds += it; null }
        }
        runCurrent() // attempt 1 consumed its round, now parked in the 5s gap
        cancelled.cancelAndJoin()
        assertTrue(cancelled.isCancelled)
        assertEquals(listOf(1), rounds)
    }

    /**
     * Jitter draw [0, 50) cycling 0, 10, 20, 30, 40, 0, ... - deterministic
     * without betting on [Random] seed internals.
     */
    private class SteppedRandom : Random() {
        private var next = 0L
        override fun nextBits(bitCount: Int): Int = 0 // unused: the poll only draws nextLong
        override fun nextLong(until: Long): Long {
            val value = next
            next = (next + 10) % until
            return value
        }
    }

    private object CancellationExceptionIsh : kotlin.coroutines.cancellation.CancellationException("stop")
}
