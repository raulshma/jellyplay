package com.raulshma.jellyplay.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.ExperimentalSharedTransitionApi
import com.raulshma.jellyplay.core.ui.navigation.LocalSharedTransitionScope
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.HeaderStatusIndicator
import com.raulshma.jellyplay.core.ui.components.LocalNetworkStatus
import com.raulshma.jellyplay.core.ui.components.MediaRow
import com.raulshma.jellyplay.core.ui.components.ModeSwitch
import com.raulshma.jellyplay.core.ui.components.resolveHeaderStatus

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun HomeScreen(
    onItemClick: (String) -> Unit,
    onSettingsClick: () -> Unit = {},
    onSyncPlayClick: () -> Unit = {},
    onDownloadsClick: () -> Unit = {},
    homeMode: HomeMode = HomeMode.VIDEO,
    onModeChange: (HomeMode) -> Unit = {},
    musicContent: @Composable () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    if (viewModel.homeMode == HomeMode.MUSIC) {
        musicContent()
        return
    }

    val sections = viewModel.sections
    val isLoading = viewModel.isLoading
    val error = viewModel.error
    val kidsMode = viewModel.kidsModeEnabled
    val networkStatus by LocalNetworkStatus.current.collectAsStateWithLifecycle()
    val activeDownloadCount by viewModel.activeDownloadCount.collectAsStateWithLifecycle()

    val headerStatus = resolveHeaderStatus(
        isLoading = isLoading,
        hasError = error != null,
        networkStatus = networkStatus,
    )

    var showSurprise by remember { mutableStateOf(false) }
    val allItems = remember(sections) { sections.flatMap { it.items } }
    
    // Pick a featured item for the hero header (random or first from continue watching/first section)
    val featuredItem = remember(allItems, showSurprise) {
        if (allItems.isNotEmpty()) {
            if (showSurprise) allItems.random() 
            else allItems.firstOrNull { it.mediaType == com.raulshma.jellyplay.core.model.MediaType.MOVIE || it.mediaType == com.raulshma.jellyplay.core.model.MediaType.SERIES } ?: allItems.firstOrNull()
        } else null
    }

    val listState = rememberLazyListState()
    val scrollOffset = listState.firstVisibleItemScrollOffset.toFloat() + (listState.firstVisibleItemIndex * 1000f)
    
    val density = LocalDensity.current
    val headerHeight = 500.dp
    val headerHeightPx = with(density) { headerHeight.toPx() }
    val scrollFraction = (scrollOffset / headerHeightPx).coerceIn(0f, 1f)
    
    val appBarAlpha by animateFloatAsState(
        targetValue = if (scrollFraction > 0.8f) 1f else 0f,
        animationSpec = tween(300), label = "appBarAlpha"
    )

    val fallbackImageUrlBuilder: (MediaItem) -> List<String> = { item ->
        if (item.mediaType == com.raulshma.jellyplay.core.model.MediaType.AUDIO || item.mediaType == com.raulshma.jellyplay.core.model.MediaType.MUSIC) {
            listOfNotNull(
                item.parentId?.let { viewModel.getImageUrl(it) },
                item.artistItems.firstOrNull()?.id?.let { viewModel.getImageUrl(it) }
            )
        } else {
            emptyList()
        }
    }

    if (kidsMode) {
        KidsHomeScreen(
            sections = sections,
            favorites = viewModel.favorites,
            isLoading = isLoading,
            error = error,
            imageUrlBuilder = { item -> viewModel.getImageUrl(item.id) },
            fallbackImageUrlBuilder = fallbackImageUrlBuilder,
            onItemClick = onItemClick,
            onRefresh = { viewModel.refresh() },
        )
    } else {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            when {
                error != null && sections.isEmpty() -> {
                    ErrorScreen(
                        message = error!!,
                        onRetry = { viewModel.refresh() },
                    )
                }
                else -> {
                    if (sections.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                if (isLoading) "" else "No content available. Check your Jellyfin libraries.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 100.dp),
                        ) {
                            if (featuredItem != null) {
                                item {
                                    HeroHeader(
                                        item = featuredItem,
                                        backdropUrl = viewModel.getBackdropUrl(featuredItem.id),
                                        scrollOffset = scrollOffset,
                                        height = headerHeight,
                                        onClick = { onItemClick(featuredItem.id) }
                                    )
                                }
                            } else {
                                item {
                                    Spacer(modifier = Modifier.height(100.dp))
                                }
                            }

                            items(count = sections.size) { index ->
                                val section = sections[index]
                                    MediaRow(
                                        title = section.title,
                                        items = section.items,
                                        imageUrlBuilder = { item -> viewModel.getImageUrl(item.id) },
                                        fallbackImageUrlBuilder = fallbackImageUrlBuilder,
                                        onItemClick = { item -> onItemClick(item.id) },
                                        modifier = Modifier.padding(top = if (index == 0 && featuredItem != null) 0.dp else 16.dp)
                                    )
                            }
                        }
                    }
                }
            }

            // Animated Top App Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.background.copy(alpha = appBarAlpha)
                    )
            ) {
                TopAppBar(
                    title = {
                        Text(
                            "JellyPlay",
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = appBarAlpha),
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 12.dp),
                        ) {
                            ModeSwitch(
                                currentMode = viewModel.homeMode,
                                onModeChange = onModeChange,
                            )
                            HeaderStatusIndicator(
                                status = headerStatus,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { showSurprise = !showSurprise }) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = "Surprise Me",
                                tint = if (showSurprise) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                            )
                        }
                        IconButton(onClick = onSyncPlayClick) {
                            Icon(Icons.Default.Group, contentDescription = "SyncPlay", tint = MaterialTheme.colorScheme.onBackground)
                        }
                        BadgedBox(
                            badge = {
                                if (activeDownloadCount > 0) {
                                    Badge {
                                        Text(activeDownloadCount.toString())
                                    }
                                }
                            }
                        ) {
                            IconButton(onClick = onDownloadsClick) {
                                Icon(Icons.Default.Download, contentDescription = "Downloads", tint = MaterialTheme.colorScheme.onBackground)
                            }
                        }
                        IconButton(onClick = onSettingsClick) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onBackground)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    modifier = Modifier.statusBarsPadding(),
                )
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun HeroHeader(
    item: MediaItem,
    backdropUrl: String,
    scrollOffset: Float,
    height: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit
) {
    val sharedTransitionScope = LocalSharedTransitionScope.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clickable(onClick = onClick)
            .graphicsLayer {
                translationY = scrollOffset * 0.5f // Parallax
            }
    ) {
        com.raulshma.jellyplay.core.ui.image.MediaImage(
            url = backdropUrl,
            contentDescription = item.name,
            blurHash = item.blurHashes.backdrop,
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (sharedTransitionScope != null) {
                        with(sharedTransitionScope) {
                            Modifier.sharedElementWithCallerManagedVisibility(
                                rememberSharedContentState(key = "backdrop_${item.id}"),
                                visible = true,
                            )
                        }
                    } else {
                        Modifier
                    }
                ),
            contentScale = ContentScale.Crop,
        )

        // Gradient Overlays
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.4f), // Darker top for AppBar
                            Color.Transparent,
                            MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                            MaterialTheme.colorScheme.background
                        ),
                        startY = 0f,
                        endY = Float.POSITIVE_INFINITY
                    )
                )
        )

        // Content
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 24.dp)
                .padding(bottom = 60.dp)
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item.year?.let {
                    Text(
                        text = it.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
                item.runTimeTicks?.let { ticks ->
                    val minutes = ticks / 600_000_000
                    Text(
                        text = "${minutes}m",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onClick,
                modifier = Modifier.height(48.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text("Play", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            }
        }
    }
}
