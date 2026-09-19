package com.raulshma.jellyplay.core.network.seerr

import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import com.raulshma.jellyplay.core.model.arr.ArrServiceKind
import com.raulshma.jellyplay.core.model.seerr.*
import com.raulshma.jellyplay.core.network.api.HttpExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.encodeToString
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SeerrApiClientImpl @Inject constructor(
    okHttpClient: OkHttpClient,
) : SeerrApiClient {

    private val json = lenientJson

    private fun buildUrl(baseUrl: String, path: String): String {
        val base = baseUrl.trimEnd('/')
        return "$base/api/v1${path}"
    }

    private fun Request.Builder.withAuth(credentials: SeerrCredentials): Request.Builder {
        return when (credentials) {
            is SeerrCredentials.ApiKey -> this.header("X-Api-Key", credentials.apiKey)
            is SeerrCredentials.SessionCookie -> this.header("Cookie", credentials.cookie)
        }
    }

    /**
     * The shared OkHttp execute chassis (the Arr sibling rides the same
     * executor). Seerr's texts stay this client's own: a message-field
     * `parseErrorMessage`, the generic-"server" failure ladder, and no
     * Retry-After capture (the historical `fromSeerrHttp` shape, which drops
     * the header). Retry rides the chassis (`retryHttpCalls`), replacing the
     * deleted `ResilientSeerrApiClient` DI wrapper — every funnel below
     * (parse/text/cookie) is a single HTTP call per attempt, so per-call
     * retry is equivalent to the wrapper's per-method retry.
     */
    private val http = HttpExecutor(
        okHttpClient = okHttpClient,
        json = json,
        options = HttpExecutor.Options(
            parseErrorMessage = ::parseErrorMessage,
            formatNetworkError = ::formatNetworkError,
            retryHttpCalls = true,
        ),
    )

    private fun parseErrorMessage(code: Int, body: String): String {
        return try {
            val errorJson = json.parseToJsonElement(body).jsonObject
            val message = errorJson["message"]?.toString()?.trim('"') ?: ""
            if (message.isNotBlank()) "HTTP $code: $message" else "HTTP $code: $body"
        } catch (_: Exception) {
            "HTTP $code: ${body.take(200)}"
        }
    }

    private fun formatNetworkError(e: Exception): String {
        return when (e) {
            is UnknownHostException -> "Unable to reach server. Check the URL and your network connection."
            is ConnectException -> "Could not connect to server. Ensure the server is running and accessible."
            is SocketTimeoutException -> "Connection timed out. The server took too long to respond."
            is IOException -> "Network error: ${e.message ?: e.javaClass.simpleName}"
            else -> e.message ?: e.javaClass.simpleName
        }
    }

    /** Stream-decoding request execution; see [HttpExecutor.parseJson]. */
    private suspend inline fun <reified T> parseRequest(request: Request): Result<T> =
        http.parseJson(request)

    private suspend inline fun <reified T> getAndParse(
        baseUrl: String,
        credentials: SeerrCredentials,
        path: String,
    ): Result<T> {
        val request = Request.Builder()
            .url(buildUrl(baseUrl, path))
            .withAuth(credentials)
            .get()
            .build()
        return parseRequest(request)
    }

    private suspend inline fun <reified T> postAndParse(
        baseUrl: String,
        credentials: SeerrCredentials,
        path: String,
    ): Result<T> {
        val requestBody = "{}".toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(buildUrl(baseUrl, path))
            .withAuth(credentials)
            .post(requestBody)
            .build()
        return parseRequest(request)
    }

    private suspend inline fun <reified T, reified B> postAndParse(
        baseUrl: String,
        credentials: SeerrCredentials,
        path: String,
        body: B,
    ): Result<T> {
        val requestBody = json.encodeToString(body).toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(buildUrl(baseUrl, path))
            .withAuth(credentials)
            .post(requestBody)
            .build()
        return parseRequest(request)
    }

    private suspend inline fun <reified T, reified B> putAndParse(
        baseUrl: String,
        credentials: SeerrCredentials,
        path: String,
        body: B,
    ): Result<T> {
        val request = Request.Builder()
            .url(buildUrl(baseUrl, path))
            .withAuth(credentials)
            .put(json.encodeToString(body).toRequestBody("application/json".toMediaType()))
            .build()
        return parseRequest(request)
    }

    override suspend fun loginJellyfin(
        baseUrl: String,
        username: String,
        password: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        val payload = json.encodeToString(SeerrAuthJellyfinRequest(username, password))
        val request = Request.Builder()
            .url(buildUrl(baseUrl, "/auth/jellyfin"))
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        http.executeForCookie(request).mapCatching { (_, cookie) ->
            cookie ?: throw Exception("No session cookie received from server")
        }
    }

    override suspend fun loginLocal(
        baseUrl: String,
        email: String,
        password: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        val payload = json.encodeToString(SeerrAuthLocalRequest(email, password))
        val request = Request.Builder()
            .url(buildUrl(baseUrl, "/auth/local"))
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        http.executeForCookie(request).mapCatching { (_, cookie) ->
            cookie ?: throw Exception("No session cookie received from server")
        }
    }

    override suspend fun testConnection(baseUrl: String, credentials: SeerrCredentials): Result<SeerrStatusResponse> =
        getAndParse(baseUrl, credentials, "/status")

    override suspend fun search(
        baseUrl: String, credentials: SeerrCredentials, query: String, page: Int,
    ): Result<SeerrSearchResponse> {
        // Build the HttpUrl directly rather than constructing a throwaway
        // Request merely to borrow its url. Same final URL, less garbage.
        val url = buildUrl(baseUrl, "/search").toHttpUrl().newBuilder()
            .addQueryParameter("query", query)
            .addQueryParameter("page", page.toString())
            .build()
        val request = Request.Builder().url(url).withAuth(credentials).get().build()
        return parseRequest(request)
    }

    override suspend fun getMovieDetails(baseUrl: String, credentials: SeerrCredentials, tmdbId: Int): Result<SeerrMovieDetails> =
        getAndParse(baseUrl, credentials, "/movie/$tmdbId")

    override suspend fun getTvDetails(baseUrl: String, credentials: SeerrCredentials, tmdbId: Int): Result<SeerrTvDetails> =
        getAndParse(baseUrl, credentials, "/tv/$tmdbId")

    override suspend fun getTvSeasonDetails(baseUrl: String, credentials: SeerrCredentials, tvId: Int, seasonNumber: Int): Result<SeerrSeasonDetail> =
        getAndParse(baseUrl, credentials, "/tv/$tvId/season/$seasonNumber")

    override suspend fun getMovieRatings(baseUrl: String, credentials: SeerrCredentials, tmdbId: Int): Result<SeerrRatings> =
        getAndParse(baseUrl, credentials, "/movie/$tmdbId/ratings")

    override suspend fun getTvRatings(baseUrl: String, credentials: SeerrCredentials, tmdbId: Int): Result<SeerrRatings> =
        getAndParse(baseUrl, credentials, "/tv/$tmdbId/ratings")

    override suspend fun getMovieRatingsCombined(baseUrl: String, credentials: SeerrCredentials, tmdbId: Int): Result<SeerrRatings> =
        getAndParse(baseUrl, credentials, "/movie/$tmdbId/ratingscombined")

    override suspend fun getMovieRecommendations(baseUrl: String, credentials: SeerrCredentials, tmdbId: Int, page: Int): Result<SeerrSearchResponse> =
        getAndParse(baseUrl, credentials, "/movie/$tmdbId/recommendations?page=$page")

    override suspend fun getMovieSimilar(baseUrl: String, credentials: SeerrCredentials, tmdbId: Int, page: Int): Result<SeerrSearchResponse> =
        getAndParse(baseUrl, credentials, "/movie/$tmdbId/similar?page=$page")

    override suspend fun getTvRecommendations(baseUrl: String, credentials: SeerrCredentials, tmdbId: Int, page: Int): Result<SeerrSearchResponse> =
        getAndParse(baseUrl, credentials, "/tv/$tmdbId/recommendations?page=$page")

    override suspend fun getTvSimilar(baseUrl: String, credentials: SeerrCredentials, tmdbId: Int, page: Int): Result<SeerrSearchResponse> =
        getAndParse(baseUrl, credentials, "/tv/$tmdbId/similar?page=$page")

    override suspend fun requestMedia(
        baseUrl: String, credentials: SeerrCredentials, mediaType: String, mediaId: Int,
        tvdbId: Int?, seasons: List<Int>?, serverId: Int?, profileId: Int?,
        rootFolder: String?, tags: List<Int>?,
    ): Result<SeerrMediaRequest> = postAndParse(baseUrl, credentials, "/request",
        SeerrRequestPayload(mediaType = mediaType, mediaId = mediaId, tvdbId = tvdbId,
            seasons = seasons, serverId = serverId, profileId = profileId,
            rootFolder = rootFolder, tags = tags))

    override suspend fun getRadarrSettings(baseUrl: String, credentials: SeerrCredentials): Result<List<SeerrRadarrSettings>> =
        getAndParse(baseUrl, credentials, "/settings/radarr")

    override suspend fun getSonarrSettings(baseUrl: String, credentials: SeerrCredentials): Result<List<SeerrSonarrSettings>> =
        getAndParse(baseUrl, credentials, "/settings/sonarr")

    override suspend fun getRadarrServiceDetail(baseUrl: String, credentials: SeerrCredentials, id: Int): Result<SeerrRadarrServiceDetail> =
        getAndParse(baseUrl, credentials, "/settings/radarr/$id")

    override suspend fun getSonarrServiceDetail(baseUrl: String, credentials: SeerrCredentials, id: Int): Result<SeerrSonarrServiceDetail> =
        getAndParse(baseUrl, credentials, "/settings/sonarr/$id")

    override suspend fun getServiceRadarrServers(baseUrl: String, credentials: SeerrCredentials): Result<List<SeerrServiceServer>> =
        getAndParse(baseUrl, credentials, "/service/radarr")

    override suspend fun getServiceSonarrServers(baseUrl: String, credentials: SeerrCredentials): Result<List<SeerrServiceServer>> =
        getAndParse(baseUrl, credentials, "/service/sonarr")

    override suspend fun getServiceDetail(
        baseUrl: String,
        credentials: SeerrCredentials,
        id: Int,
        kind: ArrServiceKind,
    ): Result<SeerrServiceDetail> =
        // The two endpoints differ only in path; each kind decodes to its own
        // concrete payload (Result.map upcasts the subtype to the sealed parent).
        when (kind) {
            ArrServiceKind.RADARR ->
                getAndParse<SeerrRadarrServiceDetail>(baseUrl, credentials, "/service/radarr/$id")
            ArrServiceKind.SONARR ->
                getAndParse<SeerrSonarrServiceDetail>(baseUrl, credentials, "/service/sonarr/$id")
        }.map { it }

    override suspend fun getTrending(baseUrl: String, credentials: SeerrCredentials, page: Int): Result<SeerrSearchResponse> =
        getAndParse(baseUrl, credentials, "/discover/trending?page=$page")

    override suspend fun getDiscoverMovies(
        baseUrl: String, credentials: SeerrCredentials, page: Int, primaryReleaseDateGte: String?,
    ): Result<SeerrSearchResponse> {
        val path = buildString {
            append("/discover/movies?page=$page")
            if (primaryReleaseDateGte != null) {
                append("&primaryReleaseDateGte=")
                append(java.net.URLEncoder.encode(primaryReleaseDateGte, "UTF-8"))
            }
        }
        return getAndParse(baseUrl, credentials, path)
    }

    override suspend fun getDiscoverTv(
        baseUrl: String, credentials: SeerrCredentials, page: Int, firstAirDateGte: String?,
    ): Result<SeerrSearchResponse> {
        val path = buildString {
            append("/discover/tv?page=$page")
            if (firstAirDateGte != null) {
                append("&firstAirDateGte=")
                append(java.net.URLEncoder.encode(firstAirDateGte, "UTF-8"))
            }
        }
        return getAndParse(baseUrl, credentials, path)
    }

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
    ): Result<SeerrRequestListResponse> {
        val path = buildString {
            append("/request?take=$take&skip=$skip&filter=$filter&sort=$sort&sortDirection=$sortDirection")
            requestedBy?.let { append("&requestedBy=$it") }
            mediaType?.let { append("&mediaType=$it") }
            search?.takeIf { it.isNotBlank() }?.let {
                append("&search=")
                append(java.net.URLEncoder.encode(it, "UTF-8"))
            }
        }
        val request = Request.Builder()
            .url(buildUrl(baseUrl, path))
            .withAuth(credentials)
            .get()
            .build()
        return parseRequest(request)
    }

    override suspend fun getRequest(
        baseUrl: String,
        credentials: SeerrCredentials,
        id: Int,
    ): Result<SeerrRequestItem> {
        val request = Request.Builder()
            .url(buildUrl(baseUrl, "/request/$id"))
            .withAuth(credentials)
            .get()
            .build()
        return parseRequest(request)
    }

    override suspend fun approveRequest(baseUrl: String, credentials: SeerrCredentials, id: Int): Result<SeerrRequestItem> =
        postAndParse(baseUrl, credentials, "/request/$id/approve")

    override suspend fun declineRequest(baseUrl: String, credentials: SeerrCredentials, id: Int): Result<SeerrRequestItem> =
        postAndParse(baseUrl, credentials, "/request/$id/decline")

    override suspend fun retryRequest(baseUrl: String, credentials: SeerrCredentials, id: Int): Result<SeerrRequestItem> =
        postAndParse(baseUrl, credentials, "/request/$id/retry")

    override suspend fun deleteRequest(
        baseUrl: String,
        credentials: SeerrCredentials,
        id: Int,
    ): Result<Unit> {
        val request = Request.Builder()
            .url(buildUrl(baseUrl, "/request/$id"))
            .withAuth(credentials)
            .delete()
            .build()
        return http.executeForText(request).map { }
    }

    override suspend fun deleteMedia(
        baseUrl: String,
        credentials: SeerrCredentials,
        mediaId: Int,
        is4k: Boolean,
    ): Result<Unit> {
        runCatchingRethrowingCancellation {
            val fileRequest = Request.Builder()
                .url(buildUrl(baseUrl, "/media/$mediaId/file?is4k=$is4k"))
                .withAuth(credentials)
                .delete()
                .build()
            http.executeForText(fileRequest)
        }
        val request = Request.Builder()
            .url(buildUrl(baseUrl, "/media/$mediaId"))
            .withAuth(credentials)
            .delete()
            .build()
        return http.executeForText(request).map { }
    }

    override suspend fun editRequest(
        baseUrl: String, credentials: SeerrCredentials, id: Int, mediaType: String,
        mediaId: Int, serverId: Int?, profileId: Int?, rootFolder: String?, tags: List<Int>?, seasons: List<Int>?,
    ): Result<SeerrRequestItem> = putAndParse(baseUrl, credentials, "/request/$id",
        SeerrEditRequestPayload(mediaType = mediaType, mediaId = mediaId,
            serverId = serverId, profileId = profileId, rootFolder = rootFolder,
            tags = tags, seasons = seasons))

    override suspend fun getRequestCount(baseUrl: String, credentials: SeerrCredentials): Result<SeerrRequestCount> =
        getAndParse(baseUrl, credentials, "/request/count")

    override suspend fun getCurrentUser(baseUrl: String, credentials: SeerrCredentials): Result<SeerrCurrentUser> =
        getAndParse(baseUrl, credentials, "/auth/me")

    companion object {
        internal val lenientJson = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            isLenient = true
        }
    }
}
