package com.raulshma.jellyplay.core.ui.viewmodel

import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import com.raulshma.jellyplay.core.model.UserDataChange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * The mode a [DeferredFetchCoordinator] fetch runs in — the one bit of
 * context every host fetch body needs and used to thread by hand as a
 * `silent: Boolean`.
 */
enum class FetchMode {
    /**
     * The user asked for this load (screen entry, retry, pull-to-refresh):
     * [DeferredFetchCoordinator.onLoudStart] has already published the loud
     * UI (Loading state / spinner), and a failure surfaces its error.
     */
    LOUD,

    /**
     * The deferred regeneration, fired by screen re-entry after a user-data
     * change landed off-screen: quiet — no loading state, no error reset —
     * and a failure keeps the last published content
     * (serve-stale-while-revalidate) instead of flashing an error over it.
     */
    SILENT,
}

/**
 * The single-flight fetch lifecycle around a [DeferredUserDataRefresher]:
 * one fetch-job slot shared by loud and silent loads — a loud load cancels
 * an in-flight silent one (it regenerates the same data loudly), and the
 * deferred refresh skips itself while any load is active, so two fetches
 * never race into a last-writer-wins swap.
 *
 * Hosts adapt, they do not choreograph. [load] is the loud entry and
 * carries the subject's id; the coordinator tracks what that id's last
 * completed fetch did, which IS the back-stack re-entry guard the detail
 * hosts used to hand-roll (the `currentXId` + `state is Success` checks and
 * `needsLoudReloadOnReentry`): a loud load for the id already showing
 * whose last fetch succeeded is a no-op — re-entry from the back stack
 * re-runs the screen's `LaunchedEffect`, and a second loud load there
 * would only race the deferred refresh's silent regeneration — while a
 * previously-FAILED loud load re-arms the guard so re-entry reloads.
 * [force] bypasses the guard (pull-to-refresh, retry) and reaches [fetch]
 * as the repository cache-bypass flag. A host without an id (music home)
 * instantiates with `Unit` and forces every loud entry: its loud path is a
 * plain refresh, so there is no identity to guard.
 *
 * [fetch] receives the mode so hosts stop threading a `silent: Boolean`
 * they manage themselves: it publishes per mode (a SILENT fetch serves
 * stale-while-revalidate — no loading state, and a failed half keeps the
 * last whole result) and reports plain success, `false` meaning "failed
 * without surfacing anything" (a failed silent regeneration, offline
 * gating). This class owns the re-arm decision
 * ([DeferredUserDataRefresher.rearm]): any failed fetch re-arms, so the
 * next screen re-entry retries instead of trusting the consumed pending
 * flag. A loud failure with nothing pending over-arms at worst: one quiet
 * refetch on the next re-entry. The silent regeneration always runs forced
 * (the announced change it heals must bypass the caches the announce may
 * not have evicted) and targets whatever [load] last accepted; before the
 * first load there is nothing showing, and the silent regeneration
 * completes without fetching or consuming anything.
 *
 * The loud UI choreography is module-owned so its orderings cannot drift
 * per host: [onLoudStart] publishes the Loading state synchronously when a
 * loud load is accepted, and [onLoudError] is the thrown-fetch twin of the
 * `false` path — a repo path that blew up before building its Result must
 * clear that Loading UI and surface the error exactly as a `false` return
 * would (a SILENT throw stays fully quiet: it counts as failure and
 * re-arms, nothing more). The fetch invocation is wrapped in
 * [runCatchingRethrowingCancellation], so a cancellation always propagates
 * — a cancelled loud load cannot mask as a fetch failure and strand the
 * spinner it published — and cancellation never re-arms (the cancelling
 * load is itself the regeneration). A silent success landing over a FAILED
 * loud load heals the re-entry guard, and [onSilentHeal] lets the host
 * clear that loud failure's error surface with it (the album detail's load
 * error shares a field with mix errors, so the clear must be told apart
 * from a mix failure).
 *
 * The loud-cancels-silent rule assumes the loud load regenerates the data
 * the silent pass was regenerating; when that may not hold (a VM reused
 * for a new id), the cancelled silent pass's consumed pending flag would
 * strand — so [load] re-arms before cancelling an in-flight silent fetch.
 * When both loads target the same data that re-arm over-arms at worst:
 * one redundant quiet refetch on the next re-entry.
 *
 * Main-thread confinement as [DeferredUserDataRefresher]: construct with
 * the ViewModel's main-immediate scope and only touch it from that scope.
 */
class DeferredFetchCoordinator<K>(
    userDataChanges: Flow<UserDataChange>,
    private val scope: CoroutineScope,
    private val fetch: suspend (id: K, mode: FetchMode, force: Boolean) -> Boolean,
    private val onLoudStart: () -> Unit = {},
    private val onLoudError: (exception: Exception) -> Unit = { _ -> },
    private val onSilentHeal: () -> Unit = {},
) {

    /**
     * The one fetch in flight, loud or silent — see the class doc for the
     * single-flight contract.
     */
    private var fetchJob: Job? = null

    /** Whether [fetchJob] is the silent regeneration — see [load]. */
    private var fetchJobIsSilent = false

    /**
     * The id the last accepted loud load targeted — null until then, which
     * is exactly "nothing showing": the silent regeneration has no target
     * and no-ops (the hosts' old `currentXId?.let { … } ?: true` guard).
     */
    private var loadedId: K? = null

    /**
     * Whether [loadedId]'s last COMPLETED fetch (loud or silent) succeeded —
     * the success half of the re-entry guard (see [load]). A failed loud
     * load clears it (re-entry must reload); a later silent success sets it
     * again (healed content must not flash-reload); a silent failure leaves
     * it untouched (whatever is on screen is still the last successfully
     * fetched result). In-flight loads never decide: single-flight
     * guarantees only the surviving fetch's completion writes.
     */
    private var loadedSuccessfully = false

    /** The screen wires this with a single `DeferredRefreshEffect(...)`. */
    val deferredRefresher = DeferredUserDataRefresher(
        userDataChanges = userDataChanges,
        scope = scope,
        onRefresh = ::refreshSilently,
    )

    /**
     * Starts (or restarts) the loud load for [id], cancelling any in-flight
     * fetch. The re-entry guard no-ops when [id] is already showing and its
     * last completed fetch succeeded; [force] bypasses the guard and reaches
     * [fetch] as the repository cache-bypass flag.
     */
    fun load(id: K, force: Boolean = false) {
        if (!force && id == loadedId && loadedSuccessfully) return
        loadedId = id
        // In-flight loads never decide: until this load's fetch completes,
        // the guard must not read the previous id's success (a re-entrant
        // load during the in-flight window reloads instead of no-oping).
        loadedSuccessfully = false
        if (fetchJob?.isActive == true && fetchJobIsSilent) {
            // The in-flight silent regeneration consumed the pending flag
            // for data this loud load only regenerates when it targets the
            // same subject — if it doesn't (a VM reused for a new id), only
            // the re-armed flag can heal that data on a later re-entry.
            deferredRefresher.rearm()
        }
        fetchJob?.cancel()
        fetchJobIsSilent = false
        // Synchronous, before the launch: hosts whose loud state is a whole
        // Loading screen state publish it here, and the screen must read
        // Loading the moment it calls load — not after the scope dispatches.
        onLoudStart()
        fetchJob = scope.launch {
            val ok = runFetch(id, FetchMode.LOUD, force)
            loadedSuccessfully = ok
            if (!ok) {
                deferredRefresher.rearm()
            }
        }
    }

    private fun refreshSilently() {
        val id = loadedId ?: return // nothing showing — nothing to regenerate
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
                if (runFetch(id, FetchMode.SILENT, force = true)) {
                    // A silent success over a failed loud load heals the
                    // re-entry guard — and tells the host, so the loud
                    // failure's error surface goes with it (a mix error
                    // must survive).
                    if (!loadedSuccessfully) {
                        onSilentHeal()
                    }
                    loadedSuccessfully = true
                } else {
                    deferredRefresher.rearm()
                }
            }
        }
    }

    /**
     * Host fetch bodies report failure as `false`; one that throws a
     * non-cancellation exception (a repo path that blew up before building
     * its Result) must count as failure too — otherwise it would skip the
     * re-arm and escape to the scope's uncaught handler.
     * [runCatchingRethrowingCancellation] keeps cancellation propagating
     * (a cancelled load must not mask as failure); [onLoudError] carries a
     * LOUD exception back so the host can clear its loud UI exactly as it
     * would for a `false` return, while a SILENT exception stays quiet.
     * Throwables that are not [Exception]s are not fetch failures — they
     * rethrow and surface as they always did.
     */
    private suspend fun runFetch(id: K, mode: FetchMode, force: Boolean): Boolean =
        runCatchingRethrowingCancellation { fetch(id, mode, force) }
            .fold(
                onSuccess = { it },
                onFailure = { e ->
                    if (e !is Exception) throw e
                    if (mode == FetchMode.LOUD) onLoudError(e)
                    false
                },
            )
}
