package com.raulshma.jellyplay.feature.admin.dashboard.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Book
import com.composables.icons.tabler.outline.DeviceTv
import com.composables.icons.tabler.outline.Disc
import com.composables.icons.tabler.outline.Headphones
import com.composables.icons.tabler.outline.Movie
import com.composables.icons.tabler.outline.Video
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.ItemCounts
import com.raulshma.jellyplay.feature.admin.generated.resources.Res
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_library

@Composable
fun LibraryStatsRow(
    counts: ItemCounts,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary

    val stats = buildList {
        add(StatItem(Tabler.Outline.Movie, "Movies", counts.movieCount, primary))
        add(StatItem(Tabler.Outline.DeviceTv, "Series", counts.seriesCount, tertiary))
        add(StatItem(Tabler.Outline.Video, "Episodes", counts.episodeCount, secondary))
        add(StatItem(Tabler.Outline.Disc, "Albums", counts.albumCount, primary))
        add(StatItem(Tabler.Outline.Headphones, "Songs", counts.songCount, tertiary))
        if (counts.bookCount > 0) {
            add(StatItem(Tabler.Outline.Book, "Books", counts.bookCount, secondary))
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(Res.string.admin_library),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "${counts.totalCount} total",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(stats, key = { it.label }) { stat ->
                StatCard(stat)
            }
        }
    }
}

@Composable
private fun StatCard(stat: StatItem) {
    Box(
        modifier = Modifier
            .clip(ShapeCache.smooth16)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                stat.icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = stat.tint,
            )
            Spacer(Modifier.height(8.dp))
            AnimatedCount(target = stat.count)
            Spacer(Modifier.height(2.dp))
            Text(
                stat.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AnimatedCount(target: Long) {
    val animated by animateFloatAsState(
        targetValue = target.toFloat(),
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "statCount",
    )
    Text(
        animated.toLong().toString(),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

private data class StatItem(
    val icon: ImageVector,
    val label: String,
    val count: Long,
    val tint: Color,
)
