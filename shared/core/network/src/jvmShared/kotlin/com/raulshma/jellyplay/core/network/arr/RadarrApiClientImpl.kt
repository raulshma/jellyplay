package com.raulshma.jellyplay.core.network.arr

import com.raulshma.jellyplay.core.model.arr.ArrBlocklistItem
import com.raulshma.jellyplay.core.model.arr.ArrCalendarItem
import com.raulshma.jellyplay.core.model.arr.ArrCommand
import com.raulshma.jellyplay.core.model.arr.ArrCommandName
import com.raulshma.jellyplay.core.model.arr.ArrHistoryItem
import com.raulshma.jellyplay.core.model.arr.ArrQueueDeleteOptions
import com.raulshma.jellyplay.core.model.arr.ArrQueueItem
import com.raulshma.jellyplay.core.model.arr.ArrWantedItem
import kotlinx.serialization.encodeToString
import okhttp3.OkHttpClient

/**
 * OkHttp-backed implementation of [RadarrApiClient] — a thin adapter over the
 * shared [ArrV3Client] engine: the twelve endpoint-method twins Sonarr
 * exposes identically (queue read + delete, grab, manualimport, calendar,
 * history, blocklist, wanted, command, system status) are the engine's
 * methods parameterized by [ArrV3Service.RADARR] and Radarr's wire-row
 * decoders ([ArrWireDto]); the genuinely Radarr-specific surface — the
 * `/movie?tmdbId=` lookups, the movie-file delete, and the monitor toggle —
 * stays here, riding the engine's list/PUT/DELETE arms so the request
 * preamble still has exactly one copy.
 *
 * Structure mirrors [com.raulshma.jellyplay.core.network.seerr.SeerrApiClientImpl]
 * and [SonarrApiClientImpl]: injects the shared unqualified [OkHttpClient],
 * reuses `SeerrApiClientImpl.lenientJson` (now via the engine), and routes
 * failures through `ApiException.fromHttp` / `fromNetwork` so
 * [com.raulshma.jellyplay.core.network.RetryPolicy] can classify retryability
 * (all inside [ArrClientSupport], the engine's funnel).
 *
 * Radarr's v3 API uses `X-Api-Key` for auth — the same header name Seerr uses —
 * so no new credential type is required.
 */
class RadarrApiClientImpl(
    private val okHttpClient: OkHttpClient,
) : RadarrApiClient {

    /** The one v3 endpoint engine, carrying Radarr's divergent bits. */
    private val engine = ArrV3Client(okHttpClient, ArrV3Service.RADARR)

    override suspend fun getQueue(baseUrl: String, apiKey: String): Result<List<ArrQueueItem>> =
        engine.getQueue(baseUrl, apiKey, RadarrQueueResource.serializer(), RadarrQueueResource::toArrQueueItem)

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
        engine.getCalendar(baseUrl, apiKey, start, end, RadarrMovieResource.serializer(), RadarrMovieResource::toCalendarItem)

    override suspend fun getHistory(
        baseUrl: String,
        apiKey: String,
        eventType: Int?,
    ): Result<List<ArrHistoryItem>> =
        engine.getHistory(baseUrl, apiKey, eventType, RadarrHistoryRecord.serializer(), RadarrHistoryRecord::toArrHistoryItem)

    override suspend fun getBlocklist(
        baseUrl: String,
        apiKey: String,
        page: Int,
        pageSize: Int,
    ): Result<List<ArrBlocklistItem>> =
        engine.getBlocklist(
            baseUrl, apiKey, page, pageSize,
            RadarrBlocklistRecord.serializer(), RadarrBlocklistRecord::toArrBlocklistItem,
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
        engine.getWanted(baseUrl, apiKey, page, pageSize, RadarrMovieResource.serializer(), RadarrMovieResource::toArrWantedItem)

    override suspend fun postCommand(
        baseUrl: String,
        apiKey: String,
        commandName: ArrCommandName,
        movieIds: List<Int>?,
        episodeIds: List<Int>?,
    ): Result<ArrCommand> = engine.postCommand(
        baseUrl,
        apiKey,
        engine.json.encodeToString(
            RadarrCommandRequest(
                name = commandName.serialName,
                movieIds = movieIds,
                movieId = movieIds?.firstOrNull(),
            ),
        ),
    )

    override suspend fun findMovieIdByTmdb(baseUrl: String, apiKey: String, tmdbId: Int): Result<Int?> {
        // /api/v3/movie?tmdbId= returns a single-element array (or empty when
        // no match). Decoded as a list rather than a bare object so the
        // not-tracked case is a clean empty list instead of a parse error.
        return engine.getList(
            baseUrl, apiKey, "/movie", listOf("tmdbId" to tmdbId.toString()), RadarrMovieResource.serializer(),
        ).map { list -> list.firstOrNull()?.id }
    }

    override suspend fun getMovieForTmdb(baseUrl: String, apiKey: String, tmdbId: Int): Result<RadarrMovieInfo?> =
        engine.getList(
            baseUrl, apiKey, "/movie", listOf("tmdbId" to tmdbId.toString()), RadarrMovieResource.serializer(),
        ).map { list ->
            list.firstOrNull()?.let {
                RadarrMovieInfo(
                    id = it.id,
                    movieFileId = it.movieFileId,
                    hasFile = it.hasFile,
                    monitored = it.monitored,
                )
            }
        }

    override suspend fun deleteMovieFile(baseUrl: String, apiKey: String, movieFileId: Int): Result<Unit> =
        engine.deletePath(baseUrl, apiKey, "/movieFile/$movieFileId")

    override suspend fun monitorMovies(
        baseUrl: String,
        apiKey: String,
        movieIds: List<Int>,
        monitored: Boolean,
    ): Result<Unit> {
        if (movieIds.isEmpty()) return Result.success(Unit)
        return engine.putJson(
            baseUrl,
            apiKey,
            "/movie/monitor",
            engine.json.encodeToString(
                RadarrMovieMonitorRequest(movieIds = movieIds, monitored = monitored),
            ),
        )
    }

    override suspend fun testConnection(baseUrl: String, apiKey: String): Result<Unit> =
        engine.testConnection(baseUrl, apiKey)
}
