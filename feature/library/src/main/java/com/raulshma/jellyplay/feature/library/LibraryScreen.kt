package com.raulshma.jellyplay.feature.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.raulshma.jellyplay.core.designsystem.theme.LocalArtworkColors
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.PosterCard
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.WindowSizeClass
import com.raulshma.jellyplay.core.ui.adaptive.*
import com.raulshma.jellyplay.core.ui.tv.tvFocusable
import com.raulshma.jellyplay.feature.library.components.LibraryFilterSheet
import com.raulshma.jellyplay.feature.library.components.ShimmerLoadingGrid
import com.raulshma.jellyplay.core.designsystem.theme.AlphaEasing
import com.raulshma.jellyplay.core.designsystem.theme.FancyTransitionEasing
import com.raulshma.jellyplay.core.ui.animation.AnimationTokens
import com.raulshma.jellyplay.core.ui.animation.animateContentSizeNoClip

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LibraryScreen(
    onItemClick: (String) -> Unit,
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

    val pagedItems = viewModel.pagedItems.collectAsLazyPagingItems()
    val networkStatus by com.raulshma.jellyplay.core.ui.components.LocalNetworkStatus.current.collectAsStateWithLifecycle()

    val headerStatus = com.raulshma.jellyplay.core.ui.components.resolveHeaderStatus(
        isLoading = isLoading,
        hasError = error != null,
        networkStatus = networkStatus,
    )

    val gridState = rememberLazyGridState()
    val hasActiveFilters = filters.mediaTypes.isNotEmpty() ||
        filters.genres.isNotEmpty() ||
        filters.playedStatus != PlayedStatus.ALL

    // ── Cinematic background color (same approach as MediaDetailScreen) ──
    val artworkColors = LocalArtworkColors.current
    val baseColor = artworkColors?.darkMuted
        ?: artworkColors?.dominant
        ?: MaterialTheme.colorScheme.background
    val backgroundColor = lerp(baseColor, Color.Black, 0.70f)

    // Entrance animation for header
    var headerVisible by remember { mutableStateOf(true) }

    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = com.raulshma.jellyplay.core.ui.tv.isTvDevice()
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
                // ── Header Section (cinematic dark, white-on-dark text)
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
                    // ── Title + action row ──
                    AnimatedVisibility(
                        visible = headerVisible,
                        enter = fadeIn(tween(AnimationTokens.SlowDuration, easing = AlphaEasing)) + slideInVertically(
                            tween(AnimationTokens.SlowDuration, easing = FancyTransitionEasing),
                            initialOffsetY = { -40 },
                        ),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 24.dp, end = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Library",
                                    style = MaterialTheme.typography.headlineLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                    ),
                                    color = Color.White,
                                )
                                com.raulshma.jellyplay.core.ui.components.HeaderStatusIndicator(
                                    status = headerStatus,
                                    modifier = Modifier.padding(start = 12.dp),
                                )
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                GlassIconButton(
                                    onClick = onSmartPlaylistsClick,
                                    icon = Icons.Default.AutoAwesome,
                                    contentDescription = "Smart Playlists",
                                )
                                GlassIconButton(
                                    onClick = onMoodPlaylistsClick,
                                    icon = Icons.Default.Mood,
                                    contentDescription = "Mood Playlists",
                                )
                                GlassIconButton(
                                    onClick = onPlaylistsClick,
                                    icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                                    contentDescription = "Playlists",
                                )
                                // Filter button with active-dot badge
                                Box {
                                    GlassIconButton(
                                        onClick = { viewModel.toggleShowFilters() },
                                        icon = Icons.Default.FilterList,
                                        contentDescription = "Filters",
                                        highlighted = hasActiveFilters,
                                    )
                                    if (hasActiveFilters) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(4.dp)
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // ── Folder chips (glass pill style, like season badges) ──
                    AnimatedVisibility(
                        visible = headerVisible && folders.size > 1,
                        enter = fadeIn(tween(AnimationTokens.SlowDuration, delayMillis = 100, easing = AlphaEasing)) + slideInVertically(
                            tween(AnimationTokens.SlowDuration, delayMillis = 100, easing = FancyTransitionEasing),
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
                            items(folders.size, contentType = { "folder" }) { index ->
                                val folder = folders[index]
                                GlassPill(
                                    label = folder.name,
                                    selected = selectedFolder?.id == folder.id,
                                    onClick = { viewModel.selectFolder(folder) },
                                )
                            }
                        }
                    }

                    // ── Active filters bar (dismissible glass tags) ──
                    AnimatedVisibility(
                        visible = hasActiveFilters,
                        enter = fadeIn(tween(AnimationTokens.DefaultDuration, easing = AlphaEasing)) + expandVertically(),
                        exit = fadeOut(tween(AnimationTokens.DefaultDuration, easing = AlphaEasing)) + shrinkVertically(),
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
                            // Clear all link
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .tvFocusable().clickable { viewModel.clearFilters() }
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
                        enter = fadeIn(tween(400, delayMillis = 200)),
                    ) {
                        Text(
                            text = "${pagedItems.itemCount} items",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.5f),
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
                            ShimmerLoadingGrid(contentPadding = gridPadding)
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
                                // ── Empty state ──
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Icon(
                                            Icons.Default.Search,
                                            contentDescription = null,
                                            modifier = Modifier.size(48.dp),
                                            tint = Color.White.copy(alpha = 0.3f),
                                        )
                                        Text(
                                            text = "No items found",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = Color.White.copy(alpha = 0.5f),
                                        )
                                        if (hasActiveFilters) {
                                            Text(
                                                text = "Try adjusting your filters",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.White.copy(alpha = 0.3f),
                                            )
                                        }
                                    }
                                }
                            } else {
                                // ── Media Grid ──
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
                                            PosterCard(
                                                item = item,
                                                imageUrl = viewModel.getImageUrl(item.id),
                                                onClick = { onItemClick(item.id) },
                                                showProgress = item.playbackPositionTicks != null && item.playbackPositionTicks!! > 0,
                                                progressPercent = if (item.runTimeTicks != null && item.runTimeTicks!! > 0) {
                                                    (item.playbackPositionTicks?.toFloat()
                                                        ?: 0f) / item.runTimeTicks!!.toFloat()
                                                } else 0f,
                                                blurHash = item.blurHashes.primary,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ── Append loading (gradient fade + progress bar) ──
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
                            androidx.compose.material3.LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth(0.4f)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = Color.White.copy(alpha = 0.1f),
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
// ── Subcomponents (matching MediaDetailScreen design language)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Semi-transparent icon button matching the detail screen's glass icon buttons.
 * Uses [Color.White.copy(alpha = 0.15f)] backgrounds like the detail screen's
 * action buttons (favorite, download, etc.)
 */
@Composable
private fun GlassIconButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    highlighted: Boolean = false,
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = if (highlighted) 0.18f else 0.08f))
            .tvFocusable().clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(18.dp),
            tint = if (highlighted) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.8f),
        )
    }
}

/**
 * Glass pill selector matching the detail screen's season badge style.
 * Selected = solid white bg + black text. Unselected = translucent white bg + white text.
 */
@Composable
private fun GlassPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .tvFocusable().clickable(onClick = onClick)
            .animateContentSizeNoClip(spring(stiffness = Spring.StiffnessMediumLow)),
        color = if (selected) Color.White else Color.White.copy(alpha = 0.12f),
        contentColor = if (selected) Color.Black else Color.White,
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

/**
 * Small dismissible filter tag with a glass background and close icon.
 * Matches the genre pill style from the detail screen.
 */
@Composable
private fun GlassDismissTag(
    label: String,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .tvFocusable().clickable(onClick = onDismiss)
            .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.85f),
            fontWeight = FontWeight.Medium,
        )
        Icon(
            Icons.Default.Close,
            contentDescription = "Remove",
            modifier = Modifier.size(14.dp),
            tint = Color.White.copy(alpha = 0.5f),
        )
    }
}
