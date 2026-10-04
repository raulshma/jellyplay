package com.raulshma.jellyplay.feature.player.video

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins [SleepTimerBoundaryPolicy] — the end-of-episode sleep-timer ×
 * auto-advance precedence truth table the VM's `handlePlaybackEnded`
 * consults before the advance branch (ahead of the still-watching gate).
 * Same pure style as [AutoPlayControllerTest] / the StillWatchingGate pins.
 */
class SleepTimerBoundaryPolicyTest {

    @Test
    fun endOfEpisodeArm_winsOverAutoAdvance() {
        assertTrue(
            SleepTimerBoundaryPolicy.sleepTimerWinsOverAutoAdvance(active = true, endOfEpisode = true),
        )
    }

    @Test
    fun timedArm_doesNotIntercept_persistsAcrossEpisodes() {
        // A running timed timer deliberately PERSISTS across episodes — the
        // advance proceeds under it.
        assertFalse(
            SleepTimerBoundaryPolicy.sleepTimerWinsOverAutoAdvance(active = true, endOfEpisode = false),
        )
    }

    @Test
    fun inactiveOrIdleTimer_doesNotIntercept() {
        assertFalse(
            SleepTimerBoundaryPolicy.sleepTimerWinsOverAutoAdvance(active = false, endOfEpisode = true),
        )
        assertFalse(
            SleepTimerBoundaryPolicy.sleepTimerWinsOverAutoAdvance(active = false, endOfEpisode = false),
        )
    }
}
