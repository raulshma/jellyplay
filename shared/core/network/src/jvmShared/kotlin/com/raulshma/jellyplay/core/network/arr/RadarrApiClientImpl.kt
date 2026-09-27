package com.raulshma.jellyplay.core.network.arr

import com.raulshma.jellyplay.core.model.arr.ArrBlocklistItem
import com.raulshma.jellyplay.core.model.arr.ArrCalendarItem
import com.raulshma.jellyplay.core.model.arr.ArrCommand
import com.raulshma.jellyplay.core.model.arr.ArrCommandName
import com.raulshma.jellyplay.core.model.arr.ArrHistoryItem
import com.raulshma.jellyplay.core.model.arr.ArrQueueDeleteOptions
import com.raulshma.jellyplay.core.model.arr.ArrQueueItem
import com.raulshma.jellyplay.core.model.arr.ArrServerConfig
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

    override suspend fun getQueue(server: ArrServerConfig): Result<List<ArrQueueItem>> =
        engine.getQueue(server, RadarrQueueResource.serializer(), RadarrQueueResource::toArrQueueItem)

    override suspend fun deleteQueueItem(
        server: ArrServerConfig,
        id: Int,
        options: ArrQueueDeleteOptions,
    ): Result<Unit> = engine.deleteQueueItem(server, id, options)

    override suspend fun deleteQueueItems(
        server: ArrServerConfig,
        ids: List<Int>,
        options: ArrQueueDeleteOptions,
    ): Result<Unit> = engine.deleteQueueItems(server, ids, options)

    override suspend fun grabQueueItem(server: ArrServerConfig, id: Int): Result<Unit> =
        engine.grabQueueItem(server, id)

    override suspend fun importQueueItem(server: ArrServerConfig, downloadId: String): Result<Unit> =
        engine.importQueueItem(server, downloadId)

    override suspend fun getCalendar(
        server: ArrServerConfig,
        start: String,
        end: String,
    ): Result<List<ArrCalendarItem>> =
        engine.getCalendar(server, start, end, RadarrMovieResource.serializer(), RadarrMovieResource::toCalendarItem)

    override suspend fun getHistory(
        server: ArrServerConfig,
        eventType: Int?,
    ): Result<List<ArrHistoryItem>> =
        engine.getHistory(server, eventType, RadarrHistoryRecord.serializer(), RadarrHistoryRecord::toArrHistoryItem)

    override suspend fun getBlocklist(
        server: ArrServerConfig,
        page: Int,
        pageSize: Int,
    ): Result<List<ArrBlocklistItem>> =
        engine.getBlocklist(
            server, page, pageSize,
            RadarrBlocklistRecord.serializer(), RadarrBlocklistRecord::toArrBlocklistItem,
        )

    override suspend fun deleteBlocklistItem(server: ArrServerConfig, id: Int): Result<Unit> =
        engine.deleteBlocklistItem(server, id)

    override suspend fun deleteBlocklistItems(server: ArrServerConfig, ids: List<Int>): Result<Unit> =
        engine.deleteBlocklistItems(server, ids)

    override suspend fun getWanted(
        server: ArrServerConfig,
        page: Int,
        pageSize: Int,
    ): Result<List<ArrWantedItem>> =
        engine.getWanted(server, page, pageSize, RadarrMovieResource.serializer(), RadarrMovieResource::toArrWantedItem)

    override suspend fun postCommand(
        server: ArrServerConfig,
        commandName: ArrCommandName,
        movieIds: List<Int>?,
        episodeIds: List<Int>?,
    ): Result<ArrCommand> = engine.postCommand(
        server,
        engine.json.encodeToString(
            RadarrCommandRequest(
                name = commandName.serialName,
                movieIds = movieIds,
                movieId = movieIds?.firstOrNull(),
            ),
        ),
    )

    override suspend fun findMovieIdByTmdb(server: ArrServerConfig, tmdbId: Int): Result<Int?> {
        // /api/v3/movie?tmdbId= returns a single-element array (or empty when
        // no match). Decoded as a list rather than a bare object so the
        // not-tracked case is a clean empty list instead of a parse error.
        return engine.getList(
            server, "/movie", listOf("tmdbId" to tmdbId.toString()), RadarrMovieResource.serializer(),
        ).map { list -> list.firstOrNull()?.id }
    }

    override suspend fun getMovieForTmdb(server: ArrServerConfig, tmdbId: Int): Result<RadarrMovieInfo?> =
        engine.getList(
            server, "/movie", listOf("tmdbId" to tmdbId.toString()), RadarrMovieResource.serializer(),
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

    override suspend fun deleteMovieFile(server: ArrServerConfig, movieFileId: Int): Result<Unit> =
        engine.deletePath(server, "/movieFile/$movieFileId")

    override suspend fun monitorMovies(
        server: ArrServerConfig,
        movieIds: List<Int>,
        monitored: Boolean,
    ): Result<Unit> {
        if (movieIds.isEmpty()) return Result.success(Unit)
        return engine.putJson(
            server,
            "/movie/monitor",
            engine.json.encodeToString(
                RadarrMovieMonitorRequest(movieIds = movieIds, monitored = monitored),
            ),
        )
    }

    override suspend fun testConnection(server: ArrServerConfig): Result<Unit> =
        engine.testConnection(server)
}
