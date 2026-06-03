package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.CreditTimestamps
import com.raulshma.jellyplay.core.model.IntroTimestamps
import com.raulshma.jellyplay.core.model.MediaSegment
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

    fun getStreamUrl(itemId: String, mediaSourceId: String, startTimeTicks: Long = 0): String
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
}
