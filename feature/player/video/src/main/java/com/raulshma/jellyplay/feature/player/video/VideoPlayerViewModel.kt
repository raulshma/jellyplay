package com.raulshma.jellyplay.feature.player.video

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import com.raulshma.jellyplay.core.data.playback.PlaybackSessionManager
import com.raulshma.jellyplay.core.data.playback.PlayerLifecycleManager
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

import com.raulshma.jellyplay.feature.player.video.trickplay.TrickplayManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    val playerLifecycleManager: PlayerLifecycleManager,
    val videoMiniPlayerState: VideoMiniPlayerState,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VideoPlayerUiState())
    val uiState = _uiState.asStateFlow()

    private val _syncPlayPrefs = MutableStateFlow(com.raulshma.jellyplay.core.model.UserPreferences())
    private val syncPlayPrefs: StateFlow<com.raulshma.jellyplay.core.model.UserPreferences> = _syncPlayPrefs.asStateFlow()

    private val playerSessionManager = PlayerSessionManager(
        context = context,
        scope = viewModelScope,
        mediaRepository = mediaRepository,
        playbackRepository = playbackRepository,
        downloadRepository = downloadRepository,
        preferencesStore = preferencesStore,
        playerLifecycleManager = playerLifecycleManager,
        adaptiveBitrateManager = adaptiveBitrateManager,
    )

    private var mediaDetail: MediaDetail? = null
    
    private var equalizerEnabled: Boolean = false
    private var playSessionId: String = java.util.UUID.randomUUID().toString()
    private var autoplayNext: Boolean = false
    private val trickplayManager = TrickplayManager(playbackRepository)
    private var videoMediaSession: MediaSession? = null

    private val progressReporter = PlaybackProgressReporter(
        playbackRepository = playbackRepository,
        viewModel = this,
        uiState = _uiState,
        getCurrentItemId = { playerSessionManager.sessionState.value.currentItemId },
        getPlaySessionId = { playSessionId },
        getResolvedPlayMethod = { playerSessionManager.sessionState.value.playMethod },
        getMediaEngine = { playerSessionManager.engine },
        onAutoSkipIntro = { skipIntro() },
        onAutoSkipOutro = { skipCredits() },
    )
    private val syncPlayController = SyncPlayController(
        syncPlayManager = syncPlayManager,
        viewModel = this,
        uiState = _uiState,
        getMediaEngine = { playerSessionManager.engine },
        getCurrentItemId = { playerSessionManager.sessionState.value.currentItemId },
        onLoadItem = { itemId, positionTicks ->
            if (playerSessionManager.sessionState.value.currentItemId != itemId) {
                initialize(itemId, null, positionTicks)
            } else {
                playerSessionManager.engine?.seekTo(positionTicks / 10_000)
            }
        },
        preferencesFlow = syncPlayPrefs,
    )

    init {
        viewModelScope.launch {
            preferencesStore.preferences.collect { prefs ->
                _syncPlayPrefs.value = prefs

                if (_uiState.value.subtitleStyle != prefs.subtitleStyle) {
                    _uiState.update { it.copy(subtitleStyle = prefs.subtitleStyle) }
                    playerSessionManager.engine?.let {
                        updateConfigWithUiState()
                    }
                }
            }
        }
        syncPlayController.start()

        // Sync UI state with Session Manager
        viewModelScope.launch {
            playerSessionManager.sessionState.collect { session ->
                _uiState.update { state ->
                    state.copy(
                        title = session.title,
                        subtitle = session.subtitle,
                        currentMediaSource = session.currentMediaSource,
                        mediaStreams = session.mediaStreams,
                        playMethod = session.playMethodString,
                    )
                }
            }
        }

        viewModelScope.launch {
            playerSessionManager.engineFlow.collect { engine ->
                if (engine != null) {
                    _uiState.update { it.copy(
                        engineCapabilities = engine.capabilities,
                        audioDelayMs = preferencesStore.preferences.first().audioDelayMs,
                        decoderMode = preferencesStore.preferences.first().decoderMode,
                        audioPassthrough = preferencesStore.preferences.first().audioPassthrough,
                        subtitleStyle = preferencesStore.preferences.first().subtitleStyle,
                    )}
                    launch { engine.isPlaying.collect { isPlaying ->
                        _uiState.update { s -> s.copy(isPlaying = isPlaying) }
                        syncPlayController.onIsPlayingChanged(isPlaying)
                    } }
                    launch { engine.playbackState.collect { state ->
                        _uiState.update { s -> s.copy(isPlaying = engine.isPlaying.value) }
                        val stateInt = when (state) {
                            com.raulshma.jellyplay.feature.player.video.engine.EnginePlaybackState.IDLE -> 1
                            com.raulshma.jellyplay.feature.player.video.engine.EnginePlaybackState.BUFFERING -> 2
                            com.raulshma.jellyplay.feature.player.video.engine.EnginePlaybackState.READY -> 3
                            com.raulshma.jellyplay.feature.player.video.engine.EnginePlaybackState.ENDED -> 4
                            com.raulshma.jellyplay.feature.player.video.engine.EnginePlaybackState.ERROR -> 1
                        }
                        syncPlayController.onPlaybackStateChanged(stateInt)
                    } }
                    launch { engine.availableTracks.collect { updateTracksFromEngine(engine) } }
                    launch { engine.errorFlow.collect { e -> _uiState.update { s -> s.copy(playerError = e) } } }
                }
            }
        }

        viewModelScope.launch {
            playerLifecycleManager.pipDismissed.collect { dismissed ->
                if (dismissed) {
                    playerSessionManager.engine?.pause()
                }
            }
        }
    }

    val playerEngineRef: com.raulshma.jellyplay.feature.player.video.engine.MediaEngine? get() = playerSessionManager.engine

    @Suppress("DEPRECATION")
    private var pendingSubtitleStreamIndex: Int? = null
    private var pendingAudioStreamIndex: Int? = null

    fun initialize(
        itemId: String,
        mediaSourceId: String?,
        startPositionTicks: Long,
        subtitleStreamIndex: Int? = null,
        audioStreamIndex: Int? = null,
    ) {
        pendingSubtitleStreamIndex = subtitleStreamIndex
        pendingAudioStreamIndex = audioStreamIndex
        val currentItemId = playerSessionManager.sessionState.value.currentItemId
        if (currentItemId == itemId) {
            val engine = playerSessionManager.engine
            val state = engine?.playbackState?.value
            if (state != null && state != com.raulshma.jellyplay.feature.player.video.engine.EnginePlaybackState.ENDED && state != com.raulshma.jellyplay.feature.player.video.engine.EnginePlaybackState.IDLE && state != com.raulshma.jellyplay.feature.player.video.engine.EnginePlaybackState.ERROR) {
                if (startPositionTicks != 0L) return
                val currentPos = engine.currentPositionMs
                if (currentPos > 0) return
            }
        }
        val wasInSyncPlay = syncPlayManager.isInSyncPlaySession

        // Report stop position for the current item before switching
        reportCurrentPlaybackStopped()

        val reclaimed = videoMiniPlayerState.tryReclaimEngine(itemId) as? com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
        if (reclaimed != null) {
            viewModelScope.launch {
                val detailResult = mediaRepository.getMediaDetail(itemId)
                val detail = detailResult.getOrNull()
                if (detail != null) {
                    playerSessionManager.bindReclaimedEngine(reclaimed, itemId, detail)
                    val sessionState = playerSessionManager.sessionState.value
                    createVideoMediaSession(
                        itemId,
                        sessionState.title,
                        sessionState.subtitle,
                    )
                    progressReporter.startPositionTracking()
                    progressReporter.startProgressReporting()
                    fetchIntroTimestamps(itemId)
                    fetchCreditTimestamps(itemId)
                    fetchNextEpisode(detail)
                    loadSeriesEpisodes(detail)
                }
            }
            return
        }

        releaseInternals()
        playSessionId = java.util.UUID.randomUUID().toString()
        trickplayManager.clear()

        if (wasInSyncPlay) {
            syncPlayController.reattachSession()
        }

        viewModelScope.launch {
            val groupPlayingId = syncPlayManager.currentGroup?.playingItemId
            if (syncPlayManager.isInSyncPlaySession && groupPlayingId != null && groupPlayingId != itemId) {
                try {
                    syncPlayManager.setNewQueue(
                        itemIds = listOf(itemId),
                        playingItemId = itemId,
                        mediaSourceId = mediaSourceId,
                        startPositionTicks = startPositionTicks
                    )
                } catch (_: Exception) { }
            }

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
                seekDurationMs = prefs.videoSeekDurationMs,
                defaultOrientation = prefs.videoDefaultOrientation,
                controlsTimeoutMs = prefs.videoControlsTimeoutMs,
                gesturesEnabled = prefs.videoGesturesEnabled,
                defaultSpeed = prefs.videoDefaultSpeed,
                swipeSeekMaxMs = prefs.videoSwipeSeekMaxMs,
                rememberBrightness = prefs.videoRememberBrightness,
                brightnessLevel = prefs.videoBrightnessLevel,
                aspectRatio = defaultAspectRatio,
                trickplayEnabled = prefs.trickplayEnabled,
                trickplayOnSeekGesture = prefs.trickplayOnSeekGesture,
                skipIntroEnabled = prefs.skipIntroEnabled,
                skipOutroEnabled = prefs.skipOutroEnabled,
                autoSkipIntro = prefs.autoSkipIntro,
                autoSkipOutro = prefs.autoSkipOutro,
                videoEpisodeBrowserEnabled = prefs.videoEpisodeBrowserEnabled,
            ) }
            autoplayNext = prefs.videoAutoplayNext

            playerSessionManager.loadMedia(itemId, mediaSourceId, startPositionTicks)

            val sessionState = playerSessionManager.sessionState.value
            val source = sessionState.currentMediaSource
            val detail = sessionState.mediaDetail

            // Create MediaSession for notification / lock screen controls
            createVideoMediaSession(itemId, sessionState.title, sessionState.subtitle)

            if (detail != null) {
                applyMediaDetail(detail)
            }

            source?.trickplayInfo?.let { info ->
                trickplayManager.initialize(itemId, info)
                _uiState.update { it.copy(trickplayInfo = info) }
            }

            playbackRepository.reportPlaybackStart(
                com.raulshma.jellyplay.core.model.PlaybackStartInfo(
                    itemId = itemId,
                    sessionId = playSessionId,
                    mediaSourceId = source?.id,
                    playMethod = sessionState.playMethod,
                )
            )

            progressReporter.startPositionTracking()
            progressReporter.startProgressReporting()
            fetchIntroTimestamps(itemId)
            fetchCreditTimestamps(itemId)
            if (detail != null) {
                fetchNextEpisode(detail)
                loadSeriesEpisodes(detail)
            }
        }
    }

    private fun loadSeriesEpisodes(detail: MediaDetail) {
        val seriesId = detail.item.seriesId ?: return
        val currentSeasonId = detail.item.seasonId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingEpisodes = true) }
            val seasonsResult = mediaRepository.getSeasons(seriesId)
            val seasonList = seasonsResult.getOrElse { emptyList() }
            _uiState.update { it.copy(seriesSeasons = seasonList, currentSeasonId = currentSeasonId) }
            loadSeasonEpisodes(currentSeasonId)
        }
    }

    fun loadSeasonEpisodes(seasonId: String) {
        val seriesId = mediaDetail?.item?.seriesId ?: uiState.value.seriesId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingEpisodes = true) }
            val episodesResult = mediaRepository.getEpisodes(seriesId, seasonId)
            val episodeList = episodesResult.getOrElse { emptyList() }
            _uiState.update { it.copy(
                seasonEpisodes = episodeList,
                currentSeasonId = seasonId,
                isLoadingEpisodes = false,
            ) }
        }
    }

    fun playEpisode(episodeId: String, startPositionTicks: Long = 0L) {
        initialize(episodeId, null, startPositionTicks)
    }

    fun setPlaybackSpeed(speed: Float) {
        _uiState.update { it.copy(playbackSpeed = speed) }
        playerSessionManager.engine?.setPlaybackSpeed(speed)
    }

    fun selectAudioTrack(option: TrackOption) {
        val engine = playerSessionManager.engine ?: return
        engine.selectTrack(com.raulshma.jellyplay.feature.player.video.engine.TrackType.AUDIO, option.index, option.trackGroup)
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
        val engine = playerSessionManager.engine ?: return
        
        // Apply selection to the player engine
        engine.selectTrack(com.raulshma.jellyplay.feature.player.video.engine.TrackType.SUBTITLE, option.index, option.trackGroup)
        
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

    

    fun getCurrentPrimarySubtitleText(): String? {
        val engine = playerSessionManager.engine ?: return null
        val cues = engine.currentCues.value
        if (cues.isEmpty()) return null
        return cues.joinToString("\n").takeIf { it.isNotBlank() }
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
        // We can just update preferences, and let the session manager or viewmodel push it
        // Or we can manually push the update:
        val prefs = _syncPlayPrefs.value
        val config = com.raulshma.jellyplay.feature.player.video.engine.EngineConfig(
            decoderMode = _uiState.value.decoderMode,
            audioPassthrough = _uiState.value.audioPassthrough,
            audioDelayMs = _uiState.value.audioDelayMs,
            subtitleDelayMs = style.offsetMs,
            subtitleStyle = style,
            audioEffects = com.raulshma.jellyplay.feature.player.video.engine.AudioEffectsConfig(
                dialogueBoostEnabled = _uiState.value.dialogueBoostEnabled,
                nightModeEnabled = _uiState.value.nightModeEnabled,
                equalizerEnabled = equalizerEnabled,
                equalizerSettings = prefs.equalizerSettings
            )
        )
        playerSessionManager.engine?.updateConfig(config)
        viewModelScope.launch {
            preferencesStore.setSubtitleStyle(style)
        }
    }

    fun applySubtitleStyleToView(view: android.view.View?) {
        val engine = playerSessionManager.engine ?: return
        if (view != null) engine.applySubtitleStyleToView(view, _uiState.value.subtitleStyle)
    }

    fun toggleDialogueBoost() {
        val newVal = !_uiState.value.dialogueBoostEnabled
        _uiState.update { it.copy(dialogueBoostEnabled = newVal) }
        updateConfigWithUiState()
        viewModelScope.launch {
            preferencesStore.setDialogueBoostEnabled(newVal)
        }
    }

    fun toggleNightMode() {
        val newVal = !_uiState.value.nightModeEnabled
        _uiState.update { it.copy(nightModeEnabled = newVal) }
        updateConfigWithUiState()
        viewModelScope.launch {
            preferencesStore.setNightModeEnabled(newVal)
        }
    }

    fun setAudioDelay(ms: Long) {
        _uiState.update { it.copy(audioDelayMs = ms) }
        updateConfigWithUiState()
        viewModelScope.launch {
            preferencesStore.setAudioDelay(ms)
        }
    }

    fun setDecoderMode(mode: DecoderMode) {
        _uiState.update { it.copy(decoderMode = mode) }
        updateConfigWithUiState()
        viewModelScope.launch {
            preferencesStore.setDecoderMode(mode)
        }
    }

    fun setAudioPassthrough(enabled: Boolean) {
        _uiState.update { it.copy(audioPassthrough = enabled) }
        updateConfigWithUiState()
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
        updateConfigWithUiState()
        viewModelScope.launch {
            preferencesStore.setEqualizerEnabled(equalizerEnabled)
        }
    }

    fun setEqualizerSettings(settings: com.raulshma.jellyplay.core.model.EqualizerSettings) {
        // Will be updated via prefs flow, but let's push config immediately if needed
        viewModelScope.launch {
            preferencesStore.setEqualizerSettings(settings)
            // Wait for pref update to propagate, then update engine config
        }
    }
    
    private fun updateConfigWithUiState() {
        val state = _uiState.value
        val config = com.raulshma.jellyplay.feature.player.video.engine.EngineConfig(
            decoderMode = state.decoderMode,
            audioPassthrough = state.audioPassthrough,
            audioDelayMs = state.audioDelayMs,
            subtitleDelayMs = state.subtitleStyle.offsetMs,
            subtitleStyle = state.subtitleStyle,
            audioEffects = com.raulshma.jellyplay.feature.player.video.engine.AudioEffectsConfig(
                dialogueBoostEnabled = state.dialogueBoostEnabled,
                nightModeEnabled = state.nightModeEnabled,
                equalizerEnabled = equalizerEnabled,
                equalizerSettings = _syncPlayPrefs.value.equalizerSettings
            )
        )
        playerSessionManager.engine?.updateConfig(config)
    }

    fun playNextEpisode() {
        val detail = mediaDetail ?: return
        val seriesId = detail.item.seriesId ?: return
        viewModelScope.launch {
            val episodes = mediaRepository.getEpisodes(seriesId, detail.item.seasonId ?: return@launch)
                .getOrElse { return@launch }
            val currentItemId = playerSessionManager.sessionState.value.currentItemId
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
        val state = _uiState.value
        val ts = state.introTimestamps
        
        // Prefer API timestamps if they are valid
        val endTicks = if (ts != null && ts.hasIntro) {
            ts.introEndTicks
        } else {
            // Fall back to chapter-based segment end
            state.introSegmentEndTicks
        }

        if (endTicks != null && endTicks > 0) {
            val targetMs = endTicks / 10_000
            playerSessionManager.engine?.seekTo(targetMs)
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
            val currentItemId = playerSessionManager.sessionState.value.currentItemId
            val currentIndex = episodes.indexOfFirst { it.id == currentItemId }
            if (currentIndex >= 0 && currentIndex + 1 < episodes.size) {
                _uiState.update { it.copy(nextEpisode = episodes[currentIndex + 1]) }
            } else {
                _uiState.update { it.copy(nextEpisode = null) }
            }
        }
    }

    fun skipCredits() {
        val state = _uiState.value
        
        // If we are in credits and near the end, and autoplay is on, just play next
        if (state.isOutroNearEnd && state.nextEpisode != null && autoplayNext) {
            playNextEpisode()
            return
        }

        val ts = state.creditTimestamps
        // Prefer API timestamps if they are valid
        val endTicks = if (ts != null && ts.hasCredits) {
            ts.creditEndTicks
        } else {
            // Fall back to chapter-based segment end
            state.creditSegmentEndTicks
        }

        if (endTicks != null && endTicks > 0) {
            val targetMs = endTicks / 10_000
            playerSessionManager.engine?.seekTo(targetMs)
        }
    }

    private fun applyMediaDetail(detail: MediaDetail) {
        mediaDetail = detail
        _uiState.update { state ->
            state.copy(
                chapters = detail.chapters,
                seriesId = detail.item.seriesId,
                currentSeasonId = detail.item.seasonId ?: state.currentSeasonId,
            )
        }
    }

    fun getImageUrl(itemId: String, maxWidth: Int = 400): String =
        playbackRepository.getImageUrl(itemId, "Primary", maxWidth)

    fun loadRemoteSubtitles() {
        val itemId = playerSessionManager.sessionState.value.currentItemId ?: return
        _uiState.update { it.copy(isLoadingRemoteSubtitles = true) }
        viewModelScope.launch {
            val subs = playbackRepository.getRemoteSubtitles(itemId).getOrElse { emptyList() }
            _uiState.update { it.copy(remoteSubtitles = subs, isLoadingRemoteSubtitles = false) }
        }
    }

    fun downloadSubtitle(subtitleInfo: com.raulshma.jellyplay.core.model.RemoteSubtitleInfo) {
        val itemId = playerSessionManager.sessionState.value.currentItemId ?: return
        viewModelScope.launch {
            playbackRepository.downloadSubtitle(itemId, subtitleInfo.id)
            val detailResult = mediaRepository.getMediaDetail(itemId)
            detailResult.getOrNull()?.let { detail ->
                applyMediaDetail(detail)
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

    fun syncPlayTogglePlayPause() {
        viewModelScope.launch {
            if (playerSessionManager.engine?.isPlaying?.value == true) {
                playerSessionManager.engine?.pause()
                _uiState.update { it.copy(isPlaying = false) }
                syncPlayManager.sendPause()
            } else {
                syncPlayManager.sendUnpause()
            }
        }
    }

    fun syncPlaySeekTo(positionMs: Long) {
        viewModelScope.launch {
            playerSessionManager.engine?.seekTo(positionMs)
            syncPlayManager.sendSeek(positionMs * 10_000)
        }
    }

    fun syncPlaySetIgnoreWait(ignore: Boolean) {
        syncPlayController.setIgnoreWait(ignore)
    }

    fun syncPlayStop() {
        syncPlayController.sendStop()
    }

    val syncPlayNotifications: kotlinx.coroutines.flow.SharedFlow<String>
        get() = syncPlayController.notifications

    val syncPlayIgnoreWait: kotlinx.coroutines.flow.StateFlow<Boolean>
        get() = syncPlayController.ignoreWait

    val syncPlayChatMessages: kotlinx.coroutines.flow.StateFlow<List<com.raulshma.jellyplay.core.model.SyncPlayChatMessage>>
        get() = syncPlayController.chatMessages

    fun syncPlaySendChatMessage(text: String) {
        syncPlayController.sendChatMessage(text)
    }

    val isCastAvailable: Boolean
        get() = castManager.isCastAvailable

    val isCastConnected: Boolean
        get() = castManager.isConnected

    val isInSyncPlaySession: Boolean
        get() = syncPlayController.isInSession

    fun castToDevice() {
        val state = _uiState.value
        val engine = playerSessionManager.engine ?: return
        val currentItemId = playerSessionManager.sessionState.value.currentItemId

        val url = state.streamUrl ?: return
        val artworkUri = currentItemId?.let {
            try { Uri.parse(playbackRepository.getImageUrl(it, maxWidth = 300)) } catch (_: Exception) { null }
        }
        val mediaItem = MediaItem.Builder()
            .setUri(url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(state.title)
                    .setSubtitle(state.subtitle)
                    .setArtworkUri(artworkUri)
                    .build()
            )
            .build()
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
        return playerSessionManager.engine?.captureViewBitmap()
    }

    fun clearOcrText() {
        _uiState.update { it.copy(ocrText = null) }
    }

    suspend fun getTrickplayThumbnail(positionMs: Long): Bitmap? {
        val state = _uiState.value
        if (!state.trickplayEnabled && !state.trickplayOnSeekGesture) return null
        return trickplayManager.getThumbnail(positionMs)
    }

    private var selectedSubtitleTrackId: Pair<Int, Any?>? = null

    private fun updateTracksFromEngine(engine: com.raulshma.jellyplay.feature.player.video.engine.MediaEngine) {
        val streams = _uiState.value.mediaStreams
        val audioOptions = engine.availableTracks.value.filter { it.type == com.raulshma.jellyplay.feature.player.video.engine.TrackType.AUDIO }.map { t ->
            TrackOption(t.index, t.label, t.language, t.isSelected, trackGroup = t.trackGroup as? androidx.media3.common.TrackGroup)
        }

        val audioTracks = if (audioOptions.isEmpty()) {
            listOf(TrackOption(-1, "Default", null, true))
        } else {
            listOf(TrackOption(-1, "Default", null, true)) + audioOptions
        }

        val engineSubOptions = engine.availableTracks.value.filter { it.type == com.raulshma.jellyplay.feature.player.video.engine.TrackType.SUBTITLE }.map { t ->
            TrackOption(t.index, t.label, t.language, t.isSelected, trackGroup = t.trackGroup as? androidx.media3.common.TrackGroup)
        }

        val subtitleTracks = if (engineSubOptions.isEmpty()) {
            listOf(TrackOption(-1, "None", null, true))
        } else {
            val sel = selectedSubtitleTrackId
            if (sel == null) {
                val engineAutoSelected = engineSubOptions.find { it.isSelected }
                if (engineAutoSelected != null) {
                    selectedSubtitleTrackId = engineAutoSelected.index to engineAutoSelected.trackGroup
                }
            }
            val resolvedSel = selectedSubtitleTrackId
            listOf(TrackOption(-1, "Off", null, resolvedSel == null)) + engineSubOptions.map { t ->
                val isSel = if (resolvedSel != null) {
                    resolvedSel.first == t.index && resolvedSel.second == t.trackGroup
                } else {
                    t.isSelected
                }
                t.copy(isSelected = isSel)
            }
        }

        _uiState.update { it.copy(audioTracks = audioTracks, subtitleTracks = subtitleTracks) }

        // Apply pending audio selection from detail screen
        val pendingAudio = pendingAudioStreamIndex
        if (pendingAudio != null) {
            pendingAudioStreamIndex = null
            val targetStream = streams.firstOrNull {
                it.type == com.raulshma.jellyplay.core.model.StreamType.AUDIO && it.index == pendingAudio
            }
            val matchByIndex = audioTracks.firstOrNull { it.index >= 0 && it.index == pendingAudio }
            val matchByLabel = if (matchByIndex == null && targetStream != null) {
                val targetLabel = targetStream.displayTitle ?: targetStream.title ?: targetStream.language
                audioTracks.firstOrNull { it.index >= 0 && it.label == targetLabel }
            } else null
            (matchByIndex ?: matchByLabel)?.let { selectAudioTrack(it) }
        }

        // Apply pending subtitle selection from detail screen
        val pending = pendingSubtitleStreamIndex
        if (pending != null) {
            pendingSubtitleStreamIndex = null
            val streams = _uiState.value.mediaStreams
            val targetStream = streams.firstOrNull { it.type == com.raulshma.jellyplay.core.model.StreamType.SUBTITLE && it.index == pending }
            if (targetStream != null) {
                val targetLabel = targetStream.displayTitle ?: targetStream.title ?: targetStream.language
                val match = subtitleTracks.firstOrNull { it.index >= 0 && it.label == targetLabel }
                if (match != null) {
                    selectSubtitleTrack(match)
                }
            }
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
            "mov_text" -> MimeTypes.APPLICATION_SUBRIP
            else -> null
        }
    }

    private fun reportCurrentPlaybackStopped() {
        val itemId = playerSessionManager.sessionState.value.currentItemId ?: return
        val sessionId = playSessionId
        val positionTicks = playerSessionManager.engine?.currentPositionMs?.let { it * 10_000 } ?: 0L
        if (positionTicks > 0) {
            viewModelScope.launch {
                playbackRepository.reportPlaybackStopped(itemId, sessionId, positionTicks)
            }
        }
    }

    /**
     * Creates a MediaSession for the video player so that the notification
     * and lock screen controls work during video playback.
     */
    @OptIn(UnstableApi::class)
    private fun createVideoMediaSession(
        itemId: String,
        title: String,
        subtitle: String,
    ) {
        // Release any existing video session
        releaseVideoMediaSession()

        val engine = playerSessionManager.engine ?: return
        val player = engine.underlyingPlayer ?: return

        val session = MediaSession.Builder(context, player)
            .setId("jellyplay_video_${itemId}")
            .build()
        videoMediaSession = session
        sessionManager.setActiveSession(session)
    }

    private fun releaseVideoMediaSession() {
        val session = videoMediaSession ?: return
        // Only clear if this is still the active session.
        // Use try-catch to guard against double-release.
        if (sessionManager.currentSession === session) {
            sessionManager.clearSession(session)
        }
        try { session.release() } catch (_: Exception) { }
        videoMediaSession = null
    }

    private fun releaseInternals() {
        progressReporter.cancelJobs()
        syncPlayController.reset()
        releaseVideoMediaSession()
        playerSessionManager.release()
        playerLifecycleManager.activeCallbacks = null
        trickplayManager.clear()
        selectedSubtitleTrackId = null
        pendingSubtitleStreamIndex = null
        pendingAudioStreamIndex = null
        
        _uiState.update { it.copy(
            introTimestamps = null,
            creditTimestamps = null,
            nextEpisode = null,
            seriesId = null,
            remoteSubtitles = emptyList(),
            chapters = emptyList(),
            seriesSeasons = emptyList(),
            seasonEpisodes = emptyList(),
            currentSeasonId = null,
            isLoadingEpisodes = false,
        ) }
    }

    fun prepareForMiniMode(
        title: String,
        subtitle: String,
    ) {
        val engine = playerSessionManager.engine ?: return
        val itemId = playerSessionManager.sessionState.value.currentItemId ?: return

        videoMiniPlayerState.enterMiniMode(
            engine = engine,
            itemId = itemId,
            mediaSourceId = null,
            title = title,
            subtitle = subtitle,
        )

        playerSessionManager.detachEngine()
        progressReporter.cancelJobs()
        playerLifecycleManager.activeCallbacks = null
        playerLifecycleManager.requestAutoEnterPip(false)
    }

    fun release() {
        val itemId = playerSessionManager.sessionState.value.currentItemId
        val sessionId = playSessionId
        val positionTicks = playerSessionManager.engine?.currentPositionMs?.let { it * 10_000 } ?: 0L
        playerLifecycleManager.requestAutoEnterPip(false)
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
        val itemId = playerSessionManager.sessionState.value.currentItemId
        val sessionId = playSessionId
        val positionTicks = playerSessionManager.engine?.currentPositionMs?.let { it * 10_000 } ?: 0L
        releaseInternals()
        castManager.release()
        if (itemId != null && positionTicks > 0) {
            kotlinx.coroutines.GlobalScope.launch {
                playbackRepository.reportPlaybackStopped(itemId, sessionId, positionTicks)
            }
        }
    }
}
