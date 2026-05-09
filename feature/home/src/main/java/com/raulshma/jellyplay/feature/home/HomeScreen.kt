package com.raulshma.jellyplay.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.designsystem.theme.ArtworkThemeWrapper
import com.raulshma.jellyplay.core.designsystem.theme.LocalArtworkColors
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.WindowSizeClass
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.HeaderStatusIndicator
import com.raulshma.jellyplay.core.ui.components.LocalNavigationBarColor
import com.raulshma.jellyplay.core.ui.components.LocalNetworkStatus
import com.raulshma.jellyplay.core.ui.components.ModeSwitch
import com.raulshma.jellyplay.core.ui.components.PosterCard
import com.raulshma.jellyplay.core.ui.components.StaggeredSection
import com.raulshma.jellyplay.core.ui.components.resolveHeaderStatus
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.ui.tv.isTvDevice
import com.raulshma.jellyplay.core.ui.tv.tvFocusable
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
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
    val featuredCandidates = remember(allItems) {
        allItems.filter {
            it.mediaType == MediaType.MOVIE || it.mediaType == MediaType.SERIES
        }.ifEmpty { allItems }
    }

    var featuredIndex by remember { mutableIntStateOf(0) }
    var autoRotateEnabled by remember { mutableStateOf(true) }

    LaunchedEffect(showSurprise) {
        if (showSurprise && featuredCandidates.isNotEmpty()) {
            featuredIndex = (0 until featuredCandidates.size).random()
            autoRotateEnabled = false
        }
    }

    if (featuredCandidates.isNotEmpty() && autoRotateEnabled) {
        LaunchedEffect(featuredCandidates) {
            while (true) {
                delay(8000)
                featuredIndex = (featuredIndex + 1) % featuredCandidates.size
            }
        }
    }

    val featuredItem = remember(featuredCandidates, featuredIndex) {
        featuredCandidates.getOrNull(featuredIndex)
    }

    val fallbackImageUrlBuilder: (MediaItem) -> List<String> = remember {
        { item ->
            if (item.mediaType == MediaType.AUDIO || item.mediaType == MediaType.MUSIC) {
                listOfNotNull(
                    item.parentId?.let { viewModel.getImageUrl(it) },
                    item.artistItems.firstOrNull()?.id?.let { viewModel.getImageUrl(it) }
                )
            } else {
                emptyList()
            }
        }
    }

    if (kidsMode) {
        val currentOnItemClick by rememberUpdatedState(onItemClick)
        val currentViewModel by rememberUpdatedState(viewModel)
        val kidsImageUrlBuilder = remember {
            { item: MediaItem -> currentViewModel.getImageUrl(item.id) }
        }
        val kidsOnRefresh = remember {
            { currentViewModel.refresh() }
        }
        val kidsOnItemClick = remember {
            { id: String -> currentOnItemClick(id) }
        }
        KidsHomeScreen(
            sections = sections,
            favorites = viewModel.favorites,
            isLoading = isLoading,
            error = error,
            imageUrlBuilder = kidsImageUrlBuilder,
            fallbackImageUrlBuilder = fallbackImageUrlBuilder,
            onItemClick = kidsOnItemClick,
            onRefresh = kidsOnRefresh,
        )
    } else {
        val backdropUrl = featuredItem?.let { viewModel.getBackdropUrl(it.id) }

        val listState = rememberSaveable(saver = LazyListState.Saver) {
            LazyListState()
        }
        val density = LocalDensity.current
        val adaptiveInfo = LocalAdaptiveInfo.current
        val isTv = isTvDevice()

        val headerHeight = when {
            isTv -> 420.dp
            adaptiveInfo.isLandscape && adaptiveInfo.windowSizeClass != WindowSizeClass.Compact -> 320.dp
            else -> 520.dp
        }
        val headerHeightPx = with(density) { headerHeight.toPx() }

        ArtworkThemeWrapper(
            imageUrl = backdropUrl,
            dynamicTheming = viewModel.dynamicTheming,
            darkTheme = true,
        ) {
            val artworkColors = LocalArtworkColors.current
            val baseOverlayColor = artworkColors?.darkMuted
                ?: artworkColors?.dominant
                ?: Color(0xFF1A1A2E)
            val targetBackgroundColor = lerp(baseOverlayColor, Color.Black, 0.65f)
            val backgroundColor by animateColorAsState(
                targetValue = targetBackgroundColor,
                animationSpec = tween(600),
                label = "backgroundColor",
            )

            val navBarColor = LocalNavigationBarColor.current
            navBarColor.value = backgroundColor

            val scrollOffset by remember {
                derivedStateOf {
                    listState.firstVisibleItemScrollOffset.toFloat() +
                            (listState.firstVisibleItemIndex * 1000f)
                }
            }
            val scrollFraction by remember {
                derivedStateOf {
                    (scrollOffset / headerHeightPx).coerceIn(0f, 1f)
                }
            }

            val appBarColor by animateFloatAsState(
                targetValue = if (scrollFraction > 0.7f) 1f else 0f,
                animationSpec = tween(300),
                label = "appBarColor",
            )

            val animatedContainerColor = lerp(
                Color.Transparent,
                backgroundColor.copy(alpha = 0.95f),
                appBarColor,
            )

            val sectionsVisible = sections.isNotEmpty()

            val currentOnItemClick by rememberUpdatedState(onItemClick)
            val currentViewModel by rememberUpdatedState(viewModel)
            val mediaImageUrlBuilder = remember {
                { item: MediaItem -> currentViewModel.getImageUrl(item.id) }
            }
            val mediaBackdropUrlBuilder = remember {
                { item: MediaItem -> currentViewModel.getBackdropUrl(item.id) }
            }
            val mediaOnItemClick = remember {
                { item: MediaItem -> currentOnItemClick(item.id) }
            }

            val showLandscapeHero = adaptiveInfo.isLandscape &&
                    adaptiveInfo.windowSizeClass != WindowSizeClass.Compact

            Box(modifier = Modifier.fillMaxSize().background(backgroundColor)) {
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
                                    color = Color.White.copy(alpha = 0.6f),
                                )
                            }
                        } else if (showLandscapeHero) {
                            Row(modifier = Modifier.fillMaxSize()) {
                                if (featuredItem != null) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .weight(0.55f)
                                    ) {
                                        AnimatedContent(
                                            targetState = featuredItem,
                                            transitionSpec = {
                                                fadeIn(tween(500)) togetherWith fadeOut(tween(500))
                                            },
                                            label = "heroRotation",
                                        ) { currentFeatured ->
                                            HeroHeader(
                                                item = currentFeatured,
                                                backdropUrl = viewModel.getBackdropUrl(currentFeatured.id),
                                                scrollOffset = 0f,
                                                height = headerHeight,
                                                backgroundColor = backgroundColor,
                                                onClick = { onItemClick(currentFeatured.id) },
                                            )
                                        }
                                    }
                                }
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier
                                        .weight(0.45f)
                                        .fillMaxHeight(),
                                    contentPadding = PaddingValues(
                                        top = 100.dp,
                                        bottom = 100.dp,
                                        end = 24.dp,
                                    ),
                                ) {
                                    items(count = sections.size, key = { sections[it].title }, contentType = { "homeSection" }) { index ->
                                        val section = sections[index]
                                        StaggeredSection(
                                            visible = sectionsVisible,
                                            index = index,
                                        ) {
                                            if (section.type == HomeSectionType.CONTINUE_WATCHING ||
                                                section.type == HomeSectionType.NEXT_UP
                                            ) {
                                                ContinueWatchingRow(
                                                    title = section.title,
                                                    items = section.items,
                                                    imageUrlBuilder = mediaImageUrlBuilder,
                                                    backdropUrlBuilder = mediaBackdropUrlBuilder,
                                                    onItemClick = mediaOnItemClick,
                                                )
                                            } else {
                                                HomeMediaRow(
                                                    title = section.title,
                                                    items = section.items,
                                                    imageUrlBuilder = mediaImageUrlBuilder,
                                                    fallbackImageUrlBuilder = fallbackImageUrlBuilder,
                                                    onItemClick = mediaOnItemClick,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 100.dp),
                            ) {
                                if (featuredItem != null) {
                                    item {
                                        AnimatedContent(
                                            targetState = featuredItem,
                                            transitionSpec = {
                                                fadeIn(tween(500)) togetherWith fadeOut(tween(500))
                                            },
                                            label = "heroRotation",
                                        ) { currentFeatured ->
                                            HeroHeader(
                                                item = currentFeatured,
                                                backdropUrl = viewModel.getBackdropUrl(currentFeatured.id),
                                                scrollOffset = scrollOffset,
                                                height = headerHeight,
                                                backgroundColor = backgroundColor,
                                                onClick = { onItemClick(currentFeatured.id) },
                                            )
                                        }
                                    }
                                } else {
                                    item { Spacer(Modifier.height(100.dp)) }
                                }

                                items(count = sections.size, key = { sections[it].title }, contentType = { "homeSection" }) { index ->
                                    val section = sections[index]
                                    val isFirstAfterHero = index == 0 && featuredItem != null
                                    val sectionModifier = Modifier
                                        .fillMaxWidth()
                                        .then(
                                            if (isFirstAfterHero) {
                                                Modifier.background(
                                                    Brush.verticalGradient(
                                                        colors = listOf(Color.Transparent, backgroundColor),
                                                        startY = 0f,
                                                        endY = with(density) { 10.dp.toPx() },
                                                    )
                                                )
                                            } else {
                                                Modifier.background(backgroundColor)
                                            }
                                        )
                                        .padding(top = if (isFirstAfterHero) 0.dp else 16.dp)

                                    StaggeredSection(
                                        visible = sectionsVisible,
                                        index = index,
                                    ) {
                                        if (section.type == HomeSectionType.CONTINUE_WATCHING ||
                                            section.type == HomeSectionType.NEXT_UP
                                        ) {
                                            ContinueWatchingRow(
                                                title = section.title,
                                                items = section.items,
                                                imageUrlBuilder = mediaImageUrlBuilder,
                                                backdropUrlBuilder = mediaBackdropUrlBuilder,
                                                onItemClick = mediaOnItemClick,
                                                modifier = sectionModifier,
                                            )
                                        } else {
                                            HomeMediaRow(
                                                title = section.title,
                                                items = section.items,
                                                imageUrlBuilder = mediaImageUrlBuilder,
                                                fallbackImageUrlBuilder = fallbackImageUrlBuilder,
                                                onItemClick = mediaOnItemClick,
                                                modifier = sectionModifier,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(animatedContainerColor)
                ) {
                    TopAppBar(
                        title = {
                            Text(
                                "JellyPlay",
                                color = Color.White.copy(alpha = appBarColor),
                                fontWeight = FontWeight.Bold,
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
                            IconButton(
                                onClick = {
                                    showSurprise = !showSurprise
                                    if (!showSurprise) autoRotateEnabled = true
                                },
                                modifier = Modifier.tvFocusable(),
                            ) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = "Surprise Me",
                                    tint = if (showSurprise) MaterialTheme.colorScheme.primary else Color.White,
                                )
                            }
                            IconButton(
                                onClick = onSyncPlayClick,
                                modifier = Modifier.tvFocusable(),
                            ) {
                                Icon(
                                    Icons.Default.Group,
                                    contentDescription = "SyncPlay",
                                    tint = Color.White,
                                )
                            }
                            BadgedBox(
                                badge = {
                                    if (activeDownloadCount > 0) {
                                        Badge { Text(activeDownloadCount.toString()) }
                                    }
                                }
                            ) {
                                IconButton(
                                    onClick = onDownloadsClick,
                                    modifier = Modifier.tvFocusable(),
                                ) {
                                    Icon(
                                        Icons.Default.Download,
                                        contentDescription = "Downloads",
                                        tint = Color.White,
                                    )
                                }
                            }
                            IconButton(
                                onClick = onSettingsClick,
                                modifier = Modifier.tvFocusable(),
                            ) {
                                Icon(
                                    Icons.Default.Settings,
                                    contentDescription = "Settings",
                                    tint = Color.White,
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                        modifier = Modifier.statusBarsPadding(),
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroHeader(
    item: MediaItem,
    backdropUrl: String,
    scrollOffset: Float,
    height: androidx.compose.ui.unit.Dp,
    backgroundColor: Color,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = tween(150),
        label = "heroPress",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
                translationY = scrollOffset * 0.5f
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .tvFocusable()
    ) {
        MediaImage(
            url = backdropUrl,
            contentDescription = item.name,
            blurHash = item.blurHashes.backdrop,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.4f),
                            Color.Transparent,
                            backgroundColor.copy(alpha = 0.3f),
                            backgroundColor.copy(alpha = 0.8f),
                            backgroundColor,
                        ),
                        startY = 0f,
                        endY = Float.POSITIVE_INFINITY,
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item.year?.let {
                    Text(
                        text = it.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                }
                item.runTimeTicks?.let { ticks ->
                    val minutes = ticks / 600_000_000
                    Text(
                        text = "${minutes}m",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                }
                item.officialRating?.let {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                        )
                    }
                }
                item.communityRating?.let { rating ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Favorite,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = String.format("%.1f", rating),
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White.copy(alpha = 0.8f),
                        )
                    }
                }
            }

            if (item.genres.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(item.genres, contentType = { "genre" }) { genre ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White.copy(alpha = 0.15f))
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = genre,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.9f),
                            )
                        }
                    }
                }
            }

            item.overview?.let { overview ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = overview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val hasProgress = item.playbackPositionTicks != null && item.playbackPositionTicks!! > 0

                Box(
                    modifier = Modifier
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(onClick = onClick)
                        .tvFocusable()
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (hasProgress) "Resume" else "Play",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White.copy(alpha = 0.15f))
                        .clickable(onClick = onClick)
                        .tvFocusable()
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Details",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun ContinueWatchingRow(
    title: String,
    items: List<MediaItem>,
    imageUrlBuilder: (MediaItem) -> String,
    backdropUrlBuilder: (MediaItem) -> String,
    onItemClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val adaptiveInfo = LocalAdaptiveInfo.current
    val cardWidth = if (adaptiveInfo.windowSizeClass != WindowSizeClass.Compact) 320.dp else 260.dp

    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items, key = { "${title}_${it.id}" }, contentType = { "mediaItem" }) { item ->
                WideMediaCard(
                    item = item,
                    imageUrl = imageUrlBuilder(item),
                    backdropUrl = backdropUrlBuilder(item),
                    onClick = { onItemClick(item) },
                    cardWidth = cardWidth,
                )
            }
        }
    }
}

@Composable
private fun WideMediaCard(
    item: MediaItem,
    imageUrl: String,
    backdropUrl: String,
    onClick: () -> Unit,
    cardWidth: androidx.compose.ui.unit.Dp = 260.dp,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = tween(150),
        label = "wideCardScale",
    )

    Column(
        modifier = Modifier
            .width(cardWidth)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .tvFocusable(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
            contentAlignment = Alignment.Center,
        ) {
            MediaImage(
                url = backdropUrl,
                fallbackUrls = listOf(imageUrl),
                contentDescription = item.name,
                blurHash = item.blurHashes.primary,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )

            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)))

            Icon(
                Icons.Default.PlayArrow,
                contentDescription = "Play",
                tint = Color.White,
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    .padding(8.dp),
            )

            if (item.playbackPositionTicks != null && item.playbackPositionTicks!! > 0) {
                val progress = if (item.runTimeTicks != null && item.runTimeTicks!! > 0) {
                    (item.playbackPositionTicks!!.toFloat() / item.runTimeTicks!!).coerceIn(0f, 1f)
                } else 0f
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(progress)
                        .height(3.dp)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }

        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.mediaType == MediaType.EPISODE) {
                item.seriesName?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            } else {
                item.runTimeTicks?.let { ticks ->
                    Text(
                        text = "${ticks / 600_000_000}m",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeMediaRow(
    title: String,
    items: List<MediaItem>,
    imageUrlBuilder: (MediaItem) -> String,
    fallbackImageUrlBuilder: (MediaItem) -> List<String>,
    onItemClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val adaptiveInfo = LocalAdaptiveInfo.current
    val cardWidth = when (adaptiveInfo.windowSizeClass) {
        WindowSizeClass.Expanded -> 180.dp
        WindowSizeClass.Medium -> 160.dp
        WindowSizeClass.Compact -> if (adaptiveInfo.isLandscape) 160.dp else 140.dp
    }

    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items, key = { "${title}_${it.id}" }, contentType = { "mediaItem" }) { item ->
                PosterCard(
                    item = item,
                    imageUrl = imageUrlBuilder(item),
                    fallbackUrls = fallbackImageUrlBuilder(item),
                    onClick = { onItemClick(item) },
                    modifier = Modifier.width(cardWidth),
                    showProgress = item.playbackPositionTicks != null && item.playbackPositionTicks!! > 0,
                    progressPercent = if (item.runTimeTicks != null && item.runTimeTicks!! > 0) {
                        (item.playbackPositionTicks?.toFloat() ?: 0f) / item.runTimeTicks!!.toFloat()
                    } else 0f,
                    blurHash = item.blurHashes.primary,
                )
            }
        }
    }
}
