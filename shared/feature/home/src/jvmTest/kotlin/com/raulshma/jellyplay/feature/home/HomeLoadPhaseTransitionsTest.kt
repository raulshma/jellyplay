package com.raulshma.jellyplay.feature.home

import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.Test

/**
 * Direct pins of the pure phase-transition rules ([HomeLoadPhaseTransitions])
 * — the convention §3 decision object beside [HomeLoadPhase]. Each rule here
 * is the compiler-checked summary of a former flag write; the refresher
 * suites ([HomeRefresherTest], [HomeRefresherFetchTest]) pin the same rules
 * end-to-end through the fetch machinery.
 */
class HomeLoadPhaseTransitionsTest {

    // ── settled: the guarded-finally / handshake-clear / stop() rule ────────

    @Test
    fun settled_demotesEverySpinnerPhaseToReady() {
        assertEquals(HomeLoadPhase.Ready, HomeLoadPhaseTransitions.settled(HomeLoadPhase.ColdLoading))
        assertEquals(HomeLoadPhase.Ready, HomeLoadPhaseTransitions.settled(HomeLoadPhase.InlineRefreshing))
        assertEquals(HomeLoadPhase.Ready, HomeLoadPhaseTransitions.settled(HomeLoadPhase.Handshaking))
    }

    @Test
    fun settled_leavesFailedAlone_theFormerClearNeverTouchedError() {
        // The handshake's loader clearing over a failed fetch (or stop() over
        // one) must not swallow the failure: the former isLoading/isRefreshing
        // clears never touched `error`.
        assertEquals(
            HomeLoadPhase.Failed("server down"),
            HomeLoadPhaseTransitions.settled(HomeLoadPhase.Failed("server down")),
        )
    }

    @Test
    fun settled_isANoOpOnReady() {
        assertEquals(HomeLoadPhase.Ready, HomeLoadPhaseTransitions.settled(HomeLoadPhase.Ready))
    }

    // ── succeeded: the former success arm's `error = null` ──────────────────

    @Test
    fun succeeded_recoversFailedToReady() {
        assertEquals(
            HomeLoadPhase.Ready,
            HomeLoadPhaseTransitions.succeeded(HomeLoadPhase.Failed("flaky network")),
        )
        // A null-message Failed (never produced in production — the resolver
        // always yields a string) recovers identically.
        assertEquals(
            HomeLoadPhase.Ready,
            HomeLoadPhaseTransitions.succeeded(HomeLoadPhase.Failed(null)),
        )
    }

    @Test
    fun succeeded_leavesSpinnerPhasesAlone_theGuardedFinallyOwnsTheDemotion() {
        // The success arm must NOT demote a handshake's loader early — the
        // guarded finally settles it, exactly as the former error=null write
        // left isLoading/isRefreshing alone.
        assertEquals(HomeLoadPhase.Handshaking, HomeLoadPhaseTransitions.succeeded(HomeLoadPhase.Handshaking))
        assertEquals(HomeLoadPhase.ColdLoading, HomeLoadPhaseTransitions.succeeded(HomeLoadPhase.ColdLoading))
        assertEquals(HomeLoadPhase.InlineRefreshing, HomeLoadPhaseTransitions.succeeded(HomeLoadPhase.InlineRefreshing))
        assertEquals(HomeLoadPhase.Ready, HomeLoadPhaseTransitions.succeeded(HomeLoadPhase.Ready))
    }

    // ── failed: the former failure arm's `error = resolve(...)` ─────────────

    @Test
    fun failed_replacesAnyActivePhase_spinnerIncluded() {
        // Declared decision (see the rule's KDoc): a failure landing
        // mid-handshake (or mid-cold-load, or mid-pull) replaces the spinner
        // phase — one value cannot hold "spinner up" AND "failure recorded",
        // and the former render fold already resolved the
        // error+isLoading combination in the failure's favor (the HardError
        // surface beat the invisible loader).
        assertEquals(
            HomeLoadPhase.Failed("server down"),
            HomeLoadPhaseTransitions.failed("server down"),
        )
        assertIs<HomeLoadPhase.Failed>(HomeLoadPhaseTransitions.failed(null))
    }
}
