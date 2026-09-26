package com.raulshma.jellyplay.core.network.arr

import com.raulshma.jellyplay.core.model.arr.ArrBlocklistItem
import com.raulshma.jellyplay.core.model.arr.ArrCommand
import com.raulshma.jellyplay.core.model.arr.ArrQueueDeleteOptions
import com.raulshma.jellyplay.core.model.arr.ArrQueueItem
import com.raulshma.jellyplay.core.model.arr.ArrHistoryItem
import com.raulshma.jellyplay.core.network.api.ApiException
import com.raulshma.jellyplay.core.network.seerr.SeerrApiClientImpl
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * The per-service divergences of the *arr v3 REST schema — everything the
 * shared [ArrV3Client] engine needs beyond the twelve endpoint twins both
 * Radarr and Sonarr expose identically apart from these bits. Kept to data,
 * not code: each entry is a service display name, a wanted sort key, or a
 * list of query params (preserving the exact order the clients add them).
 */
internal class ArrV3Service(
    /** User-visible service name ("Radarr" / "Sonarr") inside error texts. */
    val serviceName: String,
    /** `sortKey` for `GET /wanted/missing` — `airDateUtc` (episodes) vs `inCinemas` (movies). */
    val wantedSortKey: String,
    /** Identity params on `GET /queue` — Sonarr attaches series + episode, Radarr the movie. */
    val queueIncludeParams: List<Pair<String, String>>,
    /** Extra params on `GET /calendar` — only Sonarr needs the series sub-object. */
    val calendarIncludeParams: List<Pair<String, String>>,
    /** Identity param on `GET /history` — `includeSeries` vs `includeMovie`. */
    val historyIncludeParams: List<Pair<String, String>>,
    /** Extra params on `GET /wanted/missing` (after page/pageSize/sortKey/sortDirection). */
    val wantedIncludeParams: List<Pair<String, String>>,
) {
    companion object {
        val RADARR = ArrV3Service(
            serviceName = "Radarr",
            wantedSortKey = "inCinemas",
            queueIncludeParams = listOf("includeMovie" to "true"),
            calendarIncludeParams = emptyList(),
            historyIncludeParams = listOf("includeMovie" to "true"),
            wantedIncludeParams = emptyList(),
        )

        val SONARR = ArrV3Service(
            serviceName = "Sonarr",
            wantedSortKey = "airDateUtc",
            queueIncludeParams = listOf("includeSeries" to "true", "includeEpisode" to "true"),
            calendarIncludeParams = listOf("includeSeries" to "true"),
            historyIncludeParams = listOf("includeSeries" to "true"),
            wantedIncludeParams = listOf("includeSeries" to "true"),
        )
    }
}

/**
 * The ONE *arr v3 endpoint engine: the twelve endpoint-method twins
 * `RadarrApiClientImpl` / `SonarrApiClientImpl` used to hand-copy over the
 * identical v3 REST schema (queue read + single/bulk delete, grab, the 2-step
 * manualimport, calendar, history, blocklist read + delete, wanted/missing,
 * command, system status — plus the monitor PUT and file DELETE arms whose
 * request shape matches but whose paths/bodies the adapters own). Parameterized
 * by [ArrV3Service] (the genuinely divergent query-param bits + the service
 * name inside error texts) and per-call wire-row decoding — the envelope row
 * type is Radarr's `movie` vs Sonarr's `series`/`episode`, so each list
 * endpoint takes the row [KSerializer] and a wire→model mapper while the
 * engine owns URL assembly, param order, request building, execution, and the
 * retrying/error-shaping funnel ([ArrClientSupport]).
 *
 * Wire fidelity: params are attached in exactly the order the folded impls
 * added them; the `importQueueItem` 404 text rebuilds with the service name
 * byte-identically; bodies flow through the same lenient
 * [SeerrApiClientImpl.lenientJson]; the `{records: [...]}` envelope decodes
 * through the shared [ArrRecords] shape; and list/bulk delete bodies are the
 * shared [ArrIdsBody]. The service-specific endpoints that have no twin
 * (Sonarr's series/episode management, Radarr's movie lookups) stay on the
 * adapters, which ride this engine's URL/text/PUT/DELETE arms so the request
 * preamble still has exactly one copy.
 */
internal class ArrV3Client(
    okHttpClient: OkHttpClient,
    private val service: ArrV3Service,
) {

    /** Lenient wire JSON — shared with the adapters for their own body encoding. */
    internal val json: Json = SeerrApiClientImpl.lenientJson

    /**
     * The request preamble both services share verbatim — /api/v3 URL join,
     * `X-Api-Key` header, raw + stream-decoding executions, and the
     * per-service failure texts (the only textual delta between the two
     * folded clients was the service name inside those strings).
     */
    private val support = ArrClientSupport(
        okHttpClient = okHttpClient,
        json = json,
        serviceName = service.serviceName,
    )

    // ── queue ───────────────────────────────────────────────────────────────

    /** `GET /queue` — unwraps the `records` envelope; rows decoded per service. */
    suspend fun <W> getQueue(
        baseUrl: String,
        apiKey: String,
        row: KSerializer<W>,
        toModel: (W) -> ArrQueueItem,
    ): Result<List<ArrQueueItem>> =
        parseEnvelope(getRequest(baseUrl, apiKey, "/queue", service.queueIncludeParams), row)
            .map { page -> page.records.map(toModel) }

    /** `DELETE /queue/{id}` with the removeFromClient/blocklist/skipRedownload options. */
    suspend fun deleteQueueItem(
        baseUrl: String,
        apiKey: String,
        id: Int,
        options: ArrQueueDeleteOptions,
    ): Result<Unit> {
        val url = support.buildUrl(baseUrl, "/queue/$id").newBuilder().withDeleteOptions(options).build()
        val request = Request.Builder().url(url).withApiKey(apiKey).delete().build()
        return support.parseUnit(request)
    }

    /** `DELETE /queue/bulk` — one call for many ids; empty list short-circuits. */
    suspend fun deleteQueueItems(
        baseUrl: String,
        apiKey: String,
        ids: List<Int>,
        options: ArrQueueDeleteOptions,
    ): Result<Unit> {
        if (ids.isEmpty()) return Result.success(Unit)
        val url = support.buildUrl(baseUrl, "/queue/bulk").newBuilder().withDeleteOptions(options).build()
        return deleteIdsBody(url, apiKey, ids)
    }

    /** `POST /queue/grab/{id}` — force-send a queued release to the download client. */
    suspend fun grabQueueItem(baseUrl: String, apiKey: String, id: Int): Result<Unit> =
        support.postEmpty(baseUrl, apiKey, "/queue/grab/$id")

    /**
     * The 2-step manualimport flow (the *arr v3 spec has no queue/import/{id}):
     * 1) GET the candidate import rows for this download-client guid, then
     * 2) re-post them verbatim to trigger the import. The POST body schema is
     * undocumented in the OpenAPI spec; passing the GET array through
     * unchanged is both the documented usage and immune to schema drift on
     * the 16-field ManualImportResource.
     */
    suspend fun importQueueItem(baseUrl: String, apiKey: String, downloadId: String): Result<Unit> {
        val getUrl = support.buildUrl(baseUrl, "/manualimport").newBuilder()
            .addQueryParameter("downloadId", downloadId)
            .build()
        val getRequest = Request.Builder().url(getUrl).withApiKey(apiKey).get().build()
        val rows = support.executeRequest(getRequest).mapCatching { json.decodeFromString<JsonArray>(it) }
        val rowList = rows.getOrElse { return Result.failure(it) }
        if (rowList.isEmpty()) {
            return Result.failure(
                ApiException.fromHttp(
                    404,
                    "No importable files found for this download in ${service.serviceName}.",
                ),
            )
        }
        val postRequest = Request.Builder()
            .url(support.buildUrl(baseUrl, "/manualimport"))
            .withApiKey(apiKey)
            .post(rowList.toString().toRequestBody("application/json".toMediaType()))
            .build()
        return support.parseUnit(postRequest)
    }

    // ── calendar ────────────────────────────────────────────────────────────

    /** `GET /calendar?start=...&end=...` (+ the service's identity params); bare array rows. */
    suspend fun <W, M> getCalendar(
        baseUrl: String,
        apiKey: String,
        start: String,
        end: String,
        row: KSerializer<W>,
        toModel: (W) -> M,
    ): Result<List<M>> =
        getList(
            baseUrl,
            apiKey,
            "/calendar",
            listOf("start" to start, "end" to end) + service.calendarIncludeParams,
            row,
        ).map { rows -> rows.map(toModel) }

    // ── history ─────────────────────────────────────────────────────────────

    /** `GET /history` — identity param first, then the optional `eventType` filter. */
    suspend fun <W> getHistory(
        baseUrl: String,
        apiKey: String,
        eventType: Int?,
        row: KSerializer<W>,
        toModel: (W) -> ArrHistoryItem,
    ): Result<List<ArrHistoryItem>> {
        val params = service.historyIncludeParams +
            if (eventType != null) listOf("eventType" to eventType.toString()) else emptyList()
        return parseEnvelope(getRequest(baseUrl, apiKey, "/history", params), row)
            .map { page -> page.records.map(toModel) }
    }

    // ── blocklist ───────────────────────────────────────────────────────────

    /** `GET /blocklist` — both services sort by `date` descending. */
    suspend fun <W> getBlocklist(
        baseUrl: String,
        apiKey: String,
        page: Int,
        pageSize: Int,
        row: KSerializer<W>,
        toModel: (W) -> ArrBlocklistItem,
    ): Result<List<ArrBlocklistItem>> =
        parseEnvelope(
            getRequest(
                baseUrl,
                apiKey,
                "/blocklist",
                listOf(
                    "page" to page.toString(),
                    "pageSize" to pageSize.toString(),
                    "sortKey" to "date",
                    "sortDirection" to "descending",
                ),
            ),
            row,
        ).map { page -> page.records.map(toModel) }

    /** `DELETE /blocklist/{id}` — remove one blocklist entry (re-enables search). */
    suspend fun deleteBlocklistItem(baseUrl: String, apiKey: String, id: Int): Result<Unit> =
        support.deleteRequest(baseUrl, apiKey, "/blocklist/$id")

    /** `DELETE /blocklist/bulk` — remove multiple blocklist entries; empty list short-circuits. */
    suspend fun deleteBlocklistItems(baseUrl: String, apiKey: String, ids: List<Int>): Result<Unit> {
        if (ids.isEmpty()) return Result.success(Unit)
        return deleteIdsBody(support.buildUrl(baseUrl, "/blocklist/bulk"), apiKey, ids)
    }

    // ── wanted ──────────────────────────────────────────────────────────────

    /** `GET /wanted/missing` — the sort key is the per-service divergence. */
    suspend fun <W, M> getWanted(
        baseUrl: String,
        apiKey: String,
        page: Int,
        pageSize: Int,
        row: KSerializer<W>,
        toModel: (W) -> M,
    ): Result<List<M>> =
        parseEnvelope(
            getRequest(
                baseUrl,
                apiKey,
                "/wanted/missing",
                listOf(
                    "page" to page.toString(),
                    "pageSize" to pageSize.toString(),
                    "sortKey" to service.wantedSortKey,
                    "sortDirection" to "descending",
                ) + service.wantedIncludeParams,
            ),
            row,
        ).map { page -> page.records.map(toModel) }

    // ── command / monitor / file delete / status ────────────────────────────

    /**
     * `POST /command` — the adapter encodes its own command body (the two
     * services' request field names differ on the wire); the response
     * resource is the shared shape.
     */
    suspend fun postCommand(baseUrl: String, apiKey: String, bodyJson: String): Result<ArrCommand> {
        val request = Request.Builder()
            .url(support.buildUrl(baseUrl, "/command"))
            .withApiKey(apiKey)
            .post(bodyJson.toRequestBody("application/json".toMediaType()))
            .build()
        return support.parseRequest(request, ArrCommandResource.serializer()).map { it.toArrCommand() }
    }

    /** `PUT {path}` with a JSON body — the monitor toggles (`/episode/monitor`, `/movie/monitor`). */
    suspend fun putJson(baseUrl: String, apiKey: String, path: String, bodyJson: String): Result<Unit> {
        val request = Request.Builder()
            .url(support.buildUrl(baseUrl, path))
            .withApiKey(apiKey)
            .put(bodyJson.toRequestBody("application/json".toMediaType()))
            .build()
        return support.parseUnit(request)
    }

    /** `DELETE {path}` — the file deletes (`/episodeFile/{id}`, `/movieFile/{id}`). */
    suspend fun deletePath(baseUrl: String, apiKey: String, path: String): Result<Unit> =
        support.deleteRequest(baseUrl, apiKey, path)

    /** `GET /system/status` — connection probe. Succeeds iff 2xx. */
    suspend fun testConnection(baseUrl: String, apiKey: String): Result<Unit> {
        val request = Request.Builder()
            .url(support.buildUrl(baseUrl, "/system/status"))
            .withApiKey(apiKey)
            .get()
            .build()
        return support.parseUnit(request)
    }

    // ── arms for the adapter-owned service-specific endpoints ───────────────

    /** The /api/v3 URL join, for adapters building their own service-specific requests. */
    fun buildUrl(baseUrl: String, path: String): HttpUrl = support.buildUrl(baseUrl, path)

    /** Raw-text execution, for adapters decoding service-specific payloads. */
    suspend fun executeText(request: Request): Result<String> = support.executeRequest(request)

    /**
     * `GET {path}` decoding a bare JSON array with [element]'s serializer —
     * Radarr's `/movie?tmdbId=` lookups (a single-element array, or empty when
     * not tracked).
     */
    suspend fun <T> getList(
        baseUrl: String,
        apiKey: String,
        path: String,
        params: List<Pair<String, String>>,
        element: KSerializer<T>,
    ): Result<List<T>> =
        support.parseRequest(getRequest(baseUrl, apiKey, path, params), ListSerializer(element))

    // ── assembly helpers ────────────────────────────────────────────────────

    /** GET with the params attached in list order (the folded clients' exact order). */
    private fun getRequest(
        baseUrl: String,
        apiKey: String,
        path: String,
        params: List<Pair<String, String>>,
    ): Request =
        Request.Builder()
            .url(
                support.buildUrl(baseUrl, path).newBuilder().apply {
                    params.forEach { (name, value) -> addQueryParameter(name, value) }
                }.build(),
            )
            .withApiKey(apiKey)
            .get()
            .build()

    /** Stream-decodes the `{records: [...]}` page envelope around a per-service row type. */
    private suspend fun <W> parseEnvelope(request: Request, row: KSerializer<W>): Result<ArrRecords<W>> =
        support.parseRequest(request, ArrRecords.serializer(row))

    /** DELETE with the shared bare-ids body (queue/bulk + blocklist/bulk). */
    private suspend fun deleteIdsBody(url: HttpUrl, apiKey: String, ids: List<Int>): Result<Unit> {
        val body = json.encodeToString(ArrIdsBody(ids = ids))
        val request = Request.Builder()
            .url(url)
            .withApiKey(apiKey)
            .delete(body.toRequestBody("application/json".toMediaType()))
            .build()
        return support.parseUnit(request)
    }
}
