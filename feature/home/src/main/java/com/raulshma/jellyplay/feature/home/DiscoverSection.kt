package com.raulshma.jellyplay.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.seerr.DiscoverSectionType
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.WindowSizeClass
import com.raulshma.jellyplay.core.ui.components.SeerrMediaCard
import com.raulshma.jellyplay.core.ui.tv.isTvDevice
import com.raulshma.jellyplay.core.network.seerr.buildPosterUrl

/**
 * Returns the card size fraction based on the item's vote average rating.
 * Subtle differences to provide mosaic feel without massive cards.
 */
private fun ratingToSizeFraction(voteAverage: Float?): Float {
    if (voteAverage == null) return 1.0f
    return when {
        voteAverage >= 9.2f -> 1.2f
        voteAverage >= 8.5f -> 1.1f
        voteAverage >= 7.5f -> 1.0f
        else -> 0.9f
    }
}

@Composable
fun DiscoverContent(
    discoverSections: Map<DiscoverSectionType, List<SeerrSearchItem>>,
    onNavigateToDetail: (tmdbId: Int, mediaType: String) -> Unit,
    modifier: Modifier = Modifier,
    onSeerrRequest: (SeerrSearchItem) -> Unit = {},
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
        Column(modifier = modifier) {
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
                        onRequestClick = onSeerrRequest,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiscoverSubSection(
    sectionType: DiscoverSectionType,
    items: List<SeerrSearchItem>,
    onItemClick: (tmdbId: Int, mediaType: String) -> Unit,
    onRequestClick: (SeerrSearchItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = when (sectionType) {
        DiscoverSectionType.TRENDING -> "Trending"
        DiscoverSectionType.POPULAR_MOVIES -> "Popular\nMovies"
        DiscoverSectionType.POPULAR_TV -> "Popular\nSeries"
        DiscoverSectionType.UPCOMING_MOVIES -> "Upcoming\nMovies"
        DiscoverSectionType.UPCOMING_TV -> "Upcoming\nSeries"
    }

    val isTv = isTvDevice()
    val adaptiveInfo = LocalAdaptiveInfo.current
    
    // Increased column counts to keep cards compact on all screens
    val gridCols = when {
        isTv -> 10
        adaptiveInfo.windowSizeClass == WindowSizeClass.Expanded -> 8
        adaptiveInfo.windowSizeClass == WindowSizeClass.Medium -> 6
        else -> 4
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Vertical subsection title on the left
        Box(
            modifier = Modifier
                .width(36.dp)
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = androidx.compose.ui.unit.TextUnit(
                        1.2f,
                        androidx.compose.ui.unit.TextUnitType.Sp
                    ),
                ),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .rotate(-90f),
            )
        }

        // Mosaic layout using FlowRow
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp, end = 16.dp)
        ) {
            val spacing = 8.dp
            val availableWidth = maxWidth
            val baseColWidth = (availableWidth - (spacing * (gridCols - 1))) / gridCols

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(spacing),
                verticalArrangement = Arrangement.spacedBy(spacing),
                modifier = Modifier.fillMaxWidth()
            ) {
                items.forEach { item ->
                    val imageUrl = buildPosterUrl(item.posterPath)
                    val fraction = ratingToSizeFraction(item.voteAverage)
                    
                    val itemWidth = baseColWidth * fraction
                    
                    val loadingState = com.raulshma.jellyplay.core.ui.components.LocalSeerrCardLoadingState.current
                    val prefetch = com.raulshma.jellyplay.core.ui.components.LocalSeerrPrefetch.current

                    SeerrMediaCard(
                        item = item,
                        imageUrl = imageUrl,
                        isLoading = loadingState?.isLoading(item.id) == true,
                        onClick = {
                            val mediaType = when {
                                item.mediaType.equals("movie", ignoreCase = true) -> "movie"
                                item.mediaType.equals("tv", ignoreCase = true) -> "tv"
                                else -> item.mediaType
                            }
                            if (loadingState != null && prefetch != null) {
                                loadingState.startLoading(item.id)
                                prefetch(item.id, mediaType) {
                                    loadingState.stopLoading(item.id)
                                    onItemClick(item.id, mediaType)
                                }
                            } else {
                                onItemClick(item.id, mediaType)
                            }
                        },
                        onRequestClick = { onRequestClick(item) },
                        modifier = Modifier.width(itemWidth),
                    )
                }
            }
        }
    }
}
