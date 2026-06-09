package com.raulshma.jellyplay.core.network.api

import android.content.Context
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.UserInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.ClientCapabilitiesDto
import org.jellyfin.sdk.model.api.DeviceProfile
import org.jellyfin.sdk.model.api.GeneralCommandType
import org.jellyfin.sdk.model.api.MediaType as SdkMediaType
import org.jellyfin.sdk.model.api.SubtitleDeliveryMethod
import org.jellyfin.sdk.model.api.SubtitleProfile
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JellyfinApiEngine @Inject constructor(
    @ApplicationContext val context: Context,
    val jellyfin: Jellyfin,
    val okHttpClient: OkHttpClient,
) {
    val _currentServer = MutableStateFlow<ServerInfo?>(null)
    val currentServer: Flow<ServerInfo?> = _currentServer.asStateFlow()

    val _currentUser = MutableStateFlow<UserInfo?>(null)
    val currentUser: Flow<UserInfo?> = _currentUser.asStateFlow()

    val authMutex = Mutex()

    @Volatile
    var api: ApiClient? = null

    fun requireApi(): ApiClient =
        api ?: throw IllegalStateException("Not connected to server")

    suspend fun <T> apiResult(block: suspend () -> T): Result<T> =
        runCatching { withContext(Dispatchers.IO) { block() } }

    suspend fun <T> apiResultWithRetry(
        maxRetries: Int = 3,
        block: suspend () -> T,
    ): Result<T> {
        var lastResult = apiResult(block)
        repeat(maxRetries) { attempt ->
            if (lastResult.isSuccess) return lastResult
            val exception = lastResult.exceptionOrNull() ?: return lastResult
            if (!isRetryable(exception)) return lastResult
            val backoffMs = calculateRetryBackoff(attempt)
            delay(backoffMs)
            lastResult = apiResult(block)
        }
        return lastResult
    }

    private fun isRetryable(exception: Throwable): Boolean {
        if (exception is CancellationException) return false
        when (exception) {
            is SocketTimeoutException -> return true
            is ConnectException -> return true
            is UnknownHostException -> return true
            is IOException -> return true
        }
        val message = exception.message ?: return false
        return setOf(429, 500, 502, 503, 504).any { code ->
            message.contains("HTTP $code")
        }
    }

    private fun calculateRetryBackoff(attempt: Int): Long {
        val baseDelayMs = 1_000L
        val maxDelayMs = 8_000L
        val exponentialDelay = (baseDelayMs * 2.0.pow(attempt)).toLong()
        val capped = min(exponentialDelay, maxDelayMs)
        return Random.nextLong(0, capped + 1)
    }

    val currentMaxParentalRating: Int?
        get() = _currentUser.value?.maxParentalAgeRating

    fun ratingToAge(rating: String): Int? = when (rating.uppercase()) {
        "G", "TV-Y", "TV-G" -> 0
        "PG", "TV-Y7", "TV-PG" -> 7
        "PG-13", "TV-14" -> 13
        "R", "TV-MA" -> 17
        "NC-17" -> 18
        else -> null
    }

    fun <T : com.raulshma.jellyplay.core.model.MediaItem> List<T>.filterByParentalRating(): List<T> {
        val max = currentMaxParentalRating ?: return this
        return mapNotNull { item ->
            if (item.officialRating?.let { rating ->
                    ratingToAge(rating)?.let { age -> age <= max }
                } != false) item else null
        }
    }

    companion object {
        val sharedJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        private val SUPPORTED_REMOTE_COMMANDS = listOf(
            GeneralCommandType.SET_VOLUME,
            GeneralCommandType.VOLUME_UP,
            GeneralCommandType.VOLUME_DOWN,
            GeneralCommandType.MUTE,
            GeneralCommandType.UNMUTE,
            GeneralCommandType.TOGGLE_MUTE,
            GeneralCommandType.SET_AUDIO_STREAM_INDEX,
            GeneralCommandType.SET_SUBTITLE_STREAM_INDEX,
            GeneralCommandType.SET_REPEAT_MODE,
            GeneralCommandType.SET_SHUFFLE_QUEUE,
            GeneralCommandType.SET_PLAYBACK_ORDER,
            GeneralCommandType.SET_MAX_STREAMING_BITRATE,
            GeneralCommandType.TOGGLE_FULLSCREEN,
            GeneralCommandType.DISPLAY_MESSAGE,
            GeneralCommandType.PLAY,
        )
        val CACHED_CAPABILITIES by lazy {
            ClientCapabilitiesDto(
                playableMediaTypes = listOf(SdkMediaType.VIDEO, SdkMediaType.AUDIO),
                supportedCommands = SUPPORTED_REMOTE_COMMANDS,
                supportsMediaControl = true,
                supportsPersistentIdentifier = true,
                deviceProfile = DeviceProfile(
                    directPlayProfiles = emptyList(),
                    transcodingProfiles = emptyList(),
                    containerProfiles = emptyList(),
                    codecProfiles = emptyList(),
                    subtitleProfiles = listOf(
                        SubtitleProfile(format = "srt", method = SubtitleDeliveryMethod.EXTERNAL),
                        SubtitleProfile(format = "ass", method = SubtitleDeliveryMethod.EXTERNAL),
                        SubtitleProfile(format = "ssa", method = SubtitleDeliveryMethod.EXTERNAL),
                        SubtitleProfile(format = "subrip", method = SubtitleDeliveryMethod.EXTERNAL),
                        SubtitleProfile(format = "vtt", method = SubtitleDeliveryMethod.EXTERNAL),
                        SubtitleProfile(format = "webvtt", method = SubtitleDeliveryMethod.EXTERNAL),
                    ),
                ),
            )
        }
    }
}
