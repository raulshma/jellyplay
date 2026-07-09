package com.raulshma.jellyplay.core.network.arr

import com.raulshma.jellyplay.core.model.arr.ArrBlocklistItem
import com.raulshma.jellyplay.core.model.arr.ArrCalendarItem
import com.raulshma.jellyplay.core.model.arr.ArrCommand
import com.raulshma.jellyplay.core.model.arr.ArrCommandName
import com.raulshma.jellyplay.core.model.arr.ArrHistoryItem
import com.raulshma.jellyplay.core.model.arr.ArrQueueDeleteOptions
import com.raulshma.jellyplay.core.model.arr.ArrQueueItem
import com.raulshma.jellyplay.core.model.arr.ArrWantedItem

/**
 * Direct client for a Sonarr v4 instance.
 *
 * Mirrors [com.raulshma.jellyplay.core.network.seerr.SeerrApiClient]: every
 * method is `suspend`, takes `baseUrl` + `apiKey`, returns [Result], and
 * targets the `/api/v3` root (the v3 API is authoritative for Sonarr v4 — see
 * the official docs) with `X-Api-Key` auth. Sonarr differs from Radarr in two
 * ways that affect the contract:
 *
 * - `/queue` returns a `{ records: [...] }` envelope (same shape Radarr uses);
 *   [getQueue] unwraps it transparently.
 * - `/calendar` yields one row per airing episode (not one per series); the
 *   series tvdbId is attached so [com.raulshma.jellyplay.core.model.arr.ArrCalendarItem.toSeerrSearchItem]
 *   can produce a stable card identity.
 *
 * Exposes the full queue/management surface Sonarr v4 supports: queue read
 * with `includeSeries` + `includeEpisode`, per-item + bulk delete with
 * `removeFromClient` / `blocklist` / `skipRedownload`, force-grab /
 * force-import, blocklist read + delete, wanted/missing list, and the
 * asynchronous command runner (`POST /api/v3/command`) for triggering episode
 * / series searches, refreshes, rescans.
 *
 * Adding a method requires updating [ResilientSonarrApiClient] too — see its
 * doc comment.
 */
interface SonarrApiClient {

    /** `GET /api/v3/queue?includeSeries=true&includeEpisode=true` — unwraps the `records` envelope. */
    suspend fun getQueue(baseUrl: String, apiKey: String): Result<List<ArrQueueItem>>

    /**
     * `DELETE /api/v3/queue/{id}` — removes one queue row. [options] maps to
     * the `removeFromClient` / `blocklist` / `skipRedownload` query params.
     */
    suspend fun deleteQueueItem(
        baseUrl: String,
        apiKey: String,
        id: Int,
        options: ArrQueueDeleteOptions = ArrQueueDeleteOptions(),
    ): Result<Unit>

    /**
     * `DELETE /api/v3/queue/bulk` — removes multiple queue rows in one call.
     */
    suspend fun deleteQueueItems(
        baseUrl: String,
        apiKey: String,
        ids: List<Int>,
        options: ArrQueueDeleteOptions = ArrQueueDeleteOptions(),
    ): Result<Unit>

    /** `POST /api/v3/queue/grab/{id}` — force-send a queued release to the download client. */
    suspend fun grabQueueItem(baseUrl: String, apiKey: String, id: Int): Result<Unit>

    /**
     * Force-imports an already-importable release via the documented 2-step
     * manualimport flow (the *arr v3 spec exposes no `queue/import/{id}`
     * endpoint):
     *
     * 1. `GET /api/v3/manualimport?downloadId={downloadId}` — discover the
     *    candidate file rows Sonarr has identified as importable.
     * 2. `POST /api/v3/manualimport` — re-post those rows verbatim to trigger
     *    the import.
     *
     * [downloadId] is the download-client guid from the queue row (NOT the
     * queue id). Fails with a friendly 404 when no importable files are found.
     */
    suspend fun importQueueItem(baseUrl: String, apiKey: String, downloadId: String): Result<Unit>

    /**
     * `GET /api/v3/calendar?start=...&end=...` — one row per airing episode.
     */
    suspend fun getCalendar(
        baseUrl: String,
        apiKey: String,
        start: String,
        end: String,
    ): Result<List<ArrCalendarItem>>

    /**
     * `GET /api/v3/history?eventType=...` — recent grab/import/fail events.
     */
    suspend fun getHistory(
        baseUrl: String,
        apiKey: String,
        eventType: Int? = null,
    ): Result<List<ArrHistoryItem>>

    /** `GET /api/v3/blocklist` — paginated blocklist (rejected releases). */
    suspend fun getBlocklist(
        baseUrl: String,
        apiKey: String,
        page: Int = 1,
        pageSize: Int = 50,
    ): Result<List<ArrBlocklistItem>>

    /** `DELETE /api/v3/blocklist/{id}` — remove one blocklist entry (re-enables search). */
    suspend fun deleteBlocklistItem(baseUrl: String, apiKey: String, id: Int): Result<Unit>

    /** `DELETE /api/v3/blocklist/bulk` — remove multiple blocklist entries. */
    suspend fun deleteBlocklistItems(baseUrl: String, apiKey: String, ids: List<Int>): Result<Unit>

    /** `GET /api/v3/wanted/missing` — monitored episodes without a file. */
    suspend fun getWanted(
        baseUrl: String,
        apiKey: String,
        page: Int = 1,
        pageSize: Int = 50,
    ): Result<List<ArrWantedItem>>

    /**
     * `POST /api/v3/command` — queues an asynchronous command. Pass [seriesId]
     * for series-scoped commands (RefreshSeries / SeriesSearch) or
     * [episodeIds] for EpisodeSearch. Returns the queued [ArrCommand].
     */
    suspend fun postCommand(
        baseUrl: String,
        apiKey: String,
        commandName: ArrCommandName,
        seriesId: Int? = null,
        episodeIds: List<Int>? = null,
    ): Result<ArrCommand>

    /**
     * `GET /api/v3/series?tvdbId=...` — resolves the Sonarr internal series id
     * for a tvdb id. Returns null when Sonarr has no series matching [tvdbId]
     * (the series isn't tracked). Used by the delete & re-download flow to
     * translate a Jellyfin episode's tvdb id → the Sonarr series id that
     * episode + command endpoints key off.
     */
    suspend fun findSeriesByTvdb(baseUrl: String, apiKey: String, tvdbId: Int): Result<Int?>

    /**
     * `GET /api/v3/episode?seriesId=...&seasonNumber=...&episodeNumber=...` —
     * resolves the Sonarr internal episode id(s) for a specific S/E within a
     * series. Returns multiple ids when Sonarr has duplicate episodes for the
     * same number (rare; multi-episode files). Empty when the series has no
     * episode matching the given [seasonNumber]/[episodeNumber].
     */
    suspend fun getEpisodeIds(
        baseUrl: String,
        apiKey: String,
        seriesId: Int,
        seasonNumber: Int,
        episodeNumber: Int,
    ): Result<List<Int>>

    /**
     * `PUT /api/v3/episode/monitor` — toggles the monitored flag on one or more
     * episodes. Used by the delete & re-download flow to re-mark an episode
     * monitored after a delete, since Sonarr unmonitors episodes whose file is
     * deleted (so it won't re-grab them otherwise). Idempotent.
     */
    suspend fun monitorEpisodes(
        baseUrl: String,
        apiKey: String,
        episodeIds: List<Int>,
        monitored: Boolean,
    ): Result<Unit>

    /**
     * `GET /api/v3/system/status` — connection probe. Succeeds iff 2xx.
     */
    suspend fun testConnection(baseUrl: String, apiKey: String): Result<Unit>
}
