package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.CreditTimestamps
import com.raulshma.jellyplay.core.model.IntroTimestamps
import com.raulshma.jellyplay.core.model.MediaSegment
import com.raulshma.jellyplay.core.model.PlaybackProgress
import com.raulshma.jellyplay.core.model.PlaybackStartInfo
import com.raulshma.jellyplay.core.model.RemoteSubtitleInfo

interface PlaybackRepository {

    suspend fun reportPlaybackStart(info: PlaybackStartInfo): Result<Unit>

    suspend fun reportPlaybackProgress(progress: PlaybackProgress): Result<Unit>

    suspend fun reportPlaybackStopped(itemId: String, sessionId: String, positionTicks: Long): Result<Unit>

    fun getImageUrl(itemId: String, imageType: String = "Primary", maxWidth: Int = 400): String

    fun getBackdropUrl(itemId: String, maxWidth: Int = 1280): String

    fun getStreamUrl(itemId: String, mediaSourceId: String, startTimeTicks: Long = 0): String

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
