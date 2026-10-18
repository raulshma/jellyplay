package com.raulshma.jellyplay.feature.player.video.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the per-frame `time-pos` dispatch dedupe against its drift-correction
 * contract: the first observation dispatches, sub-window per-frame creep is
 * swallowed in both directions, a window-edge move (SyncPlay's 100 ms
 * correction threshold halved) dispatches, an accepted observation re-baselines
 * the window, and a reset (seek/pause/play/stop/load/track change) makes the
 * next observation land unthrottled.
 */
class TimePosDedupeTest {

    @Test
    fun `first observation dispatches`() {
        val dedupe = TimePosDedupe()

        assertTrue(dedupe.accept(0.0))
    }

    @Test
    fun `sub-window creep is deduped in both directions`() {
        val dedupe = TimePosDedupe()
        dedupe.accept(0.0)

        assertFalse(dedupe.accept(0.021))
        assertFalse(dedupe.accept(0.004))
    }

    @Test
    fun `window-edge move dispatches`() {
        val dedupe = TimePosDedupe()
        dedupe.accept(0.0)

        assertTrue(dedupe.accept(TimePosDedupe.TIME_POS_DEDUPE_SEC))
    }

    @Test
    fun `accepted observation re-baselines the window`() {
        val dedupe = TimePosDedupe()
        dedupe.accept(0.0)
        assertTrue(dedupe.accept(0.05))

        // Measured from the NEW baseline: still inside the window.
        assertFalse(dedupe.accept(0.099))
        // ...and a full window from it dispatches again.
        assertTrue(dedupe.accept(0.1))
    }

    @Test
    fun `large backwards jump dispatches`() {
        val dedupe = TimePosDedupe()
        dedupe.accept(30.0)

        assertTrue(dedupe.accept(5.0))
    }

    @Test
    fun `reset makes the very next observation land unthrottled`() {
        val dedupe = TimePosDedupe()
        dedupe.accept(30.0)
        dedupe.reset()

        assertTrue(dedupe.accept(30.001))
    }
}
