package com.raulshma.jellyplay.feature.admin.logs

import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import com.raulshma.jellyplay.core.model.ActivityLogEntry
import com.raulshma.jellyplay.core.model.LogFile
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.atomic.AtomicInteger
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
    private val serverIdentityStore: ServerIdentityStore,
) : JellyPlayViewModel() {

    private val _state = composeState(LogsState())
    val state: LogsState get() = _state.value

    private val _liveEvents = MutableSharedFlow<ActivityLogEntry>(extraBufferCapacity = 64)
    val liveEvents: SharedFlow<ActivityLogEntry> = _liveEvents.asSharedFlow()

    private companion object {
        /** Reusable lenient Json — hoisted out of the per-WebSocket-message hot path. */
        val JSON = Json { ignoreUnknownKeys = true }

        const val MAX_LIVE_ENTRIES = 200

        // Reconnect backoff for the live-stream socket. Mirrors the shared
        // JellyfinWebSocketClient: exponential backoff for a few attempts, then
        // give up and fall back to REST polling.
        const val MAX_RECONNECT_ATTEMPTS = 5
    }

    private var webSocket: WebSocket? = null
    private var pollingJob: Job? = null
    private var liveCollectJob: Job? = null
    private var logFilePollingJob: Job? = null
    private var reconnectJob: Job? = null
    private val reconnectAttempts = AtomicInteger(0)

    private val liveEntriesBuffer = ArrayDeque<ActivityLogEntry>(MAX_LIVE_ENTRIES)
    private val activityEntriesBuffer = ArrayDeque<ActivityLogEntry>(MAX_LIVE_ENTRIES)
    private val liveEntryIdsBuffer = LinkedHashSet<Long>()

    init {
        loadInitialData()
    }

    fun loadInitialData() {
        launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val logFilesResult = apiClient.getLogFiles()
                val activityResult = apiClient.getActivityLogEntries(limit = 50)
                val entries = activityResult.getOrNull() ?: emptyList()
                activityEntriesBuffer.clear()
                activityEntriesBuffer.addAll(entries)
                _state.value = _state.value.copy(
                    logFiles = logFilesResult.getOrNull() ?: emptyList(),
                    activityEntries = activityEntriesBuffer.toList(),
                    isLoading = false,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message, isLoading = false)
            }
        }
    }

    fun loadLogFile(fileName: String) {
        logFilePollingJob?.cancel()
        _state.value = _state.value.copy(
            isLoadingLogContent = true,
            selectedLogFileName = fileName,
            selectedLogFileContent = null,
            selectedLogFileLines = emptyList(),
            isLogPollingActive = true
        )
        logFilePollingJob = launch {
            val initialResult = apiClient.getLogFileContent(fileName)
            initialResult.onSuccess { content ->
                val lines = content.lines().mapIndexed { index, text ->
                    LogLine(index = index, text = text, isNew = false, addedTime = 0L)
                }
                _state.value = _state.value.copy(
                    selectedLogFileContent = content,
                    selectedLogFileLines = lines,
                    isLoadingLogContent = false
                )
            }.onFailure { e ->
                _state.value = _state.value.copy(
                    error = e.message,
                    isLoadingLogContent = false
                )
            }

            while (_state.value.selectedLogFileName == fileName) {
                delay(5000)
                if (_state.value.isLogPollingActive) {
                    val result = apiClient.getLogFileContent(fileName)
                    result.onSuccess { content ->
                        val oldLines = _state.value.selectedLogFileLines
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
                            _state.value = _state.value.copy(
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
        _state.value = _state.value.copy(isLogPollingActive = !_state.value.isLogPollingActive)
    }

    fun clearSelectedLogFile() {
        logFilePollingJob?.cancel()
        logFilePollingJob = null
        _state.value = _state.value.copy(
            selectedLogFileContent = null,
            selectedLogFileName = null,
            selectedLogFileLines = emptyList(),
            isLogPollingActive = true
        )
    }

    fun selectTab(index: Int) {
        _state.value = _state.value.copy(selectedTabIndex = index)
    }

    fun startLiveStream() {
        if (apiClient.getServerUrl() == null || apiClient.getAccessToken() == null) return
        _state.value = _state.value.copy(
            isLiveStreamActive = true,
            liveEntries = emptyList(),
        )
        liveEntriesBuffer.clear()

        liveCollectJob?.cancel()
        liveCollectJob = launch {
            liveEvents.collect { entry ->
                if (entry.id in liveEntryIdsBuffer) return@collect
                liveEntryIdsBuffer.add(entry.id)
                liveEntriesBuffer.addFirst(entry)
                activityEntriesBuffer.addFirst(entry)
                while (liveEntriesBuffer.size > MAX_LIVE_ENTRIES) {
                    liveEntriesBuffer.removeLast()
                }
                while (activityEntriesBuffer.size > MAX_LIVE_ENTRIES) {
                    activityEntriesBuffer.removeLast()
                }
                _state.value = _state.value.copy(
                    liveEntries = liveEntriesBuffer.toList(),
                    liveEntryIds = liveEntryIdsBuffer.toSet(),
                    activityEntries = activityEntriesBuffer.toList(),
                )
            }
        }

        // Opening the socket is async because resolving the stable device id
        // hits DataStore. Reconnect attempts reset here so the first connect
        // isn't biased by a prior session's failure count.
        reconnectAttempts.set(0)
        launch { connectWebSocket() }
    }

    /**
     * Opens the live-log WebSocket. Unlike the prior implementation:
     *  - the access token is sent in the `X-Emby-Token` header (already
     *    redacted by the app's OkHttp logger) instead of leaked as a
     *    `?api_key=` query param,
     *  - the device id is the app's stable id (matches the app-lifetime
     *    socket owned by MainViewModel) rather than the literal
     *    `"JellyPlayAdmin"`, so the server doesn't register a phantom
     *    second session for the same user,
     *  - [onFailure] triggers [scheduleReconnect] (exponential backoff, then
     *    REST polling) instead of a one-shot fallback.
     */
    private suspend fun connectWebSocket() {
        if (!_state.value.isLiveStreamActive) return
        val serverUrl = apiClient.getServerUrl() ?: return
        val token = apiClient.getAccessToken() ?: return
        val device = serverIdentityStore.ensureDeviceId()

        val wsUrl = serverUrl.trim().trimEnd('/')
            .replace("https://", "wss://")
            .replace("http://", "ws://") +
            "/socket?deviceId=${java.net.URLEncoder.encode(device, "UTF-8")}" +
            "&deviceName=JellyPlay&client=JellyPlay"

        val request = Request.Builder()
            .url(wsUrl)
            // Header (not query param) so the token never appears in URLs/logs.
            // This is the same header Jellyfin's auth interceptor uses for REST.
            .header("X-Emby-Token", token)
            .build()
        webSocket?.cancel()
        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                reconnectAttempts.set(0)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val obj = JSON.parseToJsonElement(text).jsonObject
                    val messageType = obj["MessageType"]?.jsonPrimitive?.contentOrNull
                    if (messageType == "ActivityLogEntry") {
                        val dataStr = obj["MessageData"].toString()
                        val entry = JSON.decodeFromString<ActivityLogEntry>(dataStr)
                        _liveEvents.tryEmit(entry)
                    }
                } catch (_: Exception) {}
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                scheduleReconnect()
            }
        })
    }

    /**
     * Exponential-backoff reconnect (mirrors the shared JellyfinWebSocketClient):
     * up to [MAX_RECONNECT_ATTEMPTS] tries with growing delay, then gives up and
     * falls back to REST polling. Cancels any in-flight reconnect first so a
     * rapid failure burst can't stack deferred connects.
     */
    private fun scheduleReconnect() {
        if (!_state.value.isLiveStreamActive) return
        val attempts = reconnectAttempts.incrementAndGet()
        if (attempts > MAX_RECONNECT_ATTEMPTS) {
            startPollingFallback()
            return
        }
        val delayMs = (1000L * (1L shl (attempts - 1).coerceAtMost(4))).coerceAtMost(30_000L)
        reconnectJob?.cancel()
        reconnectJob = launch {
            delay(delayMs)
            connectWebSocket()
        }
    }

    fun stopLiveStream() {
        webSocket?.close(1000, "Stopped")
        webSocket = null
        reconnectJob?.cancel()
        reconnectJob = null
        reconnectAttempts.set(MAX_RECONNECT_ATTEMPTS + 1)
        pollingJob?.cancel()
        pollingJob = null
        liveCollectJob?.cancel()
        liveCollectJob = null
        _state.value = _state.value.copy(isLiveStreamActive = false)
    }

    private fun startPollingFallback() {
        pollingJob?.cancel()
        pollingJob = launch {
            val knownIds = _state.value.activityEntries.map { it.id }.toMutableSet()
            while (true) {
                delay(5000)
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
        launch {
            val currentSize = _state.value.activityEntries.size
            val result = apiClient.getActivityLogEntries(startIndex = currentSize, limit = 50)
            result.onSuccess { more ->
                activityEntriesBuffer.addAll(more)
                _state.value = _state.value.copy(
                    activityEntries = activityEntriesBuffer.toList(),
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        webSocket?.close(1000, "ViewModel cleared")
        reconnectJob?.cancel()
        pollingJob?.cancel()
        liveCollectJob?.cancel()
        logFilePollingJob?.cancel()
    }
}
