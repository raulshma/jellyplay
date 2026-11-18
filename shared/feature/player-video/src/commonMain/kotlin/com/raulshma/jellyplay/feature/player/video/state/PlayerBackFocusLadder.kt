package com.raulshma.jellyplay.feature.player.video.state

import com.raulshma.jellyplay.feature.player.video.chrome.PlayerChromeBackVerdict

/**
 * The player screen's BACK + focus-handoff ladder — everything the screen's
 * back handling and hide-edge focus handoff DECIDE, extracted from
 * `VideoPlayerScreen`'s root (the [SeekChipCommitClock] precedent: a pure
 * fold so the verdicts are JVM-testable without composition). The screen
 * keeps only the registration (the back-handler wiring in
 * `VideoPlayerScreenOverlays`' `rememberPlayerBackLadder` wrapper); this
 * module owns what that wiring decides.
 *
 * The VERDICT ENGINE for a BACK press stays in the chrome module —
 * `PlayerChromeController.backVerdict` (the GLOSSARY "Player chrome module"
 * entry's hide-or-exit ladder: sheet → close, TV-visible → hide, else exit).
 * This fold owns the screen-local half the chrome module cannot know:
 *
 *  - **The system handler's arm set** ([systemBackHideArm]): the hide arm is
 *    TV-only — a touch BACK exits with the controls up. The TV D-pad arm set
 *    deliberately carries no BACK_OR_HIDE arm (the arm acting on the KeyDown
 *    AND the predictive-back callback replaying the same press on the KeyUp's
 *    Pre-IME stage double-acted: one press hid the controls AND exited), and
 *    PlayerActivity.onBackPressed suppresses the legacy key path on API 33+
 *    whenever a back callback IS registered — on API <33 the legacy path is
 *    the only dispatch, so there is nothing to suppress.
 *  - **The verdict→action dispatch** ([backAction]): what the screen does
 *    with the chrome module's verdict. `SummonControls` never fires on the
 *    system handler (its summon arm is disarmed) and ignores; `Exit` is only
 *    an exit INTENT — the confirm-exit interposer
 *    (`PlayerExitConfirmController.onBackPress`) owns whether it exits now
 *    or raises the overlay for the second BACK.
 *  - **The hide-edge focus handoff** ([focusHandoff]): when the controls
 *    hide, focus moves to the top-priority overlay that owns the screen —
 *    cinema intro, then the Up Next card, then the skip-segment button —
 *    and the plain-surface case defers to the TV focus-grab effect (its
 *    retry + re-assert is the recovery path; the handoff's single un-retried
 *    attempt loses races). Off-TV, a hardware-keyboard layer re-grabs so
 *    keys keep landing after the auto-hide.
 */
internal object PlayerBackFocusLadder {

    /** What the screen executes once the chrome module's ladder has ruled. */
    enum class BackAction {
        /** Fold the open modal away first (the sheet arm). */
        CloseSheet,
        /** Hide the chrome (the TV hide arm). */
        HideControls,
        /**
         * An exit intent only — the confirm-exit interposer owns whether the
         * screen exits now (pref off / confirm already up) or raises the
         * overlay for the second BACK.
         */
        ExitIntent,
        /**
         * The summon verdict, never armed on this screen's system handler
         * (its summon arm is off) — the press is ignored.
         */
        None,
    }

    /**
     * The system BACK handler's hide arm: TV-only. A touch BACK exits with
     * the controls up.
     */
    fun systemBackHideArm(isTv: Boolean): Boolean = isTv

    /**
     * The screen's dispatch of the chrome module's verdict — exactly the
     * former inline `when` in the root's back handler.
     */
    fun backAction(verdict: PlayerChromeBackVerdict): BackAction = when (verdict) {
        PlayerChromeBackVerdict.CloseSheet -> BackAction.CloseSheet
        PlayerChromeBackVerdict.HideControls -> BackAction.HideControls
        PlayerChromeBackVerdict.SummonControls -> BackAction.None
        PlayerChromeBackVerdict.Exit -> BackAction.ExitIntent
    }

    /** Where the hide-edge focus handoff sends focus. */
    enum class FocusHandoff {
        /** The cinema-intro overlay's skip control. */
        CinemaIntro,
        /** The Up Next card's play control. */
        NextEpisode,
        /** The skip-segment button. */
        SkipSegment,
        /**
         * No overlay is up — the plain-surface case, deferred to the TV
         * focus-grab effect (retry + re-assert is the recovery path there).
         */
        DeferToGrabEffect,
        /** Off-TV: the hardware-keyboard layer re-grabs after the auto-hide. */
        KeyboardRegrab,
        /** Controls visible — no handoff (the grab arms own the focus). */
        None,
    }

    /**
     * The hide-edge handoff ladder — exactly the former inline arms in the
     * root's `LaunchedEffect(chrome.visible, …)`: on TV the overlay-priority
     * chain (intro > up-next > skip-segment, else the grab effect), off-TV
     * the keyboard re-grab, and a visible chrome means no handoff at all.
     */
    fun focusHandoff(
        isTv: Boolean,
        hasHardwareKeyboard: Boolean,
        controlsVisible: Boolean,
        cinemaIntroVisible: Boolean,
        nextEpisodeVisible: Boolean,
        skipSegmentVisible: Boolean,
    ): FocusHandoff = when {
        controlsVisible -> FocusHandoff.None
        isTv -> when {
            cinemaIntroVisible -> FocusHandoff.CinemaIntro
            nextEpisodeVisible -> FocusHandoff.NextEpisode
            skipSegmentVisible -> FocusHandoff.SkipSegment
            else -> FocusHandoff.DeferToGrabEffect
        }
        hasHardwareKeyboard -> FocusHandoff.KeyboardRegrab
        else -> FocusHandoff.None
    }
}
