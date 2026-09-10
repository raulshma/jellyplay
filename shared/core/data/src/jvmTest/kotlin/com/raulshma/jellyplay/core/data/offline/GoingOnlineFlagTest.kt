package com.raulshma.jellyplay.core.data.offline

import com.raulshma.jellyplay.core.model.OfflineMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins [GoingOnlineFlag] — the going-online busy flag's single-owner
 * choreography. These are the guarantees the flag's former duplicate owners
 * (the Home refresh handshake's copy and the app-shell ViewModel's mirror)
 * each had to re-implement correctly, and periodically didn't:
 *
 *  * set-on-arm: the flag raises synchronously with the toggle, BEFORE the
 *    async preference write, so the Go Online spinner precedes the mode flip
 *    — including in the cold-start window where the managers' mode flow
 *    still reads its ONLINE default (the persisted mode is derived
 *    asynchronously, so an arm-refusal against that default would silently
 *    skip the spinner);
 *  * clear-on-ONLINE: the mode flow emitting ONLINE clears the flag however
 *    the transition resolves (the toggle's write landing, or an external/auto
 *    reconnect overtaking it) — which also makes a hung post-toggle fetch
 *    structurally unable to park the flag;
 *  * watchdog clear: a flag still up after
 *    [GoingOnlineFlag.DEFAULT_TIMEOUT_MS] force-clears unconditionally — a
 *    lost preference write is the production case, and the unconditional
 *    deadline is what lets arm raise without a mode-value guard (a guard
 *    could only strand a flag armed against an already-ONLINE flow, which
 *    now just ages out at the deadline instead);
 *  * external/auto transitions never raise the flag.
 *
 * Virtual time drives the watchdog; the flag's collectors run on runTest's
 * backgroundScope so teardown cancels them.
 */
class GoingOnlineFlagTest {

    private val mode = MutableStateFlow(OfflineMode.OFFLINE_MANUAL)

    private fun TestScope.buildFlag(): GoingOnlineFlag = GoingOnlineFlag(backgroundScope, mode)

    @Test
    fun `arm raises the flag synchronously`() = runTest {
        val flag = buildFlag()
        runCurrent() // clear-on-ONLINE collector subscribes (initial emission is offline: no-op)

        flag.arm()

        assertTrue(flag.goingOnline.value, "the flag must be up before the async write even starts")
    }

    @Test
    fun `ONLINE emission clears an armed flag`() = runTest {
        val flag = buildFlag()
        runCurrent()

        flag.arm()
        mode.value = OfflineMode.ONLINE
        runCurrent()

        assertFalse(flag.goingOnline.value, "the ONLINE emission is the flag's primary clear")
    }

    @Test
    fun `an armed flag survives offline-only mode churn until ONLINE or the watchdog`() = runTest {
        val flag = buildFlag()
        runCurrent()

        flag.arm()
        // A flip between offline flavours never emits ONLINE — the flag must
        // stay up (still going online) and only the watchdog can clear it.
        mode.value = OfflineMode.OFFLINE_AUTO
        runCurrent()
        assertTrue(flag.goingOnline.value)

        advanceTimeBy(GoingOnlineFlag.DEFAULT_TIMEOUT_MS + 1)
        runCurrent()
        assertFalse(flag.goingOnline.value, "the lost-write watchdog is the only clear left")
    }

    @Test
    fun `a lost preference write is force-cleared by the watchdog`() = runTest {
        val flag = buildFlag()
        runCurrent()

        flag.arm()
        assertTrue(flag.goingOnline.value)

        advanceTimeBy(GoingOnlineFlag.DEFAULT_TIMEOUT_MS + 1)
        runCurrent()

        assertFalse(flag.goingOnline.value, "no ONLINE emission ever landed — the watchdog clears")
    }

    @Test
    fun `the watchdog no-ops once the mode landed ONLINE`() = runTest {
        val flag = buildFlag()
        runCurrent()

        flag.arm()
        mode.value = OfflineMode.ONLINE
        runCurrent()
        assertFalse(flag.goingOnline.value)

        advanceTimeBy(GoingOnlineFlag.DEFAULT_TIMEOUT_MS + 1)
        runCurrent()
        assertFalse(flag.goingOnline.value)
    }

    @Test
    fun `re-arming replaces the watchdog with a fresh window`() = runTest {
        val flag = buildFlag()
        runCurrent()

        flag.arm()
        advanceTimeBy(GoingOnlineFlag.DEFAULT_TIMEOUT_MS - 10_000)
        runCurrent()
        assertTrue(flag.goingOnline.value)

        flag.arm() // second going-online attempt: full window restarts
        advanceTimeBy(GoingOnlineFlag.DEFAULT_TIMEOUT_MS - 10_000)
        runCurrent()
        assertTrue(flag.goingOnline.value, "the first arm's deadline must not clear the re-armed flag")

        advanceTimeBy(10_001)
        runCurrent()
        assertFalse(flag.goingOnline.value, "the re-armed watchdog clears after its own window")
    }

    @Test
    fun `mode transitions alone never raise the flag`() = runTest {
        val flag = buildFlag()
        runCurrent()

        mode.value = OfflineMode.ONLINE
        runCurrent()
        mode.value = OfflineMode.OFFLINE_AUTO
        runCurrent()
        mode.value = OfflineMode.ONLINE
        runCurrent()

        assertFalse(flag.goingOnline.value, "external/auto flips never arm — only the toggle's arm does")
    }

    @Test
    fun `armIfGoingOnline arms on the snapshot direction only`() = runTest {
        val flag = buildFlag()
        runCurrent()

        // An OFFLINE_AUTO toggle goes FURTHER offline: the snapshot says not
        // manual-offline, so no arm — nothing would ever clear the flag.
        flag.armIfGoingOnline(snapshotSaysManualOffline = false)
        runCurrent()
        assertFalse(flag.goingOnline.value, "a toggle that goes further offline must never arm")

        flag.armIfGoingOnline(snapshotSaysManualOffline = true)
        assertTrue(flag.goingOnline.value, "a manual-offline snapshot means the toggle goes online — arm")
    }

    @Test
    fun `a cold-start arm against the ONLINE default still spinners and clears`() = runTest {
        // The managers derive the persisted mode asynchronously over an
        // ONLINE-initialized flow: the very first Go Online toggle can land
        // while the flow still reads its default. The snapshot gate (the
        // persisted manual-offline mode) — not a mode-value guard — decides
        // the arm, so the spinner raises here, the derivation's
        // OFFLINE_MANUAL emission leaves it untouched, and the toggle's
        // write landing (ONLINE emission) clears it well inside the window.
        val flag = GoingOnlineFlag(backgroundScope, mode)
        mode.value = OfflineMode.ONLINE // the pre-derivation default
        runCurrent()

        flag.armIfGoingOnline(snapshotSaysManualOffline = true)
        assertTrue(flag.goingOnline.value, "the cold-start toggle must still raise the spinner")

        mode.value = OfflineMode.OFFLINE_MANUAL // the async derivation lands
        runCurrent()
        assertTrue(flag.goingOnline.value, "an offline derivation must not clear the flag")

        mode.value = OfflineMode.ONLINE // the toggle's write lands
        runCurrent()
        assertFalse(flag.goingOnline.value, "the ONLINE emission is still the primary clear")
    }

    @Test
    fun `an arm against a settled ONLINE mode ages out at the watchdog instead of stranding`() = runTest {
        val flag = buildFlag()
        runCurrent()

        // The unconditional-deadline watchdog is the reason arm needs no
        // mode-value refusal: even this pathological arm (unreachable in
        // production — the snapshot gate refuses it) cannot strand the flag
        // forever; it just ages out at the window.
        mode.value = OfflineMode.ONLINE
        runCurrent()
        flag.arm()
        assertTrue(flag.goingOnline.value, "arm raises unconditionally")

        advanceTimeBy(GoingOnlineFlag.DEFAULT_TIMEOUT_MS + 1)
        runCurrent()
        assertFalse(flag.goingOnline.value, "the watchdog deadline clears it — no emission needed")
    }
}
