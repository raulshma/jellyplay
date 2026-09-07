package com.raulshma.jellyplay.web

/**
 * The pure reconcile core behind the web shell's browser-history mirror
 * (extracted from WebAppRoot's local closures over the back stack +
 * `window.history`). The snapshot list IS the single owner of truth; history
 * MIRRORS it. This type decides WHAT to do; the composable's thin adapter
 * applies the decisions — trimming the list and translating
 * [WebHistoryCommand]s into pushState/replaceState/back/go calls.
 *
 *  - Push (`onEntryAdded`): the stack just appended an entry; mirror it with
 *    pushState under the hash "#wp=<newTopIndex>". History entries carry NO
 *    state payload (an empty object would need JS interop gymnastics; the
 *    hash encodes the same fact and survives reload-free navigation equally
 *    well).
 *  - Pop — the ACTIVE pop paths (the explicit Back button and
 *    NavDisplay.onBack) go through `requestPop`, DISPATCH-FIRST: a
 *    registered JellyPlayBackHandler gets the press FIRST and, when one
 *    consumes it, the shell pops nothing (the registrant owns that press —
 *    dismiss a sheet, close an overlay). Only when the dispatch reports no
 *    consumer does the guarded pop run: mutate the list FIRST (UI stays
 *    correct even if the popstate event never arrives), then history.back()
 *    so the browser cursor follows. Registrants can therefore never bypass
 *    the trimming by construction — they either handle the press (no pop at
 *    all) or decline it into the ONE guarded pop path; a raw history.back()
 *    from a registrant would break the mirror and remains wrong.
 *  - The root-refuse guard rides `requestPop` only, refusing at size <= 1
 *    (an emptied stack crashes NavDisplay) — checked AFTER the
 *    dispatch-first arm, so the explicit Back button goes inert at the root
 *    instead of popping the shell off the page, while a registered handler
 *    still consumes presses even at the root pane (Android on-back parity:
 *    a sheet over the home screen dismisses on back).
 *  - RELOAD mid-stack: composition restarts on the landing pane whatever
 *    "#wp=N" survives in the address bar, and `normalizeBootHash` rewrites
 *    THAT CURRENT entry down to the restarted stack's top via replaceState
 *    (no new history slot) so the mirror matches the restarted stack.
 *    Deeper pre-reload entries further along the session trail keep their
 *    stale hashes; surfacing one via Back/Forward simply re-runs
 *    `reconcilePopState` against the LIVE list, so an old hash can never
 *    talk the shell into a stack shape it did not choose itself.
 *  - Browser-initiated Back/Forward fires 'popstate';
 *    `reconcilePopState` reconciles the list DOWNWARD to the hashed depth
 *    (a trim — dropping panes whose state was never persisted) and treats
 *    a missing/foreign hash as depth 0.
 *  - Forward onto a pruned level walks the cursor BACK to our top
 *    ([WebHistoryCommand.GoTo] with a negative delta): panes dropped by an
 *    earlier local trim are never resurrected, so each Forward press past
 *    our real top permanently BURNS those ghost slots in this tab's session
 *    trail (accepted v1 walk-back contract).
 *
 * Pure (no DOM, no Compose), so the whole rule set is pinned browser-free
 * by WebBackStackMirrorTest on the wasmJs Node runner.
 */

/** Location-hash prefix carrying the mirrored stack depth: "#wp=<index>". */
internal const val HISTORY_HASH_PREFIX = "#wp="

/** Parses a location hash in the "#wp=<index>" form into its stack index. */
internal fun historyHashToIndex(hash: String): Int? =
    if (hash.startsWith(HISTORY_HASH_PREFIX)) {
        hash.removePrefix(HISTORY_HASH_PREFIX).toIntOrNull()
    } else {
        null
    }

/** Builds the "#wp=<index>" location hash for a stack [index]. */
internal fun indexToHistoryHash(index: Int): String = "$HISTORY_HASH_PREFIX$index"

/**
 * The history-slot effects [WebBackStackMirror] decisions translate to — the
 * adapter's entire `window.history` vocabulary.
 */
internal sealed interface WebHistoryCommand {
    /** pushState a fresh slot mirroring the (already appended) stack top. */
    data class Push(val hash: String) : WebHistoryCommand

    /** replaceState the CURRENT slot's hash only — no new history slot. */
    data class Rewrite(val hash: String) : WebHistoryCommand

    /** history.back() — follow the cursor back after a local pop. */
    data object NavigateBack : WebHistoryCommand

    /** history.go(delta) — forward onto a pruned level walks the cursor back to our top (delta < 0). */
    data class GoTo(val delta: Int) : WebHistoryCommand

    /** No history touch. */
    data object None : WebHistoryCommand
}

/** A popstate reconcile: an optional downward trim plus at most one history command. */
internal data class WebBackStackReconcile(
    /** Trim the stack to this depth (keep depth+1 entries); null leaves the list alone. */
    val trimToDepth: Int?,
    val command: WebHistoryCommand,
)

/** The decision core — stack sizes and hashes in, commands out. */
internal object WebBackStackMirror {

    /**
     * addEntry: the stack just appended a top at [newTopIndex] — mirror it as
     * a fresh history slot.
     */
    fun onEntryAdded(newTopIndex: Int): WebHistoryCommand =
        WebHistoryCommand.Push(indexToHistoryHash(newTopIndex))

    /**
     * THE guarded pop path (dispatch-first). A non-null result means "pop the
     * stack's top locally, then apply the command" (local trim before the
     * cursor move so the UI never waits on the async history turn); null
     * means the press is REFUSED and nothing — list or history — is touched:
     * either [pressConsumed] (a registered JellyPlayBackHandler owned that
     * press) or the stack sits at its root ([stackSize] <= 1).
     */
    fun requestPop(stackSize: Int, pressConsumed: Boolean): WebHistoryCommand? = when {
        pressConsumed -> null
        stackSize <= 1 -> null
        else -> WebHistoryCommand.NavigateBack
    }

    /**
     * Reload mid-stack lands with a stale "#wp=N" in the address bar while
     * the restarted stack holds only its boot entries — the address bar must
     * stop disagreeing. Rewrites the CURRENT entry only (replaceState, no
     * new history slot) down to the restarted stack's top ([stackSize] - 1);
     * no-ops for a missing/foreign/"#wp=0" boot hash. Stale hashes on DEEPER
     * pre-reload entries are left alone on purpose; every popstate arrival
     * is judged against the live list, never against stored hashes.
     */
    fun normalizeBootHash(bootHash: String, stackSize: Int): WebHistoryCommand {
        val bootIndex = historyHashToIndex(bootHash)
        return if (bootIndex != null && bootIndex != 0) {
            WebHistoryCommand.Rewrite(indexToHistoryHash(stackSize - 1))
        } else {
            WebHistoryCommand.None
        }
    }

    /**
     * Browser-initiated Back/Forward reconciliation against the hashed depth
     * of [currentHash]. A missing/foreign hash or one at/below the root is
     * depth 0 (trim all the way down — dropping panes whose state was never
     * persisted); a hash shallower than the stack trims to exactly it; a
     * hash at or beyond the stack's top is a Forward onto a pruned level —
     * walk the cursor back to our top and leave the list alone.
     */
    fun reconcilePopState(currentHash: String, stackSize: Int): WebBackStackReconcile {
        val targetIndex = historyHashToIndex(currentHash)
        return when {
            targetIndex == null || targetIndex <= 0 -> WebBackStackReconcile(
                trimToDepth = 0,
                command = WebHistoryCommand.None,
            )
            targetIndex < stackSize -> WebBackStackReconcile(
                trimToDepth = targetIndex,
                command = WebHistoryCommand.None,
            )
            else -> WebBackStackReconcile(
                trimToDepth = null,
                command = WebHistoryCommand.GoTo((stackSize - 1) - targetIndex),
            )
        }
    }
}
