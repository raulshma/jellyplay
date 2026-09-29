package com.raulshma.jellyplay.core.ui.components.seerr

import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns the Seerr request dialog's presentation cell: WHICH item the dialog
 * is open for ([item], null = closed — the screens' `?.let` render gate),
 * plus the open/dismiss choreography around it. The state-holder shape
 * (`PlaylistPickerStateHolder` / `SeerrRequestStateHolder`) shrunk to one
 * cell: the data half of the old open cascade (service details + TV
 * seasons) stays in core:data's `SeerrRequestStateHolder.prepare`, reached
 * through the [prepare] seam so this module needs no core:data dependency.
 *
 * Invariants (pinned by `SeerrRequestDialogHolderTest`):
 * the item is FROZEN at open time — only [open] and [dismiss] ever write it,
 * so an in-dialog state change (e.g. the optimistic PENDING flip on the
 * loaded detail) can never rewrite the item the user opened the dialog for;
 * and [dismiss] is the ONE teardown — drop the item (closing the dialog)
 * THEN clear the last request result via [clearRequestResult], in that
 * order, so the result banner never outlives the dialog it belongs to (the
 * same rule as feature:home's `HomeDialogSession.dismissSeerrRequest`).
 *
 * Not a Koin type: each ViewModel constructs it directly over its own
 * request state holder, wiring the two seams constructor-side.
 */
class SeerrRequestDialogHolder(
    private val prepare: (SeerrSearchItem) -> Unit,
    private val clearRequestResult: () -> Unit,
) {
    private val _item = MutableStateFlow<SeerrSearchItem?>(null)

    /** The item the dialog is open for (null = closed) — the render gate. */
    val item: StateFlow<SeerrSearchItem?> = _item.asStateFlow()

    /**
     * Opens the dialog for [item]: the item lands on [item] frozen at open
     * time, and the open cascade (service details, plus TV seasons for tv)
     * fires through the [prepare] seam here — not at the call site.
     */
    fun open(item: SeerrSearchItem) {
        _item.value = item
        prepare(item)
    }

    /**
     * The ONE dialog teardown: drop the open item (closing the dialog) THEN
     * clear the last request result — in that order, so the result banner
     * never outlives the dialog it belongs to.
     */
    fun dismiss() {
        _item.value = null
        clearRequestResult()
    }
}
