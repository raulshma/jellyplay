package com.raulshma.jellyplay.feature.admin.tasks

import com.raulshma.jellyplay.core.data.log.Log
import com.raulshma.jellyplay.core.data.repository.AdminRepository
import com.raulshma.jellyplay.core.model.ScheduledTaskInfo
import com.raulshma.jellyplay.core.model.TaskState
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.feature.admin.AdminLoad
import kotlinx.coroutines.flow.MutableStateFlow

data class ScheduledTasksState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val tasks: List<ScheduledTaskInfo> = emptyList(),
    val isRefreshing: Boolean = false,
)

class ScheduledTasksViewModel(
    private val adminRepository: AdminRepository,
) : JellyPlayViewModel() {

    private val _state = composeState(ScheduledTasksState())
    val state: ScheduledTasksState get() = _state.value

    private val hasRunningTasks = MutableStateFlow(false)

    init {
        loadTasks()
        observeRealtimeTasks()
    }

    fun loadTasks() {
        launch {
            loadTasksLadder(
                start = { _state.value = _state.value.copy(isLoading = true, error = null) },
            )
            // Final-update settle, the VM's legacy shape: the arms never touch
            // the loading flag. Declared timing unification (see AdminLoad):
            // the old ladder fired the fetch fire-and-forget, so this flag
            // cleared before the fetch landed; the folded ladder awaits it.
            _state.value = _state.value.copy(isLoading = false)
        }
    }

    fun refresh() {
        launch {
            _state.value = _state.value.copy(isRefreshing = true)
            fetchTasks()
            _state.value = _state.value.copy(isRefreshing = false)
        }
    }

    private fun fetchTasks() {
        launch {
            // No start flags: refresh raises its own isRefreshing around this
            // call (and awaits nothing — its legacy fire-and-forget shape).
            loadTasksLadder(start = { })
        }
    }

    /** The shared (fetch, arms) pair both task ladders dispatch. */
    private suspend fun loadTasksLadder(start: () -> Unit) {
        AdminLoad.load(
            start = start,
            fetch = { adminRepository.getScheduledTasks(isHidden = false) },
            onSuccess = ::applyTasks,
            onFailure = ::logTasksFailure,
        )
    }

    private fun applyTasks(tasks: List<ScheduledTaskInfo>) {
        _state.value = _state.value.copy(tasks = tasks)
        hasRunningTasks.value = tasks.any { it.state == TaskState.RUNNING }
    }

    private fun logTasksFailure(e: Throwable) {
        Log.e("ScheduledTasks", "Failed to fetch tasks", e)
        _state.value = _state.value.copy(error = e.message)
    }

    /**
     * Live task updates over WebSocket (`ScheduledTasksInfo` push). Replaces the
     * prior REST polling loop, which could not deliver progress faster than its
     * 2s interval.
     *
     * Rather than wholesale-replacing the task list (which would discard fields
     * a given server build may omit from the WS payload, causing the "last run"
     * row and trigger chips to flicker off), each WS push is merged onto the
     * REST-loaded snapshot by [ScheduledTaskInfo.key]: WS values win for the
     * live fields (state, progress), and REST values are retained for any field
     * the WS push leaves blank. This mirrors how jellyfin-web's cache behaves
     * in practice — the server's WS TaskInfo is normally complete, but merging
     * is robust against partial pushes across server versions.
     *
     * Hidden tasks are filtered out to match the REST `isHidden = false` fetch.
     */
    private fun observeRealtimeTasks() {
        launch {
            adminRepository.scheduledTasks.collect { wsTasks ->
                val existingByKey = _state.value.tasks.associateBy { it.key }
                val merged = wsTasks
                    .filter { !it.isHidden }
                    .map { ws ->
                        val rest = existingByKey[ws.key]
                        if (rest == null) {
                            ws
                        } else {
                            rest.copy(
                                state = ws.state,
                                currentProgressPercentage = ws.currentProgressPercentage,
                                // Refresh last-run + name/description/category only when
                                // the WS push actually carries a value; otherwise keep
                                // the REST snapshot's richer data.
                                lastExecutionResult = ws.lastExecutionResult ?: rest.lastExecutionResult,
                                name = ws.name.ifBlank { rest.name },
                                description = ws.description ?: rest.description,
                                category = ws.category ?: rest.category,
                                triggers = ws.triggers.ifEmpty { rest.triggers },
                            )
                        }
                    }
                _state.value = _state.value.copy(tasks = merged)
                hasRunningTasks.value = merged.any { it.state == TaskState.RUNNING }
            }
        }
    }

    fun startTask(taskId: String) {
        launch {
            adminRepository.startTask(taskId)
            // The WS push overlays the new state within ~1s; refresh once to
            // populate immediately rather than waiting for the next push.
            fetchTasks()
        }
    }

    fun cancelTask(taskId: String) {
        launch {
            adminRepository.cancelTask(taskId)
            fetchTasks()
        }
    }
}

