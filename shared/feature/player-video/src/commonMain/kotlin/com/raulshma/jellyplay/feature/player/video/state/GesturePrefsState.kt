package com.raulshma.jellyplay.feature.player.video.state

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.GestureIndicatorSide
import com.raulshma.jellyplay.core.model.GestureMode

/**
 * Gesture / hold-speed / brightness / frame-rate preference slice. The two
 * tier flags are derived from the stored [GestureMode] at seed time:
 * tap covers taps / double-tap seek / long-press hold-speed / pinch-zoom;
 * swipe covers the single-finger drag surface (seek, brightness, volume,
 * edge swipe).
 */
@Immutable
data class GesturePrefsState(
    val tapGesturesEnabled: Boolean = true,
    val swipeGesturesEnabled: Boolean = true,
    val holdSpeedEnabled: Boolean = true,
    val holdSpeedMultiplier: Float = 2.0f,
    val isHoldSpeedActive: Boolean = false,
    val defaultSpeed: Float = 1.0f,
    val swipeSeekMaxMs: Long = 120_000L,
    val seekDurationMs: Long = 10_000L,
    val rememberBrightness: Boolean = false,
    val brightnessLevel: Float = 0.5f,
    val gestureIndicatorSide: GestureIndicatorSide = GestureIndicatorSide.OPPOSITE,
    val frameRateMatching: Boolean = false,
    val refreshRateMode: com.raulshma.jellyplay.core.model.RefreshRateMode = com.raulshma.jellyplay.core.model.RefreshRateMode.OFF,
)
