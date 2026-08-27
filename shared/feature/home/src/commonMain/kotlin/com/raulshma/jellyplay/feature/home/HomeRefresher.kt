package com.raulshma.jellyplay.feature.home

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.repository.ArrRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.data.usecase.OrderHomeSectionsUseCase
import com.raulshma.jellyplay.core.data.util.TimeSource
import com.raulshma.jellyplay.core.data.widget.ContinueWatchingBroadcaster
import com.raulshma.jellyplay.core.data.widget.LibrarySyncHook
import com.raulshma.jellyplay.core.data.worker.TvWatchNextScheduler
import com.raulshma.jellyplay.core.datastore.widget.WidgetDataStore
import com.raulshma.jellyplay.core.model.HomeFreshness
import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.HomeSectionQuery
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.NetworkStatus
import com.raulshma.jellyplay.core.model.OfflineMode
import com.raulshma.jellyplay.core.model.seerr.DiscoverSectionType
import com.raulshma.jellyplay.core.model.seerr.SeerrPreferences
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.toKotlinLocalDate
import java.time.ZoneOffset

/**
 * Deep module: the Home screen's entire refresh policy behind one small
 * interface — who may fetch, when, how often, and which spinners show while
 * it happens.
 *
 * Previously this policy lived inline on the ~1390-LOC [HomeViewModel] as
 * ~250 lines of interleaved machinery: a [Mutex] plus a replaceable refresh
 * [Job], a single `lastRefreshTime` clock read by three different
 * throttle/staleness rules, a foreground/background-jittered `while(true)`
 * loop, a debounced server-push collector, a discover TTL gate, and seven
 * `_uiState` writes scattered across the fetch fan-out. Every home change
 * had to re-learn those invariants by reading the whole VM, and testing any
 * of them required constructing the VM itself — Robolectric,
 * ProcessLifecycleOwner, and a `while(true)` loop leaked past each test's
 * teardown. The bug class this extraction kills: split-brain refresh state
 * (two call sites disagreeing about who owns the spinner or the clock) and
 * cadence/throttle changes that could only be regression-tested end-to-end.
 *
 * Division of labour (deliberate):
 *  * The refresher owns WHAT and WHEN: exclusive mutex ownership, the
 *    [HomeRefreshState] writes (it is the SOLE writer of `sections` — item
 *    patches cross the seam through [patchItems], never a direct write),
 *    the refresh clock (one clock, one owner — failed/offline attempts
 *    still count as fresh), job choreography, cadence + jitter, discover
 *    TTL, the user-data-push debounce/throttle/deferral chain, and the
 *    offline transitions (the offline-mode mirror plus the user-initiated
 *    going-online handshake: busy flag, full-screen loader, outbox drain,
 *    capped fetch).
 *  * [HomeViewModel] is a flows + `onEvent` facade: it folds [state] into
 *    its single UiState object, resets the scroll anchor on identity
 *    changes and manual refresh (pure VM state the refresher cannot see),
 *    and forwards every user intent as an event — nothing else.
 *  * Per-call inputs (the section plan, Seerr prefs, feature flags) stay
 *    mirrored in the VM and cross the seam as read-only providers, so a
 *    preference change can never half-apply mid-fetch.
 *
 * Manual-refresh preamble policy — spinner raises, content/error clears,
 * discover-cache invalidation (strictly happens-before the forced fetch) —
 * lives in [request], NOT in the VM. The single preamble piece that stays in
 * the VM is the scroll reset on [RefreshTrigger.Manual]: a pure VM side
 * effect on state the refresher cannot see. The VM resets scroll, then calls
 * `request(RefreshTrigger.Manual)`.
 */
internal class HomeRefresher(
    /** The VM's scope: refresh jobs must die with the VM. */
    private val scope: CoroutineScope,
    private val timeSource: TimeSource,
    private val mediaRepository: MediaRepository,
    private val seerrRepository: SeerrRepository,
    private val arrRepository: ArrRepository,
    private val orderHomeSections: OrderHomeSectionsUseCase,
    private val widgetDataStore: WidgetDataStore,
    private val continueWatchingBroadcaster: ContinueWatchingBroadcaster,
    private val tvWatchNextScheduler: TvWatchNextScheduler,
    private val librarySyncHook: LibrarySyncHook,
    /** Offline gate consulted inside the fetch (plus the loop's skip check), and the source of the offline-mode mirror below. */
    private val offlineModeManager: OfflineModeManager,
    /**
     * Lets the playback outbox drain before the going-online fetch so
     * Continue Watching / Next Up reflect the server's post-sync state (the
     * VM hands in its [com.raulshma.jellyplay.core.data.sync.SyncStatusStateHolder]
     * gate; tests hand in a fake).
     */
    private val awaitOutboxDrained: suspend () -> Unit,
    // Per-call inputs — the VM's mutable preference mirrors stay in the VM
    // and are re-read through these providers on every fetch:
    private val planProvider: () -> HomeSectionPrefs,
    private val seerrPreferencesProvider: () -> SeerrPreferences,
    private val discoverEnabledProvider: () -> Boolean,
    private val directArrEnabledProvider: () -> Boolean,
    private val androidTvWatchNextEnabledProvider: () -> Boolean,
) {

    // All cadence/TTL constants live in core:model's HomeFreshness — the one
    // seam for the home freshness policy shared with the cache layers below.

    private companion object {
        /**
         * Hard deadline on the offline→online fetch. There is no withTimeout
         * anywhere down the getHomeSections / fetchDiscoverSections /
         * fetchRecentlyGrabbed chain — only OkHttp's per-call read timeout
         * (which a half-open socket or a hung Seerr await can defeat). Without
         * this cap a stuck fetch parks on refreshMutex forever and
         * isGoingOnline never clears, leaving the Go Online button + app bar
         * spinners spinning until the app is restarted.
         */
        private const val GOING_ONLINE_TIMEOUT_MS = 30_000L
    }

    private val _state = MutableStateFlow(HomeRefreshState())
    val state: StateFlow<HomeRefreshState> = _state.asStateFlow()

    private val refreshMutex = Mutex()
    private var refreshJob: Job? = null
    // Identity-transition choreography (sign-in / user switch) runs here —
    // NOT in [refreshJob]: its fetch must survive a mid-flight [stop] ([stop]
    // cancels only [refreshJob]). Replaced per request so a back-to-back
    // transition cancels the stale handler, mirroring the collectLatest
    // cancellation semantics this used to inherit from the VM's collector.
    private var transitionJob: Job? = null
    // Standalone discover fetches (pref-enable trigger) run outside
    // [refreshJob] — replacing the refresh job for them would cancel an
    // in-flight full refresh — but they are still tracked so [stop] and the
    // identity transitions can cancel an abandoned fan-out.
    private var discoverJob: Job? = null
    // Fallback timer for a [RefreshTrigger.GoingOnline] request whose
    // preference write never lands — see the GoingOnline branch in [request].
    private var goingOnlineWatchdogJob: Job? = null
    private var lastRefreshTime = 0L
    private var isAppInForeground = true
    // Set when a user-data change lands while backgrounded; consumed by [start].
    private var pendingUserDataRefresh = false
    // Discover-sections TTL gate (see HomeFreshness.DISCOVER_TTL_MS / fetchDiscoverSections).
    private val discoverCache = TtlCacheGate(HomeFreshness.DISCOVER_TTL_MS)
    private var lastContinueWatchingIds: Set<String> = emptySet()

    init {
        observeUserDataChanges()
        observeOfflineMode()
    }

    /**
     * The mutex-protected fetch core. Called directly — NOT via [refreshJob]
     * — by the paths that must survive a mid-flight [stop]: the identity
     * transitions ([RefreshTrigger.UserSwitched] handler) and the
     * offline→online handshake both need their fetch to finish even if the
     * app backgrounds underneath it ([stop] cancels only [refreshJob]).
     */
    suspend fun fetchOnce(force: Boolean = false) {
        // Do not drop a refresh that arrives while another request is running.
        // In particular, a sign-in can complete while Home's earlier request is
        // still failing with the just-cleared session. Waiting for the lock
        // ensures the authenticated follow-up request runs and clears that
        // transient error without requiring the user to tap Retry.
        refreshMutex.lock()
        // Deferred widget / TV Watch Next side-effects. Captured inside the
        // mutex (where we can detect a Continue-Watching change) but fired
        // after unlock so a slow broadcast IPC or WorkManager enqueue can't
        // hold the refresh lock.
        var pendingCwSideEffect: (() -> Unit)? = null
        try {
            offlineModeManager.checkNetworkAndAutoDetect()

            if (offlineModeManager.isOffline) {
                // Failed/offline attempts still count as fresh: the loop and
                // the onStart staleness check must not hammer the network
                // every tick while offline.
                lastRefreshTime = timeSource.nowEpochMillis()
                return
            }

            lastRefreshTime = timeSource.nowEpochMillis()
            val plan = planProvider()

            // Stale-while-revalidate: on a cold open (sections still empty),
            // paint the persisted snapshot from Room instantly so the home
            // screen renders before the network refresh below resolves. The
            // fresh fetch overwrites it on success; if the network fails we
            // keep showing stale rather than an empty screen. Only when
            // empty — a pull-to-refresh or pref change already has sections
            // on screen and flashing stale would feel worse than the brief
            // spinner.
            if (_state.value.sections.isEmpty()) {
                orderedCachedSections(plan)?.let { cachedSections ->
                    _state.update { it.copy(sections = cachedSections) }
                }
            }

            // The three fetch groups write independent state fields
            // (sections / discoverSections / recentlyGrabbed), so run them
            // concurrently rather than one after another — cold-open latency
            // becomes the max of the three instead of their sum. The CW
            // widget/TV-Watch-Next side-effect stays tied to the main
            // sections result and is invoked after the mutex releases.
            // Discover/arr are runCatching-wrapped so a failure in either can
            // never cancel the main fetch via coroutineScope's structured
            // concurrency.
            coroutineScope {
                val mainDeferred = async {
                    // force = the home screen's manual refresh /
                    // pull-to-refresh: bypass this query's home-sections
                    // cache rather than dropping every cache in the
                    // repository (plan 08).
                    mediaRepository.getHomeSections(plan.query, force = force)
                }
                val discoverDeferred = if (discoverEnabledProvider()) {
                    async { runCatching { fetchDiscoverSections(seerrPreferencesProvider()) } }
                } else null
                // Direct *arr "Recently Grabbed" calendar — gated by the
                // DIRECT_ARR_INTEGRATION flag and the same TTL gate as
                // discover sections so it never adds extra round-trips on
                // every refresh.
                val arrDeferred = if (directArrEnabledProvider()) {
                    async { runCatching { fetchRecentlyGrabbed() } }
                } else null

                mainDeferred.await()
                    .onSuccess { homeResult ->
                        val fetchedSections = homeResult.sections
                        // Surface a non-blocking notice only when a section
                        // type actually failed to load (403/500/network).
                        // Sections that returned zero items (e.g. no watch
                        // history, no Next Up) are NOT failures — previously
                        // the size-mismatch heuristic false-positived on new
                        // users and after merges.
                        _state.update { it.copy(partialLoadError = homeResult.failedSectionTypes.isNotEmpty()) }
                        // OrderHomeSectionsUseCase is pure and operates on a
                        // handful of sections (sub-microsecond), so no thread
                        // offload. A withContext(Dispatchers.Default) hop here
                        // previously escaped the test scheduler and made
                        // isRefreshing/isLoading assertions racy under
                        // StandardTestDispatcher — state writes stay on the
                        // caller's dispatcher.
                        val finalSections = orderHomeSections(
                            sections = fetchedSections,
                            order = plan.homeSectionOrder,
                            mergeContinueWatchingAndNextUp = plan.mergeContinueWatchingAndNextUp,
                        )

                        _state.update { it.copy(sections = finalSections) }

                        val continueWatching = finalSections
                            .find { it.type == HomeSectionType.CONTINUE_WATCHING }
                            ?.items ?: emptyList()
                        val currentIds = continueWatching.map { it.id }.toSet()
                        if (currentIds != lastContinueWatchingIds) {
                            lastContinueWatchingIds = currentIds
                            widgetDataStore.setContinueWatching(continueWatching)
                            // Defer the widget broadcast + TV Watch Next
                            // refresh until after the mutex is released (see
                            // pendingCwSideEffect above).
                            pendingCwSideEffect = {
                                // Push the CW change to the home-screen
                                // widget. The broadcaster owns the
                                // explicit-component broadcast so no Android
                                // Context is needed here.
                                continueWatchingBroadcaster.refreshContinueWatching()
                                // Refresh the Android TV "Watch Next" OS row
                                // so the system home stays in sync with the
                                // user's progress. Worker is a no-op on
                                // phones and respects its preference.
                                if (androidTvWatchNextEnabledProvider()) {
                                    tvWatchNextScheduler.scheduleRefresh()
                                }
                            }
                        }

                        _state.update { it.copy(error = null) }

                        // A successful foreground library scan is the shared
                        // hook for the auto_download foreground drain and the
                        // widget recommendations refresh. Wrapped in
                        // runCatching (the impl also self-guards) so a
                        // downstream failure can never break the home
                        // refresh.
                        runCatching { librarySyncHook.onLibraryScanComplete() }
                    }
                    .onFailure { throwable ->
                        if (_state.value.sections.isEmpty()) {
                            _state.update { s -> s.copy(error = throwable.message ?: "${throwable::class.simpleName}") }
                        }
                        _state.update { it.copy(partialLoadError = false) }
                    }

                // Await the optional groups so coroutineScope doesn't return
                // before their state writes land. Errors are already
                // swallowed by runCatching above; the results are
                // intentionally ignored.
                discoverDeferred?.await()
                arrDeferred?.await()
            }
        } finally {
            // Defensive spinner clear — but only if this fetch was NOT
            // cancelled: whoever cancelled it (the user-switch paint, a
            // replaced refresh job, [stop], the going-online timeout) owns
            // the flags now. A cancelled fetch clearing them here used to
            // land between the user-switch paint and its replacement fetch,
            // exposing sections=empty + isLoading=false + error=null — the
            // cold-launch "No Content Available" flash. A fetch that merely
            // raced a CONCURRENT fetch on the mutex still completes under
            // its own power and keeps clearing.
            // (isGoingOnline — the third flag this clear used to drop — is
            // owned by the going-online handshake below, including its
            // finally.)
            // On cancellation the CW side-effect below is intentionally
            // skipped: a fetch cancelled mid-flight may have stale CW data.
            if (currentCoroutineContext().isActive) {
                _state.update { it.copy(isLoading = false, isRefreshing = false) }
            }
            refreshMutex.unlock()
        }
        pendingCwSideEffect?.invoke()
    }

    /**
     * Fire-and-forget refresh entry points whose job-replacement policy
     * lives here — the VM's event handlers do nothing but forward the
     * trigger (plus the scroll reset for [RefreshTrigger.Manual], the one
     * preamble piece that is a pure VM side effect).
     *
     *  * [Manual] / [PullToRefresh]: replace [refreshJob] with ONE job that
     *    runs the preamble, a forced fetch, then the periodic loop. Preamble
     *    policy (spinner raise, content/error clear, discover-cache
     *    invalidation — the invalidation strictly happens-before the fetch)
     *    is refresher-owned; Manual additionally clears sections for the
     *    full-screen loader, PullToRefresh keeps content and raises only the
     *    inline spinner.
     *  * [PrefsChanged]: replace [refreshJob] with a NON-forced fetch (the
     *    new query is what matters, not cache bypass) followed by the loop.
     *    Previously this fetch ran in the VM's prefs collector outside any
     *    refresh job, so a backgrounding could not cancel it; folding it
     *    into the refresh job aligns it with every other trigger.
     *  * [UserDataChanged]: throttled silent forced refresh — see
     *    [refreshAfterUserDataChange].
     *  * [UserSwitched] / [SignedOut]: identity-transition choreography —
     *    the SWR snapshot paint + outside-the-job fetch below, and the
     *    sign-out reset respectively.
     *  * [DiscoverEnabled]: standalone discover-only fetch — see [fetchDiscover].
     *  * [GoingOnline]: user-initiated offline→online transition — raises
     *    the busy flag and toggles manual offline; the drain+fetch handshake
     *    itself runs in [observeOfflineMode] when the ONLINE emission lands.
     */
    fun request(trigger: RefreshTrigger) {
        when (trigger) {
            RefreshTrigger.Manual -> startForcedRefresh {
                _state.update {
                    it.copy(isLoading = true, sections = emptyList(), discoverSections = emptyMap(), error = null)
                }
                invalidateDiscoverCache()
            }
            RefreshTrigger.PullToRefresh -> startForcedRefresh {
                _state.update { it.copy(isRefreshing = true, error = null) }
                invalidateDiscoverCache()
            }
            RefreshTrigger.PrefsChanged -> replaceRefreshJob {
                fetchOnce()
                periodicRefreshLoop()
            }
            RefreshTrigger.UserDataChanged -> refreshAfterUserDataChange()
            RefreshTrigger.UserSwitched -> replaceTransitionJob { refreshForUserSwitch() }
            RefreshTrigger.SignedOut -> {
                // Sign-out is synchronous (no fetch): cancel the in-flight
                // transition job first — collectLatest-equivalent semantics
                // for a back-to-back identity change — then reset.
                transitionJob?.cancel()
                transitionJob = null
                refreshJob?.cancel()
                discoverJob?.cancel()
                _state.update { it.copy(sections = emptyList(), discoverSections = emptyMap(), error = null, isLoading = true) }
            }
            RefreshTrigger.DiscoverEnabled -> fetchDiscover()
            RefreshTrigger.GoingOnline -> {
                // Going online is async (preference write → mode flip →
                // drain + fetch) and previously gave zero feedback. Flip the
                // busy flag the UI can show a spinner on BEFORE the toggle,
                // so [observeOfflineMode] can tell this user-initiated
                // transition apart from an external/auto online flip (only
                // this one runs the handshake). The flag clears when the
                // transition resolves — or immediately if the mode flips
                // back offline first.
                _state.update { it.copy(isGoingOnline = true) }
                offlineModeManager.toggleManualOffline()
                // Fallback: the toggle is a preference write on the
                // manager's own scope. If that write is lost, the mode flow
                // never emits ONLINE and no observer path runs — clear the
                // flag ourselves after the same cap the handshake uses.
                // When the ONLINE emission does land, the handshake's
                // finally owns the flag and this watchdog's mode check
                // no-ops.
                goingOnlineWatchdogJob?.cancel()
                goingOnlineWatchdogJob = scope.launch {
                    delay(GOING_ONLINE_TIMEOUT_MS)
                    if (_state.value.isGoingOnline &&
                        offlineModeManager.offlineMode.value != OfflineMode.ONLINE
                    ) {
                        _state.update { it.copy(isGoingOnline = false) }
                    }
                }
            }
        }
    }

    /**
     * The single sanctioned way to mutate items inside the cached sections
     * from outside the fetch machinery — the VM's optimistic played/unplayed
     * container forwards here. Maps the patch over EVERY section, because
     * the same item can appear in several (e.g. Continue Watching and Latest
     * in X) and every visible card must flip together. Keeps
     * [HomeRefreshState.sections] single-writer: the VM folds emissions, it
     * never writes them.
     */
    fun patchItems(itemId: String, patch: (MediaItem) -> MediaItem) {
        _state.update { state ->
            state.copy(
                sections = state.sections.map { section ->
                    section.copy(
                        items = section.items.map { if (it.id == itemId) patch(it) else it }
                    )
                }
            )
        }
    }

    /**
     * Sign-in / user-switch choreography. Cancels any pending refresh job,
     * paints the persisted SWR snapshot, fetches OUTSIDE [refreshJob] — the
     * sign-in fetch must complete even if the app backgrounds mid-fetch
     * ([stop] cancels only [refreshJob]) — then restarts the periodic loop.
     *
     * Stale-while-revalidate paint: show the persisted snapshot (if any)
     * instead of clearing to empty. The empty+isLoading path drives a
     * full-screen loading box (DelayedLoadingScreen) that looks like the
     * splash screen re-appearing — so when cached content exists, show it
     * immediately and drop isLoading; the network fetch below revalidates
     * and overwrites. Only clear+load when there's genuinely nothing to
     * show.
     */
    private suspend fun refreshForUserSwitch() {
        refreshJob?.cancel()
        // An in-flight standalone discover fetch belongs to the previous
        // identity; letting it land would repopulate the just-cleared
        // discoverSections with the previous user's rows.
        discoverJob?.cancel()
        val plan = planProvider()
        val cachedSections = orderedCachedSections(plan)
        _state.update {
            if (cachedSections != null) {
                it.copy(
                    sections = cachedSections,
                    discoverSections = emptyMap(),
                    error = null,
                    isLoading = false,
                )
            } else {
                it.copy(sections = emptyList(), discoverSections = emptyMap(), error = null, isLoading = true)
            }
        }
        fetchOnce()
        startPeriodicRefresh()
    }

    /**
     * Online→offline transition: drop the cached ONLINE sections + discover
     * rows (the offline home renders the offline library instead). Touches
     * neither error nor spinners — mirrors the previous inline clear, which
     * also cleared the going-online spinner separately.
     */
    private fun dropOnlineContent() {
        _state.update { it.copy(sections = emptyList(), discoverSections = emptyMap()) }
    }

    /**
     * Raises the full-screen loader for the going-online handshake (the
     * post-toggle fetch renders behind it). Paired with
     * [clearFullScreenLoader], these are the only isLoading writes from
     * outside the fetch machinery itself — routing them through here keeps
     * every [HomeRefreshState.isLoading] transition inside this class.
     */
    private fun showFullScreenLoader() {
        _state.update { it.copy(isLoading = true) }
    }

    /**
     * Force-clears the full-screen loader raised by [showFullScreenLoader]
     * — the going-online handshake clears it in its `finally` so a hung or
     * cancelled post-toggle fetch cannot leave it stuck on.
     */
    private fun clearFullScreenLoader() {
        _state.update { it.copy(isLoading = false) }
    }

    /**
     * onStart: network re-check, stale fetch, periodic-loop start, then the
     * deferred user-data flush — in that ORDER. The stale-check and the loop
     * share ONE [refreshJob] (a bare scope.launch here was invisible to
     * [stop], so a fetch started at onStart kept running after an immediate
     * onStop); the loop continues in the same job once the (possible) stale
     * fetch lands. The deferred flush below REPLACES this job — its forced
     * fetch supersedes the stale-check.
     */
    fun start() {
        isAppInForeground = true
        replaceRefreshJob {
            offlineModeManager.checkNetworkAndAutoDetect()
            val now = timeSource.nowEpochMillis()
            if (now - lastRefreshTime >= HomeFreshness.REFRESH_INTERVAL_FOREGROUND_MS) {
                fetchOnce()
            }
            periodicRefreshLoop()
        }
        // A user-data change that arrived while backgrounded refreshes now —
        // bypassing the 60s stale check above, but still subject to the
        // user-data throttle inside refreshAfterUserDataChange. The pending
        // flag is NOT cleared here: when the throttle blocks, the flag stays
        // armed so the next onStart retries instead of silently dropping the
        // change to the periodic loop. Runs AFTER startPeriodicRefresh so the
        // deferred refresh's job (forced fetch + loop) replaces the bare
        // periodic loop instead of being cancelled by it.
        if (pendingUserDataRefresh) {
            refreshAfterUserDataChange()
        }
    }

    /**
     * onStop: cancel the refresh job (forced fetch and/or loop), the standalone discover fetch, and drop them.
     * The identity-transition job is deliberately NOT cancelled here — the
     * sign-in fetch must survive an immediate backgrounding (see
     * [refreshForUserSwitch]); it dies with the scope / is replaced by the
     * next transition instead.
     */
    fun stop() {
        isAppInForeground = false
        refreshJob?.cancel()
        refreshJob = null
        discoverJob?.cancel()
        discoverJob = null
        // stop() cancels with NO replacement fetch, so the cancelled fetch's
        // guarded finally will not clear the flags — clear them here so a
        // backgrounded cold start doesn't sit on a stuck loader.
        _state.update { it.copy(isLoading = false, isRefreshing = false) }
    }

    /**
     * Resets the discover-sections TTL so the next [fetchDiscoverSections]
     * actually hits the network. Called on user-initiated refresh.
     */
    private fun invalidateDiscoverCache() {
        discoverCache.invalidate()
    }

    /**
     * Discover-only fetch (pref- and TTL-gated), used when the user enables
     * Discover so rows appear before the next full refresh would pick them
     * up. Runs in [scope] outside the refresh mutex — same as before
     * extraction — but in a tracked, replaceable [discoverJob] so [stop] and
     * the identity transitions cancel an in-flight fan-out instead of letting
     * it run to completion abandoned.
     */
    private fun fetchDiscover() {
        discoverJob?.cancel()
        discoverJob = scope.launch { fetchDiscoverSections(seerrPreferencesProvider()) }
    }

    /**
     * The offline-mode mirror + transition policy, inlined from the VM's own
     * collector so every offline-shaped field of [HomeRefreshState] has one
     * writer. Reacts to ALL [OfflineModeManager.offlineMode] emissions
     * (app-start and external/auto changes included):
     *  * ONLINE → offline (any flavour): drop the cached online sections —
     *    the offline home renders the offline library instead — and clear
     *    any pending going-online spinner (the user or an auto-detect flip
     *    took us back offline while the prior online fetch was still parked
     *    on the refresh mutex).
     *  * offline → ONLINE: mirror the field only, UNLESS a
     *    [RefreshTrigger.GoingOnline] request is in flight — the
     *    user-initiated transition additionally runs the handshake
     *    (full-screen loader, outbox drain, capped fetch) the VM used to
     *    orchestrate itself.
     */
    private fun observeOfflineMode() {
        scope.launch {
            offlineModeManager.offlineMode.collect { mode ->
                // Capture the previous mode before overwriting so transition
                // detection is stable across the rapid manual+auto+network
                // re-emissions a single toggle can produce.
                val previousMode = _state.value.offlineMode
                _state.update { it.copy(offlineMode = mode) }
                when {
                    previousMode == OfflineMode.ONLINE && mode != OfflineMode.ONLINE -> {
                        dropOnlineContent()
                        _state.update { it.copy(isGoingOnline = false) }
                    }
                    previousMode != OfflineMode.ONLINE && mode == OfflineMode.ONLINE && _state.value.isGoingOnline -> {
                        // Offline → online (user-initiated): show the
                        // full-screen loader during the post-toggle fetch so
                        // the online branch doesn't flash blank between the
                        // mode flip and sections arriving. isGoingOnline MUST
                        // clear in finally — a bare after-the-fetch clear
                        // would leave it stuck on forever (and the user
                        // restarting the app to recover) whenever the
                        // handshake throws or is cancelled.
                        showFullScreenLoader()
                        try {
                            // Let the playback outbox drain before fetching so
                            // Continue Watching / Next Up reflect the server's
                            // post-sync state. Without this the fetch can race
                            // the drain: CW would still list an episode the user
                            // marked unplayed offline, because the server hasn't
                            // processed the mark yet. The drain is fast on
                            // reconnect; on timeout we fetch anyway and the next
                            // periodic refresh / pull-to-refresh re-syncs.
                            awaitOutboxDrained()
                            // Cap the post-toggle fetch so a hung network call
                            // cannot leave isGoingOnline (and the loader) stuck
                            // on — the symptom was the Go Online button + app
                            // bar spinners never clearing. On timeout we drop the
                            // result; a normal refresh/pull-to-refresh can
                            // still repopulate sections once the network
                            // recovers. The loader is force-cleared below.
                            withTimeoutOrNull(GOING_ONLINE_TIMEOUT_MS) {
                                fetchOnce()
                            }
                        } finally {
                            clearFullScreenLoader()
                            _state.update { it.copy(isGoingOnline = false) }
                        }
                    }
                }
            }
        }
    }

    /**
     * Live home refresh on server `UserDataChanged` pushes (played /
     * favorite flips from any client, including this one). Bursts are
     * debounced into a single silent refresh; a change that lands while
     * backgrounded (or offline) is deferred to the next [start] instead of
     * refreshing into the void.
     */
    @OptIn(FlowPreview::class)
    private fun observeUserDataChanges() {
        scope.launch {
            mediaRepository.userDataChanges
                .debounce(HomeFreshness.USER_DATA_CHANGE_REFRESH_DEBOUNCE_MS)
                .collect {
                    when {
                        // Offline behaves like backgrounded: arm the pending
                        // flag rather than swallowing the change — a push that
                        // lands during a brief disconnect is then applied on
                        // the next onStart instead of waiting for the periodic
                        // loop to happen across it.
                        isAppInForeground && !offlineModeManager.isOffline ->
                            refreshAfterUserDataChange()
                        else -> pendingUserDataRefresh = true
                    }
                }
        }
    }

    /**
     * Silent forced refresh after a user-data change. force = true because
     * server-side changes must bypass the TtlCache'd home sections. Guarded
     * by [HomeFreshness.USER_DATA_REFRESH_MIN_INTERVAL_MS] against
     * [lastRefreshTime] (same clock the refresh path writes) so a push
     * arriving right after a regular refresh doesn't re-fetch, and so the
     * server's echo of this device's own playback saves cannot force-refresh
     * more than once a minute. No spinner: isRefreshing/isLoading stay
     * untouched.
     */
    private fun refreshAfterUserDataChange() {
        if (timeSource.nowEpochMillis() - lastRefreshTime < HomeFreshness.USER_DATA_REFRESH_MIN_INTERVAL_MS) return
        // Tracked in refreshJob so onStop cancels the forced fetch with it;
        // the periodic loop continues in the same job once the fetch lands
        // (a separate startPeriodicRefresh() call here would either be
        // cancelled by its own refreshJob hand-off or restart the loop while
        // backgrounded). The pending flag stays armed until the fetch lands:
        // if onStop cancels mid-fetch the change isn't lost — the next
        // onStart retries it (still subject to the throttle above).
        pendingUserDataRefresh = true
        replaceRefreshJob {
            fetchOnce(force = true)
            pendingUserDataRefresh = false
            periodicRefreshLoop()
        }
    }

    /**
     * Replaces [refreshJob] with a forced fetch followed by the periodic
     * loop — the shared shape of manual refresh, pull-to-refresh, and the
     * user-data-change refresh. [preamble] runs first inside the new job
     * (spinner/clear state, discover-cache invalidation); fetch and loop
     * stay in ONE job so [stop] cancels them together.
     */
    private fun startForcedRefresh(preamble: suspend () -> Unit = {}) {
        replaceRefreshJob {
            preamble()
            fetchOnce(force = true)
            periodicRefreshLoop()
        }
    }

    private fun startPeriodicRefresh() {
        replaceRefreshJob { periodicRefreshLoop() }
    }

    /**
     * Cancels any in-flight [refreshJob] and replaces it with [block] in
     * [scope] — the one job-replacement choreography every trigger shares,
     * so each entry point below states only its own fetch/loop sequence.
     */
    private fun replaceRefreshJob(block: suspend () -> Unit) {
        refreshJob?.cancel()
        refreshJob = scope.launch { block() }
    }

    /**
     * The identity-transition counterpart of [replaceRefreshJob]: a new
     * [RefreshTrigger.UserSwitched] / [SignedOut] request cancels the
     * previous transition's handler mid-flight — the collectLatest
     * cancellation semantics the VM's HomeSession collector used to supply
     * when these bodies still ran inside its `collectLatest` block.
     */
    private fun replaceTransitionJob(block: suspend () -> Unit) {
        transitionJob?.cancel()
        transitionJob = scope.launch { block() }
    }

    /**
     * The periodic home-refresh loop: sleep one jittered interval, skip
     * while offline or when a fetch landed recently, then fetch. Runs inside
     * [refreshJob] so lifecycle ([stop]) cancels it together with any forced
     * fetch that preceded it in the same job.
     */
    private suspend fun periodicRefreshLoop() {
        while (true) {
            val interval = if (isAppInForeground) {
                HomeFreshness.REFRESH_INTERVAL_FOREGROUND_MS
            } else {
                HomeFreshness.REFRESH_INTERVAL_BACKGROUND_MS
            }
            // ±10% jitter avoids synchronized refresh storms when multiple
            // devices hit the server on the same fixed mark.
            val jitter = (interval * 0.1f * (kotlin.random.Random.nextFloat() * 2f - 1f)).toLong()
            delay(interval + jitter)

            // Skip while device has no network: fetchOnce would no-op after
            // acquiring the refresh mutex anyway, so this avoids the mutex
            // churn and the lastRefreshTime bookkeeping.
            if (offlineModeManager.isOffline) continue

            val now = timeSource.nowEpochMillis()
            if (now - lastRefreshTime < HomeFreshness.MIN_REFRESH_INTERVAL_MS) continue

            // fetchOnce owns the single lastRefreshTime clock and stamps it on
            // every exit path (including the offline skip), so no re-stamp here.
            fetchOnce()
        }
    }

    /**
     * Reads the persisted SWR snapshot for [plan] and orders it for display,
     * or null if nothing is cached / the cached sections are empty. Shared
     * by the user-switch paint ([refreshForUserSwitch]) and the cold-open
     * path in [fetchOnce].
     */
    private suspend fun orderedCachedSections(plan: HomeSectionPrefs): List<HomeSection>? =
        runCatching { mediaRepository.getCachedHomeSections(plan.query) }
            .getOrNull()
            ?.takeIf { it.sections.isNotEmpty() }
            ?.let { cached ->
                orderHomeSections(
                    sections = cached.sections,
                    order = plan.homeSectionOrder,
                    mergeContinueWatchingAndNextUp = plan.mergeContinueWatchingAndNextUp,
                )
            }

    /**
     * Refreshes the *arr calendar window and pushes the merged list into
     * [HomeRefreshState.recentlyGrabbed] as [SeerrSearchItem]s (reusing the
     * TMDB card model so no new card UI is needed). Window is now → +30
     * days so "coming soon" + freshly-grabbed items both surface. Failures
     * degrade to empty; the *arr repository already swallows per-server
     * errors.
     */
    private suspend fun fetchRecentlyGrabbed() {
        val now = timeSource.today(ZoneOffset.systemDefault())
        val end = now.plusDays(30)
        // Wave 15B: ArrRepository takes kotlinx.datetime.LocalDate now; the
        // home pipeline keeps java.time (TimeSource seam) and converts at the
        // boundary.
        arrRepository.refreshCalendar(now.toKotlinLocalDate(), end.toKotlinLocalDate())
        val items = arrRepository.calendar(now.toKotlinLocalDate(), end.toKotlinLocalDate()).first()
        _state.update { it.copy(recentlyGrabbed = items.map { it.toSeerrSearchItem() }) }
    }

    private suspend fun fetchDiscoverSections(prefs: SeerrPreferences) {
        if (!prefs.enabled || !prefs.discoverEnabled) return
        if (offlineModeManager.networkStatus.value == NetworkStatus.Local) return
        // Trending/popular change slowly; cache discover results for
        // HomeFreshness.DISCOVER_TTL_MS so "just sitting on Home" doesn't fan
        // out up to 5 Seerr round-trips per minute (periodic refresh + per
        // pref change). A user-initiated refresh (swipe-to-refresh) bypasses
        // this gate via [invalidateDiscoverCache].
        val now = timeSource.nowEpochMillis()
        if (!discoverCache.shouldFetch(now)) return

        val today = timeSource.today(ZoneOffset.systemDefault()).toString()

        // coroutineScope, not the outer VM scope: the Seerr fan-out must be a
        // child of the calling refresh job (or the tracked fetchDiscover job),
        // so [stop] / the VM's going-online timeout cancels in-flight requests
        // — launching on the VM scope let them escape cancellation and run to
        // completion abandoned.
        val newSections = coroutineScope {
            val deferredResults = mutableListOf<Pair<DiscoverSectionType, Deferred<Result<SeerrSearchResponse>>>>()

            if (prefs.discoverTrending) {
                deferredResults.add(DiscoverSectionType.TRENDING to async { seerrRepository.getTrending() })
            }
            if (prefs.discoverPopularMovies) {
                deferredResults.add(DiscoverSectionType.POPULAR_MOVIES to async { seerrRepository.getDiscoverMovies() })
            }
            if (prefs.discoverPopularTv) {
                deferredResults.add(DiscoverSectionType.POPULAR_TV to async { seerrRepository.getDiscoverTv() })
            }
            if (prefs.discoverUpcomingMovies) {
                deferredResults.add(DiscoverSectionType.UPCOMING_MOVIES to async { seerrRepository.getDiscoverMovies(primaryReleaseDateGte = today) })
            }
            if (prefs.discoverUpcomingTv) {
                deferredResults.add(DiscoverSectionType.UPCOMING_TV to async { seerrRepository.getDiscoverTv(firstAirDateGte = today) })
            }

            val sections = mutableMapOf<DiscoverSectionType, List<SeerrSearchItem>>()
            for ((type, deferred) in deferredResults) {
                deferred.await().onSuccess { response ->
                    sections[type] = response.results
                }
            }
            sections
        }

        discoverCache.markFetched(timeSource.nowEpochMillis())
        _state.update { it.copy(discoverSections = newSections) }
    }
}

/** What caused a refresh — decides the job-replacement policy in [HomeRefresher.request]. */
internal enum class RefreshTrigger {
    /** Menu refresh: full-screen loader, content + error cleared, discover cache invalidated, forced fetch. */
    Manual,
    /** Swipe-to-refresh: inline spinner only (no content clear), error cleared, discover cache invalidated, forced fetch. */
    PullToRefresh,
    /** Home-section preference diff: non-forced fetch with the new query, then loop restart. */
    PrefsChanged,
    /** Server UserDataChanged push: debounced, throttled, silent forced fetch. */
    UserDataChanged,
    /**
     * Sign-in / user switch (from the HomeSession transitions collector):
     * SWR snapshot paint, fetch outside the refresh job, loop restart — see
     * [HomeRefresher.request].
     */
    UserSwitched,
    /** Sign-out: cancel pending refresh work (refresh, discover AND transition jobs) and reset to the empty loading state. */
    SignedOut,
    /** Discover newly enabled in prefs: standalone discover-only fetch outside the refresh mutex. */
    DiscoverEnabled,
    /**
     * User-initiated offline → online transition (the Go Online button):
     * raises the going-online flag and toggles manual offline; the
     * drain+fetch handshake runs in the offline-mode observer when the
     * ONLINE emission lands.
     */
    GoingOnline,
}

/**
 * One snapshot of the VM's section-preference mirrors: the fetch query (all
 * seven [HomeSectionQuery] inputs, NESTED so each is declared exactly once —
 * not mirrored field-for-field here) plus the display-only ordering rules.
 * Bundled so the prefs collector can diff and adopt an emission with a single
 * `!=` / assignment and the refresher can consume one consistent snapshot per
 * fetch; adding a section preference means adding it to [HomeSectionQuery]
 * (fetch inputs) or here (display-only) — not to scattered field listings on
 * the VM.
 */
internal data class HomeSectionPrefs(
    val query: HomeSectionQuery = HomeSectionQuery(),
    val homeSectionOrder: List<HomeSectionType> = HomeSectionType.CONFIGURABLE,
    val mergeContinueWatchingAndNextUp: Boolean = false,
) {

    /**
     * Copy with [type]'s membership in the enabled-sections set toggled —
     * the policy behind the inline section-config sheet's visibility toggle.
     */
    fun withSectionVisible(type: HomeSectionType, visible: Boolean): HomeSectionPrefs =
        copy(query = query.copy(enabledSections = query.enabledSections.toMutableSet().apply {
            if (visible) add(type) else remove(type)
        }))

    /**
     * Copy with [type] swapped with its neighbour in the section order
     * ([up] or down), or null when no swap is possible (type absent, or
     * already at the requested edge) so callers can skip the write.
     */
    fun withSectionMoved(type: HomeSectionType, up: Boolean): HomeSectionPrefs? {
        val index = homeSectionOrder.indexOf(type)
        if (index == -1) return null
        val target = if (up) index - 1 else index + 1
        if (target !in homeSectionOrder.indices) return null
        return copy(homeSectionOrder = homeSectionOrder.toMutableList().apply {
            val removed = removeAt(index)
            add(target, removed)
        })
    }

    /**
     * Copy with [type]'s disabled-state toggled for [libraryId]. The override
     * map is keyed by library id with the DISABLED types as its value set; an
     * empty set removes the key (restoring default-enabled state).
     */
    fun withLibrarySectionVisible(
        libraryId: String,
        type: HomeSectionType,
        visible: Boolean,
    ): HomeSectionPrefs {
        val overrides = query.libraryHomeSectionOverrides.toMutableMap()
        val disabled = overrides[libraryId].orEmpty().toMutableSet()
        if (visible) disabled.remove(type) else disabled.add(type)
        if (disabled.isEmpty()) overrides.remove(libraryId) else overrides[libraryId] = disabled
        return copy(query = query.copy(libraryHomeSectionOverrides = overrides))
    }
}

/**
 * The refresh-owned slice of the home UiState: everything the fetch
 * machinery writes. The VM folds this into its single UiState object (same
 * pattern as the SeerrRequestStateHolder fold) so the UI still observes one
 * state object. [isLoading] starts true — a cold home screen shows the
 * loading box until the first fetch resolves.
 */
@Immutable
internal data class HomeRefreshState(
    val sections: List<HomeSection> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    /** Non-blocking notice shown when some (not all) home sections failed to load. */
    val partialLoadError: Boolean = false,
    val discoverSections: Map<DiscoverSectionType, List<SeerrSearchItem>> = emptyMap(),
    /** Direct *arr "Recently Grabbed / Coming Soon" calendar row. */
    val recentlyGrabbed: List<SeerrSearchItem> = emptyList(),
    /**
     * True while a user-initiated offline→online transition is in progress,
     * so the Go-online affordances can show an inline spinner instead of
     * being silent. Set by [RefreshTrigger.GoingOnline], cleared by the
     * offline-mode observer when the transition resolves (or is superseded
     * by a flip back offline).
     */
    val isGoingOnline: Boolean = false,
    /** Mirror of [OfflineModeManager.offlineMode]; transitions drive the policy in [HomeRefresher.observeOfflineMode]. */
    val offlineMode: OfflineMode = OfflineMode.ONLINE,
)
