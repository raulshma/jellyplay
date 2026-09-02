package com.raulshma.jellyplay.feature.livetv.epg

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.model.LiveTvChannel
import com.raulshma.jellyplay.core.model.LiveTvProgram
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.temporal.ChronoUnit

private const val REFRESH_INTERVAL_MS: Long = 5 * 60 * 1000L
private const val NOW_TICK_INTERVAL_MS: Long = 30 * 1000L
/** How far back from "now" the guide window extends (keeps recently-ended shows visible). */
private const val GUIDE_LOOKBACK_HOURS: Long = 2L
/** Total span of the guide window. Matches the 24h timeline used by the jellyfin-web guide. */
private const val GUIDE_WINDOW_HOURS: Long = 24L

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

class EpgViewModel(
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
    private val _windowStart = composeState(Instant.now().minus(GUIDE_LOOKBACK_HOURS, ChronoUnit.HOURS))
    private val _windowEnd = composeState(Instant.now().plus(GUIDE_WINDOW_HOURS - GUIDE_LOOKBACK_HOURS, ChronoUnit.HOURS))

    private val _recordDialog = composeState<RecordDialogState?>(null)
    val recordDialog: RecordDialogState? get() = _recordDialog.value

    /**
     * Cached grid snapshot. Rebuilt only when the source channels/programs or
     * the fetch window change — NOT on every recomposition. Previously this was
     * a computed getter that re-ran `buildEpgGridData` (groupBy + per-channel
     * filter + sort) on every frame read, which was the primary cause of guide
     * jank. See [rebuildGrid].
     */
    private val _gridData = composeState(
        buildEpgGridData(
            channels = emptyList(),
            programs = emptyList(),
            windowStart = _windowStart.value,
            windowEnd = _windowEnd.value,
        ),
    )
    val gridData: EpgGridData get() = _gridData.value

    /** Recompute the cached grid snapshot from the current source data. */
    private fun rebuildGrid() {
        _gridData.value = buildEpgGridData(
            channels = _channels.value,
            programs = _programs.value,
            windowStart = _windowStart.value,
            windowEnd = _windowEnd.value,
        )
    }

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
            val start = now.minus(GUIDE_LOOKBACK_HOURS, ChronoUnit.HOURS)
            val end = now.plus(GUIDE_WINDOW_HOURS - GUIDE_LOOKBACK_HOURS, ChronoUnit.HOURS)
            mediaRepository.getLiveTvGuide(startDateUtc = start.toString(), endDateUtc = end.toString(), limit = 100)
                .onSuccess { guide ->
                    _channels.value = guide.channels
                    _programs.value = guide.programs
                    _windowStart.value = start
                    _windowEnd.value = end
                    rebuildGrid()
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
                val start = now.minus(GUIDE_LOOKBACK_HOURS, ChronoUnit.HOURS)
                val end = now.plus(GUIDE_WINDOW_HOURS - GUIDE_LOOKBACK_HOURS, ChronoUnit.HOURS)
                mediaRepository.getLiveTvGuide(startDateUtc = start.toString(), endDateUtc = end.toString(), limit = 100)
                    .onSuccess { guide ->
                        _channels.value = guide.channels
                        _programs.value = guide.programs
                        _windowStart.value = start
                        _windowEnd.value = end
                        rebuildGrid()
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
