package com.raulshma.jellyplay.feature.book

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.feature.book.generated.resources.Res
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_dialog_cancel
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_sleep_timer
import com.raulshma.jellyplay.feature.book.generated.resources.book_reader_sleep_timer_end_of_chapter
import org.jetbrains.compose.resources.stringResource

/**
 * The sleep-timer sheet (reflowable reader): minute presets as FilterChips
 * plus the end-of-chapter arm, a running countdown row with cancel — the
 * audio player's sleep sheet UX mirrored at reader scale (option labels
 * match its language; books get 5/15/30/60 rather than the player's
 * 15..90 because reading sessions run shorter). One of the reader's
 * per-sheet files (split from ReaderSheets.kt).
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
                    selected = state.isPresetSelected(minutes),
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

/**
 * Whether the [minutes]-minute preset chip renders selected: only while a
 * timer RUNS and its armed option is exactly that preset — an EndOfChapter
 * arm selects no timed chip. The sheet's selection fold, pure so it is
 * pinnable without Compose (ReaderSleepTimerTest).
 */
internal fun ReaderSleepTimerState.isPresetSelected(minutes: Int): Boolean =
    running && option == ReaderSleepOption.Timed(minutes)

/** Countdown label `m:ss` / `h:mm:ss`; ceil-rounded so 0:00 only shows at fire. */
internal fun formatSleepCountdown(millis: Long): String {
    val totalSeconds = ((millis.coerceAtLeast(0L)) + 999L) / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    val two = { value: Long -> if (value < 10L) "0$value" else "$value" }
    return if (hours > 0L) "$hours:${two(minutes)}:${two(seconds)}" else "$minutes:${two(seconds)}"
}

/** Sleep-timer minute presets (reader-scaled; the audio player's run 15..90). */
internal val SLEEP_TIMER_PRESET_MINUTES = listOf(5, 15, 30, 60)
