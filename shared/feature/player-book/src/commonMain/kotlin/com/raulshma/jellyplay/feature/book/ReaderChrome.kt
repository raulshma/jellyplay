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
import com.composables.icons.tabler.outline.List
import com.composables.icons.tabler.outline.MoonStars
import com.composables.icons.tabler.outline.PlayerPause
import com.composables.icons.tabler.outline.PlayerPlay
import com.composables.icons.tabler.outline.PlayerSkipBack
import com.composables.icons.tabler.outline.PlayerSkipForward
import com.composables.icons.tabler.outline.PlayerStop
import com.composables.icons.tabler.outline.Settings
import com.composables.icons.tabler.outline.Sun
import com.composables.icons.tabler.outline.Volume2
import com.raulshma.jellyplay.feature.book.generated.resources.Res
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_auto_scroll_start
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_auto_scroll_stop
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_brightness
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_chapter_pages_left
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_toc
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_minutes_left
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_minutes_left_chapter
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_page_indicator
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_pages_left
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_percent
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_speech_active
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_speech_next_sentence
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_speech_pause
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_speech_previous_sentence
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_speech_resume
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_speech_start
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_speech_stop
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_title_fallback
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/**
 * The reader's shared, auto-hiding chrome: the top bar (title / back / mark
 * entries), the per-format bottom bar (page slider + pages left vs read
 * percent + chapter time-left, both with the brightness row), the boot/error
 * veils' container, the sheet title and the copy-confirmation toast. Pure
 * presentation — every action is a callback, the ViewModel owns the state
 * (video-player screen conventions).
 */

/**
 * The auto-hiding top bar: back, title, and the reader's mark entries —
 * [onToggleBookmark] (filled when a bookmark sits at the current position),
 * the bookmarks sheet entry, and (reflowable only) the annotations sheet
 * entry plus the sleep-timer entry (moon icon, primary-tinted while a timer
 * runs — it stops read-aloud/auto-scroll, both reflowable concerns). The
 * settings gear stays the rightmost action on every format.
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
    sleepTimerActive: Boolean = false,
    onOpenSleepTimer: () -> Unit = {},
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
                    IconButton(onClick = onOpenSleepTimer) {
                        Icon(
                            imageVector = Tabler.Outline.MoonStars,
                            contentDescription = null,
                            tint = if (sleepTimerActive) MaterialTheme.colorScheme.primary else Color.White,
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
 * The brightness dim veil shared by BOTH content kinds: a plain background
 * Box placed ABOVE the page/web content and BELOW the chrome + sheets, so
 * the controls stay full-brightness. No `pointerInput` — it must never
 * intercept taps meant for the content underneath. Nothing renders at 100 %.
 */
@Composable
internal fun BrightnessDimOverlay(
    brightnessPct: Int,
    modifier: Modifier = Modifier,
) {
    if (brightnessPct >= 100) return
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = brightnessDimAlpha(brightnessPct))),
    )
}

/**
 * The compact sun-icon + slider brightness row embedded in the bottom bars.
 * Local drag state with commit-on-settle (the page-slider convention) — the
 * veil itself follows the persisted value once the drag finishes. [leading]
 * slots an optional action before the sun icon (the paged bar's TOC entry).
 */
@Composable
private fun BrightnessSliderRow(
    brightnessPct: Int,
    onBrightnessChange: (Int) -> Unit,
    leading: (@Composable () -> Unit)? = null,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableStateOf(100f) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
    ) {
        leading?.invoke()
        Icon(
            imageVector = Tabler.Outline.Sun,
            contentDescription = stringResource(Res.string.book_reader_brightness),
            tint = Color.White,
        )
        Slider(
            value = if (dragging) dragValue else brightnessPct.toFloat(),
            onValueChange = { dragging = true; dragValue = it },
            onValueChangeFinished = {
                dragging = false
                onBrightnessChange(dragValue.roundToInt().coerceIn(0, 100))
            },
            valueRange = 0f..100f,
            modifier = Modifier.weight(1f).padding(start = 12.dp),
        )
    }
}

/**
 * The paged reader's bottom bar: the brightness row (with the TOC entry when
 * the format has an outline), a page slider with a live "Page N of M" +
 * "N pages left" label. [onSeekPage] receives the 0-based target once the
 * drag settles — dragging must not page per frame.
 */
@Composable
internal fun PagedBottomBar(
    currentPage: Int,
    pageCount: Int,
    brightnessPct: Int,
    onBrightnessChange: (Int) -> Unit,
    onSeekPage: (Int) -> Unit,
    tocVisible: Boolean = false,
    onOpenToc: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Surface(color = Color.Black.copy(alpha = 0.6f), modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            BrightnessSliderRow(
                brightnessPct = brightnessPct,
                onBrightnessChange = onBrightnessChange,
                leading = if (tocVisible) {
                    {
                        TocIconButton(onClick = onOpenToc)
                    }
                } else {
                    null
                },
            )
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
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Text(
                text = stringResource(Res.string.book_reader_pages_left, pageCount - currentPage - 1),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp),
            )
        }
    }
}

/**
 * The read-aloud action row embedded in the reflowable bottom chrome.
 * [onOpenToc] renders the always-visible TOC entry (leading — reachable on
 * every book and platform without opening the settings sheet). [speechAvailable]
 * false (desktop/web) drops the speech cluster — the caption in the settings
 * sheet explains why; [speechActive] drives the indicator tint and the
 * skip/stop affordances (the play button doubles as start when no session is
 * live). The auto-scroll toggle joins the same row right-aligned (scrolled
 * flow only) — one compact transport strip.
 */
@Composable
private fun ReaderTransportRow(
    onOpenToc: () -> Unit,
    speechAvailable: Boolean,
    speechActive: Boolean,
    speechPaused: Boolean,
    onSpeechToggle: () -> Unit,
    onSpeechSkipBack: () -> Unit,
    onSpeechSkipForward: () -> Unit,
    onSpeechStop: () -> Unit,
    autoScrollVisible: Boolean,
    autoScrollActive: Boolean,
    onAutoScrollToggle: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
    ) {
        TocIconButton(onClick = onOpenToc)
        if (speechAvailable) {
            Icon(
                imageVector = Tabler.Outline.Volume2,
                contentDescription = stringResource(Res.string.book_reader_speech_active),
                tint = if (speechActive) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f),
            )
            if (speechActive) {
                IconButton(onClick = onSpeechSkipBack) {
                    Icon(
                        imageVector = Tabler.Outline.PlayerSkipBack,
                        contentDescription = stringResource(Res.string.book_reader_speech_previous_sentence),
                        tint = Color.White,
                    )
                }
            }
            IconButton(onClick = onSpeechToggle) {
                Icon(
                    imageVector = if (speechActive && !speechPaused) {
                        Tabler.Outline.PlayerPause
                    } else {
                        Tabler.Outline.PlayerPlay
                    },
                    contentDescription = stringResource(
                        when {
                            !speechActive -> Res.string.book_reader_speech_start
                            speechPaused -> Res.string.book_reader_speech_resume
                            else -> Res.string.book_reader_speech_pause
                        },
                    ),
                    tint = Color.White,
                )
            }
            if (speechActive) {
                IconButton(onClick = onSpeechSkipForward) {
                    Icon(
                        imageVector = Tabler.Outline.PlayerSkipForward,
                        contentDescription = stringResource(Res.string.book_reader_speech_next_sentence),
                        tint = Color.White,
                    )
                }
                IconButton(onClick = onSpeechStop) {
                    Icon(
                        imageVector = Tabler.Outline.PlayerStop,
                        contentDescription = stringResource(Res.string.book_reader_speech_stop),
                        tint = Color.White,
                    )
                }
            }
        }
        if (autoScrollVisible) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(onClick = onAutoScrollToggle) {
                    Icon(
                        imageVector = if (autoScrollActive) Tabler.Outline.PlayerPause else Tabler.Outline.PlayerPlay,
                        contentDescription = stringResource(
                            if (autoScrollActive) {
                                Res.string.book_reader_auto_scroll_stop
                            } else {
                                Res.string.book_reader_auto_scroll_start
                            },
                        ),
                        tint = if (autoScrollActive) MaterialTheme.colorScheme.primary else Color.White,
                    )
                }
            }
        }
    }
}

/**
 * The reflowable reader's bottom bar: the read-aloud / auto-scroll transport
 * row with the always-visible TOC entry (speech auto-scroll gated inside —
 * the row itself stays so TOC is reachable on every book), the brightness
 * row, the read percent, the chapter-scoped pages the relocation event
 * reports, and the two "≈ N min left" estimates from
 * [locationPagesMinutesRemaining] — chapter-scoped (the relocated event's
 * chapter pages) and book-scoped (the whole-book location list) (null rows
 * simply drop — before locations exist there is nothing to report).
 */
@Composable
internal fun ReflowableBottomBar(
    percent: Double,
    remainingPages: Int?,
    minutesLeftInChapter: Int?,
    minutesLeftInBook: Int?,
    brightnessPct: Int,
    onBrightnessChange: (Int) -> Unit,
    onOpenToc: () -> Unit = {},
    speechAvailable: Boolean = false,
    speechActive: Boolean = false,
    speechPaused: Boolean = false,
    onSpeechToggle: () -> Unit = {},
    onSpeechSkipBack: () -> Unit = {},
    onSpeechSkipForward: () -> Unit = {},
    onSpeechStop: () -> Unit = {},
    autoScrollVisible: Boolean = false,
    autoScrollActive: Boolean = false,
    onAutoScrollToggle: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Surface(color = Color.Black.copy(alpha = 0.6f), modifier = modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            ReaderTransportRow(
                onOpenToc = onOpenToc,
                speechAvailable = speechAvailable,
                speechActive = speechActive,
                speechPaused = speechPaused,
                onSpeechToggle = onSpeechToggle,
                onSpeechSkipBack = onSpeechSkipBack,
                onSpeechSkipForward = onSpeechSkipForward,
                onSpeechStop = onSpeechStop,
                autoScrollVisible = autoScrollVisible,
                autoScrollActive = autoScrollActive,
                onAutoScrollToggle = onAutoScrollToggle,
            )
            if (speechActive) {
                Text(
                    text = stringResource(Res.string.book_reader_speech_active),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            BrightnessSliderRow(brightnessPct = brightnessPct, onBrightnessChange = onBrightnessChange)
            Text(
                text = stringResource(
                    Res.string.book_reader_percent,
                    (percent * 100).roundToInt().coerceIn(0, 100),
                ),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
            )
            remainingPages?.let { pages ->
                ChromeStatLine(stringResource(Res.string.book_reader_chapter_pages_left, pages))
            }
            minutesLeftInChapter?.let { minutes ->
                ChromeStatLine(stringResource(Res.string.book_reader_minutes_left_chapter, minutes))
            }
            minutesLeftInBook?.let { minutes ->
                ChromeStatLine(stringResource(Res.string.book_reader_minutes_left, minutes))
            }
        }
    }
}

/** The chrome's table-of-contents entry (both bottom bars). */
@Composable
private fun TocIconButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = Tabler.Outline.List,
            contentDescription = stringResource(Res.string.book_reader_toc),
            tint = Color.White,
        )
    }
}

/** One dim stat line in the reflowable bottom chrome ("N pages left", "≈ N min left"). */
@Composable
private fun ChromeStatLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = Color.White.copy(alpha = 0.7f),
    )
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
