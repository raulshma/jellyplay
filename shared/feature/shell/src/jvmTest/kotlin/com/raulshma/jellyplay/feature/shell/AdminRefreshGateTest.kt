package com.raulshma.jellyplay.feature.shell

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the shared admin-refresh dedupe policy both shells serve to
 * AdminRouteContainer (Android: MainViewModel.refreshAdminStatus; desktop:
 * the DesktopNavScaffold lambda): the 30 s window boundary (inclusive —
 * at exactly 30 s a refresh runs again), in-flight suppression, and the
 * only-on-success timestamp. The gate is a pure policy over injected
 * in-flight/clock lambdas, so the whole suite is fake-clock JVM.
 */
class AdminRefreshGateTest {

    private class FakeClock {
        var now: Long = 100_000L
        fun time(): Long = now
    }

    // ── Window boundary ─────────────────────────────────────────────────

    @Test
    fun `first refresh runs before any completion`() {
        val gate = AdminRefreshGate(isRefreshInFlight = { false }, nowMs = { 100_000L })

        assertTrue(gate.shouldStart())
    }

    @Test
    fun `refresh is blocked inside the 30s window and allowed at exactly 30s`() {
        val clock = FakeClock()
        val gate = AdminRefreshGate(isRefreshInFlight = { false }, nowMs = clock::time)
        assertTrue(gate.shouldStart())
        clock.now = 150_000L
        gate.onRefreshCompleted()

        // One millisecond short of the window: still blocked.
        clock.now = 150_000L + AdminRefreshGate.DEFAULT_INTERVAL_MS - 1
        assertFalse(gate.shouldStart())

        // At exactly 30 s the window is inclusive — the refresh runs.
        clock.now = 150_000L + AdminRefreshGate.DEFAULT_INTERVAL_MS
        assertTrue(gate.shouldStart())
    }

    @Test
    fun `default interval is the shared 30s constant`() {
        assertEquals(30_000L, AdminRefreshGate.DEFAULT_INTERVAL_MS)
    }

    // ── In-flight suppression ───────────────────────────────────────────

    @Test
    fun `in-flight refresh suppresses even far outside the window`() {
        val gate = AdminRefreshGate(isRefreshInFlight = { true }, nowMs = { 1_000_000L })

        assertFalse(gate.shouldStart())
    }

    @Test
    fun `in-flight flag is consulted on every decision`() {
        var inFlight = true
        val gate = AdminRefreshGate(isRefreshInFlight = { inFlight }, nowMs = { 1_000_000L })
        assertFalse(gate.shouldStart())

        inFlight = false
        assertTrue(gate.shouldStart())
    }

    // ── Timestamp bookkeeping ───────────────────────────────────────────

    @Test
    fun `onRefreshCompleted stamps the clock at completion time`() {
        val clock = FakeClock()
        val gate = AdminRefreshGate(isRefreshInFlight = { false }, nowMs = clock::time)

        assertEquals(0L, gate.lastRefreshAtMs)
        clock.now = 42_000L
        gate.onRefreshCompleted()
        assertEquals(42_000L, gate.lastRefreshAtMs)
    }

    @Test
    fun `a refresh that never completes leaves the window unadvanced`() {
        // Failure semantics live in the callers (onRefreshCompleted is only
        // invoked after a successful repository call); the gate's contract is
        // that nothing else moves lastRefreshAtMs.
        val gate = AdminRefreshGate(isRefreshInFlight = { false }, nowMs = { 50_000L })

        assertTrue(gate.shouldStart())
        assertEquals(0L, gate.lastRefreshAtMs)
        assertTrue(gate.shouldStart())
    }
}
