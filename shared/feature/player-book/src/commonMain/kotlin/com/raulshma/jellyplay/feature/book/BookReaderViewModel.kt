package com.raulshma.jellyplay.feature.book

import androidx.compose.ui.graphics.ImageBitmap
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.repository.ReaderAnnotationColor
import com.raulshma.jellyplay.core.data.repository.ReaderAnnotationStyle
import com.raulshma.jellyplay.core.data.repository.ReaderAnnotationsRepository
import com.raulshma.jellyplay.core.data.repository.ReaderAnnotation
import com.raulshma.jellyplay.core.data.repository.ReaderBookmark
import com.raulshma.jellyplay.core.datastore.reader.ReadingDirection
import com.raulshma.jellyplay.core.datastore.reader.ReaderStore
import com.raulshma.jellyplay.core.datastore.reader.ReaderTheme
import com.raulshma.jellyplay.core.model.BookFormat
import com.raulshma.jellyplay.core.model.BookProgressPolicy
import com.raulshma.jellyplay.core.model.pathExtension
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.feature.book.epub.EpubRelocation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
 *
 * Reader marks (Wave 3) live in [ReaderAnnotationsRepository] and are exposed
 * per loaded item through [bookmarks]/[annotations] (re-subscribed per load);
 * the toggle/jump COORDINATION lives here while the EPUB host stays
 * screen-owned — [jumpToBookmark] only resolves the paged path, the screen
 * drives `host.goToCfi` for reflowable jumps (it owns the host). Exact
 * reflowable resume rides the ReaderStore last-CFI map: [ReadyContent.Reflowable.resumeCfi]
 * carries the stored anchor to the screen (which jumps once the host is
 * READY), and every debounced progress flush re-persists the current anchor.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BookReaderViewModel(
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
    private val readerStore: ReaderStore,
    private val annotationsRepository: ReaderAnnotationsRepository,
    private val contentResolver: BookContentResolver,
    private val documentOpener: BookDocumentOpener,
    private val pdfOutlineParser: PdfOutlineParser,
    /**
     * Download-header format probe for items whose `Path` yields nothing.
     * Default-neutral so platforms without a probe impl still compile.
     */
    private val formatProbe: BookFormatProbe = NoopBookFormatProbe,
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

    /**
     * The item whose marks are exposed — the key of the marks flows below. A
     * load re-points it, so [bookmarks]/[annotations] re-subscribe to the new
     * item's repository streams (flatMapLatest drops the stale subscription).
     */
    private val marksItemId = MutableStateFlow<String?>(null)

    val bookmarks: StateFlow<List<ReaderBookmark>> = marksItemId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else annotationsRepository.observeBookmarks(id)
        }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val annotations: StateFlow<List<ReaderAnnotation>> = marksItemId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else annotationsRepository.observeAnnotations(id)
        }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** The reflowable reader's folded position (null until the first relocation). */
    private val _currentEpubLocation = MutableStateFlow<EpubLocation?>(null)
    val currentEpubLocation: StateFlow<EpubLocation?> = _currentEpubLocation.asStateFlow()

    /** The live text selection (null while nothing is selected). */
    private val _selection = MutableStateFlow<ReaderSelection?>(null)
    val selection: StateFlow<ReaderSelection?> = _selection.asStateFlow()

    /** The paged book's outline (PDF only; empty until parsed / for CBZ+CBR). */
    private val _pdfOutline = MutableStateFlow<List<PdfOutlineNode>>(emptyList())
    val pdfOutline: StateFlow<List<PdfOutlineNode>> = _pdfOutline.asStateFlow()

    private var loadedItemId: String? = null
    private var document: BookDocument? = null
    private val pageCache = PageCache<ImageBitmap>()
    private var currentPage = 0

    /** Latest relocated percent (reflowable only), 0.0..1.0. */
    private var currentPercent = 0.0

    /** Latest relocated CFI (reflowable only) — the exact-resume / bookmark anchor. */
    private var latestEpubCfi: String? = null
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
        marksItemId.value = itemId
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

    /**
     * Format fallback when the item's `Path` carried no known extension:
     * one cheap ranged GET against the download URL reads Content-Type +
     * Content-Disposition filename (both served for every book, 10.9–12).
     * A probe failure (or an unrecognized metadata pair — mobi/azw3) maps to
     * null and the caller reports the precise unsupported-format error.
     */
    private suspend fun probeDownloadFormat(downloadUrl: String, accessToken: String?): BookFormat? {
        val metadata = runCatching { formatProbe.probe(downloadUrl, accessToken) }.getOrNull() ?: return null
        return BookFormat.fromDownloadMetadata(metadata.contentType, metadata.fileName)
    }

    private suspend fun openBook(itemId: String) {
        _uiState.value = BookReaderUiState.Loading()
        // A re-load (new book, same VM) must not leak the previous book's
        // document, cached page bitmaps or per-book position tracking into
        // the new reader.
        document?.close()
        document = null
        pageCache.clear()
        currentPage = 0
        currentPercent = 0.0
        latestEpubCfi = null
        _currentEpubLocation.value = null
        _selection.value = null
        _pdfOutline.value = emptyList()
        val detail = mediaRepository.getMediaDetail(itemId).getOrNull()
        if (detail == null) {
            _uiState.value = BookReaderUiState.Error(BookReaderUiState.ErrorReason.CannotOpen)
            return
        }
        val downloadUrl = playbackRepository.getBookDownloadUrl(itemId)
        // Header auth for the probe + streaming fetch: Jellyfin 12 401s the
        // legacy ?api_key= query param on data endpoints, so the token must
        // ride `Authorization: MediaBrowser` (the URL keeps api_key for ≤11).
        val accessToken = playbackRepository.getAccessToken()
        val format = BookFormat.fromPath(detail.path) ?: probeDownloadFormat(downloadUrl, accessToken)
        if (format == null) {
            // Distinguish "the item's path names a file the reader can't open"
            // (e.g. .mobi — download-only) from "no format was knowable at all"
            // (path blank AND the download probe failed) so the error veil can
            // name the cause instead of a bare "unsupported format".
            val extension = detail.path
                ?.takeIf { it.isNotBlank() }
                ?.pathExtension()
                ?.takeIf { it.isNotEmpty() }
                ?.let { ".$it" }
            _uiState.value = BookReaderUiState.Error(
                reason = BookReaderUiState.ErrorReason.UnsupportedFormat,
                detail = extension,
            )
            return
        }

        val resolved = runCatching {
            contentResolver.resolve(
                itemId = itemId,
                // The resolver's sanitize owns path stripping — pass the
                // item path through; it lands as the cache file's base name.
                fileName = detail.path,
                format = format,
                downloadUrl = downloadUrl,
                accessToken = accessToken,
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
        // host owns rendering, and the position math is percent-based. The
        // locally stored CFI (ADR 0003 point 4) outranks the server percent on
        // this install — the screen jumps to it once the host is READY.
        if (format.isReflowable) {
            val resumePercent = BookProgressPolicy.ticksToPercent(detail.playbackPositionTicks)
            currentPercent = resumePercent
            _uiState.value = BookReaderUiState.Ready(
                title = detail.item.name,
                content = ReadyContent.Reflowable(
                    bookFile = resolved.path,
                    resumePercent = resumePercent,
                    resumeCfi = readerStore.lastCfi(itemId),
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
            content = ReadyContent.Paged(
                pageCount = doc.pageCount,
                currentPage = resume,
                format = format,
            ),
        )
        if (format == BookFormat.PDF) {
            parseOutline(resolved.path)
        }
    }

    /**
     * PDF outline extraction, off the main thread (PDFBox walks the document
     * synchronously). Failures already fold to an empty list inside the
     * parser — the TOC sheet simply shows its empty state.
     */
    private fun parseOutline(path: okio.Path) {
        scope.launch {
            _pdfOutline.value = withContext(Dispatchers.Default) {
                runCatching { pdfOutlineParser.parse(path) }.getOrDefault(emptyList())
            }
        }
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

    /** WebView-host percent event (reflowable) — debounced percent report. */
    fun onEpubPercentChanged(percent: Double) {
        val ready = _uiState.value as? BookReaderUiState.Ready ?: return
        if (ready.content !is ReadyContent.Reflowable) return
        currentPercent = percent.coerceIn(0.0, 1.0)
        _currentEpubLocation.value = _currentEpubLocation.value?.copy(percent = currentPercent)
        scheduleProgressReport()
    }

    /**
     * WebView-host relocation (reflowable): folds percent + chapter label +
     * the page-start CFI into [currentEpubLocation] (the bookmark/annotation
     * anchor source) and schedules the debounced report, which also
     * re-persists the exact-resume CFI. Internal: [EpubRelocation] is the
     * module-private host protocol type.
     */
    internal fun onEpubRelocated(relocation: EpubRelocation) {
        val ready = _uiState.value as? BookReaderUiState.Ready ?: return
        if (ready.content !is ReadyContent.Reflowable) return
        relocation.percent?.let { currentPercent = it.coerceIn(0.0, 1.0) }
        relocation.cfi?.let { latestEpubCfi = it }
        _currentEpubLocation.value = EpubLocation(
            percent = currentPercent,
            chapterLabel = relocation.chapterLabel,
            cfi = latestEpubCfi,
        )
        scheduleProgressReport()
    }

    /** Text selected inside the WebView host — drives the selection action row. */
    fun onEpubSelection(cfi: String, text: String) {
        _selection.value = ReaderSelection(cfi = cfi, text = text)
    }

    fun onEpubSelectionCleared() {
        _selection.value = null
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

    // ---------------------------------------------------------------------
    // Reader marks (bookmarks + annotations) — local-first per ADR 0003.
    // ---------------------------------------------------------------------

    /**
     * The bookmark sitting at the current position, if any — the toggle
     * target and the top-bar icon's filled state. Paged match = same encoded
     * page; reflowable match = same CFI (page-start anchors are stable), or
     * the null-CFI fallback comparing encoded percents (pre-Wave-3 rows).
     */
    private fun bookmarkAtCurrentPosition(): ReaderBookmark? {
        val itemId = loadedItemId ?: return null
        val ready = _uiState.value as? BookReaderUiState.Ready ?: return null
        return when (ready.content) {
            is ReadyContent.Paged -> {
                val ticks = BookProgressPolicy.pageToTicks(currentPage)
                bookmarks.value.firstOrNull { it.itemId == itemId && it.cfi == null && it.positionTicks == ticks }
            }
            is ReadyContent.Reflowable -> {
                val location = _currentEpubLocation.value
                bookmarks.value.firstOrNull { bookmark ->
                    bookmark.itemId == itemId && when {
                        bookmark.cfi != null -> bookmark.cfi == location?.cfi
                        // Null-CFI rows can only match by encoded percent.
                        else -> bookmark.positionTicks == BookProgressPolicy.percentToTicks(currentPercent)
                    }
                }
            }
        }
    }

    fun hasBookmarkAtCurrentPosition(): Boolean = bookmarkAtCurrentPosition() != null

    /**
     * Bookmark the current position, or remove the bookmark already sitting
     * there (toggle semantics — one control, no separate delete in the chrome).
     * Paged rows encode `pageToTicks(currentPage)` with a null CFI and empty
     * label (paged books have no chapter concept in v1); reflowable rows
     * encode the relocation percent plus the exact CFI + chapter label.
     */
    fun toggleBookmarkAtCurrentPosition() {
        val itemId = loadedItemId ?: return
        val ready = _uiState.value as? BookReaderUiState.Ready ?: return
        val existing = bookmarkAtCurrentPosition()
        scope.launch {
            runCatching {
                if (existing != null) {
                    annotationsRepository.removeBookmark(existing.id)
                } else {
                    when (ready.content) {
                        is ReadyContent.Paged -> annotationsRepository.addBookmark(
                            itemId = itemId,
                            positionTicks = BookProgressPolicy.pageToTicks(currentPage),
                            cfi = null,
                            chapterLabel = "",
                        )
                        is ReadyContent.Reflowable -> {
                            val location = _currentEpubLocation.value
                            annotationsRepository.addBookmark(
                                itemId = itemId,
                                positionTicks = BookProgressPolicy.percentToTicks(currentPercent),
                                cfi = location?.cfi,
                                chapterLabel = location?.chapterLabel.orEmpty(),
                            )
                        }
                    }
                }
            }
        }
    }

    fun deleteBookmark(id: Long) {
        scope.launch { runCatching { annotationsRepository.removeBookmark(id) } }
    }

    /**
     * Jump to a bookmark. Paged books resolve through the pager ([onPageChanged]
     * scrolls + reports); reflowable jumps are HOST-coordinated — the screen
     * calls `host.goToCfi(bookmark.cfi)` itself, and a null-CFI reflowable row
     * has no fallback (the host exposes no display-by-percent after boot), so
     * there is deliberately nothing to do here for that case.
     */
    fun jumpToBookmark(bookmark: ReaderBookmark) {
        val ready = _uiState.value as? BookReaderUiState.Ready ?: return
        if (ready.content is ReadyContent.Paged) {
            onPageChanged(BookProgressPolicy.ticksToPage(bookmark.positionTicks))
        }
    }

    /**
     * Turns the live selection into a persisted annotation (highlight or
     * underline, with an optional note). The screen mirrors it onto the host
     * (`addAnnotation`, never `applyAnnotations` — replace semantics would
     * wipe unrelated marks) and clears the JS selection afterwards.
     */
    fun addSelectionAnnotation(
        style: ReaderAnnotationStyle,
        color: ReaderAnnotationColor,
        note: String? = null,
    ) {
        val itemId = loadedItemId ?: return
        val selection = _selection.value ?: return
        val chapterLabel = _currentEpubLocation.value?.chapterLabel.orEmpty()
        _selection.value = null
        scope.launch {
            runCatching {
                annotationsRepository.addAnnotation(
                    itemId = itemId,
                    cfi = selection.cfi,
                    style = style,
                    color = color,
                    anchorText = selection.text,
                    note = note,
                    chapterLabel = chapterLabel,
                )
            }
        }
    }

    /**
     * Partial annotation edit — null arguments leave the stored value
     * unchanged; an empty-string note clears it (repository contract). The
     * screen re-paints the changed mark through the incremental host path.
     */
    fun updateAnnotation(
        id: Long,
        note: String? = null,
        color: ReaderAnnotationColor? = null,
        style: ReaderAnnotationStyle? = null,
    ) {
        scope.launch {
            runCatching { annotationsRepository.updateAnnotation(id, note, color, style) }
        }
    }

    fun deleteAnnotation(id: Long) {
        scope.launch { runCatching { annotationsRepository.deleteAnnotation(id) } }
    }

    /** The persisted annotation sitting on the live selection's CFI, if any (edit vs create row). */
    fun annotationAtSelection(): ReaderAnnotation? {
        val cfi = _selection.value?.cfi ?: return null
        return annotations.value.firstOrNull { it.cfi == cfi }
    }

    /**
     * Renders the item's marks through the repository's pure exporters. The
     * screen owns clipboard + confirmation (presentation-only concerns).
     */
    fun exportAnnotations(asJson: Boolean): String {
        val ready = _uiState.value as? BookReaderUiState.Ready
        return if (asJson) {
            annotationsRepository.exportJson(
                itemId = loadedItemId,
                title = ready?.title.orEmpty(),
                bookmarks = bookmarks.value,
                annotations = annotations.value,
            )
        } else {
            annotationsRepository.exportMarkdown(
                itemId = loadedItemId,
                title = ready?.title.orEmpty(),
                bookmarks = bookmarks.value,
                annotations = annotations.value,
            )
        }
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
        // cancelled by onCleared() before a plain launch could run it. The
        // last-CFI write rides along so the exact-resume anchor survives even
        // when the reader closed within the debounce window.
        val cfi = latestEpubCfi
        (if (final) flushScope else scope).launch {
            runCatching {
                if (cfi != null && ready.content is ReadyContent.Reflowable) {
                    readerStore.setLastCfi(itemId, cfi)
                }
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
