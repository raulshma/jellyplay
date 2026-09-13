package com.raulshma.jellyplay.feature.book

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.ArrowLeft
import com.composables.icons.tabler.outline.Bookmark
import com.composables.icons.tabler.outline.Bookmarks
import com.composables.icons.tabler.outline.Highlight
import com.composables.icons.tabler.outline.Settings
import com.raulshma.jellyplay.feature.book.generated.resources.Res
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_page_indicator
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_percent
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_title_fallback
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/**
 * The reader's shared, auto-hiding chrome: the top bar (title / back / mark
 * entries), the per-format bottom bar (page slider vs read percent), the
 * boot/error veils' container, the sheet title and the copy-confirmation
 * toast. Pure presentation — every action is a callback, the ViewModel owns
 * the state (video-player screen conventions).
 */

/**
 * The auto-hiding top bar: back, title, and the reader's mark entries —
 * [onToggleBookmark] (filled when a bookmark sits at the current position),
 * the bookmarks sheet entry, and (reflowable only) the annotations sheet
 * entry. The settings gear stays the rightmost action on every format.
 */
@Composable
internal fun ReaderTopBar(
    state: BookReaderUiState.Ready,
    bookmarked: Boolean,
    onToggleBookmark: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenAnnotations: () -> Unit,
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
                    text = state.title.ifBlank {
                        stringResource(Res.string.book_reader_title_fallback)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onToggleBookmark) {
                    Icon(
                        imageVector = Tabler.Outline.Bookmark,
                        contentDescription = null,
                        tint = if (bookmarked) MaterialTheme.colorScheme.primary else Color.White,
                    )
                }
                IconButton(onClick = onOpenBookmarks) {
                    Icon(
                        imageVector = Tabler.Outline.Bookmarks,
                        contentDescription = null,
                        tint = Color.White,
                    )
                }
                if (state.content is ReadyContent.Reflowable) {
                    IconButton(onClick = onOpenAnnotations) {
                        Icon(
                            imageVector = Tabler.Outline.Highlight,
                            contentDescription = null,
                            tint = Color.White,
                        )
                    }
                }
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

/**
 * The paged reader's bottom bar: a page slider with a live "Page N of M"
 * label. [onSeekPage] receives the 0-based target once the drag settles —
 * dragging must not page per frame.
 */
@Composable
internal fun PagedBottomBar(
    currentPage: Int,
    pageCount: Int,
    onSeekPage: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(color = Color.Black.copy(alpha = 0.6f), modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            var dragging by remember { mutableStateOf(false) }
            var dragValue by remember { mutableStateOf(1f) }
            Slider(
                value = if (dragging) dragValue else (currentPage + 1).toFloat(),
                onValueChange = { dragging = true; dragValue = it },
                onValueChangeFinished = {
                    dragging = false
                    val target = dragValue.toInt().coerceIn(1, pageCount)
                    onSeekPage(target - 1)
                },
                valueRange = 1f..pageCount.coerceAtLeast(1).toFloat(),
            )
            Text(
                text = stringResource(
                    Res.string.book_reader_page_indicator,
                    currentPage + 1,
                    pageCount,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp),
            )
        }
    }
}

/** The reflowable reader's bottom bar: a plain read-percent label. */
@Composable
internal fun ReflowableBottomBar(
    percent: Double,
    modifier: Modifier = Modifier,
) {
    Surface(color = Color.Black.copy(alpha = 0.6f), modifier = modifier) {
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

/** Full-screen dark veil over the booting reader (spinner / download progress). */
@Composable
internal fun ReaderVeil(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            content()
        }
    }
}

/** The bottom-sheet title every reader sheet (settings / TOC / marks) starts with. */
@Composable
internal fun SheetTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )
}

/**
 * Auto-hide the reader chrome like player controls. [suppressed] are the
 * "a sheet holds the screen" flags (settings sheet, TOC, marks) that pause
 * the countdown; any flip restarts it.
 */
@Composable
internal fun AutoHideControlsEffect(
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

internal val CONTROLS_TIMEOUT_MS = 4_000L
internal val ERROR_AUTO_BACK_MS = 2_500L
