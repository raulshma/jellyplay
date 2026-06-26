package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.CreditTimestamps
import com.raulshma.jellyplay.core.model.IntroTimestamps
import com.raulshma.jellyplay.core.model.MediaSegment
import com.raulshma.jellyplay.core.model.PlaybackInfoResult
import com.raulshma.jellyplay.core.model.PlaybackMode
import com.raulshma.jellyplay.core.model.PlaybackProgress
import com.raulshma.jellyplay.core.model.PlaybackStartInfo
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.RemoteSubtitleInfo
import com.raulshma.jellyplay.core.model.ResolvedPlayback

interface PlaybackRepository {

    suspend fun reportPlaybackStart(info: PlaybackStartInfo): Result<Unit>

    suspend fun reportPlaybackProgress(progress: PlaybackProgress): Result<Unit>

    suspend fun reportPlaybackStopped(itemId: String, sessionId: String, positionTicks: Long): Result<Unit>

    fun getImageUrl(itemId: String, imageType: String = "Primary", maxWidth: Int? = 400): String

    fun getBackdropUrl(itemId: String, maxWidth: Int = 1280): String

    fun getStreamUrl(itemId: String, mediaSourceId: String, startTimeTicks: Long = 0): String

    /**
     * Queries the server `PlaybackInfo` endpoint. See
     * [PlaybackApiClient.fetchPlaybackInfo] for parameter semantics.
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
    ): Result<PlaybackInfoResult>

    /**
     * Resolves a playable [ResolvedPlayback] (URL + PlayMethod + server
     * playSessionId) for [itemId] by consulting the `PlaybackInfo` endpoint
     * under [mode] and then choosing Direct Play / Direct Stream / Transcode
     * per the server's playability decision. Returns `null` when the server
     * offers no playable method for the source.
     */
    suspend fun resolvePlayback(
        itemId: String,
        mediaSourceId: String,
        startTimeTicks: Long,
        audioStreamIndex: Int?,
        subtitleStreamIndex: Int?,
        maxStreamingBitrateBits: Long?,
        mode: PlaybackMode,
        playerType: PlayerType,
    ): ResolvedPlayback?

    fun getStreamUrl(
        itemId: String,
        mediaSourceId: String,
        startTimeTicks: Long = 0,
        maxBitrate: Int? = null,
        useAudioEndpoint: Boolean = false,
    ): String

    fun getSubtitleDeliveryUrl(deliveryUrl: String): String

    fun getServerUrl(): String?

    fun getAccessToken(): String?

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

    suspend fun downloadSubtitle(itemId: String, subtitleId: String): Result<Unit>

    suspend fun getTrickplayTileImage(itemId: String, width: Int, index: Int): ByteArray?
}
