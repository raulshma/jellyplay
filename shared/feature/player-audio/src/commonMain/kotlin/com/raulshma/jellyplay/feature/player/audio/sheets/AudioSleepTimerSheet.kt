package com.raulshma.jellyplay.feature.player.audio.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.MoonStars
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.components.ConfirmState
import com.raulshma.jellyplay.core.ui.components.ConfirmTone
import com.raulshma.jellyplay.core.ui.components.ConfirmDialog
import com.raulshma.jellyplay.core.ui.components.PlayerModalBottomSheet
import com.raulshma.jellyplay.core.ui.components.SheetHeader
import com.raulshma.jellyplay.core.ui.components.SheetSection
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.core.ui.components.formatDurationMs
import com.raulshma.jellyplay.core.ui.components.rememberConfirmState
import com.raulshma.jellyplay.feature.player.audio.generated.resources.Res
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_cancel
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_cancel_timer
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_sleep_timer_cancel_message
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_sleep_timer_cancel_title
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_sleep_timer_duration
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_sleep_timer_end_of_episode
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_sleep_timer_keep
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_sleep_timer_stops_at
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_sleep_timer_title
import com.raulshma.jellyplay.feature.player.audio.rememberIs24HourFormat

private val SLEEP_TIMER_PRESETS = listOf(
    15 * 60 * 1000L,
    30 * 60 * 1000L,
    45 * 60 * 1000L,
    60 * 60 * 1000L,
    90 * 60 * 1000L,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AudioSleepTimerSheet(
    isActive: Boolean,
    isEndOfEpisodeMode: Boolean,
    remainingMs: Long,
    lastUsedDurationMs: Long,
    onSelectDuration: (Long) -> Unit,
    onSelectEndOfEpisode: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    // cancelling a timer with more than five minutes left asks for
    // confirmation (a fat-finger cancel of a long timer is the expensive
    // accident); the deferred lambda mirrors the repo's ConfirmState idiom.
    val cancelConfirm = rememberConfirmState()
    PlayerModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
        ) {
            SheetHeader(
                title = stringResource(Res.string.audio_sleep_timer_title),
                icon = Tabler.Outline.MoonStars,
            )
            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                Spacer(Modifier.height(8.dp))

                if (isActive) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                ShapeCache.smoothPill,
                            )
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = if (isEndOfEpisodeMode) stringResource(Res.string.audio_sleep_timer_end_of_episode) else formatDurationMs(remainingMs),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            // the projected stop wall-clock time under the
                            // countdown (end-of-episode arms nothing to project).
                            if (!isEndOfEpisodeMode) {
                                val stopsAt = rememberStopsAtTime(remainingMs)
                                if (stopsAt != null) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = stringResource(Res.string.audio_sleep_timer_stops_at, stopsAt),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        Text(
                            stringResource(Res.string.audio_cancel),
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .focusIndicator()
                                .clickable(
                                    role = androidx.compose.ui.semantics.Role.Button,
                                    onClick = { requestCancel(cancelConfirm, remainingMs) { onCancel() } },
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }

                SheetSection {
                    Text(
                        stringResource(Res.string.audio_sleep_timer_duration),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SLEEP_TIMER_PRESETS.forEach { durationMs ->
                            // Highlight the configured duration (lastUsedDurationMs is set synchronously
                            // when the timer starts). Previously this compared against remainingMs, which
                            // decrements every second, so the highlight vanished the moment the timer began.
                            val isSelected = isActive && !isEndOfEpisodeMode && durationMs == lastUsedDurationMs
                            val isLastUsed = !isActive && durationMs == lastUsedDurationMs
                            val minutes = durationMs / (60 * 1000)
                            val label = if (minutes % 60L == 0L) "${minutes / 60}h" else "${minutes}m"
                            androidx.compose.material3.FilterChip(
                                selected = isSelected || isLastUsed,
                                onClick = { onSelectDuration(durationMs); onDismiss() },
                                label = { Text(label) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    val isEndSelected = isActive && isEndOfEpisodeMode
                    androidx.compose.material3.FilterChip(
                        selected = isEndSelected,
                        onClick = { onSelectEndOfEpisode(); onDismiss() },
                        label = { Text(stringResource(Res.string.audio_sleep_timer_end_of_episode)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    // the cancel-confirmation dialog — cancel keeps the timer running,
    // confirm tears it down (the click site's own dismiss behavior is
    // preserved: this sheet's cancel never dismissed, so neither does the
    // confirmed one).
    cancelConfirm.ConfirmDialog(
        title = stringResource(Res.string.audio_sleep_timer_cancel_title),
        message = stringResource(Res.string.audio_sleep_timer_cancel_message, formatDurationMs(remainingMs)),
        confirmText = stringResource(Res.string.audio_cancel_timer),
        dismissText = stringResource(Res.string.audio_sleep_timer_keep),
        icon = Tabler.Outline.MoonStars,
        tone = ConfirmTone.WARNING,
    )
}

/**
 * Run [onCancel] immediately when the remaining time is at/below the
 * confirm threshold (or nothing is left — the end-of-episode arm), else raise
 * the confirmation dialog behind [confirm].
 */
private fun requestCancel(
    confirm: ConfirmState,
    remainingMs: Long,
    onCancel: () -> Unit,
) {
    if (SleepTimerSheetPolicy.requiresCancelConfirmation(remainingMs)) {
        confirm.request(onCancel)
    } else {
        onCancel()
    }
}

/**
 * The projected stop wall-clock label ("23:45" / "11:45 PM"), or null when
 * there is nothing to project (idle / end-of-episode). Re-anchored on every
 * authoritative [remainingMs] emission (the same wall-clock re-sync model the
 * countdown itself rides), deduped to minute boundaries so the label holds
 * steady between them.
 */
@Composable
private fun rememberStopsAtTime(remainingMs: Long): String? {
    if (remainingMs <= 0L) return null
    val is24Hour = rememberIs24HourFormat()
    val stopEpochMs = remember(remainingMs) {
        SleepTimerSheetPolicy.projectedStopEpochMs(System.currentTimeMillis(), remainingMs)
    }
    return remember(stopEpochMs / 60_000L) {
        SleepTimerSheetPolicy.formatStopTime(stopEpochMs, is24Hour)
    }
}
