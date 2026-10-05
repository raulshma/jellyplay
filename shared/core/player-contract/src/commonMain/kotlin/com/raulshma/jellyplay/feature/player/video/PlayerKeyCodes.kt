package com.raulshma.jellyplay.feature.player.video

import androidx.compose.ui.input.key.KeyEvent
import com.raulshma.jellyplay.core.model.PlayerInputKey

/**
 * Platform key-code seam for the player's hardware-keyboard layers, shared
 * by the player screen AND the settings binding editor (the key-capture
 * dialog turns a live key press into a persisted [PlayerInputKey] through
 * [playerKeyCode] + [playerInputKeyOf]).
 *
 * The androidMain actual returns the Compose event's native
 * `android.view.KeyEvent` keycode and aliases the [PlayerKeyCodes]
 * constants to the same vocabulary; the jvmMain actual maps the event's
 * key onto the same constants with arbitrary synthetic values, so the
 * desktop keyboard layer recognises the same media/arrow/letter keys.
 * Moved verbatim (same package) from the player-video module when the
 * binding editor needed the same bridge — visibility promoted from
 * internal to public.
 */
public expect val KeyEvent.playerKeyCode: Int
public expect object PlayerKeyCodes {
    val KEYCODE_SPACE: Int
    val KEYCODE_MEDIA_PLAY: Int
    val KEYCODE_MEDIA_PAUSE: Int
    val KEYCODE_MEDIA_PLAY_PAUSE: Int
    val KEYCODE_K: Int
    val KEYCODE_DPAD_RIGHT: Int
    val KEYCODE_MEDIA_FAST_FORWARD: Int
    val KEYCODE_L: Int
    val KEYCODE_DPAD_LEFT: Int
    val KEYCODE_MEDIA_REWIND: Int
    val KEYCODE_J: Int
    val KEYCODE_G: Int
    val KEYCODE_H: Int
    val KEYCODE_LEFT_BRACKET: Int
    val KEYCODE_RIGHT_BRACKET: Int
    val KEYCODE_PAGE_UP: Int
    val KEYCODE_PAGE_DOWN: Int
    val KEYCODE_MOVE_HOME: Int
    val KEYCODE_MOVE_END: Int
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
    val KEYCODE_V: Int
    val KEYCODE_ESCAPE: Int
    val KEYCODE_BACK: Int
}

/**
 * The input-mapping catalog key → platform code bridge: the exact inverse of
 * what the platforms' event extensions feed the handlers, so the mapping
 * resolver can turn a raw key-event code back into the persisted
 * [PlayerInputKey] vocabulary (bindings store NAMES, never platform codes).
 * Exhaustive over the catalog — a new [PlayerInputKey] row without a branch
 * here fails to compile.
 */
public fun playerKeyCodeOf(key: PlayerInputKey): Int = when (key) {
    PlayerInputKey.SPACE -> PlayerKeyCodes.KEYCODE_SPACE
    PlayerInputKey.K -> PlayerKeyCodes.KEYCODE_K
    PlayerInputKey.M -> PlayerKeyCodes.KEYCODE_M
    PlayerInputKey.V -> PlayerKeyCodes.KEYCODE_V
    PlayerInputKey.F -> PlayerKeyCodes.KEYCODE_F
    PlayerInputKey.G -> PlayerKeyCodes.KEYCODE_G
    PlayerInputKey.H -> PlayerKeyCodes.KEYCODE_H
    PlayerInputKey.J -> PlayerKeyCodes.KEYCODE_J
    PlayerInputKey.L -> PlayerKeyCodes.KEYCODE_L
    PlayerInputKey.BRACKET_LEFT -> PlayerKeyCodes.KEYCODE_LEFT_BRACKET
    PlayerInputKey.BRACKET_RIGHT -> PlayerKeyCodes.KEYCODE_RIGHT_BRACKET
    PlayerInputKey.F1 -> PlayerKeyCodes.KEYCODE_F1
    PlayerInputKey.F2 -> PlayerKeyCodes.KEYCODE_F2
    PlayerInputKey.F3 -> PlayerKeyCodes.KEYCODE_F3
    PlayerInputKey.F4 -> PlayerKeyCodes.KEYCODE_F4
    PlayerInputKey.ESCAPE -> PlayerKeyCodes.KEYCODE_ESCAPE
    PlayerInputKey.BACK -> PlayerKeyCodes.KEYCODE_BACK
    PlayerInputKey.DPAD_LEFT -> PlayerKeyCodes.KEYCODE_DPAD_LEFT
    PlayerInputKey.DPAD_RIGHT -> PlayerKeyCodes.KEYCODE_DPAD_RIGHT
    PlayerInputKey.DPAD_UP -> PlayerKeyCodes.KEYCODE_DPAD_UP
    PlayerInputKey.DPAD_DOWN -> PlayerKeyCodes.KEYCODE_DPAD_DOWN
    PlayerInputKey.VOLUME_UP -> PlayerKeyCodes.KEYCODE_VOLUME_UP
    PlayerInputKey.VOLUME_DOWN -> PlayerKeyCodes.KEYCODE_VOLUME_DOWN
    PlayerInputKey.PAGE_UP -> PlayerKeyCodes.KEYCODE_PAGE_UP
    PlayerInputKey.PAGE_DOWN -> PlayerKeyCodes.KEYCODE_PAGE_DOWN
    PlayerInputKey.MOVE_HOME -> PlayerKeyCodes.KEYCODE_MOVE_HOME
    PlayerInputKey.MOVE_END -> PlayerKeyCodes.KEYCODE_MOVE_END
    PlayerInputKey.MEDIA_PLAY -> PlayerKeyCodes.KEYCODE_MEDIA_PLAY
    PlayerInputKey.MEDIA_PAUSE -> PlayerKeyCodes.KEYCODE_MEDIA_PAUSE
    PlayerInputKey.MEDIA_PLAY_PAUSE -> PlayerKeyCodes.KEYCODE_MEDIA_PLAY_PAUSE
    PlayerInputKey.MEDIA_FAST_FORWARD -> PlayerKeyCodes.KEYCODE_MEDIA_FAST_FORWARD
    PlayerInputKey.MEDIA_REWIND -> PlayerKeyCodes.KEYCODE_MEDIA_REWIND
}

/**
 * The platform code → catalog key inverse of [playerKeyCodeOf]: what the
 * key-capture dialog feeds a captured code into. Returns `null` for codes
 * outside the catalog (0 = KEYCODE_UNKNOWN, or any key the platform layer
 * does not recognise) — the dialog keeps listening on `null`. Exhaustive
 * over the catalog, mirroring [playerKeyCodeOf] branch for branch.
 */
public fun playerInputKeyOf(code: Int): PlayerInputKey? = when (code) {
    PlayerKeyCodes.KEYCODE_SPACE -> PlayerInputKey.SPACE
    PlayerKeyCodes.KEYCODE_K -> PlayerInputKey.K
    PlayerKeyCodes.KEYCODE_M -> PlayerInputKey.M
    PlayerKeyCodes.KEYCODE_V -> PlayerInputKey.V
    PlayerKeyCodes.KEYCODE_F -> PlayerInputKey.F
    PlayerKeyCodes.KEYCODE_G -> PlayerInputKey.G
    PlayerKeyCodes.KEYCODE_H -> PlayerInputKey.H
    PlayerKeyCodes.KEYCODE_J -> PlayerInputKey.J
    PlayerKeyCodes.KEYCODE_L -> PlayerInputKey.L
    PlayerKeyCodes.KEYCODE_LEFT_BRACKET -> PlayerInputKey.BRACKET_LEFT
    PlayerKeyCodes.KEYCODE_RIGHT_BRACKET -> PlayerInputKey.BRACKET_RIGHT
    PlayerKeyCodes.KEYCODE_F1 -> PlayerInputKey.F1
    PlayerKeyCodes.KEYCODE_F2 -> PlayerInputKey.F2
    PlayerKeyCodes.KEYCODE_F3 -> PlayerInputKey.F3
    PlayerKeyCodes.KEYCODE_F4 -> PlayerInputKey.F4
    PlayerKeyCodes.KEYCODE_ESCAPE -> PlayerInputKey.ESCAPE
    PlayerKeyCodes.KEYCODE_BACK -> PlayerInputKey.BACK
    PlayerKeyCodes.KEYCODE_DPAD_LEFT -> PlayerInputKey.DPAD_LEFT
    PlayerKeyCodes.KEYCODE_DPAD_RIGHT -> PlayerInputKey.DPAD_RIGHT
    PlayerKeyCodes.KEYCODE_DPAD_UP -> PlayerInputKey.DPAD_UP
    PlayerKeyCodes.KEYCODE_DPAD_DOWN -> PlayerInputKey.DPAD_DOWN
    PlayerKeyCodes.KEYCODE_VOLUME_UP -> PlayerInputKey.VOLUME_UP
    PlayerKeyCodes.KEYCODE_VOLUME_DOWN -> PlayerInputKey.VOLUME_DOWN
    PlayerKeyCodes.KEYCODE_PAGE_UP -> PlayerInputKey.PAGE_UP
    PlayerKeyCodes.KEYCODE_PAGE_DOWN -> PlayerInputKey.PAGE_DOWN
    PlayerKeyCodes.KEYCODE_MOVE_HOME -> PlayerInputKey.MOVE_HOME
    PlayerKeyCodes.KEYCODE_MOVE_END -> PlayerInputKey.MOVE_END
    PlayerKeyCodes.KEYCODE_MEDIA_PLAY -> PlayerInputKey.MEDIA_PLAY
    PlayerKeyCodes.KEYCODE_MEDIA_PAUSE -> PlayerInputKey.MEDIA_PAUSE
    PlayerKeyCodes.KEYCODE_MEDIA_PLAY_PAUSE -> PlayerInputKey.MEDIA_PLAY_PAUSE
    PlayerKeyCodes.KEYCODE_MEDIA_FAST_FORWARD -> PlayerInputKey.MEDIA_FAST_FORWARD
    PlayerKeyCodes.KEYCODE_MEDIA_REWIND -> PlayerInputKey.MEDIA_REWIND
    else -> null
}
