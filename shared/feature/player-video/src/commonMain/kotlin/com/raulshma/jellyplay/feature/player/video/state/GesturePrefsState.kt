package com.raulshma.jellyplay.feature.player.video.state

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.GestureIndicatorSide
import com.raulshma.jellyplay.core.model.GestureMode
import com.raulshma.jellyplay.core.model.RefreshRateMode

/**
 * Gesture / hold-speed / brightness / frame-rate preference slice. Carries the
 * stored [GestureMode] as the single source of truth; the two tier flags are
 * derived views onto it: tap covers taps / double-tap seek / long-press
 * hold-speed / pinch-zoom; swipe covers the single-finger drag surface (seek,
 * brightness, volume, edge swipe).
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
) {
    /** Tap tier: taps, double-tap seek, long-press hold-speed, pinch-zoom. */
    val tapGesturesEnabled: Boolean get() = gestureMode.tapsEnabled

    /** Swipe tier: single-finger seek / brightness / volume / edge swipe. */
    val swipeGesturesEnabled: Boolean get() = gestureMode.swipesEnabled
}
