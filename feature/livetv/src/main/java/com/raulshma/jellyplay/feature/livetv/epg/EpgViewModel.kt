package com.raulshma.jellyplay.feature.livetv.epg

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.model.LiveTvProgram
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@HiltViewModel
class EpgViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
) : ViewModel() {

    var programs by mutableStateOf<List<LiveTvProgram>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    init {
        loadGuide()
    }

    fun loadGuide() {
        viewModelScope.launch {
            isLoading = true
            error = null
            val now = Instant.now()
            val start = now.minus(2, ChronoUnit.HOURS).toString()
            val end = now.plus(4, ChronoUnit.HOURS).toString()
            mediaRepository.getLiveTvGuide(startDateUtc = start, endDateUtc = end, limit = 100)
                .onSuccess { programs = it.programs }
                .onFailure { error = it.message }
            isLoading = false
        }
    }
}
