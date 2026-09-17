package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.MediaType
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins [TmdbApiClientImpl]'s request shaping + response/error mapping. The
 * TMDB base URL is compiled in, so an OkHttp interceptor retargets every
 * request at the local [MockWebServer] while the real call/execute/parse
 * pipeline runs:
 *  1. `getVideos` GETs `/3/{movie|tv}/{id}/videos?api_key=…` and builds the
 *     YouTube watch URL only for YouTube-hosted videos;
 *  2. `getReviews` uses the same shape for the reviews endpoint;
 *  3. an HTTP status failure maps to a typed [ApiException] carrying the code,
 *     retryable for 5xx and NOT retryable for 4xx;
 *  4. a malformed body maps to the non-retryable TMDB parse error instead of
 *     throwing SerializationException raw.
 */
class TmdbApiClientImplTest {

    private lateinit var server: MockWebServer
    private lateinit var client: TmdbApiClientImpl

    @BeforeTest
    fun setup() {
        server = MockWebServer()
        server.start()
        client = TmdbApiClientImpl(rewritingClient())
    }

    @AfterTest
    fun teardown() {
        server.shutdown()
    }

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
    fun `getVideos shapes the movie videos request and builds YouTube watch URLs`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {"results":[
                  {"key":"abc123","name":"Official Trailer","size":1080,"type":"Trailer","site":"YouTube"}
                ]}
                """.trimIndent(),
            ),
        )

        val result = client.getVideos(tmdbId = 550, mediaType = MediaType.MOVIE)

        assertTrue(result.isSuccess)
        val video = result.getOrThrow().single()
        assertEquals("abc123", video.key)
        assertEquals("https://www.youtube.com/watch?v=abc123", video.url)

        val recorded = server.takeRequest()
        assertEquals("/3/movie/550/videos", recorded.path?.substringBefore("?"))
        assertFalse(recorded.requestUrl?.queryParameter("api_key").isNullOrBlank())
    }

    @Test
    fun `getVideos leaves the URL null for non-YouTube sites`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"results":[{"key":"xyz","name":"Clip","size":720,"type":"Clip","site":"Vimeo"}]}""",
            ),
        )

        val video = client.getVideos(tmdbId = 1, mediaType = MediaType.MOVIE).getOrThrow().single()

        assertNull(video.url, "only YouTube videos get a watch URL")
    }

    @Test
    fun `getReviews uses the tv path for series`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {"results":[
                  {"author":"a","author_details":{"username":"u1"},"content":"Great.",
                   "id":"5f0000000000000000000001","url":"https://themoviedb.org/r/1"}
                ]}
                """.trimIndent(),
            ),
        )

        val result = client.getReviews(tmdbId = 1396, mediaType = MediaType.SERIES)

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow().size)
        assertEquals("/3/tv/1396/reviews", server.takeRequest().path?.substringBefore("?"))
    }

    @Test
    fun `an HTTP 404 maps to a non-retryable typed failure`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404).setBody("{}"))

        val result = client.getVideos(tmdbId = 999999, mediaType = MediaType.MOVIE)

        val error = result.exceptionOrNull() as? ApiException
        assertTrue(error != null, "the failure must be a typed ApiException")
        assertEquals(404, error!!.httpCode)
        assertFalse(error.isRetryable, "404 must not be marked retryable")
    }

    @Test
    fun `an HTTP 503 maps to a retryable typed failure`() = runTest {
        // The 503 is retryable, so the funnel burns its full retry budget
        // before surfacing the typed failure (delays run on virtual time).
        repeat(HttpExecutor.MAX_RETRIES + 1) {
            server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
        }

        val result = client.getReviews(tmdbId = 1, mediaType = MediaType.MOVIE)

        val error = result.exceptionOrNull() as? ApiException
        assertTrue(error != null)
        assertEquals(503, error!!.httpCode)
        assertTrue(error.isRetryable)
        assertEquals(HttpExecutor.MAX_RETRIES + 1, server.requestCount)
    }

    @Test
    fun `a malformed body maps to the non-retryable parse error`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("<html>boom</html>"))

        val result = client.getVideos(tmdbId = 1, mediaType = MediaType.MOVIE)

        val error = result.exceptionOrNull() as? ApiException
        assertTrue(error != null)
        assertTrue(error!!.message.orEmpty().startsWith("TMDB parse error"))
        assertFalse(error.isRetryable)
    }

    // ----- retry funnel (the ResilientTmdbApiClient replacement) -----

    @Test
    fun `retries a retryable 503 and the first success wins`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
        server.enqueue(
            MockResponse().setResponseCode(200).setBody("""{"results":[{"key":"k","site":"YouTube"}]}"""),
        )

        val result = client.getVideos(tmdbId = 1, mediaType = MediaType.MOVIE)

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow().size)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `gives up after MAX_RETRIES extra attempts on persistent 500s`() = runTest {
        repeat(HttpExecutor.MAX_RETRIES + 1) {
            server.enqueue(MockResponse().setResponseCode(500).setBody("{}"))
        }

        val result = client.getReviews(tmdbId = 1, mediaType = MediaType.MOVIE)

        assertTrue(result.isFailure)
        assertEquals(HttpExecutor.MAX_RETRIES + 1, server.requestCount)
    }

    @Test
    fun `does not retry a non-retryable 404`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("{}"))

        client.getVideos(tmdbId = 1, mediaType = MediaType.MOVIE)

        assertEquals(1, server.requestCount, "404 must fail fast")
    }

    @Test
    fun `the HTTP failure carries a parsed Retry-After when the server advises one`() = runTest {
        // Declared chassis delta: TMDB now captures Retry-After so the retry
        // policy can floor its backoff at the server's advice. The 429 is
        // retryable, so the budget burns out before the failure surfaces.
        repeat(HttpExecutor.MAX_RETRIES + 1) {
            server.enqueue(
                MockResponse().setResponseCode(429).setHeader("Retry-After", "30").setBody("{}"),
            )
        }

        val error = client.getVideos(tmdbId = 1, mediaType = MediaType.MOVIE)
            .exceptionOrNull() as? ApiException

        assertTrue(error != null)
        assertEquals(429, error!!.httpCode)
        assertEquals(30_000L, error.retryAfterMs)
    }
}
