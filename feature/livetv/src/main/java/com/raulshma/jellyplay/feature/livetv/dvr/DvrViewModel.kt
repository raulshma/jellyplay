package com.raulshma.jellyplay.feature.livetv.dvr

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.model.DvrSeriesTimer
import com.raulshma.jellyplay.core.model.DvrTimer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DvrViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
) : ViewModel() {

    var timers by mutableStateOf<List<DvrTimer>>(emptyList())
        private set

    var seriesTimers by mutableStateOf<List<DvrSeriesTimer>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            isLoading = true
            error = null

            mediaRepository.getTimers()
                .onSuccess { timers = it }
                .onFailure { error = it.message }

            mediaRepository.getSeriesTimers()
                .onSuccess { seriesTimers = it }
                .onFailure { error = it.message }

            isLoading = false
        }
    }

    fun cancelTimer(timerId: String) {
        viewModelScope.launch {
            mediaRepository.cancelTimer(timerId)
                .onSuccess { load() }
                .onFailure { error = it.message }
        }
    }

    fun cancelSeriesTimer(timerId: String) {
        viewModelScope.launch {
            // Series timers use the same cancel endpoint in Jellyfin
            mediaRepository.cancelTimer(timerId)
                .onSuccess { load() }
                .onFailure { error = it.message }
        }
    }

    fun createTimer(programId: String, channelId: String, startDate: String?, endDate: String?) {
        viewModelScope.launch {
            mediaRepository.createTimer(programId, channelId, startDate, endDate)
                .onSuccess { load() }
                .onFailure { error = it.message }
        }
    }
}
