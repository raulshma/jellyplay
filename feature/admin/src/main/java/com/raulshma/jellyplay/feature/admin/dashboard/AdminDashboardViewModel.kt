package com.raulshma.jellyplay.feature.admin.dashboard

import com.raulshma.jellyplay.core.data.repository.AuthRepository
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
) : JellyPlayViewModel() {

    private val _state = composeState(AdminDashboardState())
    val state: AdminDashboardState get() = _state.value

    private val _isAdmin = composeState(false)
    val isAdmin: Boolean get() = _isAdmin.value

    init {
        launch {
            authRepository.currentUser.collect { user ->
                _isAdmin.value = user?.isAdmin == true
            }
        }
        loadDashboard()
    }

    fun loadDashboard() {
        launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
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

                    _state.value = _state.value.copy(
                        isLoading = false,
                        systemInfo = sysInfo,
                        itemCounts = counts,
                        runningTasks = allTasks.filter { it.state == TaskState.RUNNING },
                        sessions = sessions,
                        recentActivity = activityDeferred.await() ?: emptyList(),
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
        startAutoRefresh()
    }

    private var refreshJob: kotlinx.coroutines.Job? = null

    private fun startAutoRefresh() {
        refreshJob?.cancel()
        refreshJob = launch {
            while (true) {
                delay(15000)
                refreshRunningTasks()
            }
        }
    }

    private suspend fun refreshRunningTasks() {
        val result = apiClient.getScheduledTasks()
        val tasks = result.getOrNull() ?: return
        _state.value = _state.value.copy(
            runningTasks = tasks.filter { it.state == TaskState.RUNNING },
        )
    }

    fun restartServer() {
        launch {
            _state.value = _state.value.copy(isRestarting = true)
            apiClient.restartServer()
            delay(3000)
            _state.value = _state.value.copy(isRestarting = false)
            loadDashboard()
        }
    }

    fun shutdownServer() {
        launch {
            _state.value = _state.value.copy(isShuttingDown = true)
            apiClient.shutdownServer()
            _state.value = _state.value.copy(isShuttingDown = false)
        }
    }

    override fun onCleared() {
        super.onCleared()
        refreshJob?.cancel()
    }
}
