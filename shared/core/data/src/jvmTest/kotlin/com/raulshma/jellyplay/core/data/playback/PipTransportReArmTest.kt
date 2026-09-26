package com.raulshma.jellyplay.core.data.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the two PiP transport/discharge chores both player hosts share: the
 * [reArmPipTransport] re-arm + assignment mechanics and the
 * [dischargePipDismissal] collector ordering (pause → teardown → close → the
 * defensive latch clear — the issue-#145 choreography).
 */
class PipTransportReArmTest {

    private lateinit var pip: FakePipController

    @BeforeTest
    fun setUp() {
        pip = FakePipController()
    }

    @Test
    fun nullControllerIsANoOp() {
        // Live's platform-conditional seam: platforms without PiP bind null.
        // Must neither throw nor invoke the handler.
        reArmPipTransport(null) { error("handler must not run without a controller") }
    }

    @Test
    fun assignsTransportOnController() {
        reArmPipTransport(pip) {}
        assertNotNull(pip.pipTransport)
    }

    @Test
    fun dispatchedActionsReachTheHostHandler() {
        val received = mutableListOf<PipAction>()
        reArmPipTransport(pip) { received.add(it) }

        PipAction.entries.forEach { pip.pipTransport?.handle(it) }

        assertEquals(PipAction.entries.toList(), received)
    }

    @Test
    fun reArmOverwritesThePriorTransport() {
        val first = mutableListOf<PipAction>()
        reArmPipTransport(pip) { first.add(it) }
        val staleTransport = pip.pipTransport

        val second = mutableListOf<PipAction>()
        reArmPipTransport(pip) { second.add(it) }

        // The controller now routes through the second handler only.
        pip.pipTransport?.handle(PipAction.PLAY)
        assertEquals(listOf(PipAction.PLAY), second)
        assertTrue(first.isEmpty())
        // The displaced transport is detached, not still reachable.
        assertNotNull(staleTransport)
        assertTrue(staleTransport !== pip.pipTransport)
    }

    @Test
    fun resetDetachesTransportUntilReArmed() {
        reArmPipTransport(pip) {}
        assertNotNull(pip.pipTransport)

        pip.reset()

        assertNull(pip.pipTransport)
        reArmPipTransport(pip) {}
        assertNotNull(pip.pipTransport)
    }

    // ── dischargePipDismissal ─────────────────────────────────────────────────

    @Test
    fun dischargeRunsTeardownThenCloseThenTheDefensiveClearInOrder() = runTest {
        val steps = mutableListOf<String>()
        val job = dischargePipDismissal(
            pip,
            // The hosts' teardown lists put the engine pause first — mirror
            // that here so the pin covers the full pause→teardown→close→clear
            // ordering the discharge exists to enforce.
            teardown = {
                steps.add("pause")
                steps.add("teardown")
            },
            close = { steps.add("close") },
        )

        pip.dismissedFlow.value = true
        runCurrent()
        job.cancel()

        assertEquals(
            listOf("pause", "teardown", "close"),
            steps,
            "the discharge order is pause/teardown → close, then the clear",
        )
        // The defensive clear is the fake's own counter (the latch reset is
        // controller state, not a host step) — the order pin is steps+counter.
        assertEquals(1, pip.clearCalls, "the discharge ends with exactly one defensive clear")
    }

    @Test
    fun dischargeClearsTheLatchEvenWhenCloseReleasesEarly() = runTest {
        // The issue-#145 shape: close()'s release() early-returns on its
        // idempotence latch, so the normal teardown path (PipController.reset)
        // never runs and the flag would stay set on the process singleton.
        // The helper's own clear must fire regardless.
        val job = dischargePipDismissal(
            pip,
            teardown = {},
            close = { /* a latched release: clears nothing */ },
        )

        pip.dismissedFlow.value = true
        runCurrent()
        job.cancel()

        assertEquals(1, pip.clearCalls)
        assertFalse(pip.dismissedFlow.value, "the stuck latch must not survive the discharge")
    }

    @Test
    fun dischargeIgnoresFalseEmissions() = runTest {
        val steps = mutableListOf<String>()
        val job = dischargePipDismissal(pip, teardown = { steps.add("teardown") }, close = { steps.add("close") })

        runCurrent()
        pip.dismissedFlow.value = false // a redundant false must not discharge either
        runCurrent()
        job.cancel()

        assertTrue(steps.isEmpty())
        assertEquals(0, pip.clearCalls)
    }

    @Test
    fun dischargeWithoutAControllerIsANoOp() = runTest {
        // Live's platform-conditional seam: platforms without PiP bind null.
        val job = dischargePipDismissal(
            null,
            teardown = { error("teardown must not run without a controller") },
            close = { error("close must not run without a controller") },
        )
        runCurrent()
        job.cancel()
    }

    @Test
    fun dischargeReactsToALatchAlreadyTrueAtLaunch() = runTest {
        // Plain StateFlow collect semantics are deliberate: the hosts clear
        // the latch defensively at initialize-time, so a true seen at
        // collection start is a live dismissal (the collector must not
        // swallow it — see the helper's KDoc).
        pip.dismissedFlow.value = true
        val discharged = mutableListOf<String>()
        val job = dischargePipDismissal(pip, teardown = { discharged.add("teardown") }, close = { discharged.add("close") })

        runCurrent()
        job.cancel()

        assertEquals(listOf("teardown", "close"), discharged)
    }

    @Test
    fun dischargeIsOneShotPerDismissalNotOneShotEver() = runTest {
        val steps = mutableListOf<String>()
        val job = dischargePipDismissal(pip, teardown = { steps.add("teardown") }, close = { steps.add("close") })

        pip.dismissedFlow.value = true
        runCurrent()
        // The helper's clear dropped the flag back to false; a NEW dismissal
        // (the next PiP window's auto-exit) must discharge again.
        pip.dismissedFlow.value = true
        runCurrent()
        job.cancel()

        assertEquals(listOf("teardown", "close", "teardown", "close"), steps)
        assertEquals(2, pip.clearCalls)
    }

    /**
     * Minimal [PipController] stand-in: the transport seam for the re-arm
     * pins, plus a realistic one-shot dismissal latch for the discharge pins
     * ([clearPipDismissed] drops the flag, exactly as the production
     * controller does).
     */
    private class FakePipController : PipController {
        val dismissedFlow = MutableStateFlow(false)
        override val isInPipMode: StateFlow<Boolean> = MutableStateFlow(false)
        override val pipDismissed: StateFlow<Boolean> = dismissedFlow
        override var pipTransport: PipTransport? = null
        override var pipHasNext: Boolean = false
        var clearCalls: Int = 0
        override fun setPlaying(playing: Boolean) = Unit
        override fun setControlsLocked(locked: Boolean) = Unit
        override fun requestAutoEnterPip(shouldEnter: Boolean) = Unit
        override fun requestAutoExitPip() = Unit
        override fun consumeAutoExitPip() = Unit
        override fun clearPipDismissed() {
            clearCalls++
            dismissedFlow.value = false
        }
        override fun setPipAspectRatio(aspect: Pair<Int, Int>?) = Unit
        override fun updatePipSourceRect(left: Int, top: Int, right: Int, bottom: Int) = Unit
        override fun reset() {
            pipTransport = null
        }
    }
}
