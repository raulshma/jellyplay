package com.raulshma.jellyplay.feature.player.video

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.util.Rational
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import com.raulshma.jellyplay.core.data.playback.PlayerAudioLifecycle
import com.raulshma.jellyplay.core.data.playback.PlaybackSessionManager
import com.raulshma.jellyplay.core.data.playback.PipAction
import com.raulshma.jellyplay.core.data.playback.PipController
import com.raulshma.jellyplay.core.data.playback.PipTransport
import com.raulshma.jellyplay.core.data.playback.PlayerLifecycleManager
import com.raulshma.jellyplay.core.data.playback.SleepTimerManager
import com.raulshma.jellyplay.core.data.playback.VideoMiniPlayerState
import com.raulshma.jellyplay.core.data.cast.CastManager
import com.raulshma.jellyplay.core.data.cast.CastMediaOptions
import com.raulshma.jellyplay.core.data.network.NetworkMonitor
import com.raulshma.jellyplay.core.data.playback.AdaptiveBitrateManager
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.ItemPlaybackPreferenceRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.OfflinePlaybackFacade
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayManager
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.SyncPlayRepeatMode
import com.raulshma.jellyplay.core.model.SyncPlayShuffleMode
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerAggregate
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerAggregateStore
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
import com.raulshma.jellyplay.core.model.StreamType
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.model.TrackType
import com.raulshma.jellyplay.core.model.isAudioType
import com.raulshma.jellyplay.core.model.isMusicTrack
import com.raulshma.jellyplay.core.ui.feedback.UserMessageBus
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.feature.player.video.R
import com.raulshma.jellyplay.feature.player.video.engine.AspectRatio
import com.raulshma.jellyplay.feature.player.video.engine.EnginePlaybackState
import com.raulshma.jellyplay.feature.player.video.engine.EngineVideoStats
import com.raulshma.jellyplay.feature.player.video.engine.SegmentCalculator
import com.raulshma.jellyplay.feature.player.video.engine.SegmentCalculatorInput
import com.raulshma.jellyplay.feature.player.video.engine.SubtitleSource
import com.raulshma.jellyplay.feature.player.video.state.EpisodeBrowserState
import com.raulshma.jellyplay.feature.player.video.state.GesturePrefsState
import com.raulshma.jellyplay.feature.player.video.state.PlayerUiPrefsState
import com.raulshma.jellyplay.feature.player.video.state.SegmentState
import com.raulshma.jellyplay.feature.player.video.state.VideoFxState
import com.raulshma.jellyplay.feature.player.video.subtitle.FontProvider
import com.raulshma.jellyplay.feature.player.video.subtitle.SubtitleMimeMapper
import com.raulshma.jellyplay.core.model.VideoEffectsConfig

import com.raulshma.jellyplay.feature.player.video.trickplay.TrickplayManager
import com.raulshma.jellyplay.core.data.remote.ActivePlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Minimum resolved duration (ms) before smart-download auto-cleanup may fire. */
private const val MIN_DURATION_FOR_SMART_DELETE_MS = 5 * 60 * 1000L

// SavedStateHandle keys for surviving process death. The in-stream
// playback position, the item it belongs to, the server session id, and the
// epoch at which the position was last persisted are stored so playback
// resumes from the user's last seek rather than the original entry point, and
// so the eventual stop-report matches the start. The timestamp lets a restore
// reject a position that is too old to be a trustworthy "continue from here"
// (see STALE_POSITION_THRESHOLD_MS) — the primary defense is the nav-route
// strip, but a stale SavedStateHandle position is the last-resort signal that
// the player's in-memory state is gone and auto-resume would land mid-stream
// on an episode the user moved past via auto-advance.
private const val SAVED_KEY_ITEM_ID = "video_player.saved_item_id"
private const val SAVED_KEY_POSITION_MS = "video_player.saved_position_ms"
private const val SAVED_KEY_PLAY_SESSION_ID = "video_player.saved_play_session_id"
private const val SAVED_KEY_POSITION_PERSISTED_AT = "video_player.saved_position_persisted_at"
private const val POSITION_PERSIST_MIN_INTERVAL_MS = 5_000L
/**
 * Quiet-period for coalescing the *offline-mirror* DB write during rapid
 * scrubbing: [seekTo] fires one per seek gesture, and the immediate
 * `recordProgress` launches would queue against Room's executor. Only the DB
 * mirror is coalesced — the SavedStateHandle writes stay immediate so explicit
 * seek positions still survive process death. The position tick's throttled
 * mirror write (`persistPlaybackPosition(force=false)`) catches up within
 * seconds, so a dropped coalesced write is never lost for long.
 */
private const val SEEK_PROGRESS_COALESCE_MS = 500L
/**
 * A persisted position older than this is treated as stale on a process-death
 * restore and ignored: the user backgrounded the app long enough that
 * auto-resuming mid-stream (potentially on an episode they auto-advanced past)
 * is worse than landing on Home and continuing via the "Continue Watching" row.
 * Matches the ~1h threshold users report as the trigger; well above any real
 * short backgrounding (notification reply, brief app switch).
 */
private const val STALE_POSITION_THRESHOLD_MS = 60L * 60L * 1000L

/**
 * Pure resume-position resolver extracted from
 * [VideoPlayerViewModel.resolveStartTicksAfterProcessDeath] so the staleness +
 * "only advance forward" rules are unit-testable without a ViewModel.
 *
 * Rules:
 * - No persisted position (`savedPosMs <= 0`): keep the entry point.
 * - Persisted position too old (`persistedAtMs > 0` and older than
 * [staleThresholdMs]): keep the entry point. A zero/missing timestamp is
 * treated as fresh so a normal resume-from-background keeps working.
 * - Otherwise resume at the persisted position, but never below the deliberate
 * entry point (auto-advance may have moved the user forward of the route's
 * original ticks; rewinding would jump back unexpectedly).
 */
internal fun resolveResumeTicks(
    savedPosMs: Long,
    persistedAtMs: Long,
    nowMs: Long,
    entryPointTicks: Long,
    staleThresholdMs: Long = STALE_POSITION_THRESHOLD_MS,
): Long {
    if (savedPosMs <= 0L) return entryPointTicks
    if (persistedAtMs > 0L && nowMs - persistedAtMs > staleThresholdMs) return entryPointTicks
    val savedTicks = savedPosMs * 10_000
    return if (savedTicks > entryPointTicks) savedTicks else entryPointTicks
}
/** Debounce window for engine config syncs driven by slider drags. */
private const val CONFIG_SYNC_DEBOUNCE_MS = 150L

/**
 * Debounce window for the engine apply of a subtitle-delay change. A delay
 * change forces ExoPlayer/LibVLC to reload the media item to re-parse cues
 * through the offset wrapper (a rebuffer), so a burst of fine-tune nudges is
 * coalesced into a single reload. The in-memory value (and thus the overlay
 * readout) updates immediately; only the expensive engine push waits.
 */
private const val SUBTITLE_DELAY_APPLY_DEBOUNCE_MS = 500L

/**
 * Segment-relevant slice of [VideoPlayerUiState], used by
 * [VideoPlayerViewModel.segmentOverlayState]. Projecting only these fields
 * (and `distinctUntilChanged`-ing them) means a 4 Hz [currentPositionMs][VideoPlayerViewModel.currentPositionMs]
 * tick does not allocate a fresh `VideoPlayerUiState.copy(...)` or
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
        segments = state.segmentState.segments,
        chapters = state.chapters,
        segmentBehaviors = state.segmentState.segmentBehaviors,
        autoplayCancelled = state.autoplay.autoplayCancelled,
        isInSyncPlaySession = state.isInSyncPlaySession,
        nextEpisode = state.episodes.nextEpisode,
        seriesId = state.media.seriesId,
    )

    /**
     * Builds the [SegmentOverlayState] for a given live position/duration.
     *
     * Calls [SegmentCalculator] directly with the projected inputs — no
     * throwaway [VideoPlayerUiState] allocation. Runs only when the
     * projection changes — not on every 4 Hz tick.
     */
    fun computeOverlay(positionMs: Long, durationMs: Long): SegmentOverlayState {
        val input = SegmentCalculatorInput(
            segments = segments,
            chapters = chapters,
            segmentBehaviors = segmentBehaviors,
            durationMs = durationMs,
            autoplayCancelled = autoplayCancelled,
            isInSyncPlaySession = isInSyncPlaySession,
            hasNextEpisode = nextEpisode != null,
            seriesId = seriesId,
        )
        val activeSegment = SegmentCalculator.computeActiveSegment(input, positionMs)
        return SegmentOverlayState(
            activeSegment = activeSegment,
            activeSegmentBehavior = activeSegment?.let {
                SegmentCalculator.behaviorForType(input, it.type)
            } ?: SegmentBehavior.IGNORE,
            isInIntro = SegmentCalculator.isInSegmentType(input, positionMs, MediaSegmentType.INTRO),
            isInCredits = SegmentCalculator.isInSegmentType(input, positionMs, MediaSegmentType.OUTRO),
            shouldShowUpNext = SegmentCalculator.shouldShowUpNext(input, positionMs),
        )
    }
}

@HiltViewModel
class VideoPlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
    private val subtitleProviderRepository: com.raulshma.jellyplay.core.data.repository.SubtitleProviderRepository,
    private val streamingSubtitleStore: com.raulshma.jellyplay.core.data.repository.StreamingSubtitleStore,
    private val imageUrlProvider: ImageUrlProvider,
    private val downloadRepository: DownloadRepository,
    private val offlineRepository: OfflineRepository,
    private val offlinePlaybackFacade: OfflinePlaybackFacade,
    /**
     * The deep "playback source resolver" — single owner of the
     * completed-download predicate ([resolveOfflineResumeTicks] pivots onto
     * [com.raulshma.jellyplay.core.data.playback.PlaybackSourceResolver.resolveStartPositionTicks]
     * and [PlayerSessionManager] consumes
     * [com.raulshma.jellyplay.core.data.playback.PlaybackSourceResolver.resolveUsableDownload]).
     */
    private val playbackSourceResolver: com.raulshma.jellyplay.core.data.playback.PlaybackSourceResolver,
    /**
     * The consolidated series seasons/episodes snapshot. [resolveSeasons] and
     * [resolveEpisodes] delegate here (online and offline), so the player's
     * episode discovery shares the same single-flight + cache as the detail
     * screen and the download paths. Offline-ness is read per-session from
     * [playerSessionManager] and passed as a parameter.
     */
    private val episodeCatalogue: com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogue,
    private val itemPlaybackPreferenceRepository: ItemPlaybackPreferenceRepository,
    private val aggregateStore: VideoPlayerAggregateStore,
    private val engineStore: com.raulshma.jellyplay.core.datastore.engine.PlayerEngineStore,
    private val subtitleStore: com.raulshma.jellyplay.core.datastore.subtitle.SubtitleLanguageStore,
    private val playbackStore: com.raulshma.jellyplay.core.datastore.playback.PlaybackStore,
    private val audioStore: com.raulshma.jellyplay.core.datastore.audio.AudioStore,
    private val audioEffectsStore: com.raulshma.jellyplay.core.datastore.audioeffects.AudioEffectsStore,
    private val videoPlayerStore: com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerStore,
    private val securityStore: com.raulshma.jellyplay.core.datastore.security.SecurityStore,
    private val syncPlayCastStore: com.raulshma.jellyplay.core.datastore.syncplaycast.SyncPlayCastStore,
    private val downloadsStore: com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore,
    private val appearanceStore: com.raulshma.jellyplay.core.datastore.appearance.AppearanceStore,
    private val networkOfflineStore: com.raulshma.jellyplay.core.datastore.network.NetworkOfflineStore,
    private val sessionManager: PlaybackSessionManager,
    // Public: the screen's cast UI (route button, disconnect handler) needs the
    // manager directly; every playback-side use stays private above.
    val castManager: CastManager,
    private val jellyfinRemotePlayCastStrategy: com.raulshma.jellyplay.core.data.cast.remote.JellyfinRemotePlayCastStrategy,
    private val syncPlayManager: SyncPlayManager,
    private val adaptiveBitrateManager: AdaptiveBitrateManager,
    private val networkMonitor: NetworkMonitor,
    private val activePlayerController: ActivePlayerController,
    val playerLifecycleManager: PlayerLifecycleManager,
    val pipController: PipController,
    val videoMiniPlayerState: VideoMiniPlayerState,
    private val sleepTimerManager: SleepTimerManager,
    private val userMessageBus: UserMessageBus,
    private val playerEngineFactory: com.raulshma.jellyplay.feature.player.video.engine.PlayerEngineFactory,
    // Public for the screen: the zoom-safe Compose overlay consumes the same
    // singleton (LRU typeface cache + startup prewarm) instead of building a
    // private FontProvider per composition.
    val fontProvider: FontProvider,
    private val savedStateHandle: SavedStateHandle,
    private val subtitlePreviewRepository: com.raulshma.jellyplay.feature.player.video.subtitle.SubtitlePreviewRepository,
    private val userDataMutator: com.raulshma.jellyplay.core.data.repository.UserDataMutator,
) : JellyPlayViewModel() {

    private val _uiState = stateFlow(VideoPlayerUiState())
    val uiState: StateFlow<VideoPlayerUiState> = _uiState.flow

    /** In-flight subtitle-preview cue load; cancelled on track change / sheet close. */
    private var subtitlePreviewLoadJob: Job? = null

    // --- High-frequency playback streams ---------------------------------------
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
     * not allocate a fresh `VideoPlayerUiState.copy(...)` and re-run
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

    /**
     * Fires once when playback resumes from a saved position, so the screen can
     * surface a transient "Resumed — Restart" affordance. Carries
     * the resumed position in ms so the chip can label it. `null`/0 means "no
     * reminder pending".
     */
    private val _resumeReminder = kotlinx.coroutines.flow.MutableSharedFlow<Long>(
        extraBufferCapacity = 1,
    )
    val resumeReminder: kotlinx.coroutines.flow.SharedFlow<Long> = _resumeReminder

    private val playerSessionManager = PlayerSessionManager(
        context = context,
        scope = scope,
        mediaRepository = mediaRepository,
        playbackRepository = playbackRepository,
        downloadRepository = downloadRepository,
        offlineRepository = offlineRepository,
        aggregateStore = aggregateStore,
        playerLifecycleManager = playerLifecycleManager,
        adaptiveBitrateManager = adaptiveBitrateManager,
        playerEngineFactory = playerEngineFactory,
        pipController = pipController,
        playbackSourceResolver = playbackSourceResolver,
        streamingSubtitleStore = streamingSubtitleStore,
    )

    // ── Engine-event orchestration ──────────────────────────────────────────
    // The coordinator owns the engine-event *policies* (guarded play/buffering
    // mirrors, the FORCE_DIRECT_PLAY → transcode one-shot fallback latch, the
    // initial-buffering watchdog, subtitle toasts, pass-out protection) and
    // emits [EngineDecision]s; the fan-out collectors started by
    // [startEngineEventCoordinatorOutputs] execute them against uiState and
    // this VM's collaborators. A `var` because performRelease() disposes it
    // and this Activity-scoped VM is reused across media: the next
    // initializeInternal re-arms it via ensureEngineEventCoordinatorActive().
    private var engineEventCoordinator: EngineEventCoordinator =
        createEngineEventCoordinator()

    private fun createEngineEventCoordinator() = EngineEventCoordinator(
        scope = scope,
        engineFlow = playerSessionManager.engineFlow,
        getPlaybackMode = { _uiState.value.uiPrefs.playbackMode },
        directPlayFallbackNotice = { errorText ->
            context.getString(R.string.player_direct_play_fallback, errorText)
        },
        passOutHours = _uiState.flow.map { it.uiPrefs.passOutProtectionHours }.distinctUntilChanged(),
    )

    /** Fan-out collectors for the coordinator's mirrors + decisions. */
    private var engineEventOutputsJob: Job? = null

    // @Volatile: written from launched coroutines (applyMediaDetail) and read
    // cross-coroutine (playNextEpisode); without it readers can see stale null.
    @Volatile
    private var mediaDetail: MediaDetail? = null

    private var equalizerEnabled: Boolean = false

    /**
     * Single resolved playback-session id. The server issues its own id
     * via the `PlaybackInfo` endpoint (stored in [PlayerSessionState.playSessionId]);
     * [PlaybackSession.playSessionId] is the locally-allocated UUID fallback. Previously
     * start/stop reports read the local UUID directly while progress reports
     * read `sessionState.playSessionId ?: playSessionId`, so the two could
     * desync (start reported id A, stop reported id B). Routing every report
     * and the SavedStateHandle persist through this resolver guarantees a
     * single value is used for the whole session lifecycle.
     */
    private val currentPlaySessionId: String
        get() = playerSessionManager.sessionState.value.playSessionId ?: playbackSession.playSessionId
    private val autoplayController = AutoPlayController()
    // @Volatile: written by the init collector, read off-Main (e.g. from
    // reportCurrentPlaybackStopped launched on Default).
    @Volatile
    private var cachedAggregate: VideoPlayerAggregate = VideoPlayerAggregate()

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

    // @Volatile: written by initializeInternal launch + advanceCinemaIntro,
    // read from handlePlaybackEnded / skipIntro / setVideoEffects.
    @Volatile
    private var cinemaIntroContext: CinemaIntroContext? = null

    private val trickplayManager = TrickplayManager(
        playbackRepository = playbackRepository,
        lowRamDevice = run {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
            am?.let { it.isLowRamDevice || it.memoryClass <= 256 } ?: false
        },
    )
    internal val subtitles = SubtitleManager(
        context = context,
        playbackRepository = playbackRepository,
        mediaRepository = mediaRepository,
        subtitleProviderRepository = subtitleProviderRepository,
        streamingSubtitleStore = streamingSubtitleStore,
        userMessageBus = userMessageBus,
        scope = scope,
        addExternalSubtitle = { playerSessionManager.addExternalSubtitle(it) },
        getMediaStreams = { _uiState.value.media.mediaStreams },
        getCurrentItemId = { playerSessionManager.sessionState.value.currentItemId },
        onMediaDetailRefreshed = { detail -> applyMediaDetailAndSourceState(detail) },
        getCurrentMediaDetail = { mediaDetail },
    )
    internal val sleepTimer = SleepTimerController(
        sleepTimerManager = sleepTimerManager,
        audioStore = audioStore,
        scope = scope,
        getEngine = { playerSessionManager.engine },
        isMuted = { _uiState.value.isMuted },
    )
    internal val abRepeat = AbRepeatController(
        scope = scope,
        getEngine = { playerSessionManager.engine },
        positionFlow = currentPositionMs,
    ).also { it.start() }
    internal val cast = PlayerCastController(
        castManager = castManager,
        playbackRepository = playbackRepository,
        adaptiveBitrateManager = adaptiveBitrateManager,
        syncPlayCastStore = syncPlayCastStore,
        getEngine = { playerSessionManager.engine },
        getCurrentPlaybackMode = { _uiState.value.uiPrefs.playbackMode },
        getSessionState = { playerSessionManager.sessionState.value },
    )
    private val settingsProjector = SettingsProjector(
        getUiState = { _uiState.value },
        updateUiState = { transform -> _uiState.update(transform) },
        getItemId = { playerSessionManager.sessionState.value.currentItemId },
        getMediaStreams = { _uiState.value.media.mediaStreams },
    )
    private val mediaSessionController = MediaSessionController(
        context = context,
        sessionManager = sessionManager,
        getPlayer = { playerSessionManager.engine?.underlyingPlayer },
        getImageUrl = { itemId, maxWidth -> playbackRepository.getImageUrl(itemId = itemId, maxWidth = maxWidth) },
    )

    /**
     * Owns audio-focus (duck/restore) + becoming-noisy auto-pause. Shared with
     * the live TV VM to eliminate the prior copy-paste. [control] reads the
     * current engine on every callback so engine swaps (retry/fallback) and
     * teardown stay correct. [onRegain] applies the `videoSkipBackOnResumeMs`
     * resume-skip the VOD path needs (live has no equivalent).
     */
    private val playerAudioLifecycle = PlayerAudioLifecycle(
        context = context,
        control = {
            playerSessionManager.engine?.let { engine ->
                PlayerAudioLifecycle.PlaybackControl(
                    isPlaying = { engine.isPlaying.value },
                    volume = { engine.volume },
                    pause = { engine.pause() },
                    play = { engine.play() },
                    setVolume = { engine.setVolume(it) },
                    setMuted = { engine.setMuted(it) },
                )
            }
        },
        isMuted = { _uiState.value.isMuted },
        onRegain = {
            val skipMs = aggregateStore.aggregate.value.videoPlayer.videoSkipBackOnResumeMs
            if (skipMs > 0L) {
                val target = ((playerSessionManager.engine?.currentPositionMs ?: 0L) - skipMs)
                    .coerceAtLeast(0L)
                seekTo(target)
            }
        },
    )

    private val _passOutEvents = Channel<String>(Channel.BUFFERED)
    val passOutEvents: kotlinx.coroutines.flow.Flow<String> = _passOutEvents.receiveAsFlow()

    /** Resets the pass-out interaction clock (delegates to the coordinator). */
    fun onUserInteraction() {
        engineEventCoordinator.onUserInteraction()
    }

    /**
     * Snapshot of [uiState] with the live playback position/duration injected
     * from the dedicated high-frequency flows. Use this anywhere that
     * needs the position-aware derived properties ([activeSegment],
     * [shouldShowUpNext], …) so the logic does not depend on the (now stale)
     * `currentPosition`/`duration` fields stored on uiState itself.
     */
    private fun positionAwareState(): VideoPlayerUiState = _uiState.value.copy(
        currentPosition = _currentPositionMs.value,
        duration = _durationMs.value,
    )

    fun seekTo(positionMs: Long) {
        playbackSession.lastSeekPositionMs = positionMs
        playbackSession.lastSeekTimestamp = System.currentTimeMillis()
        // Update the dedicated position flow so the seek bar reflects the
        // new position immediately; uiState is no longer the source of truth.
        _currentPositionMs.value = positionMs
        playerSessionManager.engine?.seekTo(positionMs)
        // Explicit seeks are the most important position to survive process
        // death; persist the SavedStateHandle snapshot immediately rather than
        // waiting for the throttle.
        val itemId = playerSessionManager.sessionState.value.currentItemId
        if (itemId != null) {
            playbackSession.lastPersistedPositionMs = positionMs
            savedStateHandle[SAVED_KEY_ITEM_ID] = itemId
            savedStateHandle[SAVED_KEY_POSITION_MS] = positionMs
            savedStateHandle[SAVED_KEY_PLAY_SESSION_ID] = currentPlaySessionId
            savedStateHandle[SAVED_KEY_POSITION_PERSISTED_AT] = System.currentTimeMillis()
            // The DB mirror is coalesced: rapid scrubbing no longer queues one
            // recordProgress per seek. SavedStateHandle above is already
            // immediate, and the throttled tick mirror catches up regardless.
            val durationMs = playerSessionManager.engine?.durationMs ?: 0L
            scheduleCoalescedSeekProgress(itemId, positionMs, durationMs)
        }
    }

    /**
     * Restarts the current item from the beginning. Backs the
     * "Restart" action on the resume-reminder chip shown when playback resumes
     * from a saved position.
     */
    fun restartPlayback() {
        seekTo(0L)
    }

    fun resumePlayback() {
        val engine = playerSessionManager.engine ?: return
        val skipMs = aggregateStore.aggregate.value.videoPlayer.videoSkipBackOnResumeMs
        if (skipMs > 0L && !engine.isPlaying.value) {
            val target = (engine.currentPositionMs - skipMs).coerceAtLeast(0L)
            seekTo(target)
        }
        engine.play()
    }

    private fun getReportPositionMs(): Long {
        val enginePos = playerSessionManager.engine?.currentPositionMs ?: 0L
        val seekPos = playbackSession.lastSeekPositionMs
        val seekTime = playbackSession.lastSeekTimestamp
        if (seekPos != null && seekTime > 0L) {
            val timeSinceSeek = System.currentTimeMillis() - seekTime
            if (timeSinceSeek < 3000L) {
                return seekPos
            }
        }
        return enginePos
    }

    // Explicit type: the getPlaySessionId lambda below reaches into
    // `playbackSession`, so leaving this type implicit would make the
    // inference of `playbackSession` (which takes this property as a
    // constructor argument) recursive.
    private val progressReporter: PlaybackProgressReporter = PlaybackProgressReporter(
        playbackRepository = playbackRepository,
        scope = viewModelScope,
        uiState = _uiState,
        getCurrentItemId = { playerSessionManager.sessionState.value.currentItemId },
        getPlaySessionId = { playerSessionManager.sessionState.value.playSessionId ?: playbackSession.playSessionId },
        getResolvedPlayMethod = { playerSessionManager.sessionState.value.playMethod },
        getMediaEngine = { playerSessionManager.engine },
        getIncognitoModeEnabled = { cachedAggregate.videoPlayer.incognitoModeEnabled },
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
            // Closes the gap where playback crossed the watched threshold but no
            // clean Stop telemetry reached the server (process kill / crash),
            // leaving the item unplayed server-side.
            //
            // Normal mode: mark watched through PlayedStateSync.flip (via
            // markPlayed) so the change applies to the offline store AND reaches
            // the server — immediately when online, or via the playback outbox
            // on reconnect when offline.
            //
            // Incognito: never reach the server or create outbox rows — the same
            // invariant reportCurrentPlaybackStopped enforces. Fall back to the
            // local-only offline mark so a downloaded copy still shows watched,
            // matching how persistPlaybackPosition keeps writing the local resume
            // cache in incognito.
            launch {
                if (cachedAggregate.videoPlayer.incognitoModeEnabled) {
                    offlinePlaybackFacade.recordPlayed(itemId)
                } else {
                    // Silent mode (plan 03): the player is not a detail surface —
                    // no in-place flip, the repository write (PlayedStateSync
                    // fan-out + self-invalidation) is all this path needs.
                    userDataMutator.setPlayed(itemId, played = true)
                }
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
    internal val syncPlay = SyncPlayBridge(
        syncPlayManager = syncPlayManager,
        getMediaEngine = { playerSessionManager.engine },
        getCurrentItemId = { playerSessionManager.sessionState.value.currentItemId },
        onLoadItem = { itemId, positionTicks ->
            if (playerSessionManager.sessionState.value.currentItemId != itemId) {
                initialize(itemId, null, positionTicks)
            } else {
                seekTo(positionTicks / 10_000)
            }
        },
        // Session-state write seam: the bridge no longer holds the UiState
        // handle; the play/pause mirror it maintained goes through this narrow
        // lambda.
        setIsPlaying = { playing -> _uiState.update { s -> s.copy(isPlaying = playing) } },
        scope = scope,
    )

    /**
     * Playback-session shell (Stage B): owns the session-scoped latches and
     * bookkeeping that used to be flat VM fields (release flag, Stop-report
     * dedup, seek + persist positions, play-session id, load/seek jobs, the
     * release scope). The VM still hosts the methods that mutate them; this
     * step is a pure field move with no behavior change.
     *
     * Declared above `init` per the construction-order convention (later
     * extraction steps launch init collectors that reach into it). The
     * reporter is constructed above and passed in already built — its
     * ui-state handle wiring stays in this file by design.
     */
    private val playbackSession = PlaybackSession(
        scope = scope,
        playerSessionManager = playerSessionManager,
        progressReporter = progressReporter,
    )

    // ── Session load pipeline ────────────────────────────────────────────────
    // The pipeline owns the ORDER of the load stages that initializeInternal
    // used to inline; the outputs/hooks below own the uiState writes and the
    // VM-bound bodies (controllers, session bookkeeping, reports).
    private val sessionLoadOutputs = object : SessionLoadOutputs {
        override fun onPrefsProjected(ui: VideoPlayerUiState.() -> VideoPlayerUiState) {
            _uiState.update(ui)
        }

        override fun onInitializing(visible: Boolean) {
            _uiState.update { it.copy(isInitializing = visible) }
        }

        override fun onDurationSeeded(runtimeMs: Long) {
            // Guarded so a value already set by the engine (e.g. ExoPlayer
            // resolving duration on prepare) is never clobbered.
            if (_durationMs.value == 0L) {
                _durationMs.value = runtimeMs
            }
        }

        override fun onPlayheadSeeded(startPositionTicks: Long) {
            preSeedPlayhead(startPositionTicks)
            // Surface a one-shot "Resumed — Restart" reminder when opening at a
            // saved position; emitted here (not in initializeInternal) so
            // offline-resolved resume positions — invisible in the raw request
            // ticks — raise the chip too.
            if (startPositionTicks > 0) {
                _resumeReminder.tryEmit(startPositionTicks / 10_000)
            }
        }

        override fun onStreamUrlResolved(url: String) {
            _uiState.update { it.copy(media = it.media.copy(streamUrl = url)) }
        }
    }

    private val sessionLoadPipeline = SessionLoadPipeline(
        sessionManager = playerSessionManager,
        mediaRepository = mediaRepository,
        aggregateStore = aggregateStore,
        networkOfflineStore = networkOfflineStore,
        outputs = sessionLoadOutputs,
        hooks = SessionLoadHooks(
            reconcileSyncPlayQueue = { itemId, mediaSourceId, startPositionTicks ->
                syncPlay.reconcileQueueForItem(itemId, mediaSourceId, startPositionTicks)
            },
            shouldAttemptCinemaMode = { agg, itemId, startPositionTicks ->
                shouldAttemptCinemaMode(agg, itemId, startPositionTicks)
            },
            beginCinemaMode = { intros, request ->
                cinemaIntroContext = CinemaIntroContext(
                    mainItemId = request.itemId,
                    mainMediaSourceId = request.mediaSourceId,
                    mainStartPositionTicks = request.startPositionTicks,
                    mainSubtitleStreamIndex = request.subtitleStreamIndex,
                    mainAudioStreamIndex = request.audioStreamIndex,
                    intros = intros,
                    currentIndex = 0,
                )
                loadCinemaIntro(intros.first())
            },
            resolveOfflineResumeTicks = { itemId, startPositionTicks ->
                resolveOfflineResumeTicks(itemId, startPositionTicks)
            },
            onSessionPrefsApplied = { agg ->
                autoplayController.setEnabled(agg.videoPlayer.videoAutoplayNext)
            },
            restoreRememberedMuted = { agg ->
                if (agg.videoPlayer.videoRememberMuted && agg.videoPlayer.videoMuted) {
                    _uiState.update { it.copy(isMuted = true) }
                    playerSessionManager.engine?.setMuted(true)
                }
            },
            onItemHydrated = { itemId, hydratedAgg ->
                // Restore per-item persisted video filters (if any) before
                // playback kicks off.
                val hydratedEffects = hydratedAgg.engine.videoEffectsByItem[itemId] ?: VideoEffectsConfig()
                if (_uiState.value.videoFx.videoEffects != hydratedEffects) {
                    _uiState.update { it.copy(videoFx = it.videoFx.copy(videoEffects = hydratedEffects)) }
                    updateConfigWithUiStateDebounced()
                }
                // Resolve the effective subtitle-sync delay for this item: a
                // stored per-item correction wins, otherwise the global
                // "Subtitle sync offset" default applies. Always apply (even
                // when no per-item entry exists) so the previous item's
                // in-memory delay can't bleed into this one. Push to the engine
                // immediately so the AV-sync slider and the rendered cues stay
                // in sync on resume.
                val itemDelay = resolveSubtitleDelayMs(hydratedAgg.subtitle, itemId)
                val currentStyle = _uiState.value.subtitleStyle
                if (currentStyle.offsetMs != itemDelay) {
                    _uiState.update { it.copy(subtitleStyle = currentStyle.copy(offsetMs = itemDelay)) }
                    updateConfigWithUiStateDebounced()
                }
            },
            createMediaSession = { itemId, title, subtitle ->
                createVideoMediaSession(itemId, title, subtitle)
            },
            applyMediaDetail = { detail -> applyMediaDetail(detail) },
            initializeTrickplay = { itemId, source -> initializeTrickplayForItem(itemId, source) },
            reportPlaybackStart = { itemId, source, playMethod ->
                if (!cachedAggregate.videoPlayer.incognitoModeEnabled) {
                    playbackRepository.reportPlaybackStart(
                        com.raulshma.jellyplay.core.model.PlaybackStartInfo(
                            itemId = itemId,
                            sessionId = currentPlaySessionId,
                            mediaSourceId = source?.id,
                            playMethod = playMethod,
                        )
                    )
                }
            },
            startPositionTracking = { progressReporter.startPositionTracking() },
            startProgressReporting = { progressReporter.startProgressReporting() },
            fetchMediaSegments = { itemId -> fetchMediaSegments(itemId) },
            fetchAdjacentEpisodes = { detail -> fetchAdjacentEpisodes(detail) },
            loadSeriesEpisodes = { detail -> loadSeriesEpisodes(detail) },
            // No terminal-outcome action today; stated explicitly here so a
            // future consumer is a construction-site change, not a hidden
            // default somewhere else.
            onOutcome = { },
        ),
    )

    /**
     * Trickplay three-way selection for [initializeInternal]'s load spine:
     * server trickplay cached into the download dir, a local bundle shipped
     * with the download, or the server manifest fetched on demand and cached
     * for the next offline session.
     */
    private suspend fun initializeTrickplayForItem(itemId: String, source: com.raulshma.jellyplay.core.model.MediaSource?) {
        source?.trickplayInfo?.let { info ->
            val downloadPath = offlinePlaybackFacade.getDownloadPath(itemId)
            if (downloadPath != null) {
                val cacheDir = java.io.File(java.io.File(downloadPath).parentFile, "trickplay")
                trickplayManager.initializeWithCache(itemId, info, cacheDir)
            } else {
                trickplayManager.initialize(itemId, info)
            }
            _uiState.update { it.copy(uiPrefs = it.uiPrefs.copy(trickplayInfo = info)) }
        }

        if (source?.trickplayInfo == null) {
            val downloadPath = offlinePlaybackFacade.getDownloadPath(itemId)
            if (downloadPath != null) {
                val localInfo = com.raulshma.jellyplay.feature.player.video.trickplay.OfflineTrickplayHelper
                    .loadLocalTrickplayInfo(downloadPath, itemId)
                if (localInfo != null) {
                    val cacheDir = com.raulshma.jellyplay.feature.player.video.trickplay.OfflineTrickplayHelper
                        .getLocalTrickplayDir(downloadPath, itemId)
                    if (cacheDir != null) {
                        trickplayManager.initializeLocal(itemId, localInfo, cacheDir)
                        _uiState.update { it.copy(uiPrefs = it.uiPrefs.copy(trickplayInfo = localInfo)) }
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
                        _uiState.update { it.copy(uiPrefs = it.uiPrefs.copy(trickplayInfo = serverInfo)) }
                    }
                }
            }
        }
    }

    private var engineCollectionJob: Job? = null

    // Coalesces a burst of subtitle-delay fine-tune changes into one engine
    // apply (one media reload on ExoPlayer/LibVLC). Cancelled/replaced on each
    // setSubtitleDelay call so only the last value wins.
    private var subtitleDelayApplyJob: Job? = null

    /**
     * Auto-removes a finished download when the user crosses the watched
     * threshold, gated by [com.raulshma.jellyplay.core.model.legacy.UserPreferences.smartDownloadsEnabled].
     *
     * Guards against the two risks flagged in the architecture analysis:
     * - *Premature delete on misreported duration*: the reporter derives
     * "95% watched" from `position / duration`. A live stream or a buggy
     * container can report a tiny/growing duration and trip the threshold
     * almost immediately. We require the resolved duration to be at least
     * [MIN_DURATION_FOR_SMART_DELETE_MS] before deleting.
     * - *Silent destructive action*: the deletion is now surfaced to the
     * user via [userMessageBus] instead of happening invisibly.
     */
    private fun handleSmartDownloadCleanup(itemId: String) {
        if (!downloadsStore.downloads.value.smartDownloadsEnabled) return
        if (_uiState.value.duration < MIN_DURATION_FOR_SMART_DELETE_MS) return
        launch {
            if (!offlinePlaybackFacade.deleteDownload(itemId)) return@launch
            userMessageBus.info(
                com.raulshma.jellyplay.core.ui.feedback.uiTextOf(
                    com.raulshma.jellyplay.core.ui.R.string.msg_smart_download_deleted,
                ),
            )
        }
    }

    val hapticsEnabled: Boolean get() = appearanceStore.appearance.value.hapticsEnabled

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
        engineStore = engineStore,
        subtitleStore = subtitleStore,
        getEngine = { playerSessionManager.engine },
        getMediaStreams = { _uiState.value.media.mediaStreams },
        getCurrentItemId = { playerSessionManager.sessionState.value.currentItemId },
        getCurrentSeriesId = { playerSessionManager.sessionState.value.mediaDetail?.item?.seriesId },
        getPlayMethod = { playerSessionManager.sessionState.value.playMethod },
        onReloadForStreamChange = { audioStreamIndex, subtitleStreamIndex ->
            reloadForStreamChange(audioStreamIndex, subtitleStreamIndex)
        },
        playbackPreferenceResolver = playbackPreferenceResolver,
        persistRememberedTrack = { type, track ->
            val seriesId = playerSessionManager.sessionState.value.mediaDetail?.item?.seriesId ?: return@TrackSelectionHelper
            launch {
                itemPlaybackPreferenceRepository.saveRememberedTrack(
                    scope = PlaybackPrefScope.SERIES,
                    key = seriesId,
                    type = type,
                    track = track,
                )
                // Re-resolve per-item/series preferences so the next track
                // resolution sees the just-persisted remembered track. Call the
                // resolver directly — trackSelectionHelper is still being built
                // here (its refreshPlaybackPreferences() only delegates to it).
                playbackPreferenceResolver.refresh()
            }
        },
        scope = scope,
    )

    /**
     * Owns the uniform engine-effect setters (night mode, audio delay,
     * decoder, passthrough, normalization, channel mix, bass, virtualizer,
     * reverb) and the [com.raulshma.jellyplay.feature.player.video.state.AudioEffectsState]
     * slice they mutate. Extracted from the VM body. Public VM methods delegate
     * so the 27 test references + the public API stay valid. Dialogue Boost,
     * Equalizer, and Video Effects stay inline because their state lives
     * outside this controller (per-item repo / VM field / cinema gate).
     */
    internal val effects = VideoEffectsController(
        scope = scope,
        audioStore = audioStore,
        audioEffectsStore = audioEffectsStore,
        playbackStore = playbackStore,
        syncConfig = { updateConfigWithUiState() },
    )

    // ── Controller slice handles ────────────────────────────────────────────
    // Each migrated controller owns its slice as a MutableStateFlow and is
    // exposed directly; the screen collects `handle.state` (and any
    // per-slice streams) at the leaf composables that render it, and calls
    // commands on the handle directly. The residual [uiState] keeps only
    // session + prefs-mirror state. The ViewModel does NOT relay slice
    // commands: it keeps only real orchestration (load/session/lifecycle and
    // cross-controller flows like background-cast detach).

    val trackState: StateFlow<com.raulshma.jellyplay.feature.player.video.state.TrackState>
        get() = trackSelectionHelper.state

    init {
        castManager.acquireConsumer()
        // Subscribe the engine-event fan-out FIRST: the coordinator's decisions
        // collector must be active before any initialize() can produce an
        // engine error (subscription timing).
        startEngineEventCoordinatorOutputs()
        // Register the PiP transport bridge so the Activity can dispatch PiP
        // remote-action intents (play/pause/skip/next) to the active engine.
        // Also re-armed in initializeInternal(): this VM is Activity-scoped
        // (Nav3 has no per-entry ViewModelStore here) and is reused across media,
        // and release() from the screen's onDispose runs pipController.reset()
        // which nulls the transport — but init never re-runs on the reused
        // instance, so every load must re-arm it or PiP controls go dead.
        registerPipTransport()
        launch {
            pipController.pipDismissed.collect { dismissed ->
                if (dismissed) {
                    activePlayerController.engine?.pause()
                    playerSessionManager.engine?.pause()
                    mediaSessionController.release()
                    videoMiniPlayerState.release()
                    release()
                    _closePlayer.trySend(Unit)
                }
            }
        }
        launch {
            aggregateStore.aggregate.collect { agg ->
                val oldAggregate = cachedAggregate
                cachedAggregate = agg
                // Pure prefs → uiState projection (each field guarded so an
                // unrelated pref emission does not re-emit to every collector).
                val subtitleStyleChanged = settingsProjector.project(agg)
                // Prefs seeds for controller-owned slices (the projections
                // SettingsProjector used to apply to the flat fields):
                //  - the sleep timer's last-used duration,
                //  - the per-item audio/subtitle override flags, and
                //  - the Subtitle Manager's default search language.
                sleepTimer.seedLastUsedDurationMs(agg.audio.sleepTimerDurationMs)
                trackSelectionHelper.onStoredSelectionChanged(
                    playerSessionManager.sessionState.value.currentItemId
                        ?.let { agg.engine.mediaStreamSelections[it] }
                )
                subtitles.seedDefaultSearchLanguage(
                    agg.subtitle.preferredSubtitleLanguage ?: "eng"
                )
                // Subtitle-style change needs an engine-config rebuild.
                if (subtitleStyleChanged) {
                    playerSessionManager.engine?.let { updateConfigWithUiState() }
                }
                // Autoplay-next flip also toggles the autoplay controller.
                if (_uiState.value.autoplay.videoAutoplayNext != agg.videoPlayer.videoAutoplayNext) {
                    _uiState.update {
                        it.copy(autoplay = it.autoplay.copy(videoAutoplayNext = agg.videoPlayer.videoAutoplayNext))
                    }
                    autoplayController.setEnabled(agg.videoPlayer.videoAutoplayNext)
                }
                if (oldAggregate.audioEffects.volumeBoostEnabled != agg.audioEffects.volumeBoostEnabled ||
                    oldAggregate.audioEffects.volumeBoostGain != agg.audioEffects.volumeBoostGain ||
                    oldAggregate.audioEffects.equalizerSettings != agg.audioEffects.equalizerSettings ||
                    oldAggregate.playback.pauseOnAudioFocusLoss != agg.playback.pauseOnAudioFocusLoss) {
                    playerSessionManager.engine?.let {
                        updateConfigWithUiState()
                    }
                }
                // Duck on transient audio focus loss (phone calls). Folded into
                // this single preferences collector (was a duplicate collector)
                // so a pref write rebuilds the snapshot once, not twice.
                if (agg.playback.duckOnTransientFocusLoss && !playerAudioLifecycle.isAudioFocusActive()) {
                    playerAudioLifecycle.registerAudioFocus()
                } else if (!agg.playback.duckOnTransientFocusLoss && playerAudioLifecycle.isAudioFocusActive()) {
                    playerAudioLifecycle.unregisterAudioFocus()
                }
            }
        }
        launch {
            // Surface the metered-network state so the playback metadata can
            // explain why a quality cap is being applied (AUTO on a metered link
            // caps at AdaptiveBitrateManager.MAX_BITRATE_METERED). Guarded so a
            // redundant emission (no change) doesn't allocate a fresh uiState.
            networkMonitor.isMetered.collect { metered ->
                if (_uiState.value.isConnectionMetered != metered) {
                    _uiState.update { it.copy(isConnectionMetered = metered) }
                }
            }
        }
        // Pass-out protection (interaction clock + poller) and the play-state
        // resume reset live in [EngineEventCoordinator]; the PassOutPause
        // decision is executed by executeEngineDecision().
        syncPlay.start()

        // Mirror the bridge's session flag into the residual UiState: it feeds
        // SegmentProjection/toSegmentInput() inside segmentOverlayState's
        // combine, and moving that combine onto the bridge's flow would couple
        // the segment projection to the bridge. One-way derived mirror — the
        // bridge's SyncPlayUiState.isInSyncPlaySession stays the single home.
        launch {
            syncPlay.state.map { it.isInSyncPlaySession }.distinctUntilChanged()
                .collect { inSession ->
                    if (_uiState.value.isInSyncPlaySession != inSession) {
                        _uiState.update { it.copy(isInSyncPlaySession = inSession) }
                    }
                }
        }

        // Headphone unplug auto-pause (delegated to the shared audio-lifecycle owner).
        playerAudioLifecycle.registerBecomingNoisy()

        launch {
            var lastItemId: String? = null
            var lastSeriesId: String? = null
            playerSessionManager.sessionState.collect { session ->
                val itemId = session.currentItemId
                val seriesId = session.mediaDetail?.item?.seriesId
                val prefs = cachedAggregate
                val stored = itemId?.let { prefs.engine.mediaStreamSelections[it] }
                    _uiState.update { state ->
                        state.copy(
                            title = session.title,
                            subtitle = session.subtitle,
                            media = state.media.copy(
                                currentMediaSource = session.currentMediaSource,
                                mediaStreams = session.mediaStreams,
                                playMethod = session.playMethodString,
                                transcodeReasons = session.transcodeReasons,
                                isDirectPlayForced = session.isDirectPlayForced,
                                // Mirror the session's series id so the per-series
                                // "remember subtitle/audio" toggle row renders for
                                // episode playback (footer is gated on seriesId !=
                                // null). applyMediaDetail also sets this on the
                                // initial load, but the session collector fires on
                                // every transition (e.g. next-episode autoplay) and
                                // must keep it in sync even when the detail refresh
                                // lags or is skipped.
                                seriesId = seriesId,
                            ),
                        )
                    }
                // The per-item override flags now live in the track slice.
                trackSelectionHelper.onStoredSelectionChanged(stored)
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

        // Reflect the resolved per-item/series language preference into the
        // track slice (series-pref toggle rows) + dialogue boost so the sheets
        // show the series-pref toggle state.
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
                        dialogueBoostStrength = resolvedBoost,
                        dialogueBoostEnabled = resolvedBoost != com.raulshma.jellyplay.core.model.EffectStrength.NONE,
                    )
                }
                trackSelectionHelper.onSeriesPreferenceResolved(pref)
                updateConfigWithUiState()
                // Re-apply the language preference once it resolves. The DAO
                // read in ItemPlaybackPreferenceResolver is async; on next-episode
                // autoplay the engine often publishes its track list (triggering
                // updateTracksFromEngine) before the preference lands. Without
                // re-running here, the preference never gets applied for that
                // load. Only re-run when a language preference actually exists so
                // we don't churn on null resolutions (no engine yet ⇒ no-op).
                val hasLangPref = pref?.audioLanguage != null || pref?.subtitleLanguage != null ||
                    pref?.subtitleDisabled == true
                if (hasLangPref) {
                    trackSelectionHelper.updateTracksFromEngine()
                }
            }
        }

        launch {
            playerSessionManager.engineFlow.collect { engine ->
                engineCollectionJob?.cancel()
                if (engine != null) {
                    activePlayerController.bindEngine(engine)
                    val agg = cachedAggregate
                    _uiState.update { it.copy(
                        engineCapabilities = engine.capabilities,
                        // Subtitle delay is per-media: resolve it for the
                        // current item (per-item override, else the global
                        // default) rather than seeding from the global style,
                        // so an engine swap / reloadWithEngine never resets a
                        // per-item correction to the global default.
                        subtitleStyle = resolveSubtitleStyleWithDelay(
                            agg.subtitle,
                            playerSessionManager.sessionState.value.currentItemId,
                            isHdr = isHdrFromStreams(playerSessionManager.sessionState.value.mediaStreams),
                        ),
                        // Dialogue Boost defaults to OFF until the per-item resolver
                        // applies a stored rule. It does not inherit the
                        // global default, preventing cross-item bleed.
                        dialogueBoostEnabled = false,
                        dialogueBoostStrength = com.raulshma.jellyplay.core.model.EffectStrength.NONE,
                        uiPrefs = it.uiPrefs.copy(keepScreenOnDuringVideo = agg.playback.keepScreenOnDuringVideo),
                    )}
                    // Seed the audio-effects slice from the cached preferences —
                    // the same fields this collector used to write into UiState.
                    // bass/virtualizer/reverb keep their live values (they were
                    // never seeded here) and persist across items by design.
                    effects.seedFromPreferences(
                        audioDelayMs = agg.audio.audioDelayMs,
                        decoderMode = agg.playback.decoderMode,
                        audioPassthrough = agg.playback.audioPassthrough,
                        nightModeEnabled = agg.audioEffects.nightModeEnabled,
                        nightModeStrength = agg.audioEffects.nightModeStrength,
                        audioNormalizationMode = agg.audio.audioNormalizationMode,
                        audioNormalizationEnabled = agg.audio.audioNormalizationEnabled,
                        channelMixMode = agg.audio.channelMixMode,
                        channelMixEnabled = agg.audio.channelMixEnabled,
                    )
                    cast.updateCastStrategyForEngine(engine)
                    notifyUnsupportedAudioDelayIfNeeded(engine, agg.audio.audioDelayMs)
                    // Expose whether a "next" action is available for the PiP
                    // window. Reads mediaDetail — a VM-owned slice — so it
                    // deliberately stays out of the coordinator's policies.
                    pipController.pipHasNext = mediaDetail?.item?.seriesId != null
                    engineCollectionJob = launch {
                        // The play/buffering mirrors, buffering watchdog,
                        // direct-play fallback latch, error surfacing, subtitle
                        // toasts, ENDED and pass-out protection live in
                        // [EngineEventCoordinator] (see
                        // startEngineEventCoordinatorOutputs). What remains
                        // here are the adapter fan-outs this VM owns: the
                        // SyncPlay int mapping, the PiP auto-exit, track
                        // fan-out and the cue-preview gate.
                        launch { engine.availableTracks.collect { trackSelectionHelper.updateTracksFromEngine() } }
                        // G10: accumulate embedded-subtitle cues from the engine
                        // for the sync preview. Only wins when no external text
                        // source is active — external gives the full track in
                        // both offset directions; engine accumulation covers the
                        // played range only.
                        launch {
                            // ExoPlayer fires onCues several times a second
                            // and each emission previously copied the wide
                            // UI state even when the preview wasn't visible.
                            // StateFlow already conflates to the latest
                            // value, so the remaining churn is gated out by
                            // previewSheetVisible: nothing else renders
                            // subtitlePreviewCues, so copying UI state on
                            // every tick while the sheet is closed is pure
                            // overhead. EXTERNAL source is populated on-demand
                            // by loadActiveSubtitleCues and is exempt.
                            engine.currentCues.collect { engineCues ->
                                val s = _uiState.value
                                if (s.subtitlePreviewSource != SubtitlePreviewSource.EXTERNAL &&
                                    s.previewSheetVisible
                                ) {
                                    val cues = engineCues.takeIf { it.isNotEmpty() }
                                    if (s.subtitlePreviewCues == cues && cues != null) return@collect
                                    _uiState.update {
                                        it.copy(
                                            subtitlePreviewCues = cues,
                                            subtitlePreviewSource = if (cues != null) SubtitlePreviewSource.EMBEDDED else SubtitlePreviewSource.NONE,
                                        )
                                    }
                                }
                            }
                        }
                        launch { engine.playbackState.collect { state ->
                            val stateInt = when (state) {
                                EnginePlaybackState.IDLE -> 1
                                EnginePlaybackState.BUFFERING -> 2
                                EnginePlaybackState.READY -> 3
                                EnginePlaybackState.ENDED -> 4
                                EnginePlaybackState.ERROR -> 1
                            }
                            syncPlay.onPlaybackStateChanged(stateInt)
                            // Auto-exit PiP when playback ends or errors so the
                            // window does not linger on a frozen frame. Pause is
                            // intentionally excluded — users pause to read.
                            if (pipController.isInPipMode.value &&
                                (state == EnginePlaybackState.ENDED || state == EnginePlaybackState.ERROR)
                            ) {
                                pipController.requestAutoExitPip()
                            }
                        } }
                    }
                } else {
                    activePlayerController.clearEngine()
                }
            }
        }
    }

    /**
     * Starts (or restarts, after a performRelease/initialize cycle) the
     * engine-event fan-out collectors that turn [EngineEventCoordinator]
     * mirrors and decisions into uiState writes and collaborator calls.
     *
     * Called once from `init` and again by
     * [ensureEngineEventCoordinatorActive] when a disposed coordinator is
     * re-created — this VM is Activity-scoped and survives release() across
     * media, so the fan-out must be re-armed alongside it.
     */
    private fun startEngineEventCoordinatorOutputs() {
        engineEventOutputsJob?.cancel()
        val coordinator = engineEventCoordinator
        engineEventOutputsJob = launch {
            launch {
                coordinator.isPlaying.collect { isPlaying ->
                    // Guard against same-value updates so a redundant isPlaying
                    // emission does not allocate a fresh uiState copy and
                    // invalidate every uiState collector.
                    _uiState.update { s ->
                        if (s.isPlaying == isPlaying) s else s.copy(isPlaying = isPlaying)
                    }
                    syncPlay.onIsPlayingChanged(isPlaying)
                    // Mirror play state so the Activity can render the correct
                    // play/pause icon on the PiP window.
                    pipController.setPlaying(isPlaying)
                }
            }
            launch {
                coordinator.isBuffering.collect { buffering ->
                    _uiState.update { s ->
                        if (s.isBuffering == buffering) s else s.copy(isBuffering = buffering)
                    }
                }
            }
            launch {
                coordinator.decisions.collect { decision ->
                    executeEngineDecision(decision)
                }
            }
        }
    }

    /**
     * Re-creates the engine-event coordinator if a previous [performRelease]
     * disposed it (this Activity-scoped VM is reused across media, so
     * initializeInternal must re-arm it exactly like registerPipTransport).
     */
    private fun ensureEngineEventCoordinatorActive() {
        if (!engineEventCoordinator.disposed) return
        engineEventCoordinator = createEngineEventCoordinator()
        startEngineEventCoordinatorOutputs()
    }

    /**
     * Executes one [EngineDecision] from [EngineEventCoordinator]: the VM owns
     * what a decision *does* (uiState writes, engine commands, repository
     * choreography). Idempotent after [release] — decisions emitted during
     * teardown are dropped rather than executing against a released session.
     */
    private fun executeEngineDecision(decision: EngineDecision) {
        when (decision) {
            is EngineDecision.ShowError -> {
                // EngineError is structured (retryable / Decoder / Drm /
                // Network / Source / Render / Unknown) — render the taxonomy's
                // display message AND propagate the structured retryability
                // verdict, so the dialog can offer same-engine retry
                // (Network/Render) vs. switch-engine (Decoder/Drm).
                if (playbackSession.released) return
                _uiState.update { s ->
                    if (decision.clearBuffering) {
                        s.copy(
                            playerError = decision.error.message,
                            playerErrorRetryable = decision.error.retryable,
                            showPlaybackErrorDialog = true,
                            isBuffering = false,
                        )
                    } else {
                        s.copy(
                            playerError = decision.error.message,
                            playerErrorRetryable = decision.error.retryable,
                            showPlaybackErrorDialog = true,
                        )
                    }
                }
            }
            is EngineDecision.FallbackToTranscode -> {
                if (playbackSession.released) return
                _uiState.update { it.copy(uiPrefs = it.uiPrefs.copy(playbackMode = PlaybackMode.FORCE_TRANSCODE)) }
                launch {
                    playbackStore.setPlaybackMode(PlaybackMode.FORCE_TRANSCODE)
                    reportCurrentPlaybackStopped()
                    progressReporter.cancelJobs()
                    playerSessionManager.reloadPlayback(
                        PlaybackMode.FORCE_TRANSCODE,
                        _uiState.value.uiPrefs.streamingQuality,
                        decision.fromPositionMs,
                    )
                    afterEngineReloadRebuildSessionAndTracking()
                }
            }
            EngineDecision.PlaybackEnded -> {
                if (!playbackSession.released) handlePlaybackEnded()
            }
            EngineDecision.PassOutPause -> {
                playerSessionManager.engine?.pause()
                _passOutEvents.trySend("Playback paused — pass-out protection")
            }
            is EngineDecision.InformUser -> userMessageBus.info(decision.message)
        }
    }

    /**
     * Arms the PiP transport bridge so the Activity can dispatch PiP remote-action
     * intents (play/pause/skip/next) to the active engine. Idempotent and safe to
     * call repeatedly: [release] → [performRelease] → [PipController.reset] nulls
     * the transport, and because this VM is Activity-scoped (Nav3 has no per-entry
     * ViewModelStore here) `init` does not re-run on the reused instance — so
     * [initializeInternal] must re-arm it on every load or PiP controls go dead
     * after the first media close.
     */
    private fun registerPipTransport() {
        pipController.pipTransport = PipTransport { action ->
            val engine = playerSessionManager.engine
            if (engine == null) {
                // PiP bypasses the MediaSession entirely (broadcast -> PipTransport
                // -> engine), so this silently no-ops when no engine is bound.
                // Log so a stale transport is diagnosable instead of dead-buttons.
                Log.w(TAG, "PiP action $action dropped: no active player engine")
                return@PipTransport
            }
            Log.d(TAG, "PiP action $action -> engine")
            when (action) {
                PipAction.PLAY -> engine.play()
                PipAction.PAUSE -> engine.pause()
                PipAction.SKIP_FORWARD -> {
                    val skip = _uiState.value.gestures.seekDurationMs
                    seekTo((engine.currentPositionMs + skip).coerceAtLeast(0L))
                }
                PipAction.SKIP_BACKWARD -> {
                    val skip = _uiState.value.gestures.seekDurationMs
                    seekTo((engine.currentPositionMs - skip).coerceAtLeast(0L))
                }
                PipAction.NEXT -> playNextEpisode()
            }
        }
    }

    val playerEngineRef: com.raulshma.jellyplay.feature.player.video.engine.MediaEngine? get() = playerSessionManager.engine

    /**
     * Reactive engine handle for composition. The screen previously read
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
     *
     * Staleness guard: a position persisted more than [STALE_POSITION_THRESHOLD_MS]
     * ago is ignored. The primary defense against stale auto-resume is the nav-
     * route strip in `rememberNavigationState` (a stripped route never mounts the
     * player at all, so this method never runs). This guard covers any restore
     * path that escapes the strip: if the player does mount from a stale route,
     * a long-stale position is more likely to land the user on an episode they
     * auto-advanced past than a genuine "continue here", so falling back to the
     * entry-point ticks (or, if the strip fires, never mounting at all) is the
     * safer choice. A missing/zero timestamp (positions persisted before this
     * field existed, or a non-process-death re-entry) is treated as fresh so the
     * normal resume-from-background path keeps working.
     */
    private fun resolveStartTicksAfterProcessDeath(itemId: String, startPositionTicks: Long): Long {
        val savedItemId = savedStateHandle.get<String>(SAVED_KEY_ITEM_ID) ?: return startPositionTicks
        if (savedItemId != itemId) return startPositionTicks
        val savedPosMs = savedStateHandle.get<Long>(SAVED_KEY_POSITION_MS) ?: return startPositionTicks
        val persistedAt = savedStateHandle.get<Long>(SAVED_KEY_POSITION_PERSISTED_AT) ?: 0L
        return resolveResumeTicks(
            savedPosMs = savedPosMs,
            persistedAtMs = persistedAt,
            nowMs = System.currentTimeMillis(),
            entryPointTicks = startPositionTicks,
            staleThresholdMs = STALE_POSITION_THRESHOLD_MS,
        )
    }

    /**
     * Offline resume: the offline entry points (Downloads, OfflineLibrary,
     * MediaDetail (offline), deep links, remote control, mini-player) all navigate with
     * `startPositionTicks = 0`. When no explicit position was requested and the
     * item is a completed download, fall back to the last-known position stored
     * on the downloaded item (seeded from server UserData and updated while
     * watching offline). Streaming keeps the caller-provided value.
     *
     * Extracted and `suspend` so it is unit-testable in isolation; in
     * [initializeInternal] it runs inside the load coroutine. Delegates to
     * [PlaybackSourceResolver.resolveStartPositionTicks] so the resume-position
     * rule (explicit > 0 wins, else the offline-store ticks) lives once in core.
     */
    internal suspend fun resolveOfflineResumeTicks(itemId: String, startPositionTicks: Long): Long =
        playbackSourceResolver.resolveStartPositionTicks(itemId, startPositionTicks)

    /**
     * Persists the current playback position so it survives process death.
     * Throttled to at most one write per [POSITION_PERSIST_MIN_INTERVAL_MS]
     * unless [force] (e.g. an explicit seek). Also stashes the server session
     * id so the post-restore stop-report pairs with the original start-report.
     */
    private fun persistPlaybackPosition(positionMs: Long, force: Boolean) {
        if (!force && kotlin.math.abs(positionMs - playbackSession.lastPersistedPositionMs) < POSITION_PERSIST_MIN_INTERVAL_MS) return
        val itemId = playerSessionManager.sessionState.value.currentItemId ?: return
        playbackSession.lastPersistedPositionMs = positionMs
        savedStateHandle[SAVED_KEY_ITEM_ID] = itemId
        savedStateHandle[SAVED_KEY_POSITION_MS] = positionMs
        savedStateHandle[SAVED_KEY_PLAY_SESSION_ID] = currentPlaySessionId
        savedStateHandle[SAVED_KEY_POSITION_PERSISTED_AT] = System.currentTimeMillis()
        // Mirror progress into the offline store so downloads render watched /
        // resume state while offline. No-op for non-downloaded items.
        val durationMs = playerSessionManager.engine?.durationMs ?: 0L
        val positionTicks = positionMs * 10_000L // ms → ticks
        val percentage = if (durationMs > 0L) {
            (positionMs.toDouble() / durationMs.toDouble() * 100.0).coerceIn(0.0, 100.0)
        } else 0.0
        launch {
            offlinePlaybackFacade.recordProgress(itemId, positionTicks, percentage, isPlayed = false)
        }
    }

    /**
     * Coalesces the offline-mirror DB write during seek scrubbing: cancels any
     * in-flight pending write and schedules a fresh one [SEEK_PROGRESS_COALESCE_MS]
     * later, so rapid seeks emit at most one `recordProgress` per quiet window.
     * The SavedStateHandle snapshot is already written synchronously by [seekTo],
     * and the throttled position tick (`persistPlaybackPosition(force=false)`)
     * re-writes the mirror every [POSITION_PERSIST_MIN_INTERVAL_MS], so a dropped
     * coalesced write is recovered within seconds.
     */
    private fun scheduleCoalescedSeekProgress(itemId: String, positionMs: Long, durationMs: Long) {
        playbackSession.pendingSeekProgressJob?.cancel()
        playbackSession.pendingSeekProgressJob = launch {
            delay(SEEK_PROGRESS_COALESCE_MS)
            val positionTicks = positionMs * 10_000L // ms → ticks
            val percentage = if (durationMs > 0L) {
                (positionMs.toDouble() / durationMs.toDouble() * 100.0).coerceIn(0.0, 100.0)
            } else 0.0
            offlinePlaybackFacade.recordProgress(itemId, positionTicks, percentage, isPlayed = false)
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
        playbackSession.released = false
        // Re-arm the PiP transport: the previous media's onDispose ran release()
        // → pipController.reset() which nulled it. init only ran once (this VM is
        // Activity-scoped and reused), so a new load must re-register it.
        registerPipTransport()
        // Same reuse story for the engine-event coordinator: performRelease
        // disposed it; a fresh load re-arms it and its fan-out collectors.
        ensureEngineEventCoordinatorActive()
        autoplayController.resetForNewItem()
        _uiState.update { it.copy(autoplay = it.autoplay.copy(autoplayCancelled = false)) }
        playbackSession.lastSeekPositionMs = null
        playbackSession.lastSeekTimestamp = 0L
        engineEventCoordinator.onNewItem()
        // New item = new play session id; clear the Stop dedup latch so the
        // upcoming session's Stop can be reported.
        playbackSession.stopReportedForSession = null
        trackSelectionHelper.setPendingStreams(subtitleStreamIndex, audioStreamIndex)

        // "Play On" routing: a connected Jellyfin remote session takes the
        // video instead of local playback. (Full rationale on the routing
        // helper.)
        if (routeToRemotePlaySession(itemId, mediaSourceId, startPositionTicks, subtitleStreamIndex, audioStreamIndex)) return

        if (shouldShortCircuitSameItemReload(itemId, startPositionTicks)) return
        val wasInSyncPlay = syncPlayManager.isInSyncPlaySession

        reportCurrentPlaybackStopped()

        // Cancel any in-flight load before starting a new one.
        // initializeInternal itself runs on Main.immediate so its synchronous
        // prefix cannot interleave with another call; but each call launches a
        // long-lived async load coroutine (media-detail fetch, engine load,
        // trickplay/segments/episodes). Two of those coroutines — e.g. a
        // SyncPlay `onLoadItem` event arriving while a user tap is also
        // loading — could interleave their network/teardown side effects
        // (double stop-reports, crossed engine binds). Tracking and cancelling
        // the previous load makes "latest load wins" deterministic without
        // changing the synchronous semantics of this function.
        playbackSession.loadJob?.cancel()

        tryReclaimMiniPlayerEngine(itemId)?.let { reclaimed ->
            // Reclaim promotes an already-playing mini-player engine to
            // fullscreen — playback is continuous, so no load screen.
            _uiState.update { it.copy(isInitializing = false) }
            playbackSession.loadJob = loadReclaimedMiniPlayerEngine(reclaimed, itemId)
            return
        }

        videoMiniPlayerState.release()

        // Raise the loading screen across the state reset + fresh load so the
        // seek bar never paints a stale/zero fraction during the transition. It
        // lifts once position & duration are seeded (in the load coroutine
        // below), so the bar's first paint is already at the resume fraction.
        _uiState.update { it.copy(isInitializing = true) }
        releaseInternals()
        // The playhead is seeded by the load pipeline from the RESOLVED start
        // ticks (see onPlayheadSeeded) — seeding here from the raw request
        // ticks missed the offline-mirror resume that resolution produces.
        // Restore the server session id after process death (if this is the
        // same item) so the eventual stop-report pairs with the start-report
        // instead of orphaning it. Otherwise allocate a fresh session id.
        playbackSession.playSessionId = restoreOrAllocatePlaySessionId(itemId)
        playbackSession.lastPersistedPositionMs = Long.MIN_VALUE
        playbackSession.pendingSeekProgressJob?.cancel()
        playbackSession.pendingSeekProgressJob = null
        trickplayManager.clear()

        if (wasInSyncPlay) {
            syncPlay.reattachSession()
        }

        // The ordered load spine (SyncPlay reconcile → prefs projection →
        // cinema gate → offline-start resolution → loadMedia → per-item
        // hydration → media session + duration seed → trickplay → reports)
        // lives in [SessionLoadPipeline]; its stage order is pinned by
        // SessionLoadPipelineTest.
        playbackSession.loadJob = sessionLoadPipeline.start(
            scope = scope,
            request = LoadRequest(
                itemId = itemId,
                mediaSourceId = mediaSourceId,
                startPositionTicks = startPositionTicks,
                allowCinemaMode = allowCinemaMode,
                subtitleStreamIndex = subtitleStreamIndex,
                audioStreamIndex = audioStreamIndex,
            ),
        )
    }

    /**
     * "Play On" routing early-return for [initializeInternal]: if a Jellyfin
     * remote session is connected (via the Home FAB "Play On" entry), send the
     * video to that session instead of playing locally — mirrors official
     * Jellyfin clients where picking a device routes subsequent plays to it.
     * The Home "Play On" VM uses the same strategy instance directly, so this
     * connection is independent of the video player's own CastManager cast
     * state. Returns true when the load was routed away and initialization is
     * complete.
     */
    private fun routeToRemotePlaySession(
        itemId: String,
        mediaSourceId: String?,
        startPositionTicks: Long,
        subtitleStreamIndex: Int?,
        audioStreamIndex: Int?,
    ): Boolean {
        if (!jellyfinRemotePlayCastStrategy.isConnected.value) return false
        jellyfinRemotePlayCastStrategy.loadMedia(
            itemId = itemId,
            startPositionMs = startPositionTicks / 10_000,
            mediaSourceId = mediaSourceId,
            audioStreamIndex = audioStreamIndex,
            subtitleStreamIndex = subtitleStreamIndex,
        )
        // Local player isn't loading — clear the flag so a later local UI
        // mount never shows a stuck loading screen.
        _uiState.update { it.copy(isInitializing = false) }
        return true
    }

    /**
     * Same-item short-circuit for [initializeInternal]: re-selecting the item
     * that is already loaded in a live state (not ENDED/IDLE/ERROR) is a no-op
     * — unless a non-zero resume position was requested or playback has not
     * actually started yet, in which case the reload proceeds.
     */
    private fun shouldShortCircuitSameItemReload(itemId: String, startPositionTicks: Long): Boolean {
        if (playerSessionManager.sessionState.value.currentItemId != itemId) return false
        val engine = playerSessionManager.engine ?: return false
        val state = engine.playbackState.value
        if (state == com.raulshma.jellyplay.feature.player.video.engine.EnginePlaybackState.ENDED ||
            state == com.raulshma.jellyplay.feature.player.video.engine.EnginePlaybackState.IDLE ||
            state == com.raulshma.jellyplay.feature.player.video.engine.EnginePlaybackState.ERROR
        ) {
            return false
        }
        if (startPositionTicks != 0L) return false
        return engine.currentPositionMs <= 0
    }

    /**
     * Reclaims the mini-player's live engine when it is playing exactly
     * [itemId], so the fullscreen load can bind it instead of reloading.
     * Null when there is nothing to reclaim (the mini-player state is also
     * released by the caller in that case).
     */
    private fun tryReclaimMiniPlayerEngine(itemId: String): com.raulshma.jellyplay.feature.player.video.engine.MediaEngine? =
        videoMiniPlayerState.tryReclaimEngine(itemId) as? com.raulshma.jellyplay.feature.player.video.engine.MediaEngine

    /**
     * Pre-seeds the playhead with the resolved start position so the seek bar
     * reflects where playback will resume the instant the new item opens —
     * instead of staying at 0 until the engine emits its first position tick
     * while playing (which for MPV + slow buffering can take 20-30s, and with
     * duration == 0 the bar renders its empty branch anyway). Called by the
     * session-load pipeline with the RESOLVED ticks (explicit request ticks or
     * the offline-mirror resume they resolve to). Mirrors seekTo()'s
     * synchronous display write. Display-only: written directly, not via the
     * progress reporter, so it reports nothing to the server before playback
     * actually begins.
     */
    private fun preSeedPlayhead(startPositionTicks: Long) {
        if (startPositionTicks > 0) {
            _currentPositionMs.value = startPositionTicks / 10_000
        }
    }

    /**
     * Restores the server play-session id after process death (when this is
     * the same item) so the eventual stop-report pairs with the start-report
     * instead of orphaning it; otherwise allocates a fresh session id.
     */
    private fun restoreOrAllocatePlaySessionId(itemId: String): String {
        val restoredSessionId = savedStateHandle.get<String>(SAVED_KEY_PLAY_SESSION_ID)
        val savedItemId = savedStateHandle.get<String>(SAVED_KEY_ITEM_ID)
        return if (savedItemId == itemId && !restoredSessionId.isNullOrEmpty()) {
            restoredSessionId
        } else {
            java.util.UUID.randomUUID().toString()
        }
    }

    /**
     * Load coroutine behind the mini-player-reclaim routing early-return in
     * [initializeInternal]: binds the already-playing reclaimed engine to the
     * session manager and rebuilds its session bookkeeping (media session,
     * tracking, segments, episodes). Playback is continuous — no load screen,
     * no [SessionLoadPipeline] run (the engine never reloads).
     */
    private fun loadReclaimedMiniPlayerEngine(
        reclaimed: com.raulshma.jellyplay.feature.player.video.engine.MediaEngine,
        itemId: String,
    ): Job = launch {
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
            fetchAdjacentEpisodes(detail)
            loadSeriesEpisodes(detail)
        }
    }

    private fun loadSeriesEpisodes(detail: MediaDetail) {
        val seriesId = detail.item.seriesId ?: return
        val currentSeasonId = detail.item.seasonId ?: return
        launch {
            _uiState.update { it.copy(episodes = it.episodes.copy(isLoadingEpisodes = true)) }
            val seasonList = resolveSeasons(seriesId)
            _uiState.update {
                it.copy(episodes = it.episodes.copy(seriesSeasons = seasonList, currentSeasonId = currentSeasonId))
            }
            loadSeasonEpisodes(currentSeasonId)
        }
    }

    fun loadSeasonEpisodes(seasonId: String) {
        val seriesId = mediaDetail?.item?.seriesId ?: uiState.value.media.seriesId ?: return
        launch {
            _uiState.update { it.copy(episodes = it.episodes.copy(isLoadingEpisodes = true)) }
            val episodeList = resolveEpisodes(seriesId, seasonId)
            _uiState.update { it.copy(
                episodes = it.episodes.copy(
                    seasonEpisodes = episodeList,
                    currentSeasonId = seasonId,
                    isLoadingEpisodes = false,
                ),
            ) }
        }
    }

    /**
     * Resolves the season list for [seriesId] via the consolidated [EpisodeCatalogue]
     * snapshot, branching on the current session's offline state. Offline reads
     * the local download store (airplane-mode episode discovery); online hits the
     * server. The catalogue owns single-flight + caching shared with the detail
     * screen, so re-entry into the same series (back from a sibling season) is
     * served from the snapshot rather than re-fetched.
     */
    private suspend fun resolveSeasons(seriesId: String): List<JellyfinMediaItem> {
        val offline = playerSessionManager.sessionState.value.isOffline
        return episodeCatalogue.loadSeriesEpisodes(seriesId, offline)
            .getOrDefault(com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogueSnapshot.empty(seriesId))
            .seasons
    }

    /**
     * Resolves the episode list for [seasonId] under [seriesId] via the
     * [EpisodeCatalogue], branching on offline state — mirrors [resolveSeasons].
     * Online serves from the shared snapshot if that season is present, else
     * fetches the one season; offline reads the store (ordered by episodeNumber
     * ASC at the DAO level), enabling next-episode discovery, the "up next"
     * overlay, and autoplay while offline.
     */
    private suspend fun resolveEpisodes(seriesId: String, seasonId: String): List<JellyfinMediaItem> {
        val offline = playerSessionManager.sessionState.value.isOffline
        return episodeCatalogue.loadSeasonEpisodes(seriesId, seasonId, offline)
            .getOrDefault(emptyList())
    }

    fun playEpisode(episodeId: String, startPositionTicks: Long = 0L) {
        initialize(episodeId, null, startPositionTicks)
    }

    fun setScreenLocked(locked: Boolean) {
        _uiState.update { it.copy(isScreenLocked = locked) }
    }

    suspend fun verifyPlayerLockPin(pin: String): Boolean {
        return securityStore.verifyPinOffMainThread(pin)
    }

    fun setPlaybackSpeed(speed: Float) {
        _uiState.update { it.copy(playbackSpeed = speed) }
        playerSessionManager.engine?.setPlaybackSpeed(speed)
    }

    private var speedBeforeHold: Float? = null

    fun startHoldSpeed() {
        if (_uiState.value.gestures.isHoldSpeedActive) return
        speedBeforeHold = _uiState.value.playbackSpeed
        val targetSpeed = _uiState.value.gestures.holdSpeedMultiplier
        playerSessionManager.engine?.setPlaybackSpeed(targetSpeed)
        _uiState.update { it.copy(playbackSpeed = targetSpeed, gestures = it.gestures.copy(isHoldSpeedActive = true)) }
    }

    fun stopHoldSpeed() {
        if (!_uiState.value.gestures.isHoldSpeedActive) return
        val restoreSpeed = speedBeforeHold ?: _uiState.value.gestures.defaultSpeed
        speedBeforeHold = null
        playerSessionManager.engine?.setPlaybackSpeed(restoreSpeed)
        _uiState.update { it.copy(playbackSpeed = restoreSpeed, gestures = it.gestures.copy(isHoldSpeedActive = false)) }
    }

    fun selectAudioTrack(option: TrackOption) {
        trackSelectionHelper.selectAudioTrack(option)
    }

    fun selectSubtitleTrack(option: TrackOption) {
        trackSelectionHelper.selectSubtitleTrack(option)
        // G10: the active subtitle track changed — refresh the cue preview
        // eagerly so the AV-sync sheet (if open) shows the newly selected
        // track's cues without a reopen.
        loadActiveSubtitleCues()
    }

    /**
     * Reload playback for the current item at the current position with a new
     * audio/subtitle stream index. Used when the user picks a server-origin
     * audio or subtitle track during transcoded playback — mpv cannot switch
     * audio in-place on an HLS manifest, and embedded subs aren't in the
     * transcode, so the server must re-issue the stream with the chosen index.
     */
    private fun reloadForStreamChange(audioStreamIndex: Int?, subtitleStreamIndex: Int?) {
        val engine = playerSessionManager.engine ?: return
        val positionMs = getReportPositionMs()
        launch {
            trackSelectionHelper.setPendingStreams(subtitleStreamIndex, audioStreamIndex)
            playerSessionManager.reloadForStreamChange(audioStreamIndex, subtitleStreamIndex, positionMs)
        }
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
     * Saves/clears a per-series preferred subtitle descriptor (language + role).
     * Pass the language/role of the currently-selected subtitle track to remember
     * it, or a null language to forget. The role fields ([forced] /
     * [hearingImpaired]) are optional: null means "don't care about that role",
     * a value pins it so the restore matcher prefers e.g. "English SDH" episode
     * to episode. No-op when the current item has no series.
     */
    fun setSeriesSubtitlePreference(
        language: String?,
        forced: Boolean? = null,
        hearingImpaired: Boolean? = null,
    ) {
        val seriesId = playerSessionManager.sessionState.value.mediaDetail?.item?.seriesId ?: return
        launch {
            // null = "forget": use the explicit clear so save()'s "null ⇒ preserve"
            // semantics don't silently keep the old language forever.
            if (language == null) {
                itemPlaybackPreferenceRepository.clearSubtitleLanguage(PlaybackPrefScope.SERIES, seriesId)
            } else {
                itemPlaybackPreferenceRepository.save(
                    scope = PlaybackPrefScope.SERIES,
                    key = seriesId,
                    subtitleLanguage = language,
                    subtitleForced = forced,
                    subtitleHearingImpaired = hearingImpaired,
                )
            }
            trackSelectionHelper.refreshPlaybackPreferences()
        }
    }

    /**
     * Saves/clears a per-series "subtitles off" intent. When [disabled] is true
     * every episode of the series loads with subtitles off (the resolver skips
     * the language matcher and forces Off); when false the intent is forgotten
     * so the global/per-item rules take effect again. No-op when the current
     * item has no series. Mutually exclusive with [setSeriesSubtitlePreference]:
     * enabling one clears the other's row fields.
     */
    fun setSeriesSubtitleDisabled(disabled: Boolean) {
        val seriesId = playerSessionManager.sessionState.value.mediaDetail?.item?.seriesId ?: return
        launch {
            itemPlaybackPreferenceRepository.setSubtitleDisabled(PlaybackPrefScope.SERIES, seriesId, disabled)
            trackSelectionHelper.refreshPlaybackPreferences()
        }
    }
    // -----------------------------------------------------------------------

    fun setAspectRatio(ratio: AspectRatio) {
        _uiState.update { it.copy(videoFx = it.videoFx.copy(aspectRatio = ratio)) }
        if (ratio == AspectRatio.AUTO) {
            val detected = detectAspectRatio(_uiState.value.media.mediaStreams)
            _uiState.update { it.copy(videoFx = it.videoFx.copy(detectedAspectRatio = detected)) }
        }
        // The PiP aspect ratio always tracks the underlying media, independent
        // of the in-app resize mode, so it does not need re-deriving here.
    }

    /**
     * Pushes the server-reported video stream dimensions into [PipController] as
     * a `Rational` so the PiP window matches the content (16:9, 4:3, 21:9, …)
     * instead of always letterboxing to 16:9. Falls back to `null` (→ 16:9 in
     * the Activity) when the stream or its dimensions are unknown.
     */
    private fun updatePipAspectRatio(streams: List<com.raulshma.jellyplay.core.model.MediaStream>) {
        val video = streams.firstOrNull { it.type == com.raulshma.jellyplay.core.model.StreamType.VIDEO }
        val w = video?.width
        val h = video?.height
        pipController.setPipAspectRatio(
            if (w != null && h != null && h != 0) Rational(w, h) else null
        )
    }

    /**
     * Forwards the video surface's window bounds to [PipController] as the PiP
     * source-rect hint. Thin wrapper so the screen does not reach through the
     * ViewModel into the controller.
     */
    fun updatePipSourceRect(rect: android.graphics.Rect?) {
        pipController.updatePipSourceRect(rect)
    }

    /**
     * Persists [style] to the subtitle store while preserving the persisted
     * global "Subtitle sync offset" default. The in-memory
     * [SubtitleStyle.offsetMs] is the resolved per-item delay, not a global
     * default, so a font/colour/edge/font-family change must not clobber it into
     * the global store — the cached global default is restored before writing.
     */
    private suspend fun persistSubtitleStylePreservingGlobalOffset(style: SubtitleStyle) {
        val globalOffsetMs = cachedAggregate.subtitle.subtitleStyle.offsetMs
        subtitleStore.setSubtitleStyle(style.copy(offsetMs = globalOffsetMs))
    }

    fun setSubtitleStyle(style: SubtitleStyle) {
        _uiState.update { it.copy(subtitleStyle = style) }
        updateConfigWithUiState()
        launch { persistSubtitleStylePreservingGlobalOffset(style) }
    }

    /**
     * Installs a user-picked font (from a SAF `OpenDocument` uri) via
     * [FontProvider.installUserFont], then updates + persists the resulting
     * family name/path on [SubtitleStyle] using the same pattern as
     * [setSubtitleStyle]: update in-memory state, push to the engine via
     * [updateConfigWithUiState], and persist to [preferencesStore].
     *
     * No-op if the copy/parse fails (FontProvider returns null), leaving the
     * bundled fallback font in place.
     */
    fun installUserFont(uri: android.net.Uri) {
        launch {
            val installed = fontProvider.installUserFont(uri) ?: return@launch
            val newStyle = _uiState.value.subtitleStyle.copy(
                fontFamilyPath = installed.file.absolutePath,
                fontFamilyName = installed.familyName,
            )
            _uiState.update { it.copy(subtitleStyle = newStyle) }
            updateConfigWithUiState()
            // Preserve the persisted global subtitle-delay default: the in-memory
            // offsetMs is the resolved per-item value and must not leak into the
            // global store.
            persistSubtitleStylePreservingGlobalOffset(newStyle)
        }
    }

    fun applySubtitleStyle() {
        val engine = playerSessionManager.engine ?: return
        engine.applySubtitleStyle(_uiState.value.subtitleStyle)
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
        val engineName = engine.displayName
        userMessageBus.info(
            "Audio delay (${audioDelayMs}ms) isn't supported by $engineName — switching engines re-enables it",
        )
    }

    fun setSubtitleDelay(ms: Long) {
        val current = _uiState.value.subtitleStyle
        if (current.offsetMs == ms) return
        // Subtitle delay is per-media: update the in-memory resolved value (which
        // feeds the overlay readout and the engine) and persist only to the
        // per-item store. This must NOT route through [setSubtitleStyle] — that
        // persists the whole style to the global "Subtitle sync offset" default,
        // which is how a correction for one item previously leaked into every
        // other item.
        _uiState.update { it.copy(subtitleStyle = current.copy(offsetMs = ms)) }
        playerSessionManager.sessionState.value.currentItemId?.let { itemId ->
            launch { subtitleStore.setSubtitleDelayForItem(itemId, ms) }
        }
        // Debounce the engine apply: a delay change forces ExoPlayer to reload
        // the media item to re-parse cues through the offset wrapper (a
        // rebuffer). mpv and libVLC apply the delay live (sub-delay /
        // setSpuDelay) with no reload, but ExoPlayer benefits from coalescing a
        // burst of fine-tune nudges into a single reload — the readout already
        // reflects the live in-memory value instantly.
        subtitleDelayApplyJob?.cancel()
        subtitleDelayApplyJob = launch {
            delay(SUBTITLE_DELAY_APPLY_DEBOUNCE_MS)
            updateConfigWithUiState()
        }
    }

    /**
     * G10: loads the parsed cue list for the active external subtitle track so
     * the AV-sync sheet's cue-preview can render prev/active/next lines. Resolves
     * the active track by intersecting the selected subtitle [TrackOption] with
     * the session's [external subtitles][PlayerSessionManager.currentExternalSubtitles]:
     * exact id match first (ExoPlayer side-loaded tracks carry the source id),
     * label match as fallback. Clears the preview (null) when the active track
     * has no parseable external source (embedded/image subs during DIRECT_PLAY).
     * Cancels any in-flight load so a stale result can't overwrite a newer one.
     */
    fun loadActiveSubtitleCues() {
        subtitlePreviewLoadJob?.cancel()
        subtitlePreviewLoadJob = launch {
            val externalSubs = playerSessionManager.currentExternalSubtitles ?: emptyList()
            if (externalSubs.isEmpty()) {
                // No external source: let the engine-accumulated cues (embedded
                // subs) take over by clearing the external-source precedence.
                _uiState.update { it.copy(subtitlePreviewCues = null, subtitlePreviewSource = SubtitlePreviewSource.NONE) }
                return@launch
            }
            val selected = trackSelectionHelper.state.value.subtitleTracks.firstOrNull { it.isSelected && it.index >= 0 }
            val source = resolveActivePreviewSource(externalSubs, selected)
            if (source == null) {
                // The selected track is embedded/image or unknown — never guess a
                // different external track, that would preview the wrong subtitle.
                _uiState.update { it.copy(subtitlePreviewCues = null, subtitlePreviewSource = SubtitlePreviewSource.NONE) }
                return@launch
            }
            // Auth headers for server-served HTTP subtitle URLs are assembled at
            // load time into the PlaybackRequest; surface them for the fetch.
            val headers = playerSessionManager.currentPlaybackHeaders ?: emptyMap()
            val cues = subtitlePreviewRepository.loadCues(source, headers)
            _uiState.update {
                it.copy(
                    subtitlePreviewCues = cues,
                    subtitlePreviewSource = if (cues != null) SubtitlePreviewSource.EXTERNAL else SubtitlePreviewSource.NONE,
                )
            }
        }
    }

    private fun resolveActivePreviewSource(
        externalSubs: List<SubtitleSource>,
        selected: TrackOption?,
    ): SubtitleSource? {
        if (selected == null) return null
        val byId = selected.id?.let { id -> externalSubs.firstOrNull { it.id == id } }
        if (byId != null) return byId
        return externalSubs.firstOrNull { it.label == selected.label }
    }

    /**
     * Toggles [VideoPlayerUiState.previewSheetVisible]. Called by the screen as
     * the AV-sync sheet opens/dismisses. On open, immediately re-syncs the
     * embedded cue preview from the engine's current cue list so the preview
     * isn't blank until the next onCues tick (the embedded cue pump is gated on
     * this flag, so without re-syncing the first render after open is stale).
     */
    fun setPreviewSheetVisible(visible: Boolean) {
        _uiState.update { it.copy(previewSheetVisible = visible) }
        if (visible && _uiState.value.subtitlePreviewSource != SubtitlePreviewSource.EXTERNAL) {
            val engineCues = playerSessionManager.engine?.currentCues?.value?.takeIf { it.isNotEmpty() }
            _uiState.update {
                it.copy(
                    subtitlePreviewCues = engineCues,
                    subtitlePreviewSource = if (engineCues != null) SubtitlePreviewSource.EMBEDDED else SubtitlePreviewSource.NONE,
                )
            }
        }
    }

    /** Clears the cue preview (e.g. when the active subtitle track changes). */
    fun clearActiveSubtitleCues() {
        subtitlePreviewLoadJob?.cancel()
        subtitlePreviewLoadJob = null
        subtitlePreviewRepository.clearCache()
        _uiState.update { it.copy(subtitlePreviewCues = null, subtitlePreviewSource = SubtitlePreviewSource.NONE) }
    }

    /**
     * Selects a secondary subtitle track (G4). Only mpv supports this
     * (`secondary-sid`); other engines no-op per the capability matrix. UI should
     * gate the secondary-subtitle picker on `engineCapabilities.supportsSecondarySubtitles`.
     * An [index] < 0 clears the secondary track.
     */
    fun setSecondarySubtitleTrack(index: Int) {
        playerSessionManager.engine?.setSecondarySubtitleTrack(index)
    }

    fun setPlaybackMode(mode: PlaybackMode) {
        if (_uiState.value.uiPrefs.playbackMode == mode) return
        // User explicitly changed the mode — re-arm the direct-play fallback so
        // a future FORCE_DIRECT_PLAY attempt can fail-and-retry again.
        engineEventCoordinator.onPlaybackModeChanged()
        _uiState.update { it.copy(uiPrefs = it.uiPrefs.copy(playbackMode = mode)) }
        launch {
            playbackStore.setPlaybackMode(mode)
            reloadPlaybackForMode()
        }
    }

    fun setStreamingQuality(quality: StreamingQuality) {
        if (_uiState.value.uiPrefs.streamingQuality == quality) return
        _uiState.update { it.copy(uiPrefs = it.uiPrefs.copy(streamingQuality = quality)) }
        launch {
            playbackStore.setStreamingQuality(quality)
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
        if (_uiState.value.uiPrefs.adaptiveBitrateEnabled == enabled) return
        _uiState.update { it.copy(uiPrefs = it.uiPrefs.copy(adaptiveBitrateEnabled = enabled)) }
        launch {
            networkOfflineStore.setAdaptiveBitrateEnabled(enabled)
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
        val mode = _uiState.value.uiPrefs.playbackMode
        val quality = _uiState.value.uiPrefs.streamingQuality
        val pos = playerSessionManager.engine?.currentPositionMs ?: 0L

        // Stop-report the *current* server session before the swap: reloadPlayback
        // overwrites sessionState.playSessionId with the new server id, so without
        // this the previous session is never reported stopped (the server would
        // see start(idA) → progress(idB) → stop(idB), orphaning idA — the same
        // desync class the currentPlaySessionId resolver prevents elsewhere).
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
            _uiState.update { it.copy(uiPrefs = it.uiPrefs.copy(playbackMode = PlaybackMode.FORCE_TRANSCODE)) }
            launch {
                playbackStore.setPlaybackMode(PlaybackMode.FORCE_TRANSCODE)
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

    fun retryWithEngine(playerType: PlayerType) {
        val currentPos = playerSessionManager.engine?.currentPositionMs ?: 0L
        val currentSpeed = _uiState.value.playbackSpeed
        val currentQuality = _uiState.value.uiPrefs.streamingQuality
        val maxBitrate = adaptiveBitrateManager.resolveMaxBitrate(currentQuality)?.toInt()
        progressReporter.cancelJobs()
        releaseVideoMediaSession()
        _uiState.update {
            it.copy(
                showPlaybackErrorDialog = false,
                playerError = null,
                playerErrorRetryable = false,
                preferredPlayerType = playerType,
            )
        }
        launch {
            playbackStore.setPreferredPlayer(playerType)
            playerSessionManager.reloadWithEngine(playerType, currentPos, currentSpeed, maxBitrate)
            afterEngineReloadRebuildSessionAndTracking()
        }
    }

    /**
     * Same-engine retry for recoverable [EngineError]s (Network, Render, or the
     * buffering watchdog timeout). Reloads the current engine at the current
     * position, mirroring [retryWithEngine] without changing engine. UI gates
     * this button on [VideoPlayerUiState.playerErrorRetryable]; fatal errors
     * (Decoder, Drm) only offer switch-engine.
     */
    fun retryPlayback() {
        val currentPos = playerSessionManager.engine?.currentPositionMs ?: 0L
        val currentSpeed = _uiState.value.playbackSpeed
        val currentQuality = _uiState.value.uiPrefs.streamingQuality
        val maxBitrate = adaptiveBitrateManager.resolveMaxBitrate(currentQuality)?.toInt()
        progressReporter.cancelJobs()
        releaseVideoMediaSession()
        _uiState.update {
            it.copy(
                showPlaybackErrorDialog = false,
                playerError = null,
                playerErrorRetryable = false,
            )
        }
        launch {
            playerSessionManager.reloadWithEngine(
                _uiState.value.preferredPlayerType,
                currentPos,
                currentSpeed,
                maxBitrate,
            )
            afterEngineReloadRebuildSessionAndTracking()
        }
    }

    fun dismissPlaybackError() {
        _uiState.update {
            it.copy(
                showPlaybackErrorDialog = false,
                playerError = null,
                playerErrorRetryable = false,
            )
        }
    }

    fun setFrameRateMatching(enabled: Boolean) {
        _uiState.update { it.copy(gestures = it.gestures.copy(frameRateMatching = enabled)) }
        launch {
            playbackStore.setFrameRateMatching(enabled)
        }
    }

    fun setRefreshRateMode(mode: com.raulshma.jellyplay.core.model.RefreshRateMode) {
        _uiState.update { it.copy(gestures = it.gestures.copy(refreshRateMode = mode, frameRateMatching = mode != com.raulshma.jellyplay.core.model.RefreshRateMode.OFF)) }
        launch {
            playbackStore.setRefreshRateMode(mode)
        }
    }

    fun toggleEqualizer() {
        equalizerEnabled = !equalizerEnabled
        updateConfigWithUiState()
        launch {
            audioEffectsStore.setEqualizerEnabled(equalizerEnabled)
        }
    }

    fun setEqualizerSettings(settings: com.raulshma.jellyplay.core.model.EqualizerSettings) {
        launch {
            audioEffectsStore.setEqualizerSettings(settings)
        }
    }

    fun setVideoEffects(effects: VideoEffectsConfig) {
        _uiState.update { it.copy(videoFx = it.videoFx.copy(videoEffects = effects)) }
        updateConfigWithUiStateDebounced()
        // Persist per item so the same filter preset is restored next time.
        // Skip when in Cinema Mode pre-roll — the intro is transient.
        val itemId = playerSessionManager.sessionState.value.currentItemId
        if (itemId != null && cinemaIntroContext == null) {
            launch {
                engineStore.setVideoEffectsForItem(itemId, effects)
            }
        }
    }

     private fun updateConfigWithUiState() {
        val config = EngineConfigBuilder.build(
            state = _uiState.value,
            effects = effects.state.value,
            equalizerEnabled = equalizerEnabled,
            agg = cachedAggregate,
        )
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

    fun playPreviousEpisode() {
        val detail = mediaDetail ?: return
        val seriesId = detail.item.seriesId ?: return
        val seasonId = detail.item.seasonId ?: return
        val currentItemId = playerSessionManager.sessionState.value.currentItemId ?: return
        launch {
            val episodes = resolveEpisodes(seriesId, seasonId)
            val currentIndex = episodes.indexOfFirst { it.id == currentItemId }
            if (currentIndex <= 0) return@launch
            val previous = episodes[currentIndex - 1]

            if (syncPlayManager.isInSyncPlaySession) {
                val group = syncPlayManager.currentGroup
                val currentPlaylistItemId = group?.playingPlaylistItemId
                val previousExistsInQueue = group?.playlistItemMap?.values?.contains(previous.id) == true
                if (currentPlaylistItemId != null && previousExistsInQueue) {
                    syncPlay.sendPreviousItem(currentPlaylistItemId)
                    return@launch
                }
            }

            // Resume the previous episode from its saved position (mirrors the
            // episode picker), falling back to the start when none is recorded.
            initialize(previous.id, null, previous.playbackPositionTicks ?: 0L)
        }
    }

    fun playNextEpisode() {
        val detail = mediaDetail ?: return
        val seriesId = detail.item.seriesId ?: return
        val seasonId = detail.item.seasonId ?: return
        val currentItemId = playerSessionManager.sessionState.value.currentItemId ?: return
        launch {
            val episodes = resolveEpisodes(seriesId, seasonId)
            val currentIndex = episodes.indexOfFirst { it.id == currentItemId }
            if (currentIndex < 0 || currentIndex + 1 >= episodes.size) return@launch
            val next = episodes[currentIndex + 1]

            // Auto-advancing is only reachable near the episode's end, so the
            // current episode was effectively watched. Mark it played so it
            // drops out of Continue Watching. This also covers the SyncPlay
            // branch below, which bypasses [initialize] and its stopped-position
            // report.
            if (!cachedAggregate.videoPlayer.incognitoModeEnabled) {
                runCatching { userDataMutator.setPlayed(currentItemId, played = true) }
            }

            if (syncPlayManager.isInSyncPlaySession) {
                val group = syncPlayManager.currentGroup
                val currentPlaylistItemId = group?.playingPlaylistItemId
                val nextExistsInQueue = group?.playlistItemMap?.values?.contains(next.id) == true
                if (currentPlaylistItemId != null && nextExistsInQueue) {
                    syncPlay.sendNextItem(currentPlaylistItemId)
                    return@launch
                }
            }

            initialize(next.id, null, 0L)
        }
    }

    fun cancelAutoplay() {
        autoplayController.cancel()
        _uiState.update { it.copy(autoplay = it.autoplay.copy(autoplayCancelled = true)) }
    }

    private fun handlePlaybackEnded() {
        val next = _uiState.value.episodes.nextEpisode
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
        _uiState.update { it.copy(gestures = it.gestures.copy(brightnessLevel = level)) }
        if (_uiState.value.gestures.rememberBrightness) {
            launch {
                videoPlayerStore.setVideoBrightnessLevel(level)
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
        agg: VideoPlayerAggregate,
        itemId: String,
        startPositionTicks: Long,
    ): Boolean {
        if (!agg.videoPlayer.cinemaModeEnabled) return false
        if (startPositionTicks != 0L) return false
        if (agg.playback.preferredPlayer == PlayerType.EXTERNAL) return false
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
            val local = offlinePlaybackFacade.loadSegments(itemId)
            if (local != null) {
                _uiState.update { it.copy(segmentState = it.segmentState.copy(segments = local)) }
                return@launch
            }
            val segments = playbackRepository.getMediaSegments(itemId).getOrDefault(emptyList())
            _uiState.update { it.copy(segmentState = it.segmentState.copy(segments = segments)) }
        }
    }

    private fun fetchAdjacentEpisodes(currentDetail: MediaDetail) {
        val seriesId = currentDetail.item.seriesId ?: return
        val seasonId = currentDetail.item.seasonId ?: return
        launch {
            val episodes = resolveEpisodes(seriesId, seasonId)
            val currentItemId = playerSessionManager.sessionState.value.currentItemId
            val currentIndex = episodes.indexOfFirst { it.id == currentItemId }
            val next = if (currentIndex >= 0 && currentIndex + 1 < episodes.size) {
                episodes[currentIndex + 1]
            } else null
            val previous = if (currentIndex > 0) episodes[currentIndex - 1] else null
            _uiState.update {
                it.copy(episodes = it.episodes.copy(nextEpisode = next, previousEpisode = previous))
            }
        }
    }

    fun skipCredits() {
        val state = positionAwareState()

        if (state.isOutroNearEnd && autoplayController.canSkipToNext(state.episodes.nextEpisode)) {
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
                episodes = state.episodes.copy(
                    currentSeasonId = detail.item.seasonId ?: state.episodes.currentSeasonId,
                ),
                media = state.media.copy(
                    overview = detail.item.overview ?: "",
                    people = detail.people,
                    artworkUrl = getImageUrl(detail.item.id, 400),
                    seriesId = detail.item.seriesId,
                ),
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
                _uiState.update {
                    it.copy(media = it.media.copy(lyricsLines = lyricsResult?.lines ?: emptyList()))
                }
            }
        } else {
            _uiState.update { it.copy(media = it.media.copy(lyricsLines = emptyList())) }
        }
    }

    fun getImageUrl(itemId: String, maxWidth: Int = 400): String =
        imageUrlProvider.getImageUrl(itemId, maxWidth = maxWidth)

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
            media = it.media.copy(
                currentMediaSource = source,
                mediaStreams = streams,
            ),
            videoFx = it.videoFx.copy(detectedAspectRatio = detectAspectRatio(streams)),
        ) }
        updatePipAspectRatio(streams)
        // Rebuild the audio/subtitle track options from the refreshed server
        // streams. A subtitle download/upload attaches a new MediaStream server-
        // side, but the engine's availableTracks flow only re-emits on an engine-
        // level change — so without this call the newly attached subtitle never
        // surfaces in the track picker (trackState.subtitleTracks) and the
        // user couldn't apply it. `mergeServerStreams` picks it up here.
        trackSelectionHelper.updateTracksFromEngine()
    }

    // endregion

    /**
     * Background-cast orchestration: swaps the media-session owner between the
     * cast player and the local engine. Real cross-controller flow — the cast
     * slice's own transport lives on [cast].
     */
    @OptIn(UnstableApi::class)
    fun detachForBackgroundCast() {
        castManager.markBackgroundCasting(true)
        castManager.softRelease()

        val castPlayer = castManager.castPlayerForSession
        if (castPlayer != null) {
            mediaSessionController.createForPlayer(castPlayer, "jellyplay_cast_bg")
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
            val player = engine.underlyingPlayer ?: return
            mediaSessionController.createForPlayer(player, "jellyplay_video_$itemId", itemId)
        }
    }

    fun toggleVideoStats() {
        val newValue = !_uiState.value.uiPrefs.showVideoStats
        _uiState.update { it.copy(uiPrefs = it.uiPrefs.copy(showVideoStats = newValue)) }
        playerSessionManager.engine?.setVideoStatsEnabled(newValue)
    }

    fun toggleAudioOnly() {
        _uiState.update { it.copy(audioOnly = !it.audioOnly) }
    }

    fun toggleMute() {
        val engine = playerSessionManager.engine ?: return
        val currentlyMuted = _uiState.value.isMuted
        val nowMuted = !currentlyMuted
        engine.setMuted(nowMuted)
        _uiState.update { it.copy(isMuted = nowMuted) }
        if (aggregateStore.aggregate.value.videoPlayer.videoRememberMuted) {
            launch { videoPlayerStore.setVideoMuted(nowMuted) }
        }
    }

    fun setControlsVisible(visible: Boolean) {
        playerSessionManager.engine?.setPollingIntervalMs(if (visible) 250L else 1000L)
    }

    /** Toggle the autoplay-next-episode preference from the in-player Up Next card. */
    fun setVideoAutoplayNext(enabled: Boolean) {
        _uiState.update { it.copy(autoplay = it.autoplay.copy(videoAutoplayNext = enabled)) }
        autoplayController.setEnabled(enabled)
        launch { videoPlayerStore.setVideoAutoplayNext(enabled) }
    }

    suspend fun getTrickplayThumbnail(positionMs: Long): Bitmap? {
        val state = _uiState.value
        if (!state.uiPrefs.trickplayEnabled && !state.uiPrefs.trickplayOnSeekGesture) return null
        return trickplayManager.getThumbnail(positionMs)
    }

    private fun reportCurrentPlaybackStopped() {
        if (cachedAggregate.videoPlayer.incognitoModeEnabled) return
        val itemId = playerSessionManager.sessionState.value.currentItemId ?: return
        val sessionId = currentPlaySessionId
        if (sessionId == playbackSession.stopReportedForSession) return
        val positionTicks = getReportPositionMs() * 10_000
        if (positionTicks > 0) {
            playbackSession.stopReportedForSession = sessionId
            launch {
                playbackRepository.reportPlaybackStopped(itemId, sessionId, positionTicks)
                // No manual cache invalidation (plan 08): the end-of-item
                // auto-advance path marks the episode played, which evicts
                // inside the repository; a same-item reload re-reads through
                // the provider; detail-screen re-entry force re-resolves.
            }
        }
    }

    @OptIn(UnstableApi::class)
    private fun createVideoMediaSession(
        itemId: String,
        title: String,
        subtitle: String,
    ) = mediaSessionController.createForItem(itemId, title, subtitle)

    private fun releaseVideoMediaSession() = mediaSessionController.release()

    private fun releaseInternals() {
        playbackSession.loadJob?.cancel()
        playbackSession.loadJob = null
        progressReporter.cancelJobs()
        syncPlay.reset()
        releaseVideoMediaSession()
        playerSessionManager.release()
        playerLifecycleManager.reset()
        // Clear per-item PiP mirrors but KEEP pipTransport: it is a VM-owned
        // bridge re-armed in init AND initializeInternal (because release() from
        // the screen's onDispose runs pipController.reset(), nulling it). Nulling
        // it here would deaden PiP controls mid-session, since initialize() calls
        // releaseInternals() on every item load. The full reset() (transport
        // included) runs in performRelease() on teardown, and the next load
        // re-arms it.
        pipController.setPlaying(false)
        pipController.pipHasNext = false
        trickplayManager.clear()
        // Per-item resets for controller-owned slices: each slice's
        // semantics now live with its owner instead of an implicit UiState
        // rebuild. Sleep timer + audio effects deliberately persist (no call).
        trackSelectionHelper.reset()
        trackSelectionHelper.resetForItem()
        subtitles.resetForItem()
        abRepeat.resetForItem()
        mediaDetail = null
        autoplayController.setEnabled(false)
        equalizerEnabled = false
        playbackSession.lastSeekPositionMs = null
        playbackSession.lastSeekTimestamp = 0L
        cinemaIntroContext = null

        // Residual reset: session + prefs-mirror fields only. Everything that
        // reset implicitly (track lists, subtitle search, sleep timer, audio
        // effects, SyncPlay display, A/B repeat) is now reset — or deliberately
        // not reset — by its owning controller above.
        _uiState.update { currentState ->
            VideoPlayerUiState(
                preferredPlayerType = currentState.preferredPlayerType,
                // uiPrefs: the prefs-mirror leaves carry across an item switch
                // (orientation, controls timeout, metadata/clock/time-remaining
                // visibility, keep-screen-on); the per-item / runtime leaves
                // (stats overlay, pass-out hours, trickplay info + toggles,
                // quality, ABR, playback mode, lock/PIN flags) reset to
                // defaults. A FRESH slice (not a .copy) mirrors the old flat
                // constructor: unlisted leaves take slice defaults.
                uiPrefs = PlayerUiPrefsState(
                    defaultOrientation = currentState.uiPrefs.defaultOrientation,
                    controlsTimeoutMs = currentState.uiPrefs.controlsTimeoutMs,
                    showPlaybackMetadata = currentState.uiPrefs.showPlaybackMetadata,
                    showClock = currentState.uiPrefs.showClock,
                    showTimeRemaining = currentState.uiPrefs.showTimeRemaining,
                    keepScreenOnDuringVideo = currentState.uiPrefs.keepScreenOnDuringVideo,
                ),
                // gestures: the prefs-mirror leaves carry across an item switch
                // (seek window, gesture toggle, default speed, swipe cap,
                // brightness flag + level); the runtime leaves (hold-speed
                // toggle/multiplier/active flag, indicator side, frame-rate
                // matching, refresh-rate mode) reset to defaults. Fresh slice —
                // same tight semantics as uiPrefs above.
                gestures = GesturePrefsState(
                    seekDurationMs = currentState.gestures.seekDurationMs,
                    gesturesEnabled = currentState.gestures.gesturesEnabled,
                    defaultSpeed = currentState.gestures.defaultSpeed,
                    swipeSeekMaxMs = currentState.gestures.swipeSeekMaxMs,
                    rememberBrightness = currentState.gestures.rememberBrightness,
                    brightnessLevel = currentState.gestures.brightnessLevel,
                ),
                // segmentState: only the behaviors carry across an item switch —
                // the per-item segment list resets to default (empty).
                segmentState = SegmentState(
                    segmentBehaviors = currentState.segmentState.segmentBehaviors,
                ),
                // episodes: only the browser feature toggle carries across an
                // item switch — adjacency, season/episode lists, season id and
                // the loading flag are per-item and reset to defaults.
                episodes = EpisodeBrowserState(
                    videoEpisodeBrowserEnabled = currentState.episodes.videoEpisodeBrowserEnabled,
                ),
                // videoFx: only the TV zoom carries across an item switch —
                // the per-item effects and both aspect fields reset to defaults.
                videoFx = VideoFxState(tvZoomModePercent = currentState.videoFx.tvZoomModePercent),
                subtitleStyle = currentState.subtitleStyle,
                // Reset per-item dialogue boost so it doesn't bleed into the next
                // item before the resolver re-applies the per-item rule. (The one
                // per-item exception in the former effects whitelist; dialogue
                // boost stays resolver-driven session state — see
                // VideoEffectsController's KDoc.)
                dialogueBoostEnabled = false,
                dialogueBoostStrength = com.raulshma.jellyplay.core.model.EffectStrength.NONE,
            )
        }

        // Clear the high-frequency display streams the seek bar reads. They live
        // outside uiState (to avoid ~4 Hz whole-screen recomposition) and are
        // only ever reset on a fresh VM, so without this the previous item's
        // position/duration bleed into the next item until the new engine emits
        // its first position tick (~1-2 s). With duration == 0 the seek bar
        // renders empty (its else-branch) instead of the stale fraction.
        _currentPositionMs.value = 0L
        _durationMs.value = 0L
        _bufferedPositionMs.value = 0L
        _videoStats.value = EngineVideoStats()
    }

    fun release() {
        if (playbackSession.released) return
        playbackSession.released = true
        performRelease()
    }

    override fun onCleared() {
        super.onCleared()
        release()
        playbackSession.releaseScope.cancel()
    }

    private fun performRelease() {
        val itemId = playerSessionManager.sessionState.value.currentItemId
        val sessionId = currentPlaySessionId
        val positionTicks = getReportPositionMs() * 10_000
        pipController.requestAutoEnterPip(false)
        // Tear down the engine-event collectors BEFORE the engine is released so
        // no policy observes a released engine mid-teardown (the decisions
        // executor is additionally idempotent after release).
        engineEventCoordinator.dispose()
        // Tear down audio-focus + becoming-noisy (idempotent; safe if never registered).
        playerAudioLifecycle.release()
        sleepTimer.onRelease()
        releaseInternals()
        // Full teardown: clear the transport too (releaseInternals keeps it so
        // PiP stays usable across per-item reloads while the VM is alive).
        pipController.reset()
        castManager.releaseConsumer()
        activePlayerController.clearEngine()
        // Belt-and-suspenders: flush a pending coalesced seek-mirror write so the
        // offline store doesn't lag the final position on release. The write is
        // moved onto the release scope (IO + NonCancellable) so it survives the
        // viewModelScope being cancelled on clear().
        val pendingSeek = playbackSession.pendingSeekProgressJob
        if (pendingSeek != null && itemId != null) {
            playbackSession.releaseScope.launch(NonCancellable) {
                pendingSeek.join()
            }
        }
        // Skip the second Stop if reportCurrentPlaybackStopped already
        // sent one for this session — duplicate Stop reports confuse the
        // server's resume/progress bookkeeping.
        if (itemId != null && positionTicks > 0 && sessionId != playbackSession.stopReportedForSession) {
            playbackSession.stopReportedForSession = sessionId
            playbackSession.releaseScope.launch(NonCancellable) {
                runCatching {
                    kotlinx.coroutines.withTimeout(5_000) {
                        playbackRepository.reportPlaybackStopped(
                            itemId = itemId,
                            sessionId = sessionId,
                            positionTicks = positionTicks,
                        )
                    }
                }
                // No manual cache invalidation here (plan 08): the detail
                // screen's re-entry freshness comes from the provider's forced
                // re-resolve (requestRevalidate) and the auto-advance path
                // already evicts via markPlayed inside the repository — the
                // old invalidateUserDataCaches call duplicated both.
            }
        }
    }

    private companion object {
        const val TAG = "VideoPlayerViewModel"
    }
}
