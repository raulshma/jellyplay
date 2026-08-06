package com.raulshma.jellyplay.feature.player.video.engine

import android.content.Context
import android.view.View
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.model.TrackType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Controllable [MediaEngine] test double for ViewModel / controller unit
 * tests. Every state-holding property is a mutable flow the test can drive;
 * every control method records its invocation for assertion.
 *
 * Production code never instantiates this — it lives in `src/main` so both
 * `:feature:player:core` tests and (via the project dependency) `:feature:player:video`
 * tests can use it. It is the reference specimen for [MediaEngineContractTest]
 * (the first backend the contract suite runs against), which is what keeps it
 * from being dead code.
 */
class FakeMediaEngine : MediaEngine {

    override var capabilities: EngineCapabilities = EngineCapabilities()

    override val displayName: String = "FakeMediaEngine"

    // NOTE: `playbackState` and `bufferedPositionMs` are declared as covariant
    // overrides (MutableStateFlow <: StateFlow) so tests can drive `.value =`
    // directly. The two-declaration backing-field pattern (`val x =
    // MutableStateFlow(...)` + `override val x get() = x.asStateFlow()`) does
    // not compile — two properties cannot share a name in the same class body.
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

    // Test double for the live-subtitle line. Defaults to null (no overlay);
    // tests of the zoom overlay path can drive `liveSubtitleCueState.value = …`.
    val liveSubtitleCueState = MutableStateFlow<CharSequence?>(null)
    override val liveSubtitleCue: StateFlow<CharSequence?> get() = liveSubtitleCueState.asStateFlow()

    private val _pollingIntervalMs = MutableStateFlow(0L)
    override val pollingIntervalMs: StateFlow<Long> get() = _pollingIntervalMs.asStateFlow()
    private val _videoStatsEnabled = MutableStateFlow(false)
    override val videoStatsEnabled: StateFlow<Boolean> get() = _videoStatsEnabled.asStateFlow()

    override var currentPositionMs: Long = 0L
    var durationValue: Long = 0L
    override val durationMs: Long get() = durationValue
    override val positionFlow: Flow<Long> get() = positionEmissions.value
    val positionEmissions = MutableStateFlow(kotlinx.coroutines.flow.flowOf(0L))
    override val errorFlow: Flow<EngineError> get() = errorEmissions.asSharedFlow()
    val errorEmissions = MutableSharedFlow<EngineError>(extraBufferCapacity = 4)
    override val subtitleEvents: Flow<SubtitleEvent> get() = subtitleEventEmissions.asSharedFlow()
    val subtitleEventEmissions = MutableSharedFlow<SubtitleEvent>(extraBufferCapacity = 4)
    override val playbackSpeed: Float = 1f
    override val audioSessionId: Int = -1

    @Volatile private var volumeValue: Float = 1f
    override val volume: Float get() = volumeValue

    var loadCount = 0
    var lastRequest: PlaybackRequest? = null
    var released = false

    override fun load(request: PlaybackRequest) { loadCount++; lastRequest = request }
    override fun release() { released = true }
    override fun play() {}
    override fun pause() {}
    override fun stop() {}
    override fun seekTo(positionMs: Long) { currentPositionMs = positionMs }
    override fun setPlaybackSpeed(speed: Float) {}
    override fun setPollingIntervalMs(ms: Long) { _pollingIntervalMs.value = ms }
    override fun setVideoStatsEnabled(enabled: Boolean) { _videoStatsEnabled.value = enabled }
    override fun updateConfig(config: EngineConfig) {}
    override fun selectTrack(type: TrackType, index: Int) {}
    override fun setMaxVideoBitrate(bps: Int?) {}
    override fun setVolume(value: Float) { volumeValue = value.coerceIn(0f, 1f) }
    override fun increaseVolume(delta: Float) = setVolume(volumeValue + delta)
    override fun decreaseVolume(delta: Float) = setVolume(volumeValue - delta)
    override fun setMuted(muted: Boolean) { if (muted) setVolume(0f) }
    override fun createSurfaceView(context: Context): View = View(context)
    override fun applySubtitleStyleToView(view: View, style: SubtitleStyle) {}
    override fun setAspectRatio(mode: Int, ratio: Float?) {}

    // Test helpers — not part of MediaEngine; called from tests.
    fun simulateEnd() { playbackState.value = EnginePlaybackState.ENDED }
    fun simulateState(state: EnginePlaybackState) { playbackState.value = state }
    fun advanceTo(ms: Long) { currentPositionMs = ms }
}
