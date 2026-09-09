package com.raulshma.jellyplay.core.ui.viewmodel

import com.raulshma.jellyplay.core.model.UserDataChange
import com.raulshma.jellyplay.core.ui.components.DeferredRefreshHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Deferred grid freshness (the read-side twin of the silent flip contract):
 * a user-data change (played/favorite/progress) landing while the grid is
 * NOT on screen only MARKS the pager stale — the single regeneration happens
 * when the screen is next entered, via [onScreenActiveChanged](true). A
 * change landing while the grid IS on screen also arms the flag but never
 * regenerates immediately, so an in-place badge fix can never collapse into
 * a pager generation swap mid-scroll (the scroll-jump the silent grid
 * contract exists to prevent) — the change heals on the next re-entry
 * instead of waiting out the cache TTL.
 *
 * The regeneration is one [onRefresh] call per stale-period — the pending
 * flag is boolean, so a WS burst collapses into a single refresh on re-entry.
 * For the pager-trigger shape a bump restarts the key-combined
 * `flatMapLatest` pager as a fresh generation, which loads from its initial
 * key — `getRefreshKey` only anchors refreshes *within* one generation, it
 * does not carry the previous generation's anchor over. That is invisible in
 * practice: bumps fire on re-entry, when the grid is rebuilt from the top
 * anyway — never mid-scroll.
 *
 * It is itself a [DeferredRefreshHost], so the owning ViewModel exposes it
 * and the screen wires it with a single
 * `DeferredRefreshEffect(viewModel.deferredRefresher)` — no per-ViewModel
 * delegation to maintain.
 *
 * Main-thread confinement: [onScreenActiveChanged] is called from the host
 * composable's `LifecycleResumeEffect` (main) while the collector runs on
 * [scope] — hand it a main-immediate scope (viewModelScope) so all writes to
 * [pendingRefresh] happen on one thread without further synchronization.
 * Nothing consumes [pendingRefresh] until the first
 * [onScreenActiveChanged](true), so an event that lands any time after the
 * init collector subscribes still refreshes on the next entry even without
 * an explicit deactivate/reactivate. An event emitted before the collector
 * subscribes is lost, not deferred — [userDataChanges] is replay-0 and the
 * VM did not exist to observe it. Hosts that keep their own single-flight
 * load job beside a refresher rely on the same confinement: unsynchronized
 * check-and-cancel on those fields is only safe on that main-immediate scope.
 */
class DeferredUserDataRefresher(
    userDataChanges: Flow<UserDataChange>,
    scope: CoroutineScope,
    private val onRefresh: () -> Unit,
) : DeferredRefreshHost {

    /**
     * The pager-generation shape: [onRefresh] bumps a generation counter the
     * pager splices into its key combine, regenerating the PagingSource
     * without touching any other data.
     */
    constructor(
        userDataChanges: Flow<UserDataChange>,
        scope: CoroutineScope,
        trigger: StateFlowHandle<Int>,
    ) : this(userDataChanges, scope, { trigger.set(trigger.value + 1) })

    private var pendingRefresh = false

    init {
        scope.launch {
            userDataChanges.collect {
                // Unconditional: an active-screen event must not regenerate
                // mid-scroll, but it still arms the flag so the change is
                // not lost — the next re-entry regenerates.
                pendingRefresh = true
            }
        }
    }

    override fun onScreenActiveChanged(active: Boolean) {
        if (active && pendingRefresh) {
            pendingRefresh = false
            onRefresh()
        }
    }

    /**
     * Re-arms the pending flag without waiting for a user-data event: the
     * host calls this when a regeneration it started failed WITHOUT
     * surfacing an error, or when the refresh skipped itself because a load
     * was already in flight (whose fetches may predate the change — its
     * result would strand the consumed flag on pre-change data) — no later
     * activation would otherwise ever retry. Re-arming costs nothing while
     * the screen stays open (the flag is only consumed by the next
     * activation; over-arming costs one redundant quiet refetch). An
     * in-flight regeneration that is CANCELLED does not re-arm here: the
     * cancelling loud load is itself the regeneration.
     *
     * Main-thread confinement as above — call from the ViewModel's
     * main-immediate scope.
     */
    fun rearm() {
        pendingRefresh = true
    }
}
