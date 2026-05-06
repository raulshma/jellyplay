package com.raulshma.jellyplay.feature.player.video

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import com.raulshma.jellyplay.core.data.playback.PlaybackSessionManager
import com.raulshma.jellyplay.core.data.cast.CastManager
import com.raulshma.jellyplay.core.data.playback.AdaptiveBitrateManager
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayManager
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.DecoderMode
import com.raulshma.jellyplay.core.model.MediaItem as JellyfinMediaItem
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaSource
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.PlayMethod
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.StreamType
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.feature.player.video.components.AspectRatio
import com.raulshma.jellyplay.feature.player.video.engine.ExoPlayerEngine
import com.raulshma.jellyplay.feature.player.video.engine.PlayerEngine
import com.raulshma.jellyplay.feature.player.video.engine.PlayerEngineFactory
import com.raulshma.jellyplay.feature.player.video.subtitle.SubtitleParserHelper
import com.raulshma.jellyplay.feature.player.video.subtitle.TimedCue
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import javax.inject.Inject

data class TrackOption(
    val index: Int,
    val label: String,
    val language: String?,
    val isSelected: Boolean,
    val trackGroup: androidx.media3.common.TrackGroup? = null,
)

@HiltViewModel
class VideoPlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
    private val downloadRepository: DownloadRepository,
    private val preferencesStore: UserPreferencesStore,
    private val sessionManager: PlaybackSessionManager,
    private val castManager: CastManager,
    private val syncPlayManager: SyncPlayManager,
    private val okHttpClient: OkHttpClient,
    private val adaptiveBitrateManager: AdaptiveBitrateManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VideoPlayerUiState())
    val uiState = _uiState.asStateFlow()

    private var playerEngine: PlayerEngine? = null
    private var mediaDetail: MediaDetail? = null
    private var secondarySubtitleCues: List<TimedCue> = emptyList()
    private var secondarySubtitleOffsetMs: Long = 0L
    private var equalizerEnabled: Boolean = false
    private var resolvedPlayMethod: PlayMethod = PlayMethod.DIRECT_PLAY
    private var playSessionId: String = java.util.UUID.randomUUID().toString()
    private var currentItemId: String? = null
    private var autoplayNext: Boolean = false
    private var trickplayBaseUrl: String? = null

    private val progressReporter = PlaybackProgressReporter(
        playbackRepository = playbackRepository,
        viewModel = this,
        uiState = _uiState,
        getCurrentItemId = { currentItemId },
        getPlaySessionId = { playSessionId },
        getResolvedPlayMethod = { resolvedPlayMethod },
        getPlayerEngine = { playerEngine },
    )
    private val syncPlayController = SyncPlayController(
        syncPlayManager = syncPlayManager,
        viewModel = this,
        uiState = _uiState,
        getPlayerEngine = { playerEngine },
    )

    val playerEngineRef: PlayerEngine? get() = playerEngine

    @Suppress("DEPRECATION")
    fun initialize(itemId: String, mediaSourceId: String?, startPositionTicks: Long) {
        if (currentItemId == itemId) return
        releaseInternals()
        playSessionId = java.util.UUID.randomUUID().toString()
        currentItemId = itemId
        trickplayBaseUrl = null

        viewModelScope.launch {
            val prefs = preferencesStore.preferences.first()

            val defaultAspectRatio = try {
                when (prefs.videoDefaultAspectRatio) {
                    "FIT" -> AspectRatio.FIT
                    "FILL" -> AspectRatio.FILL
                    "CROP" -> AspectRatio.CROP
                    "16:9" -> AspectRatio.RATIO_16_9
                    "4:3" -> AspectRatio.RATIO_4_3
                    "21:9" -> AspectRatio.RATIO_21_9
                    else -> AspectRatio.AUTO
                }
            } catch (_: Exception) {
                AspectRatio.AUTO
            }

            _uiState.update { it.copy(
                preferredPlayerType = prefs.preferredPlayer,
                subtitleStyle = prefs.subtitleStyle,
                audioDelayMs = prefs.audioDelayMs,
                decoderMode = prefs.decoderMode,
                audioPassthrough = prefs.audioPassthrough,
                frameRateMatching = prefs.frameRateMatching,
                nightModeEnabled = prefs.nightModeEnabled,
                seekDurationMs = prefs.videoSeekDurationMs,
                defaultOrientation = prefs.videoDefaultOrientation,
                controlsTimeoutMs = prefs.videoControlsTimeoutMs,
                gesturesEnabled = prefs.videoGesturesEnabled,
                defaultSpeed = prefs.videoDefaultSpeed,
                swipeSeekMaxMs = prefs.videoSwipeSeekMaxMs,
                rememberBrightness = prefs.videoRememberBrightness,
                brightnessLevel = prefs.videoBrightnessLevel,
                aspectRatio = defaultAspectRatio,
            ) }
            autoplayNext = prefs.videoAutoplayNext

            val detailResult = mediaRepository.getMediaDetail(itemId)
            val detail = detailResult.getOrElse {
                _uiState.update { it.copy(title = "Error loading media") }
                return@launch
            }

            mediaDetail = detail
            val source = if (mediaSourceId != null) {
                detail.mediaSources.find { it.id == mediaSourceId }
            } else {
                detail.mediaSources.firstOrNull()
            }
            val streams = source?.mediaStreams ?: emptyList()
            val detectedRatio = detectAspectRatio(streams)

            val playMethodStr = when {
                source?.supportsDirectPlay == true -> "Direct Play"
                source?.supportsDirectStream == true -> "Direct Stream"
                source?.supportsTranscoding == true -> "Transcode"
                else -> "Direct Play"
            }
            resolvedPlayMethod = when {
                source?.supportsDirectPlay == true -> PlayMethod.DIRECT_PLAY
                source?.supportsDirectStream == true -> PlayMethod.DIRECT_STREAM
                source?.supportsTranscoding == true -> PlayMethod.TRANSCODE
                else -> PlayMethod.DIRECT_PLAY
            }

            _uiState.update { it.copy(
                title = detail.item.name,
                subtitle = detail.item.seriesName ?: (detail.item.overview?.take(60) ?: ""),
                chapters = detail.chapters,
                currentMediaSource = source,
                mediaStreams = streams,
                detectedAspectRatio = detectedRatio,
                playMethod = playMethodStr,
                seriesId = detail.item.seriesId,
            ) }

            val localDownload = downloadRepository.getDownloadByMediaItemId(itemId)
            val file = localDownload?.let {
                java.io.File(it.downloadPath).takeIf { f -> f.exists() }
            }
            val url = if (localDownload != null && file != null &&
                localDownload.status == com.raulshma.jellyplay.core.model.DownloadStatus.COMPLETED
            ) {
                _uiState.update { it.copy(playMethod = "Offline") }
                resolvedPlayMethod = PlayMethod.DIRECT_PLAY
                Uri.fromFile(file).toString()
            } else {
                playbackRepository.getStreamUrl(
                    itemId,
                    source?.id ?: "",
                    startPositionTicks,
                )
            }
            _uiState.update { it.copy(streamUrl = url) }

            val playerType = _uiState.value.preferredPlayerType
            if (playerType == PlayerType.EXTERNAL) return@launch

            initializeEngine(playerType, detail, source, url, startPositionTicks, prefs)

            playbackRepository.reportPlaybackStart(
                com.raulshma.jellyplay.core.model.PlaybackStartInfo(
                    itemId = itemId,
                    sessionId = playSessionId,
                    mediaSourceId = source?.id,
                    playMethod = resolvedPlayMethod,
                )
            )

            progressReporter.startPositionTracking()
            progressReporter.startProgressReporting()
            fetchIntroTimestamps(itemId)
            fetchCreditTimestamps(itemId)
            fetchNextEpisode(detail)
        }
    }

    private fun initializeEngine(
        playerType: PlayerType,
        detail: MediaDetail,
        source: MediaSource?,
        url: String,
        startPositionTicks: Long,
        prefs: com.raulshma.jellyplay.core.model.UserPreferences,
    ) {
        val state = _uiState.value
        val engine = PlayerEngineFactory.create(context, playerType)
        playerEngine = engine

        _uiState.update {
            it.copy(
                engineCapabilities = EngineCapabilities(
                    audioDelay = engine.supportsAudioDelay,
                    subtitleDelay = engine.supportsSubtitleDelay,
                    audioPassthrough = engine.supportsAudioPassthrough,
                    subtitleStyle = engine.supportsSubtitleStyle,
                    dialogueBoost = engine.supportsDialogueBoost,
                    nightMode = engine.supportsNightMode,
                    ocr = engine.supportsOcr,
                    cues = engine.supportsCues,
                )
            )
        }

        engine.setDecoderMode(state.decoderMode)
        engine.setAudioDelay(state.audioDelayMs)
        engine.setSubtitleDelay(state.subtitleStyle.offsetMs)
        engine.setAudioPassthrough(state.audioPassthrough)

        engine.setOnStateChanged { playing ->
            _uiState.update { it.copy(isPlaying = playing) }
        }
        engine.setOnTracksChanged {
            updateTracksFromEngine(engine)
        }
        engine.setOnError { errorMsg ->
            _uiState.update { it.copy(playerError = errorMsg) }
        }

        if (engine is ExoPlayerEngine) {
            (engine as ExoPlayerEngine).setOnPlaybackStateChanged { playbackState ->
                if (playbackState == androidx.media3.common.Player.STATE_ENDED) {
                    if (autoplayNext) playNextEpisode()
                }
            }

            engine.createPlayer(
                url = url,
                title = detail.item.name,
                startPositionMs = startPositionTicks / 10_000,
            )

            engine.setMediaSessionCallback(sessionManager, playSessionId)

            val streams = source?.mediaStreams ?: emptyList()
            val subtitleConfigs = engine.buildSubtitleConfigurations(
                streams,
                getSubtitleDeliveryUrl = { playbackRepository.getSubtitleDeliveryUrl(it) },
            )
            val artworkUri = playbackRepository.getImageUrl(detail.item.id, maxWidth = 300)
            engine.configureMedia(
                url = url,
                title = detail.item.name,
                startPositionMs = startPositionTicks / 10_000,
                subtitleConfigs = subtitleConfigs,
                artworkUri = artworkUri,
            )

            engine.setPreferredAudioLanguage(prefs.preferredAudioLanguage)
            engine.setPreferredSubtitleLanguage(prefs.preferredSubtitleLanguage)

            val maxBitrate = adaptiveBitrateManager.resolveMaxBitrate(prefs.streamingQuality)
            if (maxBitrate != null) engine.setMaxVideoBitrate(maxBitrate.toInt())

            _uiState.update { it.copy(dialogueBoostEnabled = prefs.dialogueBoostEnabled) }
            engine.setDialogueBoostEnabled(prefs.dialogueBoostEnabled)

            _uiState.update { it.copy(nightModeEnabled = prefs.nightModeEnabled) }
            engine.setNightModeEnabled(prefs.nightModeEnabled, prefs.audioNightModeGain)

            equalizerEnabled = prefs.equalizerEnabled
            if (equalizerEnabled) {
                engine.setEqualizerEnabled(true, prefs.equalizerSettings)
            }
        } else {
            // For MPV and LibVLC, configure external subtitles before initialization
            val streams = source?.mediaStreams ?: emptyList()
            val externalSubtitles = streams
                .filter { it.type == StreamType.SUBTITLE && !it.deliveryUrl.isNullOrBlank() }
                .map { stream ->
                    val subUrl = playbackRepository.getSubtitleDeliveryUrl(stream.deliveryUrl!!)
                    val label = stream.displayTitle ?: stream.title ?: stream.language ?: "Unknown"
                    subUrl to label
                }
            
            if (externalSubtitles.isNotEmpty()) {
                when (engine) {
                    is com.raulshma.jellyplay.feature.player.video.engine.MpvPlayerEngine -> {
                        engine.configureExternalSubtitles(externalSubtitles)
                    }
                    is com.raulshma.jellyplay.feature.player.video.engine.LibVlcPlayerEngine -> {
                        engine.configureExternalSubtitles(externalSubtitles)
                    }
                }
            }
            
            engine.initialize(url, detail.item.name, startPositionTicks / 10_000)
            
            // Apply dialogue boost setting for MPV and LibVLC
            _uiState.update { it.copy(dialogueBoostEnabled = prefs.dialogueBoostEnabled) }
            engine.setDialogueBoostEnabled(prefs.dialogueBoostEnabled)
        }

        val speed = state.defaultSpeed
        if (speed != 1.0f) {
            _uiState.update { it.copy(playbackSpeed = speed) }
            engine.setPlaybackSpeed(speed)
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        _uiState.update { it.copy(playbackSpeed = speed) }
        playerEngine?.setPlaybackSpeed(speed)
    }

    fun selectAudioTrack(option: TrackOption) {
        val engine = playerEngine ?: return
        engine.selectAudioTrack(option.index)
        _uiState.update { state ->
            val isDefault = option.index < 0
            state.copy(audioTracks = state.audioTracks.map { track ->
                val matches = track.index == option.index
                val isDefaultTrack = track.index < 0
                track.copy(isSelected = if (isDefault) isDefaultTrack else matches)
            })
        }
    }

    fun selectSubtitleTrack(option: TrackOption) {
        val engine = playerEngine ?: return
        
        // Apply selection to the player engine
        engine.selectSubtitleTrack(option.index)
        
        // Update internal state tracking
        if (option.index < 0) {
            selectedSubtitleTrackId = null
        } else {
            selectedSubtitleTrackId = option.index to option.trackGroup
        }
        
        // Update UI state to reflect the selection
        _uiState.update { state ->
            val isOff = option.index < 0
            state.copy(subtitleTracks = state.subtitleTracks.map { track ->
                val matches = track.index == option.index && track.trackGroup == option.trackGroup
                val isOffTrack = track.index < 0
                track.copy(isSelected = if (isOff) isOffTrack else matches)
            })
        }
    }

    fun selectSecondarySubtitleStream(stream: MediaStream?) {
        _uiState.update { it.copy(secondarySubtitleTrack = stream) }
        if (stream == null) {
            secondarySubtitleCues = emptyList()
            return
        }
        viewModelScope.launch {
            loadSecondarySubtitle(stream)
        }
    }

    fun setSecondarySubtitleOffset(offsetMs: Long) {
        secondarySubtitleOffsetMs = offsetMs
    }

    private suspend fun loadSecondarySubtitle(stream: MediaStream) {
        try {
            val url = if (!stream.deliveryUrl.isNullOrBlank()) {
                playbackRepository.getSubtitleDeliveryUrl(stream.deliveryUrl!!)
            } else {
                val itemId = currentItemId ?: return
                val sourceId = _uiState.value.currentMediaSource?.id ?: return
                val codec = stream.codec ?: "srt"
                val ext = when (codec.lowercase()) {
                    "ass", "ssa" -> "ass"
                    "vtt", "webvtt" -> "vtt"
                    "ttml", "dfxp" -> "ttml"
                    "mov_text" -> "vtt"
                    else -> "srt"
                }
                playbackRepository.getSubtitleDeliveryUrl("/Videos/$itemId/$sourceId/Subtitles/${stream.index}/0.$ext")
            }
            val mimeType = mapSubtitleCodecToMime(stream.codec)
            val request = okhttp3.Request.Builder().url(url).build()
            val response = okHttpClient.newCall(request).execute()
            response.use { resp ->
                val bytes = resp.body?.bytes() ?: return
                secondarySubtitleCues = SubtitleParserHelper.parseSubtitles(bytes, mimeType)
            }
        } catch (_: Exception) {
            secondarySubtitleCues = emptyList()
        }
    }

    fun getSecondarySubtitleText(positionMs: Long): String? {
        val cues = secondarySubtitleCues
        if (cues.isEmpty()) return null
        val cue = SubtitleParserHelper.findActiveCue(
            cues,
            positionMs * 1000L,
            secondarySubtitleOffsetMs * 1000L,
        ) ?: return null
        return cue.text.toString().takeIf { it.isNotBlank() }
    }

    fun getCurrentPrimarySubtitleText(): String? {
        val engine = playerEngine ?: return null
        val cues = engine.getCurrentCues()
        if (cues.isEmpty()) return null
        return cues.mapNotNull { it.text?.toString() }
            .joinToString("\n")
            .takeIf { it.isNotBlank() }
    }

    fun setAspectRatio(ratio: AspectRatio) {
        _uiState.update { it.copy(aspectRatio = ratio) }
        if (ratio == AspectRatio.AUTO) {
            val detected = detectAspectRatio(_uiState.value.mediaStreams)
            _uiState.update { it.copy(detectedAspectRatio = detected) }
        }
    }

    private fun detectAspectRatio(streams: List<MediaStream>): AspectRatio? {
        val videoStream = streams.firstOrNull { it.type == StreamType.VIDEO } ?: return null
        val width = videoStream.width ?: return null
        val height = videoStream.height ?: return null
        if (height == 0) return null

        val nativeRatio = width.toFloat() / height.toFloat()
        return when {
            nativeRatio >= 2.3f -> AspectRatio.RATIO_21_9
            nativeRatio >= 1.7f -> AspectRatio.RATIO_16_9
            nativeRatio >= 1.3f -> AspectRatio.RATIO_4_3
            else -> AspectRatio.FIT
        }
    }

    fun setSubtitleStyle(style: SubtitleStyle) {
        _uiState.update { it.copy(subtitleStyle = style) }
        playerEngine?.setSubtitleDelay(style.offsetMs)
        viewModelScope.launch {
            preferencesStore.setSubtitleStyle(style)
        }
    }

    fun applySubtitleStyleToView(view: android.view.View?) {
        val engine = playerEngine ?: return
        engine.setSubtitleStyle(_uiState.value.subtitleStyle, view)
    }

    fun toggleDialogueBoost() {
        val newVal = !_uiState.value.dialogueBoostEnabled
        _uiState.update { it.copy(dialogueBoostEnabled = newVal) }
        playerEngine?.setDialogueBoostEnabled(newVal)
        viewModelScope.launch {
            preferencesStore.setDialogueBoostEnabled(newVal)
        }
    }

    fun toggleNightMode() {
        val newVal = !_uiState.value.nightModeEnabled
        _uiState.update { it.copy(nightModeEnabled = newVal) }
        playerEngine?.setNightModeEnabled(newVal)
        viewModelScope.launch {
            preferencesStore.setNightModeEnabled(newVal)
        }
    }

    fun setAudioDelay(ms: Long) {
        _uiState.update { it.copy(audioDelayMs = ms) }
        playerEngine?.setAudioDelay(ms)
        viewModelScope.launch {
            preferencesStore.setAudioDelay(ms)
        }
    }

    fun setDecoderMode(mode: DecoderMode) {
        _uiState.update { it.copy(decoderMode = mode) }
        playerEngine?.setDecoderMode(mode)
        viewModelScope.launch {
            preferencesStore.setDecoderMode(mode)
        }
    }

    fun setAudioPassthrough(enabled: Boolean) {
        _uiState.update { it.copy(audioPassthrough = enabled) }
        playerEngine?.setAudioPassthrough(enabled)
        viewModelScope.launch {
            preferencesStore.setAudioPassthrough(enabled)
        }
    }

    fun setFrameRateMatching(enabled: Boolean) {
        _uiState.update { it.copy(frameRateMatching = enabled) }
        viewModelScope.launch {
            preferencesStore.setFrameRateMatching(enabled)
        }
    }

    fun toggleEqualizer() {
        equalizerEnabled = !equalizerEnabled
        val engine = playerEngine
        if (engine is ExoPlayerEngine) {
            engine.setEqualizerEnabled(equalizerEnabled)
        }
        viewModelScope.launch {
            preferencesStore.setEqualizerEnabled(equalizerEnabled)
        }
    }

    fun setEqualizerSettings(settings: com.raulshma.jellyplay.core.model.EqualizerSettings) {
        val engine = playerEngine
        if (engine is ExoPlayerEngine) {
            engine.setEqualizerEnabled(equalizerEnabled, settings)
        }
        viewModelScope.launch {
            preferencesStore.setEqualizerSettings(settings)
        }
    }

    fun playNextEpisode() {
        val detail = mediaDetail ?: return
        val seriesId = detail.item.seriesId ?: return
        viewModelScope.launch {
            val episodes = mediaRepository.getEpisodes(seriesId, detail.item.seasonId ?: return@launch)
                .getOrElse { return@launch }
            val currentIndex = episodes.indexOfFirst { it.id == currentItemId }
            if (currentIndex < 0 || currentIndex + 1 >= episodes.size) return@launch
            val next = episodes[currentIndex + 1]
            initialize(next.id, null, 0L)
        }
    }

    fun saveBrightness(level: Float) {
        _uiState.update { it.copy(brightnessLevel = level) }
        if (_uiState.value.rememberBrightness) {
            viewModelScope.launch {
                preferencesStore.setVideoBrightnessLevel(level)
            }
        }
    }

    fun skipIntro() {
        val ts = _uiState.value.introTimestamps ?: return
        val targetMs = ts.introEndTicks / 10_000
        playerEngine?.seekTo(targetMs)
    }

    private fun fetchIntroTimestamps(itemId: String) {
        viewModelScope.launch {
            val ts = playbackRepository.getIntroTimestamps(itemId).getOrNull()
            _uiState.update { it.copy(introTimestamps = ts) }
        }
    }

    private fun fetchCreditTimestamps(itemId: String) {
        viewModelScope.launch {
            val ts = playbackRepository.getCreditTimestamps(itemId).getOrNull()
            _uiState.update { it.copy(creditTimestamps = ts) }
        }
    }

    private fun fetchNextEpisode(currentDetail: MediaDetail) {
        val seriesId = currentDetail.item.seriesId ?: return
        val seasonId = currentDetail.item.seasonId ?: return
        viewModelScope.launch {
            val episodes = mediaRepository.getEpisodes(seriesId, seasonId).getOrElse { return@launch }
            val currentIndex = episodes.indexOfFirst { it.id == currentItemId }
            if (currentIndex >= 0 && currentIndex + 1 < episodes.size) {
                _uiState.update { it.copy(nextEpisode = episodes[currentIndex + 1]) }
            } else {
                _uiState.update { it.copy(nextEpisode = null) }
            }
        }
    }

    fun skipCredits() {
        val ts = _uiState.value.creditTimestamps ?: return
        val targetMs = ts.creditEndTicks / 10_000
        playerEngine?.seekTo(targetMs)
    }

    fun getImageUrl(itemId: String, maxWidth: Int = 400): String =
        playbackRepository.getImageUrl(itemId, "Primary", maxWidth)

    fun loadRemoteSubtitles() {
        val itemId = currentItemId ?: return
        _uiState.update { it.copy(isLoadingRemoteSubtitles = true) }
        viewModelScope.launch {
            val subs = playbackRepository.getRemoteSubtitles(itemId).getOrElse { emptyList() }
            _uiState.update { it.copy(remoteSubtitles = subs, isLoadingRemoteSubtitles = false) }
        }
    }

    fun downloadSubtitle(subtitleInfo: com.raulshma.jellyplay.core.model.RemoteSubtitleInfo) {
        val itemId = currentItemId ?: return
        viewModelScope.launch {
            playbackRepository.downloadSubtitle(itemId, subtitleInfo.id)
            val detailResult = mediaRepository.getMediaDetail(itemId)
            detailResult.getOrNull()?.let { detail ->
                mediaDetail = detail
                val source = detail.mediaSources.firstOrNull()
                val streams = source?.mediaStreams ?: emptyList()
                _uiState.update { it.copy(
                    currentMediaSource = source,
                    mediaStreams = streams,
                    detectedAspectRatio = detectAspectRatio(streams),
                ) }
            }
        }
    }

    fun joinSyncPlay(groupId: String) {
        syncPlayController.joinGroup(groupId)
    }

    fun leaveSyncPlay() {
        syncPlayController.leaveGroup()
    }

    val isCastAvailable: Boolean
        get() = castManager.isCastAvailable

    val isCastConnected: Boolean
        get() = castManager.isConnected

    val isInSyncPlaySession: Boolean
        get() = syncPlayController.isInSession

    fun castToDevice() {
        val state = _uiState.value
        val engine = playerEngine ?: return
        val currentMedia = if (engine is ExoPlayerEngine) {
            engine.currentMediaItem
        } else null

        val mediaItem = currentMedia ?: run {
            val url = state.streamUrl ?: return
            val artworkUri = currentItemId?.let {
                try { Uri.parse(playbackRepository.getImageUrl(it, maxWidth = 300)) } catch (_: Exception) { null }
            }
            MediaItem.Builder()
                .setUri(url)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(state.title)
                        .setSubtitle(state.subtitle)
                        .setArtworkUri(artworkUri)
                        .build()
                )
                .build()
        }
        val positionMs = engine.currentPositionMs
        castManager.loadMedia(mediaItem, positionMs, object : androidx.media3.common.Player.Listener {})
    }

    fun captureOcrSubtitle(bitmap: android.graphics.Bitmap?) {
        if (_uiState.value.isOcrRunning) return
        if (bitmap == null) {
            _uiState.update { it.copy(ocrText = null) }
            return
        }
        _uiState.update { it.copy(isOcrRunning = true) }
        viewModelScope.launch {
            try {
                val text = com.raulshma.jellyplay.feature.player.video.subtitle.SubtitleOcrHelper
                    .extractSubtitleTextFromFrame(bitmap)
                _uiState.update { it.copy(ocrText = text) }
            } catch (_: Exception) {
                _uiState.update { it.copy(ocrText = null) }
            } finally {
                if (!bitmap.isRecycled) bitmap.recycle()
                _uiState.update { it.copy(isOcrRunning = false) }
            }
        }
    }

    fun capturePlayerViewBitmap(): android.graphics.Bitmap? {
        return playerEngine?.captureViewBitmap()
    }

    fun clearOcrText() {
        _uiState.update { it.copy(ocrText = null) }
    }

    fun getTrickplayImageUrl(positionMs: Long): String? {
        val itemId = currentItemId ?: return null
        val base = trickplayBaseUrl ?: run {
            val server = playbackRepository.getImageUrl(itemId).substringBefore("/Items")
            trickplayBaseUrl = server
            server
        }
        val index = (positionMs / 10_000).toInt()
        return "$base/Items/$itemId/Trickplay/320/$index.jpg"
    }

    private var selectedSubtitleTrackId: Pair<Int, Any?>? = null

    private fun updateTracksFromEngine(engine: PlayerEngine) {
        val streams = _uiState.value.mediaStreams
        val audioOptions = engine.audioTracks.map { t ->
            TrackOption(t.index, t.label, t.language, t.isSelected, trackGroup = t.trackGroup as? androidx.media3.common.TrackGroup)
        }

        val audioTracks = if (audioOptions.isEmpty()) {
            listOf(TrackOption(-1, "Default", null, true))
        } else {
            listOf(TrackOption(-1, "Default", null, true)) + audioOptions
        }

        val engineSubOptions = engine.subtitleTracks.map { t ->
            TrackOption(t.index, t.label, t.language, t.isSelected, trackGroup = t.trackGroup as? androidx.media3.common.TrackGroup)
        }

        // Always use engine tracks as source of truth for primary subtitles
        val subtitleTracks = if (engineSubOptions.isEmpty()) {
            // No subtitle tracks available at all
            listOf(TrackOption(-1, "None", null, true))
        } else {
            val sel = selectedSubtitleTrackId
            listOf(TrackOption(-1, "Off", null, sel == null)) + engineSubOptions.map { t ->
                val isSel = sel != null && sel.first == t.index && sel.second == t.trackGroup
                t.copy(isSelected = isSel)
            }
        }

        _uiState.update { it.copy(audioTracks = audioTracks, subtitleTracks = subtitleTracks) }
    }

    private fun mapSubtitleCodecToMime(codec: String?): String {
        if (codec == null) return MimeTypes.TEXT_UNKNOWN
        return when (codec.lowercase()) {
            "srt", "subrip" -> MimeTypes.APPLICATION_SUBRIP
            "ass", "ssa" -> MimeTypes.TEXT_SSA
            "vtt", "webvtt" -> MimeTypes.APPLICATION_SUBRIP
            "ttml", "dfxp" -> MimeTypes.APPLICATION_TTML
            "pgs" -> MimeTypes.APPLICATION_PGS
            "mov_text" -> MimeTypes.APPLICATION_SUBRIP
            else -> MimeTypes.TEXT_UNKNOWN
        }
    }

    private fun releaseInternals() {
        progressReporter.cancelJobs()
        syncPlayController.reset()
        val engine = playerEngine
        if (engine is ExoPlayerEngine) {
            engine.releaseMediaSession(sessionManager)
        }
        playerEngine?.release()
        playerEngine = null
        currentItemId = null
        trickplayBaseUrl = null
        selectedSubtitleTrackId = null
        secondarySubtitleCues = emptyList()
        _uiState.update { it.copy(
            introTimestamps = null,
            creditTimestamps = null,
            nextEpisode = null,
            remoteSubtitles = emptyList(),
        ) }
    }

    fun release() {
        val itemId = currentItemId
        val sessionId = playSessionId
        val engine = playerEngine
        val positionTicks = engine?.currentPositionMs?.let { it * 10_000 } ?: 0L
        releaseInternals()
        castManager.release()
        if (itemId != null && positionTicks > 0) {
            viewModelScope.launch {
                playbackRepository.reportPlaybackStopped(
                    itemId = itemId,
                    sessionId = sessionId,
                    positionTicks = positionTicks,
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        releaseInternals()
    }
}
