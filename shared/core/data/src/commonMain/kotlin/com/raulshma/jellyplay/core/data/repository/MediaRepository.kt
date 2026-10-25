package com.raulshma.jellyplay.core.data.repository

import androidx.paging.PagingData
import com.raulshma.jellyplay.core.model.CollectionSummary
import com.raulshma.jellyplay.core.model.DiscoverRowConfig
import com.raulshma.jellyplay.core.model.Genre
import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.HomeSectionQuery
import com.raulshma.jellyplay.core.model.HomeSectionsResult
import com.raulshma.jellyplay.core.model.LibraryFilters
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.SearchResult
import com.raulshma.jellyplay.core.model.Studio
import com.raulshma.jellyplay.core.model.UserDataChange
import kotlinx.coroutines.flow.Flow

interface MediaRepository {

    // The home-feed family (the sections payload, the single-row edge-pull
    // refetch, the SWR snapshot reads and the discover-row verbs) lives on
    // [HomeFeed]; the user-data change feed's read val lives on
    // [UserDataChanges].

    /** Library folders. [force] bypasses the folders cache for this read. */
    suspend fun getLibraryFolders(force: Boolean = false): Result<List<LibraryFolder>>

    suspend fun getLatestMedia(
        parentId: String,
        limit: Int = 16,
    ): Result<List<MediaItem>>

    /**
     * Fetches the detail for [itemId]. Pass [force] to bypass the in-memory
     * detail cache for this read (the sanctioned freshness lever for
     * pull-to-refresh and re-fetch-after-write flows): the repository drops
     * the cached entry for [itemId] first, then fetches — exactly the
     * drop-then-read sequence callers used to run by hand.
     */
    suspend fun getMediaDetail(itemId: String, force: Boolean = false): Result<MediaDetail>

    suspend fun search(
        query: String,
        filters: LibraryFilters = LibraryFilters(),
        limit: Int = 50,
        startIndex: Int = 0,
    ): Result<SearchResult>

    fun getMediaItemsPaged(
        parentId: String? = null,
        filters: LibraryFilters = LibraryFilters(),
        studioIds: List<String>? = null,
        kindFilter: com.raulshma.jellyplay.core.model.ItemKindFilter = com.raulshma.jellyplay.core.model.ItemKindFilter.TOP_LEVEL,
    ): Flow<PagingData<MediaItem>>

    fun searchPaged(
        query: String,
        filters: LibraryFilters = LibraryFilters(),
    ): Flow<PagingData<MediaItem>>

    /** Genres for a library ([parentId], or server-wide when null). [force] bypasses the genres cache for this read. */
    suspend fun getGenres(parentId: String? = null, force: Boolean = false): Result<List<Genre>>

    suspend fun getStudios(parentId: String? = null): Result<List<Studio>>

    /**
     * [force] drops the cached track list first (the freshness lever the
     * album detail's deferred silent refresh needs: a track user-data flip
     * evicts `tracks_<trackId>`, never `tracks_<albumId>`, so the album's
     * cached list can only be superseded by an explicit force).
     *
     * The one music-catalogue read left on the union: the detail provider's
     * session is a mixed consumer (detail + catalogue in one resolve), so its
     * catalogue read rides [MediaRepository] — the family's clean consumers
     * inject [MusicCatalogue].
     */
    suspend fun getAlbumTracks(albumId: String, force: Boolean = false): Result<List<MediaItem>>

    suspend fun getSimilarItems(itemId: String, limit: Int = 12): Result<List<MediaItem>>

    /**
     * [force] drops every cached page for the collection first — the prefix
     * evict spans all pages, so forcing one page heals the earlier ones too
     * (the freshness lever the collection detail's deferred silent refresh
     * needs: a member item's user-data flip evicts `detail_<itemId>`, never
     * the collection's `collection_<collectionId>_<startIndex>_<limit>` page
     * key, so the post-flip rows can only be served by an explicit force).
     */
    suspend fun getCollectionItems(
        collectionId: String,
        startIndex: Int = 0,
        limit: Int = 50,
        force: Boolean = false,
    ): Result<SearchResult>

    /**
     * Creates a new collection seeded with the given item ids and returns the
     * new collection's id. Used by the detail screen's Create-Collection flow.
     * Remote-only.
     */
    suspend fun createCollection(name: String, itemIds: List<String> = emptyList()): Result<String>

    /**
     * Adds the given item ids to an existing collection. Used by the detail
     * screen's Add-to-Collection picker. Remote-only.
     */
    suspend fun addItemsToCollection(collectionId: String, itemIds: List<String>): Result<Unit>

    fun getFavoritesPaged(
        mediaTypes: List<MediaType>? = null,
    ): Flow<PagingData<MediaItem>>

    /**
     * Hot stream of server-side user-data changes (played / favorite flips from
     * any client, including this one). Collecting subscribes to the realtime
     * channel; cancelling the collector unsubscribes after a grace window — the
     * underlying socket survives (owned app-lifetime).
     */
    val userDataChanges: Flow<UserDataChange>

    /**
     * Emits [itemIds] into [userDataChanges] as a synthetic change, as if the
     * server had pushed them. For local writers whose mutation reached the
     * server through a path the WebSocket echo cannot cover (e.g. the offline
     * outbox drain, whose markPlayedItem the socket session may never see as
     * a UserDataChanged push) — the home refresher and open detail sessions
     * listen on the same flow and refresh exactly like they do for a server
     * push. No-op with no ids or before login.
     */
    fun notifyUserDataChanged(itemIds: List<String>)

    /**
     * The one user-data write left on the union: the offline outbox drainer
     * calls it alongside [notifyUserDataChanged] (its synthetic-push partner),
     * so it cannot narrow to [UserDataWriteOperations] alone yet. Its four
     * siblings moved to that seam — their sole union caller ([UserDataMutator])
     * injects the seam now.
     */
    suspend fun markPlayed(itemId: String): Result<Unit>

    suspend fun getPhotoFolderChildImageUrls(folderId: String, limit: Int = 4): List<String>
}

/**
 * The browse-catalogue enrichment family seam of [MediaRepository]: the
 * filter sheets' tag facet and the detail screen's franchise (BoxSets-
 * containing) row — two cached reads whose consumers are clean family
 * consumers (nothing else on the wide surface they need). [MediaRepositoryImpl]
 * implements this seam alongside the wide interface (the [MusicCatalogue]
 * pattern), so the surface ratchet stays flat instead of absorbing growth
 * that only two screens read.
 */
interface MediaCatalogueEnrichments {

    /**
     * The tag names feeding the filter sheets' tag facets ([parentId], or
     * server-wide when null). The underlying read pages the library's items
     * (a TAGS-only projection, scan-capped), so a tag past the first page
     * still surfaces; the sorted distinct list is cached under the same
     * near-static catalogue-metadata ceiling as genres/studios.
     */
    suspend fun getTags(parentId: String?): Result<List<String>>

    /**
     * The BoxSets whose hierarchy contains [itemId] — the detail screen's
     * "More in this collection" franchise row's sources. The underlying read
     * enumerates the user's boxsets and probes each for the item (one
     * round-trip per boxset, bounded-concurrent), so the result is
     * identity-keyed and TTL-cached beside the detail cluster: a detail
     * re-entry inside the window re-serves the row instead of re-probing.
     * A user-data write on the item evicts it with the item's other
     * detail-scoped caches.
     */
    suspend fun getBoxSetsContaining(itemId: String): Result<List<CollectionSummary>>
}

/**
 * The music-catalogue family seam of [MediaRepository]: the album → track
 * reads and the theme-song lookup — the cached members only music surfaces
 * ever call. [MediaRepositoryImpl] implements this seam alongside the wide
 * interface (the [SonarrSeriesOperations] over-the-impl pattern), so a
 * consumer narrows without a second repository instance or a family supertype
 * creeping back onto the union. The uncached catalogue reads
 * (getArtistAlbums / getInstantMix) retired from the seam to
 * [com.raulshma.jellyplay.core.network.api.LibraryApiClient] — their callers
 * inject the client family directly.
 *
 * Consumers: the migration is complete on this family's original caller
 * census — the audio playback stack (AudioLibraryBrowser / AudioQueueFacade /
 * ThemeMusicPlayer) and feature:music's artist/album/home ViewModels all
 * inject THIS seam for the cached catalogue reads, keeping [MediaRepository]
 * only where they are mixed consumers (detail reads, the user-data change
 * feed). [getAlbumTracks] is the one member that stays dual-declared: the
 * detail provider's session (UnifiedMediaDetailProviderImpl) resolves detail +
 * album tracks together and is a mixed consumer, so its read rides the union.
 * [getMusicVideos] retired from the seam outright: zero callers anywhere.
 */
interface MusicCatalogue {

    // getAlbumTracks also sits on [MediaRepository]: Kotlin forbids an
    // override whose two superinterfaces both declare default arguments,
    // so no member here carries one — seam-typed callers pass explicit
    // values.

    /**
     * [force] drops the cached track list first (the freshness lever the
     * album detail's deferred silent refresh needs: a track user-data flip
     * evicts `tracks_<trackId>`, never `tracks_<albumId>`, so the album's
     * cached list can only be superseded by an explicit force).
     */
    suspend fun getAlbumTracks(albumId: String, force: Boolean): Result<List<MediaItem>>

    suspend fun getThemeSongs(itemId: String): Result<List<MediaItem>>
}

/**
 * The series-notification family seam of the media stack (the
 * [MusicCatalogue] narrow-collaborator pattern): the per-series Next Up
 * diff read the `NewMediaCheckWorker`'s second pass rides. Its clean sole
 * consumer injects this seam instead of the union; the impl satisfies both
 * declarations.
 *
 * The series-scoped Next Up row: `GET /Shows/NextUp` filtered to [seriesId]
 * with resumable episodes excluded (a half-watched episode is not "new"),
 * top row only — null when the series has no next-up episode. Uncached by
 * contract: the notification diff wants the live answer, one call per
 * opted-in series per check.
 */
interface SeriesNotificationReads {
    suspend fun getSeriesNextUpEpisode(seriesId: String): Result<MediaItem?>
}

/**
 * The user-data WRITE family seam of [MediaRepository]: the five toggle/mark
 * endpoints every user-data mutation funnels through. Its clean sole
 * consumer is [UserDataMutatorImpl] — the protocol facade every feature
 * consumes — which now ctor-injects this seam instead of the 40-member
 * union. [markPlayed] additionally stays on [MediaRepository] (the offline
 * outbox drainer pairs it with the synthetic-push fan-out member), and the
 * impl's overrides satisfy both declarations.
 *
 * Not to be confused with [UserDataMutator]: THAT interface is the
 * caller-facing protocol (flip modes, containers, optimistic rewrites) built
 * ON TOP of this seam; this one is the repository family its write step
 * lands on.
 */
interface UserDataWriteOperations {

    suspend fun toggleFavorite(itemId: String): Result<Boolean>

    suspend fun markPlayed(itemId: String): Result<Unit>

    suspend fun markUnplayed(itemId: String): Result<Unit>

    /**
     * Marks every episode in [seasonId] played (Jellyfin's mark-played endpoint
     * recurses into a season's children). The repository cannot resolve a
     * season's parent series on its own — seasons are never detail-cached — so
     * the series screen, which knows both ids by construction, supplies
     * [seriesId]; the mutation then owns dropping the series' detail +
     * seasons/episodes caches itself (same double-evict contract as
     * [markPlayed]).
     */
    suspend fun markSeasonPlayed(seasonId: String, seriesId: String): Result<Unit>

    /** Unplayed mirror of [markSeasonPlayed]. */
    suspend fun markSeasonUnplayed(seasonId: String, seriesId: String): Result<Unit>
}

/**
 * The home-feed family seam of [MediaRepository]: the home screen's sections
 * payload and its row-scoped verbs — the batched sections fetch, the
 * single-row edge-pull refetch, the cold-open SWR snapshot reads, and the
 * custom-discover row's dice roll. [MediaRepositoryImpl]
 * implements this seam alongside the wide interface (the [MusicCatalogue]
 * over-the-impl pattern), so a home-only consumer narrows without a second
 * repository instance or a family supertype creeping back onto the union.
 *
 * Consumers: the home feature's refresh stack (HomeRefresher, its dice-roll
 * coordinator, the VM's offline-layout mirror) injects this seam alone; the
 * background home-sections refetchers (TvWatchNextPublisher,
 * UserDataSyncWorker, the library recommendations widget worker) and the
 * discover-row editor VM inject it beside the wide interface (or the
 * collection-read seam) for their non-home members.
 */
interface HomeFeed {

    // No member here carries a default argument — the family-seam rule
    // recorded on [MusicCatalogue]: an override whose two superinterfaces
    // both declare default arguments is forbidden, so seam-typed callers
    // pass explicit values.

    /**
     * The home screen's section payload. Pass [force] to bypass the in-memory
     * home-sections cache for this read (manual refresh / pull-to-refresh —
     * the sanctioned freshness lever; narrower than a global cache drop).
     *
     * WRITE GUARANTEE (all callers, no exceptions): every assembled payload
     * this read produces is written behind the pipeline's ONE
     * [com.raulshma.jellyplay.core.network.library.HomeWriteGeneration] token
     * — owned and bumped only by the data layer's funnel, mirrored by the
     * network fetcher's row memo — so a fetch already on the wire when a roll
     * or single-row refresh lands still returns to its caller but never pins
     * its pre-roll payload into either cache. No caller therefore needs
     * external serialization for cache safety (the background refetchers —
     * TvWatchNextPublisher, UserDataSyncWorker, the library recommendations
     * widget worker — call this directly, unmutexed).
     */
    suspend fun getHomeSections(
        query: HomeSectionQuery,
        force: Boolean,
    ): Result<HomeSectionsResult>

    /**
     * The single-row home refetch (the home screen's edge-pull refresh):
     * re-runs exactly the sub-call(s) the batch fetch runs for [section]'s
     * row and returns the fresh row with its identity preserved — see
     * `HomeSectionsFetcher.refreshSection` (the network layer's single owner
     * of the mapping) for the per-type contract.
     *
     * Outcome contract:
     *  - `Result.success(section)` — swap the row in place.
     *  - `Result.success(null)` — the row's source returned no items; the row
     *    should drop (the batch assembler's zero-items policy).
     *  - `Result.failure` — keep the stale row; the caller's policy.
     *
     * [mergeNextUpIntoContinueWatching] mirrors the batch merge
     * (OrderHomeSectionsUseCase): a CONTINUE_WATCHING row's refetch then
     * rebuilds the fold from both fresh sources, so a pull on the merged row
     * cannot swap away (or drop) its Next Up half.
     *
     * Deliberately NOT routed through [getHomeSections]' cache choreography:
     * a single-row result never enters the assembled-payload TtlCache nor the
     * SWR snapshot persist (`getLatestForIdentity` picks snapshots by recency,
     * so a partial snapshot would hijack the offline home's layout mirror).
     * The network sub-call memos ARE bypassed on read and written on success,
     * so the next periodic fetch serves the fresh rows.
     */
    suspend fun refreshHomeSection(
        section: HomeSection,
        query: HomeSectionQuery,
        mergeNextUpIntoContinueWatching: Boolean,
        force: Boolean,
    ): Result<HomeSection?>

    /**
     * The dice re-roll as ONE operation: drops the caches still carrying the
     * row's pre-roll items, fetches the row fresh from the server, and — on a
     * non-empty result — commits the rolled set into the network layer's
     * per-row memo so the next home-sections fetch serves the roll instead of
     * reverting or re-rolling it. Returns the fresh items.
     *
     * THE ROLL PROTOCOL: the operation survives three race windows — the
     * feature layer's DiscoverRowsCoordinator registry (FEATURE state), the
     * repo's assembled cache, and the network row memo — all guarded by ONE
     * token, [com.raulshma.jellyplay.core.network.library.HomeWriteGeneration]:
     * the data layer's home cache-write generation, bumped by its single
     * funnel at BOTH the invalidate and the commit step and carried into
     * every cache mutation whose ordering matters. That type is the contract;
     * the ordering it buys (invalidate → fetch → seed, a fetch straddling
     * the whole roll returning but not pinning) is owned by
     * `MediaRepositoryImpl.rerollDiscoverRow`, and each guarded cache
     * documents its own window beside its guard.
     *
     * A failure or an empty result skips the commit and returns as-is — the
     * caches stay dropped, so the next home fetch re-queries the row rather
     * than replaying or pinning pre-roll items. Reads compose: a cancelled or
     * failed consuming home read re-arms its #157 staleness marker, so the
     * next read still sees the roll's drops.
     */
    suspend fun rerollDiscoverRow(row: DiscoverRowConfig): Result<List<MediaItem>>

    /**
     * Returns the last persisted home-sections snapshot for the current
     * (server, user) + [query], or null if none is cached. Room-only — no network.
     * Used by the home screen to render instantly on cold open while
     * [getHomeSections] revalidates in the background (stale-while-revalidate).
     */
    suspend fun getCachedHomeSections(query: HomeSectionQuery): HomeSectionsResult?

    /**
     * The most recently persisted home-sections snapshot for the current
     * (server, user), across cacheKeys and with NO staleness ceiling — the
     * offline home's LAYOUT mirror (issue #147): the section types, titles,
     * per-library rows and order the user last saw online, which the offline
     * home reproduces filtered to downloaded items. Even a snapshot older
     * than the SWR ceiling is a better layout source than the generic
     * offline fallback rows; membership is re-filtered against the offline
     * store at consumption, so stale items simply drop out. Null when no
     * snapshot exists for the identity (fresh install, cleared data).
     */
    suspend fun getOfflineHomeLayout(): HomeSectionsResult?
}

/**
 * The user-data change-feed seam of [MediaRepository]: the hot read stream
 * live surfaces subscribe to for server-side user-data pushes (the home
 * refresher's debounced refresh, the open detail sessions' reload, the
 * library/search/music re-arms). [MediaRepositoryImpl] implements this seam
 * alongside the wide interface — one override satisfies both declarations
 * (the [MusicCatalogue] dual-declaration pattern) — so a consumer reading
 * only the feed narrows off the union. The write side stays on the union:
 * [MediaRepository.notifyUserDataChanged], the feed's synthetic-push fan-out,
 * is welded to the impl's write paths, not to this read seam.
 */
interface UserDataChanges {

    /**
     * Hot stream of server-side user-data changes (played / favorite flips from
     * any client, including this one). Collecting subscribes to the realtime
     * channel; cancelling the collector unsubscribes after a grace window —
     * the underlying socket survives (owned app-lifetime).
     */
    val userDataChanges: Flow<UserDataChange>
}

/**
 * The library/browse family seam of [MediaRepository]: the folders, latest,
 * paged-browse, search, genres and studios reads the library, search, music,
 * photos and settings screens consume. [MediaRepositoryImpl] implements this
 * seam alongside the wide interface (the [MusicCatalogue] over-the-impl
 * pattern — one override satisfies both declarations), so a browse-only
 * consumer narrows without a second repository instance. Detail/similar and
 * the collection verbs stay on the union (their consumers are mixed); mixed
 * browse+detail consumers keep [MediaRepository].
 *
 * No member here carries a default argument — the family-seam rule recorded
 * on [MusicCatalogue]: an override whose two superinterfaces both declare
 * default arguments is forbidden, so seam-typed callers pass explicit values.
 */
interface LibraryFeed {

    /** Library folders. [force] bypasses the folders cache for this read. */
    suspend fun getLibraryFolders(force: Boolean): Result<List<LibraryFolder>>

    suspend fun getLatestMedia(parentId: String, limit: Int): Result<List<MediaItem>>

    suspend fun search(
        query: String,
        filters: LibraryFilters,
        limit: Int,
        startIndex: Int,
    ): Result<SearchResult>

    fun getMediaItemsPaged(
        parentId: String?,
        filters: LibraryFilters,
        studioIds: List<String>?,
        kindFilter: com.raulshma.jellyplay.core.model.ItemKindFilter,
    ): Flow<PagingData<MediaItem>>

    fun searchPaged(
        query: String,
        filters: LibraryFilters,
    ): Flow<PagingData<MediaItem>>

    /** Genres for a library ([parentId], or server-wide when null). [force] bypasses the genres cache for this read. */
    suspend fun getGenres(parentId: String?, force: Boolean): Result<List<Genre>>

    suspend fun getStudios(parentId: String?): Result<List<Studio>>
}
