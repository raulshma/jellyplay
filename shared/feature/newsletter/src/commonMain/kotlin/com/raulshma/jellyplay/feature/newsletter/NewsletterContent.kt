package com.raulshma.jellyplay.feature.newsletter

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.NewsletterSectionType
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer

@Composable
fun NewsletterContent(
    state: NewsletterUiState,
    viewModel: NewsletterViewModel,
    onItemClick: (MediaItem) -> Unit,
    onPlayClick: (String, String?, Long) -> Unit,
    onViewAllFreshPicks: () -> Unit = {},
    listFocusRequester: FocusRequester,
) {
    val hasAnyContent = state.recentlyAdded.isNotEmpty() ||
        state.activityDigest.isNotEmpty() ||
        state.libraryStats != null ||
        state.continueWatching.isNotEmpty() ||
        state.nextUp.isNotEmpty() ||
        state.curatedPicks.isNotEmpty()

    if (!hasAnyContent && !state.isLoading) return

    val contentPadding = PaddingValues(bottom = 24.dp)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .tvFocusRestorer()
            .focusRequester(listFocusRequester),
        contentPadding = contentPadding,
    ) {
        item {
            NewsletterHeader(
                serverName = state.serverName,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
            )
        }

        if (state.libraryStats != null) {
            item(key = "aggregated_stats") {
                val alpha by animateEntranceAlpha()
                NewsletterAggregatedStats(
                    stats = state.libraryStats,
                    recentlyAddedCount = state.recentlyAdded.size,
                    activityCount = state.activityDigest.size,
                    continueWatchingCount = state.continueWatching.size,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .graphicsLayer {
                            this.alpha = alpha
                            translationY = (1f - alpha) * 8.dp.toPx()
                        },
                )
            }
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
                NewsletterSectionType.LIBRARY_STATS -> false
                NewsletterSectionType.CONTINUE_WATCHING -> state.continueWatching.isNotEmpty()
                NewsletterSectionType.NEXT_UP -> state.nextUp.isNotEmpty()
                NewsletterSectionType.CURATED_PICKS -> state.curatedPicks.isNotEmpty()
            }
            if (!hasContent) return@items

            val alpha by animateEntranceAlpha()
            val sectionModifier = Modifier.graphicsLayer {
                this.alpha = alpha
                val scale = 0.97f + (0.03f * alpha)
                scaleX = scale
                scaleY = scale
                translationY = (1f - alpha) * 8.dp.toPx()
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
                        modifier = sectionModifier.padding(horizontal = 16.dp),
                    )
                }
                NewsletterSectionType.LIBRARY_STATS -> {}
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
                        onViewAllClick = onViewAllFreshPicks,
                        modifier = sectionModifier,
                    )
                }
            }
        }
    }
}

@Composable
private fun animateEntranceAlpha(): State<Float> {
    var hasBeenVisible by remember { mutableStateOf(false) }
    hasBeenVisible = true
    return animateFloatAsState(
        targetValue = if (hasBeenVisible) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "entrance",
    )
}
