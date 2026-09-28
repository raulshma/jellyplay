package com.raulshma.jellyplay.core.network.arr

import com.raulshma.jellyplay.core.model.arr.ArrBlocklistItem
import com.raulshma.jellyplay.core.model.arr.ArrCalendarItem
import com.raulshma.jellyplay.core.model.arr.ArrCommand
import com.raulshma.jellyplay.core.model.arr.ArrCommandName
import com.raulshma.jellyplay.core.model.arr.ArrHistoryItem
import com.raulshma.jellyplay.core.model.arr.ArrQueueDeleteOptions
import com.raulshma.jellyplay.core.model.arr.ArrQueueItem
import com.raulshma.jellyplay.core.model.arr.ArrRelease
import com.raulshma.jellyplay.core.model.arr.ArrServerConfig
import com.raulshma.jellyplay.core.model.arr.ArrWantedItem

/**
 * Direct client for a Radarr v3 instance.
 *
 * Mirrors the [com.raulshma.jellyplay.core.network.seerr.SeerrApiClient]
 * template — every method is `suspend` and returns [Result] — with one
 * deliberate divergence: every method takes one [ArrServerConfig] `server`
 * connection as its first parameter where Seerr takes a bare `baseUrl` +
 * credential pair. The `(baseUrl, apiKey)` couple used to travel unnamed
 * through every signature, so each endpoint cost two extra params and every
 * binding call site re-spelled the pair; the connection object gives it one
 * name and one construction site (the repository's resolved, de-duped server
 * list — the same type `testConnection`-style probes already routed by).
 * Only [ArrServerConfig.baseUrl] + [ArrServerConfig.apiKey] are read here;
 * the identity fields (`id`/`name`/`kind`/`isManual`) stay routing/UI
 * metadata. Seerr keeps its own parameter shape on purpose (cookie-carrying;
 * see the `ArrClientSupport` KDoc on the deliberate sibling split).
 *
 * URLs target the Radarr `/api/v3` root;
 * authentication is the `X-Api-Key` header. No Flow — the consuming repository
 * decides cadence and caches via `TtlCache`.
 *
 * Exposes the full queue/management surface Radarr supports: queue read with
 * `includeMovie`, per-item + bulk delete with `removeFromClient` / `blocklist`
 * / `skipRedownload`, force-grab / force-import, blocklist read + delete,
 * wanted/missing list, and the asynchronous command runner
 * (`POST /api/v3/command`) for triggering searches / refreshes / rescans.
 *
 * Adding a method lands on the impl directly — retry rides the shared
 * jvmShared request funnel (`ArrClientSupport`'s [com.raulshma.jellyplay.core.network.api.HttpExecutor]),
 * so there is no separate retrying wrapper to keep in sync anymore.
 */
interface RadarrApiClient {

    /** `GET /api/v3/queue?includeMovie=true` — active downloads with movie metadata. */
    suspend fun getQueue(server: ArrServerConfig): Result<List<ArrQueueItem>>

    /**
     * `DELETE /api/v3/queue/{id}` — removes one queue row. [options] maps to
     * the `removeFromClient` / `blocklist` / `skipRedownload` query params.
     */
    suspend fun deleteQueueItem(
        server: ArrServerConfig,
        id: Int,
        options: ArrQueueDeleteOptions = ArrQueueDeleteOptions(),
    ): Result<Unit>

    /**
     * `DELETE /api/v3/queue/bulk` — removes multiple queue rows in one call.
     * Body: `{ "ids": [...] }`.
     */
    suspend fun deleteQueueItems(
        server: ArrServerConfig,
        ids: List<Int>,
        options: ArrQueueDeleteOptions = ArrQueueDeleteOptions(),
    ): Result<Unit>

    /** `POST /api/v3/queue/grab/{id}` — force-send a queued release to the download client. */
    suspend fun grabQueueItem(server: ArrServerConfig, id: Int): Result<Unit>

    /**
     * Force-imports an already-importable release via the documented 2-step
     * manualimport flow (the *arr v3 spec exposes no `queue/import/{id}`
     * endpoint):
     *
     * 1. `GET /api/v3/manualimport?downloadId={downloadId}` — discover the
     *    candidate file rows the *arr has identified as importable.
     * 2. `POST /api/v3/manualimport` — re-post those rows verbatim to trigger
     *    the import.
     *
     * [downloadId] is the download-client guid from the queue row (NOT the
     * queue id). Fails with a friendly 404 when no importable files are found.
     */
    suspend fun importQueueItem(server: ArrServerConfig, downloadId: String): Result<Unit>

    /**
     * `GET /api/v3/calendar?start=...&end=...` — movies with cinematic/digital
     * release dates inside `[start, end]` (ISO-8601 dates, inclusive).
     */
    suspend fun getCalendar(
        server: ArrServerConfig,
        start: String,
        end: String,
    ): Result<List<ArrCalendarItem>>

    /**
     * `GET /api/v3/history?eventType=...` — recent grab/import/fail events.
     */
    suspend fun getHistory(
        server: ArrServerConfig,
        eventType: Int? = null,
    ): Result<List<ArrHistoryItem>>

    /** `GET /api/v3/blocklist` — paginated blocklist (rejected releases). */
    suspend fun getBlocklist(
        server: ArrServerConfig,
        page: Int = 1,
        pageSize: Int = 50,
    ): Result<List<ArrBlocklistItem>>

    /** `DELETE /api/v3/blocklist/{id}` — remove one blocklist entry (re-enables search). */
    suspend fun deleteBlocklistItem(server: ArrServerConfig, id: Int): Result<Unit>

    /** `DELETE /api/v3/blocklist/bulk` — remove multiple blocklist entries. */
    suspend fun deleteBlocklistItems(server: ArrServerConfig, ids: List<Int>): Result<Unit>

    /** `GET /api/v3/wanted/missing` — monitored movies without a file. */
    suspend fun getWanted(
        server: ArrServerConfig,
        page: Int = 1,
        pageSize: Int = 50,
    ): Result<List<ArrWantedItem>>

    /**
     * `POST /api/v3/command` — queues an asynchronous command. Returns the
     * queued [ArrCommand] with its id + initial status. Use [commandName] +
     * optional movie/episode ids to trigger searches, refreshes, rescans.
     *
     * Note: Radarr command `movieId` is the internal movie id, not the tmdbId.
     * Resolve it first via [findMovieIdByTmdb] when triggering a single-movie
     * command like [ArrCommandName.SEARCH_MOVIE].
     */
    suspend fun postCommand(
        server: ArrServerConfig,
        commandName: ArrCommandName,
        movieIds: List<Int>? = null,
        episodeIds: List<Int>? = null,
    ): Result<ArrCommand>

    /**
     * `GET /api/v3/release?movieId=...` — the interactive release-search rows
     * for one movie (Radarr's internal movie id, not the tmdbId). Fails with
     * [ArrReleaseCacheMiss] when the server has no cached search results (the
     * search command must run first / the ~30 min decision cache expired) —
     * the UI offers "Search again" on that failure.
     */
    suspend fun searchReleases(server: ArrServerConfig, movieId: Int): Result<List<ArrRelease>>

    /**
     * `POST /api/v3/release` — grabs [release] (its `guid` + `indexerId` form
     * the required identity). With [shouldOverride] the grab additionally
     * carries [movieId] (Radarr's identity field for the override arm) and
     * the release's own quality prefill, so a release Radarr rejected can be
     * grabbed anyway.
     */
    suspend fun grabRelease(
        server: ArrServerConfig,
        release: ArrRelease,
        movieId: Int? = null,
        shouldOverride: Boolean = false,
    ): Result<Unit>

    /**
     * `GET /api/v3/movie?tmdbId=...` — resolves the Radarr internal movie id
     * for a TMDB id. Returns null when Radarr has no movie matching [tmdbId]
     * (the movie isn't tracked). Used to translate tmdbId → the internal id
     * Radarr commands like [ArrCommandName.SEARCH_MOVIE] require.
     */
    suspend fun findMovieIdByTmdb(server: ArrServerConfig, tmdbId: Int): Result<Int?>

    /**
     * `GET /api/v3/movie?tmdbId=...` → maps the full [RadarrMovieInfo] needed
     * for the delete & re-download flow (internal id, movieFileId, hasFile,
     * monitored). Returns null when Radarr has no movie matching [tmdbId].
     */
    suspend fun getMovieForTmdb(server: ArrServerConfig, tmdbId: Int): Result<RadarrMovieInfo?>

    /**
     * `DELETE /api/v3/movieFile/{id}` — deletes a movie's file (the same flow
     * the Radarr web UI uses under movie detail → delete file). Clears
     * `hasFile` server-side so a subsequent `SearchMovie` will re-grab.
     * Returns 200 (not 204); a 409 indicates the movie's root folder is missing
     * or empty (surfaced as an actionable error).
     */
    suspend fun deleteMovieFile(server: ArrServerConfig, movieFileId: Int): Result<Unit>

    /**
     * `PUT /api/v3/movie/monitor` — toggles the monitored flag on one or more
     * movies. Idempotent. Used by the delete & re-download flow to guarantee
     * the movie is monitored before searching (Radarr never auto-unmonitors on
     * file delete, so this is a safety net, not always required).
     */
    suspend fun monitorMovies(
        server: ArrServerConfig,
        movieIds: List<Int>,
        monitored: Boolean,
    ): Result<Unit>

    /**
     * `GET /api/v3/system/status` — connection probe. Succeeds iff 2xx.
     */
    suspend fun testConnection(server: ArrServerConfig): Result<Unit>
}

/**
 * Radarr movie info needed for the delete & re-download flow, mapped from
 * `GET /api/v3/movie?tmdbId=`. Kept in `core.network` as a client-facing
 * contract, mirroring how the *arr clients own their DTO shapes.
 */
data class RadarrMovieInfo(
    /** Radarr internal movie id (used for `SearchMovie` + monitor). */
    val id: Int,
    /** The movie file's id; 0 when no file exists. Used for `DELETE /movieFile/{id}`. */
    val movieFileId: Int,
    /** True when the movie has a file linked. Re-queried post-delete to verify. */
    val hasFile: Boolean,
    /** Current monitored flag. Re-monitor is skipped when already true. */
    val monitored: Boolean,
)
