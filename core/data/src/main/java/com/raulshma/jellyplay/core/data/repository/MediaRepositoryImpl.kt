package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.Genre
import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.SearchResult
import com.raulshma.jellyplay.core.network.JellyfinApiClient
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

    override suspend fun getGenres(parentId: String?): Result<List<Genre>> =
        apiClient.getGenres(parentId)

    override suspend fun getSimilarItems(itemId: String, limit: Int): Result<List<MediaItem>> =
        apiClient.getSimilarItems(itemId, limit)

    override suspend fun getSeasons(seriesId: String): Result<List<MediaItem>> =
        apiClient.getSeasons(seriesId)

    override suspend fun getEpisodes(seriesId: String, seasonId: String): Result<List<MediaItem>> =
        apiClient.getEpisodes(seriesId, seasonId)

    override suspend fun markPlayed(itemId: String): Result<Unit> =
        apiClient.markPlayed(itemId)

    override suspend fun markUnplayed(itemId: String): Result<Unit> =
        apiClient.markUnplayed(itemId)

    override suspend fun toggleFavorite(itemId: String): Result<Boolean> =
        apiClient.toggleFavorite(itemId)
}
