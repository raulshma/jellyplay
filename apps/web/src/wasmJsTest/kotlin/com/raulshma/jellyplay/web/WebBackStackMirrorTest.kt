package com.raulshma.jellyplay.web

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Browser-free pin of the web shell's browser-history mirror decision core
 * ([WebBackStackMirror], extracted from WebAppRoot's local closures — its
 * KDoc carries the full model notes). Same lane as
 * [WebConnectControllerTest]: pure decisions executed on the wasmJs Node
 * runner, no browser, no popstate — the DOM adapter half (SnapshotStateList
 * trim + pushState/replaceState/back/go translation) stays covered by the
 * headless-Edge CDP lane (tools/e2e/web-verify.mjs) exactly as before.
 *
 * The five contracts pinned here are the ones whose mistakes only show up in
 * a real browser session:
 *  - pops at the root are refused (an emptied stack crashes NavDisplay) and
 *    a consumed dispatch never pops;
 *  - a reload's surviving "#wp=N" is rewritten down to the restarted stack's
 *    top, and only when it disagrees;
 *  - a popstate hash deeper than the stack is a Forward onto a pruned level
 *    — the cursor walks BACK to our top, ghost panes stay dead;
 *  - a popstate hash shallower than the stack trims to exactly that depth;
 *  - a push mirrors the new top as a fresh "#wp=<index>" slot.
 */
class WebBackStackMirrorTest {

    // ── hash ↔ index parsing ────────────────────────────────────────────────

    @Test
    fun hashParsingAcceptsOnlyWpIndexHashes() {
        assertEquals(0, historyHashToIndex("#wp=0"))
        assertEquals(3, historyHashToIndex("#wp=3"))
        // The empty initial-page hash, foreign fragments and garbage indices
        // are all "not one of our entries" — callers treat null as depth 0.
        assertNull(historyHashToIndex(""))
        assertNull(historyHashToIndex("#section"))
        assertNull(historyHashToIndex("#wp="))
        assertNull(historyHashToIndex("#wp=abc"))
        assertNull(historyHashToIndex("wp=3"))
    }

    @Test
    fun hashRoundTripsThroughTheIndexForm() {
        assertEquals(7, historyHashToIndex(indexToHistoryHash(7)))
    }

    // ── push mirror ─────────────────────────────────────────────────────────

    @Test
    fun pushMirrorsTheNewTopAsAFreshSlot() {
        assertEquals(WebHistoryCommand.Push("#wp=0"), WebBackStackMirror.onEntryAdded(0))
        assertEquals(WebHistoryCommand.Push("#wp=3"), WebBackStackMirror.onEntryAdded(3))
    }

    // ── the guarded pop path (dispatch-first + root refuse) ─────────────────

    @Test
    fun popAtTheRootIsRefused() {
        assertNull(WebBackStackMirror.requestPop(stackSize = 1, pressConsumed = false))
    }

    @Test
    fun aConsumedPressNeverPopsEvenBelowTheRootGuard() {
        // Dispatch-first: the registrant owns the press regardless of depth.
        assertNull(WebBackStackMirror.requestPop(stackSize = 3, pressConsumed = true))
    }

    @Test
    fun anUnconsumedPressBelowTheRootPopsAndFollowsWithBack() {
        assertEquals(
            WebHistoryCommand.NavigateBack,
            WebBackStackMirror.requestPop(stackSize = 3, pressConsumed = false),
        )
    }

    // ── reload hash normalization ───────────────────────────────────────────

    @Test
    fun aReloadSurvivingHashIsRewrittenDownToTheRestartedTop() {
        assertEquals(
            WebHistoryCommand.Rewrite("#wp=0"),
            WebBackStackMirror.normalizeBootHash(bootHash = "#wp=3", stackSize = 1),
        )
    }

    @Test
    fun aBootRouteDeepRestartRewritesDownToTheBootTop() {
        assertEquals(
            WebHistoryCommand.Rewrite("#wp=1"),
            WebBackStackMirror.normalizeBootHash(bootHash = "#wp=4", stackSize = 2),
        )
    }

    @Test
    fun aCompliantBootHashTouchesNothing() {
        assertEquals(WebHistoryCommand.None, WebBackStackMirror.normalizeBootHash("#wp=0", stackSize = 1))
    }

    @Test
    fun aMissingOrForeignBootHashTouchesNothing() {
        assertEquals(WebHistoryCommand.None, WebBackStackMirror.normalizeBootHash("", stackSize = 1))
        assertEquals(WebHistoryCommand.None, WebBackStackMirror.normalizeBootHash("#other", stackSize = 1))
    }

    // ── popstate reconciliation ─────────────────────────────────────────────

    @Test
    fun aMissingOrForeignPopstateHashTrimsAllTheWayToTheRoot() {
        assertEquals(
            WebBackStackReconcile(trimToDepth = 0, command = WebHistoryCommand.None),
            WebBackStackMirror.reconcilePopState("", stackSize = 3),
        )
        assertEquals(
            WebBackStackReconcile(trimToDepth = 0, command = WebHistoryCommand.None),
            WebBackStackMirror.reconcilePopState("#foreign", stackSize = 3),
        )
    }

    @Test
    fun aRootHashPopstateTrimsAllTheWayToTheRoot() {
        assertEquals(
            WebBackStackReconcile(trimToDepth = 0, command = WebHistoryCommand.None),
            WebBackStackMirror.reconcilePopState("#wp=0", stackSize = 3),
        )
    }

    @Test
    fun aShallowerPopstateHashTrimsToExactlyThatDepth() {
        // depth N keeps N+1 entries.
        assertEquals(
            WebBackStackReconcile(trimToDepth = 2, command = WebHistoryCommand.None),
            WebBackStackMirror.reconcilePopState("#wp=2", stackSize = 5),
        )
    }

    @Test
    fun aHashAtTheCurrentTopIsACompliantNoOpTrim() {
        // depth 1 on a 2-entry stack keeps both — the adapter's trim loop
        // no-ops, and no history command fires.
        assertEquals(
            WebBackStackReconcile(trimToDepth = 1, command = WebHistoryCommand.None),
            WebBackStackMirror.reconcilePopState("#wp=1", stackSize = 2),
        )
    }

    @Test
    fun forwardOntoAPrunedLevelWalksTheCursorBackToOurTop() {
        // Stack pruned to 2 entries; the surfaced forward slot says 5 →
        // history.go(-4). The list is left alone (ghost panes stay dead).
        assertEquals(
            WebBackStackReconcile(trimToDepth = null, command = WebHistoryCommand.GoTo(-4)),
            WebBackStackMirror.reconcilePopState("#wp=5", stackSize = 2),
        )
    }

    @Test
    fun forwardOntoTheExactTopWalksBackOneSlot() {
        // targetIndex == size is NOT the trim branch — one ghost slot back.
        assertEquals(
            WebBackStackReconcile(trimToDepth = null, command = WebHistoryCommand.GoTo(-1)),
            WebBackStackMirror.reconcilePopState("#wp=2", stackSize = 2),
        )
    }
}
