package com.raulshma.jellyplay.core.network.arr

import com.raulshma.jellyplay.core.model.arr.ArrCommandName
import com.raulshma.jellyplay.core.model.arr.ArrQueueDeleteOptions
import com.raulshma.jellyplay.core.network.seerr.arrSeerrWireJson
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the wasm Radarr/Sonarr request-wire bytes: the POST/PUT/DELETE bodies
 * (which kotlinx must render EXACTLY like the JVM impls' `json.encodeToString`
 * calls through `SeerrApiClientImpl.lenientJson` — encodeDefaults=false means
 * a `false` monitored flag and null ids are OMITTED), the auth header pair,
 * the queue-delete option query pairs, and the `/api/v3` URL join.
 */
class ArrRequestWireTest {

    private val json = arrSeerrWireJson

    // ── auth + URL join ─────────────────────────────────────────────────────

    @Test
    fun `arr auth is the X-Api-Key header`() {
        assertEquals(listOf("X-Api-Key" to "arr-key-1"), arrApiKeyHeaders("arr-key-1"))
    }

    @Test
    fun `arr url join trims the base trailing slash and prefixes the v3 root`() {
        assertEquals("http://radarr:7878/api/v3/queue", arrApiUrl("http://radarr:7878/", "/queue"))
        assertEquals("http://radarr:7878/api/v3/queue", arrApiUrl("http://radarr:7878", "/queue"))
        assertEquals(
            "http://host/radarr/api/v3/movieFile/12",
            arrApiUrl("http://host/radarr/", "/movieFile/12"),
            "reverse-proxy subpath preserved",
        )
    }

    // ── delete options → query pairs ────────────────────────────────────────

    @Test
    fun `queue delete options render booleans in JVM order`() {
        assertEquals(
            listOf(
                "removeFromClient" to "true",
                "blocklist" to "false",
                "skipRedownload" to "false",
            ),
            ArrQueueDeleteOptions().toQueryPairs(),
            "the *arr web-UI defaults: removeFromClient=true, blocklist=false, skipRedownload=false",
        )
        assertEquals(
            listOf(
                "removeFromClient" to "false",
                "blocklist" to "true",
                "skipRedownload" to "true",
            ),
            ArrQueueDeleteOptions(removeFromClient = false, blocklist = true, skipRedownload = true).toQueryPairs(),
        )
    }

    // ── POST/PUT/DELETE bodies ──────────────────────────────────────────────

    @Test
    fun `bulk queue and blocklist delete bodies are bare ids objects`() {
        assertEquals("""{"ids":[1,2,3]}""", json.encodeToString(RadarrQueueBulkRequest(ids = listOf(1, 2, 3))))
        assertEquals("""{"ids":[]}""", json.encodeToString(SonarrIdsBulkRequest(ids = emptyList())))
    }

    @Test
    fun `radarr command body writes name plus movieIds and firstOrNull movieId`() {
        assertEquals(
            """{"name":"MoviesSearch"}""",
            json.encodeToString(RadarrCommandRequest(name = ArrCommandName.SEARCH_MOVIES.serialName, movieIds = null, movieId = null)),
            "null ids omitted — the global search carries no id fields",
        )
        assertEquals(
            """{"name":"SearchMovie","movieIds":[55],"movieId":55}""",
            json.encodeToString(RadarrCommandRequest(name = ArrCommandName.SEARCH_MOVIE.serialName, movieIds = listOf(55), movieId = 55)),
            "movieId = movieIds.firstOrNull() exactly when a single movie is targeted",
        )
    }

    @Test
    fun `radarr monitor body always writes monitored - the JVM DTO has no default to omit`() {
        // Fixture-vs-impl check: RadarrMovieMonitorRequest declares monitored
        // WITHOUT a Kotlin default (verbatim from the JVM impl), so
        // encodeDefaults=false never omits it — the JVM wire carries
        // `"monitored":false` too.
        assertEquals(
            """{"movieIds":[9,10],"monitored":false}""",
            json.encodeToString(RadarrMovieMonitorRequest(movieIds = listOf(9, 10), monitored = false)),
        )
        assertEquals(
            """{"movieIds":[9],"monitored":true}""",
            json.encodeToString(RadarrMovieMonitorRequest(movieIds = listOf(9), monitored = true)),
        )
    }

    @Test
    fun `sonarr command body writes the optional ids in declaration order`() {
        assertEquals(
            """{"name":"SeriesSearch","seriesId":4}""",
            json.encodeToString(SonarrCommandRequest(name = "SeriesSearch", seriesId = 4, episodeIds = null, seasonNumber = null)),
        )
        assertEquals(
            """{"name":"EpisodeSearch","episodeIds":[60,61]}""",
            json.encodeToString(SonarrCommandRequest(name = "EpisodeSearch", seriesId = null, episodeIds = listOf(60, 61), seasonNumber = null)),
        )
        assertEquals(
            """{"name":"SeasonSearch","seriesId":4,"seasonNumber":2}""",
            json.encodeToString(SonarrCommandRequest(name = "SeasonSearch", seriesId = 4, episodeIds = null, seasonNumber = 2)),
        )
    }

    @Test
    fun `sonarr monitor body always writes monitored - the JVM DTO has no default to omit`() {
        assertEquals(
            """{"episodeIds":[60],"monitored":false}""",
            json.encodeToString(SonarrEpisodeMonitorRequest(episodeIds = listOf(60), monitored = false)),
        )
        assertEquals(
            """{"episodeIds":[60,61],"monitored":true}""",
            json.encodeToString(SonarrEpisodeMonitorRequest(episodeIds = listOf(60, 61), monitored = true)),
        )
    }
}
