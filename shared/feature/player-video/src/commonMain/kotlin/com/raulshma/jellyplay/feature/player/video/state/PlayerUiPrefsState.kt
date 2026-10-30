package com.raulshma.jellyplay.feature.player.video.state

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.BitmapSubtitleScaleMode
import com.raulshma.jellyplay.core.model.MpvDebandMode
import com.raulshma.jellyplay.core.model.MpvToneMappingCurve
import com.raulshma.jellyplay.core.model.OrientationMode
import com.raulshma.jellyplay.core.model.PauseScreensaverMode
import com.raulshma.jellyplay.core.model.PlaybackMode
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.core.model.TrickplayInfo
import com.raulshma.jellyplay.core.model.TrickplayMode

/**
 * Player UI / system preferences: control visibility, orientation, trickplay,
 * streaming quality, playback mode, lock/PIN. These are low-frequency (set once
 * or via settings) — splitting them out insulates the player screen from the
 * high-churn position/buffering flows.
 */
@Immutable
data class PlayerUiPrefsState(
    val controlsTimeoutMs: Long = 5_000L,
    /** The skip-intro/segment invitation countdown length (3–30 s). */
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
)
