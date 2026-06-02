package com.raulshma.jellyplay.feature.newsletter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.ItemCounts

@Composable
fun NewsletterAggregatedStats(
    stats: ItemCounts,
    recentlyAddedCount: Int,
    activityCount: Int,
    continueWatchingCount: Int,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatCard(
                label = "Total Items",
                count = stats.totalCount,
                modifier = Modifier.weight(1f),
                highlight = true,
            )
            StatCard(
                label = "Movies",
                count = stats.movieCount,
                modifier = Modifier.weight(1f),
            )
            StatCard(
                label = "Series",
                count = stats.seriesCount,
                modifier = Modifier.weight(1f),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatCard(
                label = "Episodes",
                count = stats.episodeCount,
                modifier = Modifier.weight(1f),
            )
            StatCard(
                label = "Albums",
                count = stats.albumCount,
                modifier = Modifier.weight(1f),
            )
            StatCard(
                label = "Songs",
                count = stats.songCount,
                modifier = Modifier.weight(1f),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatCard(
                label = "New This Week",
                count = recentlyAddedCount.toLong(),
                modifier = Modifier.weight(1f),
                highlight = recentlyAddedCount > 0,
            )
            StatCard(
                label = "In Progress",
                count = continueWatchingCount.toLong(),
                modifier = Modifier.weight(1f),
                highlight = continueWatchingCount > 0,
            )
            StatCard(
                label = "Activity",
                count = activityCount.toLong(),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    count: Long,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
) {
    val containerColor = if (highlight) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val countColor = if (highlight) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = modifier
            .clip(ShapeCache.smooth16)
            .background(containerColor)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = formatCount(count),
                style = if (highlight) {
                    MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                } else {
                    MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                },
                color = countColor,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatCount(count: Long): String = when {
    count >= 1_000_000 -> "${count / 1_000_000}.${(count % 1_000_000) / 100_000}M"
    count >= 1_000 -> "${count / 1_000}.${(count % 1_000) / 100}K"
    else -> count.toString()
}
