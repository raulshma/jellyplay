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

/** State of the "Record program" confirmation dialog driven from the EPG grid. */
sealed interface RecordDialogState {
    /** A program is selected and awaiting the user's confirm/cancel decision. */
    data class Confirm(val program: LiveTvProgram) : RecordDialogState
    /** Recording request is in flight. */
    data object Requesting : RecordDialogState
    /** The timer was created successfully. */
    data class Success(val programName: String) : RecordDialogState
    /** Creating the timer failed. */
    data class Error(val message: String) : RecordDialogState
}

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

    private val _recordDialog = composeState<RecordDialogState?>(null)
    val recordDialog: RecordDialogState? get() = _recordDialog.value

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

    /** Open the record-confirmation dialog for the given program. */
    fun requestRecord(program: LiveTvProgram) {
        _recordDialog.value = RecordDialogState.Confirm(program)
    }

    /** Confirm creating a timer for the program currently awaiting confirmation. */
    fun confirmRecord() {
        val pending = (_recordDialog.value as? RecordDialogState.Confirm)?.program ?: return
        _recordDialog.value = RecordDialogState.Requesting
        launch {
            mediaRepository.createTimer(pending.id)
                .onSuccess {
                    _recordDialog.value = RecordDialogState.Success(pending.name)
                    loadGuide()
                }
                .onFailure { e ->
                    _recordDialog.value = RecordDialogState.Error(e.message ?: "Failed to create recording")
                }
        }
    }

    fun dismissRecordDialog() { _recordDialog.value = null }

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
