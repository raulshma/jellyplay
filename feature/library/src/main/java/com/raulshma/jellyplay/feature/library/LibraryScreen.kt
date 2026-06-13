package com.raulshma.jellyplay.feature.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import com.raulshma.jellyplay.core.ui.components.JellyPlayLinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.raulshma.jellyplay.core.designsystem.theme.LocalIsLightTheme
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.ExpressiveToolbarIconButton
import com.raulshma.jellyplay.core.ui.components.GlassDismissTag
import com.raulshma.jellyplay.core.ui.components.LoadingScreen
import com.raulshma.jellyplay.core.ui.components.PosterCard
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.*
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.model.LibraryViewMode
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.feature.library.components.LibraryFilterSheet
import com.raulshma.jellyplay.feature.library.components.LibraryListItem
import com.raulshma.jellyplay.core.ui.animation.animateContentSizeNoClip
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import kotlinx.coroutines.launch

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class,
)
@Composable
fun LibraryScreen(
    onItemClick: (itemId: String, mediaType: MediaType, parentId: String?, itemName: String) -> Unit,
    onSmartPlaylistsClick: () -> Unit = {},
    onMoodPlaylistsClick: () -> Unit = {},
    onPlaylistsClick: () -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val selectedFolder by viewModel.selectedFolder.collectAsStateWithLifecycle()
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    val genres by viewModel.genres.collectAsStateWithLifecycle()
    val showFilters by viewModel.showFilters.collectAsStateWithLifecycle()
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()

    val pagedItems = viewModel.pagedItems.collectAsLazyPagingItems()
    val photoFolderChildUrls by viewModel.photoFolderChildUrls.collectAsStateWithLifecycle()

    LaunchedEffect(pagedItems.itemCount) {
        val snapshot = (0 until pagedItems.itemCount).mapNotNull { pagedItems[it] }
        viewModel.prefetchPhotoFolderChildUrls(snapshot)
    }
    val networkStatus by com.raulshma.jellyplay.core.ui.components.LocalNetworkStatus.current.collectAsStateWithLifecycle()

    val headerStatus = com.raulshma.jellyplay.core.ui.components.resolveHeaderStatus(
        isLoading = isLoading,
        hasError = error != null,
        networkStatus = networkStatus,
    )

    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    val hasActiveFilters by remember {
        derivedStateOf {
            filters.mediaTypes.isNotEmpty() ||
                filters.genres.isNotEmpty() ||
                filters.playedStatus != PlayedStatus.ALL
        }
    }

    val backgroundColor = MaterialTheme.colorScheme.background

    var headerVisible by remember { mutableStateOf(true) }

    val isScrolled by remember {
        derivedStateOf { gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 50 }
    }
    var toolbarExpanded by remember { mutableStateOf(true) }
    LaunchedEffect(isScrolled) {
        toolbarExpanded = !isScrolled
    }

    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    val contentPad = adaptiveInfo.contentPadding(isTv)
    val spacing = adaptiveInfo.itemSpacing(isTv)
    val bottomPad = adaptiveInfo.bottomPadding(isTv)

    val gridPadding = PaddingValues(
        start = contentPad,
        end = contentPad,
        top = 8.dp,
        bottom = bottomPad,
    )

    val gridCellSize = adaptiveInfo.gridCellSize(isTv)
    val navOffsetPx = com.raulshma.jellyplay.core.ui.components.LocalFloatingNavOffset.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor),
    ) {
        if (error != null && pagedItems.itemCount == 0) {
            ErrorScreen(
                message = error!!,
                onRetry = { viewModel.refresh() },
            )
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                // ═══════════════════════════════════════════════════════════════
                // ── Header Section
                // ═══════════════════════════════════════════════════════════════
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    backgroundColor.copy(alpha = 0.95f),
                                    backgroundColor,
                                ),
                            )
                        )
                        .statusBarsPadding()
                        .padding(top = 16.dp),
                ) {
                    // ── Title row ──
                    AnimatedVisibility(
                        visible = headerVisible,
                        enter = fadeIn(
                            spring(stiffness = Spring.StiffnessMediumLow)
                        ) + slideInVertically(
                            spring(
                                stiffness = Spring.StiffnessMediumLow,
                                dampingRatio = Spring.DampingRatioLowBouncy,
                            ),
                            initialOffsetY = { -40 },
                        ),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 24.dp, end = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Library",
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            ErrorAwareStatusIndicator(
                                status = headerStatus,
                                errorMessage = error,
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // ── Folder chips (glass pill with spring shape morphing) ──
                    AnimatedVisibility(
                        visible = headerVisible && folders.size > 1,
                        enter = fadeIn(
                            spring(stiffness = Spring.StiffnessMediumLow)
                        ) + slideInVertically(
                            spring(
                                stiffness = Spring.StiffnessMediumLow,
                                dampingRatio = Spring.DampingRatioLowBouncy,
                            ),
                            initialOffsetY = { 40 },
                        ),
                    ) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            item {
                                GlassPill(
                                    label = "All",
                                    selected = selectedFolder == null,
                                    onClick = { viewModel.selectFolder(null) },
                                )
                            }
                            items(folders.size, key = { folders[it].id }, contentType = { "folder" }) { index ->
                                val folder = folders[index]
                                GlassPill(
                                    label = folder.name,
                                    selected = selectedFolder?.id == folder.id,
                                    onClick = { viewModel.selectFolder(folder) },
                                )
                            }
                        }
                    }

                    // ── Active filters bar ──
                    AnimatedVisibility(
                        visible = hasActiveFilters,
                        enter = fadeIn(
                            spring(stiffness = Spring.StiffnessHigh)
                        ) + expandVertically(),
                        exit = fadeOut(
                            spring(stiffness = Spring.StiffnessHigh)
                        ) + shrinkVertically(),
                    ) {
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                                .padding(top = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            filters.mediaTypes.forEach { mediaType ->
                                GlassDismissTag(
                                    label = mediaType.name,
                                    onDismiss = {
                                        viewModel.updateFilters(
                                            filters.copy(mediaTypes = filters.mediaTypes - mediaType)
                                        )
                                    },
                                )
                            }
                            if (filters.playedStatus != PlayedStatus.ALL) {
                                GlassDismissTag(
                                    label = filters.playedStatus.displayName,
                                    onDismiss = {
                                        viewModel.updateFilters(
                                            filters.copy(playedStatus = PlayedStatus.ALL)
                                        )
                                    },
                                )
                            }
                            filters.genres.forEach { genre ->
                                GlassDismissTag(
                                    label = genre,
                                    onDismiss = {
                                        viewModel.updateFilters(
                                            filters.copy(genres = filters.genres - genre)
                                        )
                                    },
                                )
                            }
                            val clearAllFocusState = rememberTvFocusState(focusedScale = 1.05f)
                            val clearAllInteractionSource = remember { MutableInteractionSource() }
                            val isClearAllPressed by clearAllInteractionSource.collectIsPressedAsState()
                            val clearAllScale by animateFloatAsState(
                                targetValue = if (isClearAllPressed) 0.95f else 1f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium,
                                ),
                                label = "clearAllPressedScale"
                            )
                            val clearAllShapeMorph by animateFloatAsState(
                                targetValue = if (isClearAllPressed) 1f else 0f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium,
                                ),
                                label = "clearAllShapeMorph"
                            )
                            val clearAllShape = remember(clearAllShapeMorph) {
                                if (clearAllShapeMorph > 0.5f) ShapeCache.smooth12 else ShapeCache.smooth8
                            }
                            Box(
                                modifier = Modifier
                                    .graphicsLayer {
                                        scaleX = clearAllScale * clearAllFocusState.scale
                                        scaleY = clearAllScale * clearAllFocusState.scale
                                    }
                                    .clip(clearAllShape)
                                    .then(clearAllFocusState.focusModifier)
                                    .tvFocusIndicator(clearAllFocusState, clearAllShape)
                                    .clickable(
                                        interactionSource = clearAllInteractionSource,
                                        indication = null,
                                        onClick = { viewModel.clearFilters() }
                                    )
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                            ) {
                                Text(
                                    text = "Clear all",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }

                    // ── Item count ──
                    AnimatedVisibility(
                        visible = headerVisible && pagedItems.itemCount > 0,
                        enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                    ) {
                        Text(
                            text = "${pagedItems.itemCount} items",
                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(
                                horizontal = 24.dp,
                                vertical = 8.dp,
                            ),
                        )
                    }
                }

                // ═══════════════════════════════════════════════════════════════
                // ── Grid Content
                // ═══════════════════════════════════════════════════════════════
                Box(modifier = Modifier.fillMaxSize()) {
                    when (pagedItems.loadState.refresh) {
                        is LoadState.Loading -> {
                            LoadingScreen()
                        }

                        is LoadState.Error -> {
                            val refreshError = pagedItems.loadState.refresh as LoadState.Error
                            ErrorScreen(
                                message = refreshError.error.localizedMessage
                                    ?: "Failed to load items",
                                onRetry = { pagedItems.refresh() },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }

                        is LoadState.NotLoading -> {
                            if (pagedItems.itemCount == 0) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Icon(
                                            Tabler.Outline.Search,
                                            contentDescription = null,
                                            modifier = Modifier.size(48.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                        )
                                        Text(
                                            text = "No items found",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        if (hasActiveFilters) {
                                            Text(
                                                text = "Try adjusting your filters",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            )
                                        }
                                    }
                                }
                            } else {
                                if (viewMode == LibraryViewMode.LIST) {
                                    LazyColumn(
                                        state = listState,
                                        contentPadding = gridPadding,
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.fillMaxSize(),
                                    ) {
                                        items(
                                            count = pagedItems.itemCount,
                                            key = pagedItems.itemKey { it.id },
                                            contentType = { "mediaItem" },
                                        ) { index ->
                                            val item = pagedItems[index]
                                            if (item != null) {
                                                val memoizedClick = remember(item.id, item.mediaType, item.parentId, item.name) {
                                                    { onItemClick(item.id, item.mediaType, item.parentId, item.name) }
                                                }
                                                val subtitle = remember(item.year, item.mediaType) {
                                                    buildString {
                                                        if (item.year != null) append("${item.year}")
                                                        val typeLabel = when (item.mediaType) {
                                                            MediaType.EPISODE -> "Episode"
                                                            MediaType.SERIES -> "Series"
                                                            MediaType.MOVIE -> "Movie"
                                                            MediaType.AUDIO -> "Audio"
                                                            MediaType.MUSIC -> "Music"
                                                            MediaType.PHOTO, MediaType.PHOTO_FOLDER -> "Photo"
                                                            else -> null
                                                        }
                                                        if (typeLabel != null) {
                                                            if (isNotEmpty()) append(" · ")
                                                            append(typeLabel)
                                                        }
                                                    }
                                                }
                                                LibraryListItem(
                                                    title = item.name,
                                                    subtitle = subtitle,
                                                    imageUrl = remember(item.id) { viewModel.getImageUrl(item.id) },
                                                    blurHash = item.blurHashes.primary,
                                                    onClick = memoizedClick,
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    LazyVerticalGrid(
                                        state = gridState,
                                        columns = GridCells.Adaptive(gridCellSize),
                                        contentPadding = gridPadding,
                                        horizontalArrangement = Arrangement.spacedBy(spacing),
                                        verticalArrangement = Arrangement.spacedBy(spacing),
                                        modifier = Modifier.fillMaxSize(),
                                    ) {
                                        items(
                                            count = pagedItems.itemCount,
                                            key = pagedItems.itemKey { it.id },
                                            contentType = { "mediaItem" },
                                        ) { index ->
                                            val item = pagedItems[index]
                                            if (item != null) {
                                                val memoizedClick = remember(item.id, item.mediaType, item.parentId, item.name) {
                                                    { onItemClick(item.id, item.mediaType, item.parentId, item.name) }
                                                }
                                                PosterCard(
                                                    item = item,
                                                    imageUrl = remember(item.id) { viewModel.getImageUrl(item.id) },
                                                    onClick = memoizedClick,
                                                    showProgress = item.playbackPositionTicks != null && item.playbackPositionTicks!! > 0,
                                                    progressPercent = if (item.runTimeTicks != null && item.runTimeTicks!! > 0) {
                                                        (item.playbackPositionTicks?.toFloat()
                                                            ?: 0f) / item.runTimeTicks!!.toFloat()
                                                    } else 0f,
                                                    blurHash = item.blurHashes.primary,
                                                    sharedElementKey = "poster_${item.id}",
                                                    photoFolderChildImageUrls = photoFolderChildUrls[item.id].orEmpty(),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ── Scroll-aware Floating Toolbar ──
                    if (!isTv && pagedItems.itemCount > 0) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = toolbarExpanded,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 64.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
                                .offset {
                                    val maxOffset = 64.dp.toPx()
                                    val yOffset = (-navOffsetPx).coerceAtMost(maxOffset)
                                    androidx.compose.ui.unit.IntOffset(x = 0, y = yOffset.toInt())
                                },
                            enter = fadeIn(
                                spring(stiffness = Spring.StiffnessMedium)
                            ) + slideInVertically(
                                spring(
                                    stiffness = Spring.StiffnessMedium,
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                ),
                                initialOffsetY = { it },
                            ),
                            exit = fadeOut(
                                spring(stiffness = Spring.StiffnessHigh)
                            ) + androidx.compose.animation.slideOutVertically(
                                spring(stiffness = Spring.StiffnessHigh),
                                targetOffsetY = { it },
                            ),
                        ) {
                            HorizontalFloatingToolbar(
                                expanded = true,
                                modifier = Modifier.padding(horizontal = 24.dp),
                                colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
                                floatingActionButton = {
                                    FloatingToolbarDefaults.VibrantFloatingActionButton(
                                        onClick = { viewModel.toggleShowFilters() },
                                    ) {
                                        Icon(
                                            Tabler.Outline.Filter,
                                            contentDescription = "Filters",
                                        )
                                    }
                                },
                            ) {
                                IconButton(
                                    onClick = {
                                        viewModel.setViewMode(
                                            if (viewMode == LibraryViewMode.GRID) LibraryViewMode.LIST else LibraryViewMode.GRID
                                        )
                                    },
                                    shapes = IconButtonDefaults.shapes(),
                                ) {
                                    Icon(
                                        if (viewMode == LibraryViewMode.GRID) Tabler.Outline.List else Tabler.Outline.GridDots,
                                        contentDescription = if (viewMode == LibraryViewMode.GRID) "List view" else "Grid view",
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                IconButton(
                                    onClick = onSmartPlaylistsClick,
                                    shapes = IconButtonDefaults.shapes(),
                                ) {
                                    Icon(
                                        Tabler.Outline.Wand,
                                        contentDescription = "Smart Playlists",
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                IconButton(
                                    onClick = onMoodPlaylistsClick,
                                    shapes = IconButtonDefaults.shapes(),
                                ) {
                                    Icon(
                                        Tabler.Outline.MoodSmile,
                                        contentDescription = "Mood Playlists",
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                IconButton(
                                    onClick = onPlaylistsClick,
                                    shapes = IconButtonDefaults.shapes(),
                                ) {
                                    Icon(
                                        Tabler.Outline.Playlist,
                                        contentDescription = "Playlists",
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    }

                    // ── Append loading ──
                    if (pagedItems.loadState.append is LoadState.Loading) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            backgroundColor,
                                        ),
                                    )
                                )
                                .padding(vertical = 20.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            JellyPlayLinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth(0.4f)
                                    .clip(ShapeCache.smooth4),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    // ── Append error ──
                    if (pagedItems.loadState.append is LoadState.Error) {
                        val appendError = pagedItems.loadState.append as LoadState.Error
                        Text(
                            text = appendError.error.localizedMessage
                                ?: "Failed to load more items",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(16.dp)
                                .fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }

    if (showFilters) {
        LibraryFilterSheet(
            currentFilters = filters,
            genres = genres,
            onApply = { newFilters ->
                viewModel.updateFilters(newFilters)
                viewModel.toggleShowFilters()
            },
            onDismiss = { viewModel.toggleShowFilters() },
        )
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// ── Subcomponents
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ErrorAwareStatusIndicator(
    status: com.raulshma.jellyplay.core.ui.components.HeaderStatus,
    errorMessage: String?,
    modifier: Modifier = Modifier,
) {
    val isError = status is com.raulshma.jellyplay.core.ui.components.HeaderStatus.Error
    val tooltipState = rememberTooltipState(isPersistent = false)
    val scope = rememberCoroutineScope()

    if (isError && errorMessage != null) {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
            tooltip = {
                PlainTooltip(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = ShapeCache.smooth12,
                ) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            state = tooltipState,
            enableUserInput = true,
            modifier = modifier,
        ) {
            com.raulshma.jellyplay.core.ui.components.HeaderStatusIndicator(
                status = status,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { scope.launch { tooltipState.show() } },
                ),
            )
        }
    } else {
        com.raulshma.jellyplay.core.ui.components.HeaderStatusIndicator(
            status = status,
            modifier = modifier,
        )
    }
}



@Composable
private fun GlassPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val focusState = rememberTvFocusState(focusedScale = 1.05f)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val baseScale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "pillPressedScale"
    )
    val scale = baseScale * focusState.scale

    val shapeMorphProgress by animateFloatAsState(
        targetValue = if (isPressed || selected) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "pillShapeMorph"
    )
    val shape = remember(shapeMorphProgress) {
        if (shapeMorphProgress > 0.5f) ShapeCache.smooth20 else ShapeCache.smooth16
    }

    val isLight = LocalIsLightTheme.current
    val surfaceColor = when {
        selected -> MaterialTheme.colorScheme.primary
        else -> if (isLight) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    }
    val contentColor = when {
        selected -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .then(focusState.focusModifier)
            .tvFocusIndicator(focusState, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .animateContentSizeNoClip(MaterialTheme.motionScheme.slowSpatialSpec()),
        shape = shape,
        color = surfaceColor,
        contentColor = contentColor,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}


