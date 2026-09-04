package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.core.data.repository.PlaybackOutboxEntry
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem

/**
 * The dialog SESSION half of the home screen: the event cascades behind
 * opening/dismissing the Seerr request dialog and the while-open loading of
 * the pending-sync details sheet.
 *
 * Stateless by design — the dialog state itself ([HomeUiState.seerrRequestState],
 * the sync sheet's entries/details flows) lives in the VM where it survives
 * configuration change; what was missing was an owner for the ORDERING and
 * CASCADES around it. Opening the Seerr dialog must fire the service-details
 * load, plus the season load for TV only; dismissing must clear the pending
 * item BEFORE dropping the request result, or a stale result banner can flash
 * into the next dialog's state. That choreography used to live as
 * LaunchedEffect/onDismiss bodies inside HomeScreen — reachable by no test;
 * here each sequence is one method pinned by [HomeDialogSessionTest].
 *
 * Compose-free: the screen keeps the `requestItem?.let` gating and the
 * sheet-open `if` — this module only owns what firing those intents MEANS.
 * Same pattern as [HomeSearchSession] (which owns the search close ordering).
 */
internal class HomeDialogSession(
    private val onEvent: (HomeUiEvent) -> Unit,
) {
    /**
     * A Seerr request dialog is opening for [item]: load the request-server
     * service details for its media type, and — for TV only (case-insensitive;
     * Seerr reports "tv" lowercase but the casing is not contractual) — also
     * load the show's season list so the dialog can offer episode selection.
     * The mediaType is forwarded verbatim.
     */
    fun openSeerrRequest(item: SeerrSearchItem) {
        onEvent(HomeUiEvent.LoadSeerrServiceDetails(item.mediaType))
        if (item.mediaType.equals("tv", ignoreCase = true)) {
            onEvent(HomeUiEvent.LoadTvSeasons(item.id))
        }
    }

    /**
     * The ONE Seerr dialog teardown: drop the pending request item (closing
     * the dialog) THEN clear the last request result — in that order, so the
     * result banner never outlives the dialog it belongs to.
     */
    fun dismissSeerrRequest() {
        onEvent(HomeUiEvent.SelectSeerrRequestItem(null))
        onEvent(HomeUiEvent.ClearRequestResult)
    }

    /**
     * The pending-sync details sheet (re)opened or its entries changed: resolve
     * offline-first metadata for the currently-queued rows. Fires with the
     * entry ids in queue order, including the empty case (sheet open with
     * nothing queued) — the while-open gating lives at the call site, which
     * only collects entries while the sheet is up.
     */
    fun syncSheetOpened(entries: List<PlaybackOutboxEntry>) {
        onEvent(HomeUiEvent.EnsurePendingItemDetails(entries.map { it.itemId }))
    }
}
