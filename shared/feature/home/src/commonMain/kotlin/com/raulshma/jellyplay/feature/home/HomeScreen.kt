@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, kotlinx.coroutines.FlowPreview::class)
package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.core.ui.components.JellyPlayBackHandler
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
import com.raulshma.jellyplay.core.ui.components.PullToRefreshBox
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Download
import com.raulshma.jellyplay.feature.home.generated.resources.home_error_load_content
import com.raulshma.jellyplay.feature.home.generated.resources.home_go_online_action
import com.raulshma.jellyplay.feature.home.generated.resources.home_implicit_offline_banner
import com.raulshma.jellyplay.feature.home.generated.resources.home_no_downloads_description
import com.raulshma.jellyplay.feature.home.generated.resources.home_no_downloads_yet
import com.raulshma.jellyplay.feature.home.generated.resources.Res
import com.raulshma.jellyplay.core.designsystem.theme.ArtworkThemeWrapper
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.MediaQuickActionScope
import com.raulshma.jellyplay.core.model.quickActions
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.model.seerr.DiscoverSectionType
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.DeleteDownloadedEpisodesSheet
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.ScreenEmptyState
import com.raulshma.jellyplay.core.ui.components.LocalMediaQuickActionController
import com.raulshma.jellyplay.core.ui.components.LocalNavigationBarColor
import com.raulshma.jellyplay.core.ui.components.LocalNetworkStatus
import com.raulshma.jellyplay.core.ui.components.LocalServerHealth
import com.raulshma.jellyplay.core.ui.components.MediaQuickActionHost
import com.raulshma.jellyplay.core.ui.components.QuickAction
import com.raulshma.jellyplay.core.ui.components.RemoveDownloadConfirmHost
import com.raulshma.jellyplay.core.ui.components.SeerrRequestDialog
import com.raulshma.jellyplay.core.ui.components.SeriesDownloadSheet
import com.raulshma.jellyplay.core.ui.components.TvSafeSheet
import com.raulshma.jellyplay.core.ui.components.UndoSnackbarOverlay
import com.raulshma.jellyplay.core.ui.components.rememberMediaQuickActionController
import com.raulshma.jellyplay.core.ui.components.rememberRemoveDownloadState
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
    val onOfflineLibraryClick: () -> Unit = {},
    /** Open an item's detail screen from the inline card long-press Download;
     * [openDownloadSheet] pre-presents the series download sheet there. */
    val onDownloadDetailClick: (itemId: String, openDownloadSheet: Boolean) -> Unit = { _, _ -> },
    val onSeerrItemClick: (tmdbId: Int, mediaType: String) -> Unit = { _, _ -> },
    val onModeChange: (HomeMode) -> Unit = {},
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
internal fun HomeScreen(
    callbacks: HomeCallbacks,
    homeMode: HomeMode = HomeMode.VIDEO,
    musicContent: @Composable () -> Unit = {},
    surpriseRequests: kotlinx.coroutines.flow.Flow<Unit> = kotlinx.coroutines.flow.emptyFlow(),
    viewModel: HomeViewModel = koinViewModel(),
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

    val reportFullyDrawn = rememberReportHomeFullyDrawn()
    var homeFullyDrawn by remember { mutableStateOf(false) }
    LaunchedEffect(state.currentUser != null, !state.isLoading) {
        if (!homeFullyDrawn && state.currentUser != null && !state.isLoading) {
            // Effect bodies run post-composition but before the frame is on
            // screen — wait for the first drawn frame so TTFD measures
            // content the user actually sees.
            withFrameNanos { }
            homeFullyDrawn = true
            reportFullyDrawn()
        }
    }

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

    // Offline home = the normal home content list fed with sections derived
    // from the offline library (#147) — no dedicated offline screen. The whole
    // render model (filtered lists, derived sections, id→item lookup) is
    // derived in ONE place (buildOfflineHomeContent) and passed down as a
    // single value; derived here (above the hero setup, not inside the content
    // lambda) so the hero rotates the same offline titles while offline.
    val offlineTitles = rememberOfflineHomeSectionTitles()
    val offlineContent = remember(
        state.offlineLibrary, state.offlineEpisodes, state.homeMode, offlineTitles,
        state.offlineSectionPrefs, state.offlineLayoutSections,
    ) {
        buildOfflineHomeContent(
            library = state.offlineLibrary,
            episodes = state.offlineEpisodes,
            homeMode = state.homeMode,
            titles = offlineTitles,
            prefs = state.offlineSectionPrefs,
            cachedLayout = state.offlineLayoutSections,
        )
    }
    val offlineSections = offlineContent.sections
    // The screen's entire render-branch policy folded ONCE into a typed
    // surface (see [homeSurface]): which of the four surfaces renders, the
    // content feed, and the hero/banner/quick-action facts derived from the
    // render source — no site below re-derives the predicate from the
    // offlineMode mirror or a private mix of error/sections/mode inputs.
    val surface = remember(state, offlineContent) { homeSurface(state, offlineContent) }
    val contentSurface = surface as? HomeSurface.Content
    // The three render facts as reads of the ONE carried source — a new
    // offline flavour changes no shape here.
    val contentRenderSource = contentSurface?.renderSource
    val renderingOffline = contentRenderSource is HomeRenderSource.Offline
    val explicitOffline = contentRenderSource == HomeRenderSource.Offline.Explicit
    // Server fetch failed but downloads exist -> implicit offline: the same
    // integrated home plus a status banner so the fallback isn't silent.
    val implicitOfflineBanner = if (contentRenderSource == HomeRenderSource.Offline.Implicit) {
        stringResource(Res.string.home_implicit_offline_banner)
    } else null

    // Hero featured-candidate selection — single-pass (was up to 3x flatMap/filter).
    val featuredCandidates = remember(renderingOffline, state.homeMode, state.sections, offlineSections) {
        selectHomeHeroCandidates(renderingOffline, state.homeMode, state.sections, offlineSections)
    }
    val heroFocusRequester = remember { FocusRequester() }
    val mediaImageUrlBuilder = remember(viewModel) { { item: com.raulshma.jellyplay.core.model.MediaItem -> viewModel.getImageUrl(item.id) } }
    val mediaBackdropUrlBuilder = remember(viewModel) { { item: com.raulshma.jellyplay.core.model.MediaItem -> viewModel.getBackdropUrl(item.id) } }
    // Hero artwork while offline resolves to the downloaded item's local
    // backdrop (falling back to its poster) instead of a server URL, which is
    // unreachable offline. Episodes are included so an all-episodes offline
    // library still features artwork. The lookup itself is built once per
    // emission inside [OfflineHomeContent]; the resolver keys on the id+path
    // triples (structurally equal across download-progress emissions) so its
    // identity is stable across ticks and the hero controller below is not
    // rebuilt — resetting its rotation state — while the CONTENT it reads
    // (via currentOfflineContent) is always fresh.
    val currentOfflineContent by rememberUpdatedState(offlineContent)
    val offlineResolverKey = remember(offlineContent) {
        offlineContent.library.map { Triple(it.id, it.backdropPath, it.posterPath) } +
            offlineContent.episodes.map { Triple(it.id, it.backdropPath, it.posterPath) }
    }
    val offlineBackdropResolver = remember(offlineResolverKey) {
        return@remember { id: String ->
            currentOfflineContent.itemsById[id]?.let { it.backdropPath ?: it.posterPath } ?: ""
        }
    }
    val onlineBackdropResolver = remember(viewModel) { { id: String -> viewModel.getBackdropUrl(id) } }
    // The single hero backdrop builder the content list consumes — the parent
    // resolves online-vs-offline once, so the child never re-branches.
    val heroBackdropUrlBuilder = if (renderingOffline) offlineBackdropResolver else onlineBackdropResolver

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
        getBackdropUrl = heroBackdropUrlBuilder,
    )

    // Global nav overflow "Surprise Me" (#115): the hero controller lives here
    // (it owns the hero LazyListState + featured candidates), so the app shell
    // emits a one-shot signal that Home forwards to it.
    androidx.compose.runtime.LaunchedEffect(heroController, surpriseRequests) {
        surpriseRequests.collect { heroController.toggleSurprise() }
    }

    val headerHeight = rememberHeroHeight()

    val bgState = rememberHomeBackgroundState(
        dynamicTheming = state.appearance.dynamicTheming,
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
    // The shared holder (core/ui — see RemoveDownloadState) hoists the pending
    // item so the dialog survives the card leaving composition while it's open.
    val removeDownloadState = rememberRemoveDownloadState()

    // Collected (not read as a .value snapshot inside the resolve lambda) so
    // the resolver is rebuilt when the downloaded set changes — a download
    // completing flips the card's Download↔Remove-download action without
    // waiting for an unrelated recomposition. The set is distinct-collapsed
    // upstream, so active transfers don't churn it.
    val downloadedIds by viewModel.downloadedIds.collectAsStateWithLifecycle()

    // Quick actions on card long-press and the TV Menu key on the focused
    // card. Provided to every PosterCard in scope via
    // CompositionLocal — the cards wire their own long-press.
    val quickActionController = rememberMediaQuickActionController(
        resolveActions = remember(viewModel, downloadedIds, explicitOffline) {
            { item: com.raulshma.jellyplay.core.model.MediaItem ->
                // Download/Remove-download are gated by real download state
                // (works online and off); the offline home additionally offers
                // remove-download for series/seasons via includeRemoveDownload
                // — Explicit offline only (pinned behaviour; see HomeSurface).
                item.quickActions(
                    MediaQuickActionScope.HOME,
                    includeDownload = true,
                    includeRemoveDownload = explicitOffline,
                    isDownloaded = item.id in downloadedIds,
                )
            }
        },
        executeAction = remember(viewModel, mediaOnItemClick, mediaOnPlayClick, callbacks, removeDownloadState) {
            { item: com.raulshma.jellyplay.core.model.MediaItem, action: QuickAction ->
                // The routing table lives in homeQuickActionEffect (pure,
                // pinned by HomeQuickActionsTest); this dispatch is mechanical.
                when (
                    val effect = homeQuickActionEffect(
                        item,
                        action,
                        onOpenDetail = { itemId, openDownloadSheet ->
                            callbacks.onDownloadDetailClick(itemId, openDownloadSheet)
                        },
                    )
                ) {
                    is HomeQuickActionEffect.Play -> mediaOnPlayClick(effect.item)
                    is HomeQuickActionEffect.MarkPlayed ->
                        viewModel.onEvent(
                            if (effect.played) HomeUiEvent.MarkItemPlayed(effect.item)
                            else HomeUiEvent.MarkItemUnplayed(effect.item),
                        )
                    is HomeQuickActionEffect.ShowDetails -> mediaOnItemClick(effect.item)
                    is HomeQuickActionEffect.OpenSeriesDownloadSheet ->
                        viewModel.onEvent(HomeUiEvent.RequestSeriesDownload(effect.series))
                    is HomeQuickActionEffect.StartDownload ->
                        viewModel.onEvent(HomeUiEvent.DownloadItem(effect.item, effect.onOpenDetail))
                    is HomeQuickActionEffect.OpenSeriesDeleteSheet ->
                        viewModel.onEvent(HomeUiEvent.RequestSeriesDelete(effect.series))
                    is HomeQuickActionEffect.ConfirmDeleteDownload -> removeDownloadState.request(effect.item)
                    HomeQuickActionEffect.None -> Unit
                }
            }
        },
    )
    // TV-only: the card currently holding D-pad focus, so the Menu key can open
    // its quick actions. Rows report via HomeContentCallbacks.
    var tvFocusedItem by remember { mutableStateOf<com.raulshma.jellyplay.core.model.MediaItem?>(null) }

    // Only photo-folder items are relevant to the prefetcher (it filters to
    // PHOTO_FOLDER internally), so narrow the list to those items. This keeps
    // both the per-emission allocation and the effect-key proportional to the
    // number of photo folders rather than every item across all sections.
    val photoFolderItems = remember(state.sections) {
        state.sections.asSequence().flatMap { it.items }.filter { it.mediaType == MediaType.PHOTO_FOLDER }.toList()
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

    // The search SESSION (expanded flag + close ordering) lives in one holder
    // — see HomeSearchSession. The data half stays in the VM's search holder;
    // `isSearchFocused` folds both: VM-active (a live query) OR locally
    // expanded (the field is open even before/after typing).
    val searchSession = remember(viewModel) { HomeSearchSession(viewModel::onEvent) }
    val closeSearch = remember(searchSession) { { searchSession.close { focusManager.clearFocus() } } }
    val currentState by rememberUpdatedState(state)
    val isSearchFocused by remember { derivedStateOf { currentState.isSearchActive || searchSession.isExpanded } }

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

    JellyPlayBackHandler(enabled = isSearchFocused) {
        closeSearch()
    }

    ArtworkThemeWrapper(
        imageUrl = heroController.backdropUrl,
        dynamicTheming = state.appearance.dynamicTheming,
        darkTheme = !isLightTheme,
        oledMode = state.appearance.oledMode,
        colorStyle = state.appearance.colorStyle,
        accentColorSwatch = state.appearance.accentColorSwatch,
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
                    performanceMode = state.appearance.performanceMode,
                    oledMode = state.appearance.oledMode,
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
                // The render branch is one exhaustive `when` over the folded
                // surface (see [homeSurface]) — this scope decides nothing
                // about WHICH surface renders; each case only renders it.
                when (val s = surface) {
                    is HomeSurface.HardError -> {
                        ErrorScreen(
                            message = stringResource(Res.string.home_error_load_content),
                            onRetry = { viewModel.onEvent(HomeUiEvent.Refresh) },
                            modifier = Modifier.padding(horizontal = contentPad),
                        )
                    }
                    // Explicitly offline (manual or auto) with nothing downloaded.
                    is HomeSurface.NoDownloads -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(backgroundColor),
                            contentAlignment = Alignment.Center,
                        ) {
                            ScreenEmptyState(
                                icon = Tabler.Outline.Download,
                                title = stringResource(Res.string.home_no_downloads_yet),
                                description = stringResource(Res.string.home_no_downloads_description),
                                actionLabel = stringResource(Res.string.home_go_online_action),
                                onAction = { viewModel.onEvent(HomeUiEvent.ToggleOfflineMode) },
                                actionLoading = s.isGoingOnline,
                                modifier = Modifier.padding(horizontal = contentPad),
                            )
                        }
                    }
                    HomeSurface.Music -> {
                        musicContent()
                    }
                    is HomeSurface.Content -> {
                        HomeContentList(
                            state = HomeContentState(
                                // WHAT renders was decided by the fold — the
                                // feed carries the online or offline surface
                                // with the offline-only masks (spinner,
                                // banners) and the FallbackPending loading
                                // window already applied.
                                feed = s.feed,
                                homeHeroEnabled = state.homeHeroEnabled,
                                homeBackdropEnabled = state.homeBackdropEnabled,
                                discoverEnabled = state.discoverEnabled,
                                experimentalCardClippingEnabled = state.experimentalCardClippingEnabled,
                                featuredItem = heroController.featuredItem,
                                backgroundColor = backgroundColor,
                                contentPad = contentPad,
                                headerHeight = headerHeight,
                                isLightTheme = isLightTheme,
                                continueWatchingClickBehavior = state.continueWatchingClickBehavior,
                                discoverRows = discoverRows,
                                allDiscoverItems = allDiscoverItems,
                                recentlyGrabbed = state.recentlyGrabbed,
                                statusBanner = implicitOfflineBanner,
                            ),
                            callbacks = HomeContentCallbacks(
                                onRetrySectionLoad = remember(viewModel) { { viewModel.onEvent(HomeUiEvent.Refresh) } },
                                onDismissNewsletterBanner = remember(viewModel) { { viewModel.onEvent(HomeUiEvent.DismissNewsletterBanner) } },
                                onNewsletterClick = callbacks.onNewsletterClick,
                                onOfflineLibraryClick = callbacks.onOfflineLibraryClick,
                                onItemClick = remember(callbacks) { { id: String -> callbacks.onItemClick(id, MediaType.UNKNOWN, null, "") } },
                                onFocusChange = remember { { focused: Boolean -> heroController.onFocusChange(focused) } },
                                mediaOnItemClick = mediaOnItemClick,
                                mediaOnPlayClick = mediaOnPlayClick,
                                mediaImageUrlBuilder = mediaImageUrlBuilder,
                                mediaBackdropUrlBuilder = mediaBackdropUrlBuilder,
                                getImageUrl = remember(viewModel) { { id: String -> viewModel.getImageUrl(id) } },
                                getBackdropUrl = onlineBackdropResolver,
                                heroBackdropUrlBuilder = heroBackdropUrlBuilder,
                                fallbackImageUrlBuilder = fallbackImageUrlBuilder,
                                photoFolderChildUrlsFor = remember(viewModel) { { id: String -> viewModel.photoFolderChildUrlsFor(id) } },
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
                val searchOnJellyfinClick = remember(viewModel, callbacks, closeSearch) {
                    { item: com.raulshma.jellyplay.core.model.MediaItem ->
                        closeSearch()
                        callbacks.onItemClick(item.id, item.mediaType, item.parentId, item.name)
                    }
                }
                val searchOnSeerrClick = remember(viewModel, callbacks, closeSearch) {
                    { item: SeerrSearchItem ->
                        closeSearch()
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
                val searchOnSettingsClick = remember(viewModel, callbacks, closeSearch) {
                    { item: com.raulshma.jellyplay.core.ui.settingssearch.ResolvedSettingsItem ->
                        closeSearch()
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
                val dockOnSearchExpanded = remember(searchSession, closeSearch) {
                    { v: Boolean -> if (v) searchSession.open() else closeSearch() }
                }
                val dockOnSearchQueryChange = remember(viewModel) {
                    { q: String -> viewModel.onEvent(HomeUiEvent.UpdateSearchQuery(q)) }
                }
                val dockOnClearSearch = remember(viewModel, closeSearch) {
                    { closeSearch() }
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
                    searchQuery = viewModel.searchQuery,
                    includeSettingsResults = state.showSettingsInHomeSearch,
                    isTv = isTv,
                    dockState = HomeDockState(
                        offlineMode = state.offlineMode,
                        homeMode = state.homeMode,
                        headerStatus = headerStatus,
                        pendingSyncCount = pendingSyncCount,
                        showClock = state.showClock,
                        currentUser = state.currentUser,
                        currentServerUsers = currentServerUsers,
                        isSearchFocused = isSearchFocused,
                        isGoingOnline = state.isGoingOnline,
                        homeHeroEnabled = state.homeHeroEnabled,
                        hasFeaturedItem = heroController.featuredItem != null,
                        hideTopHeaderOnScroll = state.hideTopHeaderOnScroll,
                    ),
                    dockCallbacks = HomeDockCallbacks(
                        onUserSwitch = onUserSwitch,
                        onModeChange = callbacks.onModeChange,
                        onSearchExpanded = dockOnSearchExpanded,
                        onSearchQueryChange = dockOnSearchQueryChange,
                        onClearSearch = dockOnClearSearch,
                        onToggleOffline = dockOnToggleOffline,
                        onShowSyncDetails = dockOnShowSyncDetails,
                    ),
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

    // Delete-confirm for the offline home's quick-action "Delete download" —
    // the shared remove-download dialog every quick-action host renders. The
    // series delete-episodes sheet below is separate by design: series cards
    // keep their per-episode selection.
    RemoveDownloadConfirmHost(
        state = removeDownloadState,
        onConfirmRemove = { item -> viewModel.onEvent(HomeUiEvent.DeleteOfflineMedia(item)) },
    )

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

    // Series download sheet — the same one the media-detail screen uses. Shown
    // when a series card's quick-action Download is tapped; the ViewModel
    // assembles the series' seasons/episodes from the episode catalogue.
    state.seriesDownload?.let { sd ->
        HomeSeriesDownloadSheet(
            state = sd,
            onLoadEpisodes = remember(viewModel) { { seasonId: String -> viewModel.onEvent(HomeUiEvent.LoadSeriesDownloadEpisodes(seasonId)) } },
            onDownload = remember(viewModel) { { selection: Map<String, List<String>> -> viewModel.onEvent(HomeUiEvent.DownloadSeries(selection)) } },
            onDismiss = remember(viewModel) { { viewModel.onEvent(HomeUiEvent.DismissSeriesDownload) } },
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
        val capabilities = remember(
            target, state.sectionConfig,
        ) {
            sectionConfigCapabilities(
                type = target.type,
                libraryId = target.libraryId,
                order = state.sectionConfig.homeSectionOrder,
                enabledTypes = state.sectionConfig.enabledHomeSectionTypes,
                libraryOverrides = state.sectionConfig.libraryHomeSectionOverrides,
            )
        }
        HomeSectionConfigSheet(
            sectionType = target.type,
            capabilities = capabilities,
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
            onConfigureLayout = if (capabilities.perLibrary) onConfigureLibraries else onConfigureHomeLayout,
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
private fun HomeSeriesDownloadSheet(
    state: HomeSeriesDownloadState,
    onLoadEpisodes: (seasonId: String) -> Unit,
    onDownload: (selectedEpisodes: Map<String, List<String>>) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    TvSafeSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        SeriesDownloadSheet(
            seasons = state.seasons,
            episodes = state.episodesBySeason,
            loadingSeasons = state.loadingSeasons,
            downloadedEpisodeIds = state.downloadedEpisodeIds,
            onLoadEpisodes = onLoadEpisodes,
            isDownloading = false,
            onDownload = onDownload,
            onDismiss = onDismiss,
        )
    }
}

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
    searchQuery: StateFlow<String>,
    includeSettingsResults: Boolean,
    isTv: Boolean,
    dockState: HomeDockState,
    dockCallbacks: HomeDockCallbacks,
    settingsSearch: (Flow<String>) -> Flow<List<ResolvedSettingsItem>>,
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
    // resolves the catalog's Compose-Resources strings itself, so the flow is
    // built from the VM-exposed seam (which injects the settings catalog
    // through core/ui's SettingsSearchProvider). Gated by the Appearance
    // toggle — when off, an empty flow keeps the slot idle while preserving
    // the (empty) settings row.
    val settingsResults by remember(includeSettingsResults, settingsSearch) {
        if (includeSettingsResults) {
            settingsSearch(searchQuery)
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
    val canHide = dockState.hideTopHeaderOnScroll && !isTv
    var isHeaderVisible by remember { mutableStateOf(true) }
    val hideThresholdPx = with(LocalDensity.current) { 12.dp.toPx() }
    LaunchedEffect(canHide, dockState.isSearchFocused) {
        if (!canHide || dockState.isSearchFocused) {
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
        searchQuery = query,
        state = dockState,
        callbacks = dockCallbacks,
        searchResultsContent = { searchResultsContent(settingsResults) },
        modifier = Modifier
            .then(
                if (isTv) {
                    Modifier.onDpadKey(
                        onDown = {
                            if (!dockState.isSearchFocused && dockState.homeHeroEnabled && dockState.hasFeaturedItem) {
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
