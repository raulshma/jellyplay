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
 *    unlock/lock flip the [AppLockState.unlocked] StateFlow. It also owns the
 *    auto-lock timer plumbing that left MainActivity (onBackgrounded stamp +
 *    onResumed fold), pinned here as a state machine.
 *  - [AppLockRedirect] — the pure predicate + redirect rule PlayerActivity's
 *    onCreate/onNewIntent gate consumes. The truth table is the whole
 *    contract: redirect ONLY with a configured gate AND locked; with no gate
 *    configured the unlocked flag is irrelevant. [AppLockRedirect.shouldRelock]
 *    is the auto-lock-on-resume fold (formerly inline in MainActivity.onResume)
 *    with its own truth table below.
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

    // ── onBackgrounded / onResumed — the auto-lock state machine that left ─
    // ── MainActivity (the former `backgroundedAt` var + onResume fold)    ─

    @Test
    fun `resume without a prior background is a no-op`() {
        // The 0L sentinel means "never backgrounded": no relock, no
        // transition, even with a gate + timer configured and an infinite gap.
        val state = AppLockState().apply { unlock() }

        val relocked = state.onResumed(
            nowMs = Long.MAX_VALUE,
            gateConfigured = true,
            autoLockTimerMs = 60_000L,
        )

        assertFalse(relocked)
        assertTrue(state.unlocked.value)
    }

    @Test
    fun `resume after the timer elapsed while backgrounded relocks`() {
        val state = AppLockState().apply { unlock() }
        state.onBackgrounded(nowMs = 1_000L)

        val relocked = state.onResumed(
            nowMs = 61_000L, // elapsed == timerMs exactly → lock
            gateConfigured = true,
            autoLockTimerMs = 60_000L,
        )

        assertTrue(relocked)
        assertFalse(state.unlocked.value)
    }

    @Test
    fun `resume one millisecond short of the timer keeps the app unlocked`() {
        val state = AppLockState().apply { unlock() }
        state.onBackgrounded(nowMs = 1_000L)

        val relocked = state.onResumed(
            nowMs = 60_999L, // elapsed == timerMs - 1 → stay unlocked
            gateConfigured = true,
            autoLockTimerMs = 60_000L,
        )

        assertFalse(relocked)
        assertTrue(state.unlocked.value)
    }

    @Test
    fun `second resume after the auto-lock does not relock again`() {
        // The first resume consumed the background stamp, so a later resume
        // — however far in the future — can't relock off the stale stamp.
        val state = AppLockState().apply { unlock() }
        state.onBackgrounded(nowMs = 1_000L)
        state.onResumed(nowMs = 61_000L, gateConfigured = true, autoLockTimerMs = 60_000L)

        val relockedAgain = state.onResumed(
            nowMs = 1_000_000L,
            gateConfigured = true,
            autoLockTimerMs = 60_000L,
        )

        assertFalse(relockedAgain)
        assertFalse(state.unlocked.value)
    }

    @Test
    fun `a short resume still consumes the background stamp`() {
        // Mirrors the old MainActivity.onResume: backgroundedAt reset to 0 on
        // EVERY resume that followed a background — including ones where the
        // timer hadn't elapsed — so a subsequent resume can't accumulate the
        // two absences into a relock.
        val state = AppLockState().apply { unlock() }
        state.onBackgrounded(nowMs = 1_000L)

        assertFalse(state.onResumed(nowMs = 2_000L, gateConfigured = true, autoLockTimerMs = 60_000L))
        assertTrue(state.unlocked.value)
        assertFalse(state.onResumed(nowMs = 5_000_000L, gateConfigured = true, autoLockTimerMs = 60_000L))
        assertTrue(state.unlocked.value)
    }

    @Test
    fun `relock decision follows the gate and timer inputs`() {
        // Same elapsed gap as the firing case, but with no gate or a disabled
        // timer the resume must never transition.
        val state = AppLockState().apply { unlock() }
        state.onBackgrounded(nowMs = 1_000L)

        assertFalse(state.onResumed(nowMs = 61_000L, gateConfigured = false, autoLockTimerMs = 60_000L))
        assertTrue(state.unlocked.value)
        assertFalse(state.onResumed(nowMs = 61_000L, gateConfigured = true, autoLockTimerMs = 0L))
        assertTrue(state.unlocked.value)
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

    // ── shouldRelock — the auto-lock-on-resume fold (ex-MainActivity.onResume) ──

    @Test
    fun `never relocks when no gate is configured`() {
        assertFalse(
            AppLockRedirect.shouldRelock(
                gateConfigured = false,
                autoLockTimerMs = 60_000L,
                backgroundedAtMs = 1_000L,
                nowMs = 5_000_000L,
            )
        )
    }

    @Test
    fun `never relocks when the auto-lock timer is disabled`() {
        assertFalse(
            AppLockRedirect.shouldRelock(
                gateConfigured = true,
                autoLockTimerMs = 0L,
                backgroundedAtMs = 1_000L,
                nowMs = 5_000_000L,
            )
        )
    }

    @Test
    fun `relocks when the absence reached exactly the timer`() {
        assertTrue(
            AppLockRedirect.shouldRelock(
                gateConfigured = true,
                autoLockTimerMs = 60_000L,
                backgroundedAtMs = 1_000L,
                nowMs = 61_000L,
            )
        )
    }

    @Test
    fun `stays unlocked one millisecond short of the timer`() {
        assertFalse(
            AppLockRedirect.shouldRelock(
                gateConfigured = true,
                autoLockTimerMs = 60_000L,
                backgroundedAtMs = 1_000L,
                nowMs = 60_999L,
            )
        )
    }

    @Test
    fun `backgroundedAt zero means never backgrounded - never relocks`() {
        // 0L is the "no prior background" sentinel, not the epoch: without
        // this arm, nowMs - 0 would always exceed the timer and every resume
        // would relock.
        assertFalse(
            AppLockRedirect.shouldRelock(
                gateConfigured = true,
                autoLockTimerMs = 60_000L,
                backgroundedAtMs = 0L,
                nowMs = Long.MAX_VALUE,
            )
        )
    }
}
