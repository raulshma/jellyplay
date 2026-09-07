package com.raulshma.jellyplay.core.network.arr

import com.raulshma.jellyplay.core.model.arr.ArrQueueDeleteOptions
import com.raulshma.jellyplay.core.network.api.ApiException
import com.raulshma.jellyplay.core.network.api.JsonRequestClient
import com.raulshma.jellyplay.core.network.api.fromNetwork
import com.raulshma.jellyplay.core.network.api.parseJsonRequest
import com.raulshma.jellyplay.core.network.api.parseUnitRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * One seam for the request preamble the Radarr and Sonarr clients used to
 * hand-copy verbatim (the JellyfinRawRequester precedent): the /api/v3 URL
 * join, the `X-Api-Key` auth header, the raw-text [executeRequest], the
 * `*arr` [parseErrorMessage] shape, the per-service network-failure texts,
 * the [JsonRequestClient]/[parseRequest] decode bundle, the delete-options
 * query fold, and the two one-line status-only helpers ([deleteRequest],
 * [postEmpty]). Parameterized by `(okHttp, json, serviceName)` — the ONLY
 * textual difference between the two folded copies was the service name
 * inside the user-visible failure strings, so those strings are rebuilt here
 * byte-identically (they surface in user-facing messages and several tests
 * pin them).
 *
 * Deliberately a SIBLING of, not a replacement for, SeerrApiClientImpl: the
 * Seerr preamble LOOKS similar but its texts diverge structurally (generic
 * "server" phrasing with different sentence shapes, a message-field
 * `parseErrorMessage`, the cookie-carrying execution variant), so folding it
 * under a `serviceName` parameter would either change byte-level strings or
 * need a second parameterization axis for no gain. Its decode half already
 * lives in the shared `JsonRequestClient`/`parseJsonRequest` plumbing.
 */
internal class ArrClientSupport(
    private val okHttpClient: OkHttpClient,
    internal val json: Json,
    private val serviceName: String,
) {

    fun buildUrl(baseUrl: String, path: String): HttpUrl {
        val base = baseUrl.trimEnd('/')
        // The *arr API root is /api/v3; the trailing path is appended as-is so
        // callers can pass query strings via [HttpUrl.Builder] after the fact.
        return "$base/api/v3$path".toHttpUrl()
    }

    suspend fun executeRequest(request: Request): Result<String> {
        return try {
            withContext(Dispatchers.IO) {
                okHttpClient.newCall(request).execute().use { response ->
                    val body = response.body?.string() ?: return@withContext Result.failure<String>(
                        ApiException.fromHttp(response.code, "Empty response body (HTTP ${response.code})")
                    )
                    if (!response.isSuccessful) {
                        val errorMsg = parseErrorMessage(response.code, body)
                        return@withContext Result.failure(ApiException.fromHttp(response.code, errorMsg))
                    }
                    Result.success(body)
                }
            }
        } catch (e: Exception) {
            // CancellationException must propagate for structured-concurrency correctness.
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(ApiException.fromNetwork(e, formatNetworkError(e)))
        }
    }

    private fun parseErrorMessage(code: Int, body: String): String = try {
        val errorJson = json.parseToJsonElement(body)
        // *arr errors: [{ "errorMessage": "..." }] or { "message": "..." }
        val msg = errorJson.toString()
        if (msg.isNotBlank()) "HTTP $code: ${msg.take(200)}" else "HTTP $code"
    } catch (_: Exception) {
        "HTTP $code: ${body.take(200)}"
    }

    private fun formatNetworkError(e: Exception): String = when (e) {
        is UnknownHostException -> "Unable to reach $serviceName. Check the URL and your network connection."
        is ConnectException -> "Could not connect to $serviceName. Ensure the server is running and accessible."
        is SocketTimeoutException -> "Connection to $serviceName timed out. The server took too long to respond."
        is IOException -> "Network error reaching $serviceName: ${e.message ?: e.javaClass.simpleName}"
        else -> e.message ?: e.javaClass.simpleName
    }

    /** Bundled [parseJsonRequest] dependencies; see [parseRequest]. */
    val jsonRequestClient = JsonRequestClient(
        okHttpClient = okHttpClient,
        json = json,
        parseErrorMessage = ::parseErrorMessage,
        formatNetworkError = ::formatNetworkError,
    )

    /** Stream-decoding request execution; see [parseJsonRequest]. */
    internal suspend inline fun <reified T> parseRequest(request: Request): Result<T> =
        parseJsonRequest(jsonRequestClient, request)

    suspend fun deleteRequest(baseUrl: String, apiKey: String, path: String): Result<Unit> {
        val request = Request.Builder()
            .url(buildUrl(baseUrl, path))
            .withApiKey(apiKey)
            .delete()
            .build()
        return parseUnitRequest(jsonRequestClient, request)
    }

    suspend fun postEmpty(
        baseUrl: String,
        apiKey: String,
        path: String,
    ): Result<Unit> {
        val body = "{}".toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(buildUrl(baseUrl, path))
            .withApiKey(apiKey)
            .post(body)
            .build()
        return parseUnitRequest(jsonRequestClient, request)
    }
}

/**
 * The *arr v3 auth header — the same header name Seerr uses. Top-level so the
 * clients' request-builder chains read exactly as they did when each impl
 * hand-carried its private copy.
 */
internal fun Request.Builder.withApiKey(apiKey: String): Request.Builder =
    header("X-Api-Key", apiKey)

/**
 * Applies the [ArrQueueDeleteOptions] as query params on a DELETE URL.
 * Shared by single + bulk queue deletes.
 */
internal fun HttpUrl.Builder.withDeleteOptions(options: ArrQueueDeleteOptions): HttpUrl.Builder = apply {
    addQueryParameter("removeFromClient", options.removeFromClient.toString())
    addQueryParameter("blocklist", options.blocklist.toString())
    addQueryParameter("skipRedownload", options.skipRedownload.toString())
}
