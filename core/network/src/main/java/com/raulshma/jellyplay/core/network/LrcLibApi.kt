package com.raulshma.jellyplay.core.network

import com.raulshma.jellyplay.core.model.LrcLibTrack
import com.raulshma.jellyplay.core.network.api.JellyfinApiEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LrcLibApi @Inject constructor(
    private val client: OkHttpClient,
) {
    private val json = JellyfinApiEngine.sharedJson

    /**
     * On-the-wire shape returned by lrclib.net. [instrumental] arrives as a
     * native JSON boolean. Lyrics fields are blank for absent lyrics and
     * collapsed to null on the domain model.
     */
    @Serializable
    private data class LrcLibTrackDto(
        val id: Long = 0L,
        @SerialName("trackName") val trackName: String = "",
        @SerialName("artistName") val artistName: String = "",
        @SerialName("albumName") val albumName: String = "",
        val duration: Double = 0.0,
        val instrumental: Boolean = false,
        @SerialName("plainLyrics") val plainLyrics: String? = null,
        @SerialName("syncedLyrics") val syncedLyrics: String? = null,
    ) {
        fun toDomain(): LrcLibTrack = LrcLibTrack(
            id = id,
            trackName = trackName,
            artistName = artistName,
            albumName = albumName,
            duration = duration,
            instrumental = instrumental,
            plainLyrics = plainLyrics?.takeIf { it.isNotBlank() },
            syncedLyrics = syncedLyrics?.takeIf { it.isNotBlank() },
        )
    }

    private suspend fun executeAndReadBody(client: OkHttpClient, request: Request): String =
        withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("lrclib returned ${response.code}")
                }
                response.body?.string() ?: throw IllegalStateException("Empty response")
            }
        }

    suspend fun getBestMatch(
        artistName: String,
        trackName: String,
        duration: Double?,
    ): Result<LrcLibTrack> = runCatching {
        val urlBuilder = StringBuilder(BASE_URL)
            .append("/api/get?artist_name=")
            .append(java.net.URLEncoder.encode(artistName, "UTF-8"))
            .append("&track_name=")
            .append(java.net.URLEncoder.encode(trackName, "UTF-8"))
        if (duration != null) {
            urlBuilder.append("&duration=").append(duration.toLong())
        }
        val request = Request.Builder()
            .url(urlBuilder.toString())
            .header("User-Agent", "JellyPlay")
            .get()
            .build()
        json.decodeFromString<LrcLibTrackDto>(executeAndReadBody(client, request)).toDomain()
    }

    suspend fun search(query: String): Result<List<LrcLibTrack>> = runCatching {
        val url = "${BASE_URL}/api/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "JellyPlay")
            .get()
            .build()
        val body = executeAndReadBody(client, request)
        json.decodeFromString(ListSerializer(LrcLibTrackDto.serializer()), body).map { it.toDomain() }
    }

    suspend fun getById(id: Long): Result<LrcLibTrack> = runCatching {
        val request = Request.Builder()
            .url("$BASE_URL/api/get/$id")
            .header("User-Agent", "JellyPlay")
            .get()
            .build()
        json.decodeFromString<LrcLibTrackDto>(executeAndReadBody(client, request)).toDomain()
    }

    companion object {
        private const val BASE_URL = "https://lrclib.net"
    }
}
