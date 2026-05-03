package com.raulshma.jellyplay.feature.player.video.engine

import android.content.Context
import android.view.View

/**
 * Unified abstraction over different video player backends (ExoPlayer, mpv, LibVLC).
 * Each implementation provides its own rendering surface via [createPlayerView] and
 * exposes common playback controls through a single interface.
 */
interface PlayerEngine {

    // ── Lifecycle ──────────────────────────────────────────

    /** Prepare the engine and start playback of [url]. */
    fun initialize(
        url: String,
        title: String,
        startPositionMs: Long = 0,
    )

    /** Release all resources (player, surface, codecs). */
    fun release()

    // ── Playback Controls ──────────────────────────────────

    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun seekForward(amountMs: Long = 10_000)
    fun seekBack(amountMs: Long = 10_000)
    fun setPlaybackSpeed(speed: Float)

    // ── State Queries ──────────────────────────────────────

    val isPlaying: Boolean
    val currentPositionMs: Long
    val durationMs: Long
    val playbackSpeed: Float

    // ── Track Management ───────────────────────────────────

    data class TrackInfo(
        val index: Int,
        val label: String,
        val language: String?,
        val isSelected: Boolean,
        val type: TrackType,
    )

    enum class TrackType { AUDIO, SUBTITLE }

    val audioTracks: List<TrackInfo>
    val subtitleTracks: List<TrackInfo>
    fun selectAudioTrack(index: Int)
    fun selectSubtitleTrack(index: Int)

    // ── Rendering Surface ──────────────────────────────────

    /**
     * Creates the native Android [View] that this engine renders into.
     * The returned view should be hosted inside an `AndroidView` in Compose.
     */
    fun createPlayerView(context: Context): View

    // ── Callbacks ──────────────────────────────────────────

    fun setOnStateChanged(callback: ((isPlaying: Boolean) -> Unit)?)
    fun setOnTracksChanged(callback: (() -> Unit)?)
}
