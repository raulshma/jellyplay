package com.raulshma.jellyplay.core.network.arr

import com.raulshma.jellyplay.core.model.arr.ArrCommandName
import com.raulshma.jellyplay.core.network.api.ApiException
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
 * Pins the [ArrV3Client] engine's per-service request assembly through both
 * public impls: the exact query strings (param ORDER included — the
 * descriptor's [ArrV3Service] include params land where the folded impls
 * added them), the wanted sort keys (`airDateUtc` vs `inCinemas`), the
 * includeSeries/includeMovie identity params, the per-service command bodies,
 * and the service name inside the manualimport 404. Complements the commonTest
 * wire pins ([ArrRequestWireTest]) with the live OkHttp request lines.
 */
class ArrV3ClientWireTest {

    private lateinit var server: MockWebServer
    private lateinit var sonarr: SonarrApiClientImpl
    private lateinit var radarr: RadarrApiClientImpl

    @BeforeTest
    fun setup() {
        server = MockWebServer()
        server.start()
        sonarr = SonarrApiClientImpl(OkHttpClient())
        radarr = RadarrApiClientImpl(OkHttpClient())
    }

    @AfterTest
    fun teardown() {
        server.shutdown()
    }

    private fun enqueueBody(body: String) {
        server.enqueue(MockResponse().setBody(body).setResponseCode(200))
    }

    private fun recordedPath(): String = server.takeRequest().path!!

    // ── GET /queue identity params ──────────────────────────────────────────

    @Test
    fun `sonarr queue attaches includeSeries and includeEpisode in order`() = runTest {
        enqueueBody("""{"records":[]}""")
        sonarr.getQueue(server.url("/").toString(), "k")
        assertEquals("/api/v3/queue?includeSeries=true&includeEpisode=true", recordedPath())
    }

    @Test
    fun `radarr queue attaches includeMovie only`() = runTest {
        enqueueBody("""{"records":[]}""")
        radarr.getQueue(server.url("/").toString(), "k")
        assertEquals("/api/v3/queue?includeMovie=true", recordedPath())
    }

    // ── GET /calendar start/end + identity params ───────────────────────────

    @Test
    fun `sonarr calendar appends includeSeries after start and end`() = runTest {
        enqueueBody("[]")
        sonarr.getCalendar(server.url("/").toString(), "k", "2026-09-01", "2026-09-30")
        assertEquals("/api/v3/calendar?start=2026-09-01&end=2026-09-30&includeSeries=true", recordedPath())
    }

    @Test
    fun `radarr calendar sends only start and end`() = runTest {
        enqueueBody("[]")
        radarr.getCalendar(server.url("/").toString(), "k", "2026-09-01", "2026-09-30")
        assertEquals("/api/v3/calendar?start=2026-09-01&end=2026-09-30", recordedPath())
    }

    // ── GET /history identity param + eventType order ───────────────────────

    @Test
    fun `history identity param precedes eventType on both services`() = runTest {
        enqueueBody("""{"records":[]}""")
        sonarr.getHistory(server.url("/").toString(), "k", eventType = 1)
        assertEquals("/api/v3/history?includeSeries=true&eventType=1", recordedPath())

        enqueueBody("""{"records":[]}""")
        radarr.getHistory(server.url("/").toString(), "k", eventType = 1)
        assertEquals("/api/v3/history?includeMovie=true&eventType=1", recordedPath())
    }

    @Test
    fun `history omits eventType when null`() = runTest {
        enqueueBody("""{"records":[]}""")
        sonarr.getHistory(server.url("/").toString(), "k", eventType = null)
        assertEquals("/api/v3/history?includeSeries=true", recordedPath())
    }

    // ── GET /blocklist sort keys (shared) ───────────────────────────────────

    @Test
    fun `blocklist sorts by date descending on both services`() = runTest {
        enqueueBody("""{"records":[]}""")
        sonarr.getBlocklist(server.url("/").toString(), "k", page = 2, pageSize = 25)
        assertEquals(
            "/api/v3/blocklist?page=2&pageSize=25&sortKey=date&sortDirection=descending",
            recordedPath(),
        )

        enqueueBody("""{"records":[]}""")
        radarr.getBlocklist(server.url("/").toString(), "k", page = 2, pageSize = 25)
        assertEquals(
            "/api/v3/blocklist?page=2&pageSize=25&sortKey=date&sortDirection=descending",
            recordedPath(),
        )
    }

    // ── GET /wanted/missing sort key divergence ─────────────────────────────

    @Test
    fun `sonarr wanted sorts by airDateUtc and attaches includeSeries`() = runTest {
        enqueueBody("""{"records":[]}""")
        sonarr.getWanted(server.url("/").toString(), "k", page = 1, pageSize = 50)
        assertEquals(
            "/api/v3/wanted/missing?page=1&pageSize=50&sortKey=airDateUtc&sortDirection=descending&includeSeries=true",
            recordedPath(),
        )
    }

    @Test
    fun `radarr wanted sorts by inCinemas with no include param`() = runTest {
        enqueueBody("""{"records":[]}""")
        radarr.getWanted(server.url("/").toString(), "k", page = 1, pageSize = 50)
        assertEquals(
            "/api/v3/wanted/missing?page=1&pageSize=50&sortKey=inCinemas&sortDirection=descending",
            recordedPath(),
        )
    }

    // ── POST /command bodies (per-service request field names) ──────────────

    @Test
    fun `sonarr command body carries seriesId episodeIds seasonNumber per variant`() = runTest {
        suspend fun bodyOf(commandName: ArrCommandName, seriesId: Int?, episodeIds: List<Int>?, seasonNumber: Int?): String {
            enqueueBody("""{"id":1,"name":"x","status":"queued"}""")
            sonarr.postCommand(server.url("/").toString(), "k", commandName, seriesId, episodeIds, seasonNumber)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/api/v3/command", request.path)
            return request.body.readUtf8()
        }
        assertEquals("""{"name":"SeriesSearch","seriesId":4}""", bodyOf(ArrCommandName.SEARCH_SERIES, 4, null, null))
        assertEquals("""{"name":"EpisodeSearch","episodeIds":[60,61]}""", bodyOf(ArrCommandName.SEARCH_EPISODES, null, listOf(60, 61), null))
        assertEquals("""{"name":"SeasonSearch","seriesId":4,"seasonNumber":2}""", bodyOf(ArrCommandName.SEASON_SEARCH, 4, null, 2))
    }

    @Test
    fun `radarr command body carries movieIds plus firstOrNull movieId`() = runTest {
        enqueueBody("""{"id":1,"name":"x","status":"queued"}""")
        radarr.postCommand(server.url("/").toString(), "k", ArrCommandName.SEARCH_MOVIES, movieIds = null, episodeIds = null)
        val global = server.takeRequest()
        assertEquals("/api/v3/command", global.path)
        assertEquals("""{"name":"MoviesSearch"}""", global.body.readUtf8(), "null ids omitted")

        enqueueBody("""{"id":1,"name":"x","status":"queued"}""")
        radarr.postCommand(server.url("/").toString(), "k", ArrCommandName.SEARCH_MOVIE, movieIds = listOf(55), episodeIds = null)
        val single = server.takeRequest()
        assertEquals("""{"name":"SearchMovie","movieIds":[55],"movieId":55}""", single.body.readUtf8())
    }

    // ── manualimport 404 service name ───────────────────────────────────────

    @Test
    fun `importQueueItem 404 text carries the service name`() = runTest {
        server.enqueue(MockResponse().setBody("[]").setResponseCode(200))
        val sonarrResult = sonarr.importQueueItem(server.url("/").toString(), "k", "guid-1")
        assertTrue(sonarrResult.isFailure)
        val sonarrError = sonarrResult.exceptionOrNull()!! as ApiException
        assertEquals(404, sonarrError.httpCode)
        assertEquals("No importable files found for this download in Sonarr.", sonarrError.message)

        server.enqueue(MockResponse().setBody("[]").setResponseCode(200))
        val radarrResult = radarr.importQueueItem(server.url("/").toString(), "k", "guid-1")
        assertTrue(radarrResult.isFailure)
        val radarrError = radarrResult.exceptionOrNull()!! as ApiException
        assertEquals("No importable files found for this download in Radarr.", radarrError.message)
    }
}
