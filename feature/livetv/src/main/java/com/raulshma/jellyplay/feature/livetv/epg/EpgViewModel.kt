package com.raulshma.jellyplay.feature.livetv.epg

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.model.LiveTvChannel
import com.raulshma.jellyplay.core.model.LiveTvProgram
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

private const val REFRESH_INTERVAL_MS: Long = 5 * 60 * 1000L
private const val NOW_TICK_INTERVAL_MS: Long = 30 * 1000L

@HiltViewModel
class EpgViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
) : JellyPlayViewModel() {

    private val _channels = composeState<List<LiveTvChannel>>(emptyList())
    val channels: List<LiveTvChannel> get() = _channels.value

    private val _programs = composeState<List<LiveTvProgram>>(emptyList())
    val programs: List<LiveTvProgram> get() = _programs.value

    private val _isLoading = composeState(false)
    val isLoading: Boolean get() = _isLoading.value

    private val _error = composeState<String?>(null)
    val error: String? get() = _error.value

    /** Ticking "now" timestamp so the time ruler + live indicator stay live. */
    private val _now = composeState(Instant.now())
    val now: Instant get() = _now.value

    /** Half-open window [start, end) covered by the current guide fetch. */
    private val _windowStart = composeState(Instant.now().minus(2, ChronoUnit.HOURS))
    val windowStart: Instant get() = _windowStart.value
    private val _windowEnd = composeState(Instant.now().plus(4, ChronoUnit.HOURS))
    val windowEnd: Instant get() = _windowEnd.value

    /** Convenience: pre-built grid snapshot for the current data. */
    val gridData: EpgGridData
        get() = buildEpgGridData(
            channels = channels,
            programs = programs,
            windowStart = windowStart,
            windowEnd = windowEnd,
        )

    init {
        loadGuide()
        startAutoRefresh()
        startNowTick()
    }

    fun loadGuide() {
        launch {
            _isLoading.value = true
            _error.value = null
            val now = Instant.now()
            val start = now.minus(2, ChronoUnit.HOURS).toString()
            val end = now.plus(4, ChronoUnit.HOURS).toString()
            mediaRepository.getLiveTvGuide(startDateUtc = start, endDateUtc = end, limit = 100)
                .onSuccess { guide ->
                    _channels.value = guide.channels
                    _programs.value = guide.programs
                    _windowStart.value = now.minus(2, ChronoUnit.HOURS)
                    _windowEnd.value = now.plus(4, ChronoUnit.HOURS)
                }
                .onFailure { _error.value = it.message }
            _isLoading.value = false
        }
    }

    private fun startAutoRefresh() {
        launch {
            while (true) {
                delay(REFRESH_INTERVAL_MS)
                val now = Instant.now()
                val start = now.minus(2, ChronoUnit.HOURS).toString()
                val end = now.plus(4, ChronoUnit.HOURS).toString()
                mediaRepository.getLiveTvGuide(startDateUtc = start, endDateUtc = end, limit = 100)
                    .onSuccess { guide ->
                        _channels.value = guide.channels
                        _programs.value = guide.programs
                        _windowStart.value = now.minus(2, ChronoUnit.HOURS)
                        _windowEnd.value = now.plus(4, ChronoUnit.HOURS)
                    }
            }
        }
    }

    private fun startNowTick() {
        launch {
            while (true) {
                delay(NOW_TICK_INTERVAL_MS)
                _now.value = Instant.now()
            }
        }
    }
}
