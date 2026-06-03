package com.raulshma.jellyplay.feature.admin.logs

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raulshma.jellyplay.core.model.ActivityLogEntry
import com.raulshma.jellyplay.core.model.LogFile
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import javax.inject.Inject

data class LogLine(
    val index: Int,
    val text: String,
    val isNew: Boolean = false,
    val addedTime: Long = 0L,
)

data class LogsState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val logFiles: List<LogFile> = emptyList(),
    val activityEntries: List<ActivityLogEntry> = emptyList(),
    val activityTotalCount: Int = 0,
    val selectedLogFileContent: String? = null,
    val selectedLogFileName: String? = null,
    val selectedLogFileLines: List<LogLine> = emptyList(),
    val isLogPollingActive: Boolean = true,
    val isLoadingLogContent: Boolean = false,
    val selectedTabIndex: Int = 0,
    val isLiveStreamActive: Boolean = false,
    val liveEntries: List<ActivityLogEntry> = emptyList(),
    val liveEntryIds: Set<Long> = emptySet(),
)

@HiltViewModel
class LogsViewModel @Inject constructor(
    private val apiClient: JellyfinApiClient,
    private val okHttpClient: OkHttpClient,
) : ViewModel() {

    var state by mutableStateOf(LogsState())
        private set

    private val _liveEvents = MutableSharedFlow<ActivityLogEntry>(extraBufferCapacity = 64)
    val liveEvents: SharedFlow<ActivityLogEntry> = _liveEvents

    private var webSocket: WebSocket? = null
    private var pollingJob: Job? = null
    private var liveCollectJob: Job? = null
    private var logFilePollingJob: Job? = null

    init {
        loadInitialData()
    }

    fun loadInitialData() {
        viewModelScope.launch {
            state = state.copy(isLoading = true, error = null)
            try {
                val logFilesResult = apiClient.getLogFiles()
                val activityResult = apiClient.getActivityLogEntries(limit = 50)
                state = state.copy(
                    logFiles = logFilesResult.getOrNull() ?: emptyList(),
                    activityEntries = activityResult.getOrNull() ?: emptyList(),
                    isLoading = false,
                )
            } catch (e: Exception) {
                state = state.copy(error = e.message, isLoading = false)
            }
        }
    }

    fun loadLogFile(fileName: String) {
        logFilePollingJob?.cancel()
        state = state.copy(
            isLoadingLogContent = true,
            selectedLogFileName = fileName,
            selectedLogFileContent = null,
            selectedLogFileLines = emptyList(),
            isLogPollingActive = true
        )
        logFilePollingJob = viewModelScope.launch {
            // First load
            val initialResult = apiClient.getLogFileContent(fileName)
            initialResult.onSuccess { content ->
                val lines = content.lines().mapIndexed { index, text ->
                    LogLine(index = index, text = text, isNew = false, addedTime = 0L)
                }
                state = state.copy(
                    selectedLogFileContent = content,
                    selectedLogFileLines = lines,
                    isLoadingLogContent = false
                )
            }.onFailure { e ->
                state = state.copy(
                    error = e.message,
                    isLoadingLogContent = false
                )
            }

            // Polling loop
            while (state.selectedLogFileName == fileName) {
                delay(3000)
                if (state.isLogPollingActive) {
                    val result = apiClient.getLogFileContent(fileName)
                    result.onSuccess { content ->
                        val oldLines = state.selectedLogFileLines
                        val newLinesText = content.lines()
                        
                        if (newLinesText != oldLines.map { it.text }) {
                            val updatedLines = newLinesText.mapIndexed { index, text ->
                                if (index < oldLines.size && oldLines[index].text == text) {
                                    oldLines[index]
                                } else {
                                    LogLine(
                                        index = index,
                                        text = text,
                                        isNew = index >= oldLines.size,
                                        addedTime = if (index >= oldLines.size) System.currentTimeMillis() else 0L
                                    )
                                }
                            }
                            state = state.copy(
                                selectedLogFileContent = content,
                                selectedLogFileLines = updatedLines
                            )
                        }
                    }
                }
            }
        }
    }

    fun toggleLogPolling() {
        state = state.copy(isLogPollingActive = !state.isLogPollingActive)
    }

    fun clearSelectedLogFile() {
        logFilePollingJob?.cancel()
        logFilePollingJob = null
        state = state.copy(
            selectedLogFileContent = null,
            selectedLogFileName = null,
            selectedLogFileLines = emptyList(),
            isLogPollingActive = true
        )
    }

    fun selectTab(index: Int) {
        state = state.copy(selectedTabIndex = index)
    }

    fun startLiveStream() {
        val serverUrl = apiClient.getServerUrl() ?: return
        val token = apiClient.getAccessToken() ?: return
        state = state.copy(isLiveStreamActive = true, liveEntries = emptyList())

        liveCollectJob?.cancel()
        liveCollectJob = viewModelScope.launch {
            liveEvents.collect { entry ->
                state = state.copy(
                    liveEntries = (listOf(entry) + state.liveEntries).take(200),
                    liveEntryIds = state.liveEntryIds + entry.id,
                    activityEntries = (listOf(entry) + state.activityEntries).take(200),
                )
            }
        }

        val wsUrl = serverUrl.replace("http", "ws") +
                "/socket?api_key=$token&deviceId=JellyPlayAdmin"

        val request = Request.Builder().url(wsUrl).build()
        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = Json { ignoreUnknownKeys = true }
                    val obj = json.parseToJsonElement(text).jsonObject
                    val messageType = obj["MessageType"]?.jsonPrimitive?.contentOrNull
                    if (messageType == "ActivityLogEntry") {
                        val dataStr = obj["MessageData"].toString()
                        val entry = json.decodeFromString<ActivityLogEntry>(dataStr)
                        _liveEvents.tryEmit(entry)
                    }
                } catch (_: Exception) {}
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                startPollingFallback()
            }
        })
    }

    fun stopLiveStream() {
        webSocket?.close(1000, "Stopped")
        webSocket = null
        pollingJob?.cancel()
        pollingJob = null
        liveCollectJob?.cancel()
        liveCollectJob = null
        state = state.copy(isLiveStreamActive = false)
    }

    private fun startPollingFallback() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            val knownIds = state.activityEntries.map { it.id }.toMutableSet()
            while (true) {
                delay(3000)
                val result = apiClient.getActivityLogEntries(limit = 10)
                result.onSuccess { entries ->
                    val newEntries = entries.filter { it.id !in knownIds }
                    if (newEntries.isNotEmpty()) {
                        newEntries.forEach { entry ->
                            knownIds.add(entry.id)
                            _liveEvents.tryEmit(entry)
                        }
                    }
                }
            }
        }
    }

    fun loadMoreActivity() {
        viewModelScope.launch {
            val currentSize = state.activityEntries.size
            val result = apiClient.getActivityLogEntries(startIndex = currentSize, limit = 50)
            result.onSuccess { more ->
                state = state.copy(
                    activityEntries = state.activityEntries + more,
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        webSocket?.close(1000, "ViewModel cleared")
        pollingJob?.cancel()
        liveCollectJob?.cancel()
        logFilePollingJob?.cancel()
    }
}
