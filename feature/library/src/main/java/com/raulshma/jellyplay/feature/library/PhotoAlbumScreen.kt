package com.raulshma.jellyplay.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
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
import com.raulshma.jellyplay.core.ui.components.LoadingScreen
import com.raulshma.jellyplay.core.ui.components.LocalNetworkStatus
import com.raulshma.jellyplay.core.ui.components.ScreenEmptyState
import com.raulshma.jellyplay.core.ui.components.ScreenLoadingState
import com.raulshma.jellyplay.core.ui.components.resolveHeaderStatus
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.feature.library.components.PhotoGridCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoAlbumScreen(
    parentId: String,
    folderName: String,
    onPhotoClick: (itemId: String, parentId: String) -> Unit,
    onBack: () -> Unit,
    viewModel: PhotoAlbumViewModel = hiltViewModel(),
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

    JellyPlayScreenScaffold(
        title = folderName.ifBlank { "Photos" },
        onBack = onBack,
        actions = {
            var showSortMenu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { showSortMenu = true }) {
                    Icon(
                        imageVector = Tabler.Outline.DotsVertical,
                        contentDescription = "Sort options",
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
            isRefreshing = photos.loadState.refresh is LoadState.Loading,
            onRefresh = { photos.refresh() },
            enabled = !isTv,
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                when (val refreshState = photos.loadState.refresh) {
                    is LoadState.Loading -> {
                        ScreenLoadingState()
                    }
                    is LoadState.Error -> {
                        ErrorScreen(
                            message = refreshState.error.localizedMessage ?: "Failed to load photos",
                            onRetry = { photos.refresh() },
                        )
                    }
                    is LoadState.NotLoading -> {
                        if (photos.itemCount == 0) {
                            ScreenEmptyState(
                                icon = Tabler.Outline.Photo,
                                title = "No photos found",
                            )
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(adaptiveInfo.gridCellSize(isTv)),
                                contentPadding = PaddingValues(
                                    start = adaptiveInfo.contentPadding(isTv),
                                    end = adaptiveInfo.contentPadding(isTv),
                                    top = 8.dp,
                                    bottom = adaptiveInfo.bottomPadding(isTv),
                                ),
                                horizontalArrangement = Arrangement.spacedBy(adaptiveInfo.itemSpacing(isTv)),
                                verticalArrangement = Arrangement.spacedBy(adaptiveInfo.itemSpacing(isTv)),
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                items(
                                    count = photos.itemCount,
                                    key = photos.itemKey { it.id },
                                    contentType = { "photoItem" },
                                ) { index ->
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
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                when (val appendState = photos.loadState.append) {
                    is LoadState.Loading -> {
                        LoadingScreen(
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }
                    is LoadState.Error -> {
                        Text(
                            text = appendState.error.localizedMessage ?: "Failed to load more",
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
