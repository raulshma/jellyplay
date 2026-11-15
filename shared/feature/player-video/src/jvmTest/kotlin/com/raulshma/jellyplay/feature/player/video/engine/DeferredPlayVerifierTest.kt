package com.raulshma.jellyplay.feature.player.video.engine

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the pure deferred-play verify machine ([DeferredPlayVerifier]) — the
 * decision half of LibVlcPlayerEngine's `pendingPlay` latch fix, the mpv
 * load-verify cluster's mirror — over virtual time: the injected clock reads
 * the test scheduler's virtual clock and every step advances it explicitly
 * ([advanceTimeBy]; nothing here suspends, so the repo's documented
 * `advanceUntilIdle` trap — it would fast-forward past the confirm window —
 * cannot arise). The androidMain engine wiring itself needs a native libVLC
 * and stays unpinned; the machine is the libVLC-free decision surface, the
 * VlcSlaveRegistration/EngineDurationFallback precedent.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeferredPlayVerifierTest {

    /** The window the engine pins (mpv's LOAD_CONFIRM_MS precedent). */
    private companion object {
        const val CONFIRM_MS = 3_000L
    }

    /** Verifier over the test scheduler's virtual clock + a seam recorder. */
    private class Harness(scope: TestScope, confirmMs: Long = CONFIRM_MS) {
        val scheduledDelays = mutableListOf<Long>()
        var cancelCalls = 0
        val verifier = DeferredPlayVerifier(
            confirmMs = confirmMs,
            nowMs = { scope.testScheduler.currentTime },
            scheduleTimeout = { scheduledDelays += it },
            cancelTimeout = { cancelCalls++ },
        )
    }

    @Test
    fun arm_schedulesTheTimeoutPostAtTheDocumentedDelay() = runTest {
        val harness = Harness(this)
        harness.verifier.arm()
        assertEquals(listOf(CONFIRM_MS), harness.scheduledDelays)
        assertTrue(harness.verifier.isArmed)
    }

    @Test
    fun unconsumedArm_timesOutExactlyAtTheDocumentedDelay() = runTest {
        val harness = Harness(this)
        harness.verifier.arm()
        advanceTimeBy(CONFIRM_MS - 1)
        // One ms short of the window: still armed, nothing to execute.
        assertEquals(DeferredPlayVerifier.Verdict.Quiet, harness.verifier.evaluate())
        assertTrue(harness.verifier.isArmed)
        advanceTimeBy(1)
        assertEquals(DeferredPlayVerifier.Verdict.TimedOut, harness.verifier.evaluate())
        assertFalse(harness.verifier.isArmed)
    }

    @Test
    fun consumeBeforeTheWindow_cancelsThePostAndLateTicksStayQuiet() = runTest {
        val harness = Harness(this)
        harness.verifier.arm()
        advanceTimeBy(2_999)
        harness.verifier.consume()
        assertEquals(1, harness.cancelCalls)
        assertFalse(harness.verifier.isArmed)
        // Even a stale post that somehow ran after the cancel reads Quiet —
        // the record is closed.
        advanceTimeBy(1_000)
        assertEquals(DeferredPlayVerifier.Verdict.Quiet, harness.verifier.evaluate())
    }

    @Test
    fun timeoutIsOneShot_aLateSecondTickIsQuiet() = runTest {
        val harness = Harness(this)
        harness.verifier.arm()
        advanceTimeBy(CONFIRM_MS + 500)
        assertEquals(DeferredPlayVerifier.Verdict.TimedOut, harness.verifier.evaluate())
        assertEquals(DeferredPlayVerifier.Verdict.Quiet, harness.verifier.evaluate())
        assertFalse(harness.verifier.isArmed)
    }

    @Test
    fun standDown_disarmsTheRecordWithoutConsumingIt() = runTest {
        val harness = Harness(this)
        harness.verifier.arm()
        advanceTimeBy(1_000)
        // The Event.Playing / Event.Stopped / Event.EncounteredError guard:
        // the record closes while the engine's latch (not modeled here)
        // survives by declaration.
        harness.verifier.standDown()
        advanceTimeBy(CONFIRM_MS)
        assertEquals(DeferredPlayVerifier.Verdict.Quiet, harness.verifier.evaluate())
        assertFalse(harness.verifier.isArmed)
    }

    @Test
    fun reset_cancelsTheOutstandingPost() = runTest {
        val harness = Harness(this)
        harness.verifier.arm()
        harness.verifier.reset()
        assertEquals(1, harness.cancelCalls)
        assertFalse(harness.verifier.isArmed)
    }

    @Test
    fun reArmAfterReset_timesOutAgainstTheNewAnchor() = runTest {
        val harness = Harness(this)
        harness.verifier.arm()
        harness.verifier.consume()
        // Next media: the engine's load() → resetForNextMedia() → arm() dance.
        harness.verifier.reset()
        advanceTimeBy(1_000)
        harness.verifier.arm()
        assertEquals(2, harness.scheduledDelays.size)
        advanceTimeBy(CONFIRM_MS - 1)
        assertEquals(DeferredPlayVerifier.Verdict.Quiet, harness.verifier.evaluate())
        advanceTimeBy(1)
        assertEquals(DeferredPlayVerifier.Verdict.TimedOut, harness.verifier.evaluate())
    }

    @Test
    fun reArmWhileArmed_reAnchorsAndAnEarlyStalePostIsQuiet() = runTest {
        val harness = Harness(this)
        harness.verifier.arm()
        advanceTimeBy(2_000)
        // Defensive re-anchor (the engine structurally re-arms through reset
        // only): the FIRST post is now early relative to the new anchor and
        // must read Quiet; the on-window post fires.
        harness.verifier.arm()
        assertEquals(2, harness.scheduledDelays.size)
        assertEquals(DeferredPlayVerifier.Verdict.Quiet, harness.verifier.evaluate())
        advanceTimeBy(CONFIRM_MS)
        assertEquals(DeferredPlayVerifier.Verdict.TimedOut, harness.verifier.evaluate())
    }

    @Test
    fun evaluateWithoutArm_isQuiet() = runTest {
        val harness = Harness(this)
        assertEquals(DeferredPlayVerifier.Verdict.Quiet, harness.verifier.evaluate())
        assertFalse(harness.verifier.isArmed)
        assertEquals(0, harness.cancelCalls)
    }
}
