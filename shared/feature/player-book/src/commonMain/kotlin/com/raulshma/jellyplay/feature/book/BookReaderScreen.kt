package com.raulshma.jellyplay.feature.book

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Book
import com.raulshma.jellyplay.core.datastore.reader.ReadingDirection
import com.raulshma.jellyplay.core.datastore.reader.ReaderTheme
import com.raulshma.jellyplay.core.ui.components.JellyPlayBackHandler
import com.raulshma.jellyplay.feature.book.generated.resources.Res
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_error_cannot_open
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_error_no_path
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_error_unsupported_with_format
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_loading
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * The full-screen book reader: paged books render a pager over rasterized
 * pages (pinch/double-tap zoom, fit modes); reflowable (EPUB) books render
 * the platform WebView host (full typography, live appearance pushes). Both
 * share the auto-hiding chrome (with the brightness veil under it),
 * direction-aware navigation and debounced progress reporting (the VM owns
 * the writes; the screen only feeds events). The renderers live in
 * ReaderContent.kt, the chrome in ReaderChrome.kt, input in ReaderInput.kt
 * and the sheets in ReaderSheets.kt / ReaderSelection.kt — this file is the
 * state router plus the boot/error veils.
 */
@Composable
fun BookReaderScreen(
    itemId: String,
    onBack: () -> Unit,
    viewModel: BookReaderViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val direction by viewModel.readingDirection.collectAsStateWithLifecycle()
    // EFFECTIVE theme/font: the per-book override when one exists, else the
    // global — the only values the reader renders with.
    val theme by viewModel.effectiveReaderTheme.collectAsStateWithLifecycle()
    val fontSizePx by viewModel.effectiveReaderFontSizePx.collectAsStateWithLifecycle()

    val windowOps = rememberReaderWindowOps()
    DisposableEffect(Unit) {
        windowOps.enter()
        onDispose {
            windowOps.exit()
            viewModel.reportNow()
        }
    }

    // The reader is a full-screen player-class route: the shell's global back
    // handler is skipped while it is open (JellyPlayApp's isFullScreenRoute
    // branch), so it must intercept the system back itself — without this the
    // gesture/button falls through to the Activity and closes the app (the
    // video player's precedent). Open ModalBottomSheets intercept back through
    // their own windows first, so this only fires when the reader is exposed.
    JellyPlayBackHandler(enabled = true, onBack = onBack)

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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoadingVeil(state: BookReaderUiState.Loading) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (state.progress != null) {
                LinearWavyProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.fillMaxWidth(0.5f),
                )
            } else {
                LoadingIndicator(modifier = Modifier.size(48.dp))
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
        BookReaderUiState.ErrorReason.UnsupportedFormat -> when {
            state.detail != null ->
                stringResource(Res.string.book_reader_error_unsupported_with_format, state.detail)
            else ->
                stringResource(Res.string.book_reader_error_no_path)
        }
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
