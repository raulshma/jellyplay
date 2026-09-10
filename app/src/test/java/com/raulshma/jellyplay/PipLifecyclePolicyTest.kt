package com.raulshma.jellyplay

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * JVM test table for [PipLifecyclePolicy] — the PiP ordering machine that
 * lived inline in PlayerActivity. Every row is a callback sequence (or a
 * guard combination) transcribed from the OEM-ordering comments that were the
 * spec: clean expand; dismiss-before-onStop on both OEM orderings; dismiss
 * with background audio; keyguard landing inside the dismissal transition;
 * top-resumed loss during playback; the auto-enter allow/deny matrices; and
 * the param-builder folds (aspect clamp, source-rect validity).
 */
class PipLifecyclePolicyTest {

    private val none = PipLifecyclePolicy.Action.None
    private val finish = PipLifecyclePolicy.Action.Finish
    private val pause = PipLifecyclePolicy.Action.Pause
    private val resume = PipLifecyclePolicy.Action.Resume
    private val enterPip = PipLifecyclePolicy.Action.EnterPip

    // ── Callback sequences ─────────────────────────────────────────────────

    @Test
    fun `clean expand — callback at RESUMED arms nothing and onResume clears`() {
        var flag = false
        // onPipModeChanged(false) while RESUMED: genuine expand, no-op.
        val atCallback = PipLifecyclePolicy.pipExited(
            phase = PipLifecyclePolicy.Phase.RESUMED,
            keepPlayerAlive = false,
            isFinishing = false,
            justExitedPip = flag,
        )
        assertEquals(none, atCallback.action)
        assertEquals(false, atCallback.justExitedPip)
        flag = atCallback.justExitedPip

        // onResume follows: clears the flag (no-op here) and resumes playback.
        val atResume = PipLifecyclePolicy.onResume(flag)
        assertEquals(resume, atResume.action)
        assertEquals(false, atResume.justExitedPip)
    }

    @Test
    fun `expand at RESUMED leaves a pre-armed flag alone — onResume is the clearer`() {
        // Pure-table pin of the RESUMED branch: it neither arms nor clears.
        val decision = PipLifecyclePolicy.pipExited(
            phase = PipLifecyclePolicy.Phase.RESUMED,
            keepPlayerAlive = false,
            isFinishing = false,
            justExitedPip = true,
        )
        assertEquals(none, decision.action)
        assertEquals(true, decision.justExitedPip)
    }

    @Test
    fun `dismiss-before-onStop — callback BELOW_STARTED finishes directly, then onStop is inert`() {
        var flag = false
        // OEM ordering where onStop fired BEFORE the callback (state=CREATED):
        // finish here — the activity will never resume.
        val atCallback = PipLifecyclePolicy.pipExited(
            phase = PipLifecyclePolicy.Phase.BELOW_STARTED,
            keepPlayerAlive = false,
            isFinishing = false,
            justExitedPip = flag,
        )
        assertEquals(finish, atCallback.action)
        assertEquals(false, atCallback.justExitedPip)
        flag = atCallback.justExitedPip

        // The activity is finishing: the following onStop must not re-finish
        // (the isFinishing guard) nor pause.
        val atStop = PipLifecyclePolicy.onStop(
            inPip = false,
            screenOffOrLocked = false,
            keepPlayerAlive = false,
            isFinishing = true,
            justExitedPip = flag,
        )
        assertEquals(none, atStop.action)
    }

    @Test
    fun `callback-before-onStop — arm at STARTED_NOT_RESUMED discharges as finish at onStop`() {
        // The other OEM ordering: the callback fires first (state >= STARTED),
        // arms justExitedPip; the later onStop does the finish.
        val atCallback = PipLifecyclePolicy.pipExited(
            phase = PipLifecyclePolicy.Phase.STARTED_NOT_RESUMED,
            keepPlayerAlive = false,
            isFinishing = false,
            justExitedPip = false,
        )
        assertEquals(none, atCallback.action)
        assertEquals(true, atCallback.justExitedPip)

        val atStop = PipLifecyclePolicy.onStop(
            inPip = false,
            screenOffOrLocked = false,
            keepPlayerAlive = false,
            isFinishing = false,
            justExitedPip = atCallback.justExitedPip,
        )
        assertEquals(finish, atStop.action)
        assertEquals(false, atStop.justExitedPip)
    }

    @Test
    fun `dismiss with background audio — keep-alive never arms and onStop keeps playing`() {
        // Dismiss at BELOW_STARTED with background audio + interactive screen:
        // no finish, only the window closes.
        val belowStarted = PipLifecyclePolicy.pipExited(
            phase = PipLifecyclePolicy.Phase.BELOW_STARTED,
            keepPlayerAlive = true,
            isFinishing = false,
            justExitedPip = false,
        )
        assertEquals(none, belowStarted.action)
        assertEquals(false, belowStarted.justExitedPip)

        // Dismiss at the arm window: a keep-alive dismissal leaves the flag
        // clear so onStop skips finish.
        val armed = PipLifecyclePolicy.pipExited(
            phase = PipLifecyclePolicy.Phase.STARTED_NOT_RESUMED,
            keepPlayerAlive = true,
            isFinishing = false,
            justExitedPip = false,
        )
        assertEquals(none, armed.action)
        assertEquals(false, armed.justExitedPip)

        // The stopped instance behind MainActivity: no pause — playback
        // continues from the notification.
        val atStop = PipLifecyclePolicy.onStop(
            inPip = true,
            screenOffOrLocked = false,
            keepPlayerAlive = true,
            isFinishing = false,
            justExitedPip = armed.justExitedPip,
        )
        assertEquals(none, atStop.action)
    }

    @Test
    fun `keyguard landing between the callback and onStop — the keep-alive re-check denies finish`() {
        // Arming happened while the screen was interactive...
        val atCallback = PipLifecyclePolicy.pipExited(
            phase = PipLifecyclePolicy.Phase.STARTED_NOT_RESUMED,
            keepPlayerAlive = false,
            isFinishing = false,
            justExitedPip = false,
        )
        assertEquals(true, atCallback.justExitedPip)

        // ...then the keyguard landed: at onStop the fresh keep-alive read is
        // true, so the armed discharge degrades to a no-op (flag still cleared).
        val atStop = PipLifecyclePolicy.onStop(
            inPip = false,
            screenOffOrLocked = true,
            keepPlayerAlive = true,
            isFinishing = false,
            justExitedPip = atCallback.justExitedPip,
        )
        assertEquals(none, atStop.action)
        assertEquals(false, atStop.justExitedPip)
    }

    @Test
    fun `unarmed stop in PiP pauses only for screen-lock or keyguard`() {
        // Screen-lock in PiP (bg audio OFF): pause so audio doesn't leak.
        assertEquals(
            pause,
            PipLifecyclePolicy.onStop(
                inPip = true, screenOffOrLocked = true,
                keepPlayerAlive = false, isFinishing = false, justExitedPip = false,
            ).action,
        )
        // Plain minimise while in PiP: intentionally keep playing.
        assertEquals(
            none,
            PipLifecyclePolicy.onStop(
                inPip = true, screenOffOrLocked = false,
                keepPlayerAlive = true, isFinishing = false, justExitedPip = false,
            ).action,
        )
        // Not in PiP: onStop is inert (normal backgrounding pauses via the
        // onPause fold, a separate callback — exactly the original if/else-if).
        assertEquals(
            none,
            PipLifecyclePolicy.onStop(
                inPip = false, screenOffOrLocked = false,
                keepPlayerAlive = true, isFinishing = false, justExitedPip = false,
            ).action,
        )
    }

    @Test
    fun `armed stop never also pauses — the finish and pause branches are exclusive`() {
        // Pin of the original if / else-if: with the flag armed, screen-lock
        // cannot turn the same onStop into a pause.
        val decision = PipLifecyclePolicy.onStop(
            inPip = true,
            screenOffOrLocked = true,
            keepPlayerAlive = false,
            isFinishing = false,
            justExitedPip = true,
        )
        assertEquals(finish, decision.action)
    }

    @Test
    fun `onPause folds — minimise into PiP with an interactive screen keeps playing`() {
        assertEquals(pause, PipLifecyclePolicy.onPause(inPip = false, screenOffOrLocked = false))
        assertEquals(pause, PipLifecyclePolicy.onPause(inPip = false, screenOffOrLocked = true))
        assertEquals(pause, PipLifecyclePolicy.onPause(inPip = true, screenOffOrLocked = true))
        assertEquals(none, PipLifecyclePolicy.onPause(inPip = true, screenOffOrLocked = false))
    }

    @Test
    fun `onResume always clears the flag and resumes`() {
        val decision = PipLifecyclePolicy.onResume(justExitedPip = true)
        assertEquals(resume, decision.action)
        assertEquals(false, decision.justExitedPip)
    }

    // ── Top-resumed fallback (the isPlaying-divergent guard) ────────────────

    @Test
    fun `top-resumed loss during playback enters PiP`() {
        val decision = PipLifecyclePolicy.onTopResumedChanged(
            isTopResumed = false,
            inPip = false,
            apiSupportsAutoEnter = true,
            shouldAutoEnter = true,
            isPlaying = true,
            controlsLocked = false,
            screenOffOrLocked = false,
            justExitedPip = false,
        )
        assertEquals(enterPip, decision.action)
        assertEquals(false, decision.justExitedPip)
    }

    @Test
    fun `top-resumed loss is denied when paused, locked, keyguard, in PiP or API-gated`() {
        fun denied(
            isPlaying: Boolean = true,
            controlsLocked: Boolean = false,
            screenOffOrLocked: Boolean = false,
            inPip: Boolean = false,
            apiSupportsAutoEnter: Boolean = true,
        ) = PipLifecyclePolicy.onTopResumedChanged(
            isTopResumed = false,
            inPip = inPip,
            apiSupportsAutoEnter = apiSupportsAutoEnter,
            shouldAutoEnter = true,
            isPlaying = isPlaying,
            controlsLocked = controlsLocked,
            screenOffOrLocked = screenOffOrLocked,
            justExitedPip = false,
        ).action

        // The isPlaying term is the divergence from onUserLeaveHint's guard.
        assertEquals(none, denied(isPlaying = false))
        assertEquals(none, denied(controlsLocked = true))
        assertEquals(none, denied(screenOffOrLocked = true))
        assertEquals(none, denied(inPip = true))
        assertEquals(none, denied(apiSupportsAutoEnter = false))
    }

    @Test
    fun `regaining top-resumed discharges an armed flag as resume — without it, nothing`() {
        val withFlag = PipLifecyclePolicy.onTopResumedChanged(
            isTopResumed = true,
            inPip = false,
            apiSupportsAutoEnter = true,
            shouldAutoEnter = false,
            isPlaying = false,
            controlsLocked = false,
            screenOffOrLocked = false,
            justExitedPip = true,
        )
        assertEquals(resume, withFlag.action)
        assertEquals(false, withFlag.justExitedPip)

        // isTopResumed without an armed flag falls through to the loss branch,
        // whose !isTopResumed term denies: plain no-op.
        val withoutFlag = PipLifecyclePolicy.onTopResumedChanged(
            isTopResumed = true,
            inPip = false,
            apiSupportsAutoEnter = true,
            shouldAutoEnter = true,
            isPlaying = true,
            controlsLocked = false,
            screenOffOrLocked = false,
            justExitedPip = false,
        )
        assertEquals(none, withoutFlag.action)
    }

    // ── Auto-enter allow/deny matrices ─────────────────────────────────────

    @Test
    fun `userLeave auto-enter matrix — no isPlaying term`() {
        for (shouldAutoEnter in listOf(false, true)) {
            for (controlsLocked in listOf(false, true)) {
                for (screenOffOrLocked in listOf(false, true)) {
                    val expected = shouldAutoEnter && !controlsLocked && !screenOffOrLocked
                    assertEquals(
                        "userLeave($shouldAutoEnter, $controlsLocked, $screenOffOrLocked)",
                        expected,
                        PipLifecyclePolicy.userLeaveAutoEnter(shouldAutoEnter, controlsLocked, screenOffOrLocked),
                    )
                    assertEquals(
                        "onUserLeaveHint($shouldAutoEnter, $controlsLocked, $screenOffOrLocked)",
                        if (expected) enterPip else none,
                        PipLifecyclePolicy.onUserLeaveHint(shouldAutoEnter, controlsLocked, screenOffOrLocked),
                    )
                }
            }
        }
    }

    @Test
    fun `topResumedLoss auto-enter matrix — adds the isPlaying term`() {
        for (shouldAutoEnter in listOf(false, true)) {
            for (isPlaying in listOf(false, true)) {
                for (controlsLocked in listOf(false, true)) {
                    for (screenOffOrLocked in listOf(false, true)) {
                        val expected = shouldAutoEnter && isPlaying &&
                            !controlsLocked && !screenOffOrLocked
                        assertEquals(
                            "topResumedLoss($shouldAutoEnter, $isPlaying, $controlsLocked, $screenOffOrLocked)",
                            expected,
                            PipLifecyclePolicy.topResumedLossAutoEnter(
                                shouldAutoEnter, isPlaying, controlsLocked, screenOffOrLocked,
                            ),
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `systemAutoEnterEnabled matrix — no controls-lock term`() {
        for (shouldAutoEnter in listOf(false, true)) {
            for (isPlaying in listOf(false, true)) {
                for (screenOffOrLocked in listOf(false, true)) {
                    val expected = shouldAutoEnter && isPlaying && !screenOffOrLocked
                    assertEquals(
                        "systemAutoEnter($shouldAutoEnter, $isPlaying, $screenOffOrLocked)",
                        expected,
                        PipLifecyclePolicy.systemAutoEnterEnabled(shouldAutoEnter, isPlaying, screenOffOrLocked),
                    )
                }
            }
        }
    }

    // ── Param-builder folds ─────────────────────────────────────────────────

    @Test
    fun `aspect clamp passes 16-9 and in-range ratios through unrenormalised`() {
        assertEquals(16 to 9, PipLifecyclePolicy.clampAspectRatio(16, 9))
        assertEquals(1 to 1, PipLifecyclePolicy.clampAspectRatio(1, 1))
        // Exact boundaries are inclusive (the original used strict < and >).
        assertEquals(100 to 239, PipLifecyclePolicy.clampAspectRatio(100, 239))
        assertEquals(239 to 100, PipLifecyclePolicy.clampAspectRatio(239, 100))
        // Equivalent unreduced ratio stays unreduced — Rational's constructor
        // at the call site renormalises identically either way.
        assertEquals(200 to 478, PipLifecyclePolicy.clampAspectRatio(200, 478))
    }

    @Test
    fun `aspect clamp pins to 100-239 and 239-100 without overflow`() {
        assertEquals(100 to 239, PipLifecyclePolicy.clampAspectRatio(1, 3))
        assertEquals(239 to 100, PipLifecyclePolicy.clampAspectRatio(3, 1))
        assertEquals(239 to 100, PipLifecyclePolicy.clampAspectRatio(Int.MAX_VALUE, 1))
        assertEquals(100 to 239, PipLifecyclePolicy.clampAspectRatio(1, Int.MAX_VALUE))
    }

    @Test
    fun `source rect validity matrix`() {
        // In-bounds.
        assertEquals(
            true,
            PipLifecyclePolicy.isValidSourceRect(0, 0, 100, 100, windowWidth = 1000, windowHeight = 500),
        )
        // Boundary-inclusive right/bottom.
        assertEquals(
            true,
            PipLifecyclePolicy.isValidSourceRect(0, 0, 1000, 500, windowWidth = 1000, windowHeight = 500),
        )
        // Degenerate (zero or negative extent).
        assertEquals(
            false,
            PipLifecyclePolicy.isValidSourceRect(10, 10, 10, 100, windowWidth = 1000, windowHeight = 500),
        )
        assertEquals(
            false,
            PipLifecyclePolicy.isValidSourceRect(100, 0, 50, 50, windowWidth = 1000, windowHeight = 500),
        )
        // Negative origin.
        assertEquals(
            false,
            PipLifecyclePolicy.isValidSourceRect(-1, 0, 100, 100, windowWidth = 1000, windowHeight = 500),
        )
        assertEquals(
            false,
            PipLifecyclePolicy.isValidSourceRect(0, -1, 100, 100, windowWidth = 1000, windowHeight = 500),
        )
        // Outside the window bounds.
        assertEquals(
            false,
            PipLifecyclePolicy.isValidSourceRect(0, 0, 1001, 100, windowWidth = 1000, windowHeight = 500),
        )
        assertEquals(
            false,
            PipLifecyclePolicy.isValidSourceRect(0, 0, 100, 501, windowWidth = 1000, windowHeight = 500),
        )
        // Unknown window size (pre-first-layout): the bounds check is skipped.
        assertEquals(
            true,
            PipLifecyclePolicy.isValidSourceRect(0, 0, 99999, 99999, windowWidth = 0, windowHeight = 500),
        )
        assertEquals(
            true,
            PipLifecyclePolicy.isValidSourceRect(0, 0, 99999, 99999, windowWidth = 1000, windowHeight = 0),
        )
    }
}
