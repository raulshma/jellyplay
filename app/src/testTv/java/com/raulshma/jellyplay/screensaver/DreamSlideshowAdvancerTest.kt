package com.raulshma.jellyplay.screensaver

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The slideshow advance machine, driven on TestScope virtual time: an empty
 * payload never starts a cadence, a non-empty one advances one frame per
 * interval with wraparound, and a (re)start resets the cursor — the
 * composable keys its LaunchedEffect on the payload size, so the async
 * fetch's 0 → N transition re-runs [DreamSlideshowAdvancer.run] and starts
 * the cadence the pre-fix loop lost on the empty first frame (the DECLARED
 * cold-path fix; the effect key itself is compose-side and declared in the
 * DreamSlideshow source, not pinnable on this JVM lane).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DreamSlideshowAdvancerTest {

    @Test
    fun `empty payload completes without advancing`() = runTest {
        val advancer = DreamSlideshowAdvancer(INTERVAL_MS)
        // run returns promptly (no hang, no divide-by-zero): the machine's
        // half of the cold-path contract — the effect re-run on the size
        // change does the restarting.
        advancer.run(0)
        assertEquals(0, advancer.currentIndex)
    }

    @Test
    fun `advances one frame per interval once the payload is non-empty`() = runTest {
        val advancer = DreamSlideshowAdvancer(INTERVAL_MS)
        val job = launch { advancer.run(3) }
        advanceTimeBy(INTERVAL_MS)
        // The delay's resumption lands exactly on the interval boundary —
        // advanceTimeBy stops there without running it, so drain the current
        // instant first (the DreamDimStateTest pattern).
        runCurrent()
        assertEquals(1, advancer.currentIndex)
        advanceTimeBy(INTERVAL_MS)
        runCurrent()
        assertEquals(2, advancer.currentIndex)
        job.cancel()
    }

    @Test
    fun `wraps to zero after the last frame`() = runTest {
        val advancer = DreamSlideshowAdvancer(INTERVAL_MS)
        val job = launch { advancer.run(3) }
        repeat(3) {
            advanceTimeBy(INTERVAL_MS)
            runCurrent()
        }
        assertEquals(0, advancer.currentIndex)
        job.cancel()
    }

    @Test
    fun `a restart resets the cursor immediately and advances the new payload`() = runTest {
        val advancer = DreamSlideshowAdvancer(INTERVAL_MS)
        val job = launch { advancer.run(5) }
        advanceTimeBy(INTERVAL_MS * 2)
        runCurrent()
        assertEquals(2, advancer.currentIndex)
        // The size-change key relaunches the effect: cancel the old run,
        // launch the new one — the reset is synchronous with the restart,
        // not a delayed first tick.
        job.cancel()
        val restarted = launch { advancer.run(3) }
        runCurrent()
        assertEquals(0, advancer.currentIndex)
        advanceTimeBy(INTERVAL_MS)
        runCurrent()
        assertEquals(1, advancer.currentIndex)
        restarted.cancel()
    }

    private companion object {
        const val INTERVAL_MS = 10_000L
    }
}
