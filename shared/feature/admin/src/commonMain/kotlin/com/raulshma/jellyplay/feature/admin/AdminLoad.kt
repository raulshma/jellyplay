package com.raulshma.jellyplay.feature.admin

/**
 * The admin feature's ONE load ladder (the `LiveTvLoad` precedent, the
 * load-ladder fold's admin slice): the `isLoading = true, error = null`
 * suspend-guard choreography behind the admin ViewModels, replacing their
 * hand-copied start → repository call → arm-settle ladders (Dashboard,
 * Devices, Logs, Plugin Detail, Plugins, User Statistics, User Statistics
 * Detail, Scheduled Tasks, Users, and the androidMain Plugin Config).
 *
 * The helper owns the invariant part of the ladder — raise the start flag(s)
 * and clear the error before the fetch, run the single fetch, dispatch the
 * Result to EXACTLY ONE arm — while each ViewModel keeps its own settle as
 * the arms. The settles genuinely drifted across the VMs (per-arm settle vs
 * a final update after the ladder; which flag a load flavour raises; the
 * dashboard's persisted-error try/catch), so the arms stay at the call
 * sites, written once each, instead of being flattened into a one-size
 * settle that would change behaviour. Declared variants at the call sites:
 *
 *  - final-update settle (Plugins, Scheduled Tasks, Plugin Config): the
 *    arms never touch the loading flag; the VM settles it once after
 *    [load] returns. For Plugins/Scheduled Tasks this also declares a
 *    timing unification: the legacy ladder kicked the fetch off in a
 *    fire-and-forget inner coroutine, so `isLoading` cleared before the
 *    fetch landed; the folded ladder awaits its fetch, and the flag now
 *    covers the call.
 *  - try/catch + persisted-error (Dashboard): expressed as a
 *    `runCatchingRethrowingCancellation { …getOrThrow() }` fetch whose
 *    failure arm persists the error — the historical catch semantics, on
 *    the same dispatch, without masking cancellation.
 *  - flavour starts (Users' refresh, Stats Detail's page > 0): the start
 *    closure raises the flavour's own flag instead of the cold-load pair.
 */
internal object AdminLoad {

    /**
     * Runs the canonical admin load ladder for one fetch:
     *
     *  1. [start] — raise the caller's loading flag(s) and clear the error
     *     (synchronously, before the fetch is issued).
     *  2. [fetch] — the one fetch the ladder awaits (suspend; returns
     *     [Result]). Usually a single repository call; the declared
     *     variants compose more around it (Plugin Config's bridge-script
     *     prep, Stats Detail's status refresh + state write, Logs' parallel
     *     pair) — those side effects stay visible at their call sites.
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
