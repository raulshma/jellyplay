package com.raulshma.jellyplay.core.network

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins [LrcLibApi]'s request shaping + response mapping against a local
 * MockWebServer (the lrclib.net base URL is compiled in, so an OkHttp
 * interceptor rewrites the host/port onto the mock server while the real
 * call/execute/parse pipeline runs):
 *  1. `getBestMatch` hits `/api/get` with URL-encoded artist/track and a
 *     duration appended (truncated to whole seconds) only when supplied;
 *  2. `search` hits `/api/search?q=…` and decodes the track array;
 *  3. `getById` hits `/api/get/{id}`;
 *  4. blank lyric fields collapse to null on the domain model, instrumental
 *     passes through as a native boolean;
 *  5. non-2xx maps to a failed Result (never throws).
 */
class LrcLibApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: LrcLibApi

    @BeforeTest
    fun setup() {
        server = MockWebServer()
        server.start()
        api = LrcLibApi(rewritingClient())
    }

    @AfterTest
    fun teardown() {
        server.shutdown()
    }

    /**
     * LrcLibApi builds absolute lrclib.net URLs; this interceptor retargets
     * every request at the mock server so the real OkHttp + kotlinx-serialization
     * pipeline executes end-to-end.
     */
    private fun rewritingClient(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request()
            val rewritten = request.url.newBuilder()
                .scheme("http")
                .host(server.hostName)
                .port(server.port)
                .build()
            chain.proceed(request.newBuilder().url(rewritten).build())
        }
        .build()

    @Test
    fun `getBestMatch requests api-get with encoded artist, track and duration`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {"id":7,"trackName":"Song","artistName":"Artist","albumName":"Album",
                 "duration":200.5,"instrumental":false,
                 "plainLyrics":"plain words","syncedLyrics":"[00:01.00] words"}
                """.trimIndent(),
            ),
        )

        val result = api.getBestMatch("Artist & Co", "Song: Remix", duration = 200.5)

        assertTrue(result.isSuccess)
        val track = result.getOrThrow()
        assertEquals(7L, track.id)
        assertEquals("Song", track.trackName)
        assertEquals("plain words", track.plainLyrics)
        assertEquals("[00:01.00] words", track.syncedLyrics)

        val recorded = server.takeRequest()
        assertEquals("/api/get", recorded.path?.substringBefore("?"))
        val query = recorded.requestUrl?.queryParameterNames.orEmpty()
        assertEquals("Artist & Co", recorded.requestUrl?.queryParameter("artist_name"))
        assertEquals("Song: Remix", recorded.requestUrl?.queryParameter("track_name"))
        // Duration is sent as whole seconds (Long), not the fractional Double.
        assertEquals("200", recorded.requestUrl?.queryParameter("duration"))
    }

    @Test
    fun `getBestMatch omits the duration parameter when null`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"id":1,"trackName":"T","artistName":"A","albumName":"Al",
                    "duration":0.0,"instrumental":false}""".trimMargin(),
            ),
        )

        api.getBestMatch("A", "T", duration = null)

        val recorded = server.takeRequest()
        assertNull(recorded.requestUrl?.queryParameter("duration"))
    }

    @Test
    fun `blank lyric fields collapse to null on the domain model`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"id":2,"trackName":"T","artistName":"A","albumName":"",
                    "duration":12.0,"instrumental":true,
                    "plainLyrics":"   ","syncedLyrics":""}""",
            ),
        )

        val track = api.getBestMatch("A", "T", duration = 12.0).getOrThrow()

        assertTrue(track.instrumental)
        assertNull(track.plainLyrics, "a blank plainLyrics must read as 'no lyrics'")
        assertNull(track.syncedLyrics, "a blank syncedLyrics must read as 'no lyrics'")
    }

    @Test
    fun `search decodes the track array`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                [{"id":1,"trackName":"One","artistName":"A","albumName":"Al","duration":1.0,
                  "instrumental":false,"plainLyrics":"x","syncedLyrics":"y"},
                 {"id":2,"trackName":"Two","artistName":"B","albumName":"Al","duration":2.0,
                  "instrumental":false}]
                """.trimIndent(),
            ),
        )

        val result = api.search("one two")

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrThrow().size)
        assertEquals("Two", result.getOrThrow()[1].trackName)
        assertNull(result.getOrThrow()[1].plainLyrics, "absent lyric fields default to null")

        val recorded = server.takeRequest()
        assertEquals("/api/search", recorded.path?.substringBefore("?"))
        assertEquals("one two", recorded.requestUrl?.queryParameter("q"))
    }

    @Test
    fun `getById hits api-get with the numeric id path`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"id":42,"trackName":"T","artistName":"A","albumName":"Al",
                    "duration":9.0,"instrumental":false,"plainLyrics":"hi"}""",
            ),
        )

        val result = api.getById(42)

        assertTrue(result.isSuccess)
        assertEquals(42L, result.getOrThrow().id)
        assertEquals("/api/get/42", server.takeRequest().path)
    }

    @Test
    fun `non-2xx response maps to a failed Result instead of throwing`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503).setBody("busy"))

        val result = api.search("anything")

        assertTrue(result.isFailure)
        assertTrue(
            result.exceptionOrNull()?.message?.contains("503") == true,
            "the failure must carry the HTTP status: ${result.exceptionOrNull()}",
        )
    }

    @Test
    fun `malformed JSON body maps to a failed Result`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("<html>not json</html>"))

        val result = api.getById(1)

        assertTrue(result.isFailure)
    }
}
