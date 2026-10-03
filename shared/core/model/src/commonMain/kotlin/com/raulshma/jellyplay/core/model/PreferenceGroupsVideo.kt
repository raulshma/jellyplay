package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * The video-playback preference aggregates: the logical VideoPlayer domain
 * and the PlaybackSettingsScreen slice.
 */

@Immutable
@Serializable
data class VideoPlayerPreferences(
    val preferredPlayer: PlayerType = PlayerType.EXO_PLAYER,
    val decoderMode: DecoderMode = DecoderMode.HW_PREFERRED,
    val audioPassthrough: Boolean = false,
    val frameRateMatching: Boolean = false,
    /**
     * Granular refresh-rate / resolution switching mode. Supersedes
     * [frameRateMatching] (which stays as a legacy boolean alias: `true` ≈
     * [RefreshRateMode.FRAME_RATE_ONLY]). When both are set, this mode wins.
     */
    val refreshRateMode: RefreshRateMode = RefreshRateMode.OFF,
    val videoSeekDurationMs: Long = 10_000L,
    val videoDefaultOrientation: OrientationMode = OrientationMode.SENSOR_LANDSCAPE,
    val videoControlsTimeoutMs: Long = 5_000L,
    val videoGestureMode: GestureMode = GestureMode.ALL,
    val videoHoldSpeedEnabled: Boolean = true,
    val videoHoldSpeedMultiplier: Float = 2.0f,
    val videoDefaultSpeed: Float = 1.0f,
    val videoDefaultAspectRatio: String = "AUTO",
    val videoAutoplayNext: Boolean = true,
    val trailerAutoplay: Boolean = true,
    val videoSwipeSeekMaxMs: Long = 120_000L,
    val videoRememberBrightness: Boolean = true,
    val videoBrightnessLevel: Float = 0.5f,
    val videoGestureIndicatorSide: GestureIndicatorSide = GestureIndicatorSide.OPPOSITE,
    val trickplayEnabled: Boolean = true,
    val trickplayOnSeekGesture: Boolean = true,
    val videoEpisodeBrowserEnabled: Boolean = true,
    val videoShowPlaybackMetadata: Boolean = true,
    val videoPreloadBufferSize: PreloadBufferSize = PreloadBufferSize.MEDIUM,
    val videoCacheSizeMb: Int = 1024,
    val keepScreenOnDuringVideo: Boolean = true,
    val showTimeRemaining: Boolean = false,
    val pauseOnAudioFocusLoss: Boolean = true,
    val volumeBoostEnabled: Boolean = false,
    val volumeBoostGain: Int = 0,
    val backgroundVideoAudioEnabled: Boolean = false,
    val autoPlayCountdownSec: Int = 10,
    val reduceMotionEnabled: Boolean = false,
    val preferAudioDescription: Boolean = false,
    val highContrastSubtitles: Boolean = false,
    val blueLightFilterEnabled: Boolean = false,
    val blueLightFilterStrength: Float = 0.3f,
    val tvZoomModePercent: Float = 0f,
    val mpvConfig: MpvEngineConfig = MpvEngineConfig(),
    val libVlcConfig: LibVlcEngineConfig = LibVlcEngineConfig(),
    val exoPlayerConfig: ExoPlayerEngineConfig = ExoPlayerEngineConfig(),
)

// ---------------------------------------------------------------------------
// Per-screen preference slices.
//
// The logical-domain aggregates group fields by *logical domain* but several
// settings screens read fields that span domains (e.g. the Playback screen
// shows casting + DVR + syncplay + video settings; the Appearance screen shows
// theme + home layout + newsletter settings). Each slice below is the *exact*
// set of fields one settings sub-screen reads, so collecting it recomposes
// only when one of that screen's fields changes. Field names deliberately
// match the legacy `UserPreferences` aggregate these slices were carved from:
// that keeps the screen bodies (`preferences.X`) untouched by the carve-up.
//
// A field that two screens both display (e.g. `dialogueBoostEnabled` appears
// on both Playback and Audio) is projected into both slices. A write to such
// a field legitimately recomposes both screens; `distinctUntilChanged` keeps
// each slice de-duplicated.
// ---------------------------------------------------------------------------

/** Fields read by `PlaybackSettingsScreen`. */
@Immutable
@Serializable
data class PlaybackPreferences(
    val preferredPlayer: PlayerType = PlayerType.EXO_PLAYER,
    /** Which third-party app the EXTERNAL arm hands off to (chooser when unset). */
    val preferredExternalPlayer: ExternalPlayerApp = ExternalPlayerApp.SYSTEM_CHOOSER,
    val decoderMode: DecoderMode = DecoderMode.HW_PREFERRED,
    val audioPassthrough: Boolean = false,
    /**
     * The per-codec passthrough allow-list under the master
     * [audioPassthrough] toggle — a codec left out is neither bitstreamed
     * nor advertised for direct play (the server transcodes it to an
     * allowed codec instead).
     */
    val audioPassthroughCodecs: Set<AudioPassthroughCodec> = AudioPassthroughCodec.ALL,
    /** The speaker-layout cap applied by every engine (`AUTO` = uncapped). */
    val maxAudioChannels: MaxAudioChannelsEnum = MaxAudioChannelsEnum.AUTO,
    /**
     * Stereo-downmix loudness compensation in dB (0–12); 0 = off. Feeds the
     * loudness-enhancer gain on engines that expose one.
     */
    val downmixBoostDb: Float = 0f,
    val frameRateMatching: Boolean = false,
    /**
     * Granular refresh-rate / resolution switching mode. Supersedes
     * [frameRateMatching] (which stays as a legacy boolean alias: `true` ≈
     * [RefreshRateMode.FRAME_RATE_ONLY]). When both are set, this mode wins.
     */
    val refreshRateMode: RefreshRateMode = RefreshRateMode.OFF,
    val videoSeekDurationMs: Long = 10_000L,
    val videoDefaultOrientation: OrientationMode = OrientationMode.SENSOR_LANDSCAPE,
    val videoControlsTimeoutMs: Long = 5_000L,
    val videoGestureMode: GestureMode = GestureMode.ALL,
    val videoHoldSpeedEnabled: Boolean = true,
    val videoHoldSpeedMultiplier: Float = 2.0f,
    val videoDefaultSpeed: Float = 1.0f,
    val videoDefaultAspectRatio: String = "AUTO",
    val videoAutoplayNext: Boolean = true,
    val trailerAutoplay: Boolean = true,
    val cinemaModeEnabled: Boolean = false,
    /**
     * "Still watching?" confirm prompt: which trigger arms are on
     * (see [StillWatchingMode]). The rows ride the autoplay toggle.
     */
    val stillWatchingMode: StillWatchingMode = StillWatchingMode.OFF,
    /** The episode arm's threshold — consecutive auto-played episodes; 0 = off. */
    val stillWatchingEpisodeThreshold: Int = 0,
    val videoSwipeSeekMaxMs: Long = 120_000L,
    val videoRememberBrightness: Boolean = true,
    val videoBrightnessLevel: Float = 0.5f,
    val videoGestureIndicatorSide: GestureIndicatorSide = GestureIndicatorSide.OPPOSITE,
    val videoSkipBackOnResumeMs: Long = 0L,
    val videoPassOutProtectionHours: Int = 0,
    val trickplayEnabled: Boolean = true,
    val trickplayOnSeekGesture: Boolean = true,
    val segmentBehaviors: Map<MediaSegmentType, SegmentBehavior> = SegmentBehavior.DEFAULT_BEHAVIORS,
    /** Skip-on-forward-seek. Default off — see `VideoPlayerSlice`. */
    val skipSegmentsOnSeek: Boolean = false,
    val videoEpisodeBrowserEnabled: Boolean = true,
    val videoShowPlaybackMetadata: Boolean = true,
    val videoPreloadBufferSize: PreloadBufferSize = PreloadBufferSize.MEDIUM,
    val videoCacheSizeMb: Int = 1024,
    val keepScreenOnDuringVideo: Boolean = true,
    val showTimeRemaining: Boolean = false,
    val pauseOnAudioFocusLoss: Boolean = true,
    val duckOnTransientFocusLoss: Boolean = false,
    val dialogueBoostEnabled: Boolean = false,
    val dialogueBoostStrength: EffectStrength = EffectStrength.MODERATE,
    val audioDelayMs: Long = 0L,
    val backgroundVideoAudioEnabled: Boolean = false,
    /**
     * Whether leaving the player during playback auto-enters picture-in-picture
     * (issue #167). Default `true` keeps the historical behaviour; off makes
     * Home/recents background the app normally. Manual PiP entry via the
     * controls button is unaffected. Android-only surface (no desktop PiP).
     */
    val autoEnterPip: Boolean = true,
    val autoPlayCountdownSec: Int = 10,
    val incognitoModeEnabled: Boolean = false,
    val showClockInPlayer: Boolean = false,
    val tvZoomModePercent: Float = 0f,
    val streamingQuality: StreamingQuality = StreamingQuality.AUTO,
    val liveStreamOption: LiveStreamOption = LiveStreamOption.AUTO,
    /** Which copy plays when a download and a reachable server both exist. */
    val offlinePlaybackPreference: OfflinePlaybackPreference = OfflinePlaybackPreference.PREFER_DOWNLOADED,
    val mpvConfig: MpvEngineConfig = MpvEngineConfig(),
    val libVlcConfig: LibVlcEngineConfig = LibVlcEngineConfig(),
    val exoPlayerConfig: ExoPlayerEngineConfig = ExoPlayerEngineConfig(),
    val syncPlayJoinBehavior: SyncPlayJoinBehavior = SyncPlayJoinBehavior.ASK,
    val syncPlayToleranceMs: Long = 100L,
    val syncPlayAutoAcceptInvites: Boolean = false,
    val defaultCastingStrategy: CastingStrategy = CastingStrategy.ASK,
    val backgroundCastingEnabled: Boolean = true,
    val preferredRenderer: String? = null,
    val dvrPrePaddingMinutes: Int = 0,
    val dvrPostPaddingMinutes: Int = 0,
    val dvrRecordingQuality: String = "AUTO",
    val androidTvWatchNextEnabled: Boolean = true,
    /**
     * Per-content-type volume memory is on — one remembered level
     * per bucket (video / music / audiobook), applied at item start and
     * written on user-initiated changes. Desktop-video-backed (the app owns
     * mpv's volume scalar there); the row is capability-hidden on Android,
     * whose video volume is the system stream's.
     */
    val rememberVolumePerContentType: Boolean = true,
)
