package com.raulshma.jellyplay.feature.home

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.concurrency.mapConcurrentCatching
import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.error.UserErrorMessages
import com.raulshma.jellyplay.core.data.log.Log
import com.raulshma.jellyplay.core.data.repository.ArrRepository
import com.raulshma.jellyplay.core.data.repository.BookTocCacheRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.NoopBookTocCacheRepository
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.data.usecase.OrderHomeSectionsUseCase
import com.raulshma.jellyplay.core.data.widget.ContinueWatchingBroadcaster
import com.raulshma.jellyplay.core.data.widget.LibrarySyncHook
import com.raulshma.jellyplay.core.data.worker.TvWatchNextScheduler
import com.raulshma.jellyplay.core.datastore.widget.WidgetDataStore
import com.raulshma.jellyplay.core.model.DiscoverRowConfig
import com.raulshma.jellyplay.core.model.DiscoverRowSource
import com.raulshma.jellyplay.core.model.HomeFreshness
import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.HomeSectionPrefs
import com.raulshma.jellyplay.core.model.HomeSectionQuery
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.NetworkStatus
import com.raulshma.jellyplay.core.model.OfflineMode
import com.raulshma.jellyplay.core.model.SeerrRowMedia
import com.raulshma.jellyplay.core.model.descriptor
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import kotlin.time.TimeSource

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
 *    offline transitions (the offline-mode mirror plus the offline→online
 *    reconnect handshake: full-screen loader, outbox drain, capped fetch.
 *    The going-online busy flag itself is NOT here —
 *    [OfflineModeManager.goingOnline] owns it beside the transition that
 *    raises it).
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
    private val clock: HomeClock,
    private val mediaRepository: MediaRepository,
    private val seerrRepository: SeerrRepository,
    private val arrRepository: ArrRepository,
    private val orderHomeSections: OrderHomeSectionsUseCase,
    private val widgetDataStore: WidgetDataStore,
    private val continueWatchingBroadcaster: ContinueWatchingBroadcaster,
    private val tvWatchNextScheduler: TvWatchNextScheduler,
    private val librarySyncHook: LibrarySyncHook,
    /**
     * Local TOC cache backing the Continue Reading row's progress bars: a
     * paged book's real page count lives only here (the server item carries
     * ticks, never the page count), so the fractions decode exactly instead
     * of falling back to the percent reading. Best-effort — a miss or a throw
     * degrades that card to the percent fallback. Defaults to the neutral
     * no-op (tests, no-DB platforms); production wiring (factory + Koin)
     * passes the Room-backed single.
     */
    private val bookTocCacheRepository: BookTocCacheRepository = NoopBookTocCacheRepository(),
    /** Offline gate consulted inside the fetch (plus the loop's skip check), and the source of the offline-mode mirror below. */
    private val offlineModeManager: OfflineModeManager,
    /**
     * Lets the playback outbox drain before the going-online fetch so
     * Continue Watching / Next Up reflect the server's post-sync state (the
     * VM hands in its [com.raulshma.jellyplay.core.data.sync.SyncStatusStateHolder]
     * gate; tests hand in a fake). Returns whether the drain actually
     * completed — `false` means the fetch below races a still-pending sync
     * and paints pre-sync data (see [lastFetchRacedPendingSync]).
     */
    private val awaitOutboxDrained: suspend () -> Boolean,
    // Per-call inputs — the VM's mutable preference mirrors stay in the VM
    // and are re-read through these providers on every fetch:
    private val sectionPrefsProvider: () -> HomeSectionPrefs,
    private val seerrPreferencesProvider: () -> SeerrPreferences,
    private val discoverEnabledProvider: () -> Boolean,
    private val directArrEnabledProvider: () -> Boolean,
    private val androidTvWatchNextEnabledProvider: () -> Boolean,
) {

    // All cadence/TTL constants live in core:model's HomeFreshness — the one
    // seam for the home freshness policy shared with the cache layers below.

    internal companion object {
        /**
         * Hard deadline on the offline→online fetch. There is no withTimeout
         * anywhere down the getHomeSections / fetchDiscoverSections /
         * fetchRecentlyGrabbed chain — only OkHttp's per-call read timeout
         * (which a half-open socket or a hung Seerr await can defeat). Without
         * this cap a stuck fetch parks on refreshMutex forever and the
         * handshake's full-screen loader never clears, leaving the home
         * stuck until the app is restarted. (The going-online busy flag is
         * not at risk anymore: [OfflineModeManager.goingOnline] clears at
         * the ONLINE emission, before this fetch even starts.) The loader
         * MAY legitimately stay up across the handshake's whole bounded
         * sequence — drain wait + capped fetch + drain re-await + capped
         * refetch, the slow-sync path in [observeOfflineMode] — every stage
         * is capped and the finally clears the loader on every exit path.
         */
        private const val GOING_ONLINE_TIMEOUT_MS = 30_000L

        /** Logcat/console tag for the dice roll's degraded-outcome logs. */
        private const val TAG = "HomeRefresher"

        /**
         * Minimum time the rolling flag stays up per roll — the dice spin's
         * display floor. A sub-100ms local-server roll must still show
         * perceptible feedback or the affordance reads as dead.
         */
        private const val ROLL_MIN_SPIN_MS = 700L

        /** Test-visible mirror of [ROLL_MIN_SPIN_MS] (private const can't be read from tests). */
        internal const val ROLL_MIN_SPIN_FOR_TEST = ROLL_MIN_SPIN_MS
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
    private var lastRefreshTime = 0L
    private var isAppInForeground = true
    // Set when a user-data change lands while backgrounded; consumed by [start].
    private var pendingUserDataRefresh = false
    /**
     * The trailing-edge deferral timer of [refreshAfterUserDataChange], on its
     * OWN job — deliberately not [refreshJob]. Installing the delay by
     * replacing [refreshJob] would cancel whatever that job is doing: a
     * Manual / PullToRefresh fetch mid-flight, whose spinners the cancelled
     * body then never clears (the fetch's finally skips the flag clear on
     * cancellation), leaving the loader stuck until the deferred fetch landed
     * up to a minute later. With the timer here, an echo arriving during a
     * fetch only re-arms the delay; the in-flight fetch finishes and clears
     * its own spinners. [stop], [RefreshTrigger.SignedOut] and
     * [refreshForUserSwitch] cancel it alongside [refreshJob] — the pending
     * flag stays armed and the next [start] flushes it.
     */
    private var userDataRefreshJob: Job? = null
    // Discover-sections TTL gate (see HomeFreshness.DISCOVER_TTL_MS / fetchDiscoverSections).
    private val discoverCache = TtlCacheGate(HomeFreshness.DISCOVER_TTL_MS)
    // Custom Seerr discover rows: same TTL policy as the fixed sections. The
    // fetched rows are ALSO memoised in [customSeerrRowsCache] so a gated
    // fetch (TTL still fresh) can still splice the last-known rows into the
    // ordered section list — the splice needs values on EVERY main fetch,
    // unlike the fixed grid which simply skips its state write.
    private val customDiscoverCache = TtlCacheGate(HomeFreshness.DISCOVER_TTL_MS)
    /**
     * Plain var on purpose, same rule as [lastFetchRacedPendingSync]:
     * [fetchCustomSeerrRows] runs only inside [fetchOnce]'s mutex-serialized
     * body on [scope]'s main-confined dispatcher — a single writer with no
     * suspension between the memo write and the return read.
     */
    private var customSeerrRowsCache: List<HomeSection> = emptyList()
    /**
     * Dice rolls that landed while a full refresh was ALREADY in flight:
     * row id → rolled items. That fetch captured the row's pre-roll payloads
     * before [rollDiscoverRow] seeded the network cache, so its sections
     * write would transiently revert the on-screen roll; the write re-applies
     * these entries instead (see [applyPendingRolledRows]) and clears them —
     * later fetches serve the seeded cache and need no guard.
     *
     * Plain map on purpose: written by [rollDiscoverRow]'s job and consumed
     * by [fetchOnce], both on [scope]'s main-confined dispatcher with no
     * suspension between the drain and the clear.
     */
    private val pendingRolledRows = LinkedHashMap<String, List<MediaItem>>()

    /**
     * In-flight dice-roll jobs (row id → job), so the identity transitions can
     * cancel them alongside [refreshJob]/[discoverJob]: a roll that raced a
     * sign-out or user-switch belongs to the PREVIOUS identity — letting it
     * land would patch the new identity's freshly painted sections, and its
     * [pendingRolledRows] entry would make the next fetch re-apply the
     * previous user's rolled items. Removed by each roll's finally (including
     * a cancelled one, via [NonCancellable] clearing through the flag reset).
     */
    private val rollJobs = LinkedHashMap<String, Job>()
    private var lastContinueWatchingIds: Set<String> = emptySet()
    /**
     * Set when a fetch painted sections while the outbox drain was still
     * pending (the going-online handshake's drain-wait timed out). The
     * drain's completion echo — the sync-complete signal — must then bypass
     * the [HomeFreshness.USER_DATA_REFRESH_MIN_INTERVAL_MS] throttle: the
     * throttle exists to drop redundant echoes of this device's own playback
     * saves, but this echo is the ONLY prompt notice that the just-painted
     * snapshot is pre-sync and must be refetched.
     *
     * A plain var on purpose: every reader and writer runs on [scope]'s
     * main-confined dispatcher (the offline-mode and user-data collectors,
     * [start], [flushPendingUserDataChange]) — there is no cross-thread
     * access.
     *
     * Cleared only when a post-sync fetch runs to completion — the
     * handshake's [cappedForcedFetch] or the flush job's fetch in
     * [flushPendingUserDataChange]; a cancelled fetch leaves it armed so the
     * next echo retries. Reset by the identity transitions and
     * [dropOnlineContent], which stop showing the sections that raced — a
     * stale flag must not hand the next identity's echoes a throttle bypass
     * they did not earn (the handshake checks its [identityEpoch] capture
     * before arming, so a transition landing mid-handshake cannot re-arm).
     */
    private var lastFetchRacedPendingSync = false

    /**
     * Bumped by every identity transition ([RefreshTrigger.SignedOut],
     * [refreshForUserSwitch]). The going-online handshake captures it before
     * its drain wait and re-arms [lastFetchRacedPendingSync] only if it is
     * unchanged: a transition that lands while the handshake is parked on
     * the drain resets the flag, and the handshake's fetch — now painting
     * for the new identity — must not re-arm it.
     */
    private var identityEpoch = 0

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
                lastRefreshTime = clock.nowEpochMillis()
                return
            }

            lastRefreshTime = clock.nowEpochMillis()
            val sectionPrefs = sectionPrefsProvider()

            // Stale-while-revalidate: on a cold open (sections still empty),
            // paint the persisted snapshot from Room instantly so the home
            // screen renders before the network refresh below resolves. The
            // fresh fetch overwrites it on success; if the network fails we
            // keep showing stale rather than an empty screen. Only when
            // empty — a pull-to-refresh or pref change already has sections
            // on screen and flashing stale would feel worse than the brief
            // spinner.
            if (_state.value.sections.isEmpty()) {
                orderedCachedSections(sectionPrefs)?.let { cachedSections ->
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
                    mediaRepository.getHomeSections(sectionPrefs.query, force = force)
                }
                val discoverDeferred = if (discoverEnabledProvider()) {
                    async { runCatchingRethrowingCancellation { fetchDiscoverSections(seerrPreferencesProvider()) } }
                } else null
                // Custom Seerr discover rows — fetched whenever the DISCOVER
                // section is enabled and Seerr is connected (independent of
                // the legacy discover-grid toggle), spliced into the ordered
                // section list at the DISCOVER block position below.
                val customSeerrPrefs = seerrPreferencesProvider()
                val customSeerrDeferred = if (
                    customSeerrPrefs.enabled &&
                    HomeSectionType.DISCOVER in sectionPrefs.query.enabledSections &&
                    sectionPrefs.query.discoverRows.any { it.enabled && it.source == DiscoverRowSource.SEERR }
                ) {
                    async { runCatchingRethrowingCancellation { fetchCustomSeerrRows(customSeerrPrefs) } }
                } else null
                // Direct *arr "Recently Grabbed" calendar — gated by the
                // DIRECT_ARR_INTEGRATION flag and the same TTL gate as
                // discover sections so it never adds extra round-trips on
                // every refresh.
                val arrDeferred = if (directArrEnabledProvider()) {
                    async { runCatchingRethrowingCancellation { fetchRecentlyGrabbed() } }
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
                            order = sectionPrefs.homeSectionOrder,
                            mergeContinueWatchingAndNextUp = sectionPrefs.mergeContinueWatchingAndNextUp,
                        )

                        // Splice the custom Seerr rows into the DISCOVER block
                        // (row-config order across BOTH sources is the single
                        // ordering authority — see spliceDiscoverSeerrRows).
                        val splicedSections = spliceDiscoverSeerrRows(
                            sections = finalSections,
                            seerrRows = customSeerrDeferred?.await()?.getOrNull().orEmpty(),
                            prefs = sectionPrefs,
                        )

                        // Continue Reading progress bars: books carry no
                        // runTimeTicks, so the video fraction math cannot serve
                        // the row — the shared TOC-cache decode (also the
                        // offline gate's) resolves exact page fractions where
                        // the cache knows the count, percent fallback otherwise.
                        // Local Room reads, ≤20 rows, best-effort per item.
                        // Decoded from the section that is about to be written
                        // and BEFORE the sections write so sections and
                        // fractions land as ONE emission — a two-step write
                        // painted paged-book cards on the percent fallback
                        // until the second update arrived. It also runs BEFORE
                        // draining [pendingRolledRows] on purpose: the decode
                        // suspends, and a roll landing mid-decode would
                        // register into a map this fetch had already drained —
                        // its state patch would then be reverted by this
                        // write. Decoding from the pre-roll spliced sections
                        // is still the right map: the decode reads only the
                        // CONTINUE_READING section, and discover rolls never
                        // touch it.
                        val bookFractions = decodeBookProgressFractionsFor(splicedSections)

                        // A dice roll that landed while THIS fetch was in
                        // flight must survive its sections write — re-apply
                        // the rolled items over the pre-roll payloads the
                        // fetch captured (see [pendingRolledRows]). No
                        // suspension between this drain and the write below.
                        val rolledSections = applyPendingRolledRows(splicedSections)
                        _state.update { it.copy(sections = rolledSections, bookProgressFractions = bookFractions) }

                        val continueWatching = rolledSections
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
                        runCatchingRethrowingCancellation { librarySyncHook.onLibraryScanComplete() }
                    }
                    .onFailure { throwable ->
                        // Always record the failure — stale sections stay on
                        // screen, but `fetchFailed` (error != null) is what
                        // opens the implicit-offline gate downstream, so a
                        // server that can't be reached while content is cached
                        // still swaps Continue Watching / Next Up to the
                        // locally derived rows instead of freezing the
                        // pre-offline server snapshot.
                        _state.update { s -> s.copy(error = UserErrorMessages.resolve(throwable, "${throwable::class.simpleName}")) }
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
            // (The going-online busy flag is not the fetch's to clear —
            // [OfflineModeManager] owns it and clears it at the ONLINE
            // emission, before this handshake's fetch even starts.)
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
     *  * [GoingOnline]: user-initiated offline→online transition — toggles
     *    manual offline (arming [OfflineModeManager.goingOnline]); the
     *    drain+fetch handshake itself runs in [observeOfflineMode] when the
     *    ONLINE emission lands.
     */
    fun request(trigger: RefreshTrigger) {
        when (trigger) {
            RefreshTrigger.Manual -> startForcedRefresh {
                _state.update {
                    it.withContentDropped(error = null, isLoading = true)
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
                userDataRefreshJob?.cancel()
                discoverJob?.cancel()
                cancelRollsForIdentityChange()
                // The raced-sync bypass described sections this reset just
                // dropped — it must not outlive the identity that raced.
                lastFetchRacedPendingSync = false
                identityEpoch++
                _state.update {
                    it.withContentDropped(error = null, isLoading = true)
                }
            }
            RefreshTrigger.DiscoverEnabled -> fetchDiscover()
            RefreshTrigger.GoingOnline -> {
                // Going online is async (preference write → mode flip →
                // drain + fetch) and previously gave zero feedback. The busy
                // flag's whole lifecycle lives in the manager now: the
                // toggle below arms it (direction: going online), and the
                // mode flow's ONLINE emission — or the manager's own
                // watchdog, if the preference write is lost — clears it.
                // External/auto flips never raise it. This branch only
                // toggles; [observeOfflineMode]'s reconnect handshake
                // (loader, drain, capped fetch) runs when the ONLINE
                // emission lands.
                offlineModeManager.toggleManualOffline()
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
        // Same for the user-data deferral timer — a pending flag armed for the
        // previous identity stays armed for the next start flush.
        userDataRefreshJob?.cancel()
        // The raced-sync bypass is void here too: the snapshot painted below
        // belongs to the incoming identity, not the sections that raced the
        // drain.
        lastFetchRacedPendingSync = false
        identityEpoch++
        // An in-flight standalone discover fetch belongs to the previous
        // identity; letting it land would repopulate the just-cleared
        // discoverSections with the previous user's rows.
        discoverJob?.cancel()
        cancelRollsForIdentityChange()
        val sectionPrefs = sectionPrefsProvider()
        val cachedSections = orderedCachedSections(sectionPrefs)
        // Same single-emission pairing as the fetch write: the SWR paint and
        // its fractions land together, so the painted CR row never flashes
        // the percent fallback ahead of the revalidating fetch.
        val cachedFractions = if (cachedSections != null) decodeBookProgressFractionsFor(cachedSections) else emptyMap()
        _state.update {
            if (cachedSections != null) {
                it.withContentDropped(
                    sections = cachedSections,
                    bookProgressFractions = cachedFractions,
                    error = null,
                    isLoading = false,
                )
            } else {
                it.withContentDropped(error = null, isLoading = true)
            }
        }
        fetchOnce()
        startPeriodicRefresh()
    }

    /**
     * Online→offline transition: drop the cached ONLINE sections + discover
     * rows + the *arr "Coming Soon" row (the offline home renders the offline
     * library instead). The *arr row rides the same drop as discover — its
     * cards route to Seerr/TMDB, unreachable offline, so a stale row would be
     * dead weight. Touches neither error nor spinners — mirrors the previous
     * inline clear, which also cleared the going-online spinner separately.
     */
    private fun dropOnlineContent() {
        // The raced-sync bypass is void: the pre-sync sections it described
        // are exactly the ones dropped here.
        lastFetchRacedPendingSync = false
        _state.update { it.withContentDropped(dropRecentlyGrabbed = true) }
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
     * The going-online handshake's fetch: FORCED (the drain just changed
     * server state — a non-forced fetch could serve the 60-second
     * homeSectionsCache entry captured BEFORE the drain, re-showing an
     * episode just marked watched/unwatched offline; force bypasses both the
     * repo cache and the network-layer latest/similar caches) and CAPPED
     * ([GOING_ONLINE_TIMEOUT_MS] — a hung call must not park the handshake's
     * full-screen loader; on timeout we drop the result and the loader's
     * finally clears it). Returns false when the cap dropped the fetch; a
     * normal refresh / pull-to-refresh can still repopulate sections once
     * the network recovers.
     */
    private suspend fun cappedForcedFetch(): Boolean =
        withTimeoutOrNull(GOING_ONLINE_TIMEOUT_MS) { fetchOnce(force = true) } != null

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
            val now = clock.nowEpochMillis()
            if (now - lastRefreshTime >= HomeFreshness.REFRESH_INTERVAL_FOREGROUND_MS) {
                fetchOnce()
            }
            periodicRefreshLoop()
        }
        // A user-data change that arrived while backgrounded refreshes now —
        // bypassing the 60s stale check above, and bypassing the user-data
        // throttle too: the pending change predates the return to the screen
        // and the user is looking at it now. The pending flag is NOT cleared
        // before the fetch lands, so a stop() mid-fetch re-arms the flush for
        // the next onStart. Runs AFTER startPeriodicRefresh so the deferred
        // refresh's job (forced fetch + loop) replaces the bare periodic loop
        // instead of being cancelled by it.
        if (pendingUserDataRefresh) {
            flushPendingUserDataChange()
        }
    }

    /**
     * onStop: cancel the refresh job (forced fetch and/or loop), the
     * user-data deferral timer, and the standalone discover fetch, and drop
     * them. The identity-transition job is deliberately NOT cancelled here —
     * the sign-in fetch must survive an immediate backgrounding (see
     * [refreshForUserSwitch]); it dies with the scope / is replaced by the
     * next transition instead.
     */
    fun stop() {
        isAppInForeground = false
        refreshJob?.cancel()
        refreshJob = null
        userDataRefreshJob?.cancel()
        userDataRefreshJob = null
        discoverJob?.cancel()
        discoverJob = null
        // stop() cancels with NO replacement fetch, so the cancelled fetch's
        // guarded finally will not clear the flags — clear them here so a
        // backgrounded cold start doesn't sit on a stuck loader.
        _state.update { it.copy(isLoading = false, isRefreshing = false) }
    }

    /**
     * Resets the discover-sections TTL so the next [fetchDiscoverSections]
     * actually hits the network. Called on user-initiated refresh — the custom
     * Seerr rows share the treatment (pull-to-refresh re-rolls them too).
     */
    private fun invalidateDiscoverCache() {
        discoverCache.invalidate()
        customDiscoverCache.invalidate()
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
     * The dice affordance for one RANDOM-sorted Jellyfin discover row: drops
     * the cached payloads that still carry the row's pre-roll items, re-fetches
     * fresh from the server, commits the rolled set back into the network
     * layer's per-row cache (so the next periodic refresh serves it instead of
     * reverting or re-rolling) and patches the row's items in place — no full
     * refresh, no spinner, sibling rows untouched. Failures degrade silently
     * (row keeps its current items; the rolling flag still clears). One
     * in-flight roll per row — a tap while that row's dice is already
     * animating ([HomeRefreshState.rollingDiscoverRowIds]) is ignored.
     */
    fun rollDiscoverRow(row: DiscoverRowConfig, onResult: (Boolean) -> Unit = {}) {
        // Check-and-set inside _state.update's CAS loop so a concurrent state
        // writer can't slip a second roll for the same row between the check
        // and the set (double fetch + a patch racing its own finally-clear).
        var accepted = false
        _state.update { state ->
            if (row.id in state.rollingDiscoverRowIds) {
                state
            } else {
                accepted = true
                state.copy(rollingDiscoverRowIds = state.rollingDiscoverRowIds + row.id)
            }
        }
        if (!accepted) return
        val rollStartedAt = TimeSource.Monotonic.markNow()
        Log.d(TAG, "roll ${row.id}: accepted, flag up")
        rollJobs[row.id] = scope.launch {
            // The roll's outcome for the caller: true when the row's items
            // were swapped, false when the fetch failed/returned nothing
            // (the row keeps its current items — reported, not silent).
            // Declared outside try so the finally's diagnostic log can read it.
            var rolled = false
            try {
                runCatchingRethrowingCancellation {
                    // Invalidate FIRST: the network row memo AND the repo's
                    // assembled home payload both still hold the pre-roll items —
                    // the latter would replay them on the next TTL-served
                    // periodic read and revert the on-screen roll.
                    mediaRepository.invalidateDiscoverRowCache(row.id)
                    val result = mediaRepository.getDiscoverRowItems(row)
                    val items = result.getOrNull().orEmpty()
                    if (items.isEmpty()) {
                        // A silent skip here reads as a dead button on the
                        // screen — log the cause so the failure is diagnosable.
                        result.exceptionOrNull()?.let { e ->
                            Log.w(TAG, "Discover row roll failed for ${row.id}: ${e.message}", e)
                        }
                        return@runCatchingRethrowingCancellation
                    }
                    rolled = true
                    // Commit the rolled set where the next home fetch reads it,
                    // so the periodic refresh replays THIS roll rather than
                    // re-querying the server (yet another reshuffle).
                    mediaRepository.seedDiscoverRowCache(row, items)
                    // Register BEFORE the state patch below: a fetch that is
                    // already in flight captured the pre-roll payloads and will
                    // land its own sections write after this one — the entry
                    // makes that write re-apply the rolled items (see
                    // [applyPendingRolledRows]) instead of reverting the roll.
                    pendingRolledRows[row.id] = items
                    Log.d(
                        TAG,
                        "roll ${row.id}: patched ${items.size} items, first=${items.firstOrNull()?.id} " +
                            "(on-screen first=${_state.value.sections
                                .firstOrNull { it.id == HomeSectionType.DISCOVER.descriptor.idFor(row.id) }
                                ?.items?.firstOrNull()?.id})",
                    )
                    val rowSectionId = HomeSectionType.DISCOVER.descriptor.idFor(row.id)
                    _state.update { s ->
                        s.copy(
                            sections = s.sections.map { section ->
                                if (section.id == rowSectionId && section.type == HomeSectionType.DISCOVER) {
                                    section.copy(items = items)
                                } else section
                            },
                        )
                    }
                }
                onResult(rolled)
            } finally {
                // Keep the flag up for a minimum window: a fast local server
                // answers in tens of milliseconds, and a spin that flashes for
                // one frame reads as no feedback at all (the on-device report
                // behind the dice feature). NonCancellable so a cancelled roll
                // (stop()/user switch) still clears the flag — a stuck flag
                // would disable the dice forever.
                withContext(NonCancellable) {
                    val remainingMs = ROLL_MIN_SPIN_MS - rollStartedAt.elapsedNow().inWholeMilliseconds
                    Log.d(TAG, "roll ${row.id}: done in ${ROLL_MIN_SPIN_MS - remainingMs}ms, rolled=$rolled, holding flag ${remainingMs}ms more")
                    if (remainingMs > 0) delay(remainingMs)
                }
                Log.d(TAG, "roll ${row.id}: flag cleared")
                rollJobs.remove(row.id)
                _state.update { it.copy(rollingDiscoverRowIds = it.rollingDiscoverRowIds - row.id) }
            }
        }
    }

    /**
     * Identity-transition drain of the dice-roll machinery (see [rollJobs]):
     * cancel the in-flight rolls — their finally clears the rolling flags via
     * [NonCancellable] — and drop the pending-rolls entries so the incoming
     * identity's first fetch doesn't re-apply the previous user's rolled
     * items. The network-layer caches they seeded are cleared wholesale by
     * the identity transition itself.
     */
    private fun cancelRollsForIdentityChange() {
        rollJobs.values.forEach { it.cancel() }
        rollJobs.clear()
        pendingRolledRows.clear()
    }

    /**
     * Re-applies [pendingRolledRows] over the fetch's sections and drains the
     * map — the fetch's own last word on sections, so a roll that completed
     * mid-fetch keeps its freshly-rolled items on screen. Entries whose row
     * the fetch doesn't carry (disabled / absent) are dropped with the drain:
     * the roll had no section to patch either. No suspension between the
     * drain and the clear, so a roll landing on the other side of this call
     * registers cleanly into the empty map.
     */
    private fun applyPendingRolledRows(sections: List<HomeSection>): List<HomeSection> {
        if (pendingRolledRows.isEmpty()) return sections
        val rolledBySectionId = pendingRolledRows.mapKeys { (rowId, _) ->
            HomeSectionType.DISCOVER.descriptor.idFor(rowId)
        }
        pendingRolledRows.clear()
        return sections.map { section ->
            if (section.type != HomeSectionType.DISCOVER) {
                section
            } else {
                rolledBySectionId[section.id]?.let { section.copy(items = it) } ?: section
            }
        }
    }

    /**
     * Fetches the enabled SEERR-sourced custom discover rows (TTL-gated,
     * last-known-good via [customSeerrRowsCache] — see its KDoc). Failing
     * rows are dropped (pin policy); an all-out Seerr failure keeps the
     * previous rows rather than clearing them.
     */
    private suspend fun fetchCustomSeerrRows(prefs: SeerrPreferences): List<HomeSection> {
        if (!prefs.enabled) return emptyList()
        if (offlineModeManager.networkStatus.value == NetworkStatus.Local) return emptyList()
        val rows = sectionPrefsProvider().query.discoverRows
            .filter { it.enabled && it.source == DiscoverRowSource.SEERR }
        if (rows.isEmpty()) {
            customSeerrRowsCache = emptyList()
            return emptyList()
        }
        if (customDiscoverCache.shouldFetch(clock.nowEpochMillis())) {
            val today = clock.today().toString()
            // coroutineScope, not the VM scope: the fan-out must die with the
            // calling refresh job (same contract as fetchDiscoverSections).
            // R is explicitly nullable (an empty/failed row yields null) —
            // mapConcurrentCatching drops those.
            val fetched: List<HomeSection?> = coroutineScope {
                Semaphore(3).mapConcurrentCatching(rows) { row ->
                    fetchSeerrDiscoverRow(row, today)
                }
            }
            customSeerrRowsCache = fetched.filterNotNull()
            customDiscoverCache.markFetched(clock.nowEpochMillis())
        }
        return customSeerrRowsCache
    }

    /** One Seerr row: builds the discover query from the row's filters, maps to a request-capable section (or null when empty/failed). */
    private suspend fun fetchSeerrDiscoverRow(row: DiscoverRowConfig, today: String): HomeSection? {
        val filters = row.seerrFilters
        val params = filters.toSeerrDiscoverParams(today)
        val response = when (filters.media) {
            SeerrRowMedia.MOVIE -> seerrRepository.getDiscoverMovies(params = params)
            SeerrRowMedia.TV -> seerrRepository.getDiscoverTv(params = params)
        }.getOrNull() ?: return null
        val items = response.results.take(row.limit)
        if (items.isEmpty()) return null
        return HomeSection(
            id = HomeSectionType.DISCOVER.descriptor.idFor(row.id),
            title = row.title,
            type = HomeSectionType.DISCOVER,
            items = emptyList(),
            seerrItems = items,
        )
    }

    /**
     * The offline-mode mirror + transition policy, inlined from the VM's own
     * collector so every offline-shaped field of [HomeRefreshState] has one
     * writer. Reacts to ALL [OfflineModeManager.offlineMode] emissions
     * (app-start and external/auto changes included):
     *  * ONLINE → offline (any flavour): drop the cached online sections —
     *    the offline home renders the offline library instead. (The
     *    going-online busy flag is not this observer's to clear:
     *    [OfflineModeManager.goingOnline] cleared it at the ONLINE emission
     *    that preceded the flip, so there is nothing left to drop here even
     *    if the prior online fetch is still parked on the refresh mutex.)
     *  * offline → ONLINE (any flavour): run the reconnect handshake —
     *    full-screen loader, outbox drain, capped fetch. The user-initiated
     *    [RefreshTrigger.GoingOnline] path raised the manager-owned busy
     *    flag on its toggle, and the manager cleared it at this very ONLINE
     *    emission; external flips (nav ⋮ toggle from the app shell) and
     *    auto-detect reconnects used to mirror the field only, which left
     *    the home sitting on dropped-empty sections until a manual refresh
     *    or the next periodic tick.
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
                    }
                    previousMode != OfflineMode.ONLINE && mode == OfflineMode.ONLINE -> {
                        // Offline → online: show the full-screen loader
                        // during the post-toggle fetch so the online branch
                        // doesn't flash blank between the mode flip and
                        // sections arriving. The loader MUST clear in
                        // finally — a bare after-the-fetch clear would leave
                        // it stuck on forever (and the user restarting the
                        // app to recover) whenever the handshake throws or is
                        // cancelled. (The busy flag needs no such rescue:
                        // the manager already cleared it at this emission.)
                        val handshakeEpoch = identityEpoch
                        showFullScreenLoader()
                        try {
                            // Let the playback outbox drain before fetching so
                            // Continue Watching / Next Up reflect the server's
                            // post-sync state. Without this the fetch can race
                            // the drain: CW would still list an episode the user
                            // marked unplayed offline, because the server hasn't
                            // processed the mark yet. The drain is fast on
                            // reconnect; on timeout we fetch anyway and the
                            // sync-complete path below re-syncs.
                            val drained = awaitOutboxDrained()
                            // The fetch raced a pending sync: whatever it
                            // paints is the PRE-sync server snapshot. Arm the
                            // throttle bypass so the drain's completion echo
                            // can refresh (see [lastFetchRacedPendingSync])
                            // even though this fetch just stamped the clock —
                            // unless an identity transition landed while we
                            // were parked on the drain: it reset the flag for
                            // the sections it dropped, and this fetch now
                            // paints for the new identity, which raced
                            // nothing.
                            lastFetchRacedPendingSync =
                                !drained && identityEpoch == handshakeEpoch
                            cappedForcedFetch()
                            if (!drained && awaitOutboxDrained()) {
                                // The sync landed while the loader was still
                                // up: refetch so the loader drops on post-sync
                                // content instead of the pre-sync snapshot the
                                // first fetch painted. The bypass flag stays
                                // armed until this refetch RUNS — a dropped
                                // refetch (cap timeout) leaves the painted
                                // sections pre-sync, so the drain's echo must
                                // keep its throttle bypass.
                                if (cappedForcedFetch()) {
                                    lastFetchRacedPendingSync = false
                                }
                            }
                            // On a very slow sync the re-await also times out;
                            // the still-armed bypass flag lets the drain's
                            // completion echo refresh silently when it lands.
                        } finally {
                            clearFullScreenLoader()
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
     * more than once a minute — UNLESS the last fetch raced a still-pending
     * outbox drain ([lastFetchRacedPendingSync]): the drain-completion echo is
     * then the sync-complete signal, the one notice that the painted snapshot
     * is pre-sync, and must refresh regardless of the clock. No spinner:
     * isRefreshing/isLoading stay untouched.
     *
     * A change that lands inside the throttle window is DEFERRED, not
     * dropped: it arms [pendingUserDataRefresh] and schedules the forced
     * fetch at throttle expiry (each further echo re-arms the timer on
     * [userDataRefreshJob] — trailing edge, so a playback session's ~10s
     * position saves collapse into one fetch after the last save). Dropping
     * instead used to strand the final echo of a session — the position save
     * or markPlayed that lands <60s after the last refresh, exactly the
     * state the user sees when they leave the player: Continue Watching /
     * Next Up stayed mid-playback stale until the periodic loop's non-forced
     * fetch worked through the cache TTLs, or a pull-to-refresh forced it.
     */
    private fun refreshAfterUserDataChange() {
        pendingUserDataRefresh = true
        val sinceLastRefresh = clock.nowEpochMillis() - lastRefreshTime
        if (!lastFetchRacedPendingSync &&
            sinceLastRefresh < HomeFreshness.USER_DATA_REFRESH_MIN_INTERVAL_MS
        ) {
            // Trailing-edge deferral on its own timer job (see
            // [userDataRefreshJob] for why it must not live in refreshJob).
            // The periodic loop keeps running underneath — deferral only
            // adds the guaranteed TTL-bypassing fetch at expiry.
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
     * The shared tail of [refreshAfterUserDataChange]: the armed pending
     * flag becomes an immediate forced fetch. Also called directly as the
     * [start] flush (the pending change predates the return to the screen,
     * the user is looking at it now, and the throttle's anti-spam rationale —
     * don't refetch behind the player for every position save — does not
     * apply) and as the [lastFetchRacedPendingSync] consumer (the
     * sync-complete echo must refresh regardless of the clock).
     *
     * Tracked in refreshJob so onStop cancels the forced fetch with it;
     * the periodic loop continues in the same job once the fetch lands
     * (a separate startPeriodicRefresh() call here would either be
     * cancelled by its own refreshJob hand-off or restart the loop while
     * backgrounded). The pending flag stays armed until the fetch lands:
     * if onStop cancels mid-fetch the change isn't lost — the next
     * onStart retries it. The raced-sync bypass is cleared by the same
     * land-only rule (matching the handshake's refetch): a cancellation
     * mid-fetch leaves it armed so the next echo retries.
     */
    private fun flushPendingUserDataChange() {
        // Cancelling the timer from inside its own fired body is harmless:
        // nothing suspends in this function after that point, and the refresh
        // job below is launched on the scope, not as the timer's child.
        userDataRefreshJob?.cancel()
        userDataRefreshJob = null
        replaceRefreshJob {
            fetchOnce(force = true)
            lastFetchRacedPendingSync = false
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

            val now = clock.nowEpochMillis()
            if (now - lastRefreshTime < HomeFreshness.MIN_REFRESH_INTERVAL_MS) continue

            // fetchOnce owns the single lastRefreshTime clock and stamps it on
            // every exit path (including the offline skip), so no re-stamp here.
            fetchOnce()
        }
    }

    /**
     * Reads the persisted SWR snapshot for [sectionPrefs] and orders it
     * for display, or null if nothing is cached / the cached sections are
     * empty. Shared by the user-switch paint ([refreshForUserSwitch]) and
     * the cold-open path in [fetchOnce].
     */
    private suspend fun orderedCachedSections(sectionPrefs: HomeSectionPrefs): List<HomeSection>? =
        runCatchingRethrowingCancellation { mediaRepository.getCachedHomeSections(sectionPrefs.query) }
            .getOrNull()
            ?.takeIf { it.sections.isNotEmpty() }
            ?.let { cached ->
                orderHomeSections(
                    sections = cached.sections,
                    order = sectionPrefs.homeSectionOrder,
                    mergeContinueWatchingAndNextUp = sectionPrefs.mergeContinueWatchingAndNextUp,
                )
            }

    /**
     * Decodes the Continue Reading progress fractions for the sections about
     * to be painted — the shared step of the fetch write and the user-switch
     * SWR paint, so a painted CR row renders exact fractions immediately
     * instead of the percent fallback until the fetch's own emission lands.
     */
    private suspend fun decodeBookProgressFractionsFor(sections: List<HomeSection>): Map<String, Float> {
        val continueReading = sections.find { it.type == HomeSectionType.CONTINUE_READING }
            ?: return emptyMap()
        return bookTocCacheRepository.decodeBookProgressFractions(continueReading.items)
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
        val now = clock.today()
        val end = now.plus(30, DateTimeUnit.DAY)
        // ArrRepository takes kotlinx.datetime.LocalDate — the refresher's
        // HomeClock seam now speaks kotlinx LocalDate natively.
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
        val now = clock.nowEpochMillis()
        if (!discoverCache.shouldFetch(now)) return

        val today = clock.today().toString()

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

        discoverCache.markFetched(clock.nowEpochMillis())
        _state.update { it.copy(discoverSections = newSections) }
    }
}

/**
 * Merges the feature-layer Seerr discover rows into the ordered section list
 * at the DISCOVER block position. The user's row-config order (list position
 * in [HomeSectionQuery.discoverRows]) is the single ordering authority across
 * BOTH sources: the block is rebuilt as (Jellyfin rows fetched by the network
 * layer + these Seerr rows) sorted by row-config index, then re-inserted
 * where the block sits (or where the section order says it belongs when no
 * Jellyfin row rendered — e.g. Jellyfin-only rows disabled or empty).
 *
 * A disabled DISCOVER section type returns [sections] untouched (the caller
 * never fetches Seerr rows then, but the guard keeps the helper total).
 * Pure — unit-testable without the refresher.
 */
internal fun spliceDiscoverSeerrRows(
    sections: List<HomeSection>,
    seerrRows: List<HomeSection>,
    prefs: HomeSectionPrefs,
): List<HomeSection> {
    if (seerrRows.isEmpty()) return sections
    if (HomeSectionType.DISCOVER !in prefs.query.enabledSections) return sections
    val typeOrderIndex = prefs.homeSectionOrder.withIndex().associate { (index, type) -> type to index }
    val discoverOrderIndex = typeOrderIndex[HomeSectionType.DISCOVER] ?: Int.MAX_VALUE
    val rowConfigIndex = prefs.query.discoverRows.withIndex().associate { (index, row) ->
        HomeSectionType.DISCOVER.descriptor.idFor(row.id) to index
    }
    fun rowOrderKey(section: HomeSection): Int = rowConfigIndex[section.id] ?: Int.MAX_VALUE

    val existingDiscover = sections.filter { it.type == HomeSectionType.DISCOVER }
    val mergedBlock = (existingDiscover + seerrRows)
        .mapIndexed { stableIndex, section -> stableIndex to section }
        .sortedWith(compareBy({ rowOrderKey(it.second) }, { it.first }))
        .map { it.second }

    val blockStart = sections.indexOfFirst { it.type == HomeSectionType.DISCOVER }
        .takeIf { it >= 0 }
        // No rendered Jellyfin discover row: insert ahead of the first section
        // that sorts AFTER the DISCOVER type position (unknown types — e.g.
        // pinned — count as +∞ and stay last).
        ?: sections.indexOfFirst { section ->
            (typeOrderIndex[section.type] ?: Int.MAX_VALUE) > discoverOrderIndex
        }
        .takeIf { it >= 0 }
        ?: sections.size

    return buildList {
        addAll(sections.subList(0, blockStart))
        addAll(mergedBlock)
        addAll(sections.subList(blockStart, sections.size).filter { it.type != HomeSectionType.DISCOVER })
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
     * toggles manual offline (arming [OfflineModeManager.goingOnline]);
     * the drain+fetch handshake runs in the offline-mode observer when the
     * ONLINE emission lands.
     */
    GoingOnline,
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
    /**
     * Continue Reading progress bars, keyed by item id — decoded from the
     * book ticks encodings (see [com.raulshma.jellyplay.core.model.bookProgressFraction])
     * with exact page fractions where the TOC cache knows the page count.
     * Cleared wherever the sections that own the ids are cleared; a missing id
     * renders the percent fallback in the card.
     */
    val bookProgressFractions: Map<String, Float> = emptyMap(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    /** Non-blocking notice shown when some (not all) home sections failed to load. */
    val partialLoadError: Boolean = false,
    val discoverSections: Map<DiscoverSectionType, List<SeerrSearchItem>> = emptyMap(),
    /** Direct *arr "Recently Grabbed / Coming Soon" calendar row. */
    val recentlyGrabbed: List<SeerrSearchItem> = emptyList(),
    /**
     * Custom discover rows with a dice roll in flight (row ids) — drives the
     * dice icon's tumbling animation and the one-roll-per-row tap guard.
     * Cleared on completion AND failure (the finally in [rollDiscoverRow]).
     */
    val rollingDiscoverRowIds: Set<String> = emptySet(),
    /** Mirror of [OfflineModeManager.offlineMode]; transitions drive the policy in [HomeRefresher.observeOfflineMode]. */
    val offlineMode: OfflineMode = OfflineMode.ONLINE,
) {
    /**
     * The server fetch failed — the precondition for the implicit-offline
     * fallback (online mode + downloads present). Sections on screen do NOT
     * disqualify the fallback: they are the stale pre-failure snapshot, and
     * keeping them rendered would freeze Continue Watching / Next Up at
     * server data that can no longer refresh. The offline collection gate in
     * [com.raulshma.jellyplay.feature.home.HomeViewModel] keys on this, and
     * the same gate folds it into
     * [com.raulshma.jellyplay.feature.home.HomeUiState.renderSource] via
     * [com.raulshma.jellyplay.feature.home.computeHomeRenderSource] — which
     * only keeps the online feed when downloads are confirmed absent (the
     * hard-error screen then owns the empty-sections corner via
     * [com.raulshma.jellyplay.feature.home.homeSurface]).
     */
    val fetchFailed: Boolean
        get() = error != null
}

/**
 * The shared content-drop fold: the sections, the discover rows, and the
 * Continue Reading fractions clear TOGETHER — the fractions key into the
 * dropped sections' item ids, so a site clearing the sections can never
 * leave them behind. Call sites state only what differs: the painted
 * sections (empty on a clear, the SWR snapshot on user switch) and their
 * decoded fractions (empty by default — cleared with the sections; the SWR
 * repaint paints its own), the error/spinner resets, and whether the *arr
 * row rides the drop.
 */
private fun HomeRefreshState.withContentDropped(
    sections: List<HomeSection> = emptyList(),
    bookProgressFractions: Map<String, Float> = emptyMap(),
    error: String? = this.error,
    isLoading: Boolean = this.isLoading,
    dropRecentlyGrabbed: Boolean = false,
): HomeRefreshState = copy(
    sections = sections,
    discoverSections = emptyMap(),
    bookProgressFractions = bookProgressFractions,
    error = error,
    isLoading = isLoading,
    recentlyGrabbed = if (dropRecentlyGrabbed) emptyList() else this.recentlyGrabbed,
)
