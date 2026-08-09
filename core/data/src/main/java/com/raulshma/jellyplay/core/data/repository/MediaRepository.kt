package com.raulshma.jellyplay.core.data.repository

import androidx.paging.PagingData
import com.raulshma.jellyplay.core.model.Genre
import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.HomeSectionsResult
import com.raulshma.jellyplay.core.model.LibraryFilters
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.SearchResult
import com.raulshma.jellyplay.core.model.Studio
import kotlinx.coroutines.flow.Flow

interface MediaRepository : LiveTvRepository, SyncPlayRepository, NewsletterRepository, PlaylistRepository, LyricsRepository {

    suspend fun getHomeSections(
        query: HomeSectionQuery = HomeSectionQuery(),
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

    suspend fun getLibraryFolders(): Result<List<LibraryFolder>>

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

    suspend fun getMediaDetail(itemId: String): Result<MediaDetail>

    /**
     * Cinema Mode intros. Returns the list of trailers/intros configured on the
     * server for the given item (via Jellyfin's built-in intros endpoint).
     * Returns an empty list when no intros are available.
     */
    suspend fun getIntros(itemId: String): Result<List<MediaItem>>

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

    suspend fun getGenres(parentId: String? = null): Result<List<Genre>>

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

    suspend fun toggleFavorite(itemId: String): Result<Boolean>

    suspend fun markPlayed(itemId: String): Result<Unit>

    suspend fun markUnplayed(itemId: String): Result<Unit>

    /**
     * Drop in-memory caches so the next repository call fetches fresh user-data
     * (favorites, played state, playback positions) from the server. Used by the
     * background user-data sync worker to keep this device's view of the library
     * in sync with changes made on other clients.
     */
    suspend fun invalidateCaches()

    fun invalidateDetailCache(itemId: String? = null)

    /**
     * Drops the TTL-cached seasons/episodes entries for the given series so the
     * next call re-fetches fresh user-data (played state, playback positions)
     * from the server. Used after user-data mutations on an episode or the
     * series itself.
     */
    fun invalidateSeriesCache(seriesId: String)

    /**
     * Drops the TTL-cached collection-items entries for the given collection so
     * the next call re-fetches fresh data from the server. Used by the detail
     * screen's pull-to-refresh, which must bypass every in-memory cache.
     */
    fun invalidateCollectionItemsCache(collectionId: String)

    /**
     * Single operation for "user data for [itemId] changed" (favorite flip,
     * played/unplayed, playback position). Owns the series-resolution rule:
     * drops the item's detail cache, its album tracks, and — if the item
     * belongs to a series — that series' seasons/episodes caches.
     *
     * Prefer this over calling [invalidateDetailCache] +
     * [invalidateSeriesCache] separately at a call site, which re-derives the
     * "is this item part of a series?" rule locally. Callers that only have a
     * series id (no item detail cached) should still call
     * [invalidateSeriesCache] directly.
     */
    fun invalidateUserDataCaches(itemId: String)

    suspend fun getPhotoFolderChildImageUrls(folderId: String, limit: Int = 4): List<String>
}
