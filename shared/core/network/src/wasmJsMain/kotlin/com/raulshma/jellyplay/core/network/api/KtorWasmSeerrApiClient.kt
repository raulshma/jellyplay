package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.seerr.SeerrAuthJellyfinRequest
import com.raulshma.jellyplay.core.model.seerr.SeerrAuthLocalRequest
import com.raulshma.jellyplay.core.model.seerr.SeerrCredentials
import com.raulshma.jellyplay.core.model.seerr.SeerrCurrentUser
import com.raulshma.jellyplay.core.model.seerr.SeerrEditRequestPayload
import com.raulshma.jellyplay.core.model.seerr.SeerrMediaRequest
import com.raulshma.jellyplay.core.model.seerr.SeerrMovieDetails
import com.raulshma.jellyplay.core.model.seerr.SeerrRadarrServiceDetail
import com.raulshma.jellyplay.core.model.seerr.SeerrRadarrSettings
import com.raulshma.jellyplay.core.model.seerr.SeerrRequestCount
import com.raulshma.jellyplay.core.model.seerr.SeerrRequestItem
import com.raulshma.jellyplay.core.model.seerr.SeerrRequestListResponse
import com.raulshma.jellyplay.core.model.seerr.SeerrRequestPayload
import com.raulshma.jellyplay.core.model.seerr.SeerrRatings
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchResponse
import com.raulshma.jellyplay.core.model.seerr.SeerrSeasonDetail
import com.raulshma.jellyplay.core.model.seerr.SeerrServiceServer
import com.raulshma.jellyplay.core.model.seerr.SeerrSonarrServiceDetail
import com.raulshma.jellyplay.core.model.seerr.SeerrSonarrSettings
import com.raulshma.jellyplay.core.model.seerr.SeerrStatusResponse
import com.raulshma.jellyplay.core.model.seerr.SeerrTvDetails
import com.raulshma.jellyplay.core.network.seerr.SeerrApiClient
import com.raulshma.jellyplay.core.network.seerr.seerrApiUrl
import com.raulshma.jellyplay.core.network.seerr.seerrAuthHeaders
import com.raulshma.jellyplay.core.network.seerr.seerrDiscoverMoviesPath
import com.raulshma.jellyplay.core.network.seerr.seerrDiscoverTvPath
import com.raulshma.jellyplay.core.network.seerr.seerrHttpErrorMessage
import com.raulshma.jellyplay.core.network.seerr.seerrRequestsPath
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import kotlinx.serialization.encodeToString

/**
 * `SeerrApiClientImpl.formatNetworkError`'s SocketTimeoutException branch —
 * the only timeout text wasm can reproduce (the fetch engine offers no
 * DNS/refused distinction; see [ArrSeerrApiSupport]'s taxonomy note).
 */
private const val SEERR_TIMEOUT_MESSAGE = "Connection timed out. The server took too long to respond."

/** The IOException branch of `SeerrApiClientImpl.formatNetworkError`. */
private fun seerrIoFailureMessage(e: Throwable): String =
    "Network error: ${e.message ?: e::class.simpleName ?: ""}"

/** The `else` branch of `SeerrApiClientImpl.formatNetworkError`. */
private fun seerrUnclassifiedFailureMessage(e: Throwable): String =
    e.message ?: e::class.simpleName ?: ""

/**
 * The wasmJs [SeerrApiClient] — a hand-rolled Ktor replacement for the
 * jvmShared `SeerrApiClientImpl` + `ResilientSeerrApiClient` pair (OkHttp).
 * Endpoint paths, query-string assembly, request bodies, `parseErrorMessage`
 * and `formatNetworkError` texts, and the login Set-Cookie capture mirror the
 * JVM implementation request-for-request, string-for-string; the decode path
 * consumes the commonMain `core.model.seerr` types DIRECTLY — exactly what
 * the JVM impl does (it has no intermediate wire DTOs for this seam).
 *
 * Structure deltas vs the JVM pair (all documented):
 *  - Retry lives HERE (`apiResultWithRetry`, max 4 =
 *    `ResilientSeerrApiClient.MAX_RETRIES`) instead of in a DI-level
 *    Resilient wrapper; the wasm DI module binds the interface straight to
 *    this class.
 *  - Per-call credentials replace OkHttp's `withAuth` request decorator
 *    ([seerrAuthHeaders] is that `when` as data). Browsers strip the `Cookie`
 *    REQUEST header (fetch forbidden-header rule) and never expose
 *    `Set-Cookie` RESPONSE headers — session-cookie auth and login cookie
 *    capture cannot work inside a browser tab; API-key credentials are
 *    unaffected. The wire code itself is faithful and correct outside the
 *    browser sandbox.
 *
 * See [ArrSeerrApiSupport] for the remaining wasm deltas (transport taxonomy
 * collapse, Retry-After honoring, decode-failure wrapping).
 */
class KtorWasmSeerrApiClient(
    httpClient: HttpClient,
) : ArrSeerrApiSupport(
    httpClient = httpClient,
    httpFailureMessage = ::seerrHttpErrorMessage,
    timeoutFailureMessage = SEERR_TIMEOUT_MESSAGE,
    ioFailureMessage = ::seerrIoFailureMessage,
    unclassifiedFailureMessage = ::seerrUnclassifiedFailureMessage,
), SeerrApiClient {

    override suspend fun loginJellyfin(
        baseUrl: String,
        username: String,
        password: String,
    ): Result<String> = login(seerrApiUrl(baseUrl, "/auth/jellyfin")) {
        wireJson.encodeToString(SeerrAuthJellyfinRequest(username, password))
    }

    override suspend fun loginLocal(
        baseUrl: String,
        email: String,
        password: String,
    ): Result<String> = login(seerrApiUrl(baseUrl, "/auth/local")) {
        wireJson.encodeToString(SeerrAuthLocalRequest(email, password))
    }

    /**
     * The shared login spine (`loginJellyfin`/`loginLocal` are identical
     * apart from path + payload): POST the credentials UNAUTHENTICATED (the
     * JVM builds these requests without `withAuth`), then return the
     * captured session cookie — or fail with the JVM-identical message.
     */
    private suspend fun login(url: String, payload: () -> String): Result<String> = apiResultWithRetry {
        val (_, cookie) = executeForCookie {
            httpClient.post(url) {
                setBody(TextContent(payload(), ContentType.Application.Json))
            }
        }
        cookie ?: throw Exception("No session cookie received from server")
    }

    override suspend fun testConnection(baseUrl: String, credentials: SeerrCredentials): Result<SeerrStatusResponse> =
        getAndParseResult(baseUrl, "/status", credentials)

    override suspend fun search(
        baseUrl: String,
        credentials: SeerrCredentials,
        query: String,
        page: Int,
    ): Result<SeerrSearchResponse> = apiResultWithRetry {
        getAndParse<SeerrSearchResponse>(
            url = seerrApiUrl(baseUrl, "/search"),
            headers = seerrAuthHeaders(credentials),
            // OkHttp addQueryParameter counterpart (both encode space as %20).
            query = listOf("query" to query, "page" to page.toString()),
        )
    }

    override suspend fun getMovieDetails(baseUrl: String, credentials: SeerrCredentials, tmdbId: Int): Result<SeerrMovieDetails> =
        getAndParseResult(baseUrl, "/movie/$tmdbId", credentials)

    override suspend fun getTvDetails(baseUrl: String, credentials: SeerrCredentials, tmdbId: Int): Result<SeerrTvDetails> =
        getAndParseResult(baseUrl, "/tv/$tmdbId", credentials)

    override suspend fun getTvSeasonDetails(
        baseUrl: String,
        credentials: SeerrCredentials,
        tvId: Int,
        seasonNumber: Int,
    ): Result<SeerrSeasonDetail> =
        getAndParseResult(baseUrl, "/tv/$tvId/season/$seasonNumber", credentials)

    override suspend fun getMovieRatings(baseUrl: String, credentials: SeerrCredentials, tmdbId: Int): Result<SeerrRatings> =
        getAndParseResult(baseUrl, "/movie/$tmdbId/ratings", credentials)

    override suspend fun getTvRatings(baseUrl: String, credentials: SeerrCredentials, tmdbId: Int): Result<SeerrRatings> =
        getAndParseResult(baseUrl, "/tv/$tmdbId/ratings", credentials)

    override suspend fun getMovieRatingsCombined(baseUrl: String, credentials: SeerrCredentials, tmdbId: Int): Result<SeerrRatings> =
        getAndParseResult(baseUrl, "/movie/$tmdbId/ratingscombined", credentials)

    override suspend fun getMovieRecommendations(
        baseUrl: String,
        credentials: SeerrCredentials,
        tmdbId: Int,
        page: Int,
    ): Result<SeerrSearchResponse> =
        getAndParseResult(baseUrl, "/movie/$tmdbId/recommendations?page=$page", credentials)

    override suspend fun getMovieSimilar(baseUrl: String, credentials: SeerrCredentials, tmdbId: Int, page: Int): Result<SeerrSearchResponse> =
        getAndParseResult(baseUrl, "/movie/$tmdbId/similar?page=$page", credentials)

    override suspend fun getTvRecommendations(
        baseUrl: String,
        credentials: SeerrCredentials,
        tmdbId: Int,
        page: Int,
    ): Result<SeerrSearchResponse> =
        getAndParseResult(baseUrl, "/tv/$tmdbId/recommendations?page=$page", credentials)

    override suspend fun getTvSimilar(baseUrl: String, credentials: SeerrCredentials, tmdbId: Int, page: Int): Result<SeerrSearchResponse> =
        getAndParseResult(baseUrl, "/tv/$tmdbId/similar?page=$page", credentials)

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
    ): Result<SeerrMediaRequest> = apiResultWithRetry {
        postAndParse<SeerrMediaRequest>(
            url = seerrApiUrl(baseUrl, "/request"),
            headers = seerrAuthHeaders(credentials),
            // encodeDefaults=false: nulls and the false is4k (never set by the
            // JVM impl) are omitted from the wire, byte-parity with OkHttp.
            bodyText = wireJson.encodeToString(
                SeerrRequestPayload(
                    mediaType = mediaType,
                    mediaId = mediaId,
                    tvdbId = tvdbId,
                    seasons = seasons,
                    serverId = serverId,
                    profileId = profileId,
                    rootFolder = rootFolder,
                    tags = tags,
                ),
            ),
        )
    }

    override suspend fun getRadarrSettings(baseUrl: String, credentials: SeerrCredentials): Result<List<SeerrRadarrSettings>> =
        getAndParseResult(baseUrl, "/settings/radarr", credentials)

    override suspend fun getSonarrSettings(baseUrl: String, credentials: SeerrCredentials): Result<List<SeerrSonarrSettings>> =
        getAndParseResult(baseUrl, "/settings/sonarr", credentials)

    override suspend fun getRadarrServiceDetail(baseUrl: String, credentials: SeerrCredentials, id: Int): Result<SeerrRadarrServiceDetail> =
        getAndParseResult(baseUrl, "/settings/radarr/$id", credentials)

    override suspend fun getSonarrServiceDetail(baseUrl: String, credentials: SeerrCredentials, id: Int): Result<SeerrSonarrServiceDetail> =
        getAndParseResult(baseUrl, "/settings/sonarr/$id", credentials)

    override suspend fun getServiceRadarrServers(baseUrl: String, credentials: SeerrCredentials): Result<List<SeerrServiceServer>> =
        getAndParseResult(baseUrl, "/service/radarr", credentials)

    override suspend fun getServiceSonarrServers(baseUrl: String, credentials: SeerrCredentials): Result<List<SeerrServiceServer>> =
        getAndParseResult(baseUrl, "/service/sonarr", credentials)

    override suspend fun getServiceRadarrDetail(baseUrl: String, credentials: SeerrCredentials, id: Int): Result<SeerrRadarrServiceDetail> =
        getAndParseResult(baseUrl, "/service/radarr/$id", credentials)

    override suspend fun getServiceSonarrDetail(baseUrl: String, credentials: SeerrCredentials, id: Int): Result<SeerrSonarrServiceDetail> =
        getAndParseResult(baseUrl, "/service/sonarr/$id", credentials)

    override suspend fun getTrending(baseUrl: String, credentials: SeerrCredentials, page: Int): Result<SeerrSearchResponse> =
        getAndParseResult(baseUrl, "/discover/trending?page=$page", credentials)

    override suspend fun getDiscoverMovies(
        baseUrl: String,
        credentials: SeerrCredentials,
        page: Int,
        primaryReleaseDateGte: String?,
    ): Result<SeerrSearchResponse> =
        getAndParseResult(baseUrl, seerrDiscoverMoviesPath(page, primaryReleaseDateGte), credentials)

    override suspend fun getDiscoverTv(
        baseUrl: String,
        credentials: SeerrCredentials,
        page: Int,
        firstAirDateGte: String?,
    ): Result<SeerrSearchResponse> =
        getAndParseResult(baseUrl, seerrDiscoverTvPath(page, firstAirDateGte), credentials)

    override suspend fun getRequests(
        baseUrl: String,
        credentials: SeerrCredentials,
        take: Int,
        skip: Int,
        filter: String,
        sort: String,
        sortDirection: String,
        requestedBy: Int?,
        mediaType: String?,
        search: String?,
    ): Result<SeerrRequestListResponse> =
        getAndParseResult(
            baseUrl,
            seerrRequestsPath(take, skip, filter, sort, sortDirection, requestedBy, mediaType, search),
            credentials,
        )

    override suspend fun getRequest(baseUrl: String, credentials: SeerrCredentials, id: Int): Result<SeerrRequestItem> =
        getAndParseResult(baseUrl, "/request/$id", credentials)

    override suspend fun approveRequest(baseUrl: String, credentials: SeerrCredentials, id: Int): Result<SeerrRequestItem> =
        postAndParseResult(baseUrl, "/request/$id/approve", credentials)

    override suspend fun declineRequest(baseUrl: String, credentials: SeerrCredentials, id: Int): Result<SeerrRequestItem> =
        postAndParseResult(baseUrl, "/request/$id/decline", credentials)

    override suspend fun retryRequest(baseUrl: String, credentials: SeerrCredentials, id: Int): Result<SeerrRequestItem> =
        postAndParseResult(baseUrl, "/request/$id/retry", credentials)

    override suspend fun deleteRequest(baseUrl: String, credentials: SeerrCredentials, id: Int): Result<Unit> =
        apiResultWithRetry {
            executeForText {
                httpClient.delete(seerrApiUrl(baseUrl, "/request/$id")) {
                    attachHeaders(seerrAuthHeaders(credentials))
                }
            }
            Unit
        }

    override suspend fun deleteMedia(
        baseUrl: String,
        credentials: SeerrCredentials,
        mediaId: Int,
        is4k: Boolean,
    ): Result<Unit> = apiResultWithRetry {
        // Step 1 (best-effort — the JVM `runCatching` swallow kept verbatim):
        // delete the media FILE from the *arr; a failure here must not abort
        // the media deletion itself.
        runCatching {
            executeForText {
                httpClient.delete(seerrApiUrl(baseUrl, "/media/$mediaId/file?is4k=$is4k")) {
                    attachHeaders(seerrAuthHeaders(credentials))
                }
            }
        }
        // Step 2: delete the media record; ITS result is the flow result.
        executeForText {
            httpClient.delete(seerrApiUrl(baseUrl, "/media/$mediaId")) {
                attachHeaders(seerrAuthHeaders(credentials))
            }
        }
        Unit
    }

    override suspend fun editRequest(
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
    ): Result<SeerrRequestItem> = apiResultWithRetry {
        putAndParse<SeerrRequestItem>(
            url = seerrApiUrl(baseUrl, "/request/$id"),
            headers = seerrAuthHeaders(credentials),
            bodyText = wireJson.encodeToString(
                SeerrEditRequestPayload(
                    mediaType = mediaType,
                    mediaId = mediaId,
                    serverId = serverId,
                    profileId = profileId,
                    rootFolder = rootFolder,
                    tags = tags,
                    seasons = seasons,
                ),
            ),
        )
    }

    override suspend fun getRequestCount(baseUrl: String, credentials: SeerrCredentials): Result<SeerrRequestCount> =
        getAndParseResult(baseUrl, "/request/count", credentials)

    override suspend fun getCurrentUser(baseUrl: String, credentials: SeerrCredentials): Result<SeerrCurrentUser> =
        getAndParseResult(baseUrl, "/auth/me", credentials)

    // ── Private shape helpers ───────────────────────────────────────────────

    /** The JVM `getAndParse` + `req { }` pair in one: authenticated GET under retry. */
    private suspend inline fun <reified T> getAndParseResult(
        baseUrl: String,
        path: String,
        credentials: SeerrCredentials,
    ): Result<T> = apiResultWithRetry {
        getAndParse<T>(
            url = seerrApiUrl(baseUrl, path),
            headers = seerrAuthHeaders(credentials),
        )
    }

    /** The JVM empty-body `postAndParse` + `req { }` pair in one (the `"{}"` POST body). */
    private suspend inline fun <reified T> postAndParseResult(
        baseUrl: String,
        path: String,
        credentials: SeerrCredentials,
    ): Result<T> = apiResultWithRetry {
        postAndParse<T>(
            url = seerrApiUrl(baseUrl, path),
            headers = seerrAuthHeaders(credentials),
        )
    }
}
