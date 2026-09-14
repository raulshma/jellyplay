package com.raulshma.jellyplay.feature.book

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.PhotoOff
import com.raulshma.jellyplay.core.data.repository.ReaderAnnotation
import com.raulshma.jellyplay.core.data.repository.ReaderAnnotationColor
import com.raulshma.jellyplay.core.data.repository.ReaderAnnotationStyle
import com.raulshma.jellyplay.core.datastore.reader.ReadingDirection
import com.raulshma.jellyplay.core.datastore.reader.ReaderTheme
import com.raulshma.jellyplay.core.model.BookFormat
import com.raulshma.jellyplay.feature.book.epub.EpubAnnotationColor
import com.raulshma.jellyplay.feature.book.epub.EpubAnnotationSpec
import com.raulshma.jellyplay.feature.book.epub.EpubAnnotationStyle
import com.raulshma.jellyplay.feature.book.epub.EpubAppearance
import com.raulshma.jellyplay.feature.book.epub.EpubReaderCallbacks
import com.raulshma.jellyplay.feature.book.epub.EpubReaderStatus
import com.raulshma.jellyplay.feature.book.epub.EpubSearchResult
import com.raulshma.jellyplay.feature.book.epub.EpubTapZone
import com.raulshma.jellyplay.feature.book.epub.EpubTocItem
import com.raulshma.jellyplay.feature.book.epub.rememberEpubReaderHost
import com.raulshma.jellyplay.feature.book.generated.resources.Res
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_copied
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_downloading_viewer
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_preparing_locations
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The two reader renderers (split from BookReaderScreen.kt so the screen
 * stays a thin state router): the paged pager over rasterized pages and the
 * reflowable WebView host. Input handling differs deliberately —
 * [PagedReaderContent] keeps the NATIVE direction-aware tap zones
 * ([readerInput]); [ReflowableReaderContent] must NOT overlay a
 * pointerInput (it would swallow the web touches text selection needs), so
 * it keeps keyboard-only input ([readerKeys]) and maps the JS-reported tap
 * events through the same direction-aware logic. Both share the chrome
 * (ReaderChrome.kt) and the sheets (ReaderSheets.kt / ReaderSelection.kt).
 */

@Composable
internal fun PagedReaderContent(
    state: BookReaderUiState.Ready,
    content: ReadyContent.Paged,
    direction: ReadingDirection,
    viewModel: BookReaderViewModel,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var surfaceWidthPx by remember { mutableStateOf(1080) }
    var surfaceHeightPx by remember { mutableStateOf(1920) }
    // Fit mode is a per-SESSION view toggle (settings sheet state): never
    // persisted — reopening a book starts at FIT_WIDTH.
    var fitMode by remember { mutableStateOf(ReaderFitMode.FIT_WIDTH) }
    // Double-tap zoom events reach the CURRENT page's tile through this
    // token (the tap zones own taps; the tile owns the zoom state).
    var doubleTapZoomToken by remember { mutableStateOf(0) }
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val pdfOutline by viewModel.pdfOutline.collectAsStateWithLifecycle()
    val brightnessPct by viewModel.brightnessPct.collectAsStateWithLifecycle()
    val volumeKeyPaging by viewModel.volumeKeyPaging.collectAsStateWithLifecycle()
    val animatedPageTurns by viewModel.animatedPageTurns.collectAsStateWithLifecycle()
    val tocRailVisible by viewModel.tocRailVisible.collectAsStateWithLifecycle()
    var showBookmarks by remember { mutableStateOf(false) }
    var showToc by remember { mutableStateOf(false) }

    // Bookmarks load per item; the current page rides `state` — keying the
    // derived fill on both keeps the icon in step with either changing.
    val bookmarked = remember(bookmarks, state) { viewModel.hasBookmarkAtCurrentPosition() }

    // Page turn while zoomed: the token resets so the newly current tile
    // never consumes a stale double-tap (the outgoing zoomed tile drops its
    // zoom state with its composition — the pager disposes off-screen pages).
    LaunchedEffect(content.currentPage) { doubleTapZoomToken = 0 }

    // Auto-hide the chrome like player controls; suppress while a sheet
    // holds the screen.
    AutoHideControlsEffect(state.showControls, state.showSettings, showBookmarks, showToc, onTimeout = viewModel::toggleControls)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged {
                surfaceWidthPx = it.width.coerceAtLeast(1)
                surfaceHeightPx = it.height.coerceAtLeast(1)
            }
            .readerInput(
                direction = direction,
                onForward = viewModel::nextPage,
                onBackward = viewModel::previousPage,
                onBack = onBack,
                onToggleControls = viewModel::toggleControls,
                volumeKeyPaging = volumeKeyPaging,
                onDoubleTap = { doubleTapZoomToken++ },
            ),
    ) {
        val pagerState = rememberPagerState(initialPage = content.currentPage) { content.pageCount }
        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.currentPage }
                .drop(1)
                .collect { viewModel.onPageChanged(it) }
        }
        // VM-driven paging (keyboard, slider, tap zones, outline/bookmark
        // jumps) → scroll the pager; user swipes flow back through the
        // snapshotFlow above (the VM's same-page guard makes the round trip
        // idempotent). Programmatic turns animate or snap per the
        // animatedPageTurns preference; user swipes always animate.
        LaunchedEffect(pagerState, content.currentPage, animatedPageTurns) {
            if (pagerState.currentPage != content.currentPage &&
                pagerState.targetPage != content.currentPage
            ) {
                if (pageTurnScroll(animatedPageTurns) == PageTurnScroll.ANIMATED) {
                    pagerState.animateScrollToPage(content.currentPage)
                } else {
                    pagerState.scrollToPage(content.currentPage)
                }
            }
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            reverseLayout = direction == ReadingDirection.RTL,
            key = { it },
        ) { page ->
            PageTile(
                pageIndex = page,
                widthPx = surfaceWidthPx,
                heightPx = surfaceHeightPx,
                fitMode = fitMode,
                doubleTapZoomToken = if (page == content.currentPage) doubleTapZoomToken else 0,
                viewModel = viewModel,
            )
        }

        // Above the pages, under the chrome — controls stay full-brightness.
        BrightnessDimOverlay(brightnessPct)

        // PDF's TOC tick rail: current-outline-neighborhood ticks on the
        // start edge (tap/drag to jump — see ReaderTocRail). Opt-in via the
        // reader controls; other paged formats have no TOC and render
        // nothing.
        if (content.format == BookFormat.PDF && tocRailVisible) {
            val pdfTicks = remember(pdfOutline) { pdfTocTicks(pdfOutline) }
            val pdfTickIndex = remember(pdfTicks, content.currentPage) {
                pdfCurrentTickIndex(pdfTicks, content.currentPage)
            }
            ReaderTocRail(
                ticks = pdfTicks,
                currentIndex = pdfTickIndex,
                onJump = { tick ->
                    val page = tick.page ?: return@ReaderTocRail
                    scope.launch {
                        if (pageTurnScroll(animatedPageTurns) == PageTurnScroll.ANIMATED) {
                            pagerState.animateScrollToPage(page)
                        } else {
                            pagerState.scrollToPage(page)
                        }
                    }
                },
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 4.dp),
            )
        }

        ReaderTopBar(
            state = state,
            bookmarked = bookmarked,
            onToggleBookmark = viewModel::toggleBookmarkAtCurrentPosition,
            onOpenBookmarks = { showBookmarks = true },
            onOpenAnnotations = {},
            onOpenSettings = viewModel::openSettings,
            onBack = onBack,
            modifier = Modifier.align(Alignment.TopCenter),
        )
        AnimatedVisibility(
            visible = state.showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            PagedBottomBar(
                currentPage = content.currentPage,
                pageCount = content.pageCount,
                brightnessPct = brightnessPct,
                onBrightnessChange = viewModel::setBrightnessPct,
                tocVisible = content.format == BookFormat.PDF,
                onOpenToc = { showToc = true },
                onSeekPage = { page ->
                    // Slider seeks only scroll the pager — the VM follows
                    // through the snapshotFlow (the original contract).
                    scope.launch {
                        if (pageTurnScroll(animatedPageTurns) == PageTurnScroll.ANIMATED) {
                            pagerState.animateScrollToPage(page)
                        } else {
                            pagerState.scrollToPage(page)
                        }
                    }
                },
            )
        }

        if (state.showSettings) {
            PagedSettingsSheet(
                direction = direction,
                tocAvailable = content.format == BookFormat.PDF,
                fitMode = fitMode,
                behavior = ReaderBehaviorState(
                    volumeKeyPaging = volumeKeyPaging,
                    animatedPageTurns = animatedPageTurns,
                    readingSpeedWpm = viewModel.readingSpeedWpm.value,
                    tocRailVisible = tocRailVisible,
                ),
                onSetDirection = viewModel::setReadingDirection,
                onSetFitMode = { fitMode = it },
                onBehaviorChange = { next ->
                    if (next.volumeKeyPaging != volumeKeyPaging) {
                        viewModel.setVolumeKeyPaging(next.volumeKeyPaging)
                    }
                    if (next.animatedPageTurns != animatedPageTurns) {
                        viewModel.setAnimatedPageTurns(next.animatedPageTurns)
                    }
                    if (next.tocRailVisible != tocRailVisible) {
                        viewModel.setTocRailVisible(next.tocRailVisible)
                    }
                },
                onOpenToc = { viewModel.dismissSettings(); showToc = true },
                onDismissRequest = viewModel::dismissSettings,
            )
        }
        if (showToc && content.format == BookFormat.PDF) {
            PdfOutlineSheet(
                nodes = pdfOutline,
                onJump = { page ->
                    showToc = false
                    viewModel.onPageChanged(page)
                },
                onDismissRequest = { showToc = false },
            )
        }
        if (showBookmarks) {
            BookmarksSheet(
                bookmarks = bookmarks,
                title = state.title,
                onJump = { bookmark ->
                    showBookmarks = false
                    viewModel.jumpToBookmark(bookmark)
                },
                onDelete = { bookmark -> viewModel.deleteBookmark(bookmark.id) },
                onDismissRequest = { showBookmarks = false },
            )
        }
    }
}

/** The note dialog's target: a fresh selection or one existing annotation. */
internal sealed interface NoteDialogTarget {
    data object Selection : NoteDialogTarget
    data class Existing(val annotation: ReaderAnnotation) : NoteDialogTarget
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ReflowableReaderContent(
    state: BookReaderUiState.Ready,
    content: ReadyContent.Reflowable,
    direction: ReadingDirection,
    theme: ReaderTheme,
    fontSizePx: Int,
    viewModel: BookReaderViewModel,
    onBack: () -> Unit,
) {
    var epubStatus by remember { mutableStateOf(EpubReaderStatus.LOADING) }
    var percent by remember(content.bookFile) { mutableStateOf(content.resumePercent) }
    var tocItems by remember { mutableStateOf<List<EpubTocItem>>(emptyList()) }
    var showToc by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var showAnnotations by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showSleepTimer by remember { mutableStateOf(false) }
    var noteTarget by remember { mutableStateOf<NoteDialogTarget?>(null) }
    var resumedFromCfi by remember { mutableStateOf(false) }
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val annotations by viewModel.annotations.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val epubLocation by viewModel.currentEpubLocation.collectAsStateWithLifecycle()

    // Global typography + behavior slices (the theme/font params above are
    // the EFFECTIVE values — per-book override ?: global).
    val fontFamily by viewModel.readerFontFamily.collectAsStateWithLifecycle()
    val lineHeightPct by viewModel.lineHeightPct.collectAsStateWithLifecycle()
    val marginPct by viewModel.marginPct.collectAsStateWithLifecycle()
    val justifyText by viewModel.justify.collectAsStateWithLifecycle()
    val scrollMode by viewModel.scrollMode.collectAsStateWithLifecycle()
    val perBookOverride by viewModel.perBookAppearance.collectAsStateWithLifecycle()
    val brightnessPct by viewModel.brightnessPct.collectAsStateWithLifecycle()
    val volumeKeyPaging by viewModel.volumeKeyPaging.collectAsStateWithLifecycle()
    val readingSpeedWpm by viewModel.readingSpeedWpm.collectAsStateWithLifecycle()
    val tocRailVisible by viewModel.tocRailVisible.collectAsStateWithLifecycle()

    // Read aloud + sleep timer + auto-scroll (Wave 5). Speech/auto-scroll
    // STATE lives in the VM/screen as noted; the AUTO-SCROLL speed is the
    // persisted ReaderStore preference (sessions reopen at the chosen speed).
    val speechState by viewModel.speechState.collectAsStateWithLifecycle()
    // != UNAVAILABLE (not == AVAILABLE): the Android engine is lazy — its
    // INITIALIZING window lasts until the first speak, so keying the play
    // button on AVAILABLE would hide it behind its own starting condition.
    // Platforms without an engine report UNAVAILABLE and hide the controls.
    val speechAvailable =
        viewModel.speechAvailability.collectAsStateWithLifecycle().value != BookSpeechAvailability.UNAVAILABLE
    val speechRate by viewModel.speechRate.collectAsStateWithLifecycle()
    val speechPitch by viewModel.speechPitch.collectAsStateWithLifecycle()
    val sleepTimerState by viewModel.sleepTimerState.collectAsStateWithLifecycle()
    var autoScrollActive by remember { mutableStateOf(false) }
    val autoScrollSpeedPx by viewModel.autoScrollSpeedPxPerSec.collectAsStateWithLifecycle()

    /** The ephemeral speech-highlight CFI (null = nothing painted). */
    var speechHighlightCfi by remember { mutableStateOf<String?>(null) }
    // The highlight paint checks the live persisted marks (a paragraph whose
    // CFI a saved annotation occupies is never painted — removeAnnotation is
    // CFI-keyed and would later wipe the real mark), so it reads the CURRENT
    // list, not the composition-time capture.
    val currentAnnotations by rememberUpdatedState(annotations)

    // The "pending mark" snapshot the selection bar previews and the note
    // dialog saves with — also the last-used style/color memory.
    var pendingStyle by remember { mutableStateOf(ReaderAnnotationStyle.HIGHLIGHT) }
    var pendingColor by remember { mutableStateOf(ReaderAnnotationColor.YELLOW) }

    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val copiedLabel = stringResource(Res.string.book_reader_copied)

    // Search plumbing: the latest token only (late results of older queries
    // drop), and the ephemeral result flash highlight.
    var searchState by remember { mutableStateOf<ReaderSearchState>(ReaderSearchState.Idle) }
    var searchToken by remember { mutableStateOf(0) }
    var ephemeralCfi by remember { mutableStateOf<String?>(null) }

    // `remember {}` callbacks capture state OBJECTS (fresh reads at event
    // time), but plain parameters (direction, uiState) would go stale in the
    // remembered closure — direction flows through rememberUpdatedState and
    // chrome visibility through the VM's uiState instead.
    val currentDirection by rememberUpdatedState(direction)
    // Late-bound host: the callbacks object is created BEFORE the host (the
    // host factory takes the callbacks), so JS-tap navigation reaches it
    // through this stable holder — filled as soon as the host exists (taps
    // can only arrive after the page loads, so it is never null in practice).
    val hostRef = remember { mutableStateOf<com.raulshma.jellyplay.feature.book.epub.EpubReaderHandle?>(null) }

    /** Drops the search flash highlight (leaving the search context). */
    fun removeEphemeralHighlight() {
        ephemeralCfi?.let { cfi -> hostRef.value?.removeAnnotation(cfi) }
        ephemeralCfi = null
    }

    // JS-reported taps drive touch navigation with the same direction-aware
    // mapping as the keys (LEFT zone pages forward under RTL). reader.js
    // already skips taps while a selection gesture is live; the selection
    // state guard here is the belt. Sheet-open taps are pure noise.
    fun handleJsTap(zone: EpubTapZone) {
        val sheetOpen = showToc || showBookmarks || showAnnotations || showSearch ||
            (viewModel.uiState.value as? BookReaderUiState.Ready)?.showSettings == true
        if (sheetOpen || viewModel.selection.value != null) return
        when (epubTapAction(zone, currentDirection)) {
            ReaderTapAction.FORWARD -> {
                removeEphemeralHighlight()
                hostRef.value?.next()
            }
            ReaderTapAction.BACKWARD -> {
                removeEphemeralHighlight()
                hostRef.value?.prev()
            }
            ReaderTapAction.TOGGLE_CONTROLS -> viewModel.toggleControls()
        }
    }

    val callbacks = remember {
        EpubReaderCallbacks(
            onPercentChanged = { viewModel.onEpubPercentChanged(it); percent = it },
            onStatusChanged = { epubStatus = it },
            onDirectionReported = viewModel::onEpubDirection,
            onTocReady = { tocItems = it; viewModel.onEpubTocReady(it) },
            onRelocated = { viewModel.onEpubRelocated(it) },
            onSelection = { cfi, text -> viewModel.onEpubSelection(cfi, text) },
            onSelectionCleared = { viewModel.onEpubSelectionCleared() },
            onSearchResults = { token, results ->
                if (token == searchToken) {
                    searchState = ReaderSearchState.Results(results)
                }
            },
            onTap = { zone -> handleJsTap(zone) },
            onSwipe = { toLeft ->
                // Physical swipe → the zone a tap on that side would produce
                // (swipe left ≡ tap right), so navigation rides the exact
                // same direction-aware mapping as taps.
                handleJsTap(if (toLeft) EpubTapZone.RIGHT else EpubTapZone.LEFT)
            },
            onSpeechContext = { paragraphs -> viewModel.onSpeechContext(paragraphs) },
            onAutoScrollStopped = { autoScrollActive = false },
        )
    }
    // The full appearance bundle: rides the chunked load protocol on boot
    // AND re-fires the hosts' change push whenever any axis moves (their
    // LaunchedEffect keys on the data class). Mappings: family → CSS stack
    // (SYSTEM = null → the pushed clear), leading → pct/100, margins → the
    // proportional px band (see ReaderAppearance.kt).
    val appearance = EpubAppearance(
        theme = theme,
        fontSizePx = fontSizePx,
        fontFamilyCss = fontFamily.epubCssStack(),
        lineHeight = epubLineHeight(lineHeightPct),
        marginsPx = epubMarginsPx(marginPct),
        justify = justifyText,
        scrolled = scrollMode,
    )
    val host = rememberEpubReaderHost(
        bookFile = content.bookFile,
        resumePercent = content.resumePercent,
        appearance = appearance,
        callbacks = callbacks,
    )
    LaunchedEffect(host) { hostRef.value = host }
    // Scroll-mode flip: reader.js's setFlow rebuilds the rendition and
    // re-displays the same position. Keyed on boot status too — a flip made
    // while the WebView was still loading is dropped by the platform (a
    // script before page-finished is a no-op), so it must re-fire on READY
    // to land; reader.js no-ops when the flow already matches `pending`.
    LaunchedEffect(scrollMode, epubStatus) {
        host.setFlow(scrollMode)
        // Leaving scrolled flow kills auto-scroll (its scroller is gone);
        // mirror the state so the chrome toggle resets with it.
        if (!scrollMode && autoScrollActive) {
            autoScrollActive = false
            host.setAutoScroll(false, autoScrollSpeedPx)
        }
    }

    /**
     * Auto-scroll toggle for scrolled flow: host rAF loop at the session
     * speed. Any web input (reader.js stops on wheel/touchstart) reports
     * `onAutoScrollStopped`, which resets [autoScrollActive] above so this
     * icon re-arms cleanly after every manual interruption.
     */
    fun toggleAutoScroll() {
        autoScrollActive = !autoScrollActive
        host.setAutoScroll(autoScrollActive, autoScrollSpeedPx)
    }

    /** Drops the ephemeral speech highlight (session end). */
    fun removeSpeechHighlight() {
        speechHighlightCfi?.let { cfi -> hostRef.value?.removeAnnotation(cfi) }
        speechHighlightCfi = null
    }

    // The VM's one-shot host commands: speech context requests, chapter
    // turns, paragraph follows and the sleep timer's auto-scroll stop. The
    // VM owns the loop, this is the only place the host gets touched for it.
    LaunchedEffect(Unit) {
        viewModel.hostCommands.collect { command ->
            when (command) {
                is ReaderHostCommand.RequestSpeechContext ->
                    hostRef.value?.requestSpeechContext(command.cfi)
                ReaderHostCommand.AdvanceSpeechChapter -> hostRef.value?.next()
                is ReaderHostCommand.FollowSpeech -> {
                    // Follow: repaint the ephemeral paragraph highlight (a
                    // persisted mark on the same CFI is never overwritten —
                    // removal is CFI-keyed and would later wipe it) and
                    // goToCfi (epub.js no-ops when already visible).
                    removeSpeechHighlight()
                    if (currentAnnotations.none { it.cfi == command.cfi }) {
                        hostRef.value?.addAnnotation(
                            EpubAnnotationSpec(command.cfi, EpubAnnotationStyle.HIGHLIGHT, EpubAnnotationColor.YELLOW),
                        )
                        speechHighlightCfi = command.cfi
                    }
                    hostRef.value?.goToCfi(command.cfi)
                }
                ReaderHostCommand.SleepTimerFired -> {
                    if (autoScrollActive) {
                        autoScrollActive = false
                        hostRef.value?.setAutoScroll(false, autoScrollSpeedPx)
                    }
                }
            }
        }
    }
    // Session end (stop/finish/engine loss) drops the painted highlight.
    LaunchedEffect(speechState.active) {
        if (!speechState.active) removeSpeechHighlight()
    }
    // `host`'s composable call above emits the platform WebView directly into
    // the parent Box (this composable declares no root container), so the
    // interaction Box below composes on top of it. The Box carries
    // KEYBOARD-ONLY input (readerKeys): a pointerInput overlay would swallow
    // the web touches text selection needs — touch navigation rides the
    // JS-reported tap events instead.
    val downloadProgress by host.viewerDownloadProgress

    // Exact resume (ADR 0003 point 4): once READY, jump to the locally stored
    // CFI — exactly once. A deep-link destination (detail "Contents" tap)
    // outranks the resume anchor. A failed display degrades in-place
    // (reader.js keeps the current page and reports displayError), i.e. the
    // percent resume.
    LaunchedEffect(epubStatus, content.resumeCfi, content.jumpHref) {
        if (epubStatus == EpubReaderStatus.READY && !resumedFromCfi) {
            resumedFromCfi = true
            when {
                content.jumpHref != null -> host.goTo(content.jumpHref)
                else -> content.resumeCfi?.let(host::goToCfi)
            }
        }
    }

    // Persisted annotations → host paint. READY applies the full set once
    // (replace semantics are safe pre-interaction); every later list change
    // syncs INCREMENTALLY (add/remove per CFI) so the ephemeral search flash
    // and any selection-in-progress survive unrelated edits.
    var appliedAnnotations by remember { mutableStateOf<Map<String, EpubAnnotationSpec>>(emptyMap()) }
    LaunchedEffect(epubStatus) {
        if (epubStatus == EpubReaderStatus.READY) {
            val target = annotations.associate { it.cfi to it.toEpubSpec() }
            host.applyAnnotations(target.values.toList())
            appliedAnnotations = target
        }
    }
    LaunchedEffect(annotations, epubStatus) {
        if (epubStatus != EpubReaderStatus.READY) return@LaunchedEffect
        val target = annotations.associate { it.cfi to it.toEpubSpec() }
        if (target == appliedAnnotations) return@LaunchedEffect
        (appliedAnnotations.keys - target.keys).forEach(host::removeAnnotation)
        target.forEach { (cfi, spec) ->
            if (appliedAnnotations[cfi] != spec) host.addAnnotation(spec)
        }
        appliedAnnotations = target
    }

    // The selection bar must know whether the live selection sits on an
    // existing mark (create vs edit row).
    val existingAnnotation = remember(selection, annotations) {
        selection?.let { sel -> annotations.firstOrNull { it.cfi == sel.cfi } }
    }
    val bookmarked = remember(bookmarks, epubLocation, state) { viewModel.hasBookmarkAtCurrentPosition() }

    // Auto-hide the chrome like player controls; suppress while a sheet holds
    // the screen.
    AutoHideControlsEffect(
        state.showControls,
        state.showSettings,
        showToc,
        showBookmarks,
        showAnnotations,
        showSearch,
        showSleepTimer,
        onTimeout = viewModel::toggleControls,
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .readerKeys(
                direction = direction,
                onForward = { host.next() },
                onBackward = { host.prev() },
                onBack = onBack,
                onToggleControls = viewModel::toggleControls,
                volumeKeyPaging = volumeKeyPaging,
            ),
    ) {
        // Above the WebView, under the veils/chrome/sheets — the controls
        // stay full-brightness and the veil never intercepts web touches.
        BrightnessDimOverlay(brightnessPct)

        // Desktop-only: the CEF bundle downloads on first run.
        downloadProgress?.let { progress ->
            ReaderVeil {
                Text(
                    text = stringResource(
                        Res.string.book_reader_downloading_viewer,
                        (progress * 100).roundToInt().coerceIn(0, 100),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f),
                )
                LinearWavyProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(0.5f).padding(top = 16.dp),
                )
            }
        } ?: run {
            if (epubStatus != EpubReaderStatus.READY && epubStatus != EpubReaderStatus.ERROR) {
                ReaderVeil {
                    LoadingIndicator(modifier = Modifier.size(48.dp))
                    Text(
                        text = stringResource(Res.string.book_reader_preparing_locations),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }
        }

        ReaderTopBar(
            state = state,
            bookmarked = bookmarked,
            onToggleBookmark = viewModel::toggleBookmarkAtCurrentPosition,
            onOpenBookmarks = { showBookmarks = true },
            onOpenAnnotations = { showAnnotations = true },
            onOpenSettings = viewModel::openSettings,
            onBack = onBack,
            sleepTimerActive = sleepTimerState.running,
            onOpenSleepTimer = { showSleepTimer = true },
            modifier = Modifier.align(Alignment.TopCenter),
        )
        // The EPUB TOC tick rail (opt-in via the reader controls):
        // current-chapter-neighborhood ticks on the start edge. Current entry
        // rides the relocated event's spine href (the chapter label alone
        // collides on duplicate titles); hidden until both the toc event and
        // the first relocation have landed.
        val tocTicks = remember(tocItems) { epubTocTicks(tocItems) }
        val tocTickIndex = remember(tocTicks, epubLocation?.chapterHref) {
            epubCurrentTocIndex(tocItems, epubLocation?.chapterHref)
        }
        if (tocRailVisible) {
            ReaderTocRail(
                ticks = tocTicks,
                currentIndex = tocTickIndex,
                onJump = { tick -> tick.href?.let { href -> hostRef.value?.goTo(href) } },
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 4.dp),
            )
        }
        // The bottom chrome hides while the selection bar is up — two
        // bottom-anchored bars would fight for the same edge.
        AnimatedVisibility(
            visible = state.showControls && selection == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            ReflowableBottomBar(
                // The relocated event's percent (latest) wins once a location
                // exists; the percent-event state is the boot fallback.
                percent = epubLocation?.percent ?: percent,
                remainingPages = epubLocation?.remainingPages,
                minutesLeftInChapter = epubLocation?.remainingPages
                    ?.let { locationPagesMinutesRemaining(it, readingSpeedWpm) },
                minutesLeftInBook = epubLocation?.remainingLocations
                    ?.let { locationPagesMinutesRemaining(it, readingSpeedWpm) },
                brightnessPct = brightnessPct,
                onBrightnessChange = viewModel::setBrightnessPct,
                onOpenToc = { showToc = true },
                speechAvailable = speechAvailable,
                speechActive = speechState.active,
                speechPaused = speechState.paused,
                onSpeechToggle = viewModel::toggleReadAloud,
                onSpeechSkipBack = viewModel::skipSpeechBack,
                onSpeechSkipForward = viewModel::skipSpeechForward,
                onSpeechStop = viewModel::stopReadAloud,
                autoScrollVisible = scrollMode,
                autoScrollActive = autoScrollActive,
                onAutoScrollToggle = ::toggleAutoScroll,
            )
        }
        selection?.let { sel ->
            Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                SelectionActionBar(
                    existing = existingAnnotation,
                    style = pendingStyle,
                    color = pendingColor,
                    onColorTap = { color ->
                        pendingColor = color
                        if (existingAnnotation != null) {
                            viewModel.updateAnnotation(existingAnnotation.id, color = color)
                        } else {
                            viewModel.addSelectionAnnotation(pendingStyle, color)
                        }
                        host.clearSelection()
                    },
                    onToggleStyle = {
                        pendingStyle = if (pendingStyle == ReaderAnnotationStyle.UNDERLINE) {
                            ReaderAnnotationStyle.HIGHLIGHT
                        } else {
                            ReaderAnnotationStyle.UNDERLINE
                        }
                        if (existingAnnotation != null) {
                            viewModel.updateAnnotation(existingAnnotation.id, style = pendingStyle)
                        }
                    },
                    onEditNote = {
                        noteTarget = if (existingAnnotation != null) {
                            NoteDialogTarget.Existing(existingAnnotation)
                        } else {
                            NoteDialogTarget.Selection
                        }
                    },
                    onDelete = {
                        existingAnnotation?.let { viewModel.deleteAnnotation(it.id) }
                        host.clearSelection()
                    },
                    onCopy = { clipboard.setText(AnnotatedString(sel.text)) },
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp),
        )

        if (state.showSettings) {
            ReflowableSettingsSheet(
                theme = theme,
                fontSizePx = fontSizePx,
                perBook = perBookOverride != null,
                typography = ReaderTypographyState(
                    fontFamily = fontFamily,
                    lineHeightPct = lineHeightPct,
                    marginPct = marginPct,
                    justify = justifyText,
                    scrollMode = scrollMode,
                ),
                behavior = ReaderBehaviorState(
                    volumeKeyPaging = volumeKeyPaging,
                    animatedPageTurns = viewModel.animatedPageTurns.value,
                    readingSpeedWpm = readingSpeedWpm,
                    tocRailVisible = tocRailVisible,
                ),
                speechRate = speechRate,
                speechPitch = speechPitch,
                speechAvailable = speechAvailable,
                autoScrollSpeedPx = autoScrollSpeedPx,
                onSetTheme = viewModel::setReaderTheme,
                onAdjustFontSize = viewModel::adjustReaderFontSize,
                onSetPerBook = viewModel::setUsePerBookAppearance,
                onTypographyChange = { next ->
                    if (next.fontFamily != fontFamily) viewModel.setFontFamily(next.fontFamily)
                    if (next.lineHeightPct != lineHeightPct) viewModel.setLineHeightPct(next.lineHeightPct)
                    if (next.marginPct != marginPct) viewModel.setMarginPct(next.marginPct)
                    if (next.justify != justifyText) viewModel.setJustify(next.justify)
                    if (next.scrollMode != scrollMode) viewModel.setScrollMode(next.scrollMode)
                },
                onBehaviorChange = { next ->
                    if (next.volumeKeyPaging != volumeKeyPaging) {
                        viewModel.setVolumeKeyPaging(next.volumeKeyPaging)
                    }
                    if (next.readingSpeedWpm != readingSpeedWpm) {
                        viewModel.setReadingSpeedWpm(next.readingSpeedWpm)
                    }
                    if (next.tocRailVisible != tocRailVisible) {
                        viewModel.setTocRailVisible(next.tocRailVisible)
                    }
                },
                onSetSpeechRate = viewModel::setSpeechRate,
                onSetSpeechPitch = viewModel::setSpeechPitch,
                onSetAutoScrollSpeed = { speed ->
                    viewModel.setAutoScrollSpeedPxPerSec(speed)
                    // Live re-target: an active rAF loop picks the new speed.
                    if (autoScrollActive) host.setAutoScroll(true, speed)
                },
                onOpenToc = { viewModel.dismissSettings(); showToc = true },
                onDismissRequest = viewModel::dismissSettings,
            )
        }
        if (showSleepTimer) {
            SleepTimerSheet(
                state = sleepTimerState,
                onSelect = viewModel::startSleepTimer,
                onCancel = viewModel::cancelSleepTimer,
                onDismissRequest = { showSleepTimer = false },
            )
        }
        if (showToc) {
            EpubTocSheet(
                tocItems = tocItems,
                onJump = { href -> showToc = false; host.goTo(href) },
                onOpenSearch = { showToc = false; showSearch = true; searchState = ReaderSearchState.Idle },
                onDismissRequest = { showToc = false },
            )
        }
        if (showBookmarks) {
            BookmarksSheet(
                bookmarks = bookmarks,
                title = state.title,
                onJump = { bookmark ->
                    showBookmarks = false
                    // Null-CFI rows have no in-reader fallback (the host has
                    // no display-by-percent after boot) — the jump is a no-op.
                    bookmark.cfi?.let(host::goToCfi)
                },
                onDelete = { bookmark -> viewModel.deleteBookmark(bookmark.id) },
                onDismissRequest = { showBookmarks = false },
            )
        }
        if (showAnnotations) {
            AnnotationsSheet(
                annotations = annotations,
                onJump = { annotation -> showAnnotations = false; host.goToCfi(annotation.cfi) },
                onEditNote = { annotation -> noteTarget = NoteDialogTarget.Existing(annotation) },
                onRecolor = { annotation, color -> viewModel.updateAnnotation(annotation.id, color = color) },
                onDelete = { annotation -> viewModel.deleteAnnotation(annotation.id) },
                onExport = { asJson ->
                    val exported = viewModel.exportAnnotations(asJson)
                    clipboard.setText(AnnotatedString(exported))
                    scope.launch { snackbarHostState.showSnackbar(copiedLabel) }
                },
                onDismissRequest = { showAnnotations = false },
            )
        }
        if (showSearch) {
            SearchSheet(
                state = searchState,
                onSearch = { query, token ->
                    searchToken = token
                    searchState = ReaderSearchState.Searching
                    removeEphemeralHighlight()
                    host.search(query, token)
                },
                onResultTap = { result ->
                    removeEphemeralHighlight()
                    host.goToCfi(result.cfi)
                    // Ephemeral flash — skipped when a persisted mark already
                    // paints this CFI (removeAnnotation is CFI-keyed and
                    // would later wipe the real mark's paint).
                    if (annotations.none { it.cfi == result.cfi }) {
                        host.addAnnotation(
                            EpubAnnotationSpec(result.cfi, EpubAnnotationStyle.HIGHLIGHT, EpubAnnotationColor.YELLOW),
                        )
                        ephemeralCfi = result.cfi
                    }
                },
                onDismissRequest = {
                    showSearch = false
                    removeEphemeralHighlight()
                    searchState = ReaderSearchState.Idle
                },
            )
        }
        noteTarget?.let { target ->
            NoteDialog(
                initialNote = (target as? NoteDialogTarget.Existing)?.annotation?.note,
                onSave = { note ->
                    when (target) {
                        is NoteDialogTarget.Selection -> {
                            viewModel.addSelectionAnnotation(pendingStyle, pendingColor, note)
                            host.clearSelection()
                        }
                        is NoteDialogTarget.Existing ->
                            viewModel.updateAnnotation(target.annotation.id, note = note)
                    }
                    noteTarget = null
                },
                onDismiss = { noteTarget = null },
            )
        }
    }
}

/**
 * Live zoom/pan state of one page tile. `raster` is the bitmap's render
 * multiplier vs the base fit width (1 = the fitted base bitmap); `zoom` is
 * the graphicsLayer scale ON TOP of that. The VISUAL scale the user sees is
 * `visual` — at raster > 1 the bitmap displays 1:1 (ContentScale.None), so
 * its intrinsic pixels already carry `raster`× of the zoom. Pan is clamped
 * to ±(visual − 1) × tile / 2 per axis: the page can never be dragged fully
 * off-screen, and it pans freely inside its overflow at each scale.
 */
private class PageZoomState {
    var zoom by mutableFloatStateOf(1f)
    var raster by mutableFloatStateOf(1f)
    var pan by mutableStateOf(Offset.Zero)
    var tileSize by mutableStateOf(IntSize.Zero)

    val visual: Float get() = if (raster > 1f) raster * zoom else zoom

    fun setVisual(value: Float) {
        val r = raster
        zoom = (value / if (r > 1f) r else 1f).coerceIn(0.05f, 20f)
        clampPan()
    }

    fun clampPan() {
        val maxX = (visual - 1f) * tileSize.width / 2f
        val maxY = (visual - 1f) * tileSize.height / 2f
        pan = Offset(pan.x.coerceIn(-maxX, maxX), pan.y.coerceIn(-maxY, maxY))
    }

    fun reset() {
        zoom = 1f
        raster = 1f
        pan = Offset.Zero
    }
}

/**
 * One pager page. Base render is fit-aware ([ReaderFitMode] via
 * [BookReaderViewModel.requestPage]); interactive zoom is a graphicsLayer
 * transform driven by a custom transform gesture that CLAIMS the gesture
 * only for real zooms — a second finger, or any finger while zoomed — so a
 * single-finger swipe at 1× still reaches the pager and tap zones. Double-tap
 * (routed in from the content tap zones via [doubleTapZoomToken]) toggles
 * 1× ↔ 2.5× with a short animation.
 *
 * Zoom settle → re-raster: once the visual scale stops moving for
 * [ZOOM_SETTLE_MS], the page re-renders at surface width × scale (capped at
 * [MAX_PAGE_RASTER_SCALE]; above the cap the scaled bitmap is kept) and the
 * graphicsLayer scale resets to 1× carrying the sharper bitmap 1:1 — the
 * zoom never visually jumps. A page turn disposes the tile (the pager keeps
 * no off-screen pages), which snaps the next visit back to 1×.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PageTile(
    pageIndex: Int,
    widthPx: Int,
    heightPx: Int,
    fitMode: ReaderFitMode,
    doubleTapZoomToken: Int,
    viewModel: BookReaderViewModel,
) {
    val bitmap = remember(pageIndex, widthPx, heightPx, fitMode) { mutableStateOf<ImageBitmap?>(null) }
    val failed = remember(pageIndex, widthPx, heightPx, fitMode) { mutableStateOf(false) }
    val zoomState = remember(pageIndex) { PageZoomState() }

    // Base raster; a fit/surface change re-renders and resets the zoom.
    LaunchedEffect(pageIndex, widthPx, heightPx, fitMode) {
        zoomState.reset()
        bitmap.value = viewModel.requestPage(pageIndex, widthPx, heightPx, fitMode)
        failed.value = bitmap.value == null
    }

    // Double-tap toggle: 1× ↔ 2.5×, animated; the settle effect below
    // re-rasters once the animation comes to rest.
    LaunchedEffect(doubleTapZoomToken) {
        if (doubleTapZoomToken == 0) return@LaunchedEffect
        val target = if (zoomState.visual > 1.05f) 1f else DOUBLE_TAP_ZOOM_SCALE
        val animatable = Animatable(zoomState.visual)
        animatable.animateTo(target, tween(durationMillis = DOUBLE_TAP_ZOOM_ANIM_MS)) {
            zoomState.setVisual(value)
        }
    }

    // Settle → re-raster: keys on the visual scale, so pinch frames and
    // animation frames keep cancelling the timer; the still moment fires it.
    LaunchedEffect(pageIndex, widthPx, heightPx, fitMode, zoomState.visual) {
        if (zoomState.visual <= 1.01f && zoomState.raster <= 1f) return@LaunchedEffect
        delay(ZOOM_SETTLE_MS)
        val target = zoomState.visual.coerceIn(1f, MAX_PAGE_RASTER_SCALE)
        if (abs(target - zoomState.raster) < 0.05f) return@LaunchedEffect
        val fresh = viewModel.requestPage(pageIndex, widthPx, heightPx, fitMode, target)
        if (fresh == null) return@LaunchedEffect
        bitmap.value = fresh
        zoomState.raster = target
        zoomState.zoom = zoomState.visual / if (target > 1f) target else 1f
        zoomState.clampPan()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged { zoomState.tileSize = it }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var tracking = false
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.none { it.pressed }) break
                        // Claim only real zooms: two fingers, or any finger
                        // while zoomed. A single finger at 1× stays FREE for
                        // the pager's swipe (no consume).
                        if (!tracking && (event.changes.size >= 2 || zoomState.visual > 1.01f)) {
                            tracking = true
                        }
                        if (!tracking) continue
                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()
                        if (zoomChange != 1f || panChange != Offset.Zero) {
                            event.changes.forEach { it.consume() }
                            zoomState.setVisual(
                                (zoomState.visual * zoomChange).coerceIn(1f, MAX_VISUAL_ZOOM_SCALE),
                            )
                            zoomState.pan = Offset(
                                zoomState.pan.x + panChange.x,
                                zoomState.pan.y + panChange.y,
                            )
                            zoomState.clampPan()
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val current = bitmap.value
        when {
            current != null -> Image(
                bitmap = current,
                contentDescription = null,
                // At raster > 1 the re-rastered bitmap displays 1:1 (its
                // intrinsic pixels carry the zoom); at raster 1 the fitted
                // bitmap fills the tile as before.
                contentScale = if (zoomState.raster > 1f) ContentScale.None else ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = zoomState.zoom
                        scaleY = zoomState.zoom
                        translationX = zoomState.pan.x
                        translationY = zoomState.pan.y
                    },
            )
            // A corrupt archive entry resolves null once — an error tile beats
            // a spinner that can never finish.
            failed.value -> Icon(
                imageVector = Tabler.Outline.PhotoOff,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.4f),
                modifier = Modifier.size(36.dp),
            )
            else -> LoadingIndicator(
                modifier = Modifier.size(36.dp),
                color = Color.White.copy(alpha = 0.6f),
            )
        }
    }
}

/** Double-tap zoom target and its animation length. */
private const val DOUBLE_TAP_ZOOM_SCALE = 2.5f
private const val DOUBLE_TAP_ZOOM_ANIM_MS = 220

/** Stillness window that fires a zoom re-raster. */
private const val ZOOM_SETTLE_MS = 300L

/** Pinch ceiling — above the 3× re-raster cap the scaled bitmap is kept. */
private const val MAX_VISUAL_ZOOM_SCALE = 6f

/** Domain annotation → the host paint spec (the two enum pairs are 1:1). */
internal fun ReaderAnnotation.toEpubSpec(): EpubAnnotationSpec = EpubAnnotationSpec(
    cfi = cfi,
    style = when (style) {
        ReaderAnnotationStyle.HIGHLIGHT -> EpubAnnotationStyle.HIGHLIGHT
        ReaderAnnotationStyle.UNDERLINE -> EpubAnnotationStyle.UNDERLINE
    },
    color = when (color) {
        ReaderAnnotationColor.YELLOW -> EpubAnnotationColor.YELLOW
        ReaderAnnotationColor.GREEN -> EpubAnnotationColor.GREEN
        ReaderAnnotationColor.BLUE -> EpubAnnotationColor.BLUE
        ReaderAnnotationColor.RED -> EpubAnnotationColor.RED
    },
)
