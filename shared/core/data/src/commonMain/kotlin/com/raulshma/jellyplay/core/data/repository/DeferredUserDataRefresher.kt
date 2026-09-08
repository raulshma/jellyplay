package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.UserDataChange
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
 * The paging source's `getRefreshKey` anchors the new generation around the
 * previous anchor position, so the restored list loads around where the user
 * was instead of snapping to the top.
 *
 * Main-thread confinement: [onScreenActiveChanged] is called from the host
 * composable's `LifecycleResumeEffect` (main) while the collector runs on
 * [scope] — hand it a main-immediate scope (viewModelScope) so all writes to
 * [pendingRefresh] happen on one thread without further synchronization.
 * Nothing consumes [pendingRefresh] until the first
 * [onScreenActiveChanged](true), so an event racing the first composition
 * still refreshes on entry.
 */
class DeferredUserDataRefresher(
    userDataChanges: Flow<UserDataChange>,
    scope: CoroutineScope,
    private val onRefresh: () -> Unit,
) {

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

    fun onScreenActiveChanged(active: Boolean) {
        if (active && pendingRefresh) {
            pendingRefresh = false
            onRefresh()
        }
    }
}
