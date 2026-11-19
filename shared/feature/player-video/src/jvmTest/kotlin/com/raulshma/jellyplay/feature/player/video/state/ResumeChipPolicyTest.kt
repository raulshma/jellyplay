package com.raulshma.jellyplay.feature.player.video.state

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pure-JVM tests for [ResumeChipPolicy] — the resume chip's decision fold
 * extracted from `VideoPlayerScreen`'s resume-reminder collector (the
 * [SeekChipCommitClockTest] pattern: no Compose, no coroutines — the folds
 * are total functions over the pref's display window). Pins the behaviors
 * the former inline collector carried with zero coverage:
 *
 *  - the show gate (the pref's 0 off arm; there is no second gate);
 *  - the auto-dismiss window math (the pref's value IS the window — the
 *    snackbar shows Indefinite because SnackbarDuration has no 3s preset);
 *  - the tap semantics (only ActionPerformed restarts; the window's
 *    auto-dismiss is a silent dismissal).
 */
class ResumeChipPolicyTest {

    // ---- shouldShow: the pref's off arm ----

    @Test
    fun `a zero window never shows the chip`() {
        // 0 = never show the chip (the pref's documented off arm) — the
        // collector's former `if (chipDisplayMs <= 0L) return@collect`.
        assertFalse(ResumeChipPolicy.shouldShow(0L))
    }

    @Test
    fun `a negative window is the off arm too`() {
        assertFalse(ResumeChipPolicy.shouldShow(-1L))
        assertFalse(ResumeChipPolicy.shouldShow(Long.MIN_VALUE))
    }

    @Test
    fun `any positive window shows the chip`() {
        assertTrue(ResumeChipPolicy.shouldShow(1L))
        // The shipped default: a 3s chip (the pref's documented value).
        assertTrue(ResumeChipPolicy.shouldShow(3_000L))
        assertTrue(ResumeChipPolicy.shouldShow(Long.MAX_VALUE))
    }

    // ---- autoDismissWindowMs: the window math ----

    @Test
    fun `the pref's value IS the auto-dismiss window`() {
        // SnackbarDuration has no 3s preset (Short ≈ 1.5s), so the snackbar
        // shows Indefinite and the wiring delays exactly the pref's window.
        assertEquals(3_000L, ResumeChipPolicy.autoDismissWindowMs(3_000L))
        assertEquals(1L, ResumeChipPolicy.autoDismissWindowMs(1L))
        assertEquals(Long.MAX_VALUE, ResumeChipPolicy.autoDismissWindowMs(Long.MAX_VALUE))
    }

    @Test
    fun `the window is the identity fold - no clamp no floor`() {
        // The identity matters: a clamped window would silently shorten or
        // lengthen every user-tuned pref value.
        for (windowMs in longArrayOf(500L, 3_000L, 10_000L, 60_000L)) {
            assertEquals(windowMs, ResumeChipPolicy.autoDismissWindowMs(windowMs))
        }
    }

    // ---- restartRequested: the tap semantics ----

    @Test
    fun `only the action tap restarts playback`() {
        assertTrue(ResumeChipPolicy.restartRequested(actionPerformed = true))
    }

    @Test
    fun `the window's auto-dismiss is a silent dismissal`() {
        // Dismissed (the wiring's currentSnackbarData?.dismiss()) and any
        // other exit must NOT restart playback — only the user's tap on
        // "Restart" does.
        assertFalse(ResumeChipPolicy.restartRequested(actionPerformed = false))
    }
}
