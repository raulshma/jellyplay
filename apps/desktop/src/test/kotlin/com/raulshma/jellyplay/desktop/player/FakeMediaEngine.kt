package com.raulshma.jellyplay.desktop.player

import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.model.TrackType
import com.raulshma.jellyplay.feature.player.video.engine.AspectRatio
import com.raulshma.jellyplay.feature.player.video.engine.EngineCapabilities
import com.raulshma.jellyplay.feature.player.video.engine.EngineConfig
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
import kotlinx.coroutines.flow.emptyFlow

/**
 * Hand-rolled [MediaEngine] test double for the desktop audio queue-semantics
 * suite (desktop tests carry no mocking library). Models the single-file,
 * auto-play-on-load, keep-open-at-EOF behavior of MpvDesktopEngine that
 * DesktopAudioQueueManager's advance logic is written against:
 *
 *  - [load] records the request, parks at READY and flips isPlaying on
 *    (mpv auto-play);
 *  - [simulateEnded] parks at ENDED with isPlaying off — the keep-open
 *    eof-reached mapping;
 *  - [play] from ENDED seeks back to 0 first (the V2b replay contract),
 *    which lets the replay tests assert position reset through the same
 *    observable surface a real engine exposes.
 */
internal class FakeMediaEngine : MediaEngine {

    val loadedRequests = mutableListOf<PlaybackRequest>()
    private val _errors = MutableSharedFlow<com.raulshma.jellyplay.feature.player.video.engine.EngineError>(extraBufferCapacity = 4)

    private val _playbackState = MutableStateFlow(EnginePlaybackState.IDLE)
    override val playbackState: StateFlow<EnginePlaybackState> = _playbackState.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    /** var: `val` overridden by `var` — tests drive position directly. */
    override var currentPositionMs: Long = 0L

    /** Simulated demuxer duration; load() adopts the request's server duration. */
    override var durationMs: Long = 0L
        private set

    override val displayName: String = "fake"

    override fun load(request: PlaybackRequest) {
        loadedRequests += request
        currentPositionMs = request.startPositionMs.coerceAtLeast(0L)
        durationMs = if (request.serverDurationMs > 0) request.serverDurationMs else 6_000L
        _playbackState.value = EnginePlaybackState.BUFFERING
        _playbackState.value = EnginePlaybackState.READY
        _isPlaying.value = true
    }

    override fun play() {
        // keep-open V2b contract: play-from-ENDED seeks back to 0 and resumes.
        if (_playbackState.value == EnginePlaybackState.ENDED) {
            currentPositionMs = 0L
            _playbackState.value = EnginePlaybackState.READY
        }
        _isPlaying.value = true
    }

    override fun pause() {
        _isPlaying.value = false
    }

    override fun stop() {
        _playbackState.value = EnginePlaybackState.IDLE
        _isPlaying.value = false
        currentPositionMs = 0L
        durationMs = 0L
    }

    override fun seekTo(positionMs: Long) {
        currentPositionMs = positionMs.coerceAtLeast(0L)
    }

    /**
     * The keep-open EOF mapping: park at ENDED with isPlaying off.
     *
     * Order matters here: the manager's ENDED collector runs INLINE under the
     * test dispatcher (a repeat-one replay resumes within this very call), so
     * isPlaying must be written BEFORE the state flip or the parking write
     * would clobber the replay's own unpause.
     */
    fun simulateEnded() {
        _isPlaying.value = false
        _playbackState.value = EnginePlaybackState.ENDED
    }

    override val errorFlow: Flow<com.raulshma.jellyplay.feature.player.video.engine.EngineError> = _errors.asSharedFlow()
    override val subtitleEvents: Flow<SubtitleEvent> = emptyFlow()
    override val positionFlow: Flow<Long> = emptyFlow()

    private var speedValue = 1.0f
    override val playbackSpeed: Float get() = speedValue
    override fun setPlaybackSpeed(speed: Float) {
        speedValue = speed
    }

    override val bufferedPositionMs: StateFlow<Long> = MutableStateFlow(0L)
    override val videoStats: StateFlow<EngineVideoStats> = MutableStateFlow(EngineVideoStats())
    override val currentCues: StateFlow<List<TimedCue>> = MutableStateFlow(emptyList())
    override val liveSubtitleCue: StateFlow<CharSequence?> = MutableStateFlow(null)
    override val pollingIntervalMs: StateFlow<Long> = MutableStateFlow(1_000L)
    override val videoStatsEnabled: StateFlow<Boolean> = MutableStateFlow(false)
    override fun setPollingIntervalMs(ms: Long) {}
    override fun setVideoStatsEnabled(enabled: Boolean) {}

    override val audioSessionId: Int = 0
    override val capabilities: EngineCapabilities = EngineCapabilities()
    /** Every [updateConfig] push, in order — effects-application assertions read this. */
    val appliedConfigs = mutableListOf<EngineConfig>()
    override fun updateConfig(config: EngineConfig) {
        appliedConfigs += config
    }

    override val availableTracks: StateFlow<List<MediaTrack>> = MutableStateFlow(emptyList())
    override fun applySubtitleStyle(style: SubtitleStyle) {}
    override fun setAspectRatio(ratio: AspectRatio) {}

    // A backing field + getter avoids the JVM signature clash between a
    // `var volume` and the interface's `setVolume(Float)` member.
    private var volumeValue = 1f
    override val volume: Float get() = volumeValue
    override fun selectTrack(type: TrackType, index: Int) {}
    override fun setMaxVideoBitrate(bps: Int?) {}
    override fun setVolume(value: Float) {
        volumeValue = value.coerceIn(0f, 1f)
    }
    override fun increaseVolume(delta: Float) {
        volumeValue = (volumeValue + delta).coerceAtMost(1f)
    }
    override fun decreaseVolume(delta: Float) {
        volumeValue = (volumeValue - delta).coerceAtLeast(0f)
    }
    override fun setMuted(muted: Boolean) {}
    override fun release() {
        releasedFlag = true
    }
    var releasedFlag = false
        private set
}

/**
 * Polls [condition] every 50 ms until true or [timeoutMs] elapses. Named
 * `pollUntil` (not `waitUntil`) to stay clear of MpvDesktopEngineTest's
 * file-private helper of that name.
 */
internal fun pollUntil(message: String = "condition", timeoutMs: Long = 5_000, condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (!condition()) {
        if (System.currentTimeMillis() > deadline) {
            throw AssertionError("$message not met within ${timeoutMs}ms")
        }
        Thread.sleep(50)
    }
}
