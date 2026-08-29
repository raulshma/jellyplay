@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, kotlinx.coroutines.FlowPreview::class)
package com.raulshma.jellyplay.feature.home

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import com.raulshma.jellyplay.core.ui.components.clearFloatingNav
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.focusGroup
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Download
import com.composables.icons.tabler.outline.Trash
import com.raulshma.jellyplay.core.designsystem.theme.ArtworkThemeWrapper
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.formatBytes
import com.raulshma.jellyplay.core.model.MediaQuickActionScope
import com.raulshma.jellyplay.core.model.quickActions
import com.raulshma.jellyplay.core.model.OfflineMode
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.model.seerr.DiscoverSectionType
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.ConfirmDialog
import com.raulshma.jellyplay.core.ui.components.ConfirmTone
import com.raulshma.jellyplay.core.ui.components.DeleteDownloadedEpisodesSheet
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.ScreenEmptyState
import com.raulshma.jellyplay.core.ui.components.LocalMediaQuickActionController
import com.raulshma.jellyplay.core.ui.components.LocalNavigationBarColor
import com.raulshma.jellyplay.core.ui.components.LocalNetworkStatus
import com.raulshma.jellyplay.core.ui.components.LocalServerHealth
import com.raulshma.jellyplay.core.ui.components.MediaQuickActionHost
import com.raulshma.jellyplay.core.ui.components.QuickAction
import com.raulshma.jellyplay.core.ui.components.SeerrRequestDialog
import com.raulshma.jellyplay.core.ui.components.TvSafeSheet
import com.raulshma.jellyplay.core.ui.components.UndoSnackbarOverlay
import com.raulshma.jellyplay.core.ui.components.rememberMediaQuickActionController
import com.raulshma.jellyplay.core.ui.components.rememberSeerrCardLoadingState
import com.raulshma.jellyplay.core.ui.components.resolveHeaderStatus
import com.raulshma.jellyplay.core.ui.components.HeaderStatus
import com.raulshma.jellyplay.core.ui.navigation.withHighlightSettingId
import com.raulshma.jellyplay.core.ui.settingssearch.ResolvedSettingsItem
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.input.onDpadKey
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus

/**
 * Aggregates every navigation callback the Home screen needs so that
 * (a) the public [HomeScreen] signature stays readable,
 * (b) [MainHomeContent] receives a single stable parameter (treated as skip-worthy
 * by the Compose compiler thanks to `@Immutable`) instead of ~20 individual
 * unstable lambda parameters, and
 * (c) the navigation call site can `remember` one instance, eliminating
 * cascading recompositions of children on every parent state change.
 *
 * Callers should construct via `remember(navigator) { HomeCallbacks(...) }` so
 * the same instance is reused across recompositions.
 */
@androidx.compose.runtime.Immutable
data class HomeCallbacks(
    val onItemClick: (itemId: String, mediaType: com.raulshma.jellyplay.core.model.MediaType, parentId: String?, itemName: String) -> Unit,
    val onPlayClick: (itemId: String, mediaSourceId: String?, startPosition: Long, mediaType: com.raulshma.jellyplay.core.model.MediaType, parentId: String?, itemName: String) -> Unit = { _, _, _, _, _, _ -> },
    val onSettingsClick: () -> Unit = {},
    val onSyncPlayClick: () -> Unit = {},
    val onDownloadsClick: () -> Unit = {},
    val onPlayOnClick: () -> Unit = {},
    val onOfflineLibraryClick: () -> Unit = {},
    /** Open a specific downloaded item: series go to the offline series
     * browser, everything else to the offline detail screen. */
    val onOfflineItemClick: (itemId: String, mediaType: com.raulshma.jellyplay.core.model.MediaType) -> Unit = { _, _ -> },
    /** Open an item's detail screen from the inline card long-press Download;
     * [openDownloadSheet] pre-presents the series download sheet there. */
    val onDownloadDetailClick: (itemId: String, openDownloadSheet: Boolean) -> Unit = { _, _ -> },
    val onSeerrItemClick: (tmdbId: Int, mediaType: String) -> Unit = { _, _ -> },
    val onModeChange: (HomeMode) -> Unit = {},
    val onSearchItemClick: (String) -> Unit = {},
    val onSearchSeerrClick: (Int, String) -> Unit = { _, _ -> },
    /** Open a settings destination surfaced by the home search bar. The passed
     * [com.raulshma.jellyplay.core.ui.navigation.Route] already has its
     * `highlightSettingId` populated (via [withHighlightSettingId]) so the
     * destination screen scrolls to / focuses the matched setting row. */
    val onSettingsSearchItemClick: (com.raulshma.jellyplay.core.ui.navigation.Route) -> Unit = {},
    val onNewsletterClick: () -> Unit = {},
    /** Deep-link into Settings → Home Screen Layout. Reached from the inline
     * section-config sheet's "Configure Home Layout" action. */
    val onConfigureHomeLayout: () -> Unit = {},
    /** Deep-link into Settings → Configure Libraries (per-library section
     * overrides). Reached from the inline section-config sheet when a
     * per-library (LATEST_MEDIA) row is being configured. */
    val onConfigureLibraries: () -> Unit = {},
    /** Open the full library screen for a home-section "See All" action.
     * Carries the section [HomeSectionType], the optional per-library id (non-null
     * only for LATEST_MEDIA), the optional per-library [collectionType] (used to
     * reproduce the home row's leaf item type + sort), and the resolved title. */
    val onSeeAllClick: (sectionType: HomeSectionType, libraryId: String?, collectionType: String?, title: String) -> Unit = { _, _, _, _ -> },
)

@Composable
fun HomeScreen(
    callbacks: HomeCallbacks,
    homeMode: HomeMode = HomeMode.VIDEO,
    musicContent: @Composable () -> Unit = {},
    surpriseRequests: kotlinx.coroutines.flow.Flow<Unit> = kotlinx.coroutines.flow.emptyFlow(),
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    MainHomeContent(
        state = state,
        viewModel = viewModel,
        callbacks = callbacks,
        musicContent = musicContent,
        surpriseRequests = surpriseRequests,
    )
}

@Composable
private fun MainHomeContent(
    state: HomeUiState,
    viewModel: HomeViewModel,
    callbacks: HomeCallbacks,
    musicContent: @Composable () -> Unit,
    surpriseRequests: kotlinx.coroutines.flow.Flow<Unit> = kotlinx.coroutines.flow.emptyFlow(),
) {
    val density = LocalDensity.current
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    val focusManager = LocalFocusManager.current

    val networkStatus by LocalNetworkStatus.current.collectAsStateWithLifecycle()
    val serverHealth by LocalServerHealth.current.collectAsStateWithLifecycle()
    val headerStatus = remember(state.isLoading, state.error != null, networkStatus, serverHealth) {
        resolveHeaderStatus(
            isLoading = state.isLoading,
            hasError = state.error != null,
            networkStatus = networkStatus,
            serverHealth = serverHealth,
        )
    }

    val activity = LocalActivity.current
    var homeFullyDrawn by remember { mutableStateOf(false) }
    LaunchedEffect(state.currentUser != null, !state.isLoading) {
        if (!homeFullyDrawn && state.currentUser != null && !state.isLoading) {
            // Effect bodies run post-composition but before the frame is on
            // screen — wait for the first drawn frame so TTFD measures
            // content the user actually sees.
            withFrameNanos { }
            homeFullyDrawn = true
            activity?.reportFullyDrawn()
        }
    }

    val activeDownloadCount by viewModel.activeDownloadCount.collectAsStateWithLifecycle()
    val pendingSyncCount by viewModel.pendingSyncCount.collectAsStateWithLifecycle()
    val currentServerUsers by viewModel.currentServerUsers.collectAsStateWithLifecycle()
    var showSyncDetails by remember { mutableStateOf(false) }
    val searchHistory by viewModel.searchHistory.collectAsStateWithLifecycle()
    // Stabilize the user-switch lambda so HomeTopDock stays skippable on
    // recompositions that reach it (scroll, search focus). Mirrors the
    // dock lambda memoization above.
    val onUserSwitch = remember(viewModel) { { id: String -> viewModel.onEvent(HomeUiEvent.SwitchUser(id)) } }

    val seerrCardLoadingState = rememberSeerrCardLoadingState()
    val seerrPrefetch: (Int, String, () -> Unit) -> Unit = remember(viewModel) {
        { tmdbId, mediaType, onDone ->
            viewModel.onEvent(HomeUiEvent.PrefetchSeerrDetails(tmdbId, mediaType, onDone))
        }
    }

    // Hero featured-candidate selection — single-pass (was up to 3x flatMap/filter).
    val featuredCandidates = remember(state.sections) { selectFeaturedCandidates(state.sections) }
    val heroFocusRequester = remember { FocusRequester() }
    val mediaImageUrlBuilder = remember(viewModel) { { item: com.raulshma.jellyplay.core.model.MediaItem -> viewModel.getImageUrl(item.id) } }
    val mediaBackdropUrlBuilder = remember(viewModel) { { item: com.raulshma.jellyplay.core.model.MediaItem -> viewModel.getBackdropUrl(item.id) } }

    // Scroll state must exist before the hero controller (auto-rotate reads
    // listState.isScrollInProgress). Both own Compose side-effects, so order
    // only matters for the data dependency, not for effect registration.
    val homeScrollState = rememberHomeScrollState(
        savePosition = remember(viewModel) { { index, offset -> viewModel.saveHomeScrollPosition(index, offset) } },
        initialPosition = viewModel.getHomeScrollPosition(),
    )
    val listState = homeScrollState.listState

    val heroController = rememberHeroController(
        featuredCandidates = featuredCandidates,
        listState = listState,
        heroFocusRequester = heroFocusRequester,
        getBackdropUrl = remember(viewModel) { { id: String -> viewModel.getBackdropUrl(id) } },
    )

    // Global nav overflow "Surprise Me" (#115): the hero controller lives here
    // (it owns the hero LazyListState + featured candidates), so the app shell
    // emits a one-shot signal that Home forwards to it.
    androidx.compose.runtime.LaunchedEffect(heroController, surpriseRequests) {
        surpriseRequests.collect { heroController.toggleSurprise() }
    }

    val headerHeight = rememberHeroHeight()

    val bgState = rememberHomeBackgroundState(
        dynamicTheming = state.dynamicTheming,
        backdropUrl = heroController.backdropUrl,
    )
    val backgroundColor = bgState.backgroundColor
    val isLightTheme = bgState.isLightTheme

    // NOTE: scrollFraction / appBarIconColor / appBarIconColorFaded are no
    // longer read in this scope. Reading scrollFraction here (changes every
    // pixel over the first 140 dp of hero scroll) invalidated the entire
    // 450-line MainHomeContent body on every scroll frame. The color
    // computation now lives in HomeTopDockScrim, a leaf that reads
    // scrollFraction internally so only it (and HomeTopDock) recompose.

    val contentPad = remember(adaptiveInfo, isTv) { adaptiveInfo.contentPadding(isTv) }

    val currentOnItemClick by rememberUpdatedState(callbacks.onItemClick)
    val mediaOnItemClick = remember { { item: com.raulshma.jellyplay.core.model.MediaItem -> currentOnItemClick(item.id, item.mediaType, item.parentId, item.name) } }
    val currentOnPlayClick by rememberUpdatedState(callbacks.onPlayClick)
    // Single funnel for every play affordance on a media card (overlay button,
    // quick-action menu, continue-watching ASK dialog). A SERIES card must
    // never route its own folder id to the player — the VM resolves which
    // episode to start first (resume → next unplayed → replay, the detail
    // screen's smart-play rule), through the onEvent funnel like every other
    // intent; this lambda only maps the outcome onto the navigation callbacks.
    val mediaOnPlayClick = remember { { item: com.raulshma.jellyplay.core.model.MediaItem ->
        if (item.mediaType == MediaType.SERIES) {
            viewModel.onEvent(HomeUiEvent.PlaySeries(item) { resolution ->
                when (resolution) {
                    is SeriesPlayResolution.Episode -> currentOnPlayClick(
                        resolution.item.id,
                        null,
                        resolution.startPositionTicks,
                        resolution.item.mediaType,
                        resolution.item.parentId,
                        resolution.item.name,
                    )
                    is SeriesPlayResolution.Details -> currentOnItemClick(
                        resolution.series.id,
                        resolution.series.mediaType,
                        resolution.series.parentId,
                        resolution.series.name,
                    )
                }
            })
        } else {
            currentOnPlayClick(item.id, null, item.playbackPositionTicks ?: 0L, item.mediaType, item.parentId, item.name)
        }
    } }

    // Item awaiting a delete-confirm from the offline home's quick-action menu.
    // Hoisted here (not in the sheet callback) so the dialog survives the card
    // leaving composition while it's open.
    var pendingDelete by remember { mutableStateOf<com.raulshma.jellyplay.core.model.MediaItem?>(null) }

    // Quick actions on card long-press and the TV Menu key on the focused
    // card. Provided to every PosterCard in scope via
    // CompositionLocal — the cards wire their own long-press.
    val quickActionController = rememberMediaQuickActionController(
        resolveActions = remember(viewModel) {
            { item: com.raulshma.jellyplay.core.model.MediaItem ->
                // Download/Remove-download are gated by real download state
                // (works online and off); the offline home additionally offers
                // remove-download for series/seasons via includeRemoveDownload.
                val offline = viewModel.uiState.value.offlineMode != OfflineMode.ONLINE
                item.quickActions(
                    MediaQuickActionScope.HOME,
                    includeDownload = true,
                    includeRemoveDownload = offline,
                    isDownloaded = viewModel.downloadedIds.value.contains(item.id),
                )
            }
        },
        executeAction = remember(viewModel, mediaOnItemClick, mediaOnPlayClick, callbacks) {
            { item: com.raulshma.jellyplay.core.model.MediaItem, action: QuickAction ->
                when (action) {
                    QuickAction.PLAY -> mediaOnPlayClick(item)
                    QuickAction.MARK_WATCHED -> viewModel.onEvent(HomeUiEvent.MarkItemPlayed(item))
                    QuickAction.MARK_UNWATCHED -> viewModel.onEvent(HomeUiEvent.MarkItemUnplayed(item))
                    QuickAction.DETAILS -> mediaOnItemClick(item)
                    // Single-stream items start inline at the default quality;
                    // series (and other non-inline types) open the detail
                    // screen — for a series with the download sheet pre-presented.
                    QuickAction.DOWNLOAD -> viewModel.downloadItem(
                        item,
                        onOpenDetail = { itemId, openDownloadSheet ->
                            callbacks.onDownloadDetailClick(itemId, openDownloadSheet)
                        },
                    )
                    // Series opens the advanced delete-episodes sheet
                    // (select episodes / seasons / entire series); anything
                    // else (movie/music) opens the simple confirm dialog below.
                    QuickAction.REMOVE_DOWNLOAD -> {
                        if (item.mediaType == MediaType.SERIES) viewModel.onEvent(HomeUiEvent.RequestSeriesDelete(item))
                        else pendingDelete = item
                    }
                    else -> Unit
                }
            }
        },
    )
    // TV-only: the card currently holding D-pad focus, so the Menu key can open
    // its quick actions. Rows report via HomeContentCallbacks.
    var tvFocusedItem by remember { mutableStateOf<com.raulshma.jellyplay.core.model.MediaItem?>(null) }

    val photoFolderChildUrls by viewModel.photoFolderChildUrls.collectAsStateWithLifecycle()
    // Only photo-folder items are relevant to the prefetcher (it filters to
    // PHOTO_FOLDER internally), so narrow the list to those items. This keeps
    // both the per-emission allocation and the effect-key proportional to the
    // number of photo folders rather than every item across all sections.
    val photoFolderItems = remember(state.sections) {
        state.sections.flatMap { it.items }.filter { it.mediaType == MediaType.PHOTO_FOLDER }
    }
    // Structural fingerprint so the effect only re-runs when the photo-folder
    // set actually changes, not on every partial-load emission that produces a
    // new list instance with the same ids. Computed once per sections change
    // (not per recomposition) so scrolling never allocates here.
    val photoFolderKey = remember(photoFolderItems) { photoFolderItems.map { it.id } }
    androidx.compose.runtime.LaunchedEffect(photoFolderKey) {
        viewModel.onEvent(HomeUiEvent.PrefetchPhotoFolderChildUrls(photoFolderItems))
    }

    val fallbackImageUrlBuilder = rememberFallbackUrls(viewModel)

    val discoverSectionOrder = remember {
        listOf(DiscoverSectionType.TRENDING, DiscoverSectionType.POPULAR_MOVIES, DiscoverSectionType.POPULAR_TV, DiscoverSectionType.UPCOMING_MOVIES, DiscoverSectionType.UPCOMING_TV)
    }
    val allDiscoverItems = remember(state.discoverSections) {
        discoverSectionOrder.flatMap { state.discoverSections[it] ?: emptyList() }.distinctBy { it.id }
    }
    val discoverRows = rememberDiscoverRows(allDiscoverItems)

    var isSearchExpanded by remember { mutableStateOf(false) }
    val isSearchFocused by remember { derivedStateOf { state.isSearchActive || isSearchExpanded } }

    // Inline section-config sheet target — set by long-pressing a configurable
    // section title. Hoisted here (not in the LazyColumn item) so opening the
    // sheet doesn't recompose the content list, and so it survives the row
    // leaving composition while the sheet is open. Carries the optional
    // libraryId for per-library (LATEST_MEDIA) rows so the sheet can apply a
    // per-library override instead of a global toggle.
    var sectionConfigTarget by remember { mutableStateOf<SectionConfigTarget?>(null) }
    val onConfigureSection = remember { { type: HomeSectionType, libraryId: String? ->
        sectionConfigTarget = SectionConfigTarget(type, libraryId)
    } }
    val onConfigureHomeLayout = remember(callbacks) { { callbacks.onConfigureHomeLayout() } }
    val onConfigureLibraries = remember(callbacks) { { callbacks.onConfigureLibraries() } }
    val dismissSectionConfig = remember { { sectionConfigTarget = null } }

    BackHandler(enabled = isSearchFocused) {
        isSearchExpanded = false
        viewModel.onEvent(HomeUiEvent.ClearSearch)
        focusManager.clearFocus()
    }

    ArtworkThemeWrapper(
        imageUrl = heroController.backdropUrl,
        dynamicTheming = state.dynamicTheming,
        darkTheme = !isLightTheme,
        oledMode = state.oledMode,
        colorStyle = state.colorStyle,
        accentColorSwatch = state.accentColorSwatch,
    ) {
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.onEvent(HomeUiEvent.PullToRefresh) },
            enabled = !isTv && !isSearchFocused,
            modifier = Modifier.fillMaxSize(),
        ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind { drawRect(backgroundColor) }
                .onDpadKey(
                    onMenu = {
                        // TV remote Menu button: open the focused card's quick
                        // actions. Rows track the focused item via
                        // onFocusedMediaItem.
                        val focused = tvFocusedItem
                        if (focused != null) {
                            quickActionController.show(focused)
                            true
                        } else {
                            false
                        }
                    },
                ),
        ) {
            // Recovery path for search-history delete/clear. Home
            // previously had no SnackbarHost at all; this is the single host.
            UndoSnackbarOverlay(
                actions = viewModel.undoActions,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
            // Ambient backdrop: the hero artwork's BlurHash, or a palette-derived
            // gradient when no hero/blurhash is available. Sits behind all content
            // and above the flat background fill. Suppressed in performance mode.
            HomeBackdrop(
                state = HomeBackdropState(
                    enabled = state.homeBackdropEnabled,
                    performanceMode = state.performanceMode,
                    oledMode = state.oledMode,
                    isLightTheme = isLightTheme,
                    blurHash = heroController.featuredItem?.blurHashes?.backdrop,
                    backdropUrl = heroController.backdropUrl,
                    backgroundColor = backgroundColor,
                ),
            )
            CompositionLocalProvider(LocalMediaQuickActionController provides quickActionController) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .focusProperties {
                            onEnter = {
                                if (isSearchFocused) {
                                    FocusRequester.Cancel
                                } else if (requestedFocusDirection == FocusDirection.Down && state.homeHeroEnabled && heroController.featuredItem != null) {
                                    heroFocusRequester
                                } else {
                                    FocusRequester.Default
                                }
                            }
                        }
                        .focusGroup()
                ) {
                // Offline home = the normal home content list fed with sections
                // derived from the offline library (#147) — no dedicated offline
                // screen. Sections are recomputed only when the library, the
                // home mode, or the localized titles change.
                val offlineTitles = rememberOfflineHomeSectionTitles()
                val offlineSections = remember(state.offlineLibrary, state.homeMode, offlineTitles) {
                    buildOfflineHomeSections(
                        filterOfflineByMode(state.offlineLibrary, state.homeMode),
                        offlineTitles,
                    )
                }
                // Server fetch failed but downloads exist -> implicit offline: the
                // same integrated home plus a status banner so the fallback isn't
                // silent. (state.offlineLibrary is only collected while offline or
                // mid-failure, so this never shadows the online home.)
                val implicitOffline = state.error != null && state.sections.isEmpty() &&
                    state.offlineMode == OfflineMode.ONLINE && state.offlineLibrary.isNotEmpty()
                val renderingOffline = state.offlineMode != OfflineMode.ONLINE || implicitOffline

                when {
                    // When an online fetch fails but we have no downloads, there is
                    // nothing to fall back on — show the hard error.
                    state.error != null && state.sections.isEmpty() && state.offlineMode == OfflineMode.ONLINE &&
                        state.offlineLibrary.isEmpty() -> {
                        ErrorScreen(
                            message = stringResource(R.string.home_error_load_content),
                            onRetry = { viewModel.onEvent(HomeUiEvent.Refresh) },
                            modifier = Modifier.padding(horizontal = contentPad),
                        )
                    }
                    // Explicitly offline (manual or auto) with nothing downloaded.
                    state.offlineMode != OfflineMode.ONLINE && offlineSections.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(backgroundColor),
                            contentAlignment = Alignment.Center,
                        ) {
                            ScreenEmptyState(
                                icon = Tabler.Outline.Download,
                                title = stringResource(R.string.home_no_downloads_yet),
                                description = stringResource(R.string.home_no_downloads_description),
                                actionLabel = stringResource(R.string.home_go_online_action),
                                onAction = { viewModel.onEvent(HomeUiEvent.ToggleOfflineMode) },
                                actionLoading = state.isGoingOnline,
                                modifier = Modifier.padding(horizontal = contentPad),
                            )
                        }
                    }
                    state.homeMode == HomeMode.MUSIC && state.offlineMode == OfflineMode.ONLINE -> {
                        musicContent()
                    }
                    else -> {
                        HomeContentList(
                            state = HomeContentState(
                                // Offline rendering short-circuits the server-only
                                // surfaces (loading spinner, banners, hero) — the
                                // content list shows the offline-derived sections.
                                isLoading = !renderingOffline && state.isLoading,
                                homeHeroEnabled = state.homeHeroEnabled,
                                homeBackdropEnabled = state.homeBackdropEnabled,
                                newsletterBannerVisible = !renderingOffline && state.newsletterBannerVisible,
                                discoverEnabled = state.discoverEnabled,
                                experimentalCardClippingEnabled = state.experimentalCardClippingEnabled,
                                sections = if (renderingOffline) offlineSections else state.sections,
                                partialLoadError = !renderingOffline && state.partialLoadError,
                                featuredItem = if (renderingOffline) null else heroController.featuredItem,
                                backgroundColor = backgroundColor,
                                contentPad = contentPad,
                                headerHeight = headerHeight,
                                isLightTheme = isLightTheme,
                                continueWatchingClickBehavior = state.continueWatchingClickBehavior,
                                discoverRows = discoverRows,
                                allDiscoverItems = allDiscoverItems,
                                recentlyGrabbed = state.recentlyGrabbed,
                                photoFolderChildUrls = photoFolderChildUrls,
                                // Only forward offlineLibrary when it can actually
                                // render (offline branch), so download-progress ticks while
                                // online never invalidate the content list.
                                offlineLibrary = if (renderingOffline) state.offlineLibrary else emptyList(),
                                statusBanner = if (implicitOffline) {
                                    "Couldn't reach the server — showing your downloads."
                                } else null,
                            ),
                            callbacks = HomeContentCallbacks(
                                onRetrySectionLoad = remember(viewModel) { { viewModel.onEvent(HomeUiEvent.Refresh) } },
                                onDismissNewsletterBanner = remember(viewModel) { { viewModel.onEvent(HomeUiEvent.DismissNewsletterBanner) } },
                                onNewsletterClick = callbacks.onNewsletterClick,
                                onOfflineLibraryClick = callbacks.onOfflineLibraryClick,
                                onOfflineItemClick = remember(callbacks) {
                                    { itemId: String, mediaType: MediaType -> callbacks.onOfflineItemClick(itemId, mediaType) }
                                },
                                onItemClick = remember(callbacks) { { id: String -> callbacks.onItemClick(id, MediaType.UNKNOWN, null, "") } },
                                onFocusChange = remember { { focused: Boolean -> heroController.onFocusChange(focused) } },
                                mediaOnItemClick = mediaOnItemClick,
                                mediaOnPlayClick = mediaOnPlayClick,
                                mediaImageUrlBuilder = mediaImageUrlBuilder,
                                mediaBackdropUrlBuilder = mediaBackdropUrlBuilder,
                                getImageUrl = remember(viewModel) { { id: String -> viewModel.getImageUrl(id) } },
                                getBackdropUrl = remember(viewModel) { { id: String -> viewModel.getBackdropUrl(id) } },
                                fallbackImageUrlBuilder = fallbackImageUrlBuilder,
                                onSeerrItemClick = callbacks.onSeerrItemClick,
                                onSeerrRequest = remember(viewModel) { { item: SeerrSearchItem -> viewModel.onEvent(HomeUiEvent.SelectSeerrRequestItem(item)) } },
                                seerrPrefetch = seerrPrefetch,
                                onConfigureSection = onConfigureSection,
                                onConfigureHomeLayout = onConfigureHomeLayout,
                                onConfigureLibraries = onConfigureLibraries,
                                onSeeAllClick = remember(callbacks) { { type, libraryId, collectionType, title -> callbacks.onSeeAllClick(type, libraryId, collectionType, title) } },
                                onFocusedMediaItem = remember { { item: MediaItem -> tvFocusedItem = item } },
                            ),
                            listState = listState,
                            density = density,
                            seerrCardLoadingState = seerrCardLoadingState,
                            heroFocusRequester = heroFocusRequester,
                        )
                    }
                }
            }

                // Lift the search-results-overlay lambdas (recreated per keystroke
                // below) into remembered locals so HomeSearchResultsOverlay's
                // results LazyColumn stops being re-scored each keystroke.
                val searchGetImageUrl = remember(viewModel) { { id: String -> viewModel.getImageUrl(id) } }
                val searchOnJellyfinClick = remember(viewModel, callbacks) {
                    { item: com.raulshma.jellyplay.core.model.MediaItem ->
                        isSearchExpanded = false
                        viewModel.onEvent(HomeUiEvent.ClearSearch)
                        focusManager.clearFocus()
                        callbacks.onItemClick(item.id, item.mediaType, item.parentId, item.name)
                    }
                }
                val searchOnSeerrClick = remember(viewModel, callbacks) {
                    { item: SeerrSearchItem ->
                        isSearchExpanded = false
                        viewModel.onEvent(HomeUiEvent.ClearSearch)
                        focusManager.clearFocus()
                        callbacks.onSearchSeerrClick(item.id, item.mediaType)
                    }
                }
                val searchOnHistoryClick = remember(viewModel) {
                    { query: String -> viewModel.onEvent(HomeUiEvent.UpdateSearchQuery(query)) }
                }
                val searchOnDeleteHistoryItem = remember(viewModel) {
                    { id: Long -> viewModel.onEvent(HomeUiEvent.DeleteSearchHistoryItem(id)) }
                }
                val searchOnClearHistory = remember(viewModel) { { viewModel.onEvent(HomeUiEvent.ClearSearchHistory) } }
                val searchOnSettingsClick = remember(viewModel, callbacks) {
                    { item: com.raulshma.jellyplay.core.ui.settingssearch.ResolvedSettingsItem ->
                        isSearchExpanded = false
                        viewModel.onEvent(HomeUiEvent.ClearSearch)
                        focusManager.clearFocus()
                        viewModel.onEvent(HomeUiEvent.SettingsResultClicked(item))
                        // Inject the matched setting's id as the deep-link scroll/
                        // focus target so the destination screen scrolls to and
                        // highlights it — same behavior as the in-settings search.
                        callbacks.onSettingsSearchItemClick(item.route.withHighlightSettingId(item.id))
                    }
                }

                // Lift the HomeTopDock lambdas. The per-keystroke query string
                // no longer flows through MainHomeContent (it's collected in the
                // HomeTopDockScrim leaf via viewModel.searchQuery), but the
                // lambdas are still hoisted so HomeTopDock stays skippable on
                // the recompositions that DO reach it (scroll, search focus).
                // `isSearchExpanded` is a MutableState delegate and stable.
                val dockOnSearchExpanded = remember { { v: Boolean -> isSearchExpanded = v } }
                val dockOnSearchQueryChange = remember(viewModel) {
                    { q: String -> viewModel.onEvent(HomeUiEvent.UpdateSearchQuery(q)) }
                }
                val dockOnClearSearch = remember(viewModel) {
                    {
                        isSearchExpanded = false
                        viewModel.onEvent(HomeUiEvent.ClearSearch)
                    }
                }
                val dockOnToggleOffline = remember(viewModel) {
                    { viewModel.onEvent(HomeUiEvent.ToggleOfflineMode) }
                }
                val dockOnShowSyncDetails = remember(viewModel) {
                    { showSyncDetails = true }
                }

                HomeTopDockScrim(
                    settingsSearch = viewModel::settingsSearchResults,
                    homeScrollState = homeScrollState,
                    isLightTheme = isLightTheme,
                    isSearchFocused = isSearchFocused,
                    searchQuery = viewModel.searchQuery,
                    includeSettingsResults = state.showSettingsInHomeSearch,
                    offlineMode = state.offlineMode,
                    homeMode = state.homeMode,
                    headerStatus = headerStatus,
                    activeDownloadCount = activeDownloadCount,
                    pendingSyncCount = pendingSyncCount,
                    showClock = state.showClock,
                    homeHeroEnabled = state.homeHeroEnabled,
                    hasFeaturedItem = heroController.featuredItem != null,
                    isTv = isTv,
                    hideTopHeaderOnScroll = state.hideTopHeaderOnScroll,
                    currentUser = state.currentUser,
                    currentServerUsers = currentServerUsers,
                    onUserSwitch = onUserSwitch,
                    onModeChange = callbacks.onModeChange,
                    onSearchExpanded = dockOnSearchExpanded,
                    onSearchQueryChange = dockOnSearchQueryChange,
                    onClearSearch = dockOnClearSearch,
                    onToggleOffline = dockOnToggleOffline,
                    isGoingOnline = state.isGoingOnline,
                    onShowSyncDetails = dockOnShowSyncDetails,
                    onHeroFocusDown = remember(heroFocusRequester) {
                        { heroFocusRequester.tryRequestFocus("top_dock_down_hero") }
                    },
                    searchResultsContent = { settingsResults ->
                        if (state.isSearchActive || searchHistory.isNotEmpty()) {
                            HomeSearchResultsOverlay(
                                jellyfinResults = state.searchState.jellyfinResults,
                                seerrResults = state.searchState.seerrResults,
                                isSearching = state.searchState.isSearching,
                                getImageUrl = searchGetImageUrl,
                                onJellyfinClick = searchOnJellyfinClick,
                                onSeerrClick = searchOnSeerrClick,
                                searchHistory = searchHistory,
                                onHistoryClick = searchOnHistoryClick,
                                onDeleteHistoryItem = searchOnDeleteHistoryItem,
                                onClearHistory = searchOnClearHistory,
                                settingsResults = settingsResults,
                                onSettingsClick = searchOnSettingsClick,
                            )
                        }
                    },
                )


        } // end CompositionLocalProvider(LocalMediaQuickActionController)
        }
    }
    }

    // Long-press / TV-Menu quick actions for home cards.
    MediaQuickActionHost(quickActionController)

    // Delete-confirm for the offline home's quick-action "Delete download".
    pendingDelete?.let { target ->
        val sizeBytes = remember(state.offlineLibrary, target.id) {
            state.offlineLibrary.firstOrNull { it.id == target.id }?.totalSizeBytes ?: 0L
        }
        ConfirmDialog(
            title = stringResource(R.string.home_delete_download_title),
            message = if (sizeBytes > 0L) {
                stringResource(R.string.home_delete_download_message, target.name, sizeBytes.formatBytes())
            } else {
                stringResource(R.string.home_delete_download_message_no_size, target.name)
            },
            confirmText = stringResource(R.string.home_delete_download_confirm),
            dismissText = stringResource(com.raulshma.jellyplay.core.ui.R.string.core_cancel),
            icon = Tabler.Outline.Trash,
            tone = ConfirmTone.DESTRUCTIVE,
            onConfirm = {
                viewModel.onEvent(HomeUiEvent.DeleteOfflineMedia(target))
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }

    // Advanced series delete-episodes sheet — the same one the media-detail
    // screen uses. Shown when a series card's quick-action Delete is tapped;
    // the ViewModel loads the series' seasons/downloaded episodes.
    state.seriesDelete?.let { sd ->
        HomeSeriesDeleteSheet(
            state = sd,
            onDelete = remember(viewModel) { { episodeIds: Set<String> -> viewModel.onEvent(HomeUiEvent.DeleteOfflineEpisodes(episodeIds)) } },
            onDeleteEntireSeries = remember(viewModel, sd.seriesId) { { viewModel.onEvent(HomeUiEvent.DeleteOfflineSeries(sd.seriesId)) } },
            onDismiss = remember(viewModel) { { viewModel.onEvent(HomeUiEvent.DismissSeriesDelete) } },
        )
    }

    state.seerrRequestState.requestItem?.let { item ->
        androidx.compose.runtime.LaunchedEffect(item.id) {
            viewModel.onEvent(HomeUiEvent.LoadSeerrServiceDetails(item.mediaType))
            if (item.mediaType.equals("tv", ignoreCase = true)) {
                viewModel.onEvent(HomeUiEvent.LoadTvSeasons(item.id))
            }
        }

        SeerrRequestDialog(
            item = item,
            snapshot = state.seerrRequestState.snapshot,
            onConfirm = { serverId, profileId, rootFolder, tags, seasons ->
                viewModel.onEvent(HomeUiEvent.RequestSeerrMedia(item, seasons, serverId, profileId, rootFolder, tags))
            },
            onDismiss = {
                viewModel.onEvent(HomeUiEvent.SelectSeerrRequestItem(null))
                viewModel.onEvent(HomeUiEvent.ClearRequestResult)
            },
        )
    }

    // Pending playback-sync details sheet. Opened from the dedicated SyncStatusIcon
    // in the dock; closed via Back or the Close button. The "Sync now" action
    // enqueues the drain worker and dismisses (only enabled while online, since
    // the worker carries a NetworkType.CONNECTED constraint).
    if (showSyncDetails) {
        // Collect pendingSyncEntries / pendingItemDetails ONLY while the sheet
        // is open. Hoisting these collections out of MainHomeContent (and
        // gating them on showSyncDetails) means each outbox write no longer
        // re-subscribes / invalidates the ~510-line orchestrator body — the
        // VM's own StateFlow(WhileSubscribed(5_000)) even pauses the upstream
        // flow while the sheet is closed.
        val entries by viewModel.pendingSyncEntries.collectAsStateWithLifecycle()
        val itemDetails by viewModel.pendingItemDetails.collectAsStateWithLifecycle()
        // Resolve media metadata (offline-first) for the sheet's rows while it's
        // open — keeps the map pruned to the currently-queued ids and avoids any
        // lookup cost when the sheet is closed.
        LaunchedEffect(entries) {
            viewModel.onEvent(HomeUiEvent.EnsurePendingItemDetails(entries.map { it.itemId }))
        }
        SyncDetailsSheet(
            entries = entries,
            itemDetails = itemDetails,
            offlineMode = state.offlineMode,
            onSyncNow = { viewModel.onEvent(HomeUiEvent.SyncNow) },
            onDismiss = { showSyncDetails = false },
        )
    }

    // Inline section-config sheet (long-press a configurable section title).
    // Resolved against the current pref mirrors so toggle/move states are
    // always accurate even if the row that opened it has since recomposed.
    // Per-library (LATEST_MEDIA) rows branch to a per-library hide override
    // and hide the reorder controls, since per-library rows move as a group.
    val target = sectionConfigTarget
    if (target != null && target.type.isConfigurable) {
        val libraryId = target.libraryId
        val perLibrary = libraryId != null
        val order = state.homeSectionOrder
        val index = order.indexOf(target.type)
        val enabled = if (perLibrary) {
            // Per-library: enabled unless the type is in this library's
            // disabled override set (defaults to enabled when absent).
            target.type !in state.libraryHomeSectionOverrides[libraryId].orEmpty()
        } else {
            target.type in state.enabledHomeSectionTypes
        }
        HomeSectionConfigSheet(
            sectionType = target.type,
            enabled = enabled,
            perLibrary = perLibrary,
            position = index,
            total = order.size,
            canMoveUp = index > 0,
            canMoveDown = index in 0..(order.lastIndex - 1),
            onToggleVisible = remember(viewModel, target) {
                { visible ->
                    if (libraryId != null) {
                        viewModel.onEvent(HomeUiEvent.SetLibrarySectionVisible(libraryId, target.type, visible))
                    } else {
                        viewModel.onEvent(HomeUiEvent.SetSectionVisible(target.type, visible))
                    }
                }
            },
            onMoveUp = remember(viewModel, target) { { viewModel.onEvent(HomeUiEvent.MoveSection(target.type, up = true)) } },
            onMoveDown = remember(viewModel, target) { { viewModel.onEvent(HomeUiEvent.MoveSection(target.type, up = false)) } },
            onConfigureLayout = if (perLibrary) onConfigureLibraries else onConfigureHomeLayout,
            onDismiss = dismissSectionConfig,
        )
    }
}

/**
 * Target of the inline section-config sheet. Carries the optional [libraryId]
 * so per-library (LATEST_MEDIA) rows can apply a per-library override instead
 * of a global toggle. UI-only — never persisted, never crosses module bounds.
 */
@androidx.compose.runtime.Immutable
private data class SectionConfigTarget(
    val type: HomeSectionType,
    val libraryId: String? = null,
)

/**
 * The offline home's series delete-episodes sheet — wraps the shared
 * [DeleteDownloadedEpisodesSheet] (the same one the media-detail screen uses)
 * in a [TvSafeSheet]. Renders a centered spinner while the series'
 * seasons/episodes are loading, then the selectable sheet.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun HomeSeriesDeleteSheet(
    state: HomeSeriesDeleteState,
    onDelete: (Set<String>) -> Unit,
    onDeleteEntireSeries: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    TvSafeSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        if (state.isLoading) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 48.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            DeleteDownloadedEpisodesSheet(
                seasons = state.seasons,
                episodes = state.episodesBySeason,
                totalSizeBytes = state.totalSizeBytes,
                episodeSizeBytes = state.episodeSizeBytes,
                onDelete = onDelete,
                onDeleteEntireSeries = onDeleteEntireSeries,
                onDismiss = onDismiss,
            )
        }
    }
}

/**
 * Leaf composable that owns the scroll-coupled app-bar icon colors.
 *
 * Reads `scrollFraction` (a per-pixel-changing value over the first 140 dp of
 * hero scroll) inside its own scope, so only this composable + [HomeTopDock]
 * + the menu button recompose during scroll — [MainHomeContent] (450+ lines)
 * is left untouched. Previously the color computation lived in
 * `MainHomeContent`, which meant every scroll frame re-executed the entire
 * function body.
 *
 * `scrollFraction` is passed as a `() -> Float` getter rather than a `Float`
 * so that reading it (a Compose snapshot read) happens inside this composable's
 * scope, not the caller's.
 */
@Composable
private fun HomeTopDockScrim(
    homeScrollState: HomeScrollState,
    isLightTheme: Boolean,
    isSearchFocused: Boolean,
    searchQuery: StateFlow<String>,
    includeSettingsResults: Boolean,
    offlineMode: com.raulshma.jellyplay.core.model.OfflineMode,
    homeMode: HomeMode,
    headerStatus: HeaderStatus,
    activeDownloadCount: Int,
    pendingSyncCount: Int,
    showClock: Boolean,
    homeHeroEnabled: Boolean,
    hasFeaturedItem: Boolean,
    isTv: Boolean,
    hideTopHeaderOnScroll: Boolean,
    currentUser: com.raulshma.jellyplay.core.model.UserInfo?,
    currentServerUsers: List<com.raulshma.jellyplay.core.model.UserInfo>,
    settingsSearch: (Flow<String>, Context) -> Flow<List<ResolvedSettingsItem>>,
    onUserSwitch: (String) -> Unit,
    onModeChange: (HomeMode) -> Unit,
    onSearchExpanded: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onToggleOffline: () -> Unit,
    isGoingOnline: Boolean = false,
    onShowSyncDetails: () -> Unit = {},
    onHeroFocusDown: () -> Boolean,
    searchResultsContent: @Composable (List<ResolvedSettingsItem>) -> Unit,
) {
    val onSurface = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
    val baseIconColor = if (isLightTheme) onSurface else androidx.compose.ui.graphics.Color.White
    val fraction = homeScrollState.scrollFraction
    val appBarIconColor = lerp(baseIconColor, onSurface, fraction)
    val appBarIconColorFaded = appBarIconColor.copy(alpha = 0.9f)
    // Collect the per-keystroke query string here (a leaf), not in
    // MainHomeContent. Only this composable + HomeTopDock + the TextField
    // recompose on each keystroke; the orchestrator stays untouched. Mirrors
    // the scrollFraction deferral documented at the top of this KDoc.
    val query by searchQuery.collectAsStateWithLifecycle()

    // Local settings search also lives in this leaf: it is pure-local and
    // needs an Android Context to resolve the catalog's @StringRes ids, so the
    // flow is built from the VM-exposed seam (which injects the settings
    // catalog through core/ui's SettingsSearchProvider). Gated by the
    // Appearance toggle — when off, an empty flow keeps the slot idle while
    // preserving the (empty) settings row.
    val settingsContext = LocalContext.current
    val settingsResults by remember(includeSettingsResults, settingsContext, settingsSearch) {
        if (includeSettingsResults) {
            settingsSearch(searchQuery, settingsContext)
        } else {
            kotlinx.coroutines.flow.flowOf(emptyList())
        }
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    // ── Auto-hide top header on scroll ──
    // Mirrors the floating nav-bar hide-on-scroll: hide on scroll-down, reveal
    // on scroll-up, always visible at the very top. The dock is an overlay
    // sibling of the LazyColumn (not a scroll ancestor), so it can't host a
    // NestedScrollConnection; direction is derived here from the shared
    // LazyListState via snapshotFlow. Kept inside this leaf so the 510-line
    // MainHomeContent orchestrator never recomposes on scroll — the same
    // isolation discipline as scrollFraction above. Forced visible while
    // search is focused and disabled entirely on TV.
    val listState = homeScrollState.listState
    val canHide = hideTopHeaderOnScroll && !isTv
    var isHeaderVisible by remember { mutableStateOf(true) }
    val hideThresholdPx = with(LocalDensity.current) { 12.dp.toPx() }
    LaunchedEffect(canHide, isSearchFocused) {
        if (!canHide || isSearchFocused) {
            isHeaderVisible = true
            return@LaunchedEffect
        }
        var prevIndex = listState.firstVisibleItemIndex
        var prevOffset = listState.firstVisibleItemScrollOffset
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.collect { (index, offset) ->
            when {
                index == 0 && offset == 0 -> isHeaderVisible = true
                index > prevIndex -> isHeaderVisible = false
                index < prevIndex -> isHeaderVisible = true
                offset - prevOffset > hideThresholdPx -> isHeaderVisible = false
                prevOffset - offset > hideThresholdPx -> isHeaderVisible = true
            }
            prevIndex = index
            prevOffset = offset
        }
    }
    val hideProgress by animateFloatAsState(
        targetValue = if (isHeaderVisible) 0f else 1f,
        animationSpec = tween(durationMillis = 200),
        label = "topHeaderHide",
    )
    var headerHeightPx by remember { mutableIntStateOf(0) }

    Box(modifier = Modifier.fillMaxSize()) {
        HomeTopDock(
        appBarIconColor = appBarIconColor,
        appBarIconColorFaded = appBarIconColorFaded,
        isSearchFocused = isSearchFocused,
        searchQuery = query,
        offlineMode = offlineMode,
        homeMode = homeMode,
        headerStatus = headerStatus,
        activeDownloadCount = activeDownloadCount,
        pendingSyncCount = pendingSyncCount,
        showClock = showClock,
        currentUser = currentUser,
        currentServerUsers = currentServerUsers,
        onUserSwitch = onUserSwitch,
        onModeChange = onModeChange,
        onSearchExpanded = onSearchExpanded,
        onSearchQueryChange = onSearchQueryChange,
        onClearSearch = onClearSearch,
        onToggleOffline = onToggleOffline,
        isGoingOnline = isGoingOnline,
        onShowSyncDetails = onShowSyncDetails,
        searchResultsContent = { searchResultsContent(settingsResults) },
        modifier = Modifier
            .then(
                if (isTv) {
                    Modifier.onDpadKey(
                        onDown = {
                            if (!isSearchFocused && homeHeroEnabled && hasFeaturedItem) {
                                onHeroFocusDown()
                            } else false
                        }
                    )
                } else Modifier
            )
            .then(
                if (canHide) {
                    Modifier
                        .onSizeChanged { headerHeightPx = it.height }
                        .graphicsLayer {
                            translationY = -headerHeightPx * hideProgress
                            alpha = 1f - hideProgress
                        }
                } else Modifier
            )
    )
    } // end Box(fillMaxSize) wrapper providing BoxScope for align()
}

@Composable
private fun rememberFallbackUrls(
    viewModel: HomeViewModel,
): (com.raulshma.jellyplay.core.model.MediaItem) -> List<String> {
    return remember(viewModel) {
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
