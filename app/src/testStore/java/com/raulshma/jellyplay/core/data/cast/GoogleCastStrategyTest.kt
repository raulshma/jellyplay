package com.raulshma.jellyplay.core.data.cast

import android.app.Application
import com.raulshma.jellyplay.core.data.cast.CastSessionPhase.Connected
import com.raulshma.jellyplay.core.data.cast.CastSessionPhase.Connecting
import com.raulshma.jellyplay.core.data.cast.CastSessionPhase.Failed
import com.raulshma.jellyplay.core.data.cast.CastSessionPhase.Idle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Pins [GoogleCastStrategy]'s session-event → phase wiring — the store-lane
 * half of the contract whose pure half [CastSessionPhaseTest] pins in
 * core:data's jvmTest. The strategy's GMS callbacks need a live CastContext,
 * which Robolectric cannot provide, so the suite drives the `session*`
 * handlers the [GoogleCastStrategy.sessionListener] adapter forwards to
 * (the thin-host split; the adapter itself is one-line delegation) and pins
 * the OBSERVABLE projections the rest of the app reads.
 *
 * Pinned here:
 *  - the async connect ladder the GMS listener drives: Starting → Started
 *    lands Connected (isConnected true, isConnecting false);
 *  - DECLARED FIX #1: a failed START clears a stale connected flag. The
 *    former writer cleared only `isConnecting`, so a prior session's
 *    `isConnected=true` survived the failure — the phantom connected dead
 *    session. Now the phase lands in Failed and BOTH projections read false.
 *  - session end resets to Idle; resume failure lands in Failed (same
 *    projections the old `false/false` writes produced, plus the reason);
 *  - an `onSessionResumed` recovery flips Failed back to Connected.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class GoogleCastStrategyTest {

    private fun strategy() = GoogleCastStrategy(RuntimeEnvironment.getApplication())

    // ── connect ladder ────────────────────────────────────────────────────

    @Test
    fun `session starting then started lands the projections connected`() {
        val s = strategy()

        s.sessionStarting()

        assertTrue(s.isConnecting.value)
        assertFalse(s.isConnected.value)
        assertEquals(Connecting, s.phase.value)

        s.sessionStarted()

        assertTrue(s.isConnected.value)
        assertFalse(s.isConnecting.value)
        assertEquals(Connected, s.phase.value)
    }

    // ── DECLARED FIX #1: failed start clears a stale connected flag ───────

    @Test
    fun `a failed session start clears the stale connected flag`() {
        val s = strategy()
        // A prior session marked connected (its onSessionEnded never reached
        // us), then the next start attempt fails.
        s.sessionStarting()
        s.sessionStarted()
        assertTrue(s.isConnected.value) // the stale flag...

        s.sessionStarting()
        s.sessionStartFailed(error = 2200)

        // ...is now honest: the strategy reports a DEAD session, not a
        // connected one (the former writer cleared only isConnecting).
        assertEquals(Failed(reason = "2200"), s.phase.value)
        assertFalse(s.isConnected.value)
        assertFalse(s.isConnecting.value)
    }

    // ── end / resume-failure / recovery ───────────────────────────────────

    @Test
    fun `session ended resets to Idle`() {
        val s = strategy()
        s.sessionStarting()
        s.sessionStarted()

        s.sessionEnded()

        assertEquals(Idle, s.phase.value)
        assertFalse(s.isConnected.value)
        assertFalse(s.isConnecting.value)
    }

    @Test
    fun `resume failure lands in Failed and clears both projections`() {
        val s = strategy()
        s.sessionStarting()
        s.sessionStarted()

        s.sessionResumeFailed(error = 2000)

        assertEquals(Failed(reason = "2000"), s.phase.value)
        assertFalse(s.isConnected.value)
        assertFalse(s.isConnecting.value)
    }

    @Test
    fun `session resumed recovers a Failed phase to Connected`() {
        val s = strategy()
        s.sessionStartFailed(error = 2200)

        s.sessionResumed()

        assertEquals(Connected, s.phase.value)
        assertTrue(s.isConnected.value)
    }
}
