package com.raulshma.jellyplay.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wave 20E pins for the app-lock plumbing shared by both hosts:
 *
 *  - [AppLockState] — the Koin-single unlocked flag hoisted off
 *    MainActivity's former compose-local `isPinUnlocked` state. The holder is
 *    deliberately dumb (no timers, no gate-config knowledge), so the pins are
 *    the transition semantics: starts locked (cold-process default),
 *    unlock/lock flip the [AppLockState.unlocked] StateFlow.
 *  - [AppLockRedirect] — the pure predicate + redirect rule PlayerActivity's
 *    onCreate/onNewIntent gate consumes. The truth table is the whole
 *    contract: redirect ONLY with a configured gate AND locked; with no gate
 *    configured the unlocked flag is irrelevant.
 *
 * The activity-level redirect behavior (startActivity(MainActivity) + finish)
 * is covered by `PlayerActivityLockRedirectTest` under Robolectric.
 */
class AppLockStateTest {

    @Test
    fun `starts locked - cold process default`() {
        // Locked-by-default is what makes the PlayerActivity redirect close
        // the media-notification bypass on a cold/restored process: the
        // holder carries no persistence, so process death re-locks.
        assertFalse(AppLockState().unlocked.value)
    }

    @Test
    fun `unlock flips the flow to true`() {
        val state = AppLockState()

        state.unlock()

        assertTrue(state.unlocked.value)
    }

    @Test
    fun `lock flips the flow back to false`() {
        val state = AppLockState().apply { unlock() }

        state.lock()

        assertFalse(state.unlocked.value)
    }

    @Test
    fun `unlock is idempotent and stays true until an explicit lock`() {
        val state = AppLockState()

        state.unlock()
        state.unlock()

        assertTrue(state.unlocked.value)
    }

    @Test
    fun `unlocked flow carries the current value to new collectors`() {
        // MainActivity collects this flow for its gate; a new collector
        // (recomposition, or PlayerActivity reading .value) must see the
        // latest transition, not a stale one.
        val state = AppLockState()
        state.unlock()
        state.lock()
        state.unlock()

        assertEquals(true, state.unlocked.value)
    }
}

class AppLockRedirectTest {

    // ── isGateConfigured — MainActivity's gate predicate, shared ──────────

    @Test
    fun `gate predicate is false only when neither lock is enabled`() {
        assertFalse(AppLockRedirect.isGateConfigured(pinLockEnabled = false, biometricLockEnabled = false))
    }

    @Test
    fun `pin lock alone configures the gate`() {
        assertTrue(AppLockRedirect.isGateConfigured(pinLockEnabled = true, biometricLockEnabled = false))
    }

    @Test
    fun `biometric lock alone configures the gate`() {
        assertTrue(AppLockRedirect.isGateConfigured(pinLockEnabled = false, biometricLockEnabled = true))
    }

    @Test
    fun `both locks together configure the gate`() {
        assertTrue(AppLockRedirect.isGateConfigured(pinLockEnabled = true, biometricLockEnabled = true))
    }

    // ── shouldRedirect — the PlayerActivity hand-off rule ─────────────────

    @Test
    fun `redirects only when a gate is configured and the app is locked`() {
        assertTrue(AppLockRedirect.shouldRedirect(gateConfigured = true, unlocked = false))
    }

    @Test
    fun `never redirects when the app is unlocked`() {
        // The whole point of the holder: a cleared challenge in MainActivity
        // is visible to PlayerActivity's gate through the same flag.
        assertFalse(AppLockRedirect.shouldRedirect(gateConfigured = true, unlocked = true))
    }

    @Test
    fun `never redirects when no gate is configured - locked flag irrelevant`() {
        assertFalse(AppLockRedirect.shouldRedirect(gateConfigured = false, unlocked = false))
        assertFalse(AppLockRedirect.shouldRedirect(gateConfigured = false, unlocked = true))
    }
}
