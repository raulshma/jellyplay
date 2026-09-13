package com.raulshma.jellyplay.feature.book

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Bookmark
import com.composables.icons.tabler.outline.List
import com.composables.icons.tabler.outline.Minus
import com.composables.icons.tabler.outline.Plus
import com.composables.icons.tabler.outline.Search
import com.composables.icons.tabler.outline.Trash
import com.raulshma.jellyplay.core.data.repository.ReaderBookmark
import com.raulshma.jellyplay.core.datastore.reader.ReaderFontFamily
import com.raulshma.jellyplay.core.datastore.reader.ReadingDirection
import com.raulshma.jellyplay.core.datastore.reader.ReaderStore
import com.raulshma.jellyplay.core.datastore.reader.ReaderTheme
import com.raulshma.jellyplay.core.model.BookProgressPolicy
import com.raulshma.jellyplay.feature.book.epub.EpubSearchResult
import com.raulshma.jellyplay.feature.book.epub.EpubTocItem
import com.raulshma.jellyplay.feature.book.generated.resources.Res
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_animated_turns
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_auto_scroll_speed
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_behavior
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_bookmark_page
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_bookmark_percent
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_bookmarks
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_bookmarks_empty
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_dialog_cancel
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_direction_ltr
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_direction_rtl
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_fit_mode
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_fit_original
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_fit_page
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_fit_width
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_font_family
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_font_mono
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_font_sans
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_font_serif
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_font_size
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_font_system
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_justify
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_line_height
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_line_height_value
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_margins
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_per_book
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_read_aloud
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_reading_speed
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_scroll_mode
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_search
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_search_hint
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_search_no_results
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_search_searching
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_settings
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_sleep_timer
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_sleep_timer_end_of_chapter
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_speech_pitch
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_speech_rate
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_speech_unavailable
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_theme
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_theme_dark
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_theme_light
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_theme_sepia
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_toc
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_toc_empty
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_typography
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_volume_keys
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource

/**
 * The reader's bottom sheets: per-format reading settings, the TOC (EPUB nav
 * document / PDF outline tree), the bookmarks list and the in-book search.
 * All state is caller-owned; every sheet is pure presentation + callbacks,
 * and every list sheet keeps its own dismiss request at the caller's elbow
 * (tap-through must jump AND dismiss in one gesture).
 */

/**
 * The reflowable typography bundle the settings sheet edits as one copy-on-
 * change value (chips/switches commit immediately; sliders commit on settle).
 * The VM's individual setters stay the write surface — the caller diffs.
 */
internal data class ReaderTypographyState(
    val fontFamily: ReaderFontFamily,
    val lineHeightPct: Int,
    val marginPct: Int,
    val justify: Boolean,
    val scrollMode: Boolean,
)

/**
 * The behavior bundle (volume-key paging, animated page turns, reading
 * speed) — same copy-on-change edit contract as [ReaderTypographyState].
 */
internal data class ReaderBehaviorState(
    val volumeKeyPaging: Boolean,
    val animatedPageTurns: Boolean,
    val readingSpeedWpm: Int,
)

/** The section label the settings sheets repeat between groups. */
@Composable
private fun SectionLabel(text: String, topPadding: androidx.compose.ui.unit.Dp = 20.dp) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp).padding(top = topPadding),
    )
}

/** A label + Switch row (justify / scroll mode / behavior toggles). */
@Composable
private fun SettingsSwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/**
 * The settings sheet for PAGED books: the per-book reading direction chips,
 * the per-session page-fit chips ([ReaderFitMode] is view state — deliberately
 * not persisted), the behavior section, plus the TOC entry when the format
 * has one (PDF outlines only — CBZ/CBR books have no TOC story, so the row is
 * absent rather than disabled).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PagedSettingsSheet(
    direction: ReadingDirection,
    tocAvailable: Boolean,
    fitMode: ReaderFitMode,
    behavior: ReaderBehaviorState,
    onSetDirection: (ReadingDirection) -> Unit,
    onSetFitMode: (ReaderFitMode) -> Unit,
    onBehaviorChange: (ReaderBehaviorState) -> Unit,
    onOpenToc: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        SheetTitle(text = stringResource(Res.string.book_reader_settings))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FilterChip(
                selected = direction == ReadingDirection.LTR,
                onClick = { onSetDirection(ReadingDirection.LTR) },
                label = { Text(stringResource(Res.string.book_reader_direction_ltr)) },
            )
            FilterChip(
                selected = direction == ReadingDirection.RTL,
                onClick = { onSetDirection(ReadingDirection.RTL) },
                label = { Text(stringResource(Res.string.book_reader_direction_rtl)) },
            )
        }
        SectionLabel(text = stringResource(Res.string.book_reader_fit_mode), topPadding = 8.dp)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FilterChip(
                selected = fitMode == ReaderFitMode.FIT_WIDTH,
                onClick = { onSetFitMode(ReaderFitMode.FIT_WIDTH) },
                label = { Text(stringResource(Res.string.book_reader_fit_width)) },
            )
            FilterChip(
                selected = fitMode == ReaderFitMode.FIT_PAGE,
                onClick = { onSetFitMode(ReaderFitMode.FIT_PAGE) },
                label = { Text(stringResource(Res.string.book_reader_fit_page)) },
            )
            FilterChip(
                selected = fitMode == ReaderFitMode.ORIGINAL,
                onClick = { onSetFitMode(ReaderFitMode.ORIGINAL) },
                label = { Text(stringResource(Res.string.book_reader_fit_original)) },
            )
        }
        SectionLabel(text = stringResource(Res.string.book_reader_behavior))
        SettingsSwitchRow(
            label = stringResource(Res.string.book_reader_volume_keys),
            checked = behavior.volumeKeyPaging,
            onChange = { onBehaviorChange(behavior.copy(volumeKeyPaging = it)) },
        )
        SettingsSwitchRow(
            label = stringResource(Res.string.book_reader_animated_turns),
            checked = behavior.animatedPageTurns,
            onChange = { onBehaviorChange(behavior.copy(animatedPageTurns = it)) },
        )
        if (tocAvailable) {
            TextButton(
                onClick = onOpenToc,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Icon(imageVector = Tabler.Outline.List, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text(stringResource(Res.string.book_reader_toc))
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * The settings sheet for REFLOWABLE books: theme, font size, the per-book
 * override switch, the typography section (family / line height / margins /
 * justify / scroll mode), the behavior section with the reading-speed
 * stepper and (scrolled flow only) the auto-scroll speed slider, the
 * read-aloud section (rate/pitch steppers; an availability caption replaces
 * them where the platform has no engine), and the TOC / search entries. The
 * TOC entry stays here (v1 behavior) — the search entry rides the TOC sheet
 * to keep the top bar uncluttered.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReflowableSettingsSheet(
    theme: ReaderTheme,
    fontSizePx: Int,
    perBook: Boolean,
    typography: ReaderTypographyState,
    behavior: ReaderBehaviorState,
    speechRate: Int,
    speechPitch: Int,
    speechAvailable: Boolean,
    autoScrollSpeedPx: Int,
    onSetTheme: (ReaderTheme) -> Unit,
    onAdjustFontSize: (Int) -> Unit,
    onSetPerBook: (Boolean) -> Unit,
    onTypographyChange: (ReaderTypographyState) -> Unit,
    onBehaviorChange: (ReaderBehaviorState) -> Unit,
    onSetSpeechRate: (Int) -> Unit,
    onSetSpeechPitch: (Int) -> Unit,
    onSetAutoScrollSpeed: (Int) -> Unit,
    onOpenToc: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        SheetTitle(text = stringResource(Res.string.book_reader_settings))
        SectionLabel(text = stringResource(Res.string.book_reader_theme), topPadding = 0.dp)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FilterChip(
                selected = theme == ReaderTheme.DARK,
                onClick = { onSetTheme(ReaderTheme.DARK) },
                label = { Text(stringResource(Res.string.book_reader_theme_dark)) },
            )
            FilterChip(
                selected = theme == ReaderTheme.SEPIA,
                onClick = { onSetTheme(ReaderTheme.SEPIA) },
                label = { Text(stringResource(Res.string.book_reader_theme_sepia)) },
            )
            FilterChip(
                selected = theme == ReaderTheme.LIGHT,
                onClick = { onSetTheme(ReaderTheme.LIGHT) },
                label = { Text(stringResource(Res.string.book_reader_theme_light)) },
            )
        }
        SectionLabel(text = stringResource(Res.string.book_reader_font_size))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 4.dp),
        ) {
            IconButton(onClick = { onAdjustFontSize(-1) }) {
                Icon(imageVector = Tabler.Outline.Minus, contentDescription = null)
            }
            Text(
                text = "$fontSizePx px",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            IconButton(onClick = { onAdjustFontSize(+1) }) {
                Icon(imageVector = Tabler.Outline.Plus, contentDescription = null)
            }
        }
        SettingsSwitchRow(
            label = stringResource(Res.string.book_reader_per_book),
            checked = perBook,
            onChange = onSetPerBook,
        )

        SectionLabel(text = stringResource(Res.string.book_reader_typography))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = typography.fontFamily == ReaderFontFamily.SYSTEM,
                onClick = { onTypographyChange(typography.copy(fontFamily = ReaderFontFamily.SYSTEM)) },
                label = { Text(stringResource(Res.string.book_reader_font_system)) },
            )
            FilterChip(
                selected = typography.fontFamily == ReaderFontFamily.SERIF,
                onClick = { onTypographyChange(typography.copy(fontFamily = ReaderFontFamily.SERIF)) },
                label = { Text(stringResource(Res.string.book_reader_font_serif)) },
            )
            FilterChip(
                selected = typography.fontFamily == ReaderFontFamily.SANS,
                onClick = { onTypographyChange(typography.copy(fontFamily = ReaderFontFamily.SANS)) },
                label = { Text(stringResource(Res.string.book_reader_font_sans)) },
            )
            FilterChip(
                selected = typography.fontFamily == ReaderFontFamily.MONO,
                onClick = { onTypographyChange(typography.copy(fontFamily = ReaderFontFamily.MONO)) },
                label = { Text(stringResource(Res.string.book_reader_font_mono)) },
            )
        }
        LineHeightSlider(lineHeightPct = typography.lineHeightPct) {
            onTypographyChange(typography.copy(lineHeightPct = it))
        }
        MarginSlider(marginPct = typography.marginPct) {
            onTypographyChange(typography.copy(marginPct = it))
        }
        SettingsSwitchRow(
            label = stringResource(Res.string.book_reader_justify),
            checked = typography.justify,
            onChange = { onTypographyChange(typography.copy(justify = it)) },
        )
        SettingsSwitchRow(
            label = stringResource(Res.string.book_reader_scroll_mode),
            checked = typography.scrollMode,
            onChange = { onTypographyChange(typography.copy(scrollMode = it)) },
        )

        SectionLabel(text = stringResource(Res.string.book_reader_behavior))
        SettingsSwitchRow(
            label = stringResource(Res.string.book_reader_volume_keys),
            checked = behavior.volumeKeyPaging,
            onChange = { onBehaviorChange(behavior.copy(volumeKeyPaging = it)) },
        )
        ReadingSpeedStepper(wpm = behavior.readingSpeedWpm) {
            onBehaviorChange(behavior.copy(readingSpeedWpm = it))
        }
        if (typography.scrollMode) {
            AutoScrollSpeedSlider(speedPxPerSec = autoScrollSpeedPx, onCommit = onSetAutoScrollSpeed)
        }

        SectionLabel(text = stringResource(Res.string.book_reader_read_aloud))
        if (!speechAvailable) {
            SheetEmptyText(text = stringResource(Res.string.book_reader_speech_unavailable))
        } else {
            SpeechRateStepper(
                label = stringResource(Res.string.book_reader_speech_rate),
                pct = speechRate,
                onCommit = onSetSpeechRate,
            )
            SpeechRateStepper(
                label = stringResource(Res.string.book_reader_speech_pitch),
                pct = speechPitch,
                onCommit = onSetSpeechPitch,
            )
        }
        TextButton(
            onClick = onOpenToc,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Icon(imageVector = Tabler.Outline.List, contentDescription = null)
            Spacer(modifier = Modifier.size(8.dp))
            Text(stringResource(Res.string.book_reader_toc))
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * Line-height slider (1.0×–2.0×, the store band / 100). Commit-on-settle per
 * the page-slider convention; the "1.6×" style value label rides along.
 */
@Composable
private fun LineHeightSlider(lineHeightPct: Int, onCommit: (Int) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 8.dp)) {
        Text(
            text = stringResource(Res.string.book_reader_line_height),
            style = MaterialTheme.typography.bodyLarge,
        )
        var dragging by remember { mutableStateOf(false) }
        var dragValue by remember { mutableStateOf(160f) }
        Slider(
            value = if (dragging) dragValue else lineHeightPct.toFloat(),
            onValueChange = { dragging = true; dragValue = it },
            onValueChangeFinished = {
                dragging = false
                onCommit(dragValue.roundToInt().coerceIn(ReaderStore.MIN_LINE_HEIGHT_PCT, ReaderStore.MAX_LINE_HEIGHT_PCT))
            },
            valueRange = ReaderStore.MIN_LINE_HEIGHT_PCT.toFloat()..ReaderStore.MAX_LINE_HEIGHT_PCT.toFloat(),
        )
        Text(
            text = stringResource(
                Res.string.book_reader_line_height_value,
                (if (dragging) dragValue else lineHeightPct.toFloat()) / 100f,
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Margin slider (0..100 %); commit-on-settle, plain "N %" value label. */
@Composable
private fun MarginSlider(marginPct: Int, onCommit: (Int) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 8.dp)) {
        Text(
            text = stringResource(Res.string.book_reader_margins),
            style = MaterialTheme.typography.bodyLarge,
        )
        var dragging by remember { mutableStateOf(false) }
        var dragValue by remember { mutableStateOf(8f) }
        Slider(
            value = if (dragging) dragValue else marginPct.toFloat(),
            onValueChange = { dragging = true; dragValue = it },
            onValueChangeFinished = {
                dragging = false
                onCommit(dragValue.roundToInt().coerceIn(ReaderStore.MIN_MARGIN_PCT, ReaderStore.MAX_MARGIN_PCT))
            },
            valueRange = ReaderStore.MIN_MARGIN_PCT.toFloat()..ReaderStore.MAX_MARGIN_PCT.toFloat(),
        )
        Text(
            text = "${(if (dragging) dragValue else marginPct.toFloat()).roundToInt()} %",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Reading-speed stepper (100..1000 wpm, ±10 per tap) feeding the time-left estimate. */
@Composable
private fun ReadingSpeedStepper(wpm: Int, onCommit: (Int) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 4.dp)) {
        Text(
            text = stringResource(Res.string.book_reader_reading_speed),
            style = MaterialTheme.typography.bodyLarge,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onCommit(wpm - 10) }) {
                Icon(imageVector = Tabler.Outline.Minus, contentDescription = null)
            }
            Text(
                text = "$wpm",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            IconButton(onClick = { onCommit(wpm + 10) }) {
                Icon(imageVector = Tabler.Outline.Plus, contentDescription = null)
            }
        }
    }
}

/**
 * Read-aloud voice stepper (rate or pitch — both share the store's 50..200 %
 * band, ±10 per tap). The caller clamps through the store setter; the sheet
 * steps within the band directly so the label cannot render out-of-range.
 */
@Composable
private fun SpeechRateStepper(label: String, pct: Int, onCommit: (Int) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onCommit(pct - 10) }) {
                Icon(imageVector = Tabler.Outline.Minus, contentDescription = null)
            }
            Text(
                text = "$pct %",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            IconButton(onClick = { onCommit(pct + 10) }) {
                Icon(imageVector = Tabler.Outline.Plus, contentDescription = null)
            }
        }
    }
}

/**
 * Auto-scroll speed slider (px/s, [MIN_AUTO_SCROLL_PX_PER_SEC]..
 * [MAX_AUTO_SCROLL_PX_PER_SEC]); commit-on-settle per the page-slider
 * convention. Deliberately NO store key: the speed is a per-session view
 * knob (like the paged fit mode) — every session restarts at
 * [DEFAULT_AUTO_SCROLL_PX_PER_SEC].
 */
@Composable
private fun AutoScrollSpeedSlider(speedPxPerSec: Int, onCommit: (Int) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 8.dp)) {
        Text(
            text = stringResource(Res.string.book_reader_auto_scroll_speed),
            style = MaterialTheme.typography.bodyLarge,
        )
        var dragging by remember { mutableStateOf(false) }
        var dragValue by remember { mutableStateOf(DEFAULT_AUTO_SCROLL_PX_PER_SEC.toFloat()) }
        Slider(
            value = if (dragging) dragValue else speedPxPerSec.toFloat(),
            onValueChange = { dragging = true; dragValue = it },
            onValueChangeFinished = {
                dragging = false
                onCommit(dragValue.roundToInt().coerceIn(MIN_AUTO_SCROLL_PX_PER_SEC, MAX_AUTO_SCROLL_PX_PER_SEC))
            },
            valueRange = MIN_AUTO_SCROLL_PX_PER_SEC.toFloat()..MAX_AUTO_SCROLL_PX_PER_SEC.toFloat(),
        )
        Text(
            text = "${(if (dragging) dragValue else speedPxPerSec.toFloat()).roundToInt()} px/s",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The sleep-timer sheet (reflowable reader): minute presets as FilterChips
 * plus the end-of-chapter arm, a running countdown row with cancel — the
 * audio player's sleep sheet UX mirrored at reader scale (option labels
 * match its language; books get 5/15/30/60 rather than the player's
 * 15..90 because reading sessions run shorter).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SleepTimerSheet(
    state: ReaderSleepTimerState,
    onSelect: (ReaderSleepOption) -> Unit,
    onCancel: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        SheetTitle(text = stringResource(Res.string.book_reader_sleep_timer))
        if (state.running) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            ) {
                Text(
                    text = when (val option = state.option) {
                        is ReaderSleepOption.EndOfChapter ->
                            stringResource(Res.string.book_reader_sleep_timer_end_of_chapter)
                        is ReaderSleepOption.Timed, null ->
                            formatSleepCountdown(state.remainingMillis ?: 0L)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                TextButton(onClick = onCancel) {
                    Text(
                        text = stringResource(Res.string.book_reader_dialog_cancel),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SLEEP_TIMER_PRESET_MINUTES.forEach { minutes ->
                FilterChip(
                    selected = state.running && state.option == ReaderSleepOption.Timed(minutes),
                    onClick = { onSelect(ReaderSleepOption.Timed(minutes)) },
                    label = { Text("${minutes}m") },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        FilterChip(
            selected = state.running && state.option == ReaderSleepOption.EndOfChapter,
            onClick = { onSelect(ReaderSleepOption.EndOfChapter) },
            label = { Text(stringResource(Res.string.book_reader_sleep_timer_end_of_chapter)) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 12.dp),
        )
        Spacer(modifier = Modifier.height(24.dp))
    }
}

/** Countdown label `m:ss` / `h:mm:ss`; ceil-rounded so 0:00 only shows at fire. */
internal fun formatSleepCountdown(millis: Long): String {
    val totalSeconds = ((millis.coerceAtLeast(0L)) + 999L) / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    val two = { value: Long -> if (value < 10L) "0$value" else "$value" }
    return if (hours > 0L) "$hours:${two(minutes)}:${two(seconds)}" else "$minutes:${two(seconds)}"
}

/**
 * The EPUB TOC sheet (flattened nav document) with the in-book search entry
 * pinned above the list — one sheet owns "where am I / find something".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EpubTocSheet(
    tocItems: List<EpubTocItem>,
    onJump: (href: String) -> Unit,
    onOpenSearch: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        SheetTitle(text = stringResource(Res.string.book_reader_toc))
        TextButton(
            onClick = onOpenSearch,
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            Icon(imageVector = Tabler.Outline.Search, contentDescription = null)
            Spacer(modifier = Modifier.size(8.dp))
            Text(stringResource(Res.string.book_reader_search))
        }
        if (tocItems.isEmpty()) {
            SheetEmptyText(text = stringResource(Res.string.book_reader_toc_empty))
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
                items(tocItems) { item ->
                    TextButton(
                        onClick = { onJump(item.href) },
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

/**
 * The paged PDF outline sheet: an indented tree of [PdfOutlineNode] rows.
 * Nodes whose destination never resolved (null [PdfOutlineNode.pageIndex])
 * render disabled — the title stays readable, the jump does not fire. CBZ
 * and CBR books never open this sheet (they have no TOC story at all).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PdfOutlineSheet(
    nodes: List<PdfOutlineNode>,
    onJump: (pageIndex: Int) -> Unit,
    onDismissRequest: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        SheetTitle(text = stringResource(Res.string.book_reader_toc))
        if (nodes.isEmpty()) {
            SheetEmptyText(text = stringResource(Res.string.book_reader_toc_empty))
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
                outlineRows(nodes, depth = 0) { page ->
                    onJump(page)
                }
            }
        }
    }
}

/** Flattens the outline tree into indented, jumpable rows. */
private fun androidx.compose.foundation.lazy.LazyListScope.outlineRows(
    nodes: List<PdfOutlineNode>,
    depth: Int,
    onJump: (Int) -> Unit,
) {
    nodes.forEach { node ->
        item(key = "outline-${depth}-${node.title}-${node.pageIndex}") {
            val page = node.pageIndex
            TextButton(
                onClick = { page?.let(onJump) },
                enabled = page != null,
                modifier = Modifier.fillMaxWidth().padding(start = (16 + depth * 16).dp),
            ) {
                Text(text = node.title, maxLines = 2)
            }
        }
        if (node.children.isNotEmpty()) {
            outlineRows(node.children, depth + 1, onJump)
        }
    }
}

/**
 * The bookmarks sheet: one row per bookmark — chapter label (falling back to
 * the book title) plus the encoded position rendered as "Page N" (paged,
 * null CFI) or a percent (reflowable). Tap jumps + dismisses; the trash
 * removes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BookmarksSheet(
    bookmarks: List<ReaderBookmark>,
    title: String,
    onJump: (ReaderBookmark) -> Unit,
    onDelete: (ReaderBookmark) -> Unit,
    onDismissRequest: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        SheetTitle(text = stringResource(Res.string.book_reader_bookmarks))
        if (bookmarks.isEmpty()) {
            SheetEmptyText(text = stringResource(Res.string.book_reader_bookmarks_empty))
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
                items(bookmarks, key = { it.id }) { bookmark ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = bookmark.chapterLabel.ifBlank { title },
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            BookmarkLocationText(bookmark = bookmark)
                        }
                        IconButton(onClick = { onDelete(bookmark) }) {
                            Icon(
                                imageVector = Tabler.Outline.Trash,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { onJump(bookmark) }) {
                            Icon(imageVector = Tabler.Outline.Bookmark, contentDescription = null)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Renders a bookmark's stored position the way its book encodes it: paged
 * rows (null CFI) decode their ticks to a 1-based page, reflowable rows to
 * a percent — both through the shared [BookProgressPolicy] decoders, so the
 * sheet always matches what the writer stored.
 */
@Composable
private fun BookmarkLocationText(bookmark: ReaderBookmark) {
    val label = if (bookmark.cfi == null) {
        stringResource(
            Res.string.book_reader_bookmark_page,
            BookProgressPolicy.ticksToPage(bookmark.positionTicks) + 1,
        )
    } else {
        stringResource(
            Res.string.book_reader_bookmark_percent,
            (BookProgressPolicy.ticksToPercent(bookmark.positionTicks) * 100).roundToInt().coerceIn(0, 100),
        )
    }
    Text(
        text = label,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** The search sheet's result set, token-filtered by the caller. */
internal sealed interface ReaderSearchState {
    data object Idle : ReaderSearchState

    data object Searching : ReaderSearchState

    data class Results(val rows: List<EpubSearchResult>) : ReaderSearchState
}

/**
 * The in-book search sheet (EPUB only): a debounced query field over the
 * WebView host's full-text scan. The 400 ms debounce, the incrementing token
 * and the result filtering all live HERE so the parent only supplies the
 * raw host plumbing (`onSearch` → `host.search(query, token)`, results
 * arriving back through `state`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SearchSheet(
    state: ReaderSearchState,
    onSearch: (query: String, token: Int) -> Unit,
    onResultTap: (EpubSearchResult) -> Unit,
    onDismissRequest: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        SheetTitle(text = stringResource(Res.string.book_reader_search))
        var query by remember { mutableStateOf("") }
        var token by remember { mutableStateOf(0) }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(Res.string.book_reader_search_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        )
        // Debounce: only a query that survives 400 ms fires a scan; each
        // fired scan increments the token so late results of older queries
        // are dropped (host-side token guard mirrors this).
        LaunchedEffect(query) {
            if (query.isBlank()) return@LaunchedEffect
            delay(SEARCH_DEBOUNCE_MS)
            token += 1
            onSearch(query, token)
        }
        when (state) {
            is ReaderSearchState.Searching -> Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
                Text(
                    text = stringResource(Res.string.book_reader_search_searching),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            is ReaderSearchState.Results -> if (state.rows.isEmpty()) {
                SheetEmptyText(text = stringResource(Res.string.book_reader_search_no_results))
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
                    items(state.rows, key = { it.cfi }) { row ->
                        TextButton(
                            onClick = { onResultTap(row) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column {
                                if (row.chapter.isNotBlank()) {
                                    Text(
                                        text = row.chapter,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Text(
                                    text = row.excerpt,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
            ReaderSearchState.Idle -> Unit
        }
    }
}

/** The shared "this sheet has nothing to show" line. */
@Composable
private fun SheetEmptyText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp),
    )
}

internal val SEARCH_DEBOUNCE_MS = 400L

/** Sleep-timer minute presets (reader-scaled; the audio player's run 15..90). */
internal val SLEEP_TIMER_PRESET_MINUTES = listOf(5, 15, 30, 60)

/** Auto-scroll speed band + session default (px/s) — deliberately no store key. */
internal val MIN_AUTO_SCROLL_PX_PER_SEC = 20
internal val MAX_AUTO_SCROLL_PX_PER_SEC = 120
internal val DEFAULT_AUTO_SCROLL_PX_PER_SEC = 40
