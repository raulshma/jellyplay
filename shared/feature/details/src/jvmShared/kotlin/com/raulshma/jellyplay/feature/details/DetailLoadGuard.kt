package com.raulshma.jellyplay.feature.details

/**
 * The detail screen's single staleness guard — the `HomeRefresher.identityEpoch`
 * precedent applied to the media-detail load graph. It owns exactly two cells
 * that used to be five hand-maintained VM fields:
 *
 * - **Item identity** (`itemId`, the former `currentItemId`): the item the
 *   screen is currently loading/showing, written synchronously by
 *   [enter] at the top of the load path so a freshly-composed screen still
 *   observing the previous item's detail is rejected before it fires any
 *   per-item work.
 * - **One epoch** (`generation`, the former `seerrDataGeneration`): a
 *   monotonic counter bumped by every [enter] (navigation/refresh) and by
 *   every [bump] (a Seerr-data load claim). Callers capture it before their
 *   first suspension and re-check [isCurrent] after every suspension point —
 *   a superseded flight path simply drops its write instead of painting the
 *   previous item's trailers/recommendations onto the new screen.
 *
 * One epoch — not one per purpose. The former `seerrDataGeneration` was bumped
 * at exactly the two sites this guard bumps ([enter] on navigation,
 * [bump] on Seerr load start) and nothing else consulted its value except the
 * mid-flight `generation == seerrDataGeneration` re-checks, so a separate
 * per-purpose guard instance would duplicate the counter without changing any
 * accept/reject decision: every navigation bump already invalidates every
 * in-flight Seerr fetch, and the load-start bump is the only same-item
 * invalidation source that exists (the `seerrDataLoaded` latch means one load
 * per navigation).
 *
 * [enter] bumps the epoch SYNCHRONOUSLY with the identity write. The former
 * code set `currentItemId` synchronously but deferred `seerrDataGeneration++`
 * to inside the load coroutine, leaving a microscopic window in which an
 * in-flight Seerr write passed its generation re-check against the new item's
 * screen; folding both cells into one atomic transition closes it. Under the
 * single-threaded dispatcher every VM coroutine runs on, ordering is otherwise
 * unchanged.
 *
 * A plain var on purpose: every reader and writer runs on the ViewModel's
 * main-confined scope (loads, side-effect launches, resumptions), so there is
 * no cross-thread access — the same contract `HomeRefresher.identityEpoch`
 * documents. Checks are suspend-safe because a resumption re-reads the cell on
 * that dispatcher before writing.
 *
 * What deliberately does NOT live here: `currentSeriesId` (the provider
 * catalogue-invalidation target, an identity read not a staleness check),
 * `seerrDataLoaded` (an idempotency latch, not an epoch), and
 * `lastAppliedGeneration` (compares against the provider's per-snapshot
 * `contentGeneration` to distinguish attachment ticks from new resolutions —
 * a different vocabulary from "how many navigations happened").
 */
internal class DetailLoadGuard {
    /** The item the screen is currently loading; null before the first [enter]. */
    var itemId: String? = null
        private set

    /** Monotonic navigation/load epoch; captured via [enter]/[bump] and re-checked via [isCurrent]. */
    var generation: Long = 0L
        private set

    /**
     * Records a navigation (or refresh) to [itemId]: publishes the new identity
     * and bumps the epoch in one atomic step, invalidating every in-flight
     * flight path captured against the previous item. Returns the new epoch
     * for callers that need to capture it.
     */
    fun enter(itemId: String): Long {
        this.itemId = itemId
        return ++generation
    }

    /**
     * Bumps the epoch without touching the item identity — the Seerr load
     * start's claim: any still-in-flight Seerr fetch from an earlier load of
     * the SAME item is superseded by this one. Returns the new epoch to
     * capture.
     */
    fun bump(): Long = ++generation

    /** Identity check: true when [itemId] is still the item on screen. */
    fun isCurrent(itemId: String): Boolean = this.itemId == itemId

    /** Epoch check: true when [generation] is still the epoch this flight path captured. */
    fun isCurrent(generation: Long): Boolean = this.generation == generation
}
