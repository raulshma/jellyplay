package com.raulshma.jellyplay.feature.insights.heatmap

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.concurrency.DEFAULT_FANOUT_PARALLELISM
import com.raulshma.jellyplay.core.concurrency.mapConcurrent
import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import com.raulshma.jellyplay.core.data.error.UserErrorMessages
import com.raulshma.jellyplay.core.data.repository.DailyWatchActivity
import com.raulshma.jellyplay.core.data.repository.HeatmapFilter
import com.raulshma.jellyplay.core.data.repository.StreakInfo
import com.raulshma.jellyplay.core.data.repository.WatchHistoryRepository
import com.raulshma.jellyplay.core.data.util.ArtworkUrls
import com.raulshma.jellyplay.core.data.repository.DetailFeed
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.PlaybackReportingDetail
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.core.ui.viewmodel.loadInto
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.sync.Semaphore
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Immutable
data class WatchProgressHeatmapUiState(
    val isLoading: Boolean = true,
    val year: Int = LocalDate.now().year,
    val filter: HeatmapFilter = HeatmapFilter.ALL,
    val dailyActivities: List<DailyWatchActivity> = emptyList(),
    val streakInfo: StreakInfo = StreakInfo(0, 0, 0),
    val selectedDay: SelectedDayInfo? = null,
    val isPluginAvailable: Boolean = false,
    val minActivityDate: LocalDate? = null,
    val error: String? = null,
)

@Immutable
data class SelectedDayInfo(
    val date: LocalDate,
    val dateLabel: String,
    val sessions: List<PlaybackReportingDetail>,
    val resolvedItems: Map<String, ResolvedMediaItem>,
)

@Immutable
data class ResolvedMediaItem(
    val name: String,
    val mediaType: MediaType,
    val imageUrl: String?,
)

sealed interface HeatmapEvent {
    data class SetYear(val year: Int) : HeatmapEvent
    data class SetFilter(val filter: HeatmapFilter) : HeatmapEvent
    data class SelectDay(val date: LocalDate?) : HeatmapEvent
    data object DismissDayDetail : HeatmapEvent
    // The former RequestShare / ShareConsumed arms lived only to toggle the
    // retired `shareRequested` parked UiState boolean; the share request now
    // rides the VM's one-shot [WatchProgressHeatmapViewModel.shareRequests]
    // channel via [WatchProgressHeatmapViewModel.requestShare]
    // (StateManagementConventions §2).
}

class WatchProgressHeatmapViewModel(
    private val watchHistoryRepository: WatchHistoryRepository,
    /** The detail-read family seam. */
    private val detailFeed: DetailFeed,
    /** Day-detail poster URLs (the ArtworkUrls seam — the resolved item's own image tag rides along). */
    private val artworkUrls: ArtworkUrls,
) : JellyPlayViewModel() {

    private companion object {
        val SELECTED_DAY_LABEL_FORMAT = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")
    }

    private val _uiState = stateFlow(WatchProgressHeatmapUiState())
    val uiState: StateFlow<WatchProgressHeatmapUiState> = _uiState.flow

    /**
     * The heatmap image's share request ([requestShare]) — the buffered
     * never-replayed Channel that replaced the former `shareRequested` parked
     * UiState boolean + the `HeatmapEvent.ShareConsumed` consume event (the
     * codified StateManagementConventions §2 idiom: a StateFlow replays its
     * current value to every new collector, so the parked boolean re-fired
     * the share sheet on re-subscription for a request that happened once).
     * [WatchProgressHeatmapScreen] collects this in a single
     * [androidx.compose.runtime.LaunchedEffect] and snapshots the same
     * capture layer it always has. The request carries no payload — the
     * share seam snapshots the grid's own capture layer.
     */
    private val shareRequestChannel = Channel<Unit>(Channel.BUFFERED)

    /** The one-shot share-request stream; each request is delivered exactly once. */
    val shareRequests: Flow<Unit> = shareRequestChannel.receiveAsFlow()

    /** Queues one share-sheet delivery (the share icon's command). */
    fun requestShare() {
        shareRequestChannel.trySend(Unit)
    }

    private var cachedResolvedItems = mutableMapOf<String, ResolvedMediaItem>()

    init {
        launch {
            watchHistoryRepository.refreshPlaybackReportingStatus()
            val minDateStr = watchHistoryRepository.getMinimumActivityDate()
            val minDate = minDateStr?.take(10)?.let {
                runCatching { LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull()
            }
            _uiState.update { it.copy(minActivityDate = minDate) }
            loadHeatmapData()
        }
    }

    fun onEvent(event: HeatmapEvent) {
        when (event) {
            is HeatmapEvent.SetYear -> {
                _uiState.update { it.copy(year = event.year, isLoading = true) }
                loadHeatmapData()
            }
            is HeatmapEvent.SetFilter -> {
                _uiState.update { it.copy(filter = event.filter, isLoading = true, selectedDay = null) }
                loadHeatmapData()
            }
            is HeatmapEvent.SelectDay -> selectDay(event.date)
            is HeatmapEvent.DismissDayDetail -> _uiState.update { it.copy(selectedDay = null) }
        }
    }

    /** Force a fresh fetch (e.g. pull-to-refresh or error retry). */
    fun refresh() {
        _uiState.update { it.copy(isLoading = true) }
        loadHeatmapData()
    }

    private fun loadHeatmapData() {
        launch {
            // One read of the year/filter pair drives the whole ladder — the
            // legacy body's reads, at their legacy position (an event landing
            // mid-flight must not half-update the request window).
            val state = _uiState.value
            val year = state.year
            val filter = state.filter

            loadInto(
                // The flag is raised by the entry points (SetYear, SetFilter,
                // refresh); the cold init path rides the state's isLoading=true
                // default — nothing to raise here.
                start = { },
                fetch = {
                    // The legacy ladder's catch shape (CancellationException
                    // rethrown, every other throw settling as the error arm)
                    // expressed as the sanctioned wrapper.
                    runCatchingRethrowingCancellation {
                        val activities = watchHistoryRepository.getDailyActivity(year, filter)
                        val streaks = calculateStreaks(activities)

                        val isPluginAvailable = watchHistoryRepository.playbackReportingStatus.value ==
                            com.raulshma.jellyplay.core.model.PlaybackReportingStatus.AVAILABLE

                        Triple(activities, streaks, isPluginAvailable)
                    }
                },
                onSuccess = { (activities, streaks, isPluginAvailable) ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            dailyActivities = activities,
                            streakInfo = streaks,
                            isPluginAvailable = isPluginAvailable,
                            error = null,
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            // UserErrorMessages fold (message ?: fallback); the
                            // former ladder preferred localizedMessage, which on
                            // both JVM and Android defaults to message unless a
                            // subclass overrides it — the fold expresses this.
                            error = UserErrorMessages.resolve(e, ""),
                        )
                    }
                },
            )
        }
    }

    private fun selectDay(date: LocalDate?) {
        if (date == null) {
            _uiState.update { it.copy(selectedDay = null) }
            return
        }
        launch {
            val state = _uiState.value
            val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
            val sessions = watchHistoryRepository.getItemsForDay(dateStr, state.filter)

            resolveItems(sessions.map { it.itemId }.distinct())

            _uiState.update {
                it.copy(
                    selectedDay = SelectedDayInfo(
                        date = date,
                        dateLabel = date.format(SELECTED_DAY_LABEL_FORMAT),
                        sessions = sessions,
                        resolvedItems = cachedResolvedItems.toMap(),
                    ),
                )
            }
        }
    }

    private val resolveSemaphore = Semaphore(DEFAULT_FANOUT_PARALLELISM)

    /**
     * Resolves day-detail items at bounded parallelism
     * ([DEFAULT_FANOUT_PARALLELISM]) — a
     * binge day used to pay 20-50 sequential detail round-trips before the
     * day sheet populated. Same shape as MusicHomeViewModel's
     * fetchAlbumTracksParallel: a failed detail (getOrNull() == null) drops
     * just that item, exactly the old `continue`, and survivors are folded
     * into [cachedResolvedItems] in input order after the fan-out — the
     * plain map stays single-writer on this coroutine, first-wins cache
     * semantics unchanged.
     */
    private suspend fun resolveItems(itemIds: List<String>) {
        val unresolved = itemIds.filter { it !in cachedResolvedItems }
        val resolved = resolveSemaphore.mapConcurrent(unresolved) { itemId ->
            detailFeed.getMediaDetail(itemId, force = false).getOrNull()?.let { detail ->
                itemId to ResolvedMediaItem(
                    name = detail.item.name,
                    mediaType = detail.item.mediaType,
                    // The detail's item is in hand, so the thumbnail URL
                    // carries its image tag (the etag cache-buster).
                    imageUrl = artworkUrls.poster(detail.item, 200),
                )
            }
        }.filterNotNull()
        resolved.forEach { (itemId, item) -> cachedResolvedItems[itemId] = item }
    }

    private fun calculateStreaks(activities: List<DailyWatchActivity>): StreakInfo {
        if (activities.isEmpty()) return StreakInfo(0, 0, 0)

        val activeDates = activities
            .filter { it.value > 0 }
            .mapNotNull {
                runCatching { LocalDate.parse(it.date, DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull()
            }
            .distinct()
            .sorted()

        if (activeDates.isEmpty()) return StreakInfo(0, 0, 0)

        var longestStreak = 0
        var tempStreak = 1

        for (i in 1 until activeDates.size) {
            if (activeDates[i] == activeDates[i - 1].plusDays(1)) {
                tempStreak++
            } else {
                if (tempStreak > longestStreak) longestStreak = tempStreak
                tempStreak = 1
            }
        }
        if (tempStreak > longestStreak) longestStreak = tempStreak

        val today = LocalDate.now()
        var currentStreak = 0
        var checkDate = today
        while (checkDate in activeDates) {
            currentStreak++
            checkDate = checkDate.minusDays(1)
        }

        return StreakInfo(
            currentStreak = currentStreak,
            longestStreak = longestStreak,
            totalActiveDays = activeDates.size,
        )
    }
}
