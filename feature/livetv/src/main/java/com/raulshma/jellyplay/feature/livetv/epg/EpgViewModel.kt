package com.raulshma.jellyplay.feature.livetv.epg

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.model.LiveTvProgram
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@HiltViewModel
class EpgViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
) : JellyPlayViewModel() {

    private val _programs = composeState<List<LiveTvProgram>>(emptyList())
    val programs: List<LiveTvProgram> get() = _programs.value

    private val _isLoading = composeState(false)
    val isLoading: Boolean get() = _isLoading.value

    private val _error = composeState<String?>(null)
    val error: String? get() = _error.value

    init {
        loadGuide()
        startAutoRefresh()
    }

    fun loadGuide() {
        launch {
            _isLoading.value = true
            _error.value = null
            val now = Instant.now()
            val start = now.minus(2, ChronoUnit.HOURS).toString()
            val end = now.plus(4, ChronoUnit.HOURS).toString()
            mediaRepository.getLiveTvGuide(startDateUtc = start, endDateUtc = end, limit = 100)
                .onSuccess { _programs.value = it.programs }
                .onFailure { _error.value = it.message }
            _isLoading.value = false
        }
    }

    private fun startAutoRefresh() {
        launch {
            while (true) {
                delay(5 * 60 * 1000L)
                val now = Instant.now()
                val start = now.minus(2, ChronoUnit.HOURS).toString()
                val end = now.plus(4, ChronoUnit.HOURS).toString()
                mediaRepository.getLiveTvGuide(startDateUtc = start, endDateUtc = end, limit = 100)
                    .onSuccess { _programs.value = it.programs }
            }
        }
    }
}
