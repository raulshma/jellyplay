package com.raulshma.jellyplay.core.data.worker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins [WorkerRetryVerdict] — the ONE attempt-cap ladder every retrying
 * worker/check reads: attempts inside the budget retry, the last attempt and
 * beyond exhaust, and a non-positive budget is disabled (exhausted at the
 * first attempt). The workers' platform mappings are pinned in their own
 * androidHostTest suites; this suite pins only the shared comparison.
 */
class WorkerRetryVerdictTest {

    @Test
    fun `attempts below the budget retry`() {
        assertEquals(WorkerRetryVerdict.Retry, WorkerRetryVerdict.of(attempt = 0, max = 3))
        assertEquals(WorkerRetryVerdict.Retry, WorkerRetryVerdict.of(attempt = 1, max = 3))
        assertEquals(WorkerRetryVerdict.Retry, WorkerRetryVerdict.of(attempt = 2, max = 3))
    }

    @Test
    fun `the last attempt (attempt equals max) exhausts`() {
        assertEquals(WorkerRetryVerdict.Exhausted, WorkerRetryVerdict.of(attempt = 3, max = 3))
    }

    @Test
    fun `attempts past the budget exhaust`() {
        assertEquals(WorkerRetryVerdict.Exhausted, WorkerRetryVerdict.of(attempt = 4, max = 3))
        assertEquals(WorkerRetryVerdict.Exhausted, WorkerRetryVerdict.of(attempt = 100, max = 3))
    }

    @Test
    fun `the rung boundary sits exactly between budget - 1 and budget`() {
        val budget = 3
        assertEquals(WorkerRetryVerdict.Retry, WorkerRetryVerdict.of(attempt = budget - 1, max = budget))
        assertEquals(WorkerRetryVerdict.Exhausted, WorkerRetryVerdict.of(attempt = budget, max = budget))
    }

    @Test
    fun `a zero budget is disabled - even the first attempt exhausts`() {
        assertEquals(WorkerRetryVerdict.Exhausted, WorkerRetryVerdict.of(attempt = 0, max = 0))
    }

    @Test
    fun `a negative budget is disabled`() {
        assertEquals(WorkerRetryVerdict.Exhausted, WorkerRetryVerdict.of(attempt = 0, max = -1))
    }

    @Test
    fun `a non-positive budget exhausts every attempt`() {
        // The disabled-budget rule is unconditional: a negative attempt against
        // a non-positive cap must not slip past it into Retry.
        assertEquals(WorkerRetryVerdict.Exhausted, WorkerRetryVerdict.of(attempt = -1, max = 0))
        assertEquals(WorkerRetryVerdict.Exhausted, WorkerRetryVerdict.of(attempt = -1, max = -1))
    }

    @Test
    fun `shouldRetry mirrors the verdict arms`() {
        assertTrue(WorkerRetryVerdict.of(attempt = 0, max = 3).shouldRetry)
        assertFalse(WorkerRetryVerdict.of(attempt = 3, max = 3).shouldRetry)
        assertTrue(WorkerRetryVerdict.Retry.shouldRetry)
        assertFalse(WorkerRetryVerdict.Exhausted.shouldRetry)
    }
}
