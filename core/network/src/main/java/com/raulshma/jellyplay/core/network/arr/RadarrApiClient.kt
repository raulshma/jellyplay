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
 * Direct client for a Radarr v3 instance.
 *
 * Mirrors the [com.raulshma.jellyplay.core.network.seerr.SeerrApiClient]
 * template: every method is `suspend`, takes `baseUrl` + `apiKey` as the first
 * two params, and returns [Result]. URLs target the Radarr `/api/v3` root;
 * authentication is the `X-Api-Key` header. No Flow — the consuming repository
 * decides cadence and caches via `TtlCache`.
 *
 * Exposes the full queue/management surface Radarr supports: queue read with
 * `includeMovie`, per-item + bulk delete with `removeFromClient` / `blocklist`
 * / `skipRedownload`, force-grab / force-import, blocklist read + delete,
 * wanted/missing list, and the asynchronous command runner
 * (`POST /api/v3/command`) for triggering searches / refreshes / rescans.
 *
 * Adding a method requires updating [ResilientRadarrApiClient] too — see its
 * doc comment.
 */
interface RadarrApiClient {

    /** `GET /api/v3/queue?includeMovie=true` — active downloads with movie metadata. */
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
     * Body: `{ "ids": [...] }`.
     */
    suspend fun deleteQueueItems(
        baseUrl: String,
        apiKey: String,
        ids: List<Int>,
        options: ArrQueueDeleteOptions = ArrQueueDeleteOptions(),
    ): Result<Unit>

    /** `POST /api/v3/queue/grab/{id}` — force-send a queued release to the download client. */
    suspend fun grabQueueItem(baseUrl: String, apiKey: String, id: Int): Result<Unit>

    /** `POST /api/v3/queue/import/{id}` — force an already-importable release to import now. */
    suspend fun importQueueItem(baseUrl: String, apiKey: String, id: Int): Result<Unit>

    /**
     * `GET /api/v3/calendar?start=...&end=...` — movies with cinematic/digital
     * release dates inside `[start, end]` (ISO-8601 dates, inclusive).
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

    /** `GET /api/v3/wanted/missing` — monitored movies without a file. */
    suspend fun getWanted(
        baseUrl: String,
        apiKey: String,
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
        baseUrl: String,
        apiKey: String,
        commandName: ArrCommandName,
        movieIds: List<Int>? = null,
        episodeIds: List<Int>? = null,
    ): Result<ArrCommand>

    /**
     * `GET /api/v3/movie?tmdbId=...` — resolves the Radarr internal movie id
     * for a TMDB id. Returns null when Radarr has no movie matching [tmdbId]
     * (the movie isn't tracked). Used to translate tmdbId → the internal id
     * Radarr commands like [ArrCommandName.SEARCH_MOVIE] require.
     */
    suspend fun findMovieIdByTmdb(baseUrl: String, apiKey: String, tmdbId: Int): Result<Int?>

    /**
     * `GET /api/v3/system/status` — connection probe. Succeeds iff 2xx.
     */
    suspend fun testConnection(baseUrl: String, apiKey: String): Result<Unit>
}

