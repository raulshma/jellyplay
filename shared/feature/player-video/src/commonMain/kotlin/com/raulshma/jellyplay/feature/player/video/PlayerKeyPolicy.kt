package com.raulshma.jellyplay.feature.player.video

/**
 * Pure key→action decision table for the player's hardware-keyboard media-key
 * layer, extracted verbatim from `VideoPlayerScreen`'s `handleMediaKeyDown`
 * so it is reachable by the JVM tests instead of living inline in composition
 * (the [PlayerScreenPolicies] file's precedent). Both delivery paths — the
 * focused-chain `.onKeyEvent` and the desktop shell's deterministic key sink —
 * run this one table through the screen's one-line effect shell; the
 * interaction bookkeeping (user-interaction count + `onUserInteraction`)
 * stays in the shell and keeps firing for EVERY KeyDown, matched or not,
 * exactly as before. No Compose types in these signatures: the key arrives
 * already mapped to the [PlayerKeyCodes] vocabulary by `playerKeyCode`.
 */

/**
 * What one media key MEANS, as data. The screen's effect shell maps each
 * value to its existing lambdas; [HideControls] and [Exit] are the two arms
 * of the ESC/BACK split, resolved by [mediaKeyAction] from the controls
 * visibility.
 */
internal enum class PlayerKeyAction {
    /** SPACE / MEDIA_PLAY / MEDIA_PAUSE / MEDIA_PLAY_PAUSE. */
    TogglePlayPause,

    /** DPAD_RIGHT / MEDIA_FAST_FORWARD / L. */
    SeekForward,

    /** DPAD_LEFT / MEDIA_REWIND / J. */
    SeekBack,

    /** DPAD_UP / VOLUME_UP. */
    VolumeUp,

    /** DPAD_DOWN / VOLUME_DOWN. */
    VolumeDown,

    /** F / F1–F4. */
    ToggleOrientation,

    /** M. */
    ToggleMute,

    /** ESC / BACK while the controls are visible. */
    HideControls,

    /** ESC / BACK while the controls are hidden (back out of the player). */
    Exit,
}

/**
 * Resolves [keyCode] (a [PlayerKeyCodes] constant) to the player action it
 * triggers, or null for keys the player does not handle (the shell's
 * unhandled-event false). [controlsVisible] only matters for ESC/BACK —
 * visible controls collapse to [PlayerKeyAction.HideControls], hidden
 * controls to [PlayerKeyAction.Exit]. The media-key aliases
 * (MEDIA_PLAY / MEDIA_PAUSE / MEDIA_PLAY_PAUSE, MEDIA_FAST_FORWARD /
 * MEDIA_REWIND, L / J) land on the same arms as their DPAD / letter
 * equivalents, exactly as the original when-block did. Pure: every effect
 * (play/pause toggle, seek, stream volume, orientation, mute, back) stays
 * with the screen's lambdas.
 */
internal fun mediaKeyAction(keyCode: Int, controlsVisible: Boolean): PlayerKeyAction? =
    when (keyCode) {
        PlayerKeyCodes.KEYCODE_SPACE,
        PlayerKeyCodes.KEYCODE_MEDIA_PLAY,
        PlayerKeyCodes.KEYCODE_MEDIA_PAUSE,
        PlayerKeyCodes.KEYCODE_MEDIA_PLAY_PAUSE,
        -> PlayerKeyAction.TogglePlayPause

        PlayerKeyCodes.KEYCODE_DPAD_RIGHT,
        PlayerKeyCodes.KEYCODE_MEDIA_FAST_FORWARD,
        PlayerKeyCodes.KEYCODE_L,
        -> PlayerKeyAction.SeekForward

        PlayerKeyCodes.KEYCODE_DPAD_LEFT,
        PlayerKeyCodes.KEYCODE_MEDIA_REWIND,
        PlayerKeyCodes.KEYCODE_J,
        -> PlayerKeyAction.SeekBack

        PlayerKeyCodes.KEYCODE_DPAD_UP,
        PlayerKeyCodes.KEYCODE_VOLUME_UP,
        -> PlayerKeyAction.VolumeUp

        PlayerKeyCodes.KEYCODE_DPAD_DOWN,
        PlayerKeyCodes.KEYCODE_VOLUME_DOWN,
        -> PlayerKeyAction.VolumeDown

        PlayerKeyCodes.KEYCODE_F,
        PlayerKeyCodes.KEYCODE_F1, PlayerKeyCodes.KEYCODE_F2,
        PlayerKeyCodes.KEYCODE_F3, PlayerKeyCodes.KEYCODE_F4,
        -> PlayerKeyAction.ToggleOrientation

        PlayerKeyCodes.KEYCODE_M -> PlayerKeyAction.ToggleMute

        PlayerKeyCodes.KEYCODE_ESCAPE,
        PlayerKeyCodes.KEYCODE_BACK,
        -> if (controlsVisible) PlayerKeyAction.HideControls else PlayerKeyAction.Exit

        else -> null
    }
