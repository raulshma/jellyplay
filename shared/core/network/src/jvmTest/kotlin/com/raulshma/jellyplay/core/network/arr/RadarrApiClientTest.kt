package com.raulshma.jellyplay.core.network.arr

import com.raulshma.jellyplay.core.model.arr.ArrCommandName
import com.raulshma.jellyplay.core.model.arr.ArrDownloadStatus
import com.raulshma.jellyplay.core.model.arr.ArrQueueDeleteOptions
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RadarrApiClientTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var apiClient: RadarrApiClientImpl

    @BeforeTest
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        apiClient = RadarrApiClientImpl(OkHttpClient())
    }

    @AfterTest
    fun teardown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `getQueue sends X-Api-Key header and maps records`() = runBlocking {
        // Radarr v3 (like Sonarr) wraps the queue page in a { records, page,
        // pageSize, totalRecords } envelope. The pagination fields are ignored.
        val json = """
            {
                "page": 1, "pageSize": 10, "totalRecords": 1,
                "records": [
                    {
                        "id": 7,
                        "size": 1000.0,
                        "sizeleft": 400.0,
                        "timeleft": "00:01:23",
                        "status": "downloading",
                        "trackedDownloadStatus": "ok",
                        "trackedDownloadState": "downloading",
                        "protocol": "usenet",
                        "movie": {
                            "id": 1,
                            "title": "Test Movie",
                            "tmdbId": 123,
                            "monitored": true,
                            "hasFile": false
                        }
                    }
                ]
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))

        val baseUrl = mockWebServer.url("/").toString().trimEnd('/')
        val result = apiClient.getQueue(baseUrl, "secret-key")

        assertTrue(result.isSuccess)
        val queue = result.getOrThrow()
        assertEquals(1, queue.size)
        val item = queue[0]
        assertEquals(7, item.queueId)
        assertEquals(123, item.tmdbId)
        assertEquals("Test Movie", item.title)
        assertEquals(ArrDownloadStatus.DOWNLOADING, item.status)
        assertEquals(60, item.percent) // (1000-400)/1000 = 60%
        assertEquals(1000L, item.sizeBytes)
        assertEquals(400L, item.sizeLeft)

        // Assert the request carried the API key header + hit /api/v3/queue.
        val recorded = mockWebServer.takeRequest()
        assertEquals("secret-key", recorded.getHeader("X-Api-Key"))
        assertTrue(recorded.path!!.startsWith("/api/v3/queue"))
        assertTrue(recorded.path!!.contains("includeMovie=true"))
    }

    @Test
    fun `getQueue maps imported state to IMPORTED status`() = runBlocking {
        val json = """
            { "records": [
                {
                    "id": 1, "size": 100.0, "sizeleft": 0.0, "status": "completed",
                    "trackedDownloadStatus": "ok", "trackedDownloadState": "imported",
                    "movie": { "id": 1, "title": "Done", "tmdbId": 5 }
                }
            ] }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))

        val result = apiClient.getQueue(mockWebServer.url("/").toString().trimEnd('/'), "k")
        assertTrue(result.isSuccess)
        assertEquals(ArrDownloadStatus.IMPORTED, result.getOrThrow()[0].status)
    }

    @Test
    fun `getQueue handles empty records envelope`() = runBlocking {
        mockWebServer.enqueue(MockResponse().setBody("""{ "records": [] }""").setResponseCode(200))
        val result = apiClient.getQueue(mockWebServer.url("/").toString().trimEnd('/'), "k")
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }

    @Test
    fun `getQueue fails on bare array (regression guard for envelope unwrap)`() = runBlocking {
        // Radarr never returns a bare array; if a future change reverts the
        // envelope unwrap, decoding this must fail rather than silently parse.
        mockWebServer.enqueue(MockResponse().setBody("[]").setResponseCode(200))
        val result = apiClient.getQueue(mockWebServer.url("/").toString().trimEnd('/'), "k")
        assertTrue(result.isFailure, "bare array must not decode as the envelope")
    }

    @Test
    fun `getQueue maps 401 to failure`() = runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(401).setBody("Unauthorized"))
        val result = apiClient.getQueue(mockWebServer.url("/").toString().trimEnd('/'), "bad-key")
        assertTrue(result.isFailure)
    }

    @Test
    fun `getCalendar builds start and end query params`() = runBlocking {
        val json = """
            [
                {
                    "id": 1, "title": "Future Movie", "tmdbId": 99, "hasFile": false,
                    "digitalRelease": "2026-08-15", "monitored": true,
                    "images": [{ "coverType": "poster", "remoteUrl": "/poster.jpg" }]
                }
            ]
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))

        val result = apiClient.getCalendar(
            mockWebServer.url("/").toString().trimEnd('/'), "k",
            "2026-07-01", "2026-09-01",
        )
        assertTrue(result.isSuccess)
        val items = result.getOrThrow()
        assertEquals(1, items.size)
        assertEquals(99, items[0].tmdbId)
        assertEquals("2026-08-15", items[0].airDateUtc)
        assertEquals("/poster.jpg", items[0].posterPath)

        val recorded = mockWebServer.takeRequest()
        assertTrue(recorded.path!!.startsWith("/api/v3/calendar"))
        assertTrue(recorded.path!!.contains("start=2026-07-01"))
        assertTrue(recorded.path!!.contains("end=2026-09-01"))
    }

    @Test
    fun `testConnection hits system-status and succeeds on 2xx`() = runBlocking {
        mockWebServer.enqueue(MockResponse().setBody("{}").setResponseCode(200))
        val result = apiClient.testConnection(mockWebServer.url("/").toString().trimEnd('/'), "k")
        assertTrue(result.isSuccess)
        val recorded = mockWebServer.takeRequest()
        assertTrue(recorded.path!!.startsWith("/api/v3/system/status"))
    }

    @Test
    fun `testConnection fails on 401`() = runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(401))
        val result = apiClient.testConnection(mockWebServer.url("/").toString().trimEnd('/'), "bad")
        assertTrue(result.isFailure)
    }

    @Test
    fun `deleteQueueItem sends DELETE with options params`() = runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val result = apiClient.deleteQueueItem(
            mockWebServer.url("/").toString().trimEnd('/'), "k", 42,
            ArrQueueDeleteOptions(removeFromClient = true, blocklist = true, skipRedownload = false),
        )
        assertTrue(result.isSuccess)
        val recorded = mockWebServer.takeRequest()
        assertEquals("DELETE", recorded.method)
        assertTrue(recorded.path!!.startsWith("/api/v3/queue/42"))
        assertTrue(recorded.path!!.contains("removeFromClient=true"))
        assertTrue(recorded.path!!.contains("blocklist=true"))
        assertTrue(recorded.path!!.contains("skipRedownload=false"))
    }

    @Test
    fun `deleteQueueItems sends bulk DELETE with ids body`() = runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val result = apiClient.deleteQueueItems(
            mockWebServer.url("/").toString().trimEnd('/'), "k", listOf(1, 2, 3),
        )
        assertTrue(result.isSuccess)
        val recorded = mockWebServer.takeRequest()
        assertEquals("DELETE", recorded.method)
        assertTrue(recorded.path!!.startsWith("/api/v3/queue/bulk"))
    }

    @Test
    fun `deleteQueueItems no-op on empty list`() = runBlocking {
        val result = apiClient.deleteQueueItems(
            mockWebServer.url("/").toString().trimEnd('/'), "k", emptyList(),
        )
        assertTrue(result.isSuccess)
        // No request should have been recorded.
        assertEquals(0, mockWebServer.requestCount)
    }

    @Test
    fun `grabQueueItem POSTs to queue grab`() = runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val result = apiClient.grabQueueItem(mockWebServer.url("/").toString().trimEnd('/'), "k", 5)
        assertTrue(result.isSuccess)
        val recorded = mockWebServer.takeRequest()
        assertEquals("POST", recorded.method)
        assertTrue(recorded.path!!.startsWith("/api/v3/queue/grab/5"))
    }

    @Test
    fun `getBlocklist unwraps records and maps`() = runBlocking {
        val json = """
            { "records": [
                { "id": 1, "date": "2026-07-01", "protocol": "torrent", "indexer": "idx",
                  "message": "bad", "movie": { "id": 1, "title": "M", "tmdbId": 9 } }
            ] }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))
        val result = apiClient.getBlocklist(mockWebServer.url("/").toString().trimEnd('/'), "k")
        assertTrue(result.isSuccess)
        val items = result.getOrThrow()
        assertEquals(1, items.size)
        assertEquals(9, items[0].tmdbId)
        assertEquals("M", items[0].title)
        assertEquals("bad", items[0].message)
    }

    @Test
    fun `deleteBlocklistItem sends DELETE`() = runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(200))
        val result = apiClient.deleteBlocklistItem(mockWebServer.url("/").toString().trimEnd('/'), "k", 7)
        assertTrue(result.isSuccess)
        val recorded = mockWebServer.takeRequest()
        assertEquals("DELETE", recorded.method)
        assertTrue(recorded.path!!.startsWith("/api/v3/blocklist/7"))
    }

    @Test
    fun `postCommand returns queued command`() = runBlocking {
        val json = """
            { "id": 99, "name": "SearchMovie", "status": "queued", "message": null, "queued": "2026-07-06" }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))
        val result = apiClient.postCommand(
            mockWebServer.url("/").toString().trimEnd('/'), "k",
            ArrCommandName.SEARCH_MOVIE, movieIds = listOf(123),
        )
        assertTrue(result.isSuccess)
        val cmd = result.getOrThrow()
        assertEquals(99, cmd.id)
        assertEquals("SearchMovie", cmd.name)
        assertEquals("queued", cmd.status)

        val recorded = mockWebServer.takeRequest()
        assertEquals("POST", recorded.method)
        assertTrue(recorded.path!!.startsWith("/api/v3/command"))
        // The body must carry the SearchMovie serial name (not MoviesSearch).
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("\"name\":\"SearchMovie\""), "expected SearchMovie in body, got: $body")
    }

    @Test
    fun `getQueue parses enriched v3 fields`() = runBlocking {
        val json = """
            { "records": [ { "id": 1, "size": 1000.0, "sizeleft": 0.0, "status": "completed",
                "trackedDownloadStatus": "ok", "trackedDownloadState": "imported",
                "downloadClient": "qbittorrent", "indexer": "idx", "outputPath": "/out",
                "quality": { "quality": { "name": "Bluray-1080p" } },
                "languages": [ { "name": "English" } ],
                "customFormats": [ { "name": "HDR" } ],
                "statusMessages": [ { "title": "warn", "messages": ["slow"] } ],
                "movie": { "id": 1, "title": "X", "tmdbId": 1 } } ] }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))
        val item = apiClient.getQueue(mockWebServer.url("/").toString().trimEnd('/'), "k").getOrThrow()[0]
        assertEquals("qbittorrent", item.downloadClient)
        assertEquals("Bluray-1080p", item.quality)
        assertEquals(listOf("English"), item.languages)
        assertEquals(listOf("HDR"), item.customFormats)
        assertEquals(1, item.messages.size)
        assertEquals("slow", item.messages[0].message)
        assertTrue(item.needsAttention)
    }

    @Test
    fun `findMovieIdByTmdb resolves internal id from tmdbId query`() = runBlocking {
        // GET /api/v3/movie?tmdbId= returns a single-element array (or empty).
        val json = """[ { "id": 4242, "title": "Tracked", "tmdbId": 123 } ]"""
        mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))

        val result = apiClient.findMovieIdByTmdb(mockWebServer.url("/").toString().trimEnd('/'), "k", 123)
        assertTrue(result.isSuccess)
        assertEquals(4242, result.getOrThrow())

        val recorded = mockWebServer.takeRequest()
        assertTrue(recorded.path!!.startsWith("/api/v3/movie"))
        assertTrue(recorded.path!!.contains("tmdbId=123"))
    }

    @Test
    fun `findMovieIdByTmdb returns null when movie is not tracked`() = runBlocking {
        mockWebServer.enqueue(MockResponse().setBody("[]").setResponseCode(200))
        val result = apiClient.findMovieIdByTmdb(mockWebServer.url("/").toString().trimEnd('/'), "k", 999)
        assertTrue(result.isSuccess)
        assertNull(result.getOrThrow())
    }
}
