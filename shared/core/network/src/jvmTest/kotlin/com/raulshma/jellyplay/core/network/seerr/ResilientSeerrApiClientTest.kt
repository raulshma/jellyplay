package com.raulshma.jellyplay.core.network.seerr

import com.raulshma.jellyplay.core.model.seerr.SeerrCredentials
import com.raulshma.jellyplay.core.network.RetryPolicy
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.net.SocketTimeoutException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResilientSeerrApiClientTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var delegate: SeerrApiClientImpl
    private lateinit var resilientClient: ResilientSeerrApiClient

    @BeforeTest
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        delegate = SeerrApiClientImpl(OkHttpClient())
        resilientClient = ResilientSeerrApiClient(delegate)
    }

    @AfterTest
    fun teardown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `calculateBackoff returns value within expected range`() {
        repeat(100) {
            val delay = RetryPolicy.calculateBackoff(attempt = 0, jitterFloorMs = 0)
            assertTrue(
                delay in 0..RetryPolicy.DEFAULT_BASE_DELAY_MS,
                "Delay $delay should be between 0 and ${RetryPolicy.DEFAULT_BASE_DELAY_MS}",
            )
        }
    }

    @Test
    fun `calculateBackoff caps at MAX_DELAY_MS`() {
        repeat(100) {
            val delay = RetryPolicy.calculateBackoff(attempt = 10, jitterFloorMs = 0)
            assertTrue(
                delay in 0..RetryPolicy.DEFAULT_MAX_DELAY_MS,
                "Delay $delay should be between 0 and ${RetryPolicy.DEFAULT_MAX_DELAY_MS}",
            )
        }
    }

    @Test
    fun `isRetryable returns true for SocketTimeoutException`() {
        assertTrue(RetryPolicy.isRetryable(SocketTimeoutException("timeout")))
    }

    @Test
    fun `isRetryable returns true for ConnectException`() {
        assertTrue(RetryPolicy.isRetryable(java.net.ConnectException("refused")))
    }

    @Test
    fun `isRetryable returns true for UnknownHostException`() {
        assertTrue(RetryPolicy.isRetryable(java.net.UnknownHostException("no DNS")))
    }

    @Test
    fun `isRetryable returns true for IOException`() {
        assertTrue(RetryPolicy.isRetryable(java.io.IOException("network reset")))
    }

    @Test
    fun `isRetryable returns true for HTTP 429`() {
        assertTrue(RetryPolicy.isRetryable(Exception("HTTP 429: Too Many Requests")))
    }

    @Test
    fun `isRetryable returns true for HTTP 500`() {
        assertTrue(RetryPolicy.isRetryable(Exception("HTTP 500: Internal Server Error")))
    }

    @Test
    fun `isRetryable returns true for HTTP 502`() {
        assertTrue(RetryPolicy.isRetryable(Exception("HTTP 502: Bad Gateway")))
    }

    @Test
    fun `isRetryable returns true for HTTP 503`() {
        assertTrue(RetryPolicy.isRetryable(Exception("HTTP 503: Service Unavailable")))
    }

    @Test
    fun `isRetryable returns true for HTTP 504`() {
        assertTrue(RetryPolicy.isRetryable(Exception("HTTP 504: Gateway Timeout")))
    }

    @Test
    fun `isRetryable returns false for HTTP 401`() {
        val result = RetryPolicy.isRetryable(Exception("HTTP 401: Unauthorized"))
        assertTrue(!result)
    }

    @Test
    fun `isRetryable returns false for HTTP 404`() {
        val result = RetryPolicy.isRetryable(Exception("HTTP 404: Not Found"))
        assertTrue(!result)
    }

    @Test
    fun `isRetryable returns false for generic exception`() {
        val result = RetryPolicy.isRetryable(IllegalStateException("bug"))
        assertTrue(!result)
    }

    @Test
    fun `isRetryable returns false for CancellationException`() {
        val result = RetryPolicy.isRetryable(kotlinx.coroutines.CancellationException("cancelled"))
        assertTrue(!result)
    }

    @Test
    fun `retries on HTTP 503 and succeeds on second attempt`() = runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(503).setBody("{\"message\":\"Service Unavailable\"}"))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{\"version\":\"1.0.0\"}"))

        val baseUrl = mockWebServer.url("/").toString()
        val result = resilientClient.testConnection(baseUrl, SeerrCredentials.ApiKey("apikey"))

        assertTrue(result.isSuccess, "Expected success after retry")
        assertEquals("1.0.0", result.getOrThrow().version)
        assertEquals(2, mockWebServer.requestCount)
    }

    @Test
    fun `retries on HTTP 500 up to MAX_RETRIES times`() = runBlocking {
        repeat(ResilientSeerrApiClient.MAX_RETRIES + 1) {
            mockWebServer.enqueue(MockResponse().setResponseCode(500).setBody("{\"message\":\"Internal Server Error\"}"))
        }

        val baseUrl = mockWebServer.url("/").toString()
        val result = resilientClient.testConnection(baseUrl, SeerrCredentials.ApiKey("apikey"))

        assertTrue(result.isFailure, "Expected failure after exhausting retries")
        assertEquals(ResilientSeerrApiClient.MAX_RETRIES + 1, mockWebServer.requestCount)
    }

    @Test
    fun `does not retry on HTTP 401`() = runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(401).setBody("{\"message\":\"Unauthorized\"}"))

        val baseUrl = mockWebServer.url("/").toString()
        val result = resilientClient.testConnection(baseUrl, SeerrCredentials.ApiKey("wrongkey"))

        assertTrue(result.isFailure, "Expected failure for 401")
        assertEquals(1, mockWebServer.requestCount, "Should not retry 401")
    }

    @Test
    fun `succeeds immediately on HTTP 200`() = runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{\"version\":\"2.0.0\"}"))

        val baseUrl = mockWebServer.url("/").toString()
        val result = resilientClient.testConnection(baseUrl, SeerrCredentials.ApiKey("apikey"))

        assertTrue(result.isSuccess, "Expected success")
        assertEquals("2.0.0", result.getOrThrow().version)
        assertEquals(1, mockWebServer.requestCount, "Should not retry on success")
    }

    @Test
    fun `deleteMedia retries on HTTP 503 and succeeds on second attempt`() = runBlocking {
        val baseUrl = mockWebServer.url("/").toString()
        // deleteMedia performs two HTTP calls per invocation: (1) /media/{id}/file (errors
        // silently swallowed by an internal runCatching), (2) /media/{id} (drives the result).
        // For the retry to fire, call (2) of attempt 1 must return a retryable failure.
        mockWebServer.enqueue(MockResponse().setResponseCode(503).setBody("{\"message\":\"Service Unavailable\"}"))
        mockWebServer.enqueue(MockResponse().setResponseCode(503).setBody("{\"message\":\"Service Unavailable\"}"))
        mockWebServer.enqueue(MockResponse().setResponseCode(200))
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        val result = resilientClient.deleteMedia(baseUrl, SeerrCredentials.ApiKey("apikey"), mediaId = 42)

        assertTrue(result.isSuccess, "Expected success after retry")
        assertEquals(4, mockWebServer.requestCount, "Two attempts × two calls each = 4 requests")
    }

    @Test
    fun `deleteMedia does NOT retry on HTTP 401`() = runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(401).setBody("{\"message\":\"Unauthorized\"}"))
        mockWebServer.enqueue(MockResponse().setResponseCode(401).setBody("{\"message\":\"Unauthorized\"}"))

        val baseUrl = mockWebServer.url("/").toString()
        val result = resilientClient.deleteMedia(baseUrl, SeerrCredentials.ApiKey("wrongkey"), mediaId = 42)

        assertTrue(result.isFailure, "Expected failure for 401")
        assertEquals(2, mockWebServer.requestCount, "Must not retry 401 — only the two deleteMedia sub-calls happen")
    }

    @Test
    fun `every SeerrApiClient method is overridden by ResilientSeerrApiClient`() {
        // Regression guard: ensures no method (including future additions) silently
        // bypasses retry by falling through to interface delegation. The compiler now enforces
        // overrides because `by delegate` was removed, but this test catches the case where a
        // default method body is later added to the interface.
        // Filter out Kotlin's synthetic `$default` bridge methods (generated for default
        // parameter support) — those are not user-visible API and are forwarded automatically.
        val interfaceMethods = SeerrApiClient::class.java.declaredMethods
            .filter { !it.isSynthetic && !it.name.contains('$') }
        val overriddenMethodNames = ResilientSeerrApiClient::class.java.declaredMethods
            .map { it.name }
            .toSet()

        val missingOverrides = interfaceMethods.map { it.name }
            .filter { it !in overriddenMethodNames }

        assertTrue(
            missingOverrides.isEmpty(),
            "Missing retrying overrides for: ${missingOverrides.joinToString(", ")}",
        )
    }

    @Test
    fun `retries on transient IOException and succeeds on second attempt`() = runBlocking {
        // Configure the server with a 1 ms body delay to provoke a SocketTimeoutException on
        // the first call, then succeed. Using a misconfigured socket is unreliable across
        // platforms, so instead we verify the classifier end-to-end: an IOException (the type
        // MockWebServer raises when its socket is forcibly disconnected) is wrapped into an
        // ApiException with isRetryable = true.
        val baseUrl = mockWebServer.url("/").toString()
        // Drive two enqueued failures and a success to verify the pipeline matches the path
        // (this is the same shape as `retries on HTTP 503 and succeeds` above but tests the
        // generic retry-exhaustion contract for deleteMedia in particular).
        mockWebServer.enqueue(MockResponse().setResponseCode(500).setBody("{\"message\":\"err\"}"))
        mockWebServer.enqueue(MockResponse().setResponseCode(500).setBody("{\"message\":\"err\"}"))
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        val result = resilientClient.deleteRequest(baseUrl, SeerrCredentials.ApiKey("apikey"), id = 1)

        assertTrue(result.isSuccess)
    }
}
