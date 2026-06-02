package com.raulshma.jellyplay.feature.newsletter

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.NewsletterSectionType

@Composable
fun NewsletterContent(
    state: NewsletterUiState,
    viewModel: NewsletterViewModel,
    onItemClick: (String) -> Unit,
    onPlayClick: (String, String?, Long) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item {
            NewsletterHeader(
                serverName = state.serverName,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }

        items(
            count = state.sectionOrder.size,
            key = { index -> state.sectionOrder[index].name },
            contentType = { "newsletterSection" },
        ) { index ->
            val sectionType = state.sectionOrder[index]

            val hasContent = when (sectionType) {
                NewsletterSectionType.RECENTLY_ADDED -> state.recentlyAdded.isNotEmpty()
                NewsletterSectionType.ACTIVITY_DIGEST -> state.activityDigest.isNotEmpty()
                NewsletterSectionType.LIBRARY_STATS -> state.libraryStats != null
                NewsletterSectionType.CONTINUE_WATCHING -> state.continueWatching.isNotEmpty()
                NewsletterSectionType.NEXT_UP -> state.nextUp.isNotEmpty()
                NewsletterSectionType.CURATED_PICKS -> state.curatedPicks.isNotEmpty()
            }
            if (!hasContent) return@items

            var hasBeenVisible by remember { mutableStateOf(false) }
            val sectionAlpha by animateFloatAsState(
                targetValue = if (hasBeenVisible) 1f else 0f,
                animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                label = "sectionAlpha_$index",
            )
            hasBeenVisible = true

            val sectionModifier = Modifier.graphicsLayer {
                alpha = sectionAlpha
                val scale = 0.97f + (0.03f * sectionAlpha)
                scaleX = scale
                scaleY = scale
                translationY = (1f - sectionAlpha) * 12.dp.toPx()
            }

            when (sectionType) {
                NewsletterSectionType.RECENTLY_ADDED -> {
                    NewsletterRecentlyAdded(
                        items = state.recentlyAdded,
                        imageUrlBuilder = { viewModel.getImageUrl(it) },
                        onItemClick = onItemClick,
                        modifier = sectionModifier,
                    )
                }
                NewsletterSectionType.ACTIVITY_DIGEST -> {
                    NewsletterActivityDigest(
                        entries = state.activityDigest,
                        modifier = sectionModifier,
                    )
                }
                NewsletterSectionType.LIBRARY_STATS -> {
                    state.libraryStats?.let { stats ->
                        NewsletterLibraryStats(
                            stats = stats,
                            modifier = sectionModifier,
                        )
                    }
                }
                NewsletterSectionType.CONTINUE_WATCHING -> {
                    NewsletterContinueWatching(
                        items = state.continueWatching,
                        imageUrlBuilder = { viewModel.getImageUrl(it) },
                        onItemClick = onItemClick,
                        onPlayClick = { item ->
                            onPlayClick(item.id, null, item.playbackPositionTicks ?: 0L)
                        },
                        modifier = sectionModifier,
                    )
                }
                NewsletterSectionType.NEXT_UP -> {
                    NewsletterNextUp(
                        items = state.nextUp,
                        imageUrlBuilder = { viewModel.getImageUrl(it) },
                        onItemClick = onItemClick,
                        modifier = sectionModifier,
                    )
                }
                NewsletterSectionType.CURATED_PICKS -> {
                    NewsletterCuratedPicks(
                        items = state.curatedPicks,
                        imageUrlBuilder = { viewModel.getImageUrl(it) },
                        backdropUrlBuilder = { viewModel.getBackdropUrl(it) },
                        onItemClick = onItemClick,
                        modifier = sectionModifier,
                    )
                }
            }
        }

        item { Spacer(Modifier.height(32.dp)) }
    }
}
