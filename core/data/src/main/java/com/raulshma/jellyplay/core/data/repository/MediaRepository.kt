package com.raulshma.jellyplay.core.data.repository

import androidx.paging.PagingData
import com.raulshma.jellyplay.core.model.Genre
import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.LrcLibTrack
import com.raulshma.jellyplay.core.model.LyricsResult
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.SearchResult
import kotlinx.coroutines.flow.Flow

interface MediaRepository : LiveTvRepository, SyncPlayRepository, NewsletterRepository, PlaylistRepository {

    suspend fun getHomeSections(
        enabledSections: Set<HomeSectionType> = HomeSectionType.CONFIGURABLE.toSet(),
        hiddenLibraryIds: Set<String> = emptySet(),
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
        sortBy: String = "SortName",
        sortOrder: String = "Ascending",
        startIndex: Int = 0,
        limit: Int = 50,
    ): Result<SearchResult>

    suspend fun getMediaDetail(itemId: String): Result<MediaDetail>

    suspend fun search(
        query: String,
        mediaTypes: List<MediaType>? = null,
        limit: Int = 50,
        startIndex: Int = 0,
    ): Result<SearchResult>

    fun getMediaItemsPaged(
        parentId: String? = null,
        mediaTypes: List<MediaType>? = null,
        genres: List<String>? = null,
        years: List<Int>? = null,
        sortBy: String = "SortName",
        sortOrder: String = "Ascending",
    ): Flow<PagingData<MediaItem>>

    fun searchPaged(
        query: String,
        mediaTypes: List<MediaType>? = null,
    ): Flow<PagingData<MediaItem>>

    suspend fun getGenres(parentId: String? = null): Result<List<Genre>>

    suspend fun getArtistAlbums(artistId: String, limit: Int = 50): Result<List<MediaItem>>

    suspend fun getAlbumTracks(albumId: String): Result<List<MediaItem>>

    suspend fun getSimilarItems(itemId: String, limit: Int = 12): Result<List<MediaItem>>

    suspend fun getItemsByPerson(personId: String, limit: Int = 50): Result<List<MediaItem>>

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

    suspend fun getLyrics(itemId: String): Result<LyricsResult>

    suspend fun getLyricsWithFallback(
        itemId: String,
        artistName: String?,
        trackName: String?,
        duration: Double?,
    ): Result<LyricsResult>

    suspend fun searchLyrics(query: String): Result<List<LrcLibTrack>>

    suspend fun getLyricsById(lrcLibId: Long, itemId: String): Result<LyricsResult>

    suspend fun toggleFavorite(itemId: String): Result<Boolean>

    suspend fun markPlayed(itemId: String): Result<Unit>

    suspend fun markUnplayed(itemId: String): Result<Unit>

    suspend fun cleanupLyricsCache()
}
