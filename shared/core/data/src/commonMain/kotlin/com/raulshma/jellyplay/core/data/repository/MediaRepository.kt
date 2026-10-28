package com.raulshma.jellyplay.core.data.repository

import androidx.paging.PagingData
import com.raulshma.jellyplay.core.model.CollectionSummary
import com.raulshma.jellyplay.core.model.DiscoverRowConfig
import com.raulshma.jellyplay.core.model.Genre
import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.HomeSectionQuery
import com.raulshma.jellyplay.core.model.HomeSectionsResult
import com.raulshma.jellyplay.core.model.ItemKindFilter
import com.raulshma.jellyplay.core.model.LibraryFilters
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.SearchResult
import com.raulshma.jellyplay.core.model.Studio
import com.raulshma.jellyplay.core.model.UserDataChange
import kotlinx.coroutines.flow.Flow

/**
 * The family-seam rule every repository family in this file follows: NO
 * member carries a default argument. All defaults died with the
 * `MediaRepository` union they were dual-declared against (Kotlin forbids an
 * override whose two superinterfaces both declare default arguments), and the
 * no-default posture stays — it is load-bearing for mockk captures (a
 * seam-typed call stub/verify records every argument explicitly), so
 * seam-typed callers pass explicit values.
 */

/**
 * The browse-catalogue enrichment family seam: the filter sheets' tag facet
 * and the detail screen's franchise (BoxSets-containing) row — two cached
 * reads whose consumers are clean family consumers (nothing else on the wide
 * surface they need). [MediaRepositoryImpl] implements this seam alongside
 * the other families (the [MusicCatalogue] pattern), so the surface ratchet
 * stays flat instead of absorbing growth that only two screens read.
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
 * The music-catalogue family seam: the album → track reads and the
 * theme-song lookup — the cached members only music surfaces ever call.
 * [MediaRepositoryImpl] implements this seam alongside the other families
 * (the [com.raulshma.jellyplay.core.data.repository.SonarrSeriesOperations]
 * over-the-impl pattern), so a consumer narrows without a second repository
 * instance or a family supertype creeping back onto a wide interface. The
 * uncached catalogue reads (getArtistAlbums / getInstantMix) retired from
 * the seam to [com.raulshma.jellyplay.core.network.api.LibraryApiClient] —
 * their callers inject the client family directly.
 *
 * Consumers: the audio playback stack (AudioLibraryBrowser /
 * AudioQueueFacade / ThemeMusicPlayer) and feature:music's artist/album/home
 * ViewModels inject THIS seam for the cached catalogue reads; the detail
 * provider's session (UnifiedMediaDetailProviderImpl) resolves detail +
 * album tracks together and takes this seam beside [DetailFeed].
 * [getMusicVideos] retired from the seam outright: zero callers anywhere.
 */
interface MusicCatalogue {

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
 * consumer injects this seam; the impl satisfies it beside the other
 * families.
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
 * The photo-folder family seam of the media stack (the
 * [SeriesNotificationReads] one-member pattern): the cached child-image-URL
 * read the photo-folder prefetcher fans out over. Its clean sole consumer
 * injects this seam; the impl satisfies it beside the other families.
 *
 * The read rides the repository (not the client family directly) because it
 * is cached: a TTL'd per-folder entry in the impl's spec registry, dropped by
 * the wholesale identity ladder like the folders/genres siblings.
 */
interface PhotoFolderReads {
    suspend fun getPhotoFolderChildImageUrls(folderId: String, limit: Int): List<String>
}

/**
 * The user-data WRITE family seam: the five toggle/mark endpoints every
 * user-data mutation funnels through, plus the offline outbox drain's deep
 * write. Its clean consumers are [UserDataMutatorImpl] — the protocol facade
 * every feature consumes — and the offline outbox drainer (whose drain-tail
 * fan-out lands in [recordDrainedPlays]).
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

    /**
     * The offline outbox drain's deep write: marks every id in [itemIds]
     * played and pairs each flip with its confirmed-write fan-out — the exact
     * [markPlayed] per-item body (PlayedStateSync flip + the repository's
     * cache-invalidation wrapper), whose delivered flips announce themselves
     * on the synthetic-push flow from inside the flip, batched so the drain's
     * derived-watched-flip pass is ONE call into this seam. The drain
     * announces its remaining delivered ids (replayed entries, adopted rows)
     * once at the tail through the announcer, never naming a delivered
     * derived flip twice.
     */
    suspend fun recordDrainedPlays(itemIds: List<String>)
}

/**
 * The home-feed family seam: the home screen's sections payload and its
 * row-scoped verbs — the batched sections fetch, the single-row edge-pull
 * refetch, the cold-open SWR snapshot reads, and the custom-discover row's
 * dice roll. [MediaRepositoryImpl] implements this seam alongside the other
 * families (the [MusicCatalogue] over-the-impl pattern), so a home-only
 * consumer narrows without a second repository instance.
 *
 * Consumers: the home feature's refresh stack (HomeRefresher, its dice-roll
 * coordinator, the VM's offline-layout mirror) injects this seam alone; the
 * background home-sections refetchers (TvWatchNextPublisher,
 * UserDataSyncWorker, the library recommendations widget worker) and the
 * discover-row editor VM inject it beside the other seams they are mixed
 * consumers of (the collection-read seam, the browse family).
 */
interface HomeFeed {

    /**
     * The home screen's section payload. Pass [force] to bypass the in-memory
     * home-sections cache for this read (manual refresh / pull-to-refresh —
     * the sanctioned freshness lever; narrower than a global cache drop).
     *
     * WRITE GUARANTEE (all callers, no exceptions): every assembled payload
     * this read produces is written behind this repository's ONE home
     * cache-write generation — a repo-local token, owned and bumped only by
     * the impl's funnel, which runs whenever a home row mutation lands (the
     * network layer reporting a completed dice roll or a successful
     * single-row refresh) — so a fetch already on the wire when a roll or
     * single-row refresh lands still returns to its caller but never pins
     * its pre-roll payload into the cache. (The network layer's row memos
     * keep their own write guards inside the fetcher's operations; no token
     * crosses the module seam.) No caller therefore needs external
     * serialization for cache safety (the background refetchers —
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
     * repo's assembled cache, and the network row memo. Ownership is layered:
     * the invalidate → fetch → seed choreography over the row memo is ONE
     * complete operation inside the network layer (the fetcher bumps its own
     * write guard at the drop and at the seed, so a row sub-call straddling
     * the roll is returned to its caller but never pinned into the memo),
     * and the repo bumps its own assembled-payload cache generation when the
     * operation returns — the row memo changed under the assembled payload
     * either way — so a FULL fetch in flight across the roll also returns
     * but never pins. A read landing inside the roll's raw-fetch window may
     * still be served the pre-roll assembled payload (the drop lands with
     * the post-operation bump); it can never pin.
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
 * The user-data change-feed seam: the hot read stream live surfaces
 * subscribe to for server-side user-data pushes (the home refresher's
 * debounced refresh, the open detail sessions' reload, the library/search/
 * music re-arms). [MediaRepositoryImpl] implements this seam alongside the
 * other families — the flow is the merge of the realtime WS channel and the
 * [UserDataAnnouncer] leaf's synthetic pushes (local writers whose mutation
 * reached the server outside the socket's echo), so an announce and a server
 * push reach every consumer through one flow.
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
 * The library/browse family seam: the folders, latest, paged-browse, search,
 * genres and studios reads the library, search, music, photos and settings
 * screens consume. [MediaRepositoryImpl] implements this seam alongside the
 * other families (the [MusicCatalogue] over-the-impl pattern — one class
 * satisfies every family), so a browse-only consumer narrows without a
 * second repository instance. The detail/similar and collection verbs have
 * their own family seams ([DetailFeed] / [CollectionOperations]); mixed
 * consumers inject the two families they read.
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
        kindFilter: ItemKindFilter,
    ): Flow<PagingData<MediaItem>>

    fun searchPaged(
        query: String,
        filters: LibraryFilters,
    ): Flow<PagingData<MediaItem>>

    /** Genres for a library ([parentId], or server-wide when null). [force] bypasses the genres cache for this read. */
    suspend fun getGenres(parentId: String?, force: Boolean): Result<List<Genre>>

    suspend fun getStudios(parentId: String?): Result<List<Studio>>

    /** The favorites paged read ([mediaTypes] filter, null = all kinds). */
    fun getFavoritesPaged(mediaTypes: List<MediaType>?): Flow<PagingData<MediaItem>>
}

/**
 * The detail-read family seam: the detail fetch and the similar-items row —
 * the two detail-cluster reads whose consumers are clean detail-read
 * consumers (nothing else on the wide surface they need).
 * [MediaRepositoryImpl] implements this seam alongside the other families
 * (the [LibraryFeed] over-the-impl pattern), so a detail-read-only consumer
 * narrows without a second repository instance. Both members are served by
 * the impl's [MediaRepositoryInternals] detail-cache cluster (single-flight,
 * the [force] freshness lever, epoch-guarded writes); the
 * album-tracks/theme-song/franchise siblings stay on their own families
 * ([MusicCatalogue], [MediaCatalogueEnrichments]) — a consumer needing those
 * beside the detail read takes both seams.
 */
interface DetailFeed {

    /**
     * Fetches the detail for [itemId]. Pass [force] to bypass the in-memory
     * detail cache for this read (the sanctioned freshness lever for
     * pull-to-refresh and re-fetch-after-write flows).
     */
    suspend fun getMediaDetail(itemId: String, force: Boolean): Result<MediaDetail>

    /** The similar-items row for [itemId] ([limit]-capped; the cache key includes the limit). */
    suspend fun getSimilarItems(itemId: String, limit: Int): Result<List<MediaItem>>
}

/**
 * The collection-operations family seam: the collection page read and the
 * two collection edits — the three BoxSet verbs the detail screen's
 * collection flows consume. [MediaRepositoryImpl] implements this seam
 * alongside the other families (the [LibraryFeed] over-the-impl pattern), so
 * a collections-only consumer narrows without a second repository instance.
 * The reads route through the collection page cache (the announced-staleness
 * lever — a member item's user-data flip arms the marker the next
 * non-forced read consumes); the edits self-invalidate the collection's page
 * family on success.
 */
interface CollectionOperations {

    /**
     * The collection's member page ([startIndex]/[limit] window). Pass
     * [force] to drop every cached page for the collection first — the
     * prefix evict spans all pages, so forcing one page heals the earlier
     * ones too.
     */
    suspend fun getCollectionItems(
        collectionId: String,
        startIndex: Int,
        limit: Int,
        force: Boolean,
    ): Result<SearchResult>

    /**
     * Creates a new collection seeded with the given item ids and returns the
     * new collection's id. Remote-only.
     */
    suspend fun createCollection(name: String, itemIds: List<String>): Result<String>

    /** Adds the given item ids to an existing collection. Remote-only. */
    suspend fun addItemsToCollection(collectionId: String, itemIds: List<String>): Result<Unit>
}
