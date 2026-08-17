package com.raulshma.jellyplay.core.network.api

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Bundles what every stream-decoding client passes to [parseJsonRequest]: the
 * OkHttp client + Json instance the service client is constructed with, plus
 * its two error shapers. One value per client class instead of four arguments
 * that always travel together.
 */
internal class JsonRequestClient(
    val okHttpClient: OkHttpClient,
    val json: Json,
    val parseErrorMessage: (code: Int, body: String) -> String,
    val formatNetworkError: (e: Exception) -> String,
)

/**
 * Empty-body failure for the hand-rolled stream-decoding clients (TMDB,
 * GitHub) that don't route through [parseJsonRequest].
 */
internal fun emptyResponseBodyError(source: String): ApiException {
    val message = "Empty response from $source"
    return ApiException.fromNetwork(IOException(message), message)
}

/**
 * Executes [request] and decodes the success body straight from the
 * response stream — avoids holding the buffered String and the decoded
 * object graph alive at the same time (large queue/history/discover
 * payloads). Body -> String only on the error path, for the error message.
 *
 * Shared by the Arr and Seerr clients; the per-service
 * [JsonRequestClient.parseErrorMessage] and
 * [JsonRequestClient.formatNetworkError] shape the friendly strings.
 */
internal suspend inline fun <reified T> parseJsonRequest(
    client: JsonRequestClient,
    request: Request,
): Result<T> = try {
    withContext(Dispatchers.IO) {
        client.okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val body = response.body?.string().orEmpty()
                val errorMsg = client.parseErrorMessage(response.code, body)
                Result.failure(ApiException.fromHttp(response.code, errorMsg))
            } else {
                val stream = response.body?.byteStream()
                if (stream == null) {
                    Result.failure<T>(ApiException.fromHttp(response.code, "Empty response body (HTTP ${response.code})"))
                } else {
                    // runCatching preserves the mapCatching semantics these
                    // clients had before stream-decoding: decode failures
                    // surface the raw exception, not a network
                    // ApiException wrapper.
                    runCatching { client.json.decodeFromStream<T>(stream) }
                }
            }
        }
    }
} catch (e: Exception) {
    // CancellationException must propagate for structured-concurrency
    // correctness; everything else becomes a network failure.
    if (e is CancellationException) throw e
    Result.failure(ApiException.fromNetwork(e, client.formatNetworkError(e)))
}
