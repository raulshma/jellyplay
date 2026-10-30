package com.raulshma.jellyplay.core.data.repository

import androidx.paging.PagingData
import com.raulshma.jellyplay.core.data.paging.JellyfinPagingSource
import com.raulshma.jellyplay.core.data.paging.pagedMediaPager
import com.raulshma.jellyplay.core.data.paging.searchPagingSource
import com.raulshma.jellyplay.core.model.DiscoverRowConfig
import com.raulshma.jellyplay.core.model.CollectionSummary
import com.raulshma.jellyplay.core.model.Genre
import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.HomeSectionsResult
import com.raulshma.jellyplay.core.model.HomeSectionQuery
import com.raulshma.jellyplay.core.model.LibraryFilters
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.SearchResult
import com.raulshma.jellyplay.core.model.Studio
import com.raulshma.jellyplay.core.model.UserDataChange
import com.raulshma.jellyplay.core.network.api.LibraryApiClient
import com.raulshma.jellyplay.core.network.api.CollectionApiClient
import com.raulshma.jellyplay.core.network.api.ImageUrlApiClient
import com.raulshma.jellyplay.core.network.library.HomeSectionsCachePort
import com.raulshma.jellyplay.core.network.realtime.UserDataRealtimeChannel
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
//
//  Freshness-ledger split: the freshness/cache chassis (the simple-cache
// spec registry, the #157 stale-read ladder, the home assembled-payload
// cache + its write-generation funnel, the identity-transition registration
// and the eviction ladders) moved to the [MediaFreshnessLedger] Koin single
// this ctor takes — the [MediaRepositoryInternals] /
// [HomeSectionsSnapshotStore] deep-module pattern. What stays here is the
// family surface itself: every member is a forward over the ledger plus the
// client seams below, and the outward invalidation faces
// ([MediaRepositoryCacheInvalidation], [MediaCacheInvalidator]) forward
// verbatim.
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
     * The item-image-URL family seam (the [collectionApiClient] precedent):
     * the photo-folder cover probe's child-image row read routes through it —
     * the read moved off the wide client onto the family (its wire shape and
     * Primary-tag fold live beside the URL builders), so the repo injects the
     * narrow seam, never the wide library surface, for this one read.
     */
    private val imageUrlApiClient: ImageUrlApiClient,
    /**
     * The home hot-path's cache-maintenance verb + the two COMPLETE
     * row-mutation operations (the dice roll's invalidate → fetch → seed, the
     * single-row refetch) — the narrow network-layer port beside
     * [com.raulshma.jellyplay.core.network.library.HomeSectionsFetcher], NOT
     * the wide [LibraryApiClient]: cache management is not API surface, so the
     * write/roll paths below depend on the port alone. The operations own the
     * network-side caches end-to-end (the fetcher bumps its own write guard
     * inside them); the freshness ledger answers for the assembled-payload
     * cache, bumped when an operation reports a row changed — no token crosses
     * the seam. Same underlying single (the client impl adapts to the port),
     * so ordering and epochs are exactly what they were — see the roll protocol
     * on [HomeFeed.rerollDiscoverRow].
     */
    private val homeSectionsCachePort: HomeSectionsCachePort,
    /**
     * The deep "home-sections snapshot store": the single owner of the
     * PERSISTED half of the home pipeline (the Room SWR snapshot — persist
     * dedup choreography, identity-scoped privacy clear, the two cold-open
     * reads). Injected (not constructed) so `getCachedHomeSections` and
     * `getOfflineHomeLayout` delegate to it, the same shape as the
     * facade-split delegations; the fetch-path SWR persist rides the
     * [ledger]'s flight body (still exactly once per flight). The store
     * depends on the DAO + `HomeSession` + `TimeSource` only (never on this
     * class), so this edge does NOT form a DI cycle — both are Koin singles
     * in `core:data` and the constructor edge fixes the direction. The
     * IN-MEMORY half (the assembled-payload cache, the write generation, the
     * SWR layering) lives on the ledger.
     */
    private val homeSnapshotStore: HomeSectionsSnapshotStore,
    private val playedStateSync: PlayedStateSync,
    /**
     * Server-push user-data changes over the shared WebSocket. Re-exported
     * verbatim as [userDataChanges]; the channel's current-user filter means
     * the identity-switch observer above needs no involvement of its own.
     */
    private val userDataRealtimeChannel: UserDataRealtimeChannel,
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
    /**
     * The media freshness ledger (see [MediaFreshnessLedger]): the ONE owner
     * of the freshness/cache chassis — the spec registry, the #157 stale-read
     * ladder, the home write-generation funnel, the identity-transition clear
     * and the eviction ladders — extracted verbatim from this class. A Koin
     * single ctor-injected here (the [homeSnapshotStore] precedent);
     * every cache-minded member below forwards to it.
     */
    private val ledger: MediaFreshnessLedger,
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

    override suspend fun getHomeSections(
        query: HomeSectionQuery,
        force: Boolean,
    ): Result<HomeSectionsResult> {
        val cacheKey = query.cacheKey()
        // The staleness consume, force's evict-before-fetch nuance, the
        // single-flight join and the SWR persist (the fetch-path-only write)
        // live on the ledger. The query value object crosses the repo →
        // network seam intact; the ledger hands the fetch the EFFECTIVE force
        // so a consumed staleness marker bypasses the network layer's
        // sub-call caches too, not just the in-memory one.
        return ledger.homeSections(cacheKey, force) { effectiveForce ->
            libraryApiClient.getHomeSections(query, effectiveForce)
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
                ledger.bumpHomeWriteGeneration()
            }
        }

    override suspend fun rerollDiscoverRow(row: DiscoverRowConfig): Result<List<MediaItem>> =
        // The roll protocol's repo half: the invalidate → fetch → seed
        // choreography over the row memo is ONE complete operation inside the
        // network layer — the fetcher owns the memo, its write guard, and the
        // ordering (see HomeSectionsCachePort.rerollDiscoverRow); the ledger
        // owns only the assembled-payload cache. Bump + drop AFTER the
        // operation returns, on EVERY completed attempt: the row memo changed
        // under the assembled payload either way (dropped, and on a real roll
        // re-seeded), so the cached payload — still carrying the pre-roll row
        // — must never survive to be replayed. The bump stall-guards any FULL
        // fetch in flight across the roll exactly as the refresh arm's does;
        // the ordering rules live on HomeFeed.rerollDiscoverRow.
        homeSectionsCachePort.rerollDiscoverRow(row).also {
            ledger.bumpHomeWriteGeneration()
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
        ledger.libraryFolders(force) {
            libraryApiClient.getLibraryFolders()
        }

    override suspend fun getLatestMedia(
        parentId: String,
        limit: Int,
    ): Result<List<MediaItem>> =
        ledger.latestMedia(parentId, limit) {
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
        ledger.genres(parentId, force) {
            libraryApiClient.getGenres(parentId)
        }

    // No force lever: unlike getGenres, getStudios never grew the freshness
    // parameter, and the plain shape keeps exactly that behaviour.
    override suspend fun getStudios(parentId: String?): Result<List<Studio>> =
        ledger.studios(parentId) {
            libraryApiClient.getStudios(parentId)
        }

    override suspend fun getTags(parentId: String?): Result<List<String>> =
        // Cached like its genres/studios siblings: the client read walks the
        // library's items (TAGS-only projection, scan-capped) so the filter
        // sheets' tag list is complete — the cache keeps that walk to once
        // per ceiling window instead of once per sheet open.
        ledger.tags(parentId) {
            libraryApiClient.getTags(parentId = parentId)
        }

    override suspend fun getAlbumTracks(albumId: String, force: Boolean): Result<List<MediaItem>> =
        // Announced-staleness read (the album-tracks gap group, on the
        // ledger): a track flip arms the marker because the composite
        // user-data eviction only drops the flipped item's OWN
        // `tracks_<trackId>` key, never the album's `tracks_<albumId>` entry
        // — the next non-forced read consumes the marker as the force that
        // drops it, so the album's track rows heal without a caller-side
        // force. Epoch-guarded write — see DetailCacheGroup's key
        // grammar/KDoc.
        ledger.albumTracks(force) { effectiveForce ->
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
        // The collection page read: announced-staleness consume (the
        // collection-items gap group, on the ledger — a member item's flip
        // arms the marker the next non-forced read consumes as the force
        // that drops the collection's whole page family) + the page
        // cache-through read.
        ledger.collectionPages(collectionId, startIndex, limit, force) {
            collectionApiClient.getCollectionItems(collectionId, startIndex, limit)
        }

    override suspend fun createCollection(name: String, itemIds: List<String>): Result<String> =
        // Collection edits self-invalidate, so callers never invalidate by hand.
        collectionApiClient.createCollection(name, itemIds)
            .onSuccess { ledger.invalidateCollectionItems(it) }

    override suspend fun addItemsToCollection(collectionId: String, itemIds: List<String>): Result<Unit> =
        collectionApiClient.addItemsToCollection(collectionId, itemIds)
            .onSuccess { ledger.invalidateCollectionItems(collectionId) }

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
    // the genres/studios-shaped tag spec on the ledger).

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
     * hook); the ladder itself — which groups arm, and when — lives on the
     * ledger.
     */
    internal fun armStaleReadGroupsForConfirmedWrite() {
        ledger.armStaleReadGroupsForConfirmedWrite()
    }

    override suspend fun toggleFavorite(itemId: String): Result<Boolean> {
        // Fan-out (online API + best-effort offline mirror, or local apply +
        // outbox staging when offline / online call failed) is owned by
        // PlayedStateSync — the single home for the user-data write contract,
        // shared with PlaybackSyncWorker's reconciliation (favorite included).
        // The bracketing eviction choreography (gap-group arming before, the
        // composite eviction after, both outcomes) lives on the ledger.
        return ledger.withUserDataMutationCacheInvalidation(itemId) {
            playedStateSync.toggleFavorite(itemId)
        }
    }

    override suspend fun markPlayed(itemId: String): Result<Unit> {
        // Fan-out (online API + best-effort offline mirror, or local apply +
        // outbox staging when offline / online call failed) is owned by
        // PlayedStateSync — the single home for the played/resume-state write
        // contract, shared with PlaybackSyncWorker's reconciliation.
        return ledger.withUserDataMutationCacheInvalidation(itemId) {
            playedStateSync.flip(itemId, played = true)
        }
    }

    override suspend fun markUnplayed(itemId: String): Result<Unit> {
        return ledger.withUserDataMutationCacheInvalidation(itemId) {
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
            ledger.withUserDataMutationCacheInvalidation(itemId) {
                playedStateSync.flip(itemId, played = true)
            }
        }
    }

    override suspend fun markSeasonPlayed(seasonId: String, seriesId: String): Result<Unit> {
        // Season ids are never detail-cached, so the series-resolution inside
        // the ledger's mutation bracket cannot discover the parent —
        // the caller-supplied seriesIdHint is load-bearing here. The flip also
        // announces the seriesId: detail screens are keyed by the series, so a
        // seasonId-only announcement would never match an open series screen.
        return ledger.withUserDataMutationCacheInvalidation(seriesId, seriesIdHint = seriesId) {
            playedStateSync.flip(seasonId, played = true, seriesId = seriesId)
        }
    }

    override suspend fun markSeasonUnplayed(seasonId: String, seriesId: String): Result<Unit> {
        return ledger.withUserDataMutationCacheInvalidation(seriesId, seriesIdHint = seriesId) {
            playedStateSync.flip(seasonId, played = false, seriesId = seriesId)
        }
    }

    //  Facade split: the fifteen LiveTv members that used to live here moved
    // out over the narrow LiveTvApiClient/MediaInfoApiClient family seams —
    // every one was a stateless forward, so nothing shared stayed behind (the
    // pass-through seam later retired to the client family entirely).

    /**
     * Wholesale in-memory cache drop (no repository family carries it — the
     * [MediaCacheInvalidator] port is its cross-module seam for the legacy
     * sync workers). The ladder itself lives on the ledger.
     */
    override suspend fun invalidateCaches() {
        ledger.invalidateCaches()
    }

    /**
     * Single owner of the "what did this detail's type affect" mapping —
     * the table lives on the ledger; this is the
     * [MediaRepositoryCacheInvalidation] face over it.
     */
    override fun invalidateFor(detail: MediaDetail) {
        ledger.invalidateFor(detail)
    }

    override fun invalidateForUserDataChange(itemId: String, seriesIdHint: String?) {
        ledger.invalidateForUserDataChange(itemId, seriesIdHint)
    }

    // The three newsletter members delegate over the narrow MediaInfoApiClient
    // family seam (one-line forwards, nothing shared).

    companion object {
        // The cache TTLs this repository used to declare as private literals
        // (FOLDERS_CACHE_TTL_MS 10 min, LATEST_CACHE_TTL_MS 2 min) are cited
        // by the freshness ledger's spec registry at its construction sites —
        // the named policies FreshnessCeilings.FOLDERS_TTL_MS /
        // LATEST_MEDIA_TTL_MS / PHOTO_URLS_TTL_MS (plus DETAIL_TTL_MS via
        // MediaRepositoryInternals) — same values, one readable answer for
        // "what is stale where".

        // The home-persist dedup window (HOME_PERSIST_DEDUP_WINDOW_MS, 60s)
        // lives on HomeSectionsSnapshotStore's companion (the persisted half
        // of the home pipeline); the SWR persist itself rides the ledger's
        // flight body.
    }

    override suspend fun getPhotoFolderChildImageUrls(folderId: String, limit: Int): List<String> =
        // The Result-wrap shape discipline (nothing cached on a throw) lives
        // on the ledger's photo-folder read; the wire read + Primary-tag fold
        // ride the ImageUrlApiClient family seam.
        ledger.photoFolderChildUrls(folderId) {
            imageUrlApiClient.getChildItemImageUrls(folderId, limit)
        }
}
