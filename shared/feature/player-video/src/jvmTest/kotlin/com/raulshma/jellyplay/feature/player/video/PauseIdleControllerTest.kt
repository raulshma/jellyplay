package com.raulshma.jellyplay.feature.player.video

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the paused-idle screensaver's state machine (#11,
 * [PauseIdleController]): arm-on-pause / disarm-on-resume, the fire-at-budget
 * tick fold over the INJECTED clock, and the "interaction dismisses and
 * resets" rule — including the still-paused re-arm and the quiet-gate
 * (sheet/lock/PiP) churn.
 */
class PauseIdleControllerTest {

    private class Harness(
        var gateOpen: Boolean = true,
        var budgetMs: Long = 5 * 60_000L,
    ) {
        var nowMs: Long = 1_000L

        val controller = PauseIdleController(
            isArmingEnabled = { gateOpen },
            idleTimeoutMs = { budgetMs },
            nowMs = { nowMs },
        )
    }

    @Test
    fun pauseArms_firesOnlyAtTheBudget() {
        val h = Harness()
        h.controller.onPlaybackStateChanged(isPlaying = false)

        h.nowMs += h.budgetMs - 1
        h.controller.tick()
        assertFalse(h.controller.isActive.value, "the overlay must not fire before the budget elapses")

        h.nowMs += 1 // exactly at the budget
        h.controller.tick()
        assertTrue(h.controller.isActive.value, "the overlay fires at (not after) the budget")
    }

    @Test
    fun resumeDisarmsInstantly_evenWhileActive() {
        val h = Harness()
        h.controller.onPlaybackStateChanged(isPlaying = false)
        h.nowMs += h.budgetMs
        h.controller.tick()
        assertTrue(h.controller.isActive.value)

        h.controller.onPlaybackStateChanged(isPlaying = true)
        assertFalse(h.controller.isActive.value, "play resumption dismisses the overlay instantly")
        // And the disarmed state survives ticks (the loop parks while playing,
        // but a straggler tick must not re-fire from the stale arm).
        h.nowMs += h.budgetMs
        h.controller.tick()
        assertFalse(h.controller.isActive.value)
    }

    @Test
    fun interactionWhileArmed_resetsTheClock() {
        val h = Harness()
        h.controller.onPlaybackStateChanged(isPlaying = false)
        val originalDeadline = h.nowMs + h.budgetMs

        h.nowMs = originalDeadline - 1_000
        h.controller.onUserInteraction() // resets: the new deadline is now+budget
        h.nowMs = originalDeadline
        h.controller.tick()
        assertFalse(h.controller.isActive.value, "a reset clock must not fire at the OLD deadline")

        h.nowMs += h.budgetMs
        h.controller.tick()
        assertTrue(h.controller.isActive.value, "…and fires at the NEW deadline")
    }

    @Test
    fun interactionWhileActive_dismissesInstantlyAndResets() {
        val h = Harness()
        h.controller.onPlaybackStateChanged(isPlaying = false)
        h.nowMs += h.budgetMs
        h.controller.tick()
        assertTrue(h.controller.isActive.value)

        h.controller.onUserInteraction()
        assertFalse(h.controller.isActive.value, "an interaction dismisses the overlay instantly")
        // Still paused → re-armed for another full budget.
        h.nowMs += h.budgetMs - 1
        h.controller.tick()
        assertFalse(h.controller.isActive.value)
        h.nowMs += 1
        h.controller.tick()
        assertTrue(h.controller.isActive.value)
    }

    @Test
    fun interactionWhilePlaying_isANoOp() {
        val h = Harness()
        h.controller.onUserInteraction()
        h.controller.onPlaybackStateChanged(isPlaying = true)
        h.controller.onUserInteraction()
        assertFalse(h.controller.isActive.value)

        // The next pause arms from the PAUSE edge, not from any interaction.
        h.nowMs += h.budgetMs
        h.controller.onPlaybackStateChanged(isPlaying = false)
        h.nowMs += h.budgetMs - 1
        h.controller.tick()
        assertFalse(h.controller.isActive.value)
        h.nowMs += 1
        h.controller.tick()
        assertTrue(h.controller.isActive.value)
    }

    @Test
    fun aPauseEdgeWhileArmed_reArmsTheClock() {
        val h = Harness()
        h.controller.onPlaybackStateChanged(isPlaying = false)
        h.nowMs += h.budgetMs - 1_000
        // A pause/pause echo (engine re-report) restarts the clock.
        h.controller.onPlaybackStateChanged(isPlaying = false)
        h.nowMs += 1_000
        h.controller.tick()
        assertFalse(h.controller.isActive.value, "the re-arm must have restarted the budget")

        h.nowMs += h.budgetMs
        h.controller.tick()
        assertTrue(h.controller.isActive.value)
    }

    @Test
    fun aClosedGate_neverArms_andClearsAnActiveOverlay() {
        val h = Harness()
        // Gated off before the pause: arming is refused.
        h.gateOpen = false
        h.controller.onPlaybackStateChanged(isPlaying = false)
        h.nowMs += h.budgetMs * 10
        h.controller.tick()
        assertFalse(h.controller.isActive.value, "mode OFF / sheet open / locked / PiP never arms")

        // An overlay that fired before the gate closed clears on the next tick.
        h.gateOpen = true
        h.controller.disarm()
        h.controller.onPlaybackStateChanged(isPlaying = false)
        h.nowMs += h.budgetMs
        h.controller.tick()
        assertTrue(h.controller.isActive.value)
        h.gateOpen = false // the sheet opened over the active overlay
        h.controller.tick()
        assertFalse(h.controller.isActive.value, "the tick clears the overlay once the gate goes dark")
    }

    @Test
    fun readsTheBudgetLive_aSettingsChangeAppliesWithoutARebuild() {
        val h = Harness()
        h.controller.onPlaybackStateChanged(isPlaying = false)
        h.budgetMs = 60_000L // the user shortened the pref mid-pause
        h.nowMs += 60_000L
        h.controller.tick()
        assertTrue(h.controller.isActive.value, "the live budget read applies immediately")
    }

    @Test
    fun disarm_clearsEverything() {
        val h = Harness()
        h.controller.onPlaybackStateChanged(isPlaying = false)
        h.nowMs += h.budgetMs
        h.controller.tick()
        assertTrue(h.controller.isActive.value)

        h.controller.disarm()
        assertFalse(h.controller.isActive.value)
        h.nowMs += h.budgetMs
        h.controller.tick()
        assertFalse(h.controller.isActive.value, "a disarmed controller stays down across ticks")
    }

    @Test
    fun zeroBudget_firesOnTheFirstTick() {
        val h = Harness(budgetMs = 0L)
        h.controller.onPlaybackStateChanged(isPlaying = false)
        h.controller.tick()
        assertTrue(h.controller.isActive.value, "a degenerate budget must not wedge the arm")
    }
}
