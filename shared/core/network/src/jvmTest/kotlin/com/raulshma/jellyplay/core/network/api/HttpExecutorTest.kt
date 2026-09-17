package com.raulshma.jellyplay.core.network.api

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.io.IOException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the shared OkHttp execute chassis (HttpExecutor.kt) the
 * Arr/Seerr/TMDB/GitHub/LrcLib clients run on: 2xx stream decode, error-body
 * message shaping through [HttpExecutor.Options.parseErrorMessage],
 * network-failure shaping through
 * [HttpExecutor.Options.formatNetworkError], CancellationException
 * passthrough (structured-concurrency requirement), the Unit-request body
 * discard, the raw-text and cookie-capture members, the Retry-After capture
 * flag, and the [HttpExecutor.Options.retryHttpCalls] retry flag that
 * replaced the deleted Resilient* wrappers. The service-specific shapers are
 * caller-supplied lambdas here, so the assertions pin the pipeline's
 * contract, not any one service's wording.
 */
class HttpExecutorTest {

    @Serializable
    private data class TestPayload(val name: String, val count: Int = 0)

    private lateinit var server: MockWebServer

    @BeforeTest
    fun setup() {
        server = MockWebServer()
        server.start()
    }

    @AfterTest
    fun teardown() {
        server.shutdown()
    }

    /** Deterministic stand-ins for the per-service error shapers. */
    private fun executor(
        okHttpClient: OkHttpClient = OkHttpClient(),
        retryHttpCalls: Boolean = false,
        captureRetryAfter: Boolean = false,
        emptyBodyText: String? = null,
    ): HttpExecutor = HttpExecutor(
        okHttpClient = okHttpClient,
        json = Json { ignoreUnknownKeys = true; isLenient = true },
        options = HttpExecutor.Options(
            parseErrorMessage = { code, body -> "HTTP $code: $body" },
            formatNetworkError = { e -> "network: ${e.message}" },
            captureRetryAfter = captureRetryAfter,
            emptyBodyText = emptyBodyText,
            retryHttpCalls = retryHttpCalls,
        ),
    )

    private fun request(): Request = Request.Builder().url(server.url("/thing")).get().build()

    // ----- parseJson -----

    @Test
    fun `2xx body decodes straight from the stream`() = runTest {
        server.enqueue(MockResponse().setBody("""{"name":"ok","count":3,"unknownExtra":true}"""))

        val result = executor().parseJson<TestPayload>(request())

        assertTrue(result.isSuccess)
        assertEquals(TestPayload("ok", 3), result.getOrThrow())
    }

    @Test
    fun `non-2xx shapes the body through parseErrorMessage into ApiException fromHttpResponse`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503).setBody("""{"message":"down"}"""))

        val result = executor().parseJson<TestPayload>(request())

        assertTrue(result.isFailure)
        val ex = assertIs<ApiException>(result.exceptionOrNull())
        assertEquals("""HTTP 503: {"message":"down"}""", ex.message)
        assertEquals(503, ex.httpCode)
        assertTrue(ex.isRetryable, "5xx is retryable via fromHttpResponse")
        assertFalse(ex.isAccessDenied)
    }

    @Test
    fun `401 keeps the access-denied classification`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"message":"no key"}"""))

        val result = executor().parseJson<TestPayload>(request())

        val ex = assertIs<ApiException>(result.exceptionOrNull())
        assertTrue(ex.isAccessDenied)
        assertFalse(ex.isRetryable)
        assertEquals(401, ex.httpCode)
    }

    @Test
    fun `decode failure surfaces the raw exception, not an ApiException`() = runTest {
        // Documented invariant: runCatching around the stream decode keeps
        // serialization bugs distinct from network failures so callers never
        // retry a deterministic decode error.
        server.enqueue(MockResponse().setResponseCode(200).setBody("not json"))

        val result = executor().parseJson<TestPayload>(request())

        assertTrue(result.isFailure)
        assertIs<SerializationException>(result.exceptionOrNull())
    }

    @Test
    fun `empty 2xx body is a decode failure, not a network error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))

        val result = executor().parseJson<TestPayload>(request())

        assertTrue(result.isFailure)
        assertFalse(
            result.exceptionOrNull() is ApiException,
            "an empty stream must not be classified as a network failure",
        )
    }

    @Test
    fun `CancellationException passes through for structured concurrency`() = runTest {
        val canceling = OkHttpClient.Builder()
            .addInterceptor { throw CancellationException("caller cancelled") }
            .build()

        assertFailsWith<CancellationException> {
            executor(canceling).parseJson<TestPayload>(request())
        }
    }

    @Test
    fun `IOException shapes into a retryable network ApiException`() = runTest {
        val failing = OkHttpClient.Builder()
            .addInterceptor { throw IOException("socket closed") }
            .build()

        val result = executor(failing).parseJson<TestPayload>(request())

        assertTrue(result.isFailure)
        val ex = assertIs<ApiException>(result.exceptionOrNull())
        assertEquals("network: socket closed", ex.message)
        assertTrue(ex.isRetryable)
        assertNull(ex.httpCode, "no HTTP response → no status code")
        assertNotNull(ex.cause)
    }

    @Test
    fun `captureRetryAfter parses the header onto the HTTP failure when declared`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(429).setHeader("Retry-After", "30").setBody("limited"),
        )

        val ex = assertIs<ApiException>(
            executor(captureRetryAfter = true).parseJson<TestPayload>(request()).exceptionOrNull(),
        )
        assertEquals(30_000L, ex.retryAfterMs, "declared flag floors the backoff at the server's advice")
        assertEquals(429, ex.httpCode)
    }

    @Test
    fun `Retry-After stays uncaptured unless the family declares the flag`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(429).setHeader("Retry-After", "30").setBody("limited"),
        )

        val ex = assertIs<ApiException>(
            executor().parseJson<TestPayload>(request()).exceptionOrNull(),
        )
        assertNull(ex.retryAfterMs, "the parse families' fromHttp shape never carried the header")
    }

    // ----- parseUnit -----

    @Test
    fun `unit request succeeds without decoding the body`() = runTest {
        // *arr v3 mutations return the full affected resource list; the body
        // must be discarded on the success path, success rides the status
        // code alone.
        server.enqueue(MockResponse().setResponseCode(200).setBody("""["whatever","junk"]"""))

        val result = executor().parseUnit(request())

        assertTrue(result.isSuccess)
        assertEquals(Unit, result.getOrThrow())
    }

    @Test
    fun `unit request failure shapes the error body`() = runTest {
        server.enqueue(MockResponse().setResponseCode(409).setBody("""{"message":"conflict"}"""))

        val result = executor().parseUnit(request())

        assertTrue(result.isFailure)
        val ex = assertIs<ApiException>(result.exceptionOrNull())
        assertEquals("""HTTP 409: {"message":"conflict"}""", ex.message)
        assertEquals(409, ex.httpCode)
        assertFalse(ex.isRetryable)
    }

    @Test
    fun `unit request cancellation passes through`() = runTest {
        val canceling = OkHttpClient.Builder()
            .addInterceptor { throw CancellationException("caller cancelled") }
            .build()

        assertFailsWith<CancellationException> { executor(canceling).parseUnit(request()) }
    }

    // ----- executeForText / executeForCookie / executeForBodyText -----

    @Test
    fun `executeForText returns the raw body on 2xx and shapes errors otherwise`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("plain text"))
        assertEquals("plain text", executor().executeForText(request()).getOrThrow())

        server.enqueue(MockResponse().setResponseCode(502).setBody("bad gateway"))
        val ex = assertIs<ApiException>(executor().executeForText(request()).exceptionOrNull())
        assertEquals("HTTP 502: bad gateway", ex.message)
        assertTrue(ex.isRetryable)
    }

    @Test
    fun `executeForCookie joins Set-Cookie values and collapses a blank join to null`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"ok":true}""")
                .addHeader("Set-Cookie", "session=abc; Path=/; HttpOnly")
                .addHeader("Set-Cookie", "theme=dark; Path=/"),
        )
        val (body, cookie) = executor().executeForCookie(request()).getOrThrow()
        assertEquals("""{"ok":true}""", body)
        assertEquals("session=abc; theme=dark", cookie, "attributes stripped, joined with '; '")

        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))
        val (_, noCookie) = executor().executeForCookie(request()).getOrThrow()
        assertNull(noCookie, "no Set-Cookie header → null cookie side")
    }

    @Test
    fun `the emptyBodyText arm builds the IOException-backed retryable failure`() {
        // A zero-length wire body reads as "" (the decode/parse path), so
        // executeForBodyText's absent-body branch only fires for a literally
        // null Response.body (synthetic responses) — the builder arm itself
        // is pinned directly here.
        val ex = executor(emptyBodyText = "Empty response from Test").emptyBodyNetworkError()

        assertEquals("Empty response from Test", ex.message)
        assertTrue(ex.isRetryable, "the text-shaped empty-body arm is IOException-backed and retryable")
        assertNull(ex.httpCode)
        assertIs<IOException>(ex.cause)
    }

    @Test
    fun `executeForBodyText throws the Options-shaped HTTP failure`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("junk"))

        val ex = assertFailsWith<ApiException> {
            executor().executeForBodyText(request())
        }
        // The test shaper is the parse-family one; the text families supply
        // body-ignoring texts (see the Tmdb/GitHub/LrcLib executors).
        assertEquals("HTTP 500: junk", ex.message)
    }

    // ----- retryHttpCalls (the Resilient*-wrapper replacement flag) -----

    @Test
    fun `retryHttpCalls retries a retryable failure and returns the first success`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503).setBody("down"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"name":"ok","count":1}"""))

        val result = executor(retryHttpCalls = true).parseJson<TestPayload>(request())

        assertTrue(result.isSuccess)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `retryHttpCalls off keeps the single-attempt semantics`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503).setBody("down"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"name":"ok","count":1}"""))

        val result = executor().parseJson<TestPayload>(request())

        assertTrue(result.isFailure)
        assertEquals(1, server.requestCount, "no declared flag → no retry (GitHub/LrcLib)")
    }

    @Test
    fun `retryHttpCalls gives up after MAX_RETRIES extra attempts`() = runTest {
        repeat(HttpExecutor.MAX_RETRIES + 1) {
            server.enqueue(MockResponse().setResponseCode(500).setBody("still down"))
        }

        val result = executor(retryHttpCalls = true).parseUnit(request())

        assertTrue(result.isFailure)
        assertEquals(HttpExecutor.MAX_RETRIES + 1, server.requestCount)
    }

    @Test
    fun `retryHttpCalls does not retry non-retryable statuses`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("no key"))

        val result = executor(retryHttpCalls = true).parseUnit(request())

        assertTrue(result.isFailure)
        assertEquals(1, server.requestCount, "401 must fail fast")
    }
}
