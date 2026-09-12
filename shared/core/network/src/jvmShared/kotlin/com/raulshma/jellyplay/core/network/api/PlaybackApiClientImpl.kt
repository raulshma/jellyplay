package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import com.raulshma.jellyplay.core.model.CreditTimestamps
import com.raulshma.jellyplay.core.model.IntroTimestamps
import com.raulshma.jellyplay.core.model.LiveStreamOption
import com.raulshma.jellyplay.core.model.MediaSegment
import com.raulshma.jellyplay.core.model.MediaSegmentType
import com.raulshma.jellyplay.core.model.MediaSource
import com.raulshma.jellyplay.core.model.PlaybackInfoResult
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.core.model.PlaybackMode
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.RemoteSubtitleInfo
import com.raulshma.jellyplay.core.network.playback.buildStreamUrl
import com.raulshma.jellyplay.core.network.playback.buildSubtitleDeliveryUrl
import com.raulshma.jellyplay.core.network.playback.resolveSubtitleDeliveryUrl
import com.raulshma.jellyplay.core.datastore.playback.PlaybackStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.PlaybackInfoDto
import org.jellyfin.sdk.model.serializer.toUUID
import org.jellyfin.sdk.api.client.extensions.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackApiClientImpl @Inject constructor(
    private val engine: JellyfinApiEngine,
    private val deviceProfileProvider: DeviceProfileProvider,
    private val playbackStore: PlaybackStore,
) : PlaybackApiClient {

    private val rawRequester = JellyfinRawRequester(engine)

    /**
     * Base URL for the shared stream/subtitle URL builders: the
     * router's active endpoint, falling back to the current server's primary
     * address when routing is not configured. [server] must be the non-null
     * current server the caller already checked. The raw-OkHttp endpoints go
     * through [rawRequester] instead, which resolves the same base plus its
     * session guard in one place.
     */
    private fun activeBaseUrl(server: ServerInfo): String =
        engine.activeServerAddress ?: server.address

    /**
     * The authenticated server/user session pair behind every URL the client
     * builds, or null when either half is missing — callers yield the
     * empty-URL sentinel in that case.
     */
    private fun activeSession(): Pair<ServerInfo, UserInfo>? {
        val server = engine.currentServer.value ?: return null
        val user = engine.currentUser.value ?: return null
        return server to user
    }

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

    override suspend fun reportBookProgress(itemId: String, positionTicks: Long): Result<Unit> =
        engine.apiResultWithRetry {
            // Books never open a playback session; the session-less book
            // endpoint takes the position as a query parameter. Raw (not the
            // SDK's playStateApi): jellyfin-api 1.8.12 has no typed binding
            // for /Users/{userId}/PlayingItems/{itemId}/Progress.
            val userId = engine.requireUserId()
            rawRequester.postStatusOnly(
                path = "/Users/$userId/PlayingItems/$itemId/Progress?positionTicks=$positionTicks",
                failureMessage = "Report book progress failed",
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
        val (server, user) = activeSession() ?: return ""
        // Unlike the former inline string building, the shared helper trims
        // a trailing '/' off the base, so a trailing-slash
        // active endpoint no longer yields "//Videos/…" (the wasm side
        // always had the trim — it only ever went through this helper).
        // Pinned exact-string by PlaybackUrlBuilderTest (commonTest).
        return buildStreamUrl(
            baseUrl = activeBaseUrl(server),
            apiKey = user.accessToken,
            userId = user.id,
            userServerId = user.serverId,
            itemId = itemId,
            mediaSourceId = mediaSourceId,
            startTimeTicks = startTimeTicks,
            maxBitrate = maxBitrate,
            useAudioEndpoint = useAudioEndpoint,
            liveStreamId = liveStreamId,
        )
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
        liveStreamOption: LiveStreamOption?,
    ): Result<PlaybackInfoResult> = engine.apiResultWithRetry {
        val api = engine.requireApi()
        val uuid = itemId.toUUID()

        val flags = resolvePlaybackFlags(mode, liveStreamOption, maxStreamingBitrateBits)

        // FORCE_DIRECT_PLAY sends "Direct play all" profile so
        // the server unconditionally marks sources as directly playable and
        // hands back a `?static=true` URL — the client owns the decision to
        // serve the file verbatim AUTO and
        // FORCE_TRANSCODE use the codec-aware profile so the server picks the
        // right play method and transcode target.
        val deviceProfile = if (flags.useDirectPlayAllProfile) {
            deviceProfileProvider.directPlayAll
        } else {
            deviceProfileProvider.forPlayer(
                playerType = playerType,
                pgsDirectPlay = playbackStore.playback.value.pgsSubtitleDirectPlay,
            )
        }

        val dto = PlaybackInfoDto(
            userId = engine.currentUserId()?.toUUID(),
            startTimeTicks = startTimeTicks.takeIf { it > 0 },
            maxStreamingBitrate = flags.sendBitrate?.toInt(),
            audioStreamIndex = audioStreamIndex,
            subtitleStreamIndex = subtitleStreamIndex,
            mediaSourceId = mediaSourceId.takeIf { it.isNotBlank() },
            deviceProfile = deviceProfile,
            enableDirectPlay = flags.enableDirectPlay,
            enableDirectStream = flags.enableDirectStream,
            enableTranscoding = flags.enableTranscoding,
            allowVideoStreamCopy = flags.allowStreamCopy,
            allowAudioStreamCopy = flags.allowStreamCopy,
            autoOpenLiveStream = true,
        )

        val response = api.mediaInfoApi.getPostedPlaybackInfo(uuid, dto).content
        PlaybackInfoResult(
            playSessionId = response.playSessionId,
            mediaSources = response.mediaSources.orEmpty().map { it.toMediaSource() },
        )
    }

    override suspend fun fetchActiveTranscodeReasons(itemId: String): Result<List<String>> =
        engine.apiResultWithRetry {
            val api = engine.requireApi()
            val uuid = itemId.toUUID()
            val sessions = api.sessionApi.getSessions().content
            // Match this device's session playing the item; the SDK client's
            // deviceInfo.id is the DataStore UUID shared with the socket and
            // the Play On device list (see NetworkModule.provideJellyfin).
            sessions.firstOrNull { session ->
                session.deviceId == api.deviceInfo.id &&
                    session.nowPlayingItem?.id == uuid
            }?.transcodingInfo?.transcodeReasons.orEmpty().map { it.name }
        }

    override fun getSubtitleDeliveryUrl(deliveryUrl: String): String {
        val (server, user) = activeSession() ?: return ""
        // Trailing-slash handling as in getStreamUrl: the shared helper owns the
        // base handling (trimEnd) for both platforms now.
        return resolveSubtitleDeliveryUrl(
            baseUrl = activeBaseUrl(server),
            apiKey = user.accessToken,
            deliveryUrl = deliveryUrl,
        )
    }

    override fun buildSubtitleDeliveryUrl(
        itemId: String,
        mediaSourceId: String,
        index: Int,
        codec: String?,
    ): String {
        val (server, user) = activeSession() ?: return ""
        // Trailing-slash handling as in getStreamUrl: the shared helper owns the
        // base handling (trimEnd) for both platforms now.
        return buildSubtitleDeliveryUrl(
            baseUrl = activeBaseUrl(server),
            apiKey = user.accessToken,
            itemId = itemId,
            mediaSourceId = mediaSourceId,
            index = index,
            codec = codec,
        )
    }

    override suspend fun getIntroTimestamps(itemId: String): Result<IntroTimestamps> = engine.apiResultWithRetry {
        val body = rawRequester.getBodyText(
            path = "/Items/$itemId/IntroSkipTimestamps",
            session = rawRequester.requirePlaybackSession(),
        ) ?: return@apiResultWithRetry IntroTimestamps(itemId)
        JellyfinApiEngine.sharedJson.decodeFromString<IntroTimestamps>(body)
    }

    override suspend fun getCreditTimestamps(itemId: String): Result<CreditTimestamps> = engine.apiResultWithRetry {
        val body = rawRequester.getBodyText(
            path = "/Items/$itemId/CreditTimestamps",
            session = rawRequester.requirePlaybackSession(),
        ) ?: return@apiResultWithRetry CreditTimestamps(itemId)
        JellyfinApiEngine.sharedJson.decodeFromString<CreditTimestamps>(body)
    }

    override suspend fun getMediaSegments(itemId: String): Result<List<MediaSegment>> = engine.apiResultWithRetry {
        val segments = runCatchingRethrowingCancellation {
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
        val body = rawRequester.getBodyText(
            path = "/Items/$itemId/RemoteSearch/Subtitles",
            session = rawRequester.requirePlaybackSession(),
        ) ?: return@apiResultWithRetry emptyList<RemoteSubtitleInfo>()
        JellyfinApiEngine.sharedJson.decodeFromString<List<RemoteSubtitleInfo>>(body)
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

    override suspend fun getItemImageBytes(itemId: String, imageType: String, maxWidth: Int): ByteArray? =
        try {
            withContext(Dispatchers.IO) {
                val imageTypeEnum = org.jellyfin.sdk.model.api.ImageType.fromNameOrNull(imageType)
                    ?: return@withContext null
                engine.requireApi().imageApi.getItemImage(
                    itemId = itemId.toUUID(),
                    imageType = imageTypeEnum,
                    maxWidth = maxWidth,
                ).content
            }
        } catch (_: Exception) {
            null
        }
}
