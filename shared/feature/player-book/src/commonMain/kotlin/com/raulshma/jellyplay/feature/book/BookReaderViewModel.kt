package com.raulshma.jellyplay.feature.book

import androidx.compose.ui.graphics.ImageBitmap
import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.repository.ReaderAnnotationColor
import com.raulshma.jellyplay.core.data.repository.ReaderAnnotationStyle
import com.raulshma.jellyplay.core.data.repository.ReaderAnnotationsRepository
import com.raulshma.jellyplay.core.data.repository.ReaderAnnotation
import com.raulshma.jellyplay.core.data.repository.ReaderBookmark
import com.raulshma.jellyplay.core.data.playback.focus.FocusClaimState
import com.raulshma.jellyplay.core.data.playback.focus.FocusOutcome
import com.raulshma.jellyplay.core.data.playback.focus.NoopPlaybackFocus
import com.raulshma.jellyplay.core.data.playback.focus.PlaybackFocus
import com.raulshma.jellyplay.core.data.playback.focus.PlaybackSurfaceId
import com.raulshma.jellyplay.core.datastore.reader.ReadingDirection
import com.raulshma.jellyplay.core.data.repository.BookTocCacheRepository
import com.raulshma.jellyplay.core.data.repository.NoopBookTocCacheRepository
import com.raulshma.jellyplay.core.model.BookFormat
import com.raulshma.jellyplay.core.model.BookProgressPolicy
import com.raulshma.jellyplay.core.model.BookTocEntry
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.feature.book.epub.EpubEvent
import com.raulshma.jellyplay.feature.book.epub.EpubRelocation
import com.raulshma.jellyplay.feature.book.epub.EpubSpeechParagraph
import com.raulshma.jellyplay.feature.book.epub.EpubTocItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Koin-owned state owner for the book reader (video-player conventions:
 * sealed uiState, no writes from non-VM modules). One screen lifetime loads
 * one item through [BookSessionLoader] — MediaDetail → [BookFormat.fromPath]
 * → [BookContentResolver] (offline download first, else the reader-cache
 * fetch) → then by format: PAGED books go through [BookDocumentOpener] into
 * paged state, while REFLOWABLE (EPUB) books skip the document layer
 * entirely — the screen's WebView host renders the resolved file, and
 * progress is a percent (jellyfin-web interop: `ticks = floor(percent ×
 * 10_000_000)`).
 *
 * Progress reporting is delegated to [ReaderProgressReporter] (debounce,
 * final-flush idempotence across onDispose+onCleared, the flush-scope
 * escape, the last-CFI ride-along); the VM keeps only the payload lambdas
 * that read its session state. `final = true` reports go to
 * `PlaybackRepository.reportBookProgress` immediately on dispose.
 * Marking a book read stays a user action (see docs/book-reader.md): nothing
 * here auto-sets the played flag at the last page or a percent threshold.
 *
 * Reader marks (Wave 3) live in [ReaderAnnotationsRepository] and are exposed
 * per loaded item through [bookmarks]/[annotations] (re-subscribed per load);
 * the toggle/jump COORDINATION lives here while the EPUB host stays
 * screen-owned — [jumpToBookmark] only resolves the paged path, the screen
 * drives `host.goToCfi` for reflowable jumps (it owns the host). The
 * paged-vs-reflowable encoding of a row is [ReaderBookmarkCodec]'s one rule.
 * Exact reflowable resume rides the ReaderStore last-CFI map:
 * [ReadyContent.Reflowable.resumeCfi] carries the stored anchor to the
 * screen (which jumps once the host is READY), and every debounced progress
 * flush re-persists the current anchor.
 *
 * Read aloud + sleep timer (Wave 5) follow the same split: the speech LOOP
 * ([ReaderSpeechController]) and the timer ([ReaderSleepTimer]) live here
 * over the [BookSpeechEngine] seam, while every host touch runs through the
 * attached [ReaderSessionPort] (the screen's reflowable session, bound by
 * the reader composition): the speech chapter continuation executes
 * controller → host directly, and the paragraph follow + the sleep timer's
 * auto-scroll stop are plain calls on the port — the one-shot
 * `hostCommands` channel and its screen collector are gone.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BookReaderViewModel(
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
    /**
     * The reader preference choreography ([ReaderPreferences] — snapshot +
     * commands + write-through). Every pref write routes through it; the VM
     * keeps only the session-stateful halves (direction gate, engine
     * configure).
     */
    val preferences: ReaderPreferences,
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
     * Paragraph read-aloud engine (Android TTS; desktop degrades to the
     * neutral [NoopBookSpeechEngine] and the reader hides its speech UI).
     * Defaulted so tests and non-DI constructions compile unchanged.
     */
    private val speechEngine: BookSpeechEngine = NoopBookSpeechEngine,
    /**
     * Local book-TOC cache (the `book_toc_cache` table the media-detail
     * screen reads). Every successful open writes what became known — page
     * counts and/or TOC entries — so the detail screen's "Contents" renders
     * without re-parsing the file. Neutral default keeps tests compiling.
     */
    private val tocCacheRepository: BookTocCacheRepository = NoopBookTocCacheRepository(),
    /**
     * Cross-player exclusivity (PlaybackFocus): read-aloud claims the audio
     * floor so background music pauses instead of mixing with the spoken
     * word (ADR-0004). Defaulted to the vacuous [NoopPlaybackFocus] so tests
     * and platforms without a binding keep single-player semantics.
     */
    private val playbackFocus: PlaybackFocus = NoopPlaybackFocus,
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
     * The reader preference snapshot ([ReaderPreferences.snapshot]) — every
     * knob value the screen and sheets render, the per-book override, the
     * per-item direction and the EFFECTIVE appearance fold (override ?:
     * global), in one emission. The individual pref flows this replaced were
     * a 16-member `map + stateIn` re-exposure of the slice field-by-field.
     */
    val prefs: StateFlow<ReaderPrefsSnapshot> get() = preferences.snapshot

    /**
     * The item whose marks + appearance override are exposed — the key of the
     * per-item flows. A load re-points it, so [bookmarks]/[annotations]
     * re-subscribe to the new item's streams (flatMapLatest drops the stale
     * subscription) and [preferences.attach] re-points the pref routing.
     */
    private val marksItemId = MutableStateFlow<String?>(null)

    /** Read aloud (Wave 5) + sleep timer + auto-scroll coordination. */

    /**
     * The screen session seam: the reflowable composition attaches its
     * [ReflowableReaderSession] here, giving the speech loop and the sleep
     * timer a direct line to the session-owned host (the chapter turn and
     * the context request) and to the screen-owned choreography (paragraph
     * follow, auto-scroll stop). Null until the reader composes; a disposed
     * screen's session keeps the port but nulls its own host, so every call
     * degrades to a no-op — the old channel-without-collector semantics.
     */
    private var readerSession: ReaderSessionPort? = null

    /** The reader composition's session binding (one screen, one session). */
    internal fun attachReaderSession(session: ReaderSessionPort) {
        readerSession = session
    }

    /**
     * The read-aloud loop driver (sentence sequencing, pause/skip). Chapter
     * continuation: the speech context covers the CURRENT chapter only, so
     * on chapter end the loop turns the page itself through the attached
     * session's host and re-requests the context once the relocation lands
     * (or the chapter-advance timeout fires — a book-end
     * `next()` never relocates). Book end is detected by identity, not
     * timing: a context whose first paragraph matches the chapter being
     * spoken means `next()` did not move — finish.
     */
    private val speechController = ReaderSpeechController(
        engine = speechEngine,
        scope = scope,
        host = { readerSession?.host },
        onSpeakParagraph = { _, cfi -> readerSession?.followSpeech(cfi) },
        onFinished = {},
        onError = {},
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
     * tells the attached session so it stops its screen-owned auto-scroll.
     */
    private val sleepTimer = ReaderSleepTimer(scope) {
        stopReadAloud()
        readerSession?.sleepTimerFired()
    }

    internal val sleepTimerState: StateFlow<ReaderSleepTimerState> = sleepTimer.state

    /**
     * Last rate/pitch PUSHED to the engine, resolved from the preference
     * snapshot ([ReaderPreferences] write-through keeps it synchronous — the
     * value a setter just commanded is visible here immediately).
     */
    private fun configureSpeechEngine(rate: Int? = null, pitch: Int? = null) {
        val slice = preferences.snapshot.value.global
        speechEngine.configure(rate ?: slice.speechRate, pitch ?: slice.speechPitch)
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
        // Displaced claimant (newest-wins, ADR-0004): the user started music
        // (or later, video) while read-aloud held the floor — pause the loop.
        // Speech NEVER auto-resumes; the module ignores regains too, so the
        // user's next play tap is the only way back.
        scope.launch {
            playbackFocus.claimState.collect { state ->
                val holder = (state as? FocusClaimState.Held)?.holder ?: return@collect
                if (holder != PlaybackSurfaceId.READ_ALOUD &&
                    speechState.value.active &&
                    !speechState.value.paused
                ) {
                    speechController.pause()
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

    /**
     * The reflowable reader's folded position — the ONE percent carrier the
     * screen reads. Null before the first Ready; seeded with the boot resume
     * percent in [openBook]; folded from both position event kinds through
     * [foldEpubPosition] afterwards.
     */
    private val _currentEpubLocation = MutableStateFlow<EpubLocation?>(null)
    val currentEpubLocation: StateFlow<EpubLocation?> = _currentEpubLocation.asStateFlow()

    /** The live text selection (null while nothing is selected). */
    private val _selection = MutableStateFlow<ReaderSelection?>(null)
    val selection: StateFlow<ReaderSelection?> = _selection.asStateFlow()

    /** The paged book's outline (PDF only; empty until parsed / for CBZ+CBR). */
    private val _pdfOutline = MutableStateFlow<List<PdfOutlineNode>>(emptyList())
    val pdfOutline: StateFlow<List<PdfOutlineNode>> = _pdfOutline.asStateFlow()

    private var loadedItemId: String? = null
    private var loadedBookFormat: BookFormat? = null
    /** Deep-link destination carried from [load] into [openBook] (cleared there). */
    private var pendingJumpHref: String? = null
    private var pendingJumpPage: Int? = null
    private var document: BookDocument? = null
    private val pageCache = PageCache<ImageBitmap>()
    private var currentPage = 0

    /**
     * Latest folded percent (reflowable only; seeded from the resume math,
     * refreshed by both position event kinds), 0.0..1.0 — the progress
     * report payload's source. The screen never reads this scalar; it reads
     * [currentEpubLocation].
     */
    private var currentPercent = 0.0

    /** Latest relocated CFI (reflowable only) — the exact-resume / bookmark anchor. */
    private var latestEpubCfi: String? = null

    /**
     * The position-report choreography (debounce, final-flush idempotence,
     * flush-scope escape, last-CFI ride-along). Its payload lambdas read the
     * session state below — the reporter owns the when, the VM keeps the what.
     */
    private val progressReporter = ReaderProgressReporter(
        scope = scope,
        flushScope = flushScope,
        itemId = { loadedItemId },
        buildReport = { item ->
            val ready = _uiState.value as? BookReaderUiState.Ready
            if (ready == null) {
                null
            } else {
                ReaderProgressReport(
                    itemId = item,
                    ticks = when (val content = ready.content) {
                        is ReadyContent.Paged -> BookProgressPolicy.pageToTicks(currentPage)
                        is ReadyContent.Reflowable -> BookProgressPolicy.percentToTicks(currentPercent)
                    },
                    cfi = (ready.content as? ReadyContent.Reflowable)?.let { latestEpubCfi },
                )
            }
        },
        reportProgress = { item, ticks, final ->
            playbackRepository.reportBookProgress(item, ticks, final = final)
        },
        setLastCfi = { item, cfi -> preferences.setLastCfi(item, cfi) },
    )

    private var directionJob: Job? = null
    private var loadJob: Job? = null

    /**
     * True once the user has a persisted per-book direction for this item.
     * That choice always outranks the direction an EPUB reports about
     * itself — see [onEpubDirection].
     */
    private var userDirectionPinned = false

    /**
     * The one-book open sequence (detail fetch → format resolve + probe
     * fallback → content resolve → resume math → document open), owning the
     * TOC-cache write-through. Built from this VM's constructor seams plus
     * the three hooks that touch VM state — the loader surfaces
     * [BookSessionOutcome]s and never writes the ui state.
     */
    private val sessionLoader = BookSessionLoader(
        scope = scope,
        mediaRepository = mediaRepository,
        playbackRepository = playbackRepository,
        contentResolver = contentResolver,
        documentOpener = documentOpener,
        formatProbe = formatProbe,
        pdfOutlineParser = pdfOutlineParser,
        tocCacheRepository = tocCacheRepository,
        storedResumeCfi = { itemId -> preferences.lastCfi(itemId) },
        onSessionReset = ::resetForNewSession,
        onDownloadProgress = { progress ->
            (_uiState.value as? BookReaderUiState.Loading)?.let {
                _uiState.value = it.copy(progress = progress)
            }
        },
        onFormatResolved = { format -> loadedBookFormat = format },
    )

    /** Screen entry point (audio-player convention: the screen triggers the load, the VM holds no itemId ctor dep). */
    fun load(itemId: String, jumpHref: String? = null, jumpPage: Int? = null) {
        if (loadedItemId == itemId && _uiState.value !is BookReaderUiState.Idle) return
        loadedItemId = itemId
        marksItemId.value = itemId
        // Pref routing (per-book override/direction) re-points at the new item;
        // any optimistic per-book state from the previous book dies here.
        preferences.attach(itemId)
        // One-shot deep-link destination (detail "Contents" tap). Consumed
        // (and cleared) by [openBook] — a later plain re-load must resume.
        pendingJumpHref = jumpHref
        pendingJumpPage = jumpPage
        userDirectionPinned = false
        // One collector per item — a re-load must not stack a second one.
        // The persisted direction (with its pin flag) rides the pref snapshot.
        directionJob?.cancel()
        directionJob = scope.launch {
            preferences.snapshot.collect { snap ->
                // A persisted user choice always wins; without one, keep
                // whatever the book itself reported or the LTR default.
                if (snap.directionPinned) {
                    userDirectionPinned = true
                    _readingDirection.value = snap.direction
                }
            }
        }
        // Single-flight: a re-load cancels the in-flight fetch/open (which also
        // aborts the streaming download) before starting the next one.
        loadJob?.cancel()
        loadJob = scope.launch { openBook(itemId) }
    }

    /**
     * Session-scoped state reset for a re-load (new book, same VM): the
     * previous book's document, cached page bitmaps and per-book position
     * tracking must not leak into the new reader. Invoked by
     * [BookSessionLoader.open] at exactly the old position — right after the
     * loading veil, before the detail fetch. (Per-book pref state dies in
     * [preferences.attach].)
     */
    private fun resetForNewSession() {
        document?.close()
        document = null
        pageCache.clear()
        currentPage = 0
        currentPercent = 0.0
        latestEpubCfi = null
        _currentEpubLocation.value = null
        _selection.value = null
        _pdfOutline.value = emptyList()
        // Read-aloud + sleep timer belong to the previous book's session.
        stopReadAloud()
        sleepTimer.cancel()
    }

    /**
     * Thin caller over [BookSessionLoader.open]: raises the loading veil,
     * consumes the pending deep-link destination, then applies the returned
     * [BookSessionOutcome] — every uiState write and every session-state
     * mutation (document, page cache, resume anchors, TOC cache) happens
     * here, in the order the old inlined body used.
     */
    private suspend fun openBook(itemId: String) {
        _uiState.value = BookReaderUiState.Loading()
        val jump = PendingJump(pendingJumpHref, pendingJumpPage)
        pendingJumpHref = null
        pendingJumpPage = null
        when (val session = sessionLoader.open(itemId, jump)) {
            is BookSessionOutcome.Reflowable -> {
                currentPercent = session.resumePercent
                // The boot carrier: the location flow leaves the gate already
                // carrying the resume percent (empty chapter label, no anchor —
                // exactly what the screen's old local percent fallback showed),
                // so the screen has ONE percent source from the first Ready
                // frame on. Seeded BEFORE the Ready write so no collector can
                // observe a location-less Ready reflowable book.
                _currentEpubLocation.value = EpubLocation(
                    percent = session.resumePercent,
                    chapterLabel = "",
                    cfi = null,
                )
                _uiState.value = BookReaderUiState.Ready(
                    title = session.title,
                    content = ReadyContent.Reflowable(
                        bookFile = session.bookFile,
                        resumePercent = session.resumePercent,
                        resumeCfi = session.resumeCfi,
                        jumpHref = session.jumpHref,
                    ),
                )
            }
            is BookSessionOutcome.Paged -> {
                document = session.document
                currentPage = session.resumePage
                pageCache.onPageChanged(session.resumePage)
                _uiState.value = BookReaderUiState.Ready(
                    title = session.title,
                    content = ReadyContent.Paged(
                        pageCount = session.document.pageCount,
                        currentPage = session.resumePage,
                        format = session.format,
                    ),
                )
                // TOC cache write-through AFTER the Ready write (the old
                // ordering): the page count is known the moment the document
                // opens — for a comic that is the whole story (no TOC), for a
                // PDF the outline rides the parse callback.
                sessionLoader.startOpenTocCache(
                    itemId = itemId,
                    format = session.format,
                    file = session.file,
                    pageCount = session.document.pageCount,
                    onOutlineParsed = { nodes -> _pdfOutline.value = nodes },
                )
            }
            is BookSessionOutcome.Failed -> _uiState.value = when (val error = session.error) {
                BookOpenError.CannotOpen -> BookReaderUiState.Error(BookReaderUiState.ErrorReason.CannotOpen)
                is BookOpenError.UnsupportedFormat -> BookReaderUiState.Error(
                    reason = BookReaderUiState.ErrorReason.UnsupportedFormat,
                    detail = error.fileExtension,
                )
            }
        }
    }

    /**
     * The EPUB host's TOC event (arriving through [onEpubEvent]). The reader
     * UI keeps its own copy — this only feeds the detail screen's cache (flat
     * labels, level 0: epub.js's toc carries no nesting).
     */
    private fun onEpubTocReady(items: List<EpubTocItem>) {
        if (items.isEmpty()) return
        val format = loadedBookFormat ?: return
        val itemId = loadedItemId ?: return
        val entries = items.map { BookTocEntry(label = it.label, href = it.href, page = null, level = 0) }
        sessionLoader.cacheToc(itemId, format, pageCount = 0, entries = entries)
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
        progressReporter.schedule()
    }

    /** Keyboard / tap-zone paging. The pager follows [currentPage] via the screen's sync effect. */
    fun nextPage() {
        onPageChanged(currentPage + 1)
    }

    fun previousPage() {
        onPageChanged(currentPage - 1)
    }

    /**
     * The ViewModel's single consumption point for the EPUB event channel —
     * one funnel, one `when`, dispatching to the private per-event folds
     * below. Screen-owned kinds (the boot status veil, the search session,
     * tap/swipe navigation, auto-scroll stops) arrive here too — the screen's
     * seam forwards every event — and deliberately fold to Unit: their state
     * is screen-local by the ownership split, and this funnel is the place a
     * new event kind is forced to declare its owner.
     */
    internal fun onEpubEvent(event: EpubEvent) {
        when (event) {
            is EpubEvent.Percent -> foldEpubPosition(percent = event.value, relocation = null)
            is EpubEvent.Status -> Unit // screen-local boot veil state
            is EpubEvent.Direction -> onEpubDirection(event.direction)
            is EpubEvent.Toc -> onEpubTocReady(event.items)
            is EpubEvent.Relocated -> foldEpubPosition(percent = event.relocation.percent, relocation = event.relocation)
            is EpubEvent.Tap -> Unit // screen-local navigation (readerNavDecision)
            is EpubEvent.Swipe -> Unit // screen-local navigation (readerNavDecision)
            is EpubEvent.Selected -> onEpubSelection(event.cfi, event.text)
            EpubEvent.SelectionCleared -> onEpubSelectionCleared()
            is EpubEvent.SearchResults -> Unit // screen-local search session
            is EpubEvent.SpeechContext -> onSpeechContext(event.paragraphs)
            EpubEvent.AutoScrollStopped -> Unit // screen-local auto-scroll controller
            is EpubEvent.DisplayError -> Unit // reader.js keeps the current page — no VM fallback
        }
    }

    /**
     * The ONE reflowable position fold — both position event kinds meet here,
     * so the clamp + speech-controller report + debounced-report schedule
     * sequence exists exactly once. The bare `percent` event (reader.js posts
     * it at boot, when locations finish generating, and right before every
     * relocation, which carries the same value in richer company) passes
     * [relocation] as null and only refreshes the location's percent; the
     * `relocated` event additionally folds the chapter label, the
     * chapter/book-scoped pages remaining and the page-start CFI (the
     * bookmark/annotation anchor source) into [currentEpubLocation] and
     * releases the speech chapter-turn wait. The boot carrier: [openBook]
     * seeds the location with the resume percent, so the screen reads the
     * percent from [currentEpubLocation] alone. Private: [EpubRelocation] is
     * the module-private host protocol type.
     */
    private fun foldEpubPosition(percent: Double?, relocation: EpubRelocation?) {
        val ready = _uiState.value as? BookReaderUiState.Ready ?: return
        if (ready.content !is ReadyContent.Reflowable) return
        percent?.let {
            currentPercent = it.coerceIn(0.0, 1.0)
            speechController.reportPercent(currentPercent)
        }
        relocation?.cfi?.let { latestEpubCfi = it }
        _currentEpubLocation.value = if (relocation != null) {
            EpubLocation(
                percent = currentPercent,
                chapterLabel = relocation.chapterLabel,
                cfi = latestEpubCfi,
                remainingPages = relocation.remainingPages ?: _currentEpubLocation.value?.remainingPages,
                remainingLocations = relocation.remainingLocations ?: _currentEpubLocation.value?.remainingLocations,
                chapterHref = relocation.href ?: _currentEpubLocation.value?.chapterHref,
            )
        } else {
            _currentEpubLocation.value?.copy(percent = currentPercent)
        }
        if (relocation != null) {
            // Speech chapter turn landed: the controller releases its relocation
            // wait here (requesting earlier could resolve the OLD chapter).
            speechController.onRelocated()
            // Sleep timer's END_OF_CHAPTER arm keys on the chapter label.
            sleepTimer.onChapterLabel(relocation.chapterLabel)
        }
        progressReporter.schedule()
    }

    /** Text selected inside the WebView host — drives the selection action row. */
    private fun onEpubSelection(cfi: String, text: String) {
        _selection.value = ReaderSelection(cfi = cfi, text = text)
    }

    private fun onEpubSelectionCleared() {
        _selection.value = null
    }

    /**
     * The book's reported page-flow direction, in-memory for this reading.
     * Only a default: once the user has a persisted per-book choice
     * ([setReadingDirection]), that wins for the session's remainder.
     */
    private fun onEpubDirection(direction: ReadingDirection) {
        if (userDirectionPinned) return
        _readingDirection.value = direction
    }

    // ---------------------------------------------------------------------
    // Read aloud (reflowable only — the speech context is an EPUB concept).
    // ---------------------------------------------------------------------

    /** Speech-rate write; clamped by the preference module, applied to the engine live. */
    fun setSpeechRate(pct: Int) {
        preferences.setSpeechRate(pct)
        // The snapshot is synchronous (write-through), so the clamped value
        // is already current for the other axis.
        configureSpeechEngine(rate = preferences.snapshot.value.global.speechRate)
    }

    /** Speech-pitch write; clamped by the preference module, applied to the engine live. */
    fun setSpeechPitch(pct: Int) {
        preferences.setSpeechPitch(pct)
        configureSpeechEngine(pitch = preferences.snapshot.value.global.speechPitch)
    }

    /**
     * Starts read-aloud at the current position: configures the engine with
     * the persisted rate/pitch, marks the session live and asks the host
     * (through the attached session's seam) for the current chapter's
     * paragraphs at the exact resume anchor — the answer lands in
     * [onSpeechContext]. Unavailable engines (desktop) and paged books
     * are a silent no-op — their UI never offers the button.
     */
    fun startReadAloud() {
        val ready = _uiState.value as? BookReaderUiState.Ready ?: return
        if (ready.content !is ReadyContent.Reflowable) return
        if (speechEngine.availability.value == BookSpeechAvailability.UNAVAILABLE) return
        // Claim the floor FIRST — a refused claim must not leave the session
        // live but silent (the chrome would show a playing chrome over
        // nothing). Claim → GRANTED → speak.
        if (playbackFocus.acquire(PlaybackSurfaceId.READ_ALOUD) != FocusOutcome.Granted) return
        configureSpeechEngine()
        speechController.awaitContext(latestEpubCfi)
    }

    /** The chrome's single play/pause button: start → pause → resume. */
    fun toggleReadAloud() {
        val state = speechState.value
        when {
            !state.active -> startReadAloud()
            state.paused -> resumeReadAloud()
            else -> pauseReadAloud()
        }
    }

    /** User pause: the claim STAYS held (invariant — OS focus churn between sentences is churn). */
    fun pauseReadAloud() = speechController.pause()

    /** User resume: re-acquire (a suspended claim re-requests its OS seat). */
    fun resumeReadAloud() {
        if (playbackFocus.acquire(PlaybackSurfaceId.READ_ALOUD) != FocusOutcome.Granted) return
        speechController.resume()
    }

    fun skipSpeechForward() = speechController.skipForward()

    fun skipSpeechBack() = speechController.skipBack()

    /** Ends the session (user stop, screen dispose, sleep timer, engine loss). */
    fun stopReadAloud() {
        speechController.stop()
        playbackFocus.release(PlaybackSurfaceId.READ_ALOUD)
    }

    /**
     * Host answer to a speech-context request (arriving through
     * [onEpubEvent]). Continuation protocol: a context whose first
     * paragraph repeats the chapter being spoken means the chapter turn did
     * not relocate (book end) → finish; an empty context mid-book means a
     * chapter with nothing speakable → keep advancing (the percent snapshot
     * taken at the turn breaks the loop once the position stops moving);
     * anything else starts the loop on the new chapter.
     */
    private fun onSpeechContext(paragraphs: List<EpubSpeechParagraph>) {
        speechController.onSpeechContext(paragraphs, currentPercent)
    }

    // ---------------------------------------------------------------------
    // Sleep timer
    // ---------------------------------------------------------------------

    internal fun startSleepTimer(option: ReaderSleepOption) = sleepTimer.start(option)

    fun cancelSleepTimer() = sleepTimer.cancel()

    fun toggleControls() {
        val ready = _uiState.value as? BookReaderUiState.Ready ?: return
        _uiState.value = ready.copy(showControls = !ready.showControls)
    }

    fun setReadingDirection(direction: ReadingDirection) {
        userDirectionPinned = true
        _readingDirection.value = direction
        preferences.setReadingDirection(direction)
    }

    // ---------------------------------------------------------------------
    // Reader marks (bookmarks + annotations) — local-first per ADR 0003.
    // ---------------------------------------------------------------------

    /**
     * The current reading position shaped for [ReaderBookmarkCodec] — the
     * paged-vs-reflowable branch keys on the live session's content kind,
     * exactly the branch the codec's encode/match rules key on the row.
     */
    private fun currentBookmarkLocation(ready: BookReaderUiState.Ready): ReaderBookmarkCodec.Location =
        when (ready.content) {
            is ReadyContent.Paged -> ReaderBookmarkCodec.Location.Paged(currentPage)
            is ReadyContent.Reflowable -> {
                val location = _currentEpubLocation.value
                ReaderBookmarkCodec.Location.Reflowable(
                    percent = currentPercent,
                    cfi = location?.cfi,
                    chapterLabel = location?.chapterLabel.orEmpty(),
                )
            }
        }

    /**
     * The bookmark sitting at the current position, if any — the toggle
     * target and the top-bar icon's filled state. The position rule (same
     * encoded page / same CFI / legacy percent fallback) is
     * [ReaderBookmarkCodec.matches]; item identity stays this caller's
     * predicate.
     */
    private fun bookmarkAtCurrentPosition(): ReaderBookmark? {
        val itemId = loadedItemId ?: return null
        val ready = _uiState.value as? BookReaderUiState.Ready ?: return null
        val location = currentBookmarkLocation(ready)
        return bookmarks.value.firstOrNull { it.itemId == itemId && ReaderBookmarkCodec.matches(it, location) }
    }

    fun hasBookmarkAtCurrentPosition(): Boolean = bookmarkAtCurrentPosition() != null

    /**
     * Bookmark the current position, or remove the bookmark already sitting
     * there (toggle semantics — one control, no separate delete in the
     * chrome). The row's encoding is [ReaderBookmarkCodec.encode]'s one rule:
     * paged rows are page ticks with a null CFI and empty label, reflowable
     * rows are the relocation percent plus the exact CFI + chapter label.
     */
    fun toggleBookmarkAtCurrentPosition() {
        val itemId = loadedItemId ?: return
        val ready = _uiState.value as? BookReaderUiState.Ready ?: return
        val existing = bookmarkAtCurrentPosition()
        scope.launch {
            runCatchingRethrowingCancellation {
                if (existing != null) {
                    annotationsRepository.removeBookmark(existing.id)
                } else {
                    val draft = ReaderBookmarkCodec.encode(currentBookmarkLocation(ready))
                    annotationsRepository.addBookmark(
                        itemId = itemId,
                        positionTicks = draft.positionTicks,
                        cfi = draft.cfi,
                        chapterLabel = draft.chapterLabel,
                    )
                }
            }
        }
    }

    fun deleteBookmark(id: Long) {
        scope.launch { runCatchingRethrowingCancellation { annotationsRepository.removeBookmark(id) } }
    }

    /**
     * Jump to a bookmark. Paged books resolve through the pager ([onPageChanged]
     * scrolls + reports); reflowable jumps are HOST-coordinated — the screen
     * calls `host.goToCfi(bookmark.cfi)` itself (see
     * [ReaderBookmarkCodec.isJumpable]), and a null-CFI reflowable row
     * has no fallback (the host exposes no display-by-percent after boot), so
     * there is deliberately nothing to do here for that case.
     */
    fun jumpToBookmark(bookmark: ReaderBookmark) {
        val ready = _uiState.value as? BookReaderUiState.Ready ?: return
        if (ready.content is ReadyContent.Paged) {
            onPageChanged(ReaderBookmarkCodec.pagerJumpPage(bookmark))
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
            runCatchingRethrowingCancellation {
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
            runCatchingRethrowingCancellation { annotationsRepository.updateAnnotation(id, note, color, style) }
        }
    }

    fun deleteAnnotation(id: Long) {
        scope.launch { runCatchingRethrowingCancellation { annotationsRepository.deleteAnnotation(id) } }
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
     * false: purging per page turn would thrash the caches. The choreography
     * (idempotence across the onDispose + onCleared double fire, the
     * flush-scope escape, the last-CFI ride-along) is [ReaderProgressReporter].
     */
    fun reportNow(final: Boolean = true) {
        progressReporter.reportNow(final)
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
}
