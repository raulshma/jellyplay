package com.raulshma.jellyplay.core.network.seerr

import com.raulshma.jellyplay.core.model.seerr.SeerrCredentials
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the retry contract that moved INTO [SeerrApiClientImpl]'s request
 * funnel ([com.raulshma.jellyplay.core.network.api.HttpExecutor] with
 * `retryHttpCalls`) when the `ResilientSeerrApiClient` DI wrapper was folded
 * away — the wrapper's pins, re-hosted on the impl:
 *  1. a retryable failure (HTTP 503) is retried and the first success wins;
 *  2. retry exhaustion stops at [com.raulshma.jellyplay.core.network.api.HttpExecutor.MAX_RETRIES]
 *     extra attempts (the declared family count: 4, matching the deleted
 *     wrapper's `MAX_RETRIES`);
 *  3. a non-retryable failure (HTTP 401) surfaces after exactly ONE request;
 *  4. a success never retries.
 *
 * `deleteMedia` is the one two-call method (file delete, then media delete);
 * the funnel-level retry now re-runs each HTTP call independently, pinned by
 * its own test.
 */
class SeerrApiClientImplTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var client: SeerrApiClientImpl

    @BeforeTest
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        client = SeerrApiClientImpl(OkHttpClient())
    }

    @AfterTest
    fun teardown() {
        mockWebServer.shutdown()
    }

    private fun baseUrl() = mockWebServer.url("/").toString()

    @Test
    fun `retries on HTTP 503 and succeeds on second attempt`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(503).setBody("""{"message":"Service Unavailable"}"""))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"version":"1.0.0"}"""))

        val result = client.testConnection(baseUrl(), SeerrCredentials.ApiKey("apikey"))

        assertTrue(result.isSuccess, "Expected success after retry")
        assertEquals("1.0.0", result.getOrThrow().version)
        assertEquals(2, mockWebServer.requestCount)
    }

    @Test
    fun `retries on HTTP 500 up to MAX_RETRIES times`() = runTest {
        repeat(com.raulshma.jellyplay.core.network.api.HttpExecutor.MAX_RETRIES + 1) {
            mockWebServer.enqueue(MockResponse().setResponseCode(500).setBody("""{"message":"Internal Server Error"}"""))
        }

        val result = client.testConnection(baseUrl(), SeerrCredentials.ApiKey("apikey"))

        assertTrue(result.isFailure, "Expected failure after exhausting retries")
        assertEquals(com.raulshma.jellyplay.core.network.api.HttpExecutor.MAX_RETRIES + 1, mockWebServer.requestCount)
    }

    @Test
    fun `does not retry on HTTP 401`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(401).setBody("""{"message":"Unauthorized"}"""))

        val result = client.testConnection(baseUrl(), SeerrCredentials.ApiKey("wrongkey"))

        assertTrue(result.isFailure, "Expected failure for 401")
        assertEquals(1, mockWebServer.requestCount, "Should not retry 401")
    }

    @Test
    fun `succeeds immediately on HTTP 200`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"version":"2.0.0"}"""))

        val result = client.testConnection(baseUrl(), SeerrCredentials.ApiKey("apikey"))

        assertTrue(result.isSuccess, "Expected success")
        assertEquals("2.0.0", result.getOrThrow().version)
        assertEquals(1, mockWebServer.requestCount, "Should not retry on success")
    }

    @Test
    fun `deleteMedia retries each of its two HTTP calls independently`() = runTest {
        val base = baseUrl()
        // deleteMedia performs two HTTP calls per invocation: (1) /media/{id}/file
        // (errors silently swallowed by an internal runCatching), (2) /media/{id}
        // (drives the result). Retry lives in the funnel now, so the FIRST call
        // retries its own 503 before the second call fires once.
        mockWebServer.enqueue(MockResponse().setResponseCode(503).setBody("""{"message":"Service Unavailable"}"""))
        mockWebServer.enqueue(MockResponse().setResponseCode(200))
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        val result = client.deleteMedia(base, SeerrCredentials.ApiKey("apikey"), mediaId = 42)

        assertTrue(result.isSuccess, "call (1) retries its transient 503; call (2) then succeeds")
        assertEquals(3, mockWebServer.requestCount, "call (1) ×2 (one retry) + call (2) ×1")
    }

    @Test
    fun `deleteMedia does NOT retry on HTTP 401`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(401).setBody("""{"message":"Unauthorized"}"""))
        mockWebServer.enqueue(MockResponse().setResponseCode(401).setBody("""{"message":"Unauthorized"}"""))

        val result = client.deleteMedia(baseUrl(), SeerrCredentials.ApiKey("wrongkey"), mediaId = 42)

        assertTrue(result.isFailure, "Expected failure for 401")
        assertEquals(2, mockWebServer.requestCount, "Must not retry 401 — only the two deleteMedia sub-calls happen")
    }
}
