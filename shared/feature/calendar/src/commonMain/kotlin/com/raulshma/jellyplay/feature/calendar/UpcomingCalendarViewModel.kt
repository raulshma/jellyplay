package com.raulshma.jellyplay.feature.calendar

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.snapshotFlow
import com.raulshma.jellyplay.core.data.repository.ArrRepository
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalFeatureGate
import com.raulshma.jellyplay.core.model.arr.ArrCalendarItem
import com.raulshma.jellyplay.core.model.arr.ArrMediaType
import com.raulshma.jellyplay.core.model.seerr.buildPosterUrl
import com.raulshma.jellyplay.core.ui.viewmodel.IdEnricher
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.core.ui.viewmodel.loadInto
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.onDay
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlinx.datetime.yearMonth
import kotlin.time.Clock

/**
 * Whole-screen state for the Upcoming Calendar. The merged calendar stream is
 * mirrored into [items]; [enrichedPosters] holds TMDB poster URLs resolved in
 * the background so [CalendarCard] can fall back to them when the *arr poster
 * URL is unreachable (the common case behind a reverse proxy).
 */
@Immutable
data class UpcomingCalendarUiState(
    val items: List<ArrCalendarItem> = emptyList(),
    val visibleMonth: YearMonth = today().yearMonth,
    val filter: CalendarFilter = CalendarFilter.ALL,
    val isLoading: Boolean = false,
    val error: String? = null,
    val enrichedPosters: Map<Int, String> = emptyMap(),
)

class UpcomingCalendarViewModel(
    private val arrRepository: ArrRepository,
    private val seerrRepository: SeerrRepository,
    experimentalGate: ExperimentalFeatureGate,
) : JellyPlayViewModel() {

    private val _state = composeState(UpcomingCalendarUiState())
    val state: State<UpcomingCalendarUiState> = _state.asState()

    /**
     * Whether the Direct *arr Integration experimental flag is enabled —
     * [ExperimentalFeatureGate.directArrEnabled] verbatim (rationale and
     * init-timing notes live on the gate). Exposed so the screen can render
     * the feature-disabled state.
     */
    val featureEnabled: StateFlow<Boolean> = experimentalGate.directArrEnabled

    /**
     * The poster enrichment fan-out (the former hand-copied semaphore ladder,
     * now the [IdEnricher] core): Semaphore(4)-bounded Seerr lookups, per-item
     * failures swallowed, and the merge is this screen's append-only rule —
     * existing entries are never clobbered — written inside the enricher's
     * snapshot-atomic block (the declared fix for the unsynchronized
     * read-then-write the hand-rolled version did). The fetch resolves the
     * TMDB poster path through the right details endpoint and flattens it to
     * a URL via [buildPosterUrl] (blank paths → null → swallowed, so the *arr
     * poster URL remains the card's primary source).
     */
    private val posterEnricher = IdEnricher<Pair<Int, ArrMediaType>, String>(
        scope = scope,
        fetch = { (tmdbId, mediaType) ->
            val posterPath = when (mediaType) {
                ArrMediaType.MOVIE -> seerrRepository.getMovieDetails(tmdbId).getOrNull()?.posterPath
                ArrMediaType.SERIES -> seerrRepository.getTvDetails(tmdbId).getOrNull()?.posterPath
            }
            posterPath?.let { buildPosterUrl(it) }
        },
        merge = { (tmdbId, _), url ->
            val current = _state.value.enrichedPosters
            if (tmdbId !in current) {
                _state.value = _state.value.copy(enrichedPosters = current + (tmdbId to url))
            }
        },
    )

    /**
     * The active month collector, cancelled + re-launched whenever the visible
     * month changes. Without this each [changeMonth] would leak a permanent
     * collector for the previous window.
     */
    private var monthCollectorJob: Job? = null

    init {
        startMonthCollector()
        // Single long-lived enrichment watcher: reacts to item changes from
        // any month. snapshotFlow bridges Compose snapshot state into a Flow
        // (the same pattern SettingsViewModel uses).
        launch {
            snapshotFlow { _state.value.items }
                .distinctUntilChanged()
                .collect { enrichMissing(it) }
        }
        refresh()
    }

    /**
     * (Re)launches the calendar-flow collector for the current [visibleMonth].
     * The previous collector is cancelled first; the new one is scoped to the
     * window `[atDay(1), atEndOfMonth]`. Emits are only mirrored while the
     * visible month hasn't moved on (guards against a slow emit landing after
     * the user navigated to a different month).
     */
    private fun startMonthCollector() {
        monthCollectorJob?.cancel()
        monthCollectorJob = launch {
            val month = _state.value.visibleMonth
            val from = month.onDay(1)
            val to = month.lastDay
            // The whole module runs kotlinx.datetime now —
            // the repository boundary no longer converts.
            arrRepository.calendar(from, to).collect { items ->
                if (_state.value.visibleMonth == month) {
                    _state.value = _state.value.copy(items = items, error = null)
                }
            }
        }
    }

    /**
     * Resolves TMDB posters for any items lacking an enriched entry.
     * Fire-and-forget: failures are swallowed (the *arr poster URL remains the
     * primary source on the card). Append-only: existing entries are never
     * clobbered. The already-enriched check reads the map once at fan-out
     * time (the hand-rolled ladder's frozen snapshot), so a same-month
     * re-emission re-fetches nothing.
     */
    private fun enrichMissing(items: List<ArrCalendarItem>) {
        val already = _state.value.enrichedPosters
        val toEnrich = items
            .mapNotNull { item -> item.tmdbId?.let { it to item.mediaType } }
            .filter { (tmdbId, _) -> tmdbId !in already }
        if (toEnrich.isEmpty()) return
        posterEnricher.enrich(toEnrich)
    }

    /** Re-fetches the calendar for the visible month. Never throws. */
    fun refresh() {
        if (!featureEnabled.value) {
            _state.value = _state.value.copy(isLoading = false, error = null)
            return
        }
        launch {
            loadInto(
                start = { _state.value = _state.value.copy(isLoading = true, error = null) },
                fetch = {
                    val month = _state.value.visibleMonth
                    arrRepository.refreshCalendar(month.onDay(1), month.lastDay)
                },
                // The success arm is deliberately empty (see loadInto):
                // items land through the month collector, not this payload.
                onSuccess = { },
                onFailure = { _state.value = _state.value.copy(error = it.message) },
            )
            _state.value = _state.value.copy(isLoading = false)
        }
    }

    /**
     * Shifts the visible month by [delta] months (negative = past). Restarts
     * the collector on the new window and refreshes. Swapping the month clears
     * stale items immediately so the list doesn't briefly show the old month's
     * rows during the fetch.
     */
    fun changeMonth(delta: Int) {
        val newMonth = _state.value.visibleMonth.plus(delta, DateTimeUnit.MONTH)
        if (newMonth == _state.value.visibleMonth) return
        _state.value = _state.value.copy(
            visibleMonth = newMonth,
            items = emptyList(),
            error = null,
        )
        startMonthCollector()
        refresh()
    }

    /** Jumps the visible month back to the current month. */
    fun goToToday() {
        val now = today().yearMonth
        if (_state.value.visibleMonth == now) return
        _state.value = _state.value.copy(visibleMonth = now, items = emptyList(), error = null)
        startMonthCollector()
        refresh()
    }

    /**
     * Jumps the visible month to the month containing [date] (used by the
     * tap-month → date-picker affordance). If the month is already visible
     * this is a no-op fetch; otherwise it swaps the month + refreshes like
     * [changeMonth]. Returns whether the month actually changed, so the
     * caller can decide whether to request a scroll-to-day.
     */
    fun goToDate(date: LocalDate): Boolean {
        val target = date.yearMonth
        if (_state.value.visibleMonth == target) return false
        _state.value = _state.value.copy(visibleMonth = target, items = emptyList(), error = null)
        startMonthCollector()
        refresh()
        return true
    }

    /** Pure client-side filter switch; no network. */
    fun setFilter(filter: CalendarFilter) {
        _state.value = _state.value.copy(filter = filter)
    }

    /**
     * Stable per-row id for LazyColumn `key = { ... }`. Mirrors the
     * synthetic-id rationale in `ArrCalendarItem.syntheticId`: a tvdbId alone
     * collides across episodes of one series, and `tmdbId ?: 0` collapses
     * every id-less row onto 0 (which crashes Compose on duplicate keys).
     */
    fun stableRowId(item: ArrCalendarItem): String =
        "${item.mediaType}|${item.tvdbId ?: "_"}|${item.tmdbId ?: "_"}|${item.title}|${item.airDateUtc ?: "_"}"
}

/**
 * Today's date in the device timezone, centralised so the screen and VM agree.
 * kotlin.time.Clock + the kotlinx todayIn extension (the java.time ZoneId
 * seam is gone).
 */
internal fun today(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())
