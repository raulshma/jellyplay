package com.raulshma.jellyplay.feature.book

import androidx.compose.ui.graphics.ImageBitmap
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.repository.ReaderAnnotationColor
import com.raulshma.jellyplay.core.data.repository.ReaderAnnotationStyle
import com.raulshma.jellyplay.core.data.repository.ReaderAnnotationsRepository
import com.raulshma.jellyplay.core.data.repository.ReaderAnnotation
import com.raulshma.jellyplay.core.data.repository.ReaderBookmark
import com.raulshma.jellyplay.core.datastore.reader.PerBookAppearance
import com.raulshma.jellyplay.core.datastore.reader.ReadingDirection
import com.raulshma.jellyplay.core.datastore.reader.ReaderFontFamily
import com.raulshma.jellyplay.core.datastore.reader.ReaderSlice
import com.raulshma.jellyplay.core.datastore.reader.ReaderStore
import com.raulshma.jellyplay.core.datastore.reader.ReaderTheme
import com.raulshma.jellyplay.core.model.BookFormat
import com.raulshma.jellyplay.core.model.BookProgressPolicy
import com.raulshma.jellyplay.core.model.pathExtension
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.feature.book.epub.EpubRelocation
import com.raulshma.jellyplay.feature.book.epub.EpubSpeechParagraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

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
 *
 * Read aloud + sleep timer (Wave 5) follow the same split: the speech LOOP
 * ([ReaderSpeechController]) and the timer ([ReaderSleepTimer]) live here
 * over the [BookSpeechEngine] seam, while every host touch
 * (`requestSpeechContext` / `next` / paragraph follow / auto-scroll stop)
 * rides [hostCommands] — one-shot events the screen executes because only
 * it holds the EPUB host.
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
     * Paragraph read-aloud engine (Android TTS; desktop/web degrade to the
     * neutral [NoopBookSpeechEngine] and the reader hides its speech UI).
     * Defaulted so tests and non-DI constructions compile unchanged.
     */
    private val speechEngine: BookSpeechEngine = NoopBookSpeechEngine,
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

    /**
     * The item whose marks + appearance override are exposed — the key of the
     * per-item flows. A load re-points it, so [bookmarks]/[annotations] (and
     * [perBookAppearance]) re-subscribe to the new item's streams
     * (flatMapLatest drops the stale subscription).
     */
    private val marksItemId = MutableStateFlow<String?>(null)

    /**
     * The loaded item's per-book appearance override (null = the book inherits
     * the global theme/font size). Re-subscribed per load, exactly like the
     * marks flows — and the router the theme/font setters write through.
     */
    val perBookAppearance: StateFlow<PerBookAppearance?> = marksItemId
        .flatMapLatest { id ->
            if (id == null) flowOf(null) else readerStore.reader.map { it.perBookAppearance[id] }
        }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, null)

    /**
     * What the reader RENDERS with: the per-book override's axis ?: the global
     * one (pure fold in [effectiveAppearance]). Construction, live pushes and
     * the settings sheet's selection states all read THESE, never the raw
     * globals (the slice itself stays reachable through [readerStore]), so an
     * override and its global stay visually coherent.
     */
    val effectiveReaderTheme: StateFlow<ReaderTheme> = combine(readerStore.reader, perBookAppearance) { slice, per ->
        effectiveAppearance(slice, per).theme
    }.stateIn(scope, SharingStarted.Eagerly, readerStore.reader.value.readerTheme)

    val effectiveReaderFontSizePx: StateFlow<Int> = combine(readerStore.reader, perBookAppearance) { slice, per ->
        effectiveAppearance(slice, per).fontSizePx
    }.stateIn(scope, SharingStarted.Eagerly, readerStore.reader.value.readerFontSizePx)

    /**
     * One reader-slice preference as a StateFlow — every global knob below is
     * the same `map + stateIn` shape over [ReaderStore.reader], so the
     * selector + initial pair is the only per-knob code left.
     */
    private fun <T> readerPref(initial: T, selector: (ReaderSlice) -> T): StateFlow<T> =
        readerStore.reader
            .map(selector)
            .stateIn(scope, SharingStarted.Eagerly, initial)

    /** Global reflowable typography slice (family / leading / margins / justify / flow). */
    val readerFontFamily: StateFlow<ReaderFontFamily> =
        readerPref(ReaderFontFamily.SYSTEM) { it.fontFamily }

    val lineHeightPct: StateFlow<Int> =
        readerPref(ReaderStore.DEFAULT_LINE_HEIGHT_PCT) { it.lineHeightPct }

    val marginPct: StateFlow<Int> =
        readerPref(ReaderStore.DEFAULT_MARGIN_PCT) { it.marginPct }

    val justify: StateFlow<Boolean> = readerPref(false) { it.justify }

    val scrollMode: StateFlow<Boolean> = readerPref(false) { it.scrollMode }

    /** Display + behavior slice: brightness veil, volume-key paging, animated turns, reading speed. */
    val brightnessPct: StateFlow<Int> =
        readerPref(ReaderStore.DEFAULT_BRIGHTNESS_PCT) { it.brightnessPct }

    val volumeKeyPaging: StateFlow<Boolean> = readerPref(false) { it.volumeKeyPaging }

    val animatedPageTurns: StateFlow<Boolean> = readerPref(true) { it.animatedPageTurns }

    val readingSpeedWpm: StateFlow<Int> =
        readerPref(ReaderStore.DEFAULT_READING_SPEED_WPM) { it.readingSpeedWpm }

    // ---------------------------------------------------------------------
    // Read aloud (Wave 5) + sleep timer + auto-scroll coordination.
    // ---------------------------------------------------------------------

    /** Read-aloud voice knobs (percent bands live in the ReaderStore). */
    val speechRate: StateFlow<Int> =
        readerPref(ReaderStore.DEFAULT_SPEECH_RATE) { it.speechRate }

    val speechPitch: StateFlow<Int> =
        readerPref(ReaderStore.DEFAULT_SPEECH_PITCH) { it.speechPitch }

    /** Scrolled-flow auto-scroll speed (px/s; the band lives in the ReaderStore). */
    val autoScrollSpeedPxPerSec: StateFlow<Int> =
        readerPref(ReaderStore.DEFAULT_AUTO_SCROLL_SPEED_PX_PER_SEC) { it.autoScrollSpeedPxPerSec }

    /**
     * One-shot commands the SCREEN executes against its EPUB host (see
     * [ReaderHostCommand] — the host is screen-owned, so the speech loop's
     * context requests, chapter turns, paragraph follows and the sleep
     * timer's auto-scroll stop all ride this channel). Buffered: a command
     * must never drop to a slow collector mid-utterance.
     */
    private val _hostCommands = MutableSharedFlow<ReaderHostCommand>(extraBufferCapacity = 32)
    internal val hostCommands: SharedFlow<ReaderHostCommand> = _hostCommands.asSharedFlow()

    /**
     * The read-aloud loop driver (sentence sequencing, pause/skip). Chapter
     * continuation: the speech context covers the CURRENT chapter only, so
     * on chapter end the loop asks [advanceSpeechChapter], which turns the
     * page (`AdvanceSpeechChapter`) and re-requests the context once the
     * relocation lands (or the [SPEECH_CHAPTER_ADVANCE_TIMEOUT_MS] timeout
     * fires — a book-end `next()` never relocates). Book end is detected by
     * identity, not timing: a context whose first paragraph matches the
     * chapter being spoken means `next()` did not move — finish.
     */
    private val speechController = ReaderSpeechController(
        engine = speechEngine,
        onSpeakParagraph = { _, cfi ->
            _hostCommands.tryEmit(ReaderHostCommand.FollowSpeech(cfi))
        },
        onChapterEnd = { advanceSpeechChapter() },
        onFinished = { clearSpeechSession() },
        onError = { clearSpeechSession() },
    )

    internal val speechState: StateFlow<ReaderSpeechState> = speechController.state

    /**
     * Engine capability straight through. The chrome treats anything but
     * UNAVAILABLE as capable — the Android engine's INITIALIZING window
     * lasts until its first speak (lazy creation), so keying on AVAILABLE
     * would hide the play button behind its own starting condition.
     */
    val speechAvailability: StateFlow<BookSpeechAvailability> get() = speechEngine.availability

    /**
     * The sleep timer. Firing stops read-aloud HERE (the VM owns it) and
     * emits [ReaderHostCommand.SleepTimerFired] so the screen stops its
     * screen-owned auto-scroll.
     */
    private val sleepTimer = ReaderSleepTimer(scope) {
        stopReadAloud()
        _hostCommands.tryEmit(ReaderHostCommand.SleepTimerFired)
    }

    internal val sleepTimerState: StateFlow<ReaderSleepTimerState> = sleepTimer.state

    /** Paragraphs of the chapter being spoken (fed by `onSpeechContext`). */
    private var speechParagraphs: List<EpubSpeechParagraph> = emptyList()

    /** First-paragraph CFI of the chapter being spoken — the book-end identity guard. */
    private var speechChapterKey: String? = null

    /** True between "chapter end" and "context re-requested" (relocation wait window). */
    private var speechAdvancing = false

    /** Percent snapshot at the chapter turn — the empty-chapter continuation guard. */
    private var speechPercentAtAdvance = 0.0

    private var speechAdvanceTimeout: Job? = null

    /**
     * Last rate/pitch PUSHED to the engine. The setters fold onto these,
     * never onto the ReaderStore flow (which lags its own write — reading it
     * per tap would erase the other axis with a stale value), exactly like
     * [pendingFontSizePx].
     */
    private var appliedSpeechRate: Int? = null
    private var appliedSpeechPitch: Int? = null

    private fun configureSpeechEngine(rate: Int? = null, pitch: Int? = null) {
        val nextRate = rate ?: appliedSpeechRate ?: readerStore.reader.value.speechRate
        val nextPitch = pitch ?: appliedSpeechPitch ?: readerStore.reader.value.speechPitch
        appliedSpeechRate = nextRate
        appliedSpeechPitch = nextPitch
        speechEngine.configure(nextRate, nextPitch)
    }

    init {
        // Honest degradation mid-session: if the engine reports UNAVAILABLE
        // while the loop is live (service died, language vanished), end the
        // session instead of hanging on an utterance that never completes.
        scope.launch {
            speechEngine.availability.collect { availability ->
                if (availability == BookSpeechAvailability.UNAVAILABLE && speechState.value.active) {
                    speechController.engineLost()
                }
            }
        }
    }

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

    /**
     * Last override WRITE (theme/font into [PerBookAppearance]) — the
     * perBookAppearance StateFlow lags the DataStore round trip exactly like
     * [pendingFontSizePx] does, so back-to-back override writes must fold
     * onto this, not onto the stale flow value (or the second write would
     * drop the first). Cleared per load and by the per-book toggle.
     */
    private var pendingPerBook: PerBookAppearance? = null

    /** The override a theme/font write should route through (null = global). */
    private fun routingOverride(): PerBookAppearance? = pendingPerBook ?: perBookAppearance.value
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
        pendingPerBook = null
        currentPage = 0
        currentPercent = 0.0
        latestEpubCfi = null
        _currentEpubLocation.value = null
        _selection.value = null
        _pdfOutline.value = emptyList()
        // Read-aloud + sleep timer belong to the previous book's session.
        stopReadAloud()
        sleepTimer.cancel()
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
        pageCache[PageCacheKey(pageIndex, widthPx)]?.let { return it }
        // Back-ends hop to Dispatchers.IO themselves; the bitmap lands in the
        // cache on the caller's context (StateFlow-safe either way).
        val bitmap = doc.renderPage(pageIndex, widthPx)
        return bitmap?.also { pageCache.put(PageCacheKey(pageIndex, widthPx), it) }
    }

    /**
     * Fit-aware render: resolves the page's native size ([BookDocument.pageSize]),
     * folds it through [computeRenderWidth] for [fitMode], multiplies by the
     * live [zoom] (raster-capped at [MAX_PAGE_RASTER_SCALE] — above the cap the
     * caller keeps graphicsLayer-scaling the existing bitmap) and delegates to
     * the width-keyed [requestPage]. The pair-keyed [PageCache] keeps the base
     * and re-rastered entries distinct.
     */
    suspend fun requestPage(
        pageIndex: Int,
        widthPx: Int,
        heightPx: Int,
        fitMode: ReaderFitMode,
        zoom: Float = 1f,
    ): ImageBitmap? {
        val size = document?.pageSize(pageIndex)
        val base = computeRenderWidth(
            fitMode = fitMode,
            surfaceW = widthPx,
            surfaceH = heightPx,
            pageW = size?.width ?: 0f,
            pageH = size?.height ?: 0f,
        )
        val renderWidth = (base * zoom.coerceIn(1f, MAX_PAGE_RASTER_SCALE)).roundToInt().coerceAtLeast(1)
        return requestPage(pageIndex, renderWidth)
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
     * the chapter-scoped pages remaining (the time-left label's source) + the
     * page-start CFI into [currentEpubLocation] (the bookmark/annotation
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
            remainingPages = relocation.remainingPages ?: _currentEpubLocation.value?.remainingPages,
            remainingLocations = relocation.remainingLocations ?: _currentEpubLocation.value?.remainingLocations,
        )
        // Speech chapter turn landed: the context request waits for exactly
        // this relocation (requesting earlier could resolve the OLD chapter).
        if (speechAdvancing) {
            speechAdvanceTimeout?.cancel()
            speechAdvanceTimeout = null
            requestContextAfterAdvance()
        }
        // Sleep timer's END_OF_CHAPTER arm keys on the chapter label.
        sleepTimer.onChapterLabel(relocation.chapterLabel)
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

    /**
     * Theme write. Routes per [routingOverride]: with an override active
     * ("use for this book only"), the change lands in the item's override (the
     * global stays untouched); without one it writes the global. The rendered
     * look is identical either way — the reader consumes [effectiveReaderTheme].
     */
    fun setReaderTheme(theme: ReaderTheme) {
        val itemId = loadedItemId
        val override = routingOverride()
        val nextOverride = override?.copy(theme = theme)?.also { pendingPerBook = it }
        scope.launch {
            runCatching {
                if (itemId != null && nextOverride != null) {
                    readerStore.setPerBookAppearance(itemId, nextOverride)
                } else {
                    readerStore.setReaderTheme(theme)
                }
            }
        }
    }

    /**
     * Font-size stepper; the store clamps the result into its 12..32 px band.
     * Steps into the item's [PerBookAppearance] override when one is active
     * (see [setReaderTheme] for the routing contract).
     */
    fun adjustReaderFontSize(delta: Int) {
        // Step from the last stepped value — the store's StateFlow lags the
        // DataStore write, so reading it per tap drops rapid increments.
        val base = pendingFontSizePx ?: effectiveReaderFontSizePx.value
        val next = (base + delta)
            .coerceIn(ReaderStore.MIN_FONT_SIZE_PX, ReaderStore.MAX_FONT_SIZE_PX)
        pendingFontSizePx = next
        val itemId = loadedItemId
        val override = routingOverride()
        val nextOverride = override?.copy(fontSizePx = next)?.also { pendingPerBook = it }
        scope.launch {
            runCatching {
                if (itemId != null && nextOverride != null) {
                    readerStore.setPerBookAppearance(itemId, nextOverride)
                } else {
                    readerStore.setReaderFontSizePx(next)
                }
            }
        }
    }

    /**
     * The "use for this book only" switch. ON seeds the item's override with
     * the CURRENT effective values (the in-session look does not move); OFF
     * copies the effective values back into the globals and clears the
     * override — again visually seamless, and the next global change flows
     * normally. Direction stays per-book-only by design (its own map).
     */
    fun setUsePerBookAppearance(enabled: Boolean) {
        val itemId = loadedItemId ?: return
        val current = effectiveAppearance(readerStore.reader.value, routingOverride())
        if (enabled) {
            pendingPerBook = PerBookAppearance(theme = current.theme, fontSizePx = current.fontSizePx)
        } else {
            pendingPerBook = null
        }
        // Re-sync the stepper base: a pending step must not leak across the
        // routing switch (the effective font size is the new base either way).
        pendingFontSizePx = current.fontSizePx
        scope.launch {
            runCatching {
                if (enabled) {
                    readerStore.setPerBookAppearance(itemId, pendingPerBook)
                } else {
                    readerStore.setReaderTheme(current.theme)
                    readerStore.setReaderFontSizePx(current.fontSizePx)
                    readerStore.setPerBookAppearance(itemId, null)
                }
            }
        }
    }

    /** Global reflowable typography writes — each clamps into its store band. */
    fun setFontFamily(fontFamily: ReaderFontFamily) {
        scope.launch { runCatching { readerStore.setFontFamily(fontFamily) } }
    }

    fun setLineHeightPct(pct: Int) {
        val clamped = pct.coerceIn(ReaderStore.MIN_LINE_HEIGHT_PCT, ReaderStore.MAX_LINE_HEIGHT_PCT)
        scope.launch { runCatching { readerStore.setLineHeightPct(clamped) } }
    }

    fun setMarginPct(pct: Int) {
        val clamped = pct.coerceIn(ReaderStore.MIN_MARGIN_PCT, ReaderStore.MAX_MARGIN_PCT)
        scope.launch { runCatching { readerStore.setMarginPct(clamped) } }
    }

    fun setJustify(justify: Boolean) {
        scope.launch { runCatching { readerStore.setJustify(justify) } }
    }

    fun setScrollMode(scrollMode: Boolean) {
        scope.launch { runCatching { readerStore.setScrollMode(scrollMode) } }
    }

    /** Brightness veil write (0..100, 100 = no veil); clamped into the store band. */
    fun setBrightnessPct(pct: Int) {
        val clamped = pct.coerceIn(ReaderStore.MIN_BRIGHTNESS_PCT, ReaderStore.MAX_BRIGHTNESS_PCT)
        scope.launch { runCatching { readerStore.setBrightnessPct(clamped) } }
    }

    /** Behavior writes: volume-key paging (Android hardware; no-op elsewhere) + animated page turns. */
    fun setVolumeKeyPaging(enabled: Boolean) {
        scope.launch { runCatching { readerStore.setVolumeKeyPaging(enabled) } }
    }

    fun setAnimatedPageTurns(enabled: Boolean) {
        scope.launch { runCatching { readerStore.setAnimatedPageTurns(enabled) } }
    }

    /** Reading-speed write for the time-left estimate; clamped into its 100..1000 band. */
    fun setReadingSpeedWpm(wpm: Int) {
        val clamped = wpm.coerceIn(ReaderStore.MIN_READING_SPEED_WPM, ReaderStore.MAX_READING_SPEED_WPM)
        scope.launch { runCatching { readerStore.setReadingSpeedWpm(clamped) } }
    }

    /** Auto-scroll speed write (px/s); clamped into its 20..120 band. */
    fun setAutoScrollSpeedPxPerSec(pxPerSec: Int) {
        val clamped = pxPerSec.coerceIn(ReaderStore.MIN_AUTO_SCROLL_SPEED_PX_PER_SEC, ReaderStore.MAX_AUTO_SCROLL_SPEED_PX_PER_SEC)
        scope.launch { runCatching { readerStore.setAutoScrollSpeedPxPerSec(clamped) } }
    }

    // ---------------------------------------------------------------------
    // Read aloud (reflowable only — the speech context is an EPUB concept).
    // ---------------------------------------------------------------------

    /** Speech-rate write; clamped into its 50..200 band and applied to the engine live. */
    fun setSpeechRate(pct: Int) {
        val clamped = pct.coerceIn(ReaderStore.MIN_SPEECH_RATE, ReaderStore.MAX_SPEECH_RATE)
        configureSpeechEngine(rate = clamped)
        scope.launch { runCatching { readerStore.setSpeechRate(clamped) } }
    }

    /** Speech-pitch write; clamped into its 50..200 band and applied to the engine live. */
    fun setSpeechPitch(pct: Int) {
        val clamped = pct.coerceIn(ReaderStore.MIN_SPEECH_PITCH, ReaderStore.MAX_SPEECH_PITCH)
        configureSpeechEngine(pitch = clamped)
        scope.launch { runCatching { readerStore.setSpeechPitch(clamped) } }
    }

    /**
     * Starts read-aloud at the current position: configures the engine with
     * the persisted rate/pitch, marks the session live and asks the host for
     * the current chapter's paragraphs (the answer lands in
     * [onSpeechContext]). Unavailable engines (desktop/web) and paged books
     * are a silent no-op — their UI never offers the button.
     */
    fun startReadAloud() {
        val ready = _uiState.value as? BookReaderUiState.Ready ?: return
        if (ready.content !is ReadyContent.Reflowable) return
        if (speechEngine.availability.value == BookSpeechAvailability.UNAVAILABLE) return
        configureSpeechEngine()
        clearSpeechSession()
        speechController.awaitContext()
        _hostCommands.tryEmit(ReaderHostCommand.RequestSpeechContext(latestEpubCfi))
    }

    /** The chrome's single play/pause button: start → pause → resume. */
    fun toggleReadAloud() {
        val state = speechState.value
        when {
            !state.active -> startReadAloud()
            state.paused -> speechController.resume()
            else -> speechController.pause()
        }
    }

    fun pauseReadAloud() = speechController.pause()

    fun resumeReadAloud() = speechController.resume()

    fun skipSpeechForward() = speechController.skipForward()

    fun skipSpeechBack() = speechController.skipBack()

    /** Ends the session (user stop, screen dispose, sleep timer, engine loss). */
    fun stopReadAloud() {
        clearSpeechSession()
        speechController.stop()
    }

    /**
     * Host answer to a speech-context request (screen-forwarded
     * `onSpeechContext`). Continuation protocol: a context whose first
     * paragraph repeats the chapter being spoken means the chapter turn did
     * not relocate (book end) → finish; an empty context mid-book means a
     * chapter with nothing speakable → keep advancing (the percent snapshot
     * taken at the turn breaks the loop once the position stops moving);
     * anything else starts the loop on the new chapter.
     */
    internal fun onSpeechContext(paragraphs: List<EpubSpeechParagraph>) {
        if (!speechState.value.active) return // late answer to a stopped session
        speechParagraphs = paragraphs
        val firstCfi = paragraphs.firstOrNull()?.cfi
        if (firstCfi != null && firstCfi == speechChapterKey) {
            // next() did not relocate — the same chapter came back. Book end.
            speechController.finish()
            return
        }
        if (paragraphs.isEmpty() && currentPercent == speechPercentAtAdvance) {
            // Nothing speakable AND the position never moved: advancing again
            // cannot help (book end, or a static rendition) — finish instead
            // of looping.
            speechController.finish()
            return
        }
        speechChapterKey = firstCfi
        speechController.start(paragraphs)
    }

    /**
     * Chapter-end continuation: remember the chapter identity + percent,
     * turn the page, then re-request the context once the turn's relocation
     * arrives ([onEpubRelocated] releases the wait) or the timeout fires
     * (a book-end turn never relocates — the identity guard finishes there).
     */
    private fun advanceSpeechChapter() {
        speechChapterKey = speechParagraphs.firstOrNull()?.cfi
        speechPercentAtAdvance = currentPercent
        speechAdvancing = true
        _hostCommands.tryEmit(ReaderHostCommand.AdvanceSpeechChapter)
        speechAdvanceTimeout?.cancel()
        speechAdvanceTimeout = scope.launch {
            delay(SPEECH_CHAPTER_ADVANCE_TIMEOUT_MS)
            requestContextAfterAdvance()
        }
    }

    private fun requestContextAfterAdvance() {
        if (!speechAdvancing) return
        speechAdvancing = false
        _hostCommands.tryEmit(ReaderHostCommand.RequestSpeechContext(null))
    }

    /** Clears the bookkeeping (NOT the controller state — callers drive that). */
    private fun clearSpeechSession() {
        speechParagraphs = emptyList()
        speechChapterKey = null
        speechAdvancing = false
        speechAdvanceTimeout?.cancel()
        speechAdvanceTimeout = null
    }

    // ---------------------------------------------------------------------
    // Sleep timer
    // ---------------------------------------------------------------------

    internal fun startSleepTimer(option: ReaderSleepOption) = sleepTimer.start(option)

    fun cancelSleepTimer() = sleepTimer.cancel()

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
        // Read-aloud teardown: silence any live utterance and release the
        // engine's platform resources (it lazily re-creates on the next
        // speak, so a config-change recreation is unaffected).
        stopReadAloud()
        sleepTimer.cancel()
        speechEngine.shutdown()
        document?.close()
        document = null
    }

    companion object {
        private const val PROGRESS_DEBOUNCE_MS = 800L

        /**
         * How long the speech loop waits for the chapter turn's relocation
         * before re-requesting the context anyway — a book-end `next()` never
         * relocates, and the same-chapter identity guard finishes there.
         */
        private const val SPEECH_CHAPTER_ADVANCE_TIMEOUT_MS = 1_500L
    }
}
