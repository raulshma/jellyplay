package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.seerr.*
import kotlinx.coroutines.flow.Flow

interface SeerrRepository {

    suspend fun testConnection(): Result<SeerrStatusResponse>

    suspend fun loginJellyfin(username: String, password: String): Result<SeerrStatusResponse>

    suspend fun loginLocal(email: String, password: String): Result<SeerrStatusResponse>

    suspend fun testApiKeyConnection(): Result<SeerrStatusResponse>

    suspend fun search(query: String, page: Int = 1): Result<SeerrSearchResponse>

    suspend fun getMovieDetails(tmdbId: Int): Result<SeerrMovieDetails>

    suspend fun getTvDetails(tmdbId: Int): Result<SeerrTvDetails>

    suspend fun getTvSeasonDetails(tvId: Int, seasonNumber: Int): Result<SeerrSeasonDetail>

    suspend fun getRatings(tmdbId: Int, mediaType: String): Result<SeerrRatings>

    suspend fun getRecommendations(tmdbId: Int, mediaType: MediaType): Result<SeerrSearchResponse>

    suspend fun getSimilar(tmdbId: Int, mediaType: MediaType): Result<SeerrSearchResponse>

    suspend fun requestMedia(
        tmdbId: Int,
        mediaType: String,
        seasons: List<Int>? = null,
        serverId: Int? = null,
        profileId: Int? = null,
        rootFolder: String? = null,
        tags: List<Int>? = null,
    ): Result<SeerrMediaRequest>

    suspend fun getRadarrSettings(): Result<List<SeerrRadarrSettings>>

    suspend fun getSonarrSettings(): Result<List<SeerrSonarrSettings>>

    suspend fun getRadarrServiceDetail(id: Int): Result<SeerrRadarrServiceDetail>

    suspend fun getSonarrServiceDetail(id: Int): Result<SeerrSonarrServiceDetail>

    // ── /service/ endpoints (used by request modal) ──

    suspend fun getServiceRadarrServers(): Result<List<SeerrServiceServer>>

    suspend fun getServiceSonarrServers(): Result<List<SeerrServiceServer>>

    suspend fun getServiceRadarrDetail(id: Int): Result<SeerrRadarrServiceDetail>

    suspend fun getServiceSonarrDetail(id: Int): Result<SeerrSonarrServiceDetail>

    fun isConnected(): Flow<Boolean>

    fun isEnabled(): Flow<Boolean>

    fun isSearchEnabled(): Flow<Boolean>

    fun isRecommendationsEnabled(): Flow<Boolean>

    fun isDiscoverEnabled(): Flow<Boolean>

    fun getPreferences(): Flow<SeerrPreferences>

    // ── Discover endpoints ──

    suspend fun getTrending(page: Int = 1): Result<SeerrSearchResponse>

    suspend fun getDiscoverMovies(page: Int = 1, primaryReleaseDateGte: String? = null): Result<SeerrSearchResponse>

    suspend fun getDiscoverTv(page: Int = 1, firstAirDateGte: String? = null): Result<SeerrSearchResponse>
}
