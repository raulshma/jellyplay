package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.seerr.SeerrRelatedVideo
import com.raulshma.jellyplay.core.model.seerr.TmdbReview
import io.ktor.client.HttpClient
import io.ktor.client.request.get

private const val TMDB_API_KEY = "1f54bd990f1cd6ca033b09cc0412a4d5" // Community TMDB API Key

/**
 * The wasmJs [TmdbApiClient] — a hand-rolled Ktor replacement for the
 * jvmShared `TmdbApiClientImpl` + `ResilientTmdbApiClient` pair (OkHttp).
 * Same fixed community API key, same `GET /3/{movie|tv}/{id}/{endpoint}?api_key=…`
 * URLs, same YouTube-only watch-URL synthesis, and the same error texts:
 * `TMDB request failed: {code}` (HTTP, body ignored — the JVM never reads
 * it), `TMDB network error: {message}` (transport, retryable), and
 * `TMDB parse error: {message}` (everything else, non-retryable). Decode +
 * mapping go through the commonMain [TmdbVideosResponseWire] /
 * [parseTmdbReviewsWire] helpers — transcriptions of the JVM impl's private
 * DTOs.
 *
 * Deltas (documented): the JVM maps `SocketTimeoutException` through its
 * IOException branch (`TMDB network error: …`) — wasm routes its one timeout
 * signal (HttpRequestTimeoutException) to the SAME text, which is why
 * [ArrSeerrApiSupport.timeoutFailureMessage] is null here. The JVM's
 * null-body branch (`emptyResponseBodyError("TMDB")`) is unreachable on both
 * platforms (empty bodies arrive as `""` and fail decoding → the parse-error
 * text). Retry lives HERE (max 4 = `ResilientTmdbApiClient.MAX_RETRIES`).
 */
class KtorWasmTmdbApiClient(
    httpClient: HttpClient,
) : ArrSeerrApiSupport(
    httpClient = httpClient,
    httpFailureMessage = { code, _ -> "TMDB request failed: $code" },
    timeoutFailureMessage = null,
    ioFailureMessage = { e -> "TMDB network error: ${e.message ?: ""}" },
    unclassifiedFailureMessage = { e -> "TMDB parse error: ${e.message ?: ""}" },
), TmdbApiClient {

    override suspend fun getVideos(tmdbId: Int, mediaType: MediaType): Result<List<SeerrRelatedVideo>> =
        apiResultWithRetry {
            getAndParse<TmdbVideosResponseWire>(
                url = tmdbUrl(tmdbId, mediaType, "videos"),
                // TMDB needs no auth header — the JVM request carries none.
                headers = emptyList(),
            ).results.map { it.toSeerrRelatedVideo() }
        }

    override suspend fun getReviews(tmdbId: Int, mediaType: MediaType): Result<List<TmdbReview>> =
        apiResultWithRetry {
            parseTmdbReviewsWire(
                executeForText {
                    httpClient.get(tmdbUrl(tmdbId, mediaType, "reviews"))
                },
            )
        }

    /** `TmdbApiClientImpl.tmdbFetch`'s URL: `/3/{movie|tv}/{id}/{endpoint}?api_key=$apiKey`. */
    private fun tmdbUrl(tmdbId: Int, mediaType: MediaType, endpoint: String): String {
        val typeStr = if (mediaType == MediaType.MOVIE) "movie" else "tv"
        return "https://api.themoviedb.org/3/$typeStr/$tmdbId/$endpoint?api_key=$TMDB_API_KEY"
    }
}
