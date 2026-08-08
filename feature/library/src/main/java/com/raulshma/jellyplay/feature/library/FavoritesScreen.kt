package com.raulshma.jellyplay.feature.library

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.LocalMediaQuickActionController
import com.raulshma.jellyplay.core.ui.components.MediaQuickActionHost
import com.raulshma.jellyplay.core.ui.components.PosterCard
import com.raulshma.jellyplay.core.ui.components.QuickAction
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.core.ui.components.rememberMediaQuickActionController
import com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColor
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.TvFocusableGrid
import com.raulshma.jellyplay.core.ui.tv.input.onDpadKey
import com.raulshma.jellyplay.feature.library.R

@Composable
fun FavoritesScreen(
    onItemClick: (itemId: String, mediaType: MediaType, parentId: String?, itemName: String) -> Unit,
    onBack: () -> Unit,
    viewModel: FavoritesViewModel = hiltViewModel(),
) {
    val isTv = LocalTvMode.current
    val backgroundColor = rememberScreenBackgroundColor()
    val adaptiveInfo = LocalAdaptiveInfo.current
    val mediaTypeFilter by viewModel.mediaTypeFilter.collectAsStateWithLifecycle()
    val pagingItems = viewModel.pagedItems.collectAsLazyPagingItems()
    val photoFolderChildUrls by viewModel.photoFolderChildUrls.collectAsStateWithLifecycle()

    val snapshot = pagingItems.itemSnapshotList
    LaunchedEffect(snapshot) {
        viewModel.prefetchPhotoFolderChildUrls(snapshot.items)
    }

    // Long-press / TV-Menu quick actions for favorite cards
    val quickActionController = rememberMediaQuickActionController(
        resolveActions = remember { { item: MediaItem -> favoritesQuickActions(item) } },
        executeAction = remember(viewModel, onItemClick) {
            { item: MediaItem, action: QuickAction ->
                when (action) {
                    QuickAction.PLAY -> onItemClick(item.id, item.mediaType, item.parentId, item.name)
                    QuickAction.MARK_WATCHED -> viewModel.markItemPlayed(item, true)
                    QuickAction.MARK_UNWATCHED -> viewModel.markItemPlayed(item, false)
                    QuickAction.DETAILS -> onItemClick(item.id, item.mediaType, item.parentId, item.name)
                    else -> Unit
                }
            }
        },
    )
    // TV-only: the card currently holding D-pad focus, so the Menu key can open
    // its quick actions.
    var tvFocusedItem by remember { mutableStateOf<MediaItem?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onDpadKey(
                onMenu = {
                    tvFocusedItem?.let { quickActionController.show(it) }
                    true
                },
            ),
    ) {
        CompositionLocalProvider(LocalMediaQuickActionController provides quickActionController) {
        JellyPlayScreenScaffold(
            title = stringResource(R.string.library_favorites),
            onBack = onBack,
            backgroundColor = backgroundColor,
        ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = adaptiveInfo.contentPadding(isTv),
                    end = adaptiveInfo.contentPadding(isTv),
                ),
        ) {
            MediaTypeFilterRow(
                selectedType = mediaTypeFilter,
                onTypeSelected = { viewModel.setMediaTypeFilter(it) },
            )

            when {
                pagingItems.loadState.refresh is LoadState.Loading && pagingItems.itemCount == 0 -> {
                    com.raulshma.jellyplay.core.ui.components.DelayedLoadingScreen()
                }
                pagingItems.loadState.refresh is LoadState.Error -> {
                    com.raulshma.jellyplay.core.ui.components.ErrorScreen(
                        message = (pagingItems.loadState.refresh as LoadState.Error).error.message
                            ?: stringResource(R.string.library_failed_to_load_favorites),
                        onRetry = { pagingItems.retry() },
                    )
                }
                pagingItems.itemCount == 0 && pagingItems.loadState.refresh is LoadState.NotLoading -> {
                    com.raulshma.jellyplay.core.ui.components.ScreenEmptyState(
                        icon = Tabler.Outline.Heart,
                        title = stringResource(R.string.library_no_favorites),
                        description = stringResource(R.string.library_no_favorites_description),
                    )
                }
                else -> {
                    val gridState = rememberLazyGridState()
                    TvFocusableGrid(
                        itemCount = pagingItems.itemCount,
                        key = { index -> pagingItems[index]?.id ?: index },
                        columns = GridCells.Adaptive(minSize = if (isTv) 180.dp else 150.dp),
                        contentType = { "mediaItem" },
                        state = gridState,
                        onFocusedIndexChange = { index -> pagingItems[index]?.let { tvFocusedItem = it } },
                        contentPadding = PaddingValues(
                            start = if (isTv) 16.dp else 12.dp,
                            end = if (isTv) 16.dp else 12.dp,
                            top = 8.dp,
                            bottom = innerPadding.calculateBottomPadding() + 80.dp,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(if (isTv) 16.dp else 12.dp),
                        verticalArrangement = Arrangement.spacedBy(if (isTv) 20.dp else 16.dp),
                        modifier = Modifier.fillMaxSize(),
                        extraContent = {
                            if (pagingItems.loadState.append is LoadState.Loading) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        com.raulshma.jellyplay.core.ui.components.JellyPlayLoadingIndicator(
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                        },
                    ) { index, itemModifier ->
                        val item = pagingItems[index]
                        if (item != null) {
                            PosterCard(
                                item = item,
                                imageUrl = remember(item.id) { viewModel.getImageUrl(item.id) },
                                blurHash = item.blurHashes.primary,
                                onClick = { onItemClick(item.id, item.mediaType, item.parentId, item.name) },
                                photoFolderChildImageUrls = photoFolderChildUrls[item.id].orEmpty(),
                                modifier = itemModifier,
                            )
                        }
                    }
                }
            }
        } // close Column
        } // close scaffold content lambda
        } // close CompositionLocalProvider
    } // close Box
    MediaQuickActionHost(quickActionController)
} // close FavoritesScreen

@Composable
private fun MediaTypeFilterRow(
    selectedType: MediaType?,
    onTypeSelected: (MediaType?) -> Unit,
) {
    // (type, string resource) pairs — labels resolved at render time so they
    // follow the active locale. Reuses the shared core media-type strings.
    val filterTypes = remember {
        listOf<Pair<MediaType?, Int>>(
            null to R.string.library_all,
            MediaType.MOVIE to com.raulshma.jellyplay.core.ui.R.string.core_media_movie_plural,
            MediaType.SERIES to com.raulshma.jellyplay.core.ui.R.string.core_media_series_plural,
            MediaType.EPISODE to com.raulshma.jellyplay.core.ui.R.string.core_media_episode_plural,
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        filterTypes.forEach { (type, labelRes) ->
            val isSelected = selectedType == type
            val label = stringResource(labelRes)
            val containerColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                label = "filterChipColor_$label",
            )
            val contentColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                label = "filterChipTextColor_$label",
            )

            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            val scale by animateFloatAsState(
                targetValue = if (isPressed) 0.95f else 1.0f,
                animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                label = "filterChipScale_$label",
            )

            Box(
                modifier = Modifier
                    .clip(ShapeCache.smooth14)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .background(containerColor)
                    .focusIndicator(ShapeCache.smooth16)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                    ) { onTypeSelected(type) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = contentColor,
                )
            }
        }
    }
}

/**
 * Which quick actions apply to a favorite card Mirrors the
 * library grid's actions: playable entries get play / mark-watched / details.
 */
private fun favoritesQuickActions(item: MediaItem): List<QuickAction> = buildList {
    when (item.mediaType) {
        MediaType.MOVIE, MediaType.SERIES, MediaType.SEASON, MediaType.EPISODE,
        MediaType.AUDIO, MediaType.MUSIC, MediaType.ALBUM, MediaType.ARTIST,
        MediaType.MUSIC_VIDEO, MediaType.COLLECTION, MediaType.LIVE_TV, MediaType.CHANNEL -> {
            add(QuickAction.PLAY)
            add(if (item.isPlayed) QuickAction.MARK_UNWATCHED else QuickAction.MARK_WATCHED)
            add(QuickAction.DETAILS)
        }
        else -> Unit
    }
}
