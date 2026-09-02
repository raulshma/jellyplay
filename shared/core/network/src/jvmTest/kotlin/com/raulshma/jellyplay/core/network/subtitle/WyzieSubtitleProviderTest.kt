package com.raulshma.jellyplay.core.network.subtitle

import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderCredentials
import com.raulshma.jellyplay.core.model.subtitle.SubtitleQuery
import com.raulshma.jellyplay.core.network.api.ApiException
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Verifies [WyzieSubtitleProvider] request shaping + response/error handling.
 *
 * Id resolution is the key behavior under test: IMDb is Wyzie's native key and
 * is preferred when present (a TMDB id forces an internal TMDB→IMDb lookup that
 * can 400), and a non-positive TMDB id (Jellyfin emits "0" for unmatched items)
 * is rejected rather than sent as `id=0` → 400.
 */
class WyzieSubtitleProviderTest {

    private lateinit var server: MockWebServer
    private lateinit var provider: WyzieSubtitleProvider
    private val creds = SubtitleProviderCredentials.Wyzie(apiKey = "testkey")

    @BeforeTest
    fun setup() {
        server = MockWebServer()
        server.start()
        provider = WyzieSubtitleProvider(OkHttpClient())
        provider.setBaseUrlForTest(server.url("/").toString().trimEnd('/'))
    }

    @AfterTest
    fun teardown() {
        server.shutdown()
    }

    @Test
    fun `imdb id preferred over tmdb`() = runBlocking {
        server.enqueue(MockResponse().setBody("[]"))

        provider.search(
            SubtitleQuery(tmdbId = 286217, imdbId = "tt3659388", languages = listOf("eng")),
            creds,
        )
        val request = server.takeRequest()
        assertTrue(request.path!!.contains("id=tt3659388"), "IMDb id preferred over TMDB")
        assertTrue(!request.path!!.contains("286217"), "TMDB id not sent when IMDb present")
        assertTrue(request.path!!.contains("key=testkey"), "key carried as query param")
    }

    @Test
    fun `tmdb id used when imdb absent`() = runBlocking {
        server.enqueue(MockResponse().setBody("[]"))

        provider.search(SubtitleQuery(tmdbId = 286217, languages = listOf("eng")), creds)
        val request = server.takeRequest()
        assertTrue(request.path!!.contains("id=286217"), "id=286217")
    }

    @Test
    fun `non-positive tmdb id is skipped, falls back to imdb`() = runBlocking {
        server.enqueue(MockResponse().setBody("[]"))

        provider.search(SubtitleQuery(tmdbId = 0, imdbId = "tt3659388"), creds)
        val request = server.takeRequest()
        assertTrue(request.path!!.contains("id=tt3659388"), "zero tmdb must not be sent")
    }

    @Test
    fun `zero tmdb id with no imdb yields empty success without a request`() = runBlocking {
        val result = provider.search(SubtitleQuery(tmdbId = 0), creds)
        assertTrue(result.isSuccess)
        assertEquals(emptyList<Any>(), result.getOrThrow())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `missing api key yields failure without a request`() = runBlocking {
        val result = provider.search(
            SubtitleQuery(imdbId = "tt3659388"),
            SubtitleProviderCredentials.Wyzie(apiKey = ""),
        )
        assertTrue(result.isFailure)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `language converted from iso3 to comma-separated iso1`() = runBlocking {
        server.enqueue(MockResponse().setBody("[]"))

        provider.search(SubtitleQuery(imdbId = "tt3659388", languages = listOf("eng", "spa")), creds)
        val request = server.takeRequest()
        assertTrue(request.path!!.contains("language=en%2Ces"), "language=en,es")
    }

    @Test
    fun `400 response maps to a non-retryable ApiException`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(400).setBody("Bad Request"))

        val result = provider.search(SubtitleQuery(imdbId = "tt3659388"), creds)
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull() as? ApiException
        assertNotNull(ex)
        assertEquals(false, ex?.isRetryable)
        assertEquals(400, ex?.httpCode)
    }

    @Test
    fun `400 No subtitles found maps to empty success, not an error`() = runBlocking {
        // Wyzie misuses HTTP 400 to signal "zero matches" (OpenSubtitles returns 200 []).
        // We map that single case to an empty success so the UI shows no results instead
        // of a misleading error chip.
        val body = """{"code":400,"message":"No subtitles found","details":"No subtitles found for your desired parameters, sorry :("}"""
        server.enqueue(MockResponse().setResponseCode(400).setBody(body))

        val result = provider.search(SubtitleQuery(imdbId = "tt3659388", season = 1, episode = 1), creds)
        assertTrue(result.isSuccess)
        assertEquals(emptyList<Any>(), result.getOrThrow())
    }

    @Test
    fun `search response parsed into results`() = runBlocking {
        val body = """
            [{
              "id": "abc",
              "url": "https://cdn.example.com/sub.srt",
              "format": "srt",
              "display": "English",
              "language": "en",
              "isHearingImpaired": false,
              "release": "Movie.2023.1080p",
              "fileName": "Movie.2023.1080p.srt",
              "downloadCount": 42,
              "ai": false
            }]
        """.trimIndent()
        server.enqueue(MockResponse().setBody(body))

        val result = provider.search(SubtitleQuery(imdbId = "tt3659388"), creds)
        assertTrue(result.isSuccess)
        val rows = result.getOrThrow()
        assertEquals(1, rows.size)
        val row = rows.first()
        assertEquals("abc", row.id)
        assertEquals("https://cdn.example.com/sub.srt", row.downloadUrl)
        assertEquals("srt", row.format)
        assertEquals(42, row.downloadCount)
        // language normalized to ISO 639-3
        assertEquals("eng", row.language)
    }

    // region TV-episode SxxExx marker preference (issue #121)
    // Wyzie carries no echoed episode metadata, so the defensive guard prefers
    // rows whose release/file name carries the requested SxxExx marker, falling
    // back to the full list when no row carries it.

    private fun wyzieRow(id: String, release: String): String = """
        {"id":"$id","url":"https://cdn.example.com/$id.srt","format":"srt",
         "display":"English","language":"en","isHearingImpaired":false,
         "release":"$release","fileName":"$release.srt","downloadCount":10,"ai":false}
    """.trimIndent()

    @Test
    fun `TV episode query prefers rows carrying the SxxExx marker`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                "[${wyzieRow("ep1", "Show.S01E01.1080p")},${wyzieRow("ep2", "Show.S01E02.1080p")}]",
            ),
        )

        val result = provider.search(
            SubtitleQuery(imdbId = "tt3659388", season = 1, episode = 1),
            creds,
        )
        assertTrue(result.isSuccess)
        val rows = result.getOrThrow()
        assertEquals(1, rows.size)
        assertEquals("ep1", rows.first().id)
    }

    @Test
    fun `TV episode query falls back to all rows when none carry the marker`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                "[${wyzieRow("a", "Show.season.one.pack")},${wyzieRow("b", "Show.LQ")}]",
            ),
        )

        val result = provider.search(
            SubtitleQuery(imdbId = "tt3659388", season = 1, episode = 1),
            creds,
        )
        assertTrue(result.isSuccess)
        // No marker anywhere → keep the full server response.
        assertEquals(2, result.getOrThrow().size)
    }

    @Test
    fun `movie query does not apply the SxxExx preference`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                "[${wyzieRow("m1", "Movie.2023")},${wyzieRow("m2", "Other.2023.S01E01")}]",
            ),
        )

        val result = provider.search(SubtitleQuery(imdbId = "tt3659388"), creds)
        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrThrow().size)
    }
    // endregion
}
