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
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.MediaSession
import com.raulshma.jellyplay.feature.player.video.subtitle.OffsettingSubtitleParserFactory
import com.raulshma.jellyplay.feature.player.video.subtitle.SubtitleParserHelper
import com.raulshma.jellyplay.feature.player.video.subtitle.TimedCue
import com.raulshma.jellyplay.core.data.playback.DialogueBoostHelper
import com.raulshma.jellyplay.core.data.playback.EqualizerHelper
import com.raulshma.jellyplay.core.data.playback.NightModeHelper
import com.raulshma.jellyplay.core.data.playback.PlaybackSessionManager
import com.raulshma.jellyplay.core.data.cast.CastManager
import com.raulshma.jellyplay.core.data.playback.AdaptiveBitrateManager
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayCommand
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayManager
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.DecoderMode
import com.raulshma.jellyplay.core.model.CreditTimestamps
import com.raulshma.jellyplay.core.model.IntroTimestamps
import com.raulshma.jellyplay.core.model.MediaItem as JellyfinMediaItem
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaSource
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.PlayMethod
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.StreamType
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.feature.player.video.components.AspectRatio
import com.raulshma.jellyplay.feature.player.video.engine.PlayerEngine
import com.raulshma.jellyplay.feature.player.video.engine.PlayerEngineFactory
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

    private var exoPlayer: ExoPlayer? = null
    private var playerEngine: PlayerEngine? = null
    private var trackSelector: DefaultTrackSelector? = null
    private var mediaSession: MediaSession? = null
    private var mediaDetail: MediaDetail? = null
    private var secondarySubtitleCues: List<TimedCue> = emptyList()
    private var secondarySubtitleOffsetMs: Long = 0L
    private var equalizerEnabled: Boolean = false
    @Volatile
    var subtitleOffsetMs: Long = 0L
        private set
    private var resolvedPlayMethod: PlayMethod = PlayMethod.DIRECT_PLAY
    private var playSessionId: String = java.util.UUID.randomUUID().toString()
    private var currentItemId: String? = null
    private var autoplayNext: Boolean = false

    private val audioEffects = AudioEffectsController()
    private val progressReporter = PlaybackProgressReporter(
        playbackRepository = playbackRepository,
        viewModel = this,
        uiState = _uiState,
        getCurrentItemId = { currentItemId },
        getPlaySessionId = { playSessionId },
        getResolvedPlayMethod = { resolvedPlayMethod },
        getExoPlayer = { exoPlayer },
        getPlayerEngine = { playerEngine },
    )
    private val syncPlayController = SyncPlayController(
        syncPlayManager = syncPlayManager,
        viewModel = this,
        uiState = _uiState,
        getExoPlayer = { exoPlayer },
        getPlayerEngine = { playerEngine },
    )

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.update { it.copy(isPlaying = isPlaying) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            updateTracks()
            if (playbackState == Player.STATE_READY) {
                val state = _uiState.value
                if (state.dialogueBoostEnabled) audioEffects.applyDialogueBoost(exoPlayer, true)
                if (state.nightModeEnabled) audioEffects.applyNightMode(exoPlayer, true)
                if (equalizerEnabled) audioEffects.applyEqualizer(exoPlayer, true)
            }
            if (playbackState == Player.STATE_ENDED) {
                if (autoplayNext) {
                    playNextEpisode()
                }
            }
        }

        override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
            updateTracks()
        }
    }

    val exoPlayerRef: ExoPlayer? get() = exoPlayer
    val playerEngineRef: PlayerEngine? get() = playerEngine

    fun initialize(itemId: String, mediaSourceId: String?, startPositionTicks: Long) {
        if (currentItemId == itemId) return
        releaseInternals()
        playSessionId = java.util.UUID.randomUUID().toString()
        currentItemId = itemId

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
            subtitleOffsetMs = prefs.subtitleStyle.offsetMs
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
            when (playerType) {
                PlayerType.EXO_PLAYER -> initializeExoPlayer(
                    detail, source, url, startPositionTicks, prefs
                )
                PlayerType.MPV, PlayerType.LIBVLC -> initializeAlternativeEngine(
                    url, detail.item.name, startPositionTicks
                )
                PlayerType.EXTERNAL -> return@launch
            }

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

    private suspend fun initializeExoPlayer(
        detail: MediaDetail,
        source: MediaSource?,
        url: String,
        startPositionTicks: Long,
        prefs: com.raulshma.jellyplay.core.model.UserPreferences,
    ) {
        val selector = DefaultTrackSelector(context)
        trackSelector = selector

        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setSubtitleParserFactory(
                OffsettingSubtitleParserFactory(
                    DefaultSubtitleParserFactory(),
                ) { subtitleOffsetMs * 1000L }
            )

        val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val state = _uiState.value
        val rendererMode = when (state.decoderMode) {
            DecoderMode.HW_PREFERRED -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
            DecoderMode.HW_ONLY -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
            DecoderMode.SW_ONLY -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
        }
        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(rendererMode)

        val player = ExoPlayer.Builder(context)
            .setRenderersFactory(renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(selector)
            .setAudioAttributes(audioAttributes, true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        exoPlayer = player
        player.addListener(playerListener)

        val session = MediaSession.Builder(context, player)
            .setId(playSessionId)
            .build()
        mediaSession = session
        sessionManager.setActiveSession(session)

        val subtitleConfigs = buildSubtitleConfigurations(source?.mediaStreams ?: emptyList())
        val artworkUri = Uri.parse(
            playbackRepository.getImageUrl(detail.item.id, maxWidth = 300)
        )

        val mediaItem = MediaItem.Builder()
            .setUri(url)
            .setSubtitleConfigurations(subtitleConfigs)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(detail.item.name)
                    .setSubtitle(detail.item.seriesName ?: detail.item.overview?.take(60))
                    .setArtworkUri(artworkUri)
                    .build()
            )
            .build()

        player.setMediaItem(mediaItem)
        player.prepare()
        if (startPositionTicks > 0) player.seekTo(startPositionTicks / 10_000)
        val speed = state.defaultSpeed
        if (speed != 1.0f) {
            _uiState.update { it.copy(playbackSpeed = speed) }
            player.setPlaybackSpeed(speed)
        }
        player.play()

        _uiState.update { it.copy(dialogueBoostEnabled = prefs.dialogueBoostEnabled) }
        if (prefs.dialogueBoostEnabled) audioEffects.applyDialogueBoost(exoPlayer, true)

        _uiState.update { it.copy(nightModeEnabled = prefs.nightModeEnabled) }
        audioEffects.setNightModeTargetGain(prefs.audioNightModeGain)
        if (prefs.nightModeEnabled) audioEffects.applyNightMode(exoPlayer, true)

        equalizerEnabled = prefs.equalizerEnabled
        if (equalizerEnabled) {
            audioEffects.setEqualizerSettings(prefs.equalizerSettings)
            audioEffects.applyEqualizer(exoPlayer, true)
        }

        if (prefs.preferredAudioLanguage != null) {
            val params = selector.buildUponParameters()
            params.setPreferredAudioLanguage(prefs.preferredAudioLanguage!!)
            selector.setParameters(params)
        }

        if (prefs.preferredSubtitleLanguage != null) {
            val params = selector.buildUponParameters()
            params.setPreferredTextLanguage(prefs.preferredSubtitleLanguage!!)
            selector.setParameters(params)
        }

        val maxBitrate = adaptiveBitrateManager.resolveMaxBitrate(prefs.streamingQuality)
        if (maxBitrate != null) {
            val params = selector.buildUponParameters()
            params.setMaxVideoBitrate(maxBitrate.toInt())
            selector.setParameters(params)
        }
    }

    private fun initializeAlternativeEngine(
        url: String,
        title: String,
        startPositionTicks: Long,
    ) {
        val state = _uiState.value
        val engine = PlayerEngineFactory.create(context, state.preferredPlayerType)
        playerEngine = engine

        engine.setDecoderMode(state.decoderMode)
        engine.setAudioDelay(state.audioDelayMs)
        engine.setAudioPassthrough(state.audioPassthrough)

        engine.setOnStateChanged { playing ->
            _uiState.update { it.copy(isPlaying = playing) }
        }
        engine.setOnTracksChanged {
            updateTracksFromEngine(engine)
        }

        engine.initialize(url, title, startPositionTicks / 10_000)
        val speed = state.defaultSpeed
        if (speed != 1.0f) {
            _uiState.update { it.copy(playbackSpeed = speed) }
            engine.setPlaybackSpeed(speed)
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        _uiState.update { it.copy(playbackSpeed = speed) }
        if (playerEngine != null) {
            playerEngine?.setPlaybackSpeed(speed)
        } else {
            exoPlayer?.setPlaybackSpeed(speed)
        }
    }

    fun selectAudioTrack(option: TrackOption) {
        val engine = playerEngine
        if (engine != null) {
            engine.selectAudioTrack(option.index)
            updateTracksFromEngine(engine)
            return
        }
        val selector = trackSelector ?: return
        if (option.index < 0) {
            val params = selector.buildUponParameters()
            params.clearOverridesOfType(C.TRACK_TYPE_AUDIO)
            selector.setParameters(params)
        } else {
            val group = option.trackGroup ?: return
            val override = androidx.media3.common.TrackSelectionOverride(group, listOf(option.index))
            val params = selector.buildUponParameters()
                .setOverrideForType(override)
                .build()
            selector.setParameters(params)
        }
    }

    fun selectSubtitleTrack(option: TrackOption) {
        val engine = playerEngine
        val streams = _uiState.value.mediaStreams
        if (engine != null) {
            if (option.trackGroup == null && option.index >= 0) {
                val serverSubs = streams.filter { it.type == StreamType.SUBTITLE }
                val stream = serverSubs.getOrNull(option.index)
                if (stream != null) {
                    selectSecondarySubtitleStream(stream)
                    return
                }
            }
            engine.selectSubtitleTrack(option.index)
            updateTracksFromEngine(engine)
            return
        }
        val selector = trackSelector ?: return
        if (option.index < 0) {
            val params = selector.buildUponParameters()
            params.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            selector.setParameters(params)
            selectSecondarySubtitleStream(null)
        } else {
            val group = option.trackGroup
            if (group != null) {
                val override = androidx.media3.common.TrackSelectionOverride(group, listOf(option.index))
                val params = selector.buildUponParameters()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .setOverrideForType(override)
                    .build()
                selector.setParameters(params)
            } else {
                val serverSubs = streams.filter { it.type == StreamType.SUBTITLE }
                val stream = serverSubs.getOrNull(option.index)
                if (stream != null) {
                    selectSecondarySubtitleStream(stream)
                }
            }
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
            val mimeType = mapSubtitleCodecToMime(stream.codec) ?: MimeTypes.APPLICATION_SUBRIP
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
        val player = exoPlayer ?: return null
        val cues = player.currentCues.cues
        if (cues.isEmpty()) return null
        return cues.joinToString("\n") { it.text?.toString() ?: "" }
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
        subtitleOffsetMs = style.offsetMs
        viewModelScope.launch {
            preferencesStore.setSubtitleStyle(style)
        }
    }

    fun toggleDialogueBoost() {
        val newVal = !_uiState.value.dialogueBoostEnabled
        _uiState.update { it.copy(dialogueBoostEnabled = newVal) }
        audioEffects.applyDialogueBoost(exoPlayer, newVal)
        viewModelScope.launch {
            preferencesStore.setDialogueBoostEnabled(newVal)
        }
    }

    fun toggleNightMode() {
        val newVal = !_uiState.value.nightModeEnabled
        _uiState.update { it.copy(nightModeEnabled = newVal) }
        audioEffects.applyNightMode(exoPlayer, newVal)
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
        audioEffects.applyEqualizer(exoPlayer, equalizerEnabled)
        viewModelScope.launch {
            preferencesStore.setEqualizerEnabled(equalizerEnabled)
        }
    }

    fun setEqualizerSettings(settings: com.raulshma.jellyplay.core.model.EqualizerSettings) {
        audioEffects.setEqualizerSettings(settings)
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
        if (playerEngine != null) {
            playerEngine?.seekTo(targetMs)
        } else {
            exoPlayer?.seekTo(targetMs)
        }
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
        if (playerEngine != null) {
            playerEngine?.seekTo(targetMs)
        } else {
            exoPlayer?.seekTo(targetMs)
        }
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
        val currentMedia = exoPlayer?.currentMediaItem
            ?: run {
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
        val positionMs = exoPlayer?.currentPosition ?: playerEngine?.currentPositionMs ?: 0L
        castManager.loadMedia(currentMedia, positionMs, playerListener)
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

    fun clearOcrText() {
        _uiState.update { it.copy(ocrText = null) }
    }

    fun getTrickplayImageUrl(positionMs: Long): String? {
        val itemId = currentItemId ?: return null
        val server = playbackRepository.getImageUrl(itemId).substringBefore("/Items")
        val index = (positionMs / 10_000).toInt()
        return "$server/Items/$itemId/Trickplay/320/$index.jpg"
    }

    private fun updateTracks() {
        val player = exoPlayer ?: return
        val tracks = player.currentTracks
        val streams = _uiState.value.mediaStreams

        val audioOptions = mutableListOf<TrackOption>()
        val subtitleOptions = mutableListOf<TrackOption>()

        for (group in tracks.groups) {
            when (group.type) {
                C.TRACK_TYPE_AUDIO -> {
                    for (i in 0 until group.length) {
                        val format = group.getTrackFormat(i)
                        val isSelected = group.isTrackSelected(i)
                        val label = buildTrackLabel(format)
                        audioOptions.add(TrackOption(i, label, format.language, isSelected, group.mediaTrackGroup))
                    }
                }
                C.TRACK_TYPE_TEXT -> {
                    for (i in 0 until group.length) {
                        val format = group.getTrackFormat(i)
                        val isSelected = group.isTrackSelected(i)
                        val label = buildTrackLabel(format)
                        subtitleOptions.add(TrackOption(i, label, format.language, isSelected, group.mediaTrackGroup))
                    }
                }
            }
        }

        val audioTracks = if (audioOptions.isEmpty()) {
            listOf(TrackOption(-1, "Default", null, true))
        } else {
            listOf(TrackOption(-1, "Default", null, true)) + audioOptions
        }

        val subtitleTracks = if (subtitleOptions.isEmpty()) {
            val serverSubs = streams.filter { it.type == StreamType.SUBTITLE }
            if (serverSubs.isNotEmpty()) {
                listOf(TrackOption(-1, "Off", null, true)) + serverSubs.mapIndexed { index, stream ->
                    TrackOption(index, stream.displayTitle ?: stream.language ?: "Unknown", stream.language, false)
                }
            } else {
                listOf(TrackOption(-1, "None", null, true))
            }
        } else {
            listOf(TrackOption(-1, "Off", null, true)) + subtitleOptions
        }

        _uiState.update { it.copy(audioTracks = audioTracks, subtitleTracks = subtitleTracks) }
    }

    private fun updateTracksFromEngine(engine: PlayerEngine) {
        val streams = _uiState.value.mediaStreams
        val audioOptions = engine.audioTracks.map { t ->
            TrackOption(t.index, t.label, t.language, t.isSelected)
        }
        val subtitleOptions = engine.subtitleTracks.map { t ->
            TrackOption(t.index, t.label, t.language, t.isSelected)
        }

        val audioTracks = if (audioOptions.isEmpty()) {
            listOf(TrackOption(-1, "Default", null, true))
        } else {
            listOf(TrackOption(-1, "Default", null, true)) + audioOptions
        }

        val subtitleTracks = if (subtitleOptions.isEmpty()) {
            val serverSubs = streams.filter { it.type == StreamType.SUBTITLE }
            if (serverSubs.isNotEmpty()) {
                listOf(TrackOption(-1, "Off", null, true)) + serverSubs.mapIndexed { index, stream ->
                    TrackOption(index, stream.displayTitle ?: stream.language ?: "Unknown", stream.language, false)
                }
            } else {
                listOf(TrackOption(-1, "None", null, true))
            }
        } else {
            listOf(TrackOption(-1, "Off", null, true)) + subtitleOptions
        }

        _uiState.update { it.copy(audioTracks = audioTracks, subtitleTracks = subtitleTracks) }
    }

    private fun buildTrackLabel(format: Format): String {
        val lang = format.language?.let {
            try {
                java.util.Locale(it).displayLanguage.ifBlank { it }
            } catch (_: Exception) { it }
        }
        val codec = format.sampleMimeType?.let { mimeToName(it) }
        val channels = format.channelCount
        val channelLabel = when (channels) {
            1 -> "Mono"
            2 -> "Stereo"
            6 -> "5.1"
            8 -> "7.1"
            else -> null
        }
        return listOfNotNull(lang, codec, channelLabel).joinToString(" · ").ifBlank { "Unknown" }
    }

    private fun mimeToName(mime: String): String = when {
        mime.startsWith("audio/") -> mime.removePrefix("audio/")
        mime.startsWith("text/") -> mime.removePrefix("text/")
        else -> mime
    }

    private fun buildSubtitleConfigurations(streams: List<MediaStream>): List<MediaItem.SubtitleConfiguration> {
        return streams
            .filter { it.type == StreamType.SUBTITLE }
            .mapNotNull { stream ->
                val mimeType = mapSubtitleCodecToMime(stream.codec) ?: return@mapNotNull null
                val url = if (!stream.deliveryUrl.isNullOrBlank()) {
                    playbackRepository.getSubtitleDeliveryUrl(stream.deliveryUrl!!)
                } else {
                    val itemId = currentItemId ?: return@mapNotNull null
                    val sourceId = _uiState.value.currentMediaSource?.id ?: return@mapNotNull null
                    val ext = when (stream.codec?.lowercase()) {
                        "ass", "ssa" -> "ass"
                        "vtt", "webvtt" -> "vtt"
                        "ttml", "dfxp" -> "ttml"
                        "mov_text" -> "vtt"
                        else -> "srt"
                    }
                    playbackRepository.getSubtitleDeliveryUrl("/Videos/$itemId/$sourceId/Subtitles/${stream.index}/0.$ext")
                }
                MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(url))
                    .setMimeType(mimeType)
                    .setLanguage(stream.language)
                    .setLabel(stream.displayTitle ?: stream.title ?: stream.language ?: "Unknown")
                    .setSelectionFlags(
                        (if (stream.isDefault) C.SELECTION_FLAG_DEFAULT else 0) or
                        (if (stream.isForced) C.SELECTION_FLAG_FORCED else 0)
                    )
                    .build()
            }
    }

    private fun mapSubtitleCodecToMime(codec: String?): String? {
        if (codec == null) return null
        return when (codec.lowercase()) {
            "srt", "subrip" -> MimeTypes.APPLICATION_SUBRIP
            "ass", "ssa" -> MimeTypes.TEXT_SSA
            "vtt", "webvtt" -> MimeTypes.TEXT_VTT
            "ttml", "dfxp" -> MimeTypes.APPLICATION_TTML
            "pgs" -> MimeTypes.APPLICATION_PGS
            "mov_text" -> MimeTypes.TEXT_VTT
            else -> null
        }
    }

    private fun releaseInternals() {
        progressReporter.cancelJobs()
        syncPlayController.reset()
        playerEngine?.release()
        playerEngine = null
        exoPlayer?.removeListener(playerListener)
        mediaSession?.let { sessionManager.clearSession(it) }
        mediaSession?.release()
        mediaSession = null
        exoPlayer?.release()
        exoPlayer = null
        trackSelector = null
        _uiState.update { it.copy(
            introTimestamps = null,
            creditTimestamps = null,
            nextEpisode = null,
            remoteSubtitles = emptyList(),
        ) }
        audioEffects.release()
    }

    fun release() {
        val itemId = currentItemId
        val sessionId = playSessionId
        val engine = playerEngine
        val player = exoPlayer
        val positionTicks = when {
            engine != null -> engine.currentPositionMs * 10_000
            player != null -> player.currentPosition * 10_000
            else -> 0L
        }
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
