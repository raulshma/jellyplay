package com.raulshma.jellyplay.feature.player.video

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Wave 14A SPACE-focus fix — pins the platform split of the keyboard-focus
 * grab seam. The desktop (jvm) actual must stay TRUE: with the at-HEAD
 * "request focus only when the controls are hidden" condition, the desktop
 * player Box held no Compose focus while the controls were visible (they
 * START visible and nothing else on the desktop shell takes focus), and the
 * hardware-keyboard media keys (SPACE/arrows/F/M) never reached the screen's
 * key handler — the wave 13B session-harness finding. The grab is what makes
 * OVERLAY_SPACE in tools/e2e/desktop-session-pass.sh pass.
 *
 * The androidMain actual (false — Android keeps the at-HEAD timing) is not
 * loadable from a JVM test; Android parity is pinned by the
 * `:app:compilePhoneDebugKotlin :app:compileTvDebugKotlin` compile gate and
 * by code reading: PlayerKeyboardFocusGrabEffect returns before composing
 * anything when the seam is false.
 */
class KeyboardFocusGrabSeamTest {

    @Test
    fun `desktop grabs keyboard focus whenever the keyboard layer composes`() {
        assertTrue(grabsKeyboardFocusWithControlsVisible())
    }

    @Test
    fun `grab seam is a static platform fact, deterministic across reads`() {
        assertEquals(grabsKeyboardFocusWithControlsVisible(), grabsKeyboardFocusWithControlsVisible())
    }
}
