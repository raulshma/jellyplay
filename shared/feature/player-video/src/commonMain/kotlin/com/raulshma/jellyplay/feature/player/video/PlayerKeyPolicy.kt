package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.feature.player.video.chrome.KEY_SEEK_STEP_HOME_END_MS
import com.raulshma.jellyplay.feature.player.video.chrome.KEY_SEEK_STEP_PAGE_MS
import com.raulshma.jellyplay.feature.player.video.chrome.keyboardSeekStepMs

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
 * visibility. The seek keys are NOT here — [mediaKeySeek] owns them, because
 * their effect carries a per-press step the action enum cannot express.
 */
internal enum class PlayerKeyAction {
    /** SPACE / MEDIA_PLAY / MEDIA_PAUSE / MEDIA_PLAY_PAUSE / K. */
    TogglePlayPause,

    /** DPAD_UP / VOLUME_UP. */
    VolumeUp,

    /** DPAD_DOWN / VOLUME_DOWN. */
    VolumeDown,

    /** F / F1–F4. */
    ToggleOrientation,

    /** M. */
    ToggleMute,

    /** V — subtitle visibility toggle with restore memory. */
    ToggleSubtitles,

    /** `[` — subtitle delay −[KEY_SUBTITLE_DELAY_STEP_MS]. */
    SubtitleDelayDecrease,

    /** `]` — subtitle delay +[KEY_SUBTITLE_DELAY_STEP_MS]. */
    SubtitleDelayIncrease,

    /** Ctrl+`[` — audio delay −[KEY_AUDIO_DELAY_STEP_MS]. */
    AudioDelayDecrease,

    /** Ctrl+`]` — audio delay +[KEY_AUDIO_DELAY_STEP_MS]. */
    AudioDelayIncrease,

    /** ESC / BACK while the controls are visible. */
    HideControls,

    /** ESC / BACK while the controls are hidden (back out of the player). */
    Exit,
}

/**
 * One resolved keyboard seek: [direction] −1 = back / +1 = forward, [stepMs]
 * the per-press magnitude (see [mediaKeySeek]).
 */
internal data class PlayerKeySeek(val direction: Int, val stepMs: Long)

/**
 * The seek-key half of the decision table (the rows [mediaKeyAction] used to
 * carry, widened with the modifier steps and the Home/End/PgUp/PgDn rows).
 * Resolves [keyCode] to the seek ONE key-down represents, or null for keys
 * that are not seek keys. Arrows / J / L / media-FF-RW take the VLC modifier
 * steps (plain = the user's configured [configuredStepMs] jump, Shift = ±5s,
 * Ctrl = ±60s, Shift+Ctrl = ±30s, Alt+Ctrl = ±300s — the fold lives in the
 * shared player-contract's [keyboardSeekStepMs]); Home/End/PgUp/PgDn are
 * direction-fixed jellyfin-media-player rows (±10s small, ±300s big). The
 * screen's effect shell accumulates the returned seek into the shared D-pad
 * seek chip and commits it debounced — pure here: every effect stays with the
 * screen's lambdas.
 */
internal fun mediaKeySeek(
    keyCode: Int,
    configuredStepMs: Long,
    isShiftPressed: Boolean = false,
    isCtrlPressed: Boolean = false,
    isAltPressed: Boolean = false,
): PlayerKeySeek? {
    fun modifierStepped(direction: Int) = PlayerKeySeek(
        direction = direction,
        stepMs = keyboardSeekStepMs(
            isShiftPressed = isShiftPressed,
            isCtrlPressed = isCtrlPressed,
            isAltPressed = isAltPressed,
            configuredStepMs = configuredStepMs,
        ),
    )

    return when (keyCode) {
        PlayerKeyCodes.KEYCODE_DPAD_RIGHT,
        PlayerKeyCodes.KEYCODE_MEDIA_FAST_FORWARD,
        PlayerKeyCodes.KEYCODE_L,
        -> modifierStepped(direction = 1)

        PlayerKeyCodes.KEYCODE_DPAD_LEFT,
        PlayerKeyCodes.KEYCODE_MEDIA_REWIND,
        PlayerKeyCodes.KEYCODE_J,
        -> modifierStepped(direction = -1)

        PlayerKeyCodes.KEYCODE_PAGE_UP -> PlayerKeySeek(direction = 1, stepMs = KEY_SEEK_STEP_PAGE_MS)
        PlayerKeyCodes.KEYCODE_PAGE_DOWN -> PlayerKeySeek(direction = -1, stepMs = KEY_SEEK_STEP_PAGE_MS)
        PlayerKeyCodes.KEYCODE_MOVE_HOME -> PlayerKeySeek(direction = -1, stepMs = KEY_SEEK_STEP_HOME_END_MS)
        PlayerKeyCodes.KEYCODE_MOVE_END -> PlayerKeySeek(direction = 1, stepMs = KEY_SEEK_STEP_HOME_END_MS)

        else -> null
    }
}

/**
 * Resolves [keyCode] (a [PlayerKeyCodes] constant) to the player action it
 * triggers, or null for keys the player does not handle (the shell's
 * unhandled-event false). [controlsVisible] only matters for ESC/BACK —
 * visible controls collapse to [PlayerKeyAction.HideControls], hidden
 * controls to [PlayerKeyAction.Exit]. [isCtrlPressed] only matters for the
 * bracket rows — plain `[`/`]` are the subtitle-delay shortcuts, the Ctrl
 * variants the audio-delay ones. The media-key aliases
 * (MEDIA_PLAY / MEDIA_PAUSE / MEDIA_PLAY_PAUSE) land on the same arms as
 * their DPAD / letter equivalents. Pure: every effect (play/pause toggle,
 * stream volume, orientation, mute, delay steps, back) stays with the
 * screen's lambdas.
 */
internal fun mediaKeyAction(
    keyCode: Int,
    controlsVisible: Boolean,
    isCtrlPressed: Boolean = false,
): PlayerKeyAction? =
    when (keyCode) {
        PlayerKeyCodes.KEYCODE_SPACE,
        PlayerKeyCodes.KEYCODE_MEDIA_PLAY,
        PlayerKeyCodes.KEYCODE_MEDIA_PAUSE,
        PlayerKeyCodes.KEYCODE_MEDIA_PLAY_PAUSE,
        PlayerKeyCodes.KEYCODE_K,
        -> PlayerKeyAction.TogglePlayPause

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

        PlayerKeyCodes.KEYCODE_V -> PlayerKeyAction.ToggleSubtitles

        PlayerKeyCodes.KEYCODE_LEFT_BRACKET ->
            if (isCtrlPressed) PlayerKeyAction.AudioDelayDecrease
            else PlayerKeyAction.SubtitleDelayDecrease

        PlayerKeyCodes.KEYCODE_RIGHT_BRACKET ->
            if (isCtrlPressed) PlayerKeyAction.AudioDelayIncrease
            else PlayerKeyAction.SubtitleDelayIncrease

        PlayerKeyCodes.KEYCODE_G -> PlayerKeyAction.SubtitleDelayDecrease
        PlayerKeyCodes.KEYCODE_H -> PlayerKeyAction.SubtitleDelayIncrease

        PlayerKeyCodes.KEYCODE_ESCAPE,
        PlayerKeyCodes.KEYCODE_BACK,
        -> if (controlsVisible) PlayerKeyAction.HideControls else PlayerKeyAction.Exit

        else -> null
    }

// ── Delay-shortcut steps ─────────────────────────────────────────────
// The keyboard shortcuts reuse the per-press steps the on-screen delay
// surfaces already use — SubtitleDelayOverlay's STEP_MS (50ms, with its own
// press-and-hold repeat) and AVSyncSheet's DELAY_STEP_MS (100ms) — and their
// shared ±30s clamp window, so a key press and a tap on the overlay/sheet
// stepper are interchangeable.

/** Per-press subtitle-delay step for the `[`/`]`/G/H shortcuts (= SubtitleDelayOverlay's STEP_MS). */
internal const val KEY_SUBTITLE_DELAY_STEP_MS = 50L

/** Per-press audio-delay step for the Ctrl+`[`/`]` shortcuts (= AVSyncSheet's DELAY_STEP_MS). */
internal const val KEY_AUDIO_DELAY_STEP_MS = 100L

/** Shared delay clamp floor (= SubtitleDelayOverlay's MIN_MS / AVSyncSheet's MIN_DELAY_MS). */
internal const val KEY_DELAY_MIN_MS = -30_000L

/** Shared delay clamp ceiling (= SubtitleDelayOverlay's MAX_MS / AVSyncSheet's MAX_DELAY_MS). */
internal const val KEY_DELAY_MAX_MS = 30_000L

/**
 * One delay-shortcut press: [currentMs] stepped by [sign] × [stepMs] and
 * clamped to the overlays' ±30s window (the same bounds the overlay steppers
 * and the AVSync slider enforce).
 */
internal fun delayAdjustMs(currentMs: Long, sign: Int, stepMs: Long): Long =
    (currentMs + sign * stepMs).coerceIn(KEY_DELAY_MIN_MS, KEY_DELAY_MAX_MS)
