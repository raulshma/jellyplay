package com.raulshma.jellyplay.core.network.seerr

import com.raulshma.jellyplay.core.model.seerr.*

interface SeerrApiClient {

    suspend fun loginJellyfin(
        baseUrl: String,
        username: String,
        password: String,
    ): Result<String>

    suspend fun loginLocal(
        baseUrl: String,
        email: String,
        password: String,
    ): Result<String>

    suspend fun testConnection(
        baseUrl: String,
        credentials: SeerrCredentials,
    ): Result<SeerrStatusResponse>

    suspend fun search(
        baseUrl: String,
        credentials: SeerrCredentials,
        query: String,
        page: Int = 1,
    ): Result<SeerrSearchResponse>

    suspend fun getMovieDetails(
        baseUrl: String,
        credentials: SeerrCredentials,
        tmdbId: Int,
    ): Result<SeerrMovieDetails>

    suspend fun getTvDetails(
        baseUrl: String,
        credentials: SeerrCredentials,
        tmdbId: Int,
    ): Result<SeerrTvDetails>

    suspend fun getTvSeasonDetails(
        baseUrl: String,
        credentials: SeerrCredentials,
        tvId: Int,
        seasonNumber: Int,
    ): Result<SeerrSeasonDetail>

    suspend fun getMovieRatings(
        baseUrl: String,
        credentials: SeerrCredentials,
        tmdbId: Int,
    ): Result<SeerrRatings>

    suspend fun getTvRatings(
        baseUrl: String,
        credentials: SeerrCredentials,
        tmdbId: Int,
    ): Result<SeerrRatings>

    suspend fun getMovieRatingsCombined(
        baseUrl: String,
        credentials: SeerrCredentials,
        tmdbId: Int,
    ): Result<SeerrRatings>

    suspend fun getMovieRecommendations(
        baseUrl: String,
        credentials: SeerrCredentials,
        tmdbId: Int,
        page: Int = 1,
    ): Result<SeerrSearchResponse>

    suspend fun getMovieSimilar(
        baseUrl: String,
        credentials: SeerrCredentials,
        tmdbId: Int,
        page: Int = 1,
    ): Result<SeerrSearchResponse>

    suspend fun getTvRecommendations(
        baseUrl: String,
        credentials: SeerrCredentials,
        tmdbId: Int,
        page: Int = 1,
    ): Result<SeerrSearchResponse>

    suspend fun getTvSimilar(
        baseUrl: String,
        credentials: SeerrCredentials,
        tmdbId: Int,
        page: Int = 1,
    ): Result<SeerrSearchResponse>

    suspend fun requestMedia(
        baseUrl: String,
        credentials: SeerrCredentials,
        mediaType: String,
        mediaId: Int,
        tvdbId: Int? = null,
        seasons: List<Int>? = null,
        serverId: Int? = null,
        profileId: Int? = null,
        rootFolder: String? = null,
        tags: List<Int>? = null,
    ): Result<SeerrMediaRequest>

    suspend fun getRadarrSettings(
        baseUrl: String,
        credentials: SeerrCredentials,
    ): Result<List<SeerrRadarrSettings>>

    suspend fun getSonarrSettings(
        baseUrl: String,
        credentials: SeerrCredentials,
    ): Result<List<SeerrSonarrSettings>>

    suspend fun getRadarrServiceDetail(
        baseUrl: String,
        credentials: SeerrCredentials,
        id: Int,
    ): Result<SeerrRadarrServiceDetail>

    suspend fun getSonarrServiceDetail(
        baseUrl: String,
        credentials: SeerrCredentials,
        id: Int,
    ): Result<SeerrSonarrServiceDetail>

    suspend fun getServiceRadarrServers(
        baseUrl: String,
        credentials: SeerrCredentials,
    ): Result<List<SeerrServiceServer>>

    suspend fun getServiceSonarrServers(
        baseUrl: String,
        credentials: SeerrCredentials,
    ): Result<List<SeerrServiceServer>>

    suspend fun getServiceRadarrDetail(
        baseUrl: String,
        credentials: SeerrCredentials,
        id: Int,
    ): Result<SeerrRadarrServiceDetail>

    suspend fun getServiceSonarrDetail(
        baseUrl: String,
        credentials: SeerrCredentials,
        id: Int,
    ): Result<SeerrSonarrServiceDetail>

    suspend fun getTrending(
        baseUrl: String,
        credentials: SeerrCredentials,
        page: Int = 1,
    ): Result<SeerrSearchResponse>

    suspend fun getDiscoverMovies(
        baseUrl: String,
        credentials: SeerrCredentials,
        page: Int = 1,
        primaryReleaseDateGte: String? = null,
    ): Result<SeerrSearchResponse>

    suspend fun getDiscoverTv(
        baseUrl: String,
        credentials: SeerrCredentials,
        page: Int = 1,
        firstAirDateGte: String? = null,
    ): Result<SeerrSearchResponse>

    suspend fun getRequests(
        baseUrl: String,
        credentials: SeerrCredentials,
        take: Int = 10,
        skip: Int = 0,
        filter: String = "pending",
        sort: String = "added",
        sortDirection: String = "desc",
        requestedBy: Int? = null,
        mediaType: String? = null,
        search: String? = null,
    ): Result<SeerrRequestListResponse>

    suspend fun getRequest(
        baseUrl: String,
        credentials: SeerrCredentials,
        id: Int,
    ): Result<SeerrRequestItem>

    suspend fun approveRequest(
        baseUrl: String,
        credentials: SeerrCredentials,
        id: Int,
    ): Result<SeerrRequestItem>

    suspend fun declineRequest(
        baseUrl: String,
        credentials: SeerrCredentials,
        id: Int,
    ): Result<SeerrRequestItem>

    suspend fun retryRequest(
        baseUrl: String,
        credentials: SeerrCredentials,
        id: Int,
    ): Result<SeerrRequestItem>

    suspend fun deleteRequest(
        baseUrl: String,
        credentials: SeerrCredentials,
        id: Int,
    ): Result<Unit>

    suspend fun deleteMedia(
        baseUrl: String,
        credentials: SeerrCredentials,
        mediaId: Int,
        is4k: Boolean = false,
    ): Result<Unit>

    suspend fun editRequest(
        baseUrl: String,
        credentials: SeerrCredentials,
        id: Int,
        mediaType: String,
        mediaId: Int,
        serverId: Int?,
        profileId: Int?,
        rootFolder: String?,
        tags: List<Int>?,
        seasons: List<Int>?,
    ): Result<SeerrRequestItem>

    suspend fun getRequestCount(
        baseUrl: String,
        credentials: SeerrCredentials,
    ): Result<SeerrRequestCount>

    suspend fun getCurrentUser(
        baseUrl: String,
        credentials: SeerrCredentials,
    ): Result<SeerrCurrentUser>
}
