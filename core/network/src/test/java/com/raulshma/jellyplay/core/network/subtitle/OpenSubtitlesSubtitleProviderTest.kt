package com.raulshma.jellyplay.core.network.subtitle

import com.raulshma.jellyplay.core.datastore.SubtitleProviderPreferencesStore
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderCredentials
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind
import com.raulshma.jellyplay.core.model.subtitle.SubtitleQuery
import com.raulshma.jellyplay.core.network.api.ApiException
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Verifies [OpenSubtitlesSubtitleProvider] request shaping + response/error
 * handling. Mirrors [WyzieSubtitleProviderTest] using MockWebServer.
 *
 * Auth model: a compiled-in shared app `Api-Key` is sent on every request, and
 * search/download trigger a mandatory JWT `/login` with the configured
 * username/password (no API-key field on the user side). Each request test
 * therefore enqueues a `/login` response ahead of the target response.
 */
class OpenSubtitlesSubtitleProviderTest {

    private lateinit var server: MockWebServer
    private lateinit var store: SubtitleProviderPreferencesStore
    private lateinit var provider: OpenSubtitlesSubtitleProvider

    private val creds = SubtitleProviderCredentials.OpenSubtitles(
        username = "tester",
        password = "hunter2",
    )

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        // The store backs the JWT cache (read-modify-write on login). A relaxed
        // mock returns the in-progress creds (no cached token) so login runs.
        store = mockk(relaxed = true)
        coEvery { store.getCredentials(SubtitleProviderKind.OPENSUBTITLES) } returns creds
        provider = OpenSubtitlesSubtitleProvider(OkHttpClient(), store)
        provider.setBaseUrlForTest(server.url("/").toString().trimEnd('/'))
    }

    @After
    fun teardown() {
        server.shutdown()
    }

    /** Enqueues a successful `/login` (returns a fake JWT) for the mandatory auth step. */
    private fun enqueueLoginOk() {
        // A 3-segment JWT with an `exp` ~1h in the future. Body is base64url;
        // the provider decodes the middle segment to read `exp`.
        val exp = (System.currentTimeMillis() / 1000) + 3600
        val payload = """{"exp":$exp}"""
        val payloadB64 = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(payload.toByteArray())
        val token = "header.$payloadB64.signature"
        server.enqueue(MockResponse().setBody("""{"token":"$token"}"""))
    }

    @Test
    fun `tmdb id and language sent as query params, embedded api key as header`() = runBlocking {
        enqueueLoginOk()
        server.enqueue(MockResponse().setBody("""{"data":[]}"""))

        provider.search(SubtitleQuery(tmdbId = 11, languages = listOf("eng")), creds)
        // First request = login, second = search.
        val loginRequest = server.takeRequest()
        assertEquals("POST", loginRequest.method)
        assertTrue("login path", loginRequest.path!!.startsWith("/api/v1/login"))
        assertEquals(OpenSubtitlesSubtitleProvider.APP_API_KEY, loginRequest.getHeader("Api-Key"))
        val searchRequest = server.takeRequest()
        assertEquals("GET", searchRequest.method)
        assertTrue("tmdb_id=11", searchRequest.path!!.contains("tmdb_id=11"))
        assertTrue("languages=eng", searchRequest.path!!.contains("languages=eng"))
        assertEquals(OpenSubtitlesSubtitleProvider.APP_API_KEY, searchRequest.getHeader("Api-Key"))
        assertEquals("JellyPlay", searchRequest.getHeader("User-Agent"))
        assertEquals("application/json", searchRequest.getHeader("Accept"))
        assertTrue("bearer on search", searchRequest.getHeader("Authorization")!!.startsWith("Bearer "))
    }

    @Test
    fun `imdb id strips tt prefix`() = runBlocking {
        enqueueLoginOk()
        server.enqueue(MockResponse().setBody("""{"data":[]}"""))

        provider.search(SubtitleQuery(imdbId = "tt3659388", languages = listOf("eng")), creds)
        server.takeRequest() // login
        val request = server.takeRequest()
        assertTrue("bare imdb id", request.path!!.contains("imdb_id=3659388"))
        assertTrue("no tt", !request.path!!.contains("tt3659388"))
    }

    @Test
    fun `search response parsed into results`() = runBlocking {
        enqueueLoginOk()
        val body = """
            {"data":[{
              "id":"12345",
              "type":"subtitle",
              "attributes":{
                "subtitle_id":"abc",
                "language":"en",
                "release":"Movie.2023.1080p.BluRay",
                "file_id":987654,
                "file_name":"Movie.2023.1080p.BluRay.srt",
                "download_count":42,
                "ratings":7.5,
                "hearing_impaired":"0"
              }
            }]}
        """.trimIndent()
        server.enqueue(MockResponse().setBody(body))

        val result = provider.search(SubtitleQuery(tmdbId = 11, languages = listOf("eng")), creds)
        assertTrue(result.isSuccess)
        val rows = result.getOrThrow()
        assertEquals(1, rows.size)
        val row = rows.first()
        assertEquals("987654", row.id)
        assertEquals("eng", row.language)
        assertEquals("srt", row.format)
        assertEquals(42, row.downloadCount)
    }

    /**
     * Regression: OpenSubtitles can answer HTTP 200 with a plain-text body
     * (`Invalid API key`) instead of JSON. Previously, `isLenient` parsed the
     * leading bareword `Invalid` (7 chars) as a string value and then threw
     * "Unexpected JSON token at offset 7: Expected EOF after parsing...", which
     * leaked to the settings Test chip verbatim. Now it surfaces a clean,
     * non-retryable ApiException.
     */
    @Test
    fun `non-JSON plain-text 200 body yields a friendly non-retryable ApiException, not a raw JSON error`() =
        runBlocking {
            enqueueLoginOk()
            server.enqueue(MockResponse().setResponseCode(200).setBody("Invalid API key"))

            val result = provider.search(SubtitleQuery(tmdbId = 11, languages = listOf("eng")), creds)
            assertTrue(result.isFailure)
            val ex = result.exceptionOrNull() as? ApiException
            assertNotNull(ex)
            assertEquals(false, ex?.isRetryable)
            val msg = ex?.message.orEmpty()
            assertTrue("friendly message: $msg", msg.contains("unexpected response", ignoreCase = true))
            assertTrue("no raw kotlinx text leaked: $msg", !msg.contains("offset"))
        }

    /**
     * Regression: some ISPs (e.g. Airtel in India) inject an HTML court-order
     * block page in place of `api.opensubtitles.com`, answering 200 with an
     * `<iframe>`/`<meta>` interstitial. The user must NOT be told to "verify
     * your API key" — the real cause is the network block. Surfaced message
     * points at ISP/region blocking instead.
     */
    @Test
    fun `HTML block page body reports a network block, not an API-key problem`() =
        runBlocking {
            enqueueLoginOk()
            val body = """<meta name="viewport" content="width=device-width,initial-scale=1.0"/>""" +
                """<iframe src="https://www.airtel.in/court-orders/" width="100%" height="100%"></iframe>"""
            server.enqueue(MockResponse().setResponseCode(200).setBody(body))

            val result = provider.search(SubtitleQuery(tmdbId = 11, languages = listOf("eng")), creds)
            assertTrue(result.isFailure)
            val ex = result.exceptionOrNull() as? ApiException
            assertNotNull(ex)
            assertEquals(false, ex?.isRetryable)
            val msg = ex?.message.orEmpty()
            assertTrue("network-block message: $msg", msg.contains("unreachable", ignoreCase = true))
            assertTrue("points at ISP/region: $msg", msg.contains("blocked", ignoreCase = true))
            assertTrue("no API-key misdirection: $msg", !msg.contains("API key", ignoreCase = true))
        }

    @Test
    fun `400 response maps to a non-retryable ApiException`() = runBlocking {
        enqueueLoginOk()
        server.enqueue(MockResponse().setResponseCode(400).setBody("Bad Request"))

        val result = provider.search(SubtitleQuery(tmdbId = 11, languages = listOf("eng")), creds)
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull() as? ApiException
        assertNotNull(ex)
        assertEquals(false, ex?.isRetryable)
        assertEquals(400, ex?.httpCode)
    }

    @Test
    fun `missing username or password yields failure without a request`() = runBlocking {
        val result = provider.search(
            SubtitleQuery(tmdbId = 11, languages = listOf("eng")),
            SubtitleProviderCredentials.OpenSubtitles(username = "", password = ""),
        )
        assertTrue(result.isFailure)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `login failure surfaces a non-retryable ApiException`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"message":"Invalid credentials"}"""))

        val result = provider.search(SubtitleQuery(tmdbId = 11, languages = listOf("eng")), creds)
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull() as? ApiException
        assertNotNull(ex)
        assertEquals(false, ex?.isRetryable)
        assertEquals(401, ex?.httpCode)
        // Only the login call should have fired — search never ran.
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `malformed JSON body maps to a friendly ApiException rather than a raw decode error`() =
        runBlocking {
            enqueueLoginOk()
            // Bodies that are not valid JSON (e.g. trailing garbage, or the
            // plain-text "Invalid API key" error) must surface a clean message,
            // never the raw "Unexpected JSON token at offset N" kotlinx text.
            server.enqueue(MockResponse().setBody("""{"data":[]}<garbage>"""))

            val result = provider.search(SubtitleQuery(tmdbId = 11, languages = listOf("eng")), creds)
            assertTrue(result.isFailure)
            val ex = result.exceptionOrNull() as? ApiException
            assertNotNull(ex)
            assertEquals(false, ex?.isRetryable)
            assertTrue(
                "friendly message: ${ex?.message}",
                ex?.message?.contains("unexpected response", ignoreCase = true) == true,
            )
        }

    // region TV-episode client-side filter (issue #121)
    // OpenSubtitles' imdb_id + season_number/episode_number server-side match is
    // loose and can return sibling-episode subs. Each row echoes its true feature
    // via attributes.feature_details; the provider filters client-side and keeps
    // only rows matching the requested episode, falling back to the full list when
    // no row matches so the sheet is never empty.

    private fun episodeRow(fileId: Long, season: Int?, episode: Int?, release: String): String {
        val feature = if (season != null || episode != null) {
            ""","feature_details":{${season?.let { "\"season_number\":$it" } ?: ""}${if (season != null && episode != null) "," else ""}${episode?.let { "\"episode_number\":$it" } ?: ""}}"""
        } else ""
        return """
            {"id":"$fileId","type":"subtitle","attributes":{
              "language":"en","release":"$release","file_id":$fileId,
              "file_name":"$release.srt","download_count":10,"hearing_impaired":"0"
              $feature
            }}
        """.trimIndent()
    }

    private fun searchBody(vararg rows: String): String =
        """{"data":[${rows.joinToString(",")}]}"""

    @Test
    fun `TV episode query keeps only rows matching the requested episode`() = runBlocking {
        enqueueLoginOk()
        server.enqueue(
            MockResponse().setBody(
                searchBody(
                    episodeRow(1, season = 1, episode = 1, release = "Show.S01E01"),
                    episodeRow(2, season = 1, episode = 2, release = "Show.S01E02"),
                    episodeRow(3, season = 1, episode = 3, release = "Show.S01E03"),
                ),
            ),
        )

        val result = provider.search(
            SubtitleQuery(tmdbId = 11, season = 1, episode = 1, languages = listOf("eng")),
            creds,
        )
        assertTrue(result.isSuccess)
        val rows = result.getOrThrow()
        assertEquals(1, rows.size)
        assertEquals("1", rows.first().id)
        assertEquals(1, rows.first().season)
        assertEquals(1, rows.first().episode)
    }

    @Test
    fun `TV episode filter falls back to all rows when none match`() = runBlocking {
        enqueueLoginOk()
        // Server returned only other episodes (no row for S01E01) plus a row
        // with no episode metadata at all.
        server.enqueue(
            MockResponse().setBody(
                searchBody(
                    episodeRow(2, season = 1, episode = 2, release = "Show.S01E02"),
                    episodeRow(3, season = null, episode = null, release = "Show.season.pack"),
                ),
            ),
        )

        val result = provider.search(
            SubtitleQuery(tmdbId = 11, season = 1, episode = 1, languages = listOf("eng")),
            creds,
        )
        assertTrue(result.isSuccess)
        val rows = result.getOrThrow()
        // Fallback: both rows returned so the sheet stays usable.
        assertEquals(2, rows.size)
    }

    @Test
    fun `TV episode filter keeps rows with no metadata when nothing else matches`() = runBlocking {
        enqueueLoginOk()
        server.enqueue(
            MockResponse().setBody(
                searchBody(
                    episodeRow(5, season = null, episode = null, release = "Show.unknown.ep"),
                ),
            ),
        )

        val result = provider.search(
            SubtitleQuery(tmdbId = 11, season = 1, episode = 1, languages = listOf("eng")),
            creds,
        )
        assertTrue(result.isSuccess)
        // Sparse metadata → fall back rather than show an empty list.
        assertEquals(1, result.getOrThrow().size)
    }

    @Test
    fun `movie query never filters by episode`() = runBlocking {
        enqueueLoginOk()
        // No season/episode on the query → all rows returned regardless of their
        // own feature_details.
        server.enqueue(
            MockResponse().setBody(
                searchBody(
                    episodeRow(1, season = null, episode = null, release = "Movie.2023"),
                    episodeRow(2, season = 1, episode = 1, release = "Accidental.Ep.Meta"),
                ),
            ),
        )

        val result = provider.search(SubtitleQuery(tmdbId = 11, languages = listOf("eng")), creds)
        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrThrow().size)
    }
    // endregion
}
