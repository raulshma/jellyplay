package com.raulshma.jellyplay.feature.newsletter

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

    // Sections that already played their entrance, hoisted above the
    // LazyColumn so the record outlives item disposal — a section scrolled
    // out and back composes settled instead of re-running its fade.
    val entranceSeenSections = remember { mutableSetOf<String>() }

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
                val alpha by animateEntranceAlpha(key = "aggregated_stats", seenSections = entranceSeenSections)
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

            val alpha by animateEntranceAlpha(key = sectionType.name, seenSections = entranceSeenSections)
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
                        imageUrlBuilder = rememberSectionPosterBuilder(state.recentlyAdded, viewModel),
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
                        imageUrlBuilder = rememberSectionPosterBuilder(state.continueWatching, viewModel),
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
                        imageUrlBuilder = rememberSectionPosterBuilder(state.nextUp, viewModel),
                        onItemClick = onItemClick,
                        modifier = sectionModifier,
                    )
                }
                NewsletterSectionType.CURATED_PICKS -> {
                    NewsletterCuratedPicks(
                        items = state.curatedPicks,
                        imageUrlBuilder = rememberSectionPosterBuilder(state.curatedPicks, viewModel),
                        backdropUrlBuilder = rememberSectionBackdropBuilder(state.curatedPicks, viewModel),
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
private fun animateEntranceAlpha(
    key: String,
    seenSections: MutableSet<String>,
): State<Float> {
    // The visible flip lives in the effect coroutine — never a snapshot write
    // during composition — and the hoisted [seenSections] record means a
    // section re-entering view after disposal starts settled (alreadyVisible)
    // instead of replaying the entrance.
    val alreadyVisible = key in seenSections
    var hasBeenVisible by remember { mutableStateOf(alreadyVisible) }
    if (!alreadyVisible) {
        LaunchedEffect(Unit) {
            hasBeenVisible = true
            seenSections += key
        }
    }
    return animateFloatAsState(
        targetValue = if (hasBeenVisible) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "entrance",
    )
}

/**
 * The `(itemId) -> url` shape the section rows take, resolved through the
 * [items] the section itself renders so each card's URL carries that item's
 * image tag (the etag cache-buster — the row holds the [MediaItem], only its
 * id crosses this legacy seam). The id→item index is remembered off the list,
 * so a section re-render looks the URL up in O(1) instead of re-indexing.
 */
@Composable
private fun rememberSectionPosterBuilder(
    items: List<MediaItem>,
    viewModel: NewsletterViewModel,
): (String) -> String = rememberSectionUrlBuilder(items, viewModel) { viewModel.posterUrl(it) }

/** The backdrop-slot twin of [rememberSectionPosterBuilder] (the Fresh Picks featured card). */
@Composable
private fun rememberSectionBackdropBuilder(
    items: List<MediaItem>,
    viewModel: NewsletterViewModel,
): (String) -> String = rememberSectionUrlBuilder(items, viewModel) { viewModel.backdropUrl(it) }

@Composable
private fun rememberSectionUrlBuilder(
    items: List<MediaItem>,
    viewModel: NewsletterViewModel,
    urlFor: (MediaItem) -> String,
): (String) -> String {
    val byId = remember(items) { items.associateBy { it.id } }
    return remember(byId, viewModel, urlFor) {
        { id -> byId[id]?.let(urlFor) ?: viewModel.getImageUrl(id) }
    }
}
