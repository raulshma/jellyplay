package com.raulshma.jellyplay.core.network.subtitle

import com.raulshma.jellyplay.core.datastore.SubtitleProviderPreferencesStore
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderCredentials
import com.raulshma.jellyplay.core.model.subtitle.SubtitleQuery
import com.raulshma.jellyplay.core.network.api.ApiException
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
 * The search path (anonymous, API-key-only) is exercised directly; the login
 * path needs a live [SubtitleProviderPreferencesStore] so it is not covered
 * here.
 */
class OpenSubtitlesSubtitleProviderTest {

    private lateinit var server: MockWebServer
    private lateinit var provider: OpenSubtitlesSubtitleProvider
    private val creds = SubtitleProviderCredentials.OpenSubtitles(apiKey = "testkey")

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        // The store is only touched on the login path; a relaxed mock is fine
        // for the anonymous (API-key-only) creds used in these tests.
        provider = OpenSubtitlesSubtitleProvider(OkHttpClient(), mockk(relaxed = true))
        provider.setBaseUrlForTest(server.url("/").toString().trimEnd('/'))
    }

    @After
    fun teardown() {
        server.shutdown()
    }

    @Test
    fun `tmdb id and language sent as query params, api key as header`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"data":[]}"""))

        provider.search(SubtitleQuery(tmdbId = 11, languages = listOf("eng")), creds)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertTrue("tmdb_id=11", request.path!!.contains("tmdb_id=11"))
        assertTrue("languages=eng", request.path!!.contains("languages=eng"))
        assertEquals("testkey", request.getHeader("Api-Key"))
        assertEquals("JellyPlay", request.getHeader("User-Agent"))
        assertEquals("application/json", request.getHeader("Accept"))
    }

    @Test
    fun `imdb id strips tt prefix`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"data":[]}"""))

        provider.search(SubtitleQuery(imdbId = "tt3659388", languages = listOf("eng")), creds)
        val request = server.takeRequest()
        assertTrue("bare imdb id", request.path!!.contains("imdb_id=3659388"))
        assertTrue("no tt", !request.path!!.contains("tt3659388"))
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
        server.enqueue(MockResponse().setResponseCode(400).setBody("Bad Request"))

        val result = provider.search(SubtitleQuery(tmdbId = 11, languages = listOf("eng")), creds)
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull() as? ApiException
        assertNotNull(ex)
        assertEquals(false, ex?.isRetryable)
        assertEquals(400, ex?.httpCode)
    }

    @Test
    fun `missing api key yields failure without a request`() = runBlocking {
        val result = provider.search(
            SubtitleQuery(tmdbId = 11, languages = listOf("eng")),
            SubtitleProviderCredentials.OpenSubtitles(apiKey = ""),
        )
        assertTrue(result.isFailure)
        assertEquals(0, server.requestCount)
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
            assertEquals(false, ex?.isRetryable)
            assertTrue(
                "friendly message: ${ex?.message}",
                ex?.message?.contains("unexpected response", ignoreCase = true) == true,
            )
        }
}
