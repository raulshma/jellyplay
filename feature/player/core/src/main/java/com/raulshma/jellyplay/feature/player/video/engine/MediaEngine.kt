package com.raulshma.jellyplay.feature.player.video.engine

import android.content.Context
import android.view.View
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
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
    /**
     * Optional per-track ReplayGain value (dB) sourced from the media
     * item's `normalizationGain` (Jellyfin). Consumed by engines that
     * support TRACK/ALBUM loudness normalization via an in-sink
     * `AudioProcessor` (currently [ExoPlayerEngine]). `null` means the
     * server provided no gain; TRACK/ALBUM then behave as a no-op.
     */
    val normalizationGain: Float? = null,
    /**
     * Optional MIME type hint for the primary media item. When set, ExoPlayer
     * uses it in preference to URI-extension inference to pick the extractor,
     * which is essential for downloaded files whose on-disk extension does
     * not match their actual container (e.g. an MKV stream saved as `.mp4`).
     */
    val mimeType: String? = null,
    /**
     * Server-reported total runtime in milliseconds, derived from the media
     * item's `runTimeTicks`. Used by engines as a duration fallback when the
     * demuxer cannot resolve one for HLS/transcoded streams (where mpv's
     * `duration` property is frequently 0 or only partially resolved). `0`
     * when no server runtime is available (e.g. unknown-length items).
     */
    val serverDurationMs: Long = 0L,
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
    /**
     * Optional DRM hook. When non-null, engines that support DRM (currently
     * [ExoPlayerEngine]) attach the supplied [androidx.media3.exoplayer.drm.DrmSessionManager].
     * Defaults to `null` so non-DRM playback — and the rest of the codebase —
     * is unaffected. See [EngineDrmSessionManagerProvider].
     */
    val drmSessionManagerProvider: EngineDrmSessionManagerProvider? = null,
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

@Immutable
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
    val supportsLiveQualitySwitch: Boolean = false,
    val supportsBandwidthEstimate: Boolean = false,
    val supportsAssOverride: Boolean = false,
    /**
     * Distinguishes "renders ASS/SSA" (`supportsAssOverride = true`, both
     * ExoPlayer and mpv) from "applies the user's style overrides to ASS/SSA
     * tracks" (mpv only via libass `--ass-override=force`). When `false`, ASS
     * tracks render with their embedded styling and the user's colors, borders,
     * and Force-override only affect SRT/VTT. See `assMedia` 0.4.0 degradation
     * notes in `ExoPlayerEngine`.
     */
    val supportsAssStyleOverride: Boolean = false,
    val supportsFontFamily: Boolean = false,
    val supportsFreeFormColors: Boolean = false,
    val supportsBorderStyles: Boolean = false,
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
    /**
     * The demuxer/container stream index (mpv `ff-index`), when available. For
     * container-demuxed tracks this matches the server's `MediaStream.index`,
     * so it is the robust key for resolving a stored Jellyfin stream selection
     * to an engine track — label-based matching breaks when titles are blank,
     * duplicated, or translated. Null for side-loaded (`sub-add`) tracks, which
     * have no container index and are matched by label/url instead.
     */
    val streamIndex: Int? = null,
)

@Immutable
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

/**
 * Static codec/resolution/HDR/audio-channel slice of [EngineVideoStats] — the
 * only fields [com.raulshma.jellyplay.feature.player.video.components.PlaybackMetadataRow]
 * actually reads. Projecting these out via `derivedStateOf` insulates the row
 * from the high-churn fields (droppedFrames, bufferedPositionMs, videoBitrate,
 * estimatedBandwidthBps) that tick multiple times per second during playback,
 * so the metadata row recomposes only when a displayed field actually changes.
 */
@Immutable
data class PlaybackMetadataSnapshot(
    val videoCodec: String? = null,
    val videoHdrType: String? = null,
    val audioCodec: String? = null,
    val audioChannels: Int? = null,
)

/**
 * The strategy interface every playback backend implements.
 *
 * This is intentionally a single wide contract rather than a composition of
 * role interfaces: a previous split into [PlaybackLifecycle] / [PlaybackControl]
 * / [PlaybackState] / [EngineConfigurable] / [TrackControl] / [SubtitleStyling]
 * / [VideoSurfaceBinding] delivered no decoupling, because no consumer ever
 * depended on a narrow role — every call site reached through [MediaEngine].
 * The split was pure ceremony (see deletion-test note in the architecture
 * review). The members are grouped below by concern to keep the surface
 * navigable.
 *
 * Members declared directly here are either overrides of
 * [com.raulshma.jellyplay.core.data.remote.RemotePlayableEngine] or
 * [PlayerLifecycleCallbacks], or special-case internal hooks.
 */
@Stable
interface MediaEngine :
    PlayerLifecycleCallbacks,
    com.raulshma.jellyplay.core.data.remote.RemotePlayableEngine {

    // ── RemotePlayableEngine overrides (re-declared here to carry the contract
    //     and, for some, a default). ──
    override fun release()
    override fun play()
    override fun pause()
    override fun stop()
    override fun seekTo(positionMs: Long)
    override val isPlaying: StateFlow<Boolean>
    override val currentPositionMs: Long
    override fun selectTrack(type: TrackType, index: Int)
    override fun setMaxVideoBitrate(bps: Int?)
    override val underlyingPlayer: androidx.media3.common.Player? get() = null

    // ── Source loading & teardown ──
    fun load(request: PlaybackRequest)

    // ── Speed control ──
    fun setPlaybackSpeed(speed: Float)
    val playbackSpeed: Float

    // ── Reactive, hot state surface: playback state, duration, position/
    //    buffering/video-stats flows, and the adaptive polling knobs that drive
    //    the high-frequency tickers consumed by leaf UI. ──
    val playbackState: StateFlow<EnginePlaybackState>
    val durationMs: Long
    val positionFlow: Flow<Long>

    /**
     * Structured playback errors. Engines map their native error surface
     * (ExoPlayer `PlaybackException`, mpv/libvlc events) onto the
     * [EngineError] taxonomy so the UI can distinguish retryable from fatal
     * failures and offer the right affordance. The bare `Flow<String>`
     * channel was replaced because every error collapsed to
     * [EngineError.Unknown] and the retry / switch-engine paths never fired.
     */
    val errorFlow: Flow<EngineError>
    val bufferedPositionMs: StateFlow<Long>
    val videoStats: StateFlow<EngineVideoStats>

    val pollingIntervalMs: StateFlow<Long>
    val videoStatsEnabled: StateFlow<Boolean>
    fun setPollingIntervalMs(ms: Long)
    fun setVideoStatsEnabled(enabled: Boolean)

    val audioSessionId: Int

    // ── Capability advertisement and live configuration.
    //
    //    [EngineCapabilities] is the runtime query surface the UI reads to
    //    show/hide controls — see [EngineCapabilityMatrix]. ──
    val capabilities: EngineCapabilities
    fun updateConfig(config: EngineConfig)

    // ── Track enumeration and runtime subtitle-track addition. ──
    val availableTracks: StateFlow<List<MediaTrack>>
    fun addExternalSubtitle(source: SubtitleSource) {}

    // ── Per-engine subtitle styling applied to the engine's native subtitle
    //    surface (Media3 `SubtitleView` / libass / VLC freetype). Subtitles are
    //    rendered by each engine's own native renderer; there is no in-app
    //    Compose cue overlay (the previous `currentCues`/`MpvSubtitleOverlay`
    //    path was reserved and never enabled, and has been removed). ──
    fun applySubtitleStyleToView(view: View, style: SubtitleStyle)

    // ── Native surface creation and aspect-ratio control. ──
    fun createSurfaceView(context: Context): View
    fun setAspectRatio(mode: Int, ratio: Float? = null)

    // ── Special-case internal hooks. ──
    fun setRenderer(renderer: Any?) {}
}
