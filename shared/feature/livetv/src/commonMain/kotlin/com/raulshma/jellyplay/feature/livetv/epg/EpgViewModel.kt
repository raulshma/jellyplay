package com.raulshma.jellyplay.feature.livetv.epg

import com.raulshma.jellyplay.core.data.repository.LiveTvRepository
import com.raulshma.jellyplay.core.data.util.TimeSource
import com.raulshma.jellyplay.core.model.EpgGuide
import com.raulshma.jellyplay.core.model.LiveTvChannel
import com.raulshma.jellyplay.core.model.LiveTvProgram
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.feature.livetv.LIVE_TV_STALENESS_INTERVAL_MS
import com.raulshma.jellyplay.feature.livetv.components.RecordActions
import com.raulshma.jellyplay.feature.livetv.components.RecordDialogState
import com.raulshma.jellyplay.feature.livetv.components.RecordOutcome
import com.raulshma.jellyplay.feature.livetv.nowInstant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.temporal.ChronoUnit

private const val NOW_TICK_INTERVAL_MS: Long = 30 * 1000L
/** How far back from "now" the guide window extends (keeps recently-ended shows visible). */
private const val GUIDE_LOOKBACK_HOURS: Long = 2L
/** Total span of the guide window. Matches the 24h timeline used by the jellyfin-web guide. */
private const val GUIDE_WINDOW_HOURS: Long = 24L

/**
 * The standard guide fetch window for [now] — the ONE formula behind both the
 * boot defaults and every [EpgViewModel.fetchGuideIntoState] pass, so the two
 * can never drift: [GUIDE_LOOKBACK_HOURS] back over the [GUIDE_WINDOW_HOURS]
 * span.
 */
private fun guideWindow(now: Instant): Pair<Instant, Instant> =
    now.minus(GUIDE_LOOKBACK_HOURS, ChronoUnit.HOURS) to
        now.plus(GUIDE_WINDOW_HOURS - GUIDE_LOOKBACK_HOURS, ChronoUnit.HOURS)

class EpgViewModel(
    private val mediaRepository: LiveTvRepository,
    private val timeSource: TimeSource,
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
        emit(timeSource.nowInstant())
        while (true) {
            delay(NOW_TICK_INTERVAL_MS)
            emit(timeSource.nowInstant())
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), timeSource.nowInstant())

    /** Half-open window [start, end) covered by the current guide fetch. */
    private val initialWindow = guideWindow(timeSource.nowInstant())
    private val _windowStart = composeState(initialWindow.first)
    private val _windowEnd = composeState(initialWindow.second)

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
     * Serializes [rebuildGrid] passes. The grid computation runs on the
     * multi-threaded [gridDispatcher], so a user-triggered [loadGuide] can
     * overlap the auto-refresh loop; without the lock the older rebuild can
     * publish last and overwrite the newer snapshot, leaving [gridData]
     * stale against [channels]/[programs] until the next refresh.
     */
    private val rebuildGridMutex = Mutex()

    /**
     * Recompute the cached grid snapshot from the current source data. The
     * CPU-heavy groupBy + per-channel filter + sort runs on [gridDispatcher];
     * reading the inputs and publishing the snapshot happen inside
     * [rebuildGridMutex]. The lock is not fair — acquisition order need not
     * match invocation order — but each pass reads the source state at the
     * moment it holds the lock, so the last pass out always reads (and
     * publishes) the newest sources.
     */
    private suspend fun rebuildGrid() = rebuildGridMutex.withLock {
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

    /**
     * Fetches the guide for the standard window ([guideWindow] over the
     * injected clock) and, on success, publishes channels, programs, the
     * window bounds and the rebuilt grid. Shared by the user-triggered
     * [loadGuide] and the auto-refresh loop; callers own loading/error UX.
     */
    private suspend fun fetchGuideIntoState(): Result<EpgGuide> {
        val (start, end) = guideWindow(timeSource.nowInstant())
        return mediaRepository.getLiveTvGuide(startDateUtc = start.toString(), endDateUtc = end.toString(), limit = 100)
            .onSuccess { guide ->
                _channels.value = guide.channels
                _programs.value = guide.programs
                _windowStart.value = start
                _windowEnd.value = end
                rebuildGrid()
            }
    }

    fun loadGuide() {
        launch {
            _isLoading.value = true
            _error.value = null
            fetchGuideIntoState()
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
     * Starts the 5-minute guide auto-refresh loop
     * ([LIVE_TV_STALENESS_INTERVAL_MS]). Tied to screen visibility (STARTED)
     * by the EPG screen via [stopAutoRefresh] on exit so refreshes do not run
     * while the screen sits in the back stack. Repeated calls replace the
     * previous loop instead of stacking another one.
     */
    fun startAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = launch {
            while (true) {
                delay(LIVE_TV_STALENESS_INTERVAL_MS)
                fetchGuideIntoState()
            }
        }
    }

    /** Stops the guide auto-refresh loop started by [startAutoRefresh]. */
    fun stopAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = null
    }

}
