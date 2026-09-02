package com.raulshma.jellyplay.core.network.subtitle

import com.raulshma.jellyplay.core.datastore.SubtitleProviderPreferencesStore
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderCredentials
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind
import com.raulshma.jellyplay.core.model.subtitle.SubtitleQuery
import com.raulshma.jellyplay.core.model.subtitle.SubtitleSearchResult
import com.raulshma.jellyplay.core.network.api.ApiException
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    @BeforeTest
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

    @AfterTest
    fun teardown() {
        server.shutdown()
    }

    @Test
    fun `tmdb id and language sent as query params, embedded api key as header`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"data":[]}"""))

        provider.search(SubtitleQuery(tmdbId = 11, languages = listOf("eng")), creds)
        // Search needs only Api-Key — it does NOT log in first. With no cached
        // JWT, no Authorization header is sent either.
        assertEquals(1, server.requestCount, "search makes exactly one request, no login")
        val searchRequest = server.takeRequest()
        assertEquals("GET", searchRequest.method)
        assertTrue(searchRequest.path!!.contains("tmdb_id=11"), "tmdb_id=11")
        // OpenSubtitles' languages filter expects ISO 639-1 (2-letter): the internal
        // 639-3 `eng` must be sent as `en`. Sending `eng` returns 0 results live.
        assertTrue(searchRequest.path!!.contains("languages=en&"), "uses 639-1 en: ${searchRequest.path}")
        assertTrue(!searchRequest.path!!.contains("eng"), "never sends 639-2B eng: ${searchRequest.path}")
        assertEquals(OpenSubtitlesSubtitleProvider.APP_API_KEY, searchRequest.getHeader("Api-Key"))
        assertEquals("JellyPlay", searchRequest.getHeader("User-Agent"))
        assertEquals("application/json", searchRequest.getHeader("Accept"))
        assertNull(searchRequest.getHeader("Authorization"), "no Authorization without a cached token")
    }

    @Test
    fun `language is sent as ISO 639-1 not 639-2B`() = runBlocking {
        // Regression: the live `/subtitles?languages=` filter rejects ISO 639-2B
        // (`eng`) with 0 results and accepts ISO 639-1 (`en`). The internal form
        // is 639-3 (`eng`); it must be converted to 639-1 on the way out.
        server.enqueue(MockResponse().setBody("""{"data":[]}"""))
        provider.search(SubtitleQuery(tmdbId = 11, languages = listOf("eng")), creds)
        val path = server.takeRequest().path!!
        assertTrue(path.contains("languages=en&"), "uses 639-1 en: $path")
        assertTrue(!path.contains("eng"), "never sends 639-2B eng: $path")
    }

    @Test
    fun `query params are emitted in canonical alphabetical order to avoid the 301 redirect`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"data":[]}"""))

        provider.search(SubtitleQuery(tmdbId = 11, languages = listOf("eng")), creds)
        val path = server.takeRequest().path!!
        // The gateway 301-redirects non-alphabetical order (e.g. tmdb_id before
        // languages) to the sorted form; emit sorted up front to skip the redirect.
        val languagesAt = path.indexOf("languages=")
        val tmdbAt = path.indexOf("tmdb_id=")
        assertTrue(languagesAt >= 0, "languages present")
        assertTrue(tmdbAt >= 0, "tmdb_id present")
        assertTrue(languagesAt < tmdbAt, "languages must precede tmdb_id: $path")
    }

    @Test
    fun `imdb id strips tt prefix`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"data":[]}"""))

        provider.search(SubtitleQuery(imdbId = "tt3659388", languages = listOf("eng")), creds)
        val request = server.takeRequest()
        assertTrue(request.path!!.contains("imdb_id=3659388"), "bare imdb id")
        assertTrue(!request.path!!.contains("tt3659388"), "no tt")
    }

    @Test
    fun `search response parsed into results`() = runBlocking {
        val body = """
            {"data":[{
              "id":"12345",
              "type":"subtitle",
              "attributes":{
                "subtitle_id":"abc",
                "language":"en",
                "release":"Movie.2023.1080p.BluRay",
                "download_count":42,
                "ratings":7.5,
                "hearing_impaired":false,
                "files":[{"file_id":987654,"file_name":"Movie.2023.1080p.BluRay.srt"}]
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
     * Regression: the real OpenSubtitles API nests `file_id` / `file_name` under
     * `attributes.files[]` (per the Jellyfin plugin's Attributes/SubFile models),
     * NOT as top-level attributes. The old parser read `attributes.file_id`
     * directly, so every row failed the null-guard and search silently returned
     * 0 results in production — even though the mock-based test passed because
     * the mock inlined `file_id` under `attributes`. This test pins the real
     * shape: no `attributes.file_id`, `file_id` only in `files[0]`, boolean
     * `hearing_impaired`, and `feature_details` echoing season/episode.
     */
    @Test
    fun `search response with nested files array parsed correctly`() = runBlocking {
        val body = """
            {"data":[{
              "id":"778899",
              "type":"subtitle",
              "attributes":{
                "language":"eng",
                "release":"Show.S01E02.1080p.WEB",
                "download_count":150,
                "ratings":8.0,
                "hearing_impaired":true,
                "ai_translated":false,
                "feature_details":{"feature_type":"Episode","season_number":1,"episode_number":2},
                "files":[
                  {"file_id":555001,"file_name":"Show.S01E02.WEB-CMRG.srt"},
                  {"file_id":555002,"file_name":"Show.S01E02.WEB-RARBG.srt"}
                ]
              }
            }]}
        """.trimIndent()
        server.enqueue(MockResponse().setBody(body))

        val result = provider.search(
            SubtitleQuery(imdbId = "tt1", season = 1, episode = 2, languages = listOf("eng")),
            creds,
        )
        assertTrue(result.isSuccess)
        val rows = result.getOrThrow()
        assertEquals(1, rows.size)
        val row = rows.first()
        // Uses files[0].file_id, not a (nonexistent) attributes.file_id.
        assertEquals("555001", row.id)
        assertEquals("eng", row.language)
        assertEquals("srt", row.format)
        assertEquals(150, row.downloadCount)
        assertTrue(row.isHearingImpaired, "HI flag lost: API returns a boolean")
    }

    /**
     * Regression: OpenSubtitles can answer HTTP 200 with a plain-text body
     * (`Invalid API key`) instead of JSON. Previously, `isLenient` parsed the
     * leading bareword `Invalid` (7 chars) as a string value and then threw
     * "Unexpected JSON token at offset 7: Expected EOF after parsing...", which
     * leaked to the settings Test chip verbatim. Now it surfaces a clean,
     * retryable ApiException — a 2xx non-JSON body is a transient gateway
     * artifact (a real auth failure returns 401/403, classified elsewhere).
     */
    @Test
    fun `non-JSON plain-text 200 body yields a friendly retryable ApiException, not a raw JSON error`() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(200).setBody("Invalid API key"))

            val result = provider.search(SubtitleQuery(tmdbId = 11, languages = listOf("eng")), creds)
            assertTrue(result.isFailure)
            val ex = result.exceptionOrNull() as? ApiException
            assertNotNull(ex)
            assertEquals(true, ex?.isRetryable)
            val msg = ex?.message.orEmpty()
            assertTrue(msg.contains("unexpected response", ignoreCase = true), "friendly message: $msg")
            assertTrue(!msg.contains("offset"), "no raw kotlinx text leaked: $msg")
        }

    /**
     * Regression: some ISPs (e.g. Airtel in India) inject an HTML court-order
     * block page in place of `api.opensubtitles.com`, answering 200 with an
     * `<iframe>`/`<meta>` interstitial. The user must NOT be told to "verify
     * your API key" — the real cause is the network block. Surfaced message
     * points at ISP/region blocking instead. The injected block is intermittent
     * (a retry usually gets the real JSON), so the ApiException is retryable
     * and the resilient wrapper will retry before surfacing the error.
     */
    @Test
    fun `HTML block page body reports a retryable network block, not an API-key problem`() =
        runBlocking {
            val body = """<meta name="viewport" content="width=device-width,initial-scale=1.0"/>""" +
                """<iframe src="https://www.airtel.in/court-orders/" width="100%" height="100%"></iframe>"""
            server.enqueue(MockResponse().setResponseCode(200).setBody(body))

            val result = provider.search(SubtitleQuery(tmdbId = 11, languages = listOf("eng")), creds)
            assertTrue(result.isFailure)
            val ex = result.exceptionOrNull() as? ApiException
            assertNotNull(ex)
            assertEquals(true, ex?.isRetryable)
            val msg = ex?.message.orEmpty()
            assertTrue(msg.contains("unreachable", ignoreCase = true), "network-block message: $msg")
            assertTrue(msg.contains("blocked", ignoreCase = true), "points at ISP/region: $msg")
            assertTrue(!msg.contains("API key", ignoreCase = true), "no API-key misdirection: $msg")
        }

    @Test
    fun `400 response maps to a non-retryable ApiException`() = runBlocking {
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
    fun `search does not log in and succeeds even when login would fail`() = runBlocking {
        // Search uses the app Api-Key only; it must NOT call /login. So even with
        // a 401 queued (which would fail login), search consumes a 200 JSON body
        // and returns results without ever hitting /login.
        server.enqueue(MockResponse().setBody("""{"data":[]}"""))

        val result = provider.search(SubtitleQuery(tmdbId = 11, languages = listOf("eng")), creds)
        assertTrue(result.isSuccess)
        assertEquals(1, server.requestCount)
        val request = server.takeRequest()
        assertTrue(!request.path!!.startsWith("/api/v1/login"), "no login call from search")
    }

    @Test
    fun `search attaches a cached JWT when one is available`() = runBlocking {
        // A token persisted by a prior download should be reused on search for
        // authenticated rate-limit treatment (best-effort, never forced).
        val exp = (System.currentTimeMillis() / 1000) + 3600
        val payloadB64 = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"exp":$exp}""".toByteArray())
        val token = "header.$payloadB64.signature"
        val withJwt = creds.copy(jwt = token, jwtExpiresAt = exp * 1000)
        coEvery { store.getCredentials(SubtitleProviderKind.OPENSUBTITLES) } returns withJwt
        server.enqueue(MockResponse().setBody("""{"data":[]}"""))

        provider.search(SubtitleQuery(tmdbId = 11, languages = listOf("eng")), withJwt)
        val request = server.takeRequest()
        assertEquals("Bearer $token", request.getHeader("Authorization"))
    }

    @Test
    fun `download surfaces a non-retryable ApiException on login failure`() = runBlocking {
        // Download (unlike search) genuinely requires login. A 401 from /login
        // must surface as a non-retryable ApiException and never reach /download.
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"message":"Invalid credentials"}"""))

        val result = provider.download(
            SubtitleSearchResult(
                provider = SubtitleProviderKind.OPENSUBTITLES,
                id = "123",
                language = "eng",
                displayName = "English",
            ),
            creds,
        )
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull() as? ApiException
        assertNotNull(ex)
        assertEquals(false, ex?.isRetryable)
        assertEquals(401, ex?.httpCode)
        // Only the login call should have fired — download never ran.
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `malformed JSON body maps to a friendly ApiException rather than a raw decode error`() =
        runBlocking {
            // Bodies that are not valid JSON (e.g. trailing garbage, or the
            // plain-text "Invalid API key" error) must surface a clean message,
            // never the raw "Unexpected JSON token at offset N" kotlinx text.
            server.enqueue(MockResponse().setBody("""{"data":[]}<garbage>"""))

            val result = provider.search(SubtitleQuery(tmdbId = 11, languages = listOf("eng")), creds)
            assertTrue(result.isFailure)
            val ex = result.exceptionOrNull() as? ApiException
            assertNotNull(ex)
            assertEquals(true, ex?.isRetryable)
            assertTrue(
                ex?.message?.contains("unexpected response", ignoreCase = true) == true,
                "friendly message: ${ex?.message}",
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
              "language":"en","release":"$release",
              "download_count":10,"hearing_impaired":"0",
              "files":[{"file_id":$fileId,"file_name":"$release.srt"}]
              $feature
            }}
        """.trimIndent()
    }

    private fun searchBody(vararg rows: String): String =
        """{"data":[${rows.joinToString(",")}]}"""

    @Test
    fun `TV episode query keeps only rows matching the requested episode`() = runBlocking {
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

    // region verifyCredentials (Test button path)
    // verifyCredentials performs a real /login with the user's username/password
    // so a wrong password is caught BEFORE saving — search alone can't, since it
    // authenticates only with the shared app key and never logs in. The probe
    // neither reads nor persists the store: it runs against unsaved form text.

    @Test
    fun `verifyCredentials succeeds on a 200 login and discards the token`() = runBlocking {
        // A successful /login → Result.success(Unit). The token is thrown away
        // (no persistence) — only the pass/fail matters for the Test button.
        server.enqueue(
            MockResponse().setBody("""{"token":"jwt-abc","status":200}"""),
        )

        val result = provider.verifyCredentials(creds)

        assertTrue(result.isSuccess)
        assertEquals(1, server.requestCount, "verify makes exactly one /login request")
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertTrue(request.path!!.startsWith("/api/v1/login"), "hits /login: ${request.path}")
        assertEquals(OpenSubtitlesSubtitleProvider.APP_API_KEY, request.getHeader("Api-Key"))
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"username\":\"tester\""), "username in body: $body")
        assertTrue(body.contains("\"password\":\"hunter2\""), "password in body: $body")
    }

    @Test
    fun `verifyCredentials surfaces a non-retryable ApiException on a 401 login`() = runBlocking {
        // Wrong password → OpenSubtitles returns 401 from /login. This must
        // surface as a FAILURE (so the Test chip shows an error), be
        // non-retryable (401 is permanent), and never reach /subtitles.
        server.enqueue(
            MockResponse().setResponseCode(401).setBody("""{"message":"Invalid credentials"}"""),
        )

        val result = provider.verifyCredentials(creds)

        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull() as? ApiException
        assertNotNull(ex)
        assertEquals(false, ex?.isRetryable)
        assertEquals(401, ex?.httpCode)
        assertEquals(1, server.requestCount, "only the login call fired")
        assertTrue(
            !server.takeRequest().path!!.startsWith("/api/v1/subtitles"),
            "never reaches search",
        )
    }

    @Test
    fun `verifyCredentials fails fast without a request when credentials are blank`() = runBlocking {
        // The Test button fail-fast lives in the ViewModel, but the provider
        // guards too: unconfigured creds never hit the network.
        val result = provider.verifyCredentials(
            SubtitleProviderCredentials.OpenSubtitles(username = "", password = ""),
        )

        assertTrue(result.isFailure)
        assertEquals(0, server.requestCount)
    }
    // endregion
}
