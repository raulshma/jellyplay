package com.raulshma.jellyplay.feature.book

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
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
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
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
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val pdfOutline by viewModel.pdfOutline.collectAsStateWithLifecycle()
    var showBookmarks by remember { mutableStateOf(false) }
    var showToc by remember { mutableStateOf(false) }

    // Bookmarks load per item; the current page rides `state` — keying the
    // derived fill on both keeps the icon in step with either changing.
    val bookmarked = remember(bookmarks, state) { viewModel.hasBookmarkAtCurrentPosition() }

    // Auto-hide the chrome like player controls; suppress while a sheet
    // holds the screen.
    AutoHideControlsEffect(state.showControls, state.showSettings, showBookmarks, showToc, onTimeout = viewModel::toggleControls)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { surfaceWidthPx = it.width.coerceAtLeast(1) }
            .readerInput(
                direction = direction,
                onForward = viewModel::nextPage,
                onBackward = viewModel::previousPage,
                onBack = onBack,
                onToggleControls = viewModel::toggleControls,
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
        // idempotent).
        LaunchedEffect(pagerState, content.currentPage) {
            if (pagerState.currentPage != content.currentPage &&
                pagerState.targetPage != content.currentPage
            ) {
                pagerState.animateScrollToPage(content.currentPage)
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
                viewModel = viewModel,
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
                onSeekPage = { page ->
                    // Slider seeks only scroll the pager — the VM follows
                    // through the snapshotFlow (the original contract).
                    scope.launch { pagerState.animateScrollToPage(page) }
                },
            )
        }

        if (state.showSettings) {
            PagedSettingsSheet(
                direction = direction,
                tocAvailable = content.format == BookFormat.PDF,
                onSetDirection = viewModel::setReadingDirection,
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
    var noteTarget by remember { mutableStateOf<NoteDialogTarget?>(null) }
    var resumedFromCfi by remember { mutableStateOf(false) }
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val annotations by viewModel.annotations.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val epubLocation by viewModel.currentEpubLocation.collectAsStateWithLifecycle()

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
            onTocReady = { tocItems = it },
            onRelocated = { viewModel.onEpubRelocated(it) },
            onSelection = { cfi, text -> viewModel.onEpubSelection(cfi, text) },
            onSelectionCleared = { viewModel.onEpubSelectionCleared() },
            onSearchResults = { token, results ->
                if (token == searchToken) {
                    searchState = ReaderSearchState.Results(results)
                }
            },
            onTap = { zone -> handleJsTap(zone) },
        )
    }
    val host = rememberEpubReaderHost(
        bookFile = content.bookFile,
        resumePercent = content.resumePercent,
        appearance = EpubAppearance(theme = theme, fontSizePx = fontSizePx),
        callbacks = callbacks,
    )
    LaunchedEffect(host) { hostRef.value = host }
    // `host`'s composable call above emits the platform WebView directly into
    // the parent Box (this composable declares no root container), so the
    // interaction Box below composes on top of it. The Box carries
    // KEYBOARD-ONLY input (readerKeys): a pointerInput overlay would swallow
    // the web touches text selection needs — touch navigation rides the
    // JS-reported tap events instead.
    val downloadProgress by host.viewerDownloadProgress

    // Exact resume (ADR 0003 point 4): once READY, jump to the locally stored
    // CFI — exactly once. A failed display degrades in-place (reader.js keeps
    // the current page and reports displayError), i.e. the percent resume.
    LaunchedEffect(epubStatus, content.resumeCfi) {
        if (epubStatus == EpubReaderStatus.READY && !resumedFromCfi) {
            resumedFromCfi = true
            content.resumeCfi?.let(host::goToCfi)
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
            ),
    ) {
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
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(0.5f).padding(top = 16.dp),
                )
            }
        } ?: run {
            if (epubStatus != EpubReaderStatus.READY && epubStatus != EpubReaderStatus.ERROR) {
                ReaderVeil {
                    CircularProgressIndicator(modifier = Modifier.size(48.dp))
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
            modifier = Modifier.align(Alignment.TopCenter),
        )
        // The bottom chrome hides while the selection bar is up — two
        // bottom-anchored bars would fight for the same edge.
        AnimatedVisibility(
            visible = state.showControls && selection == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            ReflowableBottomBar(percent = percent)
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
                onSetTheme = viewModel::setReaderTheme,
                onAdjustFontSize = viewModel::adjustReaderFontSize,
                onOpenToc = { viewModel.dismissSettings(); showToc = true },
                onDismissRequest = viewModel::dismissSettings,
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

@Composable
private fun PageTile(
    pageIndex: Int,
    widthPx: Int,
    viewModel: BookReaderViewModel,
) {
    val bitmap = remember(pageIndex, widthPx) { mutableStateOf<ImageBitmap?>(null) }
    val failed = remember(pageIndex, widthPx) { mutableStateOf(false) }
    LaunchedEffect(pageIndex, widthPx) {
        bitmap.value = viewModel.requestPage(pageIndex, widthPx)
        failed.value = bitmap.value == null
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val current = bitmap.value
        when {
            current != null -> Image(
                bitmap = current,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            // A corrupt archive entry resolves null once — an error tile beats
            // a spinner that can never finish.
            failed.value -> Icon(
                imageVector = Tabler.Outline.PhotoOff,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.4f),
                modifier = Modifier.size(36.dp),
            )
            else -> CircularProgressIndicator(modifier = Modifier.size(36.dp), color = Color.White.copy(alpha = 0.6f))
        }
    }
}

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
