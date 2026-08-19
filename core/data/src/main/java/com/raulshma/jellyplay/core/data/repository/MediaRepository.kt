package com.raulshma.jellyplay.core.data.repository

import androidx.paging.PagingData
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

interface MediaRepository : LiveTvRepository, SyncPlayRepository, NewsletterRepository, PlaylistRepository, LyricsRepository {

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
     * Returns the last persisted home-sections snapshot for the current
     * (server, user) + [query], or null if none is cached. Room-only — no network.
     * Used by the home screen to render instantly on cold open while
     * [getHomeSections] revalidates in the background (stale-while-revalidate).
     */
    suspend fun getCachedHomeSections(
        query: HomeSectionQuery = HomeSectionQuery(),
    ): HomeSectionsResult?

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

    suspend fun getItemsByStudio(
        studioId: String,
        mediaTypes: List<MediaType>? = null,
        startIndex: Int = 0,
        limit: Int = 50,
    ): Result<SearchResult>

    suspend fun getArtistAlbums(artistId: String, limit: Int = 50): Result<List<MediaItem>>

    suspend fun getAlbumTracks(albumId: String): Result<List<MediaItem>>

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

    suspend fun getCollectionItems(
        collectionId: String,
        startIndex: Int = 0,
        limit: Int = 50,
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
