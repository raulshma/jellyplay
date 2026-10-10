package com.raulshma.jellyplay.feature.player.video

import androidx.compose.ui.input.key.KeyEvent

/**
 * Android actuals for the platform key-code seam (moved verbatim from the
 * player-video module's VideoPlayerScreenSeams.android.kt when the settings
 * binding editor needed the same bridge): the Compose event's native
 * keycode, and the catalog constants aliased to `android.view.KeyEvent`'s.
 */
public actual val KeyEvent.playerKeyCode: Int
    get() = nativeKeyEvent.keyCode

public actual object PlayerKeyCodes {
    public actual val KEYCODE_SPACE: Int = android.view.KeyEvent.KEYCODE_SPACE
    public actual val KEYCODE_MEDIA_PLAY: Int = android.view.KeyEvent.KEYCODE_MEDIA_PLAY
    public actual val KEYCODE_MEDIA_PAUSE: Int = android.view.KeyEvent.KEYCODE_MEDIA_PAUSE
    public actual val KEYCODE_MEDIA_PLAY_PAUSE: Int = android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
    public actual val KEYCODE_K: Int = android.view.KeyEvent.KEYCODE_K
    public actual val KEYCODE_DPAD_RIGHT: Int = android.view.KeyEvent.KEYCODE_DPAD_RIGHT
    public actual val KEYCODE_MEDIA_FAST_FORWARD: Int = android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
    public actual val KEYCODE_L: Int = android.view.KeyEvent.KEYCODE_L
    public actual val KEYCODE_DPAD_LEFT: Int = android.view.KeyEvent.KEYCODE_DPAD_LEFT
    public actual val KEYCODE_MEDIA_REWIND: Int = android.view.KeyEvent.KEYCODE_MEDIA_REWIND
    public actual val KEYCODE_J: Int = android.view.KeyEvent.KEYCODE_J
    public actual val KEYCODE_G: Int = android.view.KeyEvent.KEYCODE_G
    public actual val KEYCODE_H: Int = android.view.KeyEvent.KEYCODE_H
    public actual val KEYCODE_LEFT_BRACKET: Int = android.view.KeyEvent.KEYCODE_LEFT_BRACKET
    public actual val KEYCODE_RIGHT_BRACKET: Int = android.view.KeyEvent.KEYCODE_RIGHT_BRACKET
    public actual val KEYCODE_PAGE_UP: Int = android.view.KeyEvent.KEYCODE_PAGE_UP
    public actual val KEYCODE_PAGE_DOWN: Int = android.view.KeyEvent.KEYCODE_PAGE_DOWN
    public actual val KEYCODE_MOVE_HOME: Int = android.view.KeyEvent.KEYCODE_MOVE_HOME
    public actual val KEYCODE_MOVE_END: Int = android.view.KeyEvent.KEYCODE_MOVE_END
    public actual val KEYCODE_DPAD_UP: Int = android.view.KeyEvent.KEYCODE_DPAD_UP
    public actual val KEYCODE_VOLUME_UP: Int = android.view.KeyEvent.KEYCODE_VOLUME_UP
    public actual val KEYCODE_DPAD_DOWN: Int = android.view.KeyEvent.KEYCODE_DPAD_DOWN
    public actual val KEYCODE_VOLUME_DOWN: Int = android.view.KeyEvent.KEYCODE_VOLUME_DOWN
    public actual val KEYCODE_F: Int = android.view.KeyEvent.KEYCODE_F
    public actual val KEYCODE_F1: Int = android.view.KeyEvent.KEYCODE_F1
    public actual val KEYCODE_F2: Int = android.view.KeyEvent.KEYCODE_F2
    public actual val KEYCODE_F3: Int = android.view.KeyEvent.KEYCODE_F3
    public actual val KEYCODE_F4: Int = android.view.KeyEvent.KEYCODE_F4
    public actual val KEYCODE_M: Int = android.view.KeyEvent.KEYCODE_M
    public actual val KEYCODE_V: Int = android.view.KeyEvent.KEYCODE_V
    public actual val KEYCODE_ESCAPE: Int = android.view.KeyEvent.KEYCODE_ESCAPE
    public actual val KEYCODE_BACK: Int = android.view.KeyEvent.KEYCODE_BACK
}
