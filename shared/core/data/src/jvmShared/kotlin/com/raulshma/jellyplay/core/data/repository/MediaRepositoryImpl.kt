package com.raulshma.jellyplay.core.data.repository

import androidx.paging.PagingData
import com.raulshma.jellyplay.core.data.paging.JellyfinPagingSource
import com.raulshma.jellyplay.core.data.paging.pagedMediaPager
import com.raulshma.jellyplay.core.data.paging.searchPagingSource
import com.raulshma.jellyplay.core.data.session.HomeSession
import com.raulshma.jellyplay.core.data.session.SessionCacheRegistry
import com.raulshma.jellyplay.core.data.session.SessionScopedCache
import com.raulshma.jellyplay.core.model.CacheIdentity
import com.raulshma.jellyplay.core.model.FreshnessCeilings
import com.raulshma.jellyplay.core.model.CollectionSummary
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
import com.raulshma.jellyplay.core.model.TimeSource
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.SearchResult
import com.raulshma.jellyplay.core.model.Studio
import com.raulshma.jellyplay.core.model.UserDataChange
import com.raulshma.jellyplay.core.network.api.LibraryApiClient
import com.raulshma.jellyplay.core.network.api.CollectionApiClient
import com.raulshma.jellyplay.core.network.library.HomeSectionsCachePort
import com.raulshma.jellyplay.core.network.realtime.UserDataRealtimeChannel
import com.raulshma.jellyplay.core.data.cache.getOrFetch
import com.raulshma.jellyplay.core.data.cache.getOrFetchGuarded
import com.raulshma.jellyplay.core.data.concurrency.SingleFlightFetcher
import com.raulshma.jellyplay.core.data.concurrency.StaleReadGroup
import com.raulshma.jellyplay.core.data.concurrency.StaleReadGroups
import kotlinx.coroutines.flow.Flow
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
// surfaces moved out to their own impls over the narrow API family clients
// (the PlaybackRepositoryImpl ctor precedent); the PlaylistRepositoryImpl
// split survives, the LiveTv / Newsletter pass-through mirrors later retired
// to their client families entirely. The one piece of shared state an
// extracted
// surface observes — the detail-cache cluster — moved to the
// [MediaRepositoryInternals] Koin single this ctor now takes, so the group
// stays ONE instance across the split. Declared divergence: the primary
// constructor became `internal` with that move (an internal-typed ctor
// parameter cannot ride a public constructor) — every constructor caller
// (the dataJvmModule Koin definitions + this module's test suites) is
// inside the module, and no external code names the concrete type.
class MediaRepositoryImpl internal constructor(
    /** The catalogue fetch family — every read this repo serves. */
    private val libraryApiClient: LibraryApiClient,
    /**
     * The collection family seam (the PlaylistApiClient over-the-impl
     * pattern): the four BoxSet reads/writes route through their own
     * narrow interface over the SAME client single the wide library seam
     * rides — the repo never sees the playlist or write verbs.
     */
    private val collectionApiClient: CollectionApiClient,
    /**
     * The home hot-path's cache-maintenance verb + the two COMPLETE
     * row-mutation operations (the dice roll's invalidate → fetch → seed, the
     * single-row refetch) — the narrow network-layer port beside
     * [com.raulshma.jellyplay.core.network.library.HomeSectionsFetcher], NOT
     * the wide [LibraryApiClient]: cache management is not API surface, so the
     * write/roll paths below depend on the port alone. The operations own the
     * network-side caches end-to-end (the fetcher bumps its own write guard
     * inside them); this repo answers only for its assembled-payload cache,
     * bumped when an operation reports a row changed — no token crosses the
     * seam. Same underlying single (the client impl adapts to the port), so
     * ordering and epochs are exactly what they were — see the roll protocol
     * on [HomeFeed.rerollDiscoverRow].
     */
    private val homeSectionsCachePort: HomeSectionsCachePort,
    /**
     * The deep "home-sections snapshot store": the single owner of the
     * PERSISTED half of the home pipeline (the Room SWR snapshot — persist
     * dedup choreography, identity-scoped privacy clear, the two cold-open
     * reads). Injected (not constructed) so `getHomeSections`' persist hook,
     * `getCachedHomeSections` and `getOfflineHomeLayout` delegate to it, the
     * same shape as the [episodeCatalogue] delegation. The store depends on
     * the DAO + `HomeSession` + `TimeSource` only (never on this class),
     * so this edge does NOT form a DI cycle — both are
     * Koin singles in `core:data` and the constructor edge fixes the
     * direction. The IN-MEMORY half ([homeSectionsCache], the cache-write
     * generation, the SWR layering) stays here.
     */
    private val homeSnapshotStore: HomeSectionsSnapshotStore,
    private val playedStateSync: PlayedStateSync,
    /**
     * The deep "Episode Catalogue": the single owner of the series
     * seasons/episodes snapshot. Injected (not constructed) so the repo's
     * invalidation paths can drop a series' snapshot through it
     * ([invalidateSeriesCache]/[invalidateFor]) — the catalogue is the
     * read surface's own dependency (its consumers inject
     * [EpisodeCatalogue] directly; no repository family carries series
     * reads).
     * The catalogue depends on `LibraryApiClient` + `OfflineRepository` only
     * (never on this class), so this edge does NOT form a DI cycle —
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
     * The synthetic-push fan-out ([UserDataAnnouncerImpl]): owns the shared
     * flow [userDataChanges] merges beside the WS channel and the
     * [RecentLocalUserDataWrites] record the refresher's echo demotion
     * consults. A leaf single, so the write paths UPSTREAM of this
     * repository's graph (PlayedStateSyncImpl, PlaybackRepositoryImpl) can
     * announce through it eagerly, with no lazy-deferral cycle-breaking
     * needed.
     */
    private val userDataAnnouncer: UserDataAnnouncer,
) :
    // The family seams over one impl (the SonarrSeriesOperations
    // over-the-impl pattern): the same single carries the music-catalogue
    // and user-data-write families, the home-feed family, the user-data
    // change feed, the library/browse family, the detail-read family, the
    // collection-verbs family, the series-notification and photo-folder
    // one-member seams — [MusicCatalogue] / [UserDataWriteOperations] /
    // [HomeFeed] / [UserDataChanges] / [LibraryFeed] / [DetailFeed] /
    // [CollectionOperations] / [SeriesNotificationReads] /
    // [PhotoFolderReads]. The `MediaRepository` union they used to be
    // dual-declared against is gone; consumers inject the families.
    MusicCatalogue,
    UserDataWriteOperations,
    HomeFeed,
    UserDataChanges,
    LibraryFeed,
    DetailFeed,
    CollectionOperations,
    SeriesNotificationReads,
    PhotoFolderReads,
    MediaRepositoryCacheInvalidation,
    MediaCatalogueEnrichments,
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

    // ── The spec registry ──────────────────────────────────────────────────
    // Every simple identity-keyed cache this class serves, declared as a
    // per-concept [MediaCacheSpec]: the [SessionScopedCache] chassis (the
    // TtlCache + epoch + single-flight + identity-registration ritual in one
    // instance — each spec self-registers its "media-*" identity action), the
    // named FreshnessCeilings policy it cites, and the [MediaCacheGroup]s
    // whose ladders clear it. The two remaining group ladders —
    // [invalidateCaches]' wholesale drop and the whole-cache arm of the
    // composite user-data eviction ([invalidateUserDataCaches]) — iterate
    // [cacheSpecs] by group instead of enumerating fields, so a concept joins
    // every ladder its groups name by declaring one spec here. (The identity
    // ladder needs no membership: each spec's chassis registers itself.) The
    // BESPOKE caches stay outside the registry on purpose: the home-sections
    // cache (its drops ride the home write-generation funnel), the detail
    // cluster ([detailCaches] — per-item evicts + one shared epoch across
    // members, keyed by the group's grammar, shared with the playlist surface
    // through [internals]) and the episode catalogue (a snapshot store whose
    // merge paths read/write the cache directly) each carry policy a group
    // clear cannot express.
    private val libraryFoldersSpec = MediaCacheSpec<List<LibraryFolder>>(
        owner = "media-folders",
        groups = setOf(MediaCacheGroup.WHOLESALE),
        ttlMs = FreshnessCeilings.FOLDERS_TTL_MS,
        registry = sessionCacheRegistry,
        identity = { homeSession.cacheIdentity() },
    )
    private val genresSpec = MediaCacheSpec<List<Genre>>(
        owner = "media-genres",
        groups = setOf(MediaCacheGroup.WHOLESALE),
        maxSize = 64,
        // Genres and studios share the folders ceiling — both near-static
        // catalogue metadata.
        ttlMs = FreshnessCeilings.FOLDERS_TTL_MS,
        registry = sessionCacheRegistry,
        identity = { homeSession.cacheIdentity() },
    )
    private val studiosSpec = MediaCacheSpec<List<Studio>>(
        owner = "media-studios",
        groups = setOf(MediaCacheGroup.WHOLESALE),
        maxSize = 64,
        ttlMs = FreshnessCeilings.FOLDERS_TTL_MS,
        registry = sessionCacheRegistry,
        identity = { homeSession.cacheIdentity() },
    )

    // The filter sheets' tag facets: the sorted distinct tag list the client
    // collects across the paged library scan. Genres/studios-shaped (same
    // near-static catalogue-metadata ceiling), and — because the underlying
    // read walks up to the scan cap's worth of items — the one cache that
    // keeps the filter sheets from re-paying the walk on every open.
    private val tagsSpec = MediaCacheSpec<List<String>>(
        owner = "media-tags",
        groups = setOf(MediaCacheGroup.WHOLESALE),
        maxSize = 64,
        ttlMs = FreshnessCeilings.FOLDERS_TTL_MS,
        registry = sessionCacheRegistry,
        identity = { homeSession.cacheIdentity() },
    )

    /**
     * The one USER_DATA-group member: home "Latest in X" rows carry per-item
     * UserData (played/favorite) but are keyed by parent folder, not by
     * itemId, so the composite user-data eviction cannot evict selectively —
     * it clears the whole (small, LRU-bounded) cache the way the
     * home-sections cache is dropped, so home/library rows reflect the write
     * instead of serving stale badges until the TTL expires.
     */
    private val latestMediaSpec = MediaCacheSpec<List<MediaItem>>(
        owner = "media-latest",
        groups = setOf(MediaCacheGroup.WHOLESALE, MediaCacheGroup.USER_DATA),
        maxSize = 64,
        ttlMs = FreshnessCeilings.LATEST_MEDIA_TTL_MS,
        registry = sessionCacheRegistry,
        identity = { homeSession.cacheIdentity() },
    )

    // Series-scoped seasons/episodes caches used to live here; they've moved
    // into [episodeCatalogue], the single owner of the series snapshot. The
    // similar/tracks/themes item-scoped caches moved into [detailCaches]
    // (they co-evict with the detail cache through one epoch); the
    // collection-items cache stays — a plain page-shaped cache with no
    // detail-epoch coupling. WHOLESALE-only: its per-collection invalidation
    // is the key-prefix evict in [invalidateCollectionItemsCache] and its
    // read rides the [collectionItemsStale] gap group — both bespoke,
    // neither a group clear.
    private val collectionItemsSpec = MediaCacheSpec<SearchResult>(
        owner = "media-collection-items",
        groups = setOf(MediaCacheGroup.WHOLESALE),
        ttlMs = FreshnessCeilings.DETAIL_TTL_MS,
        registry = sessionCacheRegistry,
        identity = { homeSession.cacheIdentity() },
    )

    // Child-photo URLs for a photo folder (player backdrop fan-out); declared
    // with the other specs so the identity registration in the init block
    // below can enumerate every cache in one place.
    private val photoFolderChildUrlSpec = MediaCacheSpec<List<String>>(
        owner = "media-photo-urls",
        groups = setOf(MediaCacheGroup.WHOLESALE),
        maxSize = 200,
        ttlMs = FreshnessCeilings.PHOTO_URLS_TTL_MS,
        registry = sessionCacheRegistry,
        identity = { homeSession.cacheIdentity() },
    )

    /**
     * The registry the group ladders iterate. Declared resequencing vs. the
     * former field enumerations (unobservable, the
     * [DetailCacheGroup.clearAll] precedent): the member caches are
     * independent (no cross-cache read exists) and every ladder still clears
     * each member exactly once.
     */
    private val cacheSpecs: List<MediaCacheSpec<*>> = listOf(
        libraryFoldersSpec,
        genresSpec,
        studiosSpec,
        tagsSpec,
        latestMediaSpec,
        collectionItemsSpec,
        photoFolderChildUrlSpec,
    )

    // Private — the detail-cache group is repo-internal machinery;
    // reads that need freshness use getMediaDetail(force = true) and mutations/
    // invalidations run through the composite + per-type dispatch below, all
    // delegating to [detailCaches].

    // Funnels to the catalogue so the composite user-data eviction
    // (and the internal per-type dispatch) can drop a series' seasons/episodes
    // snapshot + epoch together. Private — seasons/episodes caches now live in
    // [episodeCatalogue] and no external caller needs this knob.
    private fun invalidateSeriesCache(seriesId: String) {
        episodeCatalogue.invalidateSeries(seriesId)
    }

    // Private funnel — collection edits self-invalidate, so no
    // external caller needs this knob anymore.
    private fun invalidateCollectionItemsCache(collectionId: String) {
        // Prefix of [collectionItemsKey] for every page — one evict drops all
        // of a collection's cached pages. Trailing underscore so one id is
        // never a prefix of another's keys (collection_12 vs collection_123).
        collectionItemsSpec.cache.removeByKeyPrefix(homeSession.cacheIdentitySnapshot(), "collection_${collectionId}_")
    }

    private fun collectionItemsKey(collectionId: String, startIndex: Int, limit: Int) =
        "collection_${collectionId}_$startIndex" + "_$limit"

    /**
     * Single owner of the "what did this detail's type affect" mapping.
     * Absorbs the provider's old `invalidateByType` table and the
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
     * THE ONE home cache-write generation token of THIS layer: a repo-local
     * monotonic counter guarding the assembled-payload cache's writes in
     * [getHomeSections] (read by [homeSectionsFlight]'s stall guard), bumped
     * only by [bumpHomeWriteGeneration] — which the row-mutation paths run
     * when the network layer reports a row changed (the roll operation
     * returning, the single-row refresh succeeding). The network layer's
     * per-row discover memo keeps its OWN guard inside the fetcher's roll
     * operation — the token no longer crosses the module seam, so the two
     * guarded caches are synchronized by the hand-off ordering (bump AFTER
     * the operation that mutated the row) instead of a shared value. Same
     * idiom as [MediaRepositoryInternals]' detail epoch. (Named "generation"
     * to say what it owns — every home CACHE WRITE on this side — and to
     * leave "epoch" to the feature layer's identity-scoped roll generations,
     * a different axis.)
     */
    private val homeWriteGeneration = java.util.concurrent.atomic.AtomicLong(0L)

    /**
     * Single-flight wrapper over [homeSectionsCache]: the
     * assembled home payload is the most expensive fetch in the app (the
     * ~15-20-call fan-out), yet it rode the plain non-flight
     * [TtlCache.getOrFetch] whose own KDoc warns concurrent callers can
     * double-fetch — with three concurrent callers named in
     * [refreshHomeSection]'s comment (TvWatchNextPublisher, the
     * UserDataSyncWorker, the HomeRefresher's periodic tick). Overlapping
     * callers now join ONE flight, keyed on [HomeSectionQuery.cacheKey]; the
     * epoch is the SAME [homeWriteGeneration] the write funnel bumps, so a
     * roll/refresh invalidation stall-guards the flight's store exactly as
     * it guarded the raw path.
     */
    private val homeSectionsFlight = SingleFlightFetcher(homeSectionsCache, homeWriteGeneration)

    /**
     * The ONLY writer of [homeWriteGeneration]: bump + assembled-payload drop
     * as one indivisible unit, so no call site can clear the cache without
     * stall-guarding the in-flight fetches racing it (or bump without
     * evicting). The row-mutation paths route through here AFTER the network
     * operation hands its outcome back — [refreshHomeSection] on success, the
     * dice roll on every completed attempt (the roll mutates the row memo
     * inside the network layer on failure too: the memo is dropped even when
     * the fresh fetch failed, so the cached assembled payload — still
     * carrying the pre-roll row — must never survive to be replayed). The
     * wholesale drops that are NOT cache-write generations —
     * [invalidateCaches] and the identity transitions — clear
     * [homeSectionsCache] without this funnel: they void by identity, not by
     * ordering, exactly as before the funnel existed.
     */
    private fun bumpHomeWriteGeneration() {
        homeWriteGeneration.incrementAndGet()
        homeSectionsCache.clear()
    }

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
        // Identity transitions are handled by [SessionCacheRegistry], the
        // single subscriber of HomeSession.transitions, so no component keeps
        // its own last-identity mirror over separate server/user flows. This
        // closes a privacy + correctness gap: without it the previous user's
        // home sections / detail data could be served for up to 10 minutes
        // (the longest TTL) after `switchUser` or `switchServerAddress`.
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
        //
        // The spec caches and the detail cluster need no line here: each
        // [SessionScopedCache] chassis (one per [MediaCacheSpec], four in
        // [DetailCacheGroup]) registered its own identity action — epoch bump
        // + wholesale clear — at construction. Only the home-sections cache
        // still rides a hand-rolled registration (its drops deliberately do
        // NOT bump an epoch — see [bumpHomeWriteGeneration]'s KDoc: identity
        // transitions void by identity, not by ordering).
        sessionCacheRegistry.registerCaches("media", homeSectionsCache)
        // The action carries only the reactions a plain registry drop
        // cannot express: the #157 staleness markers and the previous
        // identity's persisted SWR rows. (The detail cache's epoch bump +
        // wholesale clear lives in its members' chassis registrations; the
        // episode catalogue's snapshot drop + epoch bump lives in its own —
        // routing through invalidateCaches() here would clear every cache
        // twice and double-bump the catalogue's epoch on each transition.)
        sessionCacheRegistry.registerAction("media-identity-clear") { transition ->
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
            // Force's evict-before-fetch nuance stays caller-owned (the
            // flight's force lever only bypasses reads): a failed forced
            // fetch must leave nothing behind, exactly as the raw
            // [TtlCache.getOrFetch] force shape did.
            if (effectiveForce) {
                homeSectionsCache.remove(homeSession.cacheIdentity(), cacheKey)
            }
            homeSectionsFlight.getOrFetch(
                identity = { homeSession.cacheIdentity() },
                key = cacheKey,
                force = effectiveForce,
            ) { _ ->
                // The query value object crosses the repo → network seam intact;
                // effectiveForce (not force) so a consumed staleness marker
                // bypasses the network layer's sub-call caches too, not just
                // the in-memory one.
                //
                // SWR persist: the fetch-path-only write — the flight body runs
                // exactly once per flight, so a hit, a flight join, and a
                // locked re-read never slide the persisted snapshot's
                // fetchedAt forward (the in-memory cache is lost on process
                // death; the 24h SWR staleness ceiling depends on that
                // fetch-path-only discipline). The choreography itself (dedup
                // window, encode, upsert) lives on the [homeSnapshotStore].
                libraryApiClient.getHomeSections(query, effectiveForce)
                    .onSuccess { homeSnapshotStore.persist(cacheKey, it) }
            }
        }
    }

    override suspend fun refreshHomeSection(
        section: HomeSection,
        query: HomeSectionQuery,
        mergeNextUpIntoContinueWatching: Boolean,
        force: Boolean,
    ): Result<HomeSection?> =
        // Straight to the port (the network fetcher owns the per-type
        // mapping AND the sub-call memo writes); no assembled-payload cache
        // or SWR persist on this path — see the interface KDoc for why a
        // single-row result must bypass both.
        homeSectionsCachePort.refreshHomeSection(section, query, mergeNextUpIntoContinueWatching, force).also { result ->
            // Evict the assembled whole-plan payload on success: the pull just
            // changed one row server-side, and the in-memory entry (60s TTL)
            // still carries the pre-pull sections — the next NON-FORCED read
            // (the periodic loop's jittered tick can land inside the window)
            // would serve it and visibly repaint the stale row over the fresh
            // one. Same race window the dice roll closes, closed the same way:
            // the funnel's generation bump stall-guards any FULL fetch in
            // flight across the clear — historically the refresher's mutex
            // only serialized the feature layer's own fetches, while
            // TvWatchNextPublisher and UserDataSyncWorker call getHomeSections
            // directly, so their assembled write can straddle the clear; the
            // guard (not the mutex — see [HomeFeed.getHomeSections]' write
            // guarantee) turns that write into a return-but-don't-pin, and the
            // next read refetches instead of re-pinning the pre-pull payload.
            // The bump rides THIS side only: no network row memo is touched
            // by it (the fetcher's own memos were refreshed by the pull
            // itself, behind the fetcher's own guards). The SWR snapshot
            // persist stays untouched: the next FULL fetch re-persists it
            // complete. Failure keeps the entry — it is still accurate for
            // every row the pull didn't touch (same policy as a failed forced
            // read leaving the network sub-call memos).
            if (result.isSuccess) {
                bumpHomeWriteGeneration()
            }
        }

    override suspend fun rerollDiscoverRow(row: DiscoverRowConfig): Result<List<MediaItem>> =
        // The roll protocol's repo half: the invalidate → fetch → seed
        // choreography over the row memo is ONE complete operation inside the
        // network layer — the fetcher owns the memo, its write guard, and the
        // ordering (see HomeSectionsCachePort.rerollDiscoverRow); this side
        // owns only the assembled-payload cache. Bump + drop AFTER the
        // operation returns, on EVERY completed attempt: the row memo changed
        // under the assembled payload either way (dropped, and on a real roll
        // re-seeded), so the cached payload — still carrying the pre-roll row
        // — must never survive to be replayed. The bump stall-guards any FULL
        // fetch in flight across the roll exactly as the refresh arm's does;
        // the ordering rules live on HomeFeed.rerollDiscoverRow.
        homeSectionsCachePort.rerollDiscoverRow(row).also {
            bumpHomeWriteGeneration()
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
        libraryFoldersSpec.cache.getOrFetch("folders", force = force) {
            libraryApiClient.getLibraryFolders()
        }

    override suspend fun getLatestMedia(
        parentId: String,
        limit: Int,
    ): Result<List<MediaItem>> =
        latestMediaSpec.cache.getOrFetch("latest_${parentId}_$limit") {
            libraryApiClient.getLatestMedia(parentId = parentId, limit = limit)
        }

    override suspend fun getSeriesNextUpEpisode(seriesId: String): Result<MediaItem?> =
        libraryApiClient.getNextUp(
            limit = 1,
            seriesId = seriesId,
            enableResumable = false,
        ).map { items -> items.firstOrNull { it.mediaType == MediaType.EPISODE } }

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
        genresSpec.cache.getOrFetch("genres_${parentId ?: "root"}", force = force) {
            libraryApiClient.getGenres(parentId)
        }

    // No force lever: unlike getGenres, getStudios never grew the freshness
    // parameter, and the plain shape keeps exactly that behaviour.
    override suspend fun getStudios(parentId: String?): Result<List<Studio>> =
        studiosSpec.cache.getOrFetch("studios_${parentId ?: "root"}") {
            libraryApiClient.getStudios(parentId)
        }

    override suspend fun getTags(parentId: String?): Result<List<String>> =
        // Cached like its genres/studios siblings: the client read walks the
        // library's items (TAGS-only projection, scan-capped) so the filter
        // sheets' tag list is complete — the cache keeps that walk to once
        // per ceiling window instead of once per sheet open.
        tagsSpec.cache.getOrFetch("tags_${parentId ?: "root"}") {
            libraryApiClient.getTags(parentId = parentId)
        }

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

    override suspend fun getThemeSongs(itemId: String): Result<List<MediaItem>> =
        // Cached exactly like getSimilarItems: identity-keyed,
        // item-scoped key, 2-minute TTL, epoch-guarded write — evicted with
        // the detail's other per-item caches in DetailCacheGroup.invalidateItem.
        detailCaches.themeSongs(itemId)

    override suspend fun getBoxSetsContaining(itemId: String): Result<List<CollectionSummary>> =
        // The detail screen's franchise row: the enumerate-then-probe fan-out
        // (one round-trip per boxset) is the costliest detail enrichment, so
        // it caches beside the detail's other per-item shapes — identity
        // keyed, single-flight, epoch-guarded write, and evicted per item by
        // DetailCacheGroup.invalidateItem (the user-data write funnel) under
        // the LONGER near-static catalogue ceiling. See the member's KDoc on
        // DetailCacheGroup for the declared TTL divergence.
        detailCaches.boxSetsContaining(itemId)

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
            collectionItemsSpec.cache.getOrFetch(
                collectionItemsKey(collectionId, startIndex, limit),
            ) {
                collectionApiClient.getCollectionItems(collectionId, startIndex, limit)
            }
        }

    override suspend fun createCollection(name: String, itemIds: List<String>): Result<String> =
        // Collection edits self-invalidate, so callers never invalidate by hand.
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

    //  Facade split: the nine uncached browse-read members left
    // the union outright — every one a stateless forward this class cached
    // nothing for (zero TtlCache involvement), so their consumers inject the
    // [LibraryApiClient] family single directly (getIntros/getSpecialFeatures,
    // getPeople/getItemsByPerson/getTags, getMediaItems/getFavorites/
    // getSearchSuggestions). getItemsByStudio retired from this union: zero
    // repo-typed callers (the LibraryApiClient member itself stays — the
    // home fetcher's pinned-studio rows inject the client directly). The two
    // paged projections that routed through two of
    // those forwards (getMediaItemsPaged / getFavoritesPaged) stay here and
    // call the client directly — same named arguments, so the wire calls are
    // unchanged. One member has since returned with a cache the stateless
    // forward never had: getTags (the filter sheets' walk-the-library scan,
    // the genres/studios-shaped [tagsSpec] above).

    /**
     * WS pushes merged with the announcer's synthetic pushes — local writers
     * that reach the server outside the socket's echo (notably the offline
     * outbox drain in PlaybackSyncWorker) feed the same consumer fan-out
     * (home forced refresh, open detail sessions) through the
     * [UserDataAnnouncer] leaf. The fan-out's owner is the announcer single;
     * this merge is the feed's one composition point.
     */
    override val userDataChanges: Flow<UserDataChange> =
        merge(userDataRealtimeChannel.changes, userDataAnnouncer.changes)

    /**
     * The confirmed-write arming the announcer calls back into (DI wires the
     * hook): arms EVERY [staleReadGroups] registration — the next read of
     * each announced-stale group refetches even if no consumer was collecting
     * when the write landed (VM off the back stack / recreated cold within
     * the TTL). The announce names only item ids, so the gap groups (album
     * tracks, collection pages) arm coarsely — one marker per group, never
     * per item.
     */
    internal fun armStaleReadGroupsForConfirmedWrite() {
        staleReadGroups.announceConfirmedWrite()
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

    /**
     * The outbox drain's deep write ([UserDataWriteOperations.recordDrainedPlays]):
     * the exact [markPlayed] per-item body, batched. Each flip keeps its
     * announce-on-delivery (PlayedStateSync.flip announces the confirmed write
     * from inside the online-success branch), so the drain-tail announce —
     * which runs through the [userDataAnnouncer] leaf directly — never names
     * a delivered derived flip twice (the drainer records the ids whose outbox
     * probe reports delivery as self-announced). No per-item catch here: the
     * flip's fallible collaborators are internally caught, so the only
     * throwable is cancellation, which must propagate (the drain's contract).
     */
    override suspend fun recordDrainedPlays(itemIds: List<String>) {
        for (itemId in itemIds) {
            withUserDataMutationCacheInvalidation(itemId) {
                playedStateSync.flip(itemId, played = true)
            }
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
     * Evicts after a user-data write. The parent series id is resolved before
     * the mutation because it is read from the cached detail that the eviction
     * removes; [seriesIdHint] supplies it directly when the mutated id is not
     * detail-cached at all (e.g. season marks — seasons are never cached, so
     * the caller names the series).
     *
     * ONE eviction, in the `finally`: the post-arm covers the write on BOTH
     * outcomes (success and failure), so no pre-arm eviction is needed. The
     * gap groups that cannot be evicted by key (album tracks, collection
     * pages - see [albumTracksStale] / [collectionItemsStale]) arm their
     * markers BEFORE the mutation — the marker records "a user-data write is in
     * flight", which is true even if the write then fails. Arming is a
     * user-data statement, so it stays with this wrapper (and
     * [invalidateForUserDataChange]), not inside the eviction itself (which
     * [invalidateFor]'s ALBUM branch also reaches from forced detail reads).
     *
     * The home sections cache is deliberately NOT eagerly evicted here
     * (scroll- and flicker-sensitive: a cleared cache forces the next home
     * read into a full blocking refetch). Home freshness after a write is
     * event-driven instead: the online write paths announce through the
     * [UserDataAnnouncer] leaf, whose consumers (HomeRefresher's throttled
     * silent forced refresh, open screens) heal live, and the announcement
     * also arms the lazy staleness marker ([homeSectionsStale], #157) so the
     * next home READ refetches even when no consumer was collecting. The 60s
     * TTL remains the staleness ceiling for the no-event path, and
     * [invalidateCaches] still clears the cache wholesale.
     */
    private suspend fun <T> withUserDataMutationCacheInvalidation(
        itemId: String,
        seriesIdHint: String? = null,
        mutation: suspend () -> Result<T>,
    ): Result<T> {
        val seriesId = seriesIdHint ?: cachedSeriesId(itemId)
        // Arm the gap-group markers BEFORE the mutation: the marker records
        // "a user-data write is in flight", which is true even if the write
        // then fails.
        staleReadGroups.announceUserDataWrite()
        return try {
            mutation()
        } finally {
            // The single eviction — closing both the post-write staleness and
            // the race where a fetch started before the write observed the old
            // server state (the race the former pre-write arm existed for; a
            // fetch that raced the WRITE ITSELF re-runs the eviction here).
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
        // The USER_DATA group's spec iteration — the whole-cache drops the
        // composite eviction runs beside its per-item detail eviction, for
        // concepts whose entries carry per-item UserData under non-item keys
        // (today: latest media — see [latestMediaSpec]). Membership is
        // declared at the spec, so the eviction grows with the group instead
        // of with this ladder.
        cacheSpecs.forEach { spec ->
            if (MediaCacheGroup.USER_DATA in spec.groups) spec.cache.invalidateAll()
        }
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

    //  Facade split: the fifteen LiveTv members that used to live here moved
    // out over the narrow LiveTvApiClient/MediaInfoApiClient family seams —
    // every one was a stateless forward, so nothing shared stayed behind (the
    // pass-through seam later retired to the client family entirely).

    /**
     * Wholesale in-memory cache drop (no repository family carries it — the
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
        // The WHOLESALE group's spec iteration — the simple caches drop by
        // group instead of a field enumeration. They hold user-scoped data
        // (library folders, latest media, genres, studios, collection pages,
        // photo folder child URLs) and are identity-keyed (a wrong identity
        // misses by construction), so this wholesale clear is the secondary
        // guard; the primary one is that a previous identity's key can never
        // match the current identity's key.
        cacheSpecs.forEach { spec ->
            if (MediaCacheGroup.WHOLESALE in spec.groups) spec.cache.invalidateAll()
        }
        // Seasons/episodes caches now live in [episodeCatalogue]; drop the
        // whole catalogue (every series snapshot + the long epoch) so a
        // wholesale invalidation behaves the same as before.
        episodeCatalogue.invalidateAll()
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

    // The three newsletter members delegate over the narrow MediaInfoApiClient
    // family seam (one-line forwards, nothing shared).

    companion object {
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
        photoFolderChildUrlSpec.cache.getOrFetch(folderId) {
            Result.success(libraryApiClient.getChildItemImageUrls(folderId, limit))
        }.getOrThrow()
}

// ── The cache-spec vocabulary ─────────────────────────────────────────────
// File-private: the spec registry is repo-internal machinery — no external
// caller needs the knobs, and the ladders stay the only
// writers of group semantics.

/**
 * The invalidation GROUPS a [MediaCacheSpec] can belong to — the taxonomy the
 * group ladders in [MediaRepositoryImpl] iterate. Membership is declared once,
 * at the spec, and every ladder that owns a group picks its members up by
 * iteration: a cache that joins a ladder declares the group instead of being
 * added to the ladder's field list.
 */
private enum class MediaCacheGroup {
    /**
     * The wholesale/identity set: cleared by
     * [MediaRepositoryImpl.invalidateCaches] (the background sync-worker drop)
     * and registered with the `SessionCacheRegistry` for identity
     * transitions. Registration IS this membership — identity-keying is
     * mandatory in this repository ("every cache is identity-keyed and
     * registered for wholesale identity clears"), so every spec carries
     * WHOLESALE and the init block spreads the registry's caches verbatim.
     */
    WHOLESALE,

    /**
     * Cleared wholesale by the composite user-data eviction
     * ([MediaRepositoryImpl.invalidateUserDataCaches]) beside its per-item
     * detail eviction: concepts whose entries carry per-item UserData but
     * whose keys are not item-scoped, so no per-item evict can reach them.
     */
    USER_DATA,
}

/**
 * One cacheable concept's spec: the [SessionScopedCache] instance that serves
 * it (owner [owner] — the registry slot its identity action registers under),
 * the named freshness policy it cites ([ttlMs] — every concept cites a
 * `FreshnessCeilings` constant, never an inline literal), and the
 * [MediaCacheGroup]s whose ladders clear it. Plain data plus one constructed
 * chassis; the cache-or-forward reads themselves stay on the chassis's
 * `getOrFetch` engine — the spec owns only where the cache lives and who
 * clears it, never how a read flows through it.
 */
private class MediaCacheSpec<V : Any>(
    owner: String,
    val groups: Set<MediaCacheGroup>,
    maxSize: Int = TtlCache.DEFAULT_MAX_SIZE,
    ttlMs: Long = TtlCache.DEFAULT_TTL_MS,
    registry: SessionCacheRegistry,
    identity: suspend () -> CacheIdentity,
) {
    val cache = SessionScopedCache<V>(
        owner = owner,
        maxSize = maxSize,
        ttlMs = ttlMs,
        registry = registry,
        identity = identity,
    )
}
