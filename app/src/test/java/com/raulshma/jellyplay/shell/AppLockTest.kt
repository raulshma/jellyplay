package com.raulshma.jellyplay.shell

import com.raulshma.jellyplay.core.datastore.security.SecuritySlice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins for the app-lock plumbing shared by both hosts:
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
 *    configured the unlocked flag is irrelevant. [AppLockRedirect.shellGate]
 *    is MainActivity's composition branch (locked + unread persisted slice →
 *    Blocked, fail closed), and [AppLockRedirect.shouldRelock] is the
 *    auto-lock-on-resume fold (formerly inline in MainActivity.onResume) with
 *    its own truth table below.
 *  - [AppLockReturn.foldReturnPolicy] — the ONE return fold both hosts run
 *    (MainActivity.onResume; PlayerActivity's return gate): a landed persisted
 *    slice delegates to [AppLockState.onResumed] with its values, a timed-out
 *    read fails closed — pinned in [AppLockReturnFoldTest] below.
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
    fun `unlock consumes a pending background stamp`() {
        // The challenge a relock raises resolves the absence its stamp
        // measures: the fail-closed timeout relock and PlayerActivity's
        // delivery redirect both leave the stamp pending under the lock
        // screen, and a later fold re-measuring that dead absence would
        // relock even a momentary backgrounding past the timer.
        val state = AppLockState()
        state.onBackgrounded(nowMs = 1_000L)

        state.unlock()

        assertTrue(state.unlocked.value)
        assertFalse("authentication resolves the pending absence", state.hasPendingBackground)
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

    // ── hasPendingBackground — the stamp-presence probe hosts consult ─────

    @Test
    fun `no pending background on a fresh holder`() {
        assertFalse(AppLockState().hasPendingBackground)
    }

    @Test
    fun `backgrounding leaves a pending stamp until the next fold consumes it`() {
        // Hosts staging a transition that may skip onPause (PiP entry) check
        // this to record the stamp exactly once; the fold is what clears it.
        val state = AppLockState().apply { unlock() }
        state.onBackgrounded(nowMs = 1_000L)

        assertTrue(state.hasPendingBackground)
        assertFalse(state.onResumed(nowMs = 2_000L, gateConfigured = true, autoLockTimerMs = 60_000L))

        assertFalse(state.hasPendingBackground)
    }

    @Test
    fun `a relocking fold also consumes the stamp`() {
        val state = AppLockState().apply { unlock() }
        state.onBackgrounded(nowMs = 1_000L)

        assertTrue(state.onResumed(nowMs = 61_000L, gateConfigured = true, autoLockTimerMs = 60_000L))

        assertFalse(state.hasPendingBackground)
    }

    // ── onBackgroundedIfNonePending — the preserving record ───────────────
    // ── (PlayerActivity's onPause / PiP entry can fire twice per absence) ──

    @Test
    fun `a preserving backgrounding records a stamp when none is pending`() {
        // Normal pause-first behavior: with nothing outstanding, the pause IS
        // the start of the absence and must be foldable.
        val state = AppLockState().apply { unlock() }

        state.onBackgroundedIfNonePending(nowMs = 1_000L)

        assertTrue(state.hasPendingBackground)
        assertTrue(state.onResumed(nowMs = 61_000L, gateConfigured = true, autoLockTimerMs = 60_000L))
    }

    @Test
    fun `a preserving backgrounding keeps an outstanding stamp - the clock is not reset`() {
        // PiP entry stamped the absence; a later pause inside the same
        // absence must not move the clock forward — the fold measures from
        // 1_000, so it relocks. (A 9_000 stamp would leave 52_000 elapsed,
        // one timer short.)
        val state = AppLockState().apply { unlock() }
        state.onBackgrounded(nowMs = 1_000L)

        state.onBackgroundedIfNonePending(nowMs = 9_000L)

        assertTrue(state.hasPendingBackground)
        assertTrue(state.onResumed(nowMs = 61_000L, gateConfigured = true, autoLockTimerMs = 60_000L))
    }

    @Test
    fun `a shell pause at expand time keeps the PiP-entry stamp - the 60s timer stays expired`() {
        // ADR 0018, Decisions 5-6: PiP entry stamps the absence at 1_000; the
        // shell sits RESUMED beneath the docked window and pauses when the
        // player expands at 62_000 — ALSO still inside the same absence,
        // because the player's own resume fold runs after its onPause. A shell
        // pause recorder that overwrote the stamp with 62_000 would reset the
        // clock and let the expand fold measure zero elapsed; the preserving
        // recorder leaves 1_000, so the 60_000 timer is still expired.
        val state = AppLockState().apply { unlock() }
        state.onBackgrounded(nowMs = 1_000L) // PiP entry
        state.onBackgroundedIfNonePending(nowMs = 62_000L) // shell onPause at expand

        assertTrue(state.hasPendingBackground)
        assertTrue(state.onResumed(nowMs = 62_000L, gateConfigured = true, autoLockTimerMs = 60_000L))
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

    // ── shellGate — MainActivity's composition branch (hydration-aware) ───

    @Test
    fun `unlocked renders browse regardless of the persisted slice`() {
        assertEquals(
            AppLockRedirect.ShellGate.Browse,
            AppLockRedirect.shellGate(pinUnlocked = true, persisted = null),
        )
        assertEquals(
            AppLockRedirect.ShellGate.Browse,
            AppLockRedirect.shellGate(pinUnlocked = true, persisted = SecuritySlice(pinLockEnabled = true)),
        )
    }

    @Test
    fun `locked before the persisted slice lands blocks the shell - fail closed`() {
        // preferences seeds all-defaults ("no gate configured") until the
        // first DataStore emission; deciding from it would flash browse
        // content pre-hydration, so an unread slice must block.
        assertEquals(
            AppLockRedirect.ShellGate.Blocked,
            AppLockRedirect.shellGate(pinUnlocked = false, persisted = null),
        )
    }

    @Test
    fun `locked with a persisted gate renders the lock screen`() {
        assertEquals(
            AppLockRedirect.ShellGate.LockScreen,
            AppLockRedirect.shellGate(pinUnlocked = false, persisted = SecuritySlice(pinLockEnabled = true)),
        )
        assertEquals(
            AppLockRedirect.ShellGate.LockScreen,
            AppLockRedirect.shellGate(pinUnlocked = false, persisted = SecuritySlice(biometricLockEnabled = true)),
        )
    }

    @Test
    fun `locked with no persisted gate renders browse`() {
        assertEquals(
            AppLockRedirect.ShellGate.Browse,
            AppLockRedirect.shellGate(pinUnlocked = false, persisted = SecuritySlice()),
        )
    }

    // ── gateSnapshotStale — the shell's persisted-slice invalidation ──────

    @Test
    fun `no loaded snapshot is stale - the first persisted read always runs`() {
        assertTrue(
            AppLockRedirect.gateSnapshotStale(
                loaded = null,
                projectedPinLockEnabled = false,
                projectedBiometricLockEnabled = false,
                projectedPinHash = null,
            )
        )
    }

    @Test
    fun `a snapshot matching the projection is not stale`() {
        assertFalse(
            AppLockRedirect.gateSnapshotStale(
                loaded = SecuritySlice(pinLockEnabled = true, pinHash = "hash"),
                projectedPinLockEnabled = true,
                projectedBiometricLockEnabled = false,
                projectedPinHash = "hash",
            )
        )
    }

    @Test
    fun `enabling the PIN flag alone invalidates the pre-toggle no-gate snapshot`() {
        // The exact stale-Browse bug: the shell loaded a no-gate slice at the
        // previous lock; Settings enables the PIN flag without moving
        // `unlocked`, so the projection disagrees and the snapshot must be
        // dropped and re-read (fail closed to Blocked until it lands) instead
        // of keeping the shell on Browse for the activity's lifetime. The
        // projected hash stays equal to the loaded snapshot's so this case
        // isolates the PIN-flag term — the hash-move case has its own test.
        assertTrue(
            AppLockRedirect.gateSnapshotStale(
                loaded = SecuritySlice(),
                projectedPinLockEnabled = true,
                projectedBiometricLockEnabled = false,
                projectedPinHash = null,
            )
        )
    }

    @Test
    fun `enabling the biometric flag alone invalidates an otherwise matching snapshot`() {
        // Isolates the biometric term: every other gate input matches the
        // loaded snapshot, so the staleness can only come from
        // projectedBiometricLockEnabled.
        assertTrue(
            AppLockRedirect.gateSnapshotStale(
                loaded = SecuritySlice(pinLockEnabled = true, pinHash = "hash"),
                projectedPinLockEnabled = true,
                projectedBiometricLockEnabled = true,
                projectedPinHash = "hash",
            )
        )
    }

    @Test
    fun `a changed PIN hash invalidates an otherwise matching snapshot`() {
        // Gate configuration unchanged, but the challenge would verify the
        // OLD hash — the snapshot must still be re-read.
        assertTrue(
            AppLockRedirect.gateSnapshotStale(
                loaded = SecuritySlice(pinLockEnabled = true, pinHash = "old"),
                projectedPinLockEnabled = true,
                projectedBiometricLockEnabled = false,
                projectedPinHash = "new",
            )
        )
    }

    @Test
    fun `the all-defaults projection seed does not invalidate a matching no-gate snapshot`() {
        // Pre-hydration the projection IS all-defaults; a loaded no-gate
        // slice agrees with it, so the warm snapshot is not blanked into a
        // spurious Blocked on every emission.
        assertFalse(
            AppLockRedirect.gateSnapshotStale(
                loaded = SecuritySlice(),
                projectedPinLockEnabled = false,
                projectedBiometricLockEnabled = false,
                projectedPinHash = null,
            )
        )
    }
}

/**
 * Pins for [AppLockReturn.foldReturnPolicy] — the ONE return fold both hosts
 * run (MainActivity.onResume; PlayerActivity's return gate). A landed
 * persisted slice delegates to [AppLockState.onResumed] with ITS values; a
 * null slice (the bounded read timed out) fails CLOSED: an unlocked app with
 * a pending stamp re-locks and keeps the stamp for the challenge to resolve
 * (the successful unlock consumes it — the authentication resolves the
 * absence, so the next fold measures a fresh one).
 */
class AppLockReturnFoldTest {

    @Test
    fun `a landed slice delegates the fold with its persisted values`() {
        val state = AppLockState().apply { unlock() }
        state.onBackgrounded(nowMs = 1_000L)

        AppLockReturn.foldReturnPolicy(
            lockState = state,
            persisted = SecuritySlice(pinLockEnabled = true, autoLockTimerMs = 60_000L),
            nowMs = 61_000L,
        )

        assertFalse(state.unlocked.value)
        assertFalse("the delegated fold consumes the stamp", state.hasPendingBackground)
    }

    @Test
    fun `a landed in-window slice keeps the app unlocked and consumes the stamp`() {
        val state = AppLockState().apply { unlock() }
        state.onBackgrounded(nowMs = 1_000L)

        AppLockReturn.foldReturnPolicy(
            lockState = state,
            persisted = SecuritySlice(pinLockEnabled = true, autoLockTimerMs = 60_000L),
            nowMs = 2_000L,
        )

        assertTrue(state.unlocked.value)
        assertFalse(state.hasPendingBackground)
    }

    @Test
    fun `a landed slice with no gate configured never relocks`() {
        val state = AppLockState().apply { unlock() }
        state.onBackgrounded(nowMs = 1_000L)

        AppLockReturn.foldReturnPolicy(
            lockState = state,
            persisted = SecuritySlice(autoLockTimerMs = 60_000L),
            nowMs = 5_000_000L,
        )

        assertTrue(state.unlocked.value)
        assertFalse(state.hasPendingBackground)
    }

    @Test
    fun `a timed-out read fails closed by relocking an unlocked app and keeps the stamp`() {
        // The security slice could not be read inside the bound: the policy
        // is unknowable, so the stale unlocked flag must not survive a
        // return after a backgrounding. The stamp stays so the challenge
        // the relock raises can be resolved by unlock (which consumes it).
        val state = AppLockState().apply { unlock() }
        state.onBackgrounded(nowMs = 1_000L)

        AppLockReturn.foldReturnPolicy(lockState = state, persisted = null, nowMs = 2_000L)

        assertFalse("an unread policy must fail closed", state.unlocked.value)
        assertTrue("the unconsumed stamp stays for the next evaluator", state.hasPendingBackground)
    }

    @Test
    fun `a short absence after a fail-closed relock and unlock does not relock again`() {
        // The timeout relock keeps the stamp; the challenge it raises is the
        // absence's resolution, so the unlock that answers it consumes the
        // stamp. Without that, the next fold would measure the dead stamp
        // (10s elapsed >= the 5s timer) and relock even though the fresh
        // absence is 1s.
        val state = AppLockState().apply { unlock() }
        state.onBackgrounded(nowMs = 1_000L)
        AppLockReturn.foldReturnPolicy(lockState = state, persisted = null, nowMs = 2_000L)
        assertFalse(state.unlocked.value)
        assertTrue(state.hasPendingBackground)

        state.unlock() // the user answers the challenge
        state.onBackgroundedIfNonePending(nowMs = 10_000L) // a fresh, short absence

        AppLockReturn.foldReturnPolicy(
            lockState = state,
            persisted = SecuritySlice(pinLockEnabled = true, autoLockTimerMs = 5_000L),
            nowMs = 11_000L,
        )

        assertTrue("a fresh 1s absence inside the 5s timer must not relock", state.unlocked.value)
        assertFalse(state.hasPendingBackground)
    }

    @Test
    fun `a timed-out read without a pending stamp leaves the holder untouched`() {
        val state = AppLockState().apply { unlock() }

        AppLockReturn.foldReturnPolicy(lockState = state, persisted = null, nowMs = 2_000L)

        assertTrue(state.unlocked.value)
        assertFalse(state.hasPendingBackground)
    }

    @Test
    fun `a timed-out read keeps an already-locked app locked`() {
        val state = AppLockState()

        AppLockReturn.foldReturnPolicy(lockState = state, persisted = null, nowMs = 2_000L)

        assertFalse(state.unlocked.value)
    }

    // ── isGenuineShellReturn — the docked-resume exclusion (ADR 0018 D5) ──
    // The caller wires the second argument from its own onStop record: the
    // dock-session id (AndroidPipController.pipSession) current at the stop,
    // matched against the CURRENT session (`recorded != 0 && recorded ==
    // current`) — the pins below spell the truth table out.

    @Test
    fun `a shell resume beneath a docked PiP window is not a genuine return`() {
        // Folding there would consume the player's PiP-entry stamp, letting
        // the later expand past the auto-lock timer skip the challenge. Both
        // caller stories map here: the entry reveal (the shell never
        // backgrounded — no record), and a record from an EARLIER dock
        // session (the caller's `recorded != 0 && recorded == current` wiring
        // refuses a stale session, so the newer session's reveal keeps its
        // exemption).
        assertFalse(
            AppLockReturn.isGenuineShellReturn(
                playerDockedInPip = true,
                shellBackgroundedDuringDockedSession = false,
            )
        )
    }

    @Test
    fun `a shell resume with no docked PiP window is a genuine return`() {
        assertTrue(
            AppLockReturn.isGenuineShellReturn(
                playerDockedInPip = false,
                shellBackgroundedDuringDockedSession = false,
            )
        )
    }

    @Test
    fun `a docked shell resume after the shell backgrounded in the same dock session is a genuine return`() {
        // The finding-3/9 scenario: docked → the shell revealed beneath the
        // floating window → home/recents backgrounded the task (shell onStop
        // recorded THIS session) → returning from recents re-enters the app
        // even though the window still floats — the PIN/timer policy must
        // fold, or browse behind the window sits unlocked past the timer.
        assertTrue(
            AppLockReturn.isGenuineShellReturn(
                playerDockedInPip = true,
                shellBackgroundedDuringDockedSession = true,
            )
        )
    }

    @Test
    fun `skipping the docked shell resume leaves the stamp for the expand fold`() {
        // The full sequence the exclusion protects: PiP entry stamps at
        // 1_000; the shell resumes beneath the docked window at 62_000 and
        // must NOT fold; the player's expand fold then measures the ORIGINAL
        // absence and relocks.
        val state = AppLockState().apply { unlock() }
        state.onBackgrounded(nowMs = 1_000L) // PiP entry
        val persisted = SecuritySlice(pinLockEnabled = true, autoLockTimerMs = 60_000L)

        if (AppLockReturn.isGenuineShellReturn(
                playerDockedInPip = true,
                shellBackgroundedDuringDockedSession = false,
            )
        ) {
            AppLockReturn.foldReturnPolicy(lockState = state, persisted = persisted, nowMs = 62_000L)
        }

        assertTrue("the docked shell resume must not consume the stamp", state.hasPendingBackground)
        assertTrue(state.unlocked.value)

        AppLockReturn.foldReturnPolicy(lockState = state, persisted = persisted, nowMs = 62_500L)

        assertFalse("the expand fold relocks off the original stamp", state.unlocked.value)
        assertFalse("the expand fold consumes the stamp", state.hasPendingBackground)
    }

    @Test
    fun `a docked shell return from outside the app folds the policy like any return`() {
        // The scenario the session-matched record unlocks: entry stamps at
        // 1_000; the reveal resumes-and-skips; the shell backgrounds in the
        // SAME dock session and returns at 62_000 (window still docked) —
        // this resume IS the return, so the fold relocks and consumes.
        val state = AppLockState().apply { unlock() }
        state.onBackgrounded(nowMs = 1_000L) // PiP entry
        val persisted = SecuritySlice(pinLockEnabled = true, autoLockTimerMs = 60_000L)

        if (AppLockReturn.isGenuineShellReturn(
                playerDockedInPip = true,
                shellBackgroundedDuringDockedSession = true,
            )
        ) {
            AppLockReturn.foldReturnPolicy(lockState = state, persisted = persisted, nowMs = 62_000L)
        }

        assertFalse("the out-of-app return relocks despite the docked window", state.unlocked.value)
        assertFalse("the return fold consumes the stamp", state.hasPendingBackground)
    }
}
