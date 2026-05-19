package com.raulshma.jellyplay.core.network

import com.raulshma.jellyplay.core.model.LrcLibTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LrcLibApi @Inject constructor(
    private val client: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getBestMatch(
        artistName: String,
        trackName: String,
        duration: Double?,
    ): Result<LrcLibTrack> = withContext(Dispatchers.IO) {
        runCatching {
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
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                throw IllegalStateException("lrclib returned ${response.code}")
            }
            val body = response.body?.string() ?: throw IllegalStateException("Empty response")
            parseTrack(body)
        }
    }

    suspend fun search(query: String): Result<List<LrcLibTrack>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "${BASE_URL}/api/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "JellyPlay")
                .get()
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                throw IllegalStateException("lrclib returned ${response.code}")
            }
            val body = response.body?.string() ?: throw IllegalStateException("Empty response")
            val array = json.parseToJsonElement(body) as JsonArray
            array.map { element -> parseTrackFromJson(element as JsonObject) }
        }
    }

    suspend fun getById(id: Long): Result<LrcLibTrack> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("$BASE_URL/api/get/$id")
                .header("User-Agent", "JellyPlay")
                .get()
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                throw IllegalStateException("lrclib returned ${response.code}")
            }
            val body = response.body?.string() ?: throw IllegalStateException("Empty response")
            parseTrack(body)
        }
    }

    private fun parseTrack(body: String): LrcLibTrack {
        val element = json.parseToJsonElement(body) as JsonObject
        return parseTrackFromJson(element)
    }

    private fun parseTrackFromJson(obj: JsonObject): LrcLibTrack {
        return LrcLibTrack(
            id = obj["id"]?.let { (it as JsonPrimitive).longOrNull ?: 0L } ?: 0L,
            trackName = obj["trackName"]?.let { (it as JsonPrimitive).content } ?: "",
            artistName = obj["artistName"]?.let { (it as JsonPrimitive).content } ?: "",
            albumName = obj["albumName"]?.let { (it as JsonPrimitive).content } ?: "",
            duration = obj["duration"]?.let { (it as JsonPrimitive).doubleOrNull ?: 0.0 } ?: 0.0,
            instrumental = obj["instrumental"]?.let { (it as JsonPrimitive).content == "true" } ?: false,
            plainLyrics = obj["plainLyrics"]?.let { (it as JsonPrimitive).content.ifBlank { null } },
            syncedLyrics = obj["syncedLyrics"]?.let { (it as JsonPrimitive).content.ifBlank { null } },
        )
    }

    companion object {
        private const val BASE_URL = "https://lrclib.net"
    }
}
