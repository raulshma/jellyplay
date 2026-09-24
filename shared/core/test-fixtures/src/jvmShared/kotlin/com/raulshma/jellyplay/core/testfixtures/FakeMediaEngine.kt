package com.raulshma.jellyplay.core.testfixtures

import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.model.TrackType
import com.raulshma.jellyplay.feature.player.video.engine.AspectRatio
import com.raulshma.jellyplay.feature.player.video.engine.EngineCapabilities
import com.raulshma.jellyplay.feature.player.video.engine.EngineConfig
import com.raulshma.jellyplay.feature.player.video.engine.EngineError
import com.raulshma.jellyplay.feature.player.video.engine.EnginePlaybackState
import com.raulshma.jellyplay.feature.player.video.engine.EngineVideoStats
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import com.raulshma.jellyplay.feature.player.video.engine.MediaTrack
import com.raulshma.jellyplay.feature.player.video.engine.PlaybackRequest
import com.raulshma.jellyplay.feature.player.video.engine.SubtitleEvent
import com.raulshma.jellyplay.feature.player.video.engine.TimedCue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf

/**
 * The one behavioural [MediaEngine] test double for JVM test lanes — the
 * union of the two per-module copies that used to drift (player-video's
 * session-suite fake and core:data's audio-queue fake, which had already
 * diverged on load/play/stop semantics before the merge; apps/desktop still
 * keeps its own app-side copy for the focus + real-engine suites and is the
 * remaining per-touch adoption candidate).
 *
 * The genuinely divergent behaviours are the state transitions engines
 * perform around [load]/[play]/[stop]. They are parameterized as
 * [LoadBehavior], because the two suites pin OPPOSITE contracts:
 *
 *  - [LoadBehavior.MANUAL] (default) — the session-suite personality: load
 *    records the request and syncs position/duration but transitions
 *    NOTHING, so tests drive `playbackState` / `isPlayingState` explicitly
 *    (the buffering-watchdog and reload tests rely on load leaving the
 *    engine wherever their own driver put it).
 *  - [LoadBehavior.AUTO_PLAY] — the desktop-mpv personality the audio
 *    queue-semantics suite is written against: load parks at READY and
 *    flips isPlaying on (mpv auto-play), adopting the request's server
 *    duration with a 6 s fallback; play-from-ENDED restores READY (the V2b
 *    replay contract); stop resets the duration too.
 *
 * Everything else is the shared union: every state-holding property is a
 * mutable flow the test can drive (`playbackState`, `isPlayingState`,
 * `videoStatsState`, `tracks`, `currentCuesState`, `liveSubtitleCueState`,
 * `positionEmissions`), emissions have `tryEmit` helpers
 * ([emitError]/[emitSubtitleEvent]), and the recorders from both copies are
 * kept (`loadCount`/`lastRequest` and `loadedRequests`/`appliedConfigs`).
 * `positionEmissions` defaults to `flowOf(0L)` — the player-video suite's
 * historical default, restored after the merge had flattened it to
 * `emptyFlow()`; the data suite never reads it.
 *
 * Two order-sensitive EOF helpers coexist ON PURPOSE — under an
 * UnconfinedTestDispatcher the collectors of a manager under test run
 * INLINE inside these calls, so the write order is part of the contract:
 *  - [simulateEnded] writes isPlaying OFF BEFORE the ENDED flip (the audio
 *    queue suite: a repeat-one replay resumes within the very call, and a
 *    later parking write would clobber the replay's own unpause);
 *  - [simulateEnd] flips ENDED first, then drops isPlaying (the session
 *    suites' helper, kept byte-compatible with its former shape).
 */
class FakeMediaEngine(
    val loadBehavior: LoadBehavior = LoadBehavior.MANUAL,
) : MediaEngine {

    /** Which engine's state-transition contract this instance models. */
    enum class LoadBehavior {
        /** Load transitions nothing — tests drive state/isPlaying by hand. */
        MANUAL,

        /**
         * Desktop-mpv-like: auto-play on load (READY + isPlaying on, 6 s
         * duration fallback), play-from-ENDED restores READY at position 0,
         * stop resets the duration.
         */
        AUTO_PLAY,
    }

    override var capabilities: EngineCapabilities = EngineCapabilities()

    override val displayName: String = "FakeMediaEngine"

    override val playbackState = MutableStateFlow(EnginePlaybackState.IDLE)

    val isPlayingState = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> get() = isPlayingState.asStateFlow()

    override val bufferedPositionMs = MutableStateFlow(0L)

    val videoStatsState = MutableStateFlow(EngineVideoStats())
    override val videoStats: StateFlow<EngineVideoStats> get() = videoStatsState.asStateFlow()

    val tracks = MutableStateFlow<List<MediaTrack>>(emptyList())
    override val availableTracks: StateFlow<List<MediaTrack>> get() = tracks.asStateFlow()

    val currentCuesState = MutableStateFlow<List<TimedCue>>(emptyList())
    override val currentCues: StateFlow<List<TimedCue>> get() = currentCuesState.asStateFlow()

    val liveSubtitleCueState = MutableStateFlow<CharSequence?>(null)
    override val liveSubtitleCue: StateFlow<CharSequence?> get() = liveSubtitleCueState.asStateFlow()

    private val _pollingIntervalMs = MutableStateFlow(100L)
    override val pollingIntervalMs: StateFlow<Long> get() = _pollingIntervalMs.asStateFlow()
    private val _videoStatsEnabled = MutableStateFlow(false)
    override val videoStatsEnabled: StateFlow<Boolean> get() = _videoStatsEnabled.asStateFlow()

    /**
     * Public var on purpose (`val` overridden by `var` — tests drive position
     * directly); [advanceTo]/[advanceBy]/[seekTo] are the coercing helpers.
     */
    override var currentPositionMs: Long = 0L
    var durationValue: Long = 0L
    override val durationMs: Long get() = durationValue

    /** Injectable position stream; the restored `flowOf(0L)` default — see the class KDoc. */
    var positionEmissions = MutableStateFlow<Flow<Long>>(flowOf(0L))
    override val positionFlow: Flow<Long> get() = positionEmissions.value

    override val errorFlow: Flow<EngineError> get() = errorEmissions.asSharedFlow()
    val errorEmissions = MutableSharedFlow<EngineError>(extraBufferCapacity = 4)
    override val subtitleEvents: Flow<SubtitleEvent> get() = subtitleEventEmissions.asSharedFlow()
    val subtitleEventEmissions = MutableSharedFlow<SubtitleEvent>(extraBufferCapacity = 4)

    private var _playbackSpeed: Float = 1f
    override val playbackSpeed: Float get() = _playbackSpeed
    override val audioSessionId: Int = -1

    @Volatile private var volumeValue: Float = 1f
    override val volume: Float get() = volumeValue

    @Volatile private var subtitleDelayMs: Long = 0L

    // ── recorders (union of both former copies) ─────────────────────────────

    var loadCount = 0
    var lastRequest: PlaybackRequest? = null

    /** Every [load] request, in order — the audio-queue suite's recorder. */
    val loadedRequests = mutableListOf<PlaybackRequest>()

    /** Every [updateConfig] push, in order — effects-application assertions. */
    val appliedConfigs = mutableListOf<EngineConfig>()

    var released = false

    override fun load(request: PlaybackRequest) {
        loadCount++
        lastRequest = request
        loadedRequests += request
        currentPositionMs = request.startPositionMs.coerceAtLeast(0L)
        when (loadBehavior) {
            // Keep position/duration in sync with the request for realism, but
            // transition NOTHING: MANUAL tests drive playbackState/isPlaying
            // explicitly via simulateState / isPlayingState.
            LoadBehavior.MANUAL ->
                if (request.serverDurationMs != 0L) durationValue = request.serverDurationMs
            LoadBehavior.AUTO_PLAY -> {
                // mpv adopts the server duration (6 s fallback), parks at
                // READY and auto-plays.
                durationValue = if (request.serverDurationMs > 0) request.serverDurationMs else 6_000L
                playbackState.value = EnginePlaybackState.BUFFERING
                playbackState.value = EnginePlaybackState.READY
                isPlayingState.value = true
            }
        }
    }

    override fun release() {
        released = true
    }

    override fun play() {
        // Shared keep-open contract: play-from-ENDED rewinds to 0. AUTO_PLAY
        // additionally restores READY (the V2b replay contract the queue
        // suite pins through the same observable surface a real mpv exposes);
        // MANUAL leaves the state to the test's driver.
        if (playbackState.value == EnginePlaybackState.ENDED) {
            currentPositionMs = 0L
            if (loadBehavior == LoadBehavior.AUTO_PLAY) {
                playbackState.value = EnginePlaybackState.READY
            }
        }
        isPlayingState.value = true
    }

    override fun pause() {
        isPlayingState.value = false
    }

    override fun stop() {
        when (loadBehavior) {
            // MANUAL's order (isPlaying → IDLE) is the session suites' shape;
            // AUTO_PLAY's (IDLE → isPlaying, + duration reset) is the queue
            // suite's — both preserved verbatim from their former copies.
            LoadBehavior.MANUAL -> {
                isPlayingState.value = false
                playbackState.value = EnginePlaybackState.IDLE
                currentPositionMs = 0L
            }
            LoadBehavior.AUTO_PLAY -> {
                playbackState.value = EnginePlaybackState.IDLE
                isPlayingState.value = false
                currentPositionMs = 0L
                durationValue = 0L
            }
        }
    }

    override fun seekTo(positionMs: Long) {
        currentPositionMs = positionMs.coerceAtLeast(0L)
    }

    override fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed = speed
    }

    override fun setPollingIntervalMs(ms: Long) {
        _pollingIntervalMs.value = ms
    }

    override fun setVideoStatsEnabled(enabled: Boolean) {
        _videoStatsEnabled.value = enabled
    }

    override fun updateConfig(config: EngineConfig) {
        appliedConfigs += config
        subtitleDelayMs = config.subtitleDelayMs
    }

    override fun selectTrack(type: TrackType, index: Int) {
        val current = tracks.value.toMutableList()
        // Mark selection per type (simplified)
        tracks.value = current.map {
            if (it.type == type) it.copy(isSelected = it.index == index) else it
        }
    }

    override fun setMaxVideoBitrate(bps: Int?) {}

    @Volatile private var lastUnmuteVolume: Float = 1f

    override fun setVolume(value: Float, isUserChange: Boolean) {
        val clamped = value.coerceIn(0f, 1f)
        if (clamped > 0f) lastUnmuteVolume = clamped
        volumeValue = clamped
    }
    override fun increaseVolume(delta: Float) = setVolume(volumeValue + delta)
    override fun decreaseVolume(delta: Float) = setVolume(volumeValue - delta)
    override fun setMuted(muted: Boolean) {
        if (muted) {
            if (volumeValue > 0f) lastUnmuteVolume = volumeValue
            volumeValue = 0f
        } else {
            volumeValue = lastUnmuteVolume.coerceIn(0.05f, 1f)
        }
    }

    override fun applySubtitleStyle(style: SubtitleStyle) {}
    override fun setAspectRatio(ratio: AspectRatio) {}

    // ── Behavioural helpers ────────────────────────────────────────────────

    /** Session-suite EOF helper: ENDED first, then isPlaying off. See class KDoc. */
    fun simulateEnd() {
        playbackState.value = EnginePlaybackState.ENDED
        isPlayingState.value = false
    }

    /**
     * Queue-suite EOF helper (the keep-open mapping): isPlaying off FIRST,
     * then the ENDED flip. Order matters — see class KDoc.
     */
    fun simulateEnded() {
        isPlayingState.value = false
        playbackState.value = EnginePlaybackState.ENDED
    }

    fun simulateState(state: EnginePlaybackState) {
        playbackState.value = state
    }

    fun advanceTo(ms: Long) {
        currentPositionMs = ms
    }

    /** Advance position by [deltaMs] — virtual-time tick helper. */
    fun advanceBy(deltaMs: Long) {
        currentPositionMs = (currentPositionMs + deltaMs).coerceAtLeast(0L)
    }

    fun emitError(error: EngineError) {
        errorEmissions.tryEmit(error)
    }

    fun emitSubtitleEvent(event: SubtitleEvent) {
        subtitleEventEmissions.tryEmit(event)
    }

    /**
     * Simulates a reload that preserves position, speed, and play-state — the
     * contract that ReloadablePlayerEngine guarantees. PlaybackState is left
     * as the block left it (real engines transition asynchronously via
     * BUFFERING → READY), so the fake does not force READY and hide async
     * timing bugs.
     */
    fun simulateReloadPreserving(block: () -> Unit = {}) {
        val snapPos = currentPositionMs
        val snapPlaying = isPlayingState.value
        val snapSpeed = _playbackSpeed
        val snapState = playbackState.value
        block()
        currentPositionMs = snapPos
        _playbackSpeed = snapSpeed
        isPlayingState.value = snapPlaying
        // Preserve the pre-reload playbackState when wasPlaying; otherwise leave
        // the block's state (which may have transitioned to BUFFERING/ERROR).
        // Do not force READY — real engines report READY asynchronously after the
        // ticker observes the new media, not synchronously inside the reload.
        if (snapPlaying) {
            // Restore the snap state unless the block explicitly set an error/ended.
            if (playbackState.value == EnginePlaybackState.IDLE) {
                playbackState.value = snapState
            }
        }
    }

    fun setSubtitleDelayForTest(delayMs: Long) {
        subtitleDelayMs = delayMs
    }

    fun getSubtitleDelayForTest(): Long = subtitleDelayMs
}
