package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.CreditTimestamps
import com.raulshma.jellyplay.core.model.IntroTimestamps
import com.raulshma.jellyplay.core.model.LiveStreamOption
import com.raulshma.jellyplay.core.model.MediaSegment
import com.raulshma.jellyplay.core.model.MediaSegmentType
import com.raulshma.jellyplay.core.model.PlaybackInfoResult
import com.raulshma.jellyplay.core.model.PlaybackMode
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.PlayMethod
import com.raulshma.jellyplay.core.model.RemoteSubtitleInfo
import com.raulshma.jellyplay.core.model.UtcTimeResponse
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.core.network.auth.AtomicSessionState
import com.raulshma.jellyplay.core.network.library.KNOWN_IMAGE_TYPES
import com.raulshma.jellyplay.core.network.library.toMediaSource
import com.raulshma.jellyplay.core.network.playback.MediaSegmentQueryResultDtoWire
import com.raulshma.jellyplay.core.network.playback.PlaybackInfoRequestDtoWire
import com.raulshma.jellyplay.core.network.playback.PlaybackInfoResponseDtoWire
import com.raulshma.jellyplay.core.network.playback.PlaybackProgressInfoDtoWire
import com.raulshma.jellyplay.core.network.playback.PlaybackStartInfoDtoWire
import com.raulshma.jellyplay.core.network.playback.PlaybackStopInfoDtoWire
import com.raulshma.jellyplay.core.network.playback.SessionInfoDtoWire
import com.raulshma.jellyplay.core.network.playback.UtcTimeDtoWire
import com.raulshma.jellyplay.core.network.playback.buildStreamUrl
import com.raulshma.jellyplay.core.network.playback.buildSubtitleDeliveryUrl
import com.raulshma.jellyplay.core.network.playback.resolveSubtitleDeliveryUrl
import com.raulshma.jellyplay.core.network.playback.resolveWasmPlaybackFlags
import com.raulshma.jellyplay.core.network.playback.transcodeReasonName
import com.raulshma.jellyplay.core.network.playback.wireName
import io.ktor.client.HttpClient

/**
 * Phase W chunk 2: the wasmJs [PlaybackApiClient] — a hand-rolled Ktor
 * replacement for the jvmShared `PlaybackApiClientImpl` (Jellyfin SDK +
 * OkHttp). Endpoint paths, request bodies, the playback-mode flag table and
 * mapping semantics mirror the JVM implementation; the URL builders
 * (stream/subtitle) and the flag table are shared pure functions in
 * commonMain `playback/` so commonTest pins them.
 *
 * wasm v1 deltas vs the JVM impl (documented, none affect JVM):
 *  - No DeviceProfile sent with `PlaybackInfo` (JVM picks codec-aware /
 *    "direct play all" profiles per mode; wasm defers codec negotiation to
 *    HtmlVideoEngine, a later Phase W chunk). The enable-allow flag table
 *    and bitrate cap still honor [PlaybackMode]/[LiveStreamOption] via
 *    [resolveWasmPlaybackFlags].
 *  - No failover router: URLs derive from the atomic session's current
 *    server address (JVM: `activeServerAddress`).
 *  - `PlayerType` and the pgs-subtitle preference are accepted but unused
 *     for v1 (they only select device profiles, which wasm does not send).
 *  - Intro/credit timestamps keep raw wire date strings where the model
 *    carries them (none — only ticks), and the remote-subtitle DTO decodes
 *    straight into the app model like the JVM path.
 */
class KtorWasmPlaybackApiClient(
    httpClient: HttpClient,
    sessionState: AtomicSessionState,
    identity: WasmClientIdentity,
) : WasmApiSupport(httpClient, sessionState, identity), PlaybackApiClient {

    private val currentUser: UserInfo? get() = sessionState.currentUser.value

    // ── Playback progress reporting ────────────────────────────────────────

    override suspend fun reportPlaybackStart(
        itemId: String,
        sessionId: String,
        playMethod: PlayMethod,
    ): Result<Unit> = apiResultWithRetry {
        val server = requireConnectedServer()
        postStatusOnly(
            url = apiUrl(server.address, "/Sessions/Playing"),
            accessToken = currentToken(),
            bodyText = encodeBody(
                PlaybackStartInfoDtoWire(
                    canSeek = true,
                    itemId = itemId,
                    sessionId = sessionId,
                    isPaused = false,
                    isMuted = false,
                    playMethod = playMethod.wireName(),
                    repeatMode = "RepeatNone",
                    playbackOrder = "Default",
                ),
            ),
        )
    }

    override suspend fun reportPlaybackProgress(
        itemId: String,
        sessionId: String,
        positionTicks: Long,
        isPaused: Boolean,
        playMethod: PlayMethod,
    ): Result<Unit> = apiResultWithRetry {
        val server = requireConnectedServer()
        postStatusOnly(
            url = apiUrl(server.address, "/Sessions/Playing/Progress"),
            accessToken = currentToken(),
            bodyText = encodeBody(
                PlaybackProgressInfoDtoWire(
                    canSeek = true,
                    itemId = itemId,
                    sessionId = sessionId,
                    positionTicks = positionTicks,
                    isPaused = isPaused,
                    isMuted = false,
                    playMethod = playMethod.wireName(),
                    repeatMode = "RepeatNone",
                    playbackOrder = "Default",
                ),
            ),
        )
    }

    override suspend fun reportPlaybackStopped(
        itemId: String,
        sessionId: String,
        positionTicks: Long,
    ): Result<Unit> = apiResultWithRetry {
        val server = requireConnectedServer()
        postStatusOnly(
            url = apiUrl(server.address, "/Sessions/Playing/Stopped"),
            accessToken = currentToken(),
            bodyText = encodeBody(
                PlaybackStopInfoDtoWire(
                    itemId = itemId,
                    sessionId = sessionId,
                    positionTicks = positionTicks,
                    failed = false,
                ),
            ),
        )
    }

    // ── Stream / subtitle URL builders (pure; ported verbatim) ─────────────

    override fun getStreamUrl(
        itemId: String,
        mediaSourceId: String,
        startTimeTicks: Long,
        liveStreamId: String?,
    ): String = getStreamUrl(
        itemId = itemId,
        mediaSourceId = mediaSourceId,
        startTimeTicks = startTimeTicks,
        maxBitrate = null,
        useAudioEndpoint = false,
        liveStreamId = liveStreamId,
    )

    override fun getStreamUrl(
        itemId: String,
        mediaSourceId: String,
        startTimeTicks: Long,
        maxBitrate: Int?,
        useAudioEndpoint: Boolean,
        liveStreamId: String?,
    ): String {
        val server = sessionState.currentServer.value ?: return ""
        val user = currentUser ?: return ""
        return buildStreamUrl(
            baseUrl = server.address,
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

    // ── PlaybackInfo ───────────────────────────────────────────────────────

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
    ): Result<PlaybackInfoResult> = apiResultWithRetry {
        val server = requireConnectedServer()
        val flags = resolveWasmPlaybackFlags(mode, liveStreamOption, maxStreamingBitrateBits)
        val response = postForJson<PlaybackInfoResponseDtoWire>(
            url = apiUrl(server.address, "/Items/$itemId/PlaybackInfo"),
            accessToken = currentToken(),
            bodyText = encodeBody(
                PlaybackInfoRequestDtoWire(
                    userId = currentUser?.id,
                    startTimeTicks = startTimeTicks.takeIf { it > 0 },
                    maxStreamingBitrate = flags.sendBitrate?.toInt(),
                    audioStreamIndex = audioStreamIndex,
                    subtitleStreamIndex = subtitleStreamIndex,
                    mediaSourceId = mediaSourceId.takeIf { it.isNotBlank() },
                    enableDirectPlay = flags.enableDirectPlay,
                    enableDirectStream = flags.enableDirectStream,
                    enableTranscoding = flags.enableTranscoding,
                    allowVideoStreamCopy = flags.allowStreamCopy,
                    allowAudioStreamCopy = flags.allowStreamCopy,
                    autoOpenLiveStream = true,
                ),
            ),
        )
        PlaybackInfoResult(
            playSessionId = response.playSessionId,
            mediaSources = response.mediaSources.map { it.toMediaSource() },
        )
    }

    override suspend fun fetchActiveTranscodeReasons(itemId: String): Result<List<String>> =
        apiResultWithRetry {
            val server = requireConnectedServer()
            val sessions = getJson<List<SessionInfoDtoWire>>(
                url = apiUrl(server.address, "/Sessions"),
                accessToken = currentToken(),
            )
            // Match this device's session playing the item — the device id is
            // the random-per-boot identity shared with the Authorization
            // header (JVM: the DataStore UUID shared with the socket).
            sessions.firstOrNull { session ->
                session.deviceId == deviceId &&
                    session.nowPlayingItem?.id == itemId
            }?.transcodingInfo?.transcodeReasons.orEmpty().map { transcodeReasonName(it) }
        }

    // ── Subtitles ──────────────────────────────────────────────────────────

    override fun getSubtitleDeliveryUrl(deliveryUrl: String): String {
        val server = sessionState.currentServer.value ?: return ""
        val user = currentUser ?: return ""
        return resolveSubtitleDeliveryUrl(
            baseUrl = server.address,
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
        val server = sessionState.currentServer.value ?: return ""
        val user = currentUser ?: return ""
        return buildSubtitleDeliveryUrl(
            baseUrl = server.address,
            apiKey = user.accessToken,
            itemId = itemId,
            mediaSourceId = mediaSourceId,
            index = index,
            codec = codec,
        )
    }

    override suspend fun getRemoteSubtitles(itemId: String): Result<List<RemoteSubtitleInfo>> =
        apiResultWithRetry {
            val server = requireConnectedServer()
            val user = requireCurrentUser()
            val body = getBodyTextWithEmbyToken(
                url = apiUrl(server.address, "/Items/$itemId/RemoteSearch/Subtitles"),
                accessToken = user.accessToken,
            ) ?: return@apiResultWithRetry emptyList<RemoteSubtitleInfo>()
            wireJson.decodeFromString<List<RemoteSubtitleInfo>>(body)
        }

    override suspend fun downloadRemoteSubtitle(itemId: String, subtitleId: String): Result<Unit> =
        apiResultWithRetry {
            val server = requireConnectedServer()
            postStatusOnly(
                url = apiUrl(server.address, "/Items/$itemId/RemoteSearch/Subtitles/$subtitleId"),
                accessToken = currentToken(),
            )
        }

    // ── Segments / timestamps ──────────────────────────────────────────────

    override suspend fun getIntroTimestamps(itemId: String): Result<IntroTimestamps> =
        apiResultWithRetry {
            val server = requireConnectedServer()
            val user = requireCurrentUser()
            val body = getBodyTextWithEmbyToken(
                url = apiUrl(server.address, "/Items/$itemId/IntroSkipTimestamps"),
                accessToken = user.accessToken,
            ) ?: return@apiResultWithRetry IntroTimestamps(itemId)
            wireJson.decodeFromString<IntroTimestamps>(body)
        }

    override suspend fun getCreditTimestamps(itemId: String): Result<CreditTimestamps> =
        apiResultWithRetry {
            val server = requireConnectedServer()
            val user = requireCurrentUser()
            val body = getBodyTextWithEmbyToken(
                url = apiUrl(server.address, "/Items/$itemId/CreditTimestamps"),
                accessToken = user.accessToken,
            ) ?: return@apiResultWithRetry CreditTimestamps(itemId)
            wireJson.decodeFromString<CreditTimestamps>(body)
        }

    override suspend fun getMediaSegments(itemId: String): Result<List<MediaSegment>> =
        apiResultWithRetry {
            val server = requireConnectedServer()
            val segments = runCatching {
                getJson<MediaSegmentQueryResultDtoWire>(
                    url = apiUrl(server.address, "/MediaSegments/$itemId"),
                    accessToken = currentToken(),
                )
            }.getOrNull() ?: return@apiResultWithRetry emptyList<MediaSegment>()
            segments.items.map { dto ->
                MediaSegment(
                    id = dto.id ?: "",
                    itemId = dto.itemId ?: itemId,
                    type = MediaSegmentType.fromApiName(dto.type ?: ""),
                    startTicks = dto.startTicks,
                    endTicks = dto.endTicks,
                )
            }
        }

    // ── Bytes / time ───────────────────────────────────────────────────────

    override suspend fun getTrickplayTileImage(itemId: String, width: Int, index: Int): ByteArray? =
        try {
            val server = requireConnectedServer()
            getBytes(
                url = apiUrl(server.address, "/Videos/$itemId/Trickplay/$width/$index.jpg"),
                accessToken = currentToken(),
            )
        } catch (_: Exception) {
            null
        }

    override suspend fun getItemImageBytes(itemId: String, imageType: String, maxWidth: Int): ByteArray? {
        if (imageType !in KNOWN_IMAGE_TYPES) return null
        return try {
            val server = requireConnectedServer()
            getBytes(
                url = apiUrl(
                    server.address,
                    "/Items/$itemId/Images/$imageType?maxWidth=$maxWidth",
                ),
                accessToken = currentToken(),
            )
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun getServerTime(): Result<UtcTimeResponse> = apiResultWithRetry {
        val server = requireConnectedServer()
        val response = getJson<UtcTimeDtoWire>(
            url = apiUrl(server.address, "/GetUtcTime"),
            accessToken = currentToken(),
        )
        UtcTimeResponse(
            requestReceptionTime = response.requestReceptionTime ?: "",
            responseTransmissionTime = response.responseTransmissionTime ?: "",
        )
    }
}
