package com.raulshma.jellyplay.core.network.arr

import com.raulshma.jellyplay.core.model.arr.ArrDownloadStatus
import com.raulshma.jellyplay.core.model.arr.ArrMediaType
import com.raulshma.jellyplay.core.model.arr.ArrServerConfig
import com.raulshma.jellyplay.core.model.arr.ArrServiceKind
import com.raulshma.jellyplay.core.network.api.ApiException
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SonarrApiClientTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var apiClient: SonarrApiClientImpl

    /** The one connection fixture; rebuilt in setup so baseUrl carries the live port. */
    private lateinit var conn: ArrServerConfig

    @BeforeTest
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        apiClient = SonarrApiClientImpl(OkHttpClient())
        conn = ArrServerConfig(
            id = "sonarr-test",
            baseUrl = mockWebServer.url("/").toString().trimEnd('/'),
            apiKey = "k",
            name = "Sonarr Test",
            kind = ArrServiceKind.SONARR,
        )
    }

    @AfterTest
    fun teardown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `getQueue unwraps records envelope and maps to ArrQueueItem`() = runBlocking {
        val json = """
            {
                "records": [
                    {
                        "id": 11,
                        "size": 2000.0,
                        "sizeleft": 1000.0,
                        "status": "downloading",
                        "trackedDownloadStatus": "ok",
                        "protocol": "torrent",
                        "series": { "id": 1, "title": "Test Show", "tvdbId": 789, "monitored": true },
                        "episode": { "id": 2, "title": "Pilot", "airDateUtc": "2026-09-01" }
                    }
                ]
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))

        val result = apiClient.getQueue(conn.copy(apiKey = "key"))
        assertTrue(result.isSuccess)
        val queue = result.getOrThrow()
        assertEquals(1, queue.size)
        val item = queue[0]
        assertEquals(11, item.queueId)
        assertEquals(789, item.tvdbId)
        assertEquals("Test Show - Pilot", item.title)
        assertEquals(ArrDownloadStatus.DOWNLOADING, item.status)
        assertEquals(50, item.percent) // (2000-1000)/2000

        val recorded = mockWebServer.takeRequest()
        assertEquals("key", recorded.getHeader("X-Api-Key"))
        assertTrue(recorded.path!!.startsWith("/api/v3/queue"))
        assertTrue(recorded.path!!.contains("includeSeries=true"))
        assertTrue(recorded.path!!.contains("includeEpisode=true"))
    }

    @Test
    fun `getQueue maps warning tracked status`() = runBlocking {
        val json = """
            { "records": [
                { "id": 1, "size": 10.0, "sizeleft": 0.0, "status": "completed",
                  "trackedDownloadStatus": "warning",
                  "series": { "id": 1, "title": "S", "tvdbId": 1 } }
            ] }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))
        val result = apiClient.getQueue(conn)
        assertTrue(result.isSuccess)
        assertEquals(ArrDownloadStatus.WARNING, result.getOrThrow()[0].status)
    }

    @Test
    fun `getCalendar maps episode to SERIES calendar item with series title`() = runBlocking {
        val json = """
            [
                {
                    "id": 1, "title": "S2E01", "airDateUtc": "2026-09-05", "hasFile": true,
                    "overview": "New season",
                    "series": {
                        "id": 9, "title": "Test Show", "tvdbId": 789, "monitored": true,
                        "images": [{ "coverType": "poster", "remoteUrl": "/show.jpg" }]
                    }
                }
            ]
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))

        val result = apiClient.getCalendar(
            conn,
            "2026-09-01", "2026-09-30",
        )
        assertTrue(result.isSuccess)
        val items = result.getOrThrow()
        assertEquals(1, items.size)
        val item = items[0]
        assertEquals(ArrMediaType.SERIES, item.mediaType)
        assertEquals(789, item.tvdbId)
        assertEquals("Test Show", item.title)
        assertEquals("2026-09-05", item.airDateUtc)
        assertTrue(item.hasFile)
        assertEquals("/show.jpg", item.posterPath)
    }

    @Test
    fun `getHistory unwraps records envelope`() = runBlocking {
        val json = """
            { "records": [
                { "id": 1, "eventType": "grabbed", "date": "2026-07-06T12:00:00Z",
                  "data": { "droppedPath": "/tmp/x.mkv" },
                  "series": { "id": 1, "title": "S", "tvdbId": 5 } }
            ] }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))
        val result = apiClient.getHistory(conn)
        assertTrue(result.isSuccess)
        val records = result.getOrThrow()
        assertEquals(1, records.size)
        assertEquals("grabbed", records[0].eventType)
        assertEquals(5, records[0].tvdbId)
        assertEquals("/tmp/x.mkv", records[0].data["droppedPath"])
    }

    @Test
    fun `getQueue maps 500 to failure`() = runBlocking {
        // 500 is retryable and retry now lives in the request funnel: fill the
        // budget so the mapped failure is the 500 itself, not a queue stall.
        repeat(com.raulshma.jellyplay.core.network.api.HttpExecutor.MAX_RETRIES + 1) {
            mockWebServer.enqueue(MockResponse().setResponseCode(500).setBody("oops"))
        }
        val result = apiClient.getQueue(conn)
        assertTrue(result.isFailure)
    }

    // ── Failure shaping through the shared ArrClientSupport ────────────────
    // Mirror of the RadarrApiClientTest failure suite: same taxonomy routing,
    // but every text must carry the SONARR service name.

    @Test
    fun `unknown host surfaces the Sonarr service text with retryable classification`() = runBlocking {
        val result = apiClient.testConnection(conn.copy(baseUrl = "http://jellyplay-no-such-host.invalid"))
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()!! as ApiException
        assertEquals("Unable to reach Sonarr. Check the URL and your network connection.", error.message)
        assertTrue(error.isRetryable)
    }

    @Test
    fun `connection refusal surfaces the Sonarr connect text`() = runBlocking {
        val result = apiClient.testConnection(conn.copy(baseUrl = "http://127.0.0.1:1"))
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()!! as ApiException
        assertEquals("Could not connect to Sonarr. Ensure the server is running and accessible.", error.message)
        assertTrue(error.isRetryable)
    }

    @Test
    fun `header stall surfaces the Sonarr timeout text`() = runBlocking {
        val timeoutClient = SonarrApiClientImpl(
            OkHttpClient.Builder().readTimeout(500, TimeUnit.MILLISECONDS).build(),
        )
        mockWebServer.enqueue(MockResponse().setBody("{}").setHeadersDelay(3, TimeUnit.SECONDS))
        val result = timeoutClient.testConnection(conn)
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()!! as ApiException
        assertEquals("Connection to Sonarr timed out. The server took too long to respond.", error.message)
        assertTrue(error.isRetryable)
    }

    @Test
    fun `HTTP failure keeps the Sonarr client on the fromHttp taxonomy`() = runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(401).setBody("denied"))
        val result = apiClient.testConnection(conn.copy(apiKey = "bad"))
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()!! as ApiException
        assertEquals(401, error.httpCode)
        assertEquals("HTTP 401: denied", error.message)
        assertFalse(error.isRetryable)
        assertTrue(error.isAccessDenied)
    }
}
