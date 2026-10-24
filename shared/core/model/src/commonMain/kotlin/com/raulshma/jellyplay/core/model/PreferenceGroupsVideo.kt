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
    /**
     * The ADVISORY "allow spatial audio" toggle (Android-only) — see
     * AudioEffectsConfig.spatialAudioEnabled (MediaEngine.kt) for the full
     * rationale. Capability reporting:
     * [com.raulshma.jellyplay.feature.player.video.engine.EngineCapabilities.supportsSpatialAudio].
     */
    val spatialAudioEnabled: Boolean = false,
    /** Qualifies [spatialAudioEnabled] to headphone-class outputs only. */
    val spatialAudioOnlyHeadphones: Boolean = true,
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
    /**
     * Pausing must not summon the control overlay. Default **off**; on, only
     * the summons is suppressed (an already-visible overlay keeps its
     * auto-hide timeout).
     */
    val videoHideOsdOnPause: Boolean = false,
    val videoGestureMode: GestureMode = GestureMode.ALL,
    /**
     * The whole player input mapping (touch / wheel / keyboard / D-pad rows)
     * — the binding editor reads it here; the demoted [videoGestureMode] is
     * the mass-preset record beside it.
     */
    val videoInputBindings: PlayerInputMap = PlayerInputDefaults.defaultMap(),
    /**
     * double-tap-and-hold continuous seek in the seek zones
     * (default ON; off = legacy long-press hold-speed everywhere).
     */
    val videoDoubleTapHoldSeekEnabled: Boolean = true,
    val videoHoldSpeedEnabled: Boolean = true,
    val videoHoldSpeedMultiplier: Float = 2.0f,
    val videoDefaultSpeed: Float = 1.0f,
    val videoDefaultAspectRatio: String = "AUTO",
    val videoAutoplayNext: Boolean = true,
    /** Back-to-back autoplay cap; `0` = unlimited. Mirrors `VideoPlayerSlice.autoplayEpisodeLimit`. */
    val autoplayEpisodeLimit: Int = 0,
    /** Show the next-episode details card after an episode finishes (default on). */
    val showNextEpisodeDetailsAfterFinish: Boolean = true,
    val trailerAutoplay: Boolean = true,
    val cinemaModeEnabled: Boolean = false,
    /**
     * Trailer-then-feature (opt-in): for MOVIE plays with a trailer available,
     * the session queue is seeded with the trailer before the feature. Default
     * off — today's feature-first behavior.
     */
    val playTrailerBeforeMovie: Boolean = false,
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
    /** The trickplay preview's presentation: bubble, filmstrip or fullscreen. Default BUBBLE. */
    val trickplayMode: TrickplayMode = TrickplayMode.BUBBLE,
    val segmentBehaviors: Map<MediaSegmentType, SegmentBehavior> = SegmentBehavior.DEFAULT_BEHAVIORS,
    /**
     * Per-type auto-skip delay (ms) added on top of a segment's start before
     * an AUTO_SKIP behavior fires; all zero by default (skip at start).
     * Only consulted for types whose [segmentBehaviors] entry is AUTO_SKIP.
     */
    val segmentSkipDelaysMs: Map<MediaSegmentType, Long> = MediaSegmentType.DEFAULT_SKIP_DELAYS_MS,
    /** Skip-on-forward-seek. Default off — see `VideoPlayerSlice`. */
    val skipSegmentsOnSeek: Boolean = false,
    /** Seconds before item end that the "Next up" button appears (5–120). Default 30. */
    val nextUpButtonPrerollSec: Int = 30,
    /** The skip-intro/segment invitation countdown length (3–30 s). */
    val skipPromptDurationMs: Long = 10_000L,
    val videoEpisodeBrowserEnabled: Boolean = true,
    val videoShowPlaybackMetadata: Boolean = true,
    val videoPreloadBufferSize: PreloadBufferSize = PreloadBufferSize.MEDIUM,
    val videoCacheSizeMb: Int = 1024,
    val keepScreenOnDuringVideo: Boolean = true,
    val showTimeRemaining: Boolean = false,
    val pauseOnAudioFocusLoss: Boolean = true,
    val duckOnTransientFocusLoss: Boolean = false,
    /**
     * (opt-in, Android-only machinery): resume playback when
     * headphones reconnect, but ONLY for the pause the becoming-noisy event
     * itself made, and only within its freshness window (see
     * `HeadsetResumePolicy` in core:data). Default off.
     */
    val videoResumeOnHeadsetPlug: Boolean = false,
    val dialogueBoostEnabled: Boolean = false,
    val dialogueBoostStrength: EffectStrength = EffectStrength.MODERATE,
    val audioDelayMs: Long = 0L,
    val backgroundVideoAudioEnabled: Boolean = false,
    /**
     * Whether leaving the player during playback auto-enters picture-in-picture.
     * Default `true`; off makes Home/recents background the app normally.
     * Manual PiP entry via the controls button is unaffected. Android-only
     * surface (no desktop PiP).
     */
    val autoEnterPip: Boolean = true,
    val autoPlayCountdownSec: Int = 10,
    /** What happens to an interrupted session on cold start (OFF / ASK / AUTO). Default ASK. */
    val sessionRestoreMode: SessionRestoreMode = SessionRestoreMode.ASK,
    /** Offer to delete the watched movie file after playback finishes. Default off. */
    val offerDeleteAfterPlaybackMovies: Boolean = false,
    /** Offer to delete the watched episode file after playback finishes. Default off. */
    val offerDeleteAfterPlaybackEpisodes: Boolean = false,
    /** The user-authored device-profile JSON merged over the server's transcode decision. Empty = nothing. */
    val customDeviceProfileJson: String = "",
    val incognitoModeEnabled: Boolean = false,
    val showClockInPlayer: Boolean = false,
    /** Ambient blur: the paused frame blurs behind the pause surface. Default off. */
    val ambientBlurEnabled: Boolean = false,
    val tvZoomModePercent: Float = 0f,
    /** How bitmap subtitle (PGS/VobSub) planes scale onto the frame. Default FIT. */
    val bitmapSubtitleScaleMode: BitmapSubtitleScaleMode = BitmapSubtitleScaleMode.FIT,
    /** What replaces the video after a long pause. Default OFF (keep the paused frame). */
    val pauseScreensaverMode: PauseScreensaverMode = PauseScreensaverMode.OFF,
    /** Minutes of pause before the pause screensaver arms (1–60). Default 5. */
    val pauseScreensaverAfterMinutes: Int = 5,
    /** Whether the pause surface shows item details. Default off. */
    val pauseDetailsEnabled: Boolean = false,
    val streamingQuality: StreamingQuality = StreamingQuality.AUTO,
    /** The HDR delivery posture for direct play (which HDR family may reach the display). Default AUTO. */
    val hdrFormatPreference: HdrFormatPreference = HdrFormatPreference.AUTO,
    /** Force transcode for sources taller than this height in px; `0` = off. */
    val forceTranscodeAboveHeight: Int = 0,
    /** The preferred video codec when the server transcodes. Default AUTO (server decides). */
    val preferredTranscodeVideoCodec: TranscodeVideoCodecPreference = TranscodeVideoCodecPreference.AUTO,
    /** The preferred audio codec when the server transcodes. Default AUTO (server decides). */
    val preferredTranscodeAudioCodec: TranscodeAudioCodecPreference = TranscodeAudioCodecPreference.AUTO,
    /**
     * The home-network streaming quality as the persisted enum NAME
     * ([StreamingQuality] vocabulary). Default "AUTO". Stored by
     * `NetworkOfflineStore`; rendered beside the transcode cluster.
     */
    val homeStreamingQuality: String = "AUTO",
    val liveStreamOption: LiveStreamOption = LiveStreamOption.AUTO,
    /** Which copy plays when a download and a reachable server both exist. */
    val offlinePlaybackPreference: OfflinePlaybackPreference = OfflinePlaybackPreference.PREFER_DOWNLOADED,
    /** mpv `tone-mapping` curve family (HDR→SDR). Default AUTO. */
    val mpvToneMappingCurve: MpvToneMappingCurve = MpvToneMappingCurve.AUTO,
    /** mpv deband strength (OFF / LIGHT / MEDIUM / HEAVY). Default OFF. */
    val mpvDebandMode: MpvDebandMode = MpvDebandMode.OFF,
    /** mpv motion interpolation. Default off. */
    val mpvMotionInterpolation: Boolean = false,
    /** Raw `input.conf`-style key bindings text for mpv; empty applies nothing. */
    val mpvInputConf: String = "",
    /** Custom on-screen button definitions; empty applies nothing. */
    val mpvCustomButtons: String = "",
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
