package com.raulshma.jellyplay.core.ui.tv.input

import android.view.KeyEvent as NativeKeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.type

/**
 * Centralized key classification library — single source of truth for "what kind of key is this?"
 * across the app.
 *
 * Design decisions baked in:
 * - Gamepad bumpers (L1/L2/R1/R2) alias for Left/Right so controller users can seek.
 * - `SystemNavigation*` keys are accepted for accessibility services and trackball devices.
 * - `KeyUp` vs `KeyDown` is distinguished explicitly via [isPlayKeyUp] and similar helpers.
 */

/** True for the 4 cardinal D-pad directions + 4 diagonals. */
fun KeyEvent.isDirectionalDpad(): Boolean {
    val code = effectiveKeyCode()
    return code in setOf(
        NativeKeyEvent.KEYCODE_DPAD_UP,
        NativeKeyEvent.KEYCODE_DPAD_DOWN,
        NativeKeyEvent.KEYCODE_DPAD_LEFT,
        NativeKeyEvent.KEYCODE_DPAD_RIGHT,
        NativeKeyEvent.KEYCODE_DPAD_UP_LEFT,
        NativeKeyEvent.KEYCODE_DPAD_UP_RIGHT,
        NativeKeyEvent.KEYCODE_DPAD_DOWN_LEFT,
        NativeKeyEvent.KEYCODE_DPAD_DOWN_RIGHT,
    )
}

/** True for D-pad center plus all 8 directional keys. */
fun KeyEvent.isDpad(): Boolean =
    effectiveKeyCode() == NativeKeyEvent.KEYCODE_DPAD_CENTER || isDirectionalDpad()

/** Enter-equivalent keys: DPadCenter, Enter, NumPadEnter, ButtonSelect, ButtonA. */
fun KeyEvent.isEnterKey(): Boolean {
    val code = effectiveKeyCode()
    return code in setOf(
        NativeKeyEvent.KEYCODE_DPAD_CENTER,
        NativeKeyEvent.KEYCODE_ENTER,
        NativeKeyEvent.KEYCODE_NUMPAD_ENTER,
        NativeKeyEvent.KEYCODE_BUTTON_SELECT,
        NativeKeyEvent.KEYCODE_BUTTON_A,
    )
}

/** Back-equivalent keys: Back, ButtonB. */
fun KeyEvent.isBackKey(): Boolean {
    val code = effectiveKeyCode()
    return code == NativeKeyEvent.KEYCODE_BACK || code == NativeKeyEvent.KEYCODE_BUTTON_B
}

/** Gamepad bumpers/triggers that we treat as aliases for seek/skip commands. */
fun KeyEvent.isControllerMedia(): Boolean {
    val code = effectiveKeyCode()
    return code in setOf(
        NativeKeyEvent.KEYCODE_BUTTON_R1,
        NativeKeyEvent.KEYCODE_BUTTON_R2,
        NativeKeyEvent.KEYCODE_BUTTON_L1,
        NativeKeyEvent.KEYCODE_BUTTON_L2,
    )
}

fun KeyEvent.isDpadLeft(): Boolean {
    val code = effectiveKeyCode()
    return code == NativeKeyEvent.KEYCODE_DPAD_LEFT ||
        code == NativeKeyEvent.KEYCODE_SYSTEM_NAVIGATION_LEFT ||
        code == NativeKeyEvent.KEYCODE_DPAD_DOWN_LEFT ||
        code == NativeKeyEvent.KEYCODE_DPAD_UP_LEFT
}

fun KeyEvent.isDpadRight(): Boolean {
    val code = effectiveKeyCode()
    return code == NativeKeyEvent.KEYCODE_DPAD_RIGHT ||
        code == NativeKeyEvent.KEYCODE_SYSTEM_NAVIGATION_RIGHT ||
        code == NativeKeyEvent.KEYCODE_DPAD_DOWN_RIGHT ||
        code == NativeKeyEvent.KEYCODE_DPAD_UP_RIGHT
}

fun KeyEvent.isDpadUp(): Boolean {
    val code = effectiveKeyCode()
    return code == NativeKeyEvent.KEYCODE_DPAD_UP ||
        code == NativeKeyEvent.KEYCODE_SYSTEM_NAVIGATION_UP
}

fun KeyEvent.isDpadDown(): Boolean {
    val code = effectiveKeyCode()
    return code == NativeKeyEvent.KEYCODE_DPAD_DOWN ||
        code == NativeKeyEvent.KEYCODE_SYSTEM_NAVIGATION_DOWN
}

/** Skip-backward predicates: directional left, gamepad L1/L2. */
fun KeyEvent.isSkipBack(): Boolean = isDpadLeft() ||
    effectiveKeyCode() in setOf(
        NativeKeyEvent.KEYCODE_BUTTON_L1,
        NativeKeyEvent.KEYCODE_BUTTON_L2,
    )

/** Skip-forward predicates: directional right, gamepad R1/R2. */
fun KeyEvent.isSkipForward(): Boolean = isDpadRight() ||
    effectiveKeyCode() in setOf(
        NativeKeyEvent.KEYCODE_BUTTON_R1,
        NativeKeyEvent.KEYCODE_BUTTON_R2,
    )

/** All Media* keys + Captions + AudioTrack + Stop. */
fun KeyEvent.isMedia(): Boolean {
    val code = effectiveKeyCode()
    return code in setOf(
        NativeKeyEvent.KEYCODE_MEDIA_PLAY,
        NativeKeyEvent.KEYCODE_MEDIA_PAUSE,
        NativeKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        NativeKeyEvent.KEYCODE_MEDIA_STOP,
        NativeKeyEvent.KEYCODE_MEDIA_NEXT,
        NativeKeyEvent.KEYCODE_MEDIA_PREVIOUS,
        NativeKeyEvent.KEYCODE_MEDIA_REWIND,
        NativeKeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
        NativeKeyEvent.KEYCODE_MEDIA_SKIP_FORWARD,
        NativeKeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD,
        NativeKeyEvent.KEYCODE_MEDIA_STEP_FORWARD,
        NativeKeyEvent.KEYCODE_MEDIA_STEP_BACKWARD,
        NativeKeyEvent.KEYCODE_CAPTIONS,
        NativeKeyEvent.KEYCODE_MEDIA_AUDIO_TRACK,
    )
}

/** Page-up / ChannelUp / MediaPrevious / Rewind / SkipBackward. */
fun KeyEvent.isBackwardButton(): Boolean {
    val code = effectiveKeyCode()
    return code == NativeKeyEvent.KEYCODE_PAGE_UP ||
        code == NativeKeyEvent.KEYCODE_CHANNEL_UP ||
        code == NativeKeyEvent.KEYCODE_MEDIA_PREVIOUS ||
        code == NativeKeyEvent.KEYCODE_MEDIA_REWIND ||
        code == NativeKeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD
}

/** Page-down / ChannelDown / MediaNext / FastForward / SkipForward. */
fun KeyEvent.isForwardButton(): Boolean {
    val code = effectiveKeyCode()
    return code == NativeKeyEvent.KEYCODE_PAGE_DOWN ||
        code == NativeKeyEvent.KEYCODE_CHANNEL_DOWN ||
        code == NativeKeyEvent.KEYCODE_MEDIA_NEXT ||
        code == NativeKeyEvent.KEYCODE_MEDIA_FAST_FORWARD ||
        code == NativeKeyEvent.KEYCODE_MEDIA_SKIP_FORWARD
}

/** True when the key was released AND it's a play/play-pause key. */
fun KeyEvent.isPlayKeyUp(): Boolean =
    type == KeyEventType.KeyUp &&
        effectiveKeyCode() in setOf(
            NativeKeyEvent.KEYCODE_MEDIA_PLAY,
            NativeKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        )

private fun KeyEvent.effectiveKeyCode(): Int {
    val native = key.nativeKeyCode
    if (native != 0) return native
    return when (key) {
        Key.DirectionLeft -> NativeKeyEvent.KEYCODE_DPAD_LEFT
        Key.DirectionRight -> NativeKeyEvent.KEYCODE_DPAD_RIGHT
        Key.DirectionUp -> NativeKeyEvent.KEYCODE_DPAD_UP
        Key.DirectionDown -> NativeKeyEvent.KEYCODE_DPAD_DOWN
        Key.DirectionCenter -> NativeKeyEvent.KEYCODE_DPAD_CENTER
        Key.DirectionUpLeft -> NativeKeyEvent.KEYCODE_DPAD_UP_LEFT
        Key.DirectionUpRight -> NativeKeyEvent.KEYCODE_DPAD_UP_RIGHT
        Key.DirectionDownLeft -> NativeKeyEvent.KEYCODE_DPAD_DOWN_LEFT
        Key.DirectionDownRight -> NativeKeyEvent.KEYCODE_DPAD_DOWN_RIGHT
        Key.Enter -> NativeKeyEvent.KEYCODE_ENTER
        Key.Back -> NativeKeyEvent.KEYCODE_BACK
        Key.Menu -> NativeKeyEvent.KEYCODE_MENU
        Key.MediaPlay -> NativeKeyEvent.KEYCODE_MEDIA_PLAY
        Key.MediaPause -> NativeKeyEvent.KEYCODE_MEDIA_PAUSE
        Key.MediaPlayPause -> NativeKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        Key.MediaStop -> NativeKeyEvent.KEYCODE_MEDIA_STOP
        Key.MediaNext -> NativeKeyEvent.KEYCODE_MEDIA_NEXT
        Key.MediaPrevious -> NativeKeyEvent.KEYCODE_MEDIA_PREVIOUS
        Key.MediaRewind -> NativeKeyEvent.KEYCODE_MEDIA_REWIND
        Key.MediaFastForward -> NativeKeyEvent.KEYCODE_MEDIA_FAST_FORWARD
        Key.MediaSkipForward -> NativeKeyEvent.KEYCODE_MEDIA_SKIP_FORWARD
        Key.MediaSkipBackward -> NativeKeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD
        Key.MediaAudioTrack -> NativeKeyEvent.KEYCODE_MEDIA_AUDIO_TRACK
        Key.Captions -> NativeKeyEvent.KEYCODE_CAPTIONS
        Key.PageUp -> NativeKeyEvent.KEYCODE_PAGE_UP
        Key.PageDown -> NativeKeyEvent.KEYCODE_PAGE_DOWN
        Key.ChannelUp -> NativeKeyEvent.KEYCODE_CHANNEL_UP
        Key.ChannelDown -> NativeKeyEvent.KEYCODE_CHANNEL_DOWN
        Key.ButtonA -> NativeKeyEvent.KEYCODE_BUTTON_A
        Key.ButtonB -> NativeKeyEvent.KEYCODE_BUTTON_B
        Key.ButtonSelect -> NativeKeyEvent.KEYCODE_BUTTON_SELECT
        Key.ButtonR1 -> NativeKeyEvent.KEYCODE_BUTTON_R1
        Key.ButtonR2 -> NativeKeyEvent.KEYCODE_BUTTON_R2
        Key.ButtonL1 -> NativeKeyEvent.KEYCODE_BUTTON_L1
        Key.ButtonL2 -> NativeKeyEvent.KEYCODE_BUTTON_L2
        else -> 0
    }
}
