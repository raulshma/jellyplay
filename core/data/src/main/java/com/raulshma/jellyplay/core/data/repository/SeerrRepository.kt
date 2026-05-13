package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.seerr.*
import kotlinx.coroutines.flow.Flow

interface SeerrRepository {

    suspend fun testConnection(): Result<SeerrStatusResponse>

    suspend fun search(query: String, page: Int = 1): Result<SeerrSearchResponse>

    suspend fun getMovieDetails(tmdbId: Int): Result<SeerrMovieDetails>

    suspend fun getTvDetails(tmdbId: Int): Result<SeerrTvDetails>

    suspend fun getRatings(tmdbId: Int, mediaType: String): Result<SeerrRatings>

    suspend fun getRecommendations(tmdbId: Int, mediaType: MediaType): Result<SeerrSearchResponse>

    suspend fun getSimilar(tmdbId: Int, mediaType: MediaType): Result<SeerrSearchResponse>

    suspend fun requestMedia(tmdbId: Int, mediaType: String, seasons: List<Int>? = null): Result<SeerrMediaRequest>

    fun isConnected(): Flow<Boolean>

    fun isEnabled(): Flow<Boolean>

    fun isSearchEnabled(): Flow<Boolean>

    fun isRecommendationsEnabled(): Flow<Boolean>

    fun getPreferences(): Flow<SeerrPreferences>
}
