package com.raulshma.jellyplay.feature.player.video

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import com.raulshma.jellyplay.core.data.playback.PlaybackSessionManager
import com.raulshma.jellyplay.core.data.playback.PlayerLifecycleManager
import com.raulshma.jellyplay.core.data.playback.SleepTimerManager
import com.raulshma.jellyplay.core.data.cast.CastManager
import com.raulshma.jellyplay.core.data.cast.CastSessionEvent
import com.raulshma.jellyplay.core.data.playback.AdaptiveBitrateManager
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayManager
import com.raulshma.jellyplay.core.model.SyncPlayRepeatMode
import com.raulshma.jellyplay.core.model.SyncPlayShuffleMode
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.ChannelMixMode
import com.raulshma.jellyplay.core.model.DecoderMode
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.ReverbPreset
import com.raulshma.jellyplay.core.model.StreamType
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.feature.player.video.components.AspectRatio
import com.raulshma.jellyplay.feature.player.video.engine.MpvPlayerEngine
import com.raulshma.jellyplay.feature.player.video.engine.SubtitleSource
import com.raulshma.jellyplay.feature.player.video.engine.VideoEffectsConfig

import com.raulshma.jellyplay.feature.player.video.trickplay.TrickplayManager
import com.raulshma.jellyplay.core.data.remote.ActivePlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private val offlineRepository: OfflineRepository,
    private val preferencesStore: UserPreferencesStore,
    private val sessionManager: PlaybackSessionManager,
    private val castManager: CastManager,
    private val syncPlayManager: SyncPlayManager,
    private val okHttpClient: OkHttpClient,
    private val adaptiveBitrateManager: AdaptiveBitrateManager,
    private val activePlayerController: ActivePlayerController,
    val playerLifecycleManager: PlayerLifecycleManager,
    val videoMiniPlayerState: VideoMiniPlayerState,
    private val sleepTimerManager: SleepTimerManager,
) : JellyPlayViewModel() {

    private val _uiState = stateFlow(VideoPlayerUiState())
    val uiState: StateFlow<VideoPlayerUiState> = _uiState.flow

    private val _closePlayer = MutableStateFlow(false)
    val closePlayer: StateFlow<Boolean> = _closePlayer.asStateFlow()
    fun clearClosePlayer() { _closePlayer.value = false }

    private val playerSessionManager = PlayerSessionManager(
        context = context,
        scope = scope,
        mediaRepository = mediaRepository,
        playbackRepository = playbackRepository,
        downloadRepository = downloadRepository,
        offlineRepository = offlineRepository,
        preferencesStore = preferencesStore,
        playerLifecycleManager = playerLifecycleManager,
        adaptiveBitrateManager = adaptiveBitrateManager,
    )

    private var mediaDetail: MediaDetail? = null

    private var equalizerEnabled: Boolean = false
    private var playSessionId: String = java.util.UUID.randomUUID().toString()
    private var autoplayNext: Boolean = false
    private var cachedPreferences: com.raulshma.jellyplay.core.model.UserPreferences = com.raulshma.jellyplay.core.model.UserPreferences()
    private val trickplayManager = TrickplayManager(
        playbackRepository = playbackRepository,
        lowRamDevice = run {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
            am?.let { it.isLowRamDevice || it.memoryClass <= 256 } ?: false
        },
    )
    private var videoMediaSession: MediaSession? = null

    val castManagerField: CastManager = castManager

    private val progressReporter = PlaybackProgressReporter(
        playbackRepository = playbackRepository,
        viewModel = this,
        uiState = _uiState,
        getCurrentItemId = { playerSessionManager.sessionState.value.currentItemId },
        getPlaySessionId = { playSessionId },
        getResolvedPlayMethod = { playerSessionManager.sessionState.value.playMethod },
        getMediaEngine = { playerSessionManager.engine },
        onAutoSkip = { segment -> skipSegment(segment) },
        onPlaybackEndedNoNext = { _closePlayer.value = true },
    )
    private val syncPlayBridge = SyncPlayBridge(
        syncPlayManager = syncPlayManager,
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
        scope = scope,
    )

    private var engineCollectionJob: Job? = null

    init {
        launch {
            preferencesStore.preferences.collect { prefs ->
                cachedPreferences = prefs
                if (_uiState.value.subtitleStyle != prefs.subtitleStyle) {
                    _uiState.update { it.copy(subtitleStyle = prefs.subtitleStyle) }
                    playerSessionManager.engine?.let {
                        updateConfigWithUiState()
                    }
                }
                if (_uiState.value.sleepTimerLastUsedDurationMs != prefs.sleepTimerDurationMs) {
                    _uiState.update { it.copy(sleepTimerLastUsedDurationMs = prefs.sleepTimerDurationMs) }
                }
                if (_uiState.value.showPlaybackMetadata != prefs.videoShowPlaybackMetadata) {
                    _uiState.update { it.copy(showPlaybackMetadata = prefs.videoShowPlaybackMetadata) }
                }
            }
        }
        launch {
            sleepTimerManager.remainingMs.collect { remaining ->
                _uiState.update { it.copy(sleepTimerRemainingMs = remaining) }
            }
        }
        syncPlayBridge.start()

        launch {
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

        launch {
            playerSessionManager.engineFlow.collect { engine ->
                engineCollectionJob?.cancel()
                if (engine != null) {
                    val prefs = cachedPreferences
                    _uiState.update { it.copy(
                        engineCapabilities = engine.capabilities,
                        usesSubtitleOverlay = engine is MpvPlayerEngine,
                        currentSubtitleCues = emptyList(),
                        audioDelayMs = prefs.audioDelayMs,
                        decoderMode = prefs.decoderMode,
                        audioPassthrough = prefs.audioPassthrough,
                        subtitleStyle = prefs.subtitleStyle,
                        dialogueBoostEnabled = prefs.dialogueBoostEnabled,
                        dialogueBoostStrength = prefs.dialogueBoostStrength,
                        nightModeEnabled = prefs.nightModeEnabled,
                        nightModeStrength = prefs.nightModeStrength,
                        audioNormalizationMode = prefs.audioNormalizationMode,
                        audioNormalizationEnabled = prefs.audioNormalizationEnabled,
                        channelMixMode = prefs.channelMixMode,
                        channelMixEnabled = prefs.channelMixEnabled,
                    )}
                    updateCastStrategyForEngine(engine)
                    engineCollectionJob = launch {
                        kotlinx.coroutines.coroutineScope {
                            launch { engine.isPlaying.collect { isPlaying ->
                                _uiState.update { s -> s.copy(isPlaying = isPlaying) }
                                syncPlayBridge.onIsPlayingChanged(isPlaying)
                            } }
                            launch { engine.playbackState.collect { state ->
                                val stateInt = when (state) {
                                    com.raulshma.jellyplay.feature.player.video.engine.EnginePlaybackState.IDLE -> 1
                                    com.raulshma.jellyplay.feature.player.video.engine.EnginePlaybackState.BUFFERING -> 2
                                    com.raulshma.jellyplay.feature.player.video.engine.EnginePlaybackState.READY -> 3
                                    com.raulshma.jellyplay.feature.player.video.engine.EnginePlaybackState.ENDED -> 4
                                    com.raulshma.jellyplay.feature.player.video.engine.EnginePlaybackState.ERROR -> 1
                                }
                                syncPlayBridge.onPlaybackStateChanged(stateInt)
                            } }
                            launch { engine.currentCues.collect { cues ->
                                val filteredCues = cues.filter { it.isNotBlank() }
                                _uiState.update { s ->
                                    if (s.currentSubtitleCues == filteredCues) s else s.copy(currentSubtitleCues = filteredCues)
                                }
                            } }
                            launch { engine.availableTracks.collect { updateTracksFromEngine(engine) } }
                            launch { engine.errorFlow.collect { e -> _uiState.update { s -> s.copy(playerError = e, showPlaybackErrorDialog = true) } } }
                        }
                    }
                }
            }
        }

        launch {
            playerLifecycleManager.pipDismissed.collect { dismissed ->
                if (dismissed) {
                    playerSessionManager.engine?.pause()
                }
            }
        }

        launch {
            playerSessionManager.engineFlow.collect { engine ->
                if (engine != null) {
                    activePlayerController.bindEngine(engine)
                } else {
                    activePlayerController.clearEngine()
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
        released = false
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

        reportCurrentPlaybackStopped()

        val reclaimed = videoMiniPlayerState.tryReclaimEngine(itemId) as? com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
        if (reclaimed != null) {
            launch {
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
                    fetchMediaSegments(itemId)
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
            syncPlayBridge.reattachSession()
        }

        launch {
            val currentGroup = syncPlayManager.currentGroup
            val groupPlayingId = currentGroup?.playingItemId
            if (syncPlayManager.isInSyncPlaySession && groupPlayingId != null && groupPlayingId != itemId) {
                try {
                    val matchingEntry = currentGroup.playlistItemMap.entries.find { it.value == itemId }
                    if (matchingEntry != null) {
                        syncPlayManager.syncPlayController.setPlaylistItem(matchingEntry.key)
                    } else {
                        syncPlayManager.syncPlayController.setNewQueue(
                            itemIds = listOf(itemId),
                            playingItemId = itemId,
                            mediaSourceId = mediaSourceId,
                            startPositionTicks = startPositionTicks
                        )
                    }
                } catch (_: Exception) { }
            }

            val prefs = cachedPreferences
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
                segmentBehaviors = prefs.segmentBehaviors,
                videoEpisodeBrowserEnabled = prefs.videoEpisodeBrowserEnabled,
                showPlaybackMetadata = prefs.videoShowPlaybackMetadata,
            ) }
            autoplayNext = prefs.videoAutoplayNext

            playerSessionManager.loadMedia(itemId, mediaSourceId, startPositionTicks)

            val sessionState = playerSessionManager.sessionState.value
            val source = sessionState.currentMediaSource
            val detail = sessionState.mediaDetail

            createVideoMediaSession(itemId, sessionState.title, sessionState.subtitle)

            if (detail != null) {
                applyMediaDetail(detail)
            }

            source?.trickplayInfo?.let { info ->
                val download = downloadRepository.getDownloadByMediaItemId(itemId)
                val downloadPath = download?.downloadPath
                if (downloadPath != null) {
                    val cacheDir = java.io.File(java.io.File(downloadPath).parentFile, "trickplay")
                    trickplayManager.initializeWithCache(itemId, info, cacheDir)
                } else {
                    trickplayManager.initialize(itemId, info)
                }
                _uiState.update { it.copy(trickplayInfo = info) }
            }

            if (source?.trickplayInfo == null) {
                val download = downloadRepository.getDownloadByMediaItemId(itemId)
                val downloadPath = download?.downloadPath
                if (downloadPath != null) {
                    val localInfo = com.raulshma.jellyplay.feature.player.video.trickplay.OfflineTrickplayHelper
                        .loadLocalTrickplayInfo(downloadPath)
                    if (localInfo != null) {
                        val cacheDir = com.raulshma.jellyplay.feature.player.video.trickplay.OfflineTrickplayHelper
                            .getLocalTrickplayDir(downloadPath)
                        if (cacheDir != null) {
                            trickplayManager.initializeLocal(itemId, localInfo, cacheDir)
                            _uiState.update { it.copy(trickplayInfo = localInfo) }
                        }
                    }
                }
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
            fetchMediaSegments(itemId)
            if (detail != null) {
                kotlinx.coroutines.coroutineScope {
                    launch { fetchNextEpisode(detail) }
                    launch { loadSeriesEpisodes(detail) }
                }
            }
        }
    }

    private fun loadSeriesEpisodes(detail: MediaDetail) {
        val seriesId = detail.item.seriesId ?: return
        val currentSeasonId = detail.item.seasonId ?: return
        launch {
            _uiState.update { it.copy(isLoadingEpisodes = true) }
            val seasonsResult = mediaRepository.getSeasons(seriesId)
            val seasonList = seasonsResult.getOrElse { emptyList() }
            _uiState.update { it.copy(seriesSeasons = seasonList, currentSeasonId = currentSeasonId) }
            loadSeasonEpisodes(currentSeasonId)
        }
    }

    fun loadSeasonEpisodes(seasonId: String) {
        val seriesId = mediaDetail?.item?.seriesId ?: uiState.value.seriesId ?: return
        launch {
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

    fun setScreenLocked(locked: Boolean) {
        _uiState.update { it.copy(isScreenLocked = locked) }
    }

    fun setPlaybackSpeed(speed: Float) {
        _uiState.update { it.copy(playbackSpeed = speed) }
        playerSessionManager.engine?.setPlaybackSpeed(speed)
    }

    fun selectAudioTrack(option: TrackOption) {
        val engine = playerSessionManager.engine ?: return
        engine.selectTrack(com.raulshma.jellyplay.core.model.TrackType.AUDIO, option.index, option.trackGroup)
        if (option.index < 0) {
            selectedAudioTrackId = null
        } else {
            selectedAudioTrackId = option.index to option.trackGroup
        }
        _uiState.update { state ->
            val isDefault = option.index < 0
            state.copy(audioTracks = state.audioTracks.map { track ->
                val matches = track.index == option.index && track.trackGroup == option.trackGroup
                val isDefaultTrack = track.index < 0
                track.copy(isSelected = if (isDefault) isDefaultTrack else matches)
            })
        }
        persistStreamSelectionFromPlayer(
            audioTrackOption = option,
            subtitleTrackOption = null,
        )
    }

    fun selectSubtitleTrack(option: TrackOption) {
        val engine = playerSessionManager.engine ?: return

        engine.selectTrack(com.raulshma.jellyplay.core.model.TrackType.SUBTITLE, option.index, option.trackGroup)

        if (option.index < 0) {
            selectedSubtitleTrackId = null
        } else {
            selectedSubtitleTrackId = option.index to option.trackGroup
        }

        _uiState.update { state ->
            val isOff = option.index < 0
            state.copy(subtitleTracks = state.subtitleTracks.map { track ->
                val matches = track.index == option.index && track.trackGroup == option.trackGroup
                val isOffTrack = track.index < 0
                track.copy(isSelected = if (isOff) isOffTrack else matches)
            })
        }
        persistStreamSelectionFromPlayer(
            audioTrackOption = null,
            subtitleTrackOption = option,
        )
    }

    private fun persistStreamSelectionFromPlayer(
        audioTrackOption: TrackOption?,
        subtitleTrackOption: TrackOption?,
    ) {
        val itemId = playerSessionManager.sessionState.value.currentItemId ?: return
        val streams = _uiState.value.mediaStreams
        val currentSelection = preferencesStore.preferences.value.mediaStreamSelections[itemId]
        val audioStreamIndex = if (audioTrackOption != null) {
            if (audioTrackOption.index < 0) null
            else resolveMediaStreamIndex(streams, StreamType.AUDIO, audioTrackOption.label)
        } else {
            currentSelection?.audioStreamIndex
        }
        val subtitleStreamIndex = if (subtitleTrackOption != null) {
            if (subtitleTrackOption.index < 0) null
            else resolveMediaStreamIndex(streams, StreamType.SUBTITLE, subtitleTrackOption.label)
        } else {
            currentSelection?.subtitleStreamIndex
        }
        launch {
            preferencesStore.setMediaStreamSelection(
                itemId = itemId,
                audioStreamIndex = audioStreamIndex,
                subtitleStreamIndex = subtitleStreamIndex,
            )
        }
    }

    private fun resolveMediaStreamIndex(
        streams: List<MediaStream>,
        type: StreamType,
        trackLabel: String?,
    ): Int? {
        if (trackLabel == null) return null
        val typedStreams = streams.filter { it.type == type }
        return typedStreams.firstOrNull {
            it.displayTitle == trackLabel || it.title == trackLabel || it.language == trackLabel
        }?.index ?: typedStreams.firstOrNull { it.index >= 0 }?.index
    }

    fun getCurrentPrimarySubtitleText(): String? {
        val engine = playerSessionManager.engine ?: return null
        val cues = engine.currentCues.value
        if (cues.isEmpty()) return null
        return cues.firstOrNull() ?: return null
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
        updateConfigWithUiState()
        launch {
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
        launch {
            preferencesStore.setDialogueBoostEnabled(newVal)
        }
    }

    fun setDialogueBoostStrength(strength: com.raulshma.jellyplay.core.model.EffectStrength) {
        _uiState.update { it.copy(dialogueBoostStrength = strength) }
        updateConfigWithUiState()
        launch {
            preferencesStore.setDialogueBoostStrength(strength)
        }
    }

    fun toggleNightMode() {
        val newVal = !_uiState.value.nightModeEnabled
        _uiState.update { it.copy(nightModeEnabled = newVal) }
        updateConfigWithUiState()
        launch {
            preferencesStore.setNightModeEnabled(newVal)
        }
    }

    fun setNightModeStrength(strength: com.raulshma.jellyplay.core.model.EffectStrength) {
        _uiState.update { it.copy(nightModeStrength = strength) }
        updateConfigWithUiState()
        launch {
            preferencesStore.setNightModeStrength(strength)
        }
    }

    fun setAudioDelay(ms: Long) {
        _uiState.update { it.copy(audioDelayMs = ms) }
        updateConfigWithUiState()
        launch {
            preferencesStore.setAudioDelay(ms)
        }
    }

    fun setDecoderMode(mode: DecoderMode) {
        _uiState.update { it.copy(decoderMode = mode) }
        updateConfigWithUiState()
        launch {
            preferencesStore.setDecoderMode(mode)
        }
    }

    fun setStreamingQuality(quality: StreamingQuality) {
        _uiState.update { it.copy(streamingQuality = quality) }
        val maxBitrate = adaptiveBitrateManager.resolveMaxBitrate(quality)?.toInt()
        playerSessionManager.engine?.setMaxVideoBitrate(maxBitrate)
    }

    fun retryWithEngine(playerType: PlayerType) {
        val currentPos = playerSessionManager.engine?.currentPositionMs ?: 0L
        val currentSpeed = _uiState.value.playbackSpeed
        val currentQuality = _uiState.value.streamingQuality
        val maxBitrate = adaptiveBitrateManager.resolveMaxBitrate(currentQuality)?.toInt()
        progressReporter.cancelJobs()
        releaseVideoMediaSession()
        _uiState.update {
            it.copy(
                showPlaybackErrorDialog = false,
                playerError = null,
                preferredPlayerType = playerType,
            )
        }
        launch {
            preferencesStore.setPreferredPlayer(playerType)
            playerSessionManager.reloadWithEngine(playerType, currentPos, currentSpeed, maxBitrate)
            val sessionState = playerSessionManager.sessionState.value
            createVideoMediaSession(
                sessionState.currentItemId ?: "",
                sessionState.title,
                sessionState.subtitle,
            )
            progressReporter.startPositionTracking()
            progressReporter.startProgressReporting()
        }
    }

    fun dismissPlaybackError() {
        _uiState.update { it.copy(showPlaybackErrorDialog = false, playerError = null) }
    }

    fun setAudioPassthrough(enabled: Boolean) {
        _uiState.update { it.copy(audioPassthrough = enabled) }
        updateConfigWithUiState()
        launch {
            preferencesStore.setAudioPassthrough(enabled)
        }
    }

    fun setFrameRateMatching(enabled: Boolean) {
        _uiState.update { it.copy(frameRateMatching = enabled) }
        launch {
            preferencesStore.setFrameRateMatching(enabled)
        }
    }

    fun toggleEqualizer() {
        equalizerEnabled = !equalizerEnabled
        updateConfigWithUiState()
        launch {
            preferencesStore.setEqualizerEnabled(equalizerEnabled)
        }
    }

    fun setEqualizerSettings(settings: com.raulshma.jellyplay.core.model.EqualizerSettings) {
        launch {
            preferencesStore.setEqualizerSettings(settings)
        }
    }

    fun setAudioNormalizationMode(mode: AudioNormalizationMode) {
        _uiState.update { it.copy(audioNormalizationMode = mode, audioNormalizationEnabled = mode != AudioNormalizationMode.NONE) }
        updateConfigWithUiState()
        launch {
            preferencesStore.setAudioNormalizationMode(mode)
            preferencesStore.setAudioNormalizationEnabled(mode != AudioNormalizationMode.NONE)
        }
    }

    fun toggleAudioNormalization() {
        val newVal = !_uiState.value.audioNormalizationEnabled
        _uiState.update { it.copy(audioNormalizationEnabled = newVal) }
        updateConfigWithUiState()
        launch {
            preferencesStore.setAudioNormalizationEnabled(newVal)
        }
    }

    fun setChannelMixMode(mode: ChannelMixMode) {
        _uiState.update { it.copy(channelMixMode = mode, channelMixEnabled = mode != ChannelMixMode.AUTO) }
        updateConfigWithUiState()
        launch {
            preferencesStore.setChannelMixMode(mode)
            preferencesStore.setChannelMixEnabled(mode != ChannelMixMode.AUTO)
        }
    }

    fun toggleChannelMix() {
        val newVal = !_uiState.value.channelMixEnabled
        _uiState.update { it.copy(channelMixEnabled = newVal) }
        updateConfigWithUiState()
        launch {
            preferencesStore.setChannelMixEnabled(newVal)
        }
    }

    fun toggleBassBoost() {
        val newVal = !_uiState.value.bassBoostEnabled
        _uiState.update { it.copy(bassBoostEnabled = newVal) }
        updateConfigWithUiState()
        launch {
            preferencesStore.setBassBoostEnabled(newVal)
        }
    }

    fun setBassBoostStrength(strength: EffectStrength) {
        _uiState.update { it.copy(bassBoostStrength = strength) }
        updateConfigWithUiState()
        launch {
            preferencesStore.setBassBoostStrength(strength)
        }
    }

    fun toggleVirtualizer() {
        val newVal = !_uiState.value.virtualizerEnabled
        _uiState.update { it.copy(virtualizerEnabled = newVal) }
        updateConfigWithUiState()
        launch {
            preferencesStore.setVirtualizerEnabled(newVal)
        }
    }

    fun setVirtualizerStrength(strength: Int) {
        _uiState.update { it.copy(virtualizerStrength = strength) }
        updateConfigWithUiState()
        launch {
            preferencesStore.setVirtualizerStrength(strength)
        }
    }

    fun setReverbPreset(preset: ReverbPreset) {
        _uiState.update { it.copy(reverbPreset = preset) }
        updateConfigWithUiState()
        launch {
            preferencesStore.setReverbPreset(preset)
        }
    }

    fun setVideoEffects(effects: VideoEffectsConfig) {
        _uiState.update { it.copy(videoEffects = effects) }
        updateConfigWithUiStateDebounced()
    }

    private fun updateConfigWithUiState() {
        val state = _uiState.value
        val config = com.raulshma.jellyplay.feature.player.video.engine.EngineConfig(
            decoderMode = state.decoderMode,
            audioPassthrough = state.audioPassthrough,
            audioDelayMs = state.audioDelayMs,
            subtitleDelayMs = state.subtitleStyle.offsetMs,
            subtitleStyle = state.subtitleStyle,
            videoEffects = state.videoEffects,
            audioEffects = com.raulshma.jellyplay.feature.player.video.engine.AudioEffectsConfig(
                dialogueBoostEnabled = state.dialogueBoostEnabled,
                dialogueBoostStrength = state.dialogueBoostStrength,
                nightModeEnabled = state.nightModeEnabled,
                nightModeStrength = state.nightModeStrength,
                equalizerEnabled = equalizerEnabled,
                equalizerSettings = cachedPreferences.equalizerSettings,
                audioNormalizationMode = state.audioNormalizationMode,
                audioNormalizationEnabled = state.audioNormalizationEnabled,
                channelMixMode = state.channelMixMode,
                channelMixEnabled = state.channelMixEnabled,
                bassBoostEnabled = state.bassBoostEnabled,
                bassBoostStrength = state.bassBoostStrength,
                virtualizerEnabled = state.virtualizerEnabled,
                virtualizerStrength = state.virtualizerStrength,
                reverbPreset = state.reverbPreset,
            )
        )
        playerSessionManager.engine?.updateConfig(config)
    }

    private var configDebounceJob: Job? = null

    private fun updateConfigWithUiStateDebounced() {
        configDebounceJob?.cancel()
        configDebounceJob = launch {
            delay(50)
            updateConfigWithUiState()
        }
    }

    fun playNextEpisode() {
        val detail = mediaDetail ?: return
        val seriesId = detail.item.seriesId ?: return
        launch {
            val episodes = mediaRepository.getEpisodes(seriesId, detail.item.seasonId ?: return@launch)
                .getOrElse { return@launch }
            val currentItemId = playerSessionManager.sessionState.value.currentItemId
            val currentIndex = episodes.indexOfFirst { it.id == currentItemId }
            if (currentIndex < 0 || currentIndex + 1 >= episodes.size) return@launch
            val next = episodes[currentIndex + 1]

            if (syncPlayManager.isInSyncPlaySession) {
                val group = syncPlayManager.currentGroup
                val currentPlaylistItemId = group?.playingPlaylistItemId
                val nextExistsInQueue = group?.playlistItemMap?.values?.contains(next.id) == true
                if (currentPlaylistItemId != null && nextExistsInQueue) {
                    syncPlayBridge.sendNextItem(currentPlaylistItemId)
                    return@launch
                }
            }

            initialize(next.id, null, 0L)
        }
    }

    fun setSyncPlayRepeatMode(mode: SyncPlayRepeatMode) {
        launch {
            syncPlayManager.syncPlayController.setRepeatMode(mode)
        }
    }

    fun setSyncPlayShuffleMode(mode: SyncPlayShuffleMode) {
        launch {
            syncPlayManager.syncPlayController.setShuffleMode(mode)
        }
    }

    fun saveBrightness(level: Float) {
        _uiState.update { it.copy(brightnessLevel = level) }
        if (_uiState.value.rememberBrightness) {
            launch {
                preferencesStore.setVideoBrightnessLevel(level)
            }
        }
    }

    fun skipIntro() {
        val state = _uiState.value
        val seg = state.activeSegment
        if (seg != null && seg.type == com.raulshma.jellyplay.core.model.MediaSegmentType.INTRO) {
            skipSegment(seg)
            return
        }
        val endTicks = state.introSegmentEndTicks
        if (endTicks != null && endTicks > 0) {
            playerSessionManager.engine?.seekTo(endTicks / 10_000)
        }
    }

    private fun fetchMediaSegments(itemId: String) {
        launch {
            val segments = playbackRepository.getMediaSegments(itemId).getOrDefault(emptyList())
            _uiState.update { it.copy(segments = segments) }
        }
    }

    private fun fetchNextEpisode(currentDetail: MediaDetail) {
        val seriesId = currentDetail.item.seriesId ?: return
        val seasonId = currentDetail.item.seasonId ?: return
        launch {
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

        if (state.isOutroNearEnd && state.nextEpisode != null && autoplayNext) {
            playNextEpisode()
            return
        }

        val seg = state.activeSegment
        if (seg != null && seg.type == com.raulshma.jellyplay.core.model.MediaSegmentType.OUTRO) {
            skipSegment(seg)
            return
        }
        val endTicks = state.creditSegmentEndTicks
        if (endTicks != null && endTicks > 0) {
            playerSessionManager.engine?.seekTo(endTicks / 10_000)
        }
    }

    fun skipSegment(segment: com.raulshma.jellyplay.core.model.MediaSegment) {
        val endTicks = _uiState.value.segmentEndTicks(segment)
        if (endTicks != null && endTicks > 0) {
            playerSessionManager.engine?.seekTo(endTicks / 10_000)
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
        launch {
            val subs = playbackRepository.getRemoteSubtitles(itemId).getOrElse { emptyList() }
            _uiState.update { it.copy(remoteSubtitles = subs, isLoadingRemoteSubtitles = false) }
        }
    }

    fun downloadSubtitle(subtitleInfo: com.raulshma.jellyplay.core.model.RemoteSubtitleInfo) {
        val itemId = playerSessionManager.sessionState.value.currentItemId ?: return
        launch {
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

    fun addLocalSubtitle(uri: Uri, fileName: String) {
        val engine = playerSessionManager.engine ?: return
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val codec = when (ext) {
            "srt" -> "srt"
            "ass", "ssa" -> "ass"
            "vtt" -> "vtt"
            "ttml", "dfxp" -> "ttml"
            else -> null
        }

        val label = fileName.substringBeforeLast('.').ifBlank { "Local subtitle" }
        val source = SubtitleSource(
            url = uri.toString(),
            label = label,
            language = null,
            mimeType = null,
            codec = codec,
            isDefault = false,
            isForced = false,
            id = "local:${System.currentTimeMillis()}",
        )
        playerSessionManager.addExternalSubtitle(source)
    }

    fun joinSyncPlay(groupId: String) {
        syncPlayBridge.joinGroup(groupId)
    }

    fun leaveSyncPlay() {
        syncPlayBridge.leaveGroup()
    }

    fun syncPlayTogglePlayPause() {
        syncPlayBridge.togglePlayPause()
    }

    fun syncPlaySeekTo(positionMs: Long) {
        syncPlayBridge.seekTo(positionMs)
    }

    fun syncPlaySetIgnoreWait(ignore: Boolean) {
        syncPlayBridge.setIgnoreWait(ignore)
    }

    fun syncPlayStop() {
        syncPlayBridge.sendStop()
    }

    val syncPlayNotifications: SharedFlow<String>
        get() = syncPlayBridge.notifications

    val syncPlayIgnoreWait: StateFlow<Boolean>
        get() = syncPlayBridge.ignoreWait

    val isCastAvailable: Boolean
        get() = castManager.isCastAvailable

    val isCastConnected: Boolean
        get() = castManager.isConnected

    val castPositionMs: StateFlow<Long>
        get() = castManager.castPositionMs

    val castDurationMs: StateFlow<Long>
        get() = castManager.castDurationMs

    val castIsPlaying: StateFlow<Boolean>
        get() = castManager.castIsPlaying

    val castVolumeFlow: StateFlow<Float>
        get() = castManager.castVolume

    val isConnectedFlow: StateFlow<Boolean>
        get() = castManager.isConnectedFlow

    val isConnectingFlow: StateFlow<Boolean>
        get() = castManager.isConnectingFlow

    val castSessionEvents: SharedFlow<CastSessionEvent>
        get() = castManager.sessionEvents

    val isInSyncPlaySession: Boolean
        get() = syncPlayBridge.isInSession

    fun castToDevice() {
        val engine = playerSessionManager.engine ?: return

        val sessionState = playerSessionManager.sessionState.value
        val currentItemId = sessionState.currentItemId ?: return

        val positionMs = engine.currentPositionMs
        val startTimeTicks = positionMs * 10_000
        val sourceId = sessionState.currentMediaSource?.id ?: ""
        val url = playbackRepository.getStreamUrl(currentItemId, sourceId, startTimeTicks)
        if (url.isBlank()) return

        val artworkUri = try {
            Uri.parse(playbackRepository.getImageUrl(currentItemId, maxWidth = 300))
        } catch (_: Exception) { null }

        val subtitleConfigs = buildCastSubtitleConfigurations(
            itemId = currentItemId,
            mediaSourceId = sourceId,
            mediaStreams = sessionState.mediaStreams,
        )

        val mediaItem = MediaItem.Builder()
            .setUri(url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(sessionState.title)
                    .setSubtitle(sessionState.subtitle)
                    .setArtworkUri(artworkUri)
                    .build()
            )
            .setSubtitleConfigurations(subtitleConfigs)
            .build()
        castManager.loadMedia(mediaItem, positionMs, object : Player.Listener {})
        engine.pause()
    }

    private fun buildCastSubtitleConfigurations(
        itemId: String,
        mediaSourceId: String,
        mediaStreams: List<MediaStream>,
    ): List<MediaItem.SubtitleConfiguration> {
        return mediaStreams
            .filter { it.type == StreamType.SUBTITLE }
            .mapNotNull { stream ->
                val subUrl = when {
                    !stream.deliveryUrl.isNullOrBlank() ->
                        playbackRepository.getSubtitleDeliveryUrl(stream.deliveryUrl!!)
                    stream.isExternal ->
                        playbackRepository.buildSubtitleDeliveryUrl(
                            itemId, mediaSourceId, stream.index, "vtt",
                        )
                    else -> null
                }
                if (subUrl.isNullOrBlank()) return@mapNotNull null

                val mimeType = when ((stream.codec ?: "").lowercase()) {
                    "vtt", "webvtt" -> MimeTypes.TEXT_VTT
                    "srt", "subrip" -> MimeTypes.APPLICATION_SUBRIP
                    "ttml", "dfxp", "tt" -> MimeTypes.APPLICATION_TTML
                    "ssa", "ass" -> MimeTypes.TEXT_SSA
                    else -> MimeTypes.TEXT_VTT
                }

                MediaItem.SubtitleConfiguration.Builder(Uri.parse(subUrl))
                    .setMimeType(mimeType)
                    .setLabel(stream.displayTitle ?: stream.title ?: stream.language)
                    .setLanguage(stream.language)
                    .build()
            }
    }

    fun setCastVolume(volume: Float) {
        castManager.setVolume(volume)
    }

    fun onCastDisconnected() {
        val engine = playerSessionManager.engine ?: return
        if (!engine.isPlaying.value) {
            engine.play()
        }
    }

    fun castPlay() {
        castManager.play()
    }

    fun castPause() {
        castManager.pause()
    }

    fun castSeekTo(positionMs: Long) {
        castManager.seekTo(positionMs)
    }

    private fun updateCastStrategyForEngine(engine: com.raulshma.jellyplay.feature.player.video.engine.MediaEngine) {
        castManager.setActiveStrategy(CastManager.STRATEGY_GOOGLE)
    }

    @OptIn(UnstableApi::class)
    fun detachForBackgroundCast() {
        castManager.markBackgroundCasting(true)
        castManager.softRelease()

        val castPlayer = castManager.castPlayerForSession
        if (castPlayer != null) {
            releaseVideoMediaSession()
            val session = MediaSession.Builder(context, castPlayer)
                .setId("jellyplay_cast_bg")
                .build()
            videoMediaSession = session
            sessionManager.setActiveSession(session)
        }
    }

    @OptIn(UnstableApi::class)
    fun reattachFromBackgroundCast() {
        if (!castManager.isBackgroundCasting) return
        castManager.markBackgroundCasting(false)

        val engine = playerSessionManager.engine
        if (engine != null) {
            val sessionState = playerSessionManager.sessionState.value
            val itemId = sessionState.currentItemId ?: return
            releaseVideoMediaSession()
            val player = engine.underlyingPlayer ?: return
            val session = MediaSession.Builder(context, player)
                .setId("jellyplay_video_$itemId")
                .build()
            videoMediaSession = session
            sessionManager.setActiveSession(session)
        }
    }

    val isBackgroundCasting: Boolean
        get() = castManager.isBackgroundCasting

    fun captureOcrSubtitle(bitmap: android.graphics.Bitmap?) {
        if (_uiState.value.isOcrRunning) return
        if (bitmap == null) {
            _uiState.update { it.copy(ocrText = null) }
            return
        }
        _uiState.update { it.copy(isOcrRunning = true) }
        launch {
            try {
                val text = withContext(Dispatchers.Default) {
                    com.raulshma.jellyplay.feature.player.video.subtitle.SubtitleOcrHelper
                        .extractSubtitleTextFromFrame(bitmap)
                }
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

    fun toggleVideoStats() {
        val newValue = !_uiState.value.showVideoStats
        _uiState.update { it.copy(showVideoStats = newValue) }
        playerSessionManager.engine?.setVideoStatsEnabled(newValue)
    }

    fun setControlsVisible(visible: Boolean) {
        playerSessionManager.engine?.setPollingIntervalMs(if (visible) 250L else 1000L)
    }

    suspend fun getTrickplayThumbnail(positionMs: Long): Bitmap? {
        val state = _uiState.value
        if (!state.trickplayEnabled && !state.trickplayOnSeekGesture) return null
        return trickplayManager.getThumbnail(positionMs)
    }

    private var selectedSubtitleTrackId: Pair<Int, Any?>? = null
    private var selectedAudioTrackId: Pair<Int, Any?>? = null

    private fun updateTracksFromEngine(engine: com.raulshma.jellyplay.feature.player.video.engine.MediaEngine) {
        val streams = _uiState.value.mediaStreams
        val rawTracks = engine.availableTracks.value

        val rawAudioTracks = rawTracks.filter { it.type == com.raulshma.jellyplay.core.model.TrackType.AUDIO }
        val prevAudioSel = selectedAudioTrackId
        if (prevAudioSel != null) {
            val targetTrack = rawAudioTracks.find { it.index == prevAudioSel.first && it.trackGroup == prevAudioSel.second }
            if (targetTrack != null && !targetTrack.isSelected) {
                engine.selectTrack(com.raulshma.jellyplay.core.model.TrackType.AUDIO, targetTrack.index, targetTrack.trackGroup)
                return
            }
        }

        val rawSubTracks = rawTracks.filter { it.type == com.raulshma.jellyplay.core.model.TrackType.SUBTITLE }
        val prevSubSel = selectedSubtitleTrackId
        if (prevSubSel != null) {
            val targetTrack = rawSubTracks.find { it.index == prevSubSel.first && it.trackGroup == prevSubSel.second }
            if (targetTrack != null && !targetTrack.isSelected) {
                engine.selectTrack(com.raulshma.jellyplay.core.model.TrackType.SUBTITLE, targetTrack.index, targetTrack.trackGroup)
                return
            }
        }

        val audioOptions = rawAudioTracks.map { t ->
            TrackOption(t.index, t.label, t.language, t.isSelected, trackGroup = t.trackGroup as? androidx.media3.common.TrackGroup)
        }

        val audioTracks = if (audioOptions.isEmpty()) {
            listOf(TrackOption(-1, "Default", null, true))
        } else {
            val sel = selectedAudioTrackId
            val hasSelectionMatch = audioOptions.any { sel != null && sel.first == it.index && sel.second == it.trackGroup }
            val resolvedSel = if (hasSelectionMatch) sel else {
                val engineAutoSelected = audioOptions.find { it.isSelected }
                if (engineAutoSelected != null) {
                    selectedAudioTrackId = engineAutoSelected.index to engineAutoSelected.trackGroup
                    selectedAudioTrackId
                } else null
            }
            listOf(TrackOption(-1, "Default", null, resolvedSel == null)) + audioOptions.map { t ->
                val isSel = if (resolvedSel != null) {
                    resolvedSel.first == t.index && resolvedSel.second == t.trackGroup
                } else {
                    t.isSelected
                }
                t.copy(isSelected = isSel)
            }
        }

        val engineSubOptions = rawSubTracks.map { t ->
            TrackOption(t.index, t.label, t.language, t.isSelected, trackGroup = t.trackGroup as? androidx.media3.common.TrackGroup)
        }

        val subtitleTracks = if (engineSubOptions.isEmpty()) {
            listOf(TrackOption(-1, "None", null, true))
        } else {
            val sel = selectedSubtitleTrackId
            val hasSelectionMatch = engineSubOptions.any { sel != null && sel.first == it.index && sel.second == it.trackGroup }
            val resolvedSel = if (hasSelectionMatch) sel else {
                val engineAutoSelected = engineSubOptions.find { it.isSelected }
                if (engineAutoSelected != null) {
                    selectedSubtitleTrackId = engineAutoSelected.index to engineAutoSelected.trackGroup
                    selectedSubtitleTrackId
                } else null
            }
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
        } else {
            val itemId = playerSessionManager.sessionState.value.currentItemId
            if (itemId != null) {
                val stored = preferencesStore.preferences.value.mediaStreamSelections[itemId]
                val audioIdx = stored?.audioStreamIndex
                val prefLang = preferencesStore.preferences.value.preferredAudioLanguage
                if (audioIdx != null) {
                    val targetStream = streams.firstOrNull {
                        it.type == com.raulshma.jellyplay.core.model.StreamType.AUDIO && it.index == audioIdx
                    }
                    val targetLabel = targetStream?.displayTitle ?: targetStream?.title ?: targetStream?.language
                    if (targetLabel != null) {
                        audioTracks.firstOrNull { it.index >= 0 && it.label == targetLabel }?.let { selectAudioTrack(it) }
                    }
                } else if (prefLang != null) {
                    audioTracks.firstOrNull { it.index >= 0 && it.language.equals(prefLang, ignoreCase = true) }?.let { selectAudioTrack(it) }
                }
            }
        }

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
        } else {
            val itemId = playerSessionManager.sessionState.value.currentItemId
            if (itemId != null) {
                val stored = preferencesStore.preferences.value.mediaStreamSelections[itemId]
                val subIdx = stored?.subtitleStreamIndex
                val prefLang = preferencesStore.preferences.value.preferredSubtitleLanguage
                if (subIdx != null) {
                    val targetStream = streams.firstOrNull {
                        it.type == com.raulshma.jellyplay.core.model.StreamType.SUBTITLE && it.index == subIdx
                    }
                    val targetLabel = targetStream?.displayTitle ?: targetStream?.title ?: targetStream?.language
                    if (targetLabel != null) {
                        subtitleTracks.firstOrNull { it.index >= 0 && it.label == targetLabel }?.let { selectSubtitleTrack(it) }
                    }
                } else if (prefLang != null) {
                    subtitleTracks.firstOrNull { it.index >= 0 && it.language.equals(prefLang, ignoreCase = true) }?.let { selectSubtitleTrack(it) }
                }
            }
        }
    }

    private fun reportCurrentPlaybackStopped() {
        val itemId = playerSessionManager.sessionState.value.currentItemId ?: return
        val sessionId = playSessionId
        val positionTicks = playerSessionManager.engine?.currentPositionMs?.let { it * 10_000 } ?: 0L
        if (positionTicks > 0) {
            launch {
                playbackRepository.reportPlaybackStopped(itemId, sessionId, positionTicks)
            }
        }
    }

    @OptIn(UnstableApi::class)
    private fun createVideoMediaSession(
        itemId: String,
        title: String,
        subtitle: String,
    ) {
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
        if (sessionManager.currentSession === session) {
            sessionManager.clearSession(session)
        }
        try { session.release() } catch (_: Exception) { }
        videoMediaSession = null
    }

    private fun releaseInternals() {
        progressReporter.cancelJobs()
        syncPlayBridge.reset()
        releaseVideoMediaSession()
        playerSessionManager.release()
        playerLifecycleManager.activeCallbacks = null
        trickplayManager.clear()
        selectedSubtitleTrackId = null
        pendingSubtitleStreamIndex = null
        pendingAudioStreamIndex = null

        _uiState.update { it.copy(
            segments = emptyList(),
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

    fun startSleepTimer(durationMs: Long) {
        launch {
            preferencesStore.setSleepTimerDurationMs(durationMs)
            preferencesStore.setSleepTimerEndOfEpisode(false)
        }
        sleepTimerManager.setOnTimerExpired {
            playerSessionManager.engine?.pause()
        }
        sleepTimerManager.start(durationMs)
        _uiState.update { it.copy(
            sleepTimerActive = true,
            sleepTimerEndOfEpisode = false,
            sleepTimerRemainingMs = durationMs,
            sleepTimerLastUsedDurationMs = durationMs,
        ) }
    }

    fun startSleepTimerEndOfEpisode() {
        launch {
            preferencesStore.setSleepTimerEndOfEpisode(true)
        }
        sleepTimerManager.setOnTimerExpired {
            playerSessionManager.engine?.pause()
        }
        sleepTimerManager.startEndOfEpisode()
        _uiState.update { it.copy(
            sleepTimerActive = true,
            sleepTimerEndOfEpisode = true,
            sleepTimerRemainingMs = 0,
        ) }
    }

    fun cancelSleepTimer() {
        sleepTimerManager.cancel()
        _uiState.update { it.copy(
            sleepTimerActive = false,
            sleepTimerEndOfEpisode = false,
            sleepTimerRemainingMs = 0,
        ) }
    }

    fun triggerSleepTimerEndOfEpisode() {
        sleepTimerManager.triggerEndOfEpisode()
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

    private val releaseScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var released = false

    fun release() {
        if (released) return
        released = true
        performRelease()
    }

    override fun onCleared() {
        super.onCleared()
        release()
    }

    private fun performRelease() {
        val itemId = playerSessionManager.sessionState.value.currentItemId
        val sessionId = playSessionId
        val positionTicks = playerSessionManager.engine?.currentPositionMs?.let { it * 10_000 } ?: 0L
        playerLifecycleManager.requestAutoEnterPip(false)
        releaseInternals()
        castManager.release()
        activePlayerController.clearEngine()
        if (itemId != null && positionTicks > 0) {
            releaseScope.launch {
                runCatching {
                    kotlinx.coroutines.withTimeout(5_000) {
                        playbackRepository.reportPlaybackStopped(
                            itemId = itemId,
                            sessionId = sessionId,
                            positionTicks = positionTicks,
                        )
                    }
                }
            }
        }
    }
}
