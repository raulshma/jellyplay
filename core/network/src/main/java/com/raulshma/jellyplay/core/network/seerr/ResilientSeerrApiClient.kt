package com.raulshma.jellyplay.core.network.seerr

import com.raulshma.jellyplay.core.model.seerr.*
import com.raulshma.jellyplay.core.network.RetryPolicy
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ResilientSeerrApiClient @Inject constructor(
    private val delegate: SeerrApiClientImpl,
) : SeerrApiClient by delegate {

    private suspend fun <T> withRetry(block: suspend () -> Result<T>): Result<T> =
        RetryPolicy.executeWithRetry(
            maxRetries = MAX_RETRIES,
            jitterFloorMs = 0L,
            block = block,
        )

    companion object {
        internal const val MAX_RETRIES = 4
    }

    override suspend fun loginJellyfin(
        baseUrl: String, username: String, password: String,
    ): Result<String> = withRetry { delegate.loginJellyfin(baseUrl, username, password) }

    override suspend fun loginLocal(
        baseUrl: String, email: String, password: String,
    ): Result<String> = withRetry { delegate.loginLocal(baseUrl, email, password) }

    override suspend fun testConnection(
        baseUrl: String, credentials: SeerrCredentials,
    ): Result<SeerrStatusResponse> = withRetry { delegate.testConnection(baseUrl, credentials) }

    override suspend fun search(
        baseUrl: String, credentials: SeerrCredentials, query: String, page: Int,
    ): Result<SeerrSearchResponse> = withRetry { delegate.search(baseUrl, credentials, query, page) }

    override suspend fun getMovieDetails(
        baseUrl: String, credentials: SeerrCredentials, tmdbId: Int,
    ): Result<SeerrMovieDetails> = withRetry { delegate.getMovieDetails(baseUrl, credentials, tmdbId) }

    override suspend fun getTvDetails(
        baseUrl: String, credentials: SeerrCredentials, tmdbId: Int,
    ): Result<SeerrTvDetails> = withRetry { delegate.getTvDetails(baseUrl, credentials, tmdbId) }

    override suspend fun getTvSeasonDetails(
        baseUrl: String, credentials: SeerrCredentials, tvId: Int, seasonNumber: Int,
    ): Result<SeerrSeasonDetail> = withRetry { delegate.getTvSeasonDetails(baseUrl, credentials, tvId, seasonNumber) }

    override suspend fun getMovieRatings(
        baseUrl: String, credentials: SeerrCredentials, tmdbId: Int,
    ): Result<SeerrRatings> = withRetry { delegate.getMovieRatings(baseUrl, credentials, tmdbId) }

    override suspend fun getTvRatings(
        baseUrl: String, credentials: SeerrCredentials, tmdbId: Int,
    ): Result<SeerrRatings> = withRetry { delegate.getTvRatings(baseUrl, credentials, tmdbId) }

    override suspend fun getMovieRatingsCombined(
        baseUrl: String, credentials: SeerrCredentials, tmdbId: Int,
    ): Result<SeerrRatings> = withRetry { delegate.getMovieRatingsCombined(baseUrl, credentials, tmdbId) }

    override suspend fun getMovieRecommendations(
        baseUrl: String, credentials: SeerrCredentials, tmdbId: Int, page: Int,
    ): Result<SeerrSearchResponse> = withRetry { delegate.getMovieRecommendations(baseUrl, credentials, tmdbId, page) }

    override suspend fun getMovieSimilar(
        baseUrl: String, credentials: SeerrCredentials, tmdbId: Int, page: Int,
    ): Result<SeerrSearchResponse> = withRetry { delegate.getMovieSimilar(baseUrl, credentials, tmdbId, page) }

    override suspend fun getTvRecommendations(
        baseUrl: String, credentials: SeerrCredentials, tmdbId: Int, page: Int,
    ): Result<SeerrSearchResponse> = withRetry { delegate.getTvRecommendations(baseUrl, credentials, tmdbId, page) }

    override suspend fun getTvSimilar(
        baseUrl: String, credentials: SeerrCredentials, tmdbId: Int, page: Int,
    ): Result<SeerrSearchResponse> = withRetry { delegate.getTvSimilar(baseUrl, credentials, tmdbId, page) }

    override suspend fun requestMedia(
        baseUrl: String, credentials: SeerrCredentials, mediaType: String, mediaId: Int,
        tvdbId: Int?, seasons: List<Int>?, serverId: Int?, profileId: Int?,
        rootFolder: String?, tags: List<Int>?,
    ): Result<SeerrMediaRequest> = withRetry { delegate.requestMedia(baseUrl, credentials, mediaType, mediaId, tvdbId, seasons, serverId, profileId, rootFolder, tags) }

    override suspend fun getRadarrSettings(
        baseUrl: String, credentials: SeerrCredentials,
    ): Result<List<SeerrRadarrSettings>> = withRetry { delegate.getRadarrSettings(baseUrl, credentials) }

    override suspend fun getSonarrSettings(
        baseUrl: String, credentials: SeerrCredentials,
    ): Result<List<SeerrSonarrSettings>> = withRetry { delegate.getSonarrSettings(baseUrl, credentials) }

    override suspend fun getRadarrServiceDetail(
        baseUrl: String, credentials: SeerrCredentials, id: Int,
    ): Result<SeerrRadarrServiceDetail> = withRetry { delegate.getRadarrServiceDetail(baseUrl, credentials, id) }

    override suspend fun getSonarrServiceDetail(
        baseUrl: String, credentials: SeerrCredentials, id: Int,
    ): Result<SeerrSonarrServiceDetail> = withRetry { delegate.getSonarrServiceDetail(baseUrl, credentials, id) }

    override suspend fun getServiceRadarrServers(
        baseUrl: String, credentials: SeerrCredentials,
    ): Result<List<SeerrServiceServer>> = withRetry { delegate.getServiceRadarrServers(baseUrl, credentials) }

    override suspend fun getServiceSonarrServers(
        baseUrl: String, credentials: SeerrCredentials,
    ): Result<List<SeerrServiceServer>> = withRetry { delegate.getServiceSonarrServers(baseUrl, credentials) }

    override suspend fun getServiceRadarrDetail(
        baseUrl: String, credentials: SeerrCredentials, id: Int,
    ): Result<SeerrRadarrServiceDetail> = withRetry { delegate.getServiceRadarrDetail(baseUrl, credentials, id) }

    override suspend fun getServiceSonarrDetail(
        baseUrl: String, credentials: SeerrCredentials, id: Int,
    ): Result<SeerrSonarrServiceDetail> = withRetry { delegate.getServiceSonarrDetail(baseUrl, credentials, id) }

    override suspend fun getTrending(
        baseUrl: String, credentials: SeerrCredentials, page: Int,
    ): Result<SeerrSearchResponse> = withRetry { delegate.getTrending(baseUrl, credentials, page) }

    override suspend fun getDiscoverMovies(
        baseUrl: String, credentials: SeerrCredentials, page: Int, primaryReleaseDateGte: String?,
    ): Result<SeerrSearchResponse> = withRetry { delegate.getDiscoverMovies(baseUrl, credentials, page, primaryReleaseDateGte) }

    override suspend fun getDiscoverTv(
        baseUrl: String, credentials: SeerrCredentials, page: Int, firstAirDateGte: String?,
    ): Result<SeerrSearchResponse> = withRetry { delegate.getDiscoverTv(baseUrl, credentials, page, firstAirDateGte) }

    override suspend fun getRequests(
        baseUrl: String, credentials: SeerrCredentials, take: Int, skip: Int,
        filter: String, sort: String, sortDirection: String, requestedBy: Int?, mediaType: String?,
    ): Result<SeerrRequestListResponse> = withRetry { delegate.getRequests(baseUrl, credentials, take, skip, filter, sort, sortDirection, requestedBy, mediaType) }

    override suspend fun getRequest(
        baseUrl: String, credentials: SeerrCredentials, id: Int,
    ): Result<SeerrRequestItem> = withRetry { delegate.getRequest(baseUrl, credentials, id) }

    override suspend fun approveRequest(
        baseUrl: String, credentials: SeerrCredentials, id: Int,
    ): Result<SeerrRequestItem> = withRetry { delegate.approveRequest(baseUrl, credentials, id) }

    override suspend fun declineRequest(
        baseUrl: String, credentials: SeerrCredentials, id: Int,
    ): Result<SeerrRequestItem> = withRetry { delegate.declineRequest(baseUrl, credentials, id) }

    override suspend fun retryRequest(
        baseUrl: String, credentials: SeerrCredentials, id: Int,
    ): Result<SeerrRequestItem> = withRetry { delegate.retryRequest(baseUrl, credentials, id) }

    override suspend fun deleteRequest(
        baseUrl: String, credentials: SeerrCredentials, id: Int,
    ): Result<Unit> = withRetry { delegate.deleteRequest(baseUrl, credentials, id) }

    override suspend fun editRequest(
        baseUrl: String, credentials: SeerrCredentials, id: Int, mediaType: String,
        mediaId: Int, serverId: Int?, profileId: Int?, rootFolder: String?, tags: List<Int>?, seasons: List<Int>?,
    ): Result<SeerrRequestItem> = withRetry { delegate.editRequest(baseUrl, credentials, id, mediaType, mediaId, serverId, profileId, rootFolder, tags, seasons) }

    override suspend fun getRequestCount(
        baseUrl: String, credentials: SeerrCredentials,
    ): Result<SeerrRequestCount> = withRetry { delegate.getRequestCount(baseUrl, credentials) }

    override suspend fun getCurrentUser(
        baseUrl: String, credentials: SeerrCredentials,
    ): Result<SeerrCurrentUser> = withRetry { delegate.getCurrentUser(baseUrl, credentials) }
}
