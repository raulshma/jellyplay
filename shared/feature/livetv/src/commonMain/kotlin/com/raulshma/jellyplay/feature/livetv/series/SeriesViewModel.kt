package com.raulshma.jellyplay.feature.livetv.series

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.data.repository.LiveTvRepository
import com.raulshma.jellyplay.core.model.DvrSeriesTimer
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.feature.livetv.components.RecordActions
import com.raulshma.jellyplay.feature.livetv.components.RecordOutcome

@Immutable
data class SeriesUiState(
    val seriesTimers: List<DvrSeriesTimer> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedTimer: DvrSeriesTimer? = null,
)

/**
 * Series tab — mirrors jellyfin-web `livetvseriestimers.js`: list series
 * timers sorted by name (`getSeriesTimers(sortBy=SortName)`), with a detail/
 * cancel sheet on tap.
 */
class SeriesViewModel(
    private val mediaRepository: LiveTvRepository,
) : JellyPlayViewModel() {

    private val _uiState = stateFlow(SeriesUiState())
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
            mediaRepository.getSeriesTimers(sortBy = "SortName")
                .onSuccess { _uiState.update { s -> s.copy(seriesTimers = it, isLoading = false) } }
                .onFailure { e -> _uiState.update { s -> s.copy(error = e.message, isLoading = false) } }
        }
    }

    fun showDetail(timer: DvrSeriesTimer) { _uiState.update { it.copy(selectedTimer = timer) } }
    fun dismissDetail() { _uiState.update { it.copy(selectedTimer = null) } }

    fun cancelSeries(timerId: String) {
        recordActions.cancelSeries(timerId)
    }
}
