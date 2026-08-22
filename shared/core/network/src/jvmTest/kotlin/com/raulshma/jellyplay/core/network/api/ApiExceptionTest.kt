package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.network.RetryPolicy
import kotlinx.coroutines.CancellationException
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.exception.TimeoutException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApiExceptionTest {

    @Test
    fun `classifyJellyfin marks SocketTimeoutException as retryable`() {
        val ex = ApiException.fromJellyfin(SocketTimeoutException("timeout"))
        assertTrue(ex.isRetryable)
        assertNull(ex.httpCode)
        assertNotNull(ex.message)
        assertNotNull(ex.cause)
    }

    @Test
    fun `classifyJellyfin marks ConnectException as retryable`() {
        val ex = ApiException.fromJellyfin(ConnectException("refused"))
        assertTrue(ex.isRetryable)
    }

    @Test
    fun `classifyJellyfin marks UnknownHostException as retryable`() {
        val ex = ApiException.fromJellyfin(UnknownHostException("dns fail"))
        assertTrue(ex.isRetryable)
    }

    @Test
    fun `classifyJellyfin marks generic IOException as retryable`() {
        val ex = ApiException.fromJellyfin(IOException("reset"))
        assertTrue(ex.isRetryable)
    }

    @Test
    fun `classifyJellyfin marks Jellyfin TimeoutException as retryable`() {
        val ex = ApiException.fromJellyfin(TimeoutException("slow"))
        assertTrue(ex.isRetryable)
    }

    @Test
    fun `classifyJellyfin marks HTTP 503 InvalidStatusException as retryable with code`() {
        val ex = ApiException.fromJellyfin(InvalidStatusException(503, null))
        assertTrue(ex.isRetryable)
        assertEquals(503, ex.httpCode)
    }

    @Test
    fun `classifyJellyfin marks HTTP 429 InvalidStatusException as retryable with code`() {
        val ex = ApiException.fromJellyfin(InvalidStatusException(429, null))
        assertTrue(ex.isRetryable)
        assertEquals(429, ex.httpCode)
    }

    @Test
    fun `classifyJellyfin marks HTTP 500 InvalidStatusException as retryable with code`() {
        val ex = ApiException.fromJellyfin(InvalidStatusException(500, null))
        assertTrue(ex.isRetryable)
        assertEquals(500, ex.httpCode)
    }

    @Test
    fun `classifyJellyfin marks HTTP 401 InvalidStatusException as not retryable`() {
        val ex = ApiException.fromJellyfin(InvalidStatusException(401, null))
        assertFalse(ex.isRetryable)
        assertEquals(401, ex.httpCode)
    }

    @Test
    fun `classifyJellyfin marks HTTP 404 InvalidStatusException as not retryable`() {
        val ex = ApiException.fromJellyfin(InvalidStatusException(404, null))
        assertFalse(ex.isRetryable)
        assertEquals(404, ex.httpCode)
    }

    @Test
    fun `classifyJellyfin marks non-network programming error as not retryable`() {
        val ex = ApiException.fromJellyfin(IllegalStateException("bug"))
        assertFalse(ex.isRetryable)
        assertNull(ex.httpCode)
    }

    @Test
    fun `classifyJellyfin preserves cause for downstream inspection`() {
        val cause = SocketTimeoutException("timeout")
        val ex = ApiException.fromJellyfin(cause)
        assertEquals(cause, ex.cause)
    }

    @Test
    fun `classifyJellyfin produces non-blank user-facing message`() {
        val ex = ApiException.fromJellyfin(InvalidStatusException(503, null))
        assertTrue(!ex.message.isNullOrBlank(), "Message must not be blank for UI display")
    }

    @Test
    fun `fromSeerrNetwork preserves retryability of underlying IOException`() {
        val ex = ApiException.fromSeerrNetwork(SocketTimeoutException("timeout"), "friendly")
        assertTrue(ex.isRetryable)
        assertEquals("friendly", ex.message)
    }

    @Test
    fun `fromSeerrNetwork marks non-network throwable as not retryable`() {
        val ex = ApiException.fromSeerrNetwork(IllegalStateException("bug"), "friendly")
        assertFalse(ex.isRetryable)
    }

    @Test
    fun `fromSeerrHttp classifies retryable codes`() {
        RetryPolicy.RETRYABLE_STATUS_CODES.forEach { code ->
            val ex = ApiException.fromSeerrHttp(code, "HTTP $code")
            assertTrue(ex.isRetryable, "Expected HTTP $code to be retryable")
            assertEquals(code, ex.httpCode)
        }
    }

    @Test
    fun `fromSeerrHttp classifies non-retryable codes`() {
        listOf(400, 401, 403, 404, 409, 422).forEach { code ->
            val ex = ApiException.fromSeerrHttp(code, "HTTP $code")
            assertFalse(ex.isRetryable, "Expected HTTP $code to NOT be retryable")
            assertEquals(code, ex.httpCode)
        }
    }

    @Test
    fun `ApiException message is preserved for UI consumers`() {
        val ex = ApiException.fromSeerrHttp(503, "HTTP 503: Service Unavailable")
        assertEquals("HTTP 503: Service Unavailable", ex.message)
    }

    // --- fromHttpResponse + Retry-After parsing (subtitle-provider rate limits) ---

    @Test
    fun `parseRetryAfterMs converts delta-seconds to millis`() {
        assertNull(ApiException.parseRetryAfterMs(null))
        assertNull(ApiException.parseRetryAfterMs(""))
        assertEquals(30_000L, ApiException.parseRetryAfterMs("30"))
        assertEquals(1_000L, ApiException.parseRetryAfterMs("1"))
    }

    @Test
    fun `parseRetryAfterMs rejects non-numeric and non-positive values`() {
        assertNull(ApiException.parseRetryAfterMs("Wed, 21 Oct 2015 07:28:00 GMT")) // HTTP-date form
        assertNull(ApiException.parseRetryAfterMs("not-a-number"))
        assertNull(ApiException.parseRetryAfterMs("0"))
        assertNull(ApiException.parseRetryAfterMs("-5"))
    }

    @Test
    fun `fromHttpResponse captures Retry-After on 429`() {
        val ex = ApiException.fromHttpResponse(429, "rate limited", "30")
        assertTrue(ex.isRetryable)
        assertEquals(429, ex.httpCode)
        assertEquals(30_000L, ex.retryAfterMs)
    }

    @Test
    fun `fromHttpResponse leaves retryAfterMs null when no header`() {
        val ex = ApiException.fromHttpResponse(503, "down", null)
        assertTrue(ex.isRetryable)
        assertNull(ex.retryAfterMs)
    }

    @Test
    fun `fromHttpResponse classifies non-retryable codes`() {
        val ex = ApiException.fromHttpResponse(404, "not found", null)
        assertFalse(ex.isRetryable)
        assertNull(ex.retryAfterMs)
    }
}
