package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.feature.player.video.chrome.KEY_SEEK_STEP_HOME_END_MS
import com.raulshma.jellyplay.feature.player.video.chrome.KEY_SEEK_STEP_PAGE_MS
import com.raulshma.jellyplay.feature.player.video.chrome.keyboardSeekStepMs

/**
 * Pure key→step decision table for the player's hardware-keyboard seek keys,
 * extracted verbatim from `VideoPlayerScreen`'s `handleMediaKeyDown` so it is
 * reachable by the JVM tests instead of living inline in composition
 * (the [PlayerScreenPolicies] file's precedent). Both delivery paths — the
 * focused-chain `.onKeyEvent` and the desktop shell's deterministic key sink —
 * run through the screen's one-line effect shell; the interaction bookkeeping
 * (user-interaction count + `onUserInteraction`) stays in the shell and keeps
 * firing for EVERY KeyDown, matched or not, exactly as before. No Compose
 * types in these signatures: the key arrives already mapped to the
 * [PlayerKeyCodes] vocabulary by `playerKeyCode`.
 *
 * The FORMER `PlayerKeyAction`/`mediaKeyAction` action table retired into the
 * input mapping: the default keyboard rows live in `PlayerInputDefaults`
 * (parity-pinned by `PlayerInputDefaultsParityTest`), and runtime resolution
 * runs through the persisted map (`PlayerInputPolicy`). What remains here is
 * the piece the mapping deliberately does NOT own — the per-key seek STEP
 * (the modifier ladder and the fixed page/home magnitudes), which is
 * event-time semantics folded into the bound seek action at the shell.
 */

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
