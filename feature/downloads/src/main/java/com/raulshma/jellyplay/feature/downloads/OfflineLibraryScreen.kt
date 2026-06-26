package com.raulshma.jellyplay.feature.downloads

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.ScreenEmptyState
import com.raulshma.jellyplay.core.ui.components.ScreenLoadingState
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.ui.tv.TvFocusableGrid
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

@Composable
fun OfflineLibraryScreen(
    onItemClick: (String) -> Unit,
    onPlayOffline: (itemId: String, mediaType: MediaType) -> Unit,
    onBack: () -> Unit,
    viewModel: OfflineLibraryViewModel = hiltViewModel(),
) {
    val items by viewModel.offlineLibrary.collectAsStateWithLifecycle(initialValue = emptyList())
    val isLoading = viewModel.isLoading
    val adaptiveInfo = LocalAdaptiveInfo.current
    val contentPad = adaptiveInfo.contentPadding(isTv = false)

    var selectedTab by remember { mutableIntStateOf(0) }
    val filteredItems = remember(items, selectedTab) {
        when (selectedTab) {
            1 -> items.filter { it.mediaType == MediaType.SERIES || it.mediaType == MediaType.MOVIE || it.mediaType == MediaType.SEASON || it.mediaType == MediaType.EPISODE }
            2 -> items.filter { it.mediaType == MediaType.AUDIO || it.mediaType == MediaType.MUSIC || it.mediaType == MediaType.ALBUM || it.mediaType == MediaType.ARTIST }
            else -> items
        }
    }

    JellyPlayScreenScaffold(
        title = "Downloaded",
        onBack = onBack,
    ) {
        if (isLoading) {
            ScreenLoadingState()
        } else if (items.isEmpty()) {
            ScreenEmptyState(
                icon = Tabler.Outline.Download,
                title = "No downloaded content yet",
            )
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                TabRow(
                    selectedTabIndex = selectedTab,
                    modifier = Modifier.padding(horizontal = contentPad),
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("All") },
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Videos") },
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Music") },
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (filteredItems.isEmpty()) {
                    Box(modifier = Modifier.weight(1f)) {
                        ScreenEmptyState(
                            icon = Tabler.Outline.Download,
                            title = "No downloaded items in this category",
                        )
                    }
                } else {
                    TvFocusableGrid(
                        items = filteredItems,
                        key = { it.id },
                        columns = GridCells.Adaptive(minSize = 150.dp),
                        contentPadding = PaddingValues(
                            start = contentPad,
                            end = contentPad,
                            top = 8.dp,
                            bottom = adaptiveInfo.bottomPadding(isTv = false),
                        ),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentType = { "offlineItem" },
                        modifier = Modifier.weight(1f),
                    ) { _, item, itemModifier ->
                        OfflineMediaCard(
                            item = item,
                            onClick = {
                                if (item.mediaType == MediaType.SERIES) {
                                    onItemClick(item.id)
                                } else {
                                    onPlayOffline(item.id, item.mediaType)
                                }
                            },
                            modifier = itemModifier,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OfflineMediaCard(
    item: OfflineMediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(ShapeCache.smooth8)
            .focusIndicator()
            .clickable(onClick = onClick),
    ) {
        val imageModifier = Modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f)
            .clip(ShapeCache.smooth8)

        if (!item.posterPath.isNullOrBlank()) {
            MediaImage(
                url = item.posterPath!!,
                contentDescription = item.name,
                blurHash = item.blurHashPrimary,
                modifier = imageModifier,
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = imageModifier.background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = item.name.take(2).uppercase(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Text(
            text = item.name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )

        val isAudio = item.mediaType == MediaType.AUDIO || item.mediaType == MediaType.MUSIC
        if (isAudio) {
            item.seriesName?.let { artist ->
                Text(
                    text = artist,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            item.seasonName?.let { album ->
                Text(
                    text = album,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp),
            ) {
                if (item.year != null) {
                    Text(
                        text = item.year.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (item.communityRating != null && item.communityRating!! > 0) {
                if (item.year != null) {
                        Text(
                            text = " · ",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                    Icon(
                        Tabler.Outline.Star,
                        contentDescription = null,
                        modifier = Modifier.size(10.dp),
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = String.format("%.1f", item.communityRating),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (item.officialRating != null) {
                    Text(
                        text = " · ${item.officialRating}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (item.mediaType == MediaType.SERIES && item.childCount > 0) {
                Text(
                    text = "${item.childCount} episodes",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (item.genres.isNotEmpty()) {
                Text(
                    text = item.genres.take(3).joinToString(", "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
