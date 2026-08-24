package com.raulshma.jellyplay.core.network

import com.raulshma.jellyplay.core.network.api.ApiException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the pure retry math of [RetryPolicy] after its Phase W chunk 1 move
 * to commonMain (backoff windows, ApiException precedence, Retry-After
 * flooring). Runs per target from commonTest; the platform throwable
 * classification (isRetryableNetworkError actuals) stays untested here —
 * jvmTest's RetryPolicyRetryAfterTest keeps covering the JVM side.
 */
class RetryPolicyCommonTest {

    @Test
    fun `backoff stays inside jitter floor to cap for attempt 0`() {
        repeat(50) {
            val backoff = RetryPolicy.calculateBackoff(attempt = 0)
            assertTrue(backoff in 200..1_000, "attempt 0 backoff $backoff outside 200..1000")
        }
    }

    @Test
    fun `backoff is capped at the max delay`() {
        repeat(50) {
            val backoff = RetryPolicy.calculateBackoff(attempt = 10)
            assertTrue(backoff in 200..8_000, "attempt 10 backoff $backoff outside 200..8000")
        }
    }

    @Test
    fun `jitter floor never exceeds the capped delay`() {
        // Floor 5000 against a computed cap of 1000 must clamp to 1000, not
        // crash Random.nextLong(floor > bound).
        val backoff = RetryPolicy.calculateBackoff(attempt = 0, jitterFloorMs = 5_000)
        assertEquals(1_000, backoff, "jitter floor must clamp to the capped delay")
    }

    @Test
    fun `retryable ApiException classification wins over message sniffing`() {
        val quota = ApiException(isRetryable = false, message = "daily limit reached")
        assertFalse(RetryPolicy.isRetryable(quota), "pre-classified non-retryable ApiException")
        val serverError = ApiException.fromHttp(500, "boom")
        assertTrue(RetryPolicy.isRetryable(serverError), "5xx ApiException is retryable")
    }

    @Test
    fun `unknown throwable with retryable status text is retried`() {
        val e = IllegalStateException("Request failed (HTTP 503)")
        assertTrue(RetryPolicy.isRetryable(e), "message-based HTTP 503 classification")
    }

    @Test
    fun `retryable status set is the canonical five`() {
        assertEquals(setOf(429, 500, 502, 503, 504), RetryPolicy.RETRYABLE_STATUS_CODES)
    }
}
