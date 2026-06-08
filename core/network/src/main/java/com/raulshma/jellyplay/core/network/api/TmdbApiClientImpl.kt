package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.seerr.SeerrRelatedVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TmdbApiClientImpl @Inject constructor(
    private val okHttpClient: OkHttpClient,
) : TmdbApiClient {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

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

    override suspend fun getVideos(tmdbId: Int, isMovie: Boolean): Result<List<SeerrRelatedVideo>> {
        val typeStr = if (isMovie) "movie" else "tv"
        val url = "https://api.themoviedb.org/3/$typeStr/$tmdbId/videos?api_key=$apiKey"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        return try {
            withContext(Dispatchers.IO) {
                okHttpClient.newCall(request).execute().use { response ->
                    val body = response.body?.string() ?: return@withContext Result.failure<List<SeerrRelatedVideo>>(
                        Exception("Empty response from TMDB")
                    )
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(Exception("TMDB request failed: ${response.code}"))
                    }
                    val tmdbResponse = json.decodeFromString<TmdbVideosResponse>(body)
                    val videos = tmdbResponse.results.map {
                        SeerrRelatedVideo(
                            key = it.key,
                            name = it.name,
                            size = it.size,
                            type = it.type,
                            site = it.site,
                            url = if (it.site?.lowercase() == "youtube") "https://www.youtube.com/watch?v=${it.key}" else null
                        )
                    }
                    Result.success(videos)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
