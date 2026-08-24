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
import com.raulshma.jellyplay.feature.newsletter.generated.resources.Res
import com.raulshma.jellyplay.feature.newsletter.generated.resources.newsletter_stat_activity
import com.raulshma.jellyplay.feature.newsletter.generated.resources.newsletter_stat_albums
import com.raulshma.jellyplay.feature.newsletter.generated.resources.newsletter_stat_episodes
import com.raulshma.jellyplay.feature.newsletter.generated.resources.newsletter_stat_in_progress
import com.raulshma.jellyplay.feature.newsletter.generated.resources.newsletter_stat_movies
import com.raulshma.jellyplay.feature.newsletter.generated.resources.newsletter_stat_new_this_week
import com.raulshma.jellyplay.feature.newsletter.generated.resources.newsletter_stat_series
import com.raulshma.jellyplay.feature.newsletter.generated.resources.newsletter_stat_songs
import com.raulshma.jellyplay.feature.newsletter.generated.resources.newsletter_stat_total_items
import org.jetbrains.compose.resources.stringResource

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
                label = stringResource(Res.string.newsletter_stat_total_items),
                count = stats.totalCount,
                modifier = Modifier.weight(1f),
                highlight = true,
            )
            StatCard(
                label = stringResource(Res.string.newsletter_stat_movies),
                count = stats.movieCount,
                modifier = Modifier.weight(1f),
            )
            StatCard(
                label = stringResource(Res.string.newsletter_stat_series),
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
                label = stringResource(Res.string.newsletter_stat_episodes),
                count = stats.episodeCount,
                modifier = Modifier.weight(1f),
            )
            StatCard(
                label = stringResource(Res.string.newsletter_stat_albums),
                count = stats.albumCount,
                modifier = Modifier.weight(1f),
            )
            StatCard(
                label = stringResource(Res.string.newsletter_stat_songs),
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
                label = stringResource(Res.string.newsletter_stat_new_this_week),
                count = recentlyAddedCount.toLong(),
                modifier = Modifier.weight(1f),
                highlight = recentlyAddedCount > 0,
            )
            StatCard(
                label = stringResource(Res.string.newsletter_stat_in_progress),
                count = continueWatchingCount.toLong(),
                modifier = Modifier.weight(1f),
                highlight = continueWatchingCount > 0,
            )
            StatCard(
                label = stringResource(Res.string.newsletter_stat_activity),
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
