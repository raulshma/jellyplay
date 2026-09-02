package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.CreditTimestamps
import com.raulshma.jellyplay.core.model.IntroTimestamps
import com.raulshma.jellyplay.core.model.LiveStreamOption
import com.raulshma.jellyplay.core.model.MediaSegment
import com.raulshma.jellyplay.core.model.PlaybackInfoResult
import com.raulshma.jellyplay.core.model.PlaybackMode
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.RemoteSubtitleInfo

interface PlaybackApiClient {
    suspend fun reportPlaybackStart(
        itemId: String,
        sessionId: String,
        playMethod: com.raulshma.jellyplay.core.model.PlayMethod = com.raulshma.jellyplay.core.model.PlayMethod.DIRECT_PLAY,
    ): Result<Unit>

    suspend fun reportPlaybackProgress(
        itemId: String,
        sessionId: String,
        positionTicks: Long,
        isPaused: Boolean,
        playMethod: com.raulshma.jellyplay.core.model.PlayMethod = com.raulshma.jellyplay.core.model.PlayMethod.DIRECT_PLAY,
    ): Result<Unit>

    suspend fun reportPlaybackStopped(
        itemId: String,
        sessionId: String,
        positionTicks: Long,
    ): Result<Unit>

    fun getStreamUrl(
        itemId: String,
        mediaSourceId: String,
        startTimeTicks: Long = 0,
        liveStreamId: String? = null,
    ): String

    /**
     * Queries the Jellyfin `PlaybackInfo` endpoint to obtain the server's
     * playability decision for [itemId] under the requested [mode].
     *
     * The returned [PlaybackInfoResult] carries a refreshed
     * [com.raulshma.jellyplay.core.model.MediaSource] (with the server-decided
     * Direct Play / Direct Stream / Transcode support and a ready-to-use
     * `transcodeUrl`) plus the server-issued `playSessionId`.
     *
     * @param mode drives the `enableDirectPlay` / `enableDirectStream` /
     *   `enableTranscoding` / `allowVideoStreamCopy` flags on the request.
     *   `AUTO` enables everything; `FORCE_DIRECT_PLAY` disables stream copy
     *   and transcoding; `FORCE_TRANSCODE` disables direct play and direct
     *   stream. Ignored when [liveStreamOption] is non-null.
     * @param liveStreamOption when non-null, overrides [mode] for live TV
     *   items using a live-specific flag table (see [LiveStreamOption]).
     *   `AUTO` enables all methods; `DIRECT_STREAM` disables direct play and
     *   transcoding so the open tuner is read verbatim; `TRANSCODE` disables
     *   direct play and direct stream.
     * @param maxStreamingBitrateBits optional bitrate ceiling (bits/s) derived
     *   from the streaming-quality picker. Sent for `AUTO` and
     *   `FORCE_TRANSCODE` so a forced transcode still targets the chosen
     *   resolution; omitted for `FORCE_DIRECT_PLAY`.
     */
    suspend fun fetchPlaybackInfo(
        itemId: String,
        mediaSourceId: String,
        startTimeTicks: Long,
        audioStreamIndex: Int?,
        subtitleStreamIndex: Int?,
        maxStreamingBitrateBits: Long?,
        mode: PlaybackMode,
        playerType: PlayerType,
        liveStreamOption: LiveStreamOption? = null,
    ): Result<PlaybackInfoResult>

    /**
     * Build a stream URL with an optional max bitrate cap (in bits per
     * second). When [maxBitrate] is null, the server picks the appropriate
     * bitrate. The URL uses the audio-friendly `/Audio/{id}/universal` form
     * when [useAudioEndpoint] is true so the server returns an audio
     * transcoded stream that is friendly to mobile/cellular connections.
     */
    fun getStreamUrl(
        itemId: String,
        mediaSourceId: String,
        startTimeTicks: Long = 0,
        maxBitrate: Int? = null,
        useAudioEndpoint: Boolean = false,
        liveStreamId: String? = null,
    ): String
    fun getSubtitleDeliveryUrl(deliveryUrl: String): String

    fun buildSubtitleDeliveryUrl(
        itemId: String,
        mediaSourceId: String,
        index: Int,
        codec: String?,
    ): String

    suspend fun getIntroTimestamps(itemId: String): Result<IntroTimestamps>
    suspend fun getCreditTimestamps(itemId: String): Result<CreditTimestamps>
    suspend fun getMediaSegments(itemId: String): Result<List<MediaSegment>>
    suspend fun getRemoteSubtitles(itemId: String): Result<List<RemoteSubtitleInfo>>
    suspend fun downloadRemoteSubtitle(itemId: String, subtitleId: String): Result<Unit>
    suspend fun getTrickplayTileImage(itemId: String, width: Int, index: Int): ByteArray?
    suspend fun getServerTime(): Result<com.raulshma.jellyplay.core.model.UtcTimeResponse>
    /**
     * Fetches an item's image bytes via the authenticated Jellyfin SDK API so
     * it can be persisted locally for offline viewing. Returns null if the
     * item has no such image or the request fails.
     */
    suspend fun getItemImageBytes(itemId: String, imageType: String, maxWidth: Int): ByteArray?

    /**
     * Reads the server's live-session `TranscodingInfo.TranscodeReasons` for
     * [itemId] as played by THIS device. The SDK's `PlaybackInfo` response
     * does not expose reason tokens (jellyfin-model 1.8.12 omits
     * `MediaSourceInfo.TranscodeReasons`), so the running session is the
     * authoritative source — call it a couple of seconds after a transcode
     * resolution so the session has registered. Returns an empty list when
     * the item is not being transcoded (or on any failure — reasons are
     * diagnostics, never worth an error path).
     */
    suspend fun fetchActiveTranscodeReasons(itemId: String): Result<List<String>>
}
