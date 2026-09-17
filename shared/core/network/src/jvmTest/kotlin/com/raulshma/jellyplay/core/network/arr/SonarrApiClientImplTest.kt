package com.raulshma.jellyplay.core.network.arr

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
 * Sonarr mirror of [RadarrApiClientImplTest]: pins the retry contract that
 * moved INTO the shared ARR request funnel
 * ([com.raulshma.jellyplay.core.network.api.HttpExecutor] with
 * `retryHttpCalls`) when the `ResilientSonarrApiClient` DI wrapper was folded
 * away — first success after a retryable 503, exhaustion at
 * [com.raulshma.jellyplay.core.network.api.HttpExecutor.MAX_RETRIES] extra
 * attempts (the declared family count: 4), and fail-fast on a non-retryable
 * 401.
 */
class SonarrApiClientImplTest {

    private lateinit var server: MockWebServer
    private lateinit var client: SonarrApiClientImpl

    @BeforeTest
    fun setup() {
        server = MockWebServer()
        server.start()
        client = SonarrApiClientImpl(OkHttpClient())
    }

    @AfterTest
    fun teardown() {
        server.shutdown()
    }

    @Test
    fun `retries a 503 on the unit funnel and succeeds on second attempt`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503).setBody("down"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"some":"status"}"""))

        val result = client.testConnection(server.url("/").toString(), "key")

        assertTrue(result.isSuccess)
        assertEquals(2, server.requestCount)
        assertEquals("/api/v3/system/status", server.takeRequest().path)
    }

    @Test
    fun `retries a 503 on the parse funnel and succeeds on second attempt`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503).setBody("down"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"records":[]}"""))

        val result = client.getQueue(server.url("/").toString(), "key")

        assertTrue(result.isSuccess)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `exhausts MAX_RETRIES extra attempts before failing`() = runTest {
        repeat(com.raulshma.jellyplay.core.network.api.HttpExecutor.MAX_RETRIES + 1) {
            server.enqueue(MockResponse().setResponseCode(500).setBody("still down"))
        }

        val result = client.getWanted(server.url("/").toString(), "key", page = 1, pageSize = 50)

        assertTrue(result.isFailure)
        assertEquals(com.raulshma.jellyplay.core.network.api.HttpExecutor.MAX_RETRIES + 1, server.requestCount)
    }

    @Test
    fun `does not retry a non-retryable 401`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("bad key"))

        val result = client.getQueue(server.url("/").toString(), "badkey")

        assertTrue(result.isFailure)
        assertEquals(1, server.requestCount, "401 must fail fast")
    }
}
