package com.raulshma.jellyplay.feature.book

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.ArrowLeft
import com.composables.icons.tabler.outline.Book
import com.composables.icons.tabler.outline.List
import com.composables.icons.tabler.outline.Minus
import com.composables.icons.tabler.outline.PhotoOff
import com.composables.icons.tabler.outline.Plus
import com.composables.icons.tabler.outline.Settings
import com.raulshma.jellyplay.core.datastore.reader.ReadingDirection
import com.raulshma.jellyplay.core.datastore.reader.ReaderTheme
import com.raulshma.jellyplay.feature.book.epub.EpubReaderCallbacks
import com.raulshma.jellyplay.feature.book.epub.EpubReaderStatus
import com.raulshma.jellyplay.feature.book.epub.EpubTocItem
import com.raulshma.jellyplay.feature.book.epub.rememberEpubReaderHost
import com.raulshma.jellyplay.feature.book.generated.resources.Res
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_direction_ltr
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_direction_rtl
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_downloading_viewer
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_error_cannot_open
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_error_unsupported
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_font_size
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_loading
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_page_indicator
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_percent
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_preparing_locations
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_settings
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_theme
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_theme_dark
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_theme_light
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_theme_sepia
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_title_fallback
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_toc
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.roundToInt

/**
 * The full-screen book reader: paged books render a pager over rasterized
 * pages; reflowable (EPUB) books render the platform WebView host. Both share
 * the auto-hiding chrome, direction-aware tap zones and debounced progress
 * reporting (the VM owns the writes; the screen only feeds events).
 * Double-tap zoom is deliberately deferred (v1).
 */
@Composable
fun BookReaderScreen(
    itemId: String,
    onBack: () -> Unit,
    viewModel: BookReaderViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val direction by viewModel.readingDirection.collectAsStateWithLifecycle()
    val theme by viewModel.readerTheme.collectAsStateWithLifecycle()
    val fontSizePx by viewModel.readerFontSizePx.collectAsStateWithLifecycle()

    val windowOps = rememberReaderWindowOps()
    DisposableEffect(Unit) {
        windowOps.enter()
        onDispose {
            windowOps.exit()
            viewModel.reportNow()
        }
    }

    LaunchedEffect(itemId) {
        viewModel.load(itemId)
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        when (val state = uiState) {
            BookReaderUiState.Idle -> LoadingVeil(BookReaderUiState.Loading())
            is BookReaderUiState.Loading -> LoadingVeil(state)
            is BookReaderUiState.Error -> ErrorVeil(state, onBack)
            is BookReaderUiState.Ready -> when (val content = state.content) {
                is ReadyContent.Paged -> PagedReaderContent(
                    state = state,
                    content = content,
                    direction = direction,
                    viewModel = viewModel,
                    onBack = onBack,
                )
                is ReadyContent.Reflowable -> ReflowableReaderContent(
                    state = state,
                    content = content,
                    direction = direction,
                    theme = theme,
                    fontSizePx = fontSizePx,
                    viewModel = viewModel,
                    onBack = onBack,
                )
            }
        }
    }
}

private fun handleKeyEvent(
    event: KeyEvent,
    direction: ReadingDirection,
    onForward: () -> Unit,
    onBackward: () -> Unit,
    onBack: () -> Unit,
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    return when (event.key) {
        // Arrows follow the reading direction; PageUp/PageDown stay physical.
        Key.DirectionLeft -> {
            if (direction.isForwardFromLeft()) onForward() else onBackward()
            true
        }
        Key.DirectionRight -> {
            if (direction.isForwardFromLeft()) onBackward() else onForward()
            true
        }
        Key.PageUp, Key.MediaRewind -> {
            onBackward()
            true
        }
        Key.PageDown, Key.MediaFastForward -> {
            onForward()
            true
        }
        Key.Escape -> {
            onBack()
            true
        }
        else -> false
    }
}

/**
 * TV remotes have no tap zone: D-pad center / Enter / Menu is the only way
 * back into the (auto-hiding) settings chrome. This lives in the BUBBLE
 * phase, unlike the paging keys: a focused chrome control (the settings
 * gear) must see center/Enter first — a preview handler would swallow the
 * click and just toggle the chrome away beneath it.
 */
private fun Modifier.chromeToggleKey(onToggleControls: () -> Unit): Modifier =
    onKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown &&
            (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.Menu)
        ) {
            onToggleControls()
            true
        } else {
            false
        }
    }

@Composable
private fun LoadingVeil(state: BookReaderUiState.Loading) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (state.progress != null) {
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.fillMaxWidth(0.5f),
                )
            } else {
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
            }
            Text(
                text = stringResource(Res.string.book_reader_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun ErrorVeil(state: BookReaderUiState.Error, onBack: () -> Unit) {
    val message = when (state.reason) {
        BookReaderUiState.ErrorReason.CannotOpen ->
            stringResource(Res.string.book_reader_error_cannot_open)
        BookReaderUiState.ErrorReason.UnsupportedFormat ->
            stringResource(Res.string.book_reader_error_unsupported)
    }
    // Player-style transient error: show the reason briefly, then pop back.
    LaunchedEffect(Unit) {
        delay(ERROR_AUTO_BACK_MS)
        onBack()
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .padding(24.dp)
                .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            Icon(
                imageVector = Tabler.Outline.Book,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
            )
            Text(text = message, style = MaterialTheme.typography.bodyLarge, color = Color.White)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PagedReaderContent(
    state: BookReaderUiState.Ready,
    content: ReadyContent.Paged,
    direction: ReadingDirection,
    viewModel: BookReaderViewModel,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var surfaceWidthPx by remember { mutableStateOf(1080) }

    // Auto-hide the chrome like player controls; suppress while the settings
    // sheet holds the screen.
    AutoHideControlsEffect(state.showControls, state.showSettings, onTimeout = viewModel::toggleControls)

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
        // VM-driven paging (keyboard, slider, tap zones) → scroll the pager;
        // user swipes flow back through the snapshotFlow above (the VM's
        // same-page guard makes the round trip idempotent).
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
            var dragging by remember { mutableStateOf(false) }
            var dragValue by remember { mutableStateOf(1f) }
            Surface(color = Color.Black.copy(alpha = 0.6f)) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Slider(
                        value = if (dragging) dragValue else (content.currentPage + 1).toFloat(),
                        onValueChange = { dragging = true; dragValue = it },
                        onValueChangeFinished = {
                            dragging = false
                            val target = dragValue.toInt().coerceIn(1, content.pageCount)
                            scope.launch { pagerState.animateScrollToPage(target - 1) }
                        },
                        valueRange = 1f..content.pageCount.coerceAtLeast(1).toFloat(),
                    )
                    Text(
                        text = stringResource(
                            Res.string.book_reader_page_indicator,
                            content.currentPage + 1,
                            content.pageCount,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp),
                    )
                }
            }
        }

        if (state.showSettings) {
            ModalBottomSheet(onDismissRequest = viewModel::dismissSettings) {
                SheetTitle(text = stringResource(Res.string.book_reader_settings))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FilterChip(
                        selected = direction == ReadingDirection.LTR,
                        onClick = { viewModel.setReadingDirection(ReadingDirection.LTR) },
                        label = {
                            Text(stringResource(Res.string.book_reader_direction_ltr))
                        },
                    )
                    FilterChip(
                        selected = direction == ReadingDirection.RTL,
                        onClick = { viewModel.setReadingDirection(ReadingDirection.RTL) },
                        label = {
                            Text(stringResource(Res.string.book_reader_direction_rtl))
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReflowableReaderContent(
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
    val callbacks = remember {
        EpubReaderCallbacks(
            onPercentChanged = { viewModel.onEpubPercentChanged(it); percent = it },
            onStatusChanged = { epubStatus = it },
            onDirectionReported = viewModel::onEpubDirection,
            onTocReady = { tocItems = it },
        )
    }
    val host = rememberEpubReaderHost(
        bookFile = content.bookFile,
        resumePercent = content.resumePercent,
        theme = theme,
        fontSizePx = fontSizePx,
        callbacks = callbacks,
    )
    // `host`'s composable call above emits the platform WebView directly into
    // the parent Box (this composable declares no root container), so the
    // interaction Box below composes on top of it; its tap zones intentionally
    // swallow web clicks for v1.
    val downloadProgress by host.viewerDownloadProgress

    // Auto-hide the chrome like player controls; suppress while a sheet holds
    // the screen.
    AutoHideControlsEffect(
        state.showControls,
        state.showSettings,
        showToc,
        onTimeout = viewModel::toggleControls,
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .readerInput(
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
            Surface(color = Color.Black.copy(alpha = 0.6f)) {
                Text(
                    text = stringResource(
                        Res.string.book_reader_percent,
                        (percent * 100).roundToInt().coerceIn(0, 100),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)
                        .padding(bottom = 8.dp),
                )
            }
        }

        if (state.showSettings) {
            ModalBottomSheet(onDismissRequest = viewModel::dismissSettings) {
                SheetTitle(text = stringResource(Res.string.book_reader_settings))
                Text(
                    text = stringResource(Res.string.book_reader_theme),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FilterChip(
                        selected = theme == ReaderTheme.DARK,
                        onClick = { viewModel.setReaderTheme(ReaderTheme.DARK) },
                        label = { Text(stringResource(Res.string.book_reader_theme_dark)) },
                    )
                    FilterChip(
                        selected = theme == ReaderTheme.SEPIA,
                        onClick = { viewModel.setReaderTheme(ReaderTheme.SEPIA) },
                        label = { Text(stringResource(Res.string.book_reader_theme_sepia)) },
                    )
                    FilterChip(
                        selected = theme == ReaderTheme.LIGHT,
                        onClick = { viewModel.setReaderTheme(ReaderTheme.LIGHT) },
                        label = { Text(stringResource(Res.string.book_reader_theme_light)) },
                    )
                }
                Text(
                    text = stringResource(Res.string.book_reader_font_size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp).padding(top = 20.dp),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 4.dp),
                ) {
                    IconButton(onClick = { viewModel.adjustReaderFontSize(-1) }) {
                        Icon(imageVector = Tabler.Outline.Minus, contentDescription = null)
                    }
                    Text(
                        text = "$fontSizePx px",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    IconButton(onClick = { viewModel.adjustReaderFontSize(+1) }) {
                        Icon(imageVector = Tabler.Outline.Plus, contentDescription = null)
                    }
                }
                TextButton(
                    onClick = {
                        viewModel.dismissSettings()
                        showToc = true
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Icon(imageVector = Tabler.Outline.List, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(stringResource(Res.string.book_reader_toc))
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        if (showToc) {
            ModalBottomSheet(onDismissRequest = { showToc = false }) {
                SheetTitle(text = stringResource(Res.string.book_reader_toc))
                LazyColumn(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
                    items(tocItems.size) { index ->
                        val item = tocItems[index]
                        TextButton(
                            onClick = {
                                showToc = false
                                host.goTo(item.href)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = item.label.ifBlank { item.href },
                                maxLines = 2,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The shared auto-hiding top bar (title / back / settings). */
@Composable
private fun ReaderTopBar(
    state: BookReaderUiState.Ready,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = state.showControls,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Surface(color = Color.Black.copy(alpha = 0.6f)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
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
                    text = state.title.ifBlank {
                        stringResource(Res.string.book_reader_title_fallback)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Tabler.Outline.Settings,
                        contentDescription = null,
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderVeil(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            content()
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

/**
 * Auto-hide the reader chrome like player controls. [suppressed] are the
 * "a sheet holds the screen" flags (settings sheet, TOC) that pause the
 * countdown; any flip restarts it.
 */
@Composable
private fun AutoHideControlsEffect(
    visible: Boolean,
    vararg suppressed: Boolean,
    onTimeout: () -> Unit,
) {
    LaunchedEffect(visible, *suppressed.toTypedArray()) {
        if (visible && suppressed.all { !it }) {
            delay(CONTROLS_TIMEOUT_MS)
            onTimeout()
        }
    }
}

/**
 * Direction-aware tap zones: leading third = backward, trailing third =
 * forward, center = toggle chrome — with "leading" flipping under RTL.
 * [direction] keys the detector so a flip re-arms it with the new mapping.
 */
private fun Modifier.readerTapZones(
    direction: ReadingDirection,
    onForward: () -> Unit,
    onBackward: () -> Unit,
    onToggleControls: () -> Unit,
): Modifier = pointerInput(direction) {
    detectTapGestures { offset ->
        when {
            offset.x < size.width / 3f ->
                if (direction.isForwardFromLeft()) onForward() else onBackward()
            offset.x > size.width * 2f / 3f ->
                if (direction.isForwardFromLeft()) onBackward() else onForward()
            else -> onToggleControls()
        }
    }
}

/**
 * Physical-left input (left arrow / leading tap zone) pages forward under
 * RTL, backward under LTR — the one named place the direction mapping lives
 * for both the key handler and the tap zones.
 */
private fun ReadingDirection.isForwardFromLeft(): Boolean = this == ReadingDirection.RTL

/**
 * The shared reader input surface: keyboard/DPAD events (arrows follow the
 * reading direction, PageUp/PageDown stay physical) plus the direction-aware
 * tap zones. Both content kinds wrap their Box in this so keys and taps
 * cannot drift apart.
 */
private fun Modifier.readerInput(
    direction: ReadingDirection,
    onForward: () -> Unit,
    onBackward: () -> Unit,
    onBack: () -> Unit,
    onToggleControls: () -> Unit,
): Modifier = focusable()
    .onPreviewKeyEvent { event ->
        handleKeyEvent(
            event = event,
            direction = direction,
            onForward = onForward,
            onBackward = onBackward,
            onBack = onBack,
        )
    }
    .chromeToggleKey(onToggleControls)
    .readerTapZones(
        direction = direction,
        onForward = onForward,
        onBackward = onBackward,
        onToggleControls = onToggleControls,
    )

/** The bottom-sheet title every reader sheet (settings / TOC) starts with. */
@Composable
private fun SheetTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )
}

private const val CONTROLS_TIMEOUT_MS = 4_000L
private const val ERROR_AUTO_BACK_MS = 2_500L
