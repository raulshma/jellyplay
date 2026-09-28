package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.StillWatchingMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the "Still watching?" gate (feature 1.3) across its full truth table —
 * mode × threshold × SyncPlay × interaction — composed exactly the way the
 * ViewModel composes it: [StillWatchingGate.shouldPrompt] over
 * `AutoPlayController.needsStillWatchingCheck()` (the threshold lives on the
 * controller; the mode + SyncPlay mirror are the VM's reads). Also pins the
 * hours-arm upgrade verdict and the overlay prompt state machine
 * ([StillWatchingPromptState]).
 */
class StillWatchingGateTest {

    /** The VM's gate call, verbatim shape. */
    private fun gate(
        controller: AutoPlayController,
        mode: StillWatchingMode,
        isInSyncPlaySession: Boolean = false,
    ): Boolean = StillWatchingGate.shouldPrompt(
        mode = mode,
        episodeCheck = controller.needsStillWatchingCheck(),
        isInSyncPlaySession = isInSyncPlaySession,
    )

    private fun controllerAtThreshold(threshold: Int, autoPlays: Int): AutoPlayController =
        AutoPlayController().apply {
            setStillWatchingThreshold(threshold)
            repeat(autoPlays) { recordAutoAdvance() }
        }

    // ── The gate truth table: mode × threshold ─────────────────────────────

    @Test
    fun gate_modeOff_neverFires() {
        // Even a long streak at an armed threshold stays silent.
        val c = controllerAtThreshold(threshold = 3, autoPlays = 10)
        assertFalse(gate(c, StillWatchingMode.OFF), "OFF must never prompt")
    }

    @Test
    fun gate_thresholdZeroEqualsOff() {
        // The armed modes with the threshold at 0 (the default): no check.
        val c = controllerAtThreshold(threshold = 0, autoPlays = 10)
        assertFalse(gate(c, StillWatchingMode.EPISODES))
        assertFalse(gate(c, StillWatchingMode.BOTH))
    }

    @Test
    fun gate_episodeModes_fireAtAndBeyondThreshold() {
        for (mode in listOf(StillWatchingMode.EPISODES, StillWatchingMode.BOTH)) {
            val below = controllerAtThreshold(threshold = 3, autoPlays = 2)
            assertFalse(gate(below, mode), "$mode: below the threshold must not prompt")
            val at = controllerAtThreshold(threshold = 3, autoPlays = 3)
            assertTrue(gate(at, mode), "$mode: at the threshold must prompt")
            val beyond = controllerAtThreshold(threshold = 3, autoPlays = 5)
            assertTrue(gate(beyond, mode), "$mode: beyond the threshold must prompt")
        }
    }

    // ── The gate truth table: SyncPlay (group pacing wins) ──────────────────

    @Test
    fun gate_syncPlaySuppressesTheEpisodeArm() {
        val c = controllerAtThreshold(threshold = 2, autoPlays = 10)
        assertFalse(gate(c, StillWatchingMode.EPISODES, isInSyncPlaySession = true))
        assertFalse(gate(c, StillWatchingMode.BOTH, isInSyncPlaySession = true))
        // The hours-only mode has no episode arm either way.
        assertFalse(gate(c, StillWatchingMode.HOURS, isInSyncPlaySession = true))
    }

    // ── The gate truth table: interaction resets ────────────────────────────

    @Test
    fun gate_interactionResetsTheStreakBackBelowThreshold() {
        val c = controllerAtThreshold(threshold = 3, autoPlays = 5)
        assertTrue(gate(c, StillWatchingMode.EPISODES))

        // A user-driven signal (open, navigation, play/pause/seek/speed, tap)
        // resets the streak — the same signal the pass-out clock consumes.
        c.onUserInteraction()
        assertFalse(gate(c, StillWatchingMode.EPISODES))
        assertFalse(gate(c, StillWatchingMode.BOTH))
    }

    @Test
    fun gate_continueAnswerReArmsTheCycle() {
        // The Continue arm resets the counter before advancing, so the next
        // prompt needs another full unattended run.
        val c = controllerAtThreshold(threshold = 2, autoPlays = 2)
        assertTrue(gate(c, StillWatchingMode.EPISODES))
        c.onUserInteraction() // the Continue arm's reset
        c.recordAutoAdvance()
        assertFalse(gate(c, StillWatchingMode.EPISODES))
        c.recordAutoAdvance()
        assertTrue(gate(c, StillWatchingMode.EPISODES))
    }

    // ── The hours arm: pass-out upgrade verdict ─────────────────────────────

    @Test
    fun passOutUpgrade_onlyWhenModeIncludesHours() {
        assertFalse(StillWatchingGate.upgradesPassOutToOverlay(StillWatchingMode.OFF))
        assertFalse(StillWatchingGate.upgradesPassOutToOverlay(StillWatchingMode.EPISODES))
        assertTrue(StillWatchingGate.upgradesPassOutToOverlay(StillWatchingMode.HOURS))
        assertTrue(StillWatchingGate.upgradesPassOutToOverlay(StillWatchingMode.BOTH))
    }

    // ── The overlay prompt state machine ─────────────────────────────────────

    @Test
    fun promptState_ticksDownToExpiry() {
        val prompt = StillWatchingPromptState.forReason(StillWatchingReason.EPISODE_COUNT, countdownSeconds = 3)
        assertEquals(2, prompt.tick()?.countdownSeconds)
        assertEquals(1, prompt.tick()?.tick()?.countdownSeconds)
        assertEquals(0, prompt.tick()?.tick()?.tick()?.countdownSeconds)
        // Expiry — the caller treats null as the Stop action.
        assertNull(prompt.tick()?.tick()?.tick()?.tick())
    }

    @Test
    fun promptState_zeroBudgetCollapsesToImmediateExpiry() {
        val prompt = StillWatchingPromptState.forReason(StillWatchingReason.HOURS_IDLE, countdownSeconds = 0)
        assertNull(prompt.tick(), "a zero budget expires on the first tick — auto-Stop")
    }

    @Test
    fun promptState_negativeBudgetClampsToZero() {
        val prompt = StillWatchingPromptState.forReason(StillWatchingReason.EPISODE_COUNT, countdownSeconds = -5)
        assertEquals(0, prompt.countdownSeconds)
        assertNull(prompt.tick())
    }

    @Test
    fun promptState_carriesTheReasonForTheContinueArm() {
        assertEquals(
            StillWatchingReason.EPISODE_COUNT,
            StillWatchingPromptState.forReason(StillWatchingReason.EPISODE_COUNT, 10).reason,
        )
        assertEquals(
            StillWatchingReason.HOURS_IDLE,
            StillWatchingPromptState.forReason(StillWatchingReason.HOURS_IDLE, 10).reason,
        )
    }
}
