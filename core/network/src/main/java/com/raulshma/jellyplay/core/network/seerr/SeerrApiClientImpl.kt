package com.raulshma.jellyplay.core.network.seerr

import com.raulshma.jellyplay.core.model.seerr.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

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
                        Exception("Empty response body (HTTP ${response.code})")
                    )
                    if (!response.isSuccessful) {
                        val errorMsg = parseErrorMessage(response.code, body)
                        return@withContext Result.failure(Exception(errorMsg))
                    }
                    Result.success(body)
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception(formatNetworkError(e)))
        }
    }

    private fun executeRequestWithCookie(request: Request): Result<Pair<String, String?>> {
        return try {
            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string()
                    ?: return Result.failure(Exception("Empty response body (HTTP ${response.code})"))
                if (!response.isSuccessful) {
                    val errorMsg = parseErrorMessage(response.code, body)
                    return Result.failure(Exception(errorMsg))
                }
                val cookieHeader = response.headers("Set-Cookie").joinToString("; ") {
                    it.substringBefore(";")
                }
                Result.success(body to cookieHeader.ifBlank { null })
            }
        } catch (e: Exception) {
            Result.failure(Exception(formatNetworkError(e)))
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

    private inline fun <reified T> parseAndMap(result: Result<String>): Result<T> {
        return result.mapCatching { body ->
            json.decodeFromString<T>(body)
        }
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

    override suspend fun testConnection(baseUrl: String, credentials: SeerrCredentials): Result<SeerrStatusResponse> {
        val request = Request.Builder()
            .url(buildUrl(baseUrl, "/status"))
            .withAuth(credentials)
            .get()
            .build()

        return parseAndMap(executeRequest(request))
    }

    override suspend fun search(
        baseUrl: String,
        credentials: SeerrCredentials,
        query: String,
        page: Int,
    ): Result<SeerrSearchResponse> {
        val base = Request.Builder()
            .url(buildUrl(baseUrl, "/search"))
            .build().url.newBuilder()
            .addQueryParameter("query", query)
            .addQueryParameter("page", page.toString())
            .build()
        val request = Request.Builder()
            .url(base)
            .withAuth(credentials)
            .get()
            .build()

        return parseAndMap(executeRequest(request))
    }

    override suspend fun getMovieDetails(
        baseUrl: String,
        credentials: SeerrCredentials,
        tmdbId: Int,
    ): Result<SeerrMovieDetails> {
        val request = Request.Builder()
            .url(buildUrl(baseUrl, "/movie/$tmdbId"))
            .withAuth(credentials)
            .get()
            .build()

        return parseAndMap(executeRequest(request))
    }

    override suspend fun getTvDetails(
        baseUrl: String,
        credentials: SeerrCredentials,
        tmdbId: Int,
    ): Result<SeerrTvDetails> {
        val request = Request.Builder()
            .url(buildUrl(baseUrl, "/tv/$tmdbId"))
            .withAuth(credentials)
            .get()
            .build()

        return parseAndMap(executeRequest(request))
    }

    override suspend fun getTvSeasonDetails(
        baseUrl: String,
        credentials: SeerrCredentials,
        tvId: Int,
        seasonNumber: Int,
    ): Result<SeerrSeasonDetail> {
        val request = Request.Builder()
            .url(buildUrl(baseUrl, "/tv/$tvId/season/$seasonNumber"))
            .withAuth(credentials)
            .get()
            .build()

        return parseAndMap(executeRequest(request))
    }

    override suspend fun getMovieRatings(
        baseUrl: String,
        credentials: SeerrCredentials,
        tmdbId: Int,
    ): Result<SeerrRatings> {
        val request = Request.Builder()
            .url(buildUrl(baseUrl, "/movie/$tmdbId/ratings"))
            .withAuth(credentials)
            .get()
            .build()

        return parseAndMap(executeRequest(request))
    }

    override suspend fun getTvRatings(
        baseUrl: String,
        credentials: SeerrCredentials,
        tmdbId: Int,
    ): Result<SeerrRatings> {
        val request = Request.Builder()
            .url(buildUrl(baseUrl, "/tv/$tmdbId/ratings"))
            .withAuth(credentials)
            .get()
            .build()

        return parseAndMap(executeRequest(request))
    }

    override suspend fun getMovieRatingsCombined(
        baseUrl: String,
        credentials: SeerrCredentials,
        tmdbId: Int,
    ): Result<SeerrRatings> {
        val request = Request.Builder()
            .url(buildUrl(baseUrl, "/movie/$tmdbId/ratingscombined"))
            .withAuth(credentials)
            .get()
            .build()

        return parseAndMap(executeRequest(request))
    }

    override suspend fun getMovieRecommendations(
        baseUrl: String,
        credentials: SeerrCredentials,
        tmdbId: Int,
        page: Int,
    ): Result<SeerrSearchResponse> {
        val request = Request.Builder()
            .url(buildUrl(baseUrl, "/movie/$tmdbId/recommendations?page=$page"))
            .withAuth(credentials)
            .get()
            .build()

        return parseAndMap(executeRequest(request))
    }

    override suspend fun getMovieSimilar(
        baseUrl: String,
        credentials: SeerrCredentials,
        tmdbId: Int,
        page: Int,
    ): Result<SeerrSearchResponse> {
        val request = Request.Builder()
            .url(buildUrl(baseUrl, "/movie/$tmdbId/similar?page=$page"))
            .withAuth(credentials)
            .get()
            .build()

        return parseAndMap(executeRequest(request))
    }

    override suspend fun getTvRecommendations(
        baseUrl: String,
        credentials: SeerrCredentials,
        tmdbId: Int,
        page: Int,
    ): Result<SeerrSearchResponse> {
        val request = Request.Builder()
            .url(buildUrl(baseUrl, "/tv/$tmdbId/recommendations?page=$page"))
            .withAuth(credentials)
            .get()
            .build()

        return parseAndMap(executeRequest(request))
    }

    override suspend fun getTvSimilar(
        baseUrl: String,
        credentials: SeerrCredentials,
        tmdbId: Int,
        page: Int,
    ): Result<SeerrSearchResponse> {
        val request = Request.Builder()
            .url(buildUrl(baseUrl, "/tv/$tmdbId/similar?page=$page"))
            .withAuth(credentials)
            .get()
            .build()

        return parseAndMap(executeRequest(request))
    }

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
    ): Result<SeerrMediaRequest> {
        val payload = SeerrRequestPayload(
            mediaType = mediaType,
            mediaId = mediaId,
            tvdbId = tvdbId,
            seasons = seasons,
            serverId = serverId,
            profileId = profileId,
            rootFolder = rootFolder,
            tags = tags,
        )
        val body = json.encodeToString(payload)
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(buildUrl(baseUrl, "/request"))
            .withAuth(credentials)
            .post(body)
            .build()

        return parseAndMap(executeRequest(request))
    }

    override suspend fun getRadarrSettings(
        baseUrl: String,
        credentials: SeerrCredentials,
    ): Result<List<SeerrRadarrSettings>> {
        val request = Request.Builder()
            .url(buildUrl(baseUrl, "/settings/radarr"))
            .withAuth(credentials)
            .get()
            .build()

        return parseAndMap(executeRequest(request))
    }

    override suspend fun getSonarrSettings(
        baseUrl: String,
        credentials: SeerrCredentials,
    ): Result<List<SeerrSonarrSettings>> {
        val request = Request.Builder()
            .url(buildUrl(baseUrl, "/settings/sonarr"))
            .withAuth(credentials)
            .get()
            .build()

        return parseAndMap(executeRequest(request))
    }

    override suspend fun getRadarrServiceDetail(
        baseUrl: String,
        credentials: SeerrCredentials,
        id: Int,
    ): Result<SeerrRadarrServiceDetail> {
        val request = Request.Builder()
            .url(buildUrl(baseUrl, "/settings/radarr/$id"))
            .withAuth(credentials)
            .get()
            .build()

        return parseAndMap(executeRequest(request))
    }

    override suspend fun getSonarrServiceDetail(
        baseUrl: String,
        credentials: SeerrCredentials,
        id: Int,
    ): Result<SeerrSonarrServiceDetail> {
        val request = Request.Builder()
            .url(buildUrl(baseUrl, "/settings/sonarr/$id"))
            .withAuth(credentials)
            .get()
            .build()

        return parseAndMap(executeRequest(request))
    }

    override suspend fun getServiceRadarrServers(
        baseUrl: String,
        credentials: SeerrCredentials,
    ): Result<List<SeerrServiceServer>> {
        val request = Request.Builder()
            .url(buildUrl(baseUrl, "/service/radarr"))
            .withAuth(credentials)
            .get()
            .build()

        return parseAndMap(executeRequest(request))
    }

    override suspend fun getServiceSonarrServers(
        baseUrl: String,
        credentials: SeerrCredentials,
    ): Result<List<SeerrServiceServer>> {
        val request = Request.Builder()
            .url(buildUrl(baseUrl, "/service/sonarr"))
            .withAuth(credentials)
            .get()
            .build()

        return parseAndMap(executeRequest(request))
    }

    override suspend fun getServiceRadarrDetail(
        baseUrl: String,
        credentials: SeerrCredentials,
        id: Int,
    ): Result<SeerrRadarrServiceDetail> {
        val request = Request.Builder()
            .url(buildUrl(baseUrl, "/service/radarr/$id"))
            .withAuth(credentials)
            .get()
            .build()

        return parseAndMap(executeRequest(request))
    }

    override suspend fun getServiceSonarrDetail(
        baseUrl: String,
        credentials: SeerrCredentials,
        id: Int,
    ): Result<SeerrSonarrServiceDetail> {
        val request = Request.Builder()
            .url(buildUrl(baseUrl, "/service/sonarr/$id"))
            .withAuth(credentials)
            .get()
            .build()

        return parseAndMap(executeRequest(request))
    }

    override suspend fun getTrending(
        baseUrl: String,
        credentials: SeerrCredentials,
        page: Int,
    ): Result<SeerrSearchResponse> {
        val request = Request.Builder()
            .url(buildUrl(baseUrl, "/discover/trending?page=$page"))
            .withAuth(credentials)
            .get()
            .build()

        return parseAndMap(executeRequest(request))
    }

    override suspend fun getDiscoverMovies(
        baseUrl: String,
        credentials: SeerrCredentials,
        page: Int,
        primaryReleaseDateGte: String?,
    ): Result<SeerrSearchResponse> {
        val path = buildString {
            append("/discover/movies?page=$page")
            if (primaryReleaseDateGte != null) {
                append("&primaryReleaseDateGte=")
                append(java.net.URLEncoder.encode(primaryReleaseDateGte, "UTF-8"))
            }
        }
        val request = Request.Builder()
            .url(buildUrl(baseUrl, path))
            .withAuth(credentials)
            .get()
            .build()

        return parseAndMap(executeRequest(request))
    }

    override suspend fun getDiscoverTv(
        baseUrl: String,
        credentials: SeerrCredentials,
        page: Int,
        firstAirDateGte: String?,
    ): Result<SeerrSearchResponse> {
        val path = buildString {
            append("/discover/tv?page=$page")
            if (firstAirDateGte != null) {
                append("&firstAirDateGte=")
                append(java.net.URLEncoder.encode(firstAirDateGte, "UTF-8"))
            }
        }
        val request = Request.Builder()
            .url(buildUrl(baseUrl, path))
            .withAuth(credentials)
            .get()
            .build()

        return parseAndMap(executeRequest(request))
    }
}
