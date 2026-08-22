package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.arr.ArrBlocklistItem
import com.raulshma.jellyplay.core.model.arr.ArrCalendarItem
import com.raulshma.jellyplay.core.model.arr.ArrCommand
import com.raulshma.jellyplay.core.model.arr.ArrCommandName
import com.raulshma.jellyplay.core.model.arr.ArrDownloadSummary
import com.raulshma.jellyplay.core.model.arr.ArrQueueDeleteOptions
import com.raulshma.jellyplay.core.model.arr.ArrRedownloadResult
import com.raulshma.jellyplay.core.model.arr.ArrQueueItem
import com.raulshma.jellyplay.core.model.arr.ArrSeriesEpisode
import com.raulshma.jellyplay.core.model.arr.ArrSeriesResolution
import com.raulshma.jellyplay.core.model.arr.ArrServerConfig
import com.raulshma.jellyplay.core.model.arr.ArrServiceKind
import com.raulshma.jellyplay.core.model.arr.ArrServiceSummary
import com.raulshma.jellyplay.core.model.arr.ArrWantedItem
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Aggregates Radarr + Sonarr into a single read + management surface for the
 * `DIRECT_ARR_INTEGRATION` experimental feature.
 *
 * Server credentials are resolved once (via [resolveServers]) by merging
 * Seerr's auto-discovered servers with the manual override list, then cached
 * for [SERVER_CACHE_TTL_MS]. All queue/calendar/blocklist reads fan out across
 * every resolved server concurrently; per-server failures are swallowed and
 * the remaining servers' results merged, so a single misconfigured instance
 * never blanks the entire feature.
 *
 * Management actions ([deleteQueueItem], [grabQueueItem], [importQueueItem],
 * [deleteBlocklistItem], [searchForTmdb]) route to the owning server using the
 * `serverId` + `serverKind` fields tagged onto each row by [refreshQueue] /
 * [refreshBlocklist]. They return [Result]; failures do not throw but the UI
 * is responsible for surfacing the error message.
 *
 * Refresh is **pull-based** (ViewModels call [refreshQueue] /
 * [refreshCalendar] / [refreshBlocklist]); there is no background singleton
 * poller, mirroring the `SeerrRepository.startPolling` doc rationale — the
 * flag is opt-in and battery-sensitive.
 *
 * Every read method is safe to call when the flag is disabled: queue/calendar
 * flows simply emit empty lists, and [resolveServers] returns an empty
 * [ArrServiceSummary].
 */
interface ArrRepository {

    /**
     * Resolves the current set of Radarr + Sonarr servers (auto-discovered via
     * Seerr when enabled + manual overrides). Cached for
     * [SERVER_CACHE_TTL_MS]; callers should not cache further. Use
     * [invalidateServers] to force a fresh resolution after mutating the
     * server set or the Seerr-discovery toggle.
     */
    suspend fun resolveServers(): Result<ArrServiceSummary>

    /**
     * Drops the resolved-servers cache so the next [resolveServers] re-merges
     * Seerr discovery + manual overrides from scratch. Call after mutating the
     * manual server list or the Seerr-discovery toggle so the UI reflects the
     * change immediately instead of waiting for the TTL to expire.
     */
    fun invalidateServers()

    /**
     * Hot stream of the current download queue across all resolved servers.
     * Empty until [refreshQueue] succeeds at least once.
     */
    fun queue(): Flow<List<ArrQueueItem>>

    /**
     * Hot stream of calendar items (upcoming/recently-released) across all
     * resolved servers, filtered to `[from, to]`. Empty until
     * [refreshCalendar] succeeds at least once with matching bounds.
     */
    fun calendar(from: LocalDate, to: LocalDate): Flow<List<ArrCalendarItem>>

    /**
     * Hot stream of blocklist entries across all resolved servers. Empty until
     * [refreshBlocklist] succeeds at least once.
     */
    fun blocklist(): Flow<List<ArrBlocklistItem>>

    /** Re-fetches the queue from every resolved server. Never throws. */
    suspend fun refreshQueue(): Result<Unit>

    /** Re-fetches the calendar window from every resolved server. Never throws. */
    suspend fun refreshCalendar(from: LocalDate, to: LocalDate): Result<Unit>

    /** Re-fetches the blocklist from every resolved server. Never throws. */
    suspend fun refreshBlocklist(): Result<Unit>

    /**
     * Returns the queue item (if any) currently downloading for [tmdbId]
     * across all resolved servers. Used by Requests to enrich a single
     * request's row without forcing a full queue refresh.
     */
    suspend fun getQueueForTmdb(tmdbId: Int): ArrQueueItem?

    /**
     * Convenience wrapper producing the display-oriented [ArrDownloadSummary]
     * for a single tmdbId. Null when no queue item exists.
     */
    suspend fun getDownloadSummaryForTmdb(tmdbId: Int): ArrDownloadSummary?

    /**
     * Probes a single server's reachability via `GET /api/v3/system/status`.
     * Routes to Radarr or Sonarr by [ArrServerConfig.kind]. Succeeds iff 2xx;
     * failure carries a friendly [com.raulshma.jellyplay.core.network.api.ApiException]
     * message (DNS / connect / timeout / HTTP) suitable for direct UI display.
     *
     * Does not mutate any cache or flow — pure one-shot probe. Safe to call on
     * servers from a freshly resolved [ArrServiceSummary] regardless of
     * `isManual` (both discovered + manual entries expose `baseUrl` + `apiKey`).
     */
    suspend fun testServer(server: ArrServerConfig): Result<Unit>

    // ── Management actions ────────────────────────────────────────────────

    /**
     * Removes a single queue row from its owning server. [options] controls
     * whether the download is removed from the client, added to the blocklist,
     * and whether *arr searches for a replacement.
     */
    suspend fun deleteQueueItem(item: ArrQueueItem, options: ArrQueueDeleteOptions): Result<Unit>

    /**
     * Removes multiple queue rows in one bulk call, grouped by owning server.
     */
    suspend fun deleteQueueItems(items: List<ArrQueueItem>, options: ArrQueueDeleteOptions): Result<Unit>

    /** Force-sends a queued release to its download client. */
    suspend fun grabQueueItem(item: ArrQueueItem): Result<Unit>

    /**
     * Forces an already-importable release to import now via the *arr
     * `manualimport` flow (GET candidate rows by download-client guid, then
     * POST them back). Requires [ArrQueueItem.downloadId]; rows without one
     * cannot be force-imported through this path.
     */
    suspend fun importQueueItem(item: ArrQueueItem): Result<Unit>

    /** Removes a blocklist entry from its owning server (re-enables search). */
    suspend fun deleteBlocklistItem(item: ArrBlocklistItem): Result<Unit>

    /**
     * Triggers a search for [tmdbId] across the relevant servers. Movies →
     * Radarr `SearchMovie` (resolving tmdbId → Radarr internal id first, with
     * a global `MissingMoviesSearch` fallback when the movie isn't tracked);
     * series → Sonarr `MissingEpisodesSearch` (a tmdb→seriesId lookup is a
     * future enhancement). Returns the queued commands (one per matching
     * server) so the UI can show "search started." Empty list when no server
     * is configured.
     */
    suspend fun searchForTmdb(tmdbId: Int, kind: ArrServiceKind): Result<List<ArrCommand>>

    /**
     * The delete & re-download flow: deletes the file through the *arr file-
     * delete API (same as the web UI "Manage Files" → delete), verifies the
     * deletion, re-monitors if needed, then queues a search — so *arr re-grabs
     * a fresh copy. This is the correct flow; deleting only from Jellyfin
     * leaves *arr's `hasFile` stale and the search no-ops.
     *
     * Runs a 4-step sequence ([com.raulshma.jellyplay.core.model.arr.ArrRedownloadStep]):
     * 1. **DELETE_FILE** — resolve the file id (`episodeFileId`/`movieFileId`)
     *    then `DELETE /episodeFile|movieFile/{id}`. Skipped (not failed) when
     *    there's no file. A hard failure here aborts the flow.
     * 2. **VERIFY_DELETED** — re-query, assert `hasFile == false`.
     * 3. **MONITOR** — re-monitor only when not already monitored (idempotent).
     * 4. **SEARCH** — `EpisodeSearch` / `SearchMovie`.
     *
     * Returns [ArrRedownloadResult] with one [com.raulshma.jellyplay.core.model.arr.ArrRedownloadStepResult]
     * per step, in order. Fans out across resolved servers; the first server to
     * succeed on a step wins (per-server failures swallowed). [tmdbId] is
     * required for RADARR; [tvdbId]+[seasonNumber]+[episodeNumber] for SONARR.
     */
    suspend fun redownloadMedia(
        tmdbId: Int,
        kind: ArrServiceKind,
        tvdbId: Int? = null,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
    ): Result<ArrRedownloadResult>

    // ── Sonarr series management ("Manage Series" screen) ────────────────
    //
    // All keyed by the series' tvdb id (the same identity Jellyfin exposes in
    // MediaDetail.providerIds). Each method resolves the owning Sonarr server
    // + internal series id internally via [resolveSonarrSeries], so the UI
    // never needs to know which server tracks the series.

    /**
     * Resolves the Sonarr server + internal series id for [tvdbId]. Returns the
     * [ArrSeriesResolution] (serverId, seriesId, title, monitored) on success;
     * fails when no configured Sonarr instance tracks the series, or when
     * server resolution itself fails.
     */
    suspend fun resolveSonarrSeries(tvdbId: Int): Result<ArrSeriesResolution>

    /**
     * Fetches every episode for the series tracked at [tvdbId], mapped to the
     * rich [ArrSeriesEpisode] projection the management UI renders.
     */
    suspend fun getSonarrEpisodes(tvdbId: Int): Result<List<ArrSeriesEpisode>>

    /** Toggles the monitored flag on one or more episodes (`PUT /episode/monitor`). */
    suspend fun monitorSonarrEpisodes(tvdbId: Int, episodeIds: List<Int>, monitored: Boolean): Result<Unit>

    /** Deletes an episode file (`DELETE /episodeFile/{episodeFileId}`). */
    suspend fun deleteSonarrEpisodeFile(tvdbId: Int, episodeFileId: Int): Result<Unit>

    /** Queues an `EpisodeSearch` for the given episode ids (`POST /command`). */
    suspend fun searchSonarrEpisodes(tvdbId: Int, episodeIds: List<Int>): Result<Unit>

    /** Queues a `SeasonSearch` for monitored episodes in [seasonNumber] (`POST /command`). */
    suspend fun searchMonitoredSonarrSeason(tvdbId: Int, seasonNumber: Int): Result<Unit>

    /** Queues a `RefreshSeries` — refresh metadata from the source (`POST /command`). */
    suspend fun refreshSonarrSeries(tvdbId: Int): Result<Unit>

    /** Queues a `RescanSeries` — scan the disk for files (`POST /command`). */
    suspend fun rescanSonarrSeries(tvdbId: Int): Result<Unit>

    /** Queues a `SeriesSearch` — search all monitored missing episodes (`POST /command`). */
    suspend fun searchSonarrSeries(tvdbId: Int): Result<Unit>

    companion object {
        /** TTL for the resolved-servers cache. */
        const val SERVER_CACHE_TTL_MS = 60_000L
    }
}

