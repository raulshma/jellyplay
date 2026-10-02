package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

/**
 * Pins [AutoPlayController] — the auto-advance state machine extracted from
 * `VideoPlayerViewModel`. Pure logic, no Android.
 */
class AutoPlayControllerTest {

    private val nextEpisode = MediaItem(id = "next", name = "Next", mediaType = MediaType.EPISODE)

    @Test
    fun defaults_disabledAndNotCancelled() {
        val c = AutoPlayController()
        assertFalse(c.enabled)
        assertFalse(c.cancelled)
    }

    @Test
    fun shouldAutoPlayNext_disabledByDefault() {
        val c = AutoPlayController()
        assertFalse(c.shouldAutoPlayNext(nextEpisode))
    }

    @Test
    fun shouldAutoPlayNext_requiresEpisodeEnabledAndNotCancelled() {
        val c = AutoPlayController()
        assertFalse(c.shouldAutoPlayNext(null))

        c.setEnabled(true)
        assertTrue(c.shouldAutoPlayNext(nextEpisode))

        c.cancel()
        assertFalse(c.shouldAutoPlayNext(nextEpisode))
    }

    @Test
    fun resetForNewItem_reArmsCountdown() {
        val c = AutoPlayController()
        c.setEnabled(true)
        c.cancel()
        assertFalse(c.shouldAutoPlayNext(nextEpisode))

        c.resetForNewItem()
        assertTrue(c.shouldAutoPlayNext(nextEpisode))
    }

    @Test
    fun canSkipToNext_ignoresCancelledBecauseUserActedDeliberately() {
        val c = AutoPlayController()
        c.setEnabled(true)
        c.cancel()
        // Natural end is blocked by the dismissal…
        assertFalse(c.shouldAutoPlayNext(nextEpisode))
        // …but an explicit skip-credits press still advances.
        assertTrue(c.canSkipToNext(nextEpisode))
        assertFalse(c.canSkipToNext(null))
    }

    @Test
    fun canSkipToNext_requiresEnabled() {
        val c = AutoPlayController()
        assertFalse(c.canSkipToNext(nextEpisode))
        c.setEnabled(true)
        assertTrue(c.canSkipToNext(nextEpisode))
        c.setEnabled(false)
        assertFalse(c.canSkipToNext(nextEpisode))
    }

    @Test
    fun setEnabled_falseOnRelease() {
        val c = AutoPlayController()
        c.setEnabled(true)
        c.setEnabled(false)
        assertFalse(c.enabled)
        assertFalse(c.shouldAutoPlayNext(nextEpisode))
    }

    // ── Still-watching episode counter (feature 1.3) ─────────────────────

    @Test
    fun stillWatching_disabledAtZeroThreshold() {
        val c = AutoPlayController()
        c.setStillWatchingThreshold(0)
        repeat(10) { c.recordAutoAdvance() }
        // 0 = off: the streak grows but never triggers the check.
        assertFalse(c.needsStillWatchingCheck())
    }

    @Test
    fun stillWatching_firesOnlyWhenStreakReachesThreshold() {
        val c = AutoPlayController()
        c.setStillWatchingThreshold(3)
        assertFalse(c.needsStillWatchingCheck())
        c.recordAutoAdvance()
        c.recordAutoAdvance()
        assertFalse(c.needsStillWatchingCheck())
        c.recordAutoAdvance()
        assertTrue(c.needsStillWatchingCheck())
        // It stays fired — the gate fires until something user-driven resets.
        c.recordAutoAdvance()
        assertTrue(c.needsStillWatchingCheck())
    }

    @Test
    fun stillWatching_thresholdCoercedNonNegative() {
        val c = AutoPlayController()
        c.setStillWatchingThreshold(-2)
        repeat(5) { c.recordAutoAdvance() }
        assertFalse(c.needsStillWatchingCheck())
    }

    @Test
    fun stillWatching_userInteractionResetsStreak() {
        val c = AutoPlayController()
        c.setStillWatchingThreshold(2)
        c.recordAutoAdvance()
        c.recordAutoAdvance()
        assertTrue(c.needsStillWatchingCheck())

        c.onUserInteraction()
        assertFalse(c.needsStillWatchingCheck())
        // One more unattended advance is not enough again.
        c.recordAutoAdvance()
        assertFalse(c.needsStillWatchingCheck())
        c.recordAutoAdvance()
        assertTrue(c.needsStillWatchingCheck())
    }

    @Test
    fun stillWatching_autoAdvanceLoadDoesNotResetStreak() {
        // resetForNewItem runs on EVERY item load — including an auto-advance
        // — so the streak must live outside it; only user-driven signals
        // (onUserInteraction) reset.
        val c = AutoPlayController()
        c.setStillWatchingThreshold(2)
        c.recordAutoAdvance()
        c.recordAutoAdvance()
        c.resetForNewItem()
        assertTrue(c.needsStillWatchingCheck())
    }

    @Test
    fun stillWatching_reArmAfterContinueThenReset() {
        val c = AutoPlayController()
        c.setEnabled(true)
        c.setStillWatchingThreshold(2)
        c.recordAutoAdvance()
        c.recordAutoAdvance()
        assertTrue(c.needsStillWatchingCheck())

        // "Continue" resets the counter (the VM's Continue arm)…
        c.onUserInteraction()
        assertFalse(c.needsStillWatchingCheck())
        // …and "Stop" (cancel) doesn't disturb the counter — only the gate's
        // callers do. The next unattended pair fires again.
        c.cancel()
        c.resetForNewItem()
        assertFalse(c.needsStillWatchingCheck())
        c.recordAutoAdvance()
        c.recordAutoAdvance()
        assertTrue(c.needsStillWatchingCheck())
    }
}
