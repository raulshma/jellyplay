package com.raulshma.jellyplay.feature.player.video

import androidx.compose.runtime.Composable
import com.raulshma.jellyplay.core.model.RefreshRateMode

/**
 * Host-window seam for the commonMain [VideoPlayerScreen] (wave 9A): every
 * system-surface operation the screen performs on its host window — system-bar
 * visibility, keep-screen-on, window brightness, orientation lock, display
 * frame-rate matching — plus the device input facts the keyboard handling
 * needs. The androidMain actual wraps the host [android.app.Activity] exactly
 * as the screen did before the split (same guards, same constants); the
 * jvmMain actual is a stateless no-op singleton because a desktop window has
 * no system bars, no Activity orientation lock, and no OS brightness override
 * (the window manager owns those).
 */
internal interface PlayerWindowOps {

    /**
     * Synchronously-current PiP flag of the host (the collected
     * `pipController.isInPipMode` state lags a frame). Always false on desktop.
     */
    val isInPipMode: Boolean get() = false

    /** Immersive mode: hide the system bars (Android); no-op on desktop. */
    fun hideSystemBars() {}

    /** Restore the system bars on player exit (Android); no-op on desktop. */
    fun showSystemBars() {}

    /** FLAG_KEEP_SCREEN_ON while video plays (Android); no-op on desktop. */
    fun setKeepScreenOn(enabled: Boolean) {}

    /**
     * Read the host window's brightness override, or `-1f` when none is set
     * (the BRIGHTNESS_OVERRIDE_NONE sentinel). Always `-1f` on desktop.
     */
    fun readWindowBrightness(): Float = -1f

    /** Apply a raw brightness override to the host window (gesture path). */
    fun writeWindowBrightness(level: Float) {}

    /**
     * Restore the gesture brightness when a swipe ends: `restored >= 0f`
     * re-applies that level, anything else restores the OS default. Host-alive
     * guarded on Android; no-op on desktop.
     */
    fun restoreWindowBrightness(restored: Float) {}

    /**
     * Apply the persisted brightness level on player entry/resume
     * (host-alive guarded on Android); no-op on desktop.
     */
    fun applyWindowBrightness(level: Float) {}

    /**
     * Current/max music-stream volume pair for the volume gesture.
     * `0 to 0` on desktop — the gesture reads the engine volume instead.
     */
    fun readMusicStreamVolume(): Pair<Int, Int> = 0 to 0

    /** Set the music-stream volume (gesture path); no-op on desktop. */
    fun setMusicStreamVolume(volume: Int) {}

    /** Confirmation haptic for discrete player actions; no-op on desktop. */
    fun performConfirmHaptic() {}

    /** Lock the host to [mode] (Activity orientation lock); no-op on desktop. */
    fun lockOrientation(mode: PlayerOrientationLock) {}

    /**
     * Portrait ↔ landscape toggle. [preferLockedLandscape] resolves the
     * user's configured default landscape mode so the toggle is symmetric.
     * No-op on desktop.
     */
    fun toggleOrientation(preferLockedLandscape: Boolean) {}

    /** Display frame-rate matching (Android FrameRateMatcher); no-op on desktop. */
    fun matchFrameRate(frameRate: Float?, targetWidth: Int?, targetHeight: Int?, mode: RefreshRateMode) {}

    /** Restore the original display mode on player exit; no-op on desktop. */
    fun restoreFrameRateMode() {}

    /**
     * Host-window teardown when the player screen leaves composition (both the
     * background-cast and the full-release paths): unlock orientation to
     * [orientation], clear keep-screen-on, restore OS-default brightness,
     * re-show the system bars and hand the display mode back. Host-alive
     * guarded on Android; no-op on desktop.
     */
    fun restoreOnPlayerExit(orientation: PlayerOrientationLock) {}
}

/**
 * Platform-neutral orientation requests. The androidMain actual maps each
 * value to the `ActivityInfo` screen-orientation constant the screen used
 * before the split.
 */
internal enum class PlayerOrientationLock {
    SENSOR_LANDSCAPE,
    SENSOR_PORTRAIT,
    SENSOR,
    LOCKED_LANDSCAPE,
    LOCKED_PORTRAIT,
    /** TV players lock to sensor-landscape. */
    TV_LANDSCAPE,
    /** Follow the user (cast-connected phones). */
    USER,
    /** Restore the OS default on player exit. */
    UNSPECIFIED,
}

/** The host-window operations for the current composition (see [PlayerWindowOps]). */
@Composable
internal expect fun rememberPlayerWindowOps(): PlayerWindowOps

/**
 * STREAM_MUSIC volume nudge for the hardware-keyboard volume shortcuts.
 * Android keeps the system-volume UI/ringer behaviour of the original
 * `adjustStreamVolume` path; desktop is a no-op (hardware volume keys are
 * handled by the OS).
 */
@Composable
internal expect fun rememberStreamVolumeAdjuster(): (up: Boolean) -> Unit

/**
 * Whether the device has a hardware keyboard (Chromebooks, BT keyboards,
 * DeX) — gates the non-TV keyboard-shortcut layer. Always true on desktop.
 */
@Composable
internal expect fun rememberHasHardwareKeyboard(): Boolean

/**
 * Whether the current configuration is portrait (drives the controls'
 * portrait/landscape layout switch and the companion dashboard's). Desktop
 * reports false — the desktop player is a resizable landscape-style window.
 */
@Composable
internal expect fun rememberIsPortraitOrientation(): Boolean

/**
 * Locale 24-hour clock preference for the controls' clock. Desktop derives it
 * from the default JDK time format pattern.
 */
@Composable
internal expect fun rememberIs24HourFormat(): Boolean
