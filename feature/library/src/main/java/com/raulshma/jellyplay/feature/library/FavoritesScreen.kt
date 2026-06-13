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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.PosterCard
import com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColor
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer

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

    LaunchedEffect(pagingItems.itemCount) {
        val snapshot = (0 until pagingItems.itemCount).mapNotNull { pagingItems[it] }
        viewModel.prefetchPhotoFolderChildUrls(snapshot)
    }

    JellyPlayScreenScaffold(
        title = "Favorites",
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
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        com.raulshma.jellyplay.core.ui.components.JellyPlayLoadingIndicator(
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                pagingItems.loadState.refresh is LoadState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Failed to load favorites",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = (pagingItems.loadState.refresh as LoadState.Error).error.message ?: "Unknown error",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                pagingItems.itemCount == 0 && pagingItems.loadState.refresh is LoadState.NotLoading -> {
                    com.raulshma.jellyplay.core.ui.components.ScreenEmptyState(
                        icon = Tabler.Outline.Heart,
                        title = "No Favorites",
                        description = "Items you favorite will appear here",
                    )
                }
                else -> {
                    val gridState = rememberLazyGridState()
                    val tvGridFocusRequester = remember { FocusRequester() }
                    val tvGridFallbackRequester = remember { FocusRequester() }
                    var tvGridFocusedIndex by rememberSaveable { mutableIntStateOf(0) }
                    // Grab focus on the grid once paging data resolves, and clamp the saved index to the
                    // live page count. Without this the grid composes over async data but never receives focus.
                    TvGrabInitialFocus(
                        focusRequester = tvGridFocusRequester,
                        itemCount = pagingItems.itemCount,
                        onReady = { tvGridFocusedIndex = tvGridFocusedIndex.coerceIn(0, (pagingItems.itemCount - 1).coerceAtLeast(0)) },
                        tag = "favorites_grid_init",
                    )
                    @OptIn(ExperimentalComposeUiApi::class)
                    val tvGridModifier = if (isTv) Modifier
                        .focusProperties { onEnter = { tvGridFocusRequester.tryRequestFocus("favorites_grid") } }
                        .focusGroup()
                        .tvFocusRestorer(tvGridFallbackRequester)
                        .focusRequester(tvGridFocusRequester)
                    else Modifier
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = if (isTv) 180.dp else 150.dp),
                        state = gridState,
                        contentPadding = PaddingValues(
                            start = if (isTv) 16.dp else 12.dp,
                            end = if (isTv) 16.dp else 12.dp,
                            top = 8.dp,
                            bottom = innerPadding.calculateBottomPadding() + 80.dp,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(if (isTv) 16.dp else 12.dp),
                        verticalArrangement = Arrangement.spacedBy(if (isTv) 20.dp else 16.dp),
                        modifier = Modifier.fillMaxSize().then(tvGridModifier),
                    ) {
                        items(
                            count = pagingItems.itemCount,
                            key = { index -> pagingItems[index]?.id ?: index }
                        ) { index ->
                            val item = pagingItems[index]
                            if (item != null) {
                                val tvItemModifier = if (isTv) Modifier
                                    .onFocusChanged { if (it.hasFocus) tvGridFocusedIndex = index }
                                    .then(if (index == tvGridFocusedIndex) Modifier.focusRequester(tvGridFallbackRequester) else Modifier)
                                else Modifier
                                PosterCard(
                                    item = item,
                                    imageUrl = remember(item.id) { viewModel.getImageUrl(item.id) },
                                    blurHash = item.blurHashes.primary,
                                    onClick = { onItemClick(item.id, item.mediaType, item.parentId, item.name) },
                                    photoFolderChildImageUrls = photoFolderChildUrls[item.id].orEmpty(),
                                    modifier = tvItemModifier,
                                )
                            }
                        }

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
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaTypeFilterRow(
    selectedType: MediaType?,
    onTypeSelected: (MediaType?) -> Unit,
) {
    val filterTypes = remember {
        listOf<Pair<MediaType?, String>>(
            null to "All",
            MediaType.MOVIE to "Movies",
            MediaType.SERIES to "TV Shows",
            MediaType.EPISODE to "Episodes",
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        filterTypes.forEach { (type, label) ->
            val isSelected = selectedType == type
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
