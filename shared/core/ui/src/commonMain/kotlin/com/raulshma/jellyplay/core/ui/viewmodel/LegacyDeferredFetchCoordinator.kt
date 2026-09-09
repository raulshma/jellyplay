package com.raulshma.jellyplay.core.ui.viewmodel

import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import com.raulshma.jellyplay.core.model.UserDataChange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * The mode a [LegacyDeferredFetchCoordinator] fetch runs in — the one bit of
 * context every host fetch body needs and used to thread by hand as a
 * `silent: Boolean`.
 */
enum class FetchMode {
    /**
     * The user asked for this load (screen entry, retry, pull-to-refresh):
     * [LegacyDeferredFetchCoordinator.onLoudStart] has already published the
     * loud UI (Loading state / spinner), and a failure surfaces its error.
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
 * The pre-state-container [DeferredFetchCoordinator] engine: the fetch-job
 * slot and re-arm table WITHOUT the published load state — the host fetch
 * body receives its [FetchMode] and a Boolean success protocol plus three
 * loud/silent hooks, and owns the publish policy itself.
 *
 * Kept only for the music home screen: its loud fetch publishes PARTIAL
 * content (sections whose sub-fetches failed are dropped from the publish
 * while the load still reports failure so the re-arm heals them), and that
 * partial-publish-on-failure decision needs the mode INSIDE the fetch —
 * which is exactly what the state-container coordinator removed from the
 * host-facing interface. Its shape (multi-field uiState, cold-error vs
 * cached-toast split, offline gate that fails quietly) stays hand-rolled
 * until it can migrate; delete this class when it does. Everything else
 * already migrated — do not add hosts here.
 *
 * Behavior contract: identical to the state-container coordinator's
 * single-flight/re-arm table (see [DeferredFetchCoordinator]'s class doc —
 * [LegacyDeferredFetchCoordinatorTest] pins this engine's copy of it).
 */
class LegacyDeferredFetchCoordinator<K>(
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
