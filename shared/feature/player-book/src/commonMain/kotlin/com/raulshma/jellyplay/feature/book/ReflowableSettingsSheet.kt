package com.raulshma.jellyplay.feature.book

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.List
import com.composables.icons.tabler.outline.Minus
import com.composables.icons.tabler.outline.Plus
import com.raulshma.jellyplay.core.datastore.reader.ReaderFontFamily
import com.raulshma.jellyplay.core.datastore.reader.ReaderTheme
import com.raulshma.jellyplay.feature.book.generated.resources.Res
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_auto_scroll_speed
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_behavior
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
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_settings
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_speech_pitch
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_speech_rate
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_speech_unavailable
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_theme
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_theme_dark
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_theme_light
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_theme_sepia
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_toc
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_toc_rail
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_typography
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_volume_keys
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource

/**
 * The settings sheet for REFLOWABLE books: theme, font size, the per-book
 * override switch, the typography section (family / line height / margins /
 * justify / scroll mode), the behavior section with the reading-speed
 * stepper and (scrolled flow only) the auto-scroll speed slider, the
 * read-aloud section (rate/pitch steppers; an availability caption replaces
 * them where the platform has no engine), and the TOC / search entries. The
 * TOC entry stays here (v1 behavior) — the search entry rides the TOC sheet
 * to keep the top bar uncluttered. One of the reader's per-sheet files (split
 * from ReaderSheets.kt); its sliders ride the shared [CommitSliderRow].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReflowableSettingsSheet(
    prefs: ReaderPrefsSnapshot,
    speechAvailable: Boolean,
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
    // Selection states read the snapshot's own folds: theme/font size the
    // EFFECTIVE values (what the reader renders with), typography/behavior
    // the global axes.
    val theme = prefs.effective.theme
    val fontSizePx = prefs.effective.fontSizePx
    val perBook = prefs.perBookActive
    val typography = prefs.typographyState()
    val behavior = prefs.behaviorState()
    val speechRate = prefs.global.speechRate
    val speechPitch = prefs.global.speechPitch
    val autoScrollSpeedPx = prefs.global.autoScrollSpeedPxPerSec
    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        ) {
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
            SettingsSwitchRow(
                label = stringResource(Res.string.book_reader_toc_rail),
                checked = behavior.tocRailVisible,
                onChange = { onBehaviorChange(behavior.copy(tocRailVisible = it)) },
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
}

/** Line-height slider (1.0×–2.0×, the store band / 100). Commit-on-settle per
 * the page-slider convention; the value label rides along (formatted in code —
 * the resources pipeline does not handle `%1$.1f`-style conversions). */
@Composable
private fun LineHeightSlider(lineHeightPct: Int, onCommit: (Int) -> Unit) {
    CommitSliderRow(
        label = stringResource(Res.string.book_reader_line_height),
        value = lineHeightPct,
        range = READER_LINE_HEIGHT_RANGE,
        valueCaption = { stringResource(Res.string.book_reader_line_height_value, lineHeightLabel(it)) },
        onCommit = onCommit,
    )
}

/** 150 (percent of base) → "1.5" — the line-height × multiplier rounded to
 * one decimal (105 → "1.1", 199 → "2.0"), always one fractional digit.
 * Lives beside the slider that renders it (moved from ReaderSheets.kt);
 * pinned by LineHeightLabelTest. */
internal fun lineHeightLabel(pct: Int): String {
    val tenths = (pct / 10.0).roundToInt()
    return "${tenths / 10}.${tenths % 10}"
}

/** Margin slider (0..100 %); commit-on-settle, plain "N %" value label. */
@Composable
private fun MarginSlider(marginPct: Int, onCommit: (Int) -> Unit) {
    CommitSliderRow(
        label = stringResource(Res.string.book_reader_margins),
        value = marginPct,
        range = READER_MARGIN_RANGE,
        valueCaption = { "$it %" },
        onCommit = onCommit,
    )
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
 * Auto-scroll speed slider (px/s, the ReaderStore's 20..120 band);
 * commit-on-settle per the page-slider convention onto the persisted
 * preference (ReaderStore key — sessions reopen at the chosen speed).
 */
@Composable
private fun AutoScrollSpeedSlider(speedPxPerSec: Int, onCommit: (Int) -> Unit) {
    CommitSliderRow(
        label = stringResource(Res.string.book_reader_auto_scroll_speed),
        value = speedPxPerSec,
        range = READER_AUTO_SCROLL_SPEED_RANGE,
        valueCaption = { "$it px/s" },
        onCommit = onCommit,
    )
}
