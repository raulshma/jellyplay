package com.raulshma.jellyplay.feature.admin.dashboard

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.data.repository.AdminRepository
import com.raulshma.jellyplay.core.model.ItemCounts
import com.raulshma.jellyplay.core.model.ScheduledTaskInfo
import com.raulshma.jellyplay.core.model.SessionInfo
import com.raulshma.jellyplay.core.model.SystemInfo
import com.raulshma.jellyplay.core.model.TaskState
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

@Immutable
data class AdminDashboardState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val systemInfo: SystemInfo? = null,
    val itemCounts: ItemCounts? = null,
    val runningTasks: List<ScheduledTaskInfo> = emptyList(),
    val sessions: List<SessionInfo> = emptyList(),
    val recentActivity: List<com.raulshma.jellyplay.core.model.ActivityLogEntry> = emptyList(),
    val isRestarting: Boolean = false,
    val isShuttingDown: Boolean = false,
    val libraryScanState: LibraryScanState = LibraryScanState.Idle,
    /** Session awaiting a stop confirmation, if any. Null hides the dialog. */
    val pendingStopSession: SessionInfo? = null,
    /** True while a stop request is in flight (disables the confirm button). */
    val isStoppingSession: Boolean = false,
)

class AdminDashboardViewModel(
    private val adminRepository: AdminRepository,
) : JellyPlayViewModel() {

    private val _uiState = stateFlow(AdminDashboardState())
    val uiState: StateFlow<AdminDashboardState> = _uiState.flow

    /**
     * Deadline (via [System.currentTimeMillis]) up to which an IDLE scan task
     * is treated as RUNNING. Set when the user triggers a scan: the server
     * takes a moment to flip the scheduled task from IDLE to RUNNING after
     * [apiClient.startTask], and a WS push landing on IDLE during that window
     * would otherwise collapse the optimistic [LibraryScanState.Running] and
     * hide the progress UI. Cleared as soon as the WS confirms RUNNING, so a
     * later IDLE genuinely means the scan finished.
     */
    private val scanOptimisticUntilMs = java.util.concurrent.atomic.AtomicLong(0L)

    init {
        loadDashboard()
        observeScheduledTasks()
        observeScanLibraryTask()
    }

    /**
     * Tasks currently executing — shared by the REST seed and the WS push
     * below. Hidden tasks are excluded on both paths: the REST fetch asks for
     * all tasks (`isHidden = null`), so the filter must live here rather than
     * only in the WS collector, or a hidden RUNNING task from the seed would
     * vanish on the first WS push.
     */
    private fun List<ScheduledTaskInfo>.running() =
        filter { !it.isHidden && it.state == TaskState.RUNNING }

    fun loadDashboard() {
        launch {
            // Access control is enforced by AdminRouteContainer before this
            // screen is reached; the server still 403s as a backstop.
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val summary = adminRepository.getDashboardSummary().getOrThrow()

                val running = summary.tasks.running()
                // Seed the scan state from the initial REST snapshot so the
                // button reflects an in-progress scan before the first WS
                // push lands. Subsequent updates come from [observeScanLibraryTask].
                applyScanTask(summary.tasks.firstOrNull { it.key == KEY_SCAN_LIBRARY })
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        systemInfo = summary.systemInfo,
                        itemCounts = summary.itemCounts,
                        // Initial snapshot only: seeds the running-task card
                        // before the first WS push. While pushes land
                        // (replay = 1 + 1 Hz cadence) the socket owns this
                        // field — but a silent socket must not freeze it
                        // either; see [wsTasksFresh].
                        runningTasks = if (wsTasksFresh()) it.runningTasks else running,
                        sessions = summary.sessions,
                        recentActivity = summary.recentActivity,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    /**
     * True while WS task pushes are recent enough to be trusted over a REST
     * snapshot (1 Hz cadence; 30 s of silence means the socket is dead).
     * Freshness is judged from the channel's push timestamp, not from
     * collection: the shared flow replays its last emission to every new
     * collector, and that replay can be arbitrarily old.
     */
    private fun wsTasksFresh(): Boolean {
        val lastPushAtMs = adminRepository.scheduledTasksLastPushAtMs
        return lastPushAtMs != 0L &&
            System.currentTimeMillis() - lastPushAtMs < WS_PUSH_STALE_AFTER_MS
    }

    /**
     * Live running-task list from the WebSocket `ScheduledTasksInfo` push
     * (full list, 1 Hz, over the shared socket — the same single source of
     * truth [observeScanLibraryTask] reads). Replaces the prior 15 s REST
     * polling loop, which raced the socket as a second source of truth.
     *
     * [loadDashboard]'s REST snapshot seeds
     * [AdminDashboardState.runningTasks] only while pushes are landing
     * ([wsTasksFresh]); afterwards the socket owns the field, so a slow
     * REST response can never flicker the UI back to older data. Hidden
     * tasks are filtered out by [running], on both the REST seed and the
     * WS push.
     */
    private fun observeScheduledTasks() {
        launch {
            adminRepository.scheduledTasks.collect { tasks ->
                _uiState.update {
                    it.copy(
                        runningTasks = tasks.running(),
                    )
                }
            }
        }
    }

    fun restartServer() {
        launch {
            _uiState.update { it.copy(isRestarting = true) }
            val result = adminRepository.restartServer()
            if (result.isSuccess) {
                delay(3000)
                _uiState.update { it.copy(isRestarting = false, error = null) }
                loadDashboard()
            } else {
                _uiState.update {
                    it.copy(
                        isRestarting = false,
                        error = "Restart failed: ${result.exceptionOrNull()?.message ?: "unknown error"}",
                    )
                }
            }
        }
    }

    fun shutdownServer() {
        launch {
            _uiState.update { it.copy(isShuttingDown = true) }
            val result = adminRepository.shutdownServer()
            if (result.isSuccess) {
                _uiState.update { it.copy(isShuttingDown = false, error = null) }
            } else {
                _uiState.update {
                    it.copy(
                        isShuttingDown = false,
                        error = "Shutdown failed: ${result.exceptionOrNull()?.message ?: "unknown error"}",
                    )
                }
            }
        }
    }

    /** Opens the "stop this session's playback?" confirm dialog. */
    fun showStopSessionDialog(session: SessionInfo) {
        _uiState.update { it.copy(pendingStopSession = session) }
    }

    fun dismissStopSessionDialog() {
        if (!_uiState.value.isStoppingSession) {
            _uiState.update { it.copy(pendingStopSession = null) }
        }
    }

    /**
     * Stops active playback on the session currently pending confirmation.
     * Issues Jellyfin's play-state STOP command, then refreshes the dashboard
     * so the card reflects the stopped state.
     */
    fun stopSession() {
        val session = _uiState.value.pendingStopSession ?: return
        launch {
            _uiState.update { it.copy(isStoppingSession = true) }
            val result = adminRepository.stopSession(session.id)
            if (result.isSuccess) {
                _uiState.update {
                    it.copy(isStoppingSession = false, pendingStopSession = null, error = null)
                }
                loadDashboard()
            } else {
                _uiState.update {
                    it.copy(
                        isStoppingSession = false,
                        error = "Stop failed: ${result.exceptionOrNull()?.message ?: "unknown error"}",
                    )
                }
            }
        }
    }

    /**
     * Starts a media-library scan by triggering Jellyfin's built-in
     * "Scan media library" scheduled task ([KEY_SCAN_LIBRARY]). Live progress
     * then flows in over WebSocket via the repository's task channel
     * (the same `ScheduledTasksInfo` push jellyfin-web relies on).
     *
     * We set an optimistic grace window because the server takes a beat to
     * flip the task IDLE → RUNNING after the start request, and a WS push
     * landing in that window would report IDLE and collapse the UI. Once the
     * WS confirms RUNNING the window is cleared; a later IDLE means "done".
     */
    fun scanLibrary() {
        if (_uiState.value.libraryScanState is LibraryScanState.Running) return
        scanOptimisticUntilMs.set(System.currentTimeMillis() + scanOptimisticGraceMs)
        launch {
            _uiState.update { it.copy(libraryScanState = LibraryScanState.Running(progress = null)) }
            adminRepository.startLibraryScan()
        }
    }

    /**
     * Live library-scan state from the WebSocket `ScheduledTasksInfo` push.
     * Replaces the prior REST polling loop, which could not deliver progress
     * updates faster than its 2s interval and raced the server's IDLE→RUNNING
     * flip.
     */
    private fun observeScanLibraryTask() {
        launch {
            adminRepository.libraryScanTask.collect { task -> applyScanTask(task) }
        }
    }

    /**
     * Reflects the "Scan media library" task's [ScheduledTaskInfo] into
     * [libraryScanState], honoring the optimistic window started by [scanLibrary].
     */
    private fun applyScanTask(scanTask: ScheduledTaskInfo?) {
        // Server has actually started running — no more optimistic hold.
        if (scanTask?.state == TaskState.RUNNING) {
            scanOptimisticUntilMs.set(0L)
        }
        val withinOptimisticWindow = scanOptimisticUntilMs.get() != 0L &&
            System.currentTimeMillis() < scanOptimisticUntilMs.get()
        val nextState = when {
            scanTask?.state == TaskState.RUNNING ->
                LibraryScanState.Running(progress = scanTask.currentProgressPercentage)
            // Server still IDLE but within the grace window following a user
            // tap: preserve the optimistic Running(null) state.
            withinOptimisticWindow -> LibraryScanState.Running(progress = null)
            else -> LibraryScanState.Idle
        }
        // If the grace window expired without ever observing RUNNING, give up
        // the optimistic hold so the UI can settle to Idle.
        if (nextState is LibraryScanState.Idle) {
            scanOptimisticUntilMs.set(0L)
        }
        _uiState.update { it.copy(libraryScanState = nextState) }
    }

    private companion object {
        // Jellyfin scheduled-task key for "Scan media library".
        const val KEY_SCAN_LIBRARY = "RefreshLibrary"

        // WS task pushes arrive at 1 Hz; past this much silence the socket
        // is considered dead and REST snapshots may refresh runningTasks
        // again (see [wsTasksFresh]).
        private const val WS_PUSH_STALE_AFTER_MS = 30_000L

        // How long to keep the optimistic Running state after the user taps
        // "Scan Library", waiting for the server to flip the task to RUNNING.
        // Generous enough to cover startTask latency + the IDLE→RUNNING flip,
        // short enough to recover the UI if the start request silently failed.
        const val scanOptimisticGraceMs = 15_000L
    }
}

/**
 * State of the media-library scan surfaced on the dashboard hero card.
 */
@Immutable
sealed interface LibraryScanState {
    data object Idle : LibraryScanState
    data class Running(val progress: Double?) : LibraryScanState
}
