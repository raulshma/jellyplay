package com.raulshma.jellyplay.feature.player.video

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.key

/**
 * Desktop (jvmMain) actuals for the platform key-code seam: the Compose
 * event's [Key] mapped onto the same constant vocabulary Android's
 * `android.view.KeyEvent` codes occupy — arbitrary synthetic values, unique
 * per key, meaningful only inside this seam.
 */
public actual val KeyEvent.playerKeyCode: Int
    get() = platformKeyCodeOfKey(key)

public actual object PlayerKeyCodes {
    public actual val KEYCODE_SPACE: Int = 1
    public actual val KEYCODE_MEDIA_PLAY: Int = 2
    public actual val KEYCODE_MEDIA_PAUSE: Int = 3
    public actual val KEYCODE_MEDIA_PLAY_PAUSE: Int = 4
    public actual val KEYCODE_K: Int = 23
    public actual val KEYCODE_DPAD_RIGHT: Int = 5
    public actual val KEYCODE_MEDIA_FAST_FORWARD: Int = 6
    public actual val KEYCODE_L: Int = 7
    public actual val KEYCODE_DPAD_LEFT: Int = 8
    public actual val KEYCODE_MEDIA_REWIND: Int = 9
    public actual val KEYCODE_J: Int = 10
    public actual val KEYCODE_G: Int = 24
    public actual val KEYCODE_H: Int = 25
    public actual val KEYCODE_LEFT_BRACKET: Int = 26
    public actual val KEYCODE_RIGHT_BRACKET: Int = 27
    public actual val KEYCODE_PAGE_UP: Int = 28
    public actual val KEYCODE_PAGE_DOWN: Int = 29
    public actual val KEYCODE_MOVE_HOME: Int = 30
    public actual val KEYCODE_MOVE_END: Int = 31
    public actual val KEYCODE_DPAD_UP: Int = 11
    public actual val KEYCODE_VOLUME_UP: Int = 12
    public actual val KEYCODE_DPAD_DOWN: Int = 13
    public actual val KEYCODE_VOLUME_DOWN: Int = 14
    public actual val KEYCODE_F: Int = 15
    public actual val KEYCODE_F1: Int = 16
    public actual val KEYCODE_F2: Int = 17
    public actual val KEYCODE_F3: Int = 18
    public actual val KEYCODE_F4: Int = 19
    public actual val KEYCODE_M: Int = 20
    public actual val KEYCODE_V: Int = 32
    public actual val KEYCODE_ESCAPE: Int = 21
    public actual val KEYCODE_BACK: Int = 22
}

/** The Compose event key → this seam's synthetic code. 0 = unmatched. */
private fun platformKeyCodeOfKey(key: Key): Int = when (key) {
    Key.Spacebar -> PlayerKeyCodes.KEYCODE_SPACE
    Key.MediaPlay -> PlayerKeyCodes.KEYCODE_MEDIA_PLAY
    Key.MediaPause -> PlayerKeyCodes.KEYCODE_MEDIA_PAUSE
    Key.MediaPlayPause -> PlayerKeyCodes.KEYCODE_MEDIA_PLAY_PAUSE
    Key.K -> PlayerKeyCodes.KEYCODE_K
    Key.DirectionRight -> PlayerKeyCodes.KEYCODE_DPAD_RIGHT
    Key.MediaFastForward -> PlayerKeyCodes.KEYCODE_MEDIA_FAST_FORWARD
    Key.L -> PlayerKeyCodes.KEYCODE_L
    Key.DirectionLeft -> PlayerKeyCodes.KEYCODE_DPAD_LEFT
    Key.MediaRewind -> PlayerKeyCodes.KEYCODE_MEDIA_REWIND
    Key.J -> PlayerKeyCodes.KEYCODE_J
    Key.G -> PlayerKeyCodes.KEYCODE_G
    Key.H -> PlayerKeyCodes.KEYCODE_H
    Key.LeftBracket -> PlayerKeyCodes.KEYCODE_LEFT_BRACKET
    Key.RightBracket -> PlayerKeyCodes.KEYCODE_RIGHT_BRACKET
    Key.PageUp -> PlayerKeyCodes.KEYCODE_PAGE_UP
    Key.PageDown -> PlayerKeyCodes.KEYCODE_PAGE_DOWN
    Key.MoveHome -> PlayerKeyCodes.KEYCODE_MOVE_HOME
    Key.MoveEnd -> PlayerKeyCodes.KEYCODE_MOVE_END
    Key.DirectionUp -> PlayerKeyCodes.KEYCODE_DPAD_UP
    Key.VolumeUp -> PlayerKeyCodes.KEYCODE_VOLUME_UP
    Key.DirectionDown -> PlayerKeyCodes.KEYCODE_DPAD_DOWN
    Key.VolumeDown -> PlayerKeyCodes.KEYCODE_VOLUME_DOWN
    Key.F -> PlayerKeyCodes.KEYCODE_F
    Key.F1 -> PlayerKeyCodes.KEYCODE_F1
    Key.F2 -> PlayerKeyCodes.KEYCODE_F2
    Key.F3 -> PlayerKeyCodes.KEYCODE_F3
    Key.F4 -> PlayerKeyCodes.KEYCODE_F4
    Key.M -> PlayerKeyCodes.KEYCODE_M
    Key.V -> PlayerKeyCodes.KEYCODE_V
    Key.Escape -> PlayerKeyCodes.KEYCODE_ESCAPE
    else -> 0 // KEYCODE_UNKNOWN: unmatched keys fall through every when-branch.
}
