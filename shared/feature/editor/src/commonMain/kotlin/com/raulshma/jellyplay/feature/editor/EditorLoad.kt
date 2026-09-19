package com.raulshma.jellyplay.feature.editor

/**
 * The editor feature's ONE load ladder (the LiveTvLoad precedent): the
 * `isLoading = true, error = null` suspend-guard choreography behind
 * [EditorViewModel]'s editor-data load — raise the loading flag and clear the
 * error before the fetch, run the single fetch, dispatch its [Result] to
 * EXACTLY ONE arm.
 *
 * Only the editor-data load is a genuine single-fetch ladder. The editor's
 * other fetches are declared non-ladders: `saveMetadata` raises `isSaving`
 * (a save, not a load, and its success arm re-baselines the form session),
 * `searchAllSubtitleProviders` streams partial provider results instead of
 * dispatching one Result, the provider-subtitle download is a multi-leg
 * choreography (download → persist → upload → reload → attribute), and the
 * image/subtitle mutations use the bare `.onSuccess`/`.onFailure` idiom with
 * no loading-flag guard at all — folding any of them would change behaviour,
 * not centralize it.
 */
internal object EditorLoad {

    /**
     * Runs the canonical load ladder for one fetch:
     *
     *  1. [start] — raise the caller's loading flag and clear the error
     *     (synchronously, before the fetch is issued).
     *  2. [fetch] — the single repository call (suspend; returns [Result]).
     *  3. Dispatch: [onSuccess] with the value on success, [onFailure] with
     *     the exception on failure — never both, never neither.
     *
     * @return the fetch's [Result] AFTER its arm has run, so a caller whose
     *   choreography continues past the ladder can skip the continuation on
     *   failure without re-deciding the dispatch.
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
