package com.raulshma.jellyplay.feature.admin.tasks

import android.util.Log
import com.raulshma.jellyplay.core.model.ScheduledTaskInfo
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
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

    private var refreshJob: kotlinx.coroutines.Job? = null

    init {
        loadTasks()
    }

    fun loadTasks() {
        launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            fetchTasks()
            _state.value = _state.value.copy(isLoading = false)
        }
        startAutoRefresh()
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
            }.onFailure { e ->
                Log.e("ScheduledTasks", "Failed to fetch tasks", e)
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    private fun startAutoRefresh() {
        refreshJob?.cancel()
        refreshJob = launch {
            while (true) {
                delay(3000)
                fetchTasks()
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

    override fun onCleared() {
        super.onCleared()
        refreshJob?.cancel()
    }
}
