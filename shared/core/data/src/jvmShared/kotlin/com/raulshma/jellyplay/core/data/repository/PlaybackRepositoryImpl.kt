package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.session.HomeSession
import com.raulshma.jellyplay.core.data.session.SessionCacheRegistry
import com.raulshma.jellyplay.core.model.CultureInfo
import com.raulshma.jellyplay.core.model.FreshnessCeilings
import com.raulshma.jellyplay.core.model.LiveStreamOption
import com.raulshma.jellyplay.core.model.MediaSegment
import com.raulshma.jellyplay.core.model.PlaybackInfoResult
import com.raulshma.jellyplay.core.model.PlaybackMode
import com.raulshma.jellyplay.core.model.PlaybackProgress
import com.raulshma.jellyplay.core.model.PlaybackStartInfo
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.RemoteSubtitleInfo
import com.raulshma.jellyplay.core.model.ResolvedPlayback
import com.raulshma.jellyplay.core.model.TtlCache
import com.raulshma.jellyplay.core.network.api.AuthApiClient
import com.raulshma.jellyplay.core.network.api.LibraryApiClient
import com.raulshma.jellyplay.core.network.api.MetadataApiClient
import com.raulshma.jellyplay.core.network.api.PlaybackApiClient
import com.raulshma.jellyplay.core.network.playback.buildBookDownloadUrl
import com.raulshma.jellyplay.core.network.playback.resolveDeliveryUrl
import com.raulshma.jellyplay.core.network.playback.resolveDeliveryUrlWithApiKey
import com.raulshma.jellyplay.core.data.log.Log
import com.raulshma.jellyplay.core.data.concurrency.SingleFlightFetcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.atomic.AtomicLong

class PlaybackRepositoryImpl(
    /** Telemetry, URL builders, PlaybackInfo, segments, subtitle delivery, trickplay. */
    private val playbackApiClient: PlaybackApiClient,
    /** Image URLs + the played/favorite flips the outbox drain replays. */
    private val libraryApiClient: LibraryApiClient,
    /** Server URL + access token for the absolute-ize URL folds. */
    private val authApiClient: AuthApiClient,
    /** Remote-subtitle search/upload + the subtitle-cultures metadata read. */
    private val metadataApiClient: MetadataApiClient,
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
    /**
     * Playback-position writes (a delivered or staged STOP) mutate the same
     * served fields as a played/favorite flip (resume position, Continue
     * Watching, episode rows), so the stop path purges the repository's
     * user-data caches through the same seam those writes use.
     */
    private val mediaCacheInvalidation: MediaRepositoryCacheInvalidation,
    /**
     * Deferred cross-repository edge: the stop path announces confirmed
     * position writes on the user-data-change flow so open screens heal even
     * without a WS echo. Lazy keeps the construction graph acyclic.
     */
    private val mediaRepository: Lazy<MediaRepository>,
) : PlaybackRepository {

    private val segmentsCache = TtlCache<List<MediaSegment>>(
        maxSize = MAX_CACHE_ENTRIES,
        ttlMs = FreshnessCeilings.SEGMENTS_TTL_MS,
    )

    // Single-flight dedup for the segments read (the MediaRepositoryImpl
    // detail-cache pattern): a player surface that opens the same item from
    // two entry points near-simultaneously previously fired two full
    // getMediaSegments + intro/credit fallback batches, because TtlCache's
    // get-check-put is not atomic. The epoch is shared with
    // [invalidateSegmentsCache] so a per-item invalidation mid-flight also
    // vetoes the racing fetch's write-back.
    private val segmentsEpoch = AtomicLong(0L)
    private val segmentsFetcher = SingleFlightFetcher(segmentsCache, segmentsEpoch)

    init {
        sessionCacheRegistry.registerCaches("playback", segmentsCache)
        // Doctrine parity with MediaRepositoryImpl's DetailCacheGroup (see its
        // registryCaches KDoc): the registry's plain wholesale clear reclaims
        // the previous identity's segments entries, but only the epoch bump
        // expressed here can stop an in-flight previous-identity fetch from
        // writing its (now stale) result back into the just-cleared cache —
        // where it would sit pinned for the full TTL if the user switches
        // back to that identity.
        sessionCacheRegistry.registerAction("playback-identity-clear") {
            segmentsEpoch.incrementAndGet()
        }
    }

    override suspend fun reportPlaybackStart(info: PlaybackStartInfo): Result<Unit> = reportOrStage(
        stage = {
            outbox.enqueueStart(
                itemId = info.itemId,
                sessionId = info.sessionId,
                playMethod = info.playMethod,
                startPositionTicks = info.startPositionTicks,
            )
        },
        send = { playbackApiClient.reportPlaybackStart(info.itemId, info.sessionId, info.playMethod) },
    )

    override suspend fun reportPlaybackProgress(progress: PlaybackProgress): Result<Unit> = reportOrStage(
        stage = {
            outbox.enqueueProgress(
                itemId = progress.itemId,
                sessionId = progress.sessionId,
                positionTicks = progress.positionTicks,
                isPaused = progress.isPaused,
                playMethod = progress.playMethod,
                mediaSourceId = progress.mediaSourceId,
            )
        },
        send = {
            playbackApiClient.reportPlaybackProgress(
                progress.itemId,
                progress.sessionId,
                progress.positionTicks,
                progress.isPaused,
                progress.playMethod,
            )
        },
    )

    override suspend fun reportPlaybackStopped(
        itemId: String,
        sessionId: String,
        positionTicks: Long,
        failed: Boolean,
    ): Result<Unit> {
        // Pre-send purge pairs with the post-send one below — the same double
        // eviction the played/favorite wrapper runs around its write: a home
        // fetch in flight across the send must not repopulate the stale rows
        // after the post-send purge.
        mediaCacheInvalidation.invalidateForUserDataChange(itemId)
        val result = reportOrStage(
            stage = { outbox.enqueueStop(itemId, sessionId, positionTicks) },
            send = {
                playbackApiClient.reportPlaybackStopped(itemId, sessionId, positionTicks, failed).onSuccess {
                    // A delivered STOP supersedes any pending START/PROGRESS/STOP for
                    // this item — the server now has the authoritative final position.
                    // Scoped to telemetry only: a pending PLAYED/UNPLAYED flip is an
                    // orthogonal user intent and must still drain.
                    outbox.deletePlaybackTelemetryForItem(itemId)
                }
            },
        )
        // The item's resume position changed (or is pending in the outbox, in
        // which case the local mirror already reflects it): purge the caches
        // that serve it — the item's detail cluster and its series' episode
        // catalogue — so no surface shows the pre-playback position until the
        // TTL expires. Deliberately NOT the per-tick progress reports: those
        // change nothing queryable until the session ends, and purging on
        // every 10s tick would thrash the caches. The home sections cache is
        // also untouched here (scroll/flicker-sensitive; it heals through the
        // announcement below + the consumer's throttled forced refresh).
        mediaCacheInvalidation.invalidateForUserDataChange(itemId)
        // A delivered STOP is a confirmed user-data write (the resume point
        // moved): announce it on the same flow server WS pushes use, so home
        // heals even when the socket is down. Offline-staged stops announce
        // through the outbox drain instead (which already does).
        if (!offlineModeManager.isOffline && result.isSuccess) {
            mediaRepository.value.notifyUserDataChanged(listOf(itemId))
        }
        return result
    }

    /**
     * Stage-or-send shared by the three telemetry reports: offline, [stage]
     * enqueues the event so the PlaybackSyncWorker can replay it on reconnect
     * and success is reported back so the caller does not double-enqueue or
     * surface an error UI; online, [send] reports straight through and the
     * SAME [stage] payload is enqueued only on failure — the enqueue argument
     * list is declared exactly once, at the [stage] parameter.
     */
    private suspend fun reportOrStage(
        stage: suspend () -> Unit,
        send: suspend () -> Result<Unit>,
    ): Result<Unit> {
        if (offlineModeManager.isOffline) {
            stage()
            return Result.success(Unit)
        }
        val result = send()
        if (result.isFailure) stage()
        return result
    }

    override suspend fun reportBookProgress(
        itemId: String,
        positionTicks: Long,
        final: Boolean,
    ): Result<Unit> {
        // Same stage-or-send choreography as the session telemetry (see
        // docs/playback-progress-sync.md decision matrix): online sends
        // straight through, offline/failed enqueues a coalesced BOOK_PROGRESS
        // row. The result is swallowed on purpose — the reader is
        // fire-and-forget and the staged row guarantees eventual delivery.
        // Debounced page turns skip the cache purge exactly like the video
        // player's per-tick PROGRESS reports (purging per page would thrash);
        // the exit flush carries `final` and mirrors the STOP choreography —
        // pre-send purge, post-send purge, announcement — because the resume
        // position is a session-end fact then.
        if (!final) {
            reportOrStage(
                stage = { outbox.enqueueBookProgress(itemId, positionTicks) },
                send = { playbackApiClient.reportBookProgress(itemId, positionTicks) },
            )
            return Result.success(Unit)
        }
        mediaCacheInvalidation.invalidateForUserDataChange(itemId)
        reportOrStage(
            stage = { outbox.enqueueBookProgress(itemId, positionTicks) },
            send = { playbackApiClient.reportBookProgress(itemId, positionTicks) },
        )
        mediaCacheInvalidation.invalidateForUserDataChange(itemId)
        if (!offlineModeManager.isOffline) {
            mediaRepository.value.notifyUserDataChanged(listOf(itemId))
        }
        return Result.success(Unit)
    }

    override suspend fun replayOutboxEntry(entry: PlaybackOutboxEntry): Boolean =
        // Pure dispatch — no offline check, no enqueue. The worker owns the
        // drain loop (delete on success, retry/dead-letter on failure); this is
        // the single home for the entry-type → API-call mapping so capture and
        // drain can't drift apart.
        when (entry.eventType) {
            PlaybackOutboxEventType.START ->
                playbackApiClient.reportPlaybackStart(entry.itemId, entry.sessionId, entry.playMethod).isSuccess
            PlaybackOutboxEventType.PROGRESS ->
                playbackApiClient.reportPlaybackProgress(
                    entry.itemId,
                    entry.sessionId,
                    entry.positionTicks,
                    entry.isPaused,
                    entry.playMethod,
                ).isSuccess
            PlaybackOutboxEventType.STOP ->
                playbackApiClient.reportPlaybackStopped(entry.itemId, entry.sessionId, entry.positionTicks).isSuccess
            PlaybackOutboxEventType.BOOK_PROGRESS ->
                playbackApiClient.reportBookProgress(entry.itemId, entry.positionTicks).isSuccess
            PlaybackOutboxEventType.PLAYED ->
                libraryApiClient.markPlayed(entry.itemId).isSuccess
            PlaybackOutboxEventType.UNPLAYED ->
                libraryApiClient.markUnplayed(entry.itemId).isSuccess
            PlaybackOutboxEventType.FAVORITE ->
                libraryApiClient.setFavorite(entry.itemId, isFavorite = true).isSuccess
            PlaybackOutboxEventType.UNFAVORITE ->
                libraryApiClient.setFavorite(entry.itemId, isFavorite = false).isSuccess
        }

    override fun getImageUrl(itemId: String, imageType: String, maxWidth: Int?): String =
        libraryApiClient.getImageUrl(itemId, imageType, maxWidth)

    override fun getChapterImageUrl(itemId: String, imageIndex: Int, tag: String?, maxWidth: Int?): String =
        libraryApiClient.getImageUrl(itemId, imageType = "Chapter", maxWidth = maxWidth, imageIndex = imageIndex, tag = tag)

    override fun getBackdropUrl(itemId: String, maxWidth: Int): String =
        libraryApiClient.getBackdropImageUrl(itemId, maxWidth)

    override suspend fun getItemImageBytes(itemId: String, imageType: String, maxWidth: Int): ByteArray? =
        playbackApiClient.getItemImageBytes(itemId, imageType, maxWidth)

    override fun getStreamUrl(
        itemId: String,
        mediaSourceId: String,
        startTimeTicks: Long,
        liveStreamId: String?,
    ): String =
        playbackApiClient.getStreamUrl(itemId, mediaSourceId, startTimeTicks, liveStreamId = liveStreamId)

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
    ): Result<PlaybackInfoResult> = playbackApiClient.fetchPlaybackInfo(
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

        // Pure decision (see selectPlaybackMethod for the ladder and its
        // live-source reasoning): the facade keeps only the URL choreography
        // each decision arm points at.
        val selection = selectPlaybackMethod(source) ?: return null
        val url = when (selection.urlSource) {
            PlaybackUrlSource.LIVE_STREAM ->
                getStreamUrl(itemId, source.id, startTimeTicks, liveStreamId = source.liveStreamId)
            PlaybackUrlSource.STATIC_STREAM ->
                getStreamUrl(itemId, source.id, startTimeTicks)
            PlaybackUrlSource.TRANSCODE ->
                resolveTranscodeUrl(source.transcodeUrl)
        }
        if (url.isBlank()) return null

        return ResolvedPlayback(
            mediaSourceId = source.id,
            streamUrl = url,
            playMethod = selection.playMethod,
            playSessionId = result.playSessionId,
            maxStreamingBitrate = maxStreamingBitrateBits,
            container = source.container,
        )
    }

    private fun resolveTranscodeUrl(transcodeUrl: String?): String {
        if (transcodeUrl.isNullOrBlank()) return ""
        val server = authApiClient.getServerUrl() ?: return ""
        val token = authApiClient.getAccessToken()
        if (token.isNullOrBlank()) {
            // No session token: the fold's token-less half still owns the
            // absolute-ize (trailing-slash trim) — the hand-joined copy this
            // replaced produced `//path` for a trailing-slash server URL.
            return resolveDeliveryUrl(server, transcodeUrl)
        }
        // The pre-baked-token guard (either spelling — pre-12 servers bake
        // the legacy lowercase alias) lives in the shared delivery-URL fold.
        return resolveDeliveryUrlWithApiKey(server, transcodeUrl, token)
    }

    override fun getStreamUrl(
        itemId: String,
        mediaSourceId: String,
        startTimeTicks: Long,
        maxBitrate: Int?,
        useAudioEndpoint: Boolean,
        liveStreamId: String?,
    ): String = playbackApiClient.getStreamUrl(
        itemId = itemId,
        mediaSourceId = mediaSourceId,
        startTimeTicks = startTimeTicks,
        maxBitrate = maxBitrate,
        useAudioEndpoint = useAudioEndpoint,
        liveStreamId = liveStreamId,
    )

    override fun getSubtitleDeliveryUrl(deliveryUrl: String): String =
        playbackApiClient.getSubtitleDeliveryUrl(deliveryUrl)

    override fun getBookDownloadUrl(itemId: String): String {
        // Same pure-builder split as getStreamUrl: the shared
        // buildBookDownloadUrl does the string shaping and both platform
        // clients stay out of it. The session guard mirrors the URL builders'
        // null-base/-key → "" sentinel.
        val baseUrl = authApiClient.getServerUrl() ?: return ""
        val apiKey = authApiClient.getAccessToken() ?: return ""
        return buildBookDownloadUrl(baseUrl = baseUrl, apiKey = apiKey, itemId = itemId)
    }

    // The former getServerUrl()/getAccessToken() overrides are gone from the
    // surface: identity readers inject the PlaybackIdentity module (bound to
    // the same AuthApiClient this impl already holds) instead of this
    // repository. The internal uses above keep reading the client directly.

    override fun buildSubtitleDeliveryUrl(
        itemId: String,
        mediaSourceId: String,
        index: Int,
        codec: String?,
    ): String = playbackApiClient.buildSubtitleDeliveryUrl(itemId, mediaSourceId, index, codec)

    override suspend fun fetchActiveTranscodeReasons(itemId: String): List<String> =
        playbackApiClient.fetchActiveTranscodeReasons(itemId).getOrDefault(emptyList())

    override suspend fun getMediaSegments(itemId: String): Result<List<MediaSegment>> =
        segmentsFetcher.getOrFetchStorable({ homeSession.cacheIdentity() }, itemId) {
            val segmentsResult = playbackApiClient.getMediaSegments(itemId)
            val segments = segmentsResult.getOrDefault(emptyList())
            if (segments.isNotEmpty()) {
                return@getOrFetchStorable Result.success(segments) to true
            }

            // Distinguish "API succeeded and returned no segments" (cache the
            // fallback so repeated player opens don't re-hit the legacy endpoints)
            // from "API failed" (do not cache — a transient network error must not
            // be masked as "no segments" for the cache TTL, or the next call would
            // skip the retry and serve an empty list for 5 minutes).
            val cacheFallback = segmentsResult.isSuccess

            coroutineScope {
                val introDeferred = async { playbackApiClient.getIntroTimestamps(itemId).getOrNull() }
                val creditDeferred = async { playbackApiClient.getCreditTimestamps(itemId).getOrNull() }
                val introResult = introDeferred.await()
                val creditResult = creditDeferred.await()

                // Pure mapping (see legacySegmentFallback): the facade only
                // sequences the two legacy reads here.
                val fallbackSegments = legacySegmentFallback(introResult, creditResult)
                // Only cache on a successful (empty) segments call: the store
                // flag vetoes exactly this flight's write-back when the
                // segments API itself failed, leaving the cache untouched so
                // the next call retries the API instead of serving a stale
                // "empty" — and no concurrent item's flight is affected.
                Result.success(fallbackSegments) to cacheFallback
            }
        }

    override fun invalidateSegmentsCache(itemId: String) {
        // Snapshot read: this is a best-effort single-item eviction from a
        // non-suspend context; identity switches clear the cache wholesale
        // via SessionCacheRegistry regardless of which identity an entry was
        // keyed under. invalidate() also bumps the epoch, so an in-flight
        // segments fetch cannot re-pin the evicted entry.
        segmentsFetcher.invalidate(homeSession.cacheIdentitySnapshot(), itemId)
    }

    override suspend fun getRemoteSubtitles(itemId: String): Result<List<RemoteSubtitleInfo>> =
        playbackApiClient.getRemoteSubtitles(itemId)

    override suspend fun downloadSubtitle(itemId: String, subtitleId: String): Result<Unit> =
        playbackApiClient.downloadRemoteSubtitle(itemId, subtitleId)

    override suspend fun searchRemoteSubtitles(itemId: String, language: String): Result<List<RemoteSubtitleInfo>> =
        metadataApiClient.searchRemoteSubtitles(itemId, language)

    override suspend fun uploadSubtitle(
        itemId: String,
        data: String,
        fileName: String,
        language: String?,
        isForced: Boolean,
        isHearingImpaired: Boolean,
    ): Result<Unit> =
        metadataApiClient.uploadSubtitle(itemId, data, fileName, language, isForced, isHearingImpaired)

    override suspend fun getSubtitleCultures(itemId: String): Result<List<CultureInfo>> =
        metadataApiClient.getMetadataEditorInfo(itemId).map { it.cultures }

    override suspend fun getTrickplayTileImage(itemId: String, width: Int, index: Int): ByteArray? =
        playbackApiClient.getTrickplayTileImage(itemId, width, index)

    companion object {
        private const val TAG = "PlaybackRepository"
        private const val MAX_CACHE_ENTRIES = 50
        // The segments TTL used to be the private const SEGMENTS_CACHE_TTL_MS
        // (5 minutes) declared here; it now cites the named policy
        // FreshnessCeilings.SEGMENTS_TTL_MS at the construction site above.
    }
}
