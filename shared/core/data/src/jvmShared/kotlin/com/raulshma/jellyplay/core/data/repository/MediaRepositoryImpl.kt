package com.raulshma.jellyplay.core.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.raulshma.jellyplay.core.data.log.Log
import com.raulshma.jellyplay.core.database.dao.HomeSectionCacheDao
import com.raulshma.jellyplay.core.database.entity.HomeSectionCacheEntity
import com.raulshma.jellyplay.core.data.paging.FavoritesPagingSource
import com.raulshma.jellyplay.core.data.paging.MediaPagingSource
import com.raulshma.jellyplay.core.data.paging.SearchPagingSource
import com.raulshma.jellyplay.core.data.session.HomeSession
import com.raulshma.jellyplay.core.data.session.SessionCacheRegistry
import com.raulshma.jellyplay.core.data.session.SessionIdentity
import com.raulshma.jellyplay.core.model.CollectionSummary
import com.raulshma.jellyplay.core.model.DvrSeriesTimer
import com.raulshma.jellyplay.core.model.DvrTimer
import com.raulshma.jellyplay.core.model.EpgGuide
import com.raulshma.jellyplay.core.model.Genre
import com.raulshma.jellyplay.core.model.HomeFreshness
import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.TtlCache
import com.raulshma.jellyplay.core.model.HomeSectionsResult
import com.raulshma.jellyplay.core.model.HomeSectionQuery
import com.raulshma.jellyplay.core.model.LibraryFilters
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.LiveTvChannel
import com.raulshma.jellyplay.core.model.LiveTvProgram
import com.raulshma.jellyplay.core.model.LiveTvRecording
import com.raulshma.jellyplay.core.model.GuideInfo
import com.raulshma.jellyplay.core.model.ProgramFilters
import com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogue
import com.raulshma.jellyplay.core.data.util.TimeSource
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.Playlist
import com.raulshma.jellyplay.core.model.PlaylistItem
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.SearchResult
import com.raulshma.jellyplay.core.model.Studio
import com.raulshma.jellyplay.core.model.UserDataChange
import com.raulshma.jellyplay.core.model.SyncPlayGroup
import com.raulshma.jellyplay.core.model.SyncPlayGroupInfo
import com.raulshma.jellyplay.core.model.SyncPlayRepeatMode
import com.raulshma.jellyplay.core.model.SyncPlayShuffleMode
import com.raulshma.jellyplay.core.model.NewsletterData
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import com.raulshma.jellyplay.core.network.realtime.UserDataRealtimeChannel
import com.raulshma.jellyplay.core.data.cache.getOrFetch
import com.raulshma.jellyplay.core.data.cache.getOrFetchGuarded
import com.raulshma.jellyplay.core.data.concurrency.SingleFlightFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

// Phase X MediaRepository cluster flip: moved verbatim from the legacy
// :core:data shim (same package/name). Ctor-level transforms only — method
// bodies are byte-identical:
//  - `@Singleton` / `@Inject` stripped (one framework per type — Koin's
//    dataJvmModule constructs this single; every consumer resolves it
//    straight from Koin).
//  - `android.util.Log` → the module's Log facade.
//  - `internal suspend fun invalidateCaches` widened to public: its only
//    production caller (UserDataSyncWorker) lives in the legacy module, and
//    `internal` no longer crosses the module boundary after this move.
class MediaRepositoryImpl(
    private val apiClient: JellyfinApiClient,
    private val homeSectionCacheDao: HomeSectionCacheDao,
    private val playedStateSync: PlayedStateSync,
    /**
     * The deep "Episode Catalogue": the single owner of the series
     * seasons/episodes snapshot. Injected (not constructed) so the repo's
     * `getSeasons`/`getEpisodes`/`getAllEpisodesGrouped` can delegate to it.
     * The catalogue depends on `JellyfinApiClient` + `OfflineRepository` only
     * (never on `MediaRepository`), so this edge does NOT form a DI cycle —
     * both are Koin singles in `core:data` and the constructor edge fixes
     * the direction.
     */
    private val episodeCatalogue: EpisodeCatalogue,
    /**
     * Server-push user-data changes over the shared WebSocket. Re-exported
     * verbatim as [userDataChanges]; the channel's current-user filter means
     * the identity-switch observer above needs no involvement of its own.
     */
    private val userDataRealtimeChannel: UserDataRealtimeChannel,
    /**
     * Clock seam for the freshness gates in this repo: the wall-clock read
     * drives the SWR staleness check in [getCachedHomeSections]
     * (`HomeFreshness.isRoomSnapshotFresh`), the monotonic read drives the
     * in-memory [TtlCache] clock (whose contract requires a monotonic source
     * — a wall-clock NTP jump would mass-expire or extend every entry).
     * Already a Koin single in `dataJvmModule` (`SystemTimeSource`); injected
     * here so both ceilings are
     * unit-testable with one fake.
     */
    private val timeSource: TimeSource,
    /**
     * The single owner of identity transitions (see [HomeSession]). Replaces
     * this repo's own `lastStableIdentity` mirror + `init {}` observer: the
     * cache-invalidation reaction is registered with [SessionCacheRegistry]
     * (the single subscriber of [HomeSession.transitions]), and identity
     * reads for cache keying go through [HomeSession.cacheIdentity] (the
     * suspend source-flow read — the mirror lags a switch by a dispatch,
     * which would key fetches under the previous identity).
     */
    private val homeSession: HomeSession,
    /**
     * The single home for identity reactions (see [SessionCacheRegistry]).
     * This repo registers its TtlCaches for wholesale clears and one action
     * for the previous identity's persisted home-section SWR clear — no
     * bespoke collector or long-lived scope of its own.
     */
    private val sessionCacheRegistry: SessionCacheRegistry,
) : MediaRepository,
    LiveTvRepository,
    SyncPlayRepository,
    NewsletterRepository,
    PlaylistRepository,
    MediaRepositoryCacheInvalidation,
    MediaCacheInvalidator {

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
    // items, theme songs) stay — they're not part of the catalogue's
    // "seasons → episodes → sorted" shape.
    private val similarCache = TtlCache<List<MediaItem>>(ttlMs = DETAIL_CACHE_TTL_MS)
    private val albumTracksCache = TtlCache<List<MediaItem>>(ttlMs = DETAIL_CACHE_TTL_MS)
    private val collectionItemsCache = TtlCache<SearchResult>(ttlMs = DETAIL_CACHE_TTL_MS)

    // Theme songs for a detail item: ThemeMusicPlayer releases its player on
    // screen exit, so every detail re-entry re-fetched the (almost always
    // empty) list — one HTTP round-trip per navigation for nothing. Cached
    // with the same 2-minute TTL and epoch-guarded write as
    // similarCache/albumTracksCache above; a stale list ≤2 min after a
    // server change is harmless for ambient audio.
    private val themeSongsCache = TtlCache<List<MediaItem>>(ttlMs = DETAIL_CACHE_TTL_MS)

    // Child-photo URLs for a photo folder (player backdrop fan-out); declared
    // with the other caches so the identity registration in the init block
    // below can enumerate every cache in one place.
    private val photoFolderChildUrlCache = TtlCache<List<String>>(
        maxSize = 200,
        ttlMs = 5 * 60 * 1000L,
    )

    // Plan 08: private — the detail cache is repo-internal machinery; reads
    // that need freshness use getMediaDetail(force = true) and mutations/
    // invalidations run through the composite + per-type dispatch below.
    private fun invalidateDetailCache(itemId: String? = null) {
        detailCacheEpoch.incrementAndGet()
        if (itemId != null) {
            val identity = homeSession.cacheIdentitySnapshot()
            detailCache.remove(identity, itemId)
            // similarCache keys are `similar_${itemId}_$limit` (the limit is
            // part of the key so a different limit never serves a truncated
            // list), so evict by prefix to drop every limit variant.
            // themeSongsCache keys are `themes_$itemId` — same prefix evict.
            similarCache.removeByKeyPrefix(identity, "similar_$itemId")
            themeSongsCache.removeByKeyPrefix(identity, "themes_$itemId")
        } else {
            detailCache.clear()
            similarCache.clear()
            themeSongsCache.clear()
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
        collectionItemsCache.removeByKeyPrefix(homeSession.cacheIdentitySnapshot(), "collection_$collectionId")
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
    // TTL comes from the shared home freshness policy (HomeFreshness); the
    // clock is the injected [timeSource]'s MONOTONIC read — TtlCache's
    // contract requires one, and the same fake drives the Room SWR ceiling's
    // wall-clock read.
    private val homeSectionsCache = TtlCache<HomeSectionsResult>(
        maxSize = 1,
        ttlMs = HomeFreshness.REPO_MEMORY_TTL_MS,
        clock = { timeSource.nowElapsedRealtimeMillis() },
    )

    /**
     * Drops the in-memory home-sections cache for the current identity. Used by
     * [toggleFavorite] / [markPlayed] / [markUnplayed] so a user-data change is
     * reflected on the next home load (the previous hand-rolled slot zeroed the
     * timestamp to force a TTL miss; this is the identity-aware equivalent).
     */
    private fun invalidateHomeSectionsCache() {
        homeSectionsCache.clear()
    }


    init {
        // Identity transitions are handled by [SessionCacheRegistry] (the
        // single subscriber of HomeSession.transitions — previously this
        // repo, EpisodeCatalogueImpl and HomeViewModel each maintained their
        // own last-identity mirror over the separate server/user flows).
        // This closes a privacy + correctness gap where the previous user's
        // home sections / detail data was served for up to 10 minutes (the
        // longest TTL) after `switchUser` or `switchServerAddress`.
        //
        // Reaction rules (identical to the observer this replaces):
        //  - SignedIn            : NOTHING — session restore / first login;
        //                          caches are identity-keyed and there is no
        //                          previous identity to drop (the registry
        //                          skips SignedIn wholesale).
        //  - User/ServerSwitched : wholesale drop + clear the PREVIOUS
        //                          identity's persisted home-section SWR rows.
        //  - SignedOut           : wholesale drop + clear the logged-out
        //                          identity's rows (privacy).
        sessionCacheRegistry.registerCaches(
            "media",
            libraryFoldersCache,
            genresCache,
            studiosCache,
            latestMediaCache,
            albumTracksCache,
            collectionItemsCache,
            homeSectionsCache,
            photoFolderChildUrlCache,
        )
        // The action carries only the reactions a plain registry drop
        // cannot express: the detail cache's epoch bump (an in-flight
        // previous-identity fetch must not write back into the cleared
        // cache) together with its similar/theme-songs companions, and the
        // previous identity's persisted SWR rows. The episode catalogue's
        // snapshot drop + epoch bump lives in its own registration —
        // routing through invalidateCaches() here would clear every cache
        // twice and double-bump the catalogue's epoch on each transition.
        sessionCacheRegistry.registerAction("media-identity-clear") { transition ->
            invalidateDetailCache()
            // Clear the PREVIOUS identity's persisted home-section SWR
            // rows — scoped, not wholesale, so a multi-account server
            // keeps the other users' snapshots for their next cold
            // open. Runs here (where the transition carries the
            // previous identity) rather than in invalidateCaches()
            // (which has no identity context). Null only on SignedIn,
            // which the registry excludes.
            transition.previousIdentity?.let { previous ->
                clearHomeSectionsForIdentity(previous.serverId, previous.userId)
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
                Log.w(
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
        val cacheKey = query.cacheKey()
        return homeSectionsCache.getOrFetch(
            { homeSession.cacheIdentity() },
            cacheKey,
            force = force,
            // SWR persist: the fetch-path-only write hook — persists the
            // snapshot for stale-while-revalidate on cold open (the in-memory
            // cache is lost on process death) after the in-memory put, and
            // never on a cache hit, so a hit cannot slide the persisted row's
            // fetchedAt forward and defeat the 24h SWR staleness ceiling below.
            onFetched = { persistHomeSectionsSnapshot(cacheKey, it) },
        ) {
            // The query value object crosses the repo → network seam intact; force
            // propagates so the network layer's sub-call caches are bypassed too.
            apiClient.getHomeSections(query, force)
        }
    }

    override suspend fun getCachedHomeSections(
        query: HomeSectionQuery,
    ): HomeSectionsResult? {
        // Read identity from the source flow (via HomeSession's sanctioned
        // suspend read), not the mirror: this runs from the Home VM's
        // currentUser collector, which can fire before the session's identity
        // observer has written the mirror. .first() is suspend + non-blocking
        // and guarantees the current value, so the SWR read never misses due
        // to an observe ordering race.
        val identity = homeSession.currentIdentity() ?: return null
        val entity = homeSectionCacheDao.get(identity.serverId, identity.userId, query.cacheKey()) ?: return null
        // SWR staleness ceiling (HomeFreshness): a snapshot older than 24h
        // must not instant-paint — return null so a cold open shows the
        // spinner instead of ancient content, then the normal refresh
        // proceeds and upserts a fresh row.
        if (!HomeFreshness.isRoomSnapshotFresh(entity.fetchedAt, timeSource.nowEpochMillis())) {
            return null
        }
        // Decode the payload off the caller's (Main) dispatcher — this is the
        // cold-open critical path and the blob spans hundreds of MediaItems.
        return withContext(Dispatchers.Default) { entity.payload }
    }

    override suspend fun getOfflineHomeLayout(): HomeSectionsResult? {
        val identity = homeSession.currentIdentity() ?: return null
        // Key-agnostic latest row and no freshness ceiling, by contract (see
        // the interface KDoc): the offline home re-filters membership against
        // the offline store, so staleness only costs section ORDER/titles,
        // never content. Decode off the caller's dispatcher like the SWR read.
        val entity = homeSectionCacheDao.getLatestForIdentity(identity.serverId, identity.userId)
            ?: return null
        return withContext(Dispatchers.Default) { entity.payload }
    }

    /**
     * In-memory record of the last home snapshot this process persisted (or
     * verified byte-identical) for one (server, user, cacheKey): the DB row's
     * `fetchedAt` it was computed against, plus the cheap fingerprint of the
     * payload ([homeSnapshotFingerprint]). Lets the dedup window in
     * [persistHomeSectionsSnapshot] skip the full JSON re-encode on
     * usually-identical foreground refreshes. Null until the first persist
     * of the process — a miss simply takes the exact encode+compare path.
     */
    private class HomeSnapshotDedupState(
        val serverId: String,
        val userId: String,
        val cacheKey: String,
        val rowFetchedAt: Long,
        val fingerprint: Int,
    )

    // @Volatile: persistHomeSectionsSnapshot runs on the caller's dispatcher
    // (the home refresh path), which is not pinned to one thread.
    @Volatile
    private var lastHomeSnapshotDedup: HomeSnapshotDedupState? = null

    /**
     * Cheap structural fingerprint of a [HomeSectionsResult] for the dedup
     * window below (DATA-1): ONE string per fetch (indexed loops, no
     * per-item object allocations), covering what a foreground home refresh
     * can change — failed section types, per-section header identity
     * (id/title/type/libraryId/collectionType/seed id) and, per item, the id
     * plus the user-data fields the home rows render (playback position,
     * played + favorite flags). Deliberately NOT the full payload:
     * metadata-only changes (overview text, ratings, …) do not alter it,
     * which is exactly the "equal fingerprint does not imply a byte-identical
     * encode" caveat documented in [persistHomeSectionsSnapshot]. Hashed to a
     * single Int for retention in [lastHomeSnapshotDedup].
     */
    private fun homeSnapshotFingerprint(result: HomeSectionsResult): Int {
        val sb = StringBuilder()
        for (failedType in result.failedSectionTypes) {
            sb.append(failedType.name)
            sb.append('|')
        }
        sb.append('#')
        val sections = result.sections
        for (s in sections.indices) {
            val section = sections[s]
            sb.append(section.id)
            sb.append('|')
            sb.append(section.title)
            sb.append('|')
            sb.append(section.type.name)
            sb.append('|')
            sb.append(section.libraryId)
            sb.append('|')
            sb.append(section.collectionType)
            sb.append('|')
            sb.append(section.seedItem?.id)
            sb.append('|')
            val sectionItems = section.items
            for (i in sectionItems.indices) {
                val item = sectionItems[i]
                sb.append(item.id)
                sb.append('|')
                sb.append(item.playbackPositionTicks)
                sb.append('|')
                sb.append(item.isPlayed)
                sb.append('|')
                sb.append(item.isFavorite)
                sb.append(';')
            }
            sb.append('#')
        }
        // toString() first: StringBuilder's own hashCode is identity-based.
        return sb.toString().hashCode()
    }

    private suspend fun persistHomeSectionsSnapshot(cacheKey: String, result: HomeSectionsResult) {
        val identity = homeSession.currentIdentity() ?: return
        runCatching {
            // Foreground refreshes arrive ~once/minute with usually-identical
            // content; when the prior row is younger than the refresh cadence
            // and the payload is byte-identical, the rewrite would advance
            // nothing (fetchedAt refreshes at the next real change) — skip it.
            // Rows older than the window still rewrite, preserving the 24h
            // fetchedAt SWR staleness ceiling for every other path.
            val now = timeSource.nowEpochMillis()
            val existing = homeSectionCacheDao.get(identity.serverId, identity.userId, cacheKey)
            // Cheap-path dedup (DATA-1): inside the window, a fingerprint
            // match against the last payload this process persisted/verified
            // for this exact (server, user, cacheKey, row) skips the full
            // encode — that encode used to run on every ~1/min refresh and
            // allocate a several-hundred-KB string even when byte-identical.
            // INVARIANT: fingerprint equal ⇒ the encode would have been
            // byte-identical is NOT guaranteed (only section identity, item
            // ids and their user-data fields are fingerprinted — see
            // [homeSnapshotFingerprint]), so a metadata-only change inside
            // the window persists one refresh cycle later (audit-accepted).
            // Fingerprint unequal, window expired, or no prior state ⇒ the
            // exact pre-existing encode+compare+write path below runs.
            if (existing != null && now - existing.fetchedAt < HOME_PERSIST_DEDUP_WINDOW_MS) {
                val last = lastHomeSnapshotDedup
                if (last != null &&
                    last.serverId == identity.serverId &&
                    last.userId == identity.userId &&
                    last.cacheKey == cacheKey &&
                    last.rowFetchedAt == existing.fetchedAt &&
                    last.fingerprint == homeSnapshotFingerprint(result)
                ) {
                    return
                }
            }
            // Encode off the caller's (Main) dispatcher — this runs on every
            // successful home refresh (min. once/minute in foreground).
            val payloadJson = withContext(Dispatchers.Default) {
                com.raulshma.jellyplay.core.database.Converters.encodeHomeSectionsResult(result)
            }
            if (existing != null &&
                now - existing.fetchedAt < HOME_PERSIST_DEDUP_WINDOW_MS &&
                existing.payloadJson == payloadJson
            ) {
                // Byte-identical inside the window: remember the fingerprint
                // (against this row's fetchedAt) so the next in-window
                // refresh can take the cheap path above.
                rememberHomeSnapshotDedup(identity, cacheKey, existing.fetchedAt, result)
                return
            }
            homeSectionCacheDao.upsert(
                HomeSectionCacheEntity(
                    serverId = identity.serverId,
                    userId = identity.userId,
                    cacheKey = cacheKey,
                    payloadJson = payloadJson,
                    // Wall-clock on purpose: this value must survive a reboot to
                    // serve the next cold open, and monotonic clocks reset on
                    // boot. Goes through the injected [TimeSource] (wall-clock
                    // read in production) — the in-memory TTL above uses the
                    // same seam's monotonic read, so both freshness gates
                    // share one test fake.
                    // fetchedAt is load-bearing: getCachedHomeSections reads it
                    // against HomeFreshness's 24h SWR staleness ceiling.
                    fetchedAt = now,
                ),
            )
            rememberHomeSnapshotDedup(identity, cacheKey, now, result)
        }
    }

    /**
     * Records [lastHomeSnapshotDedup] for the row fetchedAt the fingerprint
     * was computed against — both persist paths (skipped rewrite and fresh
     * upsert) funnel through here.
     */
    private fun rememberHomeSnapshotDedup(
        identity: SessionIdentity,
        cacheKey: String,
        rowFetchedAt: Long,
        result: HomeSectionsResult,
    ) {
        lastHomeSnapshotDedup = HomeSnapshotDedupState(
            serverId = identity.serverId,
            userId = identity.userId,
            cacheKey = cacheKey,
            rowFetchedAt = rowFetchedAt,
            fingerprint = homeSnapshotFingerprint(result),
        )
    }

    override suspend fun getLibraryFolders(force: Boolean): Result<List<LibraryFolder>> =
        libraryFoldersCache.getOrFetch({ homeSession.cacheIdentity() }, "folders", force = force) {
            apiClient.getLibraryFolders()
        }

    override suspend fun getLatestMedia(
        parentId: String,
        limit: Int,
    ): Result<List<MediaItem>> =
        latestMediaCache.getOrFetch({ homeSession.cacheIdentity() }, "latest_${parentId}_$limit") {
            apiClient.getLatestMedia(parentId = parentId, limit = limit)
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
    // The fetch semantics (caller-scope async, lock-scope re-check, epoch
    // guard, cancellation ladder) live in [detailFetcher]; the epoch it shares
    // with the album-tracks/similar-items write guards below is
    // [detailCacheEpoch].
    private val detailCacheEpoch = AtomicLong(0L)
    private val detailFetcher = SingleFlightFetcher(detailCache, detailCacheEpoch)

    override suspend fun getMediaDetail(itemId: String, force: Boolean): Result<MediaDetail> {
        // Freshness lever: drop the cached entry first — verbatim the
        // invalidate-then-read sequence callers used to run by hand. The
        // detailCacheEpoch bump inside invalidateDetailCache also guards a
        // racing fetch from re-inserting the stale snapshot.
        if (force) invalidateDetailCache(itemId)
        return detailFetcher.getOrFetch({ homeSession.cacheIdentity() }, itemId) {
            apiClient.getMediaDetail(itemId)
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

    override suspend fun getGenres(parentId: String?, force: Boolean): Result<List<Genre>> =
        genresCache.getOrFetch({ homeSession.cacheIdentity() }, "genres_${parentId ?: "root"}", force = force) {
            apiClient.getGenres(parentId)
        }

    // No force lever: unlike getGenres, getStudios never grew the freshness
    // parameter, and the plain shape keeps exactly that behaviour.
    override suspend fun getStudios(parentId: String?): Result<List<Studio>> =
        studiosCache.getOrFetch({ homeSession.cacheIdentity() }, "studios_${parentId ?: "root"}") {
            apiClient.getStudios(parentId)
        }

    override suspend fun getItemsByStudio(
        studioId: String,
        mediaTypes: List<MediaType>?,
        startIndex: Int,
        limit: Int,
    ): Result<SearchResult> = apiClient.getItemsByStudio(studioId, mediaTypes, startIndex, limit)

    override suspend fun getArtistAlbums(artistId: String, limit: Int): Result<List<MediaItem>> =
        apiClient.getArtistAlbums(artistId, limit)

    override suspend fun getAlbumTracks(albumId: String): Result<List<MediaItem>> =
        // Epoch-guarded write: a user-data invalidation landing mid-fetch must
        // not pin the (now stale) snapshot for the full TTL — the epoch is
        // captured after the miss and compared at completion. See [detailCacheEpoch].
        albumTracksCache.getOrFetchGuarded(
            { homeSession.cacheIdentity() },
            "tracks_$albumId",
            currentEpoch = { detailCacheEpoch.get() },
        ) {
            apiClient.getAlbumTracks(albumId)
        }

    override suspend fun getMusicVideos(parentId: String, limit: Int): Result<List<MediaItem>> =
        apiClient.getMediaItems(
            parentId = parentId,
            filters = LibraryFilters(mediaTypes = listOf(MediaType.MUSIC_VIDEO)),
            limit = limit,
        ).map { it.items }

    override suspend fun getSimilarItems(itemId: String, limit: Int): Result<List<MediaItem>> =
        // Key includes the limit so a call with a different limit doesn't serve
        // a stale truncated list. Epoch-guarded write — a user-data invalidation
        // landing mid-fetch (e.g. a favorite toggle) must not pin the
        // pre-mutation list for the full TTL. See [detailCacheEpoch].
        similarCache.getOrFetchGuarded(
            { homeSession.cacheIdentity() },
            "similar_${itemId}_$limit",
            currentEpoch = { detailCacheEpoch.get() },
        ) {
            apiClient.getSimilarItems(itemId, limit)
        }

    override suspend fun getInstantMix(itemId: String, limit: Int): Result<List<MediaItem>> =
        apiClient.getInstantMix(itemId, limit)

    override suspend fun getItemsByPerson(personId: String, limit: Int): Result<List<MediaItem>> =
        apiClient.getItemsByPerson(personId, limit)

    override suspend fun getThemeSongs(itemId: String): Result<List<MediaItem>> =
        // Cached exactly like getSimilarItems above (DATA-3): identity-keyed,
        // item-scoped key, 2-minute TTL, epoch-guarded write so a user-data
        // invalidation landing mid-fetch cannot pin a stale list for the
        // full TTL — see [detailCacheEpoch]. Evicted with the detail's other
        // per-item caches in [invalidateDetailCache].
        themeSongsCache.getOrFetchGuarded(
            { homeSession.cacheIdentity() },
            "themes_$itemId",
            currentEpoch = { detailCacheEpoch.get() },
        ) {
            apiClient.getThemeSongs(itemId)
        }

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
    ): Result<SearchResult> =
        collectionItemsCache.getOrFetch(
            { homeSession.cacheIdentity() },
            "collection_${collectionId}_$startIndex" + "_$limit",
        ) {
            apiClient.getCollectionItems(collectionId, startIndex, limit)
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

    private val syntheticUserDataChanges = MutableSharedFlow<UserDataChange>(
        extraBufferCapacity = SYNTHETIC_CHANGES_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /**
     * WS pushes merged with [syntheticUserDataChanges] — local writers that
     * reach the server outside the socket's echo (notably the offline outbox
     * drain in PlaybackSyncWorker) feed the same consumer fan-out (home
     * forced refresh, open detail sessions) through [notifyUserDataChanged].
     */
    override val userDataChanges: Flow<UserDataChange> =
        merge(userDataRealtimeChannel.changes, syntheticUserDataChanges)

    override fun notifyUserDataChanged(itemIds: List<String>) {
        if (itemIds.isEmpty()) return
        // No identity → nothing is keyed to a user yet; emitting would only
        // risk refreshing another account's screens on a stale collector.
        val userId = homeSession.currentIdentitySnapshot()?.userId ?: return
        syntheticUserDataChanges.tryEmit(UserDataChange(userId, itemIds.distinct()))
    }

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
        val identity = homeSession.cacheIdentitySnapshot()
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
        val cached = detailCache.get(homeSession.cacheIdentitySnapshot(), itemId) ?: return null
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

    /**
     * Wholesale in-memory cache drop (plan 08: demoted off the public
     * [MediaRepository] interface, kept on the impl — the
     * [MediaCacheInvalidator] port is its cross-module seam for the legacy
     * sync workers). The only production caller is the background user-data
     * sync worker — the `SessionCacheRegistry` identity path reacts via its
     * own cache registration + action instead (see the init block). Reads
     * that need freshness use the per-query force parameters instead.
     */
    override suspend fun invalidateCaches() {
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
        // it's running for — it's called both from the registry's identity action (which
        // has the previous identity in hand) and from background sync workers
        // (which have no identity context). Clearing wholesale here would wipe
        // every user's snapshot on any sync, defeating the multi-account SWR
        // benefit. The registry action clears the previous identity's rows
        // directly via clearHomeSectionsForIdentity() — see the init block above.
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
        /**
         * Buffer for [syntheticUserDataChanges]: large enough that a drain of
         * dozens of flips never suspends or drops wholesale, small enough to
         * be pointless to tune. DROP_OLDEST keeps the tryEmit non-suspending.
         */
        private const val SYNTHETIC_CHANGES_BUFFER = 64
        /** 10 minutes — library folders change rarely during a session. */
        private const val FOLDERS_CACHE_TTL_MS = 10 * 60 * 1000L

        /** Window within which a byte-identical home SWR persist is skipped (foreground refresh cadence). */
        private const val HOME_PERSIST_DEDUP_WINDOW_MS = 60 * 1000L
        /** 2 minutes — "latest" content should feel fresh on re-entry. */
        private const val LATEST_CACHE_TTL_MS = 2 * 60 * 1000L
    }

    override suspend fun getPhotoFolderChildImageUrls(folderId: String, limit: Int): List<String> =
        // The one non-Result fetch, riding the Result-shaped seam — the cache
        // module deliberately has no fourth (non-Result) shape. The wrap is
        // pure shape: if the call throws, the lambda unwinds before any Result
        // exists (nothing is cached, the exception propagates), so the
        // trailing getOrThrow can only ever unwrap a stored success.
        photoFolderChildUrlCache.getOrFetch({ homeSession.cacheIdentity() }, folderId) {
            Result.success(apiClient.getChildItemImageUrls(folderId, limit))
        }.getOrThrow()
}
