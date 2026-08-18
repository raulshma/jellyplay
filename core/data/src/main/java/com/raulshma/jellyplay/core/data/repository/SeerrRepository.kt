package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.seerr.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

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

    /**
     * Related videos (trailers) fetched straight from TMDB — the fallback used
     * for the details screen's extras when Seerr itself is not connected.
     */
    suspend fun getTmdbVideos(tmdbId: Int, mediaType: MediaType): Result<List<SeerrRelatedVideo>>

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

    suspend fun getRequests(
        take: Int = 10,
        skip: Int = 0,
        filter: String = "pending",
        sort: String = "added",
        sortDirection: String = "desc",
        requestedBy: Int? = null,
        mediaType: String? = null,
        search: String? = null,
    ): Result<SeerrRequestListResponse>

    suspend fun getRequest(id: Int): Result<SeerrRequestItem>

    suspend fun approveRequest(id: Int): Result<SeerrRequestItem>

    suspend fun declineRequest(id: Int): Result<SeerrRequestItem>

    suspend fun retryRequest(id: Int): Result<SeerrRequestItem>

    suspend fun deleteRequest(id: Int): Result<Unit>

    suspend fun deleteMedia(mediaId: Int, is4k: Boolean = false): Result<Unit>

    suspend fun editRequest(
        id: Int,
        mediaType: String,
        mediaId: Int,
        serverId: Int? = null,
        profileId: Int? = null,
        rootFolder: String? = null,
        tags: List<Int>? = null,
        seasons: List<Int>? = null,
    ): Result<SeerrRequestItem>

    suspend fun getRequestCount(): Result<SeerrRequestCount>

    suspend fun getCurrentUser(): Result<SeerrCurrentUser>

    fun isAdmin(): Flow<Boolean>

    val currentUser: StateFlow<SeerrCurrentUser?>

    val pendingRequestCount: StateFlow<Int>

    /**
     * Starts/stops the background polling that refreshes [pendingRequestCount]
     * and [currentUser]. Consumers (e.g. RequestsViewModel) should call
     * [startPolling] when their UI is active and [stopPolling] when cleared to
     * avoid background battery drain for users who never enter the requests
     * screen. Both methods are idempotent.
     */
    fun startPolling()
    fun stopPolling()
}
