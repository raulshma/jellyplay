package com.raulshma.jellyplay.core.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.raulshma.jellyplay.core.data.paging.MediaPagingSource
import com.raulshma.jellyplay.core.data.paging.SearchPagingSource
import com.raulshma.jellyplay.core.model.DvrSeriesTimer
import com.raulshma.jellyplay.core.model.DvrTimer
import com.raulshma.jellyplay.core.model.EpgGuide
import com.raulshma.jellyplay.core.model.Genre
import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.LiveTvChannel
import com.raulshma.jellyplay.core.model.LiveTvProgram
import com.raulshma.jellyplay.core.model.LyricsResult
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.Playlist
import com.raulshma.jellyplay.core.model.PlaylistItem
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.SearchResult
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaRepositoryImpl @Inject constructor(
    private val apiClient: JellyfinApiClient,
) : MediaRepository {

    override suspend fun getHomeSections(): Result<List<HomeSection>> =
        apiClient.getHomeSections()

    override suspend fun getLibraryFolders(): Result<List<LibraryFolder>> =
        apiClient.getLibraryFolders()

    override suspend fun getMediaItems(
        parentId: String?,
        mediaTypes: List<MediaType>?,
        genres: List<String>?,
        years: List<Int>?,
        sortBy: String,
        sortOrder: String,
        startIndex: Int,
        limit: Int,
    ): Result<SearchResult> = apiClient.getMediaItems(
        parentId = parentId,
        mediaTypes = mediaTypes,
        genres = genres,
        years = years,
        sortBy = sortBy,
        sortOrder = sortOrder,
        startIndex = startIndex,
        limit = limit,
    )

    override suspend fun getMediaDetail(itemId: String): Result<MediaDetail> =
        apiClient.getMediaDetail(itemId)

    override suspend fun search(
        query: String,
        mediaTypes: List<MediaType>?,
        limit: Int,
    ): Result<SearchResult> = apiClient.getSearchHints(query, mediaTypes, limit)

    override fun getMediaItemsPaged(
        parentId: String?,
        mediaTypes: List<MediaType>?,
        genres: List<String>?,
        years: List<Int>?,
        sortBy: String,
        sortOrder: String,
    ): Flow<PagingData<MediaItem>> = Pager(
        config = PagingConfig(
            pageSize = PAGE_SIZE,
            enablePlaceholders = false,
            prefetchDistance = PREFETCH_DISTANCE,
        ),
        pagingSourceFactory = {
            MediaPagingSource(
                mediaRepository = this,
                parentId = parentId,
                mediaTypes = mediaTypes,
                genres = genres,
                years = years,
                sortBy = sortBy,
                sortOrder = sortOrder,
            )
        },
    ).flow

    override fun searchPaged(
        query: String,
        mediaTypes: List<MediaType>?,
    ): Flow<PagingData<MediaItem>> = Pager(
        config = PagingConfig(
            pageSize = PAGE_SIZE,
            enablePlaceholders = false,
            prefetchDistance = PREFETCH_DISTANCE,
        ),
        pagingSourceFactory = {
            SearchPagingSource(
                mediaRepository = this,
                query = query,
                mediaTypes = mediaTypes,
            )
        },
    ).flow

    override suspend fun getGenres(parentId: String?): Result<List<Genre>> =
        apiClient.getGenres(parentId)

    override suspend fun getSimilarItems(itemId: String, limit: Int): Result<List<MediaItem>> =
        apiClient.getSimilarItems(itemId, limit)

    override suspend fun getItemsByPerson(personId: String, limit: Int): Result<List<MediaItem>> =
        apiClient.getItemsByPerson(personId, limit)

    override suspend fun getSeasons(seriesId: String): Result<List<MediaItem>> =
        apiClient.getSeasons(seriesId)

    override suspend fun getEpisodes(seriesId: String, seasonId: String): Result<List<MediaItem>> =
        apiClient.getEpisodes(seriesId, seasonId)

    override suspend fun getCollectionItems(
        collectionId: String,
        startIndex: Int,
        limit: Int,
    ): Result<SearchResult> = apiClient.getCollectionItems(collectionId, startIndex, limit)

    override suspend fun getTags(
        parentId: String?,
        startIndex: Int,
        limit: Int,
    ): Result<List<String>> = apiClient.getTags(parentId, startIndex, limit)

    override suspend fun getFavorites(
        mediaTypes: List<MediaType>?,
        limit: Int,
    ): Result<SearchResult> = apiClient.getFavorites(mediaTypes, limit)

    override suspend fun getLyrics(itemId: String): Result<LyricsResult> = apiClient.getLyrics(itemId)

    override suspend fun getPlaylists(limit: Int): Result<List<Playlist>> = apiClient.getPlaylists(limit)

    override suspend fun getPlaylistItems(playlistId: String, startIndex: Int, limit: Int): Result<List<PlaylistItem>> =
        apiClient.getPlaylistItems(playlistId, startIndex, limit)

    companion object {
        private const val PAGE_SIZE = 50
        private const val PREFETCH_DISTANCE = 10
    }
}
