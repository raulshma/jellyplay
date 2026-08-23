package com.raulshma.jellyplay.feature.admin.logs

import com.raulshma.jellyplay.core.data.repository.AdminRepository
import com.raulshma.jellyplay.core.model.ActivityLogEntry
import com.raulshma.jellyplay.core.model.LogFile
import com.raulshma.jellyplay.core.model.trimToSize
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    private val adminRepository: AdminRepository,
) : JellyPlayViewModel() {

    private val _state = composeState(LogsState())
    val state: LogsState get() = _state.value

    private companion object {
        const val MAX_LIVE_ENTRIES = 200

        /** Cap for the live dedup id set — 2× the display buffer it guards. */
        const val MAX_LIVE_ENTRY_IDS = MAX_LIVE_ENTRIES * 2
    }

    private var liveCollectJob: Job? = null
    private var logFilePollingJob: Job? = null

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
                val logFilesResult = adminRepository.getLogFiles()
                val activityResult = adminRepository.getActivityLogEntries(limit = 50)
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
            // File metadata (size / modification date) drives the poll: when
            // unchanged, the multi-MB content re-download + lines() re-split +
            // diff are all skipped. Jellyfin logs are routinely multi-MB, so
            // this collapses the 5 s poll to one cheap list request.
            var lastSize = -1L
            var lastModified = ""
            suspend fun fetchMeta(): LogFile? =
                adminRepository.getLogFiles().getOrNull()
                    ?.firstOrNull { it.name == fileName }
            fetchMeta()?.let { lastSize = it.size; lastModified = it.dateModified }

            val initialResult = adminRepository.getLogFileContent(fileName)
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

            // 5s view-refresh polling of the selected file — view policy, owned here.
            while (_state.value.selectedLogFileName == fileName) {
                delay(5000)
                if (_state.value.isLogPollingActive) {
                    val meta = fetchMeta()
                    if (meta != null && meta.size == lastSize && meta.dateModified == lastModified) {
                        continue
                    }
                    val result = adminRepository.getLogFileContent(fileName)
                    result.onSuccess { content ->
                        if (meta != null) {
                            lastSize = meta.size
                            lastModified = meta.dateModified
                        }
                        val oldLines = _state.value.selectedLogFileLines
                        val newLinesText = content.lines()

                        // Append-only fast path: server logs only append, so
                        // when every previous line still lines up, reuse the
                        // existing LogLine instances and build only the new
                        // tail — the old path re-walked the whole (multi-MB)
                        // file and built a second full list just to compare.
                        // The prefix walk allocates nothing; non-append shapes
                        // (rotation, truncation, rewrite) fall through to the
                        // full re-diff.
                        val appendOnly = isAppendOf(oldLines, newLinesText)

                        when {
                            appendOnly && newLinesText.size == oldLines.size -> Unit
                            appendOnly -> {
                                val startIndex = oldLines.size
                                val now = System.currentTimeMillis()
                                val appended = newLinesText.subList(startIndex, newLinesText.size)
                                    .mapIndexed { i, text ->
                                        LogLine(
                                            index = startIndex + i,
                                            text = text,
                                            isNew = true,
                                            addedTime = now,
                                        )
                                    }
                                _state.value = _state.value.copy(
                                    selectedLogFileContent = content,
                                    selectedLogFileLines = oldLines + appended,
                                )
                            }
                            newLinesText != oldLines.map { it.text } -> {
                                val updatedLines = newLinesText.mapIndexed { index, text ->
                                    if (index < oldLines.size && oldLines[index].text == text) {
                                        oldLines[index]
                                    } else {
                                        LogLine(
                                            index = index,
                                            text = text,
                                            isNew = index >= oldLines.size,
                                            addedTime = if (index >= oldLines.size) System.currentTimeMillis() else 0L,
                                        )
                                    }
                                }
                                _state.value = _state.value.copy(
                                    selectedLogFileContent = content,
                                    selectedLogFileLines = updatedLines,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * True when [oldLines] is a line-for-line prefix of [newLinesText] — i.e.
     * the file only grew. Compares the full prefix rather than just the
     * boundary line so a same-size or partial rewrite (rotation) can't slip
     * through as an "append" and leave stale lines on screen.
     */
    private fun isAppendOf(oldLines: List<LogLine>, newLinesText: List<String>): Boolean {
        if (newLinesText.size < oldLines.size) return false
        for (i in oldLines.indices) {
            if (newLinesText[i] != oldLines[i].text) return false
        }
        return true
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

    /**
     * Starts the live activity stream. The socket lifecycle (reconnect backoff,
     * REST-polling fallback, teardown) is owned by the repository's channel;
     * cancelling [liveCollectJob] is the teardown.
     */
    fun startLiveStream() {
        _state.value = _state.value.copy(
            isLiveStreamActive = true,
            liveEntries = emptyList(),
        )
        liveEntriesBuffer.clear()

        liveCollectJob?.cancel()
        liveCollectJob = launch {
            val knownIds = _state.value.activityEntries.map { it.id }.toSet()
            adminRepository.liveActivityEntries(knownIds).collect { entry ->
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
                // Evict oldest ids so the dedup set cannot grow without bound
                // over a long live session (~7k ids/hour otherwise).
                liveEntryIdsBuffer.trimToSize(MAX_LIVE_ENTRY_IDS)
                _state.value = _state.value.copy(
                    liveEntries = liveEntriesBuffer.toList(),
                    liveEntryIds = liveEntryIdsBuffer.toSet(),
                    activityEntries = activityEntriesBuffer.toList(),
                )
            }
        }
    }

    fun stopLiveStream() {
        liveCollectJob?.cancel()
        liveCollectJob = null
        _state.value = _state.value.copy(isLiveStreamActive = false)
    }

    fun loadMoreActivity() {
        launch {
            val currentSize = _state.value.activityEntries.size
            val result = adminRepository.getActivityLogEntries(startIndex = currentSize, limit = 50)
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
        liveCollectJob?.cancel()
        logFilePollingJob?.cancel()
    }
}
