package com.raulshma.jellyplay.feature.player.live.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * MPV-backed live engine placeholder.
 *
 * Spec decision (`docs/superpowers/specs/2026-07-19-live-tv-player-design.md`):
 * MPV is an optional backend for live. v1 ships ExoPlayer-only — when the
 * user has PREFER_MPV selected, [LiveEngineFactory] still returns an
 * [ExoLiveEngine] for live content (MPV's strength is renderer/filter
 * control for VOD; ExoPlayer's HLS stack is more reliable for live edge
 * tracking). This stub documents the seam where a real MPV implementation
 * would slot in.
 *
 * Throwing here is intentional: if a future caller routes live through MPV,
 * the failure is loud rather than silent.
 */
class MpvLiveEngine : LivePlayerEngine {

    private val exception = IllegalStateException(
        "MPV live engine not implemented; LiveEngineFactory should return ExoLiveEngine for live"
    )

    private val _state = MutableStateFlow(LiveEngineState.IDLE)
    override val state: StateFlow<LiveEngineState> = _state.asStateFlow()
    override val isPlaying: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()
    override val positionMs: StateFlow<Long> = MutableStateFlow(0L).asStateFlow()
    override val durationMs: StateFlow<Long> = MutableStateFlow(-1L).asStateFlow()
    override val errorMessage: StateFlow<String?> = MutableStateFlow(null).asStateFlow()
    override val isAtLiveEdge: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()
    override val media3Player: androidx.media3.common.Player? get() = null
    override var onTranscodeFallbackNeeded: (() -> Unit)? = null

    override fun load(request: LivePlaybackRequest) = throw exception
    override fun play() = throw exception
    override fun pause() = throw exception
    override fun seekToLiveEdge() = throw exception
    override fun seekTo(positionMs: Long) = throw exception
    override fun refreshLiveWindow() = Unit
    override fun release() = Unit
}
