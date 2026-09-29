package com.raulshma.jellyplay.feature.player.video

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the "Still watching?" prompt LIFECYCLE controller (feature 1.3) — the
 * stateful half extracted from [VideoPlayerViewModel]; the pure gate/state
 * halves are pinned by [StillWatchingGateTest] (this suite composes against
 * the same [AutoPlayController] fixtures that gate suite uses, since the
 * Continue arm's interaction reset is what re-arms the gate's cycle).
 */
class StillWatchingControllerTest {

    /** Recording wiring for one controller under test. */
    private class Recording {
        var interactions = 0
        var nextEpisodes = 0
        var resumes = 0
        var pauses = 0
        var autoplayCancels = 0

        val controller = StillWatchingController(
            getCountdownSeconds = { 3 },
            onUserInteraction = { interactions += 1 },
            playNextEpisode = { nextEpisodes += 1 },
            resumePlayback = { resumes += 1 },
            pauseEngine = { pauses += 1 },
            cancelAutoplay = { autoplayCancels += 1 },
        )
    }

    @Test
    fun show_raisesThePromptWithTheConfiguredCountdown() {
        val r = Recording()
        assertNull(r.controller.prompt.value)
        r.controller.show(StillWatchingReason.EPISODE_COUNT)
        assertEquals(
            StillWatchingPromptState.forReason(StillWatchingReason.EPISODE_COUNT, countdownSeconds = 3),
            r.controller.prompt.value,
        )
    }

    @Test
    fun lifecycle_episodeArm_triggerThenContinueAdvancesAndResetsStreak() {
        val r = Recording()
        // The streak the gate consults — at threshold, so the gate fires.
        val autoplay = AutoPlayController().apply {
            setStillWatchingThreshold(2)
            repeat(2) { recordAutoAdvance() }
        }
        assertTrue(
            StillWatchingGate.shouldPrompt(
                mode = com.raulshma.jellyplay.core.model.StillWatchingMode.EPISODES,
                episodeCheck = autoplay.needsStillWatchingCheck(),
                isInSyncPlaySession = false,
            ),
        )

        r.controller.show(StillWatchingReason.EPISODE_COUNT)
        r.controller.onContinue()

        // Continue: prompt cleared, streak reset, episode arm advanced.
        assertNull(r.controller.prompt.value)
        assertEquals(1, r.interactions)
        assertEquals(1, r.nextEpisodes)
        assertEquals(0, r.resumes)
        assertEquals(0, r.pauses)
        assertEquals(0, r.autoplayCancels)
        // The Continue arm's reset re-arms the gate cycle (the gate suite's
        // re-arm pin, observed through the same controller).
        autoplay.onUserInteraction()
        autoplay.recordAutoAdvance()
        assertTrue(!autoplay.needsStillWatchingCheck())
    }

    @Test
    fun lifecycle_hoursArm_triggerThenContinueResumes() {
        val r = Recording()
        r.controller.show(StillWatchingReason.HOURS_IDLE)
        r.controller.onContinue()

        assertNull(r.controller.prompt.value)
        assertEquals(1, r.interactions)
        assertEquals(0, r.nextEpisodes)
        assertEquals(1, r.resumes)
        // Stop-side effects never fire on Continue.
        assertEquals(0, r.pauses)
        assertEquals(0, r.autoplayCancels)
    }

    @Test
    fun lifecycle_continueWithoutAPromptTakesTheHoursArm() {
        // Defensive: a stray Continue with no prompt rides the hours arm's
        // resume (the pre-extraction body's null arm).
        val r = Recording()
        r.controller.onContinue()
        assertEquals(1, r.interactions)
        assertEquals(1, r.resumes)
        assertEquals(0, r.nextEpisodes)
    }

    @Test
    fun lifecycle_triggerThenStop_pausesAndCancelsAutoplay() {
        val r = Recording()
        r.controller.show(StillWatchingReason.EPISODE_COUNT)
        r.controller.onStop()

        assertNull(r.controller.prompt.value)
        assertEquals(1, r.pauses)
        assertEquals(1, r.autoplayCancels)
        // Stop is not an interaction and advances nothing.
        assertEquals(0, r.interactions)
        assertEquals(0, r.nextEpisodes)
        assertEquals(0, r.resumes)
    }

    @Test
    fun lifecycle_tickCountsDown_andExpiryIsAStop() {
        val r = Recording()
        r.controller.show(StillWatchingReason.HOURS_IDLE)
        r.controller.onTick()
        assertEquals(2, r.controller.prompt.value?.countdownSeconds)
        r.controller.onTick()
        assertEquals(1, r.controller.prompt.value?.countdownSeconds)
        assertEquals(0, r.pauses)
        // The final tick expires → the Stop route.
        r.controller.onTick()
        assertEquals(0, r.controller.prompt.value?.countdownSeconds)
        r.controller.onTick()
        assertNull(r.controller.prompt.value)
        assertEquals(1, r.pauses)
        assertEquals(1, r.autoplayCancels)
    }

    @Test
    fun tickWithoutAPromptIsANoOp() {
        val r = Recording()
        r.controller.onTick()
        assertNull(r.controller.prompt.value)
        assertEquals(0, r.pauses)
        assertEquals(0, r.autoplayCancels)
    }

    @Test
    fun resetForItem_clearsARacingPrompt() {
        val r = Recording()
        r.controller.show(StillWatchingReason.EPISODE_COUNT)
        r.controller.resetForItem()
        assertNull(r.controller.prompt.value)
        // Clearing alone stops nothing — a racing load clears defensively,
        // the answer arms did not fire.
        assertEquals(0, r.pauses)
        assertEquals(0, r.autoplayCancels)
    }
}
