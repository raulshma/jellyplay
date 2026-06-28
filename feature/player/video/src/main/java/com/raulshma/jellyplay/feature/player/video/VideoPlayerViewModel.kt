package com.raulshma.jellyplay.feature.player.video

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
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
import com.raulshma.jellyplay.core.data.playback.VideoMiniPlayerState
import com.raulshma.jellyplay.core.data.cast.CastManager
import com.raulshma.jellyplay.core.data.cast.CastMediaOptions
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
import com.raulshma.jellyplay.core.model.PlaybackMode
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.ReverbPreset
import com.raulshma.jellyplay.core.model.StreamType
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.model.TrackType
import com.raulshma.jellyplay.core.model.isAudioType
import com.raulshma.jellyplay.core.model.isMusicTrack
import com.raulshma.jellyplay.core.ui.feedback.UserMessageBus
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.feature.player.video.components.AspectRatio
import com.raulshma.jellyplay.feature.player.video.engine.MpvPlayerEngine
import com.raulshma.jellyplay.feature.player.video.engine.SubtitleSource
import com.raulshma.jellyplay.core.model.VideoEffectsConfig

import com.raulshma.jellyplay.feature.player.video.trickplay.TrickplayManager
import com.raulshma.jellyplay.core.data.remote.ActivePlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import javax.inject.Inject

/** Minimum resolved duration (ms) before smart-download auto-cleanup may fire. */
private const val MIN_DURATION_FOR_SMART_DELETE_MS = 5 * 60 * 1000L

// SavedStateHandle keys for surviving process death (H13). The in-stream
// playback position, the item it belongs to, and the server session id are
// persisted so playback resumes from the user's last seek rather than the
// original entry point, and so the eventual stop-report matches the start.
private const val SAVED_KEY_ITEM_ID = "video_player.saved_item_id"
private const val SAVED_KEY_POSITION_MS = "video_player.saved_position_ms"
private const val SAVED_KEY_PLAY_SESSION_ID = "video_player.saved_play_session_id"
private const val POSITION_PERSIST_MIN_INTERVAL_MS = 5_000L

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
    private val userMessageBus: UserMessageBus,
    private val savedStateHandle: SavedStateHandle,
) : JellyPlayViewModel() {

    private val _uiState = stateFlow(VideoPlayerUiState())
    val uiState: StateFlow<VideoPlayerUiState> = _uiState.flow

    private val _closePlayer = Channel<Unit>(Channel.BUFFERED)
    val closePlayer = _closePlayer.receiveAsFlow()

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
    // Last position (ms) written to savedStateHandle; used to throttle writes.
    private var lastPersistedPositionMs: Long = Long.MIN_VALUE

    /**
     * Single resolved playback-session id (M19). The server issues its own id
     * via the `PlaybackInfo` endpoint (stored in [PlayerSessionState.playSessionId]);
     * [playSessionId] above is the locally-allocated UUID fallback. Previously
     * start/stop reports read the local UUID directly while progress reports
     * read `sessionState.playSessionId ?: playSessionId`, so the two could
     * desync (start reported id A, stop reported id B). Routing every report
     * and the SavedStateHandle persist through this resolver guarantees a
     * single value is used for the whole session lifecycle.
     */
    private val currentPlaySessionId: String
        get() = playerSessionManager.sessionState.value.playSessionId ?: playSessionId
    private var autoplayNext: Boolean = false
    private var cachedPreferences: com.raulshma.jellyplay.core.model.UserPreferences = com.raulshma.jellyplay.core.model.UserPreferences()

    /**
     * Active Cinema Mode pre-roll context. Non-null only between the moment
     * intros are queued and the moment the main feature begins loading.
     * Captures the original [initialize] arguments so the main feature can be
     * resumed once all intros have been consumed (or skipped).
     */
    private data class CinemaIntroContext(
        val mainItemId: String,
        val mainMediaSourceId: String?,
        val mainStartPositionTicks: Long,
        val mainSubtitleStreamIndex: Int?,
        val mainAudioStreamIndex: Int?,
        val intros: List<com.raulshma.jellyplay.core.model.MediaItem>,
        val currentIndex: Int,
    )

    private var cinemaIntroContext: CinemaIntroContext? = null

    private val trickplayManager = TrickplayManager(
        playbackRepository = playbackRepository,
        lowRamDevice = run {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
            am?.let { it.isLowRamDevice || it.memoryClass <= 256 } ?: false
        },
    )
    private var videoMediaSession: MediaSession? = null
    private var becomingNoisyReceiver: android.content.BroadcastReceiver? = null
    private var transientAudioFocusRequest: android.media.AudioFocusRequest? = null
    private var preDuckVolume: Float? = null
    private var wasPlayingBeforeTransientLoss = false
    private var duckingEnabledJob: Job? = null

    private val _passOutEvents = Channel<String>(Channel.BUFFERED)
    val passOutEvents: kotlinx.coroutines.flow.Flow<String> = _passOutEvents.receiveAsFlow()

    @Volatile
    private var lastInteractionElapsedMs: Long = android.os.SystemClock.elapsedRealtime()

    fun onUserInteraction() {
        lastInteractionElapsedMs = android.os.SystemClock.elapsedRealtime()
    }

    val castManagerField: CastManager = castManager

    private var lastSeekPositionMs: Long? = null
    private var lastSeekTimestamp: Long = 0L

    fun seekTo(positionMs: Long) {
        lastSeekPositionMs = positionMs
        lastSeekTimestamp = System.currentTimeMillis()
        _uiState.update { it.copy(currentPosition = positionMs) }
        playerSessionManager.engine?.seekTo(positionMs)
        // Explicit seeks are the most important position to survive process
        // death; persist immediately rather than waiting for the throttle.
        persistPlaybackPosition(positionMs, force = true)
    }

    fun resumePlayback() {
        val engine = playerSessionManager.engine ?: return
        val skipMs = preferencesStore.preferences.value.videoSkipBackOnResumeMs
        if (skipMs > 0L && !engine.isPlaying.value) {
            val target = (engine.currentPositionMs - skipMs).coerceAtLeast(0L)
            seekTo(target)
        }
        engine.play()
    }

    private fun registerTransientFocusLossListener() {
        if (transientAudioFocusRequest != null) return
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
            ?: return
        val listener = android.media.AudioManager.OnAudioFocusChangeListener { focusChange ->
            val engine = playerSessionManager.engine ?: return@OnAudioFocusChangeListener
            when (focusChange) {
                android.media.AudioManager.AUDIOFOCUS_LOSS -> {
                    // Permanent loss — abandon; system will not hand focus back automatically.
                    engine.pause()
                    preDuckVolume = null
                    wasPlayingBeforeTransientLoss = false
                    unregisterTransientFocusLossListener()
                }
                android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    // Store the raw engine volume without clamping (M1): VLC's
                    // range is 0..2 (to support >100% boost) while ExoPlayer/MPV
                    // use 0..1. A previous coerceIn(0f, 1f) here permanently
                    // halved VLC volumes above 100% on the first duck cycle.
                    // Each engine's setVolume accepts its own native range, so
                    // round-tripping the unclamped value is correct.
                    if (preDuckVolume == null) preDuckVolume = engine.volume
                    wasPlayingBeforeTransientLoss = engine.isPlaying.value
                    engine.setVolume(0.2f)
                }
                android.media.AudioManager.AUDIOFOCUS_GAIN -> {
                    // Restore pre-duck volume only if user hasn't muted in the meantime.
                    if (_uiState.value.isMuted) {
                        engine.setMuted(true)
                    } else {
                        preDuckVolume?.let { engine.setVolume(it) }
                    }
                    val skipMs = preferencesStore.preferences.value.videoSkipBackOnResumeMs
                    if (skipMs > 0L && wasPlayingBeforeTransientLoss) {
                        val target = (engine.currentPositionMs - skipMs).coerceAtLeast(0L)
                        seekTo(target)
                    }
                    if (wasPlayingBeforeTransientLoss) {
                        engine.play()
                    }
                    preDuckVolume = null
                    wasPlayingBeforeTransientLoss = false
                }
            }
        }
        val audioAttributes = android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MOVIE)
            .build()
        val request = android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(audioAttributes)
            .setAcceptsDelayedFocusGain(true)
            .setOnAudioFocusChangeListener(listener)
            .build()
        this.transientAudioFocusRequest = request
        try {
            audioManager.requestAudioFocus(request)
        } catch (_: Exception) {
            this.transientAudioFocusRequest = null
        }
    }

    private fun unregisterTransientFocusLossListener() {
        val request = transientAudioFocusRequest ?: return
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
        try {
            audioManager?.abandonAudioFocusRequest(request)
        } catch (_: Exception) {}
        transientAudioFocusRequest = null
        preDuckVolume = null
        wasPlayingBeforeTransientLoss = false
    }

    private fun getReportPositionMs(): Long {
        val enginePos = playerSessionManager.engine?.currentPositionMs ?: 0L
        val seekPos = lastSeekPositionMs
        val seekTime = lastSeekTimestamp
        if (seekPos != null && seekTime > 0L) {
            val timeSinceSeek = System.currentTimeMillis() - seekTime
            if (timeSinceSeek < 3000L) {
                return seekPos
            }
        }
        return enginePos
    }

    private val progressReporter = PlaybackProgressReporter(
        playbackRepository = playbackRepository,
        viewModel = this,
        uiState = _uiState,
        getCurrentItemId = { playerSessionManager.sessionState.value.currentItemId },
        getPlaySessionId = { playerSessionManager.sessionState.value.playSessionId ?: playSessionId },
        getResolvedPlayMethod = { playerSessionManager.sessionState.value.playMethod },
        getMediaEngine = { playerSessionManager.engine },
        getIncognitoModeEnabled = { cachedPreferences.incognitoModeEnabled },
        onAutoSkip = { segment -> skipSegment(segment) },
        onPlaybackEndedNoNext = {
            if (cinemaIntroContext != null) {
                advanceCinemaIntro()
            } else {
                _closePlayer.trySend(Unit)
            }
        },
        onWatchedThresholdReached = { itemId ->
            handleSmartDownloadCleanup(itemId)
        },
        onPositionPersisted = { positionMs -> persistPlaybackPosition(positionMs, force = false) },
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
                seekTo(positionTicks / 10_000)
            }
        },
        scope = scope,
    )

    private var engineCollectionJob: Job? = null

    // Tracks the in-flight media-load coroutine so a new [initializeInternal]
    // call can cancel it before launching its own — prevents overlapping
    // network/teardown side effects when a SyncPlay load event races a user
    // navigation. See [initializeInternal] for the full rationale (M12).
    private var loadJob: Job? = null

    /**
     * Auto-removes a finished download when the user crosses the watched
     * threshold, gated by [com.raulshma.jellyplay.core.model.UserPreferences.smartDownloadsEnabled].
     *
     * Guards against the two risks flagged in the architecture analysis:
     *  - *Premature delete on misreported duration*: the reporter derives
     *    "95% watched" from `position / duration`. A live stream or a buggy
     *    container can report a tiny/growing duration and trip the threshold
     *    almost immediately. We require the resolved duration to be at least
     *    [MIN_DURATION_FOR_SMART_DELETE_MS] before deleting.
     *  - *Silent destructive action*: the deletion is now surfaced to the
     *    user via [userMessageBus] instead of happening invisibly.
     */
    private fun handleSmartDownloadCleanup(itemId: String) {
        if (!cachedPreferences.smartDownloadsEnabled) return
        if (_uiState.value.duration < MIN_DURATION_FOR_SMART_DELETE_MS) return
        launch {
            val download = downloadRepository.getDownloadByMediaItemId(itemId) ?: return@launch
            downloadRepository.deleteDownload(download.id)
            userMessageBus.info(
                com.raulshma.jellyplay.core.ui.feedback.uiTextOf(
                    com.raulshma.jellyplay.core.ui.R.string.msg_smart_download_deleted,
                ),
            )
        }
    }

    val hapticsEnabled: Boolean get() = cachedPreferences.hapticsEnabled

    // Declared BEFORE the `init {}` block below because the engine-flow
    // collector launched from init calls `trackSelectionHelper.updateTracksFromEngine()`.
    // Kotlin initialises properties and init blocks in declaration order, so a
    // declaration after init would leave this field uninitialised at the moment
    // the collector callback is registered. The latent NPE has not fired only
    // because engine is null until loadMedia(); this removes the foot-gun.
    private val trackSelectionHelper = TrackSelectionHelper(
        preferencesStore = preferencesStore,
        getEngine = { playerSessionManager.engine },
        getUiState = { _uiState.value },
        updateUiState = { transform -> _uiState.update(transform) },
        getCurrentItemId = { playerSessionManager.sessionState.value.currentItemId },
        scope = scope,
    )

    init {
        castManager.acquireConsumer()
        launch {
            preferencesStore.preferences.collect { prefs ->
                val oldPrefs = cachedPreferences
                cachedPreferences = prefs
                val itemId = playerSessionManager.sessionState.value.currentItemId
                val stored = itemId?.let { prefs.mediaStreamSelections[it] }
                _uiState.update { state ->
                    state.copy(
                        hasAudioOverride = stored?.audioStreamIndex != null,
                        hasSubtitleOverride = stored?.subtitleStreamIndex != null,
                    )
                }
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
                if (_uiState.value.showClock != prefs.showClockInPlayer) {
                    _uiState.update { it.copy(showClock = prefs.showClockInPlayer) }
                }
                 if (_uiState.value.keepScreenOnDuringVideo != prefs.keepScreenOnDuringVideo) {
                    _uiState.update { it.copy(keepScreenOnDuringVideo = prefs.keepScreenOnDuringVideo) }
                }
                if (_uiState.value.usePinForPlayerLock != prefs.usePinForPlayerLock ||
                    _uiState.value.pinHash != prefs.pinHash) {
                    _uiState.update { it.copy(
                        usePinForPlayerLock = prefs.usePinForPlayerLock,
                        pinHash = prefs.pinHash,
                    ) }
                }
                if (_uiState.value.passOutProtectionHours != prefs.videoPassOutProtectionHours) {
                    _uiState.update { it.copy(passOutProtectionHours = prefs.videoPassOutProtectionHours) }
                }
                if (oldPrefs.volumeBoostEnabled != prefs.volumeBoostEnabled ||
                    oldPrefs.volumeBoostGain != prefs.volumeBoostGain ||
                    oldPrefs.equalizerSettings != prefs.equalizerSettings ||
                    oldPrefs.pauseOnAudioFocusLoss != prefs.pauseOnAudioFocusLoss) {
                    playerSessionManager.engine?.let {
                        updateConfigWithUiState()
                    }
                }
            }
        }
        launch {
            sleepTimerManager.remainingMs.collect { remaining ->
                _uiState.update { it.copy(sleepTimerRemainingMs = remaining) }
            }
        }
        launch {
            // Pass-out protection: pause playback after N hours of no user interaction.
            var engineJob: kotlinx.coroutines.Job? = null
            playerSessionManager.engineFlow.collect { engine ->
                engineJob?.cancel()
                if (engine != null) {
                    engineJob = launch {
                        val wasPlaying = booleanArrayOf(false)
                        engine.isPlaying.collect { playing ->
                            if (playing && !wasPlaying[0]) {
                                // Resumed playback — reset the interaction clock so a long paused period
                                // doesn't immediately trip the timer.
                                lastInteractionElapsedMs = android.os.SystemClock.elapsedRealtime()
                            }
                            wasPlaying[0] = playing
                        }
                    }
                }
            }
        }
        launch {
            while (isActive) {
                kotlinx.coroutines.delay(60_000)
                val hours = _uiState.value.passOutProtectionHours
                if (hours <= 0) continue
                val engine = playerSessionManager.engine ?: continue
                if (!engine.isPlaying.value) continue
                val elapsedMs = android.os.SystemClock.elapsedRealtime() - lastInteractionElapsedMs
                val thresholdMs = hours * 3_600_000L
                if (elapsedMs >= thresholdMs) {
                    engine.pause()
                    _passOutEvents.trySend("Playback paused — pass-out protection")
                }
            }
        }
        syncPlayBridge.start()

        // Headphone unplug auto-pause
        val becomingNoisyReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: android.content.Intent?) {
                if (intent?.action == android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                    playerSessionManager.engine?.pause()
                }
            }
        }
        this.becomingNoisyReceiver = becomingNoisyReceiver
        val filter = android.content.IntentFilter(android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        try {
            context.registerReceiver(
                becomingNoisyReceiver,
                filter,
                // Private receiver for a system broadcast — explicit flag required on API 34+.
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    Context.RECEIVER_NOT_EXPORTED
                } else 0,
            )
        } catch (_: Exception) {}

        // Duck on transient audio focus loss (phone calls). Observed dynamically so
        // toggling the preference at runtime re-registers without requiring screen re-entry.
        duckingEnabledJob?.cancel()
        duckingEnabledJob = launch {
            preferencesStore.preferences.collect { prefs ->
                if (prefs.duckOnTransientFocusLoss && transientAudioFocusRequest == null) {
                    registerTransientFocusLossListener()
                } else if (!prefs.duckOnTransientFocusLoss && transientAudioFocusRequest != null) {
                    unregisterTransientFocusLossListener()
                }
            }
        }

        launch {
            playerSessionManager.sessionState.collect { session ->
                val itemId = session.currentItemId
                val prefs = cachedPreferences
                val stored = itemId?.let { prefs.mediaStreamSelections[it] }
                    _uiState.update { state ->
                        state.copy(
                            title = session.title,
                            subtitle = session.subtitle,
                            currentMediaSource = session.currentMediaSource,
                            mediaStreams = session.mediaStreams,
                            playMethod = session.playMethodString,
                            isDirectPlayForced = session.isDirectPlayForced,
                            hasAudioOverride = stored?.audioStreamIndex != null,
                            hasSubtitleOverride = stored?.subtitleStreamIndex != null,
                        )
                    }
            }
        }

        launch {
            playerSessionManager.engineFlow.collect { engine ->
                engineCollectionJob?.cancel()
                if (engine != null) {
                    activePlayerController.bindEngine(engine)
                    val prefs = cachedPreferences
                    _uiState.update { it.copy(
                        engineCapabilities = engine.capabilities,
                        usesSubtitleOverlay = false,
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
                        keepScreenOnDuringVideo = prefs.keepScreenOnDuringVideo,
                    )}
                    updateCastStrategyForEngine(engine)
                    notifyUnsupportedAudioDelayIfNeeded(engine, prefs.audioDelayMs)
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
                                val buffering = state == com.raulshma.jellyplay.feature.player.video.engine.EnginePlaybackState.BUFFERING
                                _uiState.update { s ->
                                    if (s.isBuffering == buffering) s else s.copy(isBuffering = buffering)
                                }
                            } }
                            launch { engine.currentCues.collect { cues ->
                                val filteredCues = cues.filter { it.isNotBlank() }
                                _uiState.update { s ->
                                    if (s.currentSubtitleCues == filteredCues) s else s.copy(currentSubtitleCues = filteredCues)
                                }
                            } }
                            launch { engine.availableTracks.collect { trackSelectionHelper.updateTracksFromEngine() } }
                            launch { engine.errorFlow.collect { e -> _uiState.update { s -> s.copy(playerError = e, showPlaybackErrorDialog = true) } } }
                        }
                    }
                } else {
                    activePlayerController.clearEngine()
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
    }

    val playerEngineRef: com.raulshma.jellyplay.feature.player.video.engine.MediaEngine? get() = playerSessionManager.engine

    /**
     * Reactive engine handle for composition (M11). The screen previously read
     * [playerEngineRef] as a plain property; Compose had no subscription, so a
     * engine swap only re-created the surface `AndroidView` if some unrelated
     * state happened to recompose. Exposing the session manager's StateFlow
     * and collecting it with `collectAsStateWithLifecycle` makes engine swaps
     * deterministic: `key(engine)` now always re-keys on a real swap.
     * [playerEngineRef] is retained for the one-shot lambda reads that want
     * the current value without subscribing.
     */
    val playerEngineFlow: StateFlow<com.raulshma.jellyplay.feature.player.video.engine.MediaEngine?>
        get() = playerSessionManager.engineFlow

    fun initialize(
        itemId: String,
        mediaSourceId: String?,
        startPositionTicks: Long,
        subtitleStreamIndex: Int? = null,
        audioStreamIndex: Int? = null,
    ) {
        initializeInternal(
            itemId = itemId,
            mediaSourceId = mediaSourceId,
            startPositionTicks = resolveStartTicksAfterProcessDeath(itemId, startPositionTicks),
            subtitleStreamIndex = subtitleStreamIndex,
            audioStreamIndex = audioStreamIndex,
            allowCinemaMode = true,
        )
    }

    /**
     * After process death the Navigation 3 route still carries the *original*
     * entry-point ticks, but the user's in-stream seeks were tracked only in
     * `_uiState`. SavedStateHandle survives process death, so if we have a
     * persisted position for [itemId] that is beyond the entry point we resume
     * from there. A fresh navigation (new entry) has an empty SavedStateHandle,
     * so this is a no-op outside the process-death-restore path.
     */
    private fun resolveStartTicksAfterProcessDeath(itemId: String, startPositionTicks: Long): Long {
        val savedItemId = savedStateHandle.get<String>(SAVED_KEY_ITEM_ID) ?: return startPositionTicks
        if (savedItemId != itemId) return startPositionTicks
        val savedPosMs = savedStateHandle.get<Long>(SAVED_KEY_POSITION_MS) ?: return startPositionTicks
        if (savedPosMs <= 0L) return startPositionTicks
        val savedTicks = savedPosMs * 10_000
        // Only advance forward; never rewind below a deliberate entry point.
        return if (savedTicks > startPositionTicks) savedTicks else startPositionTicks
    }

    /**
     * Persists the current playback position so it survives process death.
     * Throttled to at most one write per [POSITION_PERSIST_MIN_INTERVAL_MS]
     * unless [force] (e.g. an explicit seek). Also stashes the server session
     * id so the post-restore stop-report pairs with the original start-report.
     */
    private fun persistPlaybackPosition(positionMs: Long, force: Boolean) {
        if (!force && kotlin.math.abs(positionMs - lastPersistedPositionMs) < POSITION_PERSIST_MIN_INTERVAL_MS) return
        val itemId = playerSessionManager.sessionState.value.currentItemId ?: return
        lastPersistedPositionMs = positionMs
        savedStateHandle[SAVED_KEY_ITEM_ID] = itemId
        savedStateHandle[SAVED_KEY_POSITION_MS] = positionMs
        savedStateHandle[SAVED_KEY_PLAY_SESSION_ID] = currentPlaySessionId
    }

    private fun initializeInternal(
        itemId: String,
        mediaSourceId: String?,
        startPositionTicks: Long,
        subtitleStreamIndex: Int?,
        audioStreamIndex: Int?,
        allowCinemaMode: Boolean,
    ) {
        released = false
        lastSeekPositionMs = null
        lastSeekTimestamp = 0L
        trackSelectionHelper.setPendingStreams(subtitleStreamIndex, audioStreamIndex)
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

        // Cancel any in-flight load before starting a new one (M12).
        // initializeInternal itself runs on Main.immediate so its synchronous
        // prefix cannot interleave with another call; but each call launches a
        // long-lived async load coroutine (media-detail fetch, engine load,
        // trickplay/segments/episodes). Two of those coroutines — e.g. a
        // SyncPlay `onLoadItem` event arriving while a user tap is also
        // loading — could interleave their network/teardown side effects
        // (double stop-reports, crossed engine binds). Tracking and cancelling
        // the previous load makes "latest load wins" deterministic without
        // changing the synchronous semantics of this function.
        loadJob?.cancel()

        val reclaimed = videoMiniPlayerState.tryReclaimEngine(itemId) as? com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
        if (reclaimed != null) {
            loadJob = launch {
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

        videoMiniPlayerState.release()

        releaseInternals()
        // Restore the server session id after process death (if this is the
        // same item) so the eventual stop-report pairs with the start-report
        // instead of orphaning it. Otherwise allocate a fresh session id.
        val restoredSessionId = savedStateHandle.get<String>(SAVED_KEY_PLAY_SESSION_ID)
        val savedItemId = savedStateHandle.get<String>(SAVED_KEY_ITEM_ID)
        playSessionId = if (savedItemId == itemId && !restoredSessionId.isNullOrEmpty()) {
            restoredSessionId
        } else {
            java.util.UUID.randomUUID().toString()
        }
        lastPersistedPositionMs = Long.MIN_VALUE
        trickplayManager.clear()

        if (wasInSyncPlay) {
            syncPlayBridge.reattachSession()
        }

        loadJob = launch {
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
                passOutProtectionHours = prefs.videoPassOutProtectionHours,
                gesturesEnabled = prefs.videoGesturesEnabled,
                holdSpeedEnabled = prefs.videoHoldSpeedEnabled,
                holdSpeedMultiplier = prefs.videoHoldSpeedMultiplier,
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
                showClock = prefs.showClockInPlayer,
                keepScreenOnDuringVideo = prefs.keepScreenOnDuringVideo,
                streamingQuality = prefs.streamingQuality,
                adaptiveBitrateEnabled = prefs.adaptiveBitrateEnabled,
                playbackMode = prefs.playbackMode,
            ) }
            autoplayNext = prefs.videoAutoplayNext

            if (allowCinemaMode && shouldAttemptCinemaMode(prefs, itemId, startPositionTicks)) {
                val intros = mediaRepository.getIntros(itemId).getOrDefault(emptyList())
                if (intros.isNotEmpty()) {
                    cinemaIntroContext = CinemaIntroContext(
                        mainItemId = itemId,
                        mainMediaSourceId = mediaSourceId,
                        mainStartPositionTicks = startPositionTicks,
                        mainSubtitleStreamIndex = subtitleStreamIndex,
                        mainAudioStreamIndex = audioStreamIndex,
                        intros = intros,
                        currentIndex = 0,
                    )
                    loadCinemaIntro(intros.first())
                    return@launch
                }
            }

            playerSessionManager.loadMedia(itemId, mediaSourceId, startPositionTicks)

            val sessionState = playerSessionManager.sessionState.value
            val source = sessionState.currentMediaSource
            val detail = sessionState.mediaDetail

            // Restore per-item persisted video filters (if any) before playback kicks off.
            val hydratedEffects = prefs.videoEffectsByItem[itemId] ?: VideoEffectsConfig()
            if (_uiState.value.videoEffects != hydratedEffects) {
                _uiState.update { it.copy(videoEffects = hydratedEffects) }
                updateConfigWithUiStateDebounced()
            }

            if (sessionState.streamUrl != null) {
                _uiState.update { it.copy(streamUrl = sessionState.streamUrl) }
            }

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

            if (!cachedPreferences.incognitoModeEnabled) {
                playbackRepository.reportPlaybackStart(
                    com.raulshma.jellyplay.core.model.PlaybackStartInfo(
                        itemId = itemId,
                        sessionId = currentPlaySessionId,
                        mediaSourceId = source?.id,
                        playMethod = sessionState.playMethod,
                    )
                )
            }

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

    fun verifyPlayerLockPin(pin: String): Boolean {
        return preferencesStore.verifyPin(pin)
    }

    fun setPlaybackSpeed(speed: Float) {
        _uiState.update { it.copy(playbackSpeed = speed) }
        playerSessionManager.engine?.setPlaybackSpeed(speed)
    }

    private var speedBeforeHold: Float? = null

    fun startHoldSpeed() {
        if (_uiState.value.isHoldSpeedActive) return
        speedBeforeHold = _uiState.value.playbackSpeed
        val targetSpeed = _uiState.value.holdSpeedMultiplier
        playerSessionManager.engine?.setPlaybackSpeed(targetSpeed)
        _uiState.update { it.copy(playbackSpeed = targetSpeed, isHoldSpeedActive = true) }
    }

    fun stopHoldSpeed() {
        if (!_uiState.value.isHoldSpeedActive) return
        val restoreSpeed = speedBeforeHold ?: _uiState.value.defaultSpeed
        speedBeforeHold = null
        playerSessionManager.engine?.setPlaybackSpeed(restoreSpeed)
        _uiState.update { it.copy(playbackSpeed = restoreSpeed, isHoldSpeedActive = false) }
    }

    fun selectAudioTrack(option: TrackOption) {
        trackSelectionHelper.selectAudioTrack(option)
    }

    fun selectSubtitleTrack(option: TrackOption) {
        trackSelectionHelper.selectSubtitleTrack(option)
    }

    fun resetAudioTrack() {
        trackSelectionHelper.resetAudioSelection()
    }

    fun resetSubtitleTrack() {
        trackSelectionHelper.resetSubtitleSelection()
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

    /**
     * Surfaces a one-time heads-up when the user has a non-zero audio-delay
     * preference (set on mpv/LibVLC) but the active engine can't apply it
     * (e.g. ExoPlayer, see `EngineCapabilities.supportsAudioDelay`). Without
     * this the user gets out-of-sync audio with no explanation after switching
     * engines (enhancements §4.4).
     *
     * Only fires when a delay is actually configured, so the common case
     * (delay == 0) stays silent.
     */
    private fun notifyUnsupportedAudioDelayIfNeeded(
        engine: com.raulshma.jellyplay.feature.player.video.engine.MediaEngine,
        audioDelayMs: Long,
    ) {
        if (audioDelayMs == 0L) return
        if (engine.capabilities.supportsAudioDelay) return
        val engineName = when (engine) {
            is com.raulshma.jellyplay.feature.player.video.engine.ExoPlayerEngine -> PlayerType.EXO_PLAYER.displayName
            is com.raulshma.jellyplay.feature.player.video.engine.MpvPlayerEngine -> PlayerType.MPV.displayName
            is com.raulshma.jellyplay.feature.player.video.engine.LibVlcPlayerEngine -> PlayerType.LIBVLC.displayName
            else -> "this engine"
        }
        userMessageBus.info(
            "Audio delay (${audioDelayMs}ms) isn't supported by $engineName — switching engines re-enables it",
        )
    }

    fun setSubtitleDelay(ms: Long) {
        val current = _uiState.value.subtitleStyle
        if (current.offsetMs == ms) return
        setSubtitleStyle(current.copy(offsetMs = ms))
    }

    fun setPlaybackMode(mode: PlaybackMode) {
        if (_uiState.value.playbackMode == mode) return
        _uiState.update { it.copy(playbackMode = mode) }
        launch {
            preferencesStore.setPlaybackMode(mode)
            reloadPlaybackForMode()
        }
    }

    fun setStreamingQuality(quality: StreamingQuality) {
        if (_uiState.value.streamingQuality == quality) return
        _uiState.update { it.copy(streamingQuality = quality) }
        launch {
            preferencesStore.setStreamingQuality(quality)
            reloadPlaybackForMode()
        }
    }

    /**
     * Toggles adaptive bitrate (the AUTO-mode network cap). Persisted and
     * re-resolved immediately so the cap change takes effect for the running
     * stream: disabling it drops the cap so the server direct-plays instead of
     * transcoding high-bitrate media.
     */
    fun setAdaptiveBitrateEnabled(enabled: Boolean) {
        if (_uiState.value.adaptiveBitrateEnabled == enabled) return
        _uiState.update { it.copy(adaptiveBitrateEnabled = enabled) }
        launch {
            preferencesStore.setAdaptiveBitrateEnabled(enabled)
            reloadPlaybackForMode()
        }
    }

    /**
     * Re-resolves the current item against the (possibly changed)
     * [PlaybackMode]/[StreamingQuality] and swaps the engine onto the new
     * stream at the current position. Surfaces a toast when switching to a
     * transcode since the brief re-buffer is otherwise surprising, and
     * auto-falls-back to transcode when a forced-direct-play request yields
     * no playable method.
     */
    private suspend fun reloadPlaybackForMode() {
        val mode = _uiState.value.playbackMode
        val quality = _uiState.value.streamingQuality
        val pos = playerSessionManager.engine?.currentPositionMs ?: 0L
        val resolved = playerSessionManager.reloadPlayback(mode, quality, pos) ?: return
        if (resolved.playMethod == com.raulshma.jellyplay.core.model.PlayMethod.TRANSCODE) {
            userMessageBus.info("Switched to transcoded stream — re-buffering")
        }
        if (mode == PlaybackMode.FORCE_DIRECT_PLAY &&
            resolved.playMethod != com.raulshma.jellyplay.core.model.PlayMethod.DIRECT_PLAY
        ) {
            userMessageBus.info("Direct Play unavailable for this item — falling back to transcode")
            _uiState.update { it.copy(playbackMode = PlaybackMode.FORCE_TRANSCODE) }
            launch {
                preferencesStore.setPlaybackMode(PlaybackMode.FORCE_TRANSCODE)
                playerSessionManager.reloadPlayback(
                    PlaybackMode.FORCE_TRANSCODE, quality,
                    playerSessionManager.engine?.currentPositionMs ?: pos,
                )
            }
        }
    }

    fun setDecoderMode(mode: DecoderMode) {
        _uiState.update { it.copy(decoderMode = mode) }
        updateConfigWithUiState()
        launch {
            preferencesStore.setDecoderMode(mode)
        }
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
        // Persist per item so the same filter preset is restored next time.
        // Skip when in Cinema Mode pre-roll — the intro is transient.
        val itemId = playerSessionManager.sessionState.value.currentItemId
        if (itemId != null && cinemaIntroContext == null) {
            launch {
                preferencesStore.setVideoEffectsForItem(itemId, effects)
            }
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
                volumeBoostEnabled = cachedPreferences.volumeBoostEnabled,
                volumeBoostGain = cachedPreferences.volumeBoostGain,
            ),
            pauseOnAudioFocusLoss = cachedPreferences.pauseOnAudioFocusLoss
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
        val currentItemId = playerSessionManager.sessionState.value.currentItemId ?: return
        launch {
            val episodes = mediaRepository.getEpisodes(seriesId, detail.item.seasonId ?: return@launch)
                .getOrElse { return@launch }
            val currentIndex = episodes.indexOfFirst { it.id == currentItemId }
            if (currentIndex < 0 || currentIndex + 1 >= episodes.size) return@launch
            val next = episodes[currentIndex + 1]

            // Auto-advancing is only reachable near the episode's end, so the
            // current episode was effectively watched. Mark it played so it
            // drops out of Continue Watching. This also covers the SyncPlay
            // branch below, which bypasses [initialize] and its stopped-position
            // report.
            if (!cachedPreferences.incognitoModeEnabled) {
                runCatching { mediaRepository.markPlayed(currentItemId) }
            }

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
        if (state.cinemaIntroState != null) {
            advanceCinemaIntro()
            return
        }
        val seg = state.activeSegment
        if (seg != null && seg.type == com.raulshma.jellyplay.core.model.MediaSegmentType.INTRO) {
            skipSegment(seg)
            return
        }
        val endTicks = state.introSegmentEndTicks
        if (endTicks != null && endTicks > 0) {
            seekTo(endTicks / 10_000)
        }
    }

    /**
     * Cinema Mode is only attempted on fresh starts (never on resume / next-episode
     * auto-advance / SyncPlay / external player / mini-mode reclaim). Server-side
     * intros are best-effort: any failure returns an empty list and falls back to
     * normal playback.
     */
    private fun shouldAttemptCinemaMode(
        prefs: com.raulshma.jellyplay.core.model.UserPreferences,
        itemId: String,
        startPositionTicks: Long,
    ): Boolean {
        if (!prefs.cinemaModeEnabled) return false
        if (startPositionTicks != 0L) return false
        if (prefs.preferredPlayer == PlayerType.EXTERNAL) return false
        if (syncPlayManager.isInSyncPlaySession) return false
        // Skip for non-video items — intros are only meaningful for movies/episodes.
        val existingDetail = mediaDetail
        if (existingDetail != null && existingDetail.item.id == itemId) {
            val type = existingDetail.item.mediaType
            if (type != com.raulshma.jellyplay.core.model.MediaType.MOVIE &&
                type != com.raulshma.jellyplay.core.model.MediaType.EPISODE &&
                type != com.raulshma.jellyplay.core.model.MediaType.UNKNOWN
            ) {
                return false
            }
        }
        return true
    }

    private fun loadCinemaIntro(intro: com.raulshma.jellyplay.core.model.MediaItem) {
        val context = cinemaIntroContext ?: return
        launch {
            _uiState.update {
                it.copy(
                    cinemaIntroState = CinemaIntroUiState(
                        title = intro.name.ifBlank { "Intro" },
                        currentIndex = context.currentIndex + 1,
                        totalCount = context.intros.size,
                    ),
                )
            }
            // Pre-roll intros are not part of the user's library history — skip
            // server-side playback reporting and segment/next-episode/trickplay
            // bookkeeping for them.
            playerSessionManager.loadMedia(intro.id, null, 0L)
            createVideoMediaSession(
                intro.id,
                playerSessionManager.sessionState.value.title,
                playerSessionManager.sessionState.value.subtitle,
            )
            progressReporter.startPositionTracking()
        }
    }

    /**
     * Advance to the next pre-roll intro, or — once all intros are exhausted —
     * resume normal playback of the main feature. Idempotent: callers may invoke
     * this on either an end-of-playback callback or an explicit "skip" tap.
     */
    private fun advanceCinemaIntro() {
        val context = cinemaIntroContext ?: return
        val nextIndex = context.currentIndex + 1
        if (nextIndex < context.intros.size) {
            cinemaIntroContext = context.copy(currentIndex = nextIndex)
            loadCinemaIntro(context.intros[nextIndex])
            return
        }
        // Out of intros — restore the main feature. Clear cinema state first so
        // the recursive [initializeInternal] call cannot re-enter cinema mode.
        cinemaIntroContext = null
        _uiState.update { it.copy(cinemaIntroState = null) }
        progressReporter.cancelJobs()
        initializeInternal(
            itemId = context.mainItemId,
            mediaSourceId = context.mainMediaSourceId,
            startPositionTicks = context.mainStartPositionTicks,
            subtitleStreamIndex = context.mainSubtitleStreamIndex,
            audioStreamIndex = context.mainAudioStreamIndex,
            allowCinemaMode = false,
        )
    }

    private fun fetchMediaSegments(itemId: String) {
        launch {
            // Offline-first: prefer segments bundled with the download so skip
            // controls (intro/outro/recap) work without a server round-trip.
            val local = downloadRepository.loadLocalSegments(itemId)
            if (local != null) {
                _uiState.update { it.copy(segments = local) }
                return@launch
            }
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
            seekTo(endTicks / 10_000)
        }
    }

    fun skipSegment(segment: com.raulshma.jellyplay.core.model.MediaSegment) {
        val endTicks = _uiState.value.segmentEndTicks(segment)
        if (endTicks != null && endTicks > 0) {
            seekTo(endTicks / 10_000)
        }
    }

    private fun applyMediaDetail(detail: MediaDetail) {
        mediaDetail = detail
        _uiState.update { state ->
            state.copy(
                chapters = detail.chapters,
                seriesId = detail.item.seriesId,
                currentSeasonId = detail.item.seasonId ?: state.currentSeasonId,
                overview = detail.item.overview ?: "",
                people = detail.people,
                artworkUrl = getImageUrl(detail.item.id, 400),
            )
        }
        fetchCompanionLyrics(detail)
    }

    private fun fetchCompanionLyrics(detail: MediaDetail) {
        val item = detail.item
        if (item.mediaType.isAudioType || item.mediaType.isMusicTrack) {
            launch {
                val artist = item.albumArtist ?: item.artistItems.firstOrNull()?.name ?: ""
                val durationSec = (item.runTimeTicks ?: 0L) / 10_000_000L
                val lyricsResult = mediaRepository.getLyricsWithFallback(
                    itemId = item.id,
                    artistName = artist,
                    trackName = item.name,
                    duration = durationSec.toDouble()
                ).getOrNull()
                _uiState.update { it.copy(lyricsLines = lyricsResult?.lines ?: emptyList()) }
            }
        } else {
            _uiState.update { it.copy(lyricsLines = emptyList()) }
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
            .setMediaId(currentItemId)
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
        // Carry the active track + quality selections into the cast session so
        // the handoff does not silently drop audio/subtitle/quality (§4.5).
        castManager.loadMedia(mediaItem, positionMs, object : Player.Listener {}, buildCastOptions(sourceId))
        engine.pause()
    }

    /**
     * Builds the cast playback intent from the engine's currently-selected
     * tracks and the active streaming-quality preference. Track indices come
     * straight from the engine's `availableTracks` (`isSelected`); the bitrate
     * ceiling mirrors the local `setMaxVideoBitrate` computation so the cast
     * session respects the same cap (no cap when forcing direct play or when
     * the quality is `AUTO`).
     */
    private fun buildCastOptions(mediaSourceId: String): CastMediaOptions {
        val tracks = playerSessionManager.engine?.availableTracks?.value.orEmpty()
        val audioIndex = tracks.firstOrNull { it.isSelected && it.type == TrackType.AUDIO }?.index
        val subtitleIndex = tracks.firstOrNull { it.isSelected && it.type == TrackType.SUBTITLE }?.index
        val maxBitrate = if (_uiState.value.playbackMode == PlaybackMode.FORCE_DIRECT_PLAY) {
            null
        } else {
            adaptiveBitrateManager.resolveMaxBitrate(_uiState.value.streamingQuality)?.toInt()
        }
        return CastMediaOptions(
            mediaSourceId = mediaSourceId.takeIf { it.isNotBlank() },
            audioStreamIndex = audioIndex,
            subtitleStreamIndex = subtitleIndex,
            maxVideoBitrate = maxBitrate,
        )
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
        if (castManager.currentStrategyName != CastManager.STRATEGY_DLNA) {
            castManager.setActiveStrategy(CastManager.STRATEGY_GOOGLE)
        }
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

    fun toggleVideoStats() {
        val newValue = !_uiState.value.showVideoStats
        _uiState.update { it.copy(showVideoStats = newValue) }
        playerSessionManager.engine?.setVideoStatsEnabled(newValue)
    }

    fun toggleMute() {
        val engine = playerSessionManager.engine ?: return
        val currentlyMuted = _uiState.value.isMuted
        engine.setMuted(!currentlyMuted)
        _uiState.update { it.copy(isMuted = !currentlyMuted) }
    }

    fun setControlsVisible(visible: Boolean) {
        playerSessionManager.engine?.setPollingIntervalMs(if (visible) 250L else 1000L)
    }

    suspend fun getTrickplayThumbnail(positionMs: Long): Bitmap? {
        val state = _uiState.value
        if (!state.trickplayEnabled && !state.trickplayOnSeekGesture) return null
        return trickplayManager.getThumbnail(positionMs)
    }

    private fun reportCurrentPlaybackStopped() {
        if (cachedPreferences.incognitoModeEnabled) return
        val itemId = playerSessionManager.sessionState.value.currentItemId ?: return
        val sessionId = currentPlaySessionId
        val positionTicks = getReportPositionMs() * 10_000
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
        loadJob?.cancel()
        loadJob = null
        progressReporter.cancelJobs()
        syncPlayBridge.reset()
        releaseVideoMediaSession()
        playerSessionManager.release()
        playerLifecycleManager.reset()
        trickplayManager.clear()
        trackSelectionHelper.reset()
        mediaDetail = null
        autoplayNext = false
        equalizerEnabled = false
        lastSeekPositionMs = null
        lastSeekTimestamp = 0L
        cinemaIntroContext = null

        _uiState.update { currentState ->
            VideoPlayerUiState(
                preferredPlayerType = currentState.preferredPlayerType,
                seekDurationMs = currentState.seekDurationMs,
                defaultOrientation = currentState.defaultOrientation,
                controlsTimeoutMs = currentState.controlsTimeoutMs,
                gesturesEnabled = currentState.gesturesEnabled,
                defaultSpeed = currentState.defaultSpeed,
                swipeSeekMaxMs = currentState.swipeSeekMaxMs,
                rememberBrightness = currentState.rememberBrightness,
                brightnessLevel = currentState.brightnessLevel,
                segmentBehaviors = currentState.segmentBehaviors,
                videoEpisodeBrowserEnabled = currentState.videoEpisodeBrowserEnabled,
                showPlaybackMetadata = currentState.showPlaybackMetadata,
                showClock = currentState.showClock,
                keepScreenOnDuringVideo = currentState.keepScreenOnDuringVideo,
                subtitleStyle = currentState.subtitleStyle,
                dialogueBoostEnabled = currentState.dialogueBoostEnabled,
                dialogueBoostStrength = currentState.dialogueBoostStrength,
                nightModeEnabled = currentState.nightModeEnabled,
                nightModeStrength = currentState.nightModeStrength,
                audioPassthrough = currentState.audioPassthrough,
                decoderMode = currentState.decoderMode,
                audioNormalizationMode = currentState.audioNormalizationMode,
                audioNormalizationEnabled = currentState.audioNormalizationEnabled,
                channelMixMode = currentState.channelMixMode,
                channelMixEnabled = currentState.channelMixEnabled,
                bassBoostEnabled = currentState.bassBoostEnabled,
                bassBoostStrength = currentState.bassBoostStrength,
                virtualizerEnabled = currentState.virtualizerEnabled,
                virtualizerStrength = currentState.virtualizerStrength,
                reverbPreset = currentState.reverbPreset,
                sleepTimerActive = currentState.sleepTimerActive,
                sleepTimerEndOfEpisode = currentState.sleepTimerEndOfEpisode,
                sleepTimerRemainingMs = currentState.sleepTimerRemainingMs,
                sleepTimerLastUsedDurationMs = currentState.sleepTimerLastUsedDurationMs,
            )
        }
    }

    fun startSleepTimer(durationMs: Long) {
        launch {
            preferencesStore.setSleepTimerDurationMs(durationMs)
            preferencesStore.setSleepTimerEndOfEpisode(false)
        }
        sleepTimerManager.setOnTimerExpired {
            playerSessionManager.engine?.pause()
        }
        sleepTimerManager.setOnFadeProgress { progress ->
            // Skip volume writes while user-muted; let mute state win.
            if (!_uiState.value.isMuted) {
                playerSessionManager.engine?.setVolume(progress)
            }
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
        sleepTimerManager.setOnFadeProgress(null)
        sleepTimerManager.startEndOfEpisode()
        _uiState.update { it.copy(
            sleepTimerActive = true,
            sleepTimerEndOfEpisode = true,
            sleepTimerRemainingMs = 0,
        ) }
    }

    fun cancelSleepTimer() {
        sleepTimerManager.cancel()
        // Restore pre-fade volume — but never override an active user mute.
        val engine = playerSessionManager.engine
        if (engine != null && !_uiState.value.isMuted) {
            engine.setVolume(1f)
        }
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
        releaseScope.cancel()
    }

    private fun performRelease() {
        val itemId = playerSessionManager.sessionState.value.currentItemId
        val sessionId = currentPlaySessionId
        val positionTicks = getReportPositionMs() * 10_000
        playerLifecycleManager.requestAutoEnterPip(false)
        // Unregister headphone unplug receiver
        becomingNoisyReceiver?.let {
            try { context.unregisterReceiver(it) } catch (_: Exception) {}
        }
        becomingNoisyReceiver = null
        duckingEnabledJob?.cancel()
        duckingEnabledJob = null
        unregisterTransientFocusLossListener()
        sleepTimerManager.setOnFadeProgress(null)
        releaseInternals()
        castManager.releaseConsumer()
        activePlayerController.clearEngine()
        if (itemId != null && positionTicks > 0) {
            releaseScope.launch(NonCancellable) {
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
