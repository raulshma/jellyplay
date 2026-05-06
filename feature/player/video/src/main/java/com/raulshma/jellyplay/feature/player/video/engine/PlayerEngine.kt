package com.raulshma.jellyplay.feature.player.video.engine

import android.content.Context
import android.graphics.Bitmap
import android.view.View
import com.raulshma.jellyplay.core.model.DecoderMode
import com.raulshma.jellyplay.core.model.EqualizerSettings
import com.raulshma.jellyplay.core.model.SubtitleStyle
import kotlinx.coroutines.flow.Flow

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
    fun setSubtitleDelay(ms: Long)
    fun setDecoderMode(mode: DecoderMode)
    fun setAudioPassthrough(enabled: Boolean)
    fun setAspectRatio(mode: Int, ratio: Float? = null)

    val isPlaying: Boolean
    val currentPositionMs: Long
    val durationMs: Long
    val playbackSpeed: Float
    val audioSessionId: Int

    val supportsAudioDelay: Boolean get() = false
    val supportsSubtitleDelay: Boolean get() = false
    val supportsAudioPassthrough: Boolean get() = false
    val supportsSubtitleStyle: Boolean get() = false
    val supportsDialogueBoost: Boolean get() = false
    val supportsNightMode: Boolean get() = false
    val supportsOcr: Boolean get() = false
    val supportsCues: Boolean get() = false

    data class TrackInfo(
        val index: Int,
        val label: String,
        val language: String?,
        val isSelected: Boolean,
        val type: TrackType,
        val trackGroup: Any? = null,
    )

    enum class TrackType { AUDIO, SUBTITLE }

    val audioTracks: List<TrackInfo>
    val subtitleTracks: List<TrackInfo>
    fun selectAudioTrack(index: Int)
    fun selectSubtitleTrack(index: Int)

    fun createPlayerView(context: Context): View

    fun setOnStateChanged(callback: ((isPlaying: Boolean) -> Unit)?)
    fun setOnTracksChanged(callback: (() -> Unit)?)

    fun setSubtitleStyle(style: SubtitleStyle, view: View?) {}

    fun getCurrentCues(): List<androidx.media3.common.text.Cue> = emptyList()

    fun setDialogueBoostEnabled(enabled: Boolean) {}
    fun setNightModeEnabled(enabled: Boolean, gain: Int = 0) {}
    fun setEqualizerEnabled(enabled: Boolean, settings: EqualizerSettings = EqualizerSettings()) {}

    fun captureViewBitmap(): Bitmap? = null

    fun positionFlow(): Flow<Long> = kotlinx.coroutines.flow.flow { while (true) { emit(currentPositionMs); kotlinx.coroutines.delay(250) } }
}
