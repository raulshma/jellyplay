package com.raulshma.jellyplay.core.network.seerr

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.SocketTimeoutException

class ResilientSeerrApiClientTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var delegate: SeerrApiClientImpl
    private lateinit var resilientClient: ResilientSeerrApiClient

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        delegate = SeerrApiClientImpl(OkHttpClient())
        resilientClient = ResilientSeerrApiClient(delegate)
    }

    @After
    fun teardown() {
        mockWebServer.shutdown()
    }

    // ── Backoff calculation tests ──

    @Test
    fun `calculateBackoff returns value within expected range`() {
        repeat(100) {
            val delay = resilientClient.calculateBackoff(attempt = 0)
            assertTrue(
                "Delay $delay should be between 0 and ${ResilientSeerrApiClient.BASE_DELAY_MS}",
                delay in 0..ResilientSeerrApiClient.BASE_DELAY_MS
            )
        }
    }

    @Test
    fun `calculateBackoff caps at MAX_DELAY_MS`() {
        repeat(100) {
            val delay = resilientClient.calculateBackoff(attempt = 10)
            assertTrue(
                "Delay $delay should be between 0 and ${ResilientSeerrApiClient.MAX_DELAY_MS}",
                delay in 0..ResilientSeerrApiClient.MAX_DELAY_MS
            )
        }
    }

    // ── isRetryable tests ──

    @Test
    fun `isRetryable returns true for SocketTimeoutException`() {
        assertTrue(resilientClient.isRetryable(SocketTimeoutException("timeout")))
    }

    @Test
    fun `isRetryable returns true for ConnectException`() {
        assertTrue(resilientClient.isRetryable(java.net.ConnectException("refused")))
    }

    @Test
    fun `isRetryable returns true for UnknownHostException`() {
        assertTrue(resilientClient.isRetryable(java.net.UnknownHostException("no DNS")))
    }

    @Test
    fun `isRetryable returns true for IOException`() {
        assertTrue(resilientClient.isRetryable(java.io.IOException("network reset")))
    }

    @Test
    fun `isRetryable returns true for HTTP 429`() {
        assertTrue(resilientClient.isRetryable(Exception("HTTP 429: Too Many Requests")))
    }

    @Test
    fun `isRetryable returns true for HTTP 500`() {
        assertTrue(resilientClient.isRetryable(Exception("HTTP 500: Internal Server Error")))
    }

    @Test
    fun `isRetryable returns true for HTTP 502`() {
        assertTrue(resilientClient.isRetryable(Exception("HTTP 502: Bad Gateway")))
    }

    @Test
    fun `isRetryable returns true for HTTP 503`() {
        assertTrue(resilientClient.isRetryable(Exception("HTTP 503: Service Unavailable")))
    }

    @Test
    fun `isRetryable returns true for HTTP 504`() {
        assertTrue(resilientClient.isRetryable(Exception("HTTP 504: Gateway Timeout")))
    }

    @Test
    fun `isRetryable returns false for HTTP 401`() {
        val result = resilientClient.isRetryable(Exception("HTTP 401: Unauthorized"))
        assertTrue(!result)
    }

    @Test
    fun `isRetryable returns false for HTTP 404`() {
        val result = resilientClient.isRetryable(Exception("HTTP 404: Not Found"))
        assertTrue(!result)
    }

    @Test
    fun `isRetryable returns false for generic exception`() {
        val result = resilientClient.isRetryable(IllegalStateException("bug"))
        assertTrue(!result)
    }

    @Test
    fun `isRetryable returns false for CancellationException`() {
        val result = resilientClient.isRetryable(CancellationException("cancelled"))
        assertTrue(!result)
    }

    // ── Integration tests with MockWebServer ──

    @Test
    fun `retries on HTTP 503 and succeeds on second attempt`() = runBlocking {
        // First attempt: 503, second attempt: 200
        mockWebServer.enqueue(MockResponse().setResponseCode(503).setBody("{\"message\":\"Service Unavailable\"}"))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{\"version\":\"1.0.0\"}"))

        val baseUrl = mockWebServer.url("/").toString()
        val result = resilientClient.testConnection(baseUrl, "apikey")

        assertTrue("Expected success after retry", result.isSuccess)
        assertEquals("1.0.0", result.getOrThrow().version)
        assertEquals(2, mockWebServer.requestCount)
    }

    @Test
    fun `retries on HTTP 500 up to MAX_RETRIES times`() = runBlocking {
        // Enqueue MAX_RETRIES + 1 failures to exhaust retries
        repeat(ResilientSeerrApiClient.MAX_RETRIES) {
            mockWebServer.enqueue(MockResponse().setResponseCode(500).setBody("{\"message\":\"Internal Server Error\"}"))
        }

        val baseUrl = mockWebServer.url("/").toString()
        val result = resilientClient.testConnection(baseUrl, "apikey")

        assertTrue("Expected failure after exhausting retries", result.isFailure)
        assertEquals(ResilientSeerrApiClient.MAX_RETRIES, mockWebServer.requestCount)
    }

    @Test
    fun `does not retry on HTTP 401`() = runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(401).setBody("{\"message\":\"Unauthorized\"}"))

        val baseUrl = mockWebServer.url("/").toString()
        val result = resilientClient.testConnection(baseUrl, "wrongkey")

        assertTrue("Expected failure for 401", result.isFailure)
        assertEquals("Should not retry 401", 1, mockWebServer.requestCount)
    }

    @Test
    fun `succeeds immediately on HTTP 200`() = runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{\"version\":\"2.0.0\"}"))

        val baseUrl = mockWebServer.url("/").toString()
        val result = resilientClient.testConnection(baseUrl, "apikey")

        assertTrue("Expected success", result.isSuccess)
        assertEquals("2.0.0", result.getOrThrow().version)
        assertEquals("Should not retry on success", 1, mockWebServer.requestCount)
    }
}
