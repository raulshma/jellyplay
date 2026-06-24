package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.network.RetryPolicy
import kotlinx.coroutines.CancellationException
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.exception.TimeoutException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

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
        assertTrue("Message must not be blank for UI display", !ex.message.isNullOrBlank())
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
            assertTrue("Expected HTTP $code to be retryable", ex.isRetryable)
            assertEquals(code, ex.httpCode)
        }
    }

    @Test
    fun `fromSeerrHttp classifies non-retryable codes`() {
        listOf(400, 401, 403, 404, 409, 422).forEach { code ->
            val ex = ApiException.fromSeerrHttp(code, "HTTP $code")
            assertFalse("Expected HTTP $code to NOT be retryable", ex.isRetryable)
            assertEquals(code, ex.httpCode)
        }
    }

    @Test
    fun `ApiException message is preserved for UI consumers`() {
        val ex = ApiException.fromSeerrHttp(503, "HTTP 503: Service Unavailable")
        assertEquals("HTTP 503: Service Unavailable", ex.message)
    }
}
