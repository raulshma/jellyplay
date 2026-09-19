package com.raulshma.jellyplay.feature.syncplay

/**
 * The syncplay feature's ONE load ladder (the `LiveTvLoad` shape, the
 * load-ladder fold's syncplay slice): the `isLoading = true, error = null`
 * suspend-guard choreography behind [SyncPlayViewModel.loadGroups] and
 * [SyncPlayViewModel.joinGroup], replacing their hand-copied start →
 * repository/session call → arm bodies.
 *
 * Same invariant as the livetv/admin/calendar slices — raise the start
 * flag(s) and clear the error before the fetch, run the single fetch,
 * dispatch the Result to EXACTLY ONE arm — with the site variants staying
 * at the call sites as declared arms: both folded sites settle with ONE
 * final update after the ladder (the arms never touch the flags), and
 * `joinGroup`'s start raises the two-flag pair (`isJoining` + `isLoading`)
 * as its flavour. The arms are `suspend` because `joinGroup`'s success arm
 * continues with suspend follow-ups (current-group load + event-listener
 * start). Deliberately NOT folded: `createGroup` (a composite
 * choreography — its success arm runs a settle delay plus a SECOND fetch,
 * the `RecordingsViewModel.deleteRecording` precedent) and the
 * transport/refresh delegates (`leaveGroup`, `refreshGroups`,
 * `loadCurrentGroup` — no start ladder).
 */
internal object SyncPlayLoad {

    /**
     * Runs the canonical syncplay load ladder for one fetch:
     *
     *  1. [start] — raise the caller's loading flag(s) and clear the error
     *     (synchronously, before the fetch is issued).
     *  2. [fetch] — the single repository/session call (suspend; returns
     *     [Result]).
     *  3. Dispatch: [onSuccess] with the value on success, [onFailure] with
     *     the exception on failure — never both, never neither.
     */
    suspend fun <T> load(
        start: () -> Unit,
        fetch: suspend () -> Result<T>,
        onSuccess: suspend (T) -> Unit,
        onFailure: suspend (Throwable) -> Unit,
    ) {
        start()
        // The arms are suspend (joinGroup's success continuation), so they
        // ride fold's inline lambdas rather than being passed as the
        // non-suspend function values directly.
        fetch().fold(
            onSuccess = { onSuccess(it) },
            onFailure = { onFailure(it) },
        )
    }
}
