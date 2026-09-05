package com.raulshma.jellyplay.feature.livetv.series

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.data.repository.LiveTvRepository
import com.raulshma.jellyplay.core.model.DvrSeriesTimer
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel

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
        launch {
            mediaRepository.cancelSeriesTimer(timerId)
                .onSuccess { _uiState.update { it.copy(selectedTimer = null) }; load() }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }
}
