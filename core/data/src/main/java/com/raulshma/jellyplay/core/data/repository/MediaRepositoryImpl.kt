package com.raulshma.jellyplay.core.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import android.util.Log
import com.raulshma.jellyplay.core.database.dao.HomeSectionCacheDao
import com.raulshma.jellyplay.core.database.dao.LyricsCacheDao
import com.raulshma.jellyplay.core.database.entity.HomeSectionCacheEntity
import com.raulshma.jellyplay.core.database.entity.LyricsCacheEntity
import com.raulshma.jellyplay.core.data.paging.FavoritesPagingSource
import com.raulshma.jellyplay.core.data.paging.MediaPagingSource
import com.raulshma.jellyplay.core.data.paging.SearchPagingSource
import com.raulshma.jellyplay.core.model.CacheIdentity
import com.raulshma.jellyplay.core.model.CollectionSummary
import com.raulshma.jellyplay.core.model.DvrSeriesTimer
import com.raulshma.jellyplay.core.model.DvrTimer
import com.raulshma.jellyplay.core.model.EpgGuide
import com.raulshma.jellyplay.core.model.Genre
import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.TtlCache
import com.raulshma.jellyplay.core.model.HomeSectionsResult
import com.raulshma.jellyplay.core.model.LibraryFilters
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.LiveTvChannel
import com.raulshma.jellyplay.core.model.LiveTvProgram
import com.raulshma.jellyplay.core.model.LiveTvRecording
import com.raulshma.jellyplay.core.model.GuideInfo
import com.raulshma.jellyplay.core.model.ProgramFilters
import com.raulshma.jellyplay.core.model.LrcLibTrack
import com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogue
import com.raulshma.jellyplay.core.data.network.NetworkMonitor
import com.raulshma.jellyplay.core.model.LyricsLine
import com.raulshma.jellyplay.core.model.LyricsResult
import com.raulshma.jellyplay.core.model.LyricsSource
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.Playlist
import com.raulshma.jellyplay.core.model.PlaylistItem
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.NetworkStatus
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.SearchResult
import com.raulshma.jellyplay.core.model.Studio
import com.raulshma.jellyplay.core.model.SyncPlayGroup
import com.raulshma.jellyplay.core.model.SyncPlayGroupInfo
import com.raulshma.jellyplay.core.model.SyncPlayRepeatMode
import com.raulshma.jellyplay.core.model.SyncPlayShuffleMode
import com.raulshma.jellyplay.core.model.NewsletterData
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import com.raulshma.jellyplay.core.network.LrcLibApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaRepositoryImpl @Inject constructor(
    private val apiClient: JellyfinApiClient,
    private val lrcLibApi: LrcLibApi,
    private val lyricsCacheDao: LyricsCacheDao,
    private val homeSectionCacheDao: HomeSectionCacheDao,
    private val networkMonitor: NetworkMonitor,
    private val playedStateSync: PlayedStateSync,
    /**
     * The deep "Episode Catalogue": the single owner of the series
     * seasons/episodes snapshot. Injected (not constructed) so the repo's
     * `getSeasons`/`getEpisodes`/`getAllEpisodesGrouped` can delegate to it.
     * The catalogue depends on `JellyfinApiClient` + `OfflineRepository` only
     * (never on `MediaRepository`), so this edge does NOT form a DI cycle —
     * both live in `core:data` and Hilt resolves the direction.
     */
    private val episodeCatalogue: EpisodeCatalogue,
) : MediaRepository, MediaRepositoryCacheInvalidation {

    private val detailCache = TtlCache<MediaDetail>(
        maxSize = DETAIL_CACHE_MAX_ENTRIES,
        ttlMs = DETAIL_CACHE_TTL_MS,
    )

    private val libraryFoldersCache = TtlCache<List<LibraryFolder>>(ttlMs = FOLDERS_CACHE_TTL_MS)
    private val genresCache = TtlCache<List<Genre>>(maxSize = 64, ttlMs = FOLDERS_CACHE_TTL_MS)
    private val studiosCache = TtlCache<List<Studio>>(maxSize = 64, ttlMs = FOLDERS_CACHE_TTL_MS)
    private val latestMediaCache = TtlCache<List<MediaItem>>(maxSize = 64, ttlMs = LATEST_CACHE_TTL_MS)

    // Series-scoped seasons/episodes caches used to live here; they've moved
    // into [episodeCatalogue], the single owner of the series snapshot. The
    // remaining series-adjacent caches (similar, album tracks, collection
    // items) stay — they're not part of the catalogue's "seasons → episodes →
    // sorted" shape.
    private val similarCache = TtlCache<List<MediaItem>>(ttlMs = DETAIL_CACHE_TTL_MS)
    private val albumTracksCache = TtlCache<List<MediaItem>>(ttlMs = DETAIL_CACHE_TTL_MS)
    private val collectionItemsCache = TtlCache<SearchResult>(ttlMs = DETAIL_CACHE_TTL_MS)

    // Plan 08: private — the detail cache is repo-internal machinery; reads
    // that need freshness use getMediaDetail(force = true) and mutations/
    // invalidations run through the composite + per-type dispatch below.
    private fun invalidateDetailCache(itemId: String? = null) {
        detailCacheEpoch.incrementAndGet()
        if (itemId != null) {
            val identity = currentIdentity()
            detailCache.remove(identity, itemId)
            // similarCache keys are `similar_${itemId}_$limit` (the limit is
            // part of the key so a different limit never serves a truncated
            // list), so evict by prefix to drop every limit variant.
            similarCache.removeByKeyPrefix(identity, "similar_$itemId")
        } else {
            detailCache.clear()
            similarCache.clear()
        }
    }

    // Plan 08: funnels to the catalogue so the composite user-data eviction
    // (and the internal per-type dispatch) can drop a series' seasons/episodes
    // snapshot + epoch together. Private — seasons/episodes caches now live in
    // [episodeCatalogue] and no external caller needs this knob.
    private fun invalidateSeriesCache(seriesId: String) {
        episodeCatalogue.invalidateSeries(seriesId)
    }

    // Plan 08: private funnel — collection edits self-invalidate, so no
    // external caller needs this knob anymore.
    private fun invalidateCollectionItemsCache(collectionId: String) {
        // Keys are `collection_${id}_${startIndex}_${limit}`, so evict by prefix.
        collectionItemsCache.removeByKeyPrefix(currentIdentity(), "collection_$collectionId")
    }

    /**
     * Single owner of the "what did this detail's type affect" mapping (plan
     * 08). Absorbs the provider's old `invalidateByType` table and the
     * series-resolution rule that used to live in the interface KDoc: one
     * encoding, no caller-side re-derivation. Reached through the module-
     * internal [MediaRepositoryCacheInvalidation] seam.
     */
    override fun invalidateFor(detail: MediaDetail) {
        when (detail.item.mediaType) {
            MediaType.SERIES -> {
                episodeCatalogue.invalidateSeries(detail.item.id)
                invalidateDetailCache(detail.item.id)
            }
            MediaType.EPISODE -> detail.item.seriesId?.let { invalidateSeriesCache(it) }
            MediaType.ALBUM -> invalidateUserDataCaches(detail.item.id)
            MediaType.COLLECTION -> invalidateCollectionItemsCache(detail.item.id)
            else -> Unit // plain item: caller-scoped invalidation already ran
        }
    }

    // In-memory home-sections cache. Previously a hand-rolled triple of
    // @Volatile fields + a lock (cachedHomeSections / Timestamp / Key + lock);
    // folded into a single-entry TtlCache so the home path shares the same
    // identity-keyed primitive as every other cache here. Identity-keyed so a
    // user/server switch can't serve the previous identity's payload.
    private val homeSectionsCache = TtlCache<HomeSectionsResult>(
        maxSize = 1,
        ttlMs = HOME_SECTIONS_CACHE_TTL_MS,
    )

    /**
     * The current `(serverId, userId)` as a [CacheIdentity], read synchronously
     * from the [lastStableIdentity] mirror maintained by the `init {}` observer.
     * `Flow<ServerInfo?>` / `Flow<UserInfo?>` are not `StateFlow`s at this
     * interface boundary, so there's no `.value` to read; the mirror is the
     * authoritative synchronous source. Returns [CacheIdentity.UNKNOWN] before
     * login / after logout — nothing cached under that key can leak across
     * users, since no real identity ever collides with it.
     */
    private fun currentIdentity(): CacheIdentity {
        val (serverId, userId) = lastStableIdentity.get() ?: return CacheIdentity.UNKNOWN
        return CacheIdentity.ofOrNull(serverId, userId)
    }

    /**
     * Drops the in-memory home-sections cache for the current identity. Used by
     * [toggleFavorite] / [markPlayed] / [markUnplayed] so a user-data change is
     * reflected on the next home load (the previous hand-rolled slot zeroed the
     * timestamp to force a TTL miss; this is the identity-aware equivalent).
     */
    private fun invalidateHomeSectionsCache() {
        homeSectionsCache.clear()
    }

    // Throttle for lyrics-cache eviction. cacheLyrics() is called on every
    // successful lyrics fetch, and each call used to fire a full
    // DELETE FROM lyrics_cache WHERE fetchedAt < :ts scan over the whole table
    // — so opening lyrics for a new track walked & re-locked the entire table.
    // Eviction is best-effort (wrapped in try/catch) and exact cadence isn't
    // observable, so we cap it at once per hour.
    @Volatile
    private var lastLyricsEvictionMs = 0L

    /**
     * Long-lived scope for the cache-invalidation observer. Never cancelled —
     * [MediaRepositoryImpl] is a `@Singleton` and lives for the process lifetime.
     */
    private val cacheScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Tracks the last observed stable (serverId, userId) pair. `null` means we're in an
     * "empty" state (logged out / restoring session). Non-null means we have a stable
     * identity whose replacement by a different value should trigger cache invalidation.
     * Kept as a pair (not a flattened key) so the observer can clear the *previous*
     * identity's persisted home-section SWR rows by identity — see [init].
     */
    private val lastStableIdentity = AtomicReference<Pair<String, String>?>(null)

    init {
        // Observe active server/user changes and self-invalidate caches. This closes a
        // privacy + correctness gap where the previous user's home sections / detail data was
        // served for up to 10 minutes (the longest TTL) after `switchUser` or
        // `switchServerAddress`. Implemented as a self-collection so we don't introduce a
        // `data → auth` dependency edge; the flows are exposed by `JellyfinApiClient` which
        // `MediaRepositoryImpl` already depends on.
        //
        // Invalidation rules:
        //  - Empty  → Stable : no invalidation (session restore from a fresh process).
        //  - Stable → Empty  : invalidate (user logged out — clear their data for privacy).
        //  - Stable → Stable : invalidate only if the identity actually changed.
        //  - Empty  → Empty  : never invalidates.
        cacheScope.launch {
            combine(apiClient.currentServer, apiClient.currentUser) { server, user ->
                if (server != null && user != null) server.id to user.id else null
            }.collect { identity ->
                val previous = lastStableIdentity.getAndSet(identity)
                val shouldInvalidate = when {
                    previous == null && identity == null -> false
                    previous == null && identity != null -> false // session restore, no prior data
                    previous != null && identity == null -> true  // logout: clear for privacy
                    previous != identity -> true                  // user or server switch
                    else -> false
                }
                if (shouldInvalidate) {
                    invalidateCaches()
                    // Clear the PREVIOUS identity's persisted home-section SWR
                    // rows — scoped, not wholesale, so a multi-account server
                    // keeps the other users' snapshots for their next cold open.
                    // Runs here (where we hold the previous identity) rather than
                    // in invalidateCaches() (which has no identity context).
                    previous?.let { (serverId, userId) ->
                        clearHomeSectionsForIdentity(serverId, userId)
                    }
                }
            }
        }
    }

    /**
     * Clears the persisted home-section SWR snapshot for a single (server, user).
     * Failure is logged, not swallowed: this runs on logout / identity switch and
     * a silent failure would leave the just-logged-out user's home payload in the
     // table, to be served to a different user on the next cold open.
     */
    private suspend fun clearHomeSectionsForIdentity(serverId: String, userId: String) {
        runCatching { homeSectionCacheDao.clearForIdentity(serverId, userId) }
            .onFailure { e ->
                android.util.Log.w(
                    "MediaRepo",
                    "Failed to clear home-section SWR cache for server=$serverId user=$userId",
                    e,
                )
            }
    }

    override suspend fun getHomeSections(
        query: HomeSectionQuery,
        force: Boolean,
    ): Result<HomeSectionsResult> {
        val identity = currentIdentity()
        val cacheKey = query.cacheKey()
        // Freshness lever: drop this query's cached snapshot first — the
        // invalidate-then-read sequence the home screen's manual refresh used
        // to run as a global cache drop (plan 08).
        if (force) homeSectionsCache.remove(identity, cacheKey)
        homeSectionsCache.get(identity, cacheKey)?.let { return Result.success(it) }
        return apiClient.getHomeSections(
            enabledSections = query.enabledSections,
            libraryHomeSectionOverrides = query.libraryHomeSectionOverrides,
            nextUpRewatching = query.nextUpRewatching,
            nextUpMaxDays = query.nextUpMaxDays,
            nextUpExcludedSeriesIds = query.nextUpExcludedSeriesIds,
            hiddenCwItemIds = query.hiddenCwItemIds,
            pinnedSections = query.pinnedSections,
        ).also { result ->
            result.getOrNull()?.let { homeResult ->
                homeSectionsCache.put(identity, cacheKey, homeResult)
                // Persist the snapshot for stale-while-revalidate on cold open.
                // The in-memory cache above is lost on process death; this row lets
                // getCachedHomeSections() render instantly next launch while a
                // network refresh runs. Identity-keyed (serverId, userId) so a
                // user switch never serves another user's payload — cleared by
                // clearHomeSectionsForIdentity() from the identity observer below.
                persistHomeSectionsSnapshot(cacheKey, homeResult)
            }
        }
    }

    override suspend fun getCachedHomeSections(
        query: HomeSectionQuery,
    ): HomeSectionsResult? {
        // Read identity directly from the source flows rather than the volatile
        // mirror fields: this runs from the Home VM's currentUser collector,
        // which can fire before the repo's identity observer has written the
        // mirror. .first() is suspend + non-blocking and guarantees the current
        // value, so the SWR read never misses due to an observe ordering race.
        val server = apiClient.currentServer.first() ?: return null
        val user = apiClient.currentUser.first() ?: return null
        return homeSectionCacheDao.get(server.id, user.id, query.cacheKey())?.payload
    }

    private suspend fun persistHomeSectionsSnapshot(cacheKey: String, result: HomeSectionsResult) {
        val server = apiClient.currentServer.first() ?: return
        val user = apiClient.currentUser.first() ?: return
        runCatching {
            homeSectionCacheDao.upsert(
                HomeSectionCacheEntity(
                    serverId = server.id,
                    userId = user.id,
                    cacheKey = cacheKey,
                    payloadJson = com.raulshma.jellyplay.core.database.Converters.encodeHomeSectionsResult(result),
                    // Wall-clock on purpose: this value must survive a reboot to
                    // serve the next cold open, and monotonic clocks reset on
                    // boot. The in-memory TTL above uses SystemClock.elapsedRealtime
                    // (monotonic) because it only compares two readings within one
                    // process. fetchedAt isn't read for a TTL today.
                    fetchedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    override suspend fun getLibraryFolders(force: Boolean): Result<List<LibraryFolder>> {
        val identity = currentIdentity()
        if (force) libraryFoldersCache.remove(identity, "folders")
        libraryFoldersCache.get(identity, "folders")?.let { return Result.success(it) }
        return apiClient.getLibraryFolders().also { result ->
            result.getOrNull()?.let { libraryFoldersCache.put(identity, "folders", it) }
        }
    }

    override suspend fun getLatestMedia(
        parentId: String,
        limit: Int,
    ): Result<List<MediaItem>> {
        val identity = currentIdentity()
        val cacheKey = "latest_${parentId}_$limit"
        latestMediaCache.get(identity, cacheKey)?.let { return Result.success(it) }
        return apiClient.getLatestMedia(parentId = parentId, limit = limit).also { result ->
            result.getOrNull()?.let { latestMediaCache.put(identity, cacheKey, it) }
        }
    }

    override suspend fun getMediaItems(
        parentId: String?,
        filters: LibraryFilters,
        studioIds: List<String>?,
        startIndex: Int,
        limit: Int,
        kindFilter: com.raulshma.jellyplay.core.model.ItemKindFilter,
    ): Result<SearchResult> = apiClient.getMediaItems(
        parentId = parentId,
        filters = filters,
        studioIds = studioIds,
        startIndex = startIndex,
        limit = limit,
        kindFilter = kindFilter,
    )

    // Single-flight dedup for getMediaDetail: the detail screen is reachable
    // from many entry points (home row tap, deep link, "play next"
    // notification, cast handshake, download resume). Two near-simultaneous
    // entries previously fired two full getItem round-trips (and, for series,
    // two full episode storms) because TtlCache's get-check-put is not atomic.
    // The Mutex guards the in-flight map; the Deferred is awaited by all
    // concurrent callers for the same key so the fetch runs exactly once.
    // The async is launched on the caller's coroutine scope (via coroutineScope)
    // so the fetch inherits the caller's dispatcher — important for tests
    // (runTest's test dispatcher) and for cancellation semantics.
    private val detailInFlightMutex = Mutex()
    private val detailInFlight = mutableMapOf<String, Deferred<Result<MediaDetail>>>()
    // Bumped on every detail-cache invalidation. An in-flight fetch captures
    // the epoch at start and skips re-caching if it changed by completion —
    // otherwise a slow fetch could re-insert a pre-mutation snapshot after a
    // concurrent markPlayed/favorite/played invalidation, pinning stale
    // user-data for the full TTL. AtomicLong so concurrent invalidations never
    // lose an increment (a lost update would weaken the stale-snapshot guard).
    private val detailCacheEpoch = AtomicLong(0L)

    override suspend fun getMediaDetail(itemId: String, force: Boolean): Result<MediaDetail> {
        // Freshness lever: drop the cached entry first — verbatim the
        // invalidate-then-read sequence callers used to run by hand. The
        // detailCacheEpoch bump below also guards a racing fetch from
        // re-inserting the stale snapshot.
        if (force) invalidateDetailCache(itemId)
        val identity = currentIdentity()
        // Fast path: serve from cache without entering coroutineScope, avoiding
        // the scope-creation overhead on the (common) cached read. The authoritative
        // single-flight coordination still happens below under the mutex.
        detailCache.get(identity, itemId)?.let { return Result.success(it) }
        return coroutineScope {
            // Single-flight: if another caller already started this fetch, await
            // its result instead of issuing a duplicate request. The cache read is
            // captured inside the lock so a concurrent fetch that completes while
            // we waited is observed exactly once.
            val cachedOrDeferred: Any = detailInFlightMutex.withLock {
                detailCache.get(identity, itemId)?.let { return@withLock it }
                val epochAtStart = detailCacheEpoch.get()
                detailInFlight.getOrPut(itemId) {
                    // async on the current coroutineScope so the fetch runs on
                    // the caller's dispatcher (not a fixed background scope).
                    async {
                        try {
                            apiClient.getMediaDetail(itemId).also { result ->
                                // Only cache the result if no invalidation landed
                                // while the fetch was in flight; otherwise the
                                // freshly fetched snapshot could be stale relative
                                // to a concurrent user-data mutation.
                                if (detailCacheEpoch.get() == epochAtStart) {
                                    result.getOrNull()?.let { detail -> detailCache.put(identity, itemId, detail) }
                                }
                            }
                        } finally {
                            // Clear the in-flight marker. Guarded so a concurrent
                            // awaiter that already grabbed the Deferred still sees
                            // the completed value, but a later caller re-fetches.
                            detailInFlightMutex.withLock { detailInFlight.remove(itemId) }
                        }
                    }
                }
            }
            @Suppress("UNCHECKED_CAST")
            when (cachedOrDeferred) {
                is MediaDetail -> Result.success(cachedOrDeferred)
                else -> {
                    val deferred = cachedOrDeferred as Deferred<Result<MediaDetail>>
                    try {
                        deferred.await()
                    } catch (ce: kotlinx.coroutines.CancellationException) {
                        // The shared in-flight Deferred is a child of its
                        // originator's coroutineScope: if that caller was
                        // cancelled (e.g. navigated away mid-fetch), the
                        // Deferred is cancelled and every concurrent awaiter
                        // would fail too. Re-fetch directly on this caller's
                        // scope so a single originator's cancellation can't
                        // take down unrelated awaiters. Re-throw only if THIS
                        // caller was itself cancelled (its Job is marked so);
                        // otherwise the interruption came from the originator
                        // and a fresh fetch on this still-alive caller is safe.
                        val job = coroutineContext[kotlinx.coroutines.Job]
                        if (job?.isCancelled == true) throw ce
                        val epochAtRetry = detailCacheEpoch.get()
                        apiClient.getMediaDetail(itemId).also { result ->
                            if (detailCacheEpoch.get() == epochAtRetry) {
                                result.getOrNull()?.let { detail -> detailCache.put(identity, itemId, detail) }
                            }
                        }
                    }
                }
            }
        }
    }

    override suspend fun getIntros(itemId: String): Result<List<MediaItem>> =
        apiClient.getIntros(itemId)

    override suspend fun getSpecialFeatures(itemId: String): Result<List<MediaItem>> =
        apiClient.getSpecialFeatures(itemId)

    override suspend fun search(
        query: String,
        filters: LibraryFilters,
        limit: Int,
        startIndex: Int,
    ): Result<SearchResult> {
        // The Jellyfin /Search/Hints endpoint doesn't accept genre/year/tags/rating
        // filters (nor sort/played-status), so when any are present fall through to
        // the filtered items query — which honours sortBy/sortOrder/playedStatus.
        val hasAdvancedFilters = filters.genres.isNotEmpty() || filters.years.isNotEmpty() ||
            filters.tags.isNotEmpty() || filters.minRating > 0f ||
            filters.playedStatus != com.raulshma.jellyplay.core.model.PlayedStatus.ALL
        return if (hasAdvancedFilters) {
            apiClient.getMediaItems(
                parentId = null,
                filters = filters,
                studioIds = null,
                startIndex = startIndex,
                limit = limit,
                searchTerm = query,
            )
        } else {
            apiClient.getSearchHints(
                query,
                filters.mediaTypes.takeIf { it.isNotEmpty() },
                limit,
                startIndex,
            )
        }
    }

    override suspend fun findItemByProviderId(provider: String, id: String): Result<String?> =
        apiClient.findItemByProviderId(provider, id)

    override suspend fun getSearchSuggestions(limit: Int): Result<SearchResult> =
        apiClient.getSearchSuggestions(limit)

    override fun getMediaItemsPaged(
        parentId: String?,
        filters: LibraryFilters,
        studioIds: List<String>?,
        kindFilter: com.raulshma.jellyplay.core.model.ItemKindFilter,
    ): Flow<PagingData<MediaItem>> = Pager(
        config = PagingConfig(
            pageSize = PAGE_SIZE,
            enablePlaceholders = false,
            prefetchDistance = PREFETCH_DISTANCE,
        ),
        pagingSourceFactory = {
            MediaPagingSource(
                mediaRepository = this,
                parentId = parentId,
                filters = filters,
                studioIds = studioIds,
                kindFilter = kindFilter,
            )
        },
    ).flow

    override fun searchPaged(
        query: String,
        filters: LibraryFilters,
    ): Flow<PagingData<MediaItem>> = Pager(
        config = PagingConfig(
            pageSize = PAGE_SIZE,
            enablePlaceholders = false,
            prefetchDistance = PREFETCH_DISTANCE,
        ),
        pagingSourceFactory = {
            SearchPagingSource(
                mediaRepository = this,
                query = query,
                filters = filters,
            )
        },
    ).flow

    override suspend fun getGenres(parentId: String?, force: Boolean): Result<List<Genre>> {
        val identity = currentIdentity()
        val cacheKey = "genres_${parentId ?: "root"}"
        if (force) genresCache.remove(identity, cacheKey)
        genresCache.get(identity, cacheKey)?.let { return Result.success(it) }
        return apiClient.getGenres(parentId).also { result ->
            result.getOrNull()?.let { genresCache.put(identity, cacheKey, it) }
        }
    }

    override suspend fun getStudios(parentId: String?): Result<List<Studio>> {
        val identity = currentIdentity()
        val cacheKey = "studios_${parentId ?: "root"}"
        studiosCache.get(identity, cacheKey)?.let { return Result.success(it) }
        return apiClient.getStudios(parentId).also { result ->
            result.getOrNull()?.let { studiosCache.put(identity, cacheKey, it) }
        }
    }

    override suspend fun getItemsByStudio(
        studioId: String,
        mediaTypes: List<MediaType>?,
        startIndex: Int,
        limit: Int,
    ): Result<SearchResult> = apiClient.getItemsByStudio(studioId, mediaTypes, startIndex, limit)

    override suspend fun getArtistAlbums(artistId: String, limit: Int): Result<List<MediaItem>> =
        apiClient.getArtistAlbums(artistId, limit)

    override suspend fun getAlbumTracks(albumId: String): Result<List<MediaItem>> {
        val identity = currentIdentity()
        val cacheKey = "tracks_$albumId"
        albumTracksCache.get(identity, cacheKey)?.let { return Result.success(it) }
        val epochAtStart = detailCacheEpoch.get()
        return apiClient.getAlbumTracks(albumId).also { result ->
            // Skip the cache write if a user-data invalidation landed while the
            // fetch was in flight — otherwise this (now stale) snapshot would be
            // pinned for the full TTL. See [detailCacheEpoch] for the rationale.
            if (detailCacheEpoch.get() == epochAtStart) {
                result.getOrNull()?.let { albumTracksCache.put(identity, cacheKey, it) }
            }
        }
    }

    override suspend fun getMusicVideos(parentId: String, limit: Int): Result<List<MediaItem>> =
        apiClient.getMediaItems(
            parentId = parentId,
            filters = LibraryFilters(mediaTypes = listOf(MediaType.MUSIC_VIDEO)),
            limit = limit,
        ).map { it.items }

    override suspend fun getSimilarItems(itemId: String, limit: Int): Result<List<MediaItem>> {
        val identity = currentIdentity()
        // Key includes the limit so a call with a different limit doesn't serve
        // a stale truncated list.
        val cacheKey = "similar_${itemId}_$limit"
        similarCache.get(identity, cacheKey)?.let { return Result.success(it) }
        val epochAtStart = detailCacheEpoch.get()
        return apiClient.getSimilarItems(itemId, limit).also { result ->
            // Skip the cache write if a user-data invalidation landed while the
            // fetch was in flight; otherwise a pre-mutation similar-items list
            // (e.g. before a favorite toggle) would be pinned for the full TTL.
            if (detailCacheEpoch.get() == epochAtStart) {
                result.getOrNull()?.let { similarCache.put(identity, cacheKey, it) }
            }
        }
    }

    override suspend fun getInstantMix(itemId: String, limit: Int): Result<List<MediaItem>> =
        apiClient.getInstantMix(itemId, limit)

    override suspend fun getItemsByPerson(personId: String, limit: Int): Result<List<MediaItem>> =
        apiClient.getItemsByPerson(personId, limit)

    override suspend fun getThemeSongs(itemId: String): Result<List<MediaItem>> =
        apiClient.getThemeSongs(itemId)

    override suspend fun getSeasons(seriesId: String): Result<List<MediaItem>> =
        // Thin passthrough: the catalogue owns the seasons/episodes snapshot
        // (grouping, single-flight, epoch guard, offline branch). Server order
        // is preserved — the snapshot never reorders seasons.
        episodeCatalogue.loadSeriesEpisodes(seriesId).map { it.seasons }

    override suspend fun getEpisodes(seriesId: String, seasonId: String): Result<List<MediaItem>> =
        // Per-season slice: serves from the shared snapshot if that season is
        // present, else fetches the one season and merges it back (the exact
        // "per-season fetch merges into the grouped cache" semantics the
        // catalogue absorbed from this repository).
        episodeCatalogue.loadSeasonEpisodes(seriesId, seasonId)

    override suspend fun getAllEpisodesGrouped(seriesId: String): Result<Map<String, List<MediaItem>>> =
        // The grouped map shape is derived from the catalogue snapshot's
        // `episodesBySeason`. groupBy-by-seasonId semantics are preserved in
        // the catalogue (an episode whose seasonId is null groups under "").
        episodeCatalogue.loadSeriesEpisodes(seriesId).map { it.episodesBySeason }

    override suspend fun getCollectionItems(
        collectionId: String,
        startIndex: Int,
        limit: Int,
    ): Result<SearchResult> {
        val identity = currentIdentity()
        val cacheKey = "collection_${collectionId}_$startIndex" + "_$limit"
        collectionItemsCache.get(identity, cacheKey)?.let { return Result.success(it) }
        return apiClient.getCollectionItems(collectionId, startIndex, limit).also { result ->
            result.getOrNull()?.let { collectionItemsCache.put(identity, cacheKey, it) }
        }
    }

    override suspend fun getCollections(limit: Int): Result<List<CollectionSummary>> =
        // Not cached: the picker refetches on every open so a freshly-created
        // collection is immediately selectable without a cache-invalidation hop.
        apiClient.getCollections(limit)

    override suspend fun createCollection(name: String, itemIds: List<String>): Result<String> =
        // Plan 08: collection edits self-invalidate — the detail screen used to
        // compensate with a manual invalidateCollectionItemsCache call.
        apiClient.createCollection(name, itemIds)
            .onSuccess { invalidateCollectionItemsCache(it) }

    override suspend fun addItemsToCollection(collectionId: String, itemIds: List<String>): Result<Unit> =
        apiClient.addItemsToCollection(collectionId, itemIds)
            .onSuccess { invalidateCollectionItemsCache(collectionId) }

    override suspend fun getTags(
        parentId: String?,
        startIndex: Int,
        limit: Int,
    ): Result<List<String>> = apiClient.getTags(parentId, startIndex, limit)

    override suspend fun getFavorites(
        mediaTypes: List<MediaType>?,
        limit: Int,
        startIndex: Int,
    ): Result<SearchResult> = apiClient.getFavorites(mediaTypes, limit, startIndex)

    override fun getFavoritesPaged(
        mediaTypes: List<MediaType>?,
    ): Flow<PagingData<MediaItem>> = Pager(
        config = PagingConfig(
            pageSize = PAGE_SIZE,
            enablePlaceholders = false,
            prefetchDistance = PREFETCH_DISTANCE,
        ),
        pagingSourceFactory = {
            FavoritesPagingSource(
                mediaRepository = this,
                mediaTypes = mediaTypes,
            )
        },
    ).flow

    override suspend fun getLyrics(itemId: String): Result<LyricsResult> = apiClient.getLyrics(itemId)

    override suspend fun getLyricsWithFallback(
        itemId: String,
        artistName: String?,
        trackName: String?,
        duration: Double?,
    ): Result<LyricsResult> = runCatching {
        val cached = lyricsCacheDao.getByItemId(itemId)
        if (cached != null) {
            val cachedSynced = cached.syncedLyrics
            val cachedPlain = cached.plainLyrics
            if (!cachedSynced.isNullOrBlank()) {
                val lines = parseLrc(cachedSynced)
                if (lines.isNotEmpty()) {
                    return@runCatching LyricsResult(
                        lines = lines,
                        source = LyricsSource.entries.find { it.name == cached.provider } ?: LyricsSource.UNKNOWN,
                    )
                }
            }
            if (!cachedPlain.isNullOrBlank() && cachedSynced.isNullOrBlank()) {
                return@runCatching LyricsResult(
                    lines = cachedPlain.lineSequence().filter { it.isNotBlank() }
                        .map { LyricsLine(timeMs = 0L, text = it.trim()) }.toList(),
                    source = LyricsSource.entries.find { it.name == cached.provider } ?: LyricsSource.UNKNOWN,
                )
            }
            if (cachedSynced == null && cachedPlain == null && cached.artistName != null) {
                return@runCatching LyricsResult(lines = emptyList(), source = LyricsSource.UNKNOWN)
            }
        }

        val jellyfinResult = apiClient.getLyrics(itemId)
        if (jellyfinResult.isSuccess) {
            val result = jellyfinResult.getOrThrow()
            if (result.lines.isNotEmpty()) {
                cacheLyrics(itemId, result.source, artistName, trackName, duration, result.lines)
                return@runCatching result
            }
        }

        if (artistName.isNullOrBlank() || trackName.isNullOrBlank()) {
            return@runCatching LyricsResult(lines = emptyList(), source = LyricsSource.UNKNOWN)
        }

        val isLocal = networkMonitor.networkStatus.value == NetworkStatus.Local
        if (!isLocal) {
            val lrcLibResult = lrcLibApi.getBestMatch(artistName, trackName, duration)
            if (lrcLibResult.isSuccess) {
                val track = lrcLibResult.getOrThrow()
                val trackSynced = track.syncedLyrics
                val trackPlain = track.plainLyrics
                if (track.instrumental) {
                    lyricsCacheDao.upsert(
                        LyricsCacheEntity(
                            itemId = itemId,
                            provider = LyricsSource.LRCLIB.name,
                            artistName = artistName,
                            trackName = trackName,
                            duration = duration,
                            lrcLibId = track.id,
                            fetchedAt = System.currentTimeMillis(),
                        )
                    )
                    return@runCatching LyricsResult(lines = emptyList(), source = LyricsSource.LRCLIB)
                }
                if (!trackSynced.isNullOrBlank()) {
                    val lines = parseLrc(trackSynced)
                    if (lines.isNotEmpty()) {
                        lyricsCacheDao.upsert(
                            LyricsCacheEntity(
                                itemId = itemId,
                                provider = LyricsSource.LRCLIB.name,
                                artistName = artistName,
                                trackName = trackName,
                                syncedLyrics = trackSynced,
                                plainLyrics = trackPlain,
                                duration = duration,
                                lrcLibId = track.id,
                                fetchedAt = System.currentTimeMillis(),
                            )
                        )
                        return@runCatching LyricsResult(lines = lines, source = LyricsSource.LRCLIB)
                    }
                }
                if (!trackPlain.isNullOrBlank()) {
                    val lines = trackPlain.lineSequence().filter { it.isNotBlank() }
                        .map { LyricsLine(timeMs = 0L, text = it.trim()) }.toList()
                    lyricsCacheDao.upsert(
                        LyricsCacheEntity(
                            itemId = itemId,
                            provider = LyricsSource.LRCLIB.name,
                            artistName = artistName,
                            trackName = trackName,
                            plainLyrics = trackPlain,
                            duration = duration,
                            lrcLibId = track.id,
                            fetchedAt = System.currentTimeMillis(),
                        )
                    )
                    return@runCatching LyricsResult(lines = lines, source = LyricsSource.LRCLIB)
                }
            }
        }

        lyricsCacheDao.upsert(
            LyricsCacheEntity(
                itemId = itemId,
                provider = LyricsSource.UNKNOWN.name,
                artistName = artistName,
                trackName = trackName,
                duration = duration,
                fetchedAt = System.currentTimeMillis(),
            )
        )
        LyricsResult(lines = emptyList(), source = LyricsSource.UNKNOWN)
    }

    override suspend fun searchLyrics(query: String): Result<List<LrcLibTrack>> =
        lrcLibApi.search(query)

    override suspend fun getLyricsById(lrcLibId: Long, itemId: String): Result<LyricsResult> =
        lrcLibApi.getById(lrcLibId).mapCatching { track ->
            val trackSynced = track.syncedLyrics
            val trackPlain = track.plainLyrics
            val lines = if (!trackSynced.isNullOrBlank()) {
                parseLrc(trackSynced)
            } else if (!trackPlain.isNullOrBlank()) {
                trackPlain.lineSequence().filter { it.isNotBlank() }
                    .map { LyricsLine(timeMs = 0L, text = it.trim()) }.toList()
            } else {
                emptyList()
            }
            lyricsCacheDao.upsert(
                LyricsCacheEntity(
                    itemId = itemId,
                    provider = LyricsSource.LRCLIB.name,
                    syncedLyrics = track.syncedLyrics,
                    plainLyrics = track.plainLyrics,
                    lrcLibId = track.id,
                    fetchedAt = System.currentTimeMillis(),
                )
            )
            LyricsResult(lines = lines, source = LyricsSource.LRCLIB)
        }

    override suspend fun getPlaylists(limit: Int): Result<List<Playlist>> = apiClient.getPlaylists(limit)

    override suspend fun getPlaylistItems(playlistId: String, startIndex: Int, limit: Int): Result<List<PlaylistItem>> =
        apiClient.getPlaylistItems(playlistId, startIndex, limit)

    override suspend fun createPlaylist(
        name: String,
        overview: String?,
        itemIds: List<String>,
        mediaType: MediaType,
    ): Result<String> =
        // Plan 08: playlist edits self-invalidate. getPlaylistItems is an
        // uncached passthrough, so the one cached projection of a playlist is
        // its detail entry — one invalidateDetailCache(playlistId) per edit
        // (PlaylistDetailViewModel used to drop it by hand on refresh).
        apiClient.createPlaylist(name, overview, itemIds, mediaType)
            .onSuccess { invalidateDetailCache(it) }

    override suspend fun updatePlaylist(
        playlistId: String,
        name: String?,
        overview: String?,
        isPublic: Boolean?,
    ): Result<Unit> =
        apiClient.updatePlaylist(playlistId, name, overview, isPublic)
            .onSuccess { invalidateDetailCache(playlistId) }

    override suspend fun deletePlaylist(playlistId: String): Result<Unit> =
        apiClient.deletePlaylist(playlistId)
            .onSuccess { invalidateDetailCache(playlistId) }

    override suspend fun addItemsToPlaylist(playlistId: String, itemIds: List<String>): Result<Unit> =
        apiClient.addItemsToPlaylist(playlistId, itemIds)
            .onSuccess { invalidateDetailCache(playlistId) }

    override suspend fun removeItemsFromPlaylist(playlistId: String, entryIds: List<String>): Result<Unit> =
        apiClient.removeItemsFromPlaylist(playlistId, entryIds)
            .onSuccess { invalidateDetailCache(playlistId) }

    override suspend fun movePlaylistItem(playlistId: String, entryId: String, newIndex: Int): Result<Unit> =
        apiClient.movePlaylistItem(playlistId, entryId, newIndex)
            .onSuccess { invalidateDetailCache(playlistId) }

    override suspend fun getSyncPlayGroups(): Result<List<SyncPlayGroup>> =
        apiClient.getSyncPlayGroups()

    override suspend fun joinSyncPlayGroup(groupId: String): Result<Unit> =
        apiClient.joinSyncPlayGroup(groupId)

    override suspend fun leaveSyncPlayGroup(): Result<Unit> =
        apiClient.leaveSyncPlayGroup()

    override suspend fun createSyncPlayGroup(groupName: String): Result<Unit> =
        apiClient.createSyncPlayGroup(groupName)

    override suspend fun getSyncPlayInfo(groupId: String?): Result<SyncPlayGroupInfo> =
        apiClient.getSyncPlayInfo(groupId)

    override suspend fun syncPlayReady(
        positionTicks: Long,
        isPlaying: Boolean,
        playlistItemId: String?,
    ): Result<Unit> =
        apiClient.syncPlayReady(positionTicks, isPlaying, playlistItemId)

    override suspend fun syncPlayPause(): Result<Unit> =
        apiClient.syncPlayPause()

    override suspend fun syncPlayUnpause(): Result<Unit> =
        apiClient.syncPlayUnpause()

    override suspend fun syncPlaySeek(positionTicks: Long): Result<Unit> =
        apiClient.syncPlaySeek(positionTicks)

    override suspend fun syncPlayStop(): Result<Unit> =
        apiClient.syncPlayStop()

    override suspend fun syncPlayNextItem(playlistItemId: String): Result<Unit> =
        apiClient.syncPlayNextItem(playlistItemId)

    override suspend fun syncPlayPreviousItem(playlistItemId: String): Result<Unit> =
        apiClient.syncPlayPreviousItem(playlistItemId)

    override suspend fun syncPlaySetRepeatMode(mode: SyncPlayRepeatMode): Result<Unit> =
        apiClient.syncPlaySetRepeatMode(mode)

    override suspend fun syncPlaySetShuffleMode(mode: SyncPlayShuffleMode): Result<Unit> =
        apiClient.syncPlaySetShuffleMode(mode)

    override suspend fun syncPlaySetNewQueue(
        itemIds: List<String>,
        playingItemId: String,
        mediaSourceId: String?,
        startPositionTicks: Long,
    ): Result<Unit> =
        apiClient.syncPlaySetNewQueue(itemIds, playingItemId, mediaSourceId, startPositionTicks)

    override suspend fun syncPlaySetIgnoreWait(ignore: Boolean): Result<Unit> =
        apiClient.syncPlaySetIgnoreWait(ignore)

    override suspend fun syncPlayRemoveFromPlaylist(playlistItemId: String): Result<Unit> =
        apiClient.syncPlayRemoveFromPlaylist(playlistItemId)

    override suspend fun syncPlayMovePlaylistItem(playlistItemId: String, newIndex: Int): Result<Unit> =
        apiClient.syncPlayMovePlaylistItem(playlistItemId, newIndex)

    override suspend fun toggleFavorite(itemId: String): Result<Boolean> {
        // Fan-out (online API + best-effort offline mirror, or local apply +
        // outbox staging when offline / online call failed) is owned by
        // PlayedStateSync — the single home for the user-data write contract,
        // shared with PlaybackSyncWorker's reconciliation (favorite included).
        return withUserDataMutationCacheInvalidation(itemId) {
            playedStateSync.toggleFavorite(itemId)
        }
    }

    override suspend fun markPlayed(itemId: String): Result<Unit> {
        // Fan-out (online API + best-effort offline mirror, or local apply +
        // outbox staging when offline / online call failed) is owned by
        // PlayedStateSync — the single home for the played/resume-state write
        // contract, shared with PlaybackSyncWorker's reconciliation.
        return withUserDataMutationCacheInvalidation(itemId) {
            playedStateSync.flip(itemId, played = true)
        }
    }

    override suspend fun markUnplayed(itemId: String): Result<Unit> {
        return withUserDataMutationCacheInvalidation(itemId) {
            playedStateSync.flip(itemId, played = false)
        }
    }

    override suspend fun markSeasonPlayed(seasonId: String, seriesId: String): Result<Unit> {
        // Season ids are never detail-cached, so the series-resolution inside
        // withUserDataMutationCacheInvalidation cannot discover the parent —
        // the caller-supplied seriesIdHint is load-bearing here.
        return withUserDataMutationCacheInvalidation(seriesId, seriesIdHint = seriesId) {
            playedStateSync.flip(seasonId, played = true)
        }
    }

    override suspend fun markSeasonUnplayed(seasonId: String, seriesId: String): Result<Unit> {
        return withUserDataMutationCacheInvalidation(seriesId, seriesIdHint = seriesId) {
            playedStateSync.flip(seasonId, played = false)
        }
    }

    /**
     * Evicts before and after a user-data write. The parent series id is
     * captured before the first eviction because that eviction removes the
     * cached detail needed to discover an episode's series; [seriesIdHint]
     * supplies it directly when the mutated id is not detail-cached at all
     * (e.g. season marks — seasons are never cached, so the caller names the
     * series).
     */
    private suspend fun <T> withUserDataMutationCacheInvalidation(
        itemId: String,
        seriesIdHint: String? = null,
        mutation: suspend () -> Result<T>,
    ): Result<T> {
        val seriesId = seriesIdHint ?: cachedSeriesId(itemId)
        invalidateHomeSectionsCache()
        invalidateUserDataCaches(itemId, seriesId)
        return try {
            mutation()
        } finally {
            // The second eviction closes the race where a fetch started after
            // the pre-write eviction observed the old server state.
            invalidateHomeSectionsCache()
            invalidateUserDataCaches(itemId, seriesId)
        }
    }

    /**
     * Composite "user data for [itemId] changed" (favorite flip, played/
     * unplayed, playback position, season mark). Owns the series-resolution
     * rule: drops the item's detail + similar caches and its album tracks,
     * and — if the item belongs to a series (either is the series or is an
     * episode of one, discovered from the cached detail or the caller-supplied
     * [seriesIdHint] when the item itself is not detail-cached, e.g. seasons) —
     * that series' seasons/episodes caches too. Single owner of the rule so a
     * call site never re-derives "is this item part of a series?" locally.
     */
    private fun invalidateUserDataCaches(itemId: String, seriesIdHint: String? = null) {
        val identity = currentIdentity()
        // Read the cached detail first to discover whether this item belongs
        // to a series (either is the series or is an episode of one) so we can
        // drop the series-scoped seasons/episodes caches too.
        val cached = detailCache.get(identity, itemId)
        albumTracksCache.remove(identity, "tracks_$itemId")
        invalidateDetailCache(itemId)
        // Home "Latest in X" rows carry per-item UserData (played/favorite) but
        // are keyed by parent folder, not by itemId, so they can't be evicted
        // selectively — drop the whole (small, LRU-bounded) cache the way the
        // home-sections cache is dropped, so home/library rows reflect the write
        // instead of serving stale badges until the TTL expires.
        latestMediaCache.clear()
        val seriesId = seriesIdHint
            ?: cached?.item?.seriesId
            ?: cached?.takeIf { it.item.mediaType == MediaType.SERIES }?.item?.id
        if (seriesId != null) invalidateSeriesCache(seriesId)
    }

    private fun cachedSeriesId(itemId: String): String? {
        val cached = detailCache.get(currentIdentity(), itemId) ?: return null
        return cached.item.seriesId
            ?: cached.takeIf { it.item.mediaType == MediaType.SERIES }?.item?.id
    }

    override suspend fun getLiveTvChannels(
        startIndex: Int,
        limit: Int,
        addCurrentProgram: Boolean,
        enableFavoriteSorting: Boolean,
        isFavorite: Boolean?,
    ): Result<List<LiveTvChannel>> =
        apiClient.getLiveTvChannels(startIndex, limit, addCurrentProgram, enableFavoriteSorting, isFavorite)

    override suspend fun getRecommendedPrograms(
        filters: ProgramFilters,
        limit: Int,
    ): Result<List<LiveTvProgram>> =
        apiClient.getRecommendedPrograms(filters, limit)

    override suspend fun getLiveTvPrograms(channelId: String, startDateUtc: String?, endDateUtc: String?): Result<List<LiveTvProgram>> =
        apiClient.getLiveTvPrograms(channelId, startDateUtc, endDateUtc)

    override suspend fun getPrograms(channelIds: List<String>, startDateUtc: String, endDateUtc: String): Result<List<LiveTvProgram>> =
        apiClient.getPrograms(channelIds, startDateUtc, endDateUtc)

    override suspend fun getLiveTvGuide(startDateUtc: String, endDateUtc: String, startIndex: Int, limit: Int): Result<EpgGuide> =
        apiClient.getLiveTvGuide(startDateUtc, endDateUtc, startIndex, limit)

    override suspend fun getGuideInfo(): Result<GuideInfo> = apiClient.getGuideInfo()

    override suspend fun getRecordings(limit: Int?, isInProgress: Boolean?): Result<List<LiveTvRecording>> =
        apiClient.getRecordings(limit, isInProgress)

    override suspend fun deleteRecording(recordingId: String): Result<Unit> =
        apiClient.deleteItem(recordingId)

    override suspend fun getTimers(isActive: Boolean?, isScheduled: Boolean?): Result<List<DvrTimer>> =
        apiClient.getTimers(isActive, isScheduled)

    override suspend fun getSeriesTimers(sortBy: String?): Result<List<DvrSeriesTimer>> =
        apiClient.getSeriesTimers(sortBy)

    override suspend fun getDefaultTimer(programId: String): Result<DvrSeriesTimer> =
        apiClient.getDefaultTimer(programId)

    override suspend fun createTimer(programId: String): Result<Unit> = apiClient.createTimer(programId)

    override suspend fun createSeriesTimer(programId: String): Result<Unit> = apiClient.createSeriesTimer(programId)

    override suspend fun cancelTimer(timerId: String): Result<Unit> = apiClient.cancelTimer(timerId)

    override suspend fun cancelSeriesTimer(seriesTimerId: String): Result<Unit> = apiClient.cancelSeriesTimer(seriesTimerId)

    private suspend fun cacheLyrics(
        itemId: String,
        source: LyricsSource,
        artistName: String?,
        trackName: String?,
        duration: Double?,
        lines: List<LyricsLine>,
    ) {
        val syncedLrc = lines.joinToString("\n") { line ->
            val min = line.timeMs / 60_000
            val sec = (line.timeMs % 60_000) / 1000.0
            "[%02d:%06.3f] %s".format(min, sec, line.text)
        }
        lyricsCacheDao.upsert(
            LyricsCacheEntity(
                itemId = itemId,
                provider = source.name,
                artistName = artistName,
                trackName = trackName,
                syncedLyrics = syncedLrc,
                duration = duration,
                fetchedAt = System.currentTimeMillis(),
            )
        )
        // Throttle eviction to at most once per hour. deleteOlderThan is a full
        // table scan; firing it on every lyrics fetch (which happens whenever a
        // user opens lyrics for a new track) was walking & re-locking the whole
        // lyrics_cache table unnecessarily. Eviction semantics (rows older than
        // 30 days eventually removed) preserved.
        val now = System.currentTimeMillis()
        if (now - lastLyricsEvictionMs > 60L * 60 * 1000) {
            lastLyricsEvictionMs = now
            try {
                lyricsCacheDao.deleteOlderThan(now - 30L * 24 * 60 * 60 * 1000)
            } catch (e: Exception) {
                Log.d("MediaRepo", "Failed to evict old lyrics cache", e)
            }
        }
    }

    override suspend fun cleanupLyricsCache() {
        try {
            lyricsCacheDao.deleteOlderThan(System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000)
        } catch (e: Exception) {
            Log.d("MediaRepo", "Failed to cleanup lyrics cache", e)
        }
    }

    /**
     * Wholesale in-memory cache drop (plan 08: demoted off the public
     * interface). Only two legitimate callers remain, both in this module:
     * the `init {}` identity observer (logout / server / user switch) and the
     * background user-data sync worker. Reads that need freshness use the
     * per-query force parameters instead.
     */
    internal suspend fun invalidateCaches() {
        invalidateDetailCache()
        homeSectionsCache.clear()
        // Also clear the secondary caches — they hold user-scoped data (library folders,
        // latest media, genres, studios, photo folder child URLs). They are now
        // identity-keyed (a wrong identity misses by construction), so this wholesale
        // clear is the secondary guard; the primary one is that a previous identity's
        // key can never match the current identity's key.
        libraryFoldersCache.clear()
        latestMediaCache.clear()
        genresCache.clear()
        studiosCache.clear()
        // Seasons/episodes caches now live in [episodeCatalogue]; drop the
        // whole catalogue (every series snapshot + the long epoch) so a
        // wholesale invalidation behaves the same as before.
        episodeCatalogue.invalidateAll()
        albumTracksCache.clear()
        collectionItemsCache.clear()
        photoFolderChildUrlCache.clear()
        // The network-layer home hot-path caches (per-folder latest + per-seed
        // similar) are likewise identity-keyed now, so they no longer need a
        // cross-boundary clear from here.
        // NOTE: the persistent home-section SWR snapshot is intentionally NOT
        // cleared here. invalidateCaches() doesn't know which (server, user)
        // it's running for — it's called both from the identity observer (which
        // has the previous identity in hand) and from background sync workers
        // (which have no identity context). Clearing wholesale here would wipe
        // every user's snapshot on any sync, defeating the multi-account SWR
        // benefit. The identity observer clears the previous identity's rows
        // directly via clearHomeSectionsForIdentity() — see init {}.
    }

    override suspend fun getNewsletterData(sinceDate: String, limit: Int): Result<NewsletterData> =
        apiClient.getNewsletterData(sinceDate, limit)

    override suspend fun sendNewsletter(): Result<Unit> =
        apiClient.sendNewsletter()

    override suspend fun sendTestNewsletter(): Result<Unit> =
        apiClient.sendTestNewsletter()

    companion object {
        private const val PAGE_SIZE = 50
        private const val PREFETCH_DISTANCE = 20
        private const val DETAIL_CACHE_MAX_ENTRIES = 30
        /** 2 minutes — short enough that server changes are reflected quickly. */
        private const val DETAIL_CACHE_TTL_MS = 2 * 60 * 1000L
        /** 60 seconds — prevents burst API calls on repeated home screen loads. */
        private const val HOME_SECTIONS_CACHE_TTL_MS = 60 * 1000L
        /** 10 minutes — library folders change rarely during a session. */
        private const val FOLDERS_CACHE_TTL_MS = 10 * 60 * 1000L
        /** 2 minutes — "latest" content should feel fresh on re-entry. */
        private const val LATEST_CACHE_TTL_MS = 2 * 60 * 1000L
        private val TIME_REGEX = Regex("""\[(\d{1,2}):(\d{2}\.\d{2,3})]""")

        private fun parseLrc(lrcContent: String): List<LyricsLine> {
            val lines = mutableListOf<LyricsLine>()
            lrcContent.lineSequence().forEach { rawLine ->
                val line = rawLine.trim()
                if (line.isEmpty()) return@forEach
                val times = TIME_REGEX.findAll(line).map { match ->
                    val minutes = match.groupValues[1].toLong()
                    val seconds = match.groupValues[2].toDouble()
                    minutes * 60_000 + (seconds * 1000).toLong()
                }.toList()
                if (times.isEmpty()) return@forEach
                val textStart = line.lastIndexOf(']') + 1
                val text = line.substring(textStart).trim()
                val words = parseInlineWordTimings(text)
                if (text.isEmpty()) {
                    times.forEach { timeMs -> lines.add(LyricsLine(timeMs = timeMs, text = "")) }
                } else {
                    times.forEach { timeMs ->
                        val adjustedWords = if (words.isNotEmpty()) {
                            words.map { it.copy(timeMs = it.timeMs) }
                        } else emptyList()
                        lines.add(
                            LyricsLine(
                                timeMs = timeMs,
                                text = text,
                                words = adjustedWords,
                            )
                        )
                    }
                }
            }
            return lines.sortedBy { it.timeMs }
        }

        /**
         * Parses Enhanced LRC inline word timings:
         * "[00:12.34]Hello [00:12.89]world [00:13.45]test"
         */
        private fun parseInlineWordTimings(text: String): List<com.raulshma.jellyplay.core.model.LyricsWord> {
            if (text.isBlank()) return emptyList()
            val matches = TIME_REGEX.findAll(text).toList()
            if (matches.isEmpty()) return emptyList()
            return matches.mapIndexed { index, match ->
                val minutes = match.groupValues[1].toLong()
                val seconds = match.groupValues[2].toDouble()
                val timeMs = minutes * 60_000 + (seconds * 1000).toLong()
                val wordStart = match.range.last + 1
                val wordEnd = matches.getOrNull(index + 1)?.range?.first ?: text.length
                val rawWord = text.substring(wordStart, wordEnd).trim()
                com.raulshma.jellyplay.core.model.LyricsWord(timeMs = timeMs, text = rawWord)
            }.filter { it.text.isNotEmpty() }
        }
    }

    private val photoFolderChildUrlCache = TtlCache<List<String>>(
        maxSize = 200,
        ttlMs = 5 * 60 * 1000L,
    )

    override suspend fun getPhotoFolderChildImageUrls(folderId: String, limit: Int): List<String> {
        val identity = currentIdentity()
        photoFolderChildUrlCache.get(identity, folderId)?.let { return it }
        val urls = apiClient.getChildItemImageUrls(folderId, limit)
        photoFolderChildUrlCache.put(identity, folderId, urls)
        return urls
    }
}
