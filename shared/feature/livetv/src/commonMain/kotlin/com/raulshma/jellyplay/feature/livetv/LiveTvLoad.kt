package com.raulshma.jellyplay.feature.livetv

/**
 * The Live-TV feature's ONE load ladder: the `isLoading = true, error = null`
 * suspend-guard choreography behind the tab ViewModels, replacing their
 * hand-copied `update { copy(isLoading = true, error = null) }` → repo call →
 * arm-settle ladders (Channels, Series, Recordings, Programs, Channel Detail).
 *
 * The helper owns the invariant part of the ladder — raise the start flag(s)
 * and clear the error before the fetch, run the single fetch, dispatch the
 * Result to EXACTLY ONE arm — while each ViewModel keeps its own settle copies
 * as the arms. The arms genuinely drifted across the tabs (per-arm settle vs a
 * single unconditional settle; which flag a load flavour raises; whether a
 * failure clears the item list), so the arms stay at the call sites, written
 * once each, instead of being flattened into a one-size settle that would
 * change behaviour.
 */
internal object LiveTvLoad {

    /**
     * Runs the canonical load ladder for one fetch:
     *
     *  1. [start] — raise the caller's loading flag(s) and clear the error
     *     (synchronously, before the fetch is issued).
     *  2. [fetch] — the single repository call (suspend; returns [Result]).
     *  3. Dispatch: [onSuccess] with the value on success, [onFailure] with
     *     the exception on failure — never both, never neither.
     *
     * @return the fetch's [Result] AFTER its arm has run, so a caller whose
     *   choreography continues past the ladder (Channel Detail's channel-meta
     *   leg gating the programs refresh) can skip the continuation on failure
     *   without re-deciding the dispatch.
     */
    suspend fun <T> load(
        start: () -> Unit,
        fetch: suspend () -> Result<T>,
        onSuccess: (T) -> Unit,
        onFailure: (Throwable) -> Unit,
    ): Result<T> {
        start()
        return fetch().also { result ->
            result.fold(onSuccess, onFailure)
        }
    }
}
