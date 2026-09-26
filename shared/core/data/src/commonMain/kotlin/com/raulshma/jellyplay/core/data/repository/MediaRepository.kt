package com.raulshma.jellyplay.core.data.repository

import androidx.paging.PagingData
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
import com.raulshma.jellyplay.core.model.PersonRef
import com.raulshma.jellyplay.core.model.SearchResult
import com.raulshma.jellyplay.core.model.Studio
import com.raulshma.jellyplay.core.model.UserDataChange
import kotlinx.coroutines.flow.Flow

interface MediaRepository {

    /**
     * The home screen's section payload. Pass [force] to bypass the in-memory
     * home-sections cache for this read (manual refresh / pull-to-refresh —
     * the sanctioned freshness lever; narrower than a global cache drop).
     */
    suspend fun getHomeSections(
        query: HomeSectionQuery = HomeSectionQuery(),
        force: Boolean = false,
    ): Result<HomeSectionsResult>

    /**
     * Fetches one custom discover row's items fresh from the server (the
     * editor's unsaved-draft preview) — bypasses every cache by construction
     * (direct client call, not the home-sections path). A ROLL that must
     * survive the next periodic home refresh is [rerollDiscoverRow]'s job,
     * not a hand-sequenced pair with a cache drop.
     */
    suspend fun getDiscoverRowItems(row: DiscoverRowConfig): Result<List<MediaItem>>

    /**
     * The dice re-roll as ONE operation: drops the caches still carrying the
     * row's pre-roll items, fetches the row fresh from the server, and — on a
     * non-empty result — commits the rolled set into the network layer's
     * per-row memo so the next home-sections fetch serves the roll instead of
     * reverting or re-rolling it. Returns the fresh items.
     *
     * ── THE ROLL PROTOCOL (single owner; implementation sites reference this
     * doc instead of restating it) ──────────────────────────────────────────
     *
     * One user action must survive THREE race windows, one per layer:
     *
     *  1. FEATURE registry — a roll landing while a full home refresh is
     *     already in flight: the raced fetch's resolved payloads still carry
     *     the row's PRE-roll items, and its single sections write would
     *     transiently revert the on-screen roll. The feature's
     *     DiscoverRowsCoordinator registry (roll generations + the drain
     *     point in HomeRefresher.fetchOnce, strictly between the last
     *     suspension and the sections write) re-applies registered rolls.
     *     This layer cannot see that write — it orders FEATURE state only.
     *  2. REPO cache epoch — [MediaRepositoryImpl.discoverRollEpoch]: a
     *     getHomeSections already on the wire when the roll landed must not
     *     pin its pre-roll assembled payload into the repo's in-memory cache
     *     (the seed's clear cannot stop a LATER write). Bumped at invalidate
     *     AND at commit; consumed as the write guard on the repo's
     *     home-sections cache-through read.
     *  3. NETWORK row epoch — HomeSectionsFetcher's discoverRowEpoch: the
     *     same stall-guard one layer down, for the per-row TTL memo (a row
     *     sub-call in flight across the roll must not memoise its pre-roll
     *     response over the seed). Same bump rule: at invalidate AND at
     *     commit.
     *
     * The ORDERING is the operation's contract, owned by
     * [MediaRepositoryImpl.rerollDiscoverRow]:
     *   invalidate ([MediaRepositoryImpl.invalidateDiscoverRowCache]:
     *     repo-epoch bump → network per-row memo drop → assembled home
     *     payload drop) → fetch (fresh row query, every cache bypassed) →
     *     seed on success ([MediaRepositoryImpl.seedDiscoverRowCache]:
     *     network row memo write + epoch bump + assembled payload drop
     *     again — a periodic fetch that raced the roll may have re-cached
     *     the pre-roll sections after the pre-fetch invalidate).
     *
     * The "epoch bumped at invalidate AND at commit" rule is what closes the
     * whole-roll window: a fetch that started BEFORE the invalidate and lands
     * AFTER the commit stays stall-guarded across both halves.
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
    suspend fun getCachedHomeSections(
        query: HomeSectionQuery = HomeSectionQuery(),
    ): HomeSectionsResult?

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
     *
     * Defaults to null — the offline home is
     * a jvm/android feature.
     */
    suspend fun getOfflineHomeLayout(): HomeSectionsResult? = null

    /** Library folders. [force] bypasses the folders cache for this read. */
    suspend fun getLibraryFolders(force: Boolean = false): Result<List<LibraryFolder>>

    suspend fun getLatestMedia(
        parentId: String,
        limit: Int = 16,
    ): Result<List<MediaItem>>

    suspend fun getMediaItems(
        parentId: String? = null,
        /**
         * Bundles the filter/sort dimensions that always travel together
         * (mediaTypes, genres, years, tags, sortBy, playedStatus, minRating,
         * isResumable). Replaces a long primitive parameter list so adding a
         * dimension is a single field on [LibraryFilters] instead of a signature
         * edit across repository → paging source → network client.
         */
        filters: LibraryFilters = LibraryFilters(),
        studioIds: List<String>? = null,
        startIndex: Int = 0,
        limit: Int = 50,
        kindFilter: com.raulshma.jellyplay.core.model.ItemKindFilter = com.raulshma.jellyplay.core.model.ItemKindFilter.TOP_LEVEL,
    ): Result<SearchResult>

    /**
     * Fetches the detail for [itemId]. Pass [force] to bypass the in-memory
     * detail cache for this read (the sanctioned freshness lever for
     * pull-to-refresh and re-fetch-after-write flows): the repository drops
     * the cached entry for [itemId] first, then fetches — exactly the
     * drop-then-read sequence callers used to run by hand.
     */
    suspend fun getMediaDetail(itemId: String, force: Boolean = false): Result<MediaDetail>

    /**
     * Cinema Mode intros. Returns the list of trailers/intros configured on the
     * server for the given item (via Jellyfin's built-in intros endpoint).
     * Returns an empty list when no intros are available.
     */
    suspend fun getIntros(itemId: String): Result<List<MediaItem>>

    /**
     * Special features / extras (featurettes, deleted scenes, interviews, etc.)
     * attached to the given item via Jellyfin's `/Items/{id}/SpecialFeatures`
     * endpoint. Returns an empty list when the item has no extras. Remote-only.
     */
    suspend fun getSpecialFeatures(itemId: String): Result<List<MediaItem>>

    suspend fun search(
        query: String,
        filters: LibraryFilters = LibraryFilters(),
        limit: Int = 50,
        startIndex: Int = 0,
    ): Result<SearchResult>

    /**
     * Discovery suggestions for the empty search state — favorited/liked movies,
     * shows and artists surfaced in random order (matches the official
     * jellyfin-web behavior). Clicking a suggestion should navigate to the
     * item's detail page.
     */
    suspend fun getSearchSuggestions(limit: Int = 20): Result<SearchResult>

    /**
     * Resolves a library item id by provider (external) id such as `tmdb`, `tvdb`,
     * or `imdb`. Returns the matching Jellyfin item id, or null when no item has
     * that provider id. Used to open a Seerr "Available" item in the library.
     */
    suspend fun findItemByProviderId(provider: String, id: String): Result<String?>

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
     * Cast/crew person lookup for the discover-row editor's People picker,
     * narrowed server-side by [searchTerm]. Deliberately returns the bare
     * id+name pair: persons have no playable detail surface in the app, so a
     * MediaItem projection would be dead weight.
     */
    suspend fun getPeople(searchTerm: String? = null, limit: Int = 50): Result<List<PersonRef>>

    suspend fun getItemsByStudio(
        studioId: String,
        mediaTypes: List<MediaType>? = null,
        startIndex: Int = 0,
        limit: Int = 50,
    ): Result<SearchResult>

    suspend fun getArtistAlbums(artistId: String, limit: Int = 50): Result<List<MediaItem>>

    /**
     * [force] drops the cached track list first (the freshness lever the
     * album detail's deferred silent refresh needs: a track user-data flip
     * evicts `tracks_<trackId>`, never `tracks_<albumId>`, so the album's
     * cached list can only be superseded by an explicit force).
     */
    suspend fun getAlbumTracks(albumId: String, force: Boolean = false): Result<List<MediaItem>>

    suspend fun getMusicVideos(parentId: String, limit: Int = 50): Result<List<MediaItem>>

    suspend fun getSimilarItems(itemId: String, limit: Int = 12): Result<List<MediaItem>>

    suspend fun getInstantMix(itemId: String, limit: Int = 100): Result<List<MediaItem>>

    suspend fun getItemsByPerson(personId: String, limit: Int = 50): Result<List<MediaItem>>

    suspend fun getThemeSongs(itemId: String): Result<List<MediaItem>>

    suspend fun getSeasons(seriesId: String): Result<List<MediaItem>>

    suspend fun getEpisodes(seriesId: String, seasonId: String): Result<List<MediaItem>>

    /**
     * Fetches every episode for a series in a single round-trip and groups the
     * result by `seasonId`. Collapses an N-season fan-out (one request per
     * season) into a single call to Jellyfin's `/Shows/{seriesId}/Episodes`
     * endpoint (which returns the full set when `seasonId` is omitted).
     */
    suspend fun getAllEpisodesGrouped(seriesId: String): Result<Map<String, List<MediaItem>>>

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
     * Lists the user's collections (Jellyfin BoxSet items) for the detail
     * screen's "Add to Collection" picker. Remote-only — collections are a
     * server-side library construct. Returned summaries are not cached: the
     * picker refetches on every open so newly-created collections appear.
     */
    suspend fun getCollections(limit: Int = 100): Result<List<com.raulshma.jellyplay.core.model.CollectionSummary>>

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

    suspend fun getTags(
        parentId: String? = null,
        startIndex: Int = 0,
        limit: Int = 100,
    ): Result<List<String>>

    suspend fun getFavorites(
        mediaTypes: List<MediaType>? = null,
        limit: Int = 50,
        startIndex: Int = 0,
    ): Result<SearchResult>

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

    suspend fun getPhotoFolderChildImageUrls(folderId: String, limit: Int = 4): List<String>
}
