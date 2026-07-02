@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, kotlinx.coroutines.FlowPreview::class)
package com.raulshma.jellyplay.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import com.raulshma.jellyplay.core.ui.components.LocalFloatingNavVisibility
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.foundation.focusGroup
import com.raulshma.jellyplay.core.ui.tv.input.onDpadKey
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import com.raulshma.jellyplay.core.designsystem.theme.ArtworkThemeWrapper
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
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
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.core.ui.components.rememberSeerrCardLoadingState
import com.raulshma.jellyplay.core.ui.components.resolveHeaderStatus
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.isTv
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged

private val COMPACT_DISCOVER_PATTERN = listOf(3, 2, 3)
private val EXPANDED_DISCOVER_PATTERN = listOf(5, 4, 6, 5)

/**
 * Aggregates every navigation callback the Home screen needs so that
 * (a) the public [HomeScreen] signature stays readable,
 * (b) [MainHomeContent] receives a single stable parameter (treated as skip-worthy
 *     by the Compose compiler thanks to `@Immutable`) instead of ~20 individual
 *     unstable lambda parameters, and
 * (c) the navigation call site can `remember` one instance, eliminating
 *     cascading recompositions of children on every parent state change.
 *
 * Callers should construct via `remember(navigator) { HomeCallbacks(...) }` so
 * the same instance is reused across recompositions.
 */
@androidx.compose.runtime.Immutable
data class HomeCallbacks(
    val onItemClick: (itemId: String, mediaType: com.raulshma.jellyplay.core.model.MediaType, parentId: String?, itemName: String) -> Unit,
    val onPlayClick: (itemId: String, mediaSourceId: String?, startPosition: Long, mediaType: com.raulshma.jellyplay.core.model.MediaType, parentId: String?) -> Unit = { _, _, _, _, _ -> },
    val onSettingsClick: () -> Unit = {},
    val onSyncPlayClick: () -> Unit = {},
    val onDownloadsClick: () -> Unit = {},
    val onOfflineLibraryClick: () -> Unit = {},
    val onSeerrItemClick: (tmdbId: Int, mediaType: String) -> Unit = { _, _ -> },
    val onModeChange: (HomeMode) -> Unit = {},
    val onSearchItemClick: (String) -> Unit = {},
    val onSearchSeerrClick: (Int, String) -> Unit = { _, _ -> },
    val onNewsletterClick: () -> Unit = {},
    val onServerManagementClick: () -> Unit = {},
    val onUserManagementClick: () -> Unit = {},
    val onSeerrSettingsClick: () -> Unit = {},
    val onAdminDashboardClick: () -> Unit = {},
    val onSetupWizardClick: () -> Unit = {},
    val onFavoritesClick: () -> Unit = {},
    val onAboutClick: () -> Unit = {},
    val onWatchProgressHeatmapClick: () -> Unit = {},
    val onRequestsClick: () -> Unit = {},
)

@Composable
fun HomeScreen(
    callbacks: HomeCallbacks,
    homeMode: HomeMode = HomeMode.VIDEO,
    musicContent: @Composable () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    MainHomeContent(
        state = state,
        viewModel = viewModel,
        callbacks = callbacks,
        musicContent = musicContent,
    )
}

@Composable
private fun MainHomeContent(
    state: HomeUiState,
    viewModel: HomeViewModel,
    callbacks: HomeCallbacks,
    musicContent: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val focusManager = LocalFocusManager.current

    val networkStatus by LocalNetworkStatus.current.collectAsStateWithLifecycle()
    val serverHealth by com.raulshma.jellyplay.core.ui.components.LocalServerHealth.current.collectAsStateWithLifecycle()
    val headerStatus = remember(state.isLoading, state.error != null, networkStatus, serverHealth) {
        resolveHeaderStatus(
            isLoading = state.isLoading,
            hasError = state.error != null,
            networkStatus = networkStatus,
            serverHealth = serverHealth,
        )
    }

    val activeDownloadCount by viewModel.activeDownloadCount.collectAsStateWithLifecycle()
    val searchHistory by viewModel.searchHistory.collectAsStateWithLifecycle()

    val seerrCardLoadingState = rememberSeerrCardLoadingState()
    val seerrPrefetch: (Int, String, () -> Unit) -> Unit = remember(viewModel) {
        { tmdbId, mediaType, onDone ->
            viewModel.prefetchSeerrDetails(tmdbId, mediaType, onDone)
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
    val heroFocusRequester = remember { FocusRequester() }

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
            firstVisibleItemIndex = if (isTv) 0 else savedScrollPos.firstVisibleItemIndex,
            firstVisibleItemScrollOffset = if (isTv) 0 else savedScrollPos.firstVisibleItemScrollOffset,
        )
    }

    // When the hero actually receives focus, snap the list back to the top so the full hero is
    // visible. This is keyed on real focus state (not a BringIntoViewResponder), so it cannot
    // interfere with D-pad traversal between content rows. The first emission is skipped so a
    // freshly (re)composed Home doesn't snap to the top before per-row focus restoration runs.
    var heroFocusScrollSettled by remember { mutableStateOf(false) }
    LaunchedEffect(focusInHero) {
        if (!heroFocusScrollSettled) {
            heroFocusScrollSettled = true
        } else if (focusInHero && isTv) {
            listState.scrollToItem(0, 0)
        }
    }

    val headerHeight = remember(isTv, adaptiveInfo.isLandscape, adaptiveInfo.windowSizeClass) {
        when {
            isTv -> AdaptiveHeroHeight.Tv
            adaptiveInfo.isLandscape && adaptiveInfo.windowSizeClass != WindowSizeClass.Compact ->
                AdaptiveHeroHeight.LandscapeMedium
            else -> AdaptiveHeroHeight.PortraitCompact
        }
    }

    // Persist the scroll position as the user scrolls (debounced) so it survives
    // process death. Previously this only ran on `sections` emission, which
    // captured the wrong moment and lost the user's real position.
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .debounce(500)
            .distinctUntilChanged()
            .collect { (index, offset) ->
                viewModel.saveHomeScrollPosition(index, offset)
            }
    }

    // Keyed on `autoRotateEnabled` so the effect restarts when "Surprise Me"
    // toggles rotation off then back on; otherwise it would terminate on the
    // first toggle and never resume.
    LaunchedEffect(featuredCandidates, listState, autoRotateEnabled) {
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
    LaunchedEffect(Unit) {
        snapshotFlow { backgroundColor }.collect { navBarColor.value = it }
    }

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
    val currentOnItemClick by rememberUpdatedState(callbacks.onItemClick)
    val mediaOnItemClick = remember { { item: com.raulshma.jellyplay.core.model.MediaItem -> currentOnItemClick(item.id, item.mediaType, item.parentId, item.name) } }
    val currentOnPlayClick by rememberUpdatedState(callbacks.onPlayClick)
    val mediaOnPlayClick = remember { { item: com.raulshma.jellyplay.core.model.MediaItem ->
        currentOnPlayClick(item.id, null, item.playbackPositionTicks ?: 0L, item.mediaType, item.parentId)
    } }

    val photoFolderChildUrls by viewModel.photoFolderChildUrls.collectAsStateWithLifecycle()
    LaunchedEffect(state.sections) {
        val allItems = state.sections.flatMap { it.items }
        viewModel.prefetchPhotoFolderChildUrls(allItems)
    }

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

    val floatingNavVisibility = LocalFloatingNavVisibility.current
    DisposableEffect(isDrawerOpen) {
        if (isDrawerOpen) {
            floatingNavVisibility.value = false
        } else {
            floatingNavVisibility.value = true
        }
        onDispose {
            if (isDrawerOpen) {
                floatingNavVisibility.value = true
            }
        }
    }

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
        HomeScreenDrawer(
            showDrawer = !isTv,
            drawerState = drawerState,
            drawerContent = {
                HomeDrawerBody(
                    currentUser = state.currentUser,
                    backgroundColor = backgroundColor,
                    drawerState = drawerState,
                    scope = scope,
                    callbacks = callbacks,
                )
            }
        ) {
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.onEvent(HomeUiEvent.PullToRefresh) },
            enabled = !isTv && !isSearchFocused,
            modifier = Modifier.fillMaxSize(),
        ) {
        Box(modifier = Modifier.fillMaxSize().drawBehind { drawRect(backgroundColor) }) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .focusProperties {
                        onEnter = {
                            if (isSearchFocused) {
                                FocusRequester.Cancel
                            } else if (requestedFocusDirection == FocusDirection.Down && state.homeHeroEnabled && featuredItem != null) {
                                heroFocusRequester
                            } else {
                                FocusRequester.Default
                            }
                        }
                    }
                    .focusGroup()
            ) {
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
                            onItemClick = callbacks.onOfflineLibraryClick,
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
                            experimentalCardClippingEnabled = state.experimentalCardClippingEnabled,
                            offlineLibrary = state.offlineLibrary,
                            sections = state.sections,
                            featuredItem = featuredItem,
                            listState = listState,
                            backgroundColor = backgroundColor,
                            contentPad = contentPad,
                            headerHeight = headerHeight,
                            isLightTheme = isLightTheme,
                            density = density,
                            mediaImageUrlBuilder = mediaImageUrlBuilder,
                            mediaBackdropUrlBuilder = mediaBackdropUrlBuilder,
                            getImageUrl = remember { { id: String -> viewModel.getImageUrl(id) } },
                            getBackdropUrl = remember { { id: String -> viewModel.getBackdropUrl(id) } },
                            onDismissNewsletterBanner = { viewModel.onEvent(HomeUiEvent.DismissNewsletterBanner) },
                            mediaOnItemClick = mediaOnItemClick,
                            mediaOnPlayClick = mediaOnPlayClick,
                            continueWatchingClickBehavior = state.continueWatchingClickBehavior,
                            fallbackImageUrlBuilder = fallbackImageUrlBuilder,
                            discoverRows = discoverRows,
                            allDiscoverItems = allDiscoverItems,
                            seerrCardLoadingState = seerrCardLoadingState,
                            seerrPrefetch = seerrPrefetch,
                            onSeerrItemClick = callbacks.onSeerrItemClick,
                            onOfflineLibraryClick = callbacks.onOfflineLibraryClick,
                            onItemClick = { id -> callbacks.onItemClick(id, MediaType.UNKNOWN, null, "") },
                            onFocusChange = { focusInHero = it },
                            onSeerrRequest = { viewModel.onEvent(HomeUiEvent.SelectSeerrRequestItem(it)) },
                            onNewsletterClick = callbacks.onNewsletterClick,
                            photoFolderChildUrls = photoFolderChildUrls,
                            heroFocusRequester = heroFocusRequester,
                        )
                    }
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
                    showClock = state.showClock,
                    onModeChange = callbacks.onModeChange,
                    onSearchExpanded = { isSearchExpanded = it },
                    onSearchQueryChange = { viewModel.onEvent(HomeUiEvent.UpdateSearchQuery(it)) },
                    onClearSearch = {
                        isSearchExpanded = false
                        viewModel.onEvent(HomeUiEvent.ClearSearch)
                    },
                    onToggleOffline = { viewModel.onEvent(HomeUiEvent.ToggleOfflineMode) },
                    searchResultsContent = {
                        if (state.searchState.query.isNotBlank() || searchHistory.isNotEmpty()) {
                            HomeSearchResultsOverlay(
                                jellyfinResults = state.searchState.jellyfinResults,
                                seerrResults = state.searchState.seerrResults,
                                isSearching = state.searchState.isSearching,
                                getImageUrl = { viewModel.getImageUrl(it) },
                                onJellyfinClick = { item ->
                                    isSearchExpanded = false
                                    viewModel.onEvent(HomeUiEvent.ClearSearch)
                                    focusManager.clearFocus()
                                    callbacks.onItemClick(item.id, item.mediaType, item.parentId, item.name)
                                },
                                onSeerrClick = { item ->
                                    isSearchExpanded = false
                                    viewModel.onEvent(HomeUiEvent.ClearSearch)
                                    focusManager.clearFocus()
                                    callbacks.onSearchSeerrClick(item.id, item.mediaType)
                                },
                                searchHistory = searchHistory,
                                onHistoryClick = { query ->
                                    viewModel.onEvent(HomeUiEvent.UpdateSearchQuery(query))
                                },
                                onDeleteHistoryItem = { id -> viewModel.deleteSearchHistoryItem(id) },
                                onClearHistory = { viewModel.clearSearchHistory() },
                            )
                        }
                    },
                    modifier = Modifier.then(
                        if (isTv) {
                            Modifier.onDpadKey(
                                onDown = {
                                    if (!isSearchFocused && state.homeHeroEnabled && featuredItem != null) {
                                        heroFocusRequester.tryRequestFocus("top_dock_down_hero")
                                        true
                                    } else false
                                }
                            )
                        } else Modifier
                    )
                )

                if (!isTv) {
                    HomeFabMenu(
                        isExpanded = isFabExpanded,
                        onToggle = { isFabExpanded = it },
                        activeDownloadCount = activeDownloadCount,
                        offlineMode = state.offlineMode,
                        onSurpriseClick = {
                            showSurprise = !showSurprise
                            if (!showSurprise) autoRotateEnabled = true
                        },
                        onSyncPlayClick = callbacks.onSyncPlayClick,
                        onDownloadsClick = callbacks.onDownloadsClick,
                        onToggleOffline = { viewModel.onEvent(HomeUiEvent.ToggleOfflineMode) },
                        onSettingsClick = callbacks.onSettingsClick,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 64.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
                            .offset {
                                val maxOffset = com.raulshma.jellyplay.core.designsystem.theme.Dimensions.floatingNavHeight.toPx()
                                val yOffset = (-navOffsetPx).coerceAtMost(maxOffset)
                                androidx.compose.ui.unit.IntOffset(x = 0, y = yOffset.toInt())
                            },
                    )
                }

                if (!isTv && !isSearchFocused) {
                    Box(
                        modifier = Modifier
                            .statusBarsPadding()
                            .padding(
                                horizontal = 16.dp,
                                vertical = 4.dp
                            )
                            .align(Alignment.TopStart)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 40.dp, height = 64.dp)
                                .clip(ShapeCache.smooth16)
                                .focusIndicator(androidx.compose.foundation.shape.CircleShape)
                                .clickable(
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                    indication = null,
                                    onClick = { scope.launch { drawerState.open() } }
                                ),
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
    experimentalCardClippingEnabled: Boolean,
    offlineLibrary: List<com.raulshma.jellyplay.core.model.OfflineMediaItem>,
    sections: List<com.raulshma.jellyplay.core.model.HomeSection>,
    featuredItem: com.raulshma.jellyplay.core.model.MediaItem?,
    listState: LazyListState,
    backgroundColor: Color,
    contentPad: Dp,
    headerHeight: Dp,
    isLightTheme: Boolean,
    density: androidx.compose.ui.unit.Density,
    mediaImageUrlBuilder: (com.raulshma.jellyplay.core.model.MediaItem) -> String,
    mediaBackdropUrlBuilder: (com.raulshma.jellyplay.core.model.MediaItem) -> String,
    getImageUrl: (String) -> String,
    getBackdropUrl: (String) -> String,
    onDismissNewsletterBanner: () -> Unit,
    mediaOnItemClick: (com.raulshma.jellyplay.core.model.MediaItem) -> Unit,
    mediaOnPlayClick: (com.raulshma.jellyplay.core.model.MediaItem) -> Unit,
    continueWatchingClickBehavior: com.raulshma.jellyplay.core.model.ContinueWatchingClickBehavior,
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
    photoFolderChildUrls: Map<String, List<String>> = emptyMap(),
    heroFocusRequester: FocusRequester? = null,
) {
    val isTv = LocalTvMode.current
    val adaptiveInfo = LocalAdaptiveInfo.current

    var askContinueItem by remember { mutableStateOf<com.raulshma.jellyplay.core.model.MediaItem?>(null) }

    // Per-row focus requesters so D-pad navigation can target each content row. On TV we restore
    // focus to the last-visited row (and the exact card within it, via the row's tvFocusRestorer)
    // on back-stack pops; only when no row has been visited yet does the hero anchor the top.
    var homeFocusRow by com.raulshma.jellyplay.core.ui.tv.rememberInt(-1)
    val savedRowIsValid = homeFocusRow in 0..sections.lastIndex
    val rowFocusRequesters = remember(sections.size) { List(sections.size) { FocusRequester() } }
    // Restore focus to the last-visited row when returning to Home. The hero's
    // requestInitialFocus = !savedRowIsValid keeps it from grabbing focus when a valid row exists;
    // here we scroll the saved row fully into view (so its FocusRequester is attached, avoiding
    // the half-clipped hero that a minimal bring-into-view caused previously) and re-request focus.
    // LaunchedEffect(Unit) re-fires on back-stack pops via the saveable-state holder.
    LaunchedEffect(Unit) {
        if (isTv && savedRowIsValid && sections.isNotEmpty()) {
            val headerOffset = 1 + (if (newsletterBannerVisible) 1 else 0)
            listState.scrollToItem(homeFocusRow + headerOffset)
            rowFocusRequesters.getOrNull(homeFocusRow)?.tryRequestFocus("home_row_restore")
        }
    }

    if (sections.isEmpty()) {
        Box(
            Modifier.fillMaxSize().padding(horizontal = contentPad),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (isLoading) "" else "No content available. Check your Jellyfin libraries.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                item(key = "hero") {
                    AnimatedHeroHeader(
                        featuredItem = featuredItem,
                        getBackdropUrl = remember { { getBackdropUrl(it) } },
                        height = headerHeight,
                        backgroundColor = backgroundColor,
                        contentPadding = contentPad,
                        listState = listState,
                        onItemClick = onItemClick,
                        onDetailsClick = onItemClick,
                        requestInitialFocus = !savedRowIsValid,
                        onFocusChange = onFocusChange,
                        focusRequester = heroFocusRequester,
                    )
                }
            } else {
                item(key = "hero_spacer") { Spacer(Modifier.height(100.dp)) }
            }

            if (newsletterBannerVisible) {
                item(key = "newsletter_banner") {
                    NewsletterBanner(
                        onClick = onNewsletterClick,
                        onDismiss = onDismissNewsletterBanner,
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
                        translationY = (1f - sectionAnimation) * 16.dp.toPx()
                    }

                val seedItem = section.seedItem
                val sectionTitle = if (section.type == HomeSectionType.RECOMMENDATIONS && seedItem != null) {
                    "Because you watched ${seedItem.name}"
                } else {
                    section.title
                }

                if (section.type == HomeSectionType.CONTINUE_WATCHING || section.type == HomeSectionType.NEXT_UP) {
                    val rowItemClick: (com.raulshma.jellyplay.core.model.MediaItem) -> Unit = remember(
                        section.type, continueWatchingClickBehavior, mediaOnItemClick, mediaOnPlayClick,
                    ) {
                        { item ->
                            if (section.type == HomeSectionType.CONTINUE_WATCHING) {
                                when (continueWatchingClickBehavior) {
                                    com.raulshma.jellyplay.core.model.ContinueWatchingClickBehavior.DETAILS -> mediaOnItemClick(item)
                                    com.raulshma.jellyplay.core.model.ContinueWatchingClickBehavior.PLAY -> mediaOnPlayClick(item)
                                    com.raulshma.jellyplay.core.model.ContinueWatchingClickBehavior.ASK -> { askContinueItem = item }
                                }
                            } else {
                                mediaOnItemClick(item)
                            }
                        }
                    }
                    ContinueWatchingRow(
                        title = sectionTitle,
                        items = section.items,
                        imageUrlBuilder = mediaImageUrlBuilder,
                        backdropUrlBuilder = mediaBackdropUrlBuilder,
                        onItemClick = rowItemClick,
                        onPlayClick = mediaOnPlayClick,
                        modifier = sectionModifier,
                        focusRequester = rowFocusRequesters[index],
                        onRowFocused = { homeFocusRow = index },
                        clippingEnabled = experimentalCardClippingEnabled,
                    )
                } else {
                    HomeMediaRow(
                        title = sectionTitle,
                        items = section.items,
                        imageUrlBuilder = mediaImageUrlBuilder,
                        fallbackImageUrlBuilder = fallbackImageUrlBuilder,
                        onItemClick = mediaOnItemClick,
                        onPlayClick = mediaOnPlayClick,
                        modifier = sectionModifier,
                        photoFolderChildUrls = photoFolderChildUrls,
                        focusRequester = rowFocusRequesters[index],
                        onRowFocused = { homeFocusRow = index },
                        clippingEnabled = experimentalCardClippingEnabled,
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
                                .then(if (experimentalCardClippingEnabled) Modifier.clipToBounds() else Modifier)
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
                                    clipToShape = experimentalCardClippingEnabled,
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
                item(key = "downloaded_row") {
                    // DownloadedSection renders its own "Downloaded" header, so we
                    // intentionally don't emit a separate header item here.
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

    val askItem = askContinueItem
    if (askItem != null) {
        AlertDialog(
            onDismissRequest = { askContinueItem = null },
            icon = { Icon(Tabler.Outline.PlayerPlay, contentDescription = null) },
            title = { Text(askItem.name) },
            text = { Text("Resume playback or open details?") },
            confirmButton = {
                TextButton(onClick = {
                    askContinueItem = null
                    mediaOnPlayClick(askItem)
                }) { Text("Resume") }
            },
            dismissButton = {
                TextButton(onClick = {
                    askContinueItem = null
                    mediaOnItemClick(askItem)
                }) { Text("Details") }
            },
        )
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

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreenDrawer(
    showDrawer: Boolean,
    drawerState: androidx.compose.material3.DrawerState,
    drawerContent: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    if (showDrawer) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = drawerContent,
        ) {
            content()
        }
    } else {
        content()
    }
}
