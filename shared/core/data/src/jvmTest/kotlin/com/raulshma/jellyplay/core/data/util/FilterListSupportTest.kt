package com.raulshma.jellyplay.core.data.util

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * Pins [loadListWithRetry]'s single-retry contract — the best-effort recovery
 * behind the genre/tag filter lookups:
 *
 *  - a failed first fetch is retried exactly once after
 *    [FILTER_RETRY_DELAY_MS] (not before, not twice), and the successful retry
 *    delivers its list to [onResult] exactly once;
 *  - a first-attempt success delivers immediately with no virtual-time delay
 *    consumed;
 *  - when both attempts fail, [onResult] is never called and the caller's
 *    result stays untouched after exactly two fetch calls.
 */
class FilterListSupportTest {

    @Test
    fun `failed first fetch retries after the delay and delivers the result once`() = runTest {
        var fetchCalls = 0
        val delivered = mutableListOf<List<String>>()
        val job = launch {
            loadListWithRetry(
                fetch = {
                    fetchCalls++
                    if (fetchCalls == 1) {
                        Result.failure(IOException("transient blip"))
                    } else {
                        Result.success(listOf("Action", "Comedy"))
                    }
                },
                onResult = { delivered.add(it) },
            )
        }

        runCurrent()
        assertEquals(1, fetchCalls) // first attempt is immediate
        assertTrue(delivered.isEmpty()) // nothing delivered after the failure

        advanceTimeBy(FILTER_RETRY_DELAY_MS - 1)
        runCurrent()
        assertEquals(1, fetchCalls) // the retry must not fire early

        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, fetchCalls) // retried exactly at the delay boundary

        job.join()
        assertEquals(listOf(listOf("Action", "Comedy")), delivered) // delivered once
    }

    @Test
    fun `first-attempt success delivers immediately without consuming the retry delay`() = runTest {
        var fetchCalls = 0
        val delivered = mutableListOf<List<Int>>()

        loadListWithRetry(
            fetch = {
                fetchCalls++
                Result.success(listOf(1, 2))
            },
            onResult = { delivered.add(it) },
        )

        assertEquals(1, fetchCalls)
        assertEquals(listOf(listOf(1, 2)), delivered)
        assertEquals(0, testScheduler.currentTime) // no retry delay elapsed
    }

    @Test
    fun `both attempts failing leaves the result untouched after exactly two calls`() = runTest {
        var fetchCalls = 0
        val delivered = mutableListOf<List<String>>()
        val job = launch {
            loadListWithRetry(
                fetch = {
                    fetchCalls++
                    Result.failure(IOException("still down"))
                },
                onResult = { delivered.add(it) },
            )
        }

        runCurrent()
        advanceTimeBy(FILTER_RETRY_DELAY_MS)
        runCurrent()

        job.join()
        assertEquals(2, fetchCalls) // exactly one retry, then give up
        assertEquals(0, delivered.size) // caller's list stays untouched
    }
}
