package com.raulshma.jellyplay.feature.admin.analytics

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.data.log.Log
import com.raulshma.jellyplay.core.data.session.JellyPlayFeatureGate
import com.raulshma.jellyplay.core.data.session.JellyPlayPluginStatusStore
import com.raulshma.jellyplay.core.data.session.isAvailableOrProbe
import com.raulshma.jellyplay.core.model.JellyPlayPluginFeatures
import com.raulshma.jellyplay.core.model.JellyPlayPluginStatus
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * The admin analytics dashboard's gate — plugin AVAILABLE **and** the
 * `analytics` feature key present **and** the user's per-feature toggle on
 * (the transcodes monitor's gate shape; ADR 0010: the capability registry is
 * the ONLY gating mechanism, the toggle rides [JellyPlayFeatureGate], the ONE
 * per-feature seam). Admin-ness is enforced upstream by the admin area's
 * AdminRouteContainer — the existing admin gate, deliberately not re-built
 * here.
 */
enum class AnalyticsGate { Unknown, Available, Unavailable }

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
)

@Immutable
data class AnalyticsState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val gate: AnalyticsGate = AnalyticsGate.Unknown,
    /** The selected window (the 7/30/90 chips; the overview fetch's `days`). */
    val days: Int = 30,
    /** null = no overview loaded yet; the screen shows its quiet degraded state. */
    val overview: AnalyticsOverviewUi? = null,
    /**
     * True when the overview route answered 404 (plugin predates analytics or
     * the key is absent) — a QUIET degrade, never an error banner.
     */
    val overviewDegraded: Boolean = false,
    val sessions: List<AnalyticsSessionRow> = emptyList(),
    /** The active per-user sessions filter (null = all users). */
    val selectedUserId: String? = null,
    /** True while a sessions (re)fetch is in flight — the section's spinner. */
    val isLoadingSessions: Boolean = false,
    /** True while a Load-more page is in flight. */
    val isLoadingMore: Boolean = false,
    /** True when the last sessions page came back full — a Load more affordance exists. */
    val hasMoreSessions: Boolean = false,
)

/**
 * Backs the admin analytics dashboard (Route.JellyPlayAnalytics): the
 * companion plugin's play-history aggregates (overview over a 7/30/90-day
 * window + the recent-sessions ledger).
 *
 * Gating — admin && plugin AVAILABLE && `analytics` feature, the transcodes
 * monitor's exact seam: the admin half is the admin area's existing
 * AdminRouteContainer gate; the plugin half flows from
 * [JellyPlayPluginStatusStore] per ADR 0010 — no analytics fetch is ever
 * issued while the gate is off, so a stock server's plugin-route 404 is never
 * observed here. The nullable-with-default ctor deps mirror the settings
 * screen's plugin-seam pattern: direct construction (tests) may omit them —
 * a null store is simply gated off.
 *
 * Loading — unlike the transcodes monitor there is NO auto-refresh loop (the
 * aggregates move on day boundaries, not seconds); [start] re-arms the probe
 * when the screen becomes visible and [stop] is the visibility idiom's
 * symmetry half. The initial load rides the gate's Available transition and
 * is the only fetch whose overview failure surfaces an error; sessions
 * failures and refresh/pagination failures are silent (the stale list stays —
 * the refresh-failure idiom). A 404 (null payload) degrades quietly:
 * [AnalyticsState.overviewDegraded], never an error.
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

    /** Whether the initial gated load has been kicked off (exactly once per gate-on). */
    private var initialLoadStarted = false

    /** The in-flight sessions fetch — a re-filter/pagination burst supersedes it. */
    private var sessionsJob: Job? = null

    init {
        observeGate()
    }

    /**
     * Screen-visibility hook (the transcodes screen's LifecycleStartEffect
     * shape): one capabilities probe while the store is still UNKNOWN. No poll
     * loop — see the class KDoc.
     */
    fun start() {
        refreshPluginStatus()
    }

    /** Symmetry half of [start]: cancels the in-flight sessions load (no loop — analytics is not live). */
    fun stop() {
        sessionsJob?.cancel()
        sessionsJob = null
    }

    /** Manual refresh (scaffold action): silent on failure — the stale data stays; success clears a shown error. */
    fun refresh() {
        refreshPluginStatus()
        if (_uiState.value.gate != AnalyticsGate.Available) return
        launch {
            _uiState.update { it.copy(isRefreshing = true) }
            fetchOverview()
            fetchSessionsPage(reset = true)
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    /**
     * One capabilities probe — the store never re-probes on its own
     * (UNKNOWN → one refresh; AVAILABLE/UNAVAILABLE stay), the identity reset
     * in the store re-arms the next visit. Same discipline as the transcodes
     * monitor.
     */
    private fun refreshPluginStatus() {
        val store = statusStore ?: return
        launch {
            if (store.status.value == JellyPlayPluginStatus.UNKNOWN) {
                store.refresh()
            }
        }
    }

    /**
     * The gate collector — the transcodes monitor's exact shape. The first
     * load rides the Available transition (never before — a gated-off screen
     * must not touch the plugin routes), so the screen opens on data rather
     * than on a wasted 404.
     */
    private fun observeGate() {
        val store = statusStore
        if (store == null) {
            // Direct-construction without the plugin seam: gated off, final.
            _uiState.update { it.copy(gate = AnalyticsGate.Unavailable, isLoading = false) }
            return
        }
        val available: kotlinx.coroutines.flow.Flow<Boolean> =
            featureGate.isAvailableOrProbe(store, JellyPlayPluginFeatures.Analytics)
        launch {
            combine(store.status, available) { status, gateOpen ->
                when {
                    gateOpen -> AnalyticsGate.Available
                    status == JellyPlayPluginStatus.UNKNOWN -> AnalyticsGate.Unknown
                    else -> AnalyticsGate.Unavailable
                }
            }.collect { gate ->
                _uiState.update {
                    it.copy(
                        gate = gate,
                        isLoading = if (gate == AnalyticsGate.Unknown) it.isLoading else false,
                    )
                }
                if (gate == AnalyticsGate.Available && !initialLoadStarted) {
                    initialLoadStarted = true
                    loadAll()
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
                start = { _uiState.update { it.copy(isLoading = true, error = null) } },
                fetch = { pluginApiClient.getAnalyticsOverview(_uiState.value.days) },
                onSuccess = { overview ->
                    _uiState.update {
                        it.copy(
                            overview = overview?.let(::toOverviewUi),
                            overviewDegraded = overview == null,
                            isLoading = false,
                        )
                    }
                },
                onFailure = { e ->
                    Log.e("JellyPlayAnalytics", "Failed to fetch analytics overview", e)
                    _uiState.update { it.copy(error = e.message, isLoading = false) }
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
        sessionsJob?.cancel()
        sessionsJob = launch {
            fetchSessionsPage(reset = reset)
        }
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

    /** The silent overview fetch behind the chips ([selectDays]) and [refresh]. */
    private suspend fun fetchOverview() {
        pluginApiClient.getAnalyticsOverview(_uiState.value.days)
            .onSuccess { overview ->
                _uiState.update {
                    it.copy(
                        overview = overview?.let(::toOverviewUi),
                        overviewDegraded = overview == null,
                        error = null,
                    )
                }
            }
            .onFailure { e ->
                Log.w("JellyPlayAnalytics", "Analytics overview fetch failed", e)
            }
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
    )
}
