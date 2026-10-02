package com.raulshma.jellyplay.core.data.repository

import androidx.paging.PagingData
import com.raulshma.jellyplay.core.data.paging.JellyfinPagingSource
import com.raulshma.jellyplay.core.data.paging.pagedMediaPager
import com.raulshma.jellyplay.core.data.paging.searchPagingSource
import com.raulshma.jellyplay.core.data.session.HomeSession
import com.raulshma.jellyplay.core.data.session.SessionCacheRegistry
import com.raulshma.jellyplay.core.model.CollectionSummary
import com.raulshma.jellyplay.core.model.FreshnessCeilings
import com.raulshma.jellyplay.core.model.DiscoverRowConfig
import com.raulshma.jellyplay.core.model.Genre
import com.raulshma.jellyplay.core.model.HomeFreshness
import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.TtlCache
import com.raulshma.jellyplay.core.model.HomeSectionsResult
import com.raulshma.jellyplay.core.model.HomeSectionQuery
import com.raulshma.jellyplay.core.model.LibraryFilters
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogue
import com.raulshma.jellyplay.core.data.util.TimeSource
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.SearchResult
import com.raulshma.jellyplay.core.model.Studio
import com.raulshma.jellyplay.core.model.UserDataChange
import com.raulshma.jellyplay.core.model.SyncPlayGroup
import com.raulshma.jellyplay.core.model.SyncPlayGroupInfo
import com.raulshma.jellyplay.core.network.api.LibraryApiClient
import com.raulshma.jellyplay.core.network.api.CollectionApiClient
import com.raulshma.jellyplay.core.network.api.SyncPlayApiClient
import com.raulshma.jellyplay.core.network.library.HomeSectionsCachePort
import com.raulshma.jellyplay.core.network.realtime.UserDataRealtimeChannel
import com.raulshma.jellyplay.core.data.cache.getOrFetch
import com.raulshma.jellyplay.core.data.cache.getOrFetchGuarded
import com.raulshma.jellyplay.core.data.concurrency.StaleReadGroup
import com.raulshma.jellyplay.core.data.concurrency.StaleReadGroups
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayManager
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.merge

//  MediaRepository cluster flip: moved verbatim from the legacy
// core:data shim (same package/name). Ctor-level transforms only — method
// bodies are byte-identical:
//  - `@Singleton` / `@Inject` stripped (one framework per type — Koin's
//    dataJvmModule constructs this single; every consumer resolves it
//    straight from Koin).
//  - `android.util.Log` → the module's Log facade.
//  - `internal suspend fun invalidateCaches` widened to public: its only
//    production caller (UserDataSyncWorker) lives in the legacy module, and
//    `internal` no longer crosses the module boundary after this move.
//
//  MediaRepository facade split: the LiveTv / Newsletter / Playlist family
// surfaces moved out to their own impls (LiveTvRepositoryImpl,
// NewsletterRepositoryImpl, PlaylistRepositoryImpl — same package), each
// over the narrow API family client (the PlaybackRepositoryImpl ctor
// precedent). SyncPlayRepository STAYS here by decision: its members (four
// after the transport-command census retired the ignored-Result twins)
// interleave with the user-data channel's invalidation choreography, not
// with any family boundary. The one piece of shared state an extracted
// surface observes — the detail-cache cluster — moved to the
// [MediaRepositoryInternals] Koin single this ctor now takes, so the group
// stays ONE instance across the split. Declared divergence: the primary
// constructor became `internal` with that move (an internal-typed ctor
// parameter cannot ride a public constructor) — every constructor caller
// (the dataJvmModule Koin definitions + this module's test suites) is
// inside the module, and no external code names the concrete type.
class MediaRepositoryImpl internal constructor(
    /** The catalogue fetch family — every read this repo serves except the
     * four SyncPlay members below. */
    private val libraryApiClient: LibraryApiClient,
    /**
     * The collection family seam (the PlaylistApiClient over-the-impl
     * pattern): the four BoxSet reads/writes route through their own
     * narrow interface over the SAME client single the wide library seam
     * rides — the repo never sees the playlist or write verbs.
     */
    private val collectionApiClient: CollectionApiClient,
    /**
     * The home hot-path's cache-maintenance verbs (sub-call cache drop, the
     * dice roll's per-row drop + seed) — the narrow network-layer port beside
     * [com.raulshma.jellyplay.core.network.library.HomeSectionsFetcher], NOT
     * the wide [LibraryApiClient]: cache management is not API surface, so the
     * write/roll paths below depend on the port alone. Same underlying single
     * (the client impl adapts to the port), so ordering and epochs are exactly
     * what they were — see the roll protocol on [MediaRepository.rerollDiscoverRow].
     */
    private val homeSectionsCachePort: HomeSectionsCachePort,
    /** The SyncPlay group reads + queue push ([SyncPlayRepository]'s members). */
    private val syncPlayApiClient: SyncPlayApiClient,
    /**
     * The deep "home-sections snapshot store": the single owner of the
     * PERSISTED half of the home pipeline (the Room SWR snapshot — persist
     * dedup choreography, identity-scoped privacy clear, the two cold-open
     * reads). Injected (not constructed) so `getHomeSections`' persist hook,
     * `getCachedHomeSections` and `getOfflineHomeLayout` delegate to it, the
     * same shape as the [episodeCatalogue] delegation. The store depends on
     * the DAO + `HomeSession` + `TimeSource` only (never on
     * `MediaRepository`), so this edge does NOT form a DI cycle — both are
     * Koin singles in `core:data` and the constructor edge fixes the
     * direction. The IN-MEMORY half ([homeSectionsCache], the roll epoch,
     * the SWR layering) stays here.
     */
    private val homeSnapshotStore: HomeSectionsSnapshotStore,
    private val playedStateSync: PlayedStateSync,
    /**
     * The deep "Episode Catalogue": the single owner of the series
     * seasons/episodes snapshot. Injected (not constructed) so the repo's
     * `getSeasons`/`getEpisodes`/`getAllEpisodesGrouped` can delegate to it.
     * The catalogue depends on `LibraryApiClient` + `OfflineRepository` only
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
    /**
     * The facade split's ONE shared-state holder (see
     * [MediaRepositoryInternals]): owns the detail-cache cluster this repo
     * reads/evicts through, shared with [PlaylistRepositoryImpl] so a
     * playlist edit made through THAT surface invalidates a detail cached
     * through this one — one epoch-guarded instance, not two hand-synced
     * copies. A Koin single in dataJvmModule; both impls take the same one.
     */
    private val internals: MediaRepositoryInternals,
    /**
     * The deepened [SyncPlayRepository.createSyncPlayGroup]'s engine: the
     * process-wide SyncPlay facade owns the create→join-MY-group choreography
     * (bounded discovery/settle windows, duplicate-name disambiguation), so
     * the seam's create member delegates to it instead of firing a bare `New`
     * the caller had to recover by name. A Koin single in the same module —
     * no DI cycle (the manager never depends on a repository).
     */
    private val syncPlayManager: SyncPlayManager,
) : MediaRepository,
    // Family seams (the SonarrSeriesOperations over-the-impl pattern): the
    // same single carries the music-catalogue and user-data-write families
    // alongside the union — [MusicCatalogue] / [UserDataWriteOperations].
    MusicCatalogue,
    UserDataWriteOperations,
    SyncPlayRepository,
    MediaRepositoryCacheInvalidation,
    MediaCacheInvalidator {

    // The detail screen's item-scoped cache cluster — detail snapshot,
    // similar items (per limit), album tracks, theme songs — plus the one
    // epoch that guards their writes against invalidation. One semantic
    // unit, one owner: [DetailCacheGroup] (in MediaRepositoryInternals.kt
    // since the facade split) owns the key grammar and the eviction
    // choreography that used to be hand-synced at two sites per key family
    // inside this class. Reaches through the [internals] single — a get(),
    // not a captured reference, so the sharing stays visible at every use.
    private val detailCaches get() = internals.detailCaches

    private val libraryFoldersCache = TtlCache<List<LibraryFolder>>(ttlMs = FreshnessCeilings.FOLDERS_TTL_MS)
    private val genresCache = TtlCache<List<Genre>>(maxSize = 64, ttlMs = FreshnessCeilings.FOLDERS_TTL_MS)
    private val studiosCache = TtlCache<List<Studio>>(maxSize = 64, ttlMs = FreshnessCeilings.FOLDERS_TTL_MS)
    private val latestMediaCache = TtlCache<List<MediaItem>>(maxSize = 64, ttlMs = FreshnessCeilings.LATEST_MEDIA_TTL_MS)

    // Series-scoped seasons/episodes caches used to live here; they've moved
    // into [episodeCatalogue], the single owner of the series snapshot. The
    // similar/tracks/themes item-scoped caches moved into [detailCaches]
    // (they co-evict with the detail cache through one epoch); the
    // collection-items cache stays — a plain page-shaped cache with no
    // detail-epoch coupling.
    private val collectionItemsCache = TtlCache<SearchResult>(ttlMs = FreshnessCeilings.DETAIL_TTL_MS)

    // Child-photo URLs for a photo folder (player backdrop fan-out); declared
    // with the other caches so the identity registration in the init block
    // below can enumerate every cache in one place.
    private val photoFolderChildUrlCache = TtlCache<List<String>>(
        maxSize = 200,
        ttlMs = FreshnessCeilings.PHOTO_URLS_TTL_MS,
    )

    // Plan 08: private — the detail-cache group is repo-internal machinery;
    // reads that need freshness use getMediaDetail(force = true) and mutations/
    // invalidations run through the composite + per-type dispatch below, all
    // delegating to [detailCaches].

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
        // Prefix of [collectionItemsKey] for every page — one evict drops all
        // of a collection's cached pages. Trailing underscore so one id is
        // never a prefix of another's keys (collection_12 vs collection_123).
        collectionItemsCache.removeByKeyPrefix(homeSession.cacheIdentitySnapshot(), "collection_${collectionId}_")
    }

    private fun collectionItemsKey(collectionId: String, startIndex: Int, limit: Int) =
        "collection_${collectionId}_$startIndex" + "_$limit"

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
                detailCaches.invalidateItem(detail.item.id)
            }
            // SEASON mirrors EPISODE: the entry's season tree is the PARENT
            // series' catalogue (DetailContentResolver.loadSeriesData resolves
            // through seriesIdForDetail), so a forced refresh must drop that
            // catalogue — not the season's own (nonexistent) cache — or the
            // refresh silently serves the TTL snapshot.
            MediaType.EPISODE, MediaType.SEASON -> detail.item.seriesId?.let { invalidateSeriesCache(it) }
            MediaType.ALBUM -> invalidateUserDataCaches(detail.item.id)
            MediaType.COLLECTION -> invalidateCollectionItemsCache(detail.item.id)
            else -> Unit // plain item: caller-scoped invalidation already ran
        }
    }

    override fun invalidateForUserDataChange(itemId: String, seriesIdHint: String?) {
        // The two gap groups this user-data change cannot evict by key (see
        // [albumTracksStale] / [collectionItemsStale]): arm them alongside
        // the eviction below so the next read of either group heals itself.
        staleReadGroups.announceUserDataWrite()
        invalidateUserDataCaches(itemId, seriesIdHint)
    }

    // In-memory home-sections cache. Previously a hand-rolled triple of
    // @Volatile fields + a lock (cachedHomeSections / Timestamp / Key + lock);
    // folded into a single-entry TtlCache so the home path shares the same
    // identity-keyed primitive as every other cache here. Identity-keyed so a
    // user/server switch can't serve the previous identity's payload.
    // TTL comes from the shared home freshness policy (HomeFreshness); the
    // clock is the injected [timeSource]'s MONOTONIC read — TtlCache's
    // contract requires one, and the same fake drives the Room SWR ceiling's
    // wall-clock read. The PERSISTED half of the home pipeline (the Room SWR
    // snapshot its dedup/fetch choreography) lives on [homeSnapshotStore].
    private val homeSectionsCache = TtlCache<HomeSectionsResult>(
        maxSize = 1,
        ttlMs = HomeFreshness.REPO_MEMORY_TTL_MS,
        clock = { timeSource.nowElapsedRealtimeMillis() },
    )

    /**
     * The dice roll's stall guard for the repo's assembled-payload cache —
     * roll-protocol window 2 of 3; the bump-at-invalidate-AND-commit rule and
     * the full ordering live on [rerollDiscoverRow] (the protocol's single
     * owner). Read as the write guard in [getHomeSections]. Same idiom as
     * [MediaRepositoryInternals]' detail epoch.
     */
    private val discoverRollEpoch = java.util.concurrent.atomic.AtomicLong(0L)

    // Lazy staleness for the announced-user-data read groups (#157): the
    // eager eviction this replaces cleared caches at every user-data
    // mutation, forcing the NEXT read into a full blocking refetch even when
    // no consumer was alive to ask for fresh data. Each group inverts the
    // timing without losing the guarantee — an announce arms it, and the
    // next non-forced read of the group consumes it as a one-shot force
    // (the [StaleReadGroup.staleAwareRead] choreography). Same freshness as
    // the eager clear, zero refetches while nobody reads the group. Server
    // WS pushes do NOT arm them — same scope as the eager eviction they
    // replace — HomeRefresher serves those live.
    //
    // One owner for the whole ladder: [StaleReadGroups] is the registration
    // point (a 4th group is one `register` call, not an edit at every arm
    // site) and its two announce channels spell this repo's two group sets —
    // every user-data write/invalidation arms the gap-group riders below,
    // and only a CONFIRMED own-write announce arms home sections too (the
    // scroll-sensitive path must not pay a forced refetch for a write that
    // failed or never confirmed).
    private val staleReadGroups = StaleReadGroups()
    private val homeSectionsStale = staleReadGroups.register()
    // The two "gap" groups the composite user-data eviction cannot reach by
    // key: a TRACK flip evicts `tracks_<trackId>` but never the album's
    // `tracks_<albumId>` entry, and a collection MEMBER flip evicts
    // `detail_<itemId>` but never the collection's page keys — the flipped
    // item's own ids are all the eviction has, and the owning album's /
    // collection's identity is not cached anywhere in the reverse direction.
    // Registered as riders (`ridesUserDataWrite`) so every user-data write/
    // invalidation arms them alongside the eviction it already runs — the
    // next non-forced read of either group heals itself instead of waiting
    // for the manual force lever the detail hosts used to hand-thread.
    private val albumTracksStale = staleReadGroups.register(ridesUserDataWrite = true)
    private val collectionItemsStale = staleReadGroups.register(ridesUserDataWrite = true)


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
            // The group's registry contribution (today: the album-tracks
            // cache — the only member whose plain wholesale clear IS the
            // whole identity reaction; see DetailCacheGroup.registryCaches).
            *detailCaches.registryCaches.toTypedArray(),
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
            detailCaches.invalidateAll()
            // The #157 staleness markers are identity-scoped state in spirit
            // (they are armed by the PREVIOUS user's confirmed writes);
            // without this reset they survive the switch and force the next
            // user's first read of each group into one redundant refetch.
            staleReadGroups.resetAll()
            // Clear the PREVIOUS identity's persisted home-section SWR
            // rows — scoped, not wholesale, so a multi-account server
            // keeps the other users' snapshots for their next cold
            // open. Runs here (where the transition carries the
            // previous identity) rather than in invalidateCaches()
            // (which has no identity context). Null only on SignedIn,
            // which the registry excludes.
            transition.previousIdentity?.let { previous ->
                homeSnapshotStore.clearIdentity(previous.serverId, previous.userId)
            }
        }
    }

    override suspend fun getHomeSections(
        query: HomeSectionQuery,
        force: Boolean,
    ): Result<HomeSectionsResult> {
        val cacheKey = query.cacheKey()
        // Consume the #157 lazy staleness marker as a one-shot force (see
        // [homeSectionsStale]): an announced user-data write makes this read
        // bypass the cached payload — a fresh Continue Watching within the
        // TTL window, not after it. The consume/re-arm choreography (an
        // announce racing the fetch re-arms for the NEXT read; a failed or
        // cancelled consuming read re-arms) lives in
        // [StaleReadGroup.staleAwareRead].
        return homeSectionsStale.staleAwareRead(force) { effectiveForce ->
            homeSectionsCache.getOrFetch(
                { homeSession.cacheIdentity() },
                cacheKey,
                force = effectiveForce,
                // SWR persist: the fetch-path-only write hook — persists the
                // snapshot for stale-while-revalidate on cold open (the in-memory
                // cache is lost on process death) after the in-memory put, and
                // never on a cache hit, so a hit cannot slide the persisted row's
                // fetchedAt forward and defeat the 24h SWR staleness ceiling.
                // The choreography itself (dedup window, encode, upsert) lives
                // on the [homeSnapshotStore].
                onFetched = { homeSnapshotStore.persist(cacheKey, it) },
                currentEpoch = discoverRollEpoch::get,
            ) {
                // The query value object crosses the repo → network seam intact;
                // effectiveForce (not force) so a consumed staleness marker
                // bypasses the network layer's sub-call caches too, not just
                // the in-memory one.
                libraryApiClient.getHomeSections(query, effectiveForce)
            }
        }
    }

    override suspend fun getDiscoverRowItems(row: DiscoverRowConfig): Result<List<MediaItem>> =
        libraryApiClient.getDiscoverRowItems(row)

    override suspend fun refreshHomeSection(
        section: HomeSection,
        query: HomeSectionQuery,
        mergeNextUpIntoContinueWatching: Boolean,
        force: Boolean,
    ): Result<HomeSection?> =
        // Straight to the port (the network fetcher owns the per-type
        // mapping); no assembled-payload cache or SWR persist on this path —
        // see the interface KDoc for why a single-row result must bypass both.
        homeSectionsCachePort.refreshHomeSection(section, query, mergeNextUpIntoContinueWatching, force).also { result ->
            // Evict the assembled whole-plan payload on success: the pull just
            // changed one row server-side, and the in-memory entry (60s TTL)
            // still carries the pre-pull sections — the next NON-FORCED read
            // (the periodic loop's jittered tick can land inside the window)
            // would serve it and visibly repaint the stale row over the fresh
            // one. Same race window the dice roll closes, closed the same way:
            // the epoch bump stall-guards any FULL fetch in flight across the
            // clear — the refresher's mutex only serializes the feature
            // layer's own fetches, but TvWatchNextPublisher and
            // UserDataSyncWorker call getHomeSections directly, so their
            // assembled write can straddle the clear; the guard turns that
            // write into a return-but-don't-pin, and the next read refetches
            // instead of re-pinning the pre-pull payload. The SWR snapshot
            // persist stays untouched: the next FULL fetch re-persists it
            // complete. Failure keeps the entry — it is still accurate for
            // every row the pull didn't touch (same policy as a failed forced
            // read leaving the network sub-call memos).
            if (result.isSuccess) {
                discoverRollEpoch.incrementAndGet()
                homeSectionsCache.clear()
            }
        }

    override suspend fun rerollDiscoverRow(row: DiscoverRowConfig): Result<List<MediaItem>> {
        // The roll protocol's implementation: invalidate → fetch → seed. The
        // ordering contract, the three race windows and the epoch bump rule
        // are owned by the interface KDoc (MediaRepository.rerollDiscoverRow).
        invalidateDiscoverRowCache(row.id)
        val result = getDiscoverRowItems(row)
        // Commit only a real roll: seedDiscoverRowCache no-ops on an empty
        // list, so a failed/empty fetch leaves nothing behind but the
        // pre-fetch drop — the next home fetch re-queries the row instead of
        // replaying or pinning pre-roll items.
        result.onSuccess { seedDiscoverRowCache(row, it) }
        return result
    }

    /**
     * Reroll half 1 (see the roll protocol on
     * [MediaRepository.rerollDiscoverRow]): repo-epoch bump + network per-row
     * memo drop + assembled-payload drop (maxSize is 1, so the clear is
     * exactly the one cached payload — nothing else pays for the roll).
     */
    private fun invalidateDiscoverRowCache(rowId: String) {
        discoverRollEpoch.incrementAndGet()
        homeSectionsCachePort.invalidateDiscoverRow(rowId)
        homeSectionsCache.clear()
    }

    /**
     * Reroll half 3 (see the roll protocol on
     * [MediaRepository.rerollDiscoverRow]): network row-memo write + the
     * commit-time epoch bump (a fetch in flight across the whole roll stays
     * stall-guarded) + the assembled-payload drop again. Cheap (maxSize 1).
     * No-op on an empty list.
     */
    private fun seedDiscoverRowCache(row: DiscoverRowConfig, items: List<MediaItem>) {
        if (items.isEmpty()) return
        discoverRollEpoch.incrementAndGet()
        homeSectionsCachePort.seedDiscoverRow(row, items)
        homeSectionsCache.clear()
    }

    override suspend fun getCachedHomeSections(
        query: HomeSectionQuery,
    ): HomeSectionsResult? =
        // The persisted read's contracts (identity-source-flow read, the 24h
        // SWR staleness ceiling, the off-dispatch decode) live on the store.
        homeSnapshotStore.cached(query)

    override suspend fun getOfflineHomeLayout(): HomeSectionsResult? =
        // Key-agnostic latest row, no freshness ceiling — the contracts live
        // on the store.
        homeSnapshotStore.offlineLayout()

    override suspend fun getLibraryFolders(force: Boolean): Result<List<LibraryFolder>> =
        libraryFoldersCache.getOrFetch({ homeSession.cacheIdentity() }, "folders", force = force) {
            libraryApiClient.getLibraryFolders()
        }

    override suspend fun getLatestMedia(
        parentId: String,
        limit: Int,
    ): Result<List<MediaItem>> =
        latestMediaCache.getOrFetch({ homeSession.cacheIdentity() }, "latest_${parentId}_$limit") {
            libraryApiClient.getLatestMedia(parentId = parentId, limit = limit)
        }

    override suspend fun getMediaDetail(itemId: String, force: Boolean): Result<MediaDetail> =
        // Single-flight dedup, the force freshness lever, the epoch guard and
        // the cancellation ladder all live in [DetailCacheGroup.detail].
        detailCaches.detail(itemId, force)

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
            libraryApiClient.getMediaItems(
                parentId = null,
                filters = filters,
                studioIds = null,
                startIndex = startIndex,
                limit = limit,
                searchTerm = query,
            )
        } else {
            libraryApiClient.getSearchHints(
                query,
                filters.mediaTypes.takeIf { it.isNotEmpty() },
                limit,
                startIndex,
            )
        }
    }

    override suspend fun findItemByProviderId(provider: String, id: String): Result<String?> =
        libraryApiClient.findItemByProviderId(provider, id)

    override fun getMediaItemsPaged(
        parentId: String?,
        filters: LibraryFilters,
        studioIds: List<String>?,
        kindFilter: com.raulshma.jellyplay.core.model.ItemKindFilter,
    ): Flow<PagingData<MediaItem>> = pagedMediaPager {
        JellyfinPagingSource { startIndex, limit ->
            libraryApiClient.getMediaItems(
                parentId = parentId,
                filters = filters,
                studioIds = studioIds,
                startIndex = startIndex,
                limit = limit,
                kindFilter = kindFilter,
            )
        }
    }

    override fun searchPaged(
        query: String,
        filters: LibraryFilters,
    ): Flow<PagingData<MediaItem>> = pagedMediaPager {
        // The blank-query guard (no repository round-trip) lives in the factory.
        searchPagingSource(query = query, filters = filters)
    }

    override suspend fun getGenres(parentId: String?, force: Boolean): Result<List<Genre>> =
        genresCache.getOrFetch({ homeSession.cacheIdentity() }, "genres_${parentId ?: "root"}", force = force) {
            libraryApiClient.getGenres(parentId)
        }

    // No force lever: unlike getGenres, getStudios never grew the freshness
    // parameter, and the plain shape keeps exactly that behaviour.
    override suspend fun getStudios(parentId: String?): Result<List<Studio>> =
        studiosCache.getOrFetch({ homeSession.cacheIdentity() }, "studios_${parentId ?: "root"}") {
            libraryApiClient.getStudios(parentId)
        }

    override suspend fun getArtistAlbums(artistId: String, limit: Int): Result<List<MediaItem>> =
        libraryApiClient.getArtistAlbums(artistId, limit)

    override suspend fun getAlbumTracks(albumId: String, force: Boolean): Result<List<MediaItem>> =
        // Announced-staleness read (see [albumTracksStale]): a track flip
        // arms the marker because the composite user-data eviction only
        // drops the flipped item's OWN `tracks_<trackId>` key, never the
        // album's `tracks_<albumId>` entry — the next non-forced read
        // consumes the marker as the force that drops it, so the album's
        // track rows heal without a caller-side force. Epoch-guarded write —
        // see DetailCacheGroup's key grammar/KDoc.
        albumTracksStale.staleAwareRead(force) { effectiveForce ->
            detailCaches.albumTracks(albumId, effectiveForce)
        }

    override suspend fun getSimilarItems(itemId: String, limit: Int): Result<List<MediaItem>> =
        // Key includes the limit (DetailCacheGroup's key grammar) so a call
        // with a different limit doesn't serve a stale truncated list;
        // epoch-guarded write — see DetailCacheGroup's KDoc.
        detailCaches.similarItems(itemId, limit)

    override suspend fun getInstantMix(itemId: String, limit: Int): Result<List<MediaItem>> =
        libraryApiClient.getInstantMix(itemId, limit)

    override suspend fun getThemeSongs(itemId: String): Result<List<MediaItem>> =
        // Cached exactly like getSimilarItems: identity-keyed,
        // item-scoped key, 2-minute TTL, epoch-guarded write — evicted with
        // the detail's other per-item caches in DetailCacheGroup.invalidateItem.
        detailCaches.themeSongs(itemId)

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
        force: Boolean,
    ): Result<SearchResult> =
        // Announced-staleness read (see [collectionItemsStale]): a member
        // item's flip arms the marker because the composite user-data
        // eviction drops `detail_<itemId>` but never the collection's page
        // key — the next non-forced read consumes the marker as the force
        // that drops the collection's whole page family (the prefix evict
        // drops every page, so a future paginated caller healing page 2 also
        // heals the earlier pages).
        collectionItemsStale.staleAwareRead(force) { effectiveForce ->
            if (effectiveForce) invalidateCollectionItemsCache(collectionId)
            collectionItemsCache.getOrFetch(
                { homeSession.cacheIdentity() },
                collectionItemsKey(collectionId, startIndex, limit),
            ) {
                collectionApiClient.getCollectionItems(collectionId, startIndex, limit)
            }
        }

    override suspend fun getCollections(limit: Int): Result<List<CollectionSummary>> =
        // Not cached: the picker refetches on every open so a freshly-created
        // collection is immediately selectable without a cache-invalidation hop.
        collectionApiClient.getCollections(limit)

    override suspend fun createCollection(name: String, itemIds: List<String>): Result<String> =
        // Plan 08: collection edits self-invalidate — the detail screen used to
        // compensate with a manual invalidateCollectionItemsCache call.
        collectionApiClient.createCollection(name, itemIds)
            .onSuccess { invalidateCollectionItemsCache(it) }

    override suspend fun addItemsToCollection(collectionId: String, itemIds: List<String>): Result<Unit> =
        collectionApiClient.addItemsToCollection(collectionId, itemIds)
            .onSuccess { invalidateCollectionItemsCache(collectionId) }

    override fun getFavoritesPaged(
        mediaTypes: List<MediaType>?,
    ): Flow<PagingData<MediaItem>> = pagedMediaPager {
        JellyfinPagingSource { startIndex, limit ->
            libraryApiClient.getFavorites(
                mediaTypes = mediaTypes,
                limit = limit,
                startIndex = startIndex,
            )
        }
    }

    //  Facade split: the eight PlaylistRepository members that used to live
    // here moved to PlaylistRepositoryImpl (same package) over the narrow
    // LibraryApiClient family seam + the shared [MediaRepositoryInternals]
    // detail cluster. The six edits' self-invalidation
    // (detailCaches.invalidateItem per playlist edit) is unchanged — it now
    // runs in the extracted impl against the SAME single-backed group.

    //  Facade split, second wave: the nine uncached browse-read members moved
    // to [MediaUncachedReadsImpl] (same package) over the same
    // [LibraryApiClient] — getIntros/getSpecialFeatures (MediaExtrasReads),
    // getPeople/getItemsByPerson/getTags (MediaBrowseReads),
    // getMediaItems/getFavorites/getSearchSuggestions (MediaCollectionReads) —
    // every one a stateless forward this class cached nothing for (zero TtlCache
    // involvement), so nothing shared stayed behind. getItemsByStudio retired
    // outright: zero repo-typed callers. The two paged projections that routed
    // through two of those forwards (getMediaItemsPaged / getFavoritesPaged)
    // stay here and call the client directly — same named arguments, so the
    // wire calls are unchanged.

    override suspend fun getSyncPlayGroups(): Result<List<SyncPlayGroup>> =
        syncPlayApiClient.getSyncPlayGroups()

    // The deepened create: the manager owns create→join MY group (bounded
    // discovery/settle windows, duplicate-name disambiguation — see its KDoc);
    // this seam hands the caller the joined group's identifying slice (id +
    // name — all any UI consumer needs).
    override suspend fun createSyncPlayGroup(groupName: String): Result<SyncPlayGroupInfo> =
        syncPlayManager.createGroup(groupName).map { group ->
            SyncPlayGroupInfo(groupId = group.groupId, groupName = group.groupName)
        }

    override suspend fun getSyncPlayInfo(groupId: String?): Result<SyncPlayGroupInfo> =
        syncPlayApiClient.getSyncPlayInfo(groupId)

    // Transport commands (pause/unpause/seek/stop/setRepeat/setShuffle/
    // setIgnoreWait) used to be one-line pass-throughs here; the second wire
    // census retired them from the seam — their only repository-typed caller
    // (SyncPlayViewModel) ignored the Result and now rides SyncPlaySession →
    // SyncPlayController.safe(). setNewQueue stays: WatchPartyActions awaits
    // and inspects its Result.

    override suspend fun syncPlaySetNewQueue(
        itemIds: List<String>,
        playingItemId: String,
        mediaSourceId: String?,
        startPositionTicks: Long,
    ): Result<Unit> =
        syncPlayApiClient.syncPlaySetNewQueue(itemIds, playingItemId, mediaSourceId, startPositionTicks)

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
        // The confirmed write also arms the lazy staleness markers (#157 —
        // the [staleReadGroups] registrations): the next read of each
        // announced-stale group refetches even if no consumer was collecting
        // when this landed (VM off the back stack / recreated cold within
        // the TTL). The announce names only item ids, so the gap groups
        // (album tracks, collection pages) arm coarsely — one marker per
        // group, never per item.
        staleReadGroups.announceConfirmedWrite()
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
        // the caller-supplied seriesIdHint is load-bearing here. The flip also
        // announces the seriesId: detail screens are keyed by the series, so a
        // seasonId-only announcement would never match an open series screen.
        return withUserDataMutationCacheInvalidation(seriesId, seriesIdHint = seriesId) {
            playedStateSync.flip(seasonId, played = true, seriesId = seriesId)
        }
    }

    override suspend fun markSeasonUnplayed(seasonId: String, seriesId: String): Result<Unit> {
        return withUserDataMutationCacheInvalidation(seriesId, seriesIdHint = seriesId) {
            playedStateSync.flip(seasonId, played = false, seriesId = seriesId)
        }
    }

    /**
     * Evicts before and after a user-data write. The parent series id is
     * captured before the first eviction because that eviction removes the
     * cached detail needed to discover an episode's series; [seriesIdHint]
     * supplies it directly when the mutated id is not detail-cached at all
     * (e.g. season marks — seasons are never cached, so the caller names the
     * series).
     *
     * The home sections cache is deliberately NOT eagerly evicted here
     * (scroll- and flicker-sensitive: a cleared cache forces the next home
     * read into a full blocking refetch). Home freshness after a write is
     * event-driven instead: the online write paths emit a synthetic
     * [notifyUserDataChanged] whose consumers (HomeRefresher's throttled
     * silent forced refresh, open screens) heal live, and the announcement
     * also arms the lazy staleness marker ([homeSectionsStale], #157) so the
     * next home READ refetches even when no consumer was collecting. The
     * gap groups that cannot be evicted by key (album tracks, collection
     * pages — see [albumTracksStale] / [collectionItemsStale]) arm their
     * markers here, alongside the first eviction — arming is a user-data
     * statement, so it stays with this wrapper (and
     * [invalidateForUserDataChange]), not inside the eviction itself (which
     * [invalidateFor]'s ALBUM branch also reaches from forced detail reads).
     * The 60s TTL remains the staleness ceiling for the no-event path, and
     * [invalidateCaches] still clears the cache wholesale.
     */
    private suspend fun <T> withUserDataMutationCacheInvalidation(
        itemId: String,
        seriesIdHint: String? = null,
        mutation: suspend () -> Result<T>,
    ): Result<T> {
        val seriesId = seriesIdHint ?: cachedSeriesId(itemId)
        // Arm the gap-group markers BEFORE the mutation, matching the
        // pre-eviction this wrapper already runs: the marker records "a
        // user-data write is in flight", which is true even if the write
        // then fails (the pre-eviction already dropped the item's own keys).
        staleReadGroups.announceUserDataWrite()
        invalidateUserDataCaches(itemId, seriesId)
        return try {
            mutation()
        } finally {
            // The second eviction closes the race where a fetch started after
            // the pre-write eviction observed the old server state.
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
        // The group's composite per-item eviction returns the cached detail
        // read BEFORE the drop, so the series-resolution below can still
        // discover whether this item belongs to a series (either is the
        // series or is an episode of one, discovered from the cached detail
        // or the caller-supplied [seriesIdHint] when the item itself is not
        // detail-cached, e.g. seasons).
        val cached = detailCaches.invalidateUserData(itemId)
        // Home "Latest in X" rows carry per-item UserData (played/favorite) but
        // are keyed by parent folder, not by itemId, so they can't be evicted
        // selectively — drop the whole (small, LRU-bounded) cache the way the
        // home-sections cache is dropped, so home/library rows reflect the write
        // instead of serving stale badges until the TTL expires.
        latestMediaCache.clear()
        // The network layer's own home hot-path caches (per-folder latest +
        // per-seed similar) carry the same per-item UserData — drop them too,
        // or a home fetch within the sub-call TTL serves the pre-write rows.
        homeSectionsCachePort.invalidateSubcallCaches()
        // The gap groups the per-item eviction above cannot reach (see
        // [albumTracksStale] / [collectionItemsStale]): a track's or a
        // collection member's flip carries per-item UserData into the rows
        // its album/collection serves, but the eviction only knows the
        // flipped item's own keys. Arming lives with the USER-DATA callers
        // (the mutation wrapper and [invalidateForUserDataChange]), not here:
        // this eviction also serves [invalidateFor]'s ALBUM branch — a forced
        // detail read, no user-data write behind it — and a pull-to-refresh
        // must not arm markers (one redundant forced read of whichever
        // album/collection is read next, healing nothing). Coarse by design
        // (one marker per group, like the home marker): a flip on an
        // unrelated item costs at most one forced read of whichever
        // album/collection is read next.
        val seriesId = seriesIdHint
            ?: cached?.item?.seriesId
            ?: cached?.takeIf { it.item.mediaType == MediaType.SERIES }?.item?.id
        if (seriesId != null) invalidateSeriesCache(seriesId)
    }

    private fun cachedSeriesId(itemId: String): String? {
        val cached = detailCaches.cachedDetail(itemId) ?: return null
        return cached.item.seriesId
            ?: cached.takeIf { it.item.mediaType == MediaType.SERIES }?.item?.id
    }

    //  Facade split: the fifteen LiveTvRepository members that used to live
    // here moved to LiveTvRepositoryImpl (same package) over the narrow
    // LiveTvApiClient/MediaInfoApiClient family seams — every one was a
    // stateless forward, so nothing shared stayed behind.

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
        // The group's wholesale drop: epoch bump + detail/similar/themes
        // (the identity-path reaction) + album tracks (which the identity
        // path clears via the registry's cache list instead).
        detailCaches.clearAll()
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
        collectionItemsCache.clear()
        photoFolderChildUrlCache.clear()
        // The network-layer home hot-path caches (per-folder latest + per-seed
        // similar) are likewise identity-keyed; they are dropped here because
        // their entries carry per-item UserData that a wholesale drop must not
        // resurrect for the sub-call TTL.
        homeSectionsCachePort.invalidateSubcallCaches()
        // NOTE: the persistent home-section SWR snapshot is intentionally NOT
        // cleared here. invalidateCaches() doesn't know which (server, user)
        // it's running for — it's called both from the registry's identity action (which
        // has the previous identity in hand) and from background sync workers
        // (which have no identity context). Clearing wholesale here would wipe
        // every user's snapshot on any sync, defeating the multi-account SWR
        // benefit. The registry action clears the previous identity's rows
        // directly via homeSnapshotStore.clearIdentity() — see the init block above.
    }

    //  Facade split: the three NewsletterRepository members that used to live
    // here moved to NewsletterRepositoryImpl (same package) over the narrow
    // MediaInfoApiClient family seam (one-line forwards, nothing shared).

    companion object {
        /**
         * Buffer for [syntheticUserDataChanges]: large enough that a drain of
         * dozens of flips never suspends or drops wholesale, small enough to
         * be pointless to tune. DROP_OLDEST keeps the tryEmit non-suspending.
         */
        private const val SYNTHETIC_CHANGES_BUFFER = 64

        // The cache TTLs this repository used to declare as private literals
        // (FOLDERS_CACHE_TTL_MS 10 min, LATEST_CACHE_TTL_MS 2 min) now cite
        // the named policies FreshnessCeilings.FOLDERS_TTL_MS /
        // LATEST_MEDIA_TTL_MS / PHOTO_URLS_TTL_MS (plus DETAIL_TTL_MS via
        // MediaRepositoryInternals) at their construction sites above — same
        // values, one readable answer for "what is stale where".

        // The home-persist dedup window (HOME_PERSIST_DEDUP_WINDOW_MS, 60s)
        // moved with the persisted half of the home pipeline into
        // HomeSectionsSnapshotStore's companion.
    }

    override suspend fun getPhotoFolderChildImageUrls(folderId: String, limit: Int): List<String> =
        // The one non-Result fetch, riding the Result-shaped seam — the cache
        // module deliberately has no fourth (non-Result) shape. The wrap is
        // pure shape: if the call throws, the lambda unwinds before any Result
        // exists (nothing is cached, the exception propagates), so the
        // trailing getOrThrow can only ever unwrap a stored success.
        photoFolderChildUrlCache.getOrFetch({ homeSession.cacheIdentity() }, folderId) {
            Result.success(libraryApiClient.getChildItemImageUrls(folderId, limit))
        }.getOrThrow()
}
