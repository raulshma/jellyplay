package com.raulshma.jellyplay.feature.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The plugin tab screens' shared load dance — gate → fetch → stale-guard →
 * quiet-degrade — one home for the per-tab ViewModels' shared choreography
 * (the "My ratings" and "Your watching" models). The VM keeps its
 * query type, state shape and select verb; this holder owns the three
 * cross-tab invariants:
 *
 *  1. **The gate.** [gateOpen] is re-checked at every refresh, so the api
 *     client is never touched without it (the plugin-gating contract; the
 *     capabilities contract can degrade mid-session). The spelling at the
 *     construction sites is the ONE seam
 *     ([com.raulshma.jellyplay.core.data.session.isAvailableNowOrProbe] —
 *     probe AND toggle, or the probe-only fallback when the gate seam is
 *     unwired). A closed gate lands [onGateClosed] — the caller's loading-off
 *     face — and fetches nothing.
 *  2. **The stale-response guard.** The query is captured from [selected]
 *     after the gate passes; on BOTH the success and the failure arm
 *     [isCurrent] re-checks it against the live selection, so only the
 *     currently-selected tab's fetch may land — a slow earlier tab's answer
 *     can never overwrite the newer selection's state.
 *  3. **The quiet-absence fold.** A fresh success lands through [land]; a
 *     fresh failure lands [quiet] through the same fold — the screen's
 *     "not available" face (a 404 or any failure is never an error).
 *
 * Constructed per-VM with the VM's own scope (one `launch` per refresh,
 * cancellation riding the ViewModel).
 */
class PluginTabLoader<Q, T>(
    private val scope: CoroutineScope,
    /** The ONE gate seam, or the probe-only fallback when the seam is unwired. */
    private val gateOpen: suspend () -> Boolean,
    /** The tab currently selected — read fresh on every refresh, after the gate. */
    private val selected: () -> Q,
    /** The tab's one fetch. */
    private val fetch: suspend (Q) -> Result<T>,
    /** Whether the query captured at fetch time is still the selected one. */
    private val isCurrent: (Q) -> Boolean,
    /** Lands a completed load in the VM's state (the loading face goes off). */
    private val land: (T) -> Unit,
    /** The quiet-absence value a fresh failure lands through [land]. */
    private val quiet: T,
    /** Lands the gate-closed face (loading off, nothing fetched). */
    private val onGateClosed: () -> Unit,
) {

    /**
     * Runs one gated fetch-and-land cycle (the screens fire one per tab
     * select plus one on open).
     */
    fun refresh() {
        scope.launch {
            if (!gateOpen()) {
                onGateClosed()
                return@launch
            }
            val query = selected()
            fetch(query)
                .onSuccess { value ->
                    // Stale-response guard: only the selected tab's fetch may land.
                    if (!isCurrent(query)) return@onSuccess
                    land(value)
                }
                .onFailure {
                    if (!isCurrent(query)) return@onFailure
                    land(quiet)
                }
        }
    }
}
