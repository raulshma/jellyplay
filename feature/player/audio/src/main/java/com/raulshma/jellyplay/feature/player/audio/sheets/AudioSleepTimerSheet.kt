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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.components.formatDurationMs

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
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                "Sleep Timer",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.height(20.dp))

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
                        text = if (isEndOfEpisodeMode) "End of episode" else formatDurationMs(remainingMs),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "Cancel",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable { onCancel() },
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            Text(
                "Duration",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SLEEP_TIMER_PRESETS.forEach { durationMs ->
                    val isSelected = !isEndOfEpisodeMode && isActive && remainingMs == durationMs
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
                label = { Text("End of episode") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
