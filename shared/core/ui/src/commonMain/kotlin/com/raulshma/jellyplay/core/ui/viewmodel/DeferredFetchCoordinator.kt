package com.raulshma.jellyplay.core.ui.viewmodel

import com.raulshma.jellyplay.core.model.UserDataChange
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * The single-flight fetch lifecycle around a [DeferredUserDataRefresher]:
 * one fetch-job slot shared by loud and silent loads — a loud load cancels
 * an in-flight silent one (it regenerates the same data loudly), and the
 * deferred refresh skips itself while any load is active, so two fetches
 * never race into a last-writer-wins swap.
 *
 * Hosts report plain success from their fetch bodies; this class owns the
 * re-arm decision ([DeferredUserDataRefresher.rearm]): a fetch that
 * returned false — a failed silent regeneration, a failed loud load a
 * skipped silent refresh may have bet on, or a load that could not run
 * (offline gating) — re-arms, so the next screen re-entry retries instead
 * of trusting the consumed pending flag. A loud failure with nothing
 * pending over-arms at worst: one quiet refetch on the next re-entry.
 * A fetch that throws a non-cancellation exception counts as failure the
 * same way (and never escapes to the scope's uncaught handler), while
 * cancellation never re-arms (the cancelling load is itself the
 * regeneration) — fetch bodies must rethrow `CancellationException`.
 * A thrown fetch would otherwise strand whatever loud UI the host published
 * before it (a Loading state, a spinner) — [onFetchError] is the host's
 * chance to surface that failure the same way it surfaces a `false` return;
 * it is told whether the fetch was silent so stale content stays untouched.
 *
 * The loud-cancels-silent rule assumes the loud load regenerates the data
 * the silent pass was regenerating; when that may not hold (a VM reused
 * for a new id), the cancelled silent pass's consumed pending flag would
 * strand — so [load] re-arms before cancelling an in-flight silent fetch.
 * When both loads target the same data that re-arm over-arms at worst:
 * one redundant quiet refetch on the next re-entry.
 *
 * The host keeps its own id/re-entry guard and Loading publish:
 * [load] receives the loud fetch so per-call parameters (id, force) stay
 * host-side, while [silentFetch] is fixed at construction and reloads
 * whatever is currently showing.
 *
 * Main-thread confinement as [DeferredUserDataRefresher]: construct with
 * the ViewModel's main-immediate scope and only touch it from that scope.
 */
class DeferredFetchCoordinator(
    userDataChanges: Flow<UserDataChange>,
    private val scope: CoroutineScope,
    private val silentFetch: suspend () -> Boolean,
    private val onFetchError: (exception: Exception, silent: Boolean) -> Unit = { _, _ -> },
) {

    /**
     * The one fetch in flight, loud or silent — see the class doc for the
     * single-flight contract.
     */
    private var fetchJob: Job? = null

    /** Whether [fetchJob] is the silent regeneration — see [load]. */
    private var fetchJobIsSilent = false

    /** The screen wires this with a single `DeferredRefreshEffect(...)`. */
    val deferredRefresher = DeferredUserDataRefresher(
        userDataChanges = userDataChanges,
        scope = scope,
        onRefresh = ::refreshSilently,
    )

    /** Starts (or restarts) the loud load, cancelling any in-flight fetch. */
    fun load(loudFetch: suspend () -> Boolean) {
        if (fetchJob?.isActive == true && fetchJobIsSilent) {
            // The in-flight silent regeneration consumed the pending flag
            // for data this loud load only regenerates when it targets the
            // same subject — if it doesn't (a VM reused for a new id), only
            // the re-armed flag can heal that data on a later re-entry.
            deferredRefresher.rearm()
        }
        fetchJob?.cancel()
        fetchJobIsSilent = false
        fetchJob = scope.launch {
            if (!runFetch(loudFetch, silent = false)) {
                deferredRefresher.rearm()
            }
        }
    }

    private fun refreshSilently() {
        // A load already in flight regenerates this data — a silent twin
        // would only duplicate the fetch (see [fetchJob]). The skip still
        // re-arms: if the in-flight load dispatched before this change
        // landed, its result is pre-change data and the next re-entry must
        // retry (over-arming costs one redundant quiet refetch at worst).
        if (fetchJob?.isActive == true) {
            deferredRefresher.rearm()
        } else {
            fetchJobIsSilent = true
            fetchJob = scope.launch {
                if (!runFetch(silentFetch, silent = true)) {
                    deferredRefresher.rearm()
                }
            }
        }
    }

    /**
     * Host fetch bodies report failure as `false`; one that throws a
     * non-cancellation exception (a repo path that blew up before building
     * its Result) must count as failure too — otherwise it would skip the
     * re-arm and escape to the scope's uncaught handler. [onFetchError]
     * carries the exception back so the host can clear its loud UI (spinner,
     * Loading) exactly as it would for a `false` return.
     */
    private suspend fun runFetch(fetch: suspend () -> Boolean, silent: Boolean): Boolean = try {
        fetch()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        onFetchError(e, silent)
        false
    }
}
