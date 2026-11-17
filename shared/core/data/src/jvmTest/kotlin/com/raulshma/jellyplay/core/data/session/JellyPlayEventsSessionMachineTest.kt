package com.raulshma.jellyplay.core.data.session

import com.raulshma.jellyplay.core.data.session.JellyPlayEventsSessionPhase.EventsRunning
import com.raulshma.jellyplay.core.data.session.JellyPlayEventsSessionPhase.Frozen
import com.raulshma.jellyplay.core.data.session.JellyPlayEventsSessionPhase.GateClosed
import com.raulshma.jellyplay.core.data.session.JellyPlayEventsSessionPhase.PluginUnavailable
import com.raulshma.jellyplay.core.data.session.JellyPlayEventsSessionPhase.ProbePending
import com.raulshma.jellyplay.core.data.session.JellyPlayEventsSessionPhase.TornDown
import com.raulshma.jellyplay.core.data.session.JellyPlayEventsSessionPhase.Unauthenticated
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pure-machine pins for [JellyPlayEventsSessionMachine] — the transition
 * table that owns [JellyPlayEventsSessionController]'s lifecycle (the
 * recorded Tier-3 finding: the phases used to be encoded by which of the
 * three `Job?` slots were live, races on null-checks held by comment
 * discipline). The controller had NO pins before this migration; the table
 * here is the machine's first (and total) specification:
 *
 *  - `start` admits only `Frozen` (the former live-job idempotence) and the
 *    first auth edge lands the resting phase;
 *  - the auth-true edge is the probe window from EVERY post-start phase
 *    (the executor's cancel-then-launch replaces any session face);
 *  - the auth-false edge is the recorded teardown from every
 *    session-carrying phase, and stays put when nothing was ever serviced;
 *  - the probe verdict splits to `PluginUnavailable` (the resting state the
 *    recorded survey's sketch missed) or `GateClosed`;
 *  - the gate edges flip `GateClosed` ↔ `EventsRunning`, idempotently.
 */
class JellyPlayEventsSessionMachineTest {

    private val all = JellyPlayEventsSessionPhase.entries
    private val postStart = all - Frozen

    // ── start (the public arm) ───────────────────────────────────────────

    @Test
    fun `start admits only Frozen and lands Unauthenticated`() {
        assertEquals(Unauthenticated, JellyPlayEventsSessionMachine.onStart(Frozen))
    }

    @Test
    fun `a re-start from any later phase is the idempotent no-op (the former live-job check)`() {
        for (phase in postStart) {
            assertEquals(
                phase,
                JellyPlayEventsSessionMachine.onStart(phase),
                "start() must leave $phase untouched",
            )
        }
    }

    // ── the auth edges ───────────────────────────────────────────────────

    @Test
    fun `the auth-true edge lands the probe window from every post-start phase`() {
        for (phase in postStart) {
            assertEquals(
                ProbePending,
                JellyPlayEventsSessionMachine.onAuthTrue(phase),
                "the true edge from $phase must cancel-then-launch into the probe",
            )
        }
    }

    @Test
    fun `the auth-false edge is the recorded teardown from every session-carrying phase`() {
        for (phase in postStart - Unauthenticated) {
            assertEquals(
                TornDown,
                JellyPlayEventsSessionMachine.onAuthFalse(phase),
                "the false edge from $phase tears the session down",
            )
        }
    }

    @Test
    fun `a false edge with nothing ever serviced stays Unauthenticated`() {
        assertEquals(
            Unauthenticated,
            JellyPlayEventsSessionMachine.onAuthFalse(Unauthenticated),
            "nothing was torn — the executor's idempotent stops run either way",
        )
    }

    // ── the probe verdict ────────────────────────────────────────────────

    @Test
    fun `an AVAILABLE probe arms the gate collector with the stream down`() {
        assertEquals(GateClosed, JellyPlayEventsSessionMachine.onProbeAvailable(ProbePending))
    }

    @Test
    fun `an unavailable probe lands the plugin-less resting state`() {
        assertEquals(PluginUnavailable, JellyPlayEventsSessionMachine.onProbeUnavailable(ProbePending))
        // The resting shapes that survive until the next auth edge:
        assertTrue(PluginUnavailable in postStart, "PluginUnavailable is a post-start resting phase")
    }

    // ── the gate edges ───────────────────────────────────────────────────

    @Test
    fun `the gate opened lands EventsRunning from the gate-riding phases`() {
        assertEquals(EventsRunning, JellyPlayEventsSessionMachine.onGateOpened(GateClosed))
        assertEquals(EventsRunning, JellyPlayEventsSessionMachine.onGateOpened(EventsRunning), "replace-never-stack re-entry")
    }

    @Test
    fun `the gate closed lands GateClosed from the gate-riding phases`() {
        assertEquals(GateClosed, JellyPlayEventsSessionMachine.onGateClosed(EventsRunning))
        assertEquals(GateClosed, JellyPlayEventsSessionMachine.onGateClosed(GateClosed), "idempotent re-close")
    }

    // ── the full ride ────────────────────────────────────────────────────

    @Test
    fun `the full ride — arm, probe, gate up, toggle off, toggle on, sign-out, re-auth`() {
        var phase = Frozen

        phase = JellyPlayEventsSessionMachine.onStart(phase)
        assertEquals(Unauthenticated, phase, "start() armed the collector")

        phase = JellyPlayEventsSessionMachine.onAuthFalse(phase)
        assertEquals(Unauthenticated, phase, "the first false edge has nothing to tear down")

        phase = JellyPlayEventsSessionMachine.onAuthTrue(phase)
        assertEquals(ProbePending, phase, "sign-in services the probe")

        phase = JellyPlayEventsSessionMachine.onProbeUnavailable(phase)
        assertEquals(PluginUnavailable, phase, "plugin-less until the next auth edge")

        phase = JellyPlayEventsSessionMachine.onAuthTrue(phase)
        assertEquals(ProbePending, phase, "a re-auth restarts from the plugin-less rest")

        phase = JellyPlayEventsSessionMachine.onProbeAvailable(phase)
        assertEquals(GateClosed, phase)

        phase = JellyPlayEventsSessionMachine.onGateOpened(phase)
        assertEquals(EventsRunning, phase, "the toggle opened the gate — the stream runs")

        phase = JellyPlayEventsSessionMachine.onGateClosed(phase)
        assertEquals(GateClosed, phase, "the toggle closed the gate mid-session")

        phase = JellyPlayEventsSessionMachine.onGateOpened(phase)
        assertEquals(EventsRunning, phase, "the toggle re-opened — replace-never-stack")

        phase = JellyPlayEventsSessionMachine.onAuthFalse(phase)
        assertEquals(TornDown, phase, "sign-out tears the session down")

        phase = JellyPlayEventsSessionMachine.onAuthTrue(phase)
        assertEquals(ProbePending, phase, "a re-auth restarts from the teardown")
    }
}
