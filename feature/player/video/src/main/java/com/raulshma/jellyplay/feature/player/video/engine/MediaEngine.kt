package com.raulshma.jellyplay.feature.player.video.engine

import android.content.Context
import android.view.View
import com.raulshma.jellyplay.core.data.playback.PlayerLifecycleCallbacks
import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.ChannelMixMode
import com.raulshma.jellyplay.core.model.DecoderMode
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.EngineSpecificConfig
import com.raulshma.jellyplay.core.model.EqualizerSettings
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.model.TrackType
import com.raulshma.jellyplay.core.model.VideoEffectsConfig
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
    val minBufferMs: Int = 15_000,
    val maxBufferMs: Int = 50_000,
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
    val videoEffects: VideoEffectsConfig = VideoEffectsConfig(),
    val engineSpecific: EngineSpecificConfig? = null,
    val pauseOnAudioFocusLoss: Boolean = true,
)

data class AudioEffectsConfig(
    val dialogueBoostEnabled: Boolean = false,
    val dialogueBoostStrength: EffectStrength = EffectStrength.MODERATE,
    val nightModeEnabled: Boolean = false,
    val nightModeStrength: EffectStrength = EffectStrength.MODERATE,
    val nightModeGain: Int = 0,
    val equalizerEnabled: Boolean = false,
    val equalizerSettings: EqualizerSettings = EqualizerSettings(),
    val audioNormalizationMode: AudioNormalizationMode = AudioNormalizationMode.NONE,
    val audioNormalizationEnabled: Boolean = false,
    val channelMixMode: ChannelMixMode = ChannelMixMode.AUTO,
    val channelMixEnabled: Boolean = false,
    val bassBoostEnabled: Boolean = false,
    val bassBoostStrength: EffectStrength = EffectStrength.MODERATE,
    val virtualizerEnabled: Boolean = false,
    val virtualizerStrength: Int = 500,
    val reverbPreset: com.raulshma.jellyplay.core.model.ReverbPreset = com.raulshma.jellyplay.core.model.ReverbPreset.NONE,
    val volumeBoostEnabled: Boolean = false,
    val volumeBoostGain: Int = 0,
)

data class EngineCapabilities(
    val supportsPip: Boolean = false,
    val supportsMiniMode: Boolean = false,
    val supportsCues: Boolean = false,
    val supportsAudioDelay: Boolean = false,
    val supportsSubtitleDelay: Boolean = false,
    val supportsAudioPassthrough: Boolean = false,
    val supportsSubtitleStyle: Boolean = false,
    val supportsSubtitleVerticalPosition: Boolean = false,
    val supportsDialogueBoost: Boolean = false,
    val supportsNightMode: Boolean = false,
    val supportsAudioNormalization: Boolean = false,
    val supportsChannelMixing: Boolean = false,
    val supportsVideoFilters: Boolean = false,
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

data class EngineVideoStats(
    val videoCodec: String? = null,
    val videoDecoder: String? = null,
    val videoResolution: String? = null,
    val videoFrameRate: Float? = null,
    val videoBitrate: Int? = null,
    val videoColorRange: String? = null,
    val videoHdrType: String? = null,
    val videoColorDepth: String? = null,
    val audioCodec: String? = null,
    val audioSampleRate: Int? = null,
    val audioChannels: Int? = null,
    val audioBitrate: Int? = null,
    val estimatedBandwidthBps: Long = 0,
    val droppedFrames: Long = 0,
    val totalVideoFrames: Long = 0,
    val bufferedPositionMs: Long = 0,
    val bufferSizeBytes: Long = 0,
)

interface MediaEngine : PlayerLifecycleCallbacks, com.raulshma.jellyplay.core.data.remote.RemotePlayableEngine {
    
    // 1. Initialization
    fun load(request: PlaybackRequest)
    fun release()

    // 2. Core Controls
    override fun play()
    override fun pause()
    override fun stop()
    override fun seekTo(positionMs: Long)
    fun setPlaybackSpeed(speed: Float)

    // 3. Reactive State
    val playbackState: StateFlow<EnginePlaybackState>
    override val isPlaying: StateFlow<Boolean>
    override val currentPositionMs: Long
    val durationMs: Long
    val positionFlow: Flow<Long>
    val currentCues: StateFlow<List<String>>
    val errorFlow: Flow<String>
    val bufferedPositionMs: StateFlow<Long>
    val videoStats: StateFlow<EngineVideoStats>

    // 3b. Adaptive polling
    val pollingIntervalMs: StateFlow<Long>
    val videoStatsEnabled: StateFlow<Boolean>
    fun setPollingIntervalMs(ms: Long)
    fun setVideoStatsEnabled(enabled: Boolean)

    // 4. Configuration & Capabilities
    val capabilities: EngineCapabilities
    fun updateConfig(config: EngineConfig)

    // 5. Track Selection
    val availableTracks: StateFlow<List<MediaTrack>>
    override fun selectTrack(type: TrackType, index: Int, trackGroup: Any?)

    // 5b. Quality
    override fun setMaxVideoBitrate(bps: Int?)

    // 5c. Runtime subtitle addition
    fun addExternalSubtitle(source: SubtitleSource) {}

    // 6. UI Binding
    fun createSurfaceView(context: Context): View
    fun applySubtitleStyleToView(view: View, style: SubtitleStyle)
    fun setAspectRatio(mode: Int, ratio: Float? = null)

    // Internal state access (needed for some specific features, but keep to a minimum)
    val playbackSpeed: Float
    val audioSessionId: Int

    override val underlyingPlayer: androidx.media3.common.Player? get() = null

    fun setRenderer(renderer: Any?) {}
}
