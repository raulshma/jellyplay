package com.raulshma.jellyplay.core.data.repository

import androidx.paging.PagingData
import com.raulshma.jellyplay.core.model.Genre
import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.PinnedHomeSection
import com.raulshma.jellyplay.core.model.SearchResult
import com.raulshma.jellyplay.core.model.Studio
import kotlinx.coroutines.flow.Flow

interface MediaRepository : LiveTvRepository, SyncPlayRepository, NewsletterRepository, PlaylistRepository, LyricsRepository {

    suspend fun getHomeSections(
        enabledSections: Set<HomeSectionType> = HomeSectionType.CONFIGURABLE.toSet(),
        hiddenLibraryIds: Set<String> = emptySet(),
        nextUpRewatching: Boolean = false,
        nextUpMaxDays: Int = 0,
        nextUpExcludedSeriesIds: Set<String> = emptySet(),
        hiddenCwItemIds: Set<String> = emptySet(),
        pinnedSections: List<PinnedHomeSection> = emptyList(),
    ): Result<List<HomeSection>>

    suspend fun getLibraryFolders(): Result<List<LibraryFolder>>

    suspend fun getLatestMedia(
        parentId: String,
        limit: Int = 16,
    ): Result<List<MediaItem>>

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
        tags: List<String>? = null,
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
        mediaTypes: List<MediaType>? = null,
        genres: List<String>? = null,
        years: List<Int>? = null,
        tags: List<String>? = null,
        limit: Int = 50,
        startIndex: Int = 0,
    ): Result<SearchResult>

    fun getMediaItemsPaged(
        parentId: String? = null,
        mediaTypes: List<MediaType>? = null,
        genres: List<String>? = null,
        years: List<Int>? = null,
        studioIds: List<String>? = null,
        sortBy: String = "SortName",
        sortOrder: String = "Ascending",
        tags: List<String>? = null,
    ): Flow<PagingData<MediaItem>>

    fun searchPaged(
        query: String,
        mediaTypes: List<MediaType>? = null,
        genres: List<String>? = null,
        years: List<Int>? = null,
        tags: List<String>? = null,
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

    suspend fun getPhotoFolderChildImageUrls(folderId: String, limit: Int = 4): List<String>
}
