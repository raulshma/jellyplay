package com.raulshma.jellyplay.core.network.seerr

import com.raulshma.jellyplay.core.model.seerr.*
import com.raulshma.jellyplay.core.network.api.ApiException
import com.raulshma.jellyplay.core.network.api.JsonRequestClient
import com.raulshma.jellyplay.core.network.api.fromSeerrNetwork
import com.raulshma.jellyplay.core.network.api.parseJsonRequest
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
    private val okHttpClient: OkHttpClient,
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

    private suspend fun executeRequest(request: Request): Result<String> {
        return try {
            withContext(Dispatchers.IO) {
                okHttpClient.newCall(request).execute().use { response ->
                    val body = response.body?.string() ?: return@withContext Result.failure<String>(
                        ApiException.fromSeerrHttp(response.code, "Empty response body (HTTP ${response.code})")
                    )
                    if (!response.isSuccessful) {
                        val errorMsg = parseErrorMessage(response.code, body)
                        return@withContext Result.failure(ApiException.fromSeerrHttp(response.code, errorMsg))
                    }
                    Result.success(body)
                }
            }
        } catch (e: Exception) {
            // CancellationException is captured by runCatching upstream; preserve it for
            // structured-concurrency correctness by rethrowing here.
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(ApiException.fromSeerrNetwork(e, formatNetworkError(e)))
        }
    }

    private suspend fun executeRequestWithCookie(request: Request): Result<Pair<String, String?>> {
        return try {
            withContext(Dispatchers.IO) {
                okHttpClient.newCall(request).execute().use { response ->
                    val body = response.body?.string()
                        ?: return@withContext Result.failure<Pair<String, String?>>(
                            ApiException.fromSeerrHttp(response.code, "Empty response body (HTTP ${response.code})")
                        )
                    if (!response.isSuccessful) {
                        val errorMsg = parseErrorMessage(response.code, body)
                        return@withContext Result.failure<Pair<String, String?>>(ApiException.fromSeerrHttp(response.code, errorMsg))
                    }
                    val cookieHeader = response.headers("Set-Cookie").joinToString("; ") {
                        it.substringBefore(";")
                    }
                    Result.success(body to cookieHeader.ifBlank { null })
                }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(ApiException.fromSeerrNetwork(e, formatNetworkError(e)))
        }
    }

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

    /** Bundled [parseJsonRequest] dependencies; see [parseRequest]. */
    private val jsonRequestClient = JsonRequestClient(
        okHttpClient = okHttpClient,
        json = json,
        parseErrorMessage = ::parseErrorMessage,
        formatNetworkError = ::formatNetworkError,
    )

    /**
     * Stream-decoding request execution; see [parseJsonRequest]. The shared
     * helper's `fromHttp`/`fromNetwork` are what [ApiException.fromSeerrHttp] /
     * [ApiException.fromSeerrNetwork] delegate to, so failure shapes are
     * unchanged.
     */
    private suspend inline fun <reified T> parseRequest(request: Request): Result<T> =
        parseJsonRequest(jsonRequestClient, request)

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
        executeRequestWithCookie(request).mapCatching { (_, cookie) ->
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
        executeRequestWithCookie(request).mapCatching { (_, cookie) ->
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

    override suspend fun getServiceRadarrDetail(baseUrl: String, credentials: SeerrCredentials, id: Int): Result<SeerrRadarrServiceDetail> =
        getAndParse(baseUrl, credentials, "/service/radarr/$id")

    override suspend fun getServiceSonarrDetail(baseUrl: String, credentials: SeerrCredentials, id: Int): Result<SeerrSonarrServiceDetail> =
        getAndParse(baseUrl, credentials, "/service/sonarr/$id")

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
        return executeRequest(request).map { }
    }

    override suspend fun deleteMedia(
        baseUrl: String,
        credentials: SeerrCredentials,
        mediaId: Int,
        is4k: Boolean,
    ): Result<Unit> {
        runCatching {
            val fileRequest = Request.Builder()
                .url(buildUrl(baseUrl, "/media/$mediaId/file?is4k=$is4k"))
                .withAuth(credentials)
                .delete()
                .build()
            executeRequest(fileRequest)
        }
        val request = Request.Builder()
            .url(buildUrl(baseUrl, "/media/$mediaId"))
            .withAuth(credentials)
            .delete()
            .build()
        return executeRequest(request).map { }
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
