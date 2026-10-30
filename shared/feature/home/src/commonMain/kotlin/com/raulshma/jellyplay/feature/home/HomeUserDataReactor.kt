package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.core.data.repository.RecentLocalUserDataWrites
import com.raulshma.jellyplay.core.data.repository.UserDataChanges
import com.raulshma.jellyplay.core.model.HomeFreshness
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

/**
 * Deep module for the home screen's user-data reaction chain — the debounced
 * server-push collector, the pending-flush latch, the trailing-edge throttle
 * deferral, and the echo-vs-external-push discrimination — behind three
 * verbs: [refreshAfterUserDataChange] (the trigger, also the collector's own
 * arm), [flushPendingUserDataChange] (the latch drain, also the onStart
 * flush), [cancelDeferralTimer] (the stop / identity-transition drain). The
 * refresher keeps the rest of WHAT/WHEN — the fetch itself, the refresh-job
 * replacement, the refresh clock it stamps, the raced-sync flag its
 * going-online handshake arms — and hands those across the narrow [Port];
 * this class owns the decisions.
 *
 * THE PROTOCOL INVARIANT — the contract this class exists to state:
 *  * A change must ALWAYS fan out into exactly one silent refresh, never
 *    drop: a change landing while backgrounded or offline latches
 *    ([pendingUserDataRefresh]) and drains on the next onStart flush; a
 *    change landing inside the throttle window defers to throttle expiry
 *    (trailing edge — each further echo re-arms the timer, so a playback
 *    session's ~10s position saves collapse into one fetch after the last
 *    save).
 *  * An echo of THIS device's own writes must not re-trigger the forced
 *    path: when every item id of the pushed change was recently recorded
 *    by [recentLocalWrites], the fetch demotes to non-forced (TTL
 *    semantics) — a cache-bypassing refetch behind the player is wasted
 *    when only this device's own state moved. An external push stays
 *    forced.
 *  * The completion echo of a drained play MUST fan out: when the
 *    going-online handshake's fetch raced a still-pending outbox drain,
 *    the drain's echo is the sync-complete signal — it bypasses the
 *    throttle AND overrides the demotion (stays forced; a TTL-semantics
 *    fetch could serve the pre-drain cache).
 *
 * The demotion verdict is evaluated at fetch-EXECUTION time, not at flush
 * time (see [Port.launchUserDataRefresh]): a debounced emission can still
 * overwrite the armed change's item ids between a flush and its job
 * dispatching, and the fetch must consult the ids it will actually have
 * fetched for.
 *
 * Plain vars on purpose: every reader and writer runs on [scope]'s
 * main-confined dispatcher (the collector, the deferral timer, the
 * refresher's flush callers) — there is no cross-thread access.
 */
internal class HomeUserDataReactor(
    /** The refresher's scope: the collector and the deferral timer die with the VM. */
    private val scope: CoroutineScope,
    private val clock: HomeClock,
    /**
     * The user-data change feed (server pushes + this device's confirmed
     * writes) — the same [UserDataChanges] seam the refresher injected; the
     * reactor is its one collector.
     */
    private val userDataChanges: UserDataChanges,
    /**
     * The write-log of this device's recent user-data writes: what lets the
     * flush tell a socket echo of THIS device's own write burst from an
     * external push and run the echo non-forced.
     */
    private val recentLocalWrites: RecentLocalUserDataWrites,
    /** The narrow seam into the refresher — see [Port]. */
    private val port: Port,
) {

    /**
     * The narrow port into the refresher the reaction chain runs THROUGH:
     * two gates, the shared refresh clock, the raced-sync bypass flag, and
     * the one fetch trigger. Deliberately NOT the whole refresher — the
     * fetch body, the refresh-job replacement and every [HomeRefreshState]
     * write stay refresher-owned.
     */
    internal interface Port {
        /** The refresher's foreground flag ([HomeRefresher.start] / [HomeRefresher.stop] maintain it). */
        fun isAppInForeground(): Boolean

        /** The offline gate — a push landing during a disconnect defers like a backgrounded one. */
        fun isOffline(): Boolean

        /**
         * When the last refresh landed — the one refresh clock
         * ([HomeRefresher.fetchOnce] stamps it on every exit path, failed
         * and offline attempts included).
         */
        fun lastRefreshTimeMillis(): Long

        /**
         * The raced-sync throttle bypass, armed by the going-online
         * handshake when its fetch painted a pre-drain snapshot.
         * Refresher-owned state — the handshake and the identity
         * transitions write it; the chain only reads it here, and
         * [Port.launchUserDataRefresh] clears it at fetch land.
         */
        fun lastFetchRacedPendingSync(): Boolean

        /**
         * Launches the silent user-data fetch as the refresher's refresh
         * job (replacing any in-flight one): bumps the image-reload epoch,
         * evaluates [decideForce]
         * at job EXECUTION time (the class-KDoc rule), runs the fetch,
         * clears the raced-sync bypass when the fetch lands, calls
         * [onLanded] (the reactor's latch clears — the same land-only rule),
         * then continues the periodic loop in the same job so
         * [HomeRefresher.stop] cancels them together.
         */
        fun launchUserDataRefresh(decideForce: () -> Boolean, onLanded: () -> Unit)
    }

    /**
     * Set when a user-data change lands while backgrounded or offline;
     * consumed by [flushPendingUserDataChange] (the onStart flush). NOT
     * cleared before the flush's fetch lands, so a stop() mid-fetch re-arms
     * the flush for the next onStart.
     */
    private var pendingUserDataRefresh = false

    /**
     * The item ids of the most recent (debounced) user-data change —
     * overwritten on every push, consumed by [flushPendingUserDataChange]:
     * consulting the write-log with them is what demotes an echo of
     * this device's own writes to a non-forced fetch. Cleared once that
     * fetch lands, alongside [pendingUserDataRefresh]; empty makes
     * [RecentLocalUserDataWrites.wereAllRecentlyWritten] answer false, so a
     * flush reached without a named change stays forced.
     */
    private var pendingUserDataChangeItemIds: List<String> = emptyList()

    /**
     * The trailing-edge deferral timer of [refreshAfterUserDataChange], on
     * its OWN job — deliberately not the refresher's refresh job. Installing
     * the delay by replacing the refresh job would cancel whatever that job
     * is doing: a Manual / PullToRefresh fetch mid-flight, whose spinners
     * the cancelled body then never clears, leaving the loader stuck until
     * the deferred fetch landed up to a minute later. With the timer here,
     * an echo arriving during a fetch only re-arms the delay; the in-flight
     * fetch finishes and clears its own spinners. [cancelDeferralTimer] is
     * called by [HomeRefresher.stop], [RefreshTrigger.SignedOut] and the
     * user-switch choreography — the pending flag stays armed and the next
     * onStart flushes it.
     */
    private var userDataRefreshJob: Job? = null

    init {
        observeUserDataChanges()
    }

    /**
     * The silent refresh after a user-data change. FORCE remains the rule for
     * external pushes: server-side changes must bypass the TtlCache'd home
     * sections. An echo of THIS device's own writes — every item id of the
     * pushed change recently recorded by [recentLocalWrites] — demotes
     * to a non-forced fetch (TTL semantics): forcing a full
     * cache-bypassing refetch behind the player for every playback-save
     * burst is wasted when only this device's own state moved. The
     * demotion decision itself lives at the flush; this function owns the
     * throttle either way, guarded by
     * [HomeFreshness.USER_DATA_REFRESH_MIN_INTERVAL_MS] against the port's
     * refresh clock (same clock the refresh path writes) so a push
     * arriving right after a regular refresh doesn't re-fetch — UNLESS the
     * last fetch raced a still-pending outbox drain
     * ([Port.lastFetchRacedPendingSync]): the drain-completion echo is then
     * the sync-complete signal, the one notice that the painted snapshot is
     * pre-sync, and must refresh regardless of the clock. No spinner:
     * isRefreshing/isLoading stay untouched.
     *
     * A change that lands inside the throttle window is DEFERRED, not
     * dropped: it arms [pendingUserDataRefresh] and schedules the fetch at
     * throttle expiry (each further echo re-arms the timer on
     * [userDataRefreshJob] — trailing edge, so a playback session's ~10s
     * position saves collapse into one fetch after the last save). Dropping
     * instead strands the final echo of a session — the position save
     * or markPlayed that lands <60s after the last refresh, exactly the
     * state the user sees when they leave the player: Continue Watching /
     * Next Up stay mid-playback stale until the periodic loop's non-forced
     * fetch works through the cache TTLs, or a pull-to-refresh forces it.
     */
    fun refreshAfterUserDataChange() {
        pendingUserDataRefresh = true
        val sinceLastRefresh = clock.nowEpochMillis() - port.lastRefreshTimeMillis()
        if (!port.lastFetchRacedPendingSync() &&
            sinceLastRefresh < HomeFreshness.USER_DATA_REFRESH_MIN_INTERVAL_MS
        ) {
            // Trailing-edge deferral on its own timer job (see
            // [userDataRefreshJob] for why it must not live in the
            // refresher's refresh job). The periodic loop keeps running
            // underneath — deferral only adds the guaranteed TTL-bypassing
            // fetch at expiry.
            userDataRefreshJob?.cancel()
            userDataRefreshJob = scope.launch {
                delay(HomeFreshness.USER_DATA_REFRESH_MIN_INTERVAL_MS - sinceLastRefresh)
                flushPendingUserDataChange()
            }
            return
        }
        flushPendingUserDataChange()
    }

    /**
     * The armed pending latch becomes an immediate fetch — forced for
     * external pushes, non-forced when the change was an echo of this
     * device's own recent writes (the [recentLocalWrites] consult in the
     * force verdict). Called as the [HomeRefresher.start] flush (the
     * pending change predates the return to the screen, the user is looking
     * at it now, and the throttle's anti-spam rationale — don't refetch
     * behind the player for every position save — does not apply: the latch
     * gate below is the only gate here) and as the raced-sync-bypass
     * consumer (the sync-complete echo must refresh regardless of the clock
     * — and stays forced regardless of the demotion, since a TTL-semantics
     * fetch could serve the pre-drain cache).
     *
     * The fetch runs in the refresher's refresh job so onStop cancels it
     * with the job; the periodic loop continues in the same job once the
     * fetch lands. The pending flag stays armed until the fetch
     * lands: if onStop cancels mid-fetch the change isn't lost — the next
     * onStart retries it. The raced-sync bypass is cleared by the same
     * land-only rule (matching the handshake's refetch): a cancellation
     * mid-fetch leaves it armed so the next echo retries.
     */
    fun flushPendingUserDataChange() {
        if (!pendingUserDataRefresh) return
        // Cancelling the timer from inside its own fired body is harmless:
        // nothing suspends in this function after that point, and the refresh
        // job is launched on the scope, not as the timer's child.
        userDataRefreshJob?.cancel()
        userDataRefreshJob = null
        port.launchUserDataRefresh(
            decideForce = {
                // An echo whose every named item was recently written by THIS
                // device runs non-forced (TTL semantics — still debounced and
                // throttled); the forced path
                // stays reserved for external pushes. The raced-sync bypass
                // overrides the demotion: that echo is the sync-complete
                // signal, and a non-forced fetch could serve the pre-drain
                // cache — exactly what the going-online handshake's forced
                // fetch exists to prevent.
                val echoOfOwnWrites = recentLocalWrites.wereAllRecentlyWritten(pendingUserDataChangeItemIds)
                !echoOfOwnWrites || port.lastFetchRacedPendingSync()
            },
            onLanded = {
                pendingUserDataRefresh = false
                pendingUserDataChangeItemIds = emptyList()
            },
        )
    }

    /**
     * The lifecycle / identity-transition drain of the deferral timer: a
     * change deferred for the previous identity (or for the backgrounded
     * episode) stays latched in [pendingUserDataRefresh] and applies on the
     * next [flushPendingUserDataChange] instead of vanishing with the timer.
     */
    fun cancelDeferralTimer() {
        userDataRefreshJob?.cancel()
        userDataRefreshJob = null
    }

    /**
     * The debounced push collector — the chain's only spontaneous input
     * ([refreshAfterUserDataChange] forwarded from
     * `HomeRefresher.request(RefreshTrigger.UserDataChanged)` is the
     * explicit one).
     */
    @OptIn(FlowPreview::class)
    private fun observeUserDataChanges() {
        scope.launch {
            userDataChanges.userDataChanges
                .debounce(HomeFreshness.USER_DATA_CHANGE_REFRESH_DEBOUNCE_MS)
                .collect { change ->
                    // Remember what the (debounced) change named — the
                    // flush consults the write-log with these ids.
                    pendingUserDataChangeItemIds = change.itemIds
                    when {
                        // Offline behaves like backgrounded: arm the pending
                        // flag rather than swallowing the change — a push that
                        // lands during a brief disconnect is then applied on
                        // the next onStart instead of waiting for the periodic
                        // loop to happen across it.
                        port.isAppInForeground() && !port.isOffline() ->
                            refreshAfterUserDataChange()
                        else -> pendingUserDataRefresh = true
                    }
                }
        }
    }
}
