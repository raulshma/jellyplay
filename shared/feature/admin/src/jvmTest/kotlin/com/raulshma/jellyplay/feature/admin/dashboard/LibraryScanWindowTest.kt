package com.raulshma.jellyplay.feature.admin.dashboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the extracted dashboard time machines — [LibraryScanWindow] (the scan
 * optimistic grace window) and [WsTaskFreshness] (the WS-push trust rule) —
 * with injected clocks: timestamps in → state out, no wall clock and no VM
 * (the mediacleanup `MediaCleanupScanStateHolderTest` precedent).
 */
class LibraryScanWindowTest {

    // ── LibraryScanWindow ───────────────────────────────────────────────

    @Test
    fun `start arms the window for exactly the grace duration`() {
        var nowMs = 1_000L
        val window = LibraryScanWindow(now = { nowMs }, graceMs = 15_000L)

        assertFalse(window.isActive(), "an unstarted window is never active")

        window.start()
        // 1ms before the deadline.
        nowMs = 1_000L + 15_000L - 1
        assertTrue(window.isActive(), "inside the window")
        // Exactly at the deadline: expired (the strict < the VM compared).
        nowMs = 1_000L + 15_000L
        assertFalse(window.isActive(), "at the deadline")
    }

    @Test
    fun `confirmRunning disarms immediately so a later idle means finished`() {
        var nowMs = 0L
        val window = LibraryScanWindow(now = { nowMs }, graceMs = 15_000L)
        window.start()
        nowMs = 1_000L
        window.confirmRunning()

        assertTrue(nowMs < 16_000L)
        assertFalse(window.isActive(), "the WS confirmed RUNNING — no more optimistic hold")
        // And it stays disarmed as time advances.
        nowMs = 2_000L
        assertFalse(window.isActive())
    }

    @Test
    fun `a re-armed window after clear restarts the full grace`() {
        var nowMs = 0L
        val window = LibraryScanWindow(now = { nowMs }, graceMs = 15_000L)

        window.start()
        window.clear()
        assertFalse(window.isActive(), "cleared")
        nowMs = 10_000L
        assertFalse(window.isActive())

        // Second scan tap: the window re-arms from NOW, not from the first tap.
        window.start()
        nowMs = 10_000L + 15_000L - 1
        assertTrue(window.isActive(), "re-armed window covers the second tap")
        nowMs = 10_000L + 15_000L
        assertFalse(window.isActive())
    }

    @Test
    fun `clear resets an active window`() {
        var nowMs = 0L
        val window = LibraryScanWindow(now = { nowMs }, graceMs = 15_000L)
        window.start()
        nowMs = 5_000L
        assertTrue(window.isActive())
        window.clear()
        assertFalse(window.isActive(), "explicit clear wins over the deadline")
    }

    @Test
    fun `default grace matches the VM constant`() {
        // The extraction moved the VM's 15s literal; pin the default so the
        // behavior delta of the fold stays zero.
        assertEquals(15_000L, LibraryScanWindow.DEFAULT_GRACE_MS)
    }

    // ── WsTaskFreshness ─────────────────────────────────────────────────

    @Test
    fun `a recent push is fresh and silence past the window is not`() {
        var nowMs = 100_000L
        val freshness = WsTaskFreshness(staleAfterMs = 30_000L, now = { nowMs })

        assertTrue(freshness.isFresh(lastPushAtMs = 100_000L), "pushed this instant")
        nowMs = 129_999L
        assertTrue(freshness.isFresh(lastPushAtMs = 100_000L), "29.999s of silence is fresh")
        nowMs = 130_000L
        assertFalse(freshness.isFresh(lastPushAtMs = 100_000L), "exactly 30s is stale (strict <)")
    }

    @Test
    fun `the zero sentinel is never fresh`() {
        // A channel that never pushed carries 0 — the heuristic must not read
        // it as "pushed an eternity ago that is somehow fresh".
        val freshness = WsTaskFreshness(staleAfterMs = 30_000L, now = { 1_000L })
        assertFalse(freshness.isFresh(lastPushAtMs = 0L))
    }

    @Test
    fun `a replay older than the window is stale`() {
        // The shared flow's replay can be arbitrarily old — freshness is
        // judged from the push timestamp, so a stale replay is refused even
        // though a collector just received it.
        val freshness = WsTaskFreshness(staleAfterMs = 30_000L, now = { 500_000L })
        assertFalse(freshness.isFresh(lastPushAtMs = 100_000L))
    }

    @Test
    fun `default staleness matches the VM constant`() {
        assertEquals(30_000L, WsTaskFreshness.DEFAULT_STALE_AFTER_MS)
    }
}
