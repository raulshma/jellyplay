package com.raulshma.jellyplay.core.concurrency

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class MapConcurrentTest {

    @Test
    fun `result order equals input order regardless of completion order`() = runTest {
        val items = (1..20).toList()
        val result = Semaphore(4).mapConcurrent(items) { item ->
            delay(((item * 37) % 23).toLong()) // scrambled completion order, virtual time
            item * 2
        }
        assertEquals(items.map { it * 2 }, result)
    }

    @Test
    fun `empty input yields empty output`() = runTest {
        assertEquals(emptyList(), Semaphore(4).mapConcurrent(emptyList<Int>()) { it })
    }

    @Test
    fun `permit bound is never exceeded`() = runTest {
        val permits = 3
        var inFlight = 0
        var maxInFlight = 0
        val result = Semaphore(permits).mapConcurrent((1..50).toList()) { item ->
            inFlight++
            maxInFlight = maxOf(maxInFlight, inFlight)
            delay(item % 5L)
            inFlight--
            item
        }
        assertEquals((1..50).toList(), result)
        assertTrue(maxInFlight > 1, "items never ran concurrently — bound tracking is vacuous")
        assertTrue(maxInFlight <= permits, "max in-flight $maxInFlight exceeded the permit bound $permits")
    }

    @Test
    fun `a CancellationException from an item is never swallowed`() = runTest {
        val probe = async {
            Semaphore(2).mapConcurrent<Int, Int>(listOf(1, 2, 3)) { throw CancellationExceptionIsh }
        }
        probe.join()
        assertTrue(probe.isCancelled, "a CancellationException must cancel the caller, not fail it")
    }

    @Test
    fun `cancellation DURING the map propagates to the caller`() = runTest {
        val cancelled = launch {
            Semaphore(1).mapConcurrent((1..5).toList()) { while (true) delay(1_000) }
        }
        runCurrent()
        cancelled.cancelAndJoin()
        assertTrue(cancelled.isCancelled)
    }

    @Test
    fun `catching variant drops failed items and keeps survivors in order`() = runTest {
        val result = Semaphore(2).mapConcurrentCatching((1..6).toList()) { item ->
            delay(((item * 13) % 7).toLong())
            if (item % 2 == 0) error("boom $item") else item
        }
        assertEquals(listOf(1, 3, 5), result)
    }

    @Test
    fun `catching variant rethrows CancellationException instead of dropping it`() = runTest {
        val probe = async {
            Semaphore(2).mapConcurrentCatching<Int, Int>(listOf(1, 2, 3)) { throw CancellationExceptionIsh }
        }
        probe.join()
        assertTrue(probe.isCancelled, "a CancellationException must propagate, never surface as a dropped item")
    }

    private object CancellationExceptionIsh : kotlin.coroutines.cancellation.CancellationException("stop")
}
