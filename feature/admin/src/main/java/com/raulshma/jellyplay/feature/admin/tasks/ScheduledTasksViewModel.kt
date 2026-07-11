package com.raulshma.jellyplay.feature.admin.tasks

import android.util.Log
import com.raulshma.jellyplay.core.model.ScheduledTaskInfo
import com.raulshma.jellyplay.core.model.TaskState
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import javax.inject.Inject

/** Poll interval while at least one task is RUNNING — short enough for live progress. */
private const val POLL_INTERVAL_MS = 2_000L

data class ScheduledTasksState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val tasks: List<ScheduledTaskInfo> = emptyList(),
    val isRefreshing: Boolean = false,
)

@HiltViewModel
class ScheduledTasksViewModel @Inject constructor(
    private val apiClient: JellyfinApiClient,
) : JellyPlayViewModel() {

    private val _state = composeState(ScheduledTasksState())
    val state: ScheduledTasksState get() = _state.value

    private val hasRunningTasks = MutableStateFlow(false)

    init {
        loadTasks()
        startAutoRefresh()
    }

    fun loadTasks() {
        launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            fetchTasks()
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
            val result = apiClient.getScheduledTasks(isHidden = false)
            result.onSuccess { tasks ->
                _state.value = _state.value.copy(tasks = tasks)
                hasRunningTasks.value = tasks.any { it.state == TaskState.RUNNING }
            }.onFailure { e ->
                Log.e("ScheduledTasks", "Failed to fetch tasks", e)
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    private fun startAutoRefresh() {
        launch {
            // Re-check the current value on each iteration instead of capturing the
            // emitted `running` param: that snapshot would never change within a single
            // emission, so the loop would either never engage (if the task was still
            // IDLE at subscribe time) or run forever.
            while (currentCoroutineContext().isActive) {
                delay(POLL_INTERVAL_MS)
                if (hasRunningTasks.value) fetchTasks()
            }
        }
    }

    fun startTask(taskId: String) {
        launch {
            // Optimistically engage the auto-refresh loop — the server may take longer
            // than one fetch to flip the task to RUNNING, and a single fetch landing on
            // IDLE would otherwise leave hasRunningTasks false and polling off.
            hasRunningTasks.value = true
            apiClient.startTask(taskId)
            fetchTasks()
        }
    }

    fun cancelTask(taskId: String) {
        launch {
            apiClient.cancelTask(taskId)
            fetchTasks()
        }
    }
}
