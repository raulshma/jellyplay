package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.CreditTimestamps
import com.raulshma.jellyplay.core.model.IntroTimestamps
import com.raulshma.jellyplay.core.model.MediaSegment
import com.raulshma.jellyplay.core.model.MediaSegmentType
import com.raulshma.jellyplay.core.model.MediaSource
import com.raulshma.jellyplay.core.model.PlaybackInfoResult
import com.raulshma.jellyplay.core.model.PlaybackMode
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.RemoteSubtitleInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jellyfin.sdk.model.api.PlaybackInfoDto
import org.jellyfin.sdk.model.serializer.toUUID
import org.jellyfin.sdk.api.client.extensions.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackApiClientImpl @Inject constructor(
    private val engine: JellyfinApiEngine,
    private val deviceProfileProvider: DeviceProfileProvider,
    private val preferencesStore: com.raulshma.jellyplay.core.datastore.UserPreferencesStore,
) : PlaybackApiClient {

    override suspend fun reportPlaybackStart(
        itemId: String,
        sessionId: String,
        playMethod: com.raulshma.jellyplay.core.model.PlayMethod,
    ): Result<Unit> =
        engine.apiResultWithRetry {
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
    ): Result<Unit> = engine.apiResultWithRetry {
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
    ): Result<Unit> = engine.apiResultWithRetry {
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

    override fun getStreamUrl(
        itemId: String,
        mediaSourceId: String,
        startTimeTicks: Long,
        liveStreamId: String?,
    ): String {
        return getStreamUrl(
            itemId = itemId,
            mediaSourceId = mediaSourceId,
            startTimeTicks = startTimeTicks,
            maxBitrate = null,
            useAudioEndpoint = false,
            liveStreamId = liveStreamId,
        )
    }

    override fun getStreamUrl(
        itemId: String,
        mediaSourceId: String,
        startTimeTicks: Long,
        maxBitrate: Int?,
        useAudioEndpoint: Boolean,
        liveStreamId: String?,
    ): String {
        val server = engine.currentServer.value ?: return ""
        val user = engine.currentUser.value ?: return ""
        val isLive = !liveStreamId.isNullOrBlank()
        val path = if (useAudioEndpoint) {
            "/Audio/$itemId/universal"
        } else {
            "/Videos/$itemId/stream"
        }
        val baseParams = buildString {
            append("mediaSourceId=$mediaSourceId")
            append("&startTimeTicks=$startTimeTicks")
            if (maxBitrate != null && maxBitrate > 0) {
                append("&maxBitrate=$maxBitrate")
            }
            if (useAudioEndpoint) {
                append("&deviceId=${user.serverId}")
                append("&userId=${user.id}")
            }
            // Echo the live-stream id so the server opens/attaches the tuner
            // session. Required for Live TV channels; omitted for VOD.
            if (isLive) append("&LiveStreamId=$liveStreamId")
        }
        // Static direct-play only applies to VOD files. Live sources are
        // opened as a (growing) direct stream — `static=true` makes the server
        // try a byte-range seek on a non-seekable stream and fail.
        val paramPrefix = if (useAudioEndpoint || isLive) "?" else "?static=true&"
        return "${server.address}$path$paramPrefix$baseParams&api_key=${user.accessToken}"
    }

    override suspend fun fetchPlaybackInfo(
        itemId: String,
        mediaSourceId: String,
        startTimeTicks: Long,
        audioStreamIndex: Int?,
        subtitleStreamIndex: Int?,
        maxStreamingBitrateBits: Long?,
        mode: PlaybackMode,
        playerType: PlayerType,
    ): Result<PlaybackInfoResult> = engine.apiResultWithRetry {
        val api = engine.requireApi()
        val uuid = itemId.toUUID()

        val enableDirectPlay = mode != PlaybackMode.FORCE_TRANSCODE
        val enableDirectStream = mode == PlaybackMode.AUTO
        val enableTranscoding = mode != PlaybackMode.FORCE_DIRECT_PLAY
        // Stream copy is only relevant when direct stream is allowed.
        val allowStreamCopy = enableDirectStream
        // Bitrate ceiling is sent for AUTO and FORCE_TRANSCODE (so a forced
        // transcode still targets the chosen resolution) but omitted for
        // FORCE_DIRECT_PLAY (no cap — the file is served verbatim).
        val sendBitrate = if (mode == PlaybackMode.FORCE_DIRECT_PLAY) null else maxStreamingBitrateBits

        val dto = PlaybackInfoDto(
            userId = engine.currentUser.value?.id?.toUUID(),
            startTimeTicks = startTimeTicks.takeIf { it > 0 },
            maxStreamingBitrate = sendBitrate?.toInt(),
            audioStreamIndex = audioStreamIndex,
            subtitleStreamIndex = subtitleStreamIndex,
            mediaSourceId = mediaSourceId.takeIf { it.isNotBlank() },
            deviceProfile = deviceProfileProvider.forPlayer(
                playerType = playerType,
                pgsDirectPlay = preferencesStore.preferences.value.pgsSubtitleDirectPlay,
            ),
            enableDirectPlay = enableDirectPlay,
            enableDirectStream = enableDirectStream,
            enableTranscoding = enableTranscoding,
            allowVideoStreamCopy = allowStreamCopy,
            allowAudioStreamCopy = allowStreamCopy,
            autoOpenLiveStream = true,
        )

        val response = api.mediaInfoApi.getPostedPlaybackInfo(uuid, dto).content
        PlaybackInfoResult(
            playSessionId = response.playSessionId,
            mediaSources = response.mediaSources.orEmpty().map { it.toMediaSource() },
        )
    }

    override fun getSubtitleDeliveryUrl(deliveryUrl: String): String {
        val server = engine.currentServer.value ?: return ""
        val user = engine.currentUser.value ?: return ""
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
        val server = engine.currentServer.value ?: return ""
        val user = engine.currentUser.value ?: return ""
        val format = when ((codec ?: "srt").lowercase()) {
            "subrip" -> "srt"
            "ass", "ssa" -> codec!!.lowercase()
            else -> (codec ?: "srt").lowercase()
        }
        return "${server.address}/Videos/$itemId/$mediaSourceId/Subtitles/$index/Stream.$format?api_key=${user.accessToken}"
    }

    override suspend fun getIntroTimestamps(itemId: String): Result<IntroTimestamps> = engine.apiResultWithRetry {
        val server = engine.currentServer.value ?: throw IllegalStateException("No server")
        val user = engine.currentUser.value ?: throw IllegalStateException("No user")
        val url = "${server.address}/Items/$itemId/IntroSkipTimestamps"
        val request = Request.Builder()
            .url(url)
            .header("X-Emby-Token", user.accessToken)
            .build()
        engine.okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                IntroTimestamps(itemId)
            } else {
                val body = response.body?.string() ?: return@apiResultWithRetry IntroTimestamps(itemId)
                JellyfinApiEngine.sharedJson.decodeFromString<IntroTimestamps>(body)
            }
        }
    }

    override suspend fun getCreditTimestamps(itemId: String): Result<CreditTimestamps> = engine.apiResultWithRetry {
        val server = engine.currentServer.value ?: throw IllegalStateException("No server")
        val user = engine.currentUser.value ?: throw IllegalStateException("No user")
        val url = "${server.address}/Items/$itemId/CreditTimestamps"
        val request = Request.Builder()
            .url(url)
            .header("X-Emby-Token", user.accessToken)
            .build()
        engine.okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                CreditTimestamps(itemId)
            } else {
                val body = response.body?.string() ?: return@apiResultWithRetry CreditTimestamps(itemId)
                JellyfinApiEngine.sharedJson.decodeFromString<CreditTimestamps>(body)
            }
        }
    }

    override suspend fun getMediaSegments(itemId: String): Result<List<MediaSegment>> = engine.apiResultWithRetry {
        val segments = runCatching {
            engine.requireApi().mediaSegmentsApi.getItemSegments(itemId = itemId.toUUID()).content
        }.getOrNull() ?: return@apiResultWithRetry emptyList()
        segments.items.orEmpty().map { dto ->
            MediaSegment(
                id = dto.id?.toString() ?: "",
                itemId = dto.itemId?.toString() ?: itemId,
                type = MediaSegmentType.fromApiName(dto.type.serialName),
                startTicks = dto.startTicks,
                endTicks = dto.endTicks,
            )
        }
    }

    override suspend fun getRemoteSubtitles(itemId: String): Result<List<RemoteSubtitleInfo>> = engine.apiResultWithRetry {
        val server = engine.currentServer.value ?: throw IllegalStateException("No server")
        val user = engine.currentUser.value ?: throw IllegalStateException("No user")
        val url = "${server.address}/Items/$itemId/RemoteSearch/Subtitles"
        val request = Request.Builder()
            .url(url)
            .header("X-Emby-Token", user.accessToken)
            .build()
        engine.okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@apiResultWithRetry emptyList<RemoteSubtitleInfo>()
            val body = response.body?.string() ?: return@apiResultWithRetry emptyList<RemoteSubtitleInfo>()
            JellyfinApiEngine.sharedJson.decodeFromString<List<RemoteSubtitleInfo>>(body)
        }
    }

    override suspend fun downloadRemoteSubtitle(itemId: String, subtitleId: String): Result<Unit> = engine.apiResultWithRetry {
        engine.requireApi().subtitleApi.downloadRemoteSubtitles(
            itemId = itemId.toUUID(),
            subtitleId = subtitleId,
        )
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

    override suspend fun getServerTime(): Result<com.raulshma.jellyplay.core.model.UtcTimeResponse> = engine.apiResultWithRetry {
        val response = engine.requireApi().timeSyncApi.getUtcTime().content
        com.raulshma.jellyplay.core.model.UtcTimeResponse(
            requestReceptionTime = response.requestReceptionTime?.toString() ?: "",
            responseTransmissionTime = response.responseTransmissionTime?.toString() ?: "",
        )
    }
}
