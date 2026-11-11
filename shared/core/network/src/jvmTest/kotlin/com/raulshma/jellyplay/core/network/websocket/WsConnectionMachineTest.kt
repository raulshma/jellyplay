package com.raulshma.jellyplay.core.network.websocket

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the socket lane's connection decision core ([WsConnectionMachine])
 * through the decisions its transitions produce — the phase hierarchy
 * ([WsConnectionPhase]) is deliberately exercised only via
 * [WsDecision.phase]/[WsDecision.actions], the same way `LiveFallbackPhase`
 * is pinned. Every case here has a socket-level counterpart or complement in
 * [JellyfinWebSocketClientReconnectTest]; the clean-close fix additionally
 * runs against a real MockWebServer socket there.
 *
 * The deterministic schedules all ride the injected jitter source (the
 * machine's one test seam): `{ 0 }` for exact ladder arithmetic,
 * `{ 250 }`/`{ 500 }` for the additive-jitter and total-cap shapes.
 */
class WsConnectionMachineTest {

    // ── Helpers ─────────────────────────────────────────────────────────────

    /** Teardown arm shared by disconnect and connect-restart (machine-internal shape). */
    private val teardown = listOf(
        WsAction.CancelRetryJobs,
        WsAction.StopKeepAlive,
        WsAction.CloseSocket,
    )

    /** A terminal callback's full decision when (re)entering a ladder rung. */
    private fun droppedWithSchedule(delayMs: Long) = listOf(
        WsAction.StopKeepAlive,
        WsAction.CloseSocket,
        WsAction.ScheduleReconnect(delayMs),
    )

    private fun freshMachine(jitter: () -> Long = { 0L }): WsConnectionMachine =
        WsConnectionMachine(jitter = jitter)

    /** Fresh explicit connect of a logical session, through to the open. */
    private fun WsConnectionMachine.connectFresh() {
        onConnectRequested(credentialsMatch = false)
        onConnectStart()
        onOpen()
    }

    /**
     * Drives consecutive terminal callbacks until the fast ladder exhausts
     * into the slow lane and returns that slow-lane entry. (The machine does
     * not require ticks between drops — a drop advances from whatever retry
     * phase it lands in — which is what keeps this helper simple.)
     */
    private fun WsConnectionMachine.exhaustFastLadder(): WsDecision {
        var decision = onFailure()
        while (decision.phase is WsConnectionPhase.Reconnecting) {
            decision = onFailure()
        }
        return decision
    }

    // ── 1. Happy connect ladder ─────────────────────────────────────────────

    @Test
    fun `happy connect ladder runs Connecting then Connected without emitting a reconnect`() {
        val machine = freshMachine()
        val requested = machine.onConnectRequested(credentialsMatch = false)
        assertEquals(WsConnectionPhase.Disconnected, requested.phase)
        assertEquals(teardown, requested.actions)
        val start = machine.onConnectStart()
        assertEquals(WsConnectionPhase.Connecting, start.phase)
        assertEquals(listOf(WsAction.OpenSocket), start.actions)
        val open = machine.onOpen()
        assertEquals(WsConnectionPhase.Connected, open.phase)
        assertEquals(listOf(WsAction.StartKeepAlive), open.actions)
        assertEquals(WsConnectionPhase.Connected, machine.phase.value)
    }

    // ── 2. Fast ladder after a failure ──────────────────────────────────────

    @Test
    fun `failure from Connected starts the fast ladder 1s through 16s with injected jitter`() {
        val machine = freshMachine(jitter = { 250L })
        machine.connectFresh()
        for (attempt in 1..5) {
            val delayMs = (1_000L shl (attempt - 1)) + 250L
            val dropped = machine.onFailure()
            assertEquals(WsConnectionPhase.Reconnecting(attempt, delayMs), dropped.phase)
            assertEquals(droppedWithSchedule(delayMs), dropped.actions)
            // The tick fires the attempt; the phase stays on the rung while
            // it is in flight (that is what lets its failure advance the
            // ladder instead of restarting it).
            assertEquals(listOf(WsAction.OpenSocket), machine.onRetryDue().actions)
            assertEquals(WsConnectionPhase.Reconnecting(attempt, delayMs), machine.phase.value)
        }
        assertEquals(WsConnectionPhase.Reconnecting(5, 16_250L), machine.phase.value)
    }

    // ── 3. Exhaustion into the slow lane, doubling to the cap ───────────────

    @Test
    fun `the sixth failure exhausts into BackgroundRetry and successive entries double to the cap`() {
        val machine = freshMachine()
        machine.connectFresh()
        assertEquals(WsConnectionPhase.BackgroundRetry(60_000L), machine.exhaustFastLadder().phase)
        machine.onRetryDue() // the slow attempt fires (phase stays on the lane)
        assertEquals(WsConnectionPhase.BackgroundRetry(120_000L), machine.exhaustFastLadder().phase)
        machine.onRetryDue()
        assertEquals(WsConnectionPhase.BackgroundRetry(240_000L), machine.exhaustFastLadder().phase)
        machine.onRetryDue()
        assertEquals(WsConnectionPhase.BackgroundRetry(480_000L), machine.exhaustFastLadder().phase)
        machine.onRetryDue()
        // The doubling base caps at 15min (480s→960s is stored as 900s), so
        // this entry — and every one after it — waits exactly the cap: a
        // permanently dead server wakes at most once per quarter hour.
        assertEquals(WsConnectionPhase.BackgroundRetry(900_000L), machine.exhaustFastLadder().phase)
        machine.onRetryDue()
        assertEquals(WsConnectionPhase.BackgroundRetry(900_000L), machine.exhaustFastLadder().phase)
    }

    // ── 4. THE FIX: a clean server-side close reconnects ────────────────────

    @Test
    fun `clean server-side close reconnects, the former dead end fixed`() {
        val machine = freshMachine()
        machine.connectFresh()
        // The former onClosed callback stopped at flipping the flag — a clean
        // close (server restart, idle policy) stranded the session forever.
        val dropped = machine.onClosedClean()
        assertEquals(WsConnectionPhase.Reconnecting(1, 1_000L), dropped.phase)
        assertEquals(droppedWithSchedule(1_000L), dropped.actions)
        // Mid-ladder: the in-flight retry's socket closed cleanly by the
        // server ADVANCES the same ladder (it used to dead-end too).
        assertEquals(listOf(WsAction.OpenSocket), machine.onRetryDue().actions)
        assertEquals(WsConnectionPhase.Reconnecting(2, 2_000L), machine.onClosedClean().phase)
        // From the slow lane: the cleanly-closed background attempt restarts
        // the fast ladder — verbatim parity with the old background retry's
        // failure shape (attempts reset, fast schedule re-earned).
        assertEquals(WsConnectionPhase.BackgroundRetry(60_000L), machine.exhaustFastLadder().phase)
        machine.onRetryDue()
        assertEquals(WsConnectionPhase.Reconnecting(1, 1_000L), machine.onClosedClean().phase)
    }

    // ── 5. Disconnect from any state ────────────────────────────────────────

    @Test
    fun `disconnect from every phase lands Disconnected with teardown and no scheduled retry`() {
        val scenarios: List<Pair<String, (WsConnectionMachine) -> Unit>> = listOf(
            "Connected" to { it.connectFresh() },
            "Connecting" to {
                it.onConnectRequested(credentialsMatch = false)
                it.onConnectStart()
            },
            "Reconnecting(1)" to {
                it.connectFresh()
                it.onFailure()
            },
            "Reconnecting(5)" to { m ->
                m.connectFresh()
                repeat(5) { m.onFailure() }
            },
            "BackgroundRetry" to {
                it.connectFresh()
                it.exhaustFastLadder()
            },
            "Disconnected (idempotent)" to { },
        )
        for ((name, setup) in scenarios) {
            val machine = freshMachine()
            setup(machine)
            val decision = machine.onDisconnectRequested()
            assertEquals(WsConnectionPhase.Disconnected, decision.phase, name)
            assertEquals(teardown, decision.actions, name)
            assertTrue(
                decision.actions.none { it is WsAction.ScheduleReconnect || it is WsAction.OpenSocket },
                "$name: disconnect must never schedule a retry or open a socket",
            )
            // No poison counter exists — a pending tick that wakes after the
            // disconnect neutralizes itself against the phase.
            assertTrue(machine.onRetryDue().actions.isEmpty(), "$name: stale tick must not open a socket")
            assertEquals(WsConnectionPhase.Disconnected, machine.phase.value, name)
        }
    }

    // ── 6. Disconnect racing a pending retry delay ──────────────────────────

    @Test
    fun `a disconnect racing a pending retry delay never opens a socket`() {
        // Slow lane: BackgroundRetry with its (simulated) 60s delay pending…
        val slow = freshMachine()
        slow.connectFresh()
        slow.exhaustFastLadder()
        slow.onDisconnectRequested()
        val slowTick = slow.onRetryDue()
        assertEquals(WsConnectionPhase.Disconnected, slowTick.phase)
        assertTrue(slowTick.actions.isEmpty(), "the raced background delay must not open a socket")

        // …and the fast lane: Reconnecting(1) with its 1s delay pending.
        val fast = freshMachine()
        fast.connectFresh()
        fast.onFailure()
        fast.onDisconnectRequested()
        val fastTick = fast.onRetryDue()
        assertEquals(WsConnectionPhase.Disconnected, fastTick.phase)
        assertTrue(fastTick.actions.isEmpty(), "the raced fast delay must not open a socket")
    }

    // ── 7. First-open vs reconnect-open emission ────────────────────────────

    @Test
    fun `open after a reconnect emits while open after a fresh connect does not`() {
        val machine = freshMachine()
        // First connect of the logical session — no emission.
        machine.onConnectRequested(credentialsMatch = false)
        machine.onConnectStart()
        assertFalse(machine.onOpen().actions.contains(WsAction.EmitReconnect))
        // Fast-lane reconnect open — emits, and BEFORE keep-alive starts
        // (the old onOpen's order).
        assertEquals(WsConnectionPhase.Reconnecting(1, 1_000L), machine.onFailure().phase)
        assertEquals(
            listOf(WsAction.EmitReconnect, WsAction.StartKeepAlive),
            machine.onOpen().actions,
        )
        // Slow-lane reconnect open — emits too (the old flag stayed set
        // across failures precisely so this open would emit).
        assertEquals(WsConnectionPhase.BackgroundRetry(60_000L), machine.exhaustFastLadder().phase)
        machine.onRetryDue()
        assertTrue(machine.onOpen().actions.contains(WsAction.EmitReconnect))
        // An explicit disconnect starts a fresh session: its open is a first
        // connect again and does not emit (the old hadConnectedOnce reset).
        machine.onDisconnectRequested()
        machine.onConnectRequested(credentialsMatch = false)
        machine.onConnectStart()
        assertFalse(machine.onOpen().actions.contains(WsAction.EmitReconnect))
    }

    // ── 8. Jitter injection: determinism on both lanes + the total cap ─────

    @Test
    fun `injected jitter drives deterministic delays on both lanes and respects the background cap`() {
        // The same 250ms source rides every fast rung…
        val jitted = freshMachine(jitter = { 250L })
        jitted.connectFresh()
        assertEquals(WsConnectionPhase.Reconnecting(1, 1_250L), jitted.onFailure().phase)
        // …and the slow lane's entry is base + jitter.
        assertEquals(WsConnectionPhase.BackgroundRetry(60_250L), jitted.exhaustFastLadder().phase)

        // The cap applies to the TOTAL: once the doubling base is pinned at
        // the 15min plateau, jitter cannot push the entry past it.
        val capped = freshMachine(jitter = { 500L })
        capped.connectFresh()
        repeat(4) {
            capped.exhaustFastLadder()
            capped.onRetryDue()
        }
        assertEquals(WsConnectionPhase.BackgroundRetry(900_000L), capped.exhaustFastLadder().phase)
    }

    // ── 9. connect() dedupe ─────────────────────────────────────────────────

    @Test
    fun `a matching connect while Connected is a no-op and a mismatched one tears down for a fresh session`() {
        val machine = freshMachine()
        machine.connectFresh()
        val dupe = machine.onConnectRequested(credentialsMatch = true)
        assertEquals(WsConnectionPhase.Connected, dupe.phase)
        assertTrue(dupe.actions.isEmpty(), "the dedupe hit must not churn the socket")
        // A different credential set restarts the session: teardown now, the
        // adapter then assigns the new credentials and calls onConnectStart.
        val restart = machine.onConnectRequested(credentialsMatch = false)
        assertEquals(WsConnectionPhase.Disconnected, restart.phase)
        assertEquals(teardown, restart.actions)
    }

    // ── 10. Stray events after a disconnect ─────────────────────────────────

    @Test
    fun `stray open, failure and clean close after a disconnect are contained`() {
        val machine = freshMachine()
        machine.connectFresh()
        machine.onDisconnectRequested()
        // The disconnect's own close(1000) echo arrives as a clean close:
        // Noop, no resurrected schedule. (The old client was only safe here
        // because its onClosed was the dead-end bug.)
        val echo = machine.onClosedClean()
        assertEquals(WsConnectionPhase.Disconnected, echo.phase)
        assertTrue(echo.actions.isEmpty())
        // A late failure: Noop (the old code scheduled a zombie retry whose
        // creds-guard was all that kept it from connecting).
        assertTrue(machine.onFailure().actions.isEmpty())
        // A stray open whose handshake raced the disconnect: close the
        // socket; do NOT resurrect isConnected (the old onOpen set it true
        // for a socket about to be torn down).
        val stray = machine.onOpen()
        assertEquals(listOf(WsAction.CloseSocket), stray.actions)
        assertEquals(WsConnectionPhase.Disconnected, machine.phase.value)
        // And the stale tick after all of it: still Noop.
        assertTrue(machine.onRetryDue().actions.isEmpty())
    }
}
