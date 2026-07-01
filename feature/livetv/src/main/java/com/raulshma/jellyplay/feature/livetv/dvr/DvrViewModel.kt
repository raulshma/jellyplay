package com.raulshma.jellyplay.feature.livetv.dvr

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.DvrSeriesTimer
import com.raulshma.jellyplay.core.model.DvrTimer
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import javax.inject.Inject

/**
 * Selection state for the recording detail sheet shown when a [DvrTimer] or
 * [DvrSeriesTimer] card is tapped. Null means no sheet is shown.
 */
sealed interface DvrDetailState {
    data class Timer(val timer: DvrTimer) : DvrDetailState
    data class SeriesTimer(val timer: DvrSeriesTimer) : DvrDetailState
}

@HiltViewModel
class DvrViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val preferencesStore: UserPreferencesStore,
) : JellyPlayViewModel() {

    private val _timers = composeState<List<DvrTimer>>(emptyList())
    val timers: List<DvrTimer> get() = _timers.value

    private val _seriesTimers = composeState<List<DvrSeriesTimer>>(emptyList())
    val seriesTimers: List<DvrSeriesTimer> get() = _seriesTimers.value

    private val _isLoading = composeState(false)
    val isLoading: Boolean get() = _isLoading.value

    private val _error = composeState<String?>(null)
    val error: String? get() = _error.value

    private val _detail = composeState<DvrDetailState?>(null)
    val detail: DvrDetailState? get() = _detail.value

    init {
        load()
    }

    fun load() {
        launch {
            _isLoading.value = true
            _error.value = null

            mediaRepository.getTimers()
                .onSuccess { _timers.value = it }
                .onFailure { _error.value = it.message }

            mediaRepository.getSeriesTimers()
                .onSuccess { _seriesTimers.value = it }
                .onFailure { _error.value = it.message }

            _isLoading.value = false
        }
    }

    fun showTimerDetail(timer: DvrTimer) { _detail.value = DvrDetailState.Timer(timer) }
    fun showSeriesTimerDetail(timer: DvrSeriesTimer) { _detail.value = DvrDetailState.SeriesTimer(timer) }
    fun dismissDetail() { _detail.value = null }

    fun cancelTimer(timerId: String) {
        launch {
            mediaRepository.cancelTimer(timerId)
                .onSuccess { _detail.value = null; load() }
                .onFailure { _error.value = it.message }
        }
    }

    fun cancelSeriesTimer(timerId: String) {
        launch {
            mediaRepository.cancelSeriesTimer(timerId)
                .onSuccess { _detail.value = null; load() }
                .onFailure { _error.value = it.message }
        }
    }

    fun createTimer(programId: String, channelId: String, startDate: String?, endDate: String?) {
        launch {
            val prefs = preferencesStore.preferences.value
            val prePad = prefs.dvrPrePaddingMinutes.coerceAtLeast(0)
            val postPad = prefs.dvrPostPaddingMinutes.coerceAtLeast(0)
            val paddedStart = if (prePad > 0) startDate?.let { shiftIso(it, -prePad.toLong()) } else startDate
            val paddedEnd = if (postPad > 0) endDate?.let { shiftIso(it, postPad.toLong()) } else endDate
            mediaRepository.createTimer(programId, channelId, paddedStart, paddedEnd)
                .onSuccess { load() }
                .onFailure { _error.value = it.message }
        }
    }

    /**
     * Shifts an ISO-8601 date-time string by [minutes] (negative = earlier).
     * Falls back to the original string if parsing fails so a bad format
     * never blocks timer creation.
     */
    private fun shiftIso(iso: String, minutes: Long): String = runCatching {
        OffsetDateTime.parse(iso)
            .plusMinutes(minutes)
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    }.recoverCatching {
        java.time.LocalDateTime.parse(
            iso.replace("Z", "").replace("T", " ").substringBefore('+').trim()
        ).plusMinutes(minutes).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    }.getOrElse { iso }
}
