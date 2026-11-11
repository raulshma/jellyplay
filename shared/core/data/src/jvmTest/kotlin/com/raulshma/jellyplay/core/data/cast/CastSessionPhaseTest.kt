package com.raulshma.jellyplay.core.data.cast

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Pins the pure cast session phase machine ([CastSessionPhase] + the
 * transitions + the [projected] read-through projections) — the decision core
 * the three strategies (Google / DLNA / Jellyfin) now route their session
 * callbacks through instead of hand-synced `_isConnected`/`_isConnecting`
 * boolean pairs. This file is the machine's lane; the per-strategy wirings
 * are pinned in their own lanes (DlnaCastStrategyTest,
 * JellyfinRemotePlayCastStrategyTest, the store lane's GoogleCastStrategyTest).
 *
 * Pinned here:
 *  - the full connect ladder: Idle → beginConnecting → Connecting →
 *    connected → Connected;
 *  - the projections per phase (isConnected / isConnecting honesty — only
 *    Connected is connected, only Connecting is connecting);
 *  - the DECLARED failed-start fix: [failed] lands in
 *    [CastSessionPhase.Failed] whose connected projection is FALSE — the
 *    former `clear isConnecting only` writer left a prior session's
 *    connected=true standing on a dead session (the phantom connection
 *    GoogleCastStrategy.onSessionStartFailed could produce);
 *  - disconnect from EVERY phase lands on Idle (totality: the reset-to-Idle
 *    event cannot wedge, a duplicate end callback is a no-op);
 *  - retry from Failed passes back through Connecting;
 *  - the re-route edge (beginConnecting from Connected) drops the connected
 *    projection — the Google route-switch window;
 *  - the [projected] read-through: a write is visible on the writer's thread
 *    with zero dispatch latency (the property `stateIn(Eagerly)` cannot
 *    deliver — and the reason the DLNA/Jellyfin synchronous connect flips
 *    survive the migration), plus the collapse of adjacent equal selections
 *    (Idle→Failed does not re-emit `false`).
 */
class CastSessionPhaseTest {

    // ── connect ladder ────────────────────────────────────────────────────

    @Test
    fun `full connect ladder walks Idle Connecting Connected`() {
        var phase: CastSessionPhase = CastSessionPhase.Idle

        phase = phase.beginConnecting()
        assertIs<CastSessionPhase.Connecting>(phase)
        assertTrue(phase.isConnecting)
        assertFalse(phase.isConnected)

        phase = phase.connected()
        assertIs<CastSessionPhase.Connected>(phase)
        assertTrue(phase.isConnected)
        assertFalse(phase.isConnecting)
    }

    @Test
    fun `a start without an attempt recovers straight to Connected`() {
        // Totality: an unannounced "started" (resume after process death) is
        // an absolute statement of truth from any phase.
        val phase = CastSessionPhase.Idle.connected()

        assertIs<CastSessionPhase.Connected>(phase)
    }

    @Test
    fun `retry from Failed passes back through Connecting`() {
        val retry = CastSessionPhase.Failed(reason = "2200").beginConnecting()

        assertIs<CastSessionPhase.Connecting>(retry)
        assertTrue(retry.isConnecting)
    }

    // ── DECLARED FIX: failed start clears the connected projection ────────

    @Test
    fun `failed start clears a stale connected flag - the phantom connection fix`() {
        // The Google bug shape: a prior session's connected flag was still
        // standing (its end never reached us), the NEXT start attempt fails.
        val staleConnected: CastSessionPhase = CastSessionPhase.Idle
            .beginConnecting()
            .connected()
        assertTrue(staleConnected.isConnected) // the stale flag...

        val afterFailedStart = staleConnected.beginConnecting().failed(reason = "2200")

        // ...is now honest: Failed is NOT connected.
        val failed = assertIs<CastSessionPhase.Failed>(afterFailedStart)
        assertEquals("2200", failed.reason)
        assertFalse(afterFailedStart.isConnected)
        assertFalse(afterFailedStart.isConnecting)
    }

    @Test
    fun `failed carries a null reason when the transport has nothing to say`() {
        val failed = CastSessionPhase.Connecting.failed(reason = null)

        assertIs<CastSessionPhase.Failed>(failed)
        assertEquals(null, failed.reason)
    }

    // ── disconnect: reset-to-Idle from EVERY phase ─────────────────────────

    @Test
    fun `disconnect lands on Idle from every phase`() {
        val phases = listOf(
            CastSessionPhase.Idle,
            CastSessionPhase.Connecting,
            CastSessionPhase.Connected,
            CastSessionPhase.Failed(reason = "7"),
        )

        for (phase in phases) {
            val disconnected = phase.disconnected()
            assertIs<CastSessionPhase.Idle>(disconnected)
            assertFalse(disconnected.isConnected)
            assertFalse(disconnected.isConnecting)
        }
    }

    @Test
    fun `a duplicate disconnect from Idle stays Idle`() {
        val again = CastSessionPhase.Idle.disconnected()

        assertIs<CastSessionPhase.Idle>(again)
    }

    // ── re-route edge ─────────────────────────────────────────────────────

    @Test
    fun `beginConnecting from Connected drops the connected projection for the re-route`() {
        // Google's route-switch window: selecting a new route while a session
        // is live. The former boolean pair kept isConnected=true through the
        // attempt; the machine reports Connecting — and the manager observers
        // see the same Disconnected→Connected pair either way.
        val reRoute = CastSessionPhase.Connected.beginConnecting()

        assertIs<CastSessionPhase.Connecting>(reRoute)
        assertFalse(reRoute.isConnected)
        assertTrue(reRoute.isConnecting)
    }

    // ── read-through projections ──────────────────────────────────────────

    @Test
    fun `projected value is visible synchronously on the writer thread`() {
        val phase = MutableStateFlow<CastSessionPhase>(CastSessionPhase.Idle)
        val isConnected = phase.projected { it.isConnected }
        val isConnecting = phase.projected { it.isConnecting }

        assertFalse(isConnected.value)

        phase.value = phase.value.beginConnecting()

        // Zero-dispatch read-through: a stateIn(Eagerly) projection would
        // still report the STALE false here.
        assertTrue(isConnecting.value)
        assertFalse(isConnected.value)

        phase.value = phase.value.connected()

        assertTrue(isConnected.value)
        assertFalse(isConnecting.value)

        phase.value = phase.value.disconnected()

        assertFalse(isConnected.value)
        assertFalse(isConnecting.value)
    }

    @Test
    fun `projected collect replays current then only distinct selections`() = runTest {
        val phase = MutableStateFlow<CastSessionPhase>(CastSessionPhase.Idle)
        val isConnected = phase.projected { it.isConnected }
        val seen = mutableListOf<Boolean>()
        val job = launch { isConnected.collect { seen.add(it) } }

        // yield between writes so each transition is observed before the
        // next — StateFlow conflates writes that happen while the collector
        // is parked, exactly like the MutableStateFlow<Boolean> pair this
        // replaces (the DLNA/Jellyfin synchronous connect flips CONFLATE the
        // same way, today as then).
        phase.value = CastSessionPhase.Connecting
        yield() // false→false: the adjacent-equal collapse — NOT re-emitted
        phase.value = CastSessionPhase.Connected
        yield()
        phase.value = CastSessionPhase.Failed(reason = "1")
        yield()
        phase.value = CastSessionPhase.Idle.disconnected()
        yield() // Failed→Idle: both false — collapsed, NOT re-emitted

        job.cancel()
        assertEquals(listOf(false, true, false), seen)
    }
}
