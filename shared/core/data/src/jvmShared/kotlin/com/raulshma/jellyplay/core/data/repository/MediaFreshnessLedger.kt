package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogue
import com.raulshma.jellyplay.core.data.concurrency.SingleFlightFetcher
import com.raulshma.jellyplay.core.data.concurrency.StaleReadGroup
import com.raulshma.jellyplay.core.data.concurrency.StaleReadGroups
import com.raulshma.jellyplay.core.data.session.HomeSession
import com.raulshma.jellyplay.core.data.session.SessionCacheRegistry
import com.raulshma.jellyplay.core.data.session.SessionScopedCache
import com.raulshma.jellyplay.core.model.CacheIdentity
import com.raulshma.jellyplay.core.model.FreshnessCeilings
import com.raulshma.jellyplay.core.model.Genre
import com.raulshma.jellyplay.core.model.HomeFreshness
import com.raulshma.jellyplay.core.model.HomeSectionsResult
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.SearchResult
import com.raulshma.jellyplay.core.model.Studio
import com.raulshma.jellyplay.core.model.TimeSource
import com.raulshma.jellyplay.core.model.TtlCache
import com.raulshma.jellyplay.core.network.library.HomeSectionsCachePort

/**
 * The media freshness ledger — ONE owner of the freshness/cache chassis the
 * media-repository family serves through: the simple-cache spec registry
 (every concept declares one [MediaCacheSpec] and joins its group ladders by
 * iteration), the announced-staleness read groups (#157), the home
 * assembled-payload cache + its write-generation funnel, the
 * identity-transition registration (registry caches + the previous identity's
 * persisted SWR clear), and the eviction ladders (the composite user-data
 * eviction, the per-type [invalidateFor] table, the wholesale
 * [invalidateCaches] drop).
 *
 * Extracted verbatim from [MediaRepositoryImpl] (the
 * [MediaRepositoryInternals] / [HomeSectionsSnapshotStore] deep-module
 * pattern): the family-seam impl keeps the reads/writes it forwards — every
 * member is a one-liner over the ledger plus its own client seams — and this
 * class answers for WHEN a cache is stale and WHO clears it. The outward
 * invalidation faces ([MediaRepositoryCacheInvalidation],
 * [MediaCacheInvalidator]) stay on the impl and forward here.
 *
 * Construction: a Koin single ctor-injected into the impl (the
 * [HomeSectionsSnapshotStore] precedent — declared before its one consumer),
 * so the registrations below happen at the same graph point the impl's
 * construction used to run them (after the [MediaRepositoryInternals] detail
 * cluster's own chassis registrations, before any transition can fire).
 */
internal class MediaFreshnessLedger(
    /**
     * The single owner of identity transitions (see [HomeSession]). Cache
     * keying goes through [HomeSession.cacheIdentity] (the suspend
     * source-flow read — the mirror lags a switch by a dispatch, which would
     * key fetches under the previous identity) and best-effort evictions
     * through [HomeSession.cacheIdentitySnapshot].
     */
    private val homeSession: HomeSession,
    /**
     * The single home for identity reactions (see [SessionCacheRegistry]).
     * This ledger registers its TtlCaches for wholesale clears and one action
     * for the previous identity's persisted home-section SWR clear — no
     * bespoke collector or long-lived scope of its own.
     */
    private val sessionCacheRegistry: SessionCacheRegistry,
    /**
     * The deep "home-sections snapshot store": the single owner of the
     * PERSISTED half of the home pipeline. Reached at the fetch path (the SWR
     * persist rides the flight body, so a hit, a flight join and a locked
     * re-read never slide the persisted snapshot's fetchedAt forward) and at
     * the identity action (the previous identity's privacy clear).
     */
    private val homeSnapshotStore: HomeSectionsSnapshotStore,
    /**
     * The single owner of the series seasons/episodes snapshot: the eviction
     * ladders drop a series' snapshot through it (the catalogue is the read
     * surface's own dependency — no repository family carries series reads).
     */
    private val episodeCatalogue: EpisodeCatalogue,
    /**
     * The facade split's ONE shared-state holder (see
     * [MediaRepositoryInternals]): the composite eviction and the per-type
     * table reach the detail-cache cluster through it — the same
     * epoch-guarded group the family impls' detail reads serve from.
     */
    private val internals: MediaRepositoryInternals,
    /**
     * The home hot-path's cache-maintenance port: the eviction ladders drop
     * the network layer's own home sub-call caches through it
     * ([HomeSectionsCachePort.invalidateSubcallCaches]).
     */
    private val homeSectionsCachePort: HomeSectionsCachePort,
    /**
     * Clock seam for the freshness gates: the monotonic read drives the
     * in-memory [TtlCache] clocks (whose contract requires a monotonic
     * source — a wall-clock NTP jump would mass-expire or extend every
     * entry). Already a Koin single; injected so the ceilings are
     * unit-testable with one fake.
     */
    private val timeSource: TimeSource,
) {

    // The detail screen's item-scoped cache cluster — detail snapshot,
    // similar items (per limit), album tracks, theme songs — plus the one
    // epoch that guards their writes against invalidation. One semantic
    // unit, one owner: [DetailCacheGroup] owns the key grammar and the
    // eviction choreography. Reaches through the [internals] single — a
    // get(), not a captured reference, so the sharing stays visible at
    // every use.
    private val detailCaches get() = internals.detailCaches

    // ── The spec registry ──────────────────────────────────────────────────
    // Every simple identity-keyed cache this ledger serves, declared as a
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

    // Series-scoped seasons/episodes caches live in [episodeCatalogue], the
    // single owner of the series snapshot. The similar/tracks/themes
    // item-scoped caches live in [detailCaches] (they co-evict with the
    // detail cache through one epoch); the collection-items cache stays — a
    // plain page-shaped cache with no detail-epoch coupling. WHOLESALE-only:
    // its per-collection invalidation is the key-prefix evict in
    // [invalidateCollectionItems] and its read rides the
    // [collectionItemsStale] gap group — both bespoke, neither a group clear.
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

    // ── The stale-read ladder (#157) ───────────────────────────────────────

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
    // site) and its two announce channels spell this ledger's two group
    // sets — every user-data write/invalidation arms the gap-group riders
    // below, and only a CONFIRMED own-write announce arms home sections too
    // (the scroll-sensitive path must not pay a forced refetch for a write
    // that failed or never confirmed).
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

    // ── The home assembled-payload cache + its write-generation funnel ─────

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
     * THE ONE home cache-write generation token of THIS layer: a ledger-local
     * monotonic counter guarding the assembled-payload cache's writes in
     * [homeSections] (read by [homeSectionsFlight]'s stall guard), bumped
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
     * refreshHomeSection's comment (TvWatchNextPublisher, the
     * UserDataSyncWorker, the HomeRefresher's periodic tick). Overlapping
     * callers now join ONE flight, keyed on the section query's cacheKey;
     * the epoch is the SAME [homeWriteGeneration] the write funnel bumps, so
     * a roll/refresh invalidation stall-guards the flight's store exactly as
     * it guarded the raw path.
     */
    private val homeSectionsFlight = SingleFlightFetcher(homeSectionsCache, homeWriteGeneration)

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

    // ── Read engines (one per cache concept; the family impls forward) ─────

    /**
     * The assembled home payload: the #157 staleness consume (a marker makes
     * this read bypass the cached payload — a fresh Continue Watching within
     * the TTL window, not after it; the consume/re-arm choreography lives in
     * [StaleReadGroup.staleAwareRead]), force's evict-before-fetch nuance (a
     * failed forced fetch must leave nothing behind), the single-flight join
     * keyed on [cacheKey], and the SWR persist (the fetch-path-only write —
     * the flight body runs exactly once per flight, so a hit, a flight join,
     * and a locked re-read never slide the persisted snapshot's fetchedAt
     * forward; the in-memory cache is lost on process death, and the 24h SWR
     * staleness ceiling depends on that fetch-path-only discipline). The
     * fetch lambda receives the EFFECTIVE force (not the caller's manual
     * lever) so a consumed staleness marker bypasses the network layer's
     * sub-call caches too, not just the in-memory one.
     */
    suspend fun homeSections(
        cacheKey: String,
        force: Boolean,
        fetch: suspend (effectiveForce: Boolean) -> Result<HomeSectionsResult>,
    ): Result<HomeSectionsResult> = homeSectionsStale.staleAwareRead(force) { effectiveForce ->
        if (effectiveForce) {
            homeSectionsCache.remove(homeSession.cacheIdentity(), cacheKey)
        }
        homeSectionsFlight.getOrFetch(
            identity = { homeSession.cacheIdentity() },
            key = cacheKey,
            force = effectiveForce,
        ) { _ ->
            fetch(effectiveForce).onSuccess { homeSnapshotStore.persist(cacheKey, it) }
        }
    }

    /**
     * The ONLY writer of [homeWriteGeneration]: bump + assembled-payload drop
     * as one indivisible unit, so no call site can clear the cache without
     * stall-guarding the in-flight fetches racing it (or bump without
     * evicting). The row-mutation paths route through here AFTER the network
     * operation hands its outcome back — the single-row refresh on success,
     * the dice roll on every completed attempt (the roll mutates the row memo
     * inside the network layer on failure too: the memo is dropped even when
     * the fresh fetch failed, so the cached assembled payload — still
     * carrying the pre-roll row — must never survive to be replayed). The
     * wholesale drops that are NOT cache-write generations —
     * [invalidateCaches] and the identity transitions — clear
     * [homeSectionsCache] without this funnel: they void by identity, not by
     * ordering, exactly as before the funnel existed.
     */
    fun bumpHomeWriteGeneration() {
        homeWriteGeneration.incrementAndGet()
        homeSectionsCache.clear()
    }

    /** The folders read — identity-keyed, force freshness lever. */
    suspend fun libraryFolders(
        force: Boolean,
        fetch: suspend () -> Result<List<LibraryFolder>>,
    ): Result<List<LibraryFolder>> =
        libraryFoldersSpec.cache.getOrFetch("folders", force = force) { fetch() }

    /** The genres read — identity-keyed, force freshness lever. */
    suspend fun genres(
        parentId: String?,
        force: Boolean,
        fetch: suspend () -> Result<List<Genre>>,
    ): Result<List<Genre>> =
        genresSpec.cache.getOrFetch("genres_${parentId ?: "root"}", force = force) { fetch() }

    /** The studios read — identity-keyed; no force lever (never grew one). */
    suspend fun studios(
        parentId: String?,
        fetch: suspend () -> Result<List<Studio>>,
    ): Result<List<Studio>> =
        studiosSpec.cache.getOrFetch("studios_${parentId ?: "root"}") { fetch() }

    /** The filter sheets' tag facets — cached like its genres/studios siblings. */
    suspend fun tags(
        parentId: String?,
        fetch: suspend () -> Result<List<String>>,
    ): Result<List<String>> =
        tagsSpec.cache.getOrFetch("tags_${parentId ?: "root"}") { fetch() }

    /** The home "Latest in X" rows — the one USER_DATA-group member. */
    suspend fun latestMedia(
        parentId: String,
        limit: Int,
        fetch: suspend () -> Result<List<MediaItem>>,
    ): Result<List<MediaItem>> =
        latestMediaSpec.cache.getOrFetch("latest_${parentId}_$limit") { fetch() }

    /**
     * The one non-Result fetch, riding the Result-shaped seam — the cache
     * module deliberately has no fourth (non-Result) shape. The wrap is
     * pure shape: if the call throws, the lambda unwinds before any Result
     * exists (nothing is cached, the exception propagates), so the
     * trailing getOrThrow can only ever unwrap a stored success.
     */
    suspend fun photoFolderChildUrls(
        folderId: String,
        fetch: suspend () -> List<String>,
    ): List<String> =
        photoFolderChildUrlSpec.cache.getOrFetch(folderId) {
            Result.success(fetch())
        }.getOrThrow()

    /**
     * Announced-staleness read over the album-tracks gap group (see
     * [albumTracksStale]): a track flip arms the marker because the composite
     * user-data eviction only drops the flipped item's OWN `tracks_<trackId>`
     * key, never the album's `tracks_<albumId>` entry — the next non-forced
     * read consumes the marker as the force that drops it, so the album's
     * track rows heal without a caller-side force. [read] receives the
     * EFFECTIVE force; the cache behind it (the detail cluster's
     * epoch-guarded album-tracks member) stays the caller's.
     */
    suspend fun <T> albumTracks(
        force: Boolean,
        read: suspend (effectiveForce: Boolean) -> Result<T>,
    ): Result<T> = albumTracksStale.staleAwareRead(force) { effectiveForce -> read(effectiveForce) }

    /**
     * The collection page read: the announced-staleness consume (a member
     * item's flip arms the marker because the composite user-data eviction
     * drops `detail_<itemId>` but never the collection's page key — the next
     * non-forced read consumes the marker as the force that drops the
     * collection's whole page family; the prefix evict drops every page, so a
     * future paginated caller healing page 2 also heals the earlier pages),
     * then the page cache-through read.
     */
    suspend fun collectionPages(
        collectionId: String,
        startIndex: Int,
        limit: Int,
        force: Boolean,
        fetch: suspend () -> Result<SearchResult>,
    ): Result<SearchResult> = collectionItemsStale.staleAwareRead(force) { effectiveForce ->
        if (effectiveForce) invalidateCollectionItems(collectionId)
        collectionItemsSpec.cache.getOrFetch(
            collectionItemsKey(collectionId, startIndex, limit),
        ) {
            fetch()
        }
    }

    // ── User-data write bracket + eviction ladders ─────────────────────────

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
    suspend fun <T> withUserDataMutationCacheInvalidation(
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
     * The confirmed-write arming the announcer calls back into (DI wires the
     * hook): arms EVERY [staleReadGroups] registration — the next read of
     * each announced-stale group refetches even if no consumer was collecting
     * when the write landed (VM off the back stack / recreated cold within
     * the TTL). The announce names only item ids, so the gap groups (album
     * tracks, collection pages) arm coarsely — one marker per group, never
     * per item.
     */
    fun armStaleReadGroupsForConfirmedWrite() {
        staleReadGroups.announceConfirmedWrite()
    }

    /**
     * Single owner of the "what did this detail's type affect" mapping.
     * Absorbs the provider's old `invalidateByType` table and the
     * series-resolution rule that used to live in the interface KDoc: one
     * encoding, no caller-side re-derivation. Reached through the module-
     * internal [MediaRepositoryCacheInvalidation] seam.
     */
    fun invalidateFor(detail: MediaDetail) {
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
            MediaType.COLLECTION -> invalidateCollectionItems(detail.item.id)
            else -> Unit // plain item: caller-scoped invalidation already ran
        }
    }

    fun invalidateForUserDataChange(itemId: String, seriesIdHint: String? = null) {
        // The two gap groups this user-data change cannot evict by key (see
        // [albumTracksStale] / [collectionItemsStale]): arm them alongside
        // the eviction below so the next read of either group heals itself.
        staleReadGroups.announceUserDataWrite()
        invalidateUserDataCaches(itemId, seriesIdHint)
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

    /**
     * Wholesale in-memory cache drop (the [MediaCacheInvalidator] port is
     * its cross-module seam for the legacy sync workers). The only
     * production caller is the background user-data sync worker — the
     * `SessionCacheRegistry` identity path reacts via its own cache
     * registration + action instead (see the init block). Reads that need
     * freshness use the per-query force parameters instead.
     */
    suspend fun invalidateCaches() {
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

    // ── Private funnels ────────────────────────────────────────────────────

    // Funnels to the catalogue so the composite user-data eviction
    // (and the internal per-type dispatch) can drop a series' seasons/episodes
    // snapshot + epoch together. Private — seasons/episodes caches live in
    // [episodeCatalogue] and no external caller needs this knob.
    private fun invalidateSeriesCache(seriesId: String) {
        episodeCatalogue.invalidateSeries(seriesId)
    }

    // Collection edits self-invalidate through this knob (the family impl's
    // create/add forwards call it from their onSuccess arms); the forced page
    // read and [invalidateFor]'s COLLECTION branch reach it too.
    fun invalidateCollectionItems(collectionId: String) {
        // Prefix of [collectionItemsKey] for every page — one evict drops all
        // of a collection's cached pages. Trailing underscore so one id is
        // never a prefix of another's keys (collection_12 vs collection_123).
        collectionItemsSpec.cache.removeByKeyPrefix(homeSession.cacheIdentitySnapshot(), "collection_${collectionId}_")
    }

    private fun collectionItemsKey(collectionId: String, startIndex: Int, limit: Int) =
        "collection_${collectionId}_$startIndex" + "_$limit"

    private fun cachedSeriesId(itemId: String): String? {
        val cached = detailCaches.cachedDetail(itemId) ?: return null
        return cached.item.seriesId
            ?: cached.takeIf { it.item.mediaType == MediaType.SERIES }?.item?.id
    }
}

// ── The cache-spec vocabulary ─────────────────────────────────────────────
// File-private: the spec registry is ledger-internal machinery — no external
// caller needs the knobs, and the ladders stay the only
// writers of group semantics.

/**
 * The invalidation GROUPS a [MediaCacheSpec] can belong to — the taxonomy the
 * group ladders in [MediaFreshnessLedger] iterate. Membership is declared once,
 * at the spec, and every ladder that owns a group picks its members up by
 * iteration: a cache that joins a ladder declares the group instead of being
 * added to the ladder's field list.
 */
private enum class MediaCacheGroup {
    /**
     * The wholesale/identity set: cleared by
     * [MediaFreshnessLedger.invalidateCaches] (the background sync-worker drop)
     * and registered with the `SessionCacheRegistry` for identity
     * transitions. Registration IS this membership — identity-keying is
     * mandatory in this repository ("every cache is identity-keyed and
     * registered for wholesale identity clears"), so every spec carries
     * WHOLESALE and the init block spreads the registry's caches verbatim.
     */
    WHOLESALE,

    /**
     * Cleared wholesale by the composite user-data eviction
     * ([MediaFreshnessLedger.invalidateUserDataCaches]) beside its per-item
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
