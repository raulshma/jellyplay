package com.raulshma.jellyplay.feature.book

import androidx.compose.ui.graphics.ImageBitmap
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.datastore.reader.ReadingDirection
import com.raulshma.jellyplay.core.datastore.reader.ReaderStore
import com.raulshma.jellyplay.core.datastore.reader.ReaderTheme
import com.raulshma.jellyplay.core.model.BookFormat
import com.raulshma.jellyplay.core.model.BookProgressPolicy
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Koin-owned state owner for the book reader (video-player conventions:
 * sealed uiState, no writes from non-VM modules). One screen lifetime loads
 * one item: MediaDetail → [BookFormat.fromPath] → [BookContentResolver]
 * (offline download first, else the reader-cache fetch) → then by format:
 * PAGED books go through [BookDocumentOpener] into paged state, while
 * REFLOWABLE (EPUB) books skip the document layer entirely — the screen's
 * WebView host renders the resolved file, and progress is a percent
 * (jellyfin-web interop: `ticks = floor(percent × 10_000_000)`).
 *
 * Progress is reported to `PlaybackRepository.reportBookProgress` debounced
 * (~800 ms) per page change / relocated event and immediately on dispose.
 * Marking a book read stays a user action (see docs/book-reader.md): nothing
 * here auto-sets the played flag at the last page or a percent threshold.
 */
class BookReaderViewModel(
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
    private val readerStore: ReaderStore,
    private val contentResolver: BookContentResolver,
    private val documentOpener: BookDocumentOpener,
    /**
     * Process-wide scope for the exit flush: [reportNow] with `final = true`
     * runs from `onDispose`, and viewModelScope is already cancelled by the
     * time `onCleared()` tears the screen down — a plain launch there would
     * silently drop the final position report.
     */
    private val flushScope: CoroutineScope,
) : JellyPlayViewModel() {

    private val _uiState = MutableStateFlow<BookReaderUiState>(BookReaderUiState.Idle)
    val uiState: StateFlow<BookReaderUiState> = _uiState.asStateFlow()

    private val _readingDirection = MutableStateFlow(ReadingDirection.LTR)
    val readingDirection: StateFlow<ReadingDirection> = _readingDirection.asStateFlow()

    /** Global reader appearance (reflowable books): persisted in [ReaderStore]. */
    val readerTheme: StateFlow<ReaderTheme> = readerStore.reader
        .map { it.readerTheme }
        .stateIn(scope, SharingStarted.Eagerly, ReaderTheme.DARK)

    val readerFontSizePx: StateFlow<Int> = readerStore.reader
        .map { it.readerFontSizePx }
        .stateIn(scope, SharingStarted.Eagerly, ReaderStore.DEFAULT_FONT_SIZE_PX)

    private var loadedItemId: String? = null
    private var document: BookDocument? = null
    private val pageCache = PageCache<ImageBitmap>()
    private var currentPage = 0

    /** Latest relocated percent (reflowable only), 0.0..1.0. */
    private var currentPercent = 0.0
    private var finalReportFlushed = false
    private var debounceJob: Job? = null
    private var directionJob: Job? = null
    private var pendingFontSizePx: Int? = null
    private var loadJob: Job? = null

    /**
     * True once the user has a persisted per-book direction for this item.
     * That choice always outranks the direction an EPUB reports about
     * itself — see [onEpubDirection].
     */
    private var userDirectionPinned = false

    /** Screen entry point (audio-player convention: the screen triggers the load, the VM holds no itemId ctor dep). */
    fun load(itemId: String) {
        if (loadedItemId == itemId && _uiState.value !is BookReaderUiState.Idle) return
        loadedItemId = itemId
        userDirectionPinned = false
        // One collector per item — a re-load must not stack a second one.
        directionJob?.cancel()
        directionJob = scope.launch {
            readerStore.reader
                .map { it.readingDirections[itemId] }
                .collect { persisted ->
                    // A persisted user choice always wins; without one, keep
                    // whatever the book itself reported or the LTR default.
                    if (persisted != null) {
                        userDirectionPinned = true
                        _readingDirection.value = persisted
                    }
                }
        }
        // Single-flight: a re-load cancels the in-flight fetch/open (which also
        // aborts the streaming download) before starting the next one.
        loadJob?.cancel()
        loadJob = scope.launch { openBook(itemId) }
    }

    private suspend fun openBook(itemId: String) {
        _uiState.value = BookReaderUiState.Loading()
        // A re-load (new book, same VM) must not leak the previous book's
        // document or its cached page bitmaps into the new pager.
        document?.close()
        document = null
        pageCache.clear()
        currentPage = 0
        currentPercent = 0.0
        val detail = mediaRepository.getMediaDetail(itemId).getOrNull()
        if (detail == null) {
            _uiState.value = BookReaderUiState.Error(BookReaderUiState.ErrorReason.CannotOpen)
            return
        }
        val format = BookFormat.fromPath(detail.path)
        if (format == null) {
            _uiState.value = BookReaderUiState.Error(BookReaderUiState.ErrorReason.UnsupportedFormat)
            return
        }

        val resolved = runCatching {
            contentResolver.resolve(
                itemId = itemId,
                // The resolver's sanitize owns path stripping — pass the
                // item path through; it lands as the cache file's base name.
                fileName = detail.path,
                format = format,
                downloadUrl = playbackRepository.getBookDownloadUrl(itemId),
                onProgress = { progress ->
                    (_uiState.value as? BookReaderUiState.Loading)?.let {
                        _uiState.value = it.copy(progress = progress.fraction)
                    }
                },
            )
        }.getOrNull()
        if (resolved == null) {
            _uiState.value = BookReaderUiState.Error(BookReaderUiState.ErrorReason.CannotOpen)
            return
        }

        // Reflowable books never enter the document/pager layer: the WebView
        // host owns rendering, and the position math is percent-based.
        if (format.isReflowable) {
            val resumePercent = BookProgressPolicy.ticksToPercent(detail.playbackPositionTicks)
            currentPercent = resumePercent
            _uiState.value = BookReaderUiState.Ready(
                title = detail.item.name,
                content = ReadyContent.Reflowable(
                    bookFile = resolved.path,
                    resumePercent = resumePercent,
                ),
            )
            return
        }

        val doc = runCatching { documentOpener.open(resolved.path, format) }.getOrNull()
        if (doc == null) {
            _uiState.value = BookReaderUiState.Error(BookReaderUiState.ErrorReason.CannotOpen)
            return
        }
        document = doc

        val resume = BookProgressPolicy.clampPage(
            BookProgressPolicy.ticksToPage(detail.playbackPositionTicks),
            doc.pageCount,
        )
        currentPage = resume
        pageCache.onPageChanged(resume)
        doc.onPageChanged(resume)
        _uiState.value = BookReaderUiState.Ready(
            title = detail.item.name,
            content = ReadyContent.Paged(pageCount = doc.pageCount, currentPage = resume),
        )
    }

    /** Render (or cache-hit) one page for the pager; null keeps the placeholder tile. */
    suspend fun requestPage(pageIndex: Int, widthPx: Int): ImageBitmap? {
        val doc = document ?: return null
        pageCache[pageIndex]?.let { return it }
        // Back-ends hop to Dispatchers.IO themselves; the bitmap lands in the
        // cache on the caller's context (StateFlow-safe either way).
        val bitmap = doc.renderPage(pageIndex, widthPx)
        return bitmap?.also { pageCache.put(pageIndex, it) }
    }

    /** Pager page settled — mirrors state, re-anchors both caches, schedules the debounced report. */
    fun onPageChanged(pageIndex: Int) {
        val ready = _uiState.value as? BookReaderUiState.Ready ?: return
        val paged = ready.content as? ReadyContent.Paged ?: return
        val page = BookProgressPolicy.clampPage(pageIndex, paged.pageCount)
        if (page == currentPage) return
        currentPage = page
        pageCache.onPageChanged(page)
        document?.onPageChanged(page)
        _uiState.value = ready.copy(content = paged.copy(currentPage = page))
        scheduleProgressReport()
    }

    /** Keyboard / tap-zone paging. The pager follows [currentPage] via the screen's sync effect. */
    fun nextPage() {
        onPageChanged(currentPage + 1)
    }

    fun previousPage() {
        onPageChanged(currentPage - 1)
    }

    /** WebView-host relocated event (reflowable) — debounced percent report. */
    fun onEpubPercentChanged(percent: Double) {
        val ready = _uiState.value as? BookReaderUiState.Ready ?: return
        if (ready.content !is ReadyContent.Reflowable) return
        currentPercent = percent.coerceIn(0.0, 1.0)
        scheduleProgressReport()
    }

    /**
     * The book's reported page-flow direction, in-memory for this reading.
     * Only a default: once the user has a persisted per-book choice
     * ([setReadingDirection]), that wins for the session's remainder.
     */
    fun onEpubDirection(direction: ReadingDirection) {
        if (userDirectionPinned) return
        _readingDirection.value = direction
    }

    fun setReaderTheme(theme: ReaderTheme) {
        scope.launch { runCatching { readerStore.setReaderTheme(theme) } }
    }

    /** Font-size stepper; the store clamps the result into its 12..32 px band. */
    fun adjustReaderFontSize(delta: Int) {
        // Step from the last stepped value — the store's StateFlow lags the
        // DataStore write, so reading it per tap drops rapid increments.
        val base = pendingFontSizePx ?: readerFontSizePx.value
        val next = (base + delta)
            .coerceIn(ReaderStore.MIN_FONT_SIZE_PX, ReaderStore.MAX_FONT_SIZE_PX)
        pendingFontSizePx = next
        scope.launch {
            runCatching { readerStore.setReaderFontSizePx(next) }
        }
    }

    fun toggleControls() {
        val ready = _uiState.value as? BookReaderUiState.Ready ?: return
        _uiState.value = ready.copy(showControls = !ready.showControls, showSettings = false)
    }

    fun openSettings() {
        val ready = _uiState.value as? BookReaderUiState.Ready ?: return
        _uiState.value = ready.copy(showSettings = true)
    }

    fun dismissSettings() {
        val ready = _uiState.value as? BookReaderUiState.Ready ?: return
        _uiState.value = ready.copy(showSettings = false)
    }

    fun setReadingDirection(direction: ReadingDirection) {
        val itemId = loadedItemId ?: return
        userDirectionPinned = true
        _readingDirection.value = direction
        scope.launch { readerStore.setReadingDirection(itemId, direction) }
    }

    /**
     * Flush of the current position. `final = true` marks the exit flush
     * (dispose / back / onCleared) — the repository then also purges the item's
     * caches and announces the change. Debounced mid-reading reports pass
     * false: purging per page turn would thrash the caches. The final flush is
     * idempotent: the screen's onDispose AND [onCleared] both fire it on a
     * normal exit (Android config change fires only onDispose — the VM
     * survives), so the second call must not repeat the session-end
     * choreography.
     */
    fun reportNow(final: Boolean = true) {
        debounceJob?.cancel()
        debounceJob = null
        val itemId = loadedItemId ?: return
        if (final) {
            if (finalReportFlushed) return
            finalReportFlushed = true
        }
        // A failed load must not report position 0 and wipe the server-side
        // reading position on dispose.
        val ready = _uiState.value as? BookReaderUiState.Ready ?: return
        val ticks = when (val content = ready.content) {
            is ReadyContent.Paged -> BookProgressPolicy.pageToTicks(currentPage)
            is ReadyContent.Reflowable -> BookProgressPolicy.percentToTicks(currentPercent)
        }
        // The exit flush rides the process-wide scope: viewModelScope is
        // cancelled by onCleared() before a plain launch could run it.
        (if (final) flushScope else scope).launch {
            runCatching {
                playbackRepository.reportBookProgress(itemId, ticks, final = final)
            }
        }
    }

    private fun scheduleProgressReport() {
        // Reading activity again (e.g. the screen re-created after the
        // config-change dispose already flushed): the next exit must flush.
        finalReportFlushed = false
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(PROGRESS_DEBOUNCE_MS)
            reportNow(final = false)
        }
    }

    override fun onCleared() {
        super.onCleared()
        // The authoritative exit flush — onDispose already fired it if the
        // screen tore down first; reportNow's idempotence collapses the two.
        reportNow(final = true)
        document?.close()
        document = null
    }

    companion object {
        private const val PROGRESS_DEBOUNCE_MS = 800L
    }
}
