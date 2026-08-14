package com.raulshma.jellyplay.core.data.catalogue

import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.model.CacheIdentity
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.TtlCache
import com.raulshma.jellyplay.core.model.toMediaItem
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single owner of the seasons → per-season episodes → playback-ordered
 * snapshot. See [EpisodeCatalogue] for the dependency direction and the
 * "offline-ness is a parameter" contract.
 *
 * ## Concurrency model (ported from `MediaRepositoryImpl`)
 *
 * Every load is single-flight + epoch-guarded, exact replica of the detail
 * pattern:
 *  - a `Mutex` guards an in-flight `Deferred` map keyed `"${source}::$seriesId"`;
 *  - the first caller for a series launches the fetch on its own coroutine
 *    scope and every concurrent caller awaits the same `Deferred`;
 *  - an `AtomicLong epoch` is bumped on every invalidation; a fetch captures
 *    the epoch at start and **skips the cache write** if it changed by
 *    completion — otherwise a slow fetch could re-insert a pre-mutation
 *    snapshot after a concurrent invalidation, pinning stale user-data for the
 *    full TTL;
 *  - if the shared `Deferred`'s originator is cancelled, its awaiters re-fetch
 *    on their own scope (a single cancelled caller can't take down unrelated
 *    awaiters).
 *
 * ## Online vs offline
 *
 * [offline] is a parameter (the player's per-session state), not a monitor.
 * Online loads hit `apiClient.getSeasons` + `apiClient.getAllEpisodes`; offline
 * loads read `OfflineRepository` flows with a fresh `.first()` per call (no
 * network, no caching across the offline boundary — matches today's
 * `OfflinePlaybackFacade`).
 *
 * ## Caching shape
 *
 * One `TtlCache<EpisodeCatalogueSnapshot>` per series, identity-keyed so a user
 * switch is a guaranteed miss. `loadSeasonEpisodes` reads/writes the SAME
 * per-series snapshot as `loadSeriesEpisodes` (it slices the season out of the
 * grouped map if present, else fetches the one season and merges it back) —
 * this is the exact "per-season fetch merges into the shared grouped map"
 * semantics `MediaRepositoryImpl.getAllEpisodesGrouped` / `getEpisodes` had, so
 * the transplanted repo tests keep passing.
 */
@Singleton
class EpisodeCatalogueImpl @Inject constructor(
    private val apiClient: JellyfinApiClient,
    private val offlineRepository: OfflineRepository,
) : EpisodeCatalogue {

    private val cache = TtlCache<EpisodeCatalogueSnapshot>(
        maxSize = CACHE_MAX_ENTRIES,
        ttlMs = CACHE_TTL_MS,
    )

    /**
     * Long-lived scope for the identity observer only. The loads themselves run
     * on the caller's scope (via `coroutineScope { async { … } }`) so they
     * inherit the caller's dispatcher and cancellation; this scope never hosts
     * a load.
     */
    private val cacheScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * The last stable `(serverId, userId)` mirror. `currentServer`/`currentUser`
     * are not `StateFlow`s at the [JellyfinApiClient] boundary, so there's no
     * `.value` to read; the mirror is the synchronous source for
     * [currentIdentity]. `null` before login / after logout.
     */
    private val lastStableIdentity = AtomicReference<Pair<String, String>?>(null)

    init {
        // Self-invalidate on identity change so a user/server switch can't serve
        // the previous identity's catalogue for up to the TTL. Same rules as
        // MediaRepositoryImpl's observer: empty→stable and stable→stable-same
        // don't invalidate; everything else does.
        cacheScope.launch {
            combine(apiClient.currentServer, apiClient.currentUser) { server, user ->
                if (server != null && user != null) server.id to user.id else null
            }.collect { identity ->
                val previous = lastStableIdentity.getAndSet(identity)
                val shouldInvalidate = when {
                    previous == null && identity == null -> false
                    previous == null && identity != null -> false
                    previous != null && identity == null -> true
                    previous != identity -> true
                    else -> false
                }
                if (shouldInvalidate) invalidateAll()
            }
        }
    }

    private fun currentIdentity(): CacheIdentity {
        val (serverId, userId) = lastStableIdentity.get() ?: return CacheIdentity.UNKNOWN
        return CacheIdentity.ofOrNull(serverId, userId)
    }

    // Single-flight coordination — the in-flight Deferred map + epoch, keyed by
    // "online/offline::seriesId" so an online and an offline load for the same
    // series can't share a result.
    private val inFlightMutex = Mutex()
    private val inFlight = mutableMapOf<String, Deferred<Result<EpisodeCatalogueSnapshot>>>()
    private val epoch = AtomicLong(0L)

    // Bounds the per-season fan-out fallback when the batched call fails.
    private val seasonSemaphore = Semaphore(MAX_PARALLEL_SEASON_FETCHES)

    override suspend fun loadSeriesEpisodes(
        seriesId: String,
        offline: Boolean,
    ): Result<EpisodeCatalogueSnapshot> {
        val identity = currentIdentity()
        val cacheKey = cacheKey(seriesId)
        cache.get(identity, cacheKey)?.let { return Result.success(it) }
        return coroutineScope {
            val deferred = inFlightMutex.withLock {
                // Re-check under the lock: a concurrent completion may have
                // populated the cache between the unlocked read above and here.
                cache.get(identity, cacheKey)?.let { return@coroutineScope Result.success(it) }
                val epochAtStart = epoch.get()
                val flightKey = flightKey(offline, seriesId)
                inFlight.getOrPut(flightKey) {
                    async {
                        try {
                            loadAndCache(identity, cacheKey, seriesId, offline, epochAtStart)
                        } finally {
                            inFlightMutex.withLock { inFlight.remove(flightKey) }
                        }
                    }
                }
            }
            awaitFlight(deferred, offline, seriesId, cacheKey)
        }
    }

    /**
     * Online/offline load + epoch-guarded cache write. The single shape both
     * the in-flight originator and the cancellation-retry path run.
     */
    private suspend fun loadAndCache(
        identity: CacheIdentity,
        cacheKey: String,
        seriesId: String,
        offline: Boolean,
        epochAtStart: Long,
    ): Result<EpisodeCatalogueSnapshot> {
        val result = if (offline) {
            loadOffline(seriesId, epochAtStart)
        } else {
            loadOnline(seriesId, epochAtStart)
        }
        result.onSuccess { snapshot ->
            writeCacheIfNotStale(identity, cacheKey, snapshot, epochAtStart)
        }
        return result
    }

    /**
     * Awaits the shared in-flight `Deferred`. If its originator was cancelled
     * (navigated away mid-fetch), the `Deferred` is cancelled and every
     * concurrent awaiter would fail too — so re-fetch on THIS caller's scope
     * when the interruption came from the originator rather than this caller.
     * Verbatim port of `MediaRepositoryImpl.getMediaDetail`'s cancellation path.
     */
    private suspend fun awaitFlight(
        deferred: Deferred<Result<EpisodeCatalogueSnapshot>>,
        offline: Boolean,
        seriesId: String,
        cacheKey: String,
    ): Result<EpisodeCatalogueSnapshot> {
        val identity = currentIdentity()
        return try {
            deferred.await()
        } catch (ce: kotlinx.coroutines.CancellationException) {
            val job = coroutineContext[Job]
            if (job?.isCancelled == true) throw ce
            // Originator was cancelled — re-fetch on this still-alive caller.
            loadAndCache(identity, cacheKey, seriesId, offline, epoch.get())
        }
    }

    override suspend fun loadSeasonEpisodes(
        seriesId: String,
        seasonId: String,
        offline: Boolean,
    ): Result<List<MediaItem>> {
        val identity = currentIdentity()
        val cacheKey = cacheKey(seriesId)

        // Serve from the grouped snapshot if this season is present.
        cache.get(identity, cacheKey)?.let { snapshot ->
            snapshot.episodesBySeason[seasonId]?.let { return Result.success(it) }
        }

        // Online absent-season path: fetch the one season and merge it into the
        // shared snapshot (the exact "per-season fetch merges into the grouped
        // cache" semantics from MediaRepositoryImpl.getEpisodes). Offline has no
        // shared snapshot to merge into — it reads the store directly.
        return if (offline) {
            runCatching {
                offlineRepository.getEpisodesForSeason(seasonId).first().map { it.toMediaItem() }
            }
        } else {
            val epochAtStart = epoch.get()
            apiClient.getEpisodes(seriesId, seasonId).mapCatching { episodes ->
                mergeSeasonIntoSnapshot(identity, cacheKey, seriesId, seasonId, episodes, epochAtStart)
                episodes
            }
        }
    }

    /**
     * Merges a freshly-fetched season into the shared series snapshot under the
     * single-flight lock so two near-simultaneous per-season completions can't
     * read the same `current` map and clobber each other (the bug
     * `MediaRepositoryImpl.getEpisodes` merge-under-mutex fixed).
     */
    private suspend fun mergeSeasonIntoSnapshot(
        identity: CacheIdentity,
        cacheKey: String,
        seriesId: String,
        seasonId: String,
        episodes: List<MediaItem>,
        epochAtStart: Long,
    ) {
        if (epoch.get() != epochAtStart) return
        inFlightMutex.withLock {
            if (epoch.get() != epochAtStart) return@withLock
            val current = cache.get(identity, cacheKey)
            val updated = if (current == null) {
                buildSnapshot(seriesId, seasons = emptyList(), mapOf(seasonId to episodes), epochAtStart)
            } else {
                // withSeasonEpisodes owns the fetchedSeasonIds quirk (a ""
                // season key stays absent) and the canonical re-sort.
                current.withSeasonEpisodes(seasonId, episodes, markFetched = true)
            }
            cache.put(identity, cacheKey, updated)
        }
    }

    override suspend fun updateSeasonEpisodes(
        seriesId: String,
        seasonId: String,
        transform: (List<MediaItem>) -> List<MediaItem>,
    ): EpisodeCatalogueSnapshot? {
        val identity = currentIdentity()
        val cacheKey = cacheKey(seriesId)
        var rewritten: EpisodeCatalogueSnapshot? = null
        inFlightMutex.withLock {
            val current = cache.get(identity, cacheKey) ?: return@withLock
            val episodes = current.episodesBySeason[seasonId] ?: return@withLock
            val updated = transform(episodes)
            // Optimistic rewrite: the season was already fetched, so don't
            // touch fetchedSeasonIds — just re-sort the rewritten episodes.
            val snapshot = current.withSeasonEpisodes(seasonId, updated, markFetched = false)
            rewritten = snapshot
            cache.put(identity, cacheKey, snapshot)
        }
        return rewritten
    }

    override fun invalidateSeries(seriesId: String) {
        epoch.incrementAndGet()
        cache.remove(currentIdentity(), cacheKey(seriesId))
    }

    override fun invalidateAll() {
        epoch.incrementAndGet()
        cache.clear()
    }

    private fun writeCacheIfNotStale(
        identity: CacheIdentity,
        cacheKey: String,
        snapshot: EpisodeCatalogueSnapshot,
        epochAtStart: Long,
    ) {
        if (epoch.get() != epochAtStart) return
        cache.put(identity, cacheKey, snapshot)
    }

    // ── online assemble ─────────────────────────────────────────────────

    private suspend fun loadOnline(
        seriesId: String,
        epochAtStart: Long,
    ): Result<EpisodeCatalogueSnapshot> {
        val seasonsResult = apiClient.getSeasons(seriesId)
        val seasons = seasonsResult.getOrElse {
            return Result.failure(it)
        }
        // Single round-trip for the full episode set, grouped by season id —
        // exact groupBy semantics of MediaRepositoryImpl.getAllEpisodesGrouped.
        val groupedResult = apiClient.getAllEpisodes(seriesId).map { all ->
            all.groupBy { it.seasonId ?: "" }
        }
        val grouped = groupedResult.getOrElse {
            // Fall back to per-season fan-out (older server that rejected the
            // unfiltered query). Caps concurrency at MAX_PARALLEL_SEASON_FETCHES.
            return Result.success(
                fanOutSeasons(seriesId, seasons, epochAtStart),
            )
        }
        return Result.success(buildSnapshot(seriesId, seasons, grouped, epochAtStart))
    }

    /**
     * Per-season fallback: N concurrent requests capped by the semaphore, each
     * merged into one map. Failures leave the season absent (the caller can
     * still refetch on demand) rather than aborting the whole snapshot —
     * matches DetailViewModel.loadAllSeasonsBatched's per-season resilience.
     */
    private suspend fun fanOutSeasons(
        seriesId: String,
        seasons: List<MediaItem>,
        epochAtStart: Long,
    ): EpisodeCatalogueSnapshot {
        val grouped = java.util.concurrent.ConcurrentHashMap<String, List<MediaItem>>()
        coroutineScope {
            seasons.forEach { season ->
                launch {
                    seasonSemaphore.withPermit {
                        val episodesResult = apiClient.getEpisodes(seriesId, season.id)
                        if (epoch.get() == epochAtStart) {
                            episodesResult.onSuccess { episodes ->
                                grouped[season.id] = episodes
                            }
                        }
                    }
                }
            }
        }
        return buildSnapshot(seriesId, seasons, grouped, epochAtStart)
    }

    // ── offline assemble ────────────────────────────────────────────────

    private suspend fun loadOffline(
        seriesId: String,
        epochAtStart: Long,
    ): Result<EpisodeCatalogueSnapshot> = runCatching {
        val seasons = offlineRepository.getSeasonsForSeries(seriesId).first().map { it.toMediaItem() }
        // Fresh per-season Room reads (matches OfflinePlaybackFacade). Every
        // season is resolved here by its real id (no ""-key orphan edge is
        // possible offline), so a season with zero downloaded episodes is
        // "fetched but empty" — recorded as an empty list so the per-season UI
        // shows its empty state and the series play button stops waiting on a
        // season that will never load. Dropping emptied seasons made them
        // indistinguishable from a not-yet-fetched season → infinite spinner +
        // "Finding Episode" after the last episode of a season was removed.
        val grouped = mutableMapOf<String, List<MediaItem>>()
        seasons.forEach { season ->
            val episodes = offlineRepository.getEpisodesForSeason(season.id).first().map { it.toMediaItem() }
            grouped[season.id] = episodes
        }
        buildSnapshot(seriesId, seasons, grouped, epochAtStart)
    }

    // ── snapshot construction ───────────────────────────────────────────

    /**
     * Builds a snapshot from the canonical inputs. `fetchedSeasonIds` is
     * derived as exactly the season ids present in [grouped] that also appear in
     * [seasons] — a season whose episodes grouped under a different key (the
     * `""`-key edge) stays absent so a per-season refetch can still fire.
     */
    private fun buildSnapshot(
        seriesId: String,
        seasons: List<MediaItem>,
        grouped: Map<String, List<MediaItem>>,
        epochAtStart: Long,
    ): EpisodeCatalogueSnapshot {
        val seasonIds = seasons.mapTo(mutableSetOf()) { it.id }
        val fetchedSeasonIds = grouped.keys.filter { it in seasonIds }.toSet()
        val sorted = grouped.values.flatten().sortedByPlaybackOrder()
        return EpisodeCatalogueSnapshot(
            seriesId = seriesId,
            seasons = seasons,
            episodesBySeason = grouped,
            fetchedSeasonIds = fetchedSeasonIds,
            sortedEpisodes = sorted,
            epoch = epochAtStart,
        )
    }

    private fun cacheKey(seriesId: String): String = "catalogue_$seriesId"
    private fun flightKey(offline: Boolean, seriesId: String): String =
        "${if (offline) "offline" else "online"}::$seriesId"

    companion object {
        private const val CACHE_MAX_ENTRIES = 30
        /** Matches `MediaRepositoryImpl.DETAIL_CACHE_TTL_MS` (2 minutes). */
        private const val CACHE_TTL_MS = 2 * 60 * 1000L
        /** Matches `DetailViewModel.MAX_PARALLEL_SEASON_FETCHES`. */
        private const val MAX_PARALLEL_SEASON_FETCHES = 5
    }
}
