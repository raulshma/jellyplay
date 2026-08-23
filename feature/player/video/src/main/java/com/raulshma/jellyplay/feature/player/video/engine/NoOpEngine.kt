package com.raulshma.jellyplay.feature.player.video.engine

import android.content.Context
import android.view.View
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.model.TrackType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * A [MediaEngine] that renders and controls nothing.
 *
 * [com.raulshma.jellyplay.core.model.PlayerType.EXTERNAL] launches playback in a
 * third-party app (e.g. MX Player); JellyPlay never decodes the stream itself in
 * that case, and playback progress is reported back to the server out-of-band
 * (see `PlaybackProgressReporter`).
 *
 * Historically [PlayerEngineFactory] aliased EXTERNAL to a fully wired
 * [ExoPlayerEngine] purely so its `when` expression was exhaustive — a coupling
 * that was non-obvious and could mislead readers into thinking the in-app engine
 * was actually used for external playback. This class makes the intent explicit:
 * the engine surface exists only to keep the factory total, and every operation
 * is a deliberate no-op. All [EngineCapabilities] are false because the engine
 * performs no playback.
 *
 * Thread-safety: control methods are safe to call from any thread (mirroring the
 * [com.raulshma.jellyplay.core.data.remote.RemotePlayableEngine] contract) — the
 * little mutable state here is backed by `@Volatile` fields.
 */
internal class NoOpEngine : MediaEngine, AndroidSurfaceProvider {

    override val capabilities = EngineCapabilityMatrix.EXTERNAL
    override val displayName: String = PlayerType.EXTERNAL.displayName

    private val _playbackState = MutableStateFlow(EnginePlaybackState.IDLE)
    override val playbackState: StateFlow<EnginePlaybackState> = _playbackState.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _bufferedPositionMs = MutableStateFlow(0L)
    override val bufferedPositionMs: StateFlow<Long> = _bufferedPositionMs.asStateFlow()

    private val _videoStats = MutableStateFlow(EngineVideoStats())
    override val videoStats: StateFlow<EngineVideoStats> = _videoStats.asStateFlow()

    private val _availableTracks = MutableStateFlow(emptyList<MediaTrack>())
    override val availableTracks: StateFlow<List<MediaTrack>> = _availableTracks.asStateFlow()

    override val currentCues: StateFlow<List<TimedCue>> = MutableStateFlow(emptyList())

    override val liveSubtitleCue: StateFlow<CharSequence?> = MutableStateFlow(null)

    private val _pollingIntervalMs = MutableStateFlow(0L)
    override val pollingIntervalMs: StateFlow<Long> = _pollingIntervalMs.asStateFlow()

    private val _videoStatsEnabled = MutableStateFlow(false)
    override val videoStatsEnabled: StateFlow<Boolean> = _videoStatsEnabled.asStateFlow()

    override val currentPositionMs: Long = 0L
    override val durationMs: Long = 0L
    override val positionFlow: Flow<Long> = emptyFlow()
    override val errorFlow: Flow<EngineError> = emptyFlow()
    override val subtitleEvents: Flow<SubtitleEvent> = emptyFlow()
    override val playbackSpeed: Float = 1f
    override val audioSessionId: Int = -1

    @Volatile
    private var volumeValue: Float = 1f
    override val volume: Float get() = volumeValue

    // 1. Initialization
    override fun load(request: PlaybackRequest) { /* no-op: external playback is out-of-band */ }
    override fun release() { /* no-op */ }

    // 2. Core controls
    override fun play() { /* no-op */ }
    override fun pause() { /* no-op */ }
    override fun stop() { /* no-op */ }
    override fun seekTo(positionMs: Long) { /* no-op */ }
    override fun setPlaybackSpeed(speed: Float) { /* no-op */ }

    // 3b. Adaptive polling
    override fun setPollingIntervalMs(ms: Long) { /* no-op */ }
    override fun setVideoStatsEnabled(enabled: Boolean) { /* no-op */ }

    // 4. Configuration
    override fun updateConfig(config: EngineConfig) { /* no-op */ }

    // 5. Track selection
    override fun selectTrack(type: TrackType, index: Int) { /* no-op */ }
    override fun setMaxVideoBitrate(bps: Int?) { /* no-op */ }

    // Volume (RemotePlayableEngine)
    override fun setVolume(value: Float) {
        volumeValue = value.coerceIn(0f, 1f)
    }

    override fun increaseVolume(delta: Float) = setVolume(volumeValue + delta)
    override fun decreaseVolume(delta: Float) = setVolume(volumeValue - delta)

    override fun setMuted(muted: Boolean) {
        if (muted) setVolume(0f)
    }

    // 6. UI binding
    override fun createSurfaceView(context: Context): View = View(context)

    override fun applySubtitleStyle(style: SubtitleStyle) { /* no-op */ }

    override fun setAspectRatio(ratio: AspectRatio) { /* no-op */ }
}
