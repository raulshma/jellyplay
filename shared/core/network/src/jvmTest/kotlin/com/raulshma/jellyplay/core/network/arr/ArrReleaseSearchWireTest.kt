package com.raulshma.jellyplay.core.network.arr

import com.raulshma.jellyplay.core.model.arr.ArrRelease
import com.raulshma.jellyplay.core.model.arr.ArrServerConfig
import com.raulshma.jellyplay.core.model.arr.ArrServiceKind
import com.raulshma.jellyplay.core.network.seerr.SeerrApiClientImpl
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Pins the interactive release-search wire: the `GET /release` query forms
 * (Radarr `movieId`; Sonarr `episodeId` / `seriesId` + `seasonNumber`), the
 * bare-array decode of both services' row shapes (one shared DTO — Radarr
 * extras vs Sonarr extras per row), the 404-on-cold-cache mapping to
 * [ArrReleaseCacheMiss], the `POST /release` grab bodies (plain + the
 * override arm with the quality prefill and per-service identity fields),
 * and the queue-row mapper's arr-internal id extraction (the release search's
 * keys).
 */
class ArrReleaseSearchWireTest {

    private lateinit var server: MockWebServer
    private lateinit var sonarr: SonarrApiClientImpl
    private lateinit var radarr: RadarrApiClientImpl

    /** The one connection fixture; rebuilt in setup so baseUrl carries the live port. */
    private lateinit var conn: ArrServerConfig

    @BeforeTest
    fun setup() {
        server = MockWebServer()
        server.start()
        sonarr = SonarrApiClientImpl(OkHttpClient())
        radarr = RadarrApiClientImpl(OkHttpClient())
        conn = ArrServerConfig(
            id = "release-test",
            baseUrl = server.url("/").toString(),
            apiKey = "k",
            name = "Release Test",
            kind = ArrServiceKind.RADARR,
        )
    }

    @AfterTest
    fun teardown() {
        server.shutdown()
    }

    private fun enqueueBody(body: String) {
        server.enqueue(MockResponse().setBody(body).setResponseCode(200))
    }

    private fun recordedPath(): String = server.takeRequest().path!!

    // ── GET /release — query forms + decode ─────────────────────────────────

    @Test
    fun `radarr searchReleases queries movieId and decodes the radarr row`() = runTest {
        enqueueBody(
            """[{
                "guid": "rls-1",
                "indexerId": 3,
                "indexer": "Example",
                "title": "Movie.2024.WEBDL-1080p-GROUP",
                "quality": {"quality": {"id": 7, "name": "WEBDL-1080p"}, "revision": {"version": 1}},
                "languages": [{"id": 1, "name": "English"}],
                "size": 2147483648,
                "ageHours": 5.5,
                "seeders": 40,
                "leechers": 2,
                "protocol": "torrent",
                "releaseGroup": "GROUP",
                "customFormats": [{"id": 1, "name": "BR-DISK"}],
                "customFormatScore": 55,
                "approved": true,
                "temporarilyRejected": false,
                "rejections": [],
                "publishDate": "2024-01-01T00:00:00Z",
                "downloadUrl": "https://example/dl",
                "magnetUrl": "magnet:?xt=1",
                "infoUrl": "https://example/info",
                "edition": "Extended",
                "movieTitles": ["Movie (2024)"],
                "someFutureField": {"ignored": true}
            }]""",
        )

        val result = radarr.searchReleases(conn, movieId = 42)

        assertEquals("/api/v3/release?movieId=42", recordedPath())
        val rows = result.getOrThrow()
        assertEquals(1, rows.size)
        val row = rows.first()
        assertEquals("rls-1", row.guid)
        assertEquals(3, row.indexerId)
        assertEquals("Example", row.indexer)
        assertEquals("Movie.2024.WEBDL-1080p-GROUP", row.title)
        assertEquals("WEBDL-1080p", row.quality)
        assertEquals(7, row.qualityId)
        assertEquals(listOf("English"), row.languages)
        assertEquals(2147483648L, row.size)
        assertEquals(5.5, row.ageHours)
        assertEquals(40, row.seeders)
        assertEquals(2, row.leechers)
        assertEquals("torrent", row.protocol)
        assertEquals("GROUP", row.releaseGroup)
        assertEquals(listOf("BR-DISK"), row.customFormats)
        assertEquals(55, row.customFormatScore)
        assertTrue(row.approved)
        assertEquals(emptyList(), row.rejections)
        assertEquals("Extended", row.edition)
        assertEquals(listOf("Movie (2024)"), row.movieTitles)
    }

    @Test
    fun `sonarr searchReleases by episodeId decodes the sonarr row`() = runTest {
        enqueueBody(
            """[{
                "guid": "rls-2",
                "indexerId": 4,
                "indexer": "Example",
                "title": "Show.S02E03.720p",
                "quality": {"quality": {"id": 5, "name": "WEBDL-720p"}},
                "size": 1073741824,
                "seeders": 12,
                "protocol": "torrent",
                "customFormatScore": -10,
                "approved": false,
                "temporarilyRejected": true,
                "rejections": ["Already in queue", "Awaiting upgrade"],
                "fullSeason": true,
                "seasonNumber": 2,
                "episodeNumbers": [3],
                "mappedEpisodeInfo": "S02E03"
            }]""",
        )

        val result = sonarr.searchReleases(conn, episodeId = 7)

        assertEquals("/api/v3/release?episodeId=7", recordedPath())
        val row = result.getOrThrow().single()
        assertEquals("rls-2", row.guid)
        assertEquals("WEBDL-720p", row.quality)
        assertTrue(row.temporarilyRejected)
        assertEquals(listOf("Already in queue", "Awaiting upgrade"), row.rejections)
        assertEquals(-10, row.customFormatScore)
        assertTrue(row.fullSeason)
        assertEquals(2, row.seasonNumber)
        assertEquals(listOf(3), row.episodeNumbers)
        assertEquals("S02E03", row.mappedEpisodeInfo)
        // The Radarr extras stay at their defaults on a Sonarr row.
        assertEquals(emptyList(), row.movieTitles)
    }

    @Test
    fun `sonarr searchReleases by series and season sends both params in order`() = runTest {
        enqueueBody("[]")

        val result = sonarr.searchReleases(conn, seriesId = 5, seasonNumber = 2)

        assertEquals("/api/v3/release?seriesId=5&seasonNumber=2", recordedPath())
        assertEquals(emptyList<ArrRelease>(), result.getOrThrow())
    }

    @Test
    fun `sonarr searchReleases without a complete query form fails before the request`() = runTest {
        val seasonOnly = sonarr.searchReleases(conn, seasonNumber = 2)

        assertTrue(seasonOnly.isFailure)
        assertEquals(0, server.requestCount, "a partial query form must not reach the wire")
    }

    // ── 404 on the cold decision cache → ArrReleaseCacheMiss ────────────────

    @Test
    fun `radarr searchReleases maps the 404 cache miss to the typed error`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setBody("Cached release results are not available, refresh and try again."),
        )

        val result = radarr.searchReleases(conn, movieId = 42)

        val error = assertIs<ArrReleaseCacheMiss>(result.exceptionOrNull())
        assertEquals("Radarr", error.serviceName)
    }

    @Test
    fun `sonarr searchReleases maps the 404 cache miss to the typed error`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setBody("Cached release results are not available, refresh and try again."),
        )

        val result = sonarr.searchReleases(conn, episodeId = 7)

        val error = assertIs<ArrReleaseCacheMiss>(result.exceptionOrNull())
        assertEquals("Sonarr", error.serviceName)
    }

    @Test
    fun `other 4xx search failures pass through untyped`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("bad key"))

        val result = radarr.searchReleases(conn, movieId = 42)

        assertTrue(result.exceptionOrNull() !is ArrReleaseCacheMiss)
    }

    // ── POST /release — grab bodies ─────────────────────────────────────────

    @Test
    fun `radarr plain grab posts only the required identity`() = runTest {
        enqueueBody("""null""")

        val result = radarr.grabRelease(conn, release(guide = "g-1", indexerId = 3))

        assertTrue(result.isSuccess)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v3/release", request.path)
        assertEquals("""{"guid":"g-1","indexerId":3}""", request.body.readUtf8())
    }

    @Test
    fun `radarr override grab posts movieId quality prefill and shouldOverride`() = runTest {
        enqueueBody("""null""")

        val result = radarr.grabRelease(
            conn,
            release(guide = "g-2", indexerId = 3, quality = "WEBDL-1080p", qualityId = 7),
            movieId = 12,
            shouldOverride = true,
        )

        assertTrue(result.isSuccess)
        val request = server.takeRequest()
        assertEquals("/api/v3/release", request.path)
        assertEquals(
            """{"guid":"g-2","indexerId":3,"quality":{"quality":{"name":"WEBDL-1080p","id":7}},"movieId":12,"shouldOverride":true}""",
            request.body.readUtf8(),
        )
    }

    @Test
    fun `sonarr override grab posts series episode identity and shouldOverride`() = runTest {
        enqueueBody("""null""")

        val result = sonarr.grabRelease(
            conn,
            release(guide = "g-3", indexerId = 4, quality = "WEBDL-720p", qualityId = 5),
            seriesId = 9,
            episodeIds = listOf(21),
            shouldOverride = true,
        )

        assertTrue(result.isSuccess)
        val request = server.takeRequest()
        assertEquals("/api/v3/release", request.path)
        assertEquals(
            """{"guid":"g-3","indexerId":4,"quality":{"quality":{"name":"WEBDL-720p","id":5}},"seriesId":9,"episodeIds":[21],"shouldOverride":true}""",
            request.body.readUtf8(),
        )
    }

    @Test
    fun `sonarr plain grab omits the empty identity and override fields`() = runTest {
        enqueueBody("""null""")

        val result = sonarr.grabRelease(conn, release(guide = "g-4", indexerId = 4))

        assertTrue(result.isSuccess)
        assertEquals("""{"guid":"g-4","indexerId":4}""", server.takeRequest().body.readUtf8())
    }

    // ── Grab-body encoding, directly (the override forms' full shape) ───────

    @Test
    fun `grab body encoding drops every unset refinement`() {
        val json = SeerrApiClientImpl.lenientJson
        assertEquals(
            """{"guid":"g","indexerId":1}""",
            json.encodeToString(ArrReleaseGrabBody(guid = "g", indexerId = 1)),
        )
        // The override form keeps only the fields the arm sets: quality prefill,
        // the per-service identity, and the flag itself.
        assertEquals(
            """{"guid":"g","indexerId":1,"quality":{"quality":{"name":"BluRay-2160p","id":18}},"movieId":6,"shouldOverride":true}""",
            json.encodeToString(
                ArrReleaseGrabBody(
                    guid = "g",
                    indexerId = 1,
                    quality = ArrQuality(quality = ArrQualityName("BluRay-2160p", 18)),
                    movieId = 6,
                    shouldOverride = true,
                ),
            ),
        )
    }

    // ── Queue-row mapper id extraction ──────────────────────────────────────

    @Test
    fun `radarr queue mapper extracts the movie id`() {
        val item = RadarrQueueResource(
            id = 1,
            movie = RadarrMovieResource(id = 55, title = "Movie", tmdbId = 900),
        ).toArrQueueItem()

        assertEquals(55, item.arrMovieId)
        assertEquals(null, item.arrSeriesId)
        assertEquals(null, item.arrEpisodeId)
    }

    @Test
    fun `sonarr queue mapper extracts the series and episode ids`() {
        val item = SonarrQueueResource(
            id = 2,
            series = SonarrSeriesResource(id = 9, title = "Show", tvdbId = 700),
            episode = SonarrEpisodeResource(id = 21, title = "E3"),
        ).toArrQueueItem()

        assertEquals(null, item.arrMovieId)
        assertEquals(9, item.arrSeriesId)
        assertEquals(21, item.arrEpisodeId)
    }

    @Test
    fun `queue mappers leave the ids null when the sub-objects are absent`() {
        val radarr = RadarrQueueResource(id = 3).toArrQueueItem()
        val sonarr = SonarrQueueResource(id = 4).toArrQueueItem()

        assertEquals(null, radarr.arrMovieId)
        assertEquals(null, sonarr.arrSeriesId)
        assertEquals(null, sonarr.arrEpisodeId)
        assertEquals(null, sonarr.arrMovieId)
    }

    /** A release fixture with only the grab-relevant fields set. */
    private fun release(
        guide: String,
        indexerId: Int,
        quality: String? = null,
        qualityId: Int? = null,
    ): ArrRelease = ArrRelease(
        guid = guide,
        indexerId = indexerId,
        title = "Release",
        quality = quality,
        qualityId = qualityId,
    )
}
