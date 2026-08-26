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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.MoonStars
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.components.PlayerModalBottomSheet
import com.raulshma.jellyplay.core.ui.components.SheetHeader
import com.raulshma.jellyplay.core.ui.components.SheetSection
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.core.ui.components.formatDurationMs
import com.raulshma.jellyplay.feature.player.audio.generated.resources.Res
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_cancel
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_sleep_timer_duration
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_sleep_timer_end_of_episode
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_sleep_timer_title

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
                        Text(
                            text = if (isEndOfEpisodeMode) stringResource(Res.string.audio_sleep_timer_end_of_episode) else formatDurationMs(remainingMs),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            stringResource(Res.string.audio_cancel),
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .focusIndicator()
                                .clickable(
                                    role = androidx.compose.ui.semantics.Role.Button,
                                    onClick = onCancel,
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
}
