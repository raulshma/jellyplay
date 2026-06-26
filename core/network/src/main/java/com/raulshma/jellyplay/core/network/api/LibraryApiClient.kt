package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.Genre
import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.HomeSectionType
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
        hiddenLibraryIds: Set<String> = emptySet(),
        nextUpRewatching: Boolean = false,
        nextUpMaxDays: Int = 0,
        nextUpExcludedSeriesIds: Set<String> = emptySet(),
        hiddenCwItemIds: Set<String> = emptySet(),
        pinnedSections: List<PinnedHomeSection> = emptyList(),
    ): Result<List<HomeSection>>

    suspend fun getLatestMedia(parentId: String, limit: Int = 16): Result<List<MediaItem>>
    suspend fun getNextUp(limit: Int = 20, enableRewatching: Boolean = false, maxDays: Int = 0): Result<List<MediaItem>>
    suspend fun getContinueWatching(limit: Int = 20): Result<List<MediaItem>>
    suspend fun getLibraryFolders(): Result<List<LibraryFolder>>

    suspend fun getMediaItems(
        parentId: String? = null,
        mediaTypes: List<MediaType>? = null,
        genres: List<String>? = null,
        years: List<Int>? = null,
        studioIds: List<String>? = null,
        sortBy: String = "SortName",
        sortOrder: String = "Ascending",
        startIndex: Int = 0,
        limit: Int = 50,
        searchTerm: String? = null,
        tags: List<String>? = null,
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
    suspend fun getRecommendations(limit: Int = 20): Result<RecommendationResult>
    suspend fun getItemsByPerson(personId: String, limit: Int = 50): Result<List<MediaItem>>
    suspend fun getThemeSongs(itemId: String): Result<List<MediaItem>>
    suspend fun getSeasons(seriesId: String): Result<List<MediaItem>>
    suspend fun getEpisodes(seriesId: String, seasonId: String): Result<List<MediaItem>>

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
    suspend fun createPlaylist(name: String, overview: String? = null, itemIds: List<String> = emptyList()): Result<String>
    suspend fun updatePlaylist(playlistId: String, name: String? = null, overview: String? = null, isPublic: Boolean? = null): Result<Unit>
    suspend fun deletePlaylist(playlistId: String): Result<Unit>
    suspend fun addItemsToPlaylist(playlistId: String, itemIds: List<String>): Result<Unit>
    suspend fun removeItemsFromPlaylist(playlistId: String, entryIds: List<String>): Result<Unit>
    suspend fun movePlaylistItem(playlistId: String, entryId: String, newIndex: Int): Result<Unit>
    suspend fun markPlayed(itemId: String): Result<Unit>
    suspend fun markUnplayed(itemId: String): Result<Unit>
    suspend fun toggleFavorite(itemId: String, currentIsFavorite: Boolean? = null): Result<Boolean>

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
}
