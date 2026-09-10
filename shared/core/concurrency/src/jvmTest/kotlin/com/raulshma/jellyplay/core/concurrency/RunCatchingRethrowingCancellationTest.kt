package com.raulshma.jellyplay.core.concurrency

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class RunCatchingRethrowingCancellationTest {

    @Test
    fun `success passes through as a result`() = runTest {
        val result = runCatchingRethrowingCancellation { 42 }
        assertEquals(Result.success(42), result)
    }

    @Test
    fun `ordinary failure lands in the result, not the caller`() = runTest {
        val result = runCatchingRethrowingCancellation<Int> { error("boom") }
        assertEquals("boom", result.exceptionOrNull()?.message)
    }

    @Test
    fun `CancellationException rethrows instead of being captured`() = runTest {
        val probe = async {
            runCatchingRethrowingCancellation<Int> {
                throw CancellationExceptionIsh
            }
        }
        probe.join()
        assertTrue(probe.isCancelled, "a CancellationException inside the block must cancel the caller, not produce a Result")
    }

    private object CancellationExceptionIsh : kotlin.coroutines.cancellation.CancellationException("stop")

    @Test
    fun `cancellation DURING the suspend block propagates - no Result is produced`() = runTest {
        val cancelled = launch {
            runCatchingRethrowingCancellation {
                while (true) delay(1_000)
            }
        }
        runCurrent()
        cancelled.cancelAndJoin()
        assertTrue(cancelled.isCancelled)
    }

    @Test
    fun `non-local return keeps stdlib runCatching ergonomics`() = runTest {
        suspend fun find(): Result<Int> {
            runCatchingRethrowingCancellation<Int> {
                return Result.success(7)
            }
            return Result.success(1)
        }
        assertEquals(Result.success(7), find())
    }
}
