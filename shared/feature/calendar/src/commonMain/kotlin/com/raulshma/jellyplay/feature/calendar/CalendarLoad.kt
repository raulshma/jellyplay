package com.raulshma.jellyplay.feature.calendar

/**
 * The calendar feature's ONE load ladder (the `LiveTvLoad` shape, the
 * load-ladder fold's calendar slice): the `isLoading = true, error = null`
 * suspend-guard choreography behind [UpcomingCalendarViewModel.refresh],
 * replacing the hand-copied start → repository call → arm body.
 *
 * The helper owns the invariant part of the ladder — raise the loading flag
 * and clear the error before the fetch, run the single fetch, dispatch the
 * Result to EXACTLY ONE arm — while the site keeps its declared variants:
 * the success arm is empty (calendar items arrive through the month
 * collector that mirrors `ArrRepository.calendar`, never through the
 * refresh payload — the refresh only triggers the server round-trip), the
 * failure arm writes the error, and the flag settles in ONE final update
 * after the ladder rather than per-arm. The month-window enrichment fan-out
 * is NOT part of the ladder (streaming partial results — the collector may
 * emit before and after the refresh settles).
 */
internal object CalendarLoad {

    /**
     * Runs the canonical calendar load ladder for one fetch:
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
