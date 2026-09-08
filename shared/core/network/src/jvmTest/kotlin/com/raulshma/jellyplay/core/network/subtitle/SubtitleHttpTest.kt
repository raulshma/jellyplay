package com.raulshma.jellyplay.core.network.subtitle

import com.raulshma.jellyplay.core.network.api.ApiException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Pins the shared [SubtitleHttp] chassis arms the provider suites exercise only
 * end-to-end: the `wrapNetwork` friendly ladder (UnknownHost / SocketTimeout /
 * the declared OpenSubtitles-only serialization reword / Wyzie's secret
 * redaction / the null-message generic), the ApiException passthrough and
 * CancellationException rethrow guards, and `execute`'s HTTP-status mapping
 * (the "`<service> HTTP <code>`" message, Retry-After parsing, retryability,
 * and the response-body capture only Wyzie enables).
 *
 * The providers pin their own wiring of these arms (see
 * [OpenSubtitlesSubtitleProviderTest] / [WyzieSubtitleProviderTest]); this
 * suite owns the ladder itself, so a chassis edit cannot silently drift from
 * either provider's error strings.
 */
class SubtitleHttpTest {

    private lateinit var server: MockWebServer
    private lateinit var http: SubtitleHttp

    @BeforeTest
    fun setup() {
        server = MockWebServer()
        server.start()
        http = SubtitleHttp(OkHttpClient())
    }

    @AfterTest
    fun teardown() {
        server.shutdown()
    }

    private fun request(): Request = Request.Builder().url(server.url("/")).build()

    private fun plainOpts(): SubtitleHttp.Options = SubtitleHttp.Options(logTag = "SubtitleHttpTest")

    @Test
    fun `wrapNetwork friendly ladder table`() {
        // (throwable, serviceName, opts, expected friendly message)
        val table = listOf(
            UnknownHostException("no dns") to "Unable to reach TestService. Check your connection.",
            SocketTimeoutException("slow") to "TestService request timed out.",
            IllegalStateException("boom") to "boom",
            IllegalStateException("failed for https://sub.wyzie.io/search?id=tt1&key=SECRET123") to
                "failed for <request url with key redacted>",
        )
        table.forEach { (throwable, expected) ->
            val opts = SubtitleHttp.Options(logTag = "SubtitleHttpTest", redactSecrets = true)
            val mapped = http.wrapNetwork(throwable, "TestService", opts)
            assertEquals(
                expected,
                mapped.message,
                "friendly ladder for ${throwable::class.simpleName} (${throwable.message})",
            )
            // The original throwable survives as the cause for diagnostics.
            assertSame(throwable, mapped.cause)
        }
    }

    @Test
    fun `wrapNetwork without redaction passes the raw message through`() {
        val raw = "failed for https://sub.wyzie.io/search?id=tt1&key=SECRET123"

        val mapped = http.wrapNetwork(IllegalStateException(raw), "TestService", plainOpts())

        assertEquals(raw, mapped.message, "secret redaction is Wyzie's declared flag, not chassis default")
    }

    @Test
    fun `wrapNetwork without a message degrades to the service generic`() {
        val mapped = http.wrapNetwork(IllegalStateException(), "TestService", plainOpts())

        assertEquals("TestService request failed", mapped.message)
    }

    @Test
    fun `wrapNetwork ladder classification follows the throwable type`() {
        // Transient transport failures are retryable, programming errors are not —
        // the chassis delegates that split to ApiException.fromNetwork; pinned here
        // so the ladder never drifts from its retry contract.
        assertTrue(http.wrapNetwork(UnknownHostException("x"), "S", plainOpts()).isRetryable)
        assertTrue(http.wrapNetwork(SocketTimeoutException("x"), "S", plainOpts()).isRetryable)
        assertEquals(false, http.wrapNetwork(IllegalStateException("x"), "S", plainOpts()).isRetryable)
    }

    @Test
    fun `wrapNetwork rewords serialization failures only when the provider declares the arm`() {
        val raw = SerializationException("Unexpected JSON token at offset 7")

        val reworded = http.wrapNetwork(
            raw,
            "TestService",
            SubtitleHttp.Options(logTag = "SubtitleHttpTest", rewordSerializationErrors = true),
        )
        assertEquals(
            "TestService returned an unexpected response. Verify your API key and credentials.",
            reworded.message,
        )

        val passthrough = http.wrapNetwork(raw, "TestService", plainOpts())
        assertEquals(
            "Unexpected JSON token at offset 7",
            passthrough.message,
            "providers without the arm see the raw serialization message",
        )
    }

    @Test
    fun `wrapNetwork passes an ApiException through unchanged and rethrows cancellation`() {
        val api = ApiException(false, message = "already classified")
        assertSame(api, http.wrapNetwork(api, "TestService", plainOpts()))

        assertFailsWith<CancellationException> {
            http.wrapNetwork(CancellationException("cancelled"), "TestService", plainOpts())
        }
    }

    @Test
    fun `non-2xx maps to the service HTTP message with retry-after honored`() {
        server.enqueue(
            MockResponse().setResponseCode(429).setHeader("Retry-After", "30").setBody("rate limited"),
        )

        val ex = assertFailsWith<ApiException> {
            http.executeForString(request(), "TestService", plainOpts())
        }
        assertEquals("TestService HTTP 429", ex.message)
        assertEquals(true, ex.isRetryable)
        assertEquals(429, ex.httpCode)
        assertEquals(30_000L, ex.retryAfterMs, "Retry-After: 30 seconds floors the backoff at 30s")
    }

    @Test
    fun `a plain 4xx maps to a non-retryable ApiException without a captured body by default`() {
        server.enqueue(MockResponse().setResponseCode(400).setBody("Bad Request"))

        val ex = assertFailsWith<ApiException> {
            http.executeForString(request(), "TestService", plainOpts())
        }
        assertEquals("TestService HTTP 400", ex.message)
        assertEquals(false, ex.isRetryable)
        assertEquals(null, ex.responseBody, "body capture is Wyzie's declared flag, not the default")
    }

    @Test
    fun `captureResponseBody attaches the raw error body for empty-match detection`() {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"message":"No subtitles found"}"""))
        val opts = SubtitleHttp.Options(logTag = "SubtitleHttpTest", captureResponseBody = true)

        val ex = assertFailsWith<ApiException> {
            http.executeForString(request(), "TestService", opts)
        }
        assertEquals("TestService HTTP 400", ex.message)
        assertNotNull(ex.responseBody)
        assertTrue("No subtitles found" in ex.responseBody!!)
    }

    @Test
    fun `2xx returns the response body through executeForString`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""[{"id":"w1"}]"""))

        val body = http.executeForString(request(), "TestService", plainOpts())

        assertEquals("""[{"id":"w1"}]""", body)
    }

    @Test
    fun `execute hands the 2xx response to the caller's parser`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("raw bytes"))

        val length = http.execute(request(), "TestService", plainOpts()) { response ->
            response.body?.string()?.length ?: -1
        }

        assertEquals(9, length, "the onResponse lambda owns the body shape, not the chassis")
    }
}
