package com.raulshma.jellyplay.feature.admin.dashboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.model.ItemCounts
import com.raulshma.jellyplay.core.model.ScheduledTaskInfo
import com.raulshma.jellyplay.core.model.SessionInfo
import com.raulshma.jellyplay.core.model.SystemInfo
import com.raulshma.jellyplay.core.model.TaskState
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

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
)

@HiltViewModel
class AdminDashboardViewModel @Inject constructor(
    private val apiClient: JellyfinApiClient,
    private val authRepository: AuthRepository,
) : ViewModel() {

    var state by mutableStateOf(AdminDashboardState())
        private set

    var isAdmin by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            authRepository.currentUser.collect { user ->
                isAdmin = user?.isAdmin == true
            }
        }
        loadDashboard()
    }

    fun loadDashboard() {
        viewModelScope.launch {
            state = state.copy(isLoading = true, error = null)
            try {
                val systemInfoResult = apiClient.getSystemInfo()
                val countsResult = apiClient.getItemCounts()
                val tasksResult = apiClient.getScheduledTasks()
                val sessionsResult = apiClient.getSessions()
                val activityResult = apiClient.getActivityLogEntries(limit = 10)

                val sysInfo = systemInfoResult.getOrNull()
                val counts = countsResult.getOrNull()
                val allTasks = tasksResult.getOrNull() ?: emptyList()
                val sessions = sessionsResult.getOrNull() ?: emptyList()

                state = state.copy(
                    isLoading = false,
                    systemInfo = sysInfo,
                    itemCounts = counts,
                    runningTasks = allTasks.filter { it.state == TaskState.RUNNING },
                    sessions = sessions,
                    recentActivity = activityResult.getOrNull() ?: emptyList(),
                )
            } catch (e: Exception) {
                state = state.copy(isLoading = false, error = e.message)
            }
        }
        startAutoRefresh()
    }

    private var refreshJob: kotlinx.coroutines.Job? = null

    private fun startAutoRefresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            while (true) {
                delay(5000)
                refreshRunningTasks()
            }
        }
    }

    private suspend fun refreshRunningTasks() {
        val result = apiClient.getScheduledTasks()
        val tasks = result.getOrNull() ?: return
        state = state.copy(
            runningTasks = tasks.filter { it.state == TaskState.RUNNING },
        )
    }

    fun restartServer() {
        viewModelScope.launch {
            state = state.copy(isRestarting = true)
            apiClient.restartServer()
            delay(3000)
            state = state.copy(isRestarting = false)
            loadDashboard()
        }
    }

    fun shutdownServer() {
        viewModelScope.launch {
            state = state.copy(isShuttingDown = true)
            apiClient.shutdownServer()
            state = state.copy(isShuttingDown = false)
        }
    }

    override fun onCleared() {
        super.onCleared()
        refreshJob?.cancel()
    }
}
