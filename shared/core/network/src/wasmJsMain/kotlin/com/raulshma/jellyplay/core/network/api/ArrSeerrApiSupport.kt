package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.network.RetryPolicy
import com.raulshma.jellyplay.core.network.seerr.arrSeerrWireJson
import com.raulshma.jellyplay.core.network.seerr.joinSetCookieHeader
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

/**
 * Request plumbing base for the Phase W wasm Seerr / Radarr / Sonarr / TMDB
 * clients — the wasm counterpart of the jvmShared OkHttp
 * `JsonRequestClient`/`parseJsonRequest`/`parseUnitRequest` plumbing
 * (`ApiResponseParsing.kt`), re-shaped for these seams' stateless per-call
 * `(baseUrl, credentials)` contract. Deliberately a SIBLING of
 * [WasmApiSupport], not an extension: that base derives URL + token from the
 * shared Jellyfin [com.raulshma.jellyplay.core.network.auth.AtomicSessionState],
 * while these services carry their base URL and auth header per call.
 *
 * Wire mechanics, ported 1:1 from the jvmShared impls:
 *  - URL join: `baseUrl.trimEnd('/') + /api/v1|/api/v3 + path` (the clients
 *    pass pre-assembled path strings, including path-embedded query strings
 *    like `?page=$page`, exactly as the JVM impls do).
 *  - Auth: one `X-Api-Key` / `Cookie` header per call (`seerrAuthHeaders` /
 *    `arrApiKeyHeaders`); no Authorization header, no session state.
 *  - GET/POST/PUT decode the body as JSON on the success path only; unit
 *    requests (arr mutations, `system/status` probes) and the raw-text
 *    executions (Seerr deletes, the 2-step manualimport, Sonarr's defensive
 *    `/series` reads) depend on the status code alone — the body is read only
 *    on the error path, for the friendly message.
 *  - Failure shaping: HTTP failures go through the per-service
 *    `parseErrorMessage` ([httpFailureMessage] — Seerr and *arr have
 *    different texts); transport failures through the per-service
 *    `formatNetworkError` counterparts ([ioFailureMessage] /
 *    [unclassifiedFailureMessage]); both classify retryability so
 *    [RetryPolicy] can act.
 *
 * Documented wasm deltas vs the jvmShared plumbing (none affect JVM):
 *  - No `Dispatchers.IO` hop (no such dispatcher on wasm; the fetch-backed
 *    engine is non-blocking). No failover router either — same as the JVM
 *    Seerr and arr clients, which take the baseUrl per call.
 *  - The JVM's `UnknownHostException` / `ConnectException` /
 *    `SocketTimeoutException` texts are NOT reproducible: the fetch engine
 *    wraps DNS/refused/TLS failures in undifferentiated IO errors. All
 *    transport failures get the service's generic IOException-branch text;
 *    request/connect timeouts (the only timeout signal wasm has) get the
 *    service's timeout text ([timeoutFailureMessage]; null for TMDB, whose
 *    JVM code has no dedicated timeout text — they fall to the IO text).
 *  - JVM `ApiException.fromHttp` drops the `Retry-After` header; the wasm
 *    base honors it via `ApiException.fromHttpResponse` (same behavior as
 *    [WasmApiSupport]) so [RetryPolicy] can floor its backoff at the
 *    server's advice.
 *  - JVM `parseJsonRequest` keeps decode failures RAW inside the failed
 *    `Result` (a bare `SerializationException`); here they are wrapped by
 *    [apiResult]'s mapping into a non-retryable [ApiException] carrying the
 *    same message. Callers only read `.message`, so the user-visible string
 *    is unchanged.
 *  - POST/PUT bodies go out as `application/json; charset=UTF-8` (Ktor
 *    `TextContent`) where OkHttp wrote a bare `application/json`; servers
 *    treat the two identically.
 *  - Retry is folded into the clients ([apiResultWithRetry], default
 *    maxRetries = 4 = the Resilient* wrappers' `MAX_RETRIES`), replacing the
 *    DI-level `Resilient*` wrappers the JVM graph binds the interfaces to
 *    (those are OkHttp-side jvmShared classes; on wasm the interface binding
 *    is the Ktor client itself).
 *
 * Timeouts are the [com.raulshma.jellyplay.core.network.createWasmHttpClient]
 * best-effort windows (the fetch engine's HttpTimeout support is limited —
 * see its CAVEAT note).
 */
open class ArrSeerrApiSupport(
    protected val httpClient: HttpClient,
    /** Per-service `parseErrorMessage(code, body)` (`::seerrHttpErrorMessage` / `::arrHttpErrorMessage` / TMDB's body-ignoring text). */
    private val httpFailureMessage: (code: Int, body: String) -> String,
    /** Per-service timeout text; null routes timeouts through [ioFailureMessage] (TMDB). */
    private val timeoutFailureMessage: String?,
    /** Per-service generic IOException-branch text. */
    private val ioFailureMessage: (e: Throwable) -> String,
    /** Per-service text for non-IO, non-HTTP failures (the JVM `else`/parse-error branches). */
    private val unclassifiedFailureMessage: (e: Throwable) -> String,
) {

    /** The shared lenient decode/encode instance — the wasm twin of `SeerrApiClientImpl.lenientJson`. */
    protected val wireJson: Json = arrSeerrWireJson

    protected fun HttpRequestBuilder.attachHeaders(headers: List<Pair<String, String>>) {
        headers.forEach { (k, v) -> header(k, v) }
    }

    /** Ktor `parameter()` — the `HttpUrl.Builder.addQueryParameter` counterpart (space → `%20` both). */
    protected fun HttpRequestBuilder.attachQuery(query: List<Pair<String, String>>) {
        query.forEach { (k, v) -> parameter(k, v) }
    }

    // ── JSON-decoding executions (the parseJsonRequest counterparts) ───────

    /** GET + decode [T] (success path only; failure throws — see [apiResult]). */
    protected suspend inline fun <reified T> getAndParse(
        url: String,
        headers: List<Pair<String, String>> = emptyList(),
        query: List<Pair<String, String>> = emptyList(),
    ): T {
        val response: HttpResponse = httpClient.get(url) {
            attachHeaders(headers)
            attachQuery(query)
        }
        return parseResponse<T>(response)
    }

    /** POST + decode [T]. [bodyText] defaults to `"{}"` — the JVM's empty-JSON POST body. */
    protected suspend inline fun <reified T> postAndParse(
        url: String,
        headers: List<Pair<String, String>> = emptyList(),
        bodyText: String = EMPTY_JSON_BODY,
        query: List<Pair<String, String>> = emptyList(),
    ): T {
        val response: HttpResponse = httpClient.post(url) {
            attachHeaders(headers)
            attachQuery(query)
            setBody(TextContent(bodyText, ContentType.Application.Json))
        }
        return parseResponse<T>(response)
    }

    /** PUT + decode [T]. */
    protected suspend inline fun <reified T> putAndParse(
        url: String,
        headers: List<Pair<String, String>>,
        bodyText: String,
    ): T {
        val response: HttpResponse = httpClient.put(url) {
            attachHeaders(headers)
            setBody(TextContent(bodyText, ContentType.Application.Json))
        }
        return parseResponse<T>(response)
    }

    // protected (not private): the protected inline reified helpers above
    // must not access less-visible declarations (inline API restriction).
    protected suspend inline fun <reified T> parseResponse(response: HttpResponse): T {
        if (!response.status.isSuccess()) throw response.toHttpApiException()
        return wireJson.decodeFromString<T>(response.bodyAsText())
    }

    // ── Status-only / raw-text executions (parseUnitRequest / executeRequest) ──

    /**
     * Executes [execute] and requires only a 2xx status (the
     * `parseUnitRequest` contract — *arr mutation endpoints return payloads
     * callers would decode purely to discard). The body is read only on the
     * error path, for the friendly message.
     */
    protected suspend fun unitRequest(execute: suspend () -> HttpResponse) {
        val response = execute()
        if (!response.status.isSuccess()) throw response.toHttpApiException()
    }

    /**
     * Executes [execute] and returns the raw body text (the `executeRequest`
     * contract: Seerr's delete endpoints, the manualimport discovery GET,
     * Sonarr's defensive `/series` reads).
     */
    protected suspend fun executeForText(execute: suspend () -> HttpResponse): String {
        val response = execute()
        if (!response.status.isSuccess()) throw response.toHttpApiException()
        return response.bodyAsText()
    }

    /**
     * Executes [execute] and returns body text + the joined `Set-Cookie`
     * header values (the `executeRequestWithCookie` contract — Seerr login).
     * WASM BROWSER CAVEAT: browsers do not expose `Set-Cookie` to `fetch`,
     * so in a browser tab the cookie side is always null and login fails
     * with the JVM-identical "No session cookie received from server" — the
     * header join itself is faithful (see [com.raulshma.jellyplay.core.network.seerr.joinSetCookieHeader]).
     */
    protected suspend fun executeForCookie(execute: suspend () -> HttpResponse): Pair<String, String?> {
        val response = execute()
        if (!response.status.isSuccess()) throw response.toHttpApiException()
        val body = response.bodyAsText()
        val cookieHeader = joinSetCookieHeader(
            response.headers.getAll("Set-Cookie").orEmpty(),
        )
        return body to cookieHeader
    }

    /** HTTP-status failure → [ApiException], with Retry-After honored (see class KDoc). */
    protected suspend fun HttpResponse.toHttpApiException(): ApiException = ApiException.fromHttpResponse(
        httpCode = status.value,
        message = httpFailureMessage(status.value, bodyAsText()),
        retryAfterHeader = headers["Retry-After"],
    )

    /**
     * Transport/unclassified-failure classification: timeouts and fetch IO
     * errors → retryable with the service texts; everything else non-
     * retryable (mirrors the JVM `classifyNetwork` + `formatNetworkError`
     * split; taxonomy collapse documented in the class KDoc).
     */
    private fun Throwable.toTransportApiException(): ApiException = when (this) {
        is ApiException -> this
        is HttpRequestTimeoutException -> ApiException(
            isRetryable = true,
            message = timeoutFailureMessage ?: ioFailureMessage(this),
            cause = this,
        )
        is IOException -> ApiException(
            isRetryable = true,
            message = ioFailureMessage(this),
            cause = this,
        )
        else -> ApiException(
            isRetryable = false,
            message = unclassifiedFailureMessage(this),
            cause = this,
        )
    }

    /**
     * The engine's apiResult shape (see [WasmApiSupport.apiResult] — the same
     * recoverCatching/cancellation parity note applies: cancellation surfaces
     * as `Result.failure(CancellationException)`, byte-parity with the JVM
     * engine's wrapper).
     */
    protected suspend fun <T> apiResult(block: suspend () -> T): Result<T> =
        runCatching { block() }.recoverCatching {
            if (it is CancellationException) throw it
            throw it.toTransportApiException()
        }

    /** [apiResult] under [RetryPolicy] — the wasm stand-in for the DI-level Resilient* wrappers. */
    protected suspend fun <T> apiResultWithRetry(
        maxRetries: Int = RESILIENT_MAX_RETRIES,
        block: suspend () -> T,
    ): Result<T> = RetryPolicy.executeWithRetry(maxRetries = maxRetries) { apiResult(block) }

    companion object {
        /** The Resilient* wrappers' `MAX_RETRIES` on jvmShared (Seerr, Radarr, Sonarr, TMDB all use 4). */
        protected const val RESILIENT_MAX_RETRIES = 4

        /** The `"{}"` POST body the JVM impls send for body-less mutations. */
        protected const val EMPTY_JSON_BODY = "{}"
    }
}
