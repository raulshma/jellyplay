package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.arr.ArrBlocklistItem
import com.raulshma.jellyplay.core.model.arr.ArrCalendarItem
import com.raulshma.jellyplay.core.model.arr.ArrCommand
import com.raulshma.jellyplay.core.model.arr.ArrCommandName
import com.raulshma.jellyplay.core.model.arr.ArrHistoryItem
import com.raulshma.jellyplay.core.model.arr.ArrQueueDeleteOptions
import com.raulshma.jellyplay.core.model.arr.ArrQueueItem
import com.raulshma.jellyplay.core.model.arr.ArrSeriesEpisode
import com.raulshma.jellyplay.core.model.arr.ArrWantedItem
import com.raulshma.jellyplay.core.network.arr.SonarrBlocklistResponse
import com.raulshma.jellyplay.core.network.arr.SonarrCommandRequest
import com.raulshma.jellyplay.core.network.arr.SonarrCommandResource
import com.raulshma.jellyplay.core.network.arr.SonarrEpisodeLookupResource
import com.raulshma.jellyplay.core.network.arr.SonarrEpisodeMonitorRequest
import com.raulshma.jellyplay.core.network.arr.SonarrEpisodeResource
import com.raulshma.jellyplay.core.network.arr.SonarrHistoryResponse
import com.raulshma.jellyplay.core.network.arr.SonarrIdsBulkRequest
import com.raulshma.jellyplay.core.network.arr.SonarrManagedEpisodeResource
import com.raulshma.jellyplay.core.network.arr.SonarrQueueBulkRequest
import com.raulshma.jellyplay.core.network.arr.SonarrQueueResponse
import com.raulshma.jellyplay.core.network.arr.SonarrSeriesResource
import com.raulshma.jellyplay.core.network.arr.SonarrWantedResponse
import com.raulshma.jellyplay.core.network.arr.SonarrApiClient
import com.raulshma.jellyplay.core.network.arr.SonarrEpisodeInfo
import com.raulshma.jellyplay.core.network.arr.SonarrSeasonSummary
import com.raulshma.jellyplay.core.network.arr.SonarrSeriesInfo
import com.raulshma.jellyplay.core.network.arr.arrApiUrl
import com.raulshma.jellyplay.core.network.arr.arrApiKeyHeaders
import com.raulshma.jellyplay.core.network.arr.arrHttpErrorMessage
import com.raulshma.jellyplay.core.network.arr.filterSeriesByTvdb
import com.raulshma.jellyplay.core.network.arr.toArrBlocklistItem
import com.raulshma.jellyplay.core.network.arr.toArrCommand
import com.raulshma.jellyplay.core.network.arr.toArrHistoryItem
import com.raulshma.jellyplay.core.network.arr.toArrQueueItem
import com.raulshma.jellyplay.core.network.arr.toArrSeriesEpisode
import com.raulshma.jellyplay.core.network.arr.toArrWantedItem
import com.raulshma.jellyplay.core.network.arr.toCalendarItem
import com.raulshma.jellyplay.core.network.arr.toQueryPairs
import com.raulshma.jellyplay.core.network.arr.toSonarrEpisodeInfo
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
import kotlinx.serialization.json.decodeFromJsonElement

private const val SONARR_TIMEOUT_MESSAGE =
    "Connection to Sonarr timed out. The server took too long to respond."

/** The IOException branch of `SonarrApiClientImpl.formatNetworkError`. */
private fun sonarrIoFailureMessage(e: Throwable): String =
    "Network error reaching Sonarr: ${e.message ?: e::class.simpleName ?: ""}"

/** The `else` branch of `SonarrApiClientImpl.formatNetworkError`. */
private fun sonarrUnclassifiedFailureMessage(e: Throwable): String =
    e.message ?: e::class.simpleName ?: ""

/**
 * The wasmJs [SonarrApiClient] — a hand-rolled Ktor replacement for the
 * jvmShared `SonarrApiClientImpl` + `ResilientSonarrApiClient` pair (OkHttp).
 * The `/api/v3` paths, query params (`includeSeries`/`includeEpisode`, the
 * wanted `airDateUtc` sort key, the queue-delete option trio), request
 * bodies, the 2-step manualimport flow, the `{ records }` envelope
 * unwrapping, the UNTRUSTED-`?tvdbId=` client-side series filter, the
 * getEpisodeInfo fast-path + cross-season fallback, and every error/network
 * text mirror the JVM implementation request-for-request; decode + mapping
 * go through the commonMain [com.raulshma.jellyplay.core.network.arr] wire
 * DTOs — field-for-field transcriptions of the JVM impl's private nested
 * DTOs.
 *
 * Retry lives HERE (`apiResultWithRetry`, max 4 =
 * `ResilientSonarrApiClient.MAX_RETRIES`) instead of in a DI-level Resilient
 * wrapper. See [ArrSeerrApiSupport] for the full wasm delta list (transport
 * taxonomy collapse, Retry-After honoring, decode-failure wrapping).
 */
class KtorWasmSonarrApiClient(
    httpClient: HttpClient,
) : ArrSeerrApiSupport(
    httpClient = httpClient,
    httpFailureMessage = ::arrHttpErrorMessage,
    timeoutFailureMessage = SONARR_TIMEOUT_MESSAGE,
    ioFailureMessage = ::sonarrIoFailureMessage,
    unclassifiedFailureMessage = ::sonarrUnclassifiedFailureMessage,
), SonarrApiClient {

    override suspend fun getQueue(baseUrl: String, apiKey: String): Result<List<ArrQueueItem>> =
        apiResultWithRetry {
            getAndParse<SonarrQueueResponse>(
                url = arrApiUrl(baseUrl, "/queue"),
                headers = arrApiKeyHeaders(apiKey),
                query = listOf("includeSeries" to "true", "includeEpisode" to "true"),
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
                    setBody(TextContent(wireJson.encodeToString(SonarrQueueBulkRequest(ids = ids)), ContentType.Application.Json))
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
                throw ApiException.fromHttp(404, "No importable files found for this download in Sonarr.")
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
        // includeSeries defaults to false; without it the `series` sub-object
        // is null and tvdbId / title / poster / monitored are silently lost
        // (see SonarrEpisodeResource.toCalendarItem).
        getAndParse<List<SonarrEpisodeResource>>(
            url = arrApiUrl(baseUrl, "/calendar"),
            headers = arrApiKeyHeaders(apiKey),
            query = listOf("start" to start, "end" to end, "includeSeries" to "true"),
        ).map { it.toCalendarItem() }
    }

    override suspend fun getHistory(
        baseUrl: String,
        apiKey: String,
        eventType: Int?,
    ): Result<List<ArrHistoryItem>> = apiResultWithRetry {
        // includeSeries defaults to false; the mapper reads series.tvdbId +
        // title, so request the sub-object or history rows lose their series
        // identity.
        val query = buildList {
            add("includeSeries" to "true")
            if (eventType != null) add("eventType" to eventType.toString())
        }
        getAndParse<SonarrHistoryResponse>(
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
        getAndParse<SonarrBlocklistResponse>(
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
                    setBody(TextContent(wireJson.encodeToString(SonarrIdsBulkRequest(ids = ids)), ContentType.Application.Json))
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
        // includeSeries defaults to false; the mapper reads series.tvdbId +
        // title, so request the sub-object or wanted rows lose identity.
        getAndParse<SonarrWantedResponse>(
            url = arrApiUrl(baseUrl, "/wanted/missing"),
            headers = arrApiKeyHeaders(apiKey),
            query = listOf(
                "page" to page.toString(),
                "pageSize" to pageSize.toString(),
                "sortKey" to "airDateUtc",
                "sortDirection" to "descending",
                "includeSeries" to "true",
            ),
        ).records.map { it.toArrWantedItem() }
    }

    override suspend fun postCommand(
        baseUrl: String,
        apiKey: String,
        commandName: ArrCommandName,
        seriesId: Int?,
        episodeIds: List<Int>?,
        seasonNumber: Int?,
    ): Result<ArrCommand> = apiResultWithRetry {
        val body = SonarrCommandRequest(
            name = commandName.serialName,
            seriesId = seriesId,
            episodeIds = episodeIds,
            seasonNumber = seasonNumber,
        )
        postAndParse<SonarrCommandResource>(
            url = arrApiUrl(baseUrl, "/command"),
            headers = arrApiKeyHeaders(apiKey),
            bodyText = wireJson.encodeToString(body),
        ).toArrCommand()
    }

    override suspend fun findSeriesByTvdb(baseUrl: String, apiKey: String, tvdbId: Int): Result<Int?> =
        apiResultWithRetry {
            // /series?tvdbId= SHOULD return only the matching series, but some
            // Sonarr versions/configs ignore the param and return ALL series.
            // Do NOT trust `firstOrNull()` here — it would pick the wrong
            // series and every downstream lookup (episode, delete, search)
            // would target it. Filter client-side by the tvdbId field on each
            // row (the same defensive filter the JVM impl applies).
            val rows = getAllSeries(baseUrl, apiKey, tvdbId)
            filterSeriesByTvdb(rows, tvdbId)?.id
        }

    override suspend fun getEpisodeInfo(
        baseUrl: String,
        apiKey: String,
        seriesId: Int,
        seasonNumber: Int,
        episodeNumber: Int,
    ): Result<SonarrEpisodeInfo?> = apiResultWithRetry {
        // Fast path: query the single season + filter client-side. A failure
        // here propagates (it throws) — never masked with the fallback.
        val seasonRows = executeForText {
            httpClient.get(arrApiUrl(baseUrl, "/episode")) {
                attachHeaders(arrApiKeyHeaders(apiKey))
                attachQuery(
                    listOf("seriesId" to seriesId.toString(), "seasonNumber" to seasonNumber.toString()),
                )
            }
        }
        parseEpisodeList(seasonRows)
            .firstOrNull { it.seasonNumber == seasonNumber && it.episodeNumber == episodeNumber }
            ?.let { return@apiResultWithRetry it.toSonarrEpisodeInfo() }

        // Miss (episode not in this season under Jellyfin's numbering). Fall
        // back to ALL episodes and match on episodeNumber across seasons —
        // handles split seasons, anime absolute numbering, specials placement.
        getAllEpisodes(baseUrl, apiKey, seriesId)
            .firstOrNull { it.episodeNumber == episodeNumber }
            ?.toSonarrEpisodeInfo()
    }

    override suspend fun getSeasonSummaries(
        baseUrl: String,
        apiKey: String,
        seriesId: Int,
    ): Result<List<SonarrSeasonSummary>> = apiResultWithRetry {
        // JVM used `toSortedMap()` (java.util) — the commonMain equivalent
        // sorts the entries, producing the same ascending-season order.
        getAllEpisodes(baseUrl, apiKey, seriesId)
            .groupBy { it.seasonNumber }
            .entries
            .sortedBy { it.key }
            .map { (season, eps) -> SonarrSeasonSummary(season, eps.map { it.episodeNumber }.sorted()) }
    }

    /**
     * Fetches every episode for [seriesId] (no season filter) and decodes to
     * the raw lookup resource. Shared by [getEpisodeInfo]'s fallback path and
     * [getSeasonSummaries] — the JVM `getAllEpisodes`.
     */
    private suspend fun getAllEpisodes(
        baseUrl: String,
        apiKey: String,
        seriesId: Int,
    ): List<SonarrEpisodeLookupResource> {
        val body = executeForText {
            httpClient.get(arrApiUrl(baseUrl, "/episode")) {
                attachHeaders(arrApiKeyHeaders(apiKey))
                attachQuery(listOf("seriesId" to seriesId.toString()))
            }
        }
        return parseEpisodeList(body)
    }

    /** `SonarrApiClientImpl.parseEpisodeList`: JsonArray → per-element decode (lenient per-row semantics preserved). */
    private fun parseEpisodeList(body: String): List<SonarrEpisodeLookupResource> {
        val arr = wireJson.decodeFromString<JsonArray>(body)
        return arr.map { wireJson.decodeFromJsonElement(SonarrEpisodeLookupResource.serializer(), it) }
    }

    override suspend fun deleteEpisodeFile(baseUrl: String, apiKey: String, episodeFileId: Int): Result<Unit> =
        apiResultWithRetry {
            unitRequest {
                httpClient.delete(arrApiUrl(baseUrl, "/episodeFile/$episodeFileId")) {
                    attachHeaders(arrApiKeyHeaders(apiKey))
                }
            }
        }

    override suspend fun monitorEpisodes(
        baseUrl: String,
        apiKey: String,
        episodeIds: List<Int>,
        monitored: Boolean,
    ): Result<Unit> {
        if (episodeIds.isEmpty()) return Result.success(Unit)
        return apiResultWithRetry {
            unitRequest {
                httpClient.put(arrApiUrl(baseUrl, "/episode/monitor")) {
                    attachHeaders(arrApiKeyHeaders(apiKey))
                    setBody(TextContent(wireJson.encodeToString(SonarrEpisodeMonitorRequest(episodeIds = episodeIds, monitored = monitored)), ContentType.Application.Json))
                }
            }
        }
    }

    override suspend fun getSeriesInfo(baseUrl: String, apiKey: String, tvdbId: Int): Result<SonarrSeriesInfo?> =
        apiResultWithRetry {
            // Same defensive client-side filter as findSeriesByTvdb: some
            // Sonarr versions ignore the ?tvdbId= param and return ALL series.
            val rows = getAllSeries(baseUrl, apiKey, tvdbId)
            filterSeriesByTvdb(rows, tvdbId)?.let {
                SonarrSeriesInfo(id = it.id, title = it.title, monitored = it.monitored, path = it.path)
            }
        }

    /**
     * `GET /series?tvdbId=` raw rows for [findSeriesByTvdb] / [getSeriesInfo]
     * — both apply the client-side tvdbId filter to whatever comes back.
     */
    private suspend fun getAllSeries(baseUrl: String, apiKey: String, tvdbId: Int): List<SonarrSeriesResource> {
        val body = executeForText {
            httpClient.get(arrApiUrl(baseUrl, "/series")) {
                attachHeaders(arrApiKeyHeaders(apiKey))
                attachQuery(listOf("tvdbId" to tvdbId.toString()))
            }
        }
        val arr = wireJson.decodeFromString<JsonArray>(body)
        return arr.map { wireJson.decodeFromJsonElement(SonarrSeriesResource.serializer(), it) }
    }

    override suspend fun getEpisodesForSeries(
        baseUrl: String,
        apiKey: String,
        seriesId: Int,
    ): Result<List<ArrSeriesEpisode>> =
        // The rich projection the management UI needs. getAllEpisodes decodes
        // the leaner lookup resource, so this issues its own request (same
        // path + params) decoding the managed resource — the JVM structure.
        apiResultWithRetry {
            val body = executeForText {
                httpClient.get(arrApiUrl(baseUrl, "/episode")) {
                    attachHeaders(arrApiKeyHeaders(apiKey))
                    attachQuery(listOf("seriesId" to seriesId.toString()))
                }
            }
            val arr = wireJson.decodeFromString<JsonArray>(body)
            arr.map { wireJson.decodeFromJsonElement(SonarrManagedEpisodeResource.serializer(), it) }
                .map { it.toArrSeriesEpisode() }
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
