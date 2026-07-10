package com.raulshma.jellyplay.feature.calendar

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Checks
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.StatusColors
import com.raulshma.jellyplay.core.model.arr.ArrCalendarItem
import com.raulshma.jellyplay.core.model.arr.ArrMediaType
import com.raulshma.jellyplay.core.model.arr.ArrServiceKind
import com.raulshma.jellyplay.core.ui.image.MediaImage

/**
 * Horizontal calendar row: poster + info column. Uses the *arr absolute poster
 * URL as the primary image with the TMDB-enriched URL as a fallback — this
 * sidesteps the latent `toSeerrSearchItem()` bug (which prepends a TMDB host
 * onto an already-absolute *arr URL, yielding broken posters on Home).
 *
 * Posters are 2:3 (W54 × H80dp) matching the media-card aspect elsewhere.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CalendarCard(
    item: ArrCalendarItem,
    enrichedPosterUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick),
        shape = ShapeCache.smooth12,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(modifier = Modifier.padding(10.dp)) {
            // Poster — *arr URL primary, TMDB enriched fallback.
            MediaImage(
                url = item.posterPath ?: "",
                fallbackUrls = listOfNotNull(enrichedPosterUrl),
                contentDescription = item.title,
                modifier = Modifier.size(width = 54.dp, height = 80.dp),
                placeholderIcon = Tabler.Outline.Checks,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ServiceBadge(kind = if (item.mediaType == ArrMediaType.MOVIE) ArrServiceKind.RADARR else ArrServiceKind.SONARR)
                    StatusPill(hasFile = item.hasFile, monitored = item.monitored)
                }
                val overview = item.overview
                if (!overview.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = overview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ServiceBadge(kind: ArrServiceKind) {
    val (label, color) = when (kind) {
        ArrServiceKind.RADARR -> "Radarr" to StatusColors.requested
        ArrServiceKind.SONARR -> "Sonarr" to StatusColors.pending
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.15f),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun StatusPill(hasFile: Boolean, monitored: Boolean) {
    val (label, color) = when {
        hasFile -> "Downloaded" to StatusColors.available
        monitored -> "Monitored" to StatusColors.info
        else -> "Missing" to StatusColors.warning
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.15f),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
