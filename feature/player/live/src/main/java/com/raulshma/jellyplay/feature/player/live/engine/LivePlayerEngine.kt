package com.raulshma.jellyplay.feature.player.live.engine

import kotlinx.coroutines.flow.StateFlow

/**
 * Thin live-only playback contract. Intentionally does NOT extend
 * [com.raulshma.jellyplay.feature.player.video.engine.MediaEngine] — live
 * playback needs none of the VOD capability matrix, DRM, or subtitle
 * side-loading surface, and this module is a clean break from the VOD
 * engine strategy tree.
 *
 * Lifecycle: a single instance is owned by
 * [com.raulshma.jellyplay.feature.player.live.LiveTvPlayerViewModel] and
 * reused across channel switches (call [load] again with a new URL). Call
 * [release] when the screen exits.
 */
interface LivePlayerEngine {

    /** Hot playback state for the rebuffer spinner / overlay visibility. */
    val state: StateFlow<LiveEngineState>

    /** True while the engine is actively playing (vs paused). */
    val isPlaying: StateFlow<Boolean>

    /** Current position in ms within the seekable live window (0 when no DVR window). */
    val positionMs: StateFlow<Long>

    /** Total duration in ms, or `-1L` for pure live (no seekable window). */
    val durationMs: StateFlow<Long>

    /** Non-null when [state] is ERROR. */
    val errorMessage: StateFlow<String?>

    /** Full technical detail (stacktrace-grade) for the last error, for an
     *  expandable details section in the error overlay. */
    val errorDetail: StateFlow<String?>

    /** True when the player is at the live edge (position >= duration - tolerance). */
    val isAtLiveEdge: StateFlow<Boolean>

    /** Underlying Media3 Player for PlayerView attachment. */
    val media3Player: androidx.media3.common.Player?

    /**
     * Load and start playback of [request]. Calling again on the same
     * instance releases the previous MediaItem and starts a new one — used
     * for channel zap.
     */
    fun load(request: LivePlaybackRequest)

    fun play()
    fun pause()

    /** Seek to the live edge. No-op for pure-live (no DVR window) streams. */
    fun seekToLiveEdge()

    /**
     * Seek to [positionMs] within the seekable live (DVR) window. No-op for
     * pure-live streams (no seekable window). Implementations must guard
     * against `duration == C.TIME_UNSET`.
     */
    fun seekTo(positionMs: Long)

    /**
     * Re-publish the current ExoPlayer position/duration/live-edge state to
     * the engine's StateFlows. Called by the ViewModel on a 500 ms ticker
     * while playing so the seek bar moves and "at live edge" stays accurate.
     */
    fun refreshLiveWindow()

    /** Tear down player resources. Idempotent. */
    fun release()
}
