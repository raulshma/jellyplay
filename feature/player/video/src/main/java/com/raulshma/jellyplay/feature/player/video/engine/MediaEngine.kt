package com.raulshma.jellyplay.feature.player.video.engine

import android.content.Context
import android.graphics.Bitmap
import android.view.View
import com.raulshma.jellyplay.core.data.playback.PlayerLifecycleCallbacks
import com.raulshma.jellyplay.core.model.DecoderMode
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.EqualizerSettings
import com.raulshma.jellyplay.core.model.SubtitleStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

data class PlaybackRequest(
    val uri: String,
    val title: String,
    val startPositionMs: Long = 0,
    val artworkUri: String? = null,
    val externalSubtitles: List<SubtitleSource> = emptyList(),
    val headers: Map<String, String> = emptyMap(),
    val preferredAudioLanguage: String? = null,
    val preferredSubtitleLanguage: String? = null,
    val maxVideoBitrate: Int? = null,
    val serverUrl: String? = null,
    val authToken: String? = null,
)

data class SubtitleSource(
    val url: String,
    val label: String,
    val language: String?,
    val mimeType: String?,
    val codec: String? = null,
    val isDefault: Boolean = false,
    val isForced: Boolean = false,
    val id: String,
)

data class EngineConfig(
    val decoderMode: DecoderMode = DecoderMode.HW_PREFERRED,
    val audioPassthrough: Boolean = false,
    val audioDelayMs: Long = 0,
    val subtitleDelayMs: Long = 0,
    val subtitleStyle: SubtitleStyle = SubtitleStyle(),
    val audioEffects: AudioEffectsConfig = AudioEffectsConfig(),
)

data class AudioEffectsConfig(
    val dialogueBoostEnabled: Boolean = false,
    val dialogueBoostStrength: EffectStrength = EffectStrength.MODERATE,
    val nightModeEnabled: Boolean = false,
    val nightModeStrength: EffectStrength = EffectStrength.MODERATE,
    val nightModeGain: Int = 0,
    val equalizerEnabled: Boolean = false,
    val equalizerSettings: EqualizerSettings = EqualizerSettings(),
)

data class EngineCapabilities(
    val supportsPip: Boolean = false,
    val supportsMiniMode: Boolean = false,
    val supportsOcr: Boolean = false,
    val supportsCues: Boolean = false,
    val supportsAudioDelay: Boolean = false,
    val supportsSubtitleDelay: Boolean = false,
    val supportsAudioPassthrough: Boolean = false,
    val supportsSubtitleStyle: Boolean = false,
    val supportsDialogueBoost: Boolean = false,
    val supportsNightMode: Boolean = false,
)

enum class EnginePlaybackState {
    IDLE, BUFFERING, READY, ENDED, ERROR
}

data class MediaTrack(
    val id: String,
    val index: Int,
    val label: String,
    val language: String?,
    val isSelected: Boolean,
    val type: TrackType,
    val trackGroup: Any? = null,
)

enum class TrackType { AUDIO, SUBTITLE }

interface MediaEngine : PlayerLifecycleCallbacks {
    
    // 1. Initialization
    fun load(request: PlaybackRequest)
    fun release()

    // 2. Core Controls
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun setPlaybackSpeed(speed: Float)

    // 3. Reactive State
    val playbackState: StateFlow<EnginePlaybackState>
    val isPlaying: StateFlow<Boolean>
    val currentPositionMs: Long
    val durationMs: Long
    val positionFlow: Flow<Long>
    val currentCues: StateFlow<List<String>> // Simple cue strings for OCR/Translate
    val errorFlow: Flow<String>

    // 4. Configuration & Capabilities
    val capabilities: EngineCapabilities
    fun updateConfig(config: EngineConfig)

    // 5. Track Selection
    val availableTracks: StateFlow<List<MediaTrack>>
    fun selectTrack(type: TrackType, index: Int, trackGroup: Any? = null)

    // 6. UI Binding
    fun createSurfaceView(context: Context): View
    fun applySubtitleStyleToView(view: View, style: SubtitleStyle)
    fun setAspectRatio(mode: Int, ratio: Float? = null)
    fun captureViewBitmap(): Bitmap?
    
    // Internal state access (needed for some specific features, but keep to a minimum)
    val playbackSpeed: Float
    val audioSessionId: Int

    /**
     * Provides access to the underlying AndroidX Player instance (e.g. ExoPlayer).
     * Used by the video player ViewModel to create a MediaSession for notification
     * and lock screen controls. Returns null for engines that don't use Media3.
     */
    val underlyingPlayer: androidx.media3.common.Player? get() = null
}
