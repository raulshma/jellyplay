package com.raulshma.jellyplay.core.network.seerr

import com.raulshma.jellyplay.core.model.seerr.*
import kotlinx.coroutines.delay
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

@Singleton
class ResilientSeerrApiClient @Inject constructor(
    private val delegate: SeerrApiClientImpl,
) : SeerrApiClient by delegate {

    companion object {
        internal const val MAX_RETRIES = 4
        internal val BASE_DELAY_MS = 1_000L
        internal val MAX_DELAY_MS = 8_000L
        internal const val BACKOFF_FACTOR = 2.0

        internal val RETRYABLE_STATUS_CODES = setOf(429, 500, 502, 503, 504)
    }

    private suspend fun <T> withRetry(block: suspend () -> Result<T>): Result<T> {
        var lastResult = block()

        repeat(MAX_RETRIES - 1) { attempt ->
            if (lastResult.isSuccess) return lastResult

            val exception = lastResult.exceptionOrNull() ?: return lastResult
            if (!isRetryable(exception)) return lastResult

            val backoffMs = calculateBackoff(attempt)
            delay(backoffMs)

            lastResult = block()
        }

        return lastResult
    }

    internal fun isRetryable(exception: Throwable): Boolean {
        if (exception is kotlinx.coroutines.CancellationException) return false

        when (exception) {
            is SocketTimeoutException -> return true
            is ConnectException -> return true
            is UnknownHostException -> return true
            is IOException -> return true
        }

        val message = exception.message ?: return false
        return RETRYABLE_STATUS_CODES.any { code ->
            message.contains("HTTP $code")
        }
    }

    internal fun calculateBackoff(attempt: Int): Long {
        val exponentialDelay = (BASE_DELAY_MS * BACKOFF_FACTOR.pow(attempt)).toLong()
        val cappedDelay = min(exponentialDelay, MAX_DELAY_MS)
        return Random.nextLong(0, cappedDelay + 1)
    }

    override suspend fun loginJellyfin(
        baseUrl: String,
        username: String,
        password: String,
    ): Result<String> = withRetry { delegate.loginJellyfin(baseUrl, username, password) }

    override suspend fun loginLocal(
        baseUrl: String,
        email: String,
        password: String,
    ): Result<String> = withRetry { delegate.loginLocal(baseUrl, email, password) }

    override suspend fun testConnection(baseUrl: String, credentials: SeerrCredentials): Result<SeerrStatusResponse> =
        withRetry { delegate.testConnection(baseUrl, credentials) }

    override suspend fun search(
        baseUrl: String,
        credentials: SeerrCredentials,
        query: String,
        page: Int,
    ): Result<SeerrSearchResponse> =
        withRetry { delegate.search(baseUrl, credentials, query, page) }

    override suspend fun getMovieDetails(
        baseUrl: String,
        credentials: SeerrCredentials,
        tmdbId: Int,
    ): Result<SeerrMovieDetails> =
        withRetry { delegate.getMovieDetails(baseUrl, credentials, tmdbId) }

    override suspend fun getTvDetails(
        baseUrl: String,
        credentials: SeerrCredentials,
        tmdbId: Int,
    ): Result<SeerrTvDetails> =
        withRetry { delegate.getTvDetails(baseUrl, credentials, tmdbId) }

    override suspend fun getTvSeasonDetails(
        baseUrl: String,
        credentials: SeerrCredentials,
        tvId: Int,
        seasonNumber: Int,
    ): Result<SeerrSeasonDetail> =
        withRetry { delegate.getTvSeasonDetails(baseUrl, credentials, tvId, seasonNumber) }

    override suspend fun getMovieRatings(
        baseUrl: String,
        credentials: SeerrCredentials,
        tmdbId: Int,
    ): Result<SeerrRatings> =
        withRetry { delegate.getMovieRatings(baseUrl, credentials, tmdbId) }

    override suspend fun getTvRatings(
        baseUrl: String,
        credentials: SeerrCredentials,
        tmdbId: Int,
    ): Result<SeerrRatings> =
        withRetry { delegate.getTvRatings(baseUrl, credentials, tmdbId) }

    override suspend fun getMovieRatingsCombined(
        baseUrl: String,
        credentials: SeerrCredentials,
        tmdbId: Int,
    ): Result<SeerrRatings> =
        withRetry { delegate.getMovieRatingsCombined(baseUrl, credentials, tmdbId) }

    override suspend fun getMovieRecommendations(
        baseUrl: String,
        credentials: SeerrCredentials,
        tmdbId: Int,
        page: Int,
    ): Result<SeerrSearchResponse> =
        withRetry { delegate.getMovieRecommendations(baseUrl, credentials, tmdbId, page) }

    override suspend fun getMovieSimilar(
        baseUrl: String,
        credentials: SeerrCredentials,
        tmdbId: Int,
        page: Int,
    ): Result<SeerrSearchResponse> =
        withRetry { delegate.getMovieSimilar(baseUrl, credentials, tmdbId, page) }

    override suspend fun getTvRecommendations(
        baseUrl: String,
        credentials: SeerrCredentials,
        tmdbId: Int,
        page: Int,
    ): Result<SeerrSearchResponse> =
        withRetry { delegate.getTvRecommendations(baseUrl, credentials, tmdbId, page) }

    override suspend fun getTvSimilar(
        baseUrl: String,
        credentials: SeerrCredentials,
        tmdbId: Int,
        page: Int,
    ): Result<SeerrSearchResponse> =
        withRetry { delegate.getTvSimilar(baseUrl, credentials, tmdbId, page) }

    override suspend fun requestMedia(
        baseUrl: String,
        credentials: SeerrCredentials,
        mediaType: String,
        mediaId: Int,
        tvdbId: Int?,
        seasons: List<Int>?,
        serverId: Int?,
        profileId: Int?,
        rootFolder: String?,
        tags: List<Int>?,
    ): Result<SeerrMediaRequest> =
        withRetry { delegate.requestMedia(baseUrl, credentials, mediaType, mediaId, tvdbId, seasons, serverId, profileId, rootFolder, tags) }

    override suspend fun getRadarrSettings(
        baseUrl: String,
        credentials: SeerrCredentials,
    ): Result<List<SeerrRadarrSettings>> =
        withRetry { delegate.getRadarrSettings(baseUrl, credentials) }

    override suspend fun getSonarrSettings(
        baseUrl: String,
        credentials: SeerrCredentials,
    ): Result<List<SeerrSonarrSettings>> =
        withRetry { delegate.getSonarrSettings(baseUrl, credentials) }

    override suspend fun getRadarrServiceDetail(
        baseUrl: String,
        credentials: SeerrCredentials,
        id: Int,
    ): Result<SeerrRadarrServiceDetail> =
        withRetry { delegate.getRadarrServiceDetail(baseUrl, credentials, id) }

    override suspend fun getSonarrServiceDetail(
        baseUrl: String,
        credentials: SeerrCredentials,
        id: Int,
    ): Result<SeerrSonarrServiceDetail> =
        withRetry { delegate.getSonarrServiceDetail(baseUrl, credentials, id) }

    override suspend fun getServiceRadarrServers(
        baseUrl: String,
        credentials: SeerrCredentials,
    ): Result<List<SeerrServiceServer>> =
        withRetry { delegate.getServiceRadarrServers(baseUrl, credentials) }

    override suspend fun getServiceSonarrServers(
        baseUrl: String,
        credentials: SeerrCredentials,
    ): Result<List<SeerrServiceServer>> =
        withRetry { delegate.getServiceSonarrServers(baseUrl, credentials) }

    override suspend fun getServiceRadarrDetail(
        baseUrl: String,
        credentials: SeerrCredentials,
        id: Int,
    ): Result<SeerrRadarrServiceDetail> =
        withRetry { delegate.getServiceRadarrDetail(baseUrl, credentials, id) }

    override suspend fun getServiceSonarrDetail(
        baseUrl: String,
        credentials: SeerrCredentials,
        id: Int,
    ): Result<SeerrSonarrServiceDetail> =
        withRetry { delegate.getServiceSonarrDetail(baseUrl, credentials, id) }

    override suspend fun getTrending(
        baseUrl: String,
        credentials: SeerrCredentials,
        page: Int,
    ): Result<SeerrSearchResponse> =
        withRetry { delegate.getTrending(baseUrl, credentials, page) }

    override suspend fun getDiscoverMovies(
        baseUrl: String,
        credentials: SeerrCredentials,
        page: Int,
        primaryReleaseDateGte: String?,
    ): Result<SeerrSearchResponse> =
        withRetry { delegate.getDiscoverMovies(baseUrl, credentials, page, primaryReleaseDateGte) }

    override suspend fun getDiscoverTv(
        baseUrl: String,
        credentials: SeerrCredentials,
        page: Int,
        firstAirDateGte: String?,
    ): Result<SeerrSearchResponse> =
        withRetry { delegate.getDiscoverTv(baseUrl, credentials, page, firstAirDateGte) }
}
