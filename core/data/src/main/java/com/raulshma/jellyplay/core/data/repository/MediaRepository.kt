package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.SearchResult
import com.raulshma.jellyplay.core.model.Genre

interface MediaRepository {

    suspend fun getHomeSections(): Result<List<HomeSection>>

    suspend fun getLibraryFolders(): Result<List<LibraryFolder>>

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
    ): Result<SearchResult>

    suspend fun getGenres(parentId: String? = null): Result<List<Genre>>

    suspend fun getSimilarItems(itemId: String, limit: Int = 12): Result<List<MediaItem>>

    suspend fun getSeasons(seriesId: String): Result<List<MediaItem>>

    suspend fun getEpisodes(seriesId: String, seasonId: String): Result<List<MediaItem>>

    suspend fun markPlayed(itemId: String): Result<Unit>

    suspend fun markUnplayed(itemId: String): Result<Unit>

    suspend fun toggleFavorite(itemId: String): Result<Boolean>
}
