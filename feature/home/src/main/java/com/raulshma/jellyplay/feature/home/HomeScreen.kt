package com.raulshma.jellyplay.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import kotlinx.coroutines.launch
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import com.raulshma.jellyplay.core.designsystem.theme.ArtworkThemeWrapper
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.OfflineMode
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.model.seerr.DiscoverSectionType
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.ui.adaptive.AdaptiveHeroHeight
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.WindowSizeClass
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.LocalNavigationBarColor
import com.raulshma.jellyplay.core.ui.components.LocalNetworkStatus
import com.raulshma.jellyplay.core.ui.components.LocalSeerrCardLoadingState
import com.raulshma.jellyplay.core.ui.components.LocalSeerrPrefetch
import com.raulshma.jellyplay.core.ui.components.SeerrMediaCard
import com.raulshma.jellyplay.core.ui.components.SeerrRequestDialog
import com.raulshma.jellyplay.core.ui.components.rememberSeerrCardLoadingState
import com.raulshma.jellyplay.core.ui.components.resolveHeaderStatus
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.isTv
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

private val COMPACT_DISCOVER_PATTERN = listOf(3, 2, 3)
private val EXPANDED_DISCOVER_PATTERN = listOf(5, 4, 6, 5)

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
    onNewsletterClick: () -> Unit = {},
    onServerManagementClick: () -> Unit = {},
    onUserManagementClick: () -> Unit = {},
    onSeerrSettingsClick: () -> Unit = {},
    onAdminDashboardClick: () -> Unit = {},
    onSetupWizardClick: () -> Unit = {},
    onFavoritesClick: () -> Unit = {},
    onAboutClick: () -> Unit = {},
    onWatchProgressHeatmapClick: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    MainHomeContent(
        state = state,
        viewModel = viewModel,
        onItemClick = onItemClick,
        onPlayClick = onPlayClick,
        onSettingsClick = onSettingsClick,
        onSyncPlayClick = onSyncPlayClick,
        onDownloadsClick = onDownloadsClick,
        onOfflineLibraryClick = onOfflineLibraryClick,
        onSeerrItemClick = onSeerrItemClick,
        onModeChange = onModeChange,
        onSearchItemClick = onSearchItemClick,
        onSearchSeerrClick = onSearchSeerrClick,
        onNewsletterClick = onNewsletterClick,
        onServerManagementClick = onServerManagementClick,
        onUserManagementClick = onUserManagementClick,
        onSeerrSettingsClick = onSeerrSettingsClick,
        onAdminDashboardClick = onAdminDashboardClick,
        onSetupWizardClick = onSetupWizardClick,
        onFavoritesClick = onFavoritesClick,
        onAboutClick = onAboutClick,
        onWatchProgressHeatmapClick = onWatchProgressHeatmapClick,
        musicContent = musicContent,
    )
}

@Composable
private fun MainHomeContent(
    state: HomeUiState,
    viewModel: HomeViewModel,
    onItemClick: (String) -> Unit,
    onPlayClick: (String, String?, Long) -> Unit,
    onSettingsClick: () -> Unit,
    onSyncPlayClick: () -> Unit,
    onDownloadsClick: () -> Unit,
    onOfflineLibraryClick: () -> Unit,
    onSeerrItemClick: (Int, String) -> Unit,
    onModeChange: (HomeMode) -> Unit,
    onSearchItemClick: (String) -> Unit,
    onSearchSeerrClick: (Int, String) -> Unit,
    onNewsletterClick: () -> Unit = {},
    onServerManagementClick: () -> Unit,
    onUserManagementClick: () -> Unit,
    onSeerrSettingsClick: () -> Unit,
    onAdminDashboardClick: () -> Unit,
    onSetupWizardClick: () -> Unit,
    onFavoritesClick: () -> Unit,
    onAboutClick: () -> Unit,
    onWatchProgressHeatmapClick: () -> Unit,
    musicContent: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val focusManager = LocalFocusManager.current

    val networkStatus by LocalNetworkStatus.current.collectAsStateWithLifecycle()
    val headerStatus = remember(state.isLoading, state.error != null, networkStatus) {
        resolveHeaderStatus(
            isLoading = state.isLoading,
            hasError = state.error != null,
            networkStatus = networkStatus,
        )
    }

    val activeDownloadCount by viewModel.activeDownloadCount.collectAsStateWithLifecycle()
    val searchHistory by viewModel.searchHistory.collectAsStateWithLifecycle()

    val seerrCardLoadingState = rememberSeerrCardLoadingState()
    val seerrPrefetch: (Int, String, () -> Unit) -> Unit = remember(viewModel) {
        { tmdbId, mediaType, onDone ->
            viewModel.onEvent(HomeUiEvent.PrefetchSeerrDetails(tmdbId, mediaType, onDone))
        }
    }

    val featuredCandidates = remember(state.sections) {
        val latestItems = state.sections
            .filter { it.type == HomeSectionType.LATEST_MEDIA }
            .flatMap { section ->
                section.items
                    .filter { it.mediaType == MediaType.MOVIE || it.mediaType == MediaType.SERIES }
                    .take(3)
            }
        if (latestItems.isNotEmpty()) latestItems
        else state.sections.flatMap { it.items }
            .filter { it.mediaType == MediaType.MOVIE || it.mediaType == MediaType.SERIES }
            .ifEmpty { state.sections.flatMap { it.items } }
    }

    var showSurprise by remember { mutableStateOf(false) }
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

    val backdropUrl = remember(featuredItem?.id) { featuredItem?.let { viewModel.getBackdropUrl(it.id) } }

    val savedScrollPos = viewModel.getHomeScrollPosition()
    val listState = rememberSaveable(saver = LazyListState.Saver) {
        LazyListState(
            firstVisibleItemIndex = savedScrollPos.firstVisibleItemIndex,
            firstVisibleItemScrollOffset = savedScrollPos.firstVisibleItemScrollOffset,
        )
    }

    val headerHeight = remember(isTv, adaptiveInfo.isLandscape, adaptiveInfo.windowSizeClass) {
        when {
            isTv -> AdaptiveHeroHeight.Tv
            adaptiveInfo.isLandscape && adaptiveInfo.windowSizeClass != WindowSizeClass.Compact ->
                AdaptiveHeroHeight.LandscapeMedium
            else -> AdaptiveHeroHeight.PortraitCompact
        }
    }

    LaunchedEffect(state.sections) {
        if (state.sections.isNotEmpty()) {
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
    val artworkColors = com.raulshma.jellyplay.core.designsystem.theme.rememberArtworkColors(
        if (state.dynamicTheming && !backdropUrl.isNullOrBlank()) backdropUrl else null
    )
    val baseOverlayColor = artworkColors?.darkMuted
        ?: artworkColors?.dominant
        ?: MaterialTheme.colorScheme.background
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
    LaunchedEffect(backgroundColor) { navBarColor.value = backgroundColor }

    val transitionRangePx = remember(density) { with(density) { 140.dp.toPx() } }
    val scrollFraction by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset.toFloat() / transitionRangePx).coerceIn(0f, 1f)
        }
    }

    val baseIconColor = if (isLightTheme) MaterialTheme.colorScheme.onSurface else Color.White
    val appBarIconColor = lerp(baseIconColor, MaterialTheme.colorScheme.onSurface, scrollFraction)
    val appBarIconColorFaded = appBarIconColor.copy(alpha = 0.9f)
    val dockScale = 1f - (0.04f * scrollFraction)

    val contentPad = remember(adaptiveInfo, isTv) { adaptiveInfo.contentPadding(isTv) }

    val mediaImageUrlBuilder = remember { { item: com.raulshma.jellyplay.core.model.MediaItem -> viewModel.getImageUrl(item.id) } }
    val mediaBackdropUrlBuilder = remember { { item: com.raulshma.jellyplay.core.model.MediaItem -> viewModel.getBackdropUrl(item.id) } }
    val currentOnItemClick by rememberUpdatedState(onItemClick)
    val mediaOnItemClick = remember { { item: com.raulshma.jellyplay.core.model.MediaItem -> currentOnItemClick(item.id) } }
    val currentOnPlayClick by rememberUpdatedState(onPlayClick)
    val mediaOnPlayClick = remember { { item: com.raulshma.jellyplay.core.model.MediaItem ->
        currentOnPlayClick(item.id, null, item.playbackPositionTicks ?: 0L)
    } }

    val fallbackImageUrlBuilder = rememberFallbackUrls(viewModel)

    val discoverSectionOrder = remember {
        listOf(DiscoverSectionType.TRENDING, DiscoverSectionType.POPULAR_MOVIES, DiscoverSectionType.POPULAR_TV, DiscoverSectionType.UPCOMING_MOVIES, DiscoverSectionType.UPCOMING_TV)
    }
    val allDiscoverItems = remember(state.discoverSections) {
        discoverSectionOrder.flatMap { state.discoverSections[it] ?: emptyList() }.distinctBy { it.id }
    }
    val discoverRows = rememberDiscoverRows(allDiscoverItems)

    var isFabExpanded by remember { mutableStateOf(false) }
    var isSearchExpanded by remember { mutableStateOf(false) }
    val isSearchFocused by remember { derivedStateOf { state.searchState.query.isNotBlank() || isSearchExpanded } }

    val navOffsetPx = com.raulshma.jellyplay.core.ui.components.LocalFloatingNavOffset.current

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val isDrawerOpen = drawerState.isOpen

    BackHandler(enabled = isFabExpanded || isSearchFocused || isDrawerOpen) {
        if (isDrawerOpen) {
            scope.launch { drawerState.close() }
        } else if (isFabExpanded) {
            isFabExpanded = false
        } else if (isSearchFocused) {
            isSearchExpanded = false
            viewModel.onEvent(HomeUiEvent.ClearSearch)
            focusManager.clearFocus()
        }
    }

    ArtworkThemeWrapper(
        imageUrl = backdropUrl,
        dynamicTheming = state.dynamicTheming,
        darkTheme = !isLightTheme,
        oledMode = state.oledMode,
        colorStyle = state.colorStyle,
        accentColorSwatch = state.accentColorSwatch,
    ) {
        @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = @Composable {
                ModalDrawerSheet(
                drawerContainerColor = backgroundColor.copy(alpha = 0.98f),
                modifier = Modifier
                    .width(320.dp)
                    .fillMaxHeight(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val user = state.currentUser
                    if (user != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        shape = androidx.compose.foundation.shape.CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Tabler.Outline.User,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = "Welcome back,",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = user.name,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        HorizontalDivider(
                            modifier = Modifier.padding(bottom = 12.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }

                    Text(
                        text = "ACCOUNT",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                    )
                    NavigationDrawerItem(
                        icon = { Icon(Tabler.Outline.Server, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        label = { Text("Server Management") },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            onServerManagementClick()
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            unselectedContainerColor = Color.Transparent
                        )
                    )
                    NavigationDrawerItem(
                        icon = { Icon(Tabler.Outline.Users, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        label = { Text("Switch User") },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            onUserManagementClick()
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            unselectedContainerColor = Color.Transparent
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "ACTIVITY & INSIGHTS",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                    )
                    NavigationDrawerItem(
                        icon = { Icon(Tabler.Outline.Heart, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        label = { Text("Browse Favorites") },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            onFavoritesClick()
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            unselectedContainerColor = Color.Transparent
                        )
                    )
                    NavigationDrawerItem(
                        icon = { Icon(Tabler.Outline.ChartBar, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        label = { Text("Watch History Heatmap") },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            onWatchProgressHeatmapClick()
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            unselectedContainerColor = Color.Transparent
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "SYSTEM",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                    )
                    if (user?.isAdmin == true) {
                        NavigationDrawerItem(
                            icon = { Icon(Tabler.Outline.Shield, contentDescription = null, modifier = Modifier.size(20.dp)) },
                            label = { Text("Admin Dashboard") },
                            selected = false,
                            onClick = {
                                scope.launch { drawerState.close() }
                                onAdminDashboardClick()
                            },
                            colors = NavigationDrawerItemDefaults.colors(
                                unselectedContainerColor = Color.Transparent
                            )
                        )
                    }
                    NavigationDrawerItem(
                        icon = { Icon(Tabler.Outline.Puzzle, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        label = { Text("Seerr Integration") },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            onSeerrSettingsClick()
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            unselectedContainerColor = Color.Transparent
                        )
                    )
                    NavigationDrawerItem(
                        icon = { Icon(Tabler.Outline.Wand, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        label = { Text("Setup Wizard") },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            onSetupWizardClick()
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            unselectedContainerColor = Color.Transparent
                        )
                    )
                    NavigationDrawerItem(
                        icon = { Icon(Tabler.Outline.InfoCircle, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        label = { Text("About JellyPlay") },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            onAboutClick()
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            unselectedContainerColor = Color.Transparent
                        )
                    )
                }
            }
        }
    ) {
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.onEvent(HomeUiEvent.PullToRefresh) },
            modifier = Modifier.fillMaxSize(),
        ) {
        Box(modifier = Modifier.fillMaxSize().background(backgroundColor)) {
            when {
                state.error != null && state.sections.isEmpty() && state.offlineMode == OfflineMode.ONLINE -> {
                    ErrorScreen(
                        message = state.error!!,
                        onRetry = { viewModel.onEvent(HomeUiEvent.Refresh) },
                        modifier = Modifier.padding(horizontal = contentPad),
                    )
                }
                state.offlineMode != OfflineMode.ONLINE -> {
                    val filteredOfflineLibrary = remember(state.offlineLibrary, state.homeMode) {
                        if (state.homeMode == HomeMode.MUSIC) {
                            state.offlineLibrary.filter {
                                it.mediaType == MediaType.AUDIO ||
                                it.mediaType == MediaType.MUSIC ||
                                it.mediaType == MediaType.ALBUM ||
                                it.mediaType == MediaType.ARTIST
                            }
                        } else {
                            state.offlineLibrary.filter {
                                it.mediaType != MediaType.AUDIO &&
                                it.mediaType != MediaType.MUSIC &&
                                it.mediaType != MediaType.ALBUM &&
                                it.mediaType != MediaType.ARTIST
                            }
                        }
                    }
                    OfflineHomeContent(
                        offlineLibrary = filteredOfflineLibrary,
                        onItemClick = onOfflineLibraryClick,
                        contentPadding = contentPad,
                        backgroundColor = backgroundColor,
                        onGoOnline = { viewModel.onEvent(HomeUiEvent.ToggleOfflineMode) },
                    )
                }
                state.homeMode == HomeMode.MUSIC -> {
                    musicContent()
                }
                else -> {
                    HomeContentList(
                        isLoading = state.isLoading,
                        homeHeroEnabled = state.homeHeroEnabled,
                        newsletterBannerVisible = state.newsletterBannerVisible,
                        discoverEnabled = state.discoverEnabled,
                        offlineLibrary = state.offlineLibrary,
                        sections = state.sections,
                        featuredItem = featuredItem,
                        viewModel = viewModel,
                        listState = listState,
                        backgroundColor = backgroundColor,
                        contentPad = contentPad,
                        headerHeight = headerHeight,
                        isLightTheme = isLightTheme,
                        density = density,
                        mediaImageUrlBuilder = mediaImageUrlBuilder,
                        mediaBackdropUrlBuilder = mediaBackdropUrlBuilder,
                        mediaOnItemClick = mediaOnItemClick,
                        mediaOnPlayClick = mediaOnPlayClick,
                        fallbackImageUrlBuilder = fallbackImageUrlBuilder,
                        discoverRows = discoverRows,
                        allDiscoverItems = allDiscoverItems,
                        seerrCardLoadingState = seerrCardLoadingState,
                        seerrPrefetch = seerrPrefetch,
                        onSeerrItemClick = onSeerrItemClick,
                        onOfflineLibraryClick = onOfflineLibraryClick,
                        onItemClick = onItemClick,
                        onFocusChange = { focusInHero = it },
                        onSeerrRequest = { viewModel.onEvent(HomeUiEvent.SelectSeerrRequestItem(it)) },
                        onNewsletterClick = onNewsletterClick,
                    )
                }
            }

                HomeTopDock(
                    listState = listState,
                    transitionRangePx = transitionRangePx,
                    baseIconColor = baseIconColor,
                    isSearchFocused = isSearchFocused,
                    searchQuery = state.searchState.query,
                    offlineMode = state.offlineMode,
                    homeMode = state.homeMode,
                    headerStatus = headerStatus,
                    activeDownloadCount = activeDownloadCount,
                    onModeChange = onModeChange,
                    onSearchExpanded = { isSearchExpanded = it },
                    onSearchQueryChange = { viewModel.onEvent(HomeUiEvent.UpdateSearchQuery(it)) },
                    onClearSearch = {
                        isSearchExpanded = false
                        viewModel.onEvent(HomeUiEvent.ClearSearch)
                    },
                    onToggleOffline = { viewModel.onEvent(HomeUiEvent.ToggleOfflineMode) },
                    searchResultsContent = {
                        HomeSearchResultsOverlay(
                            jellyfinResults = state.searchState.jellyfinResults,
                            seerrResults = state.searchState.seerrResults,
                            isSearching = state.searchState.isSearching,
                            getImageUrl = { viewModel.getImageUrl(it) },
                            onJellyfinClick = { item ->
                                isSearchExpanded = false
                                viewModel.onEvent(HomeUiEvent.ClearSearch)
                                focusManager.clearFocus()
                                onItemClick(item.id)
                            },
                            onSeerrClick = { item ->
                                isSearchExpanded = false
                                viewModel.onEvent(HomeUiEvent.ClearSearch)
                                focusManager.clearFocus()
                                onSearchSeerrClick(item.id, item.mediaType)
                            },
                            searchHistory = searchHistory,
                            onHistoryClick = { query ->
                                viewModel.onEvent(HomeUiEvent.UpdateSearchQuery(query))
                            },
                            onDeleteHistoryItem = { id -> viewModel.deleteSearchHistoryItem(id) },
                            onClearHistory = { viewModel.clearSearchHistory() },
                        )
                    },
                )

                HomeFabMenu(
                    isExpanded = isFabExpanded,
                    onToggle = { isFabExpanded = it },
                    activeDownloadCount = activeDownloadCount,
                    offlineMode = state.offlineMode,
                    onSurpriseClick = {
                        showSurprise = !showSurprise
                        if (!showSurprise) autoRotateEnabled = true
                    },
                    onSyncPlayClick = onSyncPlayClick,
                    onDownloadsClick = onDownloadsClick,
                    onToggleOffline = { viewModel.onEvent(HomeUiEvent.ToggleOfflineMode) },
                    onSettingsClick = onSettingsClick,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 88.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
                        .offset {
                            val maxOffset = 88.dp.toPx()
                            val yOffset = (-navOffsetPx).coerceAtMost(maxOffset)
                            androidx.compose.ui.unit.IntOffset(x = 0, y = yOffset.toInt())
                        },
                )

                // Floating menu button at the top left, aligned horizontally with HomeTopDock
                Box(
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(
                            horizontal = (4f + 12f * scrollFraction).dp,
                            vertical = (4f + 4f * scrollFraction).dp
                        )
                        .align(Alignment.TopStart)
                ) {
                    androidx.compose.material3.Surface(
                        onClick = {
                            scope.launch { drawerState.open() }
                        },
                        shape = RoundedCornerShape((24f + 4f * scrollFraction).dp),
                        color = if (isSearchFocused) {
                            Color.Transparent
                        } else {
                            lerp(
                                Color.Transparent,
                                MaterialTheme.colorScheme.surfaceContainerHigh,
                                scrollFraction
                            )
                        },
                        modifier = Modifier
                            .graphicsLayer {
                                scaleX = dockScale
                                scaleY = dockScale
                                alpha = if (isSearchFocused) 0f else 1f
                                shadowElevation = if (scrollFraction > 0f) 8f * scrollFraction else 0f
                            }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Tabler.Outline.Menu2,
                                contentDescription = "Open Shortcuts Menu",
                                tint = appBarIconColorFaded,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
        }
    }
    }
    }

    state.seerrRequestState.requestItem?.let { item ->
        LaunchedEffect(item.id) {
            viewModel.onEvent(HomeUiEvent.LoadSeerrServiceDetails(item.mediaType))
            if (item.mediaType.equals("tv", ignoreCase = true)) {
                viewModel.onEvent(HomeUiEvent.LoadTvSeasons(item.id))
            }
        }

        SeerrRequestDialog(
            item = item,
            radarrServers = state.seerrRequestState.radarrServers,
            sonarrServers = state.seerrRequestState.sonarrServers,
            seasons = if (item.mediaType.equals("tv", ignoreCase = true)) state.seerrRequestState.tvSeasons else emptyList(),
            isLoadingServices = state.seerrRequestState.isLoadingServices,
            isRequesting = state.seerrRequestState.result?.isLoading == true,
            requestSuccess = state.seerrRequestState.result?.success,
            requestError = state.seerrRequestState.result?.error,
            onConfirm = { serverId, profileId, rootFolder, tags, seasons ->
                viewModel.onEvent(HomeUiEvent.RequestSeerrMedia(item, seasons, serverId, profileId, rootFolder, tags))
            },
            onDismiss = {
                viewModel.onEvent(HomeUiEvent.SelectSeerrRequestItem(null))
                viewModel.onEvent(HomeUiEvent.ClearRequestResult)
            },
        )
    }
}

@Composable
private fun HomeContentList(
    isLoading: Boolean,
    homeHeroEnabled: Boolean,
    newsletterBannerVisible: Boolean,
    discoverEnabled: Boolean,
    offlineLibrary: List<com.raulshma.jellyplay.core.model.OfflineMediaItem>,
    sections: List<com.raulshma.jellyplay.core.model.HomeSection>,
    featuredItem: com.raulshma.jellyplay.core.model.MediaItem?,
    viewModel: HomeViewModel,
    listState: LazyListState,
    backgroundColor: Color,
    contentPad: Dp,
    headerHeight: Dp,
    isLightTheme: Boolean,
    density: androidx.compose.ui.unit.Density,
    mediaImageUrlBuilder: (com.raulshma.jellyplay.core.model.MediaItem) -> String,
    mediaBackdropUrlBuilder: (com.raulshma.jellyplay.core.model.MediaItem) -> String,
    mediaOnItemClick: (com.raulshma.jellyplay.core.model.MediaItem) -> Unit,
    mediaOnPlayClick: (com.raulshma.jellyplay.core.model.MediaItem) -> Unit,
    fallbackImageUrlBuilder: (com.raulshma.jellyplay.core.model.MediaItem) -> List<String>,
    discoverRows: List<List<SeerrSearchItem>>,
    allDiscoverItems: List<SeerrSearchItem>,
    seerrCardLoadingState: com.raulshma.jellyplay.core.ui.components.SeerrCardLoadingState,
    seerrPrefetch: (Int, String, () -> Unit) -> Unit,
    onSeerrItemClick: (Int, String) -> Unit,
    onOfflineLibraryClick: () -> Unit,
    onItemClick: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    onSeerrRequest: (SeerrSearchItem) -> Unit,
    onNewsletterClick: () -> Unit = {},
) {
    val isTv = LocalTvMode.current
    val adaptiveInfo = LocalAdaptiveInfo.current

    if (sections.isEmpty()) {
        Box(
            Modifier.fillMaxSize().padding(horizontal = contentPad),
            contentAlignment = Alignment.Center,
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
                if (info.visibleItemsInfo.isEmpty()) IntRange(0, 0)
                else IntRange(info.visibleItemsInfo.first().index, info.visibleItemsInfo.last().index)
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = adaptiveInfo.bottomPadding(isTv)),
        ) {
            if (featuredItem != null && homeHeroEnabled) {
                item {
                    AnimatedHeroHeader(
                        featuredItem = featuredItem,
                        getBackdropUrl = remember { { viewModel.getBackdropUrl(it) } },
                        height = headerHeight,
                        backgroundColor = backgroundColor,
                        contentPadding = contentPad,
                        listState = listState,
                        onItemClick = onItemClick,
                        onDetailsClick = onItemClick,
                        onFocusChange = onFocusChange,
                    )
                }
            } else {
                item { Spacer(Modifier.height(100.dp)) }
            }

            if (newsletterBannerVisible) {
                item(key = "newsletter_banner") {
                    NewsletterBanner(
                        onClick = onNewsletterClick,
                        onDismiss = { viewModel.onEvent(HomeUiEvent.DismissNewsletterBanner) },
                    )
                }
            }

            items(count = sections.size, key = { sections[it].id }, contentType = { "homeSection_${sections[it].type}" }) { index ->
                val section = sections[index]
                val isFirstAfterHero = index == 0 && featuredItem != null && homeHeroEnabled
                val sectionIndexInList = index + (if (featuredItem != null && homeHeroEnabled) 1 else 0)
                val isCurrentlyVisible = sectionIndexInList in visibleItemRange

                var hasBeenVisible by rememberSaveable { mutableStateOf(false) }
                if (isCurrentlyVisible && !hasBeenVisible) hasBeenVisible = true

                val sectionAnimation by animateFloatAsState(
                    targetValue = if (hasBeenVisible) 1f else 0f,
                    animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                    label = "sectionAnimation",
                )

                val heroTransitionBrush = remember(backgroundColor, density) {
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, backgroundColor),
                        startY = 0f,
                        endY = with(density) { 10.dp.toPx() },
                    )
                }
                val sectionModifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isFirstAfterHero) {
                            Modifier.background(heroTransitionBrush)
                        } else Modifier.background(backgroundColor)
                    )
                    .padding(top = if (isFirstAfterHero) 0.dp else 16.dp)
                    .graphicsLayer {
                        alpha = sectionAnimation
                        val scale = 0.97f + (0.03f * sectionAnimation)
                        scaleX = scale
                        scaleY = scale
                        translationY = (1f - sectionAnimation) * 16.dp.toPx()
                    }

                if (section.type == HomeSectionType.CONTINUE_WATCHING || section.type == HomeSectionType.NEXT_UP) {
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

            if (discoverEnabled && allDiscoverItems.isNotEmpty()) {
                item(key = "seerr_discover_header") {
                    Text(
                        text = "Discover",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
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
                    contentType = { "seerrRow" },
                ) { rowIndex ->
                    val rowItems = discoverRows[rowIndex]
                    val pattern = if (adaptiveInfo.windowSizeClass == WindowSizeClass.Compact) COMPACT_DISCOVER_PATTERN else EXPANDED_DISCOVER_PATTERN
                    val targetSize = pattern[rowIndex % pattern.size]
                    val spacing = 8.dp

                    CompositionLocalProvider(
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
                                key = { idx -> rowItems[idx].id },
                                contentType = { "seerrCard" },
                            ) { idx ->
                                val seerrItem = rowItems[idx]
                                SeerrMediaCard(
                                    item = seerrItem,
                                    imageUrl = seerrItem.posterUrl,
                                    isLoading = seerrCardLoadingState.isLoading(seerrItem.id),
                                    onClick = {
                                        val mediaType = when {
                                            seerrItem.mediaType.equals("movie", ignoreCase = true) -> "movie"
                                            seerrItem.mediaType.equals("tv", ignoreCase = true) -> "tv"
                                            else -> seerrItem.mediaType
                                        }
                                        seerrCardLoadingState.startLoading(seerrItem.id)
                                        seerrPrefetch(seerrItem.id, mediaType) {
                                            seerrCardLoadingState.stopLoading(seerrItem.id)
                                            onSeerrItemClick(seerrItem.id, mediaType)
                                        }
                                    },
                                    onRequestClick = { onSeerrRequest(seerrItem) },
                                    modifier = Modifier.width(itemWidth),
                                )
                            }
                        }
                    }
                }
            }

            if (offlineLibrary.isNotEmpty()) {
                item(key = "downloaded_header") {
                    Text(
                        text = "Downloaded",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(backgroundColor)
                            .padding(start = contentPad, top = 24.dp, bottom = 8.dp),
                    )
                }

                item(key = "downloaded_row") {
                    DownloadedSection(
                        offlineLibrary = offlineLibrary,
                        onOfflineLibraryClick = onOfflineLibraryClick,
                        contentPad = contentPad,
                        backgroundColor = backgroundColor,
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberFallbackUrls(
    viewModel: HomeViewModel,
): (com.raulshma.jellyplay.core.model.MediaItem) -> List<String> {
    return remember {
        { item: com.raulshma.jellyplay.core.model.MediaItem ->
            if (item.mediaType == MediaType.AUDIO || item.mediaType == MediaType.MUSIC) {
                listOfNotNull(
                    item.parentId?.let { viewModel.getImageUrl(it) },
                    item.artistItems.firstOrNull()?.id?.let { viewModel.getImageUrl(it) },
                )
            } else emptyList()
        }
    }
}
