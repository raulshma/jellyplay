package com.raulshma.jellyplay.core.network

import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import com.raulshma.jellyplay.core.model.LrcLibTrack
import com.raulshma.jellyplay.core.network.api.HttpExecutor
import com.raulshma.jellyplay.core.network.api.JellyfinApiEngine
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.OkHttpClient
import okhttp3.Request

class LrcLibApi(
    client: OkHttpClient,
) {
    private val json = JellyfinApiEngine.sharedJson

    /**
     * The shared OkHttp execute chassis, shaped with lrclib's texts. Declared
     * taxonomy delta of the chassis fold: the former off-taxonomy
     * `IllegalStateException`s ("lrclib returned n" / "Empty response") are
     * now typed [ApiException]s — HTTP-status failures through
     * [com.raulshma.jellyplay.core.network.api.ApiException.fromHttpResponse]
     * (same message text, so callers matching on the status keep working),
     * the absent-body arm the IOException-backed retryable shape. Transport
     * failures keep propagating raw (the `runCatchingRethrowingCancellation`
     * callers see the same IOException-shaped failures as before) and there
     * is no retry — lrclib never had a `Resilient*` wrapper.
     */
    private val http = HttpExecutor(
        okHttpClient = client,
        options = HttpExecutor.Options(
            parseErrorMessage = { code, _ -> "lrclib returned $code" },
            emptyBodyText = "Empty response",
        ),
    )

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

    private suspend fun executeAndReadBody(request: Request): String =
        http.executeForBodyText(request)

    suspend fun getBestMatch(
        artistName: String,
        trackName: String,
        duration: Double?,
    ): Result<LrcLibTrack> = runCatchingRethrowingCancellation {
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
        json.decodeFromString<LrcLibTrackDto>(executeAndReadBody(request)).toDomain()
    }

    suspend fun search(query: String): Result<List<LrcLibTrack>> = runCatchingRethrowingCancellation {
        val url = "${BASE_URL}/api/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "JellyPlay")
            .get()
            .build()
        val body = executeAndReadBody(request)
        json.decodeFromString(ListSerializer(LrcLibTrackDto.serializer()), body).map { it.toDomain() }
    }

    suspend fun getById(id: Long): Result<LrcLibTrack> = runCatchingRethrowingCancellation {
        val request = Request.Builder()
            .url("$BASE_URL/api/get/$id")
            .header("User-Agent", "JellyPlay")
            .get()
            .build()
        json.decodeFromString<LrcLibTrackDto>(executeAndReadBody(request)).toDomain()
    }

    companion object {
        private const val BASE_URL = "https://lrclib.net"
    }
}
