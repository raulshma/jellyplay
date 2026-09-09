package com.raulshma.jellyplay.core.ui.viewmodel

import com.raulshma.jellyplay.core.model.UserDataChange
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
 * Cancellation never re-arms (the cancelling load is itself the
 * regeneration) — fetch bodies must rethrow `CancellationException`.
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
) {

    /**
     * The one fetch in flight, loud or silent — see the class doc for the
     * single-flight contract.
     */
    private var fetchJob: Job? = null

    /** The screen wires this with a single `DeferredRefreshEffect(...)`. */
    val deferredRefresher = DeferredUserDataRefresher(
        userDataChanges = userDataChanges,
        scope = scope,
        onRefresh = ::refreshSilently,
    )

    /** Starts (or restarts) the loud load, cancelling any in-flight fetch. */
    fun load(loudFetch: suspend () -> Boolean) {
        fetchJob?.cancel()
        fetchJob = scope.launch {
            if (!loudFetch()) {
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
            fetchJob = scope.launch {
                if (!silentFetch()) {
                    deferredRefresher.rearm()
                }
            }
        }
    }
}
