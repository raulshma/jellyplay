package com.raulshma.jellyplay.feature.player.video.state

import com.raulshma.jellyplay.feature.player.video.chrome.KEYBOARD_SEEK_COMMIT_DELAY_MS

/**
 * The seek-chip commit clock — the two debounce timers around the shared
 * D-pad seek chip ([PlayerActionExecutor.seekState]), extracted from
 * `VideoPlayerScreen`'s two `snapshotFlow` + `collectLatest` debouncers so
 * the verdicts are JVM-testable without composition. The screen keeps only
 * collector registration; this module owns every decision those collectors
 * execute and the timing constants they delay on:
 *
 *  - **The D-pad chip's linger reset** ([LINGER_RESET_MS]): after the last
 *    seek input the chip lingers out, then [DpadSeekState.reset] clears it.
 *    One long-lived collector, not a fresh `LaunchedEffect` per key —
 *    hold-and-repeat seeking would thrash coroutine create/cancel per event.
 *  - **The keyboard commit** ([KEYBOARD_COMMIT_DELAY_MS]): the debounced
 *    twin of the D-pad's commit-on-key-up (which the desktop sink bridge
 *    cannot use — it delivers KeyDown only). Every seek key-down bumps the
 *    executor's `keyboardSeekStamp` and `collectLatest` restarts the delay,
 *    so the commit fires exactly once, after the LAST key-down.
 *
 * The race the stale-timestamp guard exists for: a drag/swipe/double-tap
 * seek INSIDE the commit window moves the chip on after the last keyboard
 * contribution (`lastKeyboardSeekChipTimestamp` holds the chip's timestamp
 * as of that contribution), so a keyboard commit from the keyboard's stale
 * base position would yank playback back over the newer, user-driven seek —
 * the commit stands down instead ([Verdict.StandDown]). The delay ordering
 * is load-bearing the other way: a keyboard-driven chip must COMMIT before
 * the linger reset fires (`KEYBOARD_COMMIT_DELAY_MS < LINGER_RESET_MS` —
 * the reset clears the chip's direction without bumping its timestamp, so
 * a reset-first race would silently swallow the commit).
 */
internal object SeekChipCommitClock {

    /**
     * How long the gesture-seek chip lingers after the last seek input (the
     * screen's former `GESTURE_SEEK_LINGER_MS`).
     */
    const val LINGER_RESET_MS = 800L

    /**
     * The keyboard commit debounce — the chrome policy's constant, the
     * keyboard arm's injected delay (re-exported so the clock stays the one
     * home of the commit timings).
     */
    const val KEYBOARD_COMMIT_DELAY_MS = KEYBOARD_SEEK_COMMIT_DELAY_MS

    /** What the keyboard commit arm executes once the debounce elapses. */
    enum class Verdict {
        /** The chip moved on after the last keyboard contribution — do nothing. */
        StandDown,
        /** Commit the accumulated forward offset, then confirm-haptic. */
        CommitForward,
        /** Commit the accumulated backward offset, then confirm-haptic. */
        CommitBackward,
    }

    /**
     * Whether the chip is live and needs its linger reset armed — the
     * collector's filter (direction 0 is the at-rest chip; the timestamp
     * pair is what `snapshotFlow` observes, this is what it decides).
     */
    fun chipIsLive(direction: Int): Boolean = direction != 0

    /**
     * The keyboard commit fold over the chip's live observation: the
     * stale-timestamp guard first (stand down if the chip moved on after
     * the last keyboard contribution), then the direction ladder — exactly
     * the former inline `when (seekState.direction)` with its implicit
     * direction-0 no-op (a linger reset that fired inside the window
     * cleared the direction without touching the timestamp).
     */
    fun keyboardCommit(
        chipTimestamp: Long,
        direction: Int,
        lastKeyboardSeekChipTimestamp: Long,
    ): Verdict = when {
        chipTimestamp != lastKeyboardSeekChipTimestamp -> Verdict.StandDown
        direction == 1 -> Verdict.CommitForward
        direction == -1 -> Verdict.CommitBackward
        else -> Verdict.StandDown
    }
}
