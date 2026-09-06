package com.raulshma.jellyplay.feature.livetv.epg

import com.raulshma.jellyplay.core.data.repository.LiveTvRepository
import com.raulshma.jellyplay.core.model.LiveTvChannel
import com.raulshma.jellyplay.core.model.LiveTvProgram
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.feature.livetv.components.RecordActions
import com.raulshma.jellyplay.feature.livetv.components.RecordDialogState
import com.raulshma.jellyplay.feature.livetv.components.RecordOutcome
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.temporal.ChronoUnit

private const val REFRESH_INTERVAL_MS: Long = 5 * 60 * 1000L
private const val NOW_TICK_INTERVAL_MS: Long = 30 * 1000L
/** How far back from "now" the guide window extends (keeps recently-ended shows visible). */
private const val GUIDE_LOOKBACK_HOURS: Long = 2L
/** Total span of the guide window. Matches the 24h timeline used by the jellyfin-web guide. */
private const val GUIDE_WINDOW_HOURS: Long = 24L

class EpgViewModel(
    private val mediaRepository: LiveTvRepository,
    /** Off-Main dispatcher for the grid rebuild; injectable so jvmTest rides the test scheduler. */
    private val gridDispatcher: CoroutineDispatcher = Dispatchers.Default,
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
    val now: StateFlow<Instant> = flow {
        emit(Instant.now())
        while (true) {
            delay(NOW_TICK_INTERVAL_MS)
            emit(Instant.now())
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), Instant.now())

    /** Half-open window [start, end) covered by the current guide fetch. */
    private val _windowStart = composeState(Instant.now().minus(GUIDE_LOOKBACK_HOURS, ChronoUnit.HOURS))
    private val _windowEnd = composeState(Instant.now().plus(GUIDE_WINDOW_HOURS - GUIDE_LOOKBACK_HOURS, ChronoUnit.HOURS))

    private val _recordDialog = composeState<RecordDialogState?>(null)
    val recordDialog: RecordDialogState? get() = _recordDialog.value

    /**
     * The shared record choreography ([RecordActions]); this tab surfaces the
     * outcome through the record dialog (Success carries the program name) and
     * reloads the guide on success so timer badges reflect the new timer.
     */
    private val recordActions = RecordActions(mediaRepository, scope) { outcome ->
        when (outcome) {
            is RecordOutcome.Requesting -> _recordDialog.value = RecordDialogState.Requesting
            is RecordOutcome.Success -> {
                _recordDialog.value = RecordDialogState.Success(outcome.request.program?.name)
                loadGuide()
            }
            is RecordOutcome.Error ->
                _recordDialog.value = RecordDialogState.Error(outcome.message ?: "Failed to create recording")
            RecordOutcome.Idle -> Unit
        }
    }

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

    private var autoRefreshJob: Job? = null

    /**
     * Recompute the cached grid snapshot from the current source data. The
     * CPU-heavy groupBy + per-channel filter + sort runs on [gridDispatcher];
     * the inputs are read and the snapshot published on the caller's context,
     * so state assignment order is unchanged.
     */
    private suspend fun rebuildGrid() {
        val channels = _channels.value
        val programs = _programs.value
        val windowStart = _windowStart.value
        val windowEnd = _windowEnd.value
        _gridData.value = withContext(gridDispatcher) {
            buildEpgGridData(
                channels = channels,
                programs = programs,
                windowStart = windowStart,
                windowEnd = windowEnd,
            )
        }
    }

    init {
        loadGuide()
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
        recordActions.recordOnce(pending)
    }

    fun dismissRecordDialog() { _recordDialog.value = null }

    /**
     * Starts the 5-minute guide auto-refresh loop. Tied to screen visibility
     * (STARTED) by the EPG screen via [stopAutoRefresh] on exit so refreshes
     * do not run while the screen sits in the back stack. Repeated calls
     * replace the previous loop instead of stacking another one.
     */
    fun startAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = launch {
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

    /** Stops the guide auto-refresh loop started by [startAutoRefresh]. */
    fun stopAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = null
    }

}
