package com.raulshma.jellyplay.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.seerr.DiscoverSectionType
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.ui.components.SeerrMediaCard
import com.raulshma.jellyplay.core.ui.tv.isTvDevice
import com.raulshma.jellyplay.core.network.seerr.buildPosterUrl

@Composable
fun DiscoverContent(
    discoverSections: Map<DiscoverSectionType, List<SeerrSearchItem>>,
    onNavigateToDetail: (tmdbId: Int, mediaType: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sectionOrder = remember {
        listOf(
            DiscoverSectionType.TRENDING,
            DiscoverSectionType.POPULAR_MOVIES,
            DiscoverSectionType.POPULAR_TV,
            DiscoverSectionType.UPCOMING_MOVIES,
            DiscoverSectionType.UPCOMING_TV,
        )
    }

    AnimatedVisibility(
        visible = discoverSections.isNotEmpty(),
        enter = fadeIn(tween(350, easing = FastOutSlowInEasing)) +
                slideInVertically(
                    initialOffsetY = { it / 20 },
                    animationSpec = tween(350, easing = FastOutSlowInEasing),
                ),
        exit = fadeOut(tween(100)),
    ) {
        androidx.compose.foundation.layout.Column(modifier = modifier) {
            // Section header
            Text(
                text = "Discover",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
            )

            for (sectionType in sectionOrder) {
                val items = discoverSections[sectionType]
                if (items != null && items.isNotEmpty()) {
                    DiscoverSubSection(
                        sectionType = sectionType,
                        items = items,
                        onItemClick = onNavigateToDetail,
                    )
                }
            }
        }
    }
}

@Composable
private fun DiscoverSubSection(
    sectionType: DiscoverSectionType,
    items: List<SeerrSearchItem>,
    onItemClick: (tmdbId: Int, mediaType: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = when (sectionType) {
        DiscoverSectionType.TRENDING -> "Trending"
        DiscoverSectionType.POPULAR_MOVIES -> "Popular Movies"
        DiscoverSectionType.POPULAR_TV -> "Popular Series"
        DiscoverSectionType.UPCOMING_MOVIES -> "Upcoming Movies"
        DiscoverSectionType.UPCOMING_TV -> "Upcoming Series"
    }

    val isTv = isTvDevice()

    androidx.compose.foundation.layout.Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
        )

        // Use a mosaic-style grid: fixed 3 columns on phone, more on TV/tablet
        val gridCols = if (isTv) 6 else 3

        LazyVerticalGrid(
            columns = GridCells.Fixed(gridCols),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(
                    calculateGridHeight(
                        itemCount = items.size,
                        columns = gridCols,
                    )
                ),
            userScrollEnabled = false,
        ) {
            items(
                items = items,
                key = { item -> "${sectionType.name}_${item.id}" },
            ) { item ->
                val imageUrl = buildPosterUrl(item.posterPath)

                SeerrMediaCard(
                    item = item,
                    imageUrl = imageUrl,
                    onClick = {
                        val mediaType = when {
                            item.mediaType.equals("movie", ignoreCase = true) -> "movie"
                            item.mediaType.equals("tv", ignoreCase = true) -> "tv"
                            else -> item.mediaType
                        }
                        onItemClick(item.id, mediaType)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f),
                )
            }
        }
    }
}

/**
 * Calculate the height needed for the grid based on item count and columns.
 * Each row is approximately 200.dp (poster aspect ratio 2:3 with grid width).
 */
private fun calculateGridHeight(itemCount: Int, columns: Int): androidx.compose.ui.unit.Dp {
    val rows = (itemCount + columns - 1) / columns
    // Each row height: card height with aspect ratio 2:3 based on column width
    // Approximate each row at ~200dp (including padding)
    return (rows * 200).dp
}
