package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.arr.ArrBlocklistItem
import com.raulshma.jellyplay.core.model.arr.ArrCalendarItem
import com.raulshma.jellyplay.core.model.arr.ArrCommand
import com.raulshma.jellyplay.core.model.arr.ArrCommandName
import com.raulshma.jellyplay.core.model.arr.ArrHistoryItem
import com.raulshma.jellyplay.core.model.arr.ArrQueueDeleteOptions
import com.raulshma.jellyplay.core.model.arr.ArrQueueItem
import com.raulshma.jellyplay.core.model.arr.ArrWantedItem
import com.raulshma.jellyplay.core.network.arr.RadarrBlocklistResponse
import com.raulshma.jellyplay.core.network.arr.RadarrCommandRequest
import com.raulshma.jellyplay.core.network.arr.RadarrCommandResource
import com.raulshma.jellyplay.core.network.arr.RadarrHistoryResponse
import com.raulshma.jellyplay.core.network.arr.RadarrIdsBulkRequest
import com.raulshma.jellyplay.core.network.arr.RadarrMovieMonitorRequest
import com.raulshma.jellyplay.core.network.arr.RadarrMovieResource
import com.raulshma.jellyplay.core.network.arr.RadarrQueueBulkRequest
import com.raulshma.jellyplay.core.network.arr.RadarrQueueResponse
import com.raulshma.jellyplay.core.network.arr.RadarrWantedResponse
import com.raulshma.jellyplay.core.network.arr.RadarrApiClient
import com.raulshma.jellyplay.core.network.arr.RadarrMovieInfo
import com.raulshma.jellyplay.core.network.arr.arrApiUrl
import com.raulshma.jellyplay.core.network.arr.arrApiKeyHeaders
import com.raulshma.jellyplay.core.network.arr.arrHttpErrorMessage
import com.raulshma.jellyplay.core.network.arr.toArrBlocklistItem
import com.raulshma.jellyplay.core.network.arr.toArrCommand
import com.raulshma.jellyplay.core.network.arr.toArrHistoryItem
import com.raulshma.jellyplay.core.network.arr.toArrQueueItem
import com.raulshma.jellyplay.core.network.arr.toArrWantedItem
import com.raulshma.jellyplay.core.network.arr.toCalendarItem
import com.raulshma.jellyplay.core.network.arr.toQueryPairs
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray

private const val RADARR_TIMEOUT_MESSAGE =
    "Connection to Radarr timed out. The server took too long to respond."

/** The IOException branch of `RadarrApiClientImpl.formatNetworkError`. */
private fun radarrIoFailureMessage(e: Throwable): String =
    "Network error reaching Radarr: ${e.message ?: e::class.simpleName ?: ""}"

/** The `else` branch of `RadarrApiClientImpl.formatNetworkError`. */
private fun radarrUnclassifiedFailureMessage(e: Throwable): String =
    e.message ?: e::class.simpleName ?: ""

/**
 * The wasmJs [RadarrApiClient] — a hand-rolled Ktor replacement for the
 * jvmShared `RadarrApiClientImpl` + `ResilientRadarrApiClient` pair (OkHttp).
 * The `/api/v3` paths, query params (`includeMovie`, the wanted/blocklist
 * sort keys, the queue-delete option trio), request bodies, the 2-step
 * manualimport flow, the `{ records }` envelope unwrapping, and every
 * error/network text mirror the JVM implementation request-for-request;
 * decode + mapping go through the commonMain [com.raulshma.jellyplay.core.network.arr]
 * wire DTOs — field-for-field transcriptions of the JVM impl's private
 * nested DTOs.
 *
 * Retry lives HERE (`apiResultWithRetry`, max 4 =
 * `ResilientRadarrApiClient.MAX_RETRIES`) instead of in a DI-level Resilient
 * wrapper. See [ArrSeerrApiSupport] for the full wasm delta list (transport
 * taxonomy collapse, Retry-After honoring, decode-failure wrapping).
 */
class KtorWasmRadarrApiClient(
    httpClient: HttpClient,
) : ArrSeerrApiSupport(
    httpClient = httpClient,
    httpFailureMessage = ::arrHttpErrorMessage,
    timeoutFailureMessage = RADARR_TIMEOUT_MESSAGE,
    ioFailureMessage = ::radarrIoFailureMessage,
    unclassifiedFailureMessage = ::radarrUnclassifiedFailureMessage,
), RadarrApiClient {

    override suspend fun getQueue(baseUrl: String, apiKey: String): Result<List<ArrQueueItem>> =
        // includeMovie=true attaches the movie resource so we can pull tmdbId + title.
        apiResultWithRetry {
            getAndParse<RadarrQueueResponse>(
                url = arrApiUrl(baseUrl, "/queue"),
                headers = arrApiKeyHeaders(apiKey),
                query = listOf("includeMovie" to "true"),
            ).records.map { it.toArrQueueItem() }
        }

    override suspend fun deleteQueueItem(
        baseUrl: String,
        apiKey: String,
        id: Int,
        options: ArrQueueDeleteOptions,
    ): Result<Unit> = apiResultWithRetry {
        unitRequest {
            httpClient.delete(arrApiUrl(baseUrl, "/queue/$id")) {
                attachHeaders(arrApiKeyHeaders(apiKey))
                attachQuery(options.toQueryPairs())
            }
        }
    }

    override suspend fun deleteQueueItems(
        baseUrl: String,
        apiKey: String,
        ids: List<Int>,
        options: ArrQueueDeleteOptions,
    ): Result<Unit> {
        if (ids.isEmpty()) return Result.success(Unit)
        return apiResultWithRetry {
            unitRequest {
                httpClient.delete(arrApiUrl(baseUrl, "/queue/bulk")) {
                    attachHeaders(arrApiKeyHeaders(apiKey))
                    attachQuery(options.toQueryPairs())
                    setBody(TextContent(wireJson.encodeToString(RadarrQueueBulkRequest(ids = ids)), ContentType.Application.Json))
                }
            }
        }
    }

    override suspend fun grabQueueItem(baseUrl: String, apiKey: String, id: Int): Result<Unit> =
        apiResultWithRetry {
            unitRequest {
                httpClient.post(arrApiUrl(baseUrl, "/queue/grab/$id")) {
                    attachHeaders(arrApiKeyHeaders(apiKey))
                    setBody(TextContent(EMPTY_JSON_BODY, ContentType.Application.Json))
                }
            }
        }

    override suspend fun importQueueItem(baseUrl: String, apiKey: String, downloadId: String): Result<Unit> =
        // The JVM Resilient wrapper retries the WHOLE two-step flow as one
        // unit — one retry block around both steps, not one per step.
        apiResultWithRetry {
            // 2-step manualimport flow (the *arr v3 spec has no queue/import/{id}):
            // 1) GET the candidate import rows for this download-client guid.
            val body = executeForText {
                httpClient.get(arrApiUrl(baseUrl, "/manualimport")) {
                    attachHeaders(arrApiKeyHeaders(apiKey))
                    attachQuery(listOf("downloadId" to downloadId))
                }
            }
            val rows = wireJson.decodeFromString<JsonArray>(body)
            if (rows.isEmpty()) {
                throw ApiException.fromHttp(404, "No importable files found for this download in Radarr.")
            }
            // 2) Re-post the rows verbatim to trigger the import. The POST body
            // schema is undocumented in the OpenAPI spec; passing the GET array
            // through unchanged is both the documented usage and immune to schema
            // drift on the 16-field ManualImportResource. JsonArray.toString() is
            // the compact re-serialization — the same bytes kotlinx produced on
            // the JVM side.
            unitRequest {
                httpClient.post(arrApiUrl(baseUrl, "/manualimport")) {
                    attachHeaders(arrApiKeyHeaders(apiKey))
                    setBody(TextContent(rows.toString(), ContentType.Application.Json))
                }
            }
        }

    override suspend fun getCalendar(
        baseUrl: String,
        apiKey: String,
        start: String,
        end: String,
    ): Result<List<ArrCalendarItem>> = apiResultWithRetry {
        getAndParse<List<RadarrMovieResource>>(
            url = arrApiUrl(baseUrl, "/calendar"),
            headers = arrApiKeyHeaders(apiKey),
            query = listOf("start" to start, "end" to end),
        ).map { it.toCalendarItem() }
    }

    override suspend fun getHistory(
        baseUrl: String,
        apiKey: String,
        eventType: Int?,
    ): Result<List<ArrHistoryItem>> = apiResultWithRetry {
        // includeMovie defaults to false; the mapper reads movie.tmdbId +
        // title, so request the sub-object or history rows lose their movie
        // identity.
        val query = buildList {
            add("includeMovie" to "true")
            if (eventType != null) add("eventType" to eventType.toString())
        }
        getAndParse<RadarrHistoryResponse>(
            url = arrApiUrl(baseUrl, "/history"),
            headers = arrApiKeyHeaders(apiKey),
            query = query,
        ).records.map { it.toArrHistoryItem() }
    }

    override suspend fun getBlocklist(
        baseUrl: String,
        apiKey: String,
        page: Int,
        pageSize: Int,
    ): Result<List<ArrBlocklistItem>> = apiResultWithRetry {
        getAndParse<RadarrBlocklistResponse>(
            url = arrApiUrl(baseUrl, "/blocklist"),
            headers = arrApiKeyHeaders(apiKey),
            query = listOf(
                "page" to page.toString(),
                "pageSize" to pageSize.toString(),
                "sortKey" to "date",
                "sortDirection" to "descending",
            ),
        ).records.map { it.toArrBlocklistItem() }
    }

    override suspend fun deleteBlocklistItem(baseUrl: String, apiKey: String, id: Int): Result<Unit> =
        apiResultWithRetry {
            unitRequest {
                httpClient.delete(arrApiUrl(baseUrl, "/blocklist/$id")) {
                    attachHeaders(arrApiKeyHeaders(apiKey))
                }
            }
        }

    override suspend fun deleteBlocklistItems(baseUrl: String, apiKey: String, ids: List<Int>): Result<Unit> {
        if (ids.isEmpty()) return Result.success(Unit)
        return apiResultWithRetry {
            unitRequest {
                httpClient.delete(arrApiUrl(baseUrl, "/blocklist/bulk")) {
                    attachHeaders(arrApiKeyHeaders(apiKey))
                    setBody(TextContent(wireJson.encodeToString(RadarrIdsBulkRequest(ids = ids)), ContentType.Application.Json))
                }
            }
        }
    }

    override suspend fun getWanted(
        baseUrl: String,
        apiKey: String,
        page: Int,
        pageSize: Int,
    ): Result<List<ArrWantedItem>> = apiResultWithRetry {
        getAndParse<RadarrWantedResponse>(
            url = arrApiUrl(baseUrl, "/wanted/missing"),
            headers = arrApiKeyHeaders(apiKey),
            query = listOf(
                "page" to page.toString(),
                "pageSize" to pageSize.toString(),
                "sortKey" to "inCinemas",
                "sortDirection" to "descending",
            ),
        ).records.map { it.toArrWantedItem() }
    }

    override suspend fun postCommand(
        baseUrl: String,
        apiKey: String,
        commandName: ArrCommandName,
        movieIds: List<Int>?,
        episodeIds: List<Int>?,
    ): Result<ArrCommand> = apiResultWithRetry {
        val body = RadarrCommandRequest(
            name = commandName.serialName,
            movieIds = movieIds,
            movieId = movieIds?.firstOrNull(),
        )
        postAndParse<RadarrCommandResource>(
            url = arrApiUrl(baseUrl, "/command"),
            headers = arrApiKeyHeaders(apiKey),
            bodyText = wireJson.encodeToString(body),
        ).toArrCommand()
    }

    override suspend fun findMovieIdByTmdb(baseUrl: String, apiKey: String, tmdbId: Int): Result<Int?> =
        // /api/v3/movie?tmdbId= returns a single-element array (or empty when
        // no match). Decoded as a list rather than a bare object so the
        // not-tracked case is a clean empty list instead of a parse error.
        apiResultWithRetry {
            getAndParse<List<RadarrMovieResource>>(
                url = arrApiUrl(baseUrl, "/movie"),
                headers = arrApiKeyHeaders(apiKey),
                query = listOf("tmdbId" to tmdbId.toString()),
            ).firstOrNull()?.id
        }

    override suspend fun getMovieForTmdb(baseUrl: String, apiKey: String, tmdbId: Int): Result<RadarrMovieInfo?> =
        apiResultWithRetry {
            getAndParse<List<RadarrMovieResource>>(
                url = arrApiUrl(baseUrl, "/movie"),
                headers = arrApiKeyHeaders(apiKey),
                query = listOf("tmdbId" to tmdbId.toString()),
            ).firstOrNull()?.let {
                RadarrMovieInfo(
                    id = it.id,
                    movieFileId = it.movieFileId,
                    hasFile = it.hasFile,
                    monitored = it.monitored,
                )
            }
        }

    override suspend fun deleteMovieFile(baseUrl: String, apiKey: String, movieFileId: Int): Result<Unit> =
        apiResultWithRetry {
            unitRequest {
                httpClient.delete(arrApiUrl(baseUrl, "/movieFile/$movieFileId")) {
                    attachHeaders(arrApiKeyHeaders(apiKey))
                }
            }
        }

    override suspend fun monitorMovies(
        baseUrl: String,
        apiKey: String,
        movieIds: List<Int>,
        monitored: Boolean,
    ): Result<Unit> {
        if (movieIds.isEmpty()) return Result.success(Unit)
        return apiResultWithRetry {
            unitRequest {
                httpClient.put(arrApiUrl(baseUrl, "/movie/monitor")) {
                    attachHeaders(arrApiKeyHeaders(apiKey))
                    setBody(TextContent(wireJson.encodeToString(RadarrMovieMonitorRequest(movieIds = movieIds, monitored = monitored)), ContentType.Application.Json))
                }
            }
        }
    }

    override suspend fun testConnection(baseUrl: String, apiKey: String): Result<Unit> =
        apiResultWithRetry {
            unitRequest {
                httpClient.get(arrApiUrl(baseUrl, "/system/status")) {
                    attachHeaders(arrApiKeyHeaders(apiKey))
                }
            }
        }
}
