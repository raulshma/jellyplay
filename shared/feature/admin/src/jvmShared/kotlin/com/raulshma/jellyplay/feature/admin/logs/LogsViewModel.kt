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

/**
 * The selected log file as ONE immutable value — replaces the former
 * `selectedLogFileName` / `selectedLogFileContent` / `selectedLogFileLines`
 * triple of independently-written state fields, whose mid-load window (new
 * name + old content — or a stale fetch's content attaching to a newer
 * selection) was observable as a torn render. Name, content and parsed lines
 * are written together, atomically, or not at all.
 */
data class SelectedLogFile(
    val name: String,
    val content: String,
    val lines: List<LogLine>,
)

/**
 * The selected-log panel's phase — replaces the former `isLoadingLogContent`
 * flag plus the file-load arm of the global `error` field (which the screen
 * never rendered): those could co-derive "loading beside a shown error" and
 * "spinner beside published content". The name rides every non-idle arm, so a
 * publish can always check it against the selection it fetched for.
 */
sealed interface LogPanelPhase {
    /** No file selected — the file list renders. */
    data object Idle : LogPanelPhase

    /** [fileName] was selected; its content is downloading + parsing off Main. */
    data class Loading(val fileName: String) : LogPanelPhase

    /** Content + parsed lines published together — one immutable [SelectedLogFile]. */
    data class Loaded(val file: SelectedLogFile) : LogPanelPhase

    /**
     * [fileName]'s load failed — [message] is the former global `error` write
     * (moved here: the screen never rendered the global field). The 5 s poll
     * keeps running and its next success re-publishes [Loaded] (the self-heal).
     */
    data class Failed(val fileName: String, val message: String?) : LogPanelPhase
}

data class LogsState(
    /**
     * The initial screen-load ladder ([loadInto]) — the transitional §4 shape,
     * out of this migration's scope (the screen does not render them yet).
     */
    val isLoading: Boolean = true,
    /** The initial screen-load ladder's error — see [isLoading]. */
    val error: String? = null,
    val logFiles: List<LogFile> = emptyList(),
    val activityEntries: List<ActivityLogEntry> = emptyList(),
    val activityTotalCount: Int = 0,
    /**
     * The selected-log panel — ONE atomic phase, replacing the torn
     * name/content/lines triple + the `isLoadingLogContent` flag (see
     * [SelectedLogFile] / [LogPanelPhase]).
     */
    val panelPhase: LogPanelPhase = LogPanelPhase.Idle,
    /**
     * The user's pause toggle for the selected-file view-refresh poll (the
     * [logFilePollCycle] round gate's user half). Genuinely two-state — a
     * switch the user flips, not a lifecycle — deliberately a boolean.
     */
    val isLogPollingActive: Boolean = true,
    /**
     * True while a paginated activity fetch (an older page) is in flight.
     * Stays a plain flag on purpose: the stateless [PageAppender] guard reads
     * it SYNCHRONOUSLY, and a phase would move it out of the guard's reach
     * (the declared, pinned re-entry shape; `hasMore` is held true by the
     * declared skip pager).
     */
    val isLoadingMoreActivity: Boolean = false,
    /**
     * The active PrimaryTabRow index — a legitimate positional index into the
     * tab list, not a lifecycle machine; a sealed tab phase would only
     * re-derive the index at every render. Deliberately left as-is.
     */
    val selectedTabIndex: Int = 0,
    /**
     * The live-socket collector's alive flag. TOGETHER WITH
     * [isLogPollingActive] this pair is NOT a phase candidate: the two flags
     * drive independent machines (the selected-file view-refresh poll vs the
     * live activity socket), can hold any combination of on/off at once, and
     * share no render decision — a shared phase would be a false coupling.
     */
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

        /**
         * Retained tail of the selected file's [LogLine] list. Server logs
         * routinely hold 50k-200k lines; keeping the whole file in compose
         * state pins tens of MB and pays an O(n) list copy per append. The
         * retained window indexes into the FULL file ([LogLine.index] is the
         * original line number), so scroll keys stay unique and the diff's
         * append fast path maps the window back onto the re-downloaded
         * content by that offset.
         */
        const val LOG_TAIL_WINDOW = 8_000

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
     * JellyPlayTranscodesViewModel shape — the refresh-cycle module's
     * gate/poll/fetch split, not a hand-rolled cancel-delay-fetch loop). The
     * user's pause toggle and "a file is selected" are the round gate: a
     * closed round fetches nothing and does not advance the backoff.
     */
    private val logFilePollCycle = RefreshCycle(
        scope = scope,
        poll = PollSpec(intervalMs = LOG_POLL_INTERVAL_MS),
        gate = {
            // A file is "selected" in every non-idle panel phase — including
            // Failed, whose poll round is the self-heal (a transient failure
            // re-publishes Loaded on the next success).
            _state.value.isLogPollingActive && _state.value.panelPhase != LogPanelPhase.Idle
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

    /**
     * Selects [fileName]: the panel goes [LogPanelPhase.Loading] and the poll
     * is re-armed — DECLARED legacy behavior: selecting a file silently
     * un-pauses a paused poll (the view-refresh is what keeps a live log
     * honest; the pause stays a per-selection choice). The metadata cursor
     * resets so the next poll round re-checks the file.
     */
    fun loadLogFile(fileName: String) {
        _state.value = _state.value.copy(
            panelPhase = LogPanelPhase.Loading(fileName),
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
                    val all = content.lines()
                    val from = (all.size - LOG_TAIL_WINDOW).coerceAtLeast(0)
                    all.subList(from, all.size).mapIndexed { i, text ->
                        LogLine(index = from + i, text = text, isNew = false, addedTime = 0L)
                    }
                }
                // ONE atomic write — name, content and lines land together, and
                // re-checked inside the write so a file switch that landed
                // during the off-Main parse cannot attach this content to the
                // newer selection (the torn window is unrepresentable).
                _state.value = _state.value.copy(
                    panelPhase = when (val p = _state.value.panelPhase) {
                        is LogPanelPhase.Loading if p.fileName == fileName ->
                            LogPanelPhase.Loaded(SelectedLogFile(fileName, content, lines))
                        else -> p
                    },
                )
            }.onFailure { e ->
                // A file switch landing mid-fetch must not fail the NEW
                // selection — the Failed arm names the file it failed.
                _state.value = _state.value.copy(
                    panelPhase = when (val p = _state.value.panelPhase) {
                        is LogPanelPhase.Loading if p.fileName == fileName ->
                            LogPanelPhase.Failed(fileName, e.message)
                        else -> p
                    },
                )
            }
        }
    }

    /**
     * One round of the [logFilePollCycle]: metadata-first (unchanged ⇒
     * nothing else runs), then the changed-file content fetch + diff. The
     * parse/diff runs off Main — the continuation here resumes on the
     * main-immediate VM scope, and a multi-MB `lines()` split or prefix walk
     * on it is the frame-drop source this wrap exists for. Publishes only
     * over a same-name [LogPanelPhase.Loaded] / [LogPanelPhase.Failed] arm
     * (Loading belongs to the explicit load's own settle; Idle has nothing to
     * publish into), and the publish is ONE atomic [SelectedLogFile] write.
     */
    private suspend fun refreshSelectedLogFile() {
        val fileName = when (val p = _state.value.panelPhase) {
            is LogPanelPhase.Loading -> p.fileName
            is LogPanelPhase.Loaded -> p.file.name
            is LogPanelPhase.Failed -> p.fileName
            LogPanelPhase.Idle -> return
        }
        val meta = fetchLogMeta(fileName)
        if (meta != null && meta.size == lastLogSize && meta.dateModified == lastLogModified) return
        val result = adminRepository.getLogFileContent(fileName)
        result.onSuccess { content ->
            if (meta != null) {
                lastLogSize = meta.size
                lastLogModified = meta.dateModified
            }
            // The pre-parse bail: a file switch/clear landing mid-fetch must
            // not pay the multi-MB diff for a stale selection (the old loop's
            // while-condition check).
            val oldLines = when (val p = _state.value.panelPhase) {
                is LogPanelPhase.Loaded -> p.file.lines.takeIf { p.file.name == fileName }
                is LogPanelPhase.Failed -> emptyList<LogLine>().takeIf { p.fileName == fileName }
                else -> null // Idle: nothing selected; Loading: the explicit load settles itself
            } ?: return@onSuccess
            val diffed = withContext(parseDispatcher) {
                diffAgainst(oldLines, content)
            } ?: return@onSuccess
            // ONE atomic write — re-checked inside the write so a switch or
            // clear that landed during the off-Main parse cannot attach this
            // content to another file (the torn window is unrepresentable).
            _state.value = _state.value.copy(
                panelPhase = when (val p = _state.value.panelPhase) {
                    is LogPanelPhase.Loaded if p.file.name == fileName ->
                        LogPanelPhase.Loaded(SelectedLogFile(fileName, content, diffed))
                    is LogPanelPhase.Failed if p.fileName == fileName ->
                        LogPanelPhase.Loaded(SelectedLogFile(fileName, content, diffed))
                    else -> p
                },
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
        val windowStart = oldLines.firstOrNull()?.index ?: 0
        val oldCount = windowStart + oldLines.size
        val appendOnly = newLinesText.size >= oldCount &&
            isAppendOf(oldLines, newLinesText, windowStart)

        return when {
            appendOnly && newLinesText.size == oldCount -> null
            appendOnly -> {
                val now = System.currentTimeMillis()
                val appended = newLinesText.subList(oldCount, newLinesText.size)
                    .mapIndexed { i, text ->
                        LogLine(
                            index = oldCount + i,
                            text = text,
                            isNew = true,
                            addedTime = now,
                        )
                    }
                retainTail(oldLines + appended)
            }
            else -> {
                // Rotation / truncation / rewrite: re-derive against the
                // window's offset slice, reusing unchanged lines so their
                // isNew / addedTime state survives.
                retainTail(
                    newLinesText.mapIndexed { index, text ->
                        val windowIdx = index - windowStart
                        if (windowIdx in oldLines.indices && oldLines[windowIdx].text == text) {
                            oldLines[windowIdx]
                        } else {
                            LogLine(
                                index = index,
                                text = text,
                                isNew = index >= oldCount,
                                addedTime = if (index >= oldCount) System.currentTimeMillis() else 0L,
                            )
                        }
                    },
                )
            }
        }
    }

    /**
     * The retained state is the LAST [LOG_TAIL_WINDOW] lines. LogLine.index
     * values are the original file line numbers, so the LazyColumn keys stay
     * unique and stable across window slides.
     */
    private fun retainTail(lines: List<LogLine>): List<LogLine> =
        if (lines.size <= LOG_TAIL_WINDOW) lines else lines.takeLast(LOG_TAIL_WINDOW)

    /**
     * True when [oldLines] (the retained tail window starting at
     * [windowStart]) is a line-for-line slice of [newLinesText] at that
     * offset — i.e. the file only grew past the window. Compares the full
     * window rather than just the boundary line so a same-size or partial
     * rewrite (rotation) can't slip through as an "append" and leave stale
     * lines on screen. Caller guarantees [newLinesText] covers the window.
     */
    private fun isAppendOf(
        oldLines: List<LogLine>,
        newLinesText: List<String>,
        windowStart: Int,
    ): Boolean {
        for (i in oldLines.indices) {
            if (newLinesText[windowStart + i] != oldLines[i].text) return false
        }
        return true
    }

    fun toggleLogPolling() {
        _state.value = _state.value.copy(isLogPollingActive = !_state.value.isLogPollingActive)
    }

    fun clearSelectedLogFile() {
        _state.value = _state.value.copy(
            panelPhase = LogPanelPhase.Idle,
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
     * fetch is in flight is suppressed — a fast fling can fire this
     * re-entrantly with the same startIndex before the buffer grows,
     * double-appending the same server page. The in-flight flag stays in
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
