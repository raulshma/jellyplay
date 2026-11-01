package com.raulshma.jellyplay.feature.player.video.state

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.GestureIndicatorSide
import com.raulshma.jellyplay.core.model.GestureMode
import com.raulshma.jellyplay.core.model.HoldSeekCadence
import com.raulshma.jellyplay.core.model.KeyboardSeekPreset
import com.raulshma.jellyplay.core.model.PlayerCenterDoubleTapAction
import com.raulshma.jellyplay.core.model.PlayerDoubleTapZoneBias
import com.raulshma.jellyplay.core.model.PlayerInputDefaults
import com.raulshma.jellyplay.core.model.PlayerInputMap
import com.raulshma.jellyplay.core.model.PlayerSwipeSensitivity
import com.raulshma.jellyplay.core.model.RefreshRateMode
import com.raulshma.jellyplay.core.model.SpeedPillClearance

/**
 * Gesture / hold-speed / brightness / frame-rate preference slice. The
 * input behavior source of truth is [inputMap] (the persisted pattern →
 * action mapping); [gestureMode] is the demoted mass-preset record — the
 * settings row that produced the current touch-tier flags, not a gate the
 * detectors read.
 */
@Immutable
data class GesturePrefsState(
    val gestureMode: GestureMode = GestureMode.ALL,
    val holdSpeedEnabled: Boolean = true,
    val holdSpeedMultiplier: Float = 2.0f,
    val isHoldSpeedActive: Boolean = false,
    val defaultSpeed: Float = 1.0f,
    val swipeSeekMaxMs: Long = 120_000L,
    val seekDurationMs: Long = 10_000L,
    /**
     * Holding the second press of a double-tap in a seek zone
     * keeps repeating the step seek (accelerating) until release, instead of
     * handing the hold to hold-speed. Default ON; turning it off restores the
     * legacy long-press = hold-speed behavior everywhere.
     */
    val doubleTapHoldSeekEnabled: Boolean = true,
    val rememberBrightness: Boolean = false,
    val brightnessLevel: Float = 0.5f,
    val gestureIndicatorSide: GestureIndicatorSide = GestureIndicatorSide.OPPOSITE,
    val frameRateMatching: Boolean = false,
    val refreshRateMode: RefreshRateMode = RefreshRateMode.OFF,
    /**
     * The whole input mapping (touch / wheel / keyboard / D-pad rows). The
     * detectors resolve every gate through this — `PlayerInputPolicy` owns
     * the lookup; the legacy tier flags below are gone (the mapping IS the
     * gate).
     */
    val inputMap: PlayerInputMap = PlayerInputDefaults.defaultMap(),
    /** The brightness/volume swipe-curve sensitivity. Default DEFAULT (exponent 0.8). */
    val swipeSensitivity: PlayerSwipeSensitivity = PlayerSwipeSensitivity.DEFAULT,
    /** The center double-tap zone's action. Default AUTO (zoom reset when zoomed, else play/pause). */
    val centerDoubleTapAction: PlayerCenterDoubleTapAction = PlayerCenterDoubleTapAction.AUTO,
    /** The left/right double-tap zone split. Default DEFAULT (35/65). */
    val doubleTapZoneBias: PlayerDoubleTapZoneBias = PlayerDoubleTapZoneBias.DEFAULT,
    /** The gesture overlays' dismiss delay (200–2 000). */
    val gestureOverlayLingerMs: Long = 800L,
    // ── The second QoL wave's gesture/input cadence + placement mirrors. ──
    /** The double-tap-and-hold repeat cadence family. Default DEFAULT (400→150ms). */
    val holdSeekCadence: HoldSeekCadence = HoldSeekCadence.DEFAULT,
    /** The hold-speed pill's clearance above the bottom controls. Default DEFAULT (180dp). */
    val speedPillClearance: SpeedPillClearance = SpeedPillClearance.DEFAULT,
    /** The keyboard modifier seek-step ladder family. Default DEFAULT (VLC's ladder). */
    val keyboardSeekPreset: KeyboardSeekPreset = KeyboardSeekPreset.DEFAULT,
)
