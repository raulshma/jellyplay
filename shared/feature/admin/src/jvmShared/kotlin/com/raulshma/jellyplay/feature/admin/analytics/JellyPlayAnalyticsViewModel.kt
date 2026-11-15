package com.raulshma.jellyplay.feature.admin.analytics

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.concurrency.TaskBundle
import com.raulshma.jellyplay.core.data.log.Log
import com.raulshma.jellyplay.core.data.session.JellyPlayCapabilityGate
import com.raulshma.jellyplay.core.data.session.JellyPlayCapabilityState
import com.raulshma.jellyplay.core.data.session.JellyPlayFeatureGate
import com.raulshma.jellyplay.core.data.session.JellyPlayPluginStatusStore
import com.raulshma.jellyplay.core.model.JellyPlayPluginFeatures
import com.raulshma.jellyplay.core.model.PlaybackActivityPoint
import com.raulshma.jellyplay.core.network.api.JellyPlayAnalyticsOverview
import com.raulshma.jellyplay.core.network.api.JellyPlayAnalyticsSession
import com.raulshma.jellyplay.core.network.api.JellyPlayAnalyticsRoutes
import com.raulshma.jellyplay.core.ui.components.formatDurationFromMinutes
import com.raulshma.jellyplay.core.ui.components.formatDurationFromTicks
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.core.ui.viewmodel.loadInto
import com.raulshma.jellyplay.feature.admin.transcodes.TranscodePlayMethod
import com.raulshma.jellyplay.feature.admin.transcodes.formatBitrate
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * The admin analytics dashboard's gate — plugin AVAILABLE **and** the
 * `analytics` feature key present **and** the user's per-feature toggle on
 * (the shared [JellyPlayCapabilityGate] verdict — the transcodes monitor's
 * gate shape, one home in the plugin capability gate module; the capability
 * registry is the ONLY gating mechanism, the toggle rides
 * [JellyPlayFeatureGate], the ONE per-feature seam). Admin-ness is enforced
 * upstream by the admin area's AdminRouteContainer — the existing admin gate.
 */

/** The window roll-up the totals row renders (durations pre-humanized). */
@Immutable
data class AnalyticsTotalsUi(
    val plays: Long,
    /** Humanized [com.raulshma.jellyplay.core.network.api.JellyPlayAnalyticsTotals.playSeconds] ("12h 34m"). */
    val watchTimeLabel: String,
    /** Humanized transcodeSeconds ("1h 5m"). */
    val transcodeTimeLabel: String,
    val uniqueUsers: Int,
    val uniqueItems: Int,
)

/** The overview as the screen renders it: chart points plus ranked rows. */
@Immutable
data class AnalyticsOverviewUi(
    val totals: AnalyticsTotalsUi,
    /** Plays-per-day chart points (the `yyyy-MM-dd` day + the play count). */
    val perDay: List<PlaybackActivityPoint>,
    val perUser: List<AnalyticsUserRow>,
    val topItems: List<AnalyticsTopItemRow>,
)

@Immutable
data class AnalyticsUserRow(
    val userId: String,
    val userName: String,
    val plays: Long,
    val playSeconds: Long,
    val transcodeSeconds: Long,
    /** Humanized [playSeconds] ("12h 34m"). */
    val watchTimeLabel: String,
    /** Humanized [transcodeSeconds]. */
    val transcodeTimeLabel: String,
)

@Immutable
data class AnalyticsTopItemRow(
    val itemId: String,
    val itemName: String,
    val itemType: String,
    val plays: Long,
    /** Humanized [com.raulshma.jellyplay.core.network.api.JellyPlayAnalyticsTopItem.playSeconds]. */
    val watchTimeLabel: String,
)

/** One recent play session as the sessions ledger renders it. */
@Immutable
data class AnalyticsSessionRow(
    val userId: String,
    val itemId: String,
    val itemName: String,
    val itemType: String,
    val seriesName: String?,
    val playMethod: TranscodePlayMethod,
    /** The raw `playMethod` wire string, for OTHER fallback rendering. */
    val playMethodRaw: String?,
    /** Humanized [JellyPlayAnalyticsSession.bitrate] (bps), or null. */
    val bitrateLabel: String?,
    /** Humanized watched position ("34m"), or null when the session recorded none. */
    val watchedLabel: String?,
    /** Named flag-bit strings the server decomposed its `[Flags]` reason word into. */
    val transcodeReasons: List<String>,
    val endedAt: Long,
    val clientName: String?,
    val deviceName: String?,
    /** The session's server-side start stamp — part of the row's stable identity. */
    val startedAt: Long = 0,
)

/**
 * The analytics dashboard's load phase — the sealed machine that replaced the
 * former `isLoading` / `isRefreshing` / `error` / `overview` / `overviewDegraded`
 * boolean-and-nullable soup (convention §1): those five fields could hold
 * combinations the screen never meant to render (`isLoading` beside a stale
 * `overview`, an `error` under fresh content) and every render site re-derived
 * which one won. One phase, one render decision.
 */
@Immutable
sealed interface AnalyticsLoadPhase {
    /**
     * The gate is unresolved ([JellyPlayCapabilityState.Unknown], pre-probe) or
     * the gate-on initial load is in flight — the full-screen spinner. While
     * the gate is OFF the screen renders the gate face and this phase is
     * unobserved (no load ever ran, so it stays here).
     */
    data object Loading : AnalyticsLoadPhase

    /**
     * A manual refresh burst is in flight — the pre-refresh content face
     * ([staleOverview] / [staleDegraded]) stays visible underneath (the
     * refresh-failure idiom: a failed refresh keeps the stale data). Raised
     * ONLY from [Loaded]; a retry from [Failed] keeps the error face until the
     * overview settles, matching the former behavior where `isRefreshing`
     * never displaced a shown error.
     */
    data class Refreshing(
        val staleOverview: AnalyticsOverviewUi?,
        val staleDegraded: Boolean,
    ) : AnalyticsLoadPhase

    /**
     * A load (or later silent overview fetch) settled: real content
     * ([overview]) or the quiet 404 degrade — the plugin predates the analytics
     * routes, [overview] null + [degraded] true, never an error banner.
     */
    data class Loaded(
        val overview: AnalyticsOverviewUi?,
        /** The overview route answered 404 (plugin predates analytics or the key is absent). */
        val degraded: Boolean,
    ) : AnalyticsLoadPhase

    /** The gate-on initial load failed — the only surfaced error; [refresh] retries it. */
    data class Failed(val message: String?) : AnalyticsLoadPhase
}

@Immutable
data class AnalyticsState(
    val gate: JellyPlayCapabilityState = JellyPlayCapabilityState.Unknown,
    val loadPhase: AnalyticsLoadPhase = AnalyticsLoadPhase.Loading,
    /** The selected window (the 7/30/90 chips; the overview fetch's `days`). */
    val days: Int = 30,
    val sessions: List<AnalyticsSessionRow> = emptyList(),
    /** The active per-user sessions filter (null = all users). */
    val selectedUserId: String? = null,
    /**
     * True while a sessions (re)fetch is in flight — the section's spinner.
     * Deliberately NOT part of [AnalyticsLoadPhase]: this and the two fields
     * below are the sessions LEDGER's second (pagination) machine —
     * in-flight facts over a list surface that outlives any single load,
     * where [isLoadingSessions] / [isLoadingMore] are the two
     * mutually-exclusive legs of one fetch, not a lifecycle.
     */
    val isLoadingSessions: Boolean = false,
    /** True while a Load-more page is in flight (the appending leg — see [isLoadingSessions]). */
    val isLoadingMore: Boolean = false,
    /** True when the last sessions page came back full — a Load more affordance exists (the declared "full page means more" inference). */
    val hasMoreSessions: Boolean = false,
)

/**
 * Backs the admin analytics dashboard (Route.JellyPlayAnalytics): the
 * companion plugin's play-history aggregates (overview over a 7/30/90-day
 * window + the recent-sessions ledger).
 *
 * Gating — admin && plugin AVAILABLE && `analytics` feature, the transcodes
 * monitor's exact seam: the admin half is the admin area's existing
 * AdminRouteContainer gate; the plugin half is the shared
 * [JellyPlayCapabilityGate] reading the store — no analytics
 * fetch is ever issued while the gate is off, so a stock server's plugin-route
 * 404 is never observed here. The nullable-with-default ctor deps mirror the
 * settings screen's plugin-seam pattern: direct construction (tests) may omit
 * them — a null store is simply gated off.
 *
 * Loading — unlike the transcodes monitor there is NO auto-refresh loop (the
 * aggregates move on day boundaries, not seconds); [start] re-arms the probe
 * when the screen becomes visible and [stop] is the visibility idiom's
 * symmetry half. The initial load rides the gate's Available transition — and
 * RE-ARMS when the gate drops (identity reset / plugin reinstall), so the
 * next gate-on reloads instead of showing the first visit's data forever —
 * and is the only fetch whose overview failure surfaces an error; sessions
 * failures and refresh/pagination failures are silent (the stale list stays —
 * the refresh-failure idiom). A 404 (null payload) degrades quietly:
 * [AnalyticsLoadPhase.Loaded]'s degraded payload, never an error.
 */
class JellyPlayAnalyticsViewModel(
    private val pluginApiClient: JellyPlayAnalyticsRoutes,
    private val statusStore: JellyPlayPluginStatusStore? = null,
    /**
     * The per-feature gate seam (probe AND the user's toggle) over the store
     * above. Nullable-with-default (the SettingsViewModel pattern); without
     * it the probe-only availability keeps the pre-toggle behavior.
     */
    private val featureGate: JellyPlayFeatureGate? = null,
) : JellyPlayViewModel() {

    private val _uiState = stateFlow(AnalyticsState())
    val uiState: StateFlow<AnalyticsState> = _uiState.flow

    /**
     * The shared capability gate (the plugin capability gate module) — the
     * probe-once-while-UNKNOWN / re-arm-on-identity-reset / gate-reads-the-
     * store discipline, the transcodes monitor's exact seam. Null with the
     * store: direct construction (tests)
     * without the plugin seam is gated off, final.
     */
    private val capabilityGate = statusStore?.let { JellyPlayCapabilityGate(it, scope) }

    /**
     * Whether the initial gated load has been kicked off for the CURRENT
     * gate-on stretch (exactly once per gate-on). The gate collector CLEARS
     * this whenever the gate leaves Available — an identity reset / plugin
     * reinstall re-arms the store's probe, the next gate-on is a fresh
     * stretch, and the dashboard must reload rather than show the first
     * visit's data forever.
     */
    private var initialLoadStarted = false

    /**
     * The keyed cancel-and-replace slot ([TaskBundle]) holding the in-flight
     * sessions fetch — replaces the former bare `private var sessionsJob:
     * Job?` pair. ONE slot on purpose: [refresh]'s session reset and a user
     * re-filter ([selectUser]) both occupy it, so the two writers SERIALIZE —
     * a refresh landing mid-flight is cancelled-and-replaced (or itself
     * replaces, capturing the then-current filter) and a late refresh write
     * can no longer land old-filter rows under the new chip. [stop] cancels
     * whatever occupies the slot.
     */
    private val sessionsTasks = TaskBundle(scope)

    /** The single [sessionsTasks] slot key — the in-flight sessions fetch. */
    private object SessionsFetchKey

    init {
        observeGate()
    }

    /**
     * Screen-visibility hook (the transcodes screen's LifecycleStartEffect
     * shape): the gate's arm — one capabilities probe while the store is
     * still UNKNOWN. No poll loop — see the class KDoc.
     */
    fun start() {
        capabilityGate?.arm()
    }

    /** Symmetry half of [start]: cancels the in-flight sessions task (no loop — analytics is not live). */
    fun stop() {
        sessionsTasks.cancel(SessionsFetchKey)
    }

    /**
     * Manual refresh (scaffold action): silent on failure — the stale data
     * stays; success clears a shown error. The overview leg rides this launch
     * and settles the phase ([fetchOverview]); the SESSION leg is routed
     * through the [sessionsTasks] slot ([loadSessionsPage]) — the same slot a
     * user re-filter occupies — so a [selectUser] landing mid-refresh
     * cancels-and-replaces the refresh's fetch (or is itself replaced,
     * capturing the then-current filter): the two writers cannot interleave a
     * wrong-filter page under the new chip, and [stop] can cancel the burst.
     */
    fun refresh() {
        capabilityGate?.arm()
        if (_uiState.value.gate != JellyPlayCapabilityState.Available) return
        launch {
            val priorPhase = _uiState.value.loadPhase
            if (priorPhase is AnalyticsLoadPhase.Loaded) {
                _uiState.update {
                    it.copy(loadPhase = AnalyticsLoadPhase.Refreshing(priorPhase.overview, priorPhase.degraded))
                }
            }
            val overviewResult = fetchOverview()
            if (overviewResult.isFailure) {
                // The refresh-failure idiom: restore the pre-refresh face —
                // unless a concurrent writer (a window-chip refetch) already
                // moved the phase past Refreshing on its own.
                _uiState.update { s ->
                    if (s.loadPhase is AnalyticsLoadPhase.Refreshing) s.copy(loadPhase = priorPhase) else s
                }
            }
            loadSessionsPage(reset = true, initial = false)
        }
    }

    /**
     * The gate collector — the shared capability gate's verdict (the
     * transcodes monitor's exact shape). The first load rides the Available
     * transition (never before — a gated-off screen must not touch the plugin
     * routes), so the screen opens on data rather than on a wasted 404 — and
     * the load latch RE-ARMS whenever the gate leaves Available, so a gate
     * drop + re-gate-on (identity reset / plugin reinstall) reloads.
     */
    private fun observeGate() {
        val gateFlow = capabilityGate?.state(JellyPlayPluginFeatures.Analytics, featureGate)
        if (gateFlow == null) {
            // Direct-construction without the plugin seam: gated off, final.
            // The phase is unobserved here (the screen renders the gate face).
            _uiState.update { it.copy(gate = JellyPlayCapabilityState.Unavailable) }
            return
        }
        launch {
            gateFlow.collect { gate ->
                _uiState.update { it.copy(gate = gate) }
                if (gate == JellyPlayCapabilityState.Available) {
                    if (!initialLoadStarted) {
                        initialLoadStarted = true
                        loadAll()
                    }
                } else {
                    // Gate dropped (Unknown after an identity reset, or
                    // Unavailable): re-arm the latch — the next Available
                    // transition is a fresh stretch and must reload.
                    initialLoadStarted = false
                }
            }
        }
    }

    /**
     * The initial gated load — overview + sessions in parallel. Only the
     * overview's failure surfaces the screen error; the sessions ledger is a
     * secondary surface whose failure stays silent (its next refresh retries).
     */
    private fun loadAll() {
        launch {
            loadInto(
                start = { _uiState.update { it.copy(loadPhase = AnalyticsLoadPhase.Loading) } },
                fetch = { pluginApiClient.getAnalyticsOverview(_uiState.value.days) },
                onSuccess = { overview ->
                    _uiState.update {
                        it.copy(
                            loadPhase = AnalyticsLoadPhase.Loaded(
                                overview = overview?.let(::toOverviewUi),
                                degraded = overview == null,
                            ),
                        )
                    }
                },
                onFailure = { e ->
                    Log.e("JellyPlayAnalytics", "Failed to fetch analytics overview", e)
                    _uiState.update { it.copy(loadPhase = AnalyticsLoadPhase.Failed(e.message)) }
                },
            )
        }
        loadSessionsPage(reset = true, initial = true)
    }

    /** Switches the overview window (the chips): a silent refetch — the stale overview stays on failure. */
    fun selectDays(days: Int) {
        if (_uiState.value.days == days) return
        _uiState.update { it.copy(days = days) }
        launch { fetchOverview() }
    }

    /**
     * Sets (or toggles off, re-tapping the same user) the sessions user
     * filter: the fetch carries [userId] AND the visible list filters
     * client-side, so both the next page and the already-loaded rows agree.
     */
    fun selectUser(userId: String?) {
        val next = userId.takeIf { it != _uiState.value.selectedUserId }
        _uiState.update { it.copy(selectedUserId = next) }
        loadSessionsPage(reset = true, initial = false)
    }

    /**
     * Loads the next sessions page: the cursor is the oldest `endedAt` of the
     * loaded (unfiltered) list. Appends de-duplicated — a session recorded
     * while paging (its `endedAt` at the cursor boundary) must not show twice.
     */
    fun loadMoreSessions() {
        val state = _uiState.value
        if (state.isLoadingMore || !state.hasMoreSessions || state.sessions.isEmpty()) return
        loadSessionsPage(reset = false, initial = false)
    }

    private fun loadSessionsPage(reset: Boolean, initial: Boolean) {
        if (initial) {
            _uiState.update { it.copy(isLoadingSessions = true) }
        }
        sessionsTasks.replace(SessionsFetchKey) { launch { fetchSessionsPage(reset = reset) } }
    }

    /**
     * The silent sessions fetch: a failure keeps the stale list (the
     * refresh-failure idiom) and only logs. `reset` replaces the list (page 1
     * — initial load, user re-filter, refresh); appending pages de-duplicate
     * on the (userId, itemId, endedAt) identity.
     */
    private suspend fun fetchSessionsPage(reset: Boolean) {
        val state = _uiState.value
        val appending = !reset
        _uiState.update {
            if (appending) {
                it.copy(isLoadingMore = true)
            } else {
                it.copy(isLoadingSessions = it.isLoadingSessions || it.sessions.isEmpty())
            }
        }
        val since = if (appending) state.sessions.minOfOrNull { it.endedAt } else null
        pluginApiClient.getAnalyticsSessions(
            userId = state.selectedUserId,
            since = since,
            limit = SESSIONS_PAGE_SIZE,
        )
            .onSuccess { page ->
                _uiState.update { current ->
                    val fetched = page?.sessions.orEmpty().map(::toSessionRow)
                    val merged = if (appending) {
                        val known = current.sessions.mapTo(mutableSetOf()) { sessionKey(it) }
                        current.sessions + fetched.filterNot { sessionKey(it) in known }
                    } else {
                        fetched
                    }
                    current.copy(
                        sessions = merged,
                        hasMoreSessions = fetched.size >= SESSIONS_PAGE_SIZE,
                        isLoadingSessions = false,
                        isLoadingMore = false,
                    )
                }
            }
            .onFailure { e ->
                Log.w("JellyPlayAnalytics", "Analytics sessions fetch failed", e)
                _uiState.update { it.copy(isLoadingSessions = false, isLoadingMore = false) }
            }
    }

    /**
     * The silent overview fetch behind the chips ([selectDays]) and [refresh]:
     * a success settles the phase to [AnalyticsLoadPhase.Loaded] (clearing a
     * shown [AnalyticsLoadPhase.Failed] — the former `error = null` write), a
     * failure only logs and leaves the phase to the caller's settle rule.
     */
    private suspend fun fetchOverview(): Result<JellyPlayAnalyticsOverview?> {
        val result = pluginApiClient.getAnalyticsOverview(_uiState.value.days)
        result
            .onSuccess { overview ->
                _uiState.update {
                    it.copy(
                        loadPhase = AnalyticsLoadPhase.Loaded(
                            overview = overview?.let(::toOverviewUi),
                            degraded = overview == null,
                        ),
                    )
                }
            }
            .onFailure { e ->
                Log.w("JellyPlayAnalytics", "Analytics overview fetch failed", e)
            }
        return result
    }

    override fun onCleared() {
        super.onCleared()
        stop()
    }

    companion object {
        /** The sessions page size (the plugin contract's default 50, max 200). */
        internal const val SESSIONS_PAGE_SIZE = 50
    }
}

/** A loaded session's identity — the de-dup key for appended pages. */
private fun sessionKey(row: AnalyticsSessionRow): String = "${row.userId}/${row.itemId}/${row.endedAt}"

/**
 * Maps the overview payload onto the screen shape: durations humanize through
 * the shared core/ui minute formatter (seconds → "12h 34m"), the per-day
 * split becomes the chart's [PlaybackActivityPoint] feed (the statistics
 * charts' point type).
 */
internal fun toOverviewUi(overview: JellyPlayAnalyticsOverview): AnalyticsOverviewUi =
    AnalyticsOverviewUi(
        totals = AnalyticsTotalsUi(
            plays = overview.totals.plays,
            watchTimeLabel = formatDurationFromMinutes(overview.totals.playSeconds / 60),
            transcodeTimeLabel = formatDurationFromMinutes(overview.totals.transcodeSeconds / 60),
            uniqueUsers = overview.totals.uniqueUsers,
            uniqueItems = overview.totals.uniqueItems,
        ),
        perDay = overview.perDay.map { day ->
            PlaybackActivityPoint(date = day.day, value = day.plays)
        },
        perUser = overview.perUser.map { user ->
            AnalyticsUserRow(
                userId = user.userId,
                userName = user.userName,
                plays = user.plays,
                playSeconds = user.playSeconds,
                transcodeSeconds = user.transcodeSeconds,
                watchTimeLabel = formatDurationFromMinutes(user.playSeconds / 60),
                transcodeTimeLabel = formatDurationFromMinutes(user.transcodeSeconds / 60),
            )
        },
        topItems = overview.topItems.map { item ->
            AnalyticsTopItemRow(
                itemId = item.itemId,
                itemName = item.itemName,
                itemType = item.itemType,
                plays = item.plays,
                watchTimeLabel = formatDurationFromMinutes(item.playSeconds / 60),
            )
        },
    )

/**
 * Maps the plugin's wire session onto the screen row — the transcodes
 * monitor's `playMethod` resolution: any "direct" spelling resolves to DIRECT,
 * any "transcode" spelling to TRANSCODE, anything else (including absent)
 * renders as OTHER/raw. Bitrate and watched position pre-humanize; the
 * transcode reasons ride along raw (the screen humanizes them at render).
 */
internal fun toSessionRow(session: JellyPlayAnalyticsSession): AnalyticsSessionRow {
    val method = session.playMethod.trim()
    val playMethod = when {
        method.contains("direct", ignoreCase = true) -> TranscodePlayMethod.DIRECT
        method.contains("transcode", ignoreCase = true) -> TranscodePlayMethod.TRANSCODE
        else -> TranscodePlayMethod.OTHER
    }
    return AnalyticsSessionRow(
        userId = session.userId,
        itemId = session.itemId,
        itemName = session.itemName,
        itemType = session.itemType,
        seriesName = session.seriesName,
        playMethod = playMethod,
        playMethodRaw = method.ifEmpty { null },
        bitrateLabel = session.bitrate?.let { formatBitrate(it.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()) },
        watchedLabel = session.positionTicks.takeIf { it > 0 }?.let(::formatDurationFromTicks),
        transcodeReasons = session.transcodeReasons.orEmpty(),
        endedAt = session.endedAt,
        clientName = session.clientName,
        deviceName = session.deviceName,
        startedAt = session.startedAt,
    )
}
