package com.raulshma.jellyplay.core.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Pins the shared "press back again to exit" policy consumed by the phone
 * shell (`JellyPlayApp.kt`'s `PhoneContent`) and the TV drawer
 * (`TvNavigationDrawer`) BackHandler feeds:
 *
 *  - the confirmation window is an exclusive bound — presses exactly
 *    [BackExitConfirmation.DEFAULT_EXIT_CONFIRMATION_TIMEOUT_MS] apart have
 *    already expired and re-prompt;
 *  - [BackExitConfirmation.Decision.Prompt] stamps the clock (carries the
 *    `nowMs` the feed must record) and [Decision.Exit] tells the feed to reset
 *    it, so prompt → exit → prompt never exits on the third press;
 *  - outside an exit point the decision is always [Decision.Pop].
 */
class BackExitConfirmationTest {

    private val policy = BackExitConfirmation()

    // ── window boundary ────────────────────────────────────────────────────

    @Test
    fun aPress1999MsAfterTheLastOneExits() {
        val decision = policy.onBack(nowMs = 10_000L, lastAtMs = 8_001L)

        assertIs<BackExitConfirmation.Decision.Exit>(decision)
    }

    @Test
    fun aPressExactly2000MsAfterTheLastOneHasExpiredAndPrompts() {
        val decision = policy.onBack(nowMs = 10_000L, lastAtMs = 8_000L)

        assertIs<BackExitConfirmation.Decision.Prompt>(decision)
    }

    @Test
    fun theFirstPressWithNoTrackedTimestampPrompts() {
        val decision = policy.onBack(nowMs = 10_000L, lastAtMs = 0L)

        assertIs<BackExitConfirmation.Decision.Prompt>(decision)
    }

    // ── stamp / reset protocol (stateless policy, feed owns the clock) ─────

    @Test
    fun promptStampsTheClockItCarries() {
        val decision = policy.onBack(nowMs = 5_000L, lastAtMs = 0L)

        assertEquals(5_000L, (decision as BackExitConfirmation.Decision.Prompt).nowMs)

        // A press strictly inside the window from that stamp exits.
        assertIs<BackExitConfirmation.Decision.Exit>(policy.onBack(nowMs = 6_999L, lastAtMs = decision.nowMs))
    }

    @Test
    fun promptThenExitThenPromptDoesNotImmediatelyExit() {
        var lastAtMs = 0L
        val policy = BackExitConfirmation()

        // First press at a tab root: prompt, feed stamps the clock.
        val first = policy.onBack(nowMs = 10_000L, lastAtMs = lastAtMs)
        assertIs<BackExitConfirmation.Decision.Prompt>(first)
        lastAtMs = first.nowMs

        // Second press inside the window: exit, feed resets the tracking.
        assertIs<BackExitConfirmation.Decision.Exit>(policy.onBack(nowMs = 11_000L, lastAtMs = lastAtMs))
        lastAtMs = 0L

        // The window restarts closed: the next press prompts again instead of exiting.
        val third = policy.onBack(nowMs = 11_500L, lastAtMs = lastAtMs)
        assertIs<BackExitConfirmation.Decision.Prompt>(third)
    }

    // ── exit-point gate ────────────────────────────────────────────────────

    @Test
    fun outsideAnExitPointTheDecisionIsAlwaysPop() {
        assertTrue(policy.onBack(nowMs = 10_000L, lastAtMs = 0L, atExitPoint = false) is BackExitConfirmation.Decision.Pop)
        assertFalse(policy.onBack(nowMs = 10_000L, lastAtMs = 9_999L, atExitPoint = false) is BackExitConfirmation.Decision.Exit)
    }

    @Test
    fun theWindowIsTheSharedDefault() {
        // 1999 ms after the stamp: still inside the 2000 ms window — exit.
        assertTrue(policy.onBack(nowMs = 10_000L, lastAtMs = 8_001L) is BackExitConfirmation.Decision.Exit)
        // 2000 ms after the stamp: the boundary is exclusive — prompt again.
        assertIs<BackExitConfirmation.Decision.Prompt>(policy.onBack(nowMs = 10_000L, lastAtMs = 8_000L))
    }
}
