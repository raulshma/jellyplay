package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.CreditTimestamps
import com.raulshma.jellyplay.core.model.IntroTimestamps
import com.raulshma.jellyplay.core.model.MediaSegment
import com.raulshma.jellyplay.core.model.MediaSegmentType
import com.raulshma.jellyplay.core.model.RemoteSubtitleInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.jellyfin.sdk.model.serializer.toUUID
import org.jellyfin.sdk.api.client.extensions.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackApiClientImpl @Inject constructor(
    private val engine: JellyfinApiEngine,
) : PlaybackApiClient {

    override suspend fun reportPlaybackStart(
        itemId: String,
        sessionId: String,
        playMethod: com.raulshma.jellyplay.core.model.PlayMethod,
    ): Result<Unit> =
        runCatching {
            val uuid = itemId.toUUID()
            val sdkPlayMethod = when (playMethod) {
                com.raulshma.jellyplay.core.model.PlayMethod.DIRECT_PLAY -> org.jellyfin.sdk.model.api.PlayMethod.DIRECT_PLAY
                com.raulshma.jellyplay.core.model.PlayMethod.DIRECT_STREAM -> org.jellyfin.sdk.model.api.PlayMethod.DIRECT_STREAM
                com.raulshma.jellyplay.core.model.PlayMethod.TRANSCODE -> org.jellyfin.sdk.model.api.PlayMethod.TRANSCODE
            }
            engine.requireApi().playStateApi.reportPlaybackStart(
                org.jellyfin.sdk.model.api.PlaybackStartInfo(
                    canSeek = true,
                    itemId = uuid,
                    sessionId = sessionId,
                    isPaused = false,
                    isMuted = false,
                    playMethod = sdkPlayMethod,
                    repeatMode = org.jellyfin.sdk.model.api.RepeatMode.REPEAT_NONE,
                    playbackOrder = org.jellyfin.sdk.model.api.PlaybackOrder.DEFAULT,
                )
            )
        }

    override suspend fun reportPlaybackProgress(
        itemId: String,
        sessionId: String,
        positionTicks: Long,
        isPaused: Boolean,
        playMethod: com.raulshma.jellyplay.core.model.PlayMethod,
    ): Result<Unit> = engine.apiResult {
        val uuid = itemId.toUUID()
        val sdkPlayMethod = when (playMethod) {
            com.raulshma.jellyplay.core.model.PlayMethod.DIRECT_PLAY -> org.jellyfin.sdk.model.api.PlayMethod.DIRECT_PLAY
            com.raulshma.jellyplay.core.model.PlayMethod.DIRECT_STREAM -> org.jellyfin.sdk.model.api.PlayMethod.DIRECT_STREAM
            com.raulshma.jellyplay.core.model.PlayMethod.TRANSCODE -> org.jellyfin.sdk.model.api.PlayMethod.TRANSCODE
        }
        engine.requireApi().playStateApi.reportPlaybackProgress(
            org.jellyfin.sdk.model.api.PlaybackProgressInfo(
                canSeek = true,
                itemId = uuid,
                sessionId = sessionId,
                positionTicks = positionTicks,
                isPaused = isPaused,
                isMuted = false,
                playMethod = sdkPlayMethod,
                repeatMode = org.jellyfin.sdk.model.api.RepeatMode.REPEAT_NONE,
                playbackOrder = org.jellyfin.sdk.model.api.PlaybackOrder.DEFAULT,
            )
        )
    }

    override suspend fun reportPlaybackStopped(
        itemId: String,
        sessionId: String,
        positionTicks: Long,
    ): Result<Unit> = engine.apiResult {
        val uuid = itemId.toUUID()
        engine.requireApi().playStateApi.reportPlaybackStopped(
            org.jellyfin.sdk.model.api.PlaybackStopInfo(
                itemId = uuid,
                sessionId = sessionId,
                positionTicks = positionTicks,
                failed = false,
            )
        )
    }

    override fun getStreamUrl(itemId: String, mediaSourceId: String, startTimeTicks: Long): String {
        val server = engine._currentServer.value ?: return ""
        val user = engine._currentUser.value ?: return ""
        return "${server.address}/Videos/$itemId/stream?static=true&mediaSourceId=$mediaSourceId&startTimeTicks=$startTimeTicks&api_key=${user.accessToken}"
    }

    override fun getSubtitleDeliveryUrl(deliveryUrl: String): String {
        val server = engine._currentServer.value ?: return ""
        val user = engine._currentUser.value ?: return ""
        val baseUrl = if (deliveryUrl.startsWith("http")) deliveryUrl else "${server.address}$deliveryUrl"
        val separator = if ("?" in baseUrl) "&" else "?"
        return "$baseUrl${separator}api_key=${user.accessToken}"
    }

    override fun buildSubtitleDeliveryUrl(
        itemId: String,
        mediaSourceId: String,
        index: Int,
        codec: String?,
    ): String {
        val server = engine._currentServer.value ?: return ""
        val user = engine._currentUser.value ?: return ""
        val format = when ((codec ?: "srt").lowercase()) {
            "subrip" -> "srt"
            "ass", "ssa" -> codec!!.lowercase()
            else -> (codec ?: "srt").lowercase()
        }
        return "${server.address}/Videos/$itemId/$mediaSourceId/Subtitles/$index/Stream.$format?api_key=${user.accessToken}"
    }

    override suspend fun getIntroTimestamps(itemId: String): Result<IntroTimestamps> = engine.apiResult {
        val server = engine._currentServer.value ?: throw IllegalStateException("No server")
        val user = engine._currentUser.value ?: throw IllegalStateException("No user")
        val url = "${server.address}/Items/$itemId/IntroSkipTimestamps"
        val request = Request.Builder()
            .url(url)
            .header("X-Emby-Token", user.accessToken)
            .build()
        engine.okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                IntroTimestamps(itemId)
            } else {
                val body = response.body?.string() ?: return@apiResult IntroTimestamps(itemId)
                JellyfinApiEngine.sharedJson.decodeFromString<IntroTimestamps>(body)
            }
        }
    }

    override suspend fun getCreditTimestamps(itemId: String): Result<CreditTimestamps> = engine.apiResult {
        val server = engine._currentServer.value ?: throw IllegalStateException("No server")
        val user = engine._currentUser.value ?: throw IllegalStateException("No user")
        val url = "${server.address}/Items/$itemId/CreditTimestamps"
        val request = Request.Builder()
            .url(url)
            .header("X-Emby-Token", user.accessToken)
            .build()
        engine.okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                CreditTimestamps(itemId)
            } else {
                val body = response.body?.string() ?: return@apiResult CreditTimestamps(itemId)
                JellyfinApiEngine.sharedJson.decodeFromString<CreditTimestamps>(body)
            }
        }
    }

    override suspend fun getMediaSegments(itemId: String): Result<List<MediaSegment>> = engine.apiResult {
        val server = engine._currentServer.value ?: throw IllegalStateException("No server")
        val user = engine._currentUser.value ?: throw IllegalStateException("No user")
        val url = "${server.address}/MediaSegments/$itemId"
        val request = Request.Builder()
            .url(url)
            .header("X-Emby-Token", user.accessToken)
            .build()
        engine.okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                emptyList()
            } else {
                val body = response.body?.string() ?: return@apiResult emptyList<MediaSegment>()
                val segmentsResponse = JellyfinApiEngine.sharedJson.decodeFromString<MediaSegmentsResponse>(body)
                segmentsResponse.Items.map { dto ->
                    MediaSegment(
                        id = dto.Id,
                        itemId = dto.ItemId,
                        type = MediaSegmentType.fromApiName(dto.Type),
                        startTicks = dto.StartTicks,
                        endTicks = dto.EndTicks,
                    )
                }
            }
        }
    }

    override suspend fun getRemoteSubtitles(itemId: String): Result<List<RemoteSubtitleInfo>> = engine.apiResult {
        val server = engine._currentServer.value ?: throw IllegalStateException("No server")
        val user = engine._currentUser.value ?: throw IllegalStateException("No user")
        val url = "${server.address}/Items/$itemId/RemoteSearch/Subtitles"
        val request = Request.Builder()
            .url(url)
            .header("X-Emby-Token", user.accessToken)
            .build()
        engine.okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@apiResult emptyList<RemoteSubtitleInfo>()
            val body = response.body?.string() ?: return@apiResult emptyList<RemoteSubtitleInfo>()
            JellyfinApiEngine.sharedJson.decodeFromString<List<RemoteSubtitleInfo>>(body)
        }
    }

    override suspend fun downloadRemoteSubtitle(itemId: String, subtitleId: String): Result<Unit> = engine.apiResult {
        val server = engine._currentServer.value ?: throw IllegalStateException("No server")
        val user = engine._currentUser.value ?: throw IllegalStateException("No user")
        val url = "${server.address}/Items/$itemId/RemoteSearch/Subtitles/$subtitleId"
        val request = Request.Builder()
            .url(url)
            .header("X-Emby-Token", user.accessToken)
            .post(okhttp3.RequestBody.create(null, ByteArray(0)))
            .build()
        engine.okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Failed to download subtitle: ${response.code}")
            }
        }
    }

    override suspend fun getTrickplayTileImage(itemId: String, width: Int, index: Int): ByteArray? =
        try {
            withContext(Dispatchers.IO) {
                engine.requireApi().trickplayApi.getTrickplayTileImage(
                    itemId = itemId.toUUID(),
                    width = width,
                    index = index,
                ).content
            }
        } catch (_: Exception) {
            null
        }

    override suspend fun getServerTime(): Result<com.raulshma.jellyplay.core.model.UtcTimeResponse> = engine.apiResult {
        val response = engine.requireApi().timeSyncApi.getUtcTime().content
        com.raulshma.jellyplay.core.model.UtcTimeResponse(
            requestReceptionTime = response.requestReceptionTime?.toString() ?: "",
            responseTransmissionTime = response.responseTransmissionTime?.toString() ?: "",
        )
    }
}

@kotlinx.serialization.Serializable
private data class MediaSegmentDto(
    val Id: String,
    val ItemId: String,
    val Type: String,
    val StartTicks: Long,
    val EndTicks: Long,
)

@kotlinx.serialization.Serializable
private data class MediaSegmentsResponse(
    val Items: List<MediaSegmentDto> = emptyList(),
    val TotalRecordCount: Int = 0,
)
