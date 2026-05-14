package com.raulshma.jellyplay.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.DisposableEffect
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
import com.raulshma.jellyplay.core.ui.adaptive.*
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.HeaderStatusIndicator
import com.raulshma.jellyplay.core.ui.components.LocalNavigationBarColor
import com.raulshma.jellyplay.core.ui.components.LocalNetworkStatus
import com.raulshma.jellyplay.core.ui.components.ModeSwitch
import com.raulshma.jellyplay.core.ui.components.PosterCard
import com.raulshma.jellyplay.core.ui.components.PlayButtonWithProgress
import com.raulshma.jellyplay.core.ui.components.rememberDominantColor
import com.raulshma.jellyplay.core.ui.components.resolveHeaderStatus
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.ui.tv.isTvDevice
import com.raulshma.jellyplay.core.ui.tv.tvFocusable
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onItemClick: (String) -> Unit,
    onPlayClick: (itemId: String, mediaSourceId: String?, startPosition: Long) -> Unit = { _, _, _ -> },
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

        val savedScrollPosition = remember { viewModel.getHomeScrollPosition() }
        val listState = rememberSaveable(saver = LazyListState.Saver) {
            LazyListState(
                firstVisibleItemIndex = savedScrollPosition.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = savedScrollPosition.firstVisibleItemScrollOffset,
            )
        }
        val saveHomeScrollPosition = {
            viewModel.saveHomeScrollPosition(
                firstVisibleItemIndex = listState.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset,
            )
        }

        DisposableEffect(listState) {
            onDispose {
                saveHomeScrollPosition()
            }
        }
        val density = LocalDensity.current
        val adaptiveInfo = LocalAdaptiveInfo.current
        val isTv = isTvDevice()

        val headerHeight = when {
            isTv -> com.raulshma.jellyplay.core.ui.adaptive.AdaptiveHeroHeight.Tv
            adaptiveInfo.isLandscape && adaptiveInfo.windowSizeClass != WindowSizeClass.Compact ->
                com.raulshma.jellyplay.core.ui.adaptive.AdaptiveHeroHeight.LandscapeMedium
            else -> com.raulshma.jellyplay.core.ui.adaptive.AdaptiveHeroHeight.PortraitCompact
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

            val appBarCollapsed by remember {
                derivedStateOf {
                    listState.firstVisibleItemIndex > 0 ||
                            listState.firstVisibleItemScrollOffset > headerHeightPx * 0.7f
                }
            }
            val heroScrollOffsetProvider = remember(listState, headerHeightPx) {
                {
                    if (listState.firstVisibleItemIndex == 0) {
                        listState.firstVisibleItemScrollOffset.toFloat()
                    } else {
                        headerHeightPx
                    }
                }
            }

            val appBarColor by animateFloatAsState(
                targetValue = if (appBarCollapsed) 1f else 0f,
                animationSpec = tween(300),
                label = "appBarColor",
            )

            val animatedContainerColor = lerp(
                Color.Transparent,
                backgroundColor.copy(alpha = 0.95f),
                appBarColor,
            )

            val currentOnItemClick by rememberUpdatedState(onItemClick)
            val currentViewModel by rememberUpdatedState(viewModel)
            val mediaImageUrlBuilder = remember {
                { item: MediaItem -> currentViewModel.getImageUrl(item.id) }
            }
            val mediaBackdropUrlBuilder = remember {
                { item: MediaItem -> currentViewModel.getBackdropUrl(item.id) }
            }
            val mediaOnItemClick = remember {
                { item: MediaItem ->
                    saveHomeScrollPosition()
                    currentOnItemClick(item.id)
                }
            }
            val currentOnPlayClick by rememberUpdatedState(onPlayClick)
            val mediaOnPlayClick = remember {
                { item: MediaItem ->
                    saveHomeScrollPosition()
                    val startPos = item.playbackPositionTicks ?: 0L
                    currentOnPlayClick(item.id, null, startPos)
                }
            }

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
                        } else {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    bottom = adaptiveInfo.bottomPadding(isTv),
                                    start = adaptiveInfo.contentPadding(isTv),
                                    end = adaptiveInfo.contentPadding(isTv)
                                ),
                            ) {
                                if (featuredItem != null) {
                                    item {
                                        AnimatedHeroHeader(
                                            featuredItem = featuredItem,
                                            getBackdropUrl = { viewModel.getBackdropUrl(it) },
                                            scrollOffsetProvider = heroScrollOffsetProvider,
                                            height = headerHeight,
                                            backgroundColor = backgroundColor,
                                            onItemClick = {
                                                saveHomeScrollPosition()
                                                onItemClick(it)
                                            },
                                        )
                                    }
                                } else {
                                    item { Spacer(Modifier.height(100.dp)) }
                                }

                                items(count = sections.size, key = { sections[it].title }, contentType = { "homeSection" }) { index ->
                                    val section = sections[index]
                                    val isFirstAfterHero = index == 0 && featuredItem != null
                                    val isSectionVisible by remember {
                                        derivedStateOf {
                                            val layoutInfo = listState.layoutInfo
                                            val itemInfo = layoutInfo.visibleItemsInfo.find { it.index == index + (if (featuredItem != null) 1 else 0) }
                                            itemInfo != null
                                        }
                                    }
                                    var hasBeenVisible by remember { mutableStateOf(isSectionVisible) }
                                    if (isSectionVisible) hasBeenVisible = true

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

                                    AnimatedVisibility(
                                        visible = hasBeenVisible,
                                        enter = fadeIn(
                                            animationSpec = tween(350, easing = FastOutSlowInEasing),
                                        ) + slideInVertically(
                                            initialOffsetY = { it / 20 },
                                            animationSpec = tween(350, easing = FastOutSlowInEasing),
                                        ) + scaleIn(
                                            initialScale = 0.97f,
                                            animationSpec = tween(350, easing = FastOutSlowInEasing),
                                        ),
                                        exit = fadeOut(tween(100)),
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
                                            onPlayClick = mediaOnPlayClick,
                                            modifier = sectionModifier,
                                        )
                                    } else {
                                        HomeMediaRow(
                                            title = section.title,
                                            items = section.items,
                                            imageUrlBuilder = mediaImageUrlBuilder,
                                            fallbackImageUrlBuilder = fallbackImageUrlBuilder,
                                            onItemClick = mediaOnItemClick,
                                            onPlayClick = mediaOnPlayClick,
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
                                onClick = {
                                    saveHomeScrollPosition()
                                    onSyncPlayClick()
                                },
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
                                    onClick = {
                                        saveHomeScrollPosition()
                                        onDownloadsClick()
                                    },
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
                                onClick = {
                                    saveHomeScrollPosition()
                                    onSettingsClick()
                                },
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
private fun AnimatedHeroHeader(
    featuredItem: MediaItem,
    getBackdropUrl: (String) -> String,
    scrollOffsetProvider: () -> Float,
    height: androidx.compose.ui.unit.Dp,
    backgroundColor: Color,
    onItemClick: (String) -> Unit,
) {
    AnimatedContent(
        targetState = featuredItem,
        transitionSpec = {
            fadeIn(
                animationSpec = tween(520, easing = FastOutSlowInEasing),
            ) + scaleIn(
                initialScale = 1.035f,
                animationSpec = tween(700, easing = FastOutSlowInEasing),
            ) + slideInHorizontally(
                initialOffsetX = { it / 24 },
                animationSpec = tween(520, easing = FastOutSlowInEasing),
            ) togetherWith fadeOut(
                animationSpec = tween(320, easing = FastOutSlowInEasing),
            ) + scaleOut(
                targetScale = 0.985f,
                animationSpec = tween(320, easing = FastOutSlowInEasing),
            ) + slideOutHorizontally(
                targetOffsetX = { -it / 36 },
                animationSpec = tween(320, easing = FastOutSlowInEasing),
            )
        },
        label = "heroRotation",
        ) { currentFeatured ->
        HeroHeader(
            item = currentFeatured,
            backdropUrl = getBackdropUrl(currentFeatured.id),
            scrollOffsetProvider = scrollOffsetProvider,
            height = height,
            backgroundColor = backgroundColor,
            onClick = { onItemClick(currentFeatured.id) },
            sharedBackdropKey = "backdrop_${currentFeatured.id}",
        )
    }
}

@Composable
private fun HeroHeader(
    item: MediaItem,
    backdropUrl: String,
    scrollOffsetProvider: () -> Float,
    height: androidx.compose.ui.unit.Dp,
    backgroundColor: Color,
    onClick: () -> Unit,
    sharedBackdropKey: String? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = tween(150),
        label = "heroPress",
    )
    val playInteractionSource = remember { MutableInteractionSource() }
    val isPlayPressed by playInteractionSource.collectIsPressedAsState()
    val playScale by animateFloatAsState(
        targetValue = if (isPlayPressed) 0.95f else 1f,
        animationSpec = spring(stiffness = 400f),
        label = "playButtonScale",
    )
    val detailsInteractionSource = remember { MutableInteractionSource() }
    val isDetailsPressed by detailsInteractionSource.collectIsPressedAsState()
    val detailsScale by animateFloatAsState(
        targetValue = if (isDetailsPressed) 0.95f else 1f,
        animationSpec = spring(stiffness = 400f),
        label = "detailsButtonScale",
    )

    val sharedScope = com.raulshma.jellyplay.core.ui.components.LocalSharedTransitionScope.current
    val animScope = com.raulshma.jellyplay.core.ui.components.LocalAnimatedVisibilityScope.current

    val backdropModifier = Modifier
        .fillMaxSize()
        .then(
            if (sharedScope != null && animScope != null && sharedBackdropKey != null) {
                with(sharedScope) {
                    Modifier.sharedElement(
                        rememberSharedContentState(sharedBackdropKey),
                        animatedVisibilityScope = animScope,
                    )
                }
            } else Modifier
        )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
                translationY = scrollOffsetProvider() * 0.5f
            }
            .tvFocusable().clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
    ) {
        MediaImage(
            url = backdropUrl,
            contentDescription = item.name,
            blurHash = item.blurHashes.backdrop,
            modifier = backdropModifier,
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
                        .tvFocusable().clickable(
                            interactionSource = playInteractionSource,
                            indication = null,
                            onClick = onClick,
                        )
                        .padding(horizontal = 24.dp)
                        .graphicsLayer { scaleX = playScale; scaleY = playScale },
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
                        .tvFocusable().clickable(
                            interactionSource = detailsInteractionSource,
                            indication = null,
                            onClick = onClick,
                        )
                        .padding(horizontal = 24.dp)
                        .graphicsLayer { scaleX = detailsScale; scaleY = detailsScale },
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
    onPlayClick: ((MediaItem) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = isTvDevice()
    val cardWidth = when {
        isTv -> 400.dp
        adaptiveInfo.windowSizeClass != WindowSizeClass.Compact -> 320.dp
        else -> 260.dp
    }
    val contentPad = adaptiveInfo.contentPadding(isTv)
    val spacing = adaptiveInfo.itemSpacing(isTv)

    Column(modifier = modifier) {
        Text(
            text = title,
            style = if (isTv) MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold)
                   else MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White,
            modifier = Modifier.padding(horizontal = contentPad, vertical = 8.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = contentPad),
            horizontalArrangement = Arrangement.spacedBy(spacing),
        ) {
            items(
                count = items.size,
                key = { items[it].id },
                contentType = { "continueWatchingCard" },
            ) { index ->
                val item = items[index]
                WideMediaCard(
                    item = item,
                    imageUrl = imageUrlBuilder(item),
                    backdropUrl = backdropUrlBuilder(item),
                    onClick = { onItemClick(item) },
                    onPlayClick = onPlayClick?.let { { it(item) } },
                    cardWidth = cardWidth,
                    sharedElementKey = "backdrop_${item.id}",
                )
            }
        }
    }
}

@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@Composable
private fun WideMediaCard(
    item: MediaItem,
    imageUrl: String,
    backdropUrl: String,
    onClick: () -> Unit,
    onPlayClick: (() -> Unit)? = null,
    cardWidth: androidx.compose.ui.unit.Dp,
    sharedElementKey: String? = null,
) {
    val isTv = isTvDevice()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 400f),
        label = "wideCardScale",
    )
    val elevation by animateFloatAsState(
        targetValue = if (isPressed) 12f else 4f,
        animationSpec = tween(200),
        label = "wideCardElevation",
    )
    val brightnessOverlay by animateFloatAsState(
        targetValue = if (isPressed) 0.08f else 0f,
        animationSpec = tween(150),
        label = "wideCardBrightness",
    )

    val dominantColor = rememberDominantColor(backdropUrl.ifBlank { imageUrl })
    val hasProgress = item.playbackPositionTicks != null && item.runTimeTicks != null && item.runTimeTicks!! > 0
    val progressPercent = if (hasProgress) {
        (item.playbackPositionTicks!!.toFloat() / item.runTimeTicks!!.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val playButtonSize = if (isTv) 44.dp else 36.dp

    val sharedScope = com.raulshma.jellyplay.core.ui.components.LocalSharedTransitionScope.current
    val animScope = com.raulshma.jellyplay.core.ui.components.LocalAnimatedVisibilityScope.current

    val imageModifier = Modifier
        .fillMaxWidth()
        .aspectRatio(16f / 9f)
        .then(
            if (sharedScope != null && animScope != null && sharedElementKey != null) {
                with(sharedScope) {
                    Modifier.sharedElement(
                        rememberSharedContentState(sharedElementKey),
                        animatedVisibilityScope = animScope,
                    )
                }
            } else Modifier
        )

    Column(modifier = Modifier.width(cardWidth)) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    shadowElevation = elevation.dp.toPx()
                }
                .tvFocusable().clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        ) {
            Box {
                MediaImage(
                    url = backdropUrl,
                    fallbackUrls = listOf(imageUrl).filter { it.isNotBlank() },
                    contentDescription = item.name,
                    blurHash = item.blurHashes.backdrop,
                    modifier = imageModifier,
                    contentScale = ContentScale.Crop,
                )

                if (brightnessOverlay > 0.01f) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.White.copy(alpha = brightnessOverlay))
                    )
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(50.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.4f),
                                ),
                            )
                        )
                )

                if (onPlayClick != null) {
                    PlayButtonWithProgress(
                        progressPercent = progressPercent,
                        dominantColor = dominantColor,
                        onClick = onPlayClick,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 8.dp, bottom = 8.dp),
                        buttonSize = playButtonSize,
                    )
                }
            }
        }

        Column(
            modifier = Modifier.padding(
                start = 4.dp,
                end = 4.dp,
                top = 6.dp,
            ),
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.9f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.seriesName != null) {
                Text(
                    text = item.seriesName!!,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else if (item.year != null) {
                Text(
                    text = item.year.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.55f),
                )
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
    onPlayClick: ((MediaItem) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = isTvDevice()
    val cardWidth = adaptiveInfo.rowCardWidth(isTv)
    val contentPad = adaptiveInfo.contentPadding(isTv)
    val spacing = adaptiveInfo.itemSpacing(isTv)

    Column(modifier = modifier) {
        Text(
            text = title,
            style = if (isTv) MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold)
                   else MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White,
            modifier = Modifier.padding(horizontal = contentPad, vertical = 8.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = contentPad),
            horizontalArrangement = Arrangement.spacedBy(spacing),
        ) {
            items(
                count = items.size,
                key = { items[it].id },
                contentType = { "homePosterCard" },
            ) { index ->
                val item = items[index]
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
                    onPlayClick = onPlayClick?.let { { it(item) } },
                    sharedElementKey = "poster_${item.id}",
                )
            }
        }
    }
}
