package com.raulshma.jellyplay.core.ui.viewmodel

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import com.raulshma.jellyplay.core.model.PendingConfirmation

/**
 * A VM-embeddable home for one [PendingConfirmation] machine: the held
 * confirm-dialog state plus the show / dismiss / confirm writes behind it,
 * so a flow's "which confirm is open" state is one object instead of a
 * hand-rolled uiState field plus per-write forwarders.
 *
 * Why the settle arm lives INSIDE [confirm]: DownloadsScreen's two
 * screen-held machines used to carry the comment "previously the confirm
 * write never cleared" — a confirm handler that gated, fired the action and
 * forgot the settle write left the payload armed, so the dialog's next open
 * pre-filled a stale target. With the clear inside the same write that
 * returns the target, that bug class is structurally impossible for the
 * flows that use the synchronous arm.
 *
 * Two settle arms, matching the two shapes the repo's confirm flows take:
 *
 *  - Synchronous ([confirm]): the action is fire-and-forget, so confirm
 *    gates, returns the target AND clears in one write — the dialog closes
 *    at the confirm tap. This is the default arm and the reason this class
 *    exists.
 *
 *  - Deferred ([confirm] with [inFlight]): the action is an awaited
 *    repository call and the dialog must stay up over it (the screen's
 *    `confirmLoading`), so the in-flight-parametered confirm gates only and
 *    never clears — the caller's settle point invokes [clear], on success
 *    only or on both outcomes, exactly as the flow's arm declares. The same
 *    in-flight fact (the site's real request flag, never a machine-held
 *    copy — see [PendingConfirmation]) guards [dismiss].
 *
 * Boundary vs the other two machines: core/model's [PendingConfirmation] is
 * the pure write algebra underneath this wrapper (kept for UiState-embedded
 * machines); core/ui components' `ConfirmState` is the composition-scoped
 * closure-payload machine behind `rememberConfirmState()`. This class is
 * the typed-payload machine for ViewModels that would otherwise hand-roll
 * the field + forwarders around the algebra.
 */
@Stable
class ConfirmationHost<T> {

    private val state = mutableStateOf(PendingConfirmation<T>())

    /** The held machine — read [PendingConfirmation.isPending] / [PendingConfirmation.item] off it. */
    var current: PendingConfirmation<T>
        get() = state.value
        private set(value) {
            state.value = value
        }

    /** The item awaiting confirmation, if any — the dialog's payload. Null hides the dialog. */
    val item: T?
        get() = current.item

    /** True while an item is held for confirmation — the dialog-open flag. */
    val isPending: Boolean
        get() = current.isPending

    /** Holds [item] for confirmation — opens the dialog, replacing any previous pending item. */
    fun show(item: T) {
        current = current.hold(item)
    }

    /**
     * The synchronous settle arm: returns the pending item AND clears the
     * machine in the same write, so the dialog closes at the confirm tap and
     * nothing stays armed for the next open (a second confirm after settle
     * is a no-op). Null when nothing is pending.
     */
    fun confirm(): T? {
        val confirmed = current.confirm(inFlight = false) ?: return null
        current = current.clear()
        return confirmed
    }

    /**
     * The deferred settle arm: the machine's gate, unchanged from
     * [PendingConfirmation.confirm] — returns the item to act on, or null
     * while the caller's [inFlight] fact is raised (a second confirm is
     * refused). NEVER clears: settle timing is the caller's [clear] write,
     * because the dialog must stay up over the in-flight request.
     */
    fun confirm(inFlight: Boolean): T? = current.confirm(inFlight)

    /**
     * The dismiss write — refused while the caller's [inFlight] fact is
     * raised: the dialog stays open until the action settles.
     */
    fun dismiss(inFlight: Boolean) {
        current = current.dismiss(inFlight)
    }

    /** The settle-time write behind the deferred arm. A no-op on an empty host. */
    fun clear() {
        current = current.clear()
    }
}
