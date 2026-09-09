package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.model.UserDataChange
import com.raulshma.jellyplay.core.ui.viewmodel.DeferredUserDataRefresher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * The single-flight fetch core the collection and person detail screens
 * share: one current-id + fetch-job pair, the back-stack re-entry guard, and
 * the deferred-refresh wiring (silent regeneration on re-entry, skip while a
 * load is active, re-arm on a failed or skipped silent fetch). The host
 * supplies the fetch itself — its parallel reads, state publishing and loud
 * error mapping — and reports plain success so this class owns the re-arm
 * decision ([DeferredUserDataRefresher.rearm]).
 *
 * Main-thread confinement as [DeferredUserDataRefresher]: construct with the
 * ViewModel's main-immediate scope and only touch it from that scope.
 */
internal class DetailFetchCoordinator(
    userDataChanges: Flow<UserDataChange>,
    private val scope: CoroutineScope,
    private val fetch: suspend (id: String, silent: Boolean) -> Boolean,
) {

    /** The loaded item — the deferred refresh reloads it silently. */
    private var currentId: String? = null

    /**
     * The one fetch in flight, loud or silent — a loud load cancels a silent
     * one (it regenerates the same data loudly), and the deferred refresh
     * skips itself while one is active, so two fetches never race.
     */
    private var fetchJob: Job? = null

    /** The screen wires this with a single `DeferredRefreshEffect(...)`. */
    val deferredRefresher = DeferredUserDataRefresher(
        userDataChanges = userDataChanges,
        scope = scope,
        onRefresh = ::refreshSilently,
    )

    /**
     * A no-op when [id] is already showing per [isShowing]: back-stack
     * re-entry re-runs the screen's `LaunchedEffect`, and a second loud load
     * would race the deferred refresh's silent regeneration (and flash
     * Loading over content the user is returning to). An Error state (or a
     * fresh VM) loads — [begin] publishes the host's Loading state.
     */
    fun load(id: String, isShowing: () -> Boolean, begin: () -> Unit) {
        if (currentId == id && isShowing()) return
        currentId = id
        begin()
        fetchJob?.cancel()
        fetchJob = scope.launch {
            fetch(id, false)
        }
    }

    private fun refreshSilently() {
        val id = currentId ?: return
        // A load already in flight regenerates this data — a silent twin
        // would only duplicate the fetch (see [fetchJob]). The skip still
        // re-arms: if the in-flight load dispatched before this change
        // landed, its result is pre-change data and the next re-entry must
        // retry (over-arming costs one redundant quiet refetch at worst).
        if (fetchJob?.isActive == true) {
            deferredRefresher.rearm()
        } else {
            fetchJob = scope.launch {
                if (!fetch(id, true)) {
                    deferredRefresher.rearm()
                }
            }
        }
    }
}
