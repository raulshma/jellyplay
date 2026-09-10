package com.raulshma.jellyplay.core.ui.viewmodel

import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import com.raulshma.jellyplay.core.model.UserDataChange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The load-lifecycle value a [DeferredFetchCoordinator] owns: the content
 * aggregate [value] plus the loud phase around it. A flat trio, not a sealed
 * Loading/Error/Success, because the phase and the content are orthogonal
 * under serve-stale: a loud load in flight keeps the last [value] behind its
 * spinner, and a loud failure keeps it behind the error — a host whose loud
 * phase is a whole Loading/Error screen (collection/person detail) collapses
 * the trio in its uiState projection and never reads [value] while
 * [isLoading] or [error] is set; a host with a spinner-plus-content layout
 * (album detail) reads all three slots.
 *
 * Hosts project [DeferredFetchCoordinator.state] into their uiState; the only
 * host-side write is the in-place optimistic patch
 * ([DeferredFetchCoordinator.updateValue]).
 */
data class DeferredFetchState<T>(
    /**
     * The last all-or-nothing content: null until the first successful
     * fetch, never cleared by a failure (that is serve-stale).
     */
    val value: T? = null,

    /** True exactly while an accepted loud load's fetch is in flight. */
    val isLoading: Boolean = true,

    /** The last LOUD failure; a silent failure never touches it. */
    val error: Exception? = null,
)

/**
 * The whole-screen host's projection precedence over a [DeferredFetchState]:
 * Loading while a loud load is in flight, then the loud error, then the
 * content. A whole-screen host (collection/person detail) never reads the
 * last content mid-load or mid-failure — its loud phase is a whole
 * Loading/Error screen — so serve-stale collapses to "the Error screen
 * replaces the Success"; each host supplies only its own uiState
 * constructors. The trailing [loading] arm is the boolean `when`'s
 * defensive exhaustive default, not a reachable phase.
 */
fun <T, U> DeferredFetchState<T>.wholeScreenPhase(
    loading: () -> U,
    error: (Exception) -> U,
    content: (T) -> U,
): U {
    val failure = this.error
    val loaded = this.value
    return when {
        isLoading -> loading()
        failure != null -> error(failure)
        loaded != null -> content(loaded)
        else -> loading()
    }
}

/**
 * The deferred-fetch state container: the single-flight job slot around a
 * [DeferredUserDataRefresher] AND the load state it publishes — hosts hand
 * over a [fetch] that returns their whole content aggregate and read
 * [state]; the loud/silent publish policy lives here, not in per-host fetch
 * bodies.
 *
 * The policy, as [state] transitions:
 *
 *  - A loud load accepted ([load]) synchronously sets [DeferredFetchState.isLoading]
 *    and clears [DeferredFetchState.error] — this [state] reads Loading the
 *    moment `load` returns (and the previous error is gone — a retry from
 *    an error screen drops the stale error immediately). A host's
 *    screen-facing projection of [state] trails by its own hop (an eager
 *    `stateIn` fold, or a collector mirroring into Compose state — see the
 *    hosts' KDocs), so a uiState read in the same dispatch as `load` may
 *    still see the previous frame. The last [DeferredFetchState.value] stays
 *    behind the spinner (a pull-to-refresh host keeps rendering its stale
 *    aggregate).
 *  - A loud fetch that returns publishes the value and clears the phase.
 *  - A loud fetch that fails publishes its exception and KEEPS the last
 *    value — the host decides in its projection whether the error replaces
 *    the content (whole-screen hosts) or shows beside/over it.
 *  - A silent regeneration (the deferred refresh fired by screen re-entry
 *    after a user-data change landed off-screen) never touches the loading
 *    phase: a success publishes the value and clears the error (a silent
 *    success over a failed loud load heals it, with no Loading flash on the
 *    way), and a failure publishes NOTHING — serve-stale-while-revalidate.
 *
 * [fetch] returns the whole content aggregate `T` (all-or-nothing: a
 * multi-part fetch that can only half-succeed must throw rather than return
 * a half) and throws on failure. The coordinator decides loud-vs-silent
 * internally — [load] is the loud entry, the re-entry-triggered regeneration
 * is silent and always forced (the announced change it heals must bypass the
 * caches the announce may not have evicted). A thrown [Exception] is a fetch
 * failure per the transitions above; cancellation always propagates (a
 * cancelled load must not mask as failure and strand the spinner it
 * published); a non-[Exception] throwable is not a fetch failure — it
 * rethrows and surfaces as it always did.
 *
 * Single-flight: one fetch-job slot shared by loud and silent loads — a loud
 * load cancels an in-flight silent one (it regenerates the same data
 * loudly), and the deferred refresh skips itself while any load is active,
 * so two fetches never race into a last-writer-wins swap. The re-arm table:
 * any failed fetch re-arms [DeferredUserDataRefresher.rearm] (the next
 * screen re-entry retries instead of trusting the consumed pending flag —
 * over-arming costs one redundant quiet refetch), a skipped refresh behind
 * an in-flight load re-arms (that load's fetches may predate the change),
 * and a CANCELLED fetch never re-arms (the cancelling load is itself the
 * regeneration) — except that [load] re-arms before cancelling an in-flight
 * silent fetch whose consumed flag would otherwise strand when the loud load
 * targets a different subject (a VM reused for a new id).
 *
 * The identity guard the detail hosts used to hand-roll: [load] for the id
 * already showing whose last completed fetch succeeded is a no-op —
 * re-entry from the back stack re-runs the screen's `LaunchedEffect`, and a
 * second loud load there would only race the deferred refresh's silent
 * regeneration — while a previously-FAILED load re-arms the guard so
 * re-entry reloads. [force] bypasses the guard (pull-to-refresh, retry) and
 * reaches [fetch] as the repository cache-bypass flag. In-flight loads never
 * decide the guard: until the current fetch completes, the guard must not
 * read the previous id's success. A host without an id instantiates with
 * `Unit` and forces every loud entry: its loud path is a plain refresh, so
 * there is no identity to guard.
 *
 * Main-thread confinement as [DeferredUserDataRefresher]: construct with
 * the ViewModel's main-immediate scope and only touch it (and
 * [updateValue]) from that scope.
 */
class DeferredFetchCoordinator<K, T>(
    userDataChanges: Flow<UserDataChange>,
    private val scope: CoroutineScope,
    private val fetch: suspend (id: K, force: Boolean) -> T,
) {

    private val _state = MutableStateFlow(DeferredFetchState<T>())

    /** The load lifecycle — see the class doc for the transition table. */
    val state: StateFlow<DeferredFetchState<T>> = _state.asStateFlow()

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
        // Synchronous, before the launch: the loading phase must be visible
        // the moment load returns (and the previous error gone), not after
        // the scope dispatches. The last value stays behind the spinner.
        _state.update { it.copy(isLoading = true, error = null) }
        fetchJob = scope.launch {
            val result = runFetch(id, force)
            loadedSuccessfully = result.isSuccess
            if (result.isSuccess) {
                _state.update {
                    it.copy(value = result.getOrThrow(), isLoading = false, error = null)
                }
            } else {
                _state.update { it.copy(isLoading = false, error = result.exceptionOrNull() as Exception) }
                deferredRefresher.rearm()
            }
        }
    }

    /**
     * Patches the shown content aggregate in place — the optimistic
     * user-data flip a host applies to the value already on screen while
     * the server truth reconciles on the next fetch. A no-op before the
     * first successful fetch (there is nothing showing to patch) and
     * orthogonal to the load phase: it never touches [isLoading] or
     * [error]. It patches the KEPT value — including one behind an
     * in-flight loud reload's spinner, where the completing load's
     * aggregate overwrites the patch.
     */
    fun updateValue(transform: (T) -> T) {
        _state.update { current ->
            current.value?.let { value -> current.copy(value = transform(value)) } ?: current
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
                val result = runFetch(id, force = true)
                if (result.isSuccess) {
                    // Quiet publish: value + heal, and isLoading stays false —
                    // a silent fetch only ever starts with no loud load in
                    // flight, and every completed loud load cleared it.
                    _state.update { it.copy(value = result.getOrThrow(), error = null) }
                    loadedSuccessfully = true
                } else {
                    deferredRefresher.rearm()
                }
            }
        }
    }

    /**
     * A fetch failure is a thrown [Exception]; anything else must not count
     * as one — a cancellation that masked as failure would strand the
     * spinner it published, and an [Error] belongs to the caller, not the
     * load lifecycle. [runCatchingRethrowingCancellation] keeps cancellation
     * propagating; the non-[Exception] rethrow skips the failure re-arm.
     */
    private suspend fun runFetch(id: K, force: Boolean): Result<T> =
        runCatchingRethrowingCancellation { fetch(id, force) }
            .fold(
                onSuccess = { Result.success(it) },
                onFailure = { e ->
                    if (e !is Exception) throw e
                    Result.failure(e)
                },
            )
}
