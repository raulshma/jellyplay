package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.IntroTimestamps
import com.raulshma.jellyplay.core.model.PlaybackProgress
import com.raulshma.jellyplay.core.model.PlaybackStartInfo
import com.raulshma.jellyplay.core.model.RemoteSubtitleInfo
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackRepositoryImpl @Inject constructor(
    private val apiClient: JellyfinApiClient,
) : PlaybackRepository {

    override suspend fun reportPlaybackStart(info: PlaybackStartInfo): Result<Unit> =
        apiClient.reportPlaybackStart(info.itemId, info.sessionId, info.playMethod)

    override suspend fun reportPlaybackProgress(progress: PlaybackProgress): Result<Unit> =
        apiClient.reportPlaybackProgress(
            progress.itemId,
            progress.sessionId,
            progress.positionTicks,
            progress.isPaused,
            progress.playMethod,
        )

    override suspend fun reportPlaybackStopped(
        itemId: String,
        sessionId: String,
        positionTicks: Long,
    ): Result<Unit> = apiClient.reportPlaybackStopped(itemId, sessionId, positionTicks)

    override fun getImageUrl(itemId: String, imageType: String, maxWidth: Int): String =
        apiClient.getImageUrl(itemId, imageType, maxWidth)

    override fun getBackdropUrl(itemId: String, maxWidth: Int): String =
        apiClient.getBackdropImageUrl(itemId, maxWidth)

    override fun getStreamUrl(itemId: String, mediaSourceId: String, startTimeTicks: Long): String =
        apiClient.getStreamUrl(itemId, mediaSourceId, startTimeTicks)

    override fun getSubtitleDeliveryUrl(deliveryUrl: String): String =
        apiClient.getSubtitleDeliveryUrl(deliveryUrl)

    override suspend fun getIntroTimestamps(itemId: String): Result<IntroTimestamps> =
        apiClient.getIntroTimestamps(itemId)

    override suspend fun getRemoteSubtitles(itemId: String): Result<List<RemoteSubtitleInfo>> =
        apiClient.getRemoteSubtitles(itemId)

    override suspend fun downloadSubtitle(itemId: String, subtitleId: String): Result<Unit> =
        apiClient.downloadRemoteSubtitle(itemId, subtitleId)
}
