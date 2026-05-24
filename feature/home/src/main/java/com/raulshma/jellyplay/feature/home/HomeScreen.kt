package com.raulshma.jellyplay.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.sp
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import com.raulshma.jellyplay.core.designsystem.theme.AlphaEasing
import com.raulshma.jellyplay.core.designsystem.theme.FancyTransitionEasing
import com.raulshma.jellyplay.core.designsystem.theme.FastInvokeEasing
import com.raulshma.jellyplay.core.designsystem.theme.PointToPointEasing
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.raulshma.jellyplay.core.designsystem.theme.ArtworkThemeWrapper
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
import com.raulshma.jellyplay.core.ui.components.SeerrRequestDialog
import com.raulshma.jellyplay.core.ui.components.LocalSeerrCardLoadingState
import com.raulshma.jellyplay.core.ui.components.LocalSeerrPrefetch
import com.raulshma.jellyplay.core.ui.components.rememberSeerrCardLoadingState
import com.raulshma.jellyplay.core.ui.components.ModeSwitch
import com.raulshma.jellyplay.core.ui.components.PosterCard
import com.raulshma.jellyplay.core.ui.components.PlayButtonWithProgress
import com.raulshma.jellyplay.core.ui.components.rememberDominantColor
import com.raulshma.jellyplay.core.ui.components.resolveHeaderStatus
import com.raulshma.jellyplay.core.ui.components.SeerrMediaCard
import com.raulshma.jellyplay.core.ui.components.formatDurationFromTicks
import com.raulshma.jellyplay.core.ui.components.formatRemainingTimeFromTicks
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.rememberInitialFocus
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.ui.tv.isTv
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.network.seerr.buildPosterUrl
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    onItemClick: (String) -> Unit,
    onPlayClick: (itemId: String, mediaSourceId: String?, startPosition: Long) -> Unit = { _, _, _ -> },
    onSettingsClick: () -> Unit = {},
    onSyncPlayClick: () -> Unit = {},
    onDownloadsClick: () -> Unit = {},
    onSeerrItemClick: (tmdbId: Int, mediaType: String) -> Unit = { _, _ -> },
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

    // Seerr request state
    var seerrRequestItem by remember { mutableStateOf<SeerrSearchItem?>(null) }
    val requestResult by viewModel.requestResult.collectAsStateWithLifecycle()
    val radarrServers by viewModel.radarrServers.collectAsStateWithLifecycle()
    val sonarrServers by viewModel.sonarrServers.collectAsStateWithLifecycle()
    val isLoadingSeerrServices by viewModel.isLoadingSeerrServices.collectAsStateWithLifecycle()
    val tvSeasons by viewModel.tvSeasons.collectAsStateWithLifecycle()
    val seerrCardLoadingState = rememberSeerrCardLoadingState()
    val seerrPrefetch: (Int, String, () -> Unit) -> Unit = remember(viewModel) {
        { tmdbId, mediaType, onDone ->
            viewModel.prefetchSeerrDetails(tmdbId, mediaType, onDone)
        }
    }

    var showSurprise by remember { mutableStateOf(false) }
    val featuredCandidates = remember(sections) {
        val latestItems = sections
            .filter { it.type == HomeSectionType.LATEST_MEDIA }
            .flatMap { section ->
                section.items
                    .filter { it.mediaType == MediaType.MOVIE || it.mediaType == MediaType.SERIES }
                    .take(3)
            }
        if (latestItems.isNotEmpty()) {
            latestItems
        } else {
            sections.flatMap { it.items }
                .filter { it.mediaType == MediaType.MOVIE || it.mediaType == MediaType.SERIES }
                .ifEmpty { sections.flatMap { it.items } }
        }
    }

    var featuredIndex by remember { mutableIntStateOf(0) }
    val isTvForRotation = LocalContext.current.isTv()
    var autoRotateEnabled by remember { mutableStateOf(!isTvForRotation) }
    var focusInHero by remember { mutableStateOf(true) }

    LaunchedEffect(showSurprise) {
        if (showSurprise && featuredCandidates.isNotEmpty()) {
            featuredIndex = (0 until featuredCandidates.size).random()
            autoRotateEnabled = false
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

        val savedScrollPos = viewModel.getHomeScrollPosition()
        val listState = rememberSaveable(saver = LazyListState.Saver) {
            LazyListState(
                firstVisibleItemIndex = savedScrollPos.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = savedScrollPos.firstVisibleItemScrollOffset,
            )
        }
        val density = LocalDensity.current
        val adaptiveInfo = LocalAdaptiveInfo.current
        val isTv = LocalTvMode.current

        val lifecycleOwner = LocalLifecycleOwner.current

        val headerHeight = when {
            isTv -> com.raulshma.jellyplay.core.ui.adaptive.AdaptiveHeroHeight.Tv
            adaptiveInfo.isLandscape && adaptiveInfo.windowSizeClass != WindowSizeClass.Compact ->
                com.raulshma.jellyplay.core.ui.adaptive.AdaptiveHeroHeight.LandscapeMedium
            else -> com.raulshma.jellyplay.core.ui.adaptive.AdaptiveHeroHeight.PortraitCompact
        }
        val headerHeightPx = with(density) { headerHeight.toPx() }

        LaunchedEffect(sections) {
            if (sections.isNotEmpty()) {
                viewModel.saveHomeScrollPosition(
                    listState.firstVisibleItemIndex,
                    listState.firstVisibleItemScrollOffset,
                )
            }
        }

        LaunchedEffect(featuredCandidates, listState) {
            if (featuredCandidates.isEmpty() || !autoRotateEnabled || !focusInHero) return@LaunchedEffect

            snapshotFlow { listState.isScrollInProgress }
                .collectLatest { isScrolling ->
                    if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return@collectLatest
                    if (!isScrolling) {
                        delay(8000)
                        if (autoRotateEnabled && focusInHero) {
                            featuredIndex = (featuredIndex + 1) % featuredCandidates.size
                        }
                    } else {
                        delay(2000)
                    }
                }
        }

        val bgColor = MaterialTheme.colorScheme.background
        val isLightTheme = remember(bgColor) {
            (bgColor.red * 0.299f + bgColor.green * 0.587f + bgColor.blue * 0.114f) > 0.5f
        }
        val artworkColors = if (viewModel.dynamicTheming && !backdropUrl.isNullOrBlank()) {
            com.raulshma.jellyplay.core.designsystem.theme.rememberArtworkColors(backdropUrl)
        } else null
        val baseOverlayColor = artworkColors?.darkMuted
            ?: artworkColors?.dominant
            ?: if (isLightTheme) MaterialTheme.colorScheme.background else Color(0xFF1A1A2E)
        val targetBackgroundColor = if (isLightTheme) {
            MaterialTheme.colorScheme.background
        } else {
            lerp(baseOverlayColor, Color.Black, 0.65f)
        }
        val backgroundColor by animateColorAsState(
            targetValue = targetBackgroundColor,
            animationSpec = tween(600, easing = FancyTransitionEasing),
            label = "backgroundColor",
        )

        val navBarColor = LocalNavigationBarColor.current
        LaunchedEffect(backgroundColor) {
            navBarColor.value = backgroundColor
        }

        val transitionRange = 140.dp
        val transitionRangePx = with(density) { transitionRange.toPx() }
        val scrollFraction by remember {
            derivedStateOf {
                if (listState.firstVisibleItemIndex > 0) {
                    1f
                } else {
                    (listState.firstVisibleItemScrollOffset.toFloat() / transitionRangePx).coerceIn(0f, 1f)
                }
            }
        }

        val contentPad = adaptiveInfo.contentPadding(isTv)

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
                currentOnItemClick(item.id)
            }
        }
        val currentOnPlayClick by rememberUpdatedState(onPlayClick)
        val mediaOnPlayClick = remember {
            { item: MediaItem ->
                val startPos = item.playbackPositionTicks ?: 0L
                currentOnPlayClick(item.id, null, startPos)
            }
        }

        val discoverSectionOrder = remember {
            listOf(
                com.raulshma.jellyplay.core.model.seerr.DiscoverSectionType.TRENDING,
                com.raulshma.jellyplay.core.model.seerr.DiscoverSectionType.POPULAR_MOVIES,
                com.raulshma.jellyplay.core.model.seerr.DiscoverSectionType.POPULAR_TV,
                com.raulshma.jellyplay.core.model.seerr.DiscoverSectionType.UPCOMING_MOVIES,
                com.raulshma.jellyplay.core.model.seerr.DiscoverSectionType.UPCOMING_TV,
            )
        }

        val allDiscoverItems = remember(viewModel.discoverSections) {
            discoverSectionOrder.flatMap {
                viewModel.discoverSections[it] ?: emptyList()
            }.distinctBy { it.id }
        }

        val discoverRows = remember(allDiscoverItems, adaptiveInfo.windowSizeClass) {
            val result = mutableListOf<List<SeerrSearchItem>>()
            var i = 0
            val pattern = if (adaptiveInfo.windowSizeClass == WindowSizeClass.Compact) {
                listOf(3, 2, 3)
            } else {
                listOf(5, 4, 6, 5)
            }
            var patternIdx = 0
            while (i < allDiscoverItems.size) {
                val targetSize = pattern[patternIdx % pattern.size]
                val rowSize = targetSize.coerceAtMost(allDiscoverItems.size - i)
                result.add(allDiscoverItems.subList(i, i + rowSize))
                i += rowSize
                patternIdx++
            }
            result
        }

        Box(modifier = Modifier.fillMaxSize().background(backgroundColor)) {
            when {
                error != null && sections.isEmpty() -> {
                    val contentPad = adaptiveInfo.contentPadding(isTv)
                    ErrorScreen(
                        message = error!!,
                        onRetry = { viewModel.refresh() },
                        modifier = Modifier.padding(horizontal = contentPad)
                    )
                }
                else -> {
                    if (sections.isEmpty()) {
                        val contentPad = adaptiveInfo.contentPadding(isTv)
                        Box(
                            Modifier.fillMaxSize().padding(horizontal = contentPad),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (isLoading) "" else "No content available. Check your Jellyfin libraries.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                        }
                    } else {
                        val visibleItemRange by remember {
                            derivedStateOf {
                                val info = listState.layoutInfo
                                if (info.visibleItemsInfo.isEmpty()) {
                                    IntRange(0, 0)
                                } else {
                                    IntRange(
                                        info.visibleItemsInfo.first().index,
                                        info.visibleItemsInfo.last().index,
                                    )
                                }
                            }
                        }

                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                bottom = adaptiveInfo.bottomPadding(isTv),
                            ),
                        ) {
                            if (featuredItem != null) {
                                item {
                                    ArtworkThemeWrapper(
                                        imageUrl = backdropUrl,
                                        dynamicTheming = viewModel.dynamicTheming,
                                        darkTheme = !isLightTheme,
                                        oledMode = viewModel.oledMode,
                                    ) {
                                        AnimatedHeroHeader(
                                            featuredItem = featuredItem,
                                            getBackdropUrl = { viewModel.getBackdropUrl(it) },
                                            height = headerHeight,
                                            backgroundColor = backgroundColor,
                                            contentPadding = contentPad,
                                            listState = listState,
                                            onItemClick = {
                                                onItemClick(it)
                                            },
                                            onDetailsClick = {
                                                onItemClick(it)
                                            },
                                            onFocusChange = { inHero ->
                                                focusInHero = inHero
                                            },
                                        )
                                    }
                                }
                            } else {
                                item { Spacer(Modifier.height(100.dp)) }
                            }

                            items(count = sections.size, key = { sections[it].id }, contentType = { "homeSection_${sections[it].type}" }) { index ->
                                val section = sections[index]
                                val isFirstAfterHero = index == 0 && featuredItem != null
                                val sectionIndexInList = index + (if (featuredItem != null) 1 else 0)
                                
                                val isCurrentlyVisible = sectionIndexInList in visibleItemRange
                                
                                var hasBeenVisible by rememberSaveable { mutableStateOf(false) }
                                if (isCurrentlyVisible && !hasBeenVisible) {
                                    hasBeenVisible = true
                                }

                                val sectionAnimation by animateFloatAsState(
                                    targetValue = if (hasBeenVisible) 1f else 0f,
                                    animationSpec = tween(350, easing = AlphaEasing),
                                    label = "sectionAnimation",
                                )

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
                                    .graphicsLayer {
                                        alpha = sectionAnimation
                                        val scale = 0.97f + (0.03f * sectionAnimation)
                                        scaleX = scale
                                        scaleY = scale
                                        translationY = (1f - sectionAnimation) * 16.dp.toPx()
                                    }

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

                            // Seerr Discover Section
                            if (viewModel.discoverEnabled && allDiscoverItems.isNotEmpty()) {
                                val contentPad = adaptiveInfo.contentPadding(isTv)
                                val spacing = 8.dp

                                item(key = "seerr_discover_header") {
                                    Text(
                                        text = "Discover",
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            fontWeight = FontWeight.Bold,
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(backgroundColor)
                                            .padding(start = contentPad, top = 24.dp, bottom = 8.dp),
                                    )
                                }

                                items(
                                    count = discoverRows.size,
                                    key = { rowIndex -> "seerr_row_${discoverRows[rowIndex].firstOrNull()?.id ?: 0}" },
                                    contentType = { "seerrRow" }
                                ) { rowIndex ->
                                    val rowItems = discoverRows[rowIndex]
                                    val pattern = if (adaptiveInfo.windowSizeClass == WindowSizeClass.Compact) listOf(3, 2, 3) else listOf(5, 4, 6, 5)
                                    val targetSize = pattern[rowIndex % pattern.size]

                                    androidx.compose.runtime.CompositionLocalProvider(
                                        LocalSeerrCardLoadingState provides seerrCardLoadingState,
                                        LocalSeerrPrefetch provides seerrPrefetch,
                                    ) {
                                        val screenWidth = LocalConfiguration.current.screenWidthDp.dp
                                        val rowWidth = screenWidth - contentPad * 2
                                        val itemWidth = (rowWidth - spacing * (targetSize - 1)) / targetSize.toFloat()
                                        LazyRow(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(backgroundColor)
                                                .padding(horizontal = contentPad, vertical = spacing / 2),
                                            horizontalArrangement = Arrangement.spacedBy(spacing),
                                            userScrollEnabled = false,
                                        ) {
                                            items(
                                                count = rowItems.size,
                                                key = { index -> rowItems[index].id }
                                            ) { index ->
                                                val item = rowItems[index]
                                                SeerrMediaCard(
                                                    item = item,
                                                    imageUrl = item.posterUrl,
                                                    isLoading = seerrCardLoadingState.isLoading(item.id),
                                                    onClick = {
                                                        val mediaType = when {
                                                            item.mediaType.equals("movie", ignoreCase = true) -> "movie"
                                                            item.mediaType.equals("tv", ignoreCase = true) -> "tv"
                                                            else -> item.mediaType
                                                        }
                                                        if (seerrCardLoadingState != null && seerrPrefetch != null) {
                                                            seerrCardLoadingState.startLoading(item.id)
                                                            seerrPrefetch(item.id, mediaType) {
                                                                seerrCardLoadingState.stopLoading(item.id)
                                                                onSeerrItemClick(item.id, mediaType)
                                                            }
                                                        } else {
                                                            onSeerrItemClick(item.id, mediaType)
                                                        }
                                                    },
                                                    onRequestClick = { seerrRequestItem = item },
                                                    modifier = Modifier.width(itemWidth),
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

            val borderAlpha = 0.12f * scrollFraction
            val appBarIconColor = lerp(Color.White, MaterialTheme.colorScheme.onSurface, scrollFraction)
            val appBarIconColorFaded = appBarIconColor.copy(alpha = 0.9f)
            val dockScale = 1f - (0.04f * scrollFraction)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(
                        horizontal = (16f * scrollFraction).dp,
                        vertical = (8f * scrollFraction).dp
                    )
                    .graphicsLayer {
                        scaleX = dockScale
                        scaleY = dockScale
                    }
                    .clip(
                        AbsoluteSmoothCornerShape(
                            cornerRadius = (28f * scrollFraction).dp,
                            smoothnessAsPercent = 60
                        )
                    )
                    .background(
                        MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.88f * scrollFraction)
                    )
                    .border(
                        BorderStroke(
                            1.dp,
                            Brush.linearGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = borderAlpha * 2f),
                                    Color.White.copy(alpha = borderAlpha * 0.5f),
                                )
                            )
                        ),
                        AbsoluteSmoothCornerShape(
                            cornerRadius = (28f * scrollFraction).dp,
                            smoothnessAsPercent = 60
                        )
                    )
            ) {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ModeSwitch(
                            currentMode = viewModel.homeMode,
                            onModeChange = onModeChange,
                        )
                        HeaderStatusIndicator(
                            status = headerStatus,
                            modifier = Modifier.padding(start = 8.dp),
                            tint = appBarIconColorFaded,
                        )
                    }

                    Spacer(Modifier.weight(1f))

                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .clip(ShapeCache.smooth20)
                            .padding(horizontal = 4.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ExpressiveIconButton(
                                onClick = {
                                    showSurprise = !showSurprise
                                    if (!showSurprise) autoRotateEnabled = true
                                },
                                modifier = Modifier.size(40.dp),
                            ) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = "Surprise Me",
                                    tint = if (showSurprise) MaterialTheme.colorScheme.primary else appBarIconColorFaded,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            ExpressiveIconButton(
                                onClick = {
                                    onSyncPlayClick()
                                },
                                modifier = Modifier.size(40.dp),
                            ) {
                                Icon(
                                    Icons.Default.Group,
                                    contentDescription = "SyncPlay",
                                    tint = appBarIconColorFaded,
                                    modifier = Modifier.size(20.dp),
                                )
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
                                ExpressiveIconButton(
                                    onClick = {
                                        onDownloadsClick()
                                    },
                                    modifier = Modifier.size(40.dp),
                                ) {
                                    Icon(
                                        Icons.Default.Download,
                                        contentDescription = "Downloads",
                                        tint = appBarIconColorFaded,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                            ExpressiveIconButton(
                                onClick = {
                                    onSettingsClick()
                                },
                                modifier = Modifier.size(40.dp),
                            ) {
                                Icon(
                                    Icons.Default.Settings,
                                    contentDescription = "Settings",
                                    tint = appBarIconColorFaded,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Seerr request dialog for discover cards
    seerrRequestItem?.let { item ->
        LaunchedEffect(item.id) {
            viewModel.loadSeerrServiceDetails(item.mediaType)
            if (item.mediaType.equals("tv", ignoreCase = true)) {
                viewModel.loadTvSeasons(item.id)
            }
        }

        SeerrRequestDialog(
            item = item,
            radarrServers = radarrServers,
            sonarrServers = sonarrServers,
            seasons = if (item.mediaType.equals("tv", ignoreCase = true)) tvSeasons else emptyList(),
            isLoadingServices = isLoadingSeerrServices,
            isRequesting = requestResult?.isLoading == true,
            requestSuccess = requestResult?.success,
            requestError = requestResult?.error,
            onConfirm = { serverId, profileId, rootFolder, tags, seasons ->
                viewModel.requestSeerrMedia(item, seasons, serverId, profileId, rootFolder, tags)
            },
            onDismiss = {
                seerrRequestItem = null
                viewModel.clearRequestResult()
            },
        )
    }
}

@Composable
private fun AnimatedHeroHeader(
    featuredItem: MediaItem,
    getBackdropUrl: (String) -> String,
    height: androidx.compose.ui.unit.Dp,
    backgroundColor: Color,
    contentPadding: Dp = 16.dp,
    listState: LazyListState,
    onItemClick: (String) -> Unit,
    onDetailsClick: ((String) -> Unit)? = null,
    onFocusChange: (Boolean) -> Unit = {},
) {
    val parallaxOffset by remember(listState) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex == 0) {
                listState.firstVisibleItemScrollOffset.toFloat() * 0.45f
            } else 0f
        }
    }

    AnimatedContent(
        targetState = featuredItem,
        transitionSpec = {
            fadeIn(
                animationSpec = tween(520, easing = AlphaEasing),
            ) + scaleIn(
                initialScale = 1.02f,
                animationSpec = tween(700, easing = FancyTransitionEasing),
            ) + slideInHorizontally(
                initialOffsetX = { it / 20 },
                animationSpec = tween(600, easing = FancyTransitionEasing),
            ) togetherWith fadeOut(
                animationSpec = tween(320, easing = AlphaEasing),
            ) + scaleOut(
                targetScale = 0.985f,
                animationSpec = tween(320, easing = PointToPointEasing),
            ) + slideOutHorizontally(
                targetOffsetX = { -it / 36 },
                animationSpec = tween(320, easing = FancyTransitionEasing),
            )
        },
        label = "heroRotation",
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomStart = 0.dp, bottomEnd = 0.dp)),
    ) { currentFeatured ->
        HeroHeader(
            item = currentFeatured,
            backdropUrl = getBackdropUrl(currentFeatured.id),
            height = height,
            backgroundColor = backgroundColor,
            contentPadding = contentPadding,
            parallaxOffset = parallaxOffset,
            onClick = { onItemClick(currentFeatured.id) },
            onDetailsClick = onDetailsClick?.let { { it(currentFeatured.id) } },
            onFocusChange = onFocusChange,
        )
    }
}

@Composable
private fun HeroHeader(
    item: MediaItem,
    backdropUrl: String,
    height: androidx.compose.ui.unit.Dp,
    backgroundColor: Color,
    contentPadding: Dp = 16.dp,
    parallaxOffset: Float = 0f,
    onClick: () -> Unit,
    onDetailsClick: (() -> Unit)? = null,
    onFocusChange: (Boolean) -> Unit = {},
) {
    val isTv = LocalTvMode.current
    val adaptiveInfo = LocalAdaptiveInfo.current
    
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = tween(150, easing = FastInvokeEasing),
        label = "heroPress",
    )
    val playInteractionSource = remember { MutableInteractionSource() }
    val isPlayPressed by playInteractionSource.collectIsPressedAsState()
    val playScale by animateFloatAsState(
        targetValue = if (isPlayPressed) 0.95f else 1f,
        animationSpec = tween(150, easing = PointToPointEasing),
        label = "playButtonScale",
    )
    val detailsInteractionSource = remember { MutableInteractionSource() }
    val isDetailsPressed by detailsInteractionSource.collectIsPressedAsState()
    val detailsScale by animateFloatAsState(
        targetValue = if (isDetailsPressed) 0.95f else 1f,
        animationSpec = tween(150, easing = PointToPointEasing),
        label = "detailsButtonScale",
    )

    val heroTvFocusState = rememberTvFocusState()

    val heroPlayFocusRequester = remember { FocusRequester() }
    val heroDetailsFocusRequester = remember { FocusRequester() }

    LaunchedEffect(isTv, item.id) {
        if (isTv) {
            heroPlayFocusRequester.requestFocus()
        }
    }

    val breathTransition = rememberInfiniteTransition(label = "hero_breath")
    val breathScale by breathTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = FancyTransitionEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath"
    )

    val playPulseScale by rememberInfiniteTransition(label = "play_pulse").animateFloat(
        initialValue = 1.0f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = AlphaEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "playPulseScale"
    )
    val playPulseAlpha by rememberInfiniteTransition(label = "play_pulse_alpha").animateFloat(
        initialValue = 0.45f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = AlphaEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "playPulseAlpha"
    )

    val ratingPulse by rememberInfiniteTransition(label = "rating_pulse").animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FancyTransitionEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ratingPulse"
    )

    // Expressive Asymmetrical mask for compact mobile portrait layout
    val heroShape = if (!isTv && adaptiveInfo.windowSizeClass == WindowSizeClass.Compact) {
        AbsoluteSmoothCornerShape(
            cornerRadiusTL = 0.dp,
            cornerRadiusTR = 0.dp,
            cornerRadiusBL = 36.dp,
            cornerRadiusBR = 14.dp,
            smoothnessAsPercentTL = 60,
            smoothnessAsPercentTR = 60,
            smoothnessAsPercentBL = 60,
            smoothnessAsPercentBR = 60,
        )
    } else {
        RoundedCornerShape(0.dp)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(heroShape)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .then(
                if (!isTv) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick,
                    )
                } else Modifier
            )
    ) {
        MediaImage(
            url = backdropUrl,
            contentDescription = item.name,
            blurHash = item.blurHashes.backdrop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = parallaxOffset
                    scaleX = breathScale
                    scaleY = breathScale
                },
            contentScale = ContentScale.Crop,
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.45f),
                            Color.Transparent,
                            backgroundColor.copy(alpha = 0.3f),
                            backgroundColor.copy(alpha = 0.85f),
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
                .padding(horizontal = contentPadding)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-1.5).sp,
                    shadow = androidx.compose.ui.graphics.Shadow(
                        color = Color.Black.copy(alpha = 0.55f),
                        offset = androidx.compose.ui.geometry.Offset(2f, 4f),
                        blurRadius = 8f
                    )
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item.year?.let {
                    Box(
                        modifier = Modifier
                            .clip(ShapeCache.smooth8)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)), ShapeCache.smooth8)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = it.toString(),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                        )
                    }
                }
                item.runTimeTicks?.let { ticks ->
                    val minutes = ticks / 600_000_000
                    Box(
                        modifier = Modifier
                            .clip(ShapeCache.smooth8)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)), ShapeCache.smooth8)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "${minutes}m",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                        )
                    }
                }
                item.officialRating?.let {
                    Box(
                        modifier = Modifier
                            .clip(ShapeCache.smooth8)
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
                            .border(
                                BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                ),
                                ShapeCache.smooth8
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            ),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                item.communityRating?.let { rating ->
                    Box(
                        modifier = Modifier
                            .clip(ShapeCache.smooth8)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)), ShapeCache.smooth8)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Favorite,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(14.dp)
                                    .graphicsLayer {
                                        scaleX = ratingPulse
                                        scaleY = ratingPulse
                                    },
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = String.format("%.1f", rating),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                            )
                        }
                    }
                }
            }

            if (item.genres.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    item.genres.forEach { genre ->
                        Box(
                            modifier = Modifier
                                .clip(ShapeCache.smooth12)
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                                .border(
                                    BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                                    ShapeCache.smooth12
                                )
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = genre,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                            )
                        }
                    }
                }
            }

            item.overview?.let { overview ->
                Spacer(Modifier.height(10.dp))
                Text(
                    text = overview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(20.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val hasProgress = item.playbackPositionTicks != null && item.playbackPositionTicks!! > 0

                Box(
                    modifier = Modifier
                        .height(48.dp)
                        .then(heroTvFocusState.focusModifier)
                        .tvFocusIndicator(heroTvFocusState, ShapeCache.smoothPill)
                        .focusRequester(heroPlayFocusRequester)
                        .onFocusChanged { focusState ->
                            onFocusChange(focusState.isFocused || focusState.hasFocus)
                        }
                        .graphicsLayer { scaleX = playScale; scaleY = playScale }
                ) {
                    if (!isTv) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .aspectRatio(2.4f)
                                .align(Alignment.Center)
                                .graphicsLayer {
                                    scaleX = playPulseScale
                                    scaleY = playPulseScale
                                    alpha = playPulseAlpha
                                }
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), ShapeCache.smoothPill)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .clip(ShapeCache.smoothPill)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                    )
                                )
                            )
                            .clickable(
                                interactionSource = playInteractionSource,
                                indication = null,
                                onClick = onClick,
                            )
                            .padding(horizontal = 28.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
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
                }

                Box(
                    modifier = Modifier
                        .height(48.dp)
                        .tvFocusIndicator(heroTvFocusState, ShapeCache.smoothPill)
                        .focusRequester(heroDetailsFocusRequester)
                        .onFocusChanged { focusState ->
                            onFocusChange(focusState.isFocused || focusState.hasFocus)
                        }
                        .graphicsLayer { scaleX = detailsScale; scaleY = detailsScale }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .clip(ShapeCache.smoothPill)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                            .border(
                                BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                                ShapeCache.smoothPill
                            )
                            .clickable(
                                interactionSource = detailsInteractionSource,
                                indication = null,
                                onClick = onDetailsClick ?: onClick,
                            )
                            .padding(horizontal = 28.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "Details",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ExpressiveIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.82f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "expressive_btn_scale"
    )
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
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
    val isTv = LocalTvMode.current
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
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = contentPad, vertical = 8.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = contentPad),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            modifier = Modifier.tvFocusRestorer(),
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
    onPlayClick: (() -> Unit)? = null,
    cardWidth: androidx.compose.ui.unit.Dp,
) {
    val isTv = LocalTvMode.current
    val tvFocusState = rememberTvFocusState()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val baseScale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = tween(150, easing = PointToPointEasing),
        label = "wideCardScale",
    )
    val scale by animateFloatAsState(
        targetValue = baseScale * tvFocusState.scale,
        animationSpec = tween(150, easing = PointToPointEasing),
        label = "wideCardCombinedScale",
    )
    val elevation by animateFloatAsState(
        targetValue = when {
            isPressed -> 12f
            tvFocusState.isFocused -> 16f
            else -> 4f
        },
        animationSpec = tween(200, easing = FancyTransitionEasing),
        label = "wideCardElevation",
    )
    val brightnessOverlay by animateFloatAsState(
        targetValue = if (isPressed) 0.08f else 0f,
        animationSpec = tween(150, easing = AlphaEasing),
        label = "wideCardBrightness",
    )

    val dominantColor = rememberDominantColor(backdropUrl.ifBlank { imageUrl })
    val hasProgress = item.playbackPositionTicks != null && item.runTimeTicks != null && item.runTimeTicks!! > 0
    val progressPercent = if (hasProgress) {
        (item.playbackPositionTicks!!.toFloat() / item.runTimeTicks!!.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val playButtonSize = if (isTv) 44.dp else 36.dp

    val imageModifier = Modifier
        .fillMaxWidth()
        .aspectRatio(16f / 9f)

    Column(modifier = Modifier.width(cardWidth)) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .then(tvFocusState.focusModifier)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    shadowElevation = elevation.dp.toPx()
                }
                .tvFocusIndicator(tvFocusState, ShapeCache.smooth12)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ),
            shape = ShapeCache.smooth12,
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Box {
                MediaImage(
                    url = backdropUrl,
                    fallbackUrls = listOf(imageUrl).filter { it.isNotBlank() },
                    contentDescription = item.name,
                    blurHash = item.blurHashes.backdrop,
                    modifier = imageModifier,
                    contentScale = ContentScale.Crop,
                    crossfade = false,
                )

                if (brightnessOverlay > 0.01f) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = brightnessOverlay))
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

                if (item.communityRating != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .background(
                                Color.Black.copy(alpha = 0.7f),
                                ShapeCache.smooth4,
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = "★",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFFFC107),
                            )
                            Text(
                                text = "%.1f".format(item.communityRating),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }

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
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.seriesName != null) {
                Text(
                    text = item.seriesName!!,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (item.year != null) {
                        Text(
                            text = item.year.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        )
                    }
                    val isSeries = item.mediaType == MediaType.SERIES
                    val hasValidDuration = item.runTimeTicks != null && item.runTimeTicks!! > 0 && !isSeries
                    val hasWatchProgress = item.playbackPositionTicks != null && item.playbackPositionTicks!! > 0 && !item.isPlayed
                    val remainingTime = if (hasWatchProgress && hasValidDuration) {
                        formatRemainingTimeFromTicks(item.runTimeTicks!!, item.playbackPositionTicks!!)
                    } else null
                    val totalTime = if (hasValidDuration && !hasWatchProgress) {
                        formatDurationFromTicks(item.runTimeTicks!!)
                    } else null
                    
                    val timeText = remainingTime ?: totalTime
                    if (timeText != null) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        )
                        Text(
                            text = if (remainingTime != null) "$timeText left" else timeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (remainingTime != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        )
                    }
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
    onPlayClick: ((MediaItem) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    val cardWidth = adaptiveInfo.rowCardWidth(isTv)
    val contentPad = adaptiveInfo.contentPadding(isTv)
    val spacing = adaptiveInfo.itemSpacing(isTv)

    Column(modifier = modifier) {
        Text(
            text = title,
            style = if (isTv) MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold)
                   else MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = contentPad, vertical = 8.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = contentPad),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            modifier = Modifier.tvFocusRestorer(),
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
                )
            }
        }
    }
}
