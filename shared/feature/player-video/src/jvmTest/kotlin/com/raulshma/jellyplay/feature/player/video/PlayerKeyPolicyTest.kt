package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.feature.player.video.chrome.KEY_SEEK_STEP_ALT_CTRL_MS
import com.raulshma.jellyplay.feature.player.video.chrome.KEY_SEEK_STEP_CTRL_MS
import com.raulshma.jellyplay.feature.player.video.chrome.KEY_SEEK_STEP_FINE_MS
import com.raulshma.jellyplay.feature.player.video.chrome.KEY_SEEK_STEP_HOME_END_MS
import com.raulshma.jellyplay.feature.player.video.chrome.KEY_SEEK_STEP_PAGE_MS
import com.raulshma.jellyplay.feature.player.video.chrome.KEY_SEEK_STEP_SHIFT_CTRL_MS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.fail

/**
 * Pins the player's media-key decision tables ([PlayerKeyPolicy.kt]) — one row
 * per key code the hardware-keyboard layer handles, the ESC/BACK
 * controls-visible fold, and the unknown-key null — so the screen's effect
 * shell can never silently drift from the key→action mapping. The seek keys
 * resolve through [mediaKeySeek] (their effect carries a per-press step, so
 * they left the action enum); everything else through [mediaKeyAction]. The
 * desktop `playerKeyCode` actual maps Compose keys onto these same constants,
 * so a row here covers both platforms' delivery paths.
 */
class PlayerKeyPolicyTest {

    @Test
    fun space_andMediaPlayAliases_togglePlayPause() {
        // The media-key aliases (PLAY / PAUSE / PLAY_PAUSE) land on the same
        // arm as SPACE, regardless of controls visibility.
        for (code in listOf(
            PlayerKeyCodes.KEYCODE_SPACE,
            PlayerKeyCodes.KEYCODE_MEDIA_PLAY,
            PlayerKeyCodes.KEYCODE_MEDIA_PAUSE,
            PlayerKeyCodes.KEYCODE_MEDIA_PLAY_PAUSE,
        )) {
            for (controlsVisible in listOf(true, false)) {
                assertEquals(
                    PlayerKeyAction.TogglePlayPause,
                    mediaKeyAction(keyCode = code, controlsVisible = controlsVisible),
                    "keyCode=$code controlsVisible=$controlsVisible",
                )
            }
        }
    }

    @Test
    fun k_completesTheJklTrio_togglesPlayPause() {
        // K is the play/pause third of the J·K·L editing trio (VLC / mpv /
        // jellyfin-web all bind it), regardless of controls visibility.
        for (controlsVisible in listOf(true, false)) {
            assertEquals(
                PlayerKeyAction.TogglePlayPause,
                mediaKeyAction(keyCode = PlayerKeyCodes.KEYCODE_K, controlsVisible = controlsVisible),
                "controlsVisible=$controlsVisible",
            )
        }
    }

    @Test
    fun leftBracket_decreasesSubtitleDelay() {
        assertEquals(
            PlayerKeyAction.SubtitleDelayDecrease,
            mediaKeyAction(keyCode = PlayerKeyCodes.KEYCODE_LEFT_BRACKET, controlsVisible = true),
        )
    }

    @Test
    fun rightBracket_increasesSubtitleDelay() {
        assertEquals(
            PlayerKeyAction.SubtitleDelayIncrease,
            mediaKeyAction(keyCode = PlayerKeyCodes.KEYCODE_RIGHT_BRACKET, controlsVisible = true),
        )
    }

    @Test
    fun ctrlLeftBracket_decreasesAudioDelay() {
        // mpv-style Ctrl+± on the audio delay — the plain brackets keep the
        // subtitle rows, so the fold keys on isCtrlPressed alone.
        assertEquals(
            PlayerKeyAction.AudioDelayDecrease,
            mediaKeyAction(
                keyCode = PlayerKeyCodes.KEYCODE_LEFT_BRACKET,
                controlsVisible = true,
                isCtrlPressed = true,
            ),
        )
    }

    @Test
    fun ctrlRightBracket_increasesAudioDelay() {
        assertEquals(
            PlayerKeyAction.AudioDelayIncrease,
            mediaKeyAction(
                keyCode = PlayerKeyCodes.KEYCODE_RIGHT_BRACKET,
                controlsVisible = true,
                isCtrlPressed = true,
            ),
        )
    }

    @Test
    fun g_decreases_andH_increasesSubtitleDelay() {
        // G/H are jellyfin-web's subtitle-delay aliases of `[`/`]`.
        assertEquals(
            PlayerKeyAction.SubtitleDelayDecrease,
            mediaKeyAction(keyCode = PlayerKeyCodes.KEYCODE_G, controlsVisible = true),
        )
        assertEquals(
            PlayerKeyAction.SubtitleDelayIncrease,
            mediaKeyAction(keyCode = PlayerKeyCodes.KEYCODE_H, controlsVisible = true),
        )
    }

    @Test
    fun dpadUp_andVolumeUp_areVolumeUp_dpadDown_andVolumeDown_areVolumeDown() {
        for (code in listOf(PlayerKeyCodes.KEYCODE_DPAD_UP, PlayerKeyCodes.KEYCODE_VOLUME_UP)) {
            assertEquals(
                PlayerKeyAction.VolumeUp,
                mediaKeyAction(keyCode = code, controlsVisible = true),
                "keyCode=$code",
            )
        }
        for (code in listOf(PlayerKeyCodes.KEYCODE_DPAD_DOWN, PlayerKeyCodes.KEYCODE_VOLUME_DOWN)) {
            assertEquals(
                PlayerKeyAction.VolumeDown,
                mediaKeyAction(keyCode = code, controlsVisible = true),
                "keyCode=$code",
            )
        }
    }

    @Test
    fun f_andFunctionKeys_toggleOrientation() {
        for (code in listOf(
            PlayerKeyCodes.KEYCODE_F,
            PlayerKeyCodes.KEYCODE_F1,
            PlayerKeyCodes.KEYCODE_F2,
            PlayerKeyCodes.KEYCODE_F3,
            PlayerKeyCodes.KEYCODE_F4,
        )) {
            assertEquals(
                PlayerKeyAction.ToggleOrientation,
                mediaKeyAction(keyCode = code, controlsVisible = true),
                "keyCode=$code",
            )
        }
    }

    @Test
    fun m_togglesMute() {
        assertEquals(
            PlayerKeyAction.ToggleMute,
            mediaKeyAction(keyCode = PlayerKeyCodes.KEYCODE_M, controlsVisible = true),
        )
    }

    @Test
    fun v_togglesSubtitles() {
        // mpv's subtitle-visibility key, regardless of controls visibility
        // — like mute, the effect lands on the video, not the chrome.
        for (controlsVisible in listOf(true, false)) {
            assertEquals(
                PlayerKeyAction.ToggleSubtitles,
                mediaKeyAction(keyCode = PlayerKeyCodes.KEYCODE_V, controlsVisible = controlsVisible),
                "controlsVisible=$controlsVisible",
            )
        }
    }

    @Test
    fun escape_andBack_hideVisibleControls() {
        // ESC's first arm: controls up → collapse them, stay in the player.
        for (code in listOf(PlayerKeyCodes.KEYCODE_ESCAPE, PlayerKeyCodes.KEYCODE_BACK)) {
            assertEquals(
                PlayerKeyAction.HideControls,
                mediaKeyAction(keyCode = code, controlsVisible = true),
                "keyCode=$code",
            )
        }
    }

    @Test
    fun escape_andBack_exitWhenControlsHidden() {
        // ESC's second arm: controls already hidden → back out of the player.
        for (code in listOf(PlayerKeyCodes.KEYCODE_ESCAPE, PlayerKeyCodes.KEYCODE_BACK)) {
            assertEquals(
                PlayerKeyAction.Exit,
                mediaKeyAction(keyCode = code, controlsVisible = false),
                "keyCode=$code",
            )
        }
    }

    @Test
    fun unknownKeys_resolveToNull() {
        // 0 is the actuals' explicit "unmatched" code (the desktop mapping's
        // fall-through); arbitrary unmapped codes must behave the same — in
        // BOTH tables.
        assertNull(mediaKeyAction(keyCode = 0, controlsVisible = true))
        assertNull(mediaKeyAction(keyCode = 999, controlsVisible = false))
        assertNull(mediaKeySeek(keyCode = 0, configuredStepMs = 10_000L))
        assertNull(mediaKeySeek(keyCode = 999, configuredStepMs = 10_000L))
    }

    @Test
    fun everyPlayerKeyCodeConstant_isCoveredByOneOfTheTables() {
        // The vocabulary is the whole input domain the actuals can produce:
        // any constant resolving to null in BOTH tables means a hardware key
        // the screen silently stopped handling (and one resolving in BOTH
        // would double-handle).
        val vocabulary: List<Int> = listOf(
            PlayerKeyCodes.KEYCODE_SPACE,
            PlayerKeyCodes.KEYCODE_MEDIA_PLAY,
            PlayerKeyCodes.KEYCODE_MEDIA_PAUSE,
            PlayerKeyCodes.KEYCODE_MEDIA_PLAY_PAUSE,
            PlayerKeyCodes.KEYCODE_K,
            PlayerKeyCodes.KEYCODE_DPAD_RIGHT,
            PlayerKeyCodes.KEYCODE_MEDIA_FAST_FORWARD,
            PlayerKeyCodes.KEYCODE_L,
            PlayerKeyCodes.KEYCODE_DPAD_LEFT,
            PlayerKeyCodes.KEYCODE_MEDIA_REWIND,
            PlayerKeyCodes.KEYCODE_J,
            PlayerKeyCodes.KEYCODE_G,
            PlayerKeyCodes.KEYCODE_H,
            PlayerKeyCodes.KEYCODE_LEFT_BRACKET,
            PlayerKeyCodes.KEYCODE_RIGHT_BRACKET,
            PlayerKeyCodes.KEYCODE_PAGE_UP,
            PlayerKeyCodes.KEYCODE_PAGE_DOWN,
            PlayerKeyCodes.KEYCODE_MOVE_HOME,
            PlayerKeyCodes.KEYCODE_MOVE_END,
            PlayerKeyCodes.KEYCODE_DPAD_UP,
            PlayerKeyCodes.KEYCODE_VOLUME_UP,
            PlayerKeyCodes.KEYCODE_DPAD_DOWN,
            PlayerKeyCodes.KEYCODE_VOLUME_DOWN,
            PlayerKeyCodes.KEYCODE_F,
            PlayerKeyCodes.KEYCODE_F1,
            PlayerKeyCodes.KEYCODE_F2,
            PlayerKeyCodes.KEYCODE_F3,
            PlayerKeyCodes.KEYCODE_F4,
            PlayerKeyCodes.KEYCODE_M,
            PlayerKeyCodes.KEYCODE_V,
            PlayerKeyCodes.KEYCODE_ESCAPE,
            PlayerKeyCodes.KEYCODE_BACK,
        )
        for (code in vocabulary) {
            val isAction = mediaKeyAction(keyCode = code, controlsVisible = true) != null
            val isSeek = mediaKeySeek(keyCode = code, configuredStepMs = 10_000L) != null
            if (isAction == isSeek) {
                fail(
                    "PlayerKeyCodes constant $code must resolve in EXACTLY one of the two " +
                        "tables (action=$isAction seek=$isSeek) — uncovered or double-handled key",
                )
            }
        }
    }
}

/**
 * The seek-table rows ([mediaKeySeek]) — one row per key, plus the
 * modifier folds. The step VALUES are the shared player-contract's
 * `keyboardSeekStepMs` table (pinned there in PlayerChromePoliciesTest); the
 * rows here pin WHICH keys carry the fold and in which direction, and that
 * the configured jump only feeds the plain (modifier-less) press.
 */
class MediaKeySeekTest {

    @Test
    fun dpadRight_fastForward_andL_seekForward() {
        for (code in listOf(
            PlayerKeyCodes.KEYCODE_DPAD_RIGHT,
            PlayerKeyCodes.KEYCODE_MEDIA_FAST_FORWARD,
            PlayerKeyCodes.KEYCODE_L,
        )) {
            assertEquals(
                PlayerKeySeek(direction = 1, stepMs = 10_000L),
                mediaKeySeek(keyCode = code, configuredStepMs = 10_000L),
                "keyCode=$code",
            )
        }
    }

    @Test
    fun dpadLeft_rewind_andJ_seekBack() {
        for (code in listOf(
            PlayerKeyCodes.KEYCODE_DPAD_LEFT,
            PlayerKeyCodes.KEYCODE_MEDIA_REWIND,
            PlayerKeyCodes.KEYCODE_J,
        )) {
            assertEquals(
                PlayerKeySeek(direction = -1, stepMs = 10_000L),
                mediaKeySeek(keyCode = code, configuredStepMs = 10_000L),
                "keyCode=$code",
            )
        }
    }

    @Test
    fun shiftArrows_takeTheFineFiveSecondStep_bothDirections() {
        assertEquals(
            PlayerKeySeek(direction = 1, stepMs = KEY_SEEK_STEP_FINE_MS),
            mediaKeySeek(
                keyCode = PlayerKeyCodes.KEYCODE_DPAD_RIGHT,
                configuredStepMs = 10_000L,
                isShiftPressed = true,
            ),
        )
        assertEquals(
            PlayerKeySeek(direction = -1, stepMs = KEY_SEEK_STEP_FINE_MS),
            mediaKeySeek(
                keyCode = PlayerKeyCodes.KEYCODE_DPAD_LEFT,
                configuredStepMs = 30_000L,
                isShiftPressed = true,
            ),
        )
    }

    @Test
    fun ctrlArrows_takeTheSixtySecondStep_bothDirections() {
        assertEquals(
            PlayerKeySeek(direction = 1, stepMs = KEY_SEEK_STEP_CTRL_MS),
            mediaKeySeek(
                keyCode = PlayerKeyCodes.KEYCODE_DPAD_RIGHT,
                configuredStepMs = 10_000L,
                isCtrlPressed = true,
            ),
        )
        assertEquals(
            PlayerKeySeek(direction = -1, stepMs = KEY_SEEK_STEP_CTRL_MS),
            mediaKeySeek(
                keyCode = PlayerKeyCodes.KEYCODE_DPAD_LEFT,
                configuredStepMs = 30_000L,
                isCtrlPressed = true,
            ),
        )
    }

    @Test
    fun shiftCtrlArrows_takeTheThirtySecondStep_beatingTheSingleModifierRows() {
        assertEquals(
            PlayerKeySeek(direction = 1, stepMs = KEY_SEEK_STEP_SHIFT_CTRL_MS),
            mediaKeySeek(
                keyCode = PlayerKeyCodes.KEYCODE_DPAD_RIGHT,
                configuredStepMs = 10_000L,
                isShiftPressed = true,
                isCtrlPressed = true,
            ),
        )
        assertEquals(
            PlayerKeySeek(direction = -1, stepMs = KEY_SEEK_STEP_SHIFT_CTRL_MS),
            mediaKeySeek(
                keyCode = PlayerKeyCodes.KEYCODE_DPAD_LEFT,
                configuredStepMs = 10_000L,
                isShiftPressed = true,
                isCtrlPressed = true,
            ),
        )
    }

    @Test
    fun altCtrlArrows_takeTheFiveMinuteStep_bothDirections() {
        assertEquals(
            PlayerKeySeek(direction = 1, stepMs = KEY_SEEK_STEP_ALT_CTRL_MS),
            mediaKeySeek(
                keyCode = PlayerKeyCodes.KEYCODE_DPAD_RIGHT,
                configuredStepMs = 10_000L,
                isAltPressed = true,
                isCtrlPressed = true,
            ),
        )
        assertEquals(
            PlayerKeySeek(direction = -1, stepMs = KEY_SEEK_STEP_ALT_CTRL_MS),
            mediaKeySeek(
                keyCode = PlayerKeyCodes.KEYCODE_DPAD_LEFT,
                configuredStepMs = 10_000L,
                isAltPressed = true,
                isCtrlPressed = true,
            ),
        )
    }

    @Test
    fun home_isBack_andEnd_isForward_atTheSmallStep() {
        // jellyfin-media-player semantics: Home/End are the small-step rows,
        // direction-fixed and modifier-independent.
        assertEquals(
            PlayerKeySeek(direction = -1, stepMs = KEY_SEEK_STEP_HOME_END_MS),
            mediaKeySeek(keyCode = PlayerKeyCodes.KEYCODE_MOVE_HOME, configuredStepMs = 60_000L),
        )
        assertEquals(
            PlayerKeySeek(direction = 1, stepMs = KEY_SEEK_STEP_HOME_END_MS),
            mediaKeySeek(keyCode = PlayerKeyCodes.KEYCODE_MOVE_END, configuredStepMs = 60_000L),
        )
    }

    @Test
    fun pageUp_isForward_andPageDown_isBack_atTheBigStep() {
        assertEquals(
            PlayerKeySeek(direction = 1, stepMs = KEY_SEEK_STEP_PAGE_MS),
            mediaKeySeek(keyCode = PlayerKeyCodes.KEYCODE_PAGE_UP, configuredStepMs = 60_000L),
        )
        assertEquals(
            PlayerKeySeek(direction = -1, stepMs = KEY_SEEK_STEP_PAGE_MS),
            mediaKeySeek(keyCode = PlayerKeyCodes.KEYCODE_PAGE_DOWN, configuredStepMs = 60_000L),
        )
    }

    @Test
    fun plainArrows_keepTheConfiguredStep() {
        // The configured preference step is the whole point of the plain row —
        // it must flow through untouched, not a hardcoded default.
        assertEquals(
            PlayerKeySeek(direction = 1, stepMs = 33_000L),
            mediaKeySeek(keyCode = PlayerKeyCodes.KEYCODE_DPAD_RIGHT, configuredStepMs = 33_000L),
        )
        assertEquals(
            PlayerKeySeek(direction = -1, stepMs = 33_000L),
            mediaKeySeek(keyCode = PlayerKeyCodes.KEYCODE_DPAD_LEFT, configuredStepMs = 33_000L),
        )
    }

    @Test
    fun delayAdjust_stepsAndClampsToTheOverlayWindow() {
        // the fold: ±step around the current value, clamped to the same ±30s
        // window SubtitleDelayOverlay and AVSyncSheet enforce.
        assertEquals(150L, delayAdjustMs(currentMs = 100L, sign = 1, stepMs = KEY_SUBTITLE_DELAY_STEP_MS))
        assertEquals(50L, delayAdjustMs(currentMs = 100L, sign = -1, stepMs = KEY_SUBTITLE_DELAY_STEP_MS))
        assertEquals(300L, delayAdjustMs(currentMs = 200L, sign = 1, stepMs = KEY_AUDIO_DELAY_STEP_MS))
        // Clamp ends: the overlays stop at ±30s, so must the shortcuts.
        assertEquals(KEY_DELAY_MAX_MS, delayAdjustMs(currentMs = KEY_DELAY_MAX_MS, sign = 1, stepMs = 5_000L))
        assertEquals(KEY_DELAY_MIN_MS, delayAdjustMs(currentMs = KEY_DELAY_MIN_MS, sign = -1, stepMs = 5_000L))
    }
}
