package com.raulshma.jellyplay.feature.player.video

import io.mockk.every
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Pins for the background-cast handoff latch (the recorded Tier 3 machine:
 * `VideoPlayerViewModel.detachForBackgroundCast`/`reattachFromBackgroundCast`
 * over the process-singleton `CastManager.backgroundCasting` flag) — the #145
 * defensive-clear-at-initialize treatment copied from the PiP latches.
 *
 * The latch is armed ONLY by a player-screen dispose
 * ([VideoPlayerViewModel.detachForBackgroundCast], via
 * PlayerWindowSessionEffects' teardown arm) and consumed only by
 * [VideoPlayerViewModel.reattachFromBackgroundCast] — whose legitimate caller
 * is the SAME VM's config-change recreation re-running the screen's routing
 * read. Because CastManager outlives every VM, an abnormally torn-down
 * previous VM (detach armed, teardown never cleared) used to hand the NEXT VM
 * a stale true: the screen's routing read
 * (`sessionSlices.cast.isBackgroundCasting`, which delegates to this flag on
 * Android) saw it, skipped [VideoPlayerUiEvent.Initialize] entirely, and
 * routed the fresh engine-less VM into the reattach no-op — a player that
 * opens dead. The VM constructor now clears the latch defensively, so a new
 * VM's routing read is always false and the reattach arm stays reachable only
 * after a detach THIS VM performed.
 *
 * The harness lifecycle is driven MANUALLY per test (not via @Before) because
 * the two scenarios need different pre-construction latch states — the stale
 * one must be armed BEFORE the VM constructor runs. The flag is modeled with
 * real state on the seam mock (the GoogleCastStrategy testStore precedent):
 * a fixed `returns true` stub would defeat the gate under test, since
 * `markBackgroundCasting` is a pure AtomicBoolean write in production.
 */
class BackgroundCastHandoffLatchTest {

    /** Shared seam stub: the mock models the AtomicBoolean with real state. */
    private abstract class LatchHarness(initial: Boolean) : VideoPlayerViewModelHarness() {
        val latch = AtomicBoolean(initial)

        override fun preConstructStubs() {
            every { castManager.isBackgroundCasting } answers { latch.get() }
            every { castManager.markBackgroundCasting(any()) } answers { latch.set(firstArg()) }
        }
    }

    /** A stale handoff latch left by an abnormally torn-down previous VM. */
    private class StaleLatchHarness : LatchHarness(initial = true)

    /** A clean VM (no stale latch) for the same-VM detach→reattach leg. */
    private class CleanLatchHarness : LatchHarness(initial = false)

    private fun withHarness(
        harness: LatchHarness,
        block: (LatchHarness) -> Unit,
    ) {
        harness.setUpHarness()
        try {
            block(harness)
        } finally {
            harness.tearDownHarness()
        }
    }

    @Test
    fun `construction clears a stale handoff latch before the screen can route on it`() {
        withHarness(StaleLatchHarness()) { harness ->
            // The VM was built with the latch pre-armed true; the defensive
            // clear must have consumed it — the screen's routing read
            // (sessionSlices.cast.isBackgroundCasting delegates to this flag
            // on Android) would otherwise skip Initialize and dead-air the
            // player.
            assertFalse(harness.latch.get())
            verify(exactly = 1) { harness.castManager.markBackgroundCasting(false) }
        }
    }

    @Test
    fun `reattach without a detach does not consume the latch or rebuild`() {
        withHarness(StaleLatchHarness()) { harness ->
            // Construction cleared the stale latch; the NEXT VM's screen now
            // routes to Initialize. A stray reattach event must stay behind
            // the closed gate: no consumption (a second false write), no
            // soft-release.
            harness.viewModel.onEvent(VideoPlayerUiEvent.ReattachFromBackgroundCast)
            assertFalse(harness.latch.get())
            verify(exactly = 1) { harness.castManager.markBackgroundCasting(false) }
            verify(exactly = 0) { harness.castManager.softRelease() }
        }
    }

    @Test
    fun `same-VM detach arms and reattach consumes the latch unchanged`() {
        withHarness(CleanLatchHarness()) { harness ->
            // The legit leg (config-change recreation while cast-playing): the
            // screen dispose arms the latch via the detach event...
            harness.viewModel.onEvent(VideoPlayerUiEvent.DetachForBackgroundCast)
            assertTrue(harness.latch.get())
            verify(exactly = 1) { harness.castManager.softRelease() }
            // ...and the recreated composition's routing read consumes it.
            harness.viewModel.onEvent(VideoPlayerUiEvent.ReattachFromBackgroundCast)
            assertFalse(harness.latch.get())
            // Exactly one consumption write beyond the constructor's defensive
            // clear (which also wrote false on this clean construction).
            verify(exactly = 2) { harness.castManager.markBackgroundCasting(false) }
        }
    }

    @Test
    fun `duplicate reattach after consumption does not re-run the arm`() {
        withHarness(CleanLatchHarness()) { harness ->
            harness.viewModel.onEvent(VideoPlayerUiEvent.DetachForBackgroundCast)
            harness.viewModel.onEvent(VideoPlayerUiEvent.ReattachFromBackgroundCast)
            // A second routing read without an intervening detach must not
            // re-consume or rebuild.
            harness.viewModel.onEvent(VideoPlayerUiEvent.ReattachFromBackgroundCast)
            assertFalse(harness.latch.get())
            verify(exactly = 2) { harness.castManager.markBackgroundCasting(false) }
        }
    }
}
