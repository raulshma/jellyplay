package com.raulshma.jellyplay.feature.newsletter

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.HorizontalUncontainedCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.designsystem.theme.RatingColors
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.core.ui.components.formatDurationFromTicks
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.feature.newsletter.generated.resources.Res
import com.raulshma.jellyplay.feature.newsletter.generated.resources.newsletter_recently_added
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsletterRecentlyAdded(
    items: List<MediaItem>,
    imageUrlBuilder: (String) -> String,
    onItemClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(top = 12.dp)) {
        Text(
            text = stringResource(Res.string.newsletter_recently_added),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
        )

        HorizontalUncontainedCarousel(
            state = rememberCarouselState { items.size },
            itemWidth = 160.dp,
            itemSpacing = 12.dp,
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) { index ->
            val item = items[index]
            NewsletterMediaCard(
                item = item,
                imageUrl = imageUrlBuilder(item.id),
                onClick = { onItemClick(item) },
                modifier = Modifier.width(160.dp),
            )
        }
    }
}

@Composable
fun NewsletterMediaCard(
    item: MediaItem,
    imageUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(ShapeCache.smooth12)
                .focusIndicator(ShapeCache.smooth16)
                .clickable(onClick = onClick),
        ) {
            MediaImage(
                url = imageUrl,
                contentDescription = item.name,
                blurHash = item.blurHashes.primary,
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.Crop,
            )

            if (item.communityRating != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            ShapeCache.smooth4,
                        )
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = "\u2605 ${"%.1f".format(item.communityRating)}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = RatingColors.star,
                    )
                }
            }

            item.mediaType.takeIf { it != MediaType.UNKNOWN }?.let { type ->
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                            ShapeCache.smooth4,
                        )
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = type.label,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }

            if (item.isPlayed) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            ShapeCache.smooth4,
                        )
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = "\u2713",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        Column(
            modifier = Modifier.padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (item.mediaType == MediaType.EPISODE && item.seriesName != null) {
                Text(
                    text = buildString {
                        append(item.seriesName)
                        item.seasonNumber?.let { s -> append(" S${s}") }
                        item.episodeNumber?.let { e -> append("E${e}") }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item.year?.let {
                    Text(
                        text = it.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                val runTimeTicks = item.runTimeTicks
                if (runTimeTicks != null && runTimeTicks > 0 && item.mediaType != MediaType.SERIES) {
                    item.year?.let {
                        Text(
                            text = "\u00B7",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = remember(runTimeTicks) {
                            formatDurationFromTicks(runTimeTicks)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                item.officialRating?.let { rating ->
                    Text(
                        text = "\u00B7",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Box(
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                ShapeCache.smooth4,
                            )
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    ) {
                        Text(
                            text = rating,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                if (item.mediaType == MediaType.SERIES && item.childCount != null) {
                    Text(
                        text = "${item.childCount} ep",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (item.genres.isNotEmpty()) {
                Text(
                    text = item.genres.take(3).joinToString(", "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

internal val MediaType.label: String
    get() = when (this) {
        MediaType.MOVIE -> "Movie"
        MediaType.SERIES -> "Series"
        MediaType.EPISODE -> "Episode"
        MediaType.MUSIC -> "Music"
        MediaType.AUDIO -> "Audio"
        MediaType.ALBUM -> "Album"
        MediaType.ARTIST -> "Artist"
        MediaType.MUSIC_VIDEO -> "Music Video"
        MediaType.COLLECTION -> "Collection"
        MediaType.PHOTO, MediaType.PHOTO_FOLDER -> "Photo"
        MediaType.LIVE_TV -> "Live TV"
        MediaType.CHANNEL -> "Channel"
        MediaType.SEASON -> "Season"
        MediaType.UNKNOWN -> ""
    }
