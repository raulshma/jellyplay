package com.raulshma.jellyplay.core.ui.tv.input

import android.view.KeyEvent as NativeKeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.type

fun KeyEvent.toDpadAction(): DpadAction? {
    val keyCode = if (key.nativeKeyCode != 0) {
        key.nativeKeyCode
    } else {
        mapComposeKey(key)
    }
    return nativeKeyCodeToAction(keyCode)
}

private fun mapComposeKey(key: Key): Int = when (key) {
    Key.DirectionLeft -> NativeKeyEvent.KEYCODE_DPAD_LEFT
    Key.DirectionRight -> NativeKeyEvent.KEYCODE_DPAD_RIGHT
    Key.DirectionUp -> NativeKeyEvent.KEYCODE_DPAD_UP
    Key.DirectionDown -> NativeKeyEvent.KEYCODE_DPAD_DOWN
    Key.DirectionCenter -> NativeKeyEvent.KEYCODE_DPAD_CENTER
    Key.Enter -> NativeKeyEvent.KEYCODE_ENTER
    Key.Back -> NativeKeyEvent.KEYCODE_BACK
    Key.MediaPlay, Key.MediaPause, Key.MediaPlayPause -> NativeKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
    Key.MediaFastForward -> NativeKeyEvent.KEYCODE_MEDIA_FAST_FORWARD
    Key.MediaRewind -> NativeKeyEvent.KEYCODE_MEDIA_REWIND
    Key.Menu -> NativeKeyEvent.KEYCODE_MENU
    else -> 0
}

private fun nativeKeyCodeToAction(keyCode: Int): DpadAction? = when (keyCode) {
    NativeKeyEvent.KEYCODE_DPAD_LEFT,
    NativeKeyEvent.KEYCODE_SYSTEM_NAVIGATION_LEFT -> DpadAction.Left
    NativeKeyEvent.KEYCODE_DPAD_RIGHT,
    NativeKeyEvent.KEYCODE_SYSTEM_NAVIGATION_RIGHT -> DpadAction.Right
    NativeKeyEvent.KEYCODE_DPAD_UP,
    NativeKeyEvent.KEYCODE_SYSTEM_NAVIGATION_UP -> DpadAction.Up
    NativeKeyEvent.KEYCODE_DPAD_DOWN,
    NativeKeyEvent.KEYCODE_SYSTEM_NAVIGATION_DOWN -> DpadAction.Down
    NativeKeyEvent.KEYCODE_DPAD_CENTER,
    NativeKeyEvent.KEYCODE_ENTER,
    NativeKeyEvent.KEYCODE_NUMPAD_ENTER -> DpadAction.Select
    NativeKeyEvent.KEYCODE_BACK -> DpadAction.Back
    NativeKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
    NativeKeyEvent.KEYCODE_MEDIA_PLAY,
    NativeKeyEvent.KEYCODE_MEDIA_PAUSE -> DpadAction.PlayPause
    NativeKeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> DpadAction.FastForward
    NativeKeyEvent.KEYCODE_MEDIA_REWIND -> DpadAction.Rewind
    NativeKeyEvent.KEYCODE_MENU -> DpadAction.Menu
    else -> null
}

actual fun KeyEvent.toDpadKeyEvent(): DpadKeyEvent? {
    val action = toDpadAction() ?: return null
    return DpadKeyEvent(
        action = action,
        type = type,
        repeatCount = nativeKeyEvent.repeatCount,
    )
}
