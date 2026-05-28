package com.raulshma.jellyplay.feature.admin.tasks

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raulshma.jellyplay.core.model.ScheduledTaskInfo
import com.raulshma.jellyplay.core.model.TaskState
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
) : ViewModel() {

    var state by mutableStateOf(ScheduledTasksState())
        private set

    private var refreshJob: kotlinx.coroutines.Job? = null

    init {
        loadTasks()
    }

    fun loadTasks() {
        viewModelScope.launch {
            state = state.copy(isLoading = true, error = null)
            fetchTasks()
            state = state.copy(isLoading = false)
        }
        startAutoRefresh()
    }

    fun refresh() {
        viewModelScope.launch {
            state = state.copy(isRefreshing = true)
            fetchTasks()
            state = state.copy(isRefreshing = false)
        }
    }

    private fun fetchTasks() {
        viewModelScope.launch {
            val result = apiClient.getScheduledTasks(isHidden = false)
            result.onSuccess { tasks ->
                state = state.copy(tasks = tasks)
            }.onFailure { e ->
                Log.e("ScheduledTasks", "Failed to fetch tasks", e)
                state = state.copy(error = e.message)
            }
        }
    }

    private fun startAutoRefresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            while (true) {
                delay(3000)
                fetchTasks()
            }
        }
    }

    fun startTask(taskId: String) {
        viewModelScope.launch {
            apiClient.startTask(taskId)
            delay(500)
            fetchTasks()
        }
    }

    fun cancelTask(taskId: String) {
        viewModelScope.launch {
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
