package com.raulshma.jellyplay.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.ui.components.JellyPlayLinearProgressIndicator
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Check
import com.composables.icons.tabler.outline.Download

@Composable
fun OfflineHomeContent(
    offlineLibrary: List<OfflineMediaItem>,
    onItemClick: () -> Unit,
    contentPadding: Dp,
    backgroundColor: Color,
    onGoOnline: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor),
        contentPadding = PaddingValues(
            top = 120.dp,
            bottom = 120.dp,
            start = contentPadding,
            end = contentPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (offlineLibrary.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Tabler.Outline.Download,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "No downloads yet",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "Download media while online to access it offline",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            item {
                Text(
                    text = "Your Downloads",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            items(
                count = offlineLibrary.size,
                key = { index -> "offline_${offlineLibrary[index].id}" },
                contentType = { "offlineItem" },
            ) { index ->
                val offlineItem = offlineLibrary[index]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ShapeCache.smooth12)
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .focusIndicator()
                        .clickable { onItemClick() }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val posterModifier = Modifier
                        .width(60.dp)
                        .aspectRatio(2f / 3f)
                        .clip(ShapeCache.smooth8)

                    if (!offlineItem.posterPath.isNullOrBlank()) {
                        MediaImage(
                            url = offlineItem.posterPath!!,
                            contentDescription = offlineItem.name,
                            blurHash = offlineItem.blurHashPrimary,
                            modifier = posterModifier,
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Box(
                            modifier = posterModifier.background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = offlineItem.name.take(2).uppercase(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp),
                    ) {
                        Text(
                            text = offlineItem.name,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!offlineItem.seriesName.isNullOrBlank()) {
                            Text(
                                text = offlineItem.seriesName!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp),
                        ) {
                            if (offlineItem.year != null) {
                                Text(
                                    text = offlineItem.year.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (offlineItem.communityRating != null && offlineItem.communityRating!! > 0) {
                                if (offlineItem.year != null) {
                                    Text(
                                        text = " · ",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                    )
                                }
                                val communityRatingText = remember(offlineItem.communityRating) {
                                    "★ ${String.format("%.1f", offlineItem.communityRating)}"
                                }
                                Text(
                                    text = communityRatingText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (!offlineItem.officialRating.isNullOrBlank()) {
                                Text(
                                    text = " · ${offlineItem.officialRating}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (offlineItem.genres.isNotEmpty()) {
                            Text(
                                text = offlineItem.genres.take(3).joinToString(", "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (offlineItem.downloadStatus == DownloadStatus.COMPLETED) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 4.dp),
                            ) {
                                Icon(
                                    Tabler.Outline.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "Downloaded",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        } else if (offlineItem.downloadStatus == DownloadStatus.DOWNLOADING) {
                            val progress = if (offlineItem.totalSizeBytes > 0) {
                                offlineItem.downloadedBytes.toFloat() / offlineItem.totalSizeBytes
                            } else 0f
                            JellyPlayLinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DownloadedSection(
    offlineLibrary: List<OfflineMediaItem>,
    onOfflineLibraryClick: () -> Unit,
    contentPad: Dp,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text = "Downloaded",
        style = MaterialTheme.typography.titleLarge.copy(
            fontWeight = FontWeight.Bold,
        ),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(start = contentPad, top = 24.dp, bottom = 8.dp),
    )

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(horizontal = contentPad, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            count = offlineLibrary.size,
            key = { index -> "offline_${offlineLibrary[index].id}" },
            contentType = { "offlineItem" },
        ) { index ->
            val offlineItem = offlineLibrary[index]
            Column(
                modifier = Modifier
                    .width(120.dp)
                    .focusIndicator()
                    .clickable { onOfflineLibraryClick() },
            ) {
                val posterModifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(ShapeCache.smooth8)

                if (!offlineItem.posterPath.isNullOrBlank()) {
                    MediaImage(
                        url = offlineItem.posterPath!!,
                        contentDescription = offlineItem.name,
                        blurHash = offlineItem.blurHashPrimary,
                        modifier = posterModifier,
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        modifier = posterModifier.background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = offlineItem.name.take(2).uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Text(
                    text = offlineItem.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
