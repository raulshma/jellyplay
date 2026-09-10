package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.network.RetryPolicy
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import java.net.SocketTimeoutException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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

    private val jellyfin: Jellyfin = mockk(relaxed = true)
    private val okHttpClient: OkHttpClient = mockk(relaxed = true)

    private fun newEngine() = JellyfinApiEngine(LazyProvider { jellyfin }, LazyProvider { okHttpClient }, DeviceProfileProvider(DesktopDeviceCodecCapabilities()), com.raulshma.jellyplay.core.network.failover.ServerAddressRouter())

    @Test
    fun `apiResult wraps SocketTimeoutException into retryable ApiException`() = runBlocking {
        val engine = newEngine()
        val result = engine.apiResult<String> { throw SocketTimeoutException("timeout") }

        val ex = result.exceptionOrNull()
        assertTrue(ex is ApiException, "Expected ApiException, got ${ex?.javaClass}")
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
            message != null && message.isNotBlank() && !message.contains("SocketTimeoutException"),
            "Expected friendly message, got '$message'",
        )
    }

    @Test
    fun `apiResult rethrows CancellationException instead of returning a failure Result`() = runBlocking {
        val engine = newEngine()
        // The shared runCatchingRethrowingCancellation helper rethrows CancellationException
        // before the typed ApiException mapping sees it: structured cancellation must
        // never surface as Result.failure (nor, a fortiori, be retried).
        val ex = try {
            engine.apiResult<String> { throw kotlinx.coroutines.CancellationException("cancel") }
            null
        } catch (e: kotlinx.coroutines.CancellationException) {
            e
        }
        assertNotNull(ex, "CancellationException must propagate out of apiResult, not land in a Result")
        assertFalse(RetryPolicy.isRetryable(ex))
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
        assertEquals(1, attempts, "Must not retry non-retryable failures")
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
        assertEquals(3, attempts, "1 initial + 2 retries = 3 attempts")
        val ex = result.exceptionOrNull()
        assertTrue(ex is ApiException, "Expected ApiException, got ${ex?.javaClass}")
        assertTrue((ex as ApiException).isRetryable)
        assertEquals(503, ex.httpCode)
    }
}
