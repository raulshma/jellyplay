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
import com.raulshma.jellyplay.core.model.NetworkStatus
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
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
 *    [HomeRefreshState] writes, the refresh clock (one clock, one owner —
 *    failed/offline attempts still count as fresh), job choreography,
 *    cadence + jitter, discover TTL, and the user-data-push
 *    debounce/throttle/deferral chain.
 *  * [HomeViewModel] keeps only UI-shaped orchestration: folding [state]
 *    into its single UiState object, the scroll reset on manual refresh, the
 *    offline→online timeout wrapper, and sign-in/out side effects it already
 *    owns (`isGoingOnline`).
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
    /** Offline gate consulted inside the fetch (plus the loop's skip check). */
    private val offlineModeManager: OfflineModeManager,
    // Per-call inputs — the VM's mutable preference mirrors stay in the VM
    // and are re-read through these providers on every fetch:
    private val planProvider: () -> HomeSectionPlan,
    private val seerrPreferencesProvider: () -> SeerrPreferences,
    private val discoverEnabledProvider: () -> Boolean,
    private val directArrEnabledProvider: () -> Boolean,
    private val androidTvWatchNextEnabledProvider: () -> Boolean,
) {

    // All cadence/TTL constants live in core:model's HomeFreshness — the one
    // seam for the home freshness policy shared with the cache layers below.

    private val _state = MutableStateFlow(HomeRefreshState())
    val state: StateFlow<HomeRefreshState> = _state.asStateFlow()

    private val refreshMutex = Mutex()
    private var refreshJob: Job? = null
    private var lastRefreshTime = 0L
    private var isAppInForeground = true
    // Set when a user-data change lands while backgrounded; consumed by [start].
    private var pendingUserDataRefresh = false
    // Discover-sections TTL gate (see HomeFreshness.DISCOVER_TTL_MS / fetchDiscoverSections).
    private val discoverCache = TtlCacheGate(HomeFreshness.DISCOVER_TTL_MS)
    private var lastContinueWatchingIds: Set<String> = emptySet()

    init {
        observeUserDataChanges()
    }

    /**
     * The mutex-protected fetch core. Called directly — NOT via [refreshJob]
     * — by the VM-orchestrated paths that must survive a mid-flight [stop]:
     * sign-in ([refreshForUserSwitch]) and the offline→online transition both
     * need their fetch to finish even if the app backgrounds underneath it
     * ([stop] cancels only [refreshJob]).
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
                            order = plan.order,
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
            // Defensive spinner clear. (isGoingOnline — the third flag this
            // clear used to drop — is offline-transition-owned and stays
            // cleared by the VM's offline collector, including its finally.)
            // On cancellation the CW side-effect below is intentionally
            // skipped: a fetch cancelled mid-flight may have stale CW data.
            _state.update { it.copy(isLoading = false, isRefreshing = false) }
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
    suspend fun refreshForUserSwitch() {
        refreshJob?.cancel()
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
     * Sign-out reset: cancels the pending refresh job and clears all
     * refresh-owned content + error, raising the loading flag. The VM
     * clears its own mirror (scroll position) around this call.
     */
    fun onSignedOut() {
        refreshJob?.cancel()
        _state.update { it.copy(sections = emptyList(), discoverSections = emptyMap(), error = null, isLoading = true) }
    }

    /**
     * Online→offline transition: drop the cached ONLINE sections + discover
     * rows (the offline home renders the offline library instead). Touches
     * neither error nor spinners — mirrors the previous inline clear, which
     * also cleared the VM-owned `isGoingOnline` spinner separately.
     */
    fun dropOnlineContent() {
        _state.update { it.copy(sections = emptyList(), discoverSections = emptyMap()) }
    }

    /**
     * Raises the full-screen loader for the VM's offline→online wrapper (the
     * post-toggle fetch renders behind it). Paired with
     * [clearFullScreenLoader], these are the only isLoading writes from
     * outside the fetch machinery itself — routing them through here keeps
     * every [HomeRefreshState.isLoading] transition inside this class, so
     * the VM never writes refresh-owned state directly.
     */
    fun showFullScreenLoader() {
        _state.update { it.copy(isLoading = true) }
    }

    /**
     * Force-clears the full-screen loader raised by [showFullScreenLoader]
     * — the VM's offline→online wrapper clears it in its `finally` so a
     * hung or cancelled post-toggle fetch cannot leave it stuck on.
     */
    fun clearFullScreenLoader() {
        _state.update { it.copy(isLoading = false) }
    }

    /**
     * onStart: network re-check, stale fetch, periodic-loop start, then the
     * deferred user-data flush — in that ORDER. See the comment at the
     * pending flush below: the forced job must replace the bare loop, not be
     * cancelled by it.
     */
    fun start() {
        isAppInForeground = true
        scope.launch {
            offlineModeManager.checkNetworkAndAutoDetect()
            val now = timeSource.nowEpochMillis()
            if (now - lastRefreshTime >= HomeFreshness.REFRESH_INTERVAL_FOREGROUND_MS) {
                fetchOnce()
            }
        }
        startPeriodicRefresh()
        // A user-data change that arrived while backgrounded refreshes now —
        // bypassing the 60s stale check above, but still subject to the
        // user-data throttle inside refreshAfterUserDataChange. Runs AFTER
        // startPeriodicRefresh so the deferred refresh's job (forced fetch +
        // loop) replaces the bare periodic loop instead of being cancelled
        // by it.
        if (pendingUserDataRefresh) {
            pendingUserDataRefresh = false
            refreshAfterUserDataChange()
        }
    }

    /** onStop: cancel the refresh job (forced fetch and/or loop) and drop it. */
    fun stop() {
        isAppInForeground = false
        refreshJob?.cancel()
        refreshJob = null
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
     * extraction.
     */
    fun fetchDiscover() {
        scope.launch { fetchDiscoverSections(seerrPreferencesProvider()) }
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
    private suspend fun orderedCachedSections(plan: HomeSectionPlan): List<HomeSection>? =
        runCatching { mediaRepository.getCachedHomeSections(plan.query) }
            .getOrNull()
            ?.takeIf { it.sections.isNotEmpty() }
            ?.let { cached ->
                orderHomeSections(
                    sections = cached.sections,
                    order = plan.order,
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
        arrRepository.refreshCalendar(now, end)
        val items = arrRepository.calendar(now, end).first()
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

        val deferredResults = mutableListOf<Pair<DiscoverSectionType, Deferred<Result<SeerrSearchResponse>>>>()

        if (prefs.discoverTrending) {
            deferredResults.add(DiscoverSectionType.TRENDING to scope.async { seerrRepository.getTrending() })
        }
        if (prefs.discoverPopularMovies) {
            deferredResults.add(DiscoverSectionType.POPULAR_MOVIES to scope.async { seerrRepository.getDiscoverMovies() })
        }
        if (prefs.discoverPopularTv) {
            deferredResults.add(DiscoverSectionType.POPULAR_TV to scope.async { seerrRepository.getDiscoverTv() })
        }
        if (prefs.discoverUpcomingMovies) {
            deferredResults.add(DiscoverSectionType.UPCOMING_MOVIES to scope.async { seerrRepository.getDiscoverMovies(primaryReleaseDateGte = today) })
        }
        if (prefs.discoverUpcomingTv) {
            deferredResults.add(DiscoverSectionType.UPCOMING_TV to scope.async { seerrRepository.getDiscoverTv(firstAirDateGte = today) })
        }

        val newSections = mutableMapOf<DiscoverSectionType, List<SeerrSearchItem>>()
        for ((type, deferred) in deferredResults) {
            deferred.await().onSuccess { response ->
                newSections[type] = response.results
            }
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
}

/**
 * One consistent snapshot of the VM's section-preference mirrors: the fetch
 * query plus the display ordering rules. Bundled so a fetch can never
 * observe a half-updated preference set — the VM's pref collector writes the
 * mirrors in one pass and the provider re-snapshots on every read.
 */
internal data class HomeSectionPlan(
    val query: HomeSectionQuery,
    val order: List<HomeSectionType>,
    val mergeContinueWatchingAndNextUp: Boolean,
)

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
)
