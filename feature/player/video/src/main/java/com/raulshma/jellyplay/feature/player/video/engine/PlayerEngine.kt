package com.raulshma.jellyplay.feature.player.video.engine

import android.content.Context
import android.view.View
import com.raulshma.jellyplay.core.model.DecoderMode

interface PlayerEngine {

    fun initialize(
        url: String,
        title: String,
        startPositionMs: Long = 0,
    )

    fun release()

    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun seekForward(amountMs: Long = 10_000)
    fun seekBack(amountMs: Long = 10_000)
    fun setPlaybackSpeed(speed: Float)
    fun setAudioDelay(ms: Long)
    fun setDecoderMode(mode: DecoderMode)
    fun setAudioPassthrough(enabled: Boolean)

    val isPlaying: Boolean
    val currentPositionMs: Long
    val durationMs: Long
    val playbackSpeed: Float

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

    fun createPlayerView(context: Context): View

    fun setOnStateChanged(callback: ((isPlaying: Boolean) -> Unit)?)
    fun setOnTracksChanged(callback: (() -> Unit)?)
}
