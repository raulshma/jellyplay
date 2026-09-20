package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.network.RetryPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

/**
 * The ONE OkHttp execute chassis for this module's hand-rolled clients —
 * the member vocabulary
 * ([parseJson]/[parseUnit]/[executeForText]/[executeForCookie]) over an
 * [Options] record in the `SubtitleHttp.Options` idiom. Every
 * `newCall().execute().use` → status check → body-null guard →
 * CancellationException-rethrow → catch-Exception→`fromNetwork` ladder the
 * Arr/Seerr/TMDB/GitHub/LrcLib clients used to hand-copy funnels through the
 * members here; the genuine per-service differences travel as declared
 * [Options] (the two error shapers, Retry-After capture, the empty-body
 * failure shape) — not as copy variance.
 *
 * Failure shaping:
 *  - **HTTP-status failures** go through [Options.parseErrorMessage] into
 *    [ApiException.fromHttpResponse] (identical to `ApiException.fromHttp`
 *    unless [Options.captureRetryAfter] is set, which attaches the server's
 *    `Retry-After` so [RetryPolicy] can floor its backoff at it).
 *  - **Transport failures** ride [ApiException.fromNetwork] with
 *    [Options.formatNetworkError]; `CancellationException` always rethrows.
 *  - **Absent bodies** take the shape [Options.emptyBodyText] declares: the
 *    parse families use the HTTP-status shape ("Empty response body (HTTP n)"),
 *    the text-shaped clients (TMDB/GitHub/LrcLib) keep their IOException-backed
 *    retryable shape ("Empty response from X").
 *  - **Decode failures** in [parseJson] surface RAW (not wrapped in an
 *    [ApiException]) so a deterministic decode error is never retried.
 *
 * Retry: when [Options.retryHttpCalls] is set, the Result members wrap
 * themselves in [RetryPolicy.executeWithRetry] at [MAX_RETRIES] — the count
 * the deleted DI-level `Resilient*` wrappers enforced (see the companion KDoc).
 * GitHub and LrcLib never had a wrapper, so their executors leave the flag off
 * and keep direct-construction semantics.
 *
 * Internal like the rest of the module's shared plumbing: nothing outside
 * `:shared:core:network` should grow another copy of the ladder.
 */
internal class HttpExecutor(
    internal val okHttpClient: OkHttpClient,
    /**
     * Lenient wire decoder, required only by [parseJson] (the text-shaped
     * members and the throwing core never decode). Nullable so the
     * execute-only clients don't have to thread a Json through. Internal
     * (not private): the inline reified members inline into caller files.
     */
    internal val json: Json? = null,
    internal val options: Options,
) {

    /**
     * Declared per-family divergences of the shared chassis. Defaults are the
     * plain-vanilla parse-family arms; each client sets only what it
     * genuinely does.
     */
    class Options(
        /** Shapes the (bounded) error body into the HTTP-failure message. */
        val parseErrorMessage: (code: Int, body: String) -> String,
        /** Shapes a transport failure into the friendly `fromNetwork` message. */
        val formatNetworkError: (e: Exception) -> String =
            { e -> e.message ?: e.javaClass.simpleName },
        /**
         * Parse the `Retry-After` header onto HTTP-status failures so
         * [RetryPolicy] honors the server's backoff advice. Off for the parse
         * families (whose `fromHttp` shape never carried it); the rate-limited
         * services (TMDB/GitHub, and SubtitleHttp's own non-2xx arm) set it.
         */
        val captureRetryAfter: Boolean = false,
        /**
         * Text of the absent-body failure for the text-shaped members
         * ("Empty response from TMDB") — an IOException-backed, retryable
         * `fromNetwork` failure. Null routes the parse families' absent-body
         * arms through the HTTP-status shape
         * ("Empty response body (HTTP n)") instead.
         */
        val emptyBodyText: String? = null,
        /**
         * Wrap the Result members in [RetryPolicy.executeWithRetry] at
         * [MAX_RETRIES]. Set for the Arr/Seerr families (whose retry used to
         * live in the deleted DI-level `Resilient*` wrappers); off for
         * GitHub/LrcLib, which never had one.
         */
        val retryHttpCalls: Boolean = false,
    )

    /**
     * The retry budget for every family whose retry used to live in the
     * deleted `Resilient*` wrappers. Declared ONCE for all five families
     * (Seerr, Radarr, Sonarr, TMDB, Subtitle): 4 — the count three of the
     * wrappers enforced; Subtitle moves up from `RetryPolicy.DEFAULT_MAX_RETRIES`
     * (3) as part of the fold. Pinned per family by the give-up-after-N
     * retry tests.
     */
    companion object {
        const val MAX_RETRIES = 4
    }

    // ── Core scaffold ───────────────────────────────────────────────────────

    /**
     * Executes [request] and splits on the status line: 2xx hands the
     * [Response] to [onResponse] (the caller owns the body shape), non-2xx
     * throws the [Options]-shaped HTTP failure. The response is always
     * closed.
     */
    internal fun <T> execute(
        request: Request,
        onResponse: (Response) -> T,
    ): T = execute(request, onHttpFailure = { throw httpFailure(it) }, onResponse = onResponse)

    /**
     * [execute] with a caller-shaped non-2xx arm — the escape hatch for
     * SubtitleHttp, whose failure arm logs a bounded body preview and captures
     * the raw body on the [ApiException] before throwing.
     */
    internal fun <T> execute(
        request: Request,
        onHttpFailure: (Response) -> Nothing,
        onResponse: (Response) -> T,
    ): T = okHttpClient.newCall(request).execute().use { response ->
        if (response.isSuccessful) onResponse(response) else onHttpFailure(response)
    }

    /** Non-2xx → the [Options]-shaped [ApiException]; the error body is read defensively. */
    internal fun httpFailure(response: Response): ApiException = ApiException.fromHttpResponse(
        httpCode = response.code,
        message = options.parseErrorMessage(
            response.code,
            runCatching { response.body?.string() }.getOrNull().orEmpty(),
        ),
        retryAfterHeader = response.retryAfterHeader(),
    )

    /** Non-2xx with an already-read body (the raw-text members read before the status split). */
    internal fun httpFailure(response: Response, body: String): ApiException = ApiException.fromHttpResponse(
        httpCode = response.code,
        message = options.parseErrorMessage(response.code, body),
        retryAfterHeader = response.retryAfterHeader(),
    )

    private fun Response.retryAfterHeader(): String? =
        if (options.captureRetryAfter) header("Retry-After") else null

    /** The parse families' absent-body failure: HTTP-status shape, message constant. */
    internal fun httpEmptyBodyFailure(code: Int): ApiException = ApiException.fromHttpResponse(
        httpCode = code,
        message = "Empty response body (HTTP $code)",
        retryAfterHeader = null,
    )

    /** The text-shaped members' absent-body failure: IOException-backed, retryable. */
    internal fun emptyBodyNetworkError(): ApiException {
        val text = requireNotNull(options.emptyBodyText) {
            "Options.emptyBodyText is required for the text-shaped members"
        }
        return ApiException.fromNetwork(IOException(text), text)
    }

    // ── Result members (the parse families' funnels) ────────────────────────

    /**
     * The shared Result ladder over [apiResultOnce] — exactly the
     * `catch CancellationException-rethrow / catch Exception → fromNetwork`
     * shape, plus [RetryPolicy] when [Options.retryHttpCalls] is set.
     */
    internal suspend fun <T> apiResult(block: suspend () -> Result<T>): Result<T> =
        if (options.retryHttpCalls) {
            RetryPolicy.executeWithRetry(maxRetries = MAX_RETRIES) { apiResultOnce(block) }
        } else {
            apiResultOnce(block)
        }

    // internal (not private): the inline reified members below inline into
    // caller files, which must see everything they reference.
    internal suspend fun <T> apiResultOnce(block: suspend () -> Result<T>): Result<T> = try {
        withContext(Dispatchers.IO) { block() }
    } catch (e: Exception) {
        // CancellationException must propagate for structured-concurrency
        // correctness; an ApiException is already classified (the HTTP-status
        // and empty-body arms thrown by the scaffold) and passes through
        // unchanged — re-running it through fromNetwork would clobber its
        // httpCode/retryAfter fields and its retryable flag. Everything else
        // becomes a network failure.
        when (e) {
            is CancellationException -> throw e
            is ApiException -> Result.failure(e)
            else -> Result.failure(ApiException.fromNetwork(e, options.formatNetworkError(e)))
        }
    }

    /**
     * Executes [request] and decodes the success body straight from the
     * response stream — avoids holding the buffered String and the decoded
     * object graph alive at the same time (large queue/history/discover
     * payloads). Body -> String only on the error path, for the error message.
     */
    internal suspend inline fun <reified T> parseJson(request: Request): Result<T> = apiResult {
        execute(request) { response ->
            val stream = response.body?.byteStream()
            if (stream == null) {
                Result.failure<T>(httpEmptyBodyFailure(response.code))
            } else {
                // runCatching preserves the mapCatching semantics these
                // clients had before stream-decoding: decode failures
                // surface the raw exception, not a network
                // ApiException wrapper.
                runCatching { requireNotNull(json).decodeFromStream<T>(stream) }
            }
        }
    }

    /**
     * Executes [request] and requires only a 2xx status: *arr v3 mutation
     * endpoints return the full affected resource list, which callers would
     * decode purely to discard it. Success depends only on the status code,
     * so the body is read only on the error path, for the error message.
     */
    internal suspend fun parseUnit(request: Request): Result<Unit> = apiResult {
        execute(request) { Result.success(Unit) }
    }

    /**
     * Executes [request] and returns the raw body text (the former
     * `executeRequest` contract: *arr raw-text reads, Seerr deletes).
     */
    internal suspend fun executeForText(request: Request): Result<String> = apiResult {
        execute(request) { response ->
            val body = response.body?.string()
            if (body == null) {
                Result.failure<String>(httpEmptyBodyFailure(response.code))
            } else {
                Result.success(body)
            }
        }
    }

    /**
     * [executeForText] plus the joined `Set-Cookie` header values (the former
     * `executeRequestWithCookie` contract — Seerr login). Blank joins read as
     * null.
     */
    internal suspend fun executeForCookie(request: Request): Result<Pair<String, String?>> = apiResult {
        execute(request) { response ->
            val body = response.body?.string()
            if (body == null) {
                Result.failure<Pair<String, String?>>(httpEmptyBodyFailure(response.code))
            } else {
                val cookieHeader = response.headers("Set-Cookie").joinToString("; ") {
                    it.substringBefore(";")
                }
                Result.success(body to cookieHeader.ifBlank { null })
            }
        }
    }

    // ── Throwing members (the text-shaped clients' funnel) ──────────────────

    /**
     * Executes [request], requires a 2xx status, and returns the buffered
     * body text. Non-2xx throws the [Options]-shaped [ApiException], an
     * absent body throws [emptyBodyNetworkError], transport failures
     * propagate raw — callers (TMDB/LrcLib) own their surrounding catch
     * ladders.
     */
    internal suspend fun executeForBodyText(request: Request): String = withContext(Dispatchers.IO) {
        execute(request) { response ->
            response.body?.string() ?: throw emptyBodyNetworkError()
        }
    }
}

/**
 * The execute scaffold SubtitleHttp rides: `newCall().execute().use` with the
 * 2xx / non-2xx split, both arms caller-shaped (subtitle logs a bounded body
 * preview and captures the raw body on the failure [ApiException], so its
 * non-2xx arm cannot be the [HttpExecutor.Options] default).
 */
internal fun <T> OkHttpClient.executeWithStatusSplit(
    request: Request,
    onHttpFailure: (Response) -> Nothing,
    onResponse: (Response) -> T,
): T = newCall(request).execute().use { response ->
    if (response.isSuccessful) onResponse(response) else onHttpFailure(response)
}
