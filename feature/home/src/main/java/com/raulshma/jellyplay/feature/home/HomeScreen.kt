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

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.carousel.HorizontalUncontainedCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.activity.compose.BackHandler
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
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
import com.raulshma.jellyplay.core.designsystem.theme.RatingColors
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.OfflineMode
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.model.DownloadStatus
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
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

@Composable
fun HomeScreen(
    onItemClick: (String) -> Unit,
    onPlayClick: (itemId: String, mediaSourceId: String?, startPosition: Long) -> Unit = { _, _, _ -> },
    onSettingsClick: () -> Unit = {},
    onSyncPlayClick: () -> Unit = {},
    onDownloadsClick: () -> Unit = {},
    onOfflineLibraryClick: () -> Unit = {},
    onSeerrItemClick: (tmdbId: Int, mediaType: String) -> Unit = { _, _ -> },
    homeMode: HomeMode = HomeMode.VIDEO,
    onModeChange: (HomeMode) -> Unit = {},
    musicContent: @Composable () -> Unit = {},
    onSearchItemClick: (String) -> Unit = {},
    onSearchSeerrClick: (Int, String) -> Unit = { _, _ -> },
    viewModel: HomeViewModel = hiltViewModel(),
) {
    if (viewModel.homeMode == HomeMode.MUSIC) {
        musicContent()
        return
    }

    val sections = viewModel.sections
    val isLoading = viewModel.isLoading
    val isRefreshing = viewModel.isRefreshing
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
            { currentViewModel.onRefresh() }
        }
        val kidsOnItemClick = remember {
            { id: String -> currentOnItemClick(id) }
        }
        KidsHomeScreen(
            sections = sections,
            favorites = viewModel.favorites,
            isLoading = isLoading,
            isRefreshing = isRefreshing,
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
            ?: if (isLightTheme) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.background
        val targetBackgroundColor = if (isLightTheme) {
            MaterialTheme.colorScheme.background
        } else {
            lerp(baseOverlayColor, Color.Black, 0.65f)
        }
        val backgroundColor by animateColorAsState(
            targetValue = targetBackgroundColor,
            animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
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

        var isFabExpanded by remember { mutableStateOf(false) }
        val focusManager = LocalFocusManager.current
        val isSearchFocused by remember { derivedStateOf { viewModel.searchQuery.isNotBlank() } }

        BackHandler(enabled = isFabExpanded || isSearchFocused) {
            if (isFabExpanded) {
                isFabExpanded = false
            } else if (viewModel.searchQuery.isNotBlank()) {
                viewModel.clearSearch()
                focusManager.clearFocus()
            }
        }

        @OptIn(ExperimentalMaterial3Api::class)
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.onRefresh() },
            modifier = Modifier.fillMaxSize(),
        ) {
        Box(modifier = Modifier.fillMaxSize().background(backgroundColor)) {
            when {
                error != null && sections.isEmpty() && viewModel.offlineMode == OfflineMode.ONLINE -> {
                    val contentPad = adaptiveInfo.contentPadding(isTv)
                    ErrorScreen(
                        message = error!!,
                        onRetry = { viewModel.refresh() },
                        modifier = Modifier.padding(horizontal = contentPad)
                    )
                }
                viewModel.offlineMode != OfflineMode.ONLINE -> {
                    OfflineHomeContent(
                        offlineLibrary = viewModel.offlineLibrary,
                        onItemClick = onOfflineLibraryClick,
                        contentPadding = contentPad,
                        backgroundColor = backgroundColor,
                        onGoOnline = { viewModel.toggleOfflineMode() },
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
                                    animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
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

                            if (viewModel.offlineLibrary.isNotEmpty()) {
                                item(key = "downloaded_header") {
                                    Text(
                                        text = "Downloaded",
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

                                item(key = "downloaded_row") {
                                    LazyRow(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(backgroundColor)
                                            .padding(horizontal = contentPad, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        items(
                                            count = viewModel.offlineLibrary.size,
                                            key = { index -> "offline_${viewModel.offlineLibrary[index].id}" },
                                        ) { index ->
                                            val offlineItem = viewModel.offlineLibrary[index]
                                            Column(
                                                modifier = Modifier
                                                    .width(120.dp)
                                                    .clickable { onOfflineLibraryClick() },
                                            ) {
                                                val posterModifier = Modifier
                                                    .fillMaxWidth()
                                                    .aspectRatio(2f / 3f)
                                                    .clip(RoundedCornerShape(8.dp))

                                                if (!offlineItem.posterPath.isNullOrBlank()) {
                                                    MediaImage(
                                                        url = offlineItem.posterPath!!,
                                                        contentDescription = offlineItem.name,
                                                        blurHash = offlineItem.blurHashPrimary,
                                                        modifier = posterModifier,
                                                        contentScale = ContentScale.Crop,
                                                    )
                                                } else {
                                                    Box(
                                                        modifier = posterModifier.background(MaterialTheme.colorScheme.surfaceVariant),
                                                        contentAlignment = Alignment.Center,
                                                    ) {
                                                        Text(
                                                            text = offlineItem.name.take(2).uppercase(),
                                                            style = MaterialTheme.typography.titleMedium,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        )
                                                    }
                                                }

                                                Text(
                                                    text = offlineItem.name,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.padding(top = 4.dp),
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

            val borderAlpha = 0.08f + (0.04f * scrollFraction)
            val appBarIconColor = lerp(Color.White, MaterialTheme.colorScheme.onSurface, scrollFraction)
            val appBarIconColorFaded = appBarIconColor.copy(alpha = 0.9f)
            val dockScale = 1f - (0.04f * scrollFraction)
            val dockCornerRadius = 16f + (12f * scrollFraction)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(
                        horizontal = (4f + 12f * scrollFraction).dp,
                        vertical = (4f + 4f * scrollFraction).dp
                    )
                    .graphicsLayer {
                        scaleX = dockScale
                        scaleY = dockScale
                    }
                    .clip(
                        AbsoluteSmoothCornerShape(
                            cornerRadius = dockCornerRadius.dp,
                            smoothnessAsPercent = 60
                        )
                    )
                    .background(
                        lerp(
                            Color.Black.copy(alpha = 0.3f),
                            MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.88f),
                            scrollFraction
                        )
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
                            cornerRadius = dockCornerRadius.dp,
                            smoothnessAsPercent = 60
                        )
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ModeSwitch(
                        currentMode = viewModel.homeMode,
                        onModeChange = onModeChange,
                    )
                    if (viewModel.offlineMode != OfflineMode.ONLINE) {
                        IconButton(
                            onClick = { viewModel.toggleOfflineMode() },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                Tabler.Outline.Download,
                                contentDescription = "Go online",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    HeaderStatusIndicator(
                        status = headerStatus,
                        modifier = Modifier.padding(start = 8.dp),
                        tint = appBarIconColorFaded,
                    )

                    @OptIn(ExperimentalMaterial3Api::class)
                    val searchTextFieldColors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = appBarIconColor,
                    )
                    TextField(
                        value = viewModel.searchQuery,
                        onValueChange = { viewModel.updateSearchQuery(it) },
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp)
                            .height(48.dp),
                        placeholder = {
                            Text(
                                "Search movies, shows, music...",
                                color = appBarIconColor.copy(alpha = 0.5f),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        },
                        colors = searchTextFieldColors,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = appBarIconColor,
                        ),
                        singleLine = true,
                    )

                    if (viewModel.searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                viewModel.clearSearch()
                                focusManager.clearFocus()
                            },
                            shapes = androidx.compose.material3.IconButtonDefaults.shapes(),
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                Tabler.Outline.X,
                                contentDescription = "Clear search",
                                tint = appBarIconColorFaded,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }

                if (viewModel.searchQuery.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 64.dp)
                            .heightIn(max = 400.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.95f),
                                ShapeCache.smooth28,
                            )
                            .clip(ShapeCache.smooth28),
                    ) {
                        HomeSearchResultsOverlay(
                            jellyfinResults = viewModel.searchJellyfinResults,
                            seerrResults = viewModel.searchSeerrResults,
                            isSearching = viewModel.isSearching,
                            getImageUrl = { viewModel.getImageUrl(it) },
                            onJellyfinClick = { item ->
                                viewModel.clearSearch()
                                focusManager.clearFocus()
                                onItemClick(item.id)
                            },
                            onSeerrClick = { item ->
                                viewModel.clearSearch()
                                focusManager.clearFocus()
                                onSearchSeerrClick(item.id, item.mediaType)
                            },
                        )
                    }
                }
            }

            FloatingActionButtonMenu(
                expanded = isFabExpanded,
                button = {
                    ToggleFloatingActionButton(
                        checked = isFabExpanded,
                        onCheckedChange = { isFabExpanded = it },
                        containerColor = ToggleFloatingActionButtonDefaults.containerColor(
                            initialColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            finalColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    ) {
                        Icon(
                            if (isFabExpanded) Tabler.Outline.X else Tabler.Outline.DotsVertical,
                            contentDescription = if (isFabExpanded) "Close menu" else "More options",
                        )
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = 8.dp,
                        bottom = 8.dp,
                    ),
            ) {
                FloatingActionButtonMenuItem(
                    onClick = {
                        isFabExpanded = false
                        showSurprise = !showSurprise
                        if (!showSurprise) autoRotateEnabled = true
                    },
                    text = { Text("Surprise Me") },
                    icon = {
                        Icon(
                            Tabler.Outline.Wand,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                )
                FloatingActionButtonMenuItem(
                    onClick = {
                        isFabExpanded = false
                        onSyncPlayClick()
                    },
                    text = { Text("SyncPlay") },
                    icon = {
                        Icon(
                            Tabler.Outline.Users,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                )
                FloatingActionButtonMenuItem(
                    onClick = {
                        isFabExpanded = false
                        onDownloadsClick()
                    },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Downloads")
                            if (activeDownloadCount > 0) {
                                Badge(
                                    modifier = Modifier.padding(start = 6.dp),
                                    containerColor = MaterialTheme.colorScheme.primary,
                                ) {
                                    Text(
                                        activeDownloadCount.toString(),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                    )
                                }
                            }
                        }
                    },
                    icon = {
                        Icon(
                            Tabler.Outline.Download,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                )
                FloatingActionButtonMenuItem(
                    onClick = {
                        isFabExpanded = false
                        viewModel.toggleOfflineMode()
                    },
                    text = {
                        Text(if (viewModel.offlineMode != OfflineMode.ONLINE) "Go Online" else "Go Offline")
                    },
                    icon = {
                        Icon(
                            if (viewModel.offlineMode != OfflineMode.ONLINE) Tabler.Outline.Wifi else Tabler.Outline.WifiOff,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    containerColor = if (viewModel.offlineMode != OfflineMode.ONLINE) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
                )
                FloatingActionButtonMenuItem(
                    onClick = {
                        isFabExpanded = false
                        onSettingsClick()
                    },
                    text = { Text("Settings") },
                    icon = {
                        Icon(
                            Tabler.Outline.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                )
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

    val slowEffects = MaterialTheme.motionScheme.slowEffectsSpec<Float>()
    val defaultEffects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    AnimatedContent(
        targetState = featuredItem,
        transitionSpec = {
            fadeIn(
                animationSpec = slowEffects,
            ) + scaleIn(
                initialScale = 1.02f,
                animationSpec = slowEffects,
            ) togetherWith fadeOut(
                animationSpec = defaultEffects,
            ) + scaleOut(
                targetScale = 0.985f,
                animationSpec = defaultEffects,
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
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "heroPress",
    )
    val playInteractionSource = remember { MutableInteractionSource() }
    val isPlayPressed by playInteractionSource.collectIsPressedAsState()
    val playScale by animateFloatAsState(
        targetValue = if (isPlayPressed) 0.95f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "playButtonScale",
    )
    val detailsInteractionSource = remember { MutableInteractionSource() }
    val isDetailsPressed by detailsInteractionSource.collectIsPressedAsState()
    val detailsScale by animateFloatAsState(
        targetValue = if (isDetailsPressed) 0.95f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
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
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
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
                                Tabler.Outline.Heart,
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
                                Tabler.Outline.PlayerPlay,
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
                                BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeSearchResultsOverlay(
    jellyfinResults: List<MediaItem>,
    seerrResults: List<SeerrSearchItem>,
    isSearching: Boolean,
    getImageUrl: (String) -> String,
    onJellyfinClick: (MediaItem) -> Unit,
    onSeerrClick: (SeerrSearchItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val totalItems = jellyfinResults.size + seerrResults.size
    val hasAnyResults = totalItems > 0

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 420.dp),
    ) {
            if (isSearching && !hasAnyResults) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            } else if (!hasAnyResults && !isSearching) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "No results found",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    if (jellyfinResults.isNotEmpty()) {
                        item(contentType = "libraryHeader") {
                            Text(
                                text = "Library",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.8.sp,
                                ),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                        items(
                            count = jellyfinResults.size,
                            key = { index -> "jf-${jellyfinResults[index].id}" },
                            contentType = { "libraryItem" },
                        ) { index ->
                            val item = jellyfinResults[index]
                            SearchItemRow(
                                title = item.name,
                                subtitle = buildString {
                                    item.year?.let { append(it) }
                                    if (item.year != null && item.mediaType != null) append(" · ")
                                    when (item.mediaType) {
                                        MediaType.MOVIE -> append("Movie")
                                        MediaType.SERIES -> append("TV Show")
                                        MediaType.AUDIO, MediaType.MUSIC -> append("Music")
                                        else -> item.mediaType?.name?.lowercase()?.replaceFirstChar { it.uppercase() }?.let { append(it) }
                                    }
                                },
                                imageUrl = getImageUrl(item.id),
                                onClick = { onJellyfinClick(item) },
                                index = index,
                            )
                        }
                    }
                    if (seerrResults.isNotEmpty()) {
                        if (jellyfinResults.isNotEmpty()) {
                            item(contentType = "divider") {
                                HorizontalDivider(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                                )
                            }
                        }
                        item(contentType = "seerrHeader") {
                            Text(
                                text = "Request via Seerr",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.8.sp,
                                ),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                        items(
                            count = seerrResults.size,
                            key = { index -> "sr-${seerrResults[index].id}" },
                            contentType = { "seerrItem" },
                        ) { index ->
                            val item = seerrResults[index]
                            SearchItemRow(
                                title = item.displayName,
                                subtitle = buildString {
                                    item.year?.let { append(it) }
                                    val typeLabel = when {
                                        item.mediaType.equals("movie", ignoreCase = true) -> "Movie"
                                        item.mediaType.equals("tv", ignoreCase = true) -> "TV Show"
                                        else -> item.mediaType
                                    }
                                    if (item.year != null) append(" · ")
                                    append(typeLabel)
                                    item.voteAverage?.let { rating ->
                                        if (rating > 0) {
                                            append(" · ★ ")
                                            append(String.format("%.1f", rating))
                                        }
                                    }
                                },
                                imageUrl = item.posterUrl ?: "",
                                onClick = { onSeerrClick(item) },
                                index = index + jellyfinResults.size,
                            )
                        }
                    }
                    if (isSearching) {
                        item(contentType = "loadingIndicator") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                )
                            }
                        }
                    }
                }
            }
        }
}

@Composable
private fun SearchItemRow(
    title: String,
    subtitle: String,
    imageUrl: String,
    onClick: () -> Unit,
    index: Int,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "searchItemScale",
    )

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val animationProgress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "searchItemAnim",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = animationProgress
                translationY = (1f - animationProgress) * 8f
            }
            .clip(ShapeCache.smooth12)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(ShapeCache.smooth10)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
            contentAlignment = Alignment.Center,
        ) {
            if (imageUrl.isNotBlank()) {
                MediaImage(
                    url = imageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(ShapeCache.smooth10),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    Tabler.Outline.Search,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
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
        HorizontalUncontainedCarousel(
            state = rememberCarouselState { items.size },
            itemWidth = cardWidth,
            itemSpacing = spacing,
            contentPadding = PaddingValues(horizontal = contentPad),
            modifier = Modifier.tvFocusRestorer(),
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
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "wideCardScale",
    )
    val scale by animateFloatAsState(
        targetValue = baseScale * tvFocusState.scale,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "wideCardCombinedScale",
    )
    val elevation by animateFloatAsState(
        targetValue = when {
            isPressed -> 12f
            tvFocusState.isFocused -> 16f
            else -> 4f
        },
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "wideCardElevation",
    )
    val brightnessOverlay by animateFloatAsState(
        targetValue = if (isPressed) 0.08f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
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
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
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
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
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
                                color = RatingColors.star,
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

@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
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
        HorizontalUncontainedCarousel(
            state = rememberCarouselState { items.size },
            itemWidth = cardWidth,
            itemSpacing = spacing,
            contentPadding = PaddingValues(horizontal = contentPad),
            modifier = Modifier.tvFocusRestorer(),
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OfflineHomeContent(
    offlineLibrary: List<OfflineMediaItem>,
    onItemClick: () -> Unit,
    contentPadding: Dp,
    backgroundColor: Color,
    onGoOnline: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor),
        contentPadding = PaddingValues(
            top = 120.dp,
            bottom = 120.dp,
            start = contentPadding,
            end = contentPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (offlineLibrary.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Tabler.Outline.Download,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "No downloads yet",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        )
                        Text(
                            "Download media while online to access it offline",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        )
                    }
                }
            }
        } else {
            item {
                Text(
                    text = "Your Downloads",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            items(
                count = offlineLibrary.size,
                key = { index -> "offline_${offlineLibrary[index].id}" },
            ) { index ->
                val offlineItem = offlineLibrary[index]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .clickable { onItemClick() }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val posterModifier = Modifier
                        .width(60.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(8.dp))

                    if (!offlineItem.posterPath.isNullOrBlank()) {
                        MediaImage(
                            url = offlineItem.posterPath!!,
                            contentDescription = offlineItem.name,
                            blurHash = offlineItem.blurHashPrimary,
                            modifier = posterModifier,
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Box(
                            modifier = posterModifier.background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = offlineItem.name.take(2).uppercase(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp),
                    ) {
                        Text(
                            text = offlineItem.name,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!offlineItem.seriesName.isNullOrBlank()) {
                            Text(
                                text = offlineItem.seriesName!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp),
                        ) {
                            if (offlineItem.year != null) {
                                Text(
                                    text = offlineItem.year.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                )
                            }
                            if (offlineItem.communityRating != null && offlineItem.communityRating!! > 0) {
                                if (offlineItem.year != null) {
                                    Text(
                                        text = " · ",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                    )
                                }
                                Text(
                                    text = "★ ${String.format("%.1f", offlineItem.communityRating)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                )
                            }
                            if (!offlineItem.officialRating.isNullOrBlank()) {
                                Text(
                                    text = " · ${offlineItem.officialRating}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                )
                            }
                        }
                        if (offlineItem.genres.isNotEmpty()) {
                            Text(
                                text = offlineItem.genres.take(3).joinToString(", "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (offlineItem.downloadStatus == DownloadStatus.COMPLETED) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 4.dp),
                            ) {
                                Icon(
                                    Tabler.Outline.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "Downloaded",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        } else if (offlineItem.downloadStatus == DownloadStatus.DOWNLOADING) {
                            val progress = if (offlineItem.totalSizeBytes > 0) {
                                offlineItem.downloadedBytes.toFloat() / offlineItem.totalSizeBytes
                            } else 0f
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
