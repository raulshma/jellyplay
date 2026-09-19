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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import com.composables.icons.tabler.outline.ArrowLeft
import com.composables.icons.tabler.outline.PhotoOff
import com.raulshma.jellyplay.core.data.repository.ReaderAnnotation
import com.raulshma.jellyplay.core.data.repository.ReaderAnnotationColor
import com.raulshma.jellyplay.core.data.repository.ReaderAnnotationStyle
import com.raulshma.jellyplay.core.datastore.reader.ReadingDirection
import com.raulshma.jellyplay.core.model.BookFormat
import com.raulshma.jellyplay.core.model.PlatformKind
import com.raulshma.jellyplay.core.model.currentPlatform
import com.raulshma.jellyplay.feature.book.epub.EpubAnnotationColor
import com.raulshma.jellyplay.feature.book.epub.EpubAnnotationSpec
import com.raulshma.jellyplay.feature.book.epub.EpubAnnotationStyle
import com.raulshma.jellyplay.feature.book.epub.EpubAppearance
import com.raulshma.jellyplay.feature.book.epub.EpubReaderHandle
import com.raulshma.jellyplay.feature.book.epub.EpubReaderStatus
import com.raulshma.jellyplay.feature.book.epub.rememberEpubReaderHost
import com.raulshma.jellyplay.feature.book.generated.resources.Res
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_copied
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_downloading_viewer
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_error_cannot_open
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_preparing_locations
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_title_fallback
import okio.Path
import kotlinx.coroutines.delay
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
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val brightnessPct = prefs.global.brightnessPct
    val volumeKeyPaging = prefs.global.volumeKeyPaging
    val animatedPageTurns = prefs.global.animatedPageTurns
    val tocRailVisible = prefs.global.tocRailVisible
    // Same admission holder as the reflowable half (the settings sheet and
    // the two paged sheets are ever raised here); one `open` fold feeds the
    // auto-hide suppression below.
    val sheets = remember { ReaderSheetStack() }

    // Bookmarks load per item; the current page rides `state` — keying the
    // derived fill on both keeps the icon in step with either changing.
    val bookmarked = remember(bookmarks, state) { viewModel.hasBookmarkAtCurrentPosition() }

    // Page turn while zoomed: the token resets so the newly current tile
    // never consumes a stale double-tap (the outgoing zoomed tile drops its
    // zoom state with its composition — the pager disposes off-screen pages).
    LaunchedEffect(content.currentPage) { doubleTapZoomToken = 0 }

    // Auto-hide the chrome like player controls; suppress while a sheet
    // holds the screen (the settings sheet included — ReaderSheetStack owns
    // every sheet now).
    AutoHideControlsEffect(state.showControls, sheets.open, onTimeout = viewModel::toggleControls)

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
                // The sheet owner closes its settings sheet on a chrome
                // toggle — the fold toggleControls used to carry as VM state.
                onToggleControls = {
                    sheets.showSettings = false
                    viewModel.toggleControls()
                },
                volumeKeyPaging = volumeKeyPaging,
                onDoubleTap = { doubleTapZoomToken++ },
            ),
    ) {
        val pagerState = rememberPagerState(initialPage = content.currentPage) { content.pageCount }
        // The page-turn protocol lives in the coordinator (PagedPagerCoordinator
        // owns the ordering contract KDoc): settle/swipe reporting installs once,
        // VM-driven paging rides turnTo's guard, rail/slider jumps ride jumpTo.
        val coordinator = rememberPagedPagerCoordinator(
            pagerState = pagerState,
            animated = { prefs.global.animatedPageTurns },
            onPageSettled = viewModel::onPageChanged,
        )
        LaunchedEffect(coordinator) { coordinator.attach(this) }
        // VM-driven paging (keyboard, slider, tap zones, outline/bookmark
        // jumps): the uiState page is already the truth — the coordinator
        // animates or snaps the pager to it, skipping pages the pager holds
        // or already flies to (which makes the settle round trip idempotent).
        LaunchedEffect(coordinator, content.currentPage, animatedPageTurns) {
            coordinator.turnTo(content.currentPage)
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
                    // Pager-first jump (the coordinator contract's jumpTo
                    // half): the VM learns through the settle collector.
                    scope.launch { coordinator.jumpTo(page) }
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
            onOpenBookmarks = { sheets.showBookmarks = true },
            // No annotations/sleep entries here: ReaderTopBar gates both on
            // ReadyContent.Reflowable (the single format gate).
            onOpenSettings = { sheets.showSettings = true },
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
                onBrightnessChange = viewModel.preferences::setBrightnessPct,
                tocVisible = content.format == BookFormat.PDF,
                onOpenToc = { sheets.showToc = true },
                onSeekPage = { page ->
                    // Pager-first jump (the coordinator contract's jumpTo
                    // half): the VM follows through the settle collector.
                    scope.launch { coordinator.jumpTo(page) }
                },
            )
        }

        if (sheets.showSettings) {
            PagedSettingsSheet(
                direction = direction,
                tocAvailable = content.format == BookFormat.PDF,
                fitMode = fitMode,
                prefs = prefs,
                onSetDirection = viewModel::setReadingDirection,
                onSetFitMode = { fitMode = it },
                onBehaviorChange = viewModel.preferences::applyBehavior,
                onOpenToc = { sheets.showSettings = false; sheets.showToc = true },
                onDismissRequest = { sheets.showSettings = false },
            )
        }
        if (sheets.showToc && content.format == BookFormat.PDF) {
            PdfOutlineSheet(
                nodes = pdfOutline,
                onJump = { page ->
                    // VM-first jump (the coordinator contract's turnTo half):
                    // the uiState moves, the sync effect turns the pager.
                    sheets.showToc = false
                    viewModel.onPageChanged(page)
                },
                onDismissRequest = { sheets.showToc = false },
            )
        }
        if (sheets.showBookmarks) {
            BookmarksSheet(
                bookmarks = bookmarks,
                title = state.title,
                onJump = { bookmark ->
                    sheets.showBookmarks = false
                    viewModel.jumpToBookmark(bookmark)
                },
                onDelete = { bookmark -> viewModel.deleteBookmark(bookmark.id) },
                onDismissRequest = { sheets.showBookmarks = false },
            )
        }
    }
}

/** The note dialog's target: a fresh selection or one existing annotation. */
internal sealed interface NoteDialogTarget {
    data object Selection : NoteDialogTarget
    data class Existing(val annotation: ReaderAnnotation) : NoteDialogTarget
}

/**
 * Whether the reflowable chrome floats OVER the content (top/bottom bars,
 * veils, rails as overlays) on [platform]. Desktop renders EPUB through
 * windowed CEF — a heavyweight surface that paints above every Compose
 * overlay — so its chrome lives in the layout around the browser instead
 * (top bar / content / bottom bar in a Column). Every other platform's
 * WebView is lightweight and overlays compose fine. Pure — pinned by
 * DesktopViewerBootTest.
 */
internal fun epubChromeOverlaysContent(platform: PlatformKind): Boolean =
    platform != PlatformKind.DESKTOP

/**
 * The reflowable host call + its two one-line dispatches (late-bound attach
 * and the scroll-mode flip), shared by the overlay and desktop in-flow
 * layouts so the boot choreography exists once. [holder] publishes the bound
 * handle for the chrome that cannot use the returned value directly (the
 * desktop layout emits the browser inside its content region but drives it
 * from the surrounding bars); it is cleared when this binding leaves the
 * composition, so commands after an error teardown no-op instead of reaching
 * a dead view. [overlayActive] rides through to the host seam (the desktop
 * windowed-CEF occlusion toggle — see rememberEpubReaderHost); only the
 * desktop layout passes it.
 */
@Composable
private fun ReflowableHostBinding(
    session: ReflowableReaderSession,
    holder: MutableState<EpubReaderHandle?>,
    bookFile: Path,
    resumePercent: Double,
    appearance: EpubAppearance,
    scrollMode: Boolean,
    overlayActive: Boolean = false,
    modifier: Modifier = Modifier.fillMaxSize(),
): EpubReaderHandle {
    val host = rememberEpubReaderHost(
        bookFile = bookFile,
        resumePercent = resumePercent,
        appearance = appearance,
        onEvent = session.onEvent,
        overlayActive = overlayActive,
        modifier = modifier,
    )
    // The late-bound binding: the session (and, through its port, the VM's
    // speech loop) reaches the host from here. Unbound again on dispose so
    // every session-side command degrades to a no-op — the old channel's
    // dropped-command semantics for a detached screen.
    DisposableEffect(host) {
        session.attachHost(host)
        holder.value = host
        onDispose {
            session.attachHost(null)
            if (holder.value === host) holder.value = null
        }
    }
    // Scroll-mode flip: reader.js's setFlow rebuilds the rendition and
    // re-displays the same position. Keyed on boot status too — a flip made
    // while the WebView was still loading is dropped by the platform (a
    // script before page-finished is a no-op), so it must re-fire on READY
    // to land; reader.js no-ops when the flow already matches `pending`.
    LaunchedEffect(scrollMode, session.status) {
        session.onFlowChanged(scrollMode)
    }
    return host
}

/**
 * The desktop in-flow boot strip: the overlay veils cannot cover the
 * windowed browser, so boot feedback lives in the layout between the top bar
 * and the content region — the viewer-download percent while the CEF bundle
 * downloads, an indeterminate strip while the book itself boots, nothing
 * once READY (or ERROR, which owns the content region).
 */
@Composable
private fun DesktopBootStrip(
    downloadProgress: Float?,
    status: EpubReaderStatus,
) {
    val text: String
    val progress: Float?
    when {
        downloadProgress != null -> {
            text = stringResource(
                Res.string.book_reader_downloading_viewer,
                (downloadProgress * 100).roundToInt().coerceIn(0, 100),
            )
            progress = downloadProgress
        }
        status != EpubReaderStatus.READY && status != EpubReaderStatus.ERROR -> {
            text = stringResource(Res.string.book_reader_preparing_locations)
            progress = null
        }
        else -> return
    }
    Surface(color = Color.Black.copy(alpha = 0.6f)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.7f),
            )
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.weight(1f),
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.weight(1f))
            }
        }
    }
}

/**
 * The desktop in-flow error screen: no browser is composed behind it (its
 * surface would cover the message), so this is the whole content — a back
 * row plus the centered reason. Closing and reopening the book retries the
 * boot from a fresh session.
 */
@Composable
private fun DesktopReaderError(
    title: String,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Surface(color = Color.Black.copy(alpha = 0.6f)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Tabler.Outline.ArrowLeft,
                        contentDescription = null,
                        tint = Color.White,
                    )
                }
                Text(
                    text = title.ifBlank {
                        stringResource(Res.string.book_reader_title_fallback)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(Res.string.book_reader_error_cannot_open),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
            )
        }
    }
}

/**
 * The reflowable reader's RENDER shell over its Compose-free
 * [ReflowableReaderSession] (ReaderControllers.kt): this composable collects
 * the VM/session state, binds the platform host ([ReflowableHostBinding])
 * into the session, and dispatches one-line effects (annotation sync, exact
 * resume, speech-end highlight drop) plus the sheet callbacks into session
 * methods — every decision lives in the session. See the file header for the
 * input-handling split ([readerKeys] + JS taps).
 *
 * Two layouts over one state (see [epubChromeOverlaysContent]): the overlay
 * tree floats the chrome over the content edge, while the desktop tree lays
 * the same bars around the browser (top bar / content / bottom bar) with an
 * in-flow boot strip and error screen — windowed CEF paints above every
 * Compose overlay, so floating chrome would hide behind it there. The sheets
 * stay shared below both trees; on desktop the binding forwards
 * `sheets.open` as the host's overlayActive, which hides the browser's
 * surface while a sheet is up (the sheet windows composite UNDER windowed
 * CEF — without the hide, the book painted through the sheet's middle band).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ReflowableReaderContent(
    state: BookReaderUiState.Ready,
    content: ReadyContent.Reflowable,
    direction: ReadingDirection,
    viewModel: BookReaderViewModel,
    onBack: () -> Unit,
) {
    // The local sheet/dialog flags ride ONE holder whose `open` fold is the
    // single "a sheet holds the screen" predicate for nav gating and
    // auto-hide (ReaderSheetStack) — the settings sheet included.
    val sheets = remember { ReaderSheetStack() }
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val annotations by viewModel.annotations.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val epubLocation by viewModel.currentEpubLocation.collectAsStateWithLifecycle()

    // Global typography + behavior slices ride ONE preference snapshot
    // instead of one collect per knob.
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val global = prefs.global
    val scrollMode = global.scrollMode
    val perBookActive = prefs.perBookActive
    val brightnessPct = global.brightnessPct
    val volumeKeyPaging = global.volumeKeyPaging
    val readingSpeedWpm = global.readingSpeedWpm
    val tocRailVisible = global.tocRailVisible

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
    val speechRate = global.speechRate
    val speechPitch = global.speechPitch
    val sleepTimerState by viewModel.sleepTimerState.collectAsStateWithLifecycle()

    // The "pending mark" snapshot the selection bar previews and the note
    // dialog saves with — also the last-used style/color memory.
    var pendingStyle by remember { mutableStateOf(ReaderAnnotationStyle.HIGHLIGHT) }
    var pendingColor by remember { mutableStateOf(ReaderAnnotationColor.YELLOW) }

    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val copiedLabel = stringResource(Res.string.book_reader_copied)

    // `remember {}` event-time reads capture state OBJECTS, but plain
    // parameters (direction) would go stale in the remembered closure —
    // direction flows through rememberUpdatedState.
    val currentDirection by rememberUpdatedState(direction)

    // The Compose-free session (ReaderControllers.kt): the host handle
    // lifecycle, the annotation/auto-scroll/search controllers, the
    // resume-jump latch, the JS-tap router and the whole event dispatch —
    // everything this composable used to inline as untestable state. What
    // remains here is the render shell: state collection, the host call, the
    // one-line effect dispatches and the sheet callbacks below. Constructor
    // lambdas read state objects/flows FRESH (the session's speed provider
    // reads the VM's preference StateFlow, never a recomposition-captured
    // slice).
    val session = remember {
        ReflowableReaderSession(
            direction = { currentDirection },
            sheetOpen = { sheets.open },
            selectionActive = { viewModel.selection.value != null },
            autoScrollSpeed = { viewModel.prefs.value.global.autoScrollSpeedPxPerSec },
            onToggleControls = viewModel::toggleControls,
            forward = viewModel::onEpubEvent,
        )
    }
    // The VM's speech loop + sleep timer execute their host-side halves
    // through this port (the collapsed command channel's replacement).
    LaunchedEffect(session) { viewModel.attachReaderSession(session) }
    // The full appearance bundle: rides the chunked load protocol on boot
    // AND re-fires the hosts' change push whenever any axis moves (their
    // LaunchedEffect keys on the data class). The snapshot → bundle fold is
    // EpubAppearance.from: theme/font from the EFFECTIVE appearance
    // (override ?: global), typography axes from the global slice.
    val appearance = EpubAppearance.from(prefs)
    // Session end (stop/finish/engine loss) drops the painted highlight.
    LaunchedEffect(speechState.active) {
        session.onSpeechActiveChanged(speechState.active)
    }

    // Overlay vs in-flow chrome (see epubChromeOverlaysContent): the overlay
    // tree composes the long-standing floating layout; the desktop tree lays
    // the same chrome around the browser because windowed CEF paints above
    // every Compose overlay. The bound handle travels through `hostHolder` so
    // bars outside the browser's region (and the shared sheets below) drive
    // it; the binding clears the holder on dispose, so an error teardown
    // no-ops instead of commanding a dead view.
    val overlayChrome = epubChromeOverlaysContent(currentPlatform)
    val hostHolder = remember { mutableStateOf<EpubReaderHandle?>(null) }

    // The EPUB TOC tick rail's ticks: hoisted — the overlay branch floats the
    // rail over the content edge while the in-flow branch docks it beside the
    // browser, but both render the same window.
    val tocTicks = remember(session.tocItems) { epubTocTicks(session.tocItems) }
    val tocTickIndex = remember(tocTicks, epubLocation?.chapterHref) {
        epubCurrentTocIndex(session.tocItems, epubLocation?.chapterHref)
    }

    // Exact resume (ADR 0003 point 4): the latch + jump decision are the
    // session's (see [ReflowableReaderSession.resumeJump]); this shell just
    // re-offers the anchors whenever the boot status or either anchor moves.
    LaunchedEffect(session.status, content.resumeCfi, content.jumpHref) {
        session.resumeJump(content.resumeCfi, content.jumpHref)
    }

    // Persisted annotations -> host paint, through the sync controller (full
    // apply on READY entry, incremental diffs after — see ReaderAnnotationSync).
    LaunchedEffect(session.status, annotations) {
        session.syncAnnotations(annotations)
    }

    // The selection bar must know whether the live selection sits on an
    // existing mark (create vs edit row).
    val existingAnnotation = remember(selection, annotations) {
        selection?.let { sel -> annotations.firstOrNull { it.cfi == sel.cfi } }
    }
    val bookmarked = remember(bookmarks, epubLocation, state) { viewModel.hasBookmarkAtCurrentPosition() }

    // Auto-hide the chrome like player controls; suppress while a sheet
    // holds the screen (the holder's fold includes the settings sheet and
    // the note dialog).
    AutoHideControlsEffect(
        state.showControls,
        sheets.open,
        onTimeout = viewModel::toggleControls,
    )

    if (!overlayChrome && session.status == EpubReaderStatus.ERROR) {
        // No browser is composed behind the error (its surface would cover
        // the message) — back out to retry from a fresh session.
        DesktopReaderError(title = state.title, onBack = onBack)
    } else if (!overlayChrome) {
        // Desktop in-flow layout: top bar / browser / bottom bar participate
        // in the layout, so nothing hides behind the windowed browser.
        // Auto-hide still collapses the bars (AnimatedVisibility reclaims the
        // space — true fullscreen while reading).
        // Keyboard shortcuts need Compose focus, and clicks land in CEF (which
        // never yields it) — request it once so arrows/Enter work immediately.
        val desktopFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) { desktopFocus.requestFocus() }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .focusRequester(desktopFocus)
                .readerKeys(
                    direction = direction,
                    onForward = { hostHolder.value?.next() },
                    onBackward = { hostHolder.value?.prev() },
                    onBack = onBack,
                    // The sheet owner closes its settings sheet on a chrome
                    // toggle — the fold toggleControls used to carry as VM state.
                    onToggleControls = {
                        sheets.showSettings = false
                        viewModel.toggleControls()
                    },
                    volumeKeyPaging = volumeKeyPaging,
                ),
        ) {
            AnimatedVisibility(
                visible = state.showControls,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                ReaderTopBar(
                    state = state,
                    bookmarked = bookmarked,
                    onToggleBookmark = viewModel::toggleBookmarkAtCurrentPosition,
                    onOpenBookmarks = { sheets.showBookmarks = true },
                    onOpenAnnotations = { sheets.showAnnotations = true },
                    onOpenSettings = { sheets.showSettings = true },
                    onBack = onBack,
                    sleepTimerActive = sleepTimerState.running,
                    onOpenSleepTimer = { sheets.showSleepTimer = true },
                )
            }
            // Boot feedback lives here — the overlay veils cannot cover the
            // browser, so they stay on the overlay branch only.
            DesktopBootStrip(
                downloadProgress = hostHolder.value?.viewerDownloadProgress?.value,
                status = session.status,
            )
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                if (tocRailVisible) {
                    ReaderTocRail(
                        ticks = tocTicks,
                        currentIndex = tocTickIndex,
                        onJump = { tick -> tick.href?.let { href -> hostHolder.value?.goTo(href) } },
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
                            .padding(start = 4.dp),
                    )
                }
                Box(modifier = Modifier.weight(1f).fillMaxSize().background(Color.Black)) {
                    // Windowed CEF paints above every Compose/sheet window on
                    // desktop, so a raised sheet shows the book through its own
                    // middle band wherever the browser still occupies pixels.
                    // The host's visibility toggle is the first belt; the
                    // second (load-bearing here) collapses the browser's layout
                    // to 0 px while a sheet holds the screen. The browser stays
                    // composed (no reload, handle stays valid) — only its
                    // native surface shrinks away, leaving the black content
                    // region for the sheet's own window to render over.
                    val sheetOpen = sheets.open
                    ReflowableHostBinding(
                        session = session,
                        holder = hostHolder,
                        bookFile = content.bookFile,
                        resumePercent = content.resumePercent,
                        appearance = appearance,
                        scrollMode = scrollMode,
                        // The sheet/dialog windows composite UNDER windowed CEF
                        // on desktop — the host hides the browser's surface
                        // while one holds the screen so the sheet shows.
                        overlayActive = sheetOpen,
                        modifier = if (sheetOpen) Modifier.size(0.dp) else Modifier.fillMaxSize(),
                    )
                }
            }
            selection?.let { sel ->
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
                        hostHolder.value?.clearSelection()
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
                        sheets.noteTarget = if (existingAnnotation != null) {
                            NoteDialogTarget.Existing(existingAnnotation)
                        } else {
                            NoteDialogTarget.Selection
                        }
                    },
                    onDelete = {
                        existingAnnotation?.let { viewModel.deleteAnnotation(it.id) }
                        hostHolder.value?.clearSelection()
                    },
                    onCopy = { clipboard.setText(AnnotatedString(sel.text)) },
                )
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 8.dp),
            )
            // The bottom chrome hides while the selection bar is up — two
            // bottom-anchored bars would fight for the same edge.
            AnimatedVisibility(
                visible = state.showControls && selection == null,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                ReflowableBottomBar(
                    // Same single-percent-carrier read as the overlay branch.
                    percent = epubLocation?.percent ?: content.resumePercent,
                    remainingPages = epubLocation?.remainingPages,
                    minutesLeftInChapter = epubLocation?.remainingPages
                        ?.let { locationPagesMinutesRemaining(it, readingSpeedWpm) },
                    minutesLeftInBook = epubLocation?.remainingLocations
                        ?.let { locationPagesMinutesRemaining(it, readingSpeedWpm) },
                    brightnessPct = brightnessPct,
                    onBrightnessChange = viewModel.preferences::setBrightnessPct,
                    // No dim veil can cover windowed CEF — the slider would
                    // be a dead control here (see ReflowableBottomBar).
                    showBrightness = false,
                    onOpenToc = { sheets.showToc = true },
                    speechAvailable = speechAvailable,
                    speechActive = speechState.active,
                    speechPaused = speechState.paused,
                    onSpeechToggle = viewModel::toggleReadAloud,
                    onSpeechSkipBack = viewModel::skipSpeechBack,
                    onSpeechSkipForward = viewModel::skipSpeechForward,
                    onSpeechStop = viewModel::stopReadAloud,
                    autoScrollVisible = scrollMode,
                    autoScrollActive = session.autoScroll.active,
                    onAutoScrollToggle = session.autoScroll::toggle,
                )
            }
        }
    } else {
        // The browser fills the screen behind this chrome (the binding emits
        // its view here, ahead of the Box — the long-standing order).
        // KEYBOARD-ONLY input on the Box: a pointerInput overlay would swallow
        // the web touches text selection needs — touch navigation rides the
        // JS-reported tap events instead.
        val overlayHost = ReflowableHostBinding(
            session = session,
            holder = hostHolder,
            bookFile = content.bookFile,
            resumePercent = content.resumePercent,
            appearance = appearance,
            scrollMode = scrollMode,
        )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .readerKeys(
                direction = direction,
                onForward = { overlayHost.next() },
                onBackward = { overlayHost.prev() },
                onBack = onBack,
                // The sheet owner closes its settings sheet on a chrome
                // toggle — the fold toggleControls used to carry as VM state.
                onToggleControls = {
                    sheets.showSettings = false
                    viewModel.toggleControls()
                },
                volumeKeyPaging = volumeKeyPaging,
            ),
    ) {
        val downloadProgress by overlayHost.viewerDownloadProgress
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
            // ERROR fails the boot veil into a message: without this arm a
            // viewer/encode failure (desktop KCEF init, corrupt payload)
            // lands on a blank reader behind the chrome.
            when (session.status) {
                EpubReaderStatus.ERROR -> ReaderVeil {
                    Text(
                        text = stringResource(Res.string.book_reader_error_cannot_open),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                }
                EpubReaderStatus.READY -> Unit
                else -> ReaderVeil {
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
            onOpenBookmarks = { sheets.showBookmarks = true },
            onOpenAnnotations = { sheets.showAnnotations = true },
            onOpenSettings = { sheets.showSettings = true },
            onBack = onBack,
            sleepTimerActive = sleepTimerState.running,
            onOpenSleepTimer = { sheets.showSleepTimer = true },
            modifier = Modifier.align(Alignment.TopCenter),
        )
        // The EPUB TOC tick rail (opt-in via the reader controls):
        // current-chapter-neighborhood ticks on the start edge. Current entry
        // rides the relocated event's spine href (the chapter label alone
        // collides on duplicate titles); hidden until both the toc event and
        // the first relocation have landed.
        if (tocRailVisible) {
            ReaderTocRail(
                ticks = tocTicks,
                currentIndex = tocTickIndex,
                onJump = { tick -> tick.href?.let { href -> overlayHost.goTo(href) } },
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
                // The location flow is the single percent carrier: the VM
                // seeds it with the boot resume percent at open, so the
                // elvis only satisfies the non-null parameter before the
                // first Ready frame observes it.
                percent = epubLocation?.percent ?: content.resumePercent,
                remainingPages = epubLocation?.remainingPages,
                minutesLeftInChapter = epubLocation?.remainingPages
                    ?.let { locationPagesMinutesRemaining(it, readingSpeedWpm) },
                minutesLeftInBook = epubLocation?.remainingLocations
                    ?.let { locationPagesMinutesRemaining(it, readingSpeedWpm) },
                brightnessPct = brightnessPct,
                onBrightnessChange = viewModel.preferences::setBrightnessPct,
                onOpenToc = { sheets.showToc = true },
                speechAvailable = speechAvailable,
                speechActive = speechState.active,
                speechPaused = speechState.paused,
                onSpeechToggle = viewModel::toggleReadAloud,
                onSpeechSkipBack = viewModel::skipSpeechBack,
                onSpeechSkipForward = viewModel::skipSpeechForward,
                onSpeechStop = viewModel::stopReadAloud,
                autoScrollVisible = scrollMode,
                autoScrollActive = session.autoScroll.active,
                onAutoScrollToggle = session.autoScroll::toggle,
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
                        overlayHost.clearSelection()
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
                        sheets.noteTarget = if (existingAnnotation != null) {
                            NoteDialogTarget.Existing(existingAnnotation)
                        } else {
                            NoteDialogTarget.Selection
                        }
                    },
                    onDelete = {
                        existingAnnotation?.let { viewModel.deleteAnnotation(it.id) }
                        overlayHost.clearSelection()
                    },
                    onCopy = { clipboard.setText(AnnotatedString(sel.text)) },
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp),
        )
    }
    }

    // Shared sheets (both layouts). Windowed CEF on desktop composites above
    // the sheet/dialog windows, so the binding passes `sheets.open` as the
    // host's overlayActive — the browser's surface hides while one of these
    // is up, and the sheet renders over the blank content region. The holder
    // is set once the binding composes; the desktop error screen composes no
    // binding, so the sheets stay home there.
    hostHolder.value?.let { sheetsHost ->
        if (sheets.showSettings) {
            ReflowableSettingsSheet(
                prefs = prefs,
                speechAvailable = speechAvailable,
                onSetTheme = viewModel.preferences::setTheme,
                onAdjustFontSize = viewModel.preferences::adjustFontSize,
                onSetPerBook = viewModel.preferences::setUsePerBookAppearance,
                // Whole-bundle commits; the which-axis-changed diff lives in
                // ReaderPreferences.applyTypography/applyBehavior (one home,
                // no stale recomposition captures).
                onTypographyChange = viewModel.preferences::applyTypography,
                onBehaviorChange = viewModel.preferences::applyBehavior,
                onSetSpeechRate = viewModel::setSpeechRate,
                onSetSpeechPitch = viewModel::setSpeechPitch,
                onSetAutoScrollSpeed = { speed ->
                    viewModel.preferences.setAutoScrollSpeedPxPerSec(speed)
                    // Live re-target: an active rAF loop picks the new speed
                    // (the session's speed provider reads the live snapshot).
                    session.autoScroll.retarget()
                },
                onOpenToc = { sheets.showSettings = false; sheets.showToc = true },
                onDismissRequest = { sheets.showSettings = false },
            )
        }
        if (sheets.showSleepTimer) {
            SleepTimerSheet(
                state = sleepTimerState,
                onSelect = viewModel::startSleepTimer,
                onCancel = viewModel::cancelSleepTimer,
                onDismissRequest = { sheets.showSleepTimer = false },
            )
        }
        if (sheets.showToc) {
            EpubTocSheet(
                tocItems = session.tocItems,
                onJump = { href -> sheets.showToc = false; sheetsHost.goTo(href) },
                onOpenSearch = { sheets.showToc = false; sheets.showSearch = true; session.resetSearch() },
                onDismissRequest = { sheets.showToc = false },
            )
        }
        if (sheets.showBookmarks) {
            BookmarksSheet(
                bookmarks = bookmarks,
                title = state.title,
                onJump = { bookmark ->
                    sheets.showBookmarks = false
                    // Jumpability is the codec's rule (CFI-carrying rows
                    // only): null-CFI rows have no in-reader fallback (the
                    // host has no display-by-percent after boot).
                    if (ReaderBookmarkCodec.isJumpable(bookmark)) sheetsHost.goToCfi(bookmark.cfi!!)
                },
                onDelete = { bookmark -> viewModel.deleteBookmark(bookmark.id) },
                onDismissRequest = { sheets.showBookmarks = false },
            )
        }
        if (sheets.showAnnotations) {
            AnnotationsSheet(
                annotations = annotations,
                onJump = { annotation -> sheets.showAnnotations = false; sheetsHost.goToCfi(annotation.cfi) },
                onEditNote = { annotation -> sheets.noteTarget = NoteDialogTarget.Existing(annotation) },
                onRecolor = { annotation, color -> viewModel.updateAnnotation(annotation.id, color = color) },
                onDelete = { annotation -> viewModel.deleteAnnotation(annotation.id) },
                onExport = { asJson ->
                    val exported = viewModel.exportAnnotations(asJson)
                    clipboard.setText(AnnotatedString(exported))
                    scope.launch { snackbarHostState.showSnackbar(copiedLabel) }
                },
                onDismissRequest = { sheets.showAnnotations = false },
            )
        }
        if (sheets.showSearch) {
            SearchSheet(
                state = session.searchSession.state,
                onSearch = session::search,
                onResultTap = session::openSearchResult,
                onDismissRequest = {
                    sheets.showSearch = false
                    session.resetSearch()
                },
            )
        }
        sheets.noteTarget?.let { target ->
            NoteDialog(
                initialNote = (target as? NoteDialogTarget.Existing)?.annotation?.note,
                onSave = { note ->
                    when (target) {
                        is NoteDialogTarget.Selection -> {
                            viewModel.addSelectionAnnotation(pendingStyle, pendingColor, note)
                            sheetsHost.clearSelection()
                        }
                        is NoteDialogTarget.Existing ->
                            viewModel.updateAnnotation(target.annotation.id, note = note)
                    }
                    sheets.noteTarget = null
                },
                onDismiss = { sheets.noteTarget = null },
            )
        }
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
        delay(ZOOM_SETTLE_MS)
        // Pure decide-then-apply: the retarget rule (cap, 0.05 dead-band,
        // base-scale skip) lives in pageRasterTarget, pinned beside the state.
        val target = pageRasterTarget(zoomState.visual, zoomState.raster) ?: return@LaunchedEffect
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
                        // Claim only real zooms (two fingers, or any
                        // finger while zoomed): a single finger at 1× stays
                        // FREE for the pager's swipe (no consume).
                        if (!tracking && shouldClaimZoomGesture(event.changes.size, zoomState.visual)) {
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
