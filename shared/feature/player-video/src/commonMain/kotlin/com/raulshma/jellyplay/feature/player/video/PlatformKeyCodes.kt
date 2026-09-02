package com.raulshma.jellyplay.feature.player.video

import androidx.compose.ui.input.key.KeyEvent

/**
 * Platform key-code seam for the player's hardware-keyboard layers (wave 9A).
 * The androidMain actual returns `nativeKeyEvent.keyCode` and aliases the
 * [PlayerKeyCodes] constants to `android.view.KeyEvent`'s — the screen's
 * `when` blocks are unchanged from their pre-split form. The jvmMain actual
 * maps the Compose [KeyEvent]'s key to the same constant vocabulary, so the
 * desktop keyboard-shortcut layer recognises the same media/arrow/letter
 * keys through Compose Desktop's key events.
 */
internal expect val KeyEvent.playerKeyCode: Int

/** Key-code constants shared by the screen's TV-space and keyboard handlers. */
internal expect object PlayerKeyCodes {
    val KEYCODE_SPACE: Int
    val KEYCODE_MEDIA_PLAY: Int
    val KEYCODE_MEDIA_PAUSE: Int
    val KEYCODE_MEDIA_PLAY_PAUSE: Int
    val KEYCODE_DPAD_RIGHT: Int
    val KEYCODE_MEDIA_FAST_FORWARD: Int
    val KEYCODE_L: Int
    val KEYCODE_DPAD_LEFT: Int
    val KEYCODE_MEDIA_REWIND: Int
    val KEYCODE_J: Int
    val KEYCODE_DPAD_UP: Int
    val KEYCODE_VOLUME_UP: Int
    val KEYCODE_DPAD_DOWN: Int
    val KEYCODE_VOLUME_DOWN: Int
    val KEYCODE_F: Int
    val KEYCODE_F1: Int
    val KEYCODE_F2: Int
    val KEYCODE_F3: Int
    val KEYCODE_F4: Int
    val KEYCODE_M: Int
    val KEYCODE_ESCAPE: Int
    val KEYCODE_BACK: Int
}
