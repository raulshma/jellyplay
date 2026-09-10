package com.raulshma.jellyplay.feature.player.video

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.fail

/**
 * Pins the player's media-key decision table ([PlayerKeyPolicy.kt]) — one row
 * per key code the hardware-keyboard layer handles, the ESC/BACK
 * controls-visible fold, and the unknown-key null — so the screen's effect
 * shell can never silently drift from the key→action mapping. The desktop
 * `playerKeyCode` actual maps Compose keys onto these same constants, so a
 * row here covers both platforms' delivery paths.
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
    fun dpadRight_fastForward_andL_seekForward() {
        for (code in listOf(
            PlayerKeyCodes.KEYCODE_DPAD_RIGHT,
            PlayerKeyCodes.KEYCODE_MEDIA_FAST_FORWARD,
            PlayerKeyCodes.KEYCODE_L,
        )) {
            assertEquals(
                PlayerKeyAction.SeekForward,
                mediaKeyAction(keyCode = code, controlsVisible = true),
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
                PlayerKeyAction.SeekBack,
                mediaKeyAction(keyCode = code, controlsVisible = true),
                "keyCode=$code",
            )
        }
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
        // fall-through); arbitrary unmapped codes must behave the same.
        assertNull(mediaKeyAction(keyCode = 0, controlsVisible = true))
        assertNull(mediaKeyAction(keyCode = 999, controlsVisible = false))
    }

    @Test
    fun everyPlayerKeyCodeConstant_isCoveredByTheTable() {
        // The vocabulary is the whole input domain the actuals can produce:
        // any constant resolving to null means a hardware key the screen
        // silently stopped handling.
        val vocabulary: List<Int> = listOf(
            PlayerKeyCodes.KEYCODE_SPACE,
            PlayerKeyCodes.KEYCODE_MEDIA_PLAY,
            PlayerKeyCodes.KEYCODE_MEDIA_PAUSE,
            PlayerKeyCodes.KEYCODE_MEDIA_PLAY_PAUSE,
            PlayerKeyCodes.KEYCODE_DPAD_RIGHT,
            PlayerKeyCodes.KEYCODE_MEDIA_FAST_FORWARD,
            PlayerKeyCodes.KEYCODE_L,
            PlayerKeyCodes.KEYCODE_DPAD_LEFT,
            PlayerKeyCodes.KEYCODE_MEDIA_REWIND,
            PlayerKeyCodes.KEYCODE_J,
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
            PlayerKeyCodes.KEYCODE_ESCAPE,
            PlayerKeyCodes.KEYCODE_BACK,
        )
        for (code in vocabulary) {
            if (mediaKeyAction(keyCode = code, controlsVisible = true) == null) {
                fail("PlayerKeyCodes constant $code resolves to null — uncovered key")
            }
        }
    }
}
