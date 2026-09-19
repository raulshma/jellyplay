package com.raulshma.jellyplay.feature.requests

/**
 * The requests feature's ONE load ladder (the `LiveTvLoad` shape, the
 * load-ladder fold's requests slice): the `isLoading = true, error = null`
 * suspend-guard choreography behind [RequestsViewModel.loadRequests],
 * replacing the hand-copied start → repository call → arm-settle body.
 *
 * The helper owns the invariant part of the ladder — raise the loading flag
 * and clear the error before the fetch, run the single fetch, dispatch the
 * Result to EXACTLY ONE arm — while the site keeps its declared variants:
 * the in-flight guard and the page/skip index math stay at the call site
 * (the guard reads the ui-state `isLoading` field the screen renders; the
 * skip comes from [com.raulshma.jellyplay.core.ui.viewmodel.PageAppender]),
 * and both arms settle the flag per-arm (the success arm also fans out the
 * media-details and *arr progress enrichments).
 */
internal object RequestsLoad {

    /**
     * Runs the canonical requests load ladder for one fetch:
     *
     *  1. [start] — raise `isLoading` and clear the error (synchronously,
     *     before the fetch is issued).
     *  2. [fetch] — the single repository call (suspend; returns [Result]).
     *  3. Dispatch: [onSuccess] with the value on success, [onFailure] with
     *     the exception on failure — never both, never neither.
     */
    suspend fun <T> load(
        start: () -> Unit,
        fetch: suspend () -> Result<T>,
        onSuccess: (T) -> Unit,
        onFailure: (Throwable) -> Unit,
    ) {
        start()
        fetch().fold(onSuccess, onFailure)
    }
}
