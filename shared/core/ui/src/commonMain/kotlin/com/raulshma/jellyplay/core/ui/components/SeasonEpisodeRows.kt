package com.raulshma.jellyplay.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.MediaItem

/**
 * Episode title + runtime labels shared by the season-scoped episode lists in
 * [DeleteDownloadedEpisodesSheet] and [SeriesDownloadSheet]: both render an
 * `E# Name` body line with an `Nm` runtime under it. The caller passes the
 * title color so each sheet keeps its own selected/downloaded tinting while
 * the text shape itself can't drift between the two.
 */
@Composable
fun RowScope.SeasonEpisodeMetaLabels(episode: MediaItem, nameColor: Color) {
    Column(modifier = Modifier.weight(1f)) {
        Text(
            text = buildString {
                episode.episodeNumber?.let { append("E$it. ") }
                append(episode.name)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = nameColor,
        )
        episode.runTimeTicks?.let { ticks ->
            val minutes = ticks / 600_000_000
            Text(
                text = "${minutes}m",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Per-season closing divider shared by the season lists in
 * [DeleteDownloadedEpisodesSheet] and [SeriesDownloadSheet], keyed and
 * content-typed identically so both lists scroll-preserve the same way.
 */
fun LazyListScope.seasonDividerItem(seasonId: String) {
    item(key = "season-$seasonId-divider", contentType = "divider") {
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
        )
    }
}
