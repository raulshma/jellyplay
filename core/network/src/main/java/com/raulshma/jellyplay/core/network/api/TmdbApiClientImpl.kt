package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.seerr.SeerrRelatedVideo
import com.raulshma.jellyplay.core.network.seerr.SeerrApiClientImpl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TmdbApiClientImpl @Inject constructor(
    private val okHttpClient: OkHttpClient,
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
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(
                            ApiException.fromHttp(
                                httpCode = response.code,
                                message = "TMDB request failed: ${response.code}",
                            )
                        )
                    }
                    val stream = response.body?.byteStream()
                    if (stream == null) {
                        return@withContext Result.failure<List<SeerrRelatedVideo>>(
                            emptyResponseBodyError("TMDB")
                        )
                    }
                    val tmdbResponse = json.decodeFromStream<TmdbVideosResponse>(stream)
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Result.failure(ApiException.fromNetwork(e, "TMDB network error: ${e.message ?: ""}"))
        } catch (e: Exception) {
            Result.failure(
                ApiException(
                    isRetryable = false,
                    message = "TMDB parse error: ${e.message ?: ""}",
                    cause = e,
                )
            )
        }
    }
}
