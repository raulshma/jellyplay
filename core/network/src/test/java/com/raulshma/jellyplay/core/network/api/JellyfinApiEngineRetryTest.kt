package com.raulshma.jellyplay.core.network.api

import android.content.Context
import com.raulshma.jellyplay.core.network.RetryPolicy
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException

/**
 * Verifies the fix: a transient failure thrown from a Jellyfin SDK call is wrapped into
 * an [ApiException] whose `isRetryable` flag is consulted by [RetryPolicy], so the pipeline
 * actually retries.
 *
 * Before this fix, `JellyfinApiEngine.apiResult` wrapped every throwable into a generic
 * `RuntimeException(JellyfinErrorMapper.map(it), it)` whose type and message failed every
 * retry-classification check, so 100+ call sites of `apiResultWithRetry` never retried.
 */
class JellyfinApiEngineRetryTest {

    private val context: Context = mockk(relaxed = true)
    private val jellyfin: Jellyfin = mockk(relaxed = true)
    private val okHttpClient: OkHttpClient = mockk(relaxed = true)

    private fun newEngine() = JellyfinApiEngine(context, jellyfin, okHttpClient, DeviceProfileProvider(DeviceCodecCapabilities()), com.raulshma.jellyplay.core.network.failover.ServerAddressRouter())

    @Test
    fun `apiResult wraps SocketTimeoutException into retryable ApiException`() = runBlocking {
        val engine = newEngine()
        val result = engine.apiResult<String> { throw SocketTimeoutException("timeout") }

        val ex = result.exceptionOrNull()
        assertTrue("Expected ApiException, got ${ex?.javaClass}", ex is ApiException)
        assertTrue((ex as ApiException).isRetryable)
    }

    @Test
    fun `apiResult wraps InvalidStatusException 503 into retryable ApiException with code`() = runBlocking {
        val engine = newEngine()
        val result = engine.apiResult<String> { throw InvalidStatusException(503, null) }

        val ex = result.exceptionOrNull() as ApiException
        assertTrue(ex.isRetryable)
        assertEquals(503, ex.httpCode)
    }

    @Test
    fun `apiResult wraps InvalidStatusException 401 into non-retryable ApiException`() = runBlocking {
        val engine = newEngine()
        val result = engine.apiResult<String> { throw InvalidStatusException(401, null) }

        val ex = result.exceptionOrNull() as ApiException
        assertFalse(ex.isRetryable)
        assertEquals(401, ex.httpCode)
    }

    @Test
    fun `apiResult preserves friendly message for UI consumers`() = runBlocking {
        val engine = newEngine()
        val result = engine.apiResult<String> { throw SocketTimeoutException("timeout") }

        val message = result.exceptionOrNull()?.message
        assertTrue(
            "Expected friendly message, got '$message'",
            message != null && message.isNotBlank() && !message.contains("SocketTimeoutException")
        )
    }

    @Test
    fun `apiResult never classifies CancellationException as retryable`() = runBlocking {
        val engine = newEngine()
        // Note: Kotlin's runCatching swallows CancellationException (stdlib behaviour).
        // The retry policy must never try to retry it, even wrapped inside ApiException.
        val result = engine.apiResult<String> { throw kotlinx.coroutines.CancellationException("cancel") }
        val ex = result.exceptionOrNull()
        // Whatever the wrapper ends up being, it must not be classified as retryable.
        assertTrue("Result must be a failure", result.isFailure)
        assertFalse(RetryPolicy.isRetryable(ex!!))
    }

    @Test
    fun `apiResultWithRetry retries SocketTimeoutException until success`() = runBlocking {
        val engine = newEngine()
        var attempts = 0

        val result = engine.apiResultWithRetry(maxRetries = 3) {
            attempts++
            if (attempts < 3) throw SocketTimeoutException("transient")
            "ok"
        }

        assertTrue(result.isSuccess)
        assertEquals("ok", result.getOrThrow())
        assertEquals(3, attempts)
    }

    @Test
    fun `apiResultWithRetry does NOT retry on InvalidStatusException 401`() = runBlocking {
        val engine = newEngine()
        var attempts = 0

        val result = engine.apiResultWithRetry(maxRetries = 3) {
            attempts++
            throw InvalidStatusException(401, null)
        }

        assertTrue(result.isFailure)
        assertEquals("Must not retry non-retryable failures", 1, attempts)
        assertTrue(result.exceptionOrNull() is ApiException)
    }

    @Test
    fun `apiResultWithRetry retries InvalidStatusException 503 up to maxRetries then fails`() = runBlocking {
        val engine = newEngine()
        var attempts = 0

        val result = engine.apiResultWithRetry(maxRetries = 2) {
            attempts++
            throw InvalidStatusException(503, null)
        }

        assertTrue(result.isFailure)
        assertEquals("1 initial + 2 retries = 3 attempts", 3, attempts)
        val ex = result.exceptionOrNull()
        assertTrue("Expected ApiException, got ${ex?.javaClass}", ex is ApiException)
        assertTrue((ex as ApiException).isRetryable)
        assertEquals(503, ex.httpCode)
    }
}
