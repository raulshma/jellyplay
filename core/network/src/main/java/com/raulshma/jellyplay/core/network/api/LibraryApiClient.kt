package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.Genre
import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.HomeSectionsResult
import com.raulshma.jellyplay.core.model.LibraryFilters
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.LyricsResult
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.PinnedHomeSection
import com.raulshma.jellyplay.core.model.Playlist
import com.raulshma.jellyplay.core.model.PlaylistItem
import com.raulshma.jellyplay.core.model.RecommendationResult
import com.raulshma.jellyplay.core.model.SearchResult
import com.raulshma.jellyplay.core.model.Studio

interface LibraryApiClient {
    suspend fun getHomeSections(
        enabledSections: Set<HomeSectionType> = HomeSectionType.CONFIGURABLE.toSet(),
        libraryHomeSectionOverrides: Map<String, Set<HomeSectionType>> = emptyMap(),
        nextUpRewatching: Boolean = false,
        nextUpMaxDays: Int = 0,
        nextUpExcludedSeriesIds: Set<String> = emptySet(),
        hiddenCwItemIds: Set<String> = emptySet(),
        pinnedSections: List<PinnedHomeSection> = emptyList(),
    ): Result<HomeSectionsResult>

    suspend fun getLatestMedia(parentId: String, limit: Int = 16): Result<List<MediaItem>>
    suspend fun getNextUp(limit: Int = 20, enableRewatching: Boolean = false, maxDays: Int = 0): Result<List<MediaItem>>
    suspend fun getContinueWatching(limit: Int = 20): Result<List<MediaItem>>
    suspend fun getLibraryFolders(): Result<List<LibraryFolder>>

    suspend fun getMediaItems(
        parentId: String? = null,
        /**
         * Bundles the filter/sort dimensions that always travel together
         * (mediaTypes, genres, years, tags, [com.raulshma.jellyplay.core.model.SortOption],
         * [com.raulshma.jellyplay.core.model.PlayedStatus], minRating, isResumable).
         * Replaces the long primitive parameter list so adding a dimension is a
         * single field on [LibraryFilters] instead of a signature edit here.
         * Defaults to the library landing sort (newest first, no filters).
         */
        filters: LibraryFilters = LibraryFilters(),
        studioIds: List<String>? = null,
        startIndex: Int = 0,
        limit: Int = 50,
        searchTerm: String? = null,
        /**
         * Controls which nested media kinds the query excludes. Library browsing
         * and section mode ("See All" from a home Latest row) both use the
         * default [com.raulshma.jellyplay.core.model.ItemKindFilter.TOP_LEVEL]
         * (seasons and episodes excluded), differing only in sort order.
         */
        kindFilter: com.raulshma.jellyplay.core.model.ItemKindFilter = com.raulshma.jellyplay.core.model.ItemKindFilter.TOP_LEVEL,
    ): Result<SearchResult>

    suspend fun getMediaDetail(itemId: String): Result<MediaDetail>

    /**
     * Fetch intros/trailers configured by the Jellyfin Cinema Mode intros plugin
     * for the given item. Returns an empty list when cinema mode is disabled or
     * no intros are configured server-side.
     */
    suspend fun getIntros(itemId: String): Result<List<MediaItem>>

    suspend fun getSearchHints(
        query: String,
        mediaTypes: List<MediaType>? = null,
        limit: Int = 50,
        startIndex: Int = 0,
    ): Result<SearchResult>

    /**
     * Discovery suggestions for the empty search state — favorited/liked items
     * surfaced in random order. Mirrors the official jellyfin-web behavior
     * (getItems sorted by `IsFavoriteOrLiked, Random`). Returned items are
     * navigable: clicking opens the item's detail page rather than filling
     * the search box.
     */
    suspend fun getSearchSuggestions(limit: Int = 20): Result<SearchResult>

    /**
     * Resolves a library item by a provider (external) id, e.g. `tmdb`, `tvdb`,
     * or `imdb`. Uses Jellyfin's `AnyProviderId` filter ("tmdb:123"). Returns the
     * first matching Jellyfin item id, or null when no match exists. Used to open
     * a Seerr "Available" item directly in the library.
     */
    suspend fun findItemByProviderId(provider: String, id: String): Result<String?>

    suspend fun getGenres(
        parentId: String? = null,
        startIndex: Int = 0,
        limit: Int = 100,
    ): Result<List<Genre>>

    suspend fun getItemsByGenre(
        genreId: String,
        mediaTypes: List<MediaType>? = null,
        startIndex: Int = 0,
        limit: Int = 50,
    ): Result<SearchResult>

    suspend fun getStudios(
        parentId: String? = null,
        startIndex: Int = 0,
        limit: Int = 100,
    ): Result<List<Studio>>

    suspend fun getItemsByStudio(
        studioId: String,
        mediaTypes: List<MediaType>? = null,
        startIndex: Int = 0,
        limit: Int = 50,
    ): Result<SearchResult>

    suspend fun getArtistAlbums(artistId: String, limit: Int = 50): Result<List<MediaItem>>
    suspend fun getAlbumTracks(albumId: String): Result<List<MediaItem>>
    suspend fun getSimilarItems(itemId: String, limit: Int = 12): Result<List<MediaItem>>
    suspend fun getInstantMix(itemId: String, limit: Int = 100): Result<List<MediaItem>>
    suspend fun getRecommendations(
        limit: Int = 20,
        // Pre-fetched seed items (e.g. Continue Watching + Next Up already loaded
        // by getHomeSections). When non-empty the implementation reuses them as
        // seeds instead of re-fetching; when empty it fetches its own.
        seeds: List<MediaItem> = emptyList(),
    ): Result<RecommendationResult>
    suspend fun getItemsByPerson(personId: String, limit: Int = 50): Result<List<MediaItem>>
    suspend fun getThemeSongs(itemId: String): Result<List<MediaItem>>
    suspend fun getSeasons(seriesId: String): Result<List<MediaItem>>
    suspend fun getEpisodes(seriesId: String, seasonId: String): Result<List<MediaItem>>

    /**
     * Fetches every episode for a series in a single round-trip. The Jellyfin
     * `/Shows/{seriesId}/Episodes` endpoint returns the full set when
     * `seasonId` is omitted, which collapses an N-season fan-out (one request
     * per season) into a single call. Callers that need per-season grouping
     * can `groupBy { it.seasonId }` the result locally.
     */
    suspend fun getAllEpisodes(seriesId: String): Result<List<MediaItem>>

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

    suspend fun getLyrics(itemId: String): Result<LyricsResult>
    suspend fun getPlaylists(limit: Int = 50): Result<List<Playlist>>
    suspend fun getPlaylistItems(playlistId: String, startIndex: Int = 0, limit: Int = 50): Result<List<PlaylistItem>>
    suspend fun createPlaylist(name: String, overview: String? = null, itemIds: List<String> = emptyList(), mediaType: MediaType = MediaType.AUDIO): Result<String>
    suspend fun updatePlaylist(playlistId: String, name: String? = null, overview: String? = null, isPublic: Boolean? = null): Result<Unit>
    suspend fun deletePlaylist(playlistId: String): Result<Unit>
    suspend fun addItemsToPlaylist(playlistId: String, itemIds: List<String>): Result<Unit>
    suspend fun removeItemsFromPlaylist(playlistId: String, entryIds: List<String>): Result<Unit>
    suspend fun movePlaylistItem(playlistId: String, entryId: String, newIndex: Int): Result<Unit>
    suspend fun markPlayed(itemId: String): Result<Unit>
    suspend fun markUnplayed(itemId: String): Result<Unit>
    suspend fun toggleFavorite(itemId: String, currentIsFavorite: Boolean? = null): Result<Boolean>

    /**
     * Sets an absolute favorite state on the server (`markFavoriteItem` when
     * [isFavorite], `unmarkFavoriteItem` otherwise). Used by the outbox replay
     * path so a staged flip lands deterministically regardless of the server's
     * current state — unlike [toggleFavorite], which reads-and-flips and is
     * unsuitable for replay.
     */
    suspend fun setFavorite(itemId: String, isFavorite: Boolean): Result<Unit>

    fun getImageUrl(
        itemId: String,
        imageType: String = "Primary",
        maxWidth: Int? = 400,
        imageIndex: Int? = null,
        tag: String? = null,
    ): String

    fun getBackdropImageUrl(
        itemId: String,
        maxWidth: Int = 1280,
        tag: String? = null,
    ): String

    suspend fun getChildItemImageUrls(
        parentId: String,
        limit: Int = 4,
    ): List<String>

    /**
     * Drops any in-memory favorite-flag cache. Used on server switch / disconnect
     * so stale flags from the previous server can't leak through.
     */
    fun clearFavoriteCache()
}
