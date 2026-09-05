package com.raulshma.jellyplay.feature.livetv.schedule

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.data.repository.LiveTvRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.DvrTimer
import com.raulshma.jellyplay.core.model.LiveTvRecording
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.feature.livetv.components.RecordActions
import com.raulshma.jellyplay.feature.livetv.components.RecordOutcome
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

private val DATE_LABEL_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d")

/** Timers grouped by their start date, matching jellyfin-web `getTimersHtml`. */
@Immutable
data class TimerDateGroup(
    val dateLabel: String,
    val timers: List<DvrTimer>,
)

@Immutable
data class ScheduleUiState(
    val activeRecordings: List<LiveTvRecording> = emptyList(),
    val upcomingGroups: List<TimerDateGroup> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    /** Selected timer for the detail/cancel sheet; null = hidden. */
    val selectedTimer: DvrTimer? = null,
)

/**
 * Schedule tab — mirrors jellyfin-web `livetvschedule.js`: active recordings
 * (`getRecordings(isInProgress=true)`) plus upcoming scheduled timers
 * (`getTimers(isActive=false, isScheduled=true)`) grouped by date.
 */
class ScheduleViewModel(
    private val mediaRepository: LiveTvRepository,
    private val imageUrlProvider: ImageUrlProvider,
) : JellyPlayViewModel() {

    private val _uiState = stateFlow(ScheduleUiState())
    val uiState get() = _uiState.flow

    /**
     * The shared record choreography ([RecordActions]) for the cancel action;
     * this tab's adaptation closes the detail sheet and reloads on success,
     * and surfaces the raw failure on the tab's error field (sheet kept open).
     */
    private val recordActions = RecordActions(mediaRepository, scope) { outcome ->
        when (outcome) {
            is RecordOutcome.Success -> {
                _uiState.update { it.copy(selectedTimer = null) }
                load()
            }
            is RecordOutcome.Error -> _uiState.update { it.copy(error = outcome.message) }
            is RecordOutcome.Requesting, RecordOutcome.Idle -> Unit
        }
    }

    init { load() }

    fun load() {
        launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val active = mediaRepository.getRecordings(isInProgress = true)
            val upcoming = mediaRepository.getTimers(isActive = false, isScheduled = true)
            active.onFailure { _uiState.update { s -> s.copy(error = it.message) } }
            upcoming.onFailure { _uiState.update { s -> s.copy(error = it.message) } }
            _uiState.update { s ->
                s.copy(
                    activeRecordings = active.getOrDefault(emptyList()),
                    upcomingGroups = groupByDate(upcoming.getOrDefault(emptyList())),
                    isLoading = false,
                )
            }
        }
    }

    fun showTimerDetail(timer: DvrTimer) { _uiState.update { it.copy(selectedTimer = timer) } }
    fun dismissDetail() { _uiState.update { it.copy(selectedTimer = null) } }

    fun cancelTimer(timerId: String) {
        recordActions.cancelTimer(timerId)
    }

    fun getImageUrl(itemId: String, imageTag: String?): String =
        if (imageTag != null) imageUrlProvider.getImageUrl(itemId) else ""

    /** Groups timers by their start-date label (e.g. "Mon, Jul 14"), sorted ascending. */
    private fun groupByDate(timers: List<DvrTimer>): List<TimerDateGroup> =
        timers.mapNotNull { t ->
            t.startDate?.let { parseDateLabel(it) }?.let { label -> label to t }
        }
            .groupBy({ it.first }, { it.second })
            .map { (label, list) -> TimerDateGroup(label, list.sortedBy { it.startDate }) }
            .sortedBy { group -> group.timers.firstNotNullOfOrNull { it.startDate } }

    private fun parseDateLabel(iso: String): String? = runCatching {
        OffsetDateTime.parse(iso, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            .format(DATE_LABEL_FORMATTER)
    }.recoverCatching {
        java.time.LocalDateTime.parse(
            iso.replace("Z", "").replace("T", " ").substringBefore('+').trim()
        ).format(DATE_LABEL_FORMATTER)
    }.getOrNull()
}
