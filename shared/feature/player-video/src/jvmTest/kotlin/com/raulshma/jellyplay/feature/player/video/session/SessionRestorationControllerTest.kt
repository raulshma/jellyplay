package com.raulshma.jellyplay.feature.player.video.session

import com.raulshma.jellyplay.core.model.SessionRestoreMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest

/**
 * Pins the controller's latch semantics ([SessionRestorationController]): ONE
 * evaluation per process (the restore-loop guard), the ask funnel's exactly-once
 * consume, and the fetch-failure degradation (null + no throw).
 */
class SessionRestorationControllerTest {

    private val fresh = SessionRestorationSnapshot(
        itemId = "item-1",
        title = "Episode 1",
        positionMs = 600_000L,
        timestampMs = 1_800_000_000_000L - 60_000L,
    )

    private class SeamCounter(var snapshot: SessionRestorationSnapshot?) {
        var calls = 0
        suspend fun fetch(): SessionRestorationSnapshot? {
            calls++
            return snapshot
        }
    }

    private fun controller(
        mode: SessionRestoreMode = SessionRestoreMode.ASK,
        seam: SeamCounter,
        nowMs: Long = 1_800_000_000_000L,
    ) = SessionRestorationController(
        restoreMode = { mode },
        fetchLatestSnapshot = seam::fetch,
        nowMs = { nowMs },
    )

    // ── the one-shot latch ───────────────────────────────────────────────

    @Test
    fun evaluate_asksOnceAndParksTheSnapshot() = runTest {
        val seam = SeamCounter(fresh)
        val controller = controller(seam = seam)

        val decision = controller.evaluate(LaunchContext.ColdStart)

        assertEquals(SessionRestorationDecision.Ask(fresh), decision)
        assertEquals(1, seam.calls, "exactly one fetch")
        assertEquals(fresh, controller.consumeAsk(), "the ask snapshot is parked for the dialog host")
        assertNull(controller.consumeAsk(), "consumeAsk takes it exactly once")
    }

    @Test
    fun evaluate_latchesOnItsFirstOutcome_regardlessOfDecision() = runTest {
        val seam = SeamCounter(fresh)
        val controller = controller(mode = SessionRestoreMode.OFF, seam = seam)

        assertEquals(SessionRestorationDecision.None, controller.evaluate(LaunchContext.ColdStart))
        assertEquals(SessionRestorationDecision.None, controller.evaluate(LaunchContext.ColdStart))
        assertEquals(1, seam.calls, "no re-fetch after the latch fired")
    }

    @Test
    fun evaluate_afterAnAsk_neverReEvaluates() = runTest {
        val seam = SeamCounter(fresh)
        val controller = controller(seam = seam)

        assertEquals(SessionRestorationDecision.Ask(fresh), controller.evaluate(LaunchContext.ColdStart))
        assertEquals(SessionRestorationDecision.None, controller.evaluate(LaunchContext.ColdStart))
        assertEquals(1, seam.calls)
    }

    @Test
    fun evaluate_afterOnRestoreHandled_staysLatched() = runTest {
        val seam = SeamCounter(fresh)
        val controller = controller(seam = seam)

        controller.evaluate(LaunchContext.ColdStart)
        controller.onRestoreHandled()
        assertEquals(SessionRestorationDecision.None, controller.evaluate(LaunchContext.ColdStart))
        assertNull(controller.consumeAsk(), "the ack cleared the parked ask")
        assertEquals(1, seam.calls)
    }

    @Test
    fun explicitLaunchContext_noneWithoutTouchingTheLatchPathTwice() = runTest {
        // A deep-link launch consumes the process's one shot with a None — the
        // shell must not re-ask later (the sign-out → sign-in recomposition).
        val seam = SeamCounter(fresh)
        val controller = controller(seam = seam)

        assertEquals(SessionRestorationDecision.None, controller.evaluate(LaunchContext.DeepLink))
        assertEquals(SessionRestorationDecision.None, controller.evaluate(LaunchContext.ColdStart))
        assertEquals(1, seam.calls)
        assertNull(controller.consumeAsk())
    }

    // ── the AUTO arm + the ask funnel's absence there ────────────────────

    @Test
    fun evaluate_autoRestoresAndParksNothing() = runTest {
        val seam = SeamCounter(fresh)
        val controller = controller(mode = SessionRestoreMode.AUTO, seam = seam)

        assertEquals(SessionRestorationDecision.AutoRestore(fresh), controller.evaluate(LaunchContext.ColdStart))
        assertNull(controller.consumeAsk(), "an auto-restore parks no ask snapshot")
        controller.onRestoreHandled() // the shell's ack — a safe no-op
    }

    @Test
    fun onRestoreHandled_clearsAParkedAsk() = runTest {
        val seam = SeamCounter(fresh)
        val controller = controller(seam = seam)

        controller.evaluate(LaunchContext.ColdStart)
        assertTrue(controller.consumeAsk() != null)
        // Re-park is impossible (the latch), but the ack stays a safe clear.
        controller.onRestoreHandled()
        assertNull(controller.consumeAsk())
    }

    // ── fetch failures degrade, never throw ──────────────────────────────

    @Test
    fun evaluate_fetchFailure_degradesToNone() = runTest {
        val controller = SessionRestorationController(
            restoreMode = { SessionRestoreMode.ASK },
            fetchLatestSnapshot = { throw IllegalStateException("server unreachable") },
            nowMs = { 1_800_000_000_000L },
        )

        assertEquals(SessionRestorationDecision.None, controller.evaluate(LaunchContext.ColdStart))
    }

    @Test
    fun evaluate_cancellationPropagates() = runTest {
        val controller = SessionRestorationController(
            restoreMode = { SessionRestoreMode.ASK },
            fetchLatestSnapshot = { throw CancellationException("composition gone") },
            nowMs = { 1_800_000_000_000L },
        )

        assertFailsWith<CancellationException> { controller.evaluate(LaunchContext.ColdStart) }
    }

    // ── the injected clock drives the staleness rule ─────────────────────

    @Test
    fun evaluate_stalenessUsesTheInjectedClock() = runTest {
        // Fresh against the injected now; stale two hours later — but the latch
        // means only the FIRST read matters, so pin both through two controllers.
        val seam = SeamCounter(fresh)
        val freshController = controller(seam = seam, nowMs = 1_800_000_000_000L)
        assertEquals(SessionRestorationDecision.Ask(fresh), freshController.evaluate(LaunchContext.ColdStart))

        val staleController = controller(seam = seam, nowMs = 1_800_000_000_000L + 2 * 60 * 60 * 1000L)
        assertEquals(SessionRestorationDecision.None, staleController.evaluate(LaunchContext.ColdStart))
    }
}
