package com.raulshma.jellyplay.feature.player.video.state

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.GestureIndicatorSide
import com.raulshma.jellyplay.core.model.GestureMode
import com.raulshma.jellyplay.core.model.PlayerInputDefaults
import com.raulshma.jellyplay.core.model.PlayerInputMap
import com.raulshma.jellyplay.core.model.RefreshRateMode

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
)
