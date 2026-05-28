package com.raulshma.jellyplay.feature.details

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.MediaItem

@Composable
fun SeriesDownloadSheet(
    seasons: List<MediaItem>,
    episodeCounts: Map<String, Int>,
    isDownloading: Boolean,
    onDownload: (selectedSeasonIds: List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedSeasonIds by remember(seasons) {
        mutableStateOf(seasons.map { it.id }.toSet())
    }

    val allSelected = selectedSeasonIds.size == seasons.size
    val noneSelected = selectedSeasonIds.isEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Text(
            text = "Download Series",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = "Select seasons to download. Each episode will be downloaded individually.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(
                onClick = {
                    selectedSeasonIds = if (allSelected) emptySet() else seasons.map { it.id }.toSet()
                },
            ) {
                Text(if (allSelected) "Deselect All" else "Select All")
            }
        }

        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(seasons, key = { it.id }) { season ->
                val isSelected = season.id in selectedSeasonIds
                val episodeCount = episodeCounts[season.id] ?: season.childCount ?: 0

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                        .clickable {
                            selectedSeasonIds = if (isSelected) {
                                selectedSeasonIds - season.id
                            } else {
                                selectedSeasonIds + season.id
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { checked ->
                            selectedSeasonIds = if (checked) {
                                selectedSeasonIds + season.id
                            } else {
                                selectedSeasonIds - season.id
                            }
                        },
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = season.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium,
                        )
                        if (episodeCount > 0) {
                            Text(
                                text = "$episodeCount episodes",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
        ) {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
            Button(
                onClick = { onDownload(selectedSeasonIds.toList()) },
                enabled = !noneSelected && !isDownloading,
            ) {
                if (isDownloading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.size(8.dp))
                }
                Text(
                    if (isDownloading) "Queuing..." else "Download${if (selectedSeasonIds.size < seasons.size) " (${selectedSeasonIds.size} seasons)" else ""}"
                )
            }
        }
    }
}
