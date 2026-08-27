package com.raulshma.jellyplay.feature.player.video

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import com.raulshma.jellyplay.core.model.RefreshRateMode
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus

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

/**
 * Whether the non-TV hardware-keyboard layer grabs focus onto its player Box
 * whenever it composes, not only on the controls-hidden edge (the screen's
 * at-HEAD focus-request condition, [VideoPlayerScreen]'s focus-request
 * effect). Static platform fact — no remember needed.
 *
 * Desktop (wave 14A) is TRUE. The desktop shell takes no Compose focus of its
 * own while the player is up: Route.VideoPlayer is isFullScreen so the nav
 * rail is removed from composition, the sign-in pane was replaced when the
 * session started, and PlayerControls' own focus grab is TV-only — but the
 * controls START visible (`showControls = true`), so the at-HEAD "request when
 * `!showControls`" condition left the player Box unfocused until the first
 * controls auto-hide (default `controlsTimeoutMs` is 5 s), or indefinitely
 * while a sheet/seek/overflow-menu suppressed the auto-hide. With no focused
 * node, Compose dispatches key events through the chain from the root down to
 * the TOPMOST key-input node only ([androidx.compose.ui.focus.FocusOwnerImpl]
 * `dispatchKeyEvent` null-focus fallback) — on desktop that topmost node is
 * DesktopNavScaffold's `onPreviewKeyEvent` Row, so ESC still popped the route
 * while SPACE/arrows never reached the player's `onKeyEvent` (the wave 13B
 * session-harness focus finding this seam's desktop grab fixes).
 *
 * Android is FALSE: the phone keyboard layer keeps the exact at-HEAD timing
 * (focus requested on the `showControls → false` edge only), so Android
 * behavior is byte-identical.
 */
internal expect fun grabsKeyboardFocusWithControlsVisible(): Boolean

/**
 * The desktop keyboard-focus grab: whenever [layerComposed] flips true (the
 * hardware-keyboard layer entered composition — screen entry, or a sheet
 * closing and the layer re-composing), grabs focus onto [focusRequester] with
 * the codebase's retry idiom ([RequestOrRestoreFocus]: the requester's node
 * may not be laid out on the first frame, so retry across a frame, up to 3
 * attempts). Additionally (wave 14D): whenever [targetHoldsFocus] flips false
 * while [layerComposed] is true — focus was taken AWAY from the grab target
 * after a successful grab — the grab is re-asserted with the same retry
 * idiom. The real desktop window needs this: the live session pass proved the
 * initial grab succeeds (attempt=1 ok=true, +68 ms) yet Compose focus is
 * dropped again ~1–2 s later when the wave-14B mpv SwingPanel/HWND surface
 * mounts (the AWT focus owner shuffles off the SkiaLayer and the player Box
 * reports hasFocus=false), and nothing ever re-grabbed — the auto-hide edge
 * re-request is a single un-retried attempt and the controls auto-hide had
 * already suppressed itself, so SPACE at the ~11 s injection found no Compose
 * focused node and died at the scaffold's null-focus fallback (wave 14A's
 * live-pass OVERLAY_SPACE failure; its desktop UI test passed because the
 * miniature topology has no surface mount to steal focus).
 *
 * The re-assert watches [targetHoldsFocus] = the target's **hasFocus** (not
 * isFocused), so a controls button INSIDE the player Box that took focus via
 * a pointer click keeps it — the re-assert only fires when nothing in the
 * player subtree holds focus at all. It is edge-triggered and gives up after
 * 3 failed frames, so a detached target cannot loop it.
 *
 * Composes nothing on platforms whose
 * [grabsKeyboardFocusWithControlsVisible] is false (Android: the at-HEAD
 * showControls→false-edge request in the screen is the whole story there).
 */
@Composable
internal fun PlayerKeyboardFocusGrabEffect(
    focusRequester: FocusRequester,
    layerComposed: Boolean,
    targetHoldsFocus: Boolean,
) {
    if (!grabsKeyboardFocusWithControlsVisible()) return
    LaunchedEffect(layerComposed) {
        if (layerComposed) {
            val startMs = System.currentTimeMillis()
            for (attempt in 1..3) {
                androidx.compose.runtime.withFrameNanos { }
                val ok = focusRequester.tryRequestFocus("keyboard_player")
                harnessFocusDiag(
                    "grab effect: attempt=$attempt ok=$ok t=+" +
                        (System.currentTimeMillis() - startMs) + "ms layerComposed=true",
                )
                if (ok) break
            }
        } else {
            harnessFocusDiag("grab effect: disarmed (layerComposed=false)")
        }
    }
    if (layerComposed) {
        LaunchedEffect(targetHoldsFocus) {
            if (targetHoldsFocus) return@LaunchedEffect
            val startMs = System.currentTimeMillis()
            for (attempt in 1..3) {
                androidx.compose.runtime.withFrameNanos { }
                val ok = focusRequester.tryRequestFocus("keyboard_player_regrab")
                harnessFocusDiag(
                    "re-assert: attempt=$attempt ok=$ok t=+" +
                        (System.currentTimeMillis() - startMs) + "ms targetHoldsFocus=false",
                )
                if (ok) break
            }
        }
    }
}

/**
 * Harness-gated focus diagnostic (wave 14D): emits one stdout line when the
 * desktop session harness is armed (`jellyplay.harness.enabled=true`, the same
 * zero-cost gate [DesktopSessionHarness] uses), so a live
 * `tools/e2e/desktop-session-pass.sh` run can correlate the Compose-side focus
 * story (grab attempts, player-Box focus transitions, keys entering the
 * screen's dispatch subtree) with the harness's AWT-side focus-owner
 * observations. Every other platform is a silent no-op, and no call site runs
 * outside the jvm-gated desktop branches, so normal boots (and all Android
 * behavior) are byte-identical.
 */
internal expect fun harnessFocusDiag(message: String)

/**
 * Wave 14E deterministic desktop key delivery: publish the composing player
 * screen's media-key handler to the desktop shell ([DesktopPlayerKeyBridge],
 * jvmMain) so `DesktopNavScaffold.onPreviewKeyEvent` can forward raw key
 * events into [VideoPlayerScreen]'s own handler even while the player Box
 * holds no Compose focus (the AWT/Compose focus flap leaves continuous
 * focus-less gaps in which Compose's null-focus fallback dispatch stops at
 * the shell's Row and the key dies). Passing `null` uninstalls (screen
 * disposal).
 *
 * The screen is the ONLY interpreter of media-key semantics: the sink lambda
 * it installs re-runs the screen's own key `when`-block, and declines when
 * the keyboard Box subtree holds focus (the normal focused dispatch chain
 * owns the key) or a sheet is open — so the shell's forward can never double
 * or misinterpret. The Android actual is a no-op and the install call site
 * sits behind [grabsKeyboardFocusWithControlsVisible] (false on Android), so
 * Android behavior is byte-identical.
 */
internal expect fun installPlayerKeySink(sink: ((androidx.compose.ui.input.key.KeyEvent) -> Boolean)?)

/**
 * [installPlayerKeySink]'s dispose twin, identity-guarded: the sink is
 * cleared only when it is still the [expected] instance — a stacked player
 * installed a newer sink that the popped screen must not disarm (latent; no
 * current route stacks players). Android actual is a no-op like install.
 */
internal expect fun uninstallPlayerKeySink(expected: ((androidx.compose.ui.input.key.KeyEvent) -> Boolean)?)
