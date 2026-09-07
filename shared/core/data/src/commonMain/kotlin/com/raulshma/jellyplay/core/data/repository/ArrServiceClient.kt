package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.arr.ArrBlocklistItem
import com.raulshma.jellyplay.core.model.arr.ArrCalendarItem
import com.raulshma.jellyplay.core.model.arr.ArrCommand
import com.raulshma.jellyplay.core.model.arr.ArrCommandName
import com.raulshma.jellyplay.core.model.arr.ArrQueueDeleteOptions
import com.raulshma.jellyplay.core.model.arr.ArrQueueItem
import com.raulshma.jellyplay.core.model.arr.ArrRedownloadStep
import com.raulshma.jellyplay.core.model.arr.ArrRedownloadStepResult
import com.raulshma.jellyplay.core.model.arr.ArrRedownloadStepStatus
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
 *
 * The redownload operations ([lookup], [deleteFile], [verifyDeleted],
 * [monitor], [search]) feed the single shared step-ladder in
 * [ArrRepositoryImpl]; everything service-specific about that flow — the
 * lookup abort reasons and the verify re-query semantics (including Sonarr's
 * inconclusive-re-query WARNING branches) — lives in the adapters.
 */
internal interface ArrServiceClient {

    /** The service's display name ("Radarr" / "Sonarr") for user-visible step messages. */
    val serviceName: String

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

    // ── Redownload ladder operations ───────────────────────────────────────

    /**
     * Lookup phase of the redownload ladder: resolve the item [ref] points at
     * on the bound server (Radarr keys off the ref's tmdbId; Sonarr resolves
     * the tvdb/season/episode triple). Returns [ArrRedownloadLookup.Found]
     * when the item is tracked, or [ArrRedownloadLookup.Aborted] carrying the
     * user-visible reason the flow must stop at the DELETE_FILE gate
     * (unresolvable ids, lookup error, not tracked, episode not found).
     */
    suspend fun lookup(ref: ArrRedownloadRef): ArrRedownloadLookup

    /** `DELETE /movieFile|episodeFile/{id}` — the DELETE_FILE step; success flag only. */
    suspend fun deleteFile(fileId: Int): Boolean

    /**
     * VERIFY_DELETED step: re-query the item and confirm the file is gone,
     * returning the ready-made step result — each service owns its own
     * re-query keys and inconclusive-outcome wording.
     */
    suspend fun verifyDeleted(item: ArrRedownloadItem): ArrRedownloadStepResult

    /** Re-mark the item monitored (`PUT /movie|episode/monitor`); success flag only. */
    suspend fun monitor(id: Int): Boolean

    /** Queue the item's search command (`SearchMovie` / `EpisodeSearch`); success flag only. */
    suspend fun search(id: Int): Boolean
}

/**
 * Identifies the item one redownload run targets, in the union shape both
 * services accept: Radarr keys off [tmdbId] only; Sonarr requires the whole
 * tvdb/season/episode triple.
 */
internal data class ArrRedownloadRef(
    val tmdbId: Int = 0,
    val tvdbId: Int? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
)

/** Outcome of the redownload lookup phase ([ArrServiceClient.lookup]). */
internal sealed interface ArrRedownloadLookup {
    /** Item is tracked; the ladder proceeds to delete → verify → monitor → search. */
    data class Found(val item: ArrRedownloadItem) : ArrRedownloadLookup

    /** Flow aborts at the DELETE_FILE gate; [message] is the user-visible reason. */
    data class Aborted(val message: String) : ArrRedownloadLookup
}

/**
 * One *arr item resolved for the redownload ladder. Besides the ids the
 * generic steps act on ([id] for monitor + search, [fileId] for the delete,
 * [monitored] for the monitor skip), it carries the re-query keys each
 * service's verify step re-asks by — Radarr keys off [tmdbId], Sonarr off
 * [seriesId] + [seasonNumber] + [episodeNumber], where [seasonNumber] is the
 * season the episode *actually* resolved in (it can differ from the requested
 * season after a cross-season fallback). The other service's fields stay at
 * their 0 defaults and are never read.
 */
internal data class ArrRedownloadItem(
    /** Movie/episode id — the monitor + search key. */
    val id: Int,
    /** Movie/episode file id; 0 → no file present, DELETE_FILE skips. */
    val fileId: Int,
    /** Current monitored flag; true → MONITOR skips. */
    val monitored: Boolean,
    /** Radarr verify re-query key: the tmdbId the movie resolved by. */
    val tmdbId: Int = 0,
    /** Sonarr verify re-query keys: owning series id, resolved season, requested episode. */
    val seriesId: Int = 0,
    val seasonNumber: Int = 0,
    val episodeNumber: Int = 0,
)

/** [ArrServiceClient] over a [RadarrApiClient] (the bound server is a Radarr kind). */
internal class RadarrServiceClient(
    private val client: RadarrApiClient,
    private val server: ArrServerConfig,
) : ArrServiceClient {
    override val serviceName: String = "Radarr"
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

    override suspend fun lookup(ref: ArrRedownloadRef): ArrRedownloadLookup {
        val result = client.getMovieForTmdb(server.baseUrl, server.apiKey, ref.tmdbId)
        val movie = result.getOrNull()
        if (result.isFailure) {
            return ArrRedownloadLookup.Aborted("Radarr lookup failed: ${result.exceptionOrNull()?.message}.")
        }
        if (movie == null) {
            return ArrRedownloadLookup.Aborted("Movie (tmdb ${ref.tmdbId}) not tracked in Radarr.")
        }
        return ArrRedownloadLookup.Found(
            ArrRedownloadItem(
                id = movie.id,
                fileId = movie.movieFileId,
                monitored = movie.monitored,
                tmdbId = ref.tmdbId,
            ),
        )
    }

    override suspend fun deleteFile(fileId: Int): Boolean =
        client.deleteMovieFile(server.baseUrl, server.apiKey, fileId).isSuccess

    override suspend fun verifyDeleted(item: ArrRedownloadItem): ArrRedownloadStepResult {
        // Re-query; an inconclusive (failed) re-query counts as verified —
        // `rechecked?.hasFile != true` is true when it returns null too.
        val rechecked = client.getMovieForTmdb(server.baseUrl, server.apiKey, item.tmdbId).getOrNull()
        val verified = rechecked?.hasFile != true
        return ArrRedownloadStepResult(
            ArrRedownloadStep.VERIFY_DELETED,
            if (verified) ArrRedownloadStepStatus.SUCCESS else ArrRedownloadStepStatus.FAILED,
            if (verified) null else "Radarr still reports a file present.",
        )
    }

    override suspend fun monitor(id: Int): Boolean =
        client.monitorMovies(server.baseUrl, server.apiKey, listOf(id), monitored = true).isSuccess

    override suspend fun search(id: Int): Boolean =
        client.postCommand(server.baseUrl, server.apiKey, ArrCommandName.SEARCH_MOVIE, movieIds = listOf(id)).isSuccess
}

/** [ArrServiceClient] over a [SonarrApiClient] (the bound server is a Sonarr kind). */
internal class SonarrServiceClient(
    private val client: SonarrApiClient,
    private val server: ArrServerConfig,
) : ArrServiceClient {
    override val serviceName: String = "Sonarr"
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

    override suspend fun lookup(ref: ArrRedownloadRef): ArrRedownloadLookup {
        if (ref.tvdbId == null || ref.seasonNumber == null || ref.episodeNumber == null) {
            return ArrRedownloadLookup.Aborted("Missing tvdb id or season/episode number.")
        }
        val seriesResult = client.findSeriesByTvdb(server.baseUrl, server.apiKey, ref.tvdbId)
        // Distinguish a genuine "not tracked" (null) from a network/parse error
        // (failure) so the message is actionable instead of misleading.
        val seriesId = seriesResult.getOrNull()
        if (seriesResult.isFailure) {
            return ArrRedownloadLookup.Aborted("Sonarr lookup failed: ${seriesResult.exceptionOrNull()?.message}.")
        }
        if (seriesId == null) {
            return ArrRedownloadLookup.Aborted("Series (tvdb ${ref.tvdbId}) not tracked in Sonarr.")
        }

        val episodeResult = client.getEpisodeInfo(
            server.baseUrl, server.apiKey, seriesId, ref.seasonNumber, ref.episodeNumber,
        )
        val episode = episodeResult.getOrNull()
        if (episodeResult.isFailure) {
            return ArrRedownloadLookup.Aborted("Sonarr episode lookup failed: ${episodeResult.exceptionOrNull()?.message}.")
        }
        if (episode == null) {
            // Episode genuinely absent from Sonarr (not a numbering mismatch we
            // could resolve). Build a diagnostic message showing what Sonarr
            // *does* have so the user can see the discrepancy. A season with a
            // null first episode number (empty summary row) renders bare —
            // `first()` would throw and lose the whole step table.
            val diag = client.getSeasonSummaries(server.baseUrl, server.apiKey, seriesId)
                .getOrNull()
                ?.takeIf { it.isNotEmpty() }
                ?.joinToString(", ") { summ ->
                    val first = summ.episodeNumbers.firstOrNull()
                    val last = summ.episodeNumbers.lastOrNull()
                    "S${summ.seasonNumber}" + when {
                        first == null -> " (no episodes listed)"
                        last != null && last > first -> " (eps $first–$last)"
                        else -> " (eps $first)"
                    }
                }
            val hint = if (diag != null) {
                "Sonarr has: $diag. "
            } else {
                "Sonarr has no episodes for this series. "
            }
            return ArrRedownloadLookup.Aborted(
                "${hint}No episode numbered E${ref.episodeNumber} found " +
                    "(requested as S${ref.seasonNumber}E${ref.episodeNumber}) in series $seriesId.",
            )
        }

        return ArrRedownloadLookup.Found(
            ArrRedownloadItem(
                id = episode.id,
                fileId = episode.episodeFileId,
                monitored = episode.monitored,
                seriesId = seriesId,
                // The re-query uses the episode's *actual* Sonarr season (may
                // differ from the requested season when the cross-season
                // fallback resolved it) and the requested episode number.
                seasonNumber = episode.seasonNumber,
                episodeNumber = ref.episodeNumber,
            ),
        )
    }

    override suspend fun deleteFile(fileId: Int): Boolean =
        client.deleteEpisodeFile(server.baseUrl, server.apiKey, fileId).isSuccess

    override suspend fun verifyDeleted(item: ArrRedownloadItem): ArrRedownloadStepResult {
        val recheckResult = client.getEpisodeInfo(
            server.baseUrl, server.apiKey, item.seriesId, item.seasonNumber, item.episodeNumber,
        )
        val rechecked = recheckResult.getOrNull()
        return when {
            recheckResult.isFailure -> ArrRedownloadStepResult(
                ArrRedownloadStep.VERIFY_DELETED,
                ArrRedownloadStepStatus.WARNING,
                "Couldn't re-query Sonarr to confirm deletion (${recheckResult.exceptionOrNull()?.message}); the delete command did return success.",
            )
            rechecked == null -> ArrRedownloadStepResult(
                ArrRedownloadStep.VERIFY_DELETED,
                ArrRedownloadStepStatus.WARNING,
                "Sonarr no longer reports the episode after delete (it may have been removed); cannot confirm file status.",
            )
            rechecked.hasFile -> ArrRedownloadStepResult(
                ArrRedownloadStep.VERIFY_DELETED,
                ArrRedownloadStepStatus.FAILED,
                "Sonarr still reports a file present.",
            )
            else -> ArrRedownloadStepResult(
                ArrRedownloadStep.VERIFY_DELETED,
                ArrRedownloadStepStatus.SUCCESS,
                null,
            )
        }
    }

    override suspend fun monitor(id: Int): Boolean =
        client.monitorEpisodes(server.baseUrl, server.apiKey, listOf(id), monitored = true).isSuccess

    override suspend fun search(id: Int): Boolean =
        client.postCommand(server.baseUrl, server.apiKey, ArrCommandName.SEARCH_EPISODES, episodeIds = listOf(id)).isSuccess
}
