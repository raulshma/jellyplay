package com.raulshma.jellyplay.core.network.seerr

import com.raulshma.jellyplay.core.model.seerr.*
import com.raulshma.jellyplay.core.network.RetryPolicy
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resilient wrapper around [SeerrApiClientImpl] that applies [RetryPolicy] to every
 * [SeerrApiClient] method.
 *
 * **Note:** This class deliberately implements [SeerrApiClient] directly (rather than using
 * Kotlin interface delegation `by delegate`) so that adding a new method to the interface
 * produces a compile error here, forcing the author to wire it through [req]. Previously,
 * `deleteMedia` (and any future method) silently bypassed retry because the auto-generated
 * delegation forwarded straight to the underlying client.
 */
@Singleton
class ResilientSeerrApiClient @Inject constructor(
    private val delegate: SeerrApiClientImpl,
) : SeerrApiClient {

    private suspend fun <T> req(block: suspend () -> Result<T>): Result<T> =
        RetryPolicy.executeWithRetry(
            maxRetries = MAX_RETRIES,
            block = block,
        )

    override suspend fun loginJellyfin(
        baseUrl: String, username: String, password: String,
    ): Result<String> = req { delegate.loginJellyfin(baseUrl, username, password) }

    override suspend fun loginLocal(
        baseUrl: String, email: String, password: String,
    ): Result<String> = req { delegate.loginLocal(baseUrl, email, password) }

    override suspend fun testConnection(
        baseUrl: String, credentials: SeerrCredentials,
    ): Result<SeerrStatusResponse> = req { delegate.testConnection(baseUrl, credentials) }

    override suspend fun search(
        baseUrl: String, credentials: SeerrCredentials, query: String, page: Int,
    ): Result<SeerrSearchResponse> = req { delegate.search(baseUrl, credentials, query, page) }

    override suspend fun getMovieDetails(
        baseUrl: String, credentials: SeerrCredentials, tmdbId: Int,
    ): Result<SeerrMovieDetails> = req { delegate.getMovieDetails(baseUrl, credentials, tmdbId) }

    override suspend fun getTvDetails(
        baseUrl: String, credentials: SeerrCredentials, tmdbId: Int,
    ): Result<SeerrTvDetails> = req { delegate.getTvDetails(baseUrl, credentials, tmdbId) }

    override suspend fun getTvSeasonDetails(
        baseUrl: String, credentials: SeerrCredentials, tvId: Int, seasonNumber: Int,
    ): Result<SeerrSeasonDetail> = req { delegate.getTvSeasonDetails(baseUrl, credentials, tvId, seasonNumber) }

    override suspend fun getMovieRatings(
        baseUrl: String, credentials: SeerrCredentials, tmdbId: Int,
    ): Result<SeerrRatings> = req { delegate.getMovieRatings(baseUrl, credentials, tmdbId) }

    override suspend fun getTvRatings(
        baseUrl: String, credentials: SeerrCredentials, tmdbId: Int,
    ): Result<SeerrRatings> = req { delegate.getTvRatings(baseUrl, credentials, tmdbId) }

    override suspend fun getMovieRatingsCombined(
        baseUrl: String, credentials: SeerrCredentials, tmdbId: Int,
    ): Result<SeerrRatings> = req { delegate.getMovieRatingsCombined(baseUrl, credentials, tmdbId) }

    override suspend fun getMovieRecommendations(
        baseUrl: String, credentials: SeerrCredentials, tmdbId: Int, page: Int,
    ): Result<SeerrSearchResponse> = req { delegate.getMovieRecommendations(baseUrl, credentials, tmdbId, page) }

    override suspend fun getMovieSimilar(
        baseUrl: String, credentials: SeerrCredentials, tmdbId: Int, page: Int,
    ): Result<SeerrSearchResponse> = req { delegate.getMovieSimilar(baseUrl, credentials, tmdbId, page) }

    override suspend fun getTvRecommendations(
        baseUrl: String, credentials: SeerrCredentials, tmdbId: Int, page: Int,
    ): Result<SeerrSearchResponse> = req { delegate.getTvRecommendations(baseUrl, credentials, tmdbId, page) }

    override suspend fun getTvSimilar(
        baseUrl: String, credentials: SeerrCredentials, tmdbId: Int, page: Int,
    ): Result<SeerrSearchResponse> = req { delegate.getTvSimilar(baseUrl, credentials, tmdbId, page) }

    override suspend fun requestMedia(
        baseUrl: String, credentials: SeerrCredentials, mediaType: String, mediaId: Int,
        tvdbId: Int?, seasons: List<Int>?, serverId: Int?, profileId: Int?,
        rootFolder: String?, tags: List<Int>?,
    ): Result<SeerrMediaRequest> = req { delegate.requestMedia(baseUrl, credentials, mediaType, mediaId, tvdbId, seasons, serverId, profileId, rootFolder, tags) }

    override suspend fun getRadarrSettings(
        baseUrl: String, credentials: SeerrCredentials,
    ): Result<List<SeerrRadarrSettings>> = req { delegate.getRadarrSettings(baseUrl, credentials) }

    override suspend fun getSonarrSettings(
        baseUrl: String, credentials: SeerrCredentials,
    ): Result<List<SeerrSonarrSettings>> = req { delegate.getSonarrSettings(baseUrl, credentials) }

    override suspend fun getRadarrServiceDetail(
        baseUrl: String, credentials: SeerrCredentials, id: Int,
    ): Result<SeerrRadarrServiceDetail> = req { delegate.getRadarrServiceDetail(baseUrl, credentials, id) }

    override suspend fun getSonarrServiceDetail(
        baseUrl: String, credentials: SeerrCredentials, id: Int,
    ): Result<SeerrSonarrServiceDetail> = req { delegate.getSonarrServiceDetail(baseUrl, credentials, id) }

    override suspend fun getServiceRadarrServers(
        baseUrl: String, credentials: SeerrCredentials,
    ): Result<List<SeerrServiceServer>> = req { delegate.getServiceRadarrServers(baseUrl, credentials) }

    override suspend fun getServiceSonarrServers(
        baseUrl: String, credentials: SeerrCredentials,
    ): Result<List<SeerrServiceServer>> = req { delegate.getServiceSonarrServers(baseUrl, credentials) }

    override suspend fun getServiceRadarrDetail(
        baseUrl: String, credentials: SeerrCredentials, id: Int,
    ): Result<SeerrRadarrServiceDetail> = req { delegate.getServiceRadarrDetail(baseUrl, credentials, id) }

    override suspend fun getServiceSonarrDetail(
        baseUrl: String, credentials: SeerrCredentials, id: Int,
    ): Result<SeerrSonarrServiceDetail> = req { delegate.getServiceSonarrDetail(baseUrl, credentials, id) }

    override suspend fun getTrending(
        baseUrl: String, credentials: SeerrCredentials, page: Int,
    ): Result<SeerrSearchResponse> = req { delegate.getTrending(baseUrl, credentials, page) }

    override suspend fun getDiscoverMovies(
        baseUrl: String, credentials: SeerrCredentials, page: Int, primaryReleaseDateGte: String?,
    ): Result<SeerrSearchResponse> = req { delegate.getDiscoverMovies(baseUrl, credentials, page, primaryReleaseDateGte) }

    override suspend fun getDiscoverTv(
        baseUrl: String, credentials: SeerrCredentials, page: Int, firstAirDateGte: String?,
    ): Result<SeerrSearchResponse> = req { delegate.getDiscoverTv(baseUrl, credentials, page, firstAirDateGte) }

    override suspend fun getRequests(
        baseUrl: String, credentials: SeerrCredentials, take: Int, skip: Int,
        filter: String, sort: String, sortDirection: String, requestedBy: Int?, mediaType: String?,
        search: String?,
    ): Result<SeerrRequestListResponse> = req { delegate.getRequests(baseUrl, credentials, take, skip, filter, sort, sortDirection, requestedBy, mediaType, search) }

    override suspend fun getRequest(
        baseUrl: String, credentials: SeerrCredentials, id: Int,
    ): Result<SeerrRequestItem> = req { delegate.getRequest(baseUrl, credentials, id) }

    override suspend fun approveRequest(
        baseUrl: String, credentials: SeerrCredentials, id: Int,
    ): Result<SeerrRequestItem> = req { delegate.approveRequest(baseUrl, credentials, id) }

    override suspend fun declineRequest(
        baseUrl: String, credentials: SeerrCredentials, id: Int,
    ): Result<SeerrRequestItem> = req { delegate.declineRequest(baseUrl, credentials, id) }

    override suspend fun retryRequest(
        baseUrl: String, credentials: SeerrCredentials, id: Int,
    ): Result<SeerrRequestItem> = req { delegate.retryRequest(baseUrl, credentials, id) }

    override suspend fun deleteRequest(
        baseUrl: String, credentials: SeerrCredentials, id: Int,
    ): Result<Unit> = req { delegate.deleteRequest(baseUrl, credentials, id) }

    override suspend fun deleteMedia(
        baseUrl: String, credentials: SeerrCredentials, mediaId: Int, is4k: Boolean,
    ): Result<Unit> = req { delegate.deleteMedia(baseUrl, credentials, mediaId, is4k) }

    override suspend fun editRequest(
        baseUrl: String, credentials: SeerrCredentials, id: Int, mediaType: String,
        mediaId: Int, serverId: Int?, profileId: Int?, rootFolder: String?, tags: List<Int>?, seasons: List<Int>?,
    ): Result<SeerrRequestItem> = req { delegate.editRequest(baseUrl, credentials, id, mediaType, mediaId, serverId, profileId, rootFolder, tags, seasons) }

    override suspend fun getRequestCount(
        baseUrl: String, credentials: SeerrCredentials,
    ): Result<SeerrRequestCount> = req { delegate.getRequestCount(baseUrl, credentials) }

    override suspend fun getCurrentUser(
        baseUrl: String, credentials: SeerrCredentials,
    ): Result<SeerrCurrentUser> = req { delegate.getCurrentUser(baseUrl, credentials) }

    companion object {
        internal const val MAX_RETRIES = 4
    }
}
