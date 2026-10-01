package com.raulshma.jellyplay.feature.book

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.PhotoOff
import com.raulshma.jellyplay.core.datastore.reader.ReadingDirection
import com.raulshma.jellyplay.core.datastore.reader.ReadingLayout
import com.raulshma.jellyplay.core.model.BookFormat
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The paged reader renderer (split from ReaderContent.kt alongside its
 * reflowable sibling so each renderer owns a file; the screen stays a thin
 * state router). The pager steps SLOTS over rasterized pages — one page in
 * SINGLE, a spread in DOUBLE — and keeps the NATIVE direction-aware tap zones
 * ([readerInput]); [ReflowableReaderContent] is the deliberate counterpart
 * (keyboard-only input + JS taps). Both share the chrome (ReaderChrome.kt)
 * and the sheets (one file per sheet — PagedSettingsSheet.kt, PdfOutlineSheet.kt,
 * BookmarksSheet.kt — plus ReaderSelection.kt).
 *
 * Scope note (honest test surface): this file is a presentation-only render
 * shell — state collection, the pager wiring and callback dispatch. Its
 * DECISIONS are pinned elsewhere (SpreadSlots, PagedPagerCoordinator,
 * PageZoomState, the reader input fold, the auto-hide timeout selection);
 * the compose tree itself has no UI-test lane in this module and is accepted
 * as presentation-only.
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
    // Per-book double-page (spread) mode — the pager steps slots, everything
    // else stays page-based (see SpreadSlots).
    val layout by viewModel.readingLayout.collectAsStateWithLifecycle()
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
    // every sheet now). The timeout is the pref knob folded through the
    // shared TV doubling (the video player's pref-driven, TV-aware policy —
    // see ReaderPrefsSnapshot.controlsAutoHideTimeoutMs).
    val isTv = LocalTvMode.current
    AutoHideControlsEffect(
        state.showControls,
        prefs.controlsAutoHideTimeoutMs(isTv),
        sheets.open,
        onTimeout = viewModel::toggleControls,
    )

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
        // The pager steps SLOTS (one page in SINGLE, a spread in DOUBLE); the
        // VM and every tick/bookmark stay page-based — the mapping happens only
        // at this boundary. Toggling the layout re-keys the state so the pager
        // re-anchors on the current page's slot.
        val slotCount = SpreadSlots.slotCount(content.pageCount, layout)
        val currentSlot = SpreadSlots.slotForPage(content.currentPage, content.pageCount, layout)
        val pagerState = key(layout) {
            rememberPagerState(initialPage = currentSlot) { slotCount }
        }
        // The page-turn protocol lives in the coordinator (PagedPagerCoordinator
        // owns the ordering contract KDoc): settle/swipe reporting installs once,
        // VM-driven paging rides turnTo's guard, rail/slider jumps ride jumpTo.
        val coordinator = rememberPagedPagerCoordinator(
            pagerState = pagerState,
            animated = { prefs.global.animatedPageTurns },
            // Settles report a SLOT; the VM learns the slot's lead page.
            onPageSettled = { slot ->
                viewModel.onPageChanged(SpreadSlots.firstPageOfSlot(slot, content.pageCount, layout))
            },
        )
        LaunchedEffect(coordinator) { coordinator.attach(this) }
        // VM-driven paging (keyboard, slider, tap zones, outline/bookmark
        // jumps): the uiState page is already the truth — the coordinator
        // animates or snaps the pager to it, skipping pages the pager holds
        // or already flies to (which makes the settle round trip idempotent).
        LaunchedEffect(coordinator, content.currentPage, animatedPageTurns, layout) {
            coordinator.turnTo(currentSlot)
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            reverseLayout = direction == ReadingDirection.RTL,
            key = { it },
        ) { slot ->
            val pages = SpreadSlots.pagesOfSlot(slot, content.pageCount, layout)
            when {
                // Spread: two tiles side by side (the lead page on the left in
                // LTR, on the right in RTL — manga's page flow), each rastered
                // to half the surface width.
                pages.size == 2 -> {
                    val arranged = if (direction == ReadingDirection.RTL) pages.reversed() else pages
                    Row(modifier = Modifier.fillMaxSize()) {
                        arranged.forEach { page ->
                            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                PageTile(
                                    pageIndex = page,
                                    widthPx = surfaceWidthPx / 2,
                                    heightPx = surfaceHeightPx,
                                    fitMode = fitMode,
                                    doubleTapZoomToken = if (page == content.currentPage) doubleTapZoomToken else 0,
                                    viewModel = viewModel,
                                )
                            }
                        }
                    }
                }
                else -> pages.forEach { page ->
                    PageTile(
                        pageIndex = page,
                        widthPx = surfaceWidthPx,
                        heightPx = surfaceHeightPx,
                        fitMode = fitMode,
                        doubleTapZoomToken = if (page == content.currentPage) doubleTapZoomToken else 0,
                        viewModel = viewModel,
                    )
                }
            }
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
                    scope.launch {
                        coordinator.jumpTo(SpreadSlots.slotForPage(page, content.pageCount, layout))
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
                    scope.launch {
                        coordinator.jumpTo(SpreadSlots.slotForPage(page, content.pageCount, layout))
                    }
                },
            )
        }

        if (sheets.showSettings) {
            PagedSettingsSheet(
                direction = direction,
                layout = layout,
                tocAvailable = content.format == BookFormat.PDF,
                fitMode = fitMode,
                prefs = prefs,
                onSetDirection = viewModel::setReadingDirection,
                onSetLayout = viewModel::setReadingLayout,
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
