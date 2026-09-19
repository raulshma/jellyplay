package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.seerr.SeerrRelatedVideo
import com.raulshma.jellyplay.core.model.seerr.TmdbReview
import com.raulshma.jellyplay.core.network.RetryPolicy
import com.raulshma.jellyplay.core.network.seerr.SeerrApiClientImpl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class TmdbReviewsResponse(
    val results: List<TmdbReview> = emptyList()
)

/** Parses a TMDB `/reviews` response body. Top-level so parsing is unit-testable. */
internal fun parseTmdbReviews(json: Json, text: String): List<TmdbReview> =
    json.decodeFromString<TmdbReviewsResponse>(text).results

@Singleton
class TmdbApiClientImpl @Inject constructor(
    okHttpClient: OkHttpClient,
) : TmdbApiClient {

    private val json = SeerrApiClientImpl.lenientJson

    private val apiKey = "1f54bd990f1cd6ca033b09cc0412a4d5" // Community TMDB API Key

    @Serializable
    private data class TmdbVideosResponse(
        val results: List<TmdbVideo> = emptyList()
    )

    @Serializable
    private data class TmdbVideo(
        val key: String? = null,
        val name: String? = null,
        val size: Int = 0,
        val type: String? = null,
        val site: String? = null,
    )

    /**
     * The shared OkHttp execute chassis. TMDB's declared deltas ride the
     * Options: a body-ignoring HTTP-failure text, its own network/parse
     * texts, the IOException-backed "Empty response from TMDB" arm, and
     * Retry-After capture (new with the chassis fold — TMDB rate-limits, and
     * [RetryPolicy] now floors its backoff at the server's advice). Retry
     * rides [tmdbFetch] itself at [HttpExecutor.MAX_RETRIES], replacing the
     * deleted `ResilientTmdbApiClient` DI wrapper.
     */
    private val http = HttpExecutor(
        okHttpClient = okHttpClient,
        options = HttpExecutor.Options(
            parseErrorMessage = { code, _ -> "TMDB request failed: $code" },
            formatNetworkError = { e -> "TMDB network error: ${e.message ?: ""}" },
            captureRetryAfter = true,
            emptyBodyText = "Empty response from TMDB",
        ),
    )

    override suspend fun getVideos(tmdbId: Int, mediaType: MediaType): Result<List<SeerrRelatedVideo>> =
        tmdbFetch(tmdbId, mediaType, "videos") { text ->
            json.decodeFromString<TmdbVideosResponse>(text).results.map {
                SeerrRelatedVideo(
                    key = it.key,
                    name = it.name,
                    size = it.size,
                    type = it.type,
                    site = it.site,
                    url = if (it.site?.lowercase() == "youtube") "https://www.youtube.com/watch?v=${it.key}" else null
                )
            }
        }

    override suspend fun getReviews(tmdbId: Int, mediaType: MediaType): Result<List<TmdbReview>> =
        tmdbFetch(tmdbId, mediaType, "reviews") { parseTmdbReviews(json, it) }

    /**
     * Shared GET `/3/{movie|tv}/{id}/{endpoint}` plumbing: builds the URL and
     * runs ONE retry-wrapped execution on the [HttpExecutor] chassis — the
     * body text via [HttpExecutor.executeForBodyText] (HTTP-status failures
     * throw the Options-shaped [ApiException], an absent body the
     * "Empty response from TMDB" arm), then [parse] maps it. Transport
     * failures map onto [ApiException] fromNetwork; parse failures surface
     * as the non-retryable "TMDB parse error" instead of throwing
     * SerializationException raw.
     */
    private suspend fun <T> tmdbFetch(
        tmdbId: Int,
        mediaType: MediaType,
        endpoint: String,
        parse: (String) -> T,
    ): Result<T> {
        val typeStr = if (mediaType == MediaType.MOVIE) "movie" else "tv"
        val url = "https://api.themoviedb.org/3/$typeStr/$tmdbId/$endpoint?api_key=$apiKey"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        return RetryPolicy.executeWithRetry(maxRetries = HttpExecutor.MAX_RETRIES) {
            try {
                withContext(Dispatchers.IO) {
                    val text = http.executeForBodyText(request)
                    Result.success(parse(text))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                // Already classified by the chassis (HTTP status / empty body).
                Result.failure(e)
            } catch (e: IOException) {
                Result.failure(ApiException.fromNetwork(e, "TMDB network error: ${e.message ?: ""}"))
            } catch (e: Exception) {
                Result.failure(
                    ApiException(
                        isRetryable = false,
                        message = "TMDB parse error: ${e.message ?: ""}",
                        cause = e,
                    ),
                )
            }
        }
    }
}
