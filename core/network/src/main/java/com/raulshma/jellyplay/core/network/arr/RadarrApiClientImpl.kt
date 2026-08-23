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
 * OkHttp-backed implementation of [RadarrApiClient]. Mirrors
 * [com.raulshma.jellyplay.core.network.seerr.SeerrApiClientImpl] verbatim:
 * injects the shared unqualified [OkHttpClient], reuses
 * [SeerrApiClientImpl.lenientJson] (same lenient config the Seerr + TMDB
 * clients use), and routes failures through [ApiException.fromHttp] /
 * [ApiException.fromNetwork] so [com.raulshma.jellyplay.core.network.RetryPolicy]
 * can classify retryability.
 *
 * Radarr's v3 API uses `X-Api-Key` for auth — the same header name Seerr uses —
 * so no new credential type is required.
 */
@Singleton
class RadarrApiClientImpl @Inject constructor(
    private val okHttpClient: OkHttpClient,
) : RadarrApiClient {

    private val json: Json = SeerrApiClientImpl.lenientJson

    private fun buildUrl(baseUrl: String, path: String): HttpUrl {
        val base = baseUrl.trimEnd('/')
        // Radarr's API root is /api/v3; the trailing path is appended as-is so
        // callers can pass query strings via [HttpUrl.Builder] after the fact.
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
            // CancellationException must propagate for structured-concurrency correctness.
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(ApiException.fromNetwork(e, formatNetworkError(e)))
        }
    }

    private fun parseErrorMessage(code: Int, body: String): String = try {
        val errorJson = json.parseToJsonElement(body)
        // Radarr errors: [{ "errorMessage": "..." }] or { "message": "..." }
        val msg = errorJson.toString()
        if (msg.isNotBlank()) "HTTP $code: ${msg.take(200)}" else "HTTP $code"
    } catch (_: Exception) {
        "HTTP $code: ${body.take(200)}"
    }

    private fun formatNetworkError(e: Exception): String = when (e) {
        is UnknownHostException -> "Unable to reach Radarr. Check the URL and your network connection."
        is ConnectException -> "Could not connect to Radarr. Ensure the server is running and accessible."
        is SocketTimeoutException -> "Connection to Radarr timed out. The server took too long to respond."
        is IOException -> "Network error reaching Radarr: ${e.message ?: e.javaClass.simpleName}"
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

    /**
     * Applies the [ArrQueueDeleteOptions] as query params on a DELETE URL.
     * Shared by single + bulk queue deletes.
     */
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
        // includeMovie=true attaches the movie resource so we can pull tmdbId + title.
        // Radarr v3 (like Sonarr) wraps the page in a { records, page, pageSize,
        // totalRecords } envelope — decoded via RadarrQueueResponse and unwrapped here.
        val url = buildUrl(baseUrl, "/queue")
            .newBuilder()
            .addQueryParameter("includeMovie", "true")
            .build()
        val request = Request.Builder().url(url).withApiKey(apiKey).get().build()
        return parseRequest<RadarrQueueResponse>(request)
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
        val body = json.encodeToString(RadarrQueueBulkRequest(ids = ids))
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
                ApiException.fromHttp(404, "No importable files found for this download in Radarr.")
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
            .build()
        val request = Request.Builder().url(url).withApiKey(apiKey).get().build()
        return parseRequest<List<RadarrMovieResource>>(request)
            .map { list -> list.map { it.toCalendarItem() } }
    }

    override suspend fun getHistory(
        baseUrl: String,
        apiKey: String,
        eventType: Int?,
    ): Result<List<ArrHistoryItem>> {
        val builder = buildUrl(baseUrl, "/history").newBuilder()
        // includeMovie defaults to false; toModel() reads movie.tmdbId + title,
        // so request the sub-object or history rows lose their movie identity.
        builder.addQueryParameter("includeMovie", "true")
        if (eventType != null) builder.addQueryParameter("eventType", eventType.toString())
        val request = Request.Builder().url(builder.build()).withApiKey(apiKey).get().build()
        return parseRequest<RadarrHistoryResponse>(request)
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
        return parseRequest<RadarrBlocklistResponse>(request)
            .map { resp -> resp.records.map { it.toModel() } }
    }

    override suspend fun deleteBlocklistItem(baseUrl: String, apiKey: String, id: Int): Result<Unit> =
        deleteRequest(baseUrl, apiKey, "/blocklist/$id")

    override suspend fun deleteBlocklistItems(baseUrl: String, apiKey: String, ids: List<Int>): Result<Unit> {
        if (ids.isEmpty()) return Result.success(Unit)
        val body = json.encodeToString(RadarrIdsBulkRequest(ids = ids))
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
            .addQueryParameter("sortKey", "inCinemas")
            .addQueryParameter("sortDirection", "descending")
            .build()
        val request = Request.Builder().url(url).withApiKey(apiKey).get().build()
        return parseRequest<RadarrWantedResponse>(request)
            .map { resp -> resp.records.map { it.toWantedItem() } }
    }

    override suspend fun postCommand(
        baseUrl: String,
        apiKey: String,
        commandName: ArrCommandName,
        movieIds: List<Int>?,
        episodeIds: List<Int>?,
    ): Result<ArrCommand> {
        val body = RadarrCommandRequest(
            name = commandName.serialName,
            movieIds = movieIds,
            movieId = movieIds?.firstOrNull(),
        )
        val request = Request.Builder()
            .url(buildUrl(baseUrl, "/command"))
            .withApiKey(apiKey)
            .post(json.encodeToString(body).toRequestBody("application/json".toMediaType()))
            .build()
        return parseRequest<RadarrCommandResource>(request).map { it.toModel() }
    }

    override suspend fun findMovieIdByTmdb(baseUrl: String, apiKey: String, tmdbId: Int): Result<Int?> {
        // /api/v3/movie?tmdbId= returns a single-element array (or empty when
        // no match). Decoded as a list rather than a bare object so the
        // not-tracked case is a clean empty list instead of a parse error.
        val url = buildUrl(baseUrl, "/movie").newBuilder()
            .addQueryParameter("tmdbId", tmdbId.toString())
            .build()
        val request = Request.Builder().url(url).withApiKey(apiKey).get().build()
        return parseRequest<List<RadarrMovieResource>>(request)
            .map { list -> list.firstOrNull()?.id }
    }

    override suspend fun getMovieForTmdb(baseUrl: String, apiKey: String, tmdbId: Int): Result<RadarrMovieInfo?> {
        val url = buildUrl(baseUrl, "/movie").newBuilder()
            .addQueryParameter("tmdbId", tmdbId.toString())
            .build()
        val request = Request.Builder().url(url).withApiKey(apiKey).get().build()
        return parseRequest<List<RadarrMovieResource>>(request)
            .map { list ->
                list.firstOrNull()?.let {
                    RadarrMovieInfo(
                        id = it.id,
                        movieFileId = it.movieFileId,
                        hasFile = it.hasFile,
                        monitored = it.monitored,
                    )
                }
            }
    }

    override suspend fun deleteMovieFile(baseUrl: String, apiKey: String, movieFileId: Int): Result<Unit> =
        deleteRequest(baseUrl, apiKey, "/movieFile/$movieFileId")

    override suspend fun monitorMovies(
        baseUrl: String,
        apiKey: String,
        movieIds: List<Int>,
        monitored: Boolean,
    ): Result<Unit> {
        if (movieIds.isEmpty()) return Result.success(Unit)
        val body = json.encodeToString(
            RadarrMovieMonitorRequest(movieIds = movieIds, monitored = monitored),
        )
        val request = Request.Builder()
            .url(buildUrl(baseUrl, "/movie/monitor"))
            .withApiKey(apiKey)
            .put(body.toRequestBody("application/json".toMediaType()))
            .build()
        return parseUnitRequest(jsonRequestClient, request)
    }

    override suspend fun testConnection(baseUrl: String, apiKey: String): Result<Unit> {
        val request = Request.Builder()
            .url(buildUrl(baseUrl, "/system/status"))
            .withApiKey(apiKey)
            .get()
            .build()
        return parseUnitRequest(jsonRequestClient, request)
    }

    // ── Radarr v3 DTOs (private; mapped to core/model types) ───────────────

    @Serializable
    private data class RadarrQueueResponse(
        val records: List<RadarrQueueResource> = emptyList(),
    )

    @Serializable
    private data class RadarrQueueResource(
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
        val quality: RadarrQuality? = null,
        val languages: List<RadarrLanguage> = emptyList(),
        val customFormats: List<RadarrCustomFormat> = emptyList(),
        val statusMessages: List<RadarrStatusMessage> = emptyList(),
        val movie: RadarrMovieResource? = null,
    )

    @Serializable
    private data class RadarrQuality(
        @SerialName("quality") val quality: RadarrQualityName? = null,
    ) {
        val name: String? get() = quality?.name
    }

    @Serializable
    private data class RadarrQualityName(val name: String? = null)

    @Serializable
    private data class RadarrLanguage(
        val name: String? = null,
    )

    @Serializable
    private data class RadarrCustomFormat(val name: String? = null)

    @Serializable
    private data class RadarrStatusMessage(
        val title: String? = null,
        val messages: List<String> = emptyList(),
    )

    @Serializable
    private data class RadarrMovieResource(
        val id: Int = 0,
        val title: String = "",
        val tmdbId: Int? = null,
        val monitored: Boolean = false,
        val hasFile: Boolean = false,
        val movieFileId: Int = 0,
        val inCinemas: String? = null,
        val digitalRelease: String? = null,
        val physicalRelease: String? = null,
        val overview: String? = null,
        val images: List<RadarrMediaCover> = emptyList(),
    )

    @Serializable
    private data class RadarrMediaCover(
        @SerialName("coverType") val coverType: String = "",
        @SerialName("url") val url: String? = null,
        @SerialName("remoteUrl") val remoteUrl: String? = null,
    )

    @Serializable
    private data class RadarrHistoryResponse(
        val records: List<RadarrHistoryRecord> = emptyList(),
    )

    @Serializable
    private data class RadarrHistoryRecord(
        val id: Int = 0,
        val eventType: String? = null,
        val date: String? = null,
        val data: Map<String, String> = emptyMap(),
        val movie: RadarrMovieResource? = null,
    )

    @Serializable
    private data class RadarrQueueBulkRequest(val ids: List<Int>)

    @Serializable
    private data class RadarrIdsBulkRequest(val ids: List<Int>)

    @Serializable
    private data class RadarrBlocklistResponse(
        val records: List<RadarrBlocklistRecord> = emptyList(),
    )

    @Serializable
    private data class RadarrBlocklistRecord(
        val id: Int = 0,
        val date: String? = null,
        val protocol: String? = null,
        val indexer: String? = null,
        val message: String? = null,
        val movie: RadarrMovieResource? = null,
    )

    @Serializable
    private data class RadarrWantedResponse(
        val records: List<RadarrMovieResource> = emptyList(),
    )

    @Serializable
    private data class RadarrCommandRequest(
        val name: String,
        val movieIds: List<Int>? = null,
        val movieId: Int? = null,
    )

    /** Body for `PUT /api/v3/movie/monitor`. */
    @Serializable
    private data class RadarrMovieMonitorRequest(
        val movieIds: List<Int>,
        val monitored: Boolean,
    )

    @Serializable
    private data class RadarrCommandResource(
        val id: Int = 0,
        val name: String = "",
        val status: String = "",
        val message: String? = null,
        val queued: String? = null,
        val started: String? = null,
        val ended: String? = null,
    )

    private fun RadarrQueueResource.toModel(): ArrQueueItem {
        val sizeBytes = size?.toLong()
        val sizeLeft = sizeleft?.toLong()
        // progress = (size - sizeleft) / size, guarded against zero / null.
        val progress = if (size != null && size > 0.0 && sizeleft != null) {
            ((size - sizeleft) / size).toFloat().coerceIn(0f, 1f)
        } else 0f
        return ArrQueueItem(
            queueId = id,
            downloadId = downloadId,
            tmdbId = movie?.tmdbId,
            title = movie?.title ?: "Unknown",
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

    private fun RadarrMovieResource.toCalendarItem(): ArrCalendarItem {
        // Calendar rows carry all three release dates; pick the most relevant
        // for "coming soon" ordering (digital > physical > cinematic).
        val airDate = digitalRelease ?: physicalRelease ?: inCinemas
        return ArrCalendarItem(
            tmdbId = tmdbId,
            title = title,
            mediaType = ArrMediaType.MOVIE,
            airDateUtc = airDate,
            hasFile = hasFile,
            monitored = monitored,
            overview = overview,
            // Prefer remoteUrl (absolute); fall back to url, which behind a
            // reverse proxy is often the only field populated (relative path).
            posterPath = images.firstOrNull { it.coverType == "poster" }?.posterPreference(),
        )
    }

    private fun RadarrBlocklistRecord.toModel(): ArrBlocklistItem = ArrBlocklistItem(
        id = id,
        tmdbId = movie?.tmdbId,
        title = movie?.title ?: "Unknown",
        dateUtc = date,
        protocol = protocol,
        indexer = indexer,
        message = message,
    )

    private fun RadarrMovieResource.toWantedItem(): ArrWantedItem = ArrWantedItem(
        id = id,
        tmdbId = tmdbId,
        title = title,
        airDateUtc = digitalRelease ?: physicalRelease ?: inCinemas,
        hasFile = hasFile,
        monitored = monitored,
        overview = overview,
        posterPath = images.firstOrNull { it.coverType == "poster" }?.posterPreference(),
        mediaType = ArrMediaType.MOVIE,
    )

    /**
     * Picks the best available poster URL. `remoteUrl` is absolute and
     * preferred; behind a reverse proxy Radarr often leaves `remoteUrl` null
     * and populates only `url` (a path relative to the Radarr root), so fall
     * back to it rather than rendering no poster.
     */
    private fun RadarrMediaCover.posterPreference(): String? = remoteUrl ?: url

    private fun RadarrCommandResource.toModel(): ArrCommand = ArrCommand(
        id = id,
        name = name,
        status = status,
        message = message,
        dateUtc = queued ?: started ?: ended,
    )

    private fun RadarrHistoryRecord.toModel(): ArrHistoryItem = ArrHistoryItem(
        historyId = id,
        eventType = eventType ?: "",
        tmdbId = movie?.tmdbId,
        title = movie?.title ?: "Unknown",
        dateUtc = date,
        data = data,
    )
}
