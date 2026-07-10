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
     * for series-scoped commands (RefreshSeries / SeriesSearch), [episodeIds]
     * for EpisodeSearch, or [seasonNumber] for SeasonSearch (requires [seriesId]).
     * Returns the queued [ArrCommand].
     */
    suspend fun postCommand(
        baseUrl: String,
        apiKey: String,
        commandName: ArrCommandName,
        seriesId: Int? = null,
        episodeIds: List<Int>? = null,
        seasonNumber: Int? = null,
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
     * Resolves the single episode matching S/E, returning the fields the
     * delete & re-download flow needs.
     *
     * Lookup strategy (handles numbering mismatches between Jellyfin and Sonarr
     * — split seasons, anime absolute numbering, specials placement):
     * 1. `GET /episode?seriesId=&seasonNumber=` then client-side filter by
     *    [episodeNumber]. Fast path; the common case.
     * 2. If (1) misses, `GET /episode?seriesId=` (all episodes) and match on
     *    [episodeNumber] **across all seasons**. Handles cases where Jellyfin's
     *    `ParentIndexNumber` (season) disagrees with Sonarr's `SeasonNumber`.
     *    The matched episode's actual season is in [SonarrEpisodeInfo.seasonNumber].
     *
     * Note: Sonarr's `/episode` controller has **no `episodeNumber` query
     * param**; passing one is silently ignored and returns the whole season.
     *
     * Returns null when no episode matches [episodeNumber] in any season.
     * [SonarrEpisodeInfo.episodeFileId] is 0 when the episode has no file.
     * Use [getSeasonSummaries] on the null path to build a diagnostic message.
     */
    suspend fun getEpisodeInfo(
        baseUrl: String,
        apiKey: String,
        seriesId: Int,
        seasonNumber: Int,
        episodeNumber: Int,
    ): Result<SonarrEpisodeInfo?>

    /**
     * `GET /api/v3/episode?seriesId=...` — a compact per-season index of the
     * series' episodes. Used **only** on the `getEpisodeInfo` null path to build
     * a diagnostic message ("Sonarr has S0 (eps 1–3), S1 (eps 1–24); no match
     * for S5E12"). Not needed when [getEpisodeInfo] resolves successfully.
     */
    suspend fun getSeasonSummaries(
        baseUrl: String,
        apiKey: String,
        seriesId: Int,
    ): Result<List<SonarrSeasonSummary>>

    /**
     * `DELETE /api/v3/episodeFile/{id}` — deletes an episode's file (the same
     * flow the Sonarr web UI uses under "Manage Episodes" → delete). Clears
     * `hasFile` server-side so a subsequent `EpisodeSearch` will re-grab.
     * Returns 200 (not 204); a 409 indicates the series' root folder is missing
     * or empty (surfaced as an actionable error). Safe to 404 if the file was
     * already removed.
     */
    suspend fun deleteEpisodeFile(baseUrl: String, apiKey: String, episodeFileId: Int): Result<Unit>

    /**
     * `PUT /api/v3/episode/monitor` — toggles the monitored flag on one or more
     * episodes. Idempotent. Used by the delete & re-download flow to guarantee
     * the episode is monitored before searching (Sonarr does NOT auto-unmonitor
     * on file delete by default, so this is a safety net, not always required).
     */
    suspend fun monitorEpisodes(
        baseUrl: String,
        apiKey: String,
        episodeIds: List<Int>,
        monitored: Boolean,
    ): Result<Unit>

    /**
     * `GET /api/v3/series?tvdbId=...` — resolves whether Sonarr tracks this
     * series and, if so, returns its internal id + monitored flag. Mirrors
     * [findSeriesByTvdb] but keeps the series metadata instead of returning just
     * the id. Used by the "Manage Series" screen to locate the owning series.
     *
     * Returns null when no series matches [tvdbId] (not tracked). As with
     * [findSeriesByTvdb], the `?tvdbId=` query param is treated as untrusted —
     * the result is filtered client-side because some Sonarr versions ignore it.
     */
    suspend fun getSeriesInfo(baseUrl: String, apiKey: String, tvdbId: Int): Result<SonarrSeriesInfo?>

    /**
     * `GET /api/v3/episode?seriesId=...` — the rich projection used by the
     * "Manage Series" screen: every episode in the series with its season /
     * episode numbers, title, air date, overview, monitored flag, and (when a
     * file exists) file id + size + quality. Mapped to [ArrSeriesEpisode].
     *
     * Distinct from [getEpisodeInfo] (single-episode lookup for the redownload
     * flow) and [getSeasonSummaries] (compact diagnostic index): this returns
     * every episode and the full field set the management UI needs.
     */
    suspend fun getEpisodesForSeries(baseUrl: String, apiKey: String, seriesId: Int): Result<List<ArrSeriesEpisode>>

    /**
     * `GET /api/v3/system/status` — connection probe. Succeeds iff 2xx.
     */
    suspend fun testConnection(baseUrl: String, apiKey: String): Result<Unit>
}

/**
 * Resolved series identity + monitored flag, mapped from `GET /api/v3/series`.
 * Kept in `core.network` (like [SonarrEpisodeInfo]) as a client-facing contract.
 */
data class SonarrSeriesInfo(
    val id: Int,
    val title: String,
    val monitored: Boolean,
    /** On-disk root folder for the series (Sonarr `path`). Null when absent. */
    val path: String? = null,
)

/**
 * Sonarr episode info needed for the delete & re-download flow, mapped from
 * `GET /api/v3/episode` rows. Kept in `core.network` (not `core.model`) as a
 * client-facing contract, mirroring how the *arr clients own their DTO shapes.
 */
data class SonarrEpisodeInfo(
    /** Sonarr internal episode id (used for `EpisodeSearch` + monitor). */
    val id: Int,
    /** The episode file's id; 0 when no file exists. Used for `DELETE /episodeFile/{id}`. */
    val episodeFileId: Int,
    /** True when the episode has a file linked. Re-queried post-delete to verify. */
    val hasFile: Boolean,
    /** Current monitored flag. Re-monitor is skipped when already true. */
    val monitored: Boolean,
    /**
     * Sonarr's season number for this episode. Differs from the requested
     * season when a cross-season fallback resolved the episode — include it in
     * the result so callers (and diagnostic messages) use Sonarr's truth.
     */
    val seasonNumber: Int,
)

/**
 * Compact per-season index, for diagnostic messages when an episode isn't
 * found. [episodeNumbers] is the list of episode numbers Sonarr has in that
 * season.
 */
data class SonarrSeasonSummary(
    val seasonNumber: Int,
    val episodeNumbers: List<Int>,
)
