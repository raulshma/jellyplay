package com.raulshma.jellyplay.feature.library

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.raulshma.jellyplay.core.ui.components.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Check
import com.composables.icons.tabler.outline.DotsVertical
import com.composables.icons.tabler.outline.Photo
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.adaptive.gridCellSize
import com.raulshma.jellyplay.core.ui.adaptive.itemSpacing
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.HeaderStatusIndicator
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.JellyPlayLoadingIndicator
import com.raulshma.jellyplay.core.ui.components.LocalNetworkStatus
import com.raulshma.jellyplay.core.ui.components.ScreenEmptyState
import com.raulshma.jellyplay.core.ui.components.ScreenLoadingState
import com.raulshma.jellyplay.core.ui.components.resolveHeaderStatus
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.TvFocusableGrid
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.ui.util.safeItemKey
import com.raulshma.jellyplay.feature.library.generated.resources.Res
import com.raulshma.jellyplay.feature.library.generated.resources.library_failed_to_load_more
import com.raulshma.jellyplay.feature.library.generated.resources.library_failed_to_load_photos
import com.raulshma.jellyplay.feature.library.generated.resources.library_no_photos_found
import com.raulshma.jellyplay.feature.library.generated.resources.library_photos
import com.raulshma.jellyplay.feature.library.generated.resources.library_sort_options
import com.raulshma.jellyplay.feature.library.components.PhotoGridCard

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PhotoAlbumScreen(
    parentId: String,
    folderName: String,
    onPhotoClick: (itemId: String, parentId: String) -> Unit,
    onBack: () -> Unit,
    viewModel: PhotoAlbumViewModel = koinViewModel(),
) {
    val isTv = LocalTvMode.current
    val adaptiveInfo = LocalAdaptiveInfo.current
    val photos = viewModel.pagedItems.collectAsLazyPagingItems()
    val sortOption by viewModel.sortOption.collectAsStateWithLifecycle()
    val networkStatus by LocalNetworkStatus.current.collectAsStateWithLifecycle()

    val headerStatus = resolveHeaderStatus(
        isLoading = photos.loadState.refresh is LoadState.Loading,
        hasError = photos.loadState.refresh is LoadState.Error,
        networkStatus = networkStatus,
    )

    LaunchedEffect(parentId) {
        viewModel.setParentId(parentId)
    }

    val savedScroll = viewModel.scrollPosition
    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = savedScroll.first,
        initialFirstVisibleItemScrollOffset = savedScroll.second,
        // Same TV cache-window tuning as the library grid — prefetch ahead so fast D-pad
        // scrolling doesn't pop cards in.
        cacheWindow = com.raulshma.jellyplay.core.ui.tv.TvGridCacheWindow,
    )

    LaunchedEffect(gridState) {
        snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                viewModel.saveScrollPosition(index, offset)
            }
    }

    JellyPlayScreenScaffold(
        title = folderName.ifBlank { stringResource(Res.string.library_photos) },
        onBack = onBack,
        actions = {
            var showSortMenu by remember { mutableStateOf(false) }
            Box {
                val sortFocusState = rememberTvFocusState()
                IconButton(
                    onClick = { showSortMenu = true },
                    modifier = Modifier.then(sortFocusState.focusModifier).tvFocusIndicator(sortFocusState, CircleShape),
                ) {
                    Icon(
                        imageVector = Tabler.Outline.DotsVertical,
                        contentDescription = stringResource(Res.string.library_sort_options),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false },
                ) {
                    PhotoSortOption.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.displayName) },
                            onClick = {
                                viewModel.setSortOption(option)
                                showSortMenu = false
                            },
                            trailingIcon = {
                                if (option == sortOption) {
                                    Icon(
                                        imageVector = Tabler.Outline.Check,
                                        contentDescription = null,
                                    )
                                }
                            }
                        )
                    }
                }
            }
            HeaderStatusIndicator(
                status = headerStatus,
                modifier = Modifier.padding(end = 8.dp),
            )
        },
    ) { _ ->
        PullToRefreshBox(
            isRefreshing = photos.loadState.refresh is LoadState.Loading && photos.itemCount > 0,
            onRefresh = { photos.refresh() },
            enabled = !isTv,
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    photos.loadState.refresh is LoadState.Loading && photos.itemCount == 0 -> {
                        ScreenLoadingState()
                    }
                    photos.loadState.refresh is LoadState.Error -> {
                        ErrorScreen(
                            message = (photos.loadState.refresh as LoadState.Error)
                                .error.localizedMessage
                                ?: stringResource(Res.string.library_failed_to_load_photos),
                            onRetry = { photos.refresh() },
                        )
                    }
                    photos.itemCount == 0 -> {
                        ScreenEmptyState(
                            icon = Tabler.Outline.Photo,
                            title = stringResource(Res.string.library_no_photos_found),
                        )
                    }
                    else -> {
                        TvFocusableGrid(
                            itemCount = photos.itemCount,
                            key = photos.safeItemKey { it.id },
                            columns = GridCells.Adaptive(adaptiveInfo.gridCellSize(isTv)),
                            state = gridState,
                            contentPadding = PaddingValues(
                                start = adaptiveInfo.contentPadding(isTv),
                                end = adaptiveInfo.contentPadding(isTv),
                                top = 8.dp,
                                bottom = adaptiveInfo.bottomPadding(isTv),
                            ),
                            horizontalArrangement = Arrangement.spacedBy(adaptiveInfo.itemSpacing(isTv)),
                            verticalArrangement = Arrangement.spacedBy(adaptiveInfo.itemSpacing(isTv)),
                            modifier = Modifier.fillMaxSize(),
                            contentType = { "photoItem" },
                        ) { index, itemModifier ->
                            val photo = photos[index]
                            if (photo != null) {
                                val imageUrl = remember(photo.id) {
                                    viewModel.getImageUrl(photo.id, maxWidth = 400)
                                }
                                val memoizedClick = remember(photo.id, parentId) {
                                    { onPhotoClick(photo.id, parentId) }
                                }
                                PhotoGridCard(
                                    imageUrl = imageUrl,
                                    contentDescription = photo.name,
                                    blurHash = photo.blurHashes.primary,
                                    onClick = memoizedClick,
                                    modifier = itemModifier,
                                )
                            }
                        }
                    }
                }

                when (val appendState = photos.loadState.append) {
                    is LoadState.Loading -> {
                        JellyPlayLoadingIndicator(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(16.dp),
                        )
                    }
                    is LoadState.Error -> {
                        Text(
                            text = appendState.error.localizedMessage
                                ?: stringResource(Res.string.library_failed_to_load_more),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(16.dp),
                        )
                    }
                    is LoadState.NotLoading -> Unit
                }
            }
        }
    }
}
