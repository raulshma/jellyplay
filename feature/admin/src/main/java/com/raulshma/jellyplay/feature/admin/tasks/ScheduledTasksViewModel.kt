package com.raulshma.jellyplay.feature.admin.tasks

import android.util.Log
import com.raulshma.jellyplay.core.model.ScheduledTaskInfo
import com.raulshma.jellyplay.core.model.TaskState
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

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
            hasRunningTasks.collect { running ->
                while (running) {
                    delay(3000)
                    fetchTasks()
                }
            }
        }
    }

    fun startTask(taskId: String) {
        launch {
            apiClient.startTask(taskId)
            delay(500)
            fetchTasks()
        }
    }

    fun cancelTask(taskId: String) {
        launch {
            apiClient.cancelTask(taskId)
            delay(500)
            fetchTasks()
        }
    }
}
