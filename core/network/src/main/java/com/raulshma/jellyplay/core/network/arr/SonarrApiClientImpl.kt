package com.raulshma.jellyplay.core.network.arr

import com.raulshma.jellyplay.core.model.arr.ArrBlocklistItem
import com.raulshma.jellyplay.core.model.arr.ArrCalendarItem
import com.raulshma.jellyplay.core.model.arr.ArrCommand
import com.raulshma.jellyplay.core.model.arr.ArrCommandName
import com.raulshma.jellyplay.core.model.arr.ArrDownloadStatus
import com.raulshma.jellyplay.core.model.arr.ArrHistoryItem
import com.raulshma.jellyplay.core.model.arr.ArrMediaType
import com.raulshma.jellyplay.core.model.arr.ArrQueueDeleteOptions
import com.raulshma.jellyplay.core.model.arr.ArrQueueItem
import com.raulshma.jellyplay.core.model.arr.ArrQueueMessage
import com.raulshma.jellyplay.core.model.arr.ArrSeriesEpisode
import com.raulshma.jellyplay.core.model.arr.ArrWantedItem
import com.raulshma.jellyplay.core.network.api.ApiException
import com.raulshma.jellyplay.core.network.api.JsonRequestClient
import com.raulshma.jellyplay.core.network.api.parseJsonRequest
import com.raulshma.jellyplay.core.network.api.parseUnitRequest
import com.raulshma.jellyplay.core.network.seerr.SeerrApiClientImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.decodeFromJsonElement
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
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp-backed implementation of [SonarrApiClient]. Mirrors
 * [com.raulshma.jellyplay.core.network.seerr.SeerrApiClientImpl] and
 * [RadarrApiClientImpl] in structure; see those files for the rationale on
 * shared OkHttp injection, lenient JSON, and [ApiException] routing.
 *
 * Sonarr-specific notes:
 * - `/queue` wraps records in a `{ records: [...] }` envelope (Radarr uses the
 *   same shape). [getQueue] unwraps it via [SonarrQueueResponse].
 * - `/calendar` rows are episodes; the parent series tvdbId + title are
 *   attached via the `series` sub-object so a calendar row can carry stable
 *   identity even when the per-episode tmdbId is absent.
 */
@Singleton
class SonarrApiClientImpl @Inject constructor(
    private val okHttpClient: OkHttpClient,
) : SonarrApiClient {

    private val json: Json = SeerrApiClientImpl.lenientJson

    private fun buildUrl(baseUrl: String, path: String): HttpUrl {
        val base = baseUrl.trimEnd('/')
        return "$base/api/v3$path".toHttpUrl()
    }

    private fun Request.Builder.withApiKey(apiKey: String): Request.Builder =
        header("X-Api-Key", apiKey)

    private suspend fun executeRequest(request: Request): Result<String> {
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
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(ApiException.fromNetwork(e, formatNetworkError(e)))
        }
    }

    private fun parseErrorMessage(code: Int, body: String): String = try {
        val msg = json.parseToJsonElement(body).toString()
        if (msg.isNotBlank()) "HTTP $code: ${msg.take(200)}" else "HTTP $code"
    } catch (_: Exception) {
        "HTTP $code: ${body.take(200)}"
    }

    private fun formatNetworkError(e: Exception): String = when (e) {
        is UnknownHostException -> "Unable to reach Sonarr. Check the URL and your network connection."
        is ConnectException -> "Could not connect to Sonarr. Ensure the server is running and accessible."
        is SocketTimeoutException -> "Connection to Sonarr timed out. The server took too long to respond."
        is IOException -> "Network error reaching Sonarr: ${e.message ?: e.javaClass.simpleName}"
        else -> e.message ?: e.javaClass.simpleName
    }

    /** Bundled [parseJsonRequest] dependencies; see [parseRequest]. */
    private val jsonRequestClient = JsonRequestClient(
        okHttpClient = okHttpClient,
        json = json,
        parseErrorMessage = ::parseErrorMessage,
        formatNetworkError = ::formatNetworkError,
    )

    /** Stream-decoding request execution; see [parseJsonRequest]. */
    private suspend inline fun <reified T> parseRequest(request: Request): Result<T> =
        parseJsonRequest(jsonRequestClient, request)

    private fun HttpUrl.Builder.withDeleteOptions(options: ArrQueueDeleteOptions): HttpUrl.Builder = apply {
        addQueryParameter("removeFromClient", options.removeFromClient.toString())
        addQueryParameter("blocklist", options.blocklist.toString())
        addQueryParameter("skipRedownload", options.skipRedownload.toString())
    }

    private suspend fun deleteRequest(baseUrl: String, apiKey: String, path: String): Result<Unit> {
        val request = Request.Builder()
            .url(buildUrl(baseUrl, path))
            .withApiKey(apiKey)
            .delete()
            .build()
        return parseUnitRequest(jsonRequestClient, request)
    }

    private suspend inline fun postEmpty(
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

    override suspend fun getQueue(baseUrl: String, apiKey: String): Result<List<ArrQueueItem>> {
        val url = buildUrl(baseUrl, "/queue")
            .newBuilder()
            .addQueryParameter("includeSeries", "true")
            .addQueryParameter("includeEpisode", "true")
            .build()
        val request = Request.Builder().url(url).withApiKey(apiKey).get().build()
        return parseRequest<SonarrQueueResponse>(request)
            .map { resp -> resp.records.map { it.toModel() } }
    }

    override suspend fun deleteQueueItem(
        baseUrl: String,
        apiKey: String,
        id: Int,
        options: ArrQueueDeleteOptions,
    ): Result<Unit> {
        val url = buildUrl(baseUrl, "/queue/$id").newBuilder().withDeleteOptions(options).build()
        val request = Request.Builder().url(url).withApiKey(apiKey).delete().build()
        return parseUnitRequest(jsonRequestClient, request)
    }

    override suspend fun deleteQueueItems(
        baseUrl: String,
        apiKey: String,
        ids: List<Int>,
        options: ArrQueueDeleteOptions,
    ): Result<Unit> {
        if (ids.isEmpty()) return Result.success(Unit)
        val url = buildUrl(baseUrl, "/queue/bulk").newBuilder().withDeleteOptions(options).build()
        val body = json.encodeToString(SonarrQueueBulkRequest(ids = ids))
        val request = Request.Builder()
            .url(url)
            .withApiKey(apiKey)
            .delete(body.toRequestBody("application/json".toMediaType()))
            .build()
        return parseUnitRequest(jsonRequestClient, request)
    }

    override suspend fun grabQueueItem(baseUrl: String, apiKey: String, id: Int): Result<Unit> =
        postEmpty(baseUrl, apiKey, "/queue/grab/$id")

    override suspend fun importQueueItem(baseUrl: String, apiKey: String, downloadId: String): Result<Unit> {
        // 2-step manualimport flow (the *arr v3 spec has no queue/import/{id}):
        // 1) GET the candidate import rows for this download-client guid.
        val getUrl = buildUrl(baseUrl, "/manualimport").newBuilder()
            .addQueryParameter("downloadId", downloadId)
            .build()
        val getRequest = Request.Builder().url(getUrl).withApiKey(apiKey).get().build()
        val rows = executeRequest(getRequest).mapCatching { json.decodeFromString<JsonArray>(it) }
        val rowList = rows.getOrElse { return Result.failure(it) }
        if (rowList.isEmpty()) {
            return Result.failure(
                ApiException.fromHttp(404, "No importable files found for this download in Sonarr.")
            )
        }
        // 2) Re-post the rows verbatim to trigger the import. The POST body
        // schema is undocumented in the OpenAPI spec; passing the GET array
        // through unchanged is both the documented usage and immune to schema
        // drift on the 16-field ManualImportResource.
        val postRequest = Request.Builder()
            .url(buildUrl(baseUrl, "/manualimport"))
            .withApiKey(apiKey)
            .post(rowList.toString().toRequestBody("application/json".toMediaType()))
            .build()
        return parseUnitRequest(jsonRequestClient, postRequest)
    }

    override suspend fun getCalendar(
        baseUrl: String,
        apiKey: String,
        start: String,
        end: String,
    ): Result<List<ArrCalendarItem>> {
        val url = buildUrl(baseUrl, "/calendar")
            .newBuilder()
            .addQueryParameter("start", start)
            .addQueryParameter("end", end)
            // includeSeries defaults to false; without it the `series` sub-object
            // is null and tvdbId / title / poster / monitored are silently lost
            // (see SonarrEpisodeResource.toCalendarItem).
            .addQueryParameter("includeSeries", "true")
            .build()
        val request = Request.Builder().url(url).withApiKey(apiKey).get().build()
        return parseRequest<List<SonarrEpisodeResource>>(request)
            .map { list -> list.map { it.toCalendarItem() } }
    }

    override suspend fun getHistory(
        baseUrl: String,
        apiKey: String,
        eventType: Int?,
    ): Result<List<ArrHistoryItem>> {
        val builder = buildUrl(baseUrl, "/history").newBuilder()
        // includeSeries defaults to false; toModel() reads series.tvdbId + title,
        // so request the sub-object or history rows lose their series identity.
        builder.addQueryParameter("includeSeries", "true")
        if (eventType != null) builder.addQueryParameter("eventType", eventType.toString())
        val request = Request.Builder().url(builder.build()).withApiKey(apiKey).get().build()
        return parseRequest<SonarrHistoryResponse>(request)
            .map { resp -> resp.records.map { it.toModel() } }
    }

    override suspend fun getBlocklist(
        baseUrl: String,
        apiKey: String,
        page: Int,
        pageSize: Int,
    ): Result<List<ArrBlocklistItem>> {
        val url = buildUrl(baseUrl, "/blocklist").newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("pageSize", pageSize.toString())
            .addQueryParameter("sortKey", "date")
            .addQueryParameter("sortDirection", "descending")
            .build()
        val request = Request.Builder().url(url).withApiKey(apiKey).get().build()
        return parseRequest<SonarrBlocklistResponse>(request)
            .map { resp -> resp.records.map { it.toModel() } }
    }

    override suspend fun deleteBlocklistItem(baseUrl: String, apiKey: String, id: Int): Result<Unit> =
        deleteRequest(baseUrl, apiKey, "/blocklist/$id")

    override suspend fun deleteBlocklistItems(baseUrl: String, apiKey: String, ids: List<Int>): Result<Unit> {
        if (ids.isEmpty()) return Result.success(Unit)
        val body = json.encodeToString(SonarrIdsBulkRequest(ids = ids))
        val request = Request.Builder()
            .url(buildUrl(baseUrl, "/blocklist/bulk"))
            .withApiKey(apiKey)
            .delete(body.toRequestBody("application/json".toMediaType()))
            .build()
        return parseUnitRequest(jsonRequestClient, request)
    }

    override suspend fun getWanted(
        baseUrl: String,
        apiKey: String,
        page: Int,
        pageSize: Int,
    ): Result<List<ArrWantedItem>> {
        val url = buildUrl(baseUrl, "/wanted/missing").newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("pageSize", pageSize.toString())
            .addQueryParameter("sortKey", "airDateUtc")
            .addQueryParameter("sortDirection", "descending")
            // includeSeries defaults to false; toWantedItem() reads series.tvdbId
            // + title, so request the sub-object or wanted rows lose identity.
            .addQueryParameter("includeSeries", "true")
            .build()
        val request = Request.Builder().url(url).withApiKey(apiKey).get().build()
        return parseRequest<SonarrWantedResponse>(request)
            .map { resp -> resp.records.map { it.toWantedItem() } }
    }

    override suspend fun postCommand(
        baseUrl: String,
        apiKey: String,
        commandName: ArrCommandName,
        seriesId: Int?,
        episodeIds: List<Int>?,
        seasonNumber: Int?,
    ): Result<ArrCommand> {
        val body = SonarrCommandRequest(
            name = commandName.serialName,
            seriesId = seriesId,
            episodeIds = episodeIds,
            seasonNumber = seasonNumber,
        )
        val request = Request.Builder()
            .url(buildUrl(baseUrl, "/command"))
            .withApiKey(apiKey)
            .post(json.encodeToString(body).toRequestBody("application/json".toMediaType()))
            .build()
        return parseRequest<SonarrCommandResource>(request).map { it.toModel() }
    }

    override suspend fun findSeriesByTvdb(baseUrl: String, apiKey: String, tvdbId: Int): Result<Int?> {
        val url = buildUrl(baseUrl, "/series").newBuilder()
            .addQueryParameter("tvdbId", tvdbId.toString())
            .build()
        val request = Request.Builder().url(url).withApiKey(apiKey).get().build()
        // /series?tvdbId= SHOULD return only the matching series, but some
        // Sonarr versions/configs ignore the param and return ALL series. Do
        // NOT trust `firstOrNull()` here — it would pick the wrong series and
        // every downstream lookup (episode, delete, search) would target it.
        // Filter client-side by the tvdbId field on each row.
        return executeRequest(request).mapCatching { body ->
            val arr = json.decodeFromString<JsonArray>(body)
            arr.asSequence()
                .map { json.decodeFromJsonElement(SonarrSeriesResource.serializer(), it) }
                .firstOrNull { it.tvdbId == tvdbId }
                ?.id
        }
    }

    override suspend fun getEpisodeInfo(
        baseUrl: String,
        apiKey: String,
        seriesId: Int,
        seasonNumber: Int,
        episodeNumber: Int,
    ): Result<SonarrEpisodeInfo?> {
        // Fast path: query the single season + filter client-side.
        val seasonUrl = buildUrl(baseUrl, "/episode").newBuilder()
            .addQueryParameter("seriesId", seriesId.toString())
            .addQueryParameter("seasonNumber", seasonNumber.toString())
            .build()
        val seasonReq = Request.Builder().url(seasonUrl).withApiKey(apiKey).get().build()
        val fastPath = executeRequest(seasonReq).mapCatching { body ->
            parseEpisodeList(body)
                .firstOrNull { it.seasonNumber == seasonNumber && it.episodeNumber == episodeNumber }
        }
        // Fast-path network/parse error → propagate (don't mask with fallback).
        if (fastPath.isFailure) {
            return Result.failure(fastPath.exceptionOrNull() ?: IllegalStateException("Episode lookup failed"))
        }
        // Fast-path hit → done.
        fastPath.getOrNull()?.let { hit ->
            return Result.success(hit.toInfo())
        }

        // Miss (episode not in this season under Jellyfin's numbering). Fall
        // back to ALL episodes and match on episodeNumber across seasons —
        // handles split seasons, anime absolute numbering, specials placement.
        return getAllEpisodes(baseUrl, apiKey, seriesId).map { all ->
            all.firstOrNull { it.episodeNumber == episodeNumber }?.toInfo()
        }
    }

    override suspend fun getSeasonSummaries(
        baseUrl: String,
        apiKey: String,
        seriesId: Int,
    ): Result<List<SonarrSeasonSummary>> =
        getAllEpisodes(baseUrl, apiKey, seriesId).map { all ->
            all.groupBy { it.seasonNumber }
                .toSortedMap()
                .map { (season, eps) ->
                    SonarrSeasonSummary(season, eps.map { it.episodeNumber }.sorted())
                }
        }

    /**
     * Fetches every episode for [seriesId] (no season filter) and decodes to
     * the raw lookup resource. Shared by [getEpisodeInfo]'s fallback path and
     * [getSeasonSummaries].
     */
    private suspend fun getAllEpisodes(
        baseUrl: String,
        apiKey: String,
        seriesId: Int,
    ): Result<List<SonarrEpisodeLookupResource>> {
        val url = buildUrl(baseUrl, "/episode").newBuilder()
            .addQueryParameter("seriesId", seriesId.toString())
            .build()
        val request = Request.Builder().url(url).withApiKey(apiKey).get().build()
        return executeRequest(request).map { body -> parseEpisodeList(body) }
    }

    private fun parseEpisodeList(body: String): List<SonarrEpisodeLookupResource> {
        val arr = json.decodeFromString<JsonArray>(body)
        return arr.map { json.decodeFromJsonElement(SonarrEpisodeLookupResource.serializer(), it) }
    }

    override suspend fun deleteEpisodeFile(baseUrl: String, apiKey: String, episodeFileId: Int): Result<Unit> =
        deleteRequest(baseUrl, apiKey, "/episodeFile/$episodeFileId")

    override suspend fun monitorEpisodes(
        baseUrl: String,
        apiKey: String,
        episodeIds: List<Int>,
        monitored: Boolean,
    ): Result<Unit> {
        if (episodeIds.isEmpty()) return Result.success(Unit)
        val body = json.encodeToString(
            SonarrEpisodeMonitorRequest(episodeIds = episodeIds, monitored = monitored),
        )
        val request = Request.Builder()
            .url(buildUrl(baseUrl, "/episode/monitor"))
            .withApiKey(apiKey)
            .put(body.toRequestBody("application/json".toMediaType()))
            .build()
        return parseUnitRequest(jsonRequestClient, request)
    }

    override suspend fun getSeriesInfo(baseUrl: String, apiKey: String, tvdbId: Int): Result<SonarrSeriesInfo?> {
        val url = buildUrl(baseUrl, "/series").newBuilder()
            .addQueryParameter("tvdbId", tvdbId.toString())
            .build()
        val request = Request.Builder().url(url).withApiKey(apiKey).get().build()
        // Same defensive client-side filter as findSeriesByTvdb: some Sonarr
        // versions ignore the ?tvdbId= param and return ALL series.
        return executeRequest(request).mapCatching { body ->
            val arr = json.decodeFromString<JsonArray>(body)
            arr.asSequence()
                .map { json.decodeFromJsonElement(SonarrSeriesResource.serializer(), it) }
                .firstOrNull { it.tvdbId == tvdbId }
                ?.let { SonarrSeriesInfo(id = it.id, title = it.title, monitored = it.monitored, path = it.path) }
        }
    }

    override suspend fun getEpisodesForSeries(
        baseUrl: String,
        apiKey: String,
        seriesId: Int,
    ): Result<List<ArrSeriesEpisode>> {
        // Reuse getAllEpisodes' /episode?seriesId= path but decode the rich
        // projection (title, airDate, overview, file size/quality) the
        // management UI needs. getAllEpisodes itself decodes the leaner
        // SonarrEpisodeLookupResource, so we issue the request directly here.
        val url = buildUrl(baseUrl, "/episode").newBuilder()
            .addQueryParameter("seriesId", seriesId.toString())
            .build()
        val request = Request.Builder().url(url).withApiKey(apiKey).get().build()
        return executeRequest(request).mapCatching { body ->
            val arr = json.decodeFromString<JsonArray>(body)
            arr.map { json.decodeFromJsonElement(SonarrManagedEpisodeResource.serializer(), it) }
                .map { it.toModel() }
        }
    }

    override suspend fun testConnection(baseUrl: String, apiKey: String): Result<Unit> {
        val request = Request.Builder()
            .url(buildUrl(baseUrl, "/system/status"))
            .withApiKey(apiKey)
            .get()
            .build()
        return parseUnitRequest(jsonRequestClient, request)
    }

    // ── Sonarr v3 DTOs (private; mapped to core/model types) ───────────────

    @Serializable
    private data class SonarrQueueResponse(
        val records: List<SonarrQueueResource> = emptyList(),
    )

    @Serializable
    private data class SonarrQueueResource(
        val id: Int = 0,
        val downloadId: String? = null,
        val size: Double? = null,
        val sizeleft: Double? = null,
        val timeleft: String? = null,
        val status: String? = null,
        val trackedDownloadStatus: String? = null,
        val trackedDownloadState: String? = null,
        val protocol: String? = null,
        val downloadClient: String? = null,
        val indexer: String? = null,
        val outputPath: String? = null,
        val quality: SonarrQuality? = null,
        val languages: List<SonarrLanguage> = emptyList(),
        val customFormats: List<SonarrCustomFormat> = emptyList(),
        val statusMessages: List<SonarrStatusMessage> = emptyList(),
        val series: SonarrSeriesResource? = null,
        val episode: SonarrEpisodeResource? = null,
    )

    @Serializable
    private data class SonarrQuality(
        @SerialName("quality") val quality: SonarrQualityName? = null,
    ) {
        val name: String? get() = quality?.name
    }

    @Serializable
    private data class SonarrQualityName(val name: String? = null)

    @Serializable
    private data class SonarrLanguage(val name: String? = null)

    @Serializable
    private data class SonarrCustomFormat(val name: String? = null)

    @Serializable
    private data class SonarrStatusMessage(
        val title: String? = null,
        val messages: List<String> = emptyList(),
    )

    @Serializable
    private data class SonarrSeriesResource(
        val id: Int = 0,
        val title: String = "",
        val tvdbId: Int? = null,
        val monitored: Boolean = false,
        val path: String? = null,
        val images: List<SonarrMediaCover> = emptyList(),
    )

    @Serializable
    private data class SonarrEpisodeResource(
        val id: Int = 0,
        val title: String = "",
        val airDateUtc: String? = null,
        val hasFile: Boolean = false,
        val overview: String? = null,
        val series: SonarrSeriesResource? = null,
    )

    @Serializable
    private data class SonarrMediaCover(
        @SerialName("coverType") val coverType: String = "",
        @SerialName("url") val url: String? = null,
        @SerialName("remoteUrl") val remoteUrl: String? = null,
    )

    @Serializable
    private data class SonarrHistoryResponse(
        val records: List<SonarrHistoryRecord> = emptyList(),
    )

    @Serializable
    private data class SonarrHistoryRecord(
        val id: Int = 0,
        val eventType: String? = null,
        val date: String? = null,
        val data: Map<String, String> = emptyMap(),
        val series: SonarrSeriesResource? = null,
    )

    @Serializable
    private data class SonarrQueueBulkRequest(val ids: List<Int>)

    @Serializable
    private data class SonarrIdsBulkRequest(val ids: List<Int>)

    /** Body for `PUT /api/v3/episode/monitor`. */
    @Serializable
    private data class SonarrEpisodeMonitorRequest(
        val episodeIds: List<Int>,
        val monitored: Boolean,
    )

    /**
     * Projection of `/episode` rows used by [getEpisodeInfo]. Carries the
     * fields the delete & re-download flow needs (id, episodeFileId, hasFile,
     * monitored) plus the season/episode numbers for client-side filtering.
     */
    @Serializable
    private data class SonarrEpisodeLookupResource(
        val id: Int = 0,
        val seasonNumber: Int = 0,
        val episodeNumber: Int = 0,
        val episodeFileId: Int = 0,
        val hasFile: Boolean = false,
        val monitored: Boolean = false,
    ) {
        fun toInfo() = SonarrEpisodeInfo(
            id = id,
            episodeFileId = episodeFileId,
            hasFile = hasFile,
            monitored = monitored,
            seasonNumber = seasonNumber,
        )
    }

    /**
     * Rich episode projection for the "Manage Series" screen. Carries every
     * field [ArrSeriesEpisode] needs: season/episode/absolute numbers, title,
     * air date, overview, monitored flag, and (when a file exists) the nested
     * file resource for id + size + quality. Mapped to [ArrSeriesEpisode].
     */
    @Serializable
    private data class SonarrManagedEpisodeResource(
        val id: Int = 0,
        val seasonNumber: Int = 0,
        val episodeNumber: Int = 0,
        val absoluteEpisodeNumber: Int? = null,
        val title: String = "",
        val airDateUtc: String? = null,
        val overview: String? = null,
        val hasFile: Boolean = false,
        val monitored: Boolean = false,
        val episodeFileId: Int = 0,
        val episodeFile: SonarrEpisodeFileResource? = null,
    ) {
        fun toModel() = ArrSeriesEpisode(
            id = id,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            absoluteEpisodeNumber = absoluteEpisodeNumber,
            title = title.ifBlank { "Episode $episodeNumber" },
            airDateUtc = airDateUtc,
            overview = overview,
            hasFile = hasFile,
            monitored = monitored,
            episodeFileId = episodeFileId,
            fileSizeBytes = episodeFile?.size?.toLong(),
            quality = episodeFile?.quality?.name,
        )
    }

    @Serializable
    private data class SonarrEpisodeFileResource(
        val id: Int = 0,
        val size: Double? = null,
        val quality: SonarrQuality? = null,
    )

    @Serializable
    private data class SonarrBlocklistResponse(
        val records: List<SonarrBlocklistRecord> = emptyList(),
    )

    @Serializable
    private data class SonarrBlocklistRecord(
        val id: Int = 0,
        val date: String? = null,
        val protocol: String? = null,
        val indexer: String? = null,
        val message: String? = null,
        val series: SonarrSeriesResource? = null,
    )

    @Serializable
    private data class SonarrWantedResponse(
        val records: List<SonarrWantedRecord> = emptyList(),
    )

    @Serializable
    private data class SonarrWantedRecord(
        val id: Int = 0,
        val title: String = "",
        val airDateUtc: String? = null,
        val hasFile: Boolean = false,
        val overview: String? = null,
        val series: SonarrSeriesResource? = null,
    )

    @Serializable
    private data class SonarrCommandRequest(
        val name: String,
        val seriesId: Int? = null,
        val episodeIds: List<Int>? = null,
        val seasonNumber: Int? = null,
    )

    @Serializable
    private data class SonarrCommandResource(
        val id: Int = 0,
        val name: String = "",
        val status: String = "",
        val message: String? = null,
        val queued: String? = null,
        val started: String? = null,
        val ended: String? = null,
    )

    private fun SonarrQueueResource.toModel(): ArrQueueItem {
        val sizeBytes = size?.toLong()
        val sizeLeft = sizeleft?.toLong()
        val progress = if (size != null && size > 0.0 && sizeleft != null) {
            ((size - sizeleft) / size).toFloat().coerceIn(0f, 1f)
        } else 0f
        return ArrQueueItem(
            queueId = id,
            downloadId = downloadId,
            tvdbId = series?.tvdbId,
            title = buildString {
                series?.title?.let { append(it) }
                episode?.let { ep ->
                    if (isNotEmpty()) append(" - ")
                    if (ep.title.isNotBlank()) append(ep.title)
                }
                if (isEmpty()) append("Unknown")
            },
            status = ArrDownloadStatus.fromApi(status, trackedDownloadStatus, trackedDownloadState),
            trackedDownloadStatus = trackedDownloadStatus,
            trackedDownloadState = trackedDownloadState,
            progress = progress,
            sizeBytes = sizeBytes,
            sizeLeft = sizeLeft,
            timeLeft = timeleft,
            protocol = protocol,
            downloadClient = downloadClient,
            indexer = indexer,
            outputPath = outputPath,
            quality = quality?.name,
            languages = languages.mapNotNull { it.name }.filter { it.isNotBlank() },
            customFormats = customFormats.mapNotNull { it.name }.filter { it.isNotBlank() },
            messages = statusMessages.flatMap { sm ->
                sm.messages.map { msg -> ArrQueueMessage(title = sm.title, message = msg) }
            },
        )
    }

    private fun SonarrEpisodeResource.toCalendarItem(): ArrCalendarItem {
        val series = series
        return ArrCalendarItem(
            tvdbId = series?.tvdbId,
            title = series?.title ?: title.ifBlank { "Unknown" },
            mediaType = ArrMediaType.SERIES,
            airDateUtc = airDateUtc,
            hasFile = hasFile,
            monitored = series?.monitored ?: true,
            overview = overview,
            // Prefer remoteUrl (absolute); fall back to url (relative path),
            // which behind a reverse proxy is often the only field populated.
            posterPath = series?.images?.firstOrNull { it.coverType == "poster" }
                ?.let { it.remoteUrl ?: it.url },
        )
    }

    private fun SonarrHistoryRecord.toModel(): ArrHistoryItem = ArrHistoryItem(
        historyId = id,
        eventType = eventType ?: "",
        tvdbId = series?.tvdbId,
        title = series?.title ?: "Unknown",
        dateUtc = date,
        data = data,
    )

    private fun SonarrBlocklistRecord.toModel(): ArrBlocklistItem = ArrBlocklistItem(
        id = id,
        tvdbId = series?.tvdbId,
        title = series?.title ?: "Unknown",
        dateUtc = date,
        protocol = protocol,
        indexer = indexer,
        message = message,
    )

    private fun SonarrWantedRecord.toWantedItem(): ArrWantedItem = ArrWantedItem(
        id = id,
        tvdbId = series?.tvdbId,
        title = series?.title ?: title.ifBlank { "Unknown" },
        airDateUtc = airDateUtc,
        hasFile = hasFile,
        monitored = true,
        overview = overview,
        mediaType = ArrMediaType.SERIES,
    )

    private fun SonarrCommandResource.toModel(): ArrCommand = ArrCommand(
        id = id,
        name = name,
        status = status,
        message = message,
        dateUtc = queued ?: started ?: ended,
    )
}
