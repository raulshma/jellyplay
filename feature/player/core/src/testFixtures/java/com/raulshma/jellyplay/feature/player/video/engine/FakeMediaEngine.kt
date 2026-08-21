package com.raulshma.jellyplay.feature.player.video.engine

import android.content.Context
import android.view.View
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.model.TrackType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/**
 * Behavioural [MediaEngine] test double. Every state-holding property is a
 * mutable flow the test can drive, plus virtual-clock ticking, reload
 * preservation, and delay handling that mirror [ReloadablePlayerEngine].
 *
 * This is the second adapter at the [MediaEngine] seam — the reference
 * specimen for [MediaEngineContractTest] and for `EnginePositionTicker`-style
 * virtual-time tests.
 */
class FakeMediaEngine(
    private val tickerScope: CoroutineScope? = null,
) : MediaEngine {

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

    @Volatile
    override var currentPositionMs: Long = 0L
        private set
    var durationValue: Long = 0L
    override val durationMs: Long get() = durationValue

    val positionEmissions = MutableStateFlow<Flow<Long>>(kotlinx.coroutines.flow.flowOf(0L))
    override val positionFlow: Flow<Long> get() = positionEmissions.value

    private fun createTickerFlow(scope: CoroutineScope): Flow<Long> = callbackFlow {
        trySend(currentPositionMs)
        val ticker = EnginePositionTicker(
            scope = scope,
            pollingIntervalMs = _pollingIntervalMs,
            isPlayingFlow = isPlayingState,
            isCurrentlyPlaying = { isPlayingState.value },
            onActive = {
                if (isPlayingState.value) {
                    currentPositionMs += _pollingIntervalMs.value
                }
                trySend(currentPositionMs)
            },
        ).launch()
        awaitClose { ticker.cancel() }
    }.conflate()

    init {
        if (tickerScope != null) {
            positionEmissions.value = createTickerFlow(tickerScope)
        }
    }

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

    var loadCount = 0
    var lastRequest: PlaybackRequest? = null
    var released = false

    override fun load(request: PlaybackRequest) {
        loadCount++
        lastRequest = request
        // Keep position/duration in sync with request for realism, but do not
        // auto-transition playbackState/isPlaying — tests drive those explicitly
        // via simulateState / isPlayingState, and the buffering watchdog test
        // relies on load leaving the state as IDLE/BUFFERING per its own driver.
        currentPositionMs = request.startPositionMs
        if (request.serverDurationMs != 0L) durationValue = request.serverDurationMs
    }

    override fun release() { released = true }

    override fun play() {
        // Behavioural toggle for contract tests; keeps legacy no-op for playbackState
        // but updates isPlaying so reload-preserve can be exercised via play().
        if (playbackState.value == EnginePlaybackState.ENDED) {
            currentPositionMs = 0L
        }
        isPlayingState.value = true
    }

    override fun pause() {
        isPlayingState.value = false
    }

    override fun stop() {
        isPlayingState.value = false
        playbackState.value = EnginePlaybackState.IDLE
        currentPositionMs = 0L
    }

    override fun seekTo(positionMs: Long) {
        currentPositionMs = positionMs.coerceAtLeast(0L)
    }

    override fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed = speed
    }

    override fun setPollingIntervalMs(ms: Long) { _pollingIntervalMs.value = ms }
    override fun setVideoStatsEnabled(enabled: Boolean) { _videoStatsEnabled.value = enabled }

    private var currentConfig = EngineConfig()
    override fun updateConfig(config: EngineConfig) {
        if (currentConfig == config) return
        currentConfig = config
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

    override fun setVolume(value: Float) {
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

    override fun createSurfaceView(context: Context): View = View(context)
    override fun applySubtitleStyle(style: SubtitleStyle) {}
    override fun setAspectRatio(ratio: AspectRatio) {}

    // ── Behavioural helpers ────────────────────────────────────────────────

    fun simulateEnd() { playbackState.value = EnginePlaybackState.ENDED; isPlayingState.value = false }
    fun simulateState(state: EnginePlaybackState) { playbackState.value = state }
    fun advanceTo(ms: Long) { currentPositionMs = ms }

    /** Advance position by [deltaMs] — virtual-time tick helper. */
    fun advanceBy(deltaMs: Long) { currentPositionMs = (currentPositionMs + deltaMs).coerceAtLeast(0L) }

    fun emitError(error: EngineError) { errorEmissions.tryEmit(error) }
    fun emitSubtitleEvent(event: SubtitleEvent) { subtitleEventEmissions.tryEmit(event) }

    /**
     * Simulates a reload that preserves position, speed, and play-state — the
     * contract that [ReloadablePlayerEngine.withPreservedPlayback] guarantees.
     * Used by [MediaEngineContractTest] Level-1. PlaybackState is left as the
     * block left it (real engines transition asynchronously via BUFFERING → READY),
     * so the fake does not force READY and hide async timing bugs.
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

    fun setSubtitleDelayForTest(delayMs: Long) { subtitleDelayMs = delayMs }

    fun getSubtitleDelayForTest(): Long = subtitleDelayMs
}
