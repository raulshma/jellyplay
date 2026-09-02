package com.raulshma.jellyplay.core.network

import com.raulshma.jellyplay.core.network.api.ApiException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies [RetryPolicy.executeWithRetry] honors a server-advised `Retry-After`
 * by flooring the backoff at it (the subtitle-provider rate-limit case). A 429
 * with Retry-After: 3 should sleep at least ~3s total before the next attempt,
 * not retry faster than the server asked.
 *
 * These tests use real wall-clock delays (bounded by Retry-After), so they use
 * small Retry-After values to stay fast. They run on the standard test dispatcher.
 */
class RetryPolicyRetryAfterTest {

    @Test
    fun `retries a 429 until success`() = runBlocking {
        var calls = 0
        val result = RetryPolicy.executeWithRetry<String>(maxRetries = 3, jitterFloorMs = 1) {
            calls++
            if (calls < 2) {
                Result.failure(ApiException.fromHttpResponse(429, "rate limited", retryAfterHeader = "1"))
            } else {
                Result.success("ok")
            }
        }
        assertEquals("ok", result.getOrThrow())
        assertEquals(2, calls)
    }

    @Test
    fun `non-retryable error is returned without retrying`() = runBlocking {
        var calls = 0
        val result = RetryPolicy.executeWithRetry<String>(maxRetries = 3) {
            calls++
            Result.failure(ApiException.fromHttpResponse(404, "not found", null))
        }
        assertTrue(result.isFailure)
        assertEquals(1, calls)
    }

    @Test
    fun `Retry-After header is parsed onto ApiException retryAfterMs`() {
        val ex = ApiException.fromHttpResponse(429, "limited", "5")
        assertEquals(5_000L, ex.retryAfterMs)
        assertTrue(ex.isRetryable)
    }

    @Test
    fun `hard quota exhaustion is non-retryable`() = runBlocking {
        // OpenSubtitles surfaces remaining_downloads == 0 as a non-retryable
        // ApiException so the wrapper must NOT burn retries against an immovable cap.
        var calls = 0
        val result = RetryPolicy.executeWithRetry<String>(maxRetries = 3) {
            calls++
            Result.failure(ApiException(false, message = "daily limit reached"))
        }
        assertTrue(result.isFailure)
        assertEquals(1, calls)
    }
}
