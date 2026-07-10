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
import com.raulshma.jellyplay.core.data.playback.PipAction
import com.raulshma.jellyplay.core.data.playback.PipTransport
import com.raulshma.jellyplay.core.data.playback.PlayerLifecycleManager
import com.raulshma.jellyplay.core.data.playback.SleepTimerManager
import com.raulshma.jellyplay.core.data.playback.VideoMiniPlayerState
import com.raulshma.jellyplay.core.data.cast.CastManager
import com.raulshma.jellyplay.core.data.cast.CastMediaOptions
import com.raulshma.jellyplay.core.data.cast.CastSessionEvent
import com.raulshma.jellyplay.core.data.playback.AdaptiveBitrateManager
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.ItemPlaybackPreferenceRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayManager
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.SyncPlayRepeatMode
import com.raulshma.jellyplay.core.model.SyncPlayShuffleMode
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.ChannelMixMode
import com.raulshma.jellyplay.core.model.DecoderMode
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.ChapterInfo
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem as JellyfinMediaItem
import com.raulshma.jellyplay.core.model.MediaSegment
import com.raulshma.jellyplay.core.model.MediaSegmentType
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.PlaybackMode
import com.raulshma.jellyplay.core.model.PlaybackPrefScope
import com.raulshma.jellyplay.core.model.SegmentBehavior
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
import com.raulshma.jellyplay.feature.player.video.engine.EngineVideoStats
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
/** Debounce window for engine config syncs driven by slider drags (C11). */
private const val CONFIG_SYNC_DEBOUNCE_MS = 150L

/**
 * Initial-buffering watchdog. If the engine has not reached READY within this
 * window since load, the playback-error dialog is surfaced so the user can
 * retry with another engine. Long enough to cover legitimate cold-start
 * buffering on slow networks, short enough to feel responsive when playback is
 * genuinely stuck (e.g. undecodable content).
 */
private const val BUFFERING_TIMEOUT_MS = 20_000L

/**
 * Segment-relevant slice of [VideoPlayerUiState], used by
 * [VideoPlayerViewModel.segmentOverlayState]. Projecting only these fields
 * (and `distinctUntilChanged`-ing them) means a 4 Hz [currentPositionMs][VideoPlayerViewModel.currentPositionMs]
 * tick does not allocate a fresh 95-field `VideoPlayerUiState.copy(...)` or
 * re-run `computeActiveSegment()` unless one of these fields actually changed.
 *
 * Note: `isInIntro` / `isInCredits` / `shouldShowUpNext` are deliberately NOT
 * captured here — they are computed properties on [VideoPlayerUiState] that
 * depend on the live position/duration, so they are re-derived inside
 * [computeOverlay] from the position-aware state. Only the *inputs* that do
 * not change every tick are projected.
 */
private data class SegmentProjection(
    val segments: List<MediaSegment>,
    val chapters: List<ChapterInfo>,
    val segmentBehaviors: Map<MediaSegmentType, SegmentBehavior>,
    val autoplayCancelled: Boolean,
    val isInSyncPlaySession: Boolean,
    val nextEpisode: JellyfinMediaItem?,
    val seriesId: String?,
) {
    constructor(state: VideoPlayerUiState) : this(
        segments = state.segments,
        chapters = state.chapters,
        segmentBehaviors = state.segmentBehaviors,
        autoplayCancelled = state.autoplayCancelled,
        isInSyncPlaySession = state.isInSyncPlaySession,
        nextEpisode = state.nextEpisode,
        seriesId = state.seriesId,
    )

    /**
     * Builds the [SegmentOverlayState] for a given live position/duration.
     * Reconstructs a position-aware [VideoPlayerUiState] carrying only the
     * segment-relevant fields so the existing `computeActiveSegment()` /
     * `behaviorForType()` / `shouldShowUpNext` logic is reused verbatim (no
     * duplication of the chapter-name-matching rules). This reconstruction
     * runs only when the projection changes — not on every 4 Hz tick.
     */
    fun computeOverlay(positionMs: Long, durationMs: Long): SegmentOverlayState {
        val positioned = VideoPlayerUiState(
            currentPosition = positionMs,
            duration = durationMs,
            segments = segments,
            chapters = chapters,
            segmentBehaviors = segmentBehaviors,
            autoplayCancelled = autoplayCancelled,
            isInSyncPlaySession = isInSyncPlaySession,
            nextEpisode = nextEpisode,
            seriesId = seriesId,
        )
        val activeSegment = positioned.computeActiveSegment()
        return SegmentOverlayState(
            activeSegment = activeSegment,
            activeSegmentBehavior = activeSegment?.let { positioned.behaviorForType(it.type) }
                ?: SegmentBehavior.IGNORE,
            isInIntro = positioned.isInIntro,
            isInCredits = positioned.isInCredits,
            shouldShowUpNext = positioned.shouldShowUpNext,
        )
    }
}

@HiltViewModel
class VideoPlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
    private val imageUrlProvider: ImageUrlProvider,
    private val downloadRepository: DownloadRepository,
    private val offlineRepository: OfflineRepository,
    private val itemPlaybackPreferenceRepository: ItemPlaybackPreferenceRepository,
    private val preferencesStore: UserPreferencesStore,
    private val sessionManager: PlaybackSessionManager,
    private val castManager: CastManager,
    private val jellyfinRemotePlayCastStrategy: com.raulshma.jellyplay.core.data.cast.remote.JellyfinRemotePlayCastStrategy,
    private val syncPlayManager: SyncPlayManager,
    private val okHttpClient: OkHttpClient,
    private val adaptiveBitrateManager: AdaptiveBitrateManager,
    private val activePlayerController: ActivePlayerController,
    val playerLifecycleManager: PlayerLifecycleManager,
    val videoMiniPlayerState: VideoMiniPlayerState,
    private val sleepTimerManager: SleepTimerManager,
    private val userMessageBus: UserMessageBus,
    private val playerEngineFactory: com.raulshma.jellyplay.feature.player.video.engine.PlayerEngineFactory,
    private val savedStateHandle: SavedStateHandle,
) : JellyPlayViewModel() {

    private val _uiState = stateFlow(VideoPlayerUiState())
    val uiState: StateFlow<VideoPlayerUiState> = _uiState.flow

    // --- High-frequency playback streams (V-1) ---------------------------------
    // currentPosition / bufferedPosition / videoStats (and duration) update at
    // up to 4 Hz while controls are visible. Previously they were folded into
    // the ~60-field [VideoPlayerUiState], so every tick invalidated the entire
    // VideoPlayerScreen body. They now live on dedicated StateFlows and are
    // collected only inside the leaf composables that render them
    // (PlayerControls seek bar, VideoStatsOverlay). The remaining uiState is
    // thereby reduced to a low-frequency stream.
    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _bufferedPositionMs = MutableStateFlow(0L)
    val bufferedPositionMs: StateFlow<Long> = _bufferedPositionMs.asStateFlow()

    private val _videoStats = MutableStateFlow(EngineVideoStats())
    val videoStats: StateFlow<EngineVideoStats> = _videoStats.asStateFlow()

    // Sleep-timer countdown, sourced directly from SleepTimerManager. Kept OUT
    // of [uiState] (mirroring the high-frequency streams above) so a 5 s tick —
    // or the 100 ms fade-out burst — does not copy the ~95-field [uiState] and
    // re-invalidate the screen root. Collected only by the leaf composables
    // that render the countdown (overflow-menu label, SleepTimerSheet).
    val sleepTimerRemainingMs: StateFlow<Long> = sleepTimerManager.remainingMs
    // ---------------------------------------------------------------------------

    /**
     * Low-frequency view of the segment / up-next overlays, derived by folding
     * the high-frequency [currentPositionMs] / [durationMs] into [uiState] only
     * to compute the active segment. The result is `distinctUntilChanged` via
     * [StateFlow], so collectors (the screen root) only recompose when one of
     * these values actually changes — i.e. at segment boundaries, not at 4 Hz.
     *
     * Only the segment-relevant slice of [uiState] is projected (via
     * [SegmentProjection] + `distinctUntilChanged`) so a 4 Hz position tick does
     * not allocate a fresh 95-field `VideoPlayerUiState.copy(...)` and re-run
     * `computeActiveSegment()` when no segment-relevant field changed.
     */
    val segmentOverlayState: StateFlow<SegmentOverlayState> = stateIn(
        initial = SegmentOverlayState(),
        flow = combine(
            currentPositionMs,
            durationMs,
            uiState.map(::SegmentProjection).distinctUntilChanged(),
        ) { pos, dur, proj ->
            proj.computeOverlay(pos, dur)
        },
    )

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
        playerEngineFactory = playerEngineFactory,
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
    private val autoplayController = AutoPlayController()
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
    private val subtitleManager = SubtitleManager(
        context = context,
        playbackRepository = playbackRepository,
        mediaRepository = mediaRepository,
        userMessageBus = userMessageBus,
        scope = scope,
        addExternalSubtitle = { playerSessionManager.addExternalSubtitle(it) },
        getUiState = { _uiState.value },
        updateUiState = { transform -> _uiState.update(transform) },
        getCurrentItemId = { playerSessionManager.sessionState.value.currentItemId },
        onMediaDetailRefreshed = { detail -> applyMediaDetailAndSourceState(detail) },
    )
    private var videoMediaSession: MediaSession? = null
    private var becomingNoisyReceiver: android.content.BroadcastReceiver? = null
    private var transientAudioFocusRequest: android.media.AudioFocusRequest? = null
    private var preDuckVolume: Float? = null
    private var wasPlayingBeforeTransientLoss = false

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

    /**
     * Snapshot of [uiState] with the live playback position/duration injected
     * from the dedicated high-frequency flows (V-1). Use this anywhere that
     * needs the position-aware derived properties ([activeSegment],
     * [shouldShowUpNext], …) so the logic does not depend on the (now stale)
     * `currentPosition`/`duration` fields stored on uiState itself.
     */
    private fun positionAwareState(): VideoPlayerUiState = _uiState.value.copy(
        currentPosition = _currentPositionMs.value,
        duration = _durationMs.value,
    )

    fun seekTo(positionMs: Long) {
        lastSeekPositionMs = positionMs
        lastSeekTimestamp = System.currentTimeMillis()
        // Update the dedicated position flow (V-1) so the seek bar reflects the
        // new position immediately; uiState is no longer the source of truth.
        _currentPositionMs.value = positionMs
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
        getIsLive = { _uiState.value.isLive },
        onAutoSkip = { segment -> skipSegment(segment) },
        onPlaybackEndedNoNext = {
            // Live streams report a tiny/growing duration; the duration-based
            // end detector would otherwise trip within seconds and dismiss
            // the player. Suppress for live (see handlePlaybackEnded).
            if (_uiState.value.isLive) return@PlaybackProgressReporter
            if (cinemaIntroContext != null) {
                advanceCinemaIntro()
            } else {
                _closePlayer.trySend(Unit)
            }
        },
        onWatchedThresholdReached = { itemId ->
            handleSmartDownloadCleanup(itemId)
            // Mark the offline copy as fully watched so its row shows the
            // watched state. No-op for non-downloaded items.
            launch {
                offlineRepository.updatePlaybackProgress(itemId, positionTicks = null, percentage = 100.0, isPlayed = true)
            }
        },
        onPositionPersisted = { positionMs -> persistPlaybackPosition(positionMs, force = false) },
        onEnginePositionUpdate = { positionMs, durationMs, bufferedPositionMs, videoStats ->
            _currentPositionMs.value = positionMs
            _durationMs.value = durationMs
            _bufferedPositionMs.value = bufferedPositionMs
            _videoStats.value = videoStats
        },
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
    private val playbackPreferenceResolver = ItemPlaybackPreferenceResolver(
        repository = itemPlaybackPreferenceRepository,
        getCurrentItemId = { playerSessionManager.sessionState.value.currentItemId },
        getCurrentSeriesId = { playerSessionManager.sessionState.value.mediaDetail?.item?.seriesId },
        scope = scope,
    )
    private val trackSelectionHelper = TrackSelectionHelper(
        preferencesStore = preferencesStore,
        getEngine = { playerSessionManager.engine },
        getUiState = { _uiState.value },
        updateUiState = { transform -> _uiState.update(transform) },
        getCurrentItemId = { playerSessionManager.sessionState.value.currentItemId },
        getCurrentSeriesId = { playerSessionManager.sessionState.value.mediaDetail?.item?.seriesId },
        playbackPreferenceResolver = playbackPreferenceResolver,
        scope = scope,
    )

    init {
        castManager.acquireConsumer()
        // Register the PiP transport bridge so the Activity can dispatch PiP
        // remote-action intents (play/pause/skip/next) to the active engine
        //. Cleared on reset() when playback ends.
        playerLifecycleManager.pipTransport = PipTransport { action ->
            val engine = playerSessionManager.engine ?: return@PipTransport
            when (action) {
                PipAction.PLAY -> engine.play()
                PipAction.PAUSE -> engine.pause()
                PipAction.SKIP_FORWARD -> {
                    val skip = _uiState.value.seekDurationMs
                    seekTo((engine.currentPositionMs + skip).coerceAtLeast(0L))
                }
                PipAction.SKIP_BACKWARD -> {
                    val skip = _uiState.value.seekDurationMs
                    seekTo((engine.currentPositionMs - skip).coerceAtLeast(0L))
                }
                PipAction.NEXT -> playNextEpisode()
            }
        }
        launch {
            preferencesStore.preferences.collect { prefs ->
                val oldPrefs = cachedPreferences
                cachedPreferences = prefs
                val itemId = playerSessionManager.sessionState.value.currentItemId
                val stored = itemId?.let { prefs.mediaStreamSelections[it] }
                // Skip the (≈95-field) state copy + collector re-emit when the
                // two flags haven't changed. Every UserPreferences emission
                // (dozens of unrelated pref writes trigger this) used to
                // allocate a fresh copy and re-emit to every uiState collector
                // even though hasAudioOverride/hasSubtitleOverride rarely
                // change. The surrounding blocks already guard every other
                // field with `if (_uiState.value.X != prefs.X)`; this is the
                // exception.
                val newAudio = stored?.audioStreamIndex != null
                val newSub = stored?.subtitleStreamIndex != null
                if (_uiState.value.hasAudioOverride != newAudio ||
                    _uiState.value.hasSubtitleOverride != newSub
                ) {
                    _uiState.update {
                        it.copy(hasAudioOverride = newAudio, hasSubtitleOverride = newSub)
                    }
                }
                val resolvedSubtitleStyle = prefs.resolvedSubtitleStyle(
                    isHdr = prefs.isHdrFromStreams(_uiState.value.mediaStreams),
                )
                if (_uiState.value.subtitleStyle != resolvedSubtitleStyle) {
                    _uiState.update { it.copy(subtitleStyle = resolvedSubtitleStyle) }
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
                if (_uiState.value.showTimeRemaining != prefs.showTimeRemaining) {
                    _uiState.update { it.copy(showTimeRemaining = prefs.showTimeRemaining) }
                }
                if (_uiState.value.tvZoomModePercent != prefs.tvZoomModePercent) {
                    _uiState.update { it.copy(tvZoomModePercent = prefs.tvZoomModePercent) }
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
                if (_uiState.value.videoAutoplayNext != prefs.videoAutoplayNext) {
                    _uiState.update { it.copy(videoAutoplayNext = prefs.videoAutoplayNext) }
                    autoplayController.setEnabled(prefs.videoAutoplayNext)
                }
                if (_uiState.value.autoPlayCountdownSec != prefs.autoPlayCountdownSec) {
                    _uiState.update { it.copy(autoPlayCountdownSec = prefs.autoPlayCountdownSec) }
                }
                // Default the Subtitle Manager's Search-tab language to the
                // user's preferred subtitle language (ISO 639-2/3, e.g. "eng").
                val searchLang = prefs.preferredSubtitleLanguage ?: "eng"
                if (_uiState.value.defaultSearchLanguage != searchLang) {
                    _uiState.update { it.copy(defaultSearchLanguage = searchLang) }
                }
                if (oldPrefs.volumeBoostEnabled != prefs.volumeBoostEnabled ||
                    oldPrefs.volumeBoostGain != prefs.volumeBoostGain ||
                    oldPrefs.equalizerSettings != prefs.equalizerSettings ||
                    oldPrefs.pauseOnAudioFocusLoss != prefs.pauseOnAudioFocusLoss) {
                    playerSessionManager.engine?.let {
                        updateConfigWithUiState()
                    }
                }
                // Duck on transient audio focus loss (phone calls). Folded into
                // this single preferences collector (was a duplicate collector)
                // so a pref write rebuilds the snapshot once, not twice.
                if (prefs.duckOnTransientFocusLoss && transientAudioFocusRequest == null) {
                    registerTransientFocusLossListener()
                } else if (!prefs.duckOnTransientFocusLoss && transientAudioFocusRequest != null) {
                    unregisterTransientFocusLossListener()
                }
            }
        }
        launch {
            // Pass-out protection: pause playback after N hours of no user interaction.
            var engineJob: kotlinx.coroutines.Job? = null
            playerSessionManager.engineFlow.collect { engine ->
                engineJob?.cancel()
                if (engine != null) {
                    // Expose whether a "next" action is available for the PiP window.
                    playerLifecycleManager.pipHasNext = mediaDetail?.item?.seriesId != null
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
            _uiState.flow.map { it.passOutProtectionHours }.distinctUntilChanged()
                .collectLatest { hours ->
                    if (hours <= 0) return@collectLatest
                    while (isActive) {
                        kotlinx.coroutines.delay(60_000)
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

        launch {
            var lastItemId: String? = null
            var lastSeriesId: String? = null
            playerSessionManager.sessionState.collect { session ->
                val itemId = session.currentItemId
                val seriesId = session.mediaDetail?.item?.seriesId
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
                            isLive = session.isLive,
                        )
                    }
                // Re-resolve per-item/series language preference when the
                // current item or series changes. The cached value feeds
                // TrackSelectionHelper and the series-pref toggles.
                if (itemId != lastItemId || seriesId != lastSeriesId) {
                    lastItemId = itemId
                    lastSeriesId = seriesId
                    trackSelectionHelper.refreshPlaybackPreferences()
                }
            }
        }

        // Reflect the resolved per-item/series language preference into UI
        // state so the track sheets can show the series-pref toggle state.
        launch {
            playbackPreferenceResolver.resolved.collect { pref ->
                // Dialogue Boost is resolved per-item: a stored rule
                // pins the strength; otherwise the effective default is OFF (NONE),
                // so the effect never silently carries across items. The global
                // setting is intentionally NOT used as the auto fallback here.
                val resolvedBoost = pref?.dialogueBoostStrength
                    ?: com.raulshma.jellyplay.core.model.EffectStrength.NONE
                _uiState.update {
                    it.copy(
                        hasSeriesAudioPref = pref?.scope == PlaybackPrefScope.SERIES && pref.audioLanguage != null,
                        hasSeriesSubtitlePref = pref?.scope == PlaybackPrefScope.SERIES && pref.subtitleLanguage != null,
                        hasSeriesDialogueBoostPref = pref?.scope == PlaybackPrefScope.SERIES && pref.dialogueBoostStrength != null,
                        dialogueBoostStrength = resolvedBoost,
                        dialogueBoostEnabled = resolvedBoost != com.raulshma.jellyplay.core.model.EffectStrength.NONE,
                    )
                }
                updateConfigWithUiState()
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
                        audioDelayMs = prefs.audioDelayMs,
                        decoderMode = prefs.decoderMode,
                        audioPassthrough = prefs.audioPassthrough,
                        subtitleStyle = prefs.resolvedSubtitleStyle(
                            isHdr = prefs.isHdrFromStreams(playerSessionManager.sessionState.value.mediaStreams),
                        ),
                        // Dialogue Boost defaults to OFF until the per-item resolver
                        // applies a stored rule. It does not inherit the
                        // global default, preventing cross-item bleed.
                        dialogueBoostEnabled = false,
                        dialogueBoostStrength = com.raulshma.jellyplay.core.model.EffectStrength.NONE,
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
                                // Guard against same-value updates (mirrors the isBuffering
                                // branch below) so a redundant isPlaying emission does not
                                // allocate a fresh 95-field uiState copy and invalidate
                                // every uiState collector.
                                _uiState.update { s ->
                                    if (s.isPlaying == isPlaying) s else s.copy(isPlaying = isPlaying)
                                }
                                syncPlayBridge.onIsPlayingChanged(isPlaying)
                                // Mirror play state so the Activity can render the correct
                                // play/pause icon on the PiP window.
                                playerLifecycleManager.setPlaying(isPlaying)
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
                                if (state == com.raulshma.jellyplay.feature.player.video.engine.EnginePlaybackState.ENDED) {
                                    handlePlaybackEnded()
                                }
                            } }
                            launch { engine.availableTracks.collect { trackSelectionHelper.updateTracksFromEngine() } }
                            launch { engine.errorFlow.collect { e -> _uiState.update { s -> s.copy(playerError = e, showPlaybackErrorDialog = true) } } }
                            // Buffering watchdog: if the engine never reaches
                            // READY within BUFFERING_TIMEOUT_MS during the
                            // *initial* buffer (before first READY), surface the
                            // playback-error dialog so the user can retry with a
                            // different engine. Without this, ExoPlayer can sit
                            // in STATE_BUFFERING forever for undecodable content
                            // (e.g. wrong extractor on a misnamed download) — no
                            // PlaybackException is raised, so the errorFlow
                            // collector above never fires and the spinner spins
                            // indefinitely. Only armed before first READY so it
                            // does not trigger on legitimate mid-playback rebuffer
                            // (seeks, quality switches, network blips).
                            launch {
                                var hasReachedReady = false
                                var watchdogJob: kotlinx.coroutines.Job? = null
                                engine.playbackState.collect { state ->
                                    // Live streams legitimately buffer longer and
                                    // rebuffer mid-playback; the 20s watchdog
                                    // would otherwise surface a false "failed to
                                    // start" error dialog for slow live tuners.
                                    // Genuine decode errors still surface via
                                    // errorFlow (onPlayerError) above.
                                    if (_uiState.value.isLive) return@collect
                                    when (state) {
                                        com.raulshma.jellyplay.feature.player.video.engine.EnginePlaybackState.BUFFERING -> {
                                            if (!hasReachedReady && watchdogJob == null) {
                                                watchdogJob = launch {
                                                    delay(BUFFERING_TIMEOUT_MS)
                                                    if (!hasReachedReady) {
                                                        _uiState.update { s -> s.copy(
                                                            playerError = "Playback failed to start. Try a different player engine.",
                                                            showPlaybackErrorDialog = true,
                                                            isBuffering = false,
                                                        ) }
                                                    }
                                                }
                                            }
                                        }
                                        com.raulshma.jellyplay.feature.player.video.engine.EnginePlaybackState.READY -> {
                                            hasReachedReady = true
                                            watchdogJob?.cancel()
                                            watchdogJob = null
                                        }
                                        else -> {
                                            watchdogJob?.cancel()
                                            watchdogJob = null
                                        }
                                    }
                                }
                            }
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
     * Offline resume: the offline entry points (Downloads, OfflineLibrary,
     * OfflineSeries, deep links, remote control, mini-player) all navigate with
     * `startPositionTicks = 0`. When no explicit position was requested and the
     * item is a completed download, fall back to the last-known position stored
     * on the downloaded item (seeded from server UserData and updated while
     * watching offline). Streaming keeps the caller-provided value.
     *
     * Extracted and `suspend` so it is unit-testable in isolation; in
     * [initializeInternal] it runs inside the load coroutine.
     */
    internal suspend fun resolveOfflineResumeTicks(itemId: String, startPositionTicks: Long): Long {
        if (startPositionTicks != 0L) return startPositionTicks
        val download = downloadRepository.getDownloadByMediaItemId(itemId)
        if (download?.status != com.raulshma.jellyplay.core.model.DownloadStatus.COMPLETED) return 0L
        return offlineRepository.getOfflineItem(itemId)?.playbackPositionTicks
            ?.takeIf { it > 0L } ?: 0L
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
        // Mirror progress into the offline store so downloads render watched /
        // resume state while offline. No-op for non-downloaded items.
        val durationMs = playerSessionManager.engine?.durationMs ?: 0L
        val positionTicks = positionMs * 10_000L // ms → ticks
        val percentage = if (durationMs > 0L) {
            (positionMs.toDouble() / durationMs.toDouble() * 100.0).coerceIn(0.0, 100.0)
        } else 0.0
        launch {
            offlineRepository.updatePlaybackProgress(itemId, positionTicks, percentage, isPlayed = false)
        }
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
        autoplayController.resetForNewItem()
        _uiState.update { it.copy(autoplayCancelled = false) }
        lastSeekPositionMs = null
        lastSeekTimestamp = 0L
        trackSelectionHelper.setPendingStreams(subtitleStreamIndex, audioStreamIndex)

        // "Play On" routing: if a Jellyfin remote session is connected (via the
        // Home FAB "Play On" entry), send the video to that session instead of
        // playing locally — mirrors official Jellyfin clients where picking a
        // device routes subsequent plays to it. The Home "Play On" VM uses the
        // same strategy instance directly, so this connection is independent of
        // the video player's own CastManager cast state.
        if (jellyfinRemotePlayCastStrategy.isConnected.value) {
            jellyfinRemotePlayCastStrategy.loadMedia(
                itemId = itemId,
                startPositionMs = startPositionTicks / 10_000,
                mediaSourceId = mediaSourceId,
                audioStreamIndex = audioStreamIndex,
                subtitleStreamIndex = subtitleStreamIndex,
            )
            return
        }

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
                showTimeRemaining = prefs.showTimeRemaining,
                tvZoomModePercent = prefs.tvZoomModePercent,
                keepScreenOnDuringVideo = prefs.keepScreenOnDuringVideo,
                streamingQuality = prefs.streamingQuality,
                adaptiveBitrateEnabled = prefs.adaptiveBitrateEnabled,
                playbackMode = prefs.playbackMode,
                videoAutoplayNext = prefs.videoAutoplayNext,
                autoPlayCountdownSec = prefs.autoPlayCountdownSec,
            ) }
            autoplayController.setEnabled(prefs.videoAutoplayNext)

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

            // Offline resume: the offline entry points (Downloads, OfflineLibrary,
            // OfflineSeries, deep links, remote control, mini-player) all navigate with
            // startPositionTicks = 0. When no explicit position was requested and the item
            // is a completed download, fall back to the last-known position stored on the
            // downloaded item (seeded from server UserData and updated while watching
            // offline). Streaming keeps the caller-provided value. Composes with
            // resolveStartTicksAfterProcessDeath, which only ever advances the position.
            val resolvedStartTicks = resolveOfflineResumeTicks(itemId, startPositionTicks)

            playerSessionManager.loadMedia(itemId, mediaSourceId, resolvedStartTicks)

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
                    } else {
                        // No local trickplay bundled with the download (the
                        // detached trickplay fetch failed or the server didn't
                        // have it at download time). Fall back to the server's
                        // trickplay manifest and cache fetched tiles into the
                        // download's trickplay dir, so the next offline session
                        // reads them locally via [initializeLocal] above.
                        val cacheDir = java.io.File(java.io.File(downloadPath).parentFile, "trickplay")
                        val serverInfo = mediaRepository.getMediaDetail(itemId)
                            .getOrNull()
                            ?.mediaSources
                            ?.firstOrNull()
                            ?.trickplayInfo
                        if (serverInfo != null) {
                            cacheDir.mkdirs()
                            trickplayManager.initializeWithCache(itemId, serverInfo, cacheDir)
                            _uiState.update { it.copy(trickplayInfo = serverInfo) }
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

    // --- Per-series playback-language preferences -------------------------------
    // The headline use case is "remember the audio/subtitle language for this
    // series". Saving writes a SERIES-scope row, preserving the other language
    // if already set; deleting clears just the relevant field. The resolver is
    // refreshed afterwards so the cached value (read by TrackSelectionHelper)
    // and the sheet toggle state stay in sync.

    /**
     * Saves/clears a per-series preferred audio language. Pass the language of
     * the currently-selected audio track to remember it, or null to forget.
     * No-op when the current item has no series (e.g. a standalone movie).
     */
    fun setSeriesAudioLanguagePreference(language: String?) {
        val seriesId = playerSessionManager.sessionState.value.mediaDetail?.item?.seriesId ?: return
        launch {
            // null = "forget": use the explicit clear so save()'s "null ⇒ preserve"
            // semantics don't silently keep the old language forever.
            if (language == null) {
                itemPlaybackPreferenceRepository.clearAudioLanguage(PlaybackPrefScope.SERIES, seriesId)
            } else {
                itemPlaybackPreferenceRepository.save(
                    scope = PlaybackPrefScope.SERIES,
                    key = seriesId,
                    audioLanguage = language,
                )
            }
            trackSelectionHelper.refreshPlaybackPreferences()
        }
    }

    /**
     * Saves/clears a per-series preferred subtitle language. Pass the language
     * of the currently-selected subtitle track to remember it, or null to
     * forget. No-op when the current item has no series.
     */
    fun setSeriesSubtitleLanguagePreference(language: String?) {
        val seriesId = playerSessionManager.sessionState.value.mediaDetail?.item?.seriesId ?: return
        launch {
            if (language == null) {
                itemPlaybackPreferenceRepository.clearSubtitleLanguage(PlaybackPrefScope.SERIES, seriesId)
            } else {
                itemPlaybackPreferenceRepository.save(
                    scope = PlaybackPrefScope.SERIES,
                    key = seriesId,
                    subtitleLanguage = language,
                )
            }
            trackSelectionHelper.refreshPlaybackPreferences()
        }
    }
    // -----------------------------------------------------------------------

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
        // Dialogue Boost is persisted per-item/series: toggling on
        // pins MODERATE for this item/series, toggling off clears it (resolves to
        // NONE). It does not touch the global setting, so it never bleeds across
        // unrelated items.
        val newVal = !_uiState.value.dialogueBoostEnabled
        val target = if (newVal) com.raulshma.jellyplay.core.model.EffectStrength.MODERATE
            else com.raulshma.jellyplay.core.model.EffectStrength.NONE
        setDialogueBoostStrength(target)
    }

    fun setDialogueBoostStrength(strength: com.raulshma.jellyplay.core.model.EffectStrength) {
        _uiState.update {
            it.copy(
                dialogueBoostStrength = strength,
                dialogueBoostEnabled = strength != com.raulshma.jellyplay.core.model.EffectStrength.NONE,
            )
        }
        updateConfigWithUiState()
        launch {
            val seriesId = playerSessionManager.sessionState.value.mediaDetail?.item?.seriesId
            val itemId = playerSessionManager.sessionState.value.currentItemId
            // Prefer SERIES scope (applies to all episodes), fall back to ITEM.
            val scopeKey = seriesId ?: itemId ?: return@launch
            val prefScope = if (seriesId != null) PlaybackPrefScope.SERIES else PlaybackPrefScope.ITEM
            if (strength == com.raulshma.jellyplay.core.model.EffectStrength.NONE) {
                itemPlaybackPreferenceRepository.clearDialogueBoostStrength(prefScope, scopeKey)
            } else {
                itemPlaybackPreferenceRepository.save(
                    scope = prefScope,
                    key = scopeKey,
                    dialogueBoostStrength = strength,
                )
            }
            trackSelectionHelper.refreshPlaybackPreferences()
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
     * engines.
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

        // Stop-report the *current* server session before the swap: reloadPlayback
        // overwrites sessionState.playSessionId with the new server id, so without
        // this the previous session is never reported stopped (the server would
        // see start(idA) → progress(idB) → stop(idB), orphaning idA — the same
        // desync class the M19 currentPlaySessionId resolver prevents elsewhere).
        reportCurrentPlaybackStopped()
        progressReporter.cancelJobs()

        val resolved = playerSessionManager.reloadPlayback(mode, quality, pos) ?: return
        afterEngineReloadRebuildSessionAndTracking()

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
                reportCurrentPlaybackStopped()
                progressReporter.cancelJobs()
                playerSessionManager.reloadPlayback(
                    PlaybackMode.FORCE_TRANSCODE, quality,
                    playerSessionManager.engine?.currentPositionMs ?: pos,
                )
                afterEngineReloadRebuildSessionAndTracking()
            }
        }
    }

    /**
     * After a same-item engine reload ([reloadPlaybackForMode], [retryWithEngine])
     * the previous engine — whose [MediaEngine.positionFlow] the position-tracking
     * job was collecting — has been released, so the job goes silent. The media
     * session was also bound to the released engine's player. Rebuild both so the
     * seek bar, buffer bar, stats overlay, segment auto-skip and the system media
     * notification track the new engine. (Every other reload path — initialize /
     * cinema / retry — already does this; consolidating it here keeps any future
     * engine swap covered the same way.)
     */
    private fun afterEngineReloadRebuildSessionAndTracking() {
        val sessionState = playerSessionManager.sessionState.value
        createVideoMediaSession(
            sessionState.currentItemId ?: "",
            sessionState.title,
            sessionState.subtitle,
        )
        progressReporter.startPositionTracking()
        progressReporter.startProgressReporting()
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
            afterEngineReloadRebuildSessionAndTracking()
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
        val config = EngineConfigBuilder.build(_uiState.value, equalizerEnabled, cachedPreferences)
        playerSessionManager.engine?.updateConfig(config)
    }

    /**
     * Backing flow for the debounced config-sync. Slider drags fire 60–120
     * value-changed callbacks/sec; previously each launched a new coroutine
     * and cancelled the previous (allocating a DispatchedContinuation per call
     * and walking the job tree on each cancel). A SharedFlow + debounce emits
     * one coroutine that only fires after the drag settles.
     */
    private val configChangeIntent = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
    )

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private val configSyncJob: Job = launch {
        configChangeIntent.debounce(CONFIG_SYNC_DEBOUNCE_MS).collect {
            updateConfigWithUiState()
        }
    }

    private fun updateConfigWithUiStateDebounced() {
        configChangeIntent.tryEmit(Unit)
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

    fun cancelAutoplay() {
        autoplayController.cancel()
        _uiState.update { it.copy(autoplayCancelled = true) }
    }

    private fun handlePlaybackEnded() {
        // Live streams are infinite: an ENDED emission (ExoPlayer stall/EOF,
        // MPV END_FILE, VLC EndReached) must never close the player — it is
        // almost always a transient rebuffer/drop, not true end-of-content.
        // Suppressing here lets the engine keep its live window and the user
        // stays in playback. Without this gate a live channel closes within
        // seconds of starting.
        if (_uiState.value.isLive) return
        val next = _uiState.value.nextEpisode
        if (autoplayController.shouldAutoPlayNext(next)) {
            playNextEpisode()
        } else {
            if (cinemaIntroContext != null) {
                advanceCinemaIntro()
            } else {
                _closePlayer.trySend(Unit)
            }
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
        val state = positionAwareState()
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
        val state = positionAwareState()

        if (state.isOutroNearEnd && autoplayController.canSkipToNext(state.nextEpisode)) {
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
        imageUrlProvider.getImageUrl(itemId, maxWidth = maxWidth)

    // region Subtitle Manager — delegates to [subtitleManager] (extracted collaborator)

    fun loadRemoteSubtitles() = subtitleManager.loadRemoteSubtitles()

    fun downloadSubtitle(subtitleInfo: com.raulshma.jellyplay.core.model.RemoteSubtitleInfo) =
        subtitleManager.downloadSubtitle(subtitleInfo)

    fun addLocalSubtitle(uri: Uri, fileName: String) = subtitleManager.addLocalSubtitle(uri, fileName)

    /**
     * Resets the Subtitle Manager's search/cultures state. Called when the sheet
     * opens (or the playback item changes) so results from a previous item don't
     * leak into a new one (H4). Cultures are reloaded on demand since they may
     * be item-scoped on some servers.
     */
    fun resetSubtitleManagerState() = subtitleManager.resetSubtitleManagerState()

    /**
     * Loads the language cultures the server understands for subtitle
     * upload/search selection. Idempotent: a no-op once cultures are already
     * populated for the current item (e.g. across tab switches / reopens).
     */
    fun loadSubtitleCultures() = subtitleManager.loadSubtitleCultures()

    /**
     * Language-scoped remote subtitle search (OpenSubtitles via the server).
     * Results populate the Search tab and are kept separate from the Download
     * tab's server-default list. A failure surfaces as [VideoPlayerUiState.subtitleSearchError]
     * (distinct from an empty result) so the UI can invite retry rather than
     * implying "no subtitles exist" (H3).
     */
    fun searchRemoteSubtitles(language: String) = subtitleManager.searchRemoteSubtitles(language)

    /**
     * Uploads a local subtitle file to the current item, then reloads the
     * media detail so the new stream appears in the track list — mirroring
     * [downloadSubtitle]'s refresh and the editor's upload path. See
     * [SubtitleManager.uploadSubtitle] for the size-cap rationale.
     */
    fun uploadSubtitle(uri: Uri, fileName: String, language: String?, isForced: Boolean, isHearingImpaired: Boolean) =
        subtitleManager.uploadSubtitle(uri, fileName, language, isForced, isHearingImpaired)

    /**
     * Re-applies [mediaDetail] and refreshes the shared source/stream/aspect-ratio
     * UiState fields after a subtitle download/upload adds a new stream. The
     * duplicated inline block previously lived separately in [downloadSubtitle]
     * and [uploadSubtitle]; folding it here lets [SubtitleManager] trigger the
     * refresh via its [onMediaDetailRefreshed] callback without a hard VM
     * reference.
     */
    private fun applyMediaDetailAndSourceState(detail: com.raulshma.jellyplay.core.model.MediaDetail) {
        applyMediaDetail(detail)
        val source = detail.mediaSources.firstOrNull()
        val streams = source?.mediaStreams ?: emptyList()
        _uiState.update { it.copy(
            currentMediaSource = source,
            mediaStreams = streams,
            detectedAspectRatio = detectAspectRatio(streams),
        ) }
    }

    // endregion

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
        // the handoff does not silently drop audio/subtitle/quality.
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
            adaptiveBitrateManager.resolveEffectiveMaxBitrate()?.toInt()
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

    val backgroundCastingEnabled: Boolean
        get() = preferencesStore.preferences.value.backgroundCastingEnabled

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
        autoplayController.setEnabled(false)
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
                showTimeRemaining = currentState.showTimeRemaining,
                tvZoomModePercent = currentState.tvZoomModePercent,
                keepScreenOnDuringVideo = currentState.keepScreenOnDuringVideo,
                subtitleStyle = currentState.subtitleStyle,
                // Reset per-item dialogue boost so it doesn't bleed into the next
                // item before the resolver re-applies the per-item rule.
                dialogueBoostEnabled = false,
                dialogueBoostStrength = com.raulshma.jellyplay.core.model.EffectStrength.NONE,
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
