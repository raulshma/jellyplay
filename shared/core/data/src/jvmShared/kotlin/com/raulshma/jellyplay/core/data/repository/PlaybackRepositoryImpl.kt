package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.session.HomeSession
import com.raulshma.jellyplay.core.data.session.SessionCacheRegistry
import com.raulshma.jellyplay.core.model.CreditTimestamps
import com.raulshma.jellyplay.core.model.CultureInfo
import com.raulshma.jellyplay.core.model.IntroTimestamps
import com.raulshma.jellyplay.core.model.LiveStreamOption
import com.raulshma.jellyplay.core.model.MediaSegment
import com.raulshma.jellyplay.core.model.MediaSegmentType
import com.raulshma.jellyplay.core.model.PlaybackInfoResult
import com.raulshma.jellyplay.core.model.PlayMethod
import com.raulshma.jellyplay.core.model.PlaybackMode
import com.raulshma.jellyplay.core.model.PlaybackProgress
import com.raulshma.jellyplay.core.model.PlaybackStartInfo
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.RemoteSubtitleInfo
import com.raulshma.jellyplay.core.model.ResolvedPlayback
import com.raulshma.jellyplay.core.model.TtlCache
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import com.raulshma.jellyplay.core.data.log.Log
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class PlaybackRepositoryImpl(
    private val apiClient: JellyfinApiClient,
    private val outbox: PlaybackOutboxRepository,
    private val offlineModeManager: OfflineModeManager,
    /**
     * Identity source for the segments cache's composite keys (see
     * [HomeSession.cacheIdentity]) — a user/server switch is a guaranteed
     * cache miss by construction instead of a stale cross-user hit.
     */
    private val homeSession: HomeSession,
    /** Registers the segments cache for wholesale clears on identity change. */
    private val sessionCacheRegistry: SessionCacheRegistry,
) : PlaybackRepository {

    private val segmentsCache = TtlCache<List<MediaSegment>>(
        maxSize = MAX_CACHE_ENTRIES,
        ttlMs = SEGMENTS_CACHE_TTL_MS,
    )

    init {
        sessionCacheRegistry.registerCaches("playback", segmentsCache)
    }

    override suspend fun reportPlaybackStart(info: PlaybackStartInfo): Result<Unit> {
        // Offline (or a transient HTTP failure): stage the event so the
        // PlaybackSyncWorker can replay it on reconnect. Report success back
        // to the caller so it does not double-enqueue or surface an error UI.
        if (offlineModeManager.isOffline) {
            outbox.enqueueStart(
                itemId = info.itemId,
                sessionId = info.sessionId,
                playMethod = info.playMethod,
                startPositionTicks = info.startPositionTicks,
            )
            return Result.success(Unit)
        }
        return apiClient.reportPlaybackStart(info.itemId, info.sessionId, info.playMethod)
            .onFailure {
                outbox.enqueueStart(
                    itemId = info.itemId,
                    sessionId = info.sessionId,
                    playMethod = info.playMethod,
                    startPositionTicks = info.startPositionTicks,
                )
            }
    }

    override suspend fun reportPlaybackProgress(progress: PlaybackProgress): Result<Unit> {
        if (offlineModeManager.isOffline) {
            outbox.enqueueProgress(
                itemId = progress.itemId,
                sessionId = progress.sessionId,
                positionTicks = progress.positionTicks,
                isPaused = progress.isPaused,
                playMethod = progress.playMethod,
                mediaSourceId = progress.mediaSourceId,
            )
            return Result.success(Unit)
        }
        return apiClient.reportPlaybackProgress(
            progress.itemId,
            progress.sessionId,
            progress.positionTicks,
            progress.isPaused,
            progress.playMethod,
        ).onFailure {
            outbox.enqueueProgress(
                itemId = progress.itemId,
                sessionId = progress.sessionId,
                positionTicks = progress.positionTicks,
                isPaused = progress.isPaused,
                playMethod = progress.playMethod,
                mediaSourceId = progress.mediaSourceId,
            )
        }
    }

    override suspend fun reportPlaybackStopped(
        itemId: String,
        sessionId: String,
        positionTicks: Long,
    ): Result<Unit> {
        if (offlineModeManager.isOffline) {
            outbox.enqueueStop(itemId, sessionId, positionTicks)
            return Result.success(Unit)
        }
        val result = apiClient.reportPlaybackStopped(itemId, sessionId, positionTicks)
        if (result.isSuccess) {
            // A delivered STOP supersedes any pending START/PROGRESS/STOP for
            // this item — the server now has the authoritative final position.
            // Scoped to telemetry only: a pending PLAYED/UNPLAYED flip is an
            // orthogonal user intent and must still drain.
            outbox.deletePlaybackTelemetryForItem(itemId)
        } else {
            outbox.enqueueStop(itemId, sessionId, positionTicks)
        }
        return result
    }

    override suspend fun replayOutboxEntry(entry: PlaybackOutboxEntry): Boolean =
        // Pure dispatch — no offline check, no enqueue. The worker owns the
        // drain loop (delete on success, retry/dead-letter on failure); this is
        // the single home for the entry-type → API-call mapping so capture and
        // drain can't drift apart.
        when (entry.eventType) {
            PlaybackOutboxEventType.START ->
                apiClient.reportPlaybackStart(entry.itemId, entry.sessionId, entry.playMethod).isSuccess
            PlaybackOutboxEventType.PROGRESS ->
                apiClient.reportPlaybackProgress(
                    entry.itemId,
                    entry.sessionId,
                    entry.positionTicks,
                    entry.isPaused,
                    entry.playMethod,
                ).isSuccess
            PlaybackOutboxEventType.STOP ->
                apiClient.reportPlaybackStopped(entry.itemId, entry.sessionId, entry.positionTicks).isSuccess
            PlaybackOutboxEventType.PLAYED ->
                apiClient.markPlayed(entry.itemId).isSuccess
            PlaybackOutboxEventType.UNPLAYED ->
                apiClient.markUnplayed(entry.itemId).isSuccess
            PlaybackOutboxEventType.FAVORITE ->
                apiClient.setFavorite(entry.itemId, isFavorite = true).isSuccess
            PlaybackOutboxEventType.UNFAVORITE ->
                apiClient.setFavorite(entry.itemId, isFavorite = false).isSuccess
        }

    override fun getImageUrl(itemId: String, imageType: String, maxWidth: Int?): String =
        apiClient.getImageUrl(itemId, imageType, maxWidth)

    override fun getChapterImageUrl(itemId: String, imageIndex: Int, tag: String?, maxWidth: Int?): String =
        apiClient.getImageUrl(itemId, imageType = "Chapter", maxWidth = maxWidth, imageIndex = imageIndex, tag = tag)

    override fun getBackdropUrl(itemId: String, maxWidth: Int): String =
        apiClient.getBackdropImageUrl(itemId, maxWidth)

    override suspend fun getItemImageBytes(itemId: String, imageType: String, maxWidth: Int): ByteArray? =
        apiClient.getItemImageBytes(itemId, imageType, maxWidth)

    override fun getStreamUrl(
        itemId: String,
        mediaSourceId: String,
        startTimeTicks: Long,
        liveStreamId: String?,
    ): String =
        apiClient.getStreamUrl(itemId, mediaSourceId, startTimeTicks, liveStreamId = liveStreamId)

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
    ): Result<PlaybackInfoResult> = apiClient.fetchPlaybackInfo(
        itemId = itemId,
        mediaSourceId = mediaSourceId,
        startTimeTicks = startTimeTicks,
        audioStreamIndex = audioStreamIndex,
        subtitleStreamIndex = subtitleStreamIndex,
        maxStreamingBitrateBits = maxStreamingBitrateBits,
        mode = mode,
        playerType = playerType,
        liveStreamOption = liveStreamOption,
    )

    override suspend fun resolvePlayback(
        itemId: String,
        mediaSourceId: String,
        startTimeTicks: Long,
        audioStreamIndex: Int?,
        subtitleStreamIndex: Int?,
        maxStreamingBitrateBits: Long?,
        mode: PlaybackMode,
        playerType: PlayerType,
        liveStreamOption: LiveStreamOption?,
    ): ResolvedPlayback? {
        val result = fetchPlaybackInfo(
            itemId = itemId,
            mediaSourceId = mediaSourceId,
            startTimeTicks = startTimeTicks,
            audioStreamIndex = audioStreamIndex,
            subtitleStreamIndex = subtitleStreamIndex,
            maxStreamingBitrateBits = maxStreamingBitrateBits,
            mode = mode,
            playerType = playerType,
            liveStreamOption = liveStreamOption,
        ).getOrNull() ?: return null

        val source = result.mediaSources.firstOrNull { it.id == mediaSourceId }
            ?: result.mediaSources.firstOrNull()
            ?: return null

        Log.i(
            TAG,
            "resolvePlayback: mode=$mode, liveOption=$liveStreamOption, " +
                "source=${source.id}, container=${source.container}, " +
                "directPlay=${source.supportsDirectPlay}, " +
                "directStream=${source.supportsDirectStream}, " +
                "transcode=${source.supportsTranscoding}, " +
                "transcodeUrl=${source.transcodeUrl != null}, " +
                "liveStreamId=${source.liveStreamId != null}"
        )

        // Live TV channels carry a server-issued liveStreamId; the stream URL
        // must echo it back as `LiveStreamId` so the tuner opens a live
        // session. Static direct-play (`/Videos/{id}/stream?static=true`) does
        // not work for live sources, so route them through direct stream.
        val isLiveStream = source.liveStreamId != null || source.requiresOpening

        val (url, method) = when {
            isLiveStream -> {
                // The SDK does not surface a DirectStreamUrl; the client
                // constructs `/Videos/{id}/stream` and appends LiveStreamId.
                // Transcode is the fallback when direct stream is unsupported.
                val liveId = source.liveStreamId
                val base = if (source.supportsDirectStream || source.supportsDirectPlay) {
                    getStreamUrl(itemId, source.id, startTimeTicks, liveStreamId = liveId)
                } else {
                    resolveTranscodeUrl(source.transcodeUrl)
                }
                val resolvedMethod = if (source.supportsDirectStream) PlayMethod.DIRECT_STREAM
                    else if (source.supportsTranscoding) PlayMethod.TRANSCODE
                    else PlayMethod.DIRECT_STREAM
                base to resolvedMethod
            }
            source.supportsDirectPlay ->
                getStreamUrl(itemId, source.id, startTimeTicks) to PlayMethod.DIRECT_PLAY
            source.supportsDirectStream ->
                resolveTranscodeUrl(source.transcodeUrl) to PlayMethod.DIRECT_STREAM
            source.supportsTranscoding ->
                resolveTranscodeUrl(source.transcodeUrl) to PlayMethod.TRANSCODE
            // No playable method offered by the server for this source/mode.
            else -> return null
        }
        if (url.isBlank()) return null

        return ResolvedPlayback(
            mediaSourceId = source.id,
            streamUrl = url,
            playMethod = method,
            playSessionId = result.playSessionId,
            maxStreamingBitrate = maxStreamingBitrateBits,
            container = source.container,
        )
    }

    private fun resolveTranscodeUrl(transcodeUrl: String?): String {
        if (transcodeUrl.isNullOrBlank()) return ""
        val server = apiClient.getServerUrl() ?: return ""
        val base = if (transcodeUrl.startsWith("http")) transcodeUrl else "$server$transcodeUrl"
        val token = apiClient.getAccessToken()
        if (token.isNullOrBlank()) return base
        // Avoid duplicating an api_key query param if the server already
        // embedded one in the transcoding URL.
        return if ("api_key=" in base) base else "$base${if ('?' in base) "&" else "?"}api_key=$token"
    }

    override fun getStreamUrl(
        itemId: String,
        mediaSourceId: String,
        startTimeTicks: Long,
        maxBitrate: Int?,
        useAudioEndpoint: Boolean,
        liveStreamId: String?,
    ): String = apiClient.getStreamUrl(
        itemId = itemId,
        mediaSourceId = mediaSourceId,
        startTimeTicks = startTimeTicks,
        maxBitrate = maxBitrate,
        useAudioEndpoint = useAudioEndpoint,
        liveStreamId = liveStreamId,
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

    override suspend fun fetchActiveTranscodeReasons(itemId: String): List<String> =
        apiClient.fetchActiveTranscodeReasons(itemId).getOrDefault(emptyList())

    override suspend fun getMediaSegments(itemId: String): Result<List<MediaSegment>> {
        val identity = homeSession.cacheIdentity()
        val cached = segmentsCache.get(identity, itemId)
        if (cached != null) {
            return Result.success(cached)
        }

        val segmentsResult = apiClient.getMediaSegments(itemId)
        val segments = segmentsResult.getOrDefault(emptyList())
        if (segments.isNotEmpty()) {
            segmentsCache.put(identity, itemId, segments)
            return Result.success(segments)
        }

        // Distinguish "API succeeded and returned no segments" (cache the
        // fallback so repeated player opens don't re-hit the legacy endpoints)
        // from "API failed" (do not cache — a transient network error must not
        // be masked as "no segments" for the cache TTL, or the next call would
        // skip the retry and serve an empty list for 5 minutes).
        val cacheFallback = segmentsResult.isSuccess

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
            // Only cache on a successful (empty) segments call. When the
            // segments API itself failed, leave the cache untouched so the
            // next call retries the API instead of serving a stale "empty".
            if (cacheFallback) {
                segmentsCache.put(identity, itemId, fallbackSegments)
            }
            Result.success(fallbackSegments)
        }
    }

    override fun invalidateSegmentsCache(itemId: String) {
        // Snapshot read: this is a best-effort single-item eviction from a
        // non-suspend context; identity switches clear the cache wholesale
        // via SessionCacheRegistry regardless of which identity an entry was
        // keyed under.
        segmentsCache.remove(homeSession.cacheIdentitySnapshot(), itemId)
    }

    override suspend fun getRemoteSubtitles(itemId: String): Result<List<RemoteSubtitleInfo>> =
        apiClient.getRemoteSubtitles(itemId)

    override suspend fun downloadSubtitle(itemId: String, subtitleId: String): Result<Unit> =
        apiClient.downloadRemoteSubtitle(itemId, subtitleId)

    override suspend fun searchRemoteSubtitles(itemId: String, language: String): Result<List<RemoteSubtitleInfo>> =
        apiClient.searchRemoteSubtitles(itemId, language)

    override suspend fun uploadSubtitle(
        itemId: String,
        data: String,
        fileName: String,
        language: String?,
        isForced: Boolean,
        isHearingImpaired: Boolean,
    ): Result<Unit> =
        apiClient.uploadSubtitle(itemId, data, fileName, language, isForced, isHearingImpaired)

    override suspend fun getSubtitleCultures(itemId: String): Result<List<CultureInfo>> =
        apiClient.getMetadataEditorInfo(itemId).map { it.cultures }

    override suspend fun getTrickplayTileImage(itemId: String, width: Int, index: Int): ByteArray? =
        apiClient.getTrickplayTileImage(itemId, width, index)

    companion object {
        private const val TAG = "PlaybackRepository"
        private const val MAX_CACHE_ENTRIES = 50
        private const val SEGMENTS_CACHE_TTL_MS = 5 * 60 * 1000L
    }
}
