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

    private fun Request.Builder.withApiKey(apiKey: String): Request.Builder {
        return this.header("X-Api-Key", apiKey)
    }

    private suspend fun executeRequest(request: Request): Result<String> = withContext(Dispatchers.IO) {
        try {
            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext Result.failure(
                Exception("Empty response body (HTTP ${response.code})")
            )
            if (!response.isSuccessful) {
                val errorMsg = parseErrorMessage(response.code, body)
                return@withContext Result.failure(Exception(errorMsg))
            }
            Result.success(body)
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

    override suspend fun testConnection(baseUrl: String, apiKey: String): Result<SeerrStatusResponse> {
        val request = Request.Builder()
            .url(buildUrl(baseUrl, "/status"))
            .withApiKey(apiKey)
            .get()
            .build()

        return parseAndMap(executeRequest(request))
    }

    override suspend fun search(
        baseUrl: String,
        apiKey: String,
        query: String,
        page: Int,
    ): Result<SeerrSearchResponse> {
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val request = Request.Builder()
            .url(buildUrl(baseUrl, "/search?query=$encodedQuery&page=$page"))
            .withApiKey(apiKey)
            .get()
            .build()

        return parseAndMap(executeRequest(request))
    }

    override suspend fun getMovieDetails(
        baseUrl: String,
        apiKey: String,
        tmdbId: Int,
    ): Result<SeerrMovieDetails> {
        val request = Request.Builder()
            .url(buildUrl(baseUrl, "/movie/$tmdbId"))
            .withApiKey(apiKey)
            .get()
            .build()

        return parseAndMap(executeRequest(request))
    }

    override suspend fun getTvDetails(
        baseUrl: String,
        apiKey: String,
        tmdbId: Int,
    ): Result<SeerrTvDetails> {
        val request = Request.Builder()
            .url(buildUrl(baseUrl, "/tv/$tmdbId"))
            .withApiKey(apiKey)
            .get()
            .build()

        return parseAndMap(executeRequest(request))
    }

    override suspend fun getMovieRatings(
        baseUrl: String,
        apiKey: String,
        tmdbId: Int,
    ): Result<SeerrRatings> {
        val request = Request.Builder()
            .url(buildUrl(baseUrl, "/movie/$tmdbId/ratings"))
            .withApiKey(apiKey)
            .get()
            .build()

        return parseAndMap(executeRequest(request))
    }

    override suspend fun getTvRatings(
        baseUrl: String,
        apiKey: String,
        tmdbId: Int,
    ): Result<SeerrRatings> {
        val request = Request.Builder()
            .url(buildUrl(baseUrl, "/tv/$tmdbId/ratings"))
            .withApiKey(apiKey)
            .get()
            .build()

        return parseAndMap(executeRequest(request))
    }

    override suspend fun getMovieRatingsCombined(
        baseUrl: String,
        apiKey: String,
        tmdbId: Int,
    ): Result<SeerrRatings> {
        val request = Request.Builder()
            .url(buildUrl(baseUrl, "/movie/$tmdbId/ratingscombined"))
            .withApiKey(apiKey)
            .get()
            .build()

        return parseAndMap(executeRequest(request))
    }

    override suspend fun getMovieRecommendations(
        baseUrl: String,
        apiKey: String,
        tmdbId: Int,
        page: Int,
    ): Result<SeerrSearchResponse> {
        val request = Request.Builder()
            .url(buildUrl(baseUrl, "/movie/$tmdbId/recommendations?page=$page"))
            .withApiKey(apiKey)
            .get()
            .build()

        return parseAndMap(executeRequest(request))
    }

    override suspend fun getMovieSimilar(
        baseUrl: String,
        apiKey: String,
        tmdbId: Int,
        page: Int,
    ): Result<SeerrSearchResponse> {
        val request = Request.Builder()
            .url(buildUrl(baseUrl, "/movie/$tmdbId/similar?page=$page"))
            .withApiKey(apiKey)
            .get()
            .build()

        return parseAndMap(executeRequest(request))
    }

    override suspend fun getTvRecommendations(
        baseUrl: String,
        apiKey: String,
        tmdbId: Int,
        page: Int,
    ): Result<SeerrSearchResponse> {
        val request = Request.Builder()
            .url(buildUrl(baseUrl, "/tv/$tmdbId/recommendations?page=$page"))
            .withApiKey(apiKey)
            .get()
            .build()

        return parseAndMap(executeRequest(request))
    }

    override suspend fun getTvSimilar(
        baseUrl: String,
        apiKey: String,
        tmdbId: Int,
        page: Int,
    ): Result<SeerrSearchResponse> {
        val request = Request.Builder()
            .url(buildUrl(baseUrl, "/tv/$tmdbId/similar?page=$page"))
            .withApiKey(apiKey)
            .get()
            .build()

        return parseAndMap(executeRequest(request))
    }

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
            .withApiKey(apiKey)
            .post(body)
            .build()

        return parseAndMap(executeRequest(request))
    }

    override suspend fun getRadarrSettings(
        baseUrl: String,
        apiKey: String,
    ): Result<List<SeerrRadarrSettings>> {
        val request = Request.Builder()
            .url(buildUrl(baseUrl, "/settings/radarr"))
            .withApiKey(apiKey)
            .get()
            .build()

        return parseAndMap(executeRequest(request))
    }

    override suspend fun getSonarrSettings(
        baseUrl: String,
        apiKey: String,
    ): Result<List<SeerrSonarrSettings>> {
        val request = Request.Builder()
            .url(buildUrl(baseUrl, "/settings/sonarr"))
            .withApiKey(apiKey)
            .get()
            .build()

        return parseAndMap(executeRequest(request))
    }

    override suspend fun getRadarrServiceDetail(
        baseUrl: String,
        apiKey: String,
        id: Int,
    ): Result<SeerrRadarrServiceDetail> {
        val request = Request.Builder()
            .url(buildUrl(baseUrl, "/settings/radarr/$id"))
            .withApiKey(apiKey)
            .get()
            .build()

        return parseAndMap(executeRequest(request))
    }

    override suspend fun getSonarrServiceDetail(
        baseUrl: String,
        apiKey: String,
        id: Int,
    ): Result<SeerrSonarrServiceDetail> {
        val request = Request.Builder()
            .url(buildUrl(baseUrl, "/settings/sonarr/$id"))
            .withApiKey(apiKey)
            .get()
            .build()

        return parseAndMap(executeRequest(request))
    }

    // ── /service/ endpoints (used by request modal) ──

    override suspend fun getServiceRadarrServers(
        baseUrl: String,
        apiKey: String,
    ): Result<List<SeerrServiceServer>> {
        val request = Request.Builder()
            .url(buildUrl(baseUrl, "/service/radarr"))
            .withApiKey(apiKey)
            .get()
            .build()

        return parseAndMap(executeRequest(request))
    }

    override suspend fun getServiceSonarrServers(
        baseUrl: String,
        apiKey: String,
    ): Result<List<SeerrServiceServer>> {
        val request = Request.Builder()
            .url(buildUrl(baseUrl, "/service/sonarr"))
            .withApiKey(apiKey)
            .get()
            .build()

        return parseAndMap(executeRequest(request))
    }

    override suspend fun getServiceRadarrDetail(
        baseUrl: String,
        apiKey: String,
        id: Int,
    ): Result<SeerrRadarrServiceDetail> {
        val request = Request.Builder()
            .url(buildUrl(baseUrl, "/service/radarr/$id"))
            .withApiKey(apiKey)
            .get()
            .build()

        return parseAndMap(executeRequest(request))
    }

    override suspend fun getServiceSonarrDetail(
        baseUrl: String,
        apiKey: String,
        id: Int,
    ): Result<SeerrSonarrServiceDetail> {
        val request = Request.Builder()
            .url(buildUrl(baseUrl, "/service/sonarr/$id"))
            .withApiKey(apiKey)
            .get()
            .build()

        return parseAndMap(executeRequest(request))
    }
}
