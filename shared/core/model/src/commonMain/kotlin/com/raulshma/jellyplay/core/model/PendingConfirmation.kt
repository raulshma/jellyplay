package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable

/**
 * The confirm-dialog STATE ALGEBRA shared by every "tap delete -> dialog ->
 * confirm" flow: one optional pending item plus the four pure writes behind
 * every screen's hold / dismiss / confirm / settle setters.
 *
 * Invariant: one pending item, at most one in-flight action. A refused
 * dismiss and a refused confirm are both silent no-ops; the dialog stays
 * open while the action is in flight.
 *
 * The split of truth is fixed: the guard RULE lives here, the in-flight
 * FACT is caller-owned and passed into each call — the site's existing
 * flag (`isDeleting`, `isStoppingSession`, `actionInProgress`, ...), never
 * a second machine-held flag that could drift from the real request.
 *
 * [confirm] gates but does NOT clear: settle timing is a declared per-site
 * arm — clear on success only, clear on both outcomes, or clear when the
 * dialog pops — and [clear] is the settle-time write the caller invokes.
 *
 * Boundary vs core/ui's `ConfirmState` (the composition-scoped
 * closure-payload machine behind `rememberConfirmState()`): that one stays
 * for closure-payload dialogs; this is the typed-payload machine for VM
 * uiState and screen `remember { }` sites.
 */
@Immutable
data class PendingConfirmation<T>(val item: T? = null) {

    /** True while an item is held for confirmation — the screens' dialog-open flag. */
    val isPending: Boolean
        get() = item != null

    /** Copy with [item] held — replaces any previous pending item. */
    fun hold(item: T): PendingConfirmation<T> = PendingConfirmation(item)

    /**
     * Copy with the pending item cleared — the dismiss write. A no-op while
     * the action is [inFlight]: the dialog must stay open until it settles.
     */
    fun dismiss(inFlight: Boolean): PendingConfirmation<T> =
        if (inFlight) this else PendingConfirmation()

    /**
     * The item to act on, or null while the action is [inFlight] — the gate
     * in front of the destructive call. Never clears: settle timing is the
     * caller's [clear] write.
     */
    fun confirm(inFlight: Boolean): T? = if (inFlight) null else item

    /** Copy with the pending item emptied — the settle-time write. */
    fun clear(): PendingConfirmation<T> = PendingConfirmation()
}
