package com.raulshma.jellyplay.feature.livetv.dvr

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.model.DvrSeriesTimer
import com.raulshma.jellyplay.core.model.DvrTimer
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class DvrViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
) : JellyPlayViewModel() {

    private val _timers = composeState<List<DvrTimer>>(emptyList())
    val timers: List<DvrTimer> get() = _timers.value

    private val _seriesTimers = composeState<List<DvrSeriesTimer>>(emptyList())
    val seriesTimers: List<DvrSeriesTimer> get() = _seriesTimers.value

    private val _isLoading = composeState(false)
    val isLoading: Boolean get() = _isLoading.value

    private val _error = composeState<String?>(null)
    val error: String? get() = _error.value

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

    fun cancelTimer(timerId: String) {
        launch {
            mediaRepository.cancelTimer(timerId)
                .onSuccess { load() }
                .onFailure { _error.value = it.message }
        }
    }

    fun cancelSeriesTimer(timerId: String) {
        launch {
            mediaRepository.cancelTimer(timerId)
                .onSuccess { load() }
                .onFailure { _error.value = it.message }
        }
    }

    fun createTimer(programId: String, channelId: String, startDate: String?, endDate: String?) {
        launch {
            mediaRepository.createTimer(programId, channelId, startDate, endDate)
                .onSuccess { load() }
                .onFailure { _error.value = it.message }
        }
    }
}
