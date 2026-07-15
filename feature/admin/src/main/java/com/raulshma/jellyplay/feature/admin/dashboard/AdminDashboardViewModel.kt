package com.raulshma.jellyplay.feature.admin.dashboard

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.ItemCounts
import com.raulshma.jellyplay.core.model.ScheduledTaskInfo
import com.raulshma.jellyplay.core.model.SessionInfo
import com.raulshma.jellyplay.core.model.SystemInfo
import com.raulshma.jellyplay.core.model.TaskState
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import javax.inject.Inject

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
)

@HiltViewModel
class AdminDashboardViewModel @Inject constructor(
    private val apiClient: JellyfinApiClient,
) : JellyPlayViewModel() {

    private val _uiState = stateFlow(AdminDashboardState())
    val uiState: StateFlow<AdminDashboardState> = _uiState.flow

    private val hasRunningTasks = MutableStateFlow(false)

    init {
        loadDashboard()
    }

    fun loadDashboard() {
        launch {
            // Access control is enforced by AdminRouteContainer before this
            // screen is reached; the server still 403s as a backstop.
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                coroutineScope {
                    val sysInfoDeferred = async { apiClient.getSystemInfo().getOrNull() }
                    val countsDeferred = async { apiClient.getItemCounts().getOrNull() }
                    val sessionsDeferred = async { apiClient.getSessions().getOrNull() }
                    val activityDeferred = async { apiClient.getActivityLogEntries(limit = 10).getOrNull() }
                    val tasksDeferred = async { apiClient.getScheduledTasks().getOrNull() }

                    val sysInfo = sysInfoDeferred.await()
                    val counts = countsDeferred.await()
                    val sessions = sessionsDeferred.await() ?: emptyList()
                    val allTasks = tasksDeferred.await() ?: emptyList()
                    val activity = activityDeferred.await() ?: emptyList()

                    val running = allTasks.filter { task -> task.state == TaskState.RUNNING }
                    hasRunningTasks.value = running.isNotEmpty()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            systemInfo = sysInfo,
                            itemCounts = counts,
                            runningTasks = running,
                            sessions = sessions,
                            recentActivity = activity,
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
        startAutoRefresh()
    }

    private var refreshJob: kotlinx.coroutines.Job? = null

    private fun startAutoRefresh() {
        refreshJob?.cancel()
        refreshJob = launch {
            hasRunningTasks.collectLatest { running ->
                while (running) {
                    delay(15000)
                    refreshRunningTasks()
                }
            }
        }
    }

    private suspend fun refreshRunningTasks() {
        val result = apiClient.getScheduledTasks()
        val tasks = result.getOrNull() ?: return
        val running = tasks.filter { task -> task.state == TaskState.RUNNING }
        hasRunningTasks.value = running.isNotEmpty()
        _uiState.update {
            it.copy(runningTasks = running)
        }
    }

    fun restartServer() {
        launch {
            _uiState.update { it.copy(isRestarting = true) }
            apiClient.restartServer()
            delay(3000)
            _uiState.update { it.copy(isRestarting = false) }
            loadDashboard()
        }
    }

    fun shutdownServer() {
        launch {
            _uiState.update { it.copy(isShuttingDown = true) }
            apiClient.shutdownServer()
            _uiState.update { it.copy(isShuttingDown = false) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        refreshJob?.cancel()
    }
}
