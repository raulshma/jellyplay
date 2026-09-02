package com.raulshma.jellyplay.feature.player.video

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The desktop software-render pane's redraw trigger policy (wave 12B), pinned
 * as a decision table: [DesktopSoftwareVideoPane]'s LaunchedEffect delegates
 * every loop step to [SwPaneTicker.plan], so these four rows ARE the behavior.
 *
 * Regression rows: pause must keep polling (slowly) so seek-while-paused
 * repaints — the pre-review code suspended until play resumed and froze a
 * stale frame under a moving progress bar; fully-idle sessions must poll ZERO
 * (suspend), preserving the "no busy tick when idle/unloaded" efficiency rule.
 */
class SwPaneTickerTest {

    @Test
    fun playing_pollsAtPlaybackCadence() {
        val plan = SwPaneTicker.plan(isPlaying = true, unloaded = false)
        assertIs<SwPaneTicker.Poll>(plan)
        assertEquals(SwPaneTicker.PULL_INTERVAL_MS, plan.intervalMs)
    }

    @Test
    fun playingButNotYetLoaded_stillPolls() {
        // Transient corner: isPlaying flipping true before the READY/IDLE flow
        // settles. Video work wins over idleness — pulling drops cleanly when
        // nothing is decoded yet.
        val plan = SwPaneTicker.plan(isPlaying = true, unloaded = true)
        assertIs<SwPaneTicker.Poll>(plan)
        assertEquals(SwPaneTicker.PULL_INTERVAL_MS, plan.intervalMs)
    }

    @Test
    fun loadedAndPaused_keepsSlowWatchdog() {
        val plan = SwPaneTicker.plan(isPlaying = false, unloaded = false)
        assertIs<SwPaneTicker.Poll>(plan)
        assertEquals(SwPaneTicker.PAUSED_POLL_INTERVAL_MS, plan.intervalMs)
        assertTrue(
            plan.intervalMs > SwPaneTicker.PULL_INTERVAL_MS,
            "paused watchdog must be cheaper than the playing ticker",
        )
    }

    @Test
    fun idleAndPaused_suspendsWithZeroPolling() {
        assertIs<SwPaneTicker.Suspend>(SwPaneTicker.plan(isPlaying = false, unloaded = true))
    }
}
