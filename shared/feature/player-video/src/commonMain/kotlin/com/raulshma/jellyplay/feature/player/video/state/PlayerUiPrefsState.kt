package com.raulshma.jellyplay.feature.player.video.state

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.BitmapSubtitleScaleMode
import com.raulshma.jellyplay.core.model.BufferingVeil
import com.raulshma.jellyplay.core.model.ControlLayout
import com.raulshma.jellyplay.core.model.DevicePerformanceTier
import com.raulshma.jellyplay.core.model.MaxZoom
import com.raulshma.jellyplay.core.model.MpvDebandMode
import com.raulshma.jellyplay.core.model.MpvToneMappingCurve
import com.raulshma.jellyplay.core.model.OrientationMode
import com.raulshma.jellyplay.core.model.PauseFadeStyle
import com.raulshma.jellyplay.core.model.PauseScreensaverMode
import com.raulshma.jellyplay.core.model.PlaybackMode
import com.raulshma.jellyplay.core.model.PlayerSeekBarStyle
import com.raulshma.jellyplay.core.model.PlayerHoldSpeedPillStyle
import com.raulshma.jellyplay.core.model.PollCadence
import com.raulshma.jellyplay.core.model.SpeedPresetSet
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.core.model.TrickplayInfo
import com.raulshma.jellyplay.core.model.TrickplayMode
import com.raulshma.jellyplay.core.model.TvTimeoutScale
import com.raulshma.jellyplay.core.model.VideoPerformanceProfile
import com.raulshma.jellyplay.core.model.VideoPerformanceTuning

/**
 * Player UI / system preferences: control visibility, orientation, trickplay,
 * streaming quality, playback mode, lock/PIN. These are low-frequency (set once
 * or via settings) — splitting them out insulates the player screen from the
 * high-churn position/buffering flows.
 */
@Immutable
data class PlayerUiPrefsState(
    val controlsTimeoutMs: Long = 5_000L,
    /** The skip-intro/segment invitation countdown length (3–30 s); `0` = whole segment (no countdown). */
    val skipPromptDurationMs: Long = 10_000L,
    /**
     * When on, PAUSING must not summon the
     * control overlay. Default **off** — today's show-on-pause behavior.
     * Only the summons is gated: an already-visible overlay is never
     * force-hidden (the [controlsTimeoutMs] auto-hide owns dismissal), and
     * resuming still summons as before.
     */
    val hideOsdOnPause: Boolean = false,
    val defaultOrientation: OrientationMode = OrientationMode.SENSOR_LANDSCAPE,
    val passOutProtectionHours: Int = 0,
    val showVideoStats: Boolean = false,
    val showPlaybackMetadata: Boolean = true,
    val showClock: Boolean = false,
    val showTimeRemaining: Boolean = false,
    val keepScreenOnDuringVideo: Boolean = true,
    val usePinForPlayerLock: Boolean = false,
    /**
     * Presence flag for the player-lock PIN. The hash itself never
     * leaves the VM/prefs — surfacing it through per-frame UiState churned
     * state identity on PIN change and exposed the hash to equals/hashCode/
     * toString (log risk). Callers only ever gate on presence.
     */
    val hasPin: Boolean = false,
    val trickplayEnabled: Boolean = true,
    val trickplayOnSeekGesture: Boolean = true,
    val trickplayInfo: TrickplayInfo? = null,
    /**
     * The trickplay preview's presentation: bubble, filmstrip or fullscreen.
     * Presentation-only — the preparation machinery is [trickplayEnabled]'s.
     */
    val trickplayMode: TrickplayMode = TrickplayMode.BUBBLE,
    /**
     * What replaces the video after a long pause (off / clock / logo / fade
     * to black). Rendered by the pause surface, not the engine.
     */
    val pauseScreensaverMode: PauseScreensaverMode = PauseScreensaverMode.OFF,
    /** Minutes of pause before [pauseScreensaverMode] arms (1–60). */
    val pauseScreensaverAfterMinutes: Int = 5,
    /** The paused frame blurs behind the pause surface. Default off. */
    val ambientBlurEnabled: Boolean = false,
    /** Whether the pause surface shows item details. Default off. */
    val pauseDetailsEnabled: Boolean = false,
    /**
     * mpv `tone-mapping` curve family (HDR→SDR). Engine-config-affecting:
     * the fanout rebuilds the engine config when it moves.
     */
    val mpvToneMappingCurve: MpvToneMappingCurve = MpvToneMappingCurve.AUTO,
    /**
     * mpv deband strength. Engine-config-affecting: the fanout rebuilds the
     * engine config when it moves.
     */
    val mpvDebandMode: MpvDebandMode = MpvDebandMode.OFF,
    /**
     * mpv motion interpolation (`interpolation=yes`). Engine-config-affecting:
     * the fanout rebuilds the engine config when it moves.
     */
    val mpvMotionInterpolation: Boolean = false,
    /**
     * How bitmap subtitle (PGS/VobSub) planes scale onto the frame.
     * Engine-config-affecting: the fanout rebuilds the engine config when it
     * moves.
     */
    val bitmapSubtitleScaleMode: BitmapSubtitleScaleMode = BitmapSubtitleScaleMode.FIT,
    /**
     * Raw custom on-screen button definitions (JSON — issue #27). Render-side
     * only: the screen's [com.raulshma.jellyplay.feature.player.video.components.CustomButtonsLayer]
     * parses it; the engine config does not consume it.
     */
    val mpvCustomButtons: String = "",
    /**
     * Seconds before item end that the "Next up" button appears (5–120).
     * Plumbed for the episode-continuation surface — not consumed by the
     * player engine.
     */
    val nextUpButtonPrerollSec: Int = 30,
    /**
     * Player control-overlay enter/exit animations (top bar, bottom bar,
     * center play button). Default **on**; off snaps the transitions — the
     * flag feeds the core:ui motion functions' parameter, not a composition
     * local.
     */
    val overlayAnimations: Boolean = true,
    /** "Restart" resume-chip display duration (0–10 000); 0 = never show. */
    val resumeChipMs: Long = 3_000L,
    /** The center prev/play/next cluster. Default on; off renders only the play button. */
    val showPrevNextButtons: Boolean = true,
    /** The seek bar's thickness. Default DEFAULT. */
    val seekBarStyle: PlayerSeekBarStyle = PlayerSeekBarStyle.DEFAULT,
    /** The seek bar's buffered bands. Default on. */
    val showBufferedRange: Boolean = true,
    /** The seek bar's chapter markers. Default on. */
    val showChapterMarkers: Boolean = true,
    /** Hides the seek bar's elapsed/total labels (minimal mode). Default off. */
    val hideSeekTimestamps: Boolean = false,
    /** The buffered-percentage readout beside the seek bar's time row. Default off. */
    val showBufferPercent: Boolean = false,
    /** The seek-scrub preview's target timestamp. Default on. */
    val seekPreviewTime: Boolean = true,
    /** The active-engine chip in the player's top bar. Default off. */
    val showEngineBadge: Boolean = false,
    /** The episode browser filters fully-watched episodes. Default off. */
    val episodeBrowserHideWatched: Boolean = false,
    /** The hold-speed pill's chrome. Default DEFAULT (speed readout). */
    val holdSpeedPillStyle: PlayerHoldSpeedPillStyle = PlayerHoldSpeedPillStyle.DEFAULT,
    /**
     * Back-to-back autoplay cap (`0` = unlimited). Plumbed for the
     * episode-continuation surface — not consumed by the player engine.
     */
    val autoplayEpisodeLimit: Int = 0,
    /**
     * Show the next-episode details card after an episode finishes. Plumbed
     * for the episode-continuation surface — not consumed by the player
     * engine.
     */
    val showNextEpisodeDetailsAfterFinish: Boolean = true,
    val streamingQuality: StreamingQuality = StreamingQuality.AUTO,
    val adaptiveBitrateEnabled: Boolean = true,
    val playbackMode: PlaybackMode = PlaybackMode.AUTO,
    /**
     * The playback performance profile (issue #186): AUTO detects the device
     * tier once; LOW/MEDIUM/HIGH pin it. A persisted preference — seeded from
     * the aggregate's playback slice (the `videoPerformanceProfile` leaf row)
     * and written by the settings/sheet rows of the profile waves.
     */
    val videoPerformanceProfile: VideoPerformanceProfile = VideoPerformanceProfile.AUTO,
    /**
     * The AUTO detector's sticky tier, straight from the resolver's detection
     * cache — runtime state, never a preference. Kept beside the profile so
     * the settings row can subtitle an explicit selection with what AUTO
     * would have picked. `null` until a detection lands.
     */
    val detectedPerformanceTier: DevicePerformanceTier? = null,
    /**
     * The RESOLVED tuning the engine config and request plan consume (§5.8 /
     * §5.9). Derived state — never user-written: seeded per session load from
     * the resolver, and the tuning the engine-config rebuild passes into
     * `EngineConfigBuilder.buildFromSlices`. [VideoPerformanceTuning.NONE]
     * (the HIGH no-op) until a resolution lands.
     */
    val performanceTuning: VideoPerformanceTuning = VideoPerformanceTuning.NONE,
    // ── The second QoL wave: transport/HUD cadence + preset mirrors. ──
    /** The TV controls-auto-hide timeout multiplier. Default X2 (the long-standing doubling). */
    val tvTimeoutScale: TvTimeoutScale = TvTimeoutScale.X2,
    /** The pause-details panel's fade durations. Default DEFAULT (200/300ms). */
    val pauseFadeStyle: PauseFadeStyle = PauseFadeStyle.DEFAULT,
    /** The control button clusters' arrangement. Default DEFAULT. */
    val controlLayout: ControlLayout = ControlLayout.DEFAULT,
    /** The pinch-zoom ceiling. Default X3 (the long-standing 1..3 clamp). */
    val maxZoom: MaxZoom = MaxZoom.X3,
    /** The engine's position-poll cadence family. Default DEFAULT (250/1000ms). */
    val pollCadence: PollCadence = PollCadence.DEFAULT,
    /** Sleep-timer cancel-confirm threshold in minutes (1–30). Default 5. */
    val sleepConfirmMinutes: Int = 5,
    /** Allow the ambient backdrop under the FILL aspect override. Default off. */
    val ambientUnderFill: Boolean = false,
    /** The buffering spinner's display rule. Default DEFAULT. */
    val bufferingVeil: BufferingVeil = BufferingVeil.DEFAULT,
    /** The speed sheet's chip preset family. Default DEFAULT (the full ladder). */
    val speedPresetSet: SpeedPresetSet = SpeedPresetSet.DEFAULT,
    /** The first BACK shows an in-player exit confirmation. Default off. */
    val confirmExit: Boolean = false,
)
