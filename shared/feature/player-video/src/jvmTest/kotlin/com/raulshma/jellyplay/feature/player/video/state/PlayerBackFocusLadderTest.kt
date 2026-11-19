package com.raulshma.jellyplay.feature.player.video.state

import com.raulshma.jellyplay.feature.player.video.chrome.PlayerChromeBackVerdict
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pure-JVM tests for [PlayerBackFocusLadder] — the screen-local half of the
 * BACK hide-or-exit ladder and the hide-edge focus handoff, extracted from
 * `VideoPlayerScreen`'s root (the [SeekChipCommitClockTest] pattern: no
 * Compose, no coroutines). The VERDICT ENGINE for a press
 * (`PlayerChromeController.backVerdict`) is the chrome module's and is
 * tested there; this suite tables what THIS fold decides:
 *
 *  - the system handler's arm set (the hide arm is TV-only — a touch BACK
 *    exits with the controls up);
 *  - the verdict→action dispatch table (the former inline `when` in the
 *    root's back handler, the summon verdict's no-op included);
 *  - the hide-edge handoff priority ladder (intro > up-next > skip-segment,
 *    the plain-surface defer to the TV grab effect, the off-TV keyboard
 *    re-grab, and no handoff while the chrome is visible).
 */
class PlayerBackFocusLadderTest {

    // ---- systemBackHideArm: the arm set ----

    @Test
    fun `the hide arm is armed on TV only`() {
        assertTrue(PlayerBackFocusLadder.systemBackHideArm(isTv = true))
        assertFalse(PlayerBackFocusLadder.systemBackHideArm(isTv = false))
    }

    @Test
    fun `a touch BACK exits with the controls up - no hide arm`() {
        // The documented semantics: hideArm = isTv, so off-TV the ladder
        // never arms the hide arm and every BACK (controls up or down) is an
        // exit intent.
        val touchVerdict = PlayerChromeBackVerdict.Exit
        assertEquals(
            PlayerBackFocusLadder.BackAction.ExitIntent,
            PlayerBackFocusLadder.backAction(touchVerdict),
        )
    }

    // ---- backAction: the verdict→action dispatch table ----

    @Test
    fun `the dispatch table maps every chrome verdict`() {
        assertEquals(
            PlayerBackFocusLadder.BackAction.CloseSheet,
            PlayerBackFocusLadder.backAction(PlayerChromeBackVerdict.CloseSheet),
        )
        assertEquals(
            PlayerBackFocusLadder.BackAction.HideControls,
            PlayerBackFocusLadder.backAction(PlayerChromeBackVerdict.HideControls),
        )
        assertEquals(
            PlayerBackFocusLadder.BackAction.None,
            PlayerBackFocusLadder.backAction(PlayerChromeBackVerdict.SummonControls),
        )
        assertEquals(
            PlayerBackFocusLadder.BackAction.ExitIntent,
            PlayerBackFocusLadder.backAction(PlayerChromeBackVerdict.Exit),
        )
    }

    @Test
    fun `the summon verdict is a no-op on the system handler`() {
        // The system handler never arms the summon arm, so a SummonControls
        // verdict (defensive) must not hide OR exit — the press is ignored.
        assertEquals(
            PlayerBackFocusLadder.BackAction.None,
            PlayerBackFocusLadder.backAction(PlayerChromeBackVerdict.SummonControls),
        )
    }

    @Test
    fun `exit is an intent - the confirm-exit interposer owns the exit`() {
        // ExitIntent routes through PlayerExitConfirmController.onBackPress,
        // which raises the overlay on the first press (pref on) and exits on
        // the second — the ladder never exits directly.
        assertEquals(
            PlayerBackFocusLadder.BackAction.ExitIntent,
            PlayerBackFocusLadder.backAction(PlayerChromeBackVerdict.Exit),
        )
    }

    // ---- focusHandoff: the hide-edge priority ladder ----

    @Test
    fun `a visible chrome means no handoff on any form factor`() {
        assertEquals(
            PlayerBackFocusLadder.FocusHandoff.None,
            PlayerBackFocusLadder.focusHandoff(
                isTv = true,
                hasHardwareKeyboard = false,
                controlsVisible = true,
                cinemaIntroVisible = true,
                nextEpisodeVisible = true,
                skipSegmentVisible = true,
            ),
        )
        assertEquals(
            PlayerBackFocusLadder.FocusHandoff.None,
            PlayerBackFocusLadder.focusHandoff(
                isTv = false,
                hasHardwareKeyboard = true,
                controlsVisible = true,
                cinemaIntroVisible = false,
                nextEpisodeVisible = false,
                skipSegmentVisible = false,
            ),
        )
    }

    @Test
    fun `the TV handoff targets the top-priority visible overlay`() {
        assertEquals(
            PlayerBackFocusLadder.FocusHandoff.CinemaIntro,
            PlayerBackFocusLadder.focusHandoff(
                isTv = true,
                hasHardwareKeyboard = false,
                controlsVisible = false,
                cinemaIntroVisible = true,
                nextEpisodeVisible = true,
                skipSegmentVisible = true,
            ),
        )
        assertEquals(
            PlayerBackFocusLadder.FocusHandoff.NextEpisode,
            PlayerBackFocusLadder.focusHandoff(
                isTv = true,
                hasHardwareKeyboard = false,
                controlsVisible = false,
                cinemaIntroVisible = false,
                nextEpisodeVisible = true,
                skipSegmentVisible = true,
            ),
        )
        assertEquals(
            PlayerBackFocusLadder.FocusHandoff.SkipSegment,
            PlayerBackFocusLadder.focusHandoff(
                isTv = true,
                hasHardwareKeyboard = false,
                controlsVisible = false,
                cinemaIntroVisible = false,
                nextEpisodeVisible = false,
                skipSegmentVisible = true,
            ),
        )
    }

    @Test
    fun `the TV plain-surface case defers to the grab effect`() {
        // The single un-retried attempt here lost races; the grab effect's
        // retry + re-assert is the recovery path for the plain Box case.
        assertEquals(
            PlayerBackFocusLadder.FocusHandoff.DeferToGrabEffect,
            PlayerBackFocusLadder.focusHandoff(
                isTv = true,
                hasHardwareKeyboard = false,
                controlsVisible = false,
                cinemaIntroVisible = false,
                nextEpisodeVisible = false,
                skipSegmentVisible = false,
            ),
        )
    }

    @Test
    fun `off-TV a hardware keyboard layer regrabs after the auto-hide`() {
        assertEquals(
            PlayerBackFocusLadder.FocusHandoff.KeyboardRegrab,
            PlayerBackFocusLadder.focusHandoff(
                isTv = false,
                hasHardwareKeyboard = true,
                controlsVisible = false,
                cinemaIntroVisible = false,
                nextEpisodeVisible = false,
                skipSegmentVisible = false,
            ),
        )
    }

    @Test
    fun `off-TV without a keyboard there is no handoff`() {
        assertEquals(
            PlayerBackFocusLadder.FocusHandoff.None,
            PlayerBackFocusLadder.focusHandoff(
                isTv = false,
                hasHardwareKeyboard = false,
                controlsVisible = false,
                cinemaIntroVisible = false,
                nextEpisodeVisible = false,
                skipSegmentVisible = false,
            ),
        )
    }

    @Test
    fun `overlay priority is cinema intro then up next then skip segment`() {
        // The exact former `when` order — a visible intro beats the up-next
        // card, which beats the skip-segment button, regardless of what else
        // is up.
        val ladder = { intro: Boolean, next: Boolean, skip: Boolean ->
            PlayerBackFocusLadder.focusHandoff(
                isTv = true,
                hasHardwareKeyboard = false,
                controlsVisible = false,
                cinemaIntroVisible = intro,
                nextEpisodeVisible = next,
                skipSegmentVisible = skip,
            )
        }
        assertEquals(PlayerBackFocusLadder.FocusHandoff.CinemaIntro, ladder(true, true, true))
        assertEquals(PlayerBackFocusLadder.FocusHandoff.CinemaIntro, ladder(true, false, true))
        assertEquals(PlayerBackFocusLadder.FocusHandoff.CinemaIntro, ladder(true, true, false))
        assertEquals(PlayerBackFocusLadder.FocusHandoff.NextEpisode, ladder(false, true, true))
        assertEquals(PlayerBackFocusLadder.FocusHandoff.NextEpisode, ladder(false, true, false))
        assertEquals(PlayerBackFocusLadder.FocusHandoff.SkipSegment, ladder(false, false, true))
    }
}
