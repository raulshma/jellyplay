package com.raulshma.jellyplay.feature.player.video

import android.content.Context
import android.net.Uri
import com.raulshma.jellyplay.core.data.playback.PlayerLifecycleManager
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaSource
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.PlayMethod
import com.raulshma.jellyplay.core.model.PlaybackMode
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.ResolvedPlayback
import com.raulshma.jellyplay.core.model.StreamType
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.model.EngineSpecificConfig
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
)

class PlayerSessionManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
    private val downloadRepository: DownloadRepository,
    private val offlineRepository: OfflineRepository,
    private val preferencesStore: UserPreferencesStore,
    private val playerLifecycleManager: PlayerLifecycleManager,
    private val adaptiveBitrateManager: com.raulshma.jellyplay.core.data.playback.AdaptiveBitrateManager,
    private val playerEngineFactory: PlayerEngineFactory,
) {
    private val _sessionState = MutableStateFlow(PlayerSessionState())
    val sessionState: StateFlow<PlayerSessionState> = _sessionState.asStateFlow()

    private val _engine = MutableStateFlow<MediaEngine?>(null)
    val engineFlow: StateFlow<MediaEngine?> = _engine.asStateFlow()
    val engine: MediaEngine? get() = _engine.value

    private var lastPlaybackRequest: PlaybackRequest? = null
    private var lastPlayerType: PlayerType? = null
    
    fun bindReclaimedEngine(engine: MediaEngine, itemId: String, detail: MediaDetail) {
        _engine.value = engine
        playerLifecycleManager.activeCallbacks = engine
        playerLifecycleManager.requestAutoEnterPip(engine.capabilities.supportsPip)
        
        _sessionState.update { 
            it.copy(
                currentItemId = itemId,
                mediaDetail = detail,
                title = detail.item.name,
                subtitle = detail.item.seriesName ?: (detail.item.overview?.take(60) ?: ""),
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
     */
    suspend fun loadMedia(source: PlaybackSource, startPositionTicks: Long) {
        val itemId = source.itemId
        _sessionState.update { it.copy(currentItemId = itemId, isReady = false) }

        // The download lookup is needed both for Auto resolution and for
        // metadata enrichment during offline playback, so it is performed
        // unconditionally — identical to the pre-refactor behaviour.
        val download = downloadRepository.getDownloadByMediaItemId(itemId)

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
            _sessionState.update { it.copy(title = "Error: offline file missing", isReady = false) }
            return
        }

        val offlineItem = offlineRepository.getOfflineItem(itemId)
        val title = offlineItem?.name ?: download?.name ?: itemId
        val subtitle = offlineItem?.seriesName ?: offlineItem?.overview?.take(60) ?: ""
        val url = Uri.fromFile(localFile).toString()

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

        val prefs = preferencesStore.preferences.first()
        val playerType = prefs.preferredPlayer

        if (playerType == PlayerType.EXTERNAL) {
            _sessionState.update { it.copy(isReady = true) }
            return
        }

        val detail = com.raulshma.jellyplay.core.model.MediaDetail(
            item = com.raulshma.jellyplay.core.model.MediaItem(
                id = itemId,
                name = title,
                mediaType = download?.mediaType ?: com.raulshma.jellyplay.core.model.MediaType.UNKNOWN,
                overview = offlineItem?.overview,
                seriesName = offlineItem?.seriesName,
                runTimeTicks = offlineItem?.runTimeTicks,
            ),
            mediaSources = emptyList(),
            chapters = emptyList(),
        )

        initializeEngine(playerType, detail, null, url, startPositionTicks, prefs, mimeType = mimeHint)

        // Attach external subtitles bundled with the download (offline subs).
        loadOfflineSubtitles(downloadPath)

        val trickplayDir = com.raulshma.jellyplay.feature.player.video.trickplay.OfflineTrickplayHelper
            .getLocalTrickplayDir(downloadPath)

        _sessionState.update { it.copy(
            isReady = true,
            mediaDetail = detail,
            offlineTrickplayDir = trickplayDir,
        ) }
    }

    private suspend fun loadOnline(
        itemId: String,
        mediaSourceId: String?,
        startPositionTicks: Long,
    ) {
        val detailResult = mediaRepository.getMediaDetail(itemId)
        val detail = detailResult.getOrElse {
            _sessionState.update { it.copy(title = "Error loading media") }
            return
        }

        val source = if (mediaSourceId != null) {
            detail.mediaSources.find { it.id == mediaSourceId }
        } else {
            detail.mediaSources.firstOrNull()
        }
        val streams = source?.mediaStreams ?: emptyList()

        val prefs = preferencesStore.preferences.first()
        val playerType = prefs.preferredPlayer
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
            mode = prefs.playbackMode,
            playerType = playerType,
        )
        val url = resolved?.streamUrl
            ?: playbackRepository.getStreamUrl(itemId, sourceId, startPositionTicks)
        val playMethod = resolved?.playMethod ?: PlayMethod.DIRECT_PLAY

        _sessionState.update {
            it.copy(
                title = detail.item.name,
                subtitle = detail.item.seriesName ?: (detail.item.overview?.take(60) ?: ""),
                mediaDetail = detail,
                currentMediaSource = source,
                mediaStreams = streams,
                playMethodString = playMethod.displayName(),
                playMethod = playMethod,
                isDirectPlayForced = prefs.playbackMode == PlaybackMode.FORCE_DIRECT_PLAY,
                playSessionId = resolved?.playSessionId,
                streamUrl = url,
            )
        }

        if (playerType == PlayerType.EXTERNAL) {
            _sessionState.update { it.copy(isReady = true) }
            return
        }

        initializeEngine(playerType, detail, source, url, startPositionTicks, prefs, playMethod)
        _sessionState.update { it.copy(isReady = true) }
    }

    private fun initializeEngine(
        playerType: PlayerType,
        detail: MediaDetail,
        source: MediaSource?,
        url: String,
        startPositionTicks: Long,
        prefs: com.raulshma.jellyplay.core.model.UserPreferences,
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
        playerLifecycleManager.requestAutoEnterPip(eng.capabilities.supportsPip)

        val config = EngineConfigBuilder.buildFromPreferences(
            prefs = prefs,
            mediaStreams = _sessionState.value.mediaStreams,
            itemId = detail.item.id,
            engineSpecific = resolveEngineConfig(playerType, prefs),
        )
        eng.updateConfig(config)
        eng.setPlaybackSpeed(prefs.videoDefaultSpeed)

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
            preferredAudioLanguage = prefs.preferredAudioLanguage,
            preferredSubtitleLanguage = prefs.preferredSubtitleLanguage,
            maxVideoBitrate = if (prefs.playbackMode == PlaybackMode.AUTO)
                adaptiveBitrateManager.resolveEffectiveMaxBitrate()?.toInt()
                else null,
            serverUrl = serverUrl,
            authToken = token,
            minBufferMs = prefs.videoPreloadBufferSize.minBufferMs,
            maxBufferMs = prefs.videoPreloadBufferSize.maxBufferMs,
            normalizationGain = detail.item.normalizationGain,
            mimeType = mimeType,
        )

        lastPlaybackRequest = request
        eng.load(request)
    }

    private fun resolveEngineConfig(
        playerType: PlayerType,
        prefs: com.raulshma.jellyplay.core.model.UserPreferences,
    ): EngineSpecificConfig? = when (playerType) {
        PlayerType.MPV -> prefs.mpvConfig
        PlayerType.LIBVLC -> prefs.libVlcConfig
        PlayerType.EXO_PLAYER -> prefs.exoPlayerConfig
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
        val prefs = preferencesStore.preferences.first()
        val playerType = lastPlayerType ?: prefs.preferredPlayer
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
            playbackSpeed = prefs.videoDefaultSpeed,
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
        val prefs = preferencesStore.preferences.first()

        try {
            _engine.value?.release()
        } finally {
            _engine.value = null
        }

        val eng = playerEngineFactory.create(playerType)
        _engine.value = eng
        lastPlayerType = playerType

        playerLifecycleManager.activeCallbacks = eng
        playerLifecycleManager.requestAutoEnterPip(eng.capabilities.supportsPip)

        val state = _sessionState.value
        val config = EngineConfigBuilder.buildFromPreferences(
            prefs = prefs,
            mediaStreams = state.mediaStreams,
            itemId = state.currentItemId,
            engineSpecific = resolveEngineConfig(playerType, prefs),
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
     * PlaybackInfo response populates this for externally-delivered subs) or
     * that are flagged [MediaStream.isExternal] are always side-loaded. For
     * direct play, embedded subtitles are intentionally left out — ExoPlayer
     * reads them from the container and side-loading would duplicate every
     * track. When the server transcodes or direct-streams, however, embedded
     * subtitles are not reliably present in the HLS manifest, so each one is
     * fetched via the Jellyfin subtitle endpoint and side-loaded; this is
     * what makes the subtitle picker populate during transcoded playback.
     */
    private fun buildExternalSubtitles(
        detail: MediaDetail,
        source: MediaSource?,
        playMethod: PlayMethod,
    ): List<SubtitleSource> {
        val streams = source?.mediaStreams ?: return emptyList()
        return streams.filter { it.type == StreamType.SUBTITLE }.mapNotNull { stream ->
            val subUrl = when {
                !stream.deliveryUrl.isNullOrBlank() ->
                    playbackRepository.getSubtitleDeliveryUrl(stream.deliveryUrl!!)
                // External subs are always side-loaded; embedded subs are also
                // side-loaded when not direct-playing, because transcoded HLS
                // does not reliably expose them in-manifest.
                stream.isExternal || playMethod != PlayMethod.DIRECT_PLAY ->
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
