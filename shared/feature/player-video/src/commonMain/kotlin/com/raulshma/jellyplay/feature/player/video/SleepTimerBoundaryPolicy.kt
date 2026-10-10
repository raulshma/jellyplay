package com.raulshma.jellyplay.feature.player.video

/**
 * The end-of-episode sleep-timer × auto-advance precedence — pure, so
 * the truth table is unit-testable without the ViewModel.
 *
 * The VM's `handlePlaybackEnded` consults this BEFORE the auto-advance
 * branch ([AutoPlayController.shouldAutoPlayNext] and, ahead of it, the
 * [StillWatchingGate] prompt): when a sleep timer is armed in end-of-episode
 * mode, the user asked to STOP at this episode's end — the timer wins, the
 * VM triggers it (pause/wind-down per [SleepTimerController] semantics) and
 * the episode does not advance. A timer armed in timed mode does NOT
 * intercept: it deliberately persists across episodes.
 *
 * Deliberate user actions keep outranking it: an explicit skip-credits
 * press (`AutoPlayController.canSkipToNext`) runs after the intercept and
 * advances regardless — the same "the user took a deliberate action"
 * reasoning as the cancelled-countdown rule.
 */
internal object SleepTimerBoundaryPolicy {

    /**
     * True when the episode-boundary moment must hand over to the sleep
     * timer instead of auto-advancing: the timer is active AND armed in
     * end-of-episode mode.
     */
    fun sleepTimerWinsOverAutoAdvance(active: Boolean, endOfEpisode: Boolean): Boolean =
        active && endOfEpisode
}
