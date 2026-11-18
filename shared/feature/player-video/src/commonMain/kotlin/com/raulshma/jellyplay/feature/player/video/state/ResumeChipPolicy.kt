package com.raulshma.jellyplay.feature.player.video.state

/**
 * The resume-chip policy — everything the "Resumed — Restart" chip decision
 * knows, extracted from `VideoPlayerScreen`'s resume-reminder collector (the
 * [SeekChipCommitClock] precedent: a pure decision fold so the verdicts are
 * JVM-testable without composition). The screen keeps only the snackbar
 * wiring (the collector registration in `VideoPlayerScreenInfo`'s
 * [com.raulshma.jellyplay.feature.player.video.rememberResumeChipEffect]);
 * this module owns what that wiring decides:
 *
 *  - **When to show** ([shouldShow]): the chip's whole lifetime is the
 *    store's `video_resume_chip_ms` pref (`uiState.uiPrefs.resumeChipMs`);
 *    0 never shows the chip (the pref's documented off arm). There is no
 *    second gate — every reminder emission with a positive window shows.
 *  - **The auto-dismiss window** ([autoDismissWindowMs]): `SnackbarDuration`
 *    has no 3s preset (Short ≈ 1.5s), so the snackbar shows
 *    `SnackbarDuration.Indefinite` and the pref's window IS the display
 *    duration — the wiring delays exactly [autoDismissWindowMs] and then
 *    dismisses whatever snackbar data is CURRENT on the chip host (never a
 *    captured handle), the dismiss-on-new-chip semantics: the collector
 *    serializes on the show-snackbar job, so exactly one chip is ever up,
 *    and a window elapsing dismisses that chip.
 *  - **The tap semantics** ([restartRequested]): tapping "Restart"
 *    (ActionPerformed) restarts playback from zero; every other exit
 *    (the window's auto-dismiss included) is a silent dismissal.
 */
internal object ResumeChipPolicy {

    /**
     * Whether a reminder emission shows the chip at all — `resumeChipMs <= 0`
     * is the pref's documented off arm (the collector's former
     * `if (chipDisplayMs <= 0L) return@collect`).
     */
    fun shouldShow(resumeChipMs: Long): Boolean = resumeChipMs > 0L

    /**
     * The auto-dismiss window for the pref's display duration — the pref's
     * value IS the window (the wiring delays this long after the chip rises,
     * then dismisses the current snackbar data).
     */
    fun autoDismissWindowMs(resumeChipMs: Long): Long = resumeChipMs

    /**
     * What a finished snackbar means: only the user's tap on the action
     * label (ActionPerformed) restarts playback; the window's auto-dismiss
     * and any other exit are silent dismissals.
     */
    fun restartRequested(actionPerformed: Boolean): Boolean = actionPerformed
}
