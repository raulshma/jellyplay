package com.raulshma.jellyplay.feature.admin.logs

import com.raulshma.jellyplay.core.data.repository.AdminRepository
import com.raulshma.jellyplay.core.model.ActivityLogEntry
import com.raulshma.jellyplay.core.model.LogFile
import com.raulshma.jellyplay.core.model.trimToSize
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.core.ui.viewmodel.PageAppender
import com.raulshma.jellyplay.core.concurrency.PollSpec
import com.raulshma.jellyplay.core.concurrency.RefreshCycle
import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import com.raulshma.jellyplay.core.ui.viewmodel.loadInto
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

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
    /** True while a paginated activity fetch (an older page) is in flight. */
    val isLoadingMoreActivity: Boolean = false,
    val selectedTabIndex: Int = 0,
    val isLiveStreamActive: Boolean = false,
    val liveEntries: List<ActivityLogEntry> = emptyList(),
    val liveEntryIds: Set<Long> = emptySet(),
)

class LogsViewModel(
    private val adminRepository: AdminRepository,
    // The multi-MB parse/diff off-Main home (the EpgViewModel gridDispatcher
    // seam): production omits it; tests pin the test scheduler.
    private val parseDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : JellyPlayViewModel() {

    private val _state = composeState(LogsState())
    val state: LogsState get() = _state.value

    private companion object {
        const val MAX_LIVE_ENTRIES = 200

        /** Cap for the live dedup id set — 2× the display buffer it guards. */
        const val MAX_LIVE_ENTRY_IDS = MAX_LIVE_ENTRIES * 2

        /** Server page size for the paginated activity fetch (cold load + more-loads). */
        const val ACTIVITY_PAGE_SIZE = 50

        /** Cadence of the selected-file view-refresh poll. */
        const val LOG_POLL_INTERVAL_MS = 5_000L
    }

    private var liveCollectJob: Job? = null

    // Poll bookkeeping for the selected file: the metadata-first check
    // (size / modification date) collapses the 5 s tick to one cheap list
    // request while the file is unchanged — the multi-MB content
    // re-download + lines() re-split + diff only run on a real change.
    private var lastLogSize = -1L
    private var lastLogModified = ""

    private val liveEntriesBuffer = ArrayDeque<ActivityLogEntry>(MAX_LIVE_ENTRIES)
    private val activityEntriesBuffer = ArrayDeque<ActivityLogEntry>(MAX_LIVE_ENTRIES)
    private val liveEntryIdsBuffer = LinkedHashSet<Long>()

    init {
        loadInitialData()
    }

    /**
     * The 5 s selected-file view-refresh poll, registered once and bound to
     * screen visibility by [startLogPolling]/[stopLogPolling] (the
     * JellyPlayTranscodesViewModel shape — the hand-rolled
     * cancel-delay-fetch loop this VM used to run for its whole lifetime
     * once a file was picked, back stack included, is the refresh-cycle
     * module's exact prior shape). The user's pause toggle and "a file is
     * selected" are the round gate: a closed round fetches nothing and does
     * not advance the backoff.
     */
    private val logFilePollCycle = RefreshCycle(
        scope = scope,
        poll = PollSpec(intervalMs = LOG_POLL_INTERVAL_MS),
        gate = {
            _state.value.isLogPollingActive && _state.value.selectedLogFileName != null
        },
        fetch = { refreshSelectedLogFile() },
    )

    /**
     * Screen-visibility hook: arms the selected-file poll (the screen calls
     * this from its `LifecycleStartEffect`). Safe to call on every entry
     * into START — a repeated call replaces the running loop
     * ([RefreshCycle.start]).
     */
    fun startLogPolling() {
        logFilePollCycle.start()
    }

    /** Stops the poll started by [startLogPolling]; the VM owner kills it for good. */
    fun stopLogPolling() {
        logFilePollCycle.stop()
    }

    fun loadInitialData() {
        launch {
            loadInto(
                start = { _state.value = _state.value.copy(isLoading = true, error = null) },
                // Declared variant (see loadInto): the legacy ladder fetched
                // both halves in parallel and settled each with
                // `getOrNull() ?: emptyList()` — a failed Result never surfaces
                // an error here, only a THROWN exception does (the old catch),
                // hence the wrapper around the awaited pair; cancellation
                // propagates instead of settling as the error arm.
                fetch = {
                    runCatchingRethrowingCancellation {
                        val logFilesDeferred = async { adminRepository.getLogFiles() }
                        val activityDeferred = async { adminRepository.getActivityLogEntries(limit = ACTIVITY_PAGE_SIZE) }
                        logFilesDeferred.await() to activityDeferred.await()
                    }
                },
                onSuccess = { (logFilesResult, activityResult) ->
                    val entries = activityResult.getOrNull() ?: emptyList()
                    activityEntriesBuffer.clear()
                    activityEntriesBuffer.addAll(entries)
                    _state.value = _state.value.copy(
                        logFiles = logFilesResult.getOrNull() ?: emptyList(),
                        activityEntries = activityEntriesBuffer.toList(),
                        isLoading = false,
                    )
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(error = e.message, isLoading = false)
                },
            )
        }
    }

    fun loadLogFile(fileName: String) {
        _state.value = _state.value.copy(
            isLoadingLogContent = true,
            selectedLogFileName = fileName,
            selectedLogFileContent = null,
            selectedLogFileLines = emptyList(),
            isLogPollingActive = true
        )
        lastLogSize = -1L
        lastLogModified = ""
        launch {
            // File metadata (size / modification date) drives the poll: when
            // unchanged, the multi-MB content re-download + lines() re-split +
            // diff are all skipped. Jellyfin logs are routinely multi-MB, so
            // this collapses the 5 s poll to one cheap list request.
            fetchLogMeta(fileName)?.let {
                lastLogSize = it.size
                lastLogModified = it.dateModified
            }

            val initialResult = adminRepository.getLogFileContent(fileName)
            initialResult.onSuccess { content ->
                // Multi-MB files: the lines() split alone allocates 50k-200k
                // Strings — parse off Main (the LicensesViewModel idiom),
                // publish on Main.
                val lines = withContext(parseDispatcher) {
                    content.lines().mapIndexed { index, text ->
                        LogLine(index = index, text = text, isNew = false, addedTime = 0L)
                    }
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
        }
    }

    /**
     * One round of the [logFilePollCycle]: metadata-first (unchanged ⇒
     * nothing else runs), then the changed-file content fetch + diff. The
     * parse/diff runs off Main — the continuation here resumes on the
     * main-immediate VM scope, and a multi-MB `lines()` split or prefix walk
     * on it is the frame-drop source this wrap exists for.
     */
    private suspend fun refreshSelectedLogFile() {
        val fileName = _state.value.selectedLogFileName ?: return
        val meta = fetchLogMeta(fileName)
        if (meta != null && meta.size == lastLogSize && meta.dateModified == lastLogModified) return
        val result = adminRepository.getLogFileContent(fileName)
        result.onSuccess { content ->
            if (meta != null) {
                lastLogSize = meta.size
                lastLogModified = meta.dateModified
            }
            // A file switch/clear landing mid-fetch must not publish into the
            // new selection (the old loop's while-condition check).
            if (_state.value.selectedLogFileName != fileName) return@onSuccess
            val oldLines = _state.value.selectedLogFileLines
            val diffed = withContext(parseDispatcher) {
                diffAgainst(oldLines, content)
            } ?: return@onSuccess
            _state.value = _state.value.copy(
                selectedLogFileContent = content,
                selectedLogFileLines = diffed,
            )
        }
    }

    private suspend fun fetchLogMeta(fileName: String): LogFile? =
        adminRepository.getLogFiles().getOrNull()
            ?.firstOrNull { it.name == fileName }

    /**
     * The content→[LogLine] transformation, run on [Dispatchers.Default] by
     * both callers. Returns null when the new content produces no visible
     * change (nothing to publish).
     *
     * Append-only fast path: server logs only append, so when every previous
     * line still lines up, reuse the existing LogLine instances and build
     * only the new tail — a full re-walk builds a second list just to
     * compare. Non-append shapes (rotation, truncation, rewrite) fall
     * through to the full re-diff.
     */
    private fun diffAgainst(oldLines: List<LogLine>, content: String): List<LogLine>? {
        val newLinesText = content.lines()
        val appendOnly = isAppendOf(oldLines, newLinesText)

        return when {
            appendOnly && newLinesText.size == oldLines.size -> null
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
                oldLines + appended
            }
            newLinesText != oldLines.map { it.text } -> {
                newLinesText.mapIndexed { index, text ->
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
            }
            else -> null
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

    /**
     * Fetches the next activity page at `startIndex = currentSize`, routed
     * through the [PageAppender] guard: a call landing while an older-page
     * fetch is in flight is suppressed — a fast fling used to fire this
     * re-entrantly with the same startIndex before the buffer grew,
     * double-appending the same server page (the screen's `!isLoadingMore`
     * guard was fed a hardcoded `false`). The in-flight flag stays in
     * [LogsState] (the stateless guard reads the site's flag), raised
     * synchronously before the coroutine so both this guard and the
     * screen's infinite-list guard see it. The fetch offset stays the live
     * list size — skip-based rather than page-numbered, since live-tail
     * prepends keep the size off any page multiple — so the guard's
     * returned ordinal is unused; what's adopted is the suppress-or-proceed
     * decision, with `hasMore` held true: the skip pager has no terminal
     * gate, an exhausted server just answers with an empty page.
     */
    fun loadMoreActivity() {
        val s = _state.value
        PageAppender.nextPageOrNull(
            // Synthetic ordinal — this skip pager has no page math (see KDoc);
            // only the guard's suppress-or-proceed decision is consumed.
            currentPage = s.activityEntries.size / ACTIVITY_PAGE_SIZE,
            inFlight = s.isLoadingMoreActivity,
            hasMore = true,
        ) ?: return
        _state.value = s.copy(isLoadingMoreActivity = true)
        launch {
            val currentSize = _state.value.activityEntries.size
            val result = adminRepository.getActivityLogEntries(startIndex = currentSize, limit = ACTIVITY_PAGE_SIZE)
            result.onSuccess { more ->
                activityEntriesBuffer.addAll(more)
                _state.value = _state.value.copy(
                    activityEntries = activityEntriesBuffer.toList(),
                )
            }
            _state.value = _state.value.copy(isLoadingMoreActivity = false)
        }
    }

    override fun onCleared() {
        super.onCleared()
        liveCollectJob?.cancel()
        // The poll cycle rides the VM scope — it dies with it; the screen's
        // STOP-side stopLogPolling is the visibility arm.
    }
}
