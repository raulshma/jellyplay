package com.raulshma.jellyplay.core.network.arr

import com.raulshma.jellyplay.core.model.arr.ArrBlocklistItem
import com.raulshma.jellyplay.core.model.arr.ArrCalendarItem
import com.raulshma.jellyplay.core.model.arr.ArrCommand
import com.raulshma.jellyplay.core.model.arr.ArrCommandName
import com.raulshma.jellyplay.core.model.arr.ArrHistoryItem
import com.raulshma.jellyplay.core.model.arr.ArrQueueDeleteOptions
import com.raulshma.jellyplay.core.model.arr.ArrQueueItem
import com.raulshma.jellyplay.core.model.arr.ArrSeriesEpisode
import com.raulshma.jellyplay.core.model.arr.ArrWantedItem
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * OkHttp-backed implementation of [SonarrApiClient] — a thin adapter over the
 * shared [ArrV3Client] engine: the twelve endpoint-method twins Radarr
 * exposes identically (queue read + delete, grab, manualimport, calendar,
 * history, blocklist, wanted, command, system status) are the engine's
 * methods parameterized by [ArrV3Service.SONARR] and Sonarr's wire-row
 * decoders ([ArrWireDto]); the genuinely Sonarr-specific surface — the
 * `/series` + `/episode` management endpoints with their client-side tvdbId
 * defense, season/episode lookup ladder, and monitor toggle — stays here,
 * riding the engine's URL/text/PUT/DELETE arms so the request preamble still
 * has exactly one copy.
 *
 * Structure mirrors [com.raulshma.jellyplay.core.network.seerr.SeerrApiClientImpl]
 * and [RadarrApiClientImpl]: injected unqualified [OkHttpClient], the shared
 * lenient JSON (`SeerrApiClientImpl.lenientJson` — now via the engine), and
 * `ApiException` routing for [com.raulshma.jellyplay.core.network.RetryPolicy]
 * (all inside [ArrClientSupport], the engine's funnel).
 *
 * Sonarr-specific notes (unchanged by the fold):
 * - `/queue` wraps records in a `{ records: [...] }` envelope; [getQueue]
 *   unwraps it via the shared [ArrRecords] shape.
 * - `/calendar` rows are episodes; the parent series tvdbId + title are
 *   attached via the `series` sub-object so a calendar row can carry stable
 *   identity even when the per-episode tmdbId is absent.
 * - `/series?tvdbId=` is treated as untrusted (some versions ignore the param
 *   and return ALL series), so [findSeriesByTvdb] / [getSeriesInfo] filter
 *   client-side on each row's own `tvdbId` field.
 */
class SonarrApiClientImpl(
    private val okHttpClient: OkHttpClient,
) : SonarrApiClient {

    /** The one v3 endpoint engine, carrying Sonarr's divergent bits. */
    private val engine = ArrV3Client(okHttpClient, ArrV3Service.SONARR)

    override suspend fun getQueue(baseUrl: String, apiKey: String): Result<List<ArrQueueItem>> =
        engine.getQueue(baseUrl, apiKey, SonarrQueueResource.serializer(), SonarrQueueResource::toArrQueueItem)

    override suspend fun deleteQueueItem(
        baseUrl: String,
        apiKey: String,
        id: Int,
        options: ArrQueueDeleteOptions,
    ): Result<Unit> = engine.deleteQueueItem(baseUrl, apiKey, id, options)

    override suspend fun deleteQueueItems(
        baseUrl: String,
        apiKey: String,
        ids: List<Int>,
        options: ArrQueueDeleteOptions,
    ): Result<Unit> = engine.deleteQueueItems(baseUrl, apiKey, ids, options)

    override suspend fun grabQueueItem(baseUrl: String, apiKey: String, id: Int): Result<Unit> =
        engine.grabQueueItem(baseUrl, apiKey, id)

    override suspend fun importQueueItem(baseUrl: String, apiKey: String, downloadId: String): Result<Unit> =
        engine.importQueueItem(baseUrl, apiKey, downloadId)

    override suspend fun getCalendar(
        baseUrl: String,
        apiKey: String,
        start: String,
        end: String,
    ): Result<List<ArrCalendarItem>> =
        engine.getCalendar(baseUrl, apiKey, start, end, SonarrEpisodeResource.serializer(), SonarrEpisodeResource::toCalendarItem)

    override suspend fun getHistory(
        baseUrl: String,
        apiKey: String,
        eventType: Int?,
    ): Result<List<ArrHistoryItem>> =
        engine.getHistory(baseUrl, apiKey, eventType, SonarrHistoryRecord.serializer(), SonarrHistoryRecord::toArrHistoryItem)

    override suspend fun getBlocklist(
        baseUrl: String,
        apiKey: String,
        page: Int,
        pageSize: Int,
    ): Result<List<ArrBlocklistItem>> =
        engine.getBlocklist(
            baseUrl, apiKey, page, pageSize,
            SonarrBlocklistRecord.serializer(), SonarrBlocklistRecord::toArrBlocklistItem,
        )

    override suspend fun deleteBlocklistItem(baseUrl: String, apiKey: String, id: Int): Result<Unit> =
        engine.deleteBlocklistItem(baseUrl, apiKey, id)

    override suspend fun deleteBlocklistItems(baseUrl: String, apiKey: String, ids: List<Int>): Result<Unit> =
        engine.deleteBlocklistItems(baseUrl, apiKey, ids)

    override suspend fun getWanted(
        baseUrl: String,
        apiKey: String,
        page: Int,
        pageSize: Int,
    ): Result<List<ArrWantedItem>> =
        engine.getWanted(baseUrl, apiKey, page, pageSize, SonarrEpisodeResource.serializer(), SonarrEpisodeResource::toArrWantedItem)

    override suspend fun postCommand(
        baseUrl: String,
        apiKey: String,
        commandName: ArrCommandName,
        seriesId: Int?,
        episodeIds: List<Int>?,
        seasonNumber: Int?,
    ): Result<ArrCommand> = engine.postCommand(
        baseUrl,
        apiKey,
        engine.json.encodeToString(
            SonarrCommandRequest(
                name = commandName.serialName,
                seriesId = seriesId,
                episodeIds = episodeIds,
                seasonNumber = seasonNumber,
            ),
        ),
    )

    override suspend fun findSeriesByTvdb(baseUrl: String, apiKey: String, tvdbId: Int): Result<Int?> {
        val url = engine.buildUrl(baseUrl, "/series").newBuilder()
            .addQueryParameter("tvdbId", tvdbId.toString())
            .build()
        val request = Request.Builder().url(url).withApiKey(apiKey).get().build()
        // /series?tvdbId= SHOULD return only the matching series, but some
        // Sonarr versions/configs ignore the param and return ALL series. Do
        // NOT trust `firstOrNull()` here — it would pick the wrong series and
        // every downstream lookup (episode, delete, search) would target it.
        // Filter client-side by the tvdbId field on each row.
        return engine.executeText(request).mapCatching { body ->
            val arr = engine.json.decodeFromString<JsonArray>(body)
            arr.asSequence()
                .map { engine.json.decodeFromJsonElement(SonarrSeriesResource.serializer(), it) }
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
        val seasonUrl = engine.buildUrl(baseUrl, "/episode").newBuilder()
            .addQueryParameter("seriesId", seriesId.toString())
            .addQueryParameter("seasonNumber", seasonNumber.toString())
            .build()
        val seasonReq = Request.Builder().url(seasonUrl).withApiKey(apiKey).get().build()
        val fastPath = engine.executeText(seasonReq).mapCatching { body ->
            parseEpisodeList(body)
                .firstOrNull { it.seasonNumber == seasonNumber && it.episodeNumber == episodeNumber }
        }
        // Fast-path network/parse error → propagate (don't mask with fallback).
        if (fastPath.isFailure) {
            return Result.failure(fastPath.exceptionOrNull() ?: IllegalStateException("Episode lookup failed"))
        }
        // Fast-path hit → done.
        fastPath.getOrNull()?.let { hit ->
            return Result.success(hit.toSonarrEpisodeInfo())
        }

        // Miss (episode not in this season under Jellyfin's numbering). Fall
        // back to ALL episodes and match on episodeNumber across seasons —
        // handles split seasons, anime absolute numbering, specials placement.
        return getAllEpisodes(baseUrl, apiKey, seriesId).map { all ->
            all.firstOrNull { it.episodeNumber == episodeNumber }?.toSonarrEpisodeInfo()
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
        val url = engine.buildUrl(baseUrl, "/episode").newBuilder()
            .addQueryParameter("seriesId", seriesId.toString())
            .build()
        val request = Request.Builder().url(url).withApiKey(apiKey).get().build()
        return engine.executeText(request).map { body -> parseEpisodeList(body) }
    }

    private fun parseEpisodeList(body: String): List<SonarrEpisodeLookupResource> {
        val arr = engine.json.decodeFromString<JsonArray>(body)
        return arr.map { engine.json.decodeFromJsonElement(SonarrEpisodeLookupResource.serializer(), it) }
    }

    override suspend fun deleteEpisodeFile(baseUrl: String, apiKey: String, episodeFileId: Int): Result<Unit> =
        engine.deletePath(baseUrl, apiKey, "/episodeFile/$episodeFileId")

    override suspend fun monitorEpisodes(
        baseUrl: String,
        apiKey: String,
        episodeIds: List<Int>,
        monitored: Boolean,
    ): Result<Unit> {
        if (episodeIds.isEmpty()) return Result.success(Unit)
        return engine.putJson(
            baseUrl,
            apiKey,
            "/episode/monitor",
            engine.json.encodeToString(
                SonarrEpisodeMonitorRequest(episodeIds = episodeIds, monitored = monitored),
            ),
        )
    }

    override suspend fun getSeriesInfo(baseUrl: String, apiKey: String, tvdbId: Int): Result<SonarrSeriesInfo?> {
        val url = engine.buildUrl(baseUrl, "/series").newBuilder()
            .addQueryParameter("tvdbId", tvdbId.toString())
            .build()
        val request = Request.Builder().url(url).withApiKey(apiKey).get().build()
        // Same defensive client-side filter as findSeriesByTvdb: some Sonarr
        // versions ignore the ?tvdbId= param and return ALL series.
        return engine.executeText(request).mapCatching { body ->
            val arr = engine.json.decodeFromString<JsonArray>(body)
            arr.asSequence()
                .map { engine.json.decodeFromJsonElement(SonarrSeriesResource.serializer(), it) }
                .firstOrNull { it.tvdbId == tvdbId }
                ?.let { SonarrSeriesInfo(id = it.id, title = it.title, monitored = it.monitored, path = it.path) }
        }
    }

    override suspend fun getEpisodesForSeries(
        baseUrl: String,
        apiKey: String,
        seriesId: Int,
    ): Result<List<ArrSeriesEpisode>> {
        // Same /episode?seriesId= path as getAllEpisodes but decoding the rich
        // projection (title, airDate, overview, file size/quality) the
        // management UI needs. getAllEpisodes itself decodes the leaner
        // SonarrEpisodeLookupResource, so we issue the request directly here.
        val url = engine.buildUrl(baseUrl, "/episode").newBuilder()
            .addQueryParameter("seriesId", seriesId.toString())
            .build()
        val request = Request.Builder().url(url).withApiKey(apiKey).get().build()
        return engine.executeText(request).mapCatching { body ->
            val arr = engine.json.decodeFromString<JsonArray>(body)
            arr.map { engine.json.decodeFromJsonElement(SonarrManagedEpisodeResource.serializer(), it) }
                .map { it.toArrSeriesEpisode() }
        }
    }

    override suspend fun testConnection(baseUrl: String, apiKey: String): Result<Unit> =
        engine.testConnection(baseUrl, apiKey)
}
