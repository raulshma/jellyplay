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
 * Pins the shared stream-decoding request pipeline (ApiResponseParsing.kt) the
 * Arr/Seerr/TMDB/GitHub clients run on: 2xx stream decode, error-body message
 * shaping through [JsonRequestClient.parseErrorMessage], network-failure
 * shaping through [JsonRequestClient.formatNetworkError], CancellationException
 * passthrough (structured-concurrency requirement), and the Unit-request body
 * discard. The service-specific shapers are caller-supplied lambdas here, so
 * the assertions pin the pipeline's contract, not any one service's wording.
 */
class ApiResponseParsingTest {

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
    private fun client(okHttpClient: OkHttpClient = OkHttpClient()): JsonRequestClient =
        JsonRequestClient(
            okHttpClient = okHttpClient,
            json = Json { ignoreUnknownKeys = true; isLenient = true },
            parseErrorMessage = { code, body -> "HTTP $code: $body" },
            formatNetworkError = { e -> "network: ${e.message}" },
        )

    private fun request(): Request = Request.Builder().url(server.url("/thing")).get().build()

    // ----- parseJsonRequest -----

    @Test
    fun `2xx body decodes straight from the stream`() = runTest {
        server.enqueue(MockResponse().setBody("""{"name":"ok","count":3,"unknownExtra":true}"""))

        val result = parseJsonRequest<TestPayload>(client(), request())

        assertTrue(result.isSuccess)
        assertEquals(TestPayload("ok", 3), result.getOrThrow())
    }

    @Test
    fun `non-2xx shapes the body through parseErrorMessage into ApiException fromHttp`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503).setBody("""{"message":"down"}"""))

        val result = parseJsonRequest<TestPayload>(client(), request())

        assertTrue(result.isFailure)
        val ex = assertIs<ApiException>(result.exceptionOrNull())
        assertEquals("""HTTP 503: {"message":"down"}""", ex.message)
        assertEquals(503, ex.httpCode)
        assertTrue(ex.isRetryable, "5xx is retryable via ApiException.fromHttp")
        assertFalse(ex.isAccessDenied)
    }

    @Test
    fun `401 keeps the access-denied classification`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"message":"no key"}"""))

        val result = parseJsonRequest<TestPayload>(client(), request())

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

        val result = parseJsonRequest<TestPayload>(client(), request())

        assertTrue(result.isFailure)
        assertIs<SerializationException>(result.exceptionOrNull())
    }

    @Test
    fun `empty 2xx body is a decode failure, not a network error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))

        val result = parseJsonRequest<TestPayload>(client(), request())

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
            parseJsonRequest<TestPayload>(client(canceling), request())
        }
    }

    @Test
    fun `IOException shapes into a retryable network ApiException`() = runTest {
        val failing = OkHttpClient.Builder()
            .addInterceptor { throw IOException("socket closed") }
            .build()

        val result = parseJsonRequest<TestPayload>(client(failing), request())

        assertTrue(result.isFailure)
        val ex = assertIs<ApiException>(result.exceptionOrNull())
        assertEquals("network: socket closed", ex.message)
        assertTrue(ex.isRetryable)
        assertNull(ex.httpCode, "no HTTP response → no status code")
        assertNotNull(ex.cause)
    }

    // ----- parseUnitRequest -----

    @Test
    fun `unit request succeeds without decoding the body`() = runTest {
        // *arr v3 mutations return the full affected resource list; the body
        // must be discarded on the success path, success rides the status
        // code alone.
        server.enqueue(MockResponse().setResponseCode(200).setBody("""["whatever","junk"]"""))

        val result = parseUnitRequest(client(), request())

        assertTrue(result.isSuccess)
        assertEquals(Unit, result.getOrThrow())
    }

    @Test
    fun `unit request failure shapes the error body`() = runTest {
        server.enqueue(MockResponse().setResponseCode(409).setBody("""{"message":"conflict"}"""))

        val result = parseUnitRequest(client(), request())

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

        assertFailsWith<CancellationException> { parseUnitRequest(client(canceling), request()) }
    }

    // ----- emptyResponseBodyError -----

    @Test
    fun `emptyResponseBodyError builds a retryable empty-response ApiException`() {
        val ex = emptyResponseBodyError("TMDB")

        assertEquals("Empty response from TMDB", ex.message)
        assertTrue(ex.isRetryable, "an IOException-backed empty response is a transient failure")
        assertNull(ex.httpCode)
        assertIs<IOException>(ex.cause)
    }
}
