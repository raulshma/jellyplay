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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.*
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineLibraryScreen(
    onItemClick: (String) -> Unit,
    onPlayOffline: (itemId: String) -> Unit,
    onBack: () -> Unit,
    viewModel: OfflineLibraryViewModel = hiltViewModel(),
) {
    val items by viewModel.offlineLibrary.collectAsStateWithLifecycle(initialValue = emptyList())
    val isLoading = viewModel.isLoading
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val adaptiveInfo = LocalAdaptiveInfo.current
    val contentPad = adaptiveInfo.contentPadding(isTv = false)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection)) {
            MediumTopAppBar(
                title = {
                    Text(
                        text = "Downloaded",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Tabler.Outline.ArrowLeft, contentDescription = "Back")
                    }
                },
                scrollBehavior = scrollBehavior,
            )

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else if (items.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No downloaded content yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    contentPadding = PaddingValues(
                        start = contentPad,
                        end = contentPad,
                        top = 8.dp,
                        bottom = 100.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(items, key = { it.id }) { item ->
                        OfflineMediaCard(
                            item = item,
                            onClick = {
                                if (item.mediaType == MediaType.SERIES) {
                                    onItemClick(item.id)
                                } else {
                                    onPlayOffline(item.id)
                                }
                            },
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
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
    ) {
        val imageModifier = Modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(8.dp))

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

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 2.dp),
        ) {
            if (item.year != null) {
                Text(
                    text = item.year.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            if (item.communityRating != null && item.communityRating!! > 0) {
                if (item.year != null) {
                    Text(
                        text = " · ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
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
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            if (item.officialRating != null) {
                Text(
                    text = " · ${item.officialRating}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                )
            }
        }

        if (item.mediaType == MediaType.SERIES && item.childCount > 0) {
            Text(
                text = "${item.childCount} episodes",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
        }

        if (item.genres.isNotEmpty()) {
            Text(
                text = item.genres.take(3).joinToString(", "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
