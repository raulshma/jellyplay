package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.arr.ArrBlocklistItem
import com.raulshma.jellyplay.core.model.arr.ArrCalendarItem
import com.raulshma.jellyplay.core.model.arr.ArrCommand
import com.raulshma.jellyplay.core.model.arr.ArrCommandName
import com.raulshma.jellyplay.core.model.arr.ArrQueueDeleteOptions
import com.raulshma.jellyplay.core.model.arr.ArrQueueItem
import com.raulshma.jellyplay.core.model.arr.ArrServerConfig
import com.raulshma.jellyplay.core.network.arr.RadarrApiClient
import com.raulshma.jellyplay.core.network.arr.SonarrApiClient

/**
 * The Radarr/Sonarr dispatch seam: the subset both *arr clients expose with
 * identical shapes, bound to one [ArrServerConfig] so call sites stop
 * hand-writing the `if (kind == RADARR) radarr.x(...) else sonarr.x(...)`
 * ladder. Adapters are thin delegates over the injected
 * [RadarrApiClient] / [SonarrApiClient] — no caching, no retry (the
 * [ArrRepositoryImpl] fan-out owns per-server failure degradation).
 *
 * [postCommand] carries the union of the two clients' command parameters
 * (`movieIds` is Radarr's, `seriesId`/`seasonNumber` are Sonarr's); each
 * adapter forwards only its client's subset, so a kind mismatched parameter
 * is silently dropped exactly as a direct call site would never pass it.
 */
interface ArrServiceClient {

    /** `GET /queue` — active downloads for the bound server. */
    suspend fun getQueue(): Result<List<ArrQueueItem>>

    /** `DELETE /queue/{id}` — removes one queue row ([options] maps the query params). */
    suspend fun deleteQueueItem(id: Int, options: ArrQueueDeleteOptions = ArrQueueDeleteOptions()): Result<Unit>

    /** `DELETE /queue/bulk` — removes multiple queue rows in one call. */
    suspend fun deleteQueueItems(ids: List<Int>, options: ArrQueueDeleteOptions = ArrQueueDeleteOptions()): Result<Unit>

    /** `POST /queue/grab/{id}` — force-send a queued release to the download client. */
    suspend fun grabQueueItem(id: Int): Result<Unit>

    /** Force-import via the 2-step manualimport flow keyed by the download-client guid. */
    suspend fun importQueueItem(downloadId: String): Result<Unit>

    /** `GET /calendar?start=...&end=...` — releases inside `[start, end]` (ISO dates, inclusive). */
    suspend fun getCalendar(start: String, end: String): Result<List<ArrCalendarItem>>

    /** `GET /blocklist` — rejected releases. */
    suspend fun getBlocklist(): Result<List<ArrBlocklistItem>>

    /** `DELETE /blocklist/{id}` — remove one blocklist entry (re-enables search). */
    suspend fun deleteBlocklistItem(id: Int): Result<Unit>

    /** `DELETE /blocklist/bulk` — remove multiple blocklist entries. */
    suspend fun deleteBlocklistItems(ids: List<Int>): Result<Unit>

    /** `POST /command` — queues an asynchronous command (see the class KDoc on parameter unions). */
    suspend fun postCommand(
        commandName: ArrCommandName,
        movieIds: List<Int>? = null,
        episodeIds: List<Int>? = null,
        seriesId: Int? = null,
        seasonNumber: Int? = null,
    ): Result<ArrCommand>

    /** `GET /system/status` — connection probe. Succeeds iff 2xx. */
    suspend fun testConnection(): Result<Unit>
}

/** [ArrServiceClient] over a [RadarrApiClient] (the bound server is a Radarr kind). */
internal class RadarrServiceClient(
    private val client: RadarrApiClient,
    private val server: ArrServerConfig,
) : ArrServiceClient {
    override suspend fun getQueue(): Result<List<ArrQueueItem>> = client.getQueue(server.baseUrl, server.apiKey)
    override suspend fun deleteQueueItem(id: Int, options: ArrQueueDeleteOptions): Result<Unit> =
        client.deleteQueueItem(server.baseUrl, server.apiKey, id, options)
    override suspend fun deleteQueueItems(ids: List<Int>, options: ArrQueueDeleteOptions): Result<Unit> =
        client.deleteQueueItems(server.baseUrl, server.apiKey, ids, options)
    override suspend fun grabQueueItem(id: Int): Result<Unit> = client.grabQueueItem(server.baseUrl, server.apiKey, id)
    override suspend fun importQueueItem(downloadId: String): Result<Unit> =
        client.importQueueItem(server.baseUrl, server.apiKey, downloadId)
    override suspend fun getCalendar(start: String, end: String): Result<List<ArrCalendarItem>> =
        client.getCalendar(server.baseUrl, server.apiKey, start, end)
    override suspend fun getBlocklist(): Result<List<ArrBlocklistItem>> =
        client.getBlocklist(server.baseUrl, server.apiKey)
    override suspend fun deleteBlocklistItem(id: Int): Result<Unit> =
        client.deleteBlocklistItem(server.baseUrl, server.apiKey, id)
    override suspend fun deleteBlocklistItems(ids: List<Int>): Result<Unit> =
        client.deleteBlocklistItems(server.baseUrl, server.apiKey, ids)
    override suspend fun postCommand(
        commandName: ArrCommandName,
        movieIds: List<Int>?,
        episodeIds: List<Int>?,
        seriesId: Int?,
        seasonNumber: Int?,
    ): Result<ArrCommand> = client.postCommand(server.baseUrl, server.apiKey, commandName, movieIds, episodeIds)
    override suspend fun testConnection(): Result<Unit> = client.testConnection(server.baseUrl, server.apiKey)
}

/** [ArrServiceClient] over a [SonarrApiClient] (the bound server is a Sonarr kind). */
internal class SonarrServiceClient(
    private val client: SonarrApiClient,
    private val server: ArrServerConfig,
) : ArrServiceClient {
    override suspend fun getQueue(): Result<List<ArrQueueItem>> = client.getQueue(server.baseUrl, server.apiKey)
    override suspend fun deleteQueueItem(id: Int, options: ArrQueueDeleteOptions): Result<Unit> =
        client.deleteQueueItem(server.baseUrl, server.apiKey, id, options)
    override suspend fun deleteQueueItems(ids: List<Int>, options: ArrQueueDeleteOptions): Result<Unit> =
        client.deleteQueueItems(server.baseUrl, server.apiKey, ids, options)
    override suspend fun grabQueueItem(id: Int): Result<Unit> = client.grabQueueItem(server.baseUrl, server.apiKey, id)
    override suspend fun importQueueItem(downloadId: String): Result<Unit> =
        client.importQueueItem(server.baseUrl, server.apiKey, downloadId)
    override suspend fun getCalendar(start: String, end: String): Result<List<ArrCalendarItem>> =
        client.getCalendar(server.baseUrl, server.apiKey, start, end)
    override suspend fun getBlocklist(): Result<List<ArrBlocklistItem>> =
        client.getBlocklist(server.baseUrl, server.apiKey)
    override suspend fun deleteBlocklistItem(id: Int): Result<Unit> =
        client.deleteBlocklistItem(server.baseUrl, server.apiKey, id)
    override suspend fun deleteBlocklistItems(ids: List<Int>): Result<Unit> =
        client.deleteBlocklistItems(server.baseUrl, server.apiKey, ids)
    override suspend fun postCommand(
        commandName: ArrCommandName,
        movieIds: List<Int>?,
        episodeIds: List<Int>?,
        seriesId: Int?,
        seasonNumber: Int?,
    ): Result<ArrCommand> = client.postCommand(
        server.baseUrl, server.apiKey, commandName,
        seriesId = seriesId, episodeIds = episodeIds, seasonNumber = seasonNumber,
    )
    override suspend fun testConnection(): Result<Unit> = client.testConnection(server.baseUrl, server.apiKey)
}
