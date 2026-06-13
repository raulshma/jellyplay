package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.data.cache.TtlCache
import com.raulshma.jellyplay.core.model.CreditTimestamps
import com.raulshma.jellyplay.core.model.IntroTimestamps
import com.raulshma.jellyplay.core.model.MediaSegment
import com.raulshma.jellyplay.core.model.MediaSegmentType
import com.raulshma.jellyplay.core.model.PlaybackProgress
import com.raulshma.jellyplay.core.model.PlaybackStartInfo
import com.raulshma.jellyplay.core.model.RemoteSubtitleInfo
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackRepositoryImpl @Inject constructor(
    private val apiClient: JellyfinApiClient,
) : PlaybackRepository {

    private val segmentsCache = TtlCache<List<MediaSegment>>(
        maxSize = MAX_CACHE_ENTRIES,
        ttlMs = SEGMENTS_CACHE_TTL_MS,
    )

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

    override fun getImageUrl(itemId: String, imageType: String, maxWidth: Int?): String =
        apiClient.getImageUrl(itemId, imageType, maxWidth)

    override fun getBackdropUrl(itemId: String, maxWidth: Int): String =
        apiClient.getBackdropImageUrl(itemId, maxWidth)

    override fun getStreamUrl(itemId: String, mediaSourceId: String, startTimeTicks: Long): String =
        apiClient.getStreamUrl(itemId, mediaSourceId, startTimeTicks)

    override fun getStreamUrl(
        itemId: String,
        mediaSourceId: String,
        startTimeTicks: Long,
        maxBitrate: Int?,
        useAudioEndpoint: Boolean,
    ): String = apiClient.getStreamUrl(
        itemId = itemId,
        mediaSourceId = mediaSourceId,
        startTimeTicks = startTimeTicks,
        maxBitrate = maxBitrate,
        useAudioEndpoint = useAudioEndpoint,
    )

    override fun getSubtitleDeliveryUrl(deliveryUrl: String): String =
        apiClient.getSubtitleDeliveryUrl(deliveryUrl)

    override fun getServerUrl(): String? = apiClient.getServerUrl()

    override fun getAccessToken(): String? = apiClient.getAccessToken()

    override fun buildSubtitleDeliveryUrl(
        itemId: String,
        mediaSourceId: String,
        index: Int,
        codec: String?,
    ): String = apiClient.buildSubtitleDeliveryUrl(itemId, mediaSourceId, index, codec)

    override suspend fun getIntroTimestamps(itemId: String): Result<IntroTimestamps> =
        apiClient.getIntroTimestamps(itemId)

    override suspend fun getCreditTimestamps(itemId: String): Result<CreditTimestamps> =
        apiClient.getCreditTimestamps(itemId)

    override suspend fun getMediaSegments(itemId: String): Result<List<MediaSegment>> {
        val cached = segmentsCache.get(itemId)
        if (cached != null) {
            return Result.success(cached)
        }

        val segmentsResult = apiClient.getMediaSegments(itemId)
        val segments = segmentsResult.getOrDefault(emptyList())
        if (segments.isNotEmpty()) {
            segmentsCache.put(itemId, segments)
            return Result.success(segments)
        }

        return coroutineScope {
            val introDeferred = async { apiClient.getIntroTimestamps(itemId).getOrNull() }
            val creditDeferred = async { apiClient.getCreditTimestamps(itemId).getOrNull() }
            val introResult = introDeferred.await()
            val creditResult = creditDeferred.await()

            val fallbackSegments = mutableListOf<MediaSegment>()
            introResult?.let { ts ->
                if (ts.hasIntro) {
                    fallbackSegments.add(
                        MediaSegment(
                            id = "legacy-intro-${ts.itemId}",
                            itemId = ts.itemId,
                            type = MediaSegmentType.INTRO,
                            startTicks = ts.introStartTicks,
                            endTicks = ts.introEndTicks,
                        )
                    )
                }
            }
            creditResult?.let { ts ->
                if (ts.hasCredits) {
                    fallbackSegments.add(
                        MediaSegment(
                            id = "legacy-outro-${ts.itemId}",
                            itemId = ts.itemId,
                            type = MediaSegmentType.OUTRO,
                            startTicks = ts.creditStartTicks,
                            endTicks = ts.creditEndTicks,
                        )
                    )
                }
            }
            val result = Result.success(fallbackSegments)
            segmentsCache.put(itemId, fallbackSegments)
            result
        }
    }

    override suspend fun getRemoteSubtitles(itemId: String): Result<List<RemoteSubtitleInfo>> =
        apiClient.getRemoteSubtitles(itemId)

    override suspend fun downloadSubtitle(itemId: String, subtitleId: String): Result<Unit> =
        apiClient.downloadRemoteSubtitle(itemId, subtitleId)

    override suspend fun getTrickplayTileImage(itemId: String, width: Int, index: Int): ByteArray? =
        apiClient.getTrickplayTileImage(itemId, width, index)

    companion object {
        private const val MAX_CACHE_ENTRIES = 50
        private const val SEGMENTS_CACHE_TTL_MS = 5 * 60 * 1000L
    }
}
