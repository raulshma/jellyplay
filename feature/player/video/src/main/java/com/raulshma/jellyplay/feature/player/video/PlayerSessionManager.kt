package com.raulshma.jellyplay.feature.player.video

import android.content.Context
import android.net.Uri
import com.raulshma.jellyplay.core.data.playback.PlayerLifecycleManager
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerAggregate
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerAggregateStore
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaSource
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.PlayMethod
import com.raulshma.jellyplay.core.model.isSideLoadableEmbeddedSubtitle
import com.raulshma.jellyplay.core.model.toMediaItem
import com.raulshma.jellyplay.core.model.PlaybackMode
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.ResolvedPlayback
import com.raulshma.jellyplay.core.model.StreamType
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.model.EngineSpecificConfig
import com.raulshma.jellyplay.feature.player.video.R
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import com.raulshma.jellyplay.feature.player.video.engine.PlaybackRequest
import com.raulshma.jellyplay.feature.player.video.engine.PlayerEngineFactory
import com.raulshma.jellyplay.feature.player.video.engine.SubtitleSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlayerSessionState(
    val currentItemId: String? = null,
    val mediaDetail: MediaDetail? = null,
    val currentMediaSource: MediaSource? = null,
    val mediaStreams: List<MediaStream> = emptyList(),
    val title: String = "",
    val subtitle: String = "",
    val playMethodString: String = "Direct Play",
    val playMethod: PlayMethod = PlayMethod.DIRECT_PLAY,
    val isDirectPlayForced: Boolean = false,
    /**
     * The server-issued play session id returned by the `PlaybackInfo`
     * endpoint. Used for progress reporting so the server can associate
     * reports with (possibly transcoded) streams. `null` for offline
     * playback or when the client falls back to a static direct URL.
     */
    val playSessionId: String? = null,
    val isReady: Boolean = false,
    val offlineTrickplayDir: java.io.File? = null,
    val streamUrl: String? = null,
    /**
     * True when the current item is playing from a local download (resolved
     * via [PlaybackSource.Offline]). The ViewModel uses this to branch
     * episode/season resolution toward the offline store so next-episode
     * discovery and autoplay work without a server round-trip.
     */
    val isOffline: Boolean = false,
)

class PlayerSessionManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
    private val downloadRepository: DownloadRepository,
    private val offlineRepository: OfflineRepository,
    private val aggregateStore: VideoPlayerAggregateStore,
    private val playerLifecycleManager: PlayerLifecycleManager,
    private val pipController: com.raulshma.jellyplay.core.data.playback.PipController,
    private val adaptiveBitrateManager: com.raulshma.jellyplay.core.data.playback.AdaptiveBitrateManager,
    private val playerEngineFactory: PlayerEngineFactory,
    private val playbackSourceResolver: com.raulshma.jellyplay.core.data.playback.PlaybackSourceResolver,
    private val streamingSubtitleStore: com.raulshma.jellyplay.core.data.repository.StreamingSubtitleStore,
) {
    private val _sessionState = MutableStateFlow(PlayerSessionState())
    val sessionState: StateFlow<PlayerSessionState> = _sessionState.asStateFlow()

    private val _engine = MutableStateFlow<MediaEngine?>(null)
    val engineFlow: StateFlow<MediaEngine?> = _engine.asStateFlow()
    val engine: MediaEngine? get() = _engine.value

    private var lastPlaybackRequest: PlaybackRequest? = null

    /**
     * The external (side-loaded) subtitle sources for the current session, or
     * null when no media is loaded. Exposed so features like the subtitle-sync
     * preview can resolve and parse the active track's bytes. The backing
     * [PlaybackRequest] is immutable — external subs are replaced wholesale
     * (see [addExternalSubtitle]).
     */
    val currentExternalSubtitles: List<SubtitleSource>?
        get() = lastPlaybackRequest?.externalSubtitles

    /**
     * The request headers (auth etc., e.g. Jellyfin `X-Emby-Token`) for the
     * current session, or null when no media is loaded. Used to authenticate
     * HTTP-fetched external subtitle bytes (see [SubtitlePreviewRepository]).
     */
    val currentPlaybackHeaders: Map<String, String>?
        get() = lastPlaybackRequest?.headers
    private var lastPlayerType: PlayerType? = null

    /**
     * Builds the secondary line shown beneath the episode title in the player
     * chrome. For a series episode this renders the show name followed by the
     * season/episode marker in `SXXEXX` form (e.g. "The Show · S01E05"). When
     * neither a series name nor season/episode data is available, falls back
     * to a trimmed overview — preserving the historical behaviour for movies
     * and other non-episodic items.
     */
    private fun buildEpisodeSubtitle(
        seriesName: String?,
        overview: String?,
        seasonNumber: Int?,
        episodeNumber: Int?,
    ): String = buildString {
        val hasSeries = !seriesName.isNullOrBlank()
        if (hasSeries) append(seriesName)
        if (seasonNumber != null && episodeNumber != null) {
            if (isNotEmpty()) append(" \u00B7 ")
            append("S${seasonNumber}E${episodeNumber}")
        }
        if (isEmpty()) append(overview?.take(60) ?: "")
    }

    fun bindReclaimedEngine(engine: MediaEngine, itemId: String, detail: MediaDetail) {
        _engine.value = engine
        playerLifecycleManager.activeCallbacks = engine
        pipController.requestAutoEnterPip(engine.capabilities.supportsPip)
        _sessionState.update { 
            it.copy(
                currentItemId = itemId,
                mediaDetail = detail,
                title = detail.item.name,
                subtitle = buildEpisodeSubtitle(
                    seriesName = detail.item.seriesName,
                    overview = detail.item.overview,
                    seasonNumber = detail.item.seasonNumber,
                    episodeNumber = detail.item.episodeNumber,
                ),
                isReady = true
            )
        }
    }

    /**
     * Legacy entry point — preserved for binary compatibility with existing
     * call-sites ([VideoPlayerViewModel.initializeInternal],
     * [VideoPlayerViewModel.loadCinemaIntro]). Delegates to
     * [loadMedia] with a [PlaybackSource.Auto].
     */
    suspend fun loadMedia(itemId: String, mediaSourceId: String?, startPositionTicks: Long) {
        loadMedia(PlaybackSource.Auto(itemId, mediaSourceId), startPositionTicks)
    }

    /**
     * Unified media loader. Dispatches to [loadOffline] or [loadOnline] based
     * on the resolved [PlaybackSource]. The [PlaybackSource.Auto] variant is
     * resolved via [PlaybackSource.Auto.resolve] using the downloads DB,
     * matching the historical auto-detection behaviour exactly.
     *
     * The completed-download predicate is owned by
     * [PlaybackSourceResolver.resolveUsableDownload] (the shared core routine
     * every call-site converges on); here it doubles as the Auto resolution
     * input and the metadata source for [loadOffline]. `Auto.resolve` then
     * maps a non-null download → Offline, null → Online.
     */
    suspend fun loadMedia(source: PlaybackSource, startPositionTicks: Long) {
        val itemId = source.itemId
        _sessionState.update { it.copy(currentItemId = itemId, isReady = false) }

        // The download lookup is needed both for Auto resolution and for
        // metadata enrichment during offline playback, so it is performed
        // unconditionally — identical to the pre-refactor behaviour. The
        // shared resolver owns the COMPLETED + file-exists predicate.
        val download = playbackSourceResolver.resolveUsableDownload(itemId)

        val resolved = when (source) {
            is PlaybackSource.Auto -> source.resolve(download)
            is PlaybackSource.Offline -> source
            is PlaybackSource.Online -> source
        }

        when (resolved) {
            is PlaybackSource.Offline -> loadOffline(
                itemId = itemId,
                download = download,
                downloadPath = resolved.downloadPath,
                startPositionTicks = startPositionTicks,
            )
            is PlaybackSource.Online -> loadOnline(
                itemId = itemId,
                mediaSourceId = resolved.mediaSourceId,
                startPositionTicks = startPositionTicks,
            )
            is PlaybackSource.Auto -> error("PlaybackSource.Auto must be resolved before dispatch")
        }
    }

    private suspend fun loadOffline(
        itemId: String,
        download: com.raulshma.jellyplay.core.model.DownloadItem?,
        downloadPath: String,
        startPositionTicks: Long,
    ) {
        val localFile = java.io.File(downloadPath).takeIf { it.exists() }
        if (localFile == null) {
            // The file vanished after resolution — surface an error rather
            // than silently falling back online (callers that want fallback
            // should use [PlaybackSource.Auto]).
            _sessionState.update { it.copy(title = context.getString(R.string.player_video_error_offline_file_missing), isReady = false) }
            return
        }

        val offlineItem = offlineRepository.getOfflineItem(itemId)
        val title = offlineItem?.name ?: download?.name ?: itemId
        val subtitle = buildEpisodeSubtitle(
            seriesName = offlineItem?.seriesName,
            overview = offlineItem?.overview,
            seasonNumber = offlineItem?.seasonNumber,
            episodeNumber = offlineItem?.episodeNumber,
        )
        val url = Uri.fromFile(localFile).toString()

        var runTimeTicks = offlineItem?.runTimeTicks
        if (runTimeTicks == null || runTimeTicks <= 0L) {
            val extractedDurationMs = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val retriever = android.media.MediaMetadataRetriever()
                    retriever.setDataSource(localFile.absolutePath)
                    val durationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                    retriever.release()
                    durationStr?.toLongOrNull()
                } catch (e: Exception) {
                    null
                }
            }
            if (extractedDurationMs != null && extractedDurationMs > 0) {
                runTimeTicks = extractedDurationMs * 10_000
            }
        }

        // Resolve the real container so ExoPlayer gets the right MIME type and
        // selects the correct extractor. Precedence:
        //   1. Container persisted at download time (new downloads).
        //   2. Magic-byte sniffing fallback (legacy downloads whose on-disk
        //      extension was hardcoded to .mp4 even when the bytes are MKV/TS/...).
        // Without this, MKV-in-.mp4 hangs ExoPlayer silently in STATE_BUFFERING
        // with no error dialog (only MPV plays, because libavformat sniffs content).
        val containerHint = download?.container
            ?: com.raulshma.jellyplay.feature.player.video.engine.ContainerSniffer.sniff(localFile)
        val mimeHint = containerHint?.let {
            com.raulshma.jellyplay.feature.player.video.engine.ContainerMimeMapper.mapToMime(it)
        }

        _sessionState.update {
            it.copy(
                title = title,
                subtitle = subtitle,
                playMethodString = "Offline",
                playMethod = PlayMethod.DIRECT_PLAY,
                streamUrl = url,
            )
        }

        // Await the hydrated aggregate (not the .value point read, which is the
        // empty default on cold start and would lose the saved per-item subtitle
        // delay). loadOnline/loadOffline are suspend, so .first() on the raw
        // flow blocks until the real DataStore values arrive.
        val agg = aggregateStore.aggregateRaw.first()
        val playerType = agg.playback.preferredPlayer

        // Preserve the offline item's rich metadata (seriesId, seasonId,
        // season/episode numbers, seriesName, …) via the canonical adapter so
        // downstream next-episode discovery, autoplay, and the "up next" overlay
        // work offline. Falls back to a minimal MediaItem when only the download
        // row (no offline_media metadata) is present. runTimeTicks is overridden
        // with the locally-extracted value so seek/duration math matches the
        // actual file, not whatever was persisted at download time.
        val detail = com.raulshma.jellyplay.core.model.MediaDetail(
            item = (offlineItem?.toMediaItem() ?: com.raulshma.jellyplay.core.model.MediaItem(
                id = itemId,
                name = title,
                mediaType = download?.mediaType ?: com.raulshma.jellyplay.core.model.MediaType.UNKNOWN,
            )).copy(
                runTimeTicks = runTimeTicks,
            ),
            mediaSources = emptyList(),
            chapters = emptyList(),
            // Carry the provider ids / external URLs persisted at download time
            // so SubtitleProviderIds can resolve a TMDB/IMDb id for Wyzie /
            // OpenSubtitles even when the Jellyfin server is unreachable. Empty
            // for legacy downloads (pre-v44) — the subtitle providers then fall
            // back to a title search.
            providerIds = offlineItem?.providerIds ?: emptyMap(),
            externalUrls = offlineItem?.externalUrls ?: emptyList(),
        )

        if (playerType == PlayerType.EXTERNAL) {
            _sessionState.update { it.copy(isReady = true, mediaDetail = detail, isOffline = true) }
            return
        }

        initializeEngine(playerType, detail, null, url, startPositionTicks, agg, mimeType = mimeHint)

        // Attach external subtitles bundled with the download (offline subs).
        loadOfflineSubtitles(downloadPath)

        // Re-attach provider-sourced subtitles (OpenSubtitles/Wyzie) persisted
        // for this item in the streaming-subtitle store. `SubtitleManager`
        // always writes provider downloads there regardless of online/offline
        // playback, so offline (downloaded) media must restore them too —
        // otherwise a subtitle downloaded once vanishes on reopen.
        loadStreamingSubtitles(itemId)

        val trickplayDir = com.raulshma.jellyplay.feature.player.video.trickplay.OfflineTrickplayHelper
            .getLocalTrickplayDir(downloadPath)

        _sessionState.update { it.copy(
            isReady = true,
            mediaDetail = detail,
            offlineTrickplayDir = trickplayDir,
            isOffline = true,
        ) }
    }

    private suspend fun loadOnline(
        itemId: String,
        mediaSourceId: String?,
        startPositionTicks: Long,
    ) {
        val detailResult = mediaRepository.getMediaDetail(itemId)
        val detail = detailResult.getOrElse {
            _sessionState.update { it.copy(title = context.getString(R.string.player_video_error_loading_media)) }
            return
        }

        val source = if (mediaSourceId != null) {
            detail.mediaSources.find { it.id == mediaSourceId }
        } else {
            detail.mediaSources.firstOrNull()
        }
        val streams = source?.mediaStreams ?: emptyList()

        // Await the hydrated aggregate (not the .value point read, which is the
        // empty default on cold start and would lose the saved per-item subtitle
        // delay). loadOnline/loadOffline are suspend, so .first() on the raw
        // flow blocks until the real DataStore values arrive.
        val agg = aggregateStore.aggregateRaw.first()
        val playerType = agg.playback.preferredPlayer
        val sourceId = source?.id ?: ""

        // Consult the PlaybackInfo endpoint so the server decides Direct
        // Play / Direct Stream / Transcode based on the device profile and
        // the user's PlaybackMode. Falls back to a static direct URL when
        // the server cannot resolve a playable method (preserving the
        // historical behaviour for non-conforming sources).
        val maxBitrate = adaptiveBitrateManager.resolveEffectiveMaxBitrate()
        val resolved = playbackRepository.resolvePlayback(
            itemId = itemId,
            mediaSourceId = sourceId,
            startTimeTicks = startPositionTicks,
            audioStreamIndex = null,
            subtitleStreamIndex = null,
            maxStreamingBitrateBits = maxBitrate,
            mode = agg.playback.playbackMode,
            playerType = playerType,
        )
        val url = resolved?.streamUrl
            ?: playbackRepository.getStreamUrl(itemId, sourceId, startPositionTicks, source?.liveStreamId)
        val playMethod = resolved?.playMethod ?: PlayMethod.DIRECT_PLAY

        _sessionState.update {
            it.copy(
                title = detail.item.name,
                subtitle = buildEpisodeSubtitle(
                    seriesName = detail.item.seriesName,
                    overview = detail.item.overview,
                    seasonNumber = detail.item.seasonNumber,
                    episodeNumber = detail.item.episodeNumber,
                ),
                mediaDetail = detail,
                currentMediaSource = source,
                mediaStreams = streams,
                playMethodString = playMethod.displayName(),
                playMethod = playMethod,
                isDirectPlayForced = agg.playback.playbackMode == PlaybackMode.FORCE_DIRECT_PLAY,
                playSessionId = resolved?.playSessionId,
                streamUrl = url,
            )
        }

        if (playerType == PlayerType.EXTERNAL) {
            _sessionState.update { it.copy(isReady = true) }
            return
        }

        initializeEngine(playerType, detail, source, url, startPositionTicks, agg, playMethod)

        // Re-attach subtitles previously saved from an external provider
        // (OpenSubtitles/Wyzie) for this streaming item. These persist across
        // replays on-device, so a subtitle downloaded once (even offline) is
        // available on the next playback without a server round-trip.
        loadStreamingSubtitles(itemId)

        _sessionState.update { it.copy(isReady = true) }
    }

    /**
     * Side-loads subtitles previously persisted by `SubtitleManager` into the
     * durable streaming-subtitle store. Mirrors [loadOfflineSubtitles] but for
     * streaming (non-downloaded) items — keyed by `itemId`, not a media-file
     * path. Files missing on disk are silently skipped.
     */
    private suspend fun loadStreamingSubtitles(itemId: String) {
        val saved = streamingSubtitleStore.loadAll(itemId)
        for (entry in saved) {
            val file = streamingSubtitleStore.fileFor(itemId, entry)
            if (!file.exists()) continue
            addExternalSubtitle(
                SubtitleSource(
                    url = Uri.fromFile(file).toString(),
                    label = entry.language ?: entry.fileName,
                    language = entry.language,
                    mimeType = null,
                    codec = entry.codec,
                    isDefault = false,
                    isForced = entry.isForced,
                    id = "streaming:${entry.provider}:${entry.providerSubtitleId}",
                ),
            )
        }
    }

    private fun initializeEngine(
        playerType: PlayerType,
        detail: MediaDetail,
        source: MediaSource?,
        url: String,
        startPositionTicks: Long,
        agg: VideoPlayerAggregate,
        playMethod: PlayMethod = PlayMethod.DIRECT_PLAY,
        mimeType: String? = null,
    ) {
        // Release the outgoing engine and null the reference before creating the
        // replacement, so a failure in create()/setup can never leave the field
        // pointing at an already-released engine.
        try {
            _engine.value?.release()
        } finally {
            _engine.value = null
        }
        val eng = playerEngineFactory.create(playerType)
        _engine.value = eng
        lastPlayerType = playerType

        playerLifecycleManager.activeCallbacks = eng
        pipController.requestAutoEnterPip(eng.capabilities.supportsPip)

        val config = EngineConfigBuilder.buildFromPreferences(
            agg = agg,
            mediaStreams = _sessionState.value.mediaStreams,
            itemId = detail.item.id,
            engineSpecific = resolveEngineConfig(playerType, agg),
        )
        eng.updateConfig(config)
        eng.setPlaybackSpeed(agg.videoPlayer.videoDefaultSpeed)

        val externalSubtitles = buildExternalSubtitles(detail, source, playMethod)

        val artworkUri = playbackRepository.getImageUrl(detail.item.id, maxWidth = 300)
        
        val headers = mutableMapOf<String, String>()
        val serverUrl = playbackRepository.getServerUrl()
        val token = playbackRepository.getAccessToken()
        if (!token.isNullOrBlank()) {
            headers["X-Emby-Token"] = token
        }

        val request = PlaybackRequest(
            uri = url,
            title = detail.item.name,
            startPositionMs = startPositionTicks / 10_000,
            artworkUri = artworkUri,
            externalSubtitles = externalSubtitles,
            headers = headers,
            preferredAudioLanguage = agg.subtitle.preferredAudioLanguage,
            preferredSubtitleLanguage = agg.subtitle.preferredSubtitleLanguage,
            maxVideoBitrate = if (agg.playback.playbackMode == PlaybackMode.AUTO)
                adaptiveBitrateManager.resolveEffectiveMaxBitrate()?.toInt()
                else null,
            serverUrl = serverUrl,
            authToken = token,
            minBufferMs = agg.videoPlayer.videoPreloadBufferSize.minBufferMs,
            maxBufferMs = agg.videoPlayer.videoPreloadBufferSize.maxBufferMs,
            normalizationGain = detail.item.normalizationGain,
            mimeType = mimeType,
            serverDurationMs = (detail.item.runTimeTicks ?: 0L) / 10_000,
        )

        lastPlaybackRequest = request
        eng.load(request)
    }

    private fun resolveEngineConfig(
        playerType: PlayerType,
        agg: VideoPlayerAggregate,
    ): EngineSpecificConfig? = when (playerType) {
        PlayerType.MPV -> agg.engine.mpvConfig
        PlayerType.LIBVLC -> agg.engine.libVlcConfig
        PlayerType.EXO_PLAYER -> agg.engine.exoPlayerConfig
        PlayerType.EXTERNAL -> null
    }

    /**
     * Re-resolves playback for the current item under [mode] and reloads the
     * engine with the resulting URL at [currentPositionMs]. Used when the
     * user toggles [PlaybackMode] or [com.raulshma.jellyplay.core.model.StreamingQuality]
     * mid-playback so a switch to/from transcode swaps the underlying stream
     * without restarting the item.
     *
     * Returns the resolved [ResolvedPlayback] (or `null` on failure) so the
     * caller can react — e.g. fall back to transcode when a forced direct
     * play request yields no playable method.
     */
    suspend fun reloadPlayback(
        mode: PlaybackMode,
        quality: com.raulshma.jellyplay.core.model.StreamingQuality,
        currentPositionMs: Long,
    ): ResolvedPlayback? {
        val itemId = _sessionState.value.currentItemId ?: return null
        val sourceId = _sessionState.value.currentMediaSource?.id ?: ""
        val agg = aggregateStore.aggregate.value
        val playerType = lastPlayerType ?: agg.playback.preferredPlayer
        val maxBitrate = adaptiveBitrateManager.resolveMaxBitrate(quality)

        val resolved = playbackRepository.resolvePlayback(
            itemId = itemId,
            mediaSourceId = sourceId,
            startTimeTicks = currentPositionMs * 10_000,
            audioStreamIndex = null,
            subtitleStreamIndex = null,
            maxStreamingBitrateBits = maxBitrate,
            mode = mode,
            playerType = playerType,
        )
        val url = resolved?.streamUrl
            ?: playbackRepository.getStreamUrl(itemId, sourceId, currentPositionMs * 10_000)
        val playMethod = resolved?.playMethod ?: PlayMethod.DIRECT_PLAY

        _sessionState.update {
            it.copy(
                playMethodString = playMethod.displayName(),
                playMethod = playMethod,
                isDirectPlayForced = mode == PlaybackMode.FORCE_DIRECT_PLAY,
                playSessionId = resolved?.playSessionId,
                streamUrl = url,
            )
        }

        // Swap the engine onto the freshly resolved URL. The engine-level
        // bitrate cap is only meaningful for AUTO (server-side cap drives
        // transcode; direct play is uncapped). Rebuild the side-loaded
        // subtitle set for the new play method so the subtitle picker stays
        // populated when switching to/from a transcode.
        val engineMaxBitrate = if (mode == PlaybackMode.AUTO) maxBitrate?.toInt() else null
        val state = _sessionState.value
        val rebuiltSubtitles = state.mediaDetail?.let { detail ->
            buildExternalSubtitles(detail, state.currentMediaSource, playMethod)
        } ?: emptyList()
        reloadWithEngine(
            playerType = playerType,
            currentPositionMs = currentPositionMs,
            playbackSpeed = agg.videoPlayer.videoDefaultSpeed,
            maxVideoBitrate = engineMaxBitrate,
            uriOverride = url,
            externalSubtitlesOverride = rebuiltSubtitles,
        )
        return resolved
    }

    /**
     * Reload playback for the current item at [currentPositionMs] with a new
     * [audioStreamIndex] and/or [subtitleStreamIndex]. Used when the user
     * selects a server-origin audio/subtitle track during transcoded playback
     * — mpv cannot switch audio in-place on an HLS manifest, and embedded subs
     * aren't delivered in the transcode, so the server must re-issue the
     * stream with the chosen index baked in (standard Jellyfin PlaybackInfo
     * re-POST).
     *
     * The current playback mode / quality / max-bitrate are preserved from the
     * existing session; only the stream indices change. Subtitles are rebuilt
     * via [buildExternalSubtitles] so the side-loaded set matches the new
     * play method.
     */
    suspend fun reloadForStreamChange(
        audioStreamIndex: Int?,
        subtitleStreamIndex: Int?,
        currentPositionMs: Long,
    ): ResolvedPlayback? {
        val itemId = _sessionState.value.currentItemId ?: return null
        val sourceId = _sessionState.value.currentMediaSource?.id ?: ""
        val agg = aggregateStore.aggregate.value
        val playerType = lastPlayerType ?: agg.playback.preferredPlayer
        val maxBitrate = adaptiveBitrateManager.resolveEffectiveMaxBitrate()
        val mode = agg.playback.playbackMode

        val resolved = playbackRepository.resolvePlayback(
            itemId = itemId,
            mediaSourceId = sourceId,
            startTimeTicks = currentPositionMs * 10_000,
            audioStreamIndex = audioStreamIndex,
            subtitleStreamIndex = subtitleStreamIndex,
            maxStreamingBitrateBits = maxBitrate,
            mode = mode,
            playerType = playerType,
        )
        val url = resolved?.streamUrl
            ?: playbackRepository.getStreamUrl(itemId, sourceId, currentPositionMs * 10_000)
        val playMethod = resolved?.playMethod ?: PlayMethod.DIRECT_PLAY

        _sessionState.update {
            it.copy(
                playMethodString = playMethod.displayName(),
                playMethod = playMethod,
                playSessionId = resolved?.playSessionId,
                streamUrl = url,
            )
        }

        val engineMaxBitrate = if (mode == PlaybackMode.AUTO) maxBitrate?.toInt() else null
        val state = _sessionState.value
        val rebuiltSubtitles = state.mediaDetail?.let { detail ->
            buildExternalSubtitles(detail, state.currentMediaSource, playMethod)
        } ?: emptyList()
        reloadWithEngine(
            playerType = playerType,
            currentPositionMs = currentPositionMs,
            playbackSpeed = agg.videoPlayer.videoDefaultSpeed,
            maxVideoBitrate = engineMaxBitrate,
            uriOverride = url,
            externalSubtitlesOverride = rebuiltSubtitles,
        )
        return resolved
    }

    suspend fun reloadWithEngine(
        playerType: PlayerType,
        currentPositionMs: Long,
        playbackSpeed: Float = 1.0f,
        maxVideoBitrate: Int? = null,
        uriOverride: String? = null,
        externalSubtitlesOverride: List<SubtitleSource>? = null,
    ) {
        val last = lastPlaybackRequest ?: return
        val agg = aggregateStore.aggregate.value

        try {
            _engine.value?.release()
        } finally {
            _engine.value = null
        }

        val eng = playerEngineFactory.create(playerType)
        _engine.value = eng
        lastPlayerType = playerType

        playerLifecycleManager.activeCallbacks = eng
        pipController.requestAutoEnterPip(eng.capabilities.supportsPip)

        val state = _sessionState.value
        val config = EngineConfigBuilder.buildFromPreferences(
            agg = agg,
            mediaStreams = state.mediaStreams,
            itemId = state.currentItemId,
            engineSpecific = resolveEngineConfig(playerType, agg),
        )
        eng.updateConfig(config)
        eng.setPlaybackSpeed(playbackSpeed)

        val request = last.copy(
            startPositionMs = currentPositionMs,
            maxVideoBitrate = maxVideoBitrate,
            uri = uriOverride ?: last.uri,
            externalSubtitles = externalSubtitlesOverride ?: last.externalSubtitles,
        )
        lastPlaybackRequest = request
        eng.load(request)
    }

    fun addExternalSubtitle(source: SubtitleSource) {
        val last = lastPlaybackRequest ?: return
        val updatedSubtitles = last.externalSubtitles + source
        lastPlaybackRequest = last.copy(externalSubtitles = updatedSubtitles)
        _engine.value?.addExternalSubtitle(source)
    }

    /**
     * Builds the side-loaded [SubtitleSource] list for the engine.
     *
     * Subtitles that already carry a server [MediaStream.deliveryUrl] (the
     * PlaybackInfo response populates this for externally-delivered subs,
     * including image subs when the PGS-direct-play profile opts in) use that
     * URL. Otherwise, only **text** subs ([isSideLoadableEmbeddedSubtitle]) are
     * considered for side-loading — the Jellyfin subtitle endpoint cannot serve
     * image formats (PGS/VOBSUB/DVB), so those are skipped (left to burn-in on
     * transcode, or container demux on direct play).
     *
     * For the text subs that survive the codec gate, side-loading is
     * method-dependent: external subs are always side-loaded; embedded text
     * subs are side-loaded only when NOT direct-playing (transcoded HLS does
     * not reliably expose them in-manifest). On DIRECT_PLAY every engine
     * (ExoPlayer, LibVLC, MPV) demuxes embedded text subs from the container
     * natively — confirmed for MPV via logcat, which lists the demuxed tracks
     * — so side-loading them too would duplicate each track and could render
     * the selected sub twice., which never
     * side-load embedded subs alongside container demuxing.
     */
    private fun buildExternalSubtitles(
        detail: MediaDetail,
        source: MediaSource?,
        playMethod: PlayMethod,
    ): List<SubtitleSource> {
        val streams = source?.mediaStreams ?: return emptyList()
        return streams.filter { it.type == StreamType.SUBTITLE }.mapNotNull { stream ->
            val subUrl = when {
                // Server-issued delivery URL (e.g. an external PGS sub the
                // server can serve verbatim when PGS direct play is opted in).
                !stream.deliveryUrl.isNullOrBlank() ->
                    playbackRepository.getSubtitleDeliveryUrl(stream.deliveryUrl!!)
                // The Jellyfin subtitle endpoint only serves text formats — it
                // cannot synthesize image subs (PGS/VOBSUB/DVB), so only
                // side-load text-side-loadable streams. Image subs are left to
                // the server's burn-in (transcode) or the player's container
                // demux (direct play on MPV).
                !isSideLoadableEmbeddedSubtitle(stream.codec) -> return@mapNotNull null
                // See the KDoc above for the DIRECT_PLAY rationale: embedded
                // text subs are side-loaded only when not direct-playing.
                stream.isExternal ||
                    playMethod != PlayMethod.DIRECT_PLAY ->
                    playbackRepository.buildSubtitleDeliveryUrl(
                        detail.item.id, source.id, stream.index, stream.codec,
                    )
                else -> return@mapNotNull null
            }
            if (subUrl.isBlank()) return@mapNotNull null

            SubtitleSource(
                url = subUrl,
                label = stream.displayTitle ?: stream.title ?: stream.language ?: "Unknown",
                language = stream.language,
                mimeType = null, // Mapped by the engine using codec or extension.
                codec = stream.codec,
                isDefault = stream.isDefault,
                isForced = stream.isForced,
                id = "external:${stream.index}",
            )
        }
    }

    private suspend fun loadOfflineSubtitles(downloadPath: String) {
        val manifest = downloadRepository.loadLocalSubtitleManifest(downloadPath) ?: return
        if (manifest.subtitles.isEmpty()) return
        val parentDir = java.io.File(downloadPath).parentFile ?: return
        val subtitlesDir = java.io.File(parentDir, "subtitles")
        for (entry in manifest.subtitles) {
            val file = java.io.File(subtitlesDir, entry.fileName)
            if (!file.exists()) continue
            addExternalSubtitle(
                SubtitleSource(
                    url = Uri.fromFile(file).toString(),
                    label = entry.displayTitle ?: entry.title ?: entry.language ?: "Subtitle ${entry.index}",
                    language = entry.language,
                    mimeType = null,
                    codec = entry.codec,
                    isDefault = entry.isDefault,
                    isForced = entry.isForced,
                    id = "offline:${entry.index}",
                )
            )
        }
    }

    fun release() {
        _engine.value?.release()
        _engine.value = null
        lastPlaybackRequest = null
        lastPlayerType = null
        _sessionState.update { PlayerSessionState() }
    }

    fun detachEngine() {
        _engine.value = null
    }
}

