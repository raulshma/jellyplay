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

/**
 * Decorator that adds retry logic with exponential backoff and jitter to all
 * [SeerrApiClient] calls. Transient upstream failures (timeouts, 5xx errors,
 * connection resets) are automatically retried without surfacing errors to the
 * caller.
 *
 * Retries are applied **per-call** so that individual request failures do not
 * affect other in-flight requests.
 */
@Singleton
class ResilientSeerrApiClient @Inject constructor(
    private val delegate: SeerrApiClientImpl,
) : SeerrApiClient by delegate {

    companion object {
        internal const val MAX_RETRIES = 4
        internal val BASE_DELAY_MS = 1_000L // 1 second
        internal val MAX_DELAY_MS = 8_000L  // 8 seconds
        internal const val BACKOFF_FACTOR = 2.0

        /** HTTP status codes that indicate a transient/retryable server error. */
        internal val RETRYABLE_STATUS_CODES = setOf(429, 500, 502, 503, 504)
    }

    /**
     * Executes [block] with retry logic. Retries up to [MAX_RETRIES] times on
     * retryable errors with exponential backoff + full jitter.
     */
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

    /**
     * Determines if an exception is worth retrying.
     *
     * Retryable: network-level transient failures and server errors (5xx, 429).
     * Non-retryable: client errors (4xx), serialization issues, cancellation.
     */
    internal fun isRetryable(exception: Throwable): Boolean {
        // Never retry coroutine cancellation
        if (exception is kotlinx.coroutines.CancellationException) return false

        // Retry transient network exceptions
        when (exception) {
            is SocketTimeoutException -> return true
            is ConnectException -> return true
            is UnknownHostException -> return true
            is IOException -> return true
        }

        // Check if the error message indicates a retryable HTTP status code
        val message = exception.message ?: return false
        return RETRYABLE_STATUS_CODES.any { code ->
            message.contains("HTTP $code")
        }
    }

    /**
     * Calculates exponential backoff with full jitter.
     *
     * Formula: `random(0, min(MAX_DELAY, BASE_DELAY * 2^attempt))`
     *
     * Full jitter spreads retries evenly across the backoff window, reducing
     * thundering-herd problems when multiple requests fail simultaneously.
     */
    internal fun calculateBackoff(attempt: Int): Long {
        val exponentialDelay = (BASE_DELAY_MS * BACKOFF_FACTOR.pow(attempt)).toLong()
        val cappedDelay = min(exponentialDelay, MAX_DELAY_MS)
        return Random.nextLong(0, cappedDelay + 1)
    }

    // ── Retry-wrapped API methods ──
    // Only non-GET methods (like requestMedia) are truly idempotent concerns,
    // but Seerr's POST /request is also safe to retry since duplicate requests
    // are deduplicated server-side by TMDB ID. All GET methods are naturally safe.

    override suspend fun testConnection(baseUrl: String, apiKey: String): Result<SeerrStatusResponse> =
        withRetry { delegate.testConnection(baseUrl, apiKey) }

    override suspend fun search(
        baseUrl: String,
        apiKey: String,
        query: String,
        page: Int,
    ): Result<SeerrSearchResponse> =
        withRetry { delegate.search(baseUrl, apiKey, query, page) }

    override suspend fun getMovieDetails(
        baseUrl: String,
        apiKey: String,
        tmdbId: Int,
    ): Result<SeerrMovieDetails> =
        withRetry { delegate.getMovieDetails(baseUrl, apiKey, tmdbId) }

    override suspend fun getTvDetails(
        baseUrl: String,
        apiKey: String,
        tmdbId: Int,
    ): Result<SeerrTvDetails> =
        withRetry { delegate.getTvDetails(baseUrl, apiKey, tmdbId) }

    override suspend fun getMovieRatings(
        baseUrl: String,
        apiKey: String,
        tmdbId: Int,
    ): Result<SeerrRatings> =
        withRetry { delegate.getMovieRatings(baseUrl, apiKey, tmdbId) }

    override suspend fun getTvRatings(
        baseUrl: String,
        apiKey: String,
        tmdbId: Int,
    ): Result<SeerrRatings> =
        withRetry { delegate.getTvRatings(baseUrl, apiKey, tmdbId) }

    override suspend fun getMovieRatingsCombined(
        baseUrl: String,
        apiKey: String,
        tmdbId: Int,
    ): Result<SeerrRatings> =
        withRetry { delegate.getMovieRatingsCombined(baseUrl, apiKey, tmdbId) }

    override suspend fun getMovieRecommendations(
        baseUrl: String,
        apiKey: String,
        tmdbId: Int,
        page: Int,
    ): Result<SeerrSearchResponse> =
        withRetry { delegate.getMovieRecommendations(baseUrl, apiKey, tmdbId, page) }

    override suspend fun getMovieSimilar(
        baseUrl: String,
        apiKey: String,
        tmdbId: Int,
        page: Int,
    ): Result<SeerrSearchResponse> =
        withRetry { delegate.getMovieSimilar(baseUrl, apiKey, tmdbId, page) }

    override suspend fun getTvRecommendations(
        baseUrl: String,
        apiKey: String,
        tmdbId: Int,
        page: Int,
    ): Result<SeerrSearchResponse> =
        withRetry { delegate.getTvRecommendations(baseUrl, apiKey, tmdbId, page) }

    override suspend fun getTvSimilar(
        baseUrl: String,
        apiKey: String,
        tmdbId: Int,
        page: Int,
    ): Result<SeerrSearchResponse> =
        withRetry { delegate.getTvSimilar(baseUrl, apiKey, tmdbId, page) }

    override suspend fun requestMedia(
        baseUrl: String,
        apiKey: String,
        mediaType: String,
        mediaId: Int,
        tvdbId: Int?,
        seasons: List<Int>?,
        serverId: Int?,
        profileId: Int?,
        rootFolder: String?,
        tags: List<Int>?,
    ): Result<SeerrMediaRequest> =
        withRetry { delegate.requestMedia(baseUrl, apiKey, mediaType, mediaId, tvdbId, seasons, serverId, profileId, rootFolder, tags) }

    override suspend fun getRadarrSettings(
        baseUrl: String,
        apiKey: String,
    ): Result<List<SeerrRadarrSettings>> =
        withRetry { delegate.getRadarrSettings(baseUrl, apiKey) }

    override suspend fun getSonarrSettings(
        baseUrl: String,
        apiKey: String,
    ): Result<List<SeerrSonarrSettings>> =
        withRetry { delegate.getSonarrSettings(baseUrl, apiKey) }

    override suspend fun getRadarrServiceDetail(
        baseUrl: String,
        apiKey: String,
        id: Int,
    ): Result<SeerrRadarrServiceDetail> =
        withRetry { delegate.getRadarrServiceDetail(baseUrl, apiKey, id) }

    override suspend fun getSonarrServiceDetail(
        baseUrl: String,
        apiKey: String,
        id: Int,
    ): Result<SeerrSonarrServiceDetail> =
        withRetry { delegate.getSonarrServiceDetail(baseUrl, apiKey, id) }

    // ── /service/ endpoint retries ──

    override suspend fun getServiceRadarrServers(
        baseUrl: String,
        apiKey: String,
    ): Result<List<SeerrServiceServer>> =
        withRetry { delegate.getServiceRadarrServers(baseUrl, apiKey) }

    override suspend fun getServiceSonarrServers(
        baseUrl: String,
        apiKey: String,
    ): Result<List<SeerrServiceServer>> =
        withRetry { delegate.getServiceSonarrServers(baseUrl, apiKey) }

    override suspend fun getServiceRadarrDetail(
        baseUrl: String,
        apiKey: String,
        id: Int,
    ): Result<SeerrRadarrServiceDetail> =
        withRetry { delegate.getServiceRadarrDetail(baseUrl, apiKey, id) }

    override suspend fun getServiceSonarrDetail(
        baseUrl: String,
        apiKey: String,
        id: Int,
    ): Result<SeerrSonarrServiceDetail> =
        withRetry { delegate.getServiceSonarrDetail(baseUrl, apiKey, id) }
}
