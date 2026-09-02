package com.raulshma.jellyplay.core.network.arr

import com.raulshma.jellyplay.core.model.arr.ArrDownloadStatus
import com.raulshma.jellyplay.core.model.arr.ArrMediaType
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SonarrApiClientTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var apiClient: SonarrApiClientImpl

    @BeforeTest
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        apiClient = SonarrApiClientImpl(OkHttpClient())
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

        val result = apiClient.getQueue(mockWebServer.url("/").toString().trimEnd('/'), "key")
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
        val result = apiClient.getQueue(mockWebServer.url("/").toString().trimEnd('/'), "k")
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
            mockWebServer.url("/").toString().trimEnd('/'), "k",
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
        val result = apiClient.getHistory(mockWebServer.url("/").toString().trimEnd('/'), "k")
        assertTrue(result.isSuccess)
        val records = result.getOrThrow()
        assertEquals(1, records.size)
        assertEquals("grabbed", records[0].eventType)
        assertEquals(5, records[0].tvdbId)
        assertEquals("/tmp/x.mkv", records[0].data["droppedPath"])
    }

    @Test
    fun `getQueue maps 500 to failure`() = runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(500).setBody("oops"))
        val result = apiClient.getQueue(mockWebServer.url("/").toString().trimEnd('/'), "k")
        assertTrue(result.isFailure)
    }
}
