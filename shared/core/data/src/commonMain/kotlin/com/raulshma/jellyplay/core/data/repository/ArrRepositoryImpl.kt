package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.datastore.ArrPreferencesStore
import com.raulshma.jellyplay.core.model.TtlCache
import com.raulshma.jellyplay.core.model.arr.ArrBlocklistItem
import com.raulshma.jellyplay.core.model.arr.ArrCalendarItem
import com.raulshma.jellyplay.core.model.arr.ArrCommand
import com.raulshma.jellyplay.core.model.arr.ArrCommandName
import com.raulshma.jellyplay.core.model.arr.ArrDiscoveryError
import com.raulshma.jellyplay.core.model.arr.ArrDownloadSummary
import com.raulshma.jellyplay.core.model.arr.ArrQueueDeleteOptions
import com.raulshma.jellyplay.core.model.arr.ArrQueueItem
import com.raulshma.jellyplay.core.model.arr.ArrRedownloadResult
import com.raulshma.jellyplay.core.model.arr.ArrSeriesEpisode
import com.raulshma.jellyplay.core.model.arr.ArrSeriesResolution
import com.raulshma.jellyplay.core.model.arr.ArrRedownloadStep
import com.raulshma.jellyplay.core.model.arr.ArrRedownloadStepResult
import com.raulshma.jellyplay.core.model.arr.ArrRedownloadStepStatus
import com.raulshma.jellyplay.core.network.arr.RadarrApiClient
import com.raulshma.jellyplay.core.network.arr.SonarrApiClient
import com.raulshma.jellyplay.core.model.arr.ArrServerConfig
import com.raulshma.jellyplay.core.model.arr.ArrServiceKind
import com.raulshma.jellyplay.core.model.arr.ArrServiceSummary
import com.raulshma.jellyplay.core.concurrency.mapConcurrent
import com.raulshma.jellyplay.core.model.seerr.SeerrRadarrSettings
import com.raulshma.jellyplay.core.model.seerr.SeerrSonarrSettings
import com.raulshma.jellyplay.core.model.seerr.arrBaseUrl
import com.raulshma.jellyplay.core.network.api.ApiException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate

/**
 * Default [ArrRepository]. See the interface KDoc for the overall contract;
 * this impl owns three concerns:
 *
 * 1. **Server resolution** ([resolveServers]). Merges Seerr's
 *    `/settings/{radarr,sonarr}` auto-discovered servers with the manual
 *    override list from [ArrPreferencesStore], de-duplicating by canonical
 *    base URL so a server present in both is fetched once. Discovery reads
 *    the `/settings` endpoints (not `/service`) because only the settings
 *    endpoints return the real `apiKey` + `hostname` — the `/service/{id}`
 *    endpoint is non-sensitive and redacts credentials. This means the Seerr
 *    account must have Admin permission; a 401/403 is surfaced as
 *    [ArrDiscoveryError.NoAdminPermission]. Resolution is bounded by a
 *    [Semaphore] (4 concurrent client calls), mirroring
 *    `RequestsViewModel.enrichRequests`. Cached for
 *    [ArrRepository.SERVER_CACHE_TTL_MS] via [TtlCache].
 *
 * 2. **Queue/calendar fan-out** ([refreshQueue], [refreshCalendar]). For each
 *    resolved server the matching client is called concurrently; per-server
 *    failures are caught and swallowed so one bad instance cannot blank the
 *    feature. Results are concatenated and pushed into hot [MutableStateFlow]s.
 *
 * 3. **Per-tmdb lookup** ([getQueueForTmdb]). Triggers a queue refresh (subject
 *    to the in-memory cache) then returns the first match. Used by Requests to
 *    enrich a single row.
 *
 * All public methods are safe to call when the experimental flag is off —
 * they degrade to empty results without throwing, so the consuming ViewModels
 * do not need their own try/catch around *arr.
 *
 * Concurrency note: the cache scope is the shared `@ApplicationScope`
 * application scope (never cancelled — this singleton lives for the process
 * lifetime); its `SupervisorJob` context keeps a failure in one fan-out
 * branch from cancelling siblings.
 */
class ArrRepositoryImpl(
    private val radarrApiClient: RadarrApiClient,
    private val sonarrApiClient: SonarrApiClient,
    private val seerrRepository: SeerrRepository,
    private val arrPreferencesStore: ArrPreferencesStore,
    private val cacheScope: CoroutineScope,
) : ArrRepository {

    /** Bounded concurrency for Seerr detail fan-out during server resolution. */
    private val resolveSemaphore = Semaphore(4)

    private val serverCache = TtlCache<ArrServiceSummary>(ttlMs = ArrRepository.SERVER_CACHE_TTL_MS)

    private val _queue = MutableStateFlow<List<ArrQueueItem>>(emptyList())
    override fun queue(): Flow<List<ArrQueueItem>> = _queue

    private val _blocklist = MutableStateFlow<List<ArrBlocklistItem>>(emptyList())
    override fun blocklist(): Flow<List<ArrBlocklistItem>> = _blocklist

    /**
     * Calendar cache holder. The [windowKey] encodes the `[from_to]` bounds so
     * a window change is detectable; [items] is the raw merged list. Consumers
     * read via [calendar] which filters to the requested bounds.
     */
    private data class CalendarCache(val windowKey: String, val items: List<ArrCalendarItem>)
    private val _calendar = MutableStateFlow(CalendarCache("", emptyList()))

    override fun calendar(from: LocalDate, to: LocalDate): Flow<List<ArrCalendarItem>> {
        val key = windowKey(from, to)
        return _calendar.map { cache ->
            if (cache.windowKey == key) {
                cache.items
            } else {
                cache.items.filter { matchesWindow(it, from, to) }
            }
        }
    }

    override suspend fun resolveServers(): Result<ArrServiceSummary> = withContext(cacheScope.coroutineContext) {
        serverCache.get(SERVERS_KEY)?.let { return@withContext Result.success(it) }

        val prefs = arrPreferencesStore.preferences.value
        val manualRadarr = prefs.manualServers.filter { it.kind == ArrServiceKind.RADARR }
        val manualSonarr = prefs.manualServers.filter { it.kind == ArrServiceKind.SONARR }

        // Discovery outcomes carry an error type so a 401/403 (non-admin Seerr
        // account) surfaces as a distinct UI message instead of an empty list.
        // Both discoveries are independent — run them concurrently.
        val (radarrOutcome, sonarrOutcome) = coroutineScope {
            val radarrDeferred = async { if (prefs.useSeerrDiscovery) discoverRadarrServers() else DiscoveryOutcome.success() }
            val sonarrDeferred = async { if (prefs.useSeerrDiscovery) discoverSonarrServers() else DiscoveryOutcome.success() }
            radarrDeferred.await() to sonarrDeferred.await()
        }

        // De-dup by canonical baseUrl: manual entries take precedence (a user
        // who manually overrides a discovered server wins).
        val radarr = dedupByBaseUrl(manualRadarr + radarrOutcome.servers)
        val sonarr = dedupByBaseUrl(manualSonarr + sonarrOutcome.servers)
        val summary = ArrServiceSummary(
            radarrServers = radarr,
            sonarrServers = sonarr,
            discoveryError = primaryDiscoveryError(radarrOutcome, sonarrOutcome),
        )
        serverCache.put(SERVERS_KEY, summary)
        Result.success(summary)
    }

    override fun invalidateServers() {
        serverCache.remove(SERVERS_KEY)
    }

    override suspend fun refreshQueue(): Result<Unit> = withContext(cacheScope.coroutineContext) {
        val summary = resolveServers().getOrDefault(ArrServiceSummary())
        if (summary.isEmpty) {
            _queue.value = emptyList()
            return@withContext Result.success(Unit)
        }
        val combined = resolveSemaphore.mapConcurrent(summary.radarrServers + summary.sonarrServers) { srv ->
            clientFor(srv).getQueue()
                .getOrElse { emptyList() }
                .map { it.tagged(srv.id, srv.kind) }
        }.flatten()
        _queue.value = combined
        Result.success(Unit)
    }

    override suspend fun refreshCalendar(from: LocalDate, to: LocalDate): Result<Unit> =
        withContext(cacheScope.coroutineContext) {
            val summary = resolveServers().getOrDefault(ArrServiceSummary())
            val key = windowKey(from, to)
            if (summary.isEmpty) {
                _calendar.value = CalendarCache(key, emptyList())
                return@withContext Result.success(Unit)
            }
            // LocalDate.toString() == ISO-8601 yyyy-MM-dd == the old
            // DateTimeFormatter.ISO_LOCAL_DATE output (byte-identical query
            // params to the Radarr/Sonarr `/api/v3/calendar` endpoints).
            val startStr = from.toString()
            val endStr = to.toString()
            val combined = resolveSemaphore.mapConcurrent(summary.radarrServers + summary.sonarrServers) { srv ->
                clientFor(srv).getCalendar(startStr, endStr).getOrElse { emptyList() }
            }.flatten()
            _calendar.value = CalendarCache(key, combined)
            Result.success(Unit)
        }

    override suspend fun getQueueForTmdb(tmdbId: Int): ArrQueueItem? {
        // Refresh if the in-memory queue is empty; otherwise the existing
        // (possibly stale by ≤ poll interval) snapshot is fine for an
        // individual row lookup.
        if (_queue.value.isEmpty()) {
            refreshQueue()
        }
        return _queue.value.firstOrNull { it.tmdbId == tmdbId }
    }

    override suspend fun getDownloadSummaryForTmdb(tmdbId: Int): ArrDownloadSummary? {
        val item = getQueueForTmdb(tmdbId) ?: return null
        return ArrDownloadSummary(
            status = item.status,
            percent = item.percent,
            sizeLeft = item.sizeLeft,
            timeLeft = item.timeLeft,
        )
    }

    override suspend fun refreshBlocklist(): Result<Unit> = withContext(cacheScope.coroutineContext) {
        val summary = resolveServers().getOrDefault(ArrServiceSummary())
        if (summary.isEmpty) {
            _blocklist.value = emptyList()
            return@withContext Result.success(Unit)
        }
        val combined = resolveSemaphore.mapConcurrent(summary.radarrServers + summary.sonarrServers) { srv ->
            clientFor(srv).getBlocklist()
                .getOrElse { emptyList() }
                .map { it.tagged(srv.id, srv.kind) }
        }.flatten()
        _blocklist.value = combined
        Result.success(Unit)
    }

    override suspend fun testServer(server: ArrServerConfig): Result<Unit> =
        withContext(cacheScope.coroutineContext) {
            clientFor(server).testConnection()
        }

    // ── Management actions ─────────────────────────────────────────────────

    override suspend fun deleteQueueItem(item: ArrQueueItem, options: ArrQueueDeleteOptions): Result<Unit> =
        withContext(cacheScope.coroutineContext) {
            withServer(item.serverId, item.serverKind, refresh = { refreshQueue() }) { client ->
                client.deleteQueueItem(item.queueId, options)
            }
        }

    override suspend fun deleteQueueItems(items: List<ArrQueueItem>, options: ArrQueueDeleteOptions): Result<Unit> =
        withContext(cacheScope.coroutineContext) {
            if (items.isEmpty()) return@withContext Result.success(Unit)
            val summary = resolveServers().getOrDefault(ArrServiceSummary())
            // Group by (serverId, kind) so each server gets one bulk call.
            val byServer = items.groupBy { it.serverId to it.serverKind }
            val results = coroutineScope {
                byServer.map { (key, group) ->
                    val (serverId, kind) = key
                    val server = (if (kind == ArrServiceKind.RADARR) summary.radarrServers else summary.sonarrServers)
                        .firstOrNull { it.id == serverId }
                    async {
                        if (server == null) return@async Result.failure<Unit>(noServerException())
                        val ids = group.map { it.queueId }
                        clientFor(server).deleteQueueItems(ids, options)
                    }
                }.awaitAll()
            }
            if (results.any { it.isFailure }) {
                // Report the first failure but still refresh so successful deletes show.
                refreshQueue()
                results.first { it.isFailure }
            } else {
                refreshQueue()
                Result.success(Unit)
            }
        }

    override suspend fun grabQueueItem(item: ArrQueueItem): Result<Unit> =
        withContext(cacheScope.coroutineContext) {
            val server = findServer(item.serverId, item.serverKind) ?: return@withContext noServer()
            clientFor(server).grabQueueItem(item.queueId)
        }

    override suspend fun importQueueItem(item: ArrQueueItem): Result<Unit> =
        withContext(cacheScope.coroutineContext) {
            val server = findServer(item.serverId, item.serverKind) ?: return@withContext noServer()
            // Import drives the manualimport flow keyed off the download-client
            // guid, not the queue row id. Rows without one (rare; legacy/
            // untracked) cannot be force-imported via this path.
            val downloadId = item.downloadId
                ?: return@withContext Result.failure(
                    ApiException.fromHttp(404, "Download id missing — cannot trigger manual import.")
                )
            clientFor(server).importQueueItem(downloadId)
        }

    override suspend fun deleteBlocklistItem(item: ArrBlocklistItem): Result<Unit> =
        withContext(cacheScope.coroutineContext) {
            withServer(item.serverId, item.serverKind, refresh = { refreshBlocklist() }) { client ->
                client.deleteBlocklistItem(item.id)
            }
        }

    override suspend fun searchForTmdb(tmdbId: Int, kind: ArrServiceKind): Result<List<ArrCommand>> =
        withContext(cacheScope.coroutineContext) {
            val summary = resolveServers().getOrDefault(ArrServiceSummary())
            if (summary.isEmpty) return@withContext Result.success(emptyList())
            val command = when (kind) {
                ArrServiceKind.RADARR -> ArrCommandName.SEARCH_MOVIE
                ArrServiceKind.SONARR -> ArrCommandName.SEARCH_SERIES
            }
            coroutineScope {
                val servers = if (kind == ArrServiceKind.RADARR) summary.radarrServers else summary.sonarrServers
                servers.map { srv ->
                    async {
                        val client = clientFor(srv)
                        if (kind == ArrServiceKind.RADARR) {
                            // Radarr's SearchMovie keys off the internal movie id, not the tmdbId.
                            // Resolve tmdbId → Radarr movie id first; if the movie isn't tracked
                            // (lookup returns null), fall back to a global MissingMoviesSearch
                            // rather than silently no-op'ing.
                            val movieId = radarrApiClient.findMovieIdByTmdb(srv.baseUrl, srv.apiKey, tmdbId)
                                .getOrNull()
                            if (movieId != null) {
                                client.postCommand(command, movieIds = listOf(movieId))
                            } else {
                                client.postCommand(ArrCommandName.MISSING_SEARCH)
                            }
                        } else {
                            // Sonarr identifies by internal seriesId, not tmdbId; passing tmdbId
                            // as seriesId is wrong. Fall back to a global MissingEpisodesSearch
                            // which the user can trigger; a tmdb→seriesId lookup would need the
                            // /series lookup endpoint (future enhancement).
                            client.postCommand(ArrCommandName.MISSING_EPISODES)
                        }.getOrNull()
                    }
                }.awaitAll().filterNotNull()
            }.let { Result.success(it) }
    }

    override suspend fun redownloadMedia(
        tmdbId: Int,
        kind: ArrServiceKind,
        tvdbId: Int?,
        seasonNumber: Int?,
        episodeNumber: Int?,
    ): Result<ArrRedownloadResult> = withContext(cacheScope.coroutineContext) {
        val summary = resolveServers().getOrDefault(ArrServiceSummary())
        val servers = if (kind == ArrServiceKind.RADARR) summary.radarrServers else summary.sonarrServers
        if (servers.isEmpty()) {
            // No relevant server configured — can't do anything. Report as a
            // DELETE_FILE failure so the UI shows an actionable message.
            return@withContext Result.success(
                ArrRedownloadResult(
                    steps = listOf(
                        ArrRedownloadStepResult(
                            ArrRedownloadStep.DELETE_FILE,
                            ArrRedownloadStepStatus.FAILED,
                            "No ${if (kind == ArrServiceKind.RADARR) "Radarr" else "Sonarr"} server configured.",
                        ),
                    ),
                    isComplete = false,
                ),
            )
        }

        // Fan out across servers. The first server whose DELETE_FILE step
        // succeeds (or is skipped because there's no file) wins; its full step
        // list becomes the result. A DELETE_FILE failure on one server falls
        // through to the next; only when ALL fail do we report the failure.
        val perServer = servers.map { srv ->
            async {
                redownloadLadder(
                    clientFor(srv),
                    kind,
                    ArrRedownloadRef(tmdbId, tvdbId, seasonNumber, episodeNumber),
                )
            }
        }.awaitAll()

        // Pick the first result that got past the DELETE_FILE gate (i.e. its
        // DELETE step is not FAILED). If none did, return the first failure.
        val winner = perServer.firstOrNull { result ->
            result.steps.firstOrNull { it.step == ArrRedownloadStep.DELETE_FILE }
                ?.status != ArrRedownloadStepStatus.FAILED
        } ?: perServer.first()
        Result.success(winner)
    }

    // ── Sonarr series management ("Manage Series" screen) ────────────────

    override suspend fun resolveSonarrSeries(tvdbId: Int): Result<ArrSeriesResolution> =
        withContext(cacheScope.coroutineContext) {
            resolveSonarrSeriesForSeries(tvdbId)?.let {
                Result.success(
                    ArrSeriesResolution(
                        serverId = it.serverId,
                        seriesId = it.seriesId,
                        title = it.title,
                        monitored = it.monitored,
                        path = it.path,
                    ),
                )
            } ?: Result.failure(noServerException())
        }

    override suspend fun getSonarrEpisodes(tvdbId: Int): Result<List<ArrSeriesEpisode>> =
        withContext(cacheScope.coroutineContext) {
            val target = resolveSonarrSeriesForSeries(tvdbId)
                ?: return@withContext Result.failure(noServerException())
            sonarrApiClient.getEpisodesForSeries(target.baseUrl, target.apiKey, target.seriesId)
        }

    override suspend fun monitorSonarrEpisodes(
        tvdbId: Int,
        episodeIds: List<Int>,
        monitored: Boolean,
    ): Result<Unit> = withContext(cacheScope.coroutineContext) {
        val target = resolveSonarrSeriesForSeries(tvdbId)
            ?: return@withContext noServer()
        if (episodeIds.isEmpty()) return@withContext Result.success(Unit)
        sonarrApiClient.monitorEpisodes(target.baseUrl, target.apiKey, episodeIds, monitored)
    }

    override suspend fun deleteSonarrEpisodeFile(tvdbId: Int, episodeFileId: Int): Result<Unit> =
        withContext(cacheScope.coroutineContext) {
            val target = resolveSonarrSeriesForSeries(tvdbId)
                ?: return@withContext noServer()
            sonarrApiClient.deleteEpisodeFile(target.baseUrl, target.apiKey, episodeFileId)
        }

    override suspend fun searchSonarrEpisodes(tvdbId: Int, episodeIds: List<Int>): Result<Unit> =
        withContext(cacheScope.coroutineContext) {
            val target = resolveSonarrSeriesForSeries(tvdbId)
                ?: return@withContext noServer()
            if (episodeIds.isEmpty()) return@withContext Result.success(Unit)
            sonarrApiClient.postCommand(
                target.baseUrl, target.apiKey,
                ArrCommandName.SEARCH_EPISODES, episodeIds = episodeIds,
            ).map { }
        }

    override suspend fun searchMonitoredSonarrSeason(tvdbId: Int, seasonNumber: Int): Result<Unit> =
        withContext(cacheScope.coroutineContext) {
            val target = resolveSonarrSeriesForSeries(tvdbId)
                ?: return@withContext noServer()
            sonarrApiClient.postCommand(
                target.baseUrl, target.apiKey,
                ArrCommandName.SEASON_SEARCH,
                seriesId = target.seriesId,
                seasonNumber = seasonNumber,
            ).map { }
        }

    override suspend fun refreshSonarrSeries(tvdbId: Int): Result<Unit> =
        withContext(cacheScope.coroutineContext) {
            val target = resolveSonarrSeriesForSeries(tvdbId)
                ?: return@withContext noServer()
            sonarrApiClient.postCommand(
                target.baseUrl, target.apiKey,
                ArrCommandName.REFRESH_SERIES, seriesId = target.seriesId,
            ).map { }
        }

    override suspend fun rescanSonarrSeries(tvdbId: Int): Result<Unit> =
        withContext(cacheScope.coroutineContext) {
            val target = resolveSonarrSeriesForSeries(tvdbId)
                ?: return@withContext noServer()
            sonarrApiClient.postCommand(
                target.baseUrl, target.apiKey,
                ArrCommandName.RESCAN_SERIES, seriesId = target.seriesId,
            ).map { }
        }

    override suspend fun searchSonarrSeries(tvdbId: Int): Result<Unit> =
        withContext(cacheScope.coroutineContext) {
            val target = resolveSonarrSeriesForSeries(tvdbId)
                ?: return@withContext noServer()
            sonarrApiClient.postCommand(
                target.baseUrl, target.apiKey,
                ArrCommandName.SEARCH_SERIES, seriesId = target.seriesId,
            ).map { }
        }

    /**
     * Resolves the owning Sonarr server + internal series id for [tvdbId] by
     * probing each configured Sonarr server. Returns the first server that
     * tracks the series (its [ResolvedSonarrSeries]), or null when no server
     * tracks it / none are configured / server resolution fails. Reuses the
     * cached [resolveServers] (TTL-bounded).
     */
    private suspend fun resolveSonarrSeriesForSeries(tvdbId: Int): ResolvedSonarrSeries? {
        val summary = resolveServers().getOrDefault(ArrServiceSummary())
        for (srv in summary.sonarrServers) {
            val info = sonarrApiClient.getSeriesInfo(srv.baseUrl, srv.apiKey, tvdbId).getOrNull()
            if (info != null) {
                return ResolvedSonarrSeries(
                    serverId = srv.id,
                    baseUrl = srv.baseUrl,
                    apiKey = srv.apiKey,
                    seriesId = info.id,
                    title = info.title,
                    monitored = info.monitored,
                    path = info.path,
                )
            }
        }
        return null
    }

    /** Private carrier for a resolved Sonarr series + its owning server credentials. */
    private data class ResolvedSonarrSeries(
        val serverId: String,
        val baseUrl: String,
        val apiKey: String,
        val seriesId: Int,
        val title: String,
        val monitored: Boolean,
        val path: String? = null,
    )

    /**
     * The one delete & re-download step-ladder, shared by the Radarr (movie)
     * and Sonarr (episode) flows: lookup → hard DELETE_FILE gate → re-query
     * VERIFY → MONITOR if unmonitored → SEARCH. Everything service-specific —
     * lookup abort reasons, the verify re-query semantics (including Sonarr's
     * inconclusive-re-query WARNING branches) — lives in the
     * [ArrServiceClient] adapters; the ladder owns only the step order and
     * the generic step messages, plus the two flow rules that genuinely
     * differ between the historical twins: the service display name
     * interpolated into those messages, and Sonarr's hard gate on a FAILED
     * verify (Radarr's verify failure is best-effort and the flow continues
     * to completion).
     */
    private suspend fun redownloadLadder(
        client: ArrServiceClient,
        kind: ArrServiceKind,
        ref: ArrRedownloadRef,
    ): ArrRedownloadResult {
        val service = client.serviceName
        val steps = mutableListOf<ArrRedownloadStepResult>()

        // Lookup: resolve the tracked item, or abort at the DELETE_FILE gate
        // with the service-specific reason (unresolvable ids, lookup error,
        // not tracked, episode not found).
        val item = when (val lookup = client.lookup(ref)) {
            is ArrRedownloadLookup.Found -> lookup.item
            is ArrRedownloadLookup.Aborted -> {
                steps += ArrRedownloadStepResult(
                    ArrRedownloadStep.DELETE_FILE,
                    ArrRedownloadStepStatus.FAILED,
                    lookup.message,
                )
                return ArrRedownloadResult(steps, isComplete = false)
            }
        }

        // Step 1: delete the file. No file → skip (already gone, not an error).
        if (item.fileId == 0) {
            steps += ArrRedownloadStepResult(
                ArrRedownloadStep.DELETE_FILE,
                ArrRedownloadStepStatus.SKIPPED,
                "No file to delete.",
            )
        } else {
            val deleteOk = client.deleteFile(item.fileId)
            steps += ArrRedownloadStepResult(
                ArrRedownloadStep.DELETE_FILE,
                if (deleteOk) ArrRedownloadStepStatus.SUCCESS else ArrRedownloadStepStatus.FAILED,
                if (deleteOk) null else "$service rejected the file delete.",
            )
            if (!deleteOk) return ArrRedownloadResult(steps, isComplete = false)
        }

        // Step 2: verify deleted via the service's own re-query (Sonarr
        // answers WARNING when the re-query is inconclusive).
        val verify = client.verifyDeleted(item)
        steps += verify
        // A FAILED verify is a hard gate on Sonarr only (file still present →
        // search would no-op); Radarr continues best-effort.
        if (kind == ArrServiceKind.SONARR && verify.status == ArrRedownloadStepStatus.FAILED) {
            return ArrRedownloadResult(steps, isComplete = false)
        }

        // Step 3: monitor only if not already monitored (idempotent otherwise).
        if (item.monitored) {
            steps += ArrRedownloadStepResult(
                ArrRedownloadStep.MONITOR,
                ArrRedownloadStepStatus.SKIPPED,
                "Already monitored.",
            )
        } else {
            val monOk = client.monitor(item.id)
            steps += ArrRedownloadStepResult(
                ArrRedownloadStep.MONITOR,
                if (monOk) ArrRedownloadStepStatus.SUCCESS else ArrRedownloadStepStatus.FAILED,
                if (monOk) null else "Failed to re-monitor.",
            )
        }

        // Step 4: search.
        val search = client.search(item.id)
        steps += ArrRedownloadStepResult(
            ArrRedownloadStep.SEARCH,
            if (search) ArrRedownloadStepStatus.SUCCESS else ArrRedownloadStepStatus.FAILED,
            if (search) "$service is searching for a new download." else "Search command failed.",
        )
        return ArrRedownloadResult(steps, isComplete = true)
    }

    // ── Routing helpers ────────────────────────────────────────────────────

    /**
     * The dispatch seam's entry point: routes one server to the shared
     * [ArrServiceClient] over its own client, replacing the per-call-site
     * `if (kind == RADARR) radarr... else sonarr...` ladder.
     */
    private fun clientFor(server: ArrServerConfig): ArrServiceClient =
        if (server.kind == ArrServiceKind.RADARR) {
            RadarrServiceClient(radarrApiClient, server)
        } else {
            SonarrServiceClient(sonarrApiClient, server)
        }

    private suspend fun findServer(serverId: String, kind: ArrServiceKind): ArrServerConfig? {
        val summary = resolveServers().getOrDefault(ArrServiceSummary())
        val pool = if (kind == ArrServiceKind.RADARR) summary.radarrServers else summary.sonarrServers
        return pool.firstOrNull { it.id == serverId }
    }

    /**
     * Fetch-then-notify ordering for the single-item management deletes,
     * owned once: resolve the owning server for [serverId]/[kind], run
     * [action] with its client, and on success run [refresh] so the deleted
     * row leaves the hot feed. A missing owner fails with the shared
     * no-server 404.
     */
    private suspend fun withServer(
        serverId: String,
        kind: ArrServiceKind,
        refresh: suspend () -> Unit,
        action: suspend (ArrServiceClient) -> Result<Unit>,
    ): Result<Unit> {
        val server = findServer(serverId, kind) ?: return noServer()
        val result = action(clientFor(server))
        if (result.isSuccess) refresh()
        return result
    }

    private fun noServer(): Result<Unit> = Result.failure(noServerException())

    /**
     * ApiException (non-retryable 404) for routing failures where the owning
     * server is no longer configured. Uses the shared [ApiException] contract
     * so callers that inspect `result.exceptionOrNull() as? ApiException` see
     * the same type the network layer returns, rather than a bare Exception.
     */
    private fun noServerException(): ApiException =
        ApiException.fromHttp(404, "Owning server no longer configured")

    /** Tags a queue row with its source server so actions can route back. */
    private fun ArrQueueItem.tagged(serverId: String, kind: ArrServiceKind): ArrQueueItem =
        copy(serverId = serverId, serverKind = kind)

    /** Tags a blocklist row with its source server. */
    private fun ArrBlocklistItem.tagged(serverId: String, kind: ArrServiceKind): ArrBlocklistItem =
        copy(serverId = serverId, serverKind = kind)

    // ── Seerr discovery helpers ────────────────────────────────────────────

    /**
     * Typed result of one service's discovery pass: the resolved servers plus an
     * optional error. Separated from [Result] so the caller can distinguish
     * "Seerr has no servers configured" (success, empty) from "Seerr rejected
     * the call" (failure), which require different UI messages.
     */
    private data class DiscoveryOutcome(
        val servers: List<ArrServerConfig> = emptyList(),
        val error: ArrDiscoveryError? = null,
    ) {
        companion object {
            fun success(servers: List<ArrServerConfig> = emptyList()) = DiscoveryOutcome(servers)
        }
    }

    /**
     * Reduces two discovery outcomes to the single [ArrDiscoveryError] (if any)
     * the UI should surface. `NoAdminPermission` is the most actionable and is
     * hoisted regardless of which service hit it; otherwise the first concrete
     * error wins. A successful (even empty) outcome never contributes an error.
     */
    private fun primaryDiscoveryError(
        radarr: DiscoveryOutcome,
        sonarr: DiscoveryOutcome,
    ): ArrDiscoveryError? {
        if (radarr.error is ArrDiscoveryError.NoAdminPermission ||
            sonarr.error is ArrDiscoveryError.NoAdminPermission
        ) return ArrDiscoveryError.NoAdminPermission
        return radarr.error ?: sonarr.error
    }

    /**
     * Reads Seerr's `/settings/radarr` — a flat array of every configured Radarr
     * server with the real `apiKey` + `hostname`. This is the only Seerr
     * endpoint that exposes credentials; the `/service/radarr/{id}` endpoint
     * redacts them. The `/settings` endpoints require Admin permission, so an
     * HTTP 401/403 is classified as [ArrDiscoveryError.NoAdminPermission] for a
     * tailored UI hint.
     */
    private suspend fun discoverRadarrServers(): DiscoveryOutcome {
        return seerrRepository.getRadarrSettings().fold(
            onSuccess = { list -> DiscoveryOutcome(list.mapNotNull { it.toArrServerConfig() }) },
            onFailure = { DiscoveryOutcome(error = it.toDiscoveryError()) },
        )
    }

    private suspend fun discoverSonarrServers(): DiscoveryOutcome {
        return seerrRepository.getSonarrSettings().fold(
            onSuccess = { list -> DiscoveryOutcome(list.mapNotNull { it.toArrServerConfig() }) },
            onFailure = { DiscoveryOutcome(error = it.toDiscoveryError()) },
        )
    }

    /**
     * Maps an [ApiException] from a `/settings` call to the user-facing
     * [ArrDiscoveryError]. 401/403 → [ArrDiscoveryError.NoAdminPermission];
     * anything else → [ArrDiscoveryError.Other] carrying the friendly message.
     */
    private fun Throwable.toDiscoveryError(): ArrDiscoveryError {
        val code = (this as? ApiException)?.httpCode
        return if (code == 401 || code == 403) {
            ArrDiscoveryError.NoAdminPermission
        } else {
            ArrDiscoveryError.Other(message ?: "Discovery failed.")
        }
    }

    private fun SeerrRadarrSettings.toArrServerConfig(): ArrServerConfig? {
        val url = arrBaseUrl(externalUrl, useSsl, hostname, port, baseUrl) ?: return null
        if (apiKey.isBlank()) return null
        return ArrServerConfig(
            id = "radarr-$id",
            baseUrl = url,
            apiKey = apiKey,
            name = name.ifBlank { "Radarr $id" },
            kind = ArrServiceKind.RADARR,
            isManual = false,
        )
    }

    private fun SeerrSonarrSettings.toArrServerConfig(): ArrServerConfig? {
        val url = arrBaseUrl(externalUrl, useSsl, hostname, port, baseUrl) ?: return null
        if (apiKey.isBlank()) return null
        return ArrServerConfig(
            id = "sonarr-$id",
            baseUrl = url,
            apiKey = apiKey,
            name = name.ifBlank { "Sonarr $id" },
            kind = ArrServiceKind.SONARR,
            isManual = false,
        )
    }

    private fun dedupByBaseUrl(servers: List<ArrServerConfig>): List<ArrServerConfig> {
        // Preserve encounter order (manual first per call-site construction),
        // keeping the first occurrence of each canonical baseUrl.
        val seen = HashSet<String>()
        return servers.filter { seen.add(canonicalBaseUrl(it.baseUrl)) }
    }

    private fun matchesWindow(item: ArrCalendarItem, from: LocalDate, to: LocalDate): Boolean {
        val dateStr = item.airDateUtc ?: return false
        // take(10) == `yyyy-MM-dd`; kotlinx parses the same ISO shape the
        // ISO_LOCAL_DATE formatter parsed (parse failures land in the same
        // runCatching null path). Comparison via Comparable — kotlinx
        // LocalDate has no isBefore/isAfter; the operators express the same
        // inclusive window the old `!date.isBefore(from) && !date.isAfter(to)`
        // did.
        val date = runCatching { LocalDate.parse(dateStr.take(10)) }.getOrNull() ?: return false
        return from <= date && date <= to
    }

    private fun windowKey(from: LocalDate, to: LocalDate): String = "${from}_$to"

    companion object {
        private const val SERVERS_KEY = "arr_servers"

        /** Lowercases + trims trailing slash for stable de-dup comparison. */
        fun canonicalBaseUrl(url: String): String =
            url.trimEnd('/').lowercase()
    }
}
