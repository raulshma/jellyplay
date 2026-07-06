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
import com.raulshma.jellyplay.core.model.arr.ArrServerConfig
import com.raulshma.jellyplay.core.model.arr.ArrServiceKind
import com.raulshma.jellyplay.core.model.arr.ArrServiceSummary
import com.raulshma.jellyplay.core.model.seerr.SeerrRadarrSettings
import com.raulshma.jellyplay.core.model.seerr.SeerrSonarrSettings
import com.raulshma.jellyplay.core.network.api.ApiException
import com.raulshma.jellyplay.core.network.arr.RadarrApiClient
import com.raulshma.jellyplay.core.network.arr.SonarrApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

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
 * Concurrency note: the cache scope is a dedicated [SupervisorJob] + IO
 * dispatcher so a failure in one fan-out branch cannot cancel siblings.
 */
@Singleton
class ArrRepositoryImpl @Inject constructor(
    private val radarrApiClient: RadarrApiClient,
    private val sonarrApiClient: SonarrApiClient,
    private val seerrRepository: SeerrRepository,
    private val arrPreferencesStore: ArrPreferencesStore,
) : ArrRepository {

    private val cacheScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
    private val isoDate: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

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

        val prefs = arrPreferencesStore.preferences.first()
        val manualRadarr = prefs.manualServers.filter { it.kind == ArrServiceKind.RADARR }
        val manualSonarr = prefs.manualServers.filter { it.kind == ArrServiceKind.SONARR }

        // Discovery outcomes carry an error type so a 401/403 (non-admin Seerr
        // account) surfaces as a distinct UI message instead of an empty list.
        val radarrOutcome = if (prefs.useSeerrDiscovery) discoverRadarrServers() else DiscoveryOutcome.success()
        val sonarrOutcome = if (prefs.useSeerrDiscovery) discoverSonarrServers() else DiscoveryOutcome.success()

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
        val combined = coroutineScope {
            val radarrJobs = summary.radarrServers.map { srv ->
                async {
                    resolveSemaphore.withPermit {
                        radarrApiClient.getQueue(srv.baseUrl, srv.apiKey)
                            .getOrElse { emptyList() }
                            .map { it.tagged(srv.id, ArrServiceKind.RADARR) }
                    }
                }
            }
            val sonarrJobs = summary.sonarrServers.map { srv ->
                async {
                    resolveSemaphore.withPermit {
                        sonarrApiClient.getQueue(srv.baseUrl, srv.apiKey)
                            .getOrElse { emptyList() }
                            .map { it.tagged(srv.id, ArrServiceKind.SONARR) }
                    }
                }
            }
            (radarrJobs + sonarrJobs).awaitAll().flatten()
        }
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
            val startStr = from.format(isoDate)
            val endStr = to.format(isoDate)
            val combined = coroutineScope {
                val radarrJobs = summary.radarrServers.map { srv ->
                    async {
                        resolveSemaphore.withPermit {
                            radarrApiClient.getCalendar(srv.baseUrl, srv.apiKey, startStr, endStr)
                                .getOrElse { emptyList() }
                        }
                    }
                }
                val sonarrJobs = summary.sonarrServers.map { srv ->
                    async {
                        resolveSemaphore.withPermit {
                            sonarrApiClient.getCalendar(srv.baseUrl, srv.apiKey, startStr, endStr)
                                .getOrElse { emptyList() }
                        }
                    }
                }
                (radarrJobs + sonarrJobs).awaitAll().flatten()
            }
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
        val combined = coroutineScope {
            val radarrJobs = summary.radarrServers.map { srv ->
                async {
                    resolveSemaphore.withPermit {
                        radarrApiClient.getBlocklist(srv.baseUrl, srv.apiKey)
                            .getOrElse { emptyList() }
                            .map { it.tagged(srv.id, ArrServiceKind.RADARR) }
                    }
                }
            }
            val sonarrJobs = summary.sonarrServers.map { srv ->
                async {
                    resolveSemaphore.withPermit {
                        sonarrApiClient.getBlocklist(srv.baseUrl, srv.apiKey)
                            .getOrElse { emptyList() }
                            .map { it.tagged(srv.id, ArrServiceKind.SONARR) }
                    }
                }
            }
            (radarrJobs + sonarrJobs).awaitAll().flatten()
        }
        _blocklist.value = combined
        Result.success(Unit)
    }

    override suspend fun testServer(server: ArrServerConfig): Result<Unit> =
        withContext(cacheScope.coroutineContext) {
            if (server.kind == ArrServiceKind.RADARR) {
                radarrApiClient.testConnection(server.baseUrl, server.apiKey)
            } else {
                sonarrApiClient.testConnection(server.baseUrl, server.apiKey)
            }
        }

    // ── Management actions ─────────────────────────────────────────────────

    override suspend fun deleteQueueItem(item: ArrQueueItem, options: ArrQueueDeleteOptions): Result<Unit> =
        withContext(cacheScope.coroutineContext) {
            val server = findServer(item.serverId, item.serverKind) ?: return@withContext noServer()
            val result = if (item.serverKind == ArrServiceKind.RADARR) {
                radarrApiClient.deleteQueueItem(server.baseUrl, server.apiKey, item.queueId, options)
            } else {
                sonarrApiClient.deleteQueueItem(server.baseUrl, server.apiKey, item.queueId, options)
            }
            if (result.isSuccess) refreshQueue()
            result
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
                        if (kind == ArrServiceKind.RADARR) {
                            radarrApiClient.deleteQueueItems(server.baseUrl, server.apiKey, ids, options)
                        } else {
                            sonarrApiClient.deleteQueueItems(server.baseUrl, server.apiKey, ids, options)
                        }
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
            if (item.serverKind == ArrServiceKind.RADARR) {
                radarrApiClient.grabQueueItem(server.baseUrl, server.apiKey, item.queueId)
            } else {
                sonarrApiClient.grabQueueItem(server.baseUrl, server.apiKey, item.queueId)
            }
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
            if (item.serverKind == ArrServiceKind.RADARR) {
                radarrApiClient.importQueueItem(server.baseUrl, server.apiKey, downloadId)
            } else {
                sonarrApiClient.importQueueItem(server.baseUrl, server.apiKey, downloadId)
            }
        }

    override suspend fun deleteBlocklistItem(item: ArrBlocklistItem): Result<Unit> =
        withContext(cacheScope.coroutineContext) {
            val server = findServer(item.serverId, item.serverKind) ?: return@withContext noServer()
            val result = if (item.serverKind == ArrServiceKind.RADARR) {
                radarrApiClient.deleteBlocklistItem(server.baseUrl, server.apiKey, item.id)
            } else {
                sonarrApiClient.deleteBlocklistItem(server.baseUrl, server.apiKey, item.id)
            }
            if (result.isSuccess) refreshBlocklist()
            result
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
                        if (kind == ArrServiceKind.RADARR) {
                            // Radarr's SearchMovie keys off the internal movie id, not the tmdbId.
                            // Resolve tmdbId → Radarr movie id first; if the movie isn't tracked
                            // (lookup returns null), fall back to a global MissingMoviesSearch
                            // rather than silently no-op'ing.
                            val movieId = radarrApiClient.findMovieIdByTmdb(srv.baseUrl, srv.apiKey, tmdbId)
                                .getOrNull()
                            if (movieId != null) {
                                radarrApiClient.postCommand(
                                    srv.baseUrl, srv.apiKey, command, movieIds = listOf(movieId),
                                )
                            } else {
                                radarrApiClient.postCommand(
                                    srv.baseUrl, srv.apiKey, ArrCommandName.MISSING_SEARCH,
                                )
                            }
                        } else {
                            // Sonarr identifies by internal seriesId, not tmdbId; passing tmdbId
                            // as seriesId is wrong. Fall back to a global MissingEpisodesSearch
                            // which the user can trigger; a tmdb→seriesId lookup would need the
                            // /series lookup endpoint (future enhancement).
                            sonarrApiClient.postCommand(srv.baseUrl, srv.apiKey, ArrCommandName.MISSING_EPISODES)
                        }.getOrNull()
                    }
                }.awaitAll().filterNotNull()
            }.let { Result.success(it) }
        }

    // ── Routing helpers ────────────────────────────────────────────────────

    private suspend fun findServer(serverId: String, kind: ArrServiceKind): ArrServerConfig? {
        val summary = resolveServers().getOrDefault(ArrServiceSummary())
        val pool = if (kind == ArrServiceKind.RADARR) summary.radarrServers else summary.sonarrServers
        return pool.firstOrNull { it.id == serverId }
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
        val url = buildBaseUrl(externalUrl, useSsl, hostname, port, baseUrl) ?: return null
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
        val url = buildBaseUrl(externalUrl, useSsl, hostname, port, baseUrl) ?: return null
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
        val date = runCatching { LocalDate.parse(dateStr.take(10), isoDate) }.getOrNull() ?: return false
        return !date.isBefore(from) && !date.isAfter(to)
    }

    private fun windowKey(from: LocalDate, to: LocalDate): String = "${from}_$to"

    companion object {
        private const val SERVERS_KEY = "arr_servers"

        /**
         * Builds a base URL from Radarr/Sonarr server fields, mirroring the
         * `getFullUrl()` helpers on `SeerrRadarrSettings` /
         * `SeerrSonarrSettings` (which are absent on the richer
         * `*ServiceDetail` types). Returns null when [hostname] is blank.
         */
        internal fun buildBaseUrl(
            externalUrl: String?,
            useSsl: Boolean,
            hostname: String,
            port: Int,
            baseUrl: String?,
        ): String? {
            if (!externalUrl.isNullOrBlank()) return externalUrl.trimEnd('/')
            if (hostname.isBlank()) return null
            val protocol = if (useSsl) "https" else "http"
            val base = baseUrl?.trim('/')?.let { if (it.isNotEmpty()) "/$it" else "" } ?: ""
            return "$protocol://$hostname:$port$base"
        }

        /** Lowercases + trims trailing slash for stable de-dup comparison. */
        internal fun canonicalBaseUrl(url: String): String =
            url.trimEnd('/').lowercase()
    }
}
