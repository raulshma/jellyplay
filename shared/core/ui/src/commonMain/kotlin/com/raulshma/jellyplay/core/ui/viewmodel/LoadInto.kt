package com.raulshma.jellyplay.core.ui.viewmodel

/**
 * The ONE load ladder (the recorded "Feature-VM load-ladder fold", landed over
 * the five per-feature slices — `LiveTvLoad`, `RequestsLoad`, `CalendarLoad`,
 * `SyncPlayLoad`, `AdminLoad` — which were themselves folded from hand-copied
 * `isLoading = true, error = null` suspend-guard ladders): raise the caller's
 * start flag(s) and clear the error BEFORE the fetch is issued, run the single
 * fetch, dispatch the [Result] to EXACTLY ONE arm — never both, never neither.
 *
 * Deliberately small, and deliberately STATELESS: the helper owns only the
 * guard choreography and the fold routing, while each ViewModel keeps mapping
 * Success payloads into its own state. The settle arms genuinely drifted
 * across the folded sites and that drift is DECLARED at the call sites, not
 * flattened into a one-size settle:
 *
 *  - per-arm settle (the canonical shape — Devices, Plugin Detail, Channels,
 *    Requests): both arms drop the loading flag themselves;
 *  - final-update settle (Calendar, Plugins, Scheduled Tasks, Plugin Config,
 *    SyncPlay): the arms publish payload/error only and the site settles the
 *    flag once AFTER the ladder returns — the folded ladder awaits its fetch,
 *    so the flag covers the call;
 *  - flavour starts (Programs' throttled re-entry, Users' refresh, Stats
 *    Detail's page > 0, SyncPlay's join pair): the [start] closure raises the
 *    flavour's own flag(s) instead of the cold-load pair, and may skip the
 *    error clear;
 *  - clearing failure arms (Recordings' legacy `getOrDefault(emptyList())`),
 *    empty success arms (Calendar — items arrive through a collector), and
 *    thrown-exception fetches (Dashboard's persisted-error catch, Logs'
 *    parallel pair) expressed at the call site, e.g. over
 *    `runCatchingRethrowingCancellation`.
 *
 * The returned [Result] is the fetch's own result AFTER its arm has run, so a
 * caller whose choreography continues past the ladder (Channel Detail's
 * channel-meta leg gating the programs refresh) can skip the continuation on
 * failure without re-deciding the dispatch; call sites that don't leg-gate
 * ignore it.
 *
 * The arms are `suspend` so a site whose arm continues with suspend follow-ups
 * (SyncPlay's join: current-group load + event-listener start) rides the same
 * ladder; non-suspend lambdas and function references pass freely where a
 * suspend function type is expected.
 *
 * Cancellation is never masked: the fetch is awaited bare — no try/catch
 * anywhere in the ladder — so a cancelled fetch propagates and NO arm runs.
 */
suspend fun <T> loadInto(
    start: () -> Unit,
    fetch: suspend () -> Result<T>,
    onSuccess: suspend (T) -> Unit,
    onFailure: suspend (Throwable) -> Unit,
): Result<T> {
    start()
    val result = fetch()
    // The arms may suspend, so they ride fold's inline lambdas rather than
    // being passed as function values directly.
    result.fold(
        onSuccess = { onSuccess(it) },
        onFailure = { onFailure(it) },
    )
    return result
}
