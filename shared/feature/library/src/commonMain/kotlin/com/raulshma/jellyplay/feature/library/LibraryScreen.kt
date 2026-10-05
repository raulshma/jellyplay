package com.raulshma.jellyplay.feature.library

import com.raulshma.jellyplay.core.data.error.UserErrorMessages
import com.raulshma.jellyplay.core.ui.components.JellyPlayBackHandler
import com.raulshma.jellyplay.core.ui.components.DeferredRefreshEffect
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import com.raulshma.jellyplay.core.ui.components.clearFloatingNav
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import com.raulshma.jellyplay.core.ui.components.JellyPlayLinearProgressIndicator
import com.raulshma.jellyplay.core.ui.components.libraryListSubtitle
import com.raulshma.jellyplay.core.ui.components.displayTitle
import com.raulshma.jellyplay.core.ui.components.rememberSeriesImageFallback
import com.raulshma.jellyplay.core.ui.components.rememberProgressFraction
import androidx.compose.material3.MaterialTheme
import com.raulshma.jellyplay.core.ui.components.PullToRefreshBox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.raulshma.jellyplay.core.ui.util.safeItemKey
import com.raulshma.jellyplay.core.designsystem.theme.LocalIsLightTheme
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.defaultEffectsTween
import com.raulshma.jellyplay.core.ui.components.AppendErrorFooter
import com.raulshma.jellyplay.core.ui.components.CircleBgBackButton
import com.raulshma.jellyplay.core.ui.components.ActiveFiltersBar
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.DelayedLoadingScreen
import com.raulshma.jellyplay.core.designsystem.theme.detailEntrance
import com.raulshma.jellyplay.core.designsystem.theme.rememberDetailEntrance
import com.raulshma.jellyplay.core.ui.components.LoadingScreen
import com.raulshma.jellyplay.core.ui.components.ScreenEmptyState
import com.raulshma.jellyplay.core.ui.components.PagedAppendRung
import com.raulshma.jellyplay.core.ui.components.PagedCollectionRung
import com.raulshma.jellyplay.core.ui.components.pagedAppendRung
import com.raulshma.jellyplay.core.ui.components.pagedCollectionRung
import com.raulshma.jellyplay.core.ui.components.rememberSimpleCollectionStatus
import com.raulshma.jellyplay.core.ui.components.toPagedRefreshPhase
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.core.ui.components.LocalAnimatedVisibilityScope
import com.raulshma.jellyplay.core.ui.model.coreClearFiltersLabel
import com.raulshma.jellyplay.core.ui.components.PosterCard
import com.raulshma.jellyplay.core.ui.components.LocalMediaQuickActionController
import com.raulshma.jellyplay.core.ui.components.QuickActionAdapter
import com.raulshma.jellyplay.core.ui.components.QuickActionIntakeHost
import com.raulshma.jellyplay.core.ui.components.rememberQuickActionIntake
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.*
import com.raulshma.jellyplay.core.ui.tv.LocalTvDrawerOpener
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.TvFocusableGrid
import com.raulshma.jellyplay.core.ui.tv.TvFocusablePagingColumn
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.ifElse
import com.raulshma.jellyplay.core.ui.tv.rememberInt
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.raulshma.jellyplay.core.ui.tv.input.onDpadKey
import com.raulshma.jellyplay.core.model.GroupBy
import com.raulshma.jellyplay.core.model.LibraryFilterDimension
import com.raulshma.jellyplay.core.model.LibraryViewMode
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaQuickActionScope
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.feature.library.components.LibraryFilterSheet
import com.raulshma.jellyplay.feature.library.components.GroupedLibraryContent
import com.raulshma.jellyplay.feature.library.components.LibraryFilterChipRow
import com.raulshma.jellyplay.feature.library.components.LibraryActionChipRow
import com.raulshma.jellyplay.feature.library.components.FilterSheetKind
import com.raulshma.jellyplay.feature.library.components.SortFilterSheet
import com.raulshma.jellyplay.feature.library.components.MediaTypeFilterSheet
import com.raulshma.jellyplay.feature.library.components.StatusFilterSheet
import com.raulshma.jellyplay.feature.library.components.GenreFilterSheet
import com.raulshma.jellyplay.feature.library.components.TagFilterSheet
import com.raulshma.jellyplay.feature.library.components.YearRangeFilterSheet
import com.raulshma.jellyplay.feature.library.components.LibraryListItem
import com.raulshma.jellyplay.feature.library.components.LibraryResetConfirmDialog
import com.raulshma.jellyplay.feature.library.components.LibraryListContent
import com.raulshma.jellyplay.feature.library.components.LibraryThumbContent
import com.raulshma.jellyplay.feature.library.components.LibraryGridContent
import com.raulshma.jellyplay.feature.library.components.LibraryMasonryContent
import com.raulshma.jellyplay.feature.library.components.ThumbCard
import com.raulshma.jellyplay.feature.library.components.AlphabetJumpRail
import com.raulshma.jellyplay.feature.library.components.ErrorAwareStatusIndicator
import com.raulshma.jellyplay.feature.library.components.GlassPill
import com.raulshma.jellyplay.feature.library.components.groupByLabel
import com.raulshma.jellyplay.core.ui.animation.isReducedMotion
import com.raulshma.jellyplay.core.ui.animation.lazyItemPlacementSpec
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.feature.library.generated.resources.Res
import com.raulshma.jellyplay.feature.library.generated.resources.library_action_group
import com.raulshma.jellyplay.feature.library.generated.resources.library_all
import com.raulshma.jellyplay.feature.library.generated.resources.library_failed_to_load_items
import com.raulshma.jellyplay.feature.library.generated.resources.library_failed_to_load_more_items
import com.raulshma.jellyplay.feature.library.generated.resources.library_filters
import com.raulshma.jellyplay.feature.library.generated.resources.library_group_by
import com.raulshma.jellyplay.feature.library.generated.resources.library_item_count
import com.raulshma.jellyplay.feature.library.generated.resources.library_mood_playlists
import com.raulshma.jellyplay.feature.library.generated.resources.library_no_downloads_offline
import com.raulshma.jellyplay.feature.library.generated.resources.library_no_items_found
import com.raulshma.jellyplay.feature.library.generated.resources.library_playlists
import com.raulshma.jellyplay.feature.library.generated.resources.library_poster_size
import com.raulshma.jellyplay.feature.library.generated.resources.library_reset
import com.raulshma.jellyplay.feature.library.generated.resources.library_shuffle
import com.raulshma.jellyplay.feature.library.generated.resources.library_smart_playlists
import com.raulshma.jellyplay.feature.library.generated.resources.library_title
import kotlinx.coroutines.launch

/**
 * Which auxiliary sheet the Library screen has open, if any: one of the
 * per-filter selection sheets (carrying its [FilterSheetKind]), the
 * poster-size slider sheet, or the group-by sheet. One nullable state behind
 * the former mixed vocabulary (a nullable `FilterSheetKind` + two booleans +
 * a hand-derived `isAnySheetOpen`).
 */
sealed interface LibrarySheet {
    /** One of the per-filter immediate-apply selection sheets. */
    data class Filter(val kind: FilterSheetKind) : LibrarySheet

    /** The poster-size slider sheet. */
    data object PosterSize : LibrarySheet

    /** The group-by chip sheet. */
    data object GroupBy : LibrarySheet
}

/** One arm of the Library screen's ordered back ladder. */
sealed interface LibraryBackAction {
    data object DismissResetDialog : LibraryBackAction
    data object CloseFilters : LibraryBackAction
    data object CloseSheet : LibraryBackAction
    data object ClearFilters : LibraryBackAction
}

/**
 * The Library screen's exhaustive back fold: reset dialog → full filter sheet
 * → open [LibrarySheet] → clear filters. `clearFilters` is the DECLARED
 * trailing arm of the fold (not an ad-hoc last rung of a back stack), so a new
 * sheet type joins [LibrarySheet] and this fold only. Null when back should
 * leave the screen.
 */
fun libraryBackAction(
    resetDialogVisible: Boolean,
    showFilters: Boolean,
    openSheet: LibrarySheet?,
    inSectionMode: Boolean,
    hasActiveFilters: Boolean,
): LibraryBackAction? = when {
    resetDialogVisible -> LibraryBackAction.DismissResetDialog
    showFilters -> LibraryBackAction.CloseFilters
    openSheet != null -> LibraryBackAction.CloseSheet
    !inSectionMode && hasActiveFilters -> LibraryBackAction.ClearFilters
    else -> null
}

/**
 * This screen's active-filter bar vocabulary: which dimensions render as
 * dismiss tags, and in which order — preserved verbatim from the hand-rolled
 * bar this replaces (media types → status → downloaded → genres; search passes
 * its own list). The shared [ActiveFiltersBar] renders whatever it is given,
 * so a dimension only one screen surfaces stays a caller decision. Note the
 * Status sheet's resumable toggle deliberately does NOT add a tag here (same
 * as before): an active resumable filter shows the bar with just the clear-all
 * chip.
 */
private val libraryActiveFilterDimensions = listOf(
    LibraryFilterDimension.MEDIA_TYPES,
    LibraryFilterDimension.PLAYED_STATUS,
    LibraryFilterDimension.IS_DOWNLOADED,
    LibraryFilterDimension.GENRES,
)

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
)
@Composable
internal fun LibraryScreen(
    onItemClick: (itemId: String, mediaType: MediaType, parentId: String?, itemName: String) -> Unit,
    onSmartPlaylistsClick: () -> Unit = {},
    onMoodPlaylistsClick: () -> Unit = {},
    onPlaylistsClick: () -> Unit = {},
    /** Open an item's detail screen from the inline card long-press Download;
     * [openDownloadSheet] pre-presents the series download sheet there. */
    onOpenDownloadDetail: (itemId: String, openDownloadSheet: Boolean) -> Unit = { _, _ -> },
    sectionContext: com.raulshma.jellyplay.core.model.LibrarySectionContext? = null,
    onBack: (() -> Unit)? = null,
    viewModel: LibraryViewModel = koinViewModel(),
) {
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    DeferredRefreshEffect(viewModel.deferredRefresher)

    // One browser-state object owns {folder, filters, viewMode, groupBy,
    // posterSize, sectionContext, title} — replacing 13 individual
    // collectAsStateWithLifecycle reads that re-derived presentation from
    // scattered sources. "In section mode" now derives from the state, unifying
    // the two drift-prone sources (screen used to trust the nav-arg, the VM
    // trusted its own _sectionContext). The nav-arg still drives the
    // LaunchedEffect below; it just stops being a parallel source of truth for
    // gating.
    val browser by viewModel.browserState.collectAsStateWithLifecycle()
    // Destructured from the single browser subscription above (one state read,
    // not 13). The body keeps the plain field names for readability.
    val selectedFolder = browser.folder
    val filters = browser.filters
    val viewMode = browser.viewMode
    val sectionTitle = browser.title
    val posterSize = browser.posterSize
    val groupBy = browser.groupBy
    val genres by viewModel.genres.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val showFilters by viewModel.showFilters.collectAsStateWithLifecycle()
    val resetDialogVisible by viewModel.resetDialogVisible.collectAsStateWithLifecycle()
    // Offline mode auto-filters the grid to downloads (#147); the flag pins
    // the Downloaded chip and swaps the empty-state copy.
    val offlineAutoFilter by viewModel.offlineAutoFilter.collectAsStateWithLifecycle()

    // Section mode: configure the VM once with the injected context. Idempotent
    // (configureSection early-returns on an equal context) so recomposition is
    // safe. In tab mode (sectionContext == null) clear any leftover section state
    // so the Library tab shows its default view — the VM is shared across the
    // tab and the section deep-link, so without this reset the "Latest X"
    // filters/sort would leak into the tab (issue #113).
    LaunchedEffect(sectionContext) {
        if (sectionContext != null) {
            viewModel.onEvent(LibraryUiEvent.ConfigureSection(sectionContext))
        } else {
            viewModel.onEvent(LibraryUiEvent.ClearSectionMode)
        }
    }

    val pagedItems = viewModel.pagedItems.collectAsLazyPagingItems()

    // Prefetch photo-folder child urls on load-state transitions (append/refresh)
    // instead of snapshot-list identity. Keying on the snapshot re-fired the
    // prefetch on every page boundary, and each merge produced a new Map in
    // _photoFolderChildUrls — which used to invalidate the whole screen (now
    // mitigated by per-item collection). Gating on loadState also lets
    // us skip non-photo libraries entirely.
    val appendState = pagedItems.loadState
    LaunchedEffect(appendState) {
        val snapshot = pagedItems.itemSnapshotList
        if (snapshot.items.any { it.mediaType == MediaType.PHOTO_FOLDER }) {
            viewModel.onEvent(LibraryUiEvent.PrefetchPhotoFolderChildUrls(snapshot.items))
        }
    }
    // The VM's loading/error flags (not the paging load states — the library
    // pager is event-driven) through the shared header-status seam.
    val headerStatus = rememberSimpleCollectionStatus(
        isLoading = isLoading,
        hasError = error != null,
    )

    val gridState = rememberLazyGridState(
        cacheWindow = com.raulshma.jellyplay.core.ui.tv.TvGridCacheWindow,
    )
    val listState = rememberLazyListState()
    // Hoisted (not local to the MASONRY branch) so the alphabet rail can drive
    // the staggered grid's scroll state from the screen root.
    val staggeredState = rememberLazyStaggeredGridState()
    // Derives from the browser state (the single source of truth), not the
    // nav-arg parameter — the two used to drift. The nav-arg still drives the
    // configureSection/clearSectionMode LaunchedEffect above.
    val inSectionMode = browser.isSection
    // Which (if any) auxiliary sheet is open (filter/poster-size/group-by).
    // Null = none. Hoisted here so the chips toggle it and the matching sheet
    // renders at the screen root; the back ladder folds over it via
    // [libraryBackAction].
    var openSheet by remember { mutableStateOf<LibrarySheet?>(null) }
    // The canonical fold on [LibraryFilters] — the same one the search screen
    // reads. Previously this hand-rolled copy omitted years/tags/minRating/
    // sort/resumable, so the badge and BackHandler guard under-reported the
    // active set.
    val hasActiveFilters by remember {
        derivedStateOf { browser.filters.hasActiveFilters() }
    }
    val backHandlerEnabled = showFilters || openSheet != null || resetDialogVisible || (!inSectionMode && hasActiveFilters)

    JellyPlayBackHandler(enabled = backHandlerEnabled) {
        when (libraryBackAction(resetDialogVisible, showFilters, openSheet, inSectionMode, hasActiveFilters)) {
            LibraryBackAction.DismissResetDialog -> viewModel.onEvent(LibraryUiEvent.DismissResetDialog)
            LibraryBackAction.CloseFilters -> viewModel.onEvent(LibraryUiEvent.ToggleFilters) // closes when open
            LibraryBackAction.CloseSheet -> openSheet = null
            LibraryBackAction.ClearFilters -> viewModel.onEvent(LibraryUiEvent.ClearFilters)
            null -> {}
        }
    }

    // ── TV header focus chain ──────────────────────────────────────────────────
    // Geometric D-pad search between the stacked header rows is unreliable: the
    // chip rows' focus bounds overlap vertically and the alphabet rail interleaves
    // with them on the right edge, so vertical hops out of each header row are
    // intercepted by that row itself and redirected to a leaf FocusRequester on
    // the neighbouring row's first chip — leaf grants always land on a real
    // focusable, unlike group-entry grants which can park focus invisibly on the
    // group node. Scoping the interception per-row (rather than at the screen
    // root with shared "which row holds focus" state) makes the routing static:
    // a row's handler only ever sees keys pressed while focus is inside that
    // row, so stale tracking can never send a hop to the wrong row.
    val showFolderRow = !inSectionMode && folders.size > 1
    val resetPillFocus = remember { FocusRequester() }
    val backFocus = remember { FocusRequester() }
    val firstFolderPillFocus = remember { FocusRequester() }
    val firstFilterChipFocus = remember { FocusRequester() }
    val firstActionChipFocus = remember { FocusRequester() }
    // The title row's focusable anchor: the back button in section mode, the
    // Reset pill otherwise (the title text itself is not focusable).
    val headerEntryLeaf = when {
        onBack != null -> backFocus
        !inSectionMode -> resetPillFocus
        else -> null
    }
    // First row below the title row: the folder pills when shown, else the
    // filter chip row. Null when neither exists.
    val rowBelowTitleLeaf = if (showFolderRow) firstFolderPillFocus else headerEntryLeaf?.let { firstFilterChipFocus }
    // First row above the filter chip row: the folder pills when shown, else
    // the title row. Null when neither exists.
    val rowAboveFilterLeaf = if (showFolderRow) firstFolderPillFocus else headerEntryLeaf

    // Collected (not read as a .value snapshot inside the intake's
    // isDownloaded lambda) so the resolver is rebuilt when the downloaded set
    // changes — a download completing flips the card's Download↔Remove-download
    // action without waiting for an unrelated recomposition. The set is
    // distinct-collapsed upstream, so active transfers don't churn it.
    val downloadedIds by viewModel.downloadedIds.collectAsStateWithLifecycle()

    // Long-press / TV-Menu quick actions for the grid. The shared intake
    // (core/ui — see QuickActionIntake) owns the sheet controller, the
    // remove-download confirm and the TV focus key; this screen supplies
    // only its routing adapter over the shared effect fold.
    val quickActionIntake = rememberQuickActionIntake(
        scope = MediaQuickActionScope.LIBRARY,
        includeDownload = viewModel.downloadSupported,
        includeAddToPlaylist = true,
        includeFavorite = true,
        // Downloaded items flip the download slot to "Remove download"
        // instead of offering both.
        isDownloaded = remember(downloadedIds) {
            { item: MediaItem -> downloadedIds.contains(item.id) }
        },
        adapter = remember(viewModel, onItemClick, onOpenDownloadDetail) {
            QuickActionAdapter(
                onPlay = { item -> onItemClick(item.id, item.mediaType, item.parentId, item.name) },
                onOpenDetail = { item -> onItemClick(item.id, item.mediaType, item.parentId, item.name) },
                onMarkPlayed = { item, played ->
                    viewModel.onEvent(LibraryUiEvent.MarkItemPlayed(item, played))
                },
                onToggleFavorite = { item -> viewModel.onEvent(LibraryUiEvent.ToggleFavorite(item)) },
                // Single-stream items start inline at the default quality;
                // series (and other non-inline types) open the detail screen —
                // for a series with the download sheet pre-presented.
                onDownload = { item ->
                    viewModel.onEvent(LibraryUiEvent.DownloadItem(item, onOpenDetail = onOpenDownloadDetail))
                },
                onRemoveDownload = { item -> viewModel.onEvent(LibraryUiEvent.RemoveItemDownload(item)) },
            )
        },
    )

    val backgroundColor = MaterialTheme.colorScheme.background
    val headerGradientBrush = remember(backgroundColor) {
        Brush.verticalGradient(
            colors = listOf(
                backgroundColor.copy(alpha = 0.95f),
                backgroundColor,
            ),
        )
    }


    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    val contentPad = adaptiveInfo.contentPadding(isTv)
    val spacing = adaptiveInfo.itemSpacing(isTv)
    val bottomPad = adaptiveInfo.bottomPadding(isTv)

    val gridPadding = PaddingValues(
        start = contentPad,
        end = contentPad,
        top = 8.dp,
        bottom = bottomPad,
    )

    val gridCellSize = adaptiveInfo.gridCellSize(isTv) / browser.posterSize
    // Landscape thumbnails are wider than they are tall (16:9), so the THUMB
    // grid needs a larger min cell width than the poster (2:3) grid to avoid
    // rendering tiny cards. Scaled from the same adaptive baseline.
    val thumbCellSize = adaptiveInfo.gridCellSize(isTv) / browser.posterSize * (16f / 9f) * (3f / 4f)

    // Stable image resolvers for the view-mode contents: one lambda identity per VM
    // (the former branches remembered the same wrappers inline per item lambda).
    val getImageUrl = remember(viewModel) { { id: String -> viewModel.getImageUrl(id) } }
    val getBackdropUrl = remember(viewModel) { { id: String -> viewModel.getBackdropUrl(id) } }
    val photoFolderChildUrlsFor = remember(viewModel) { { id: String -> viewModel.photoFolderChildUrlsFor(id) } }

    // D-pad Left at any content left edge expands the navigation drawer. The
    // exit hook fires only when focus actually leaves this subtree leftward —
    // i.e. from its leftmost focusable — so mid-row horizontal D-pad scrolling
    // is unaffected. Belt-and-suspenders on top of the native rail focus entry:
    // when that works the drawer opens either way; when the geometric search
    // comes back empty, this still expands it.
    val openTvDrawer = LocalTvDrawerOpener.current
    fun Modifier.openDrawerOnLeftExit(): Modifier = ifElse(
        isTv,
        Modifier.focusProperties {
            @Suppress("DEPRECATION")
            exit = { direction ->
                if (direction == FocusDirection.Left) openTvDrawer()
                FocusRequester.Default
            }
        },
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .onDpadKey(
                onMenu = {
                    quickActionIntake.openFocusedItem()
                    true
                },
            ),
    ) {
        CompositionLocalProvider(LocalMediaQuickActionController provides quickActionIntake.controller) {
        when (val surface = computeLibrarySurface(error = error, itemCount = pagedItems.itemCount)) {
            is LibrarySurface.Error ->
                ErrorScreen(
                    message = surface.message,
                    onRetry = { viewModel.onEvent(LibraryUiEvent.Refresh) },
                )
            LibrarySurface.Content -> {
            Column(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(headerGradientBrush)
                        .statusBarsPadding()
                        .padding(top = 4.dp),
                ) {
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(
                            MaterialTheme.motionScheme.defaultEffectsSpec()
                        ) + slideInVertically(
                            MaterialTheme.motionScheme.defaultSpatialSpec(),
                            initialOffsetY = { -40 },
                        ),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = if (onBack != null) 8.dp else 24.dp, end = 8.dp)
                                // Key-intercepted Down from the title row's focusables
                                // drops to the first header row below — the geometric
                                // search can't be trusted between these rows.
                                .ifElse(
                                    headerEntryLeaf != null,
                                    Modifier.onDpadKey(
                                        onDown = { rowBelowTitleLeaf?.tryRequestFocus("lib_header_nav") ?: false },
                                    ),
                                )
                                .openDrawerOnLeftExit(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (onBack != null) {
                                CircleBgBackButton(
                                    onClick = onBack,
                                    iconColor = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.focusRequester(backFocus),
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                text = browser.title ?: stringResource(Res.string.library_title),
                                // Matches MediaDetail's DetailTopBar title treatment
                                // (titleLarge / SemiBold) rather than the old
                                // headlineLarge / Bold — keeps the library header
                                // visually consistent with the detail screen.
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            ErrorAwareStatusIndicator(
                                status = headerStatus,
                                errorMessage = error,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                            if (!inSectionMode) {
                                // Reset-all pill — matches the screen's chip/action
                                // language (glass chip, press scale, TV focus glow).
                                com.raulshma.jellyplay.core.ui.components.ExpressiveChipContainer(
                                    onClick = { viewModel.onEvent(LibraryUiEvent.ResetClicked) },
                                    containerColor = if (LocalIsLightTheme.current) {
                                        Color.Black.copy(alpha = 0.06f)
                                    } else {
                                        Color.White.copy(alpha = 0.12f)
                                    },
                                    modifier = Modifier
                                        .padding(start = 4.dp)
                                        .focusRequester(resetPillFocus),
                                ) {
                                    Icon(
                                        Tabler.Outline.Restore,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = stringResource(Res.string.library_reset),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = !inSectionMode && folders.size > 1,
                        enter = fadeIn(
                            MaterialTheme.motionScheme.defaultEffectsSpec()
                        ) + slideInVertically(
                            MaterialTheme.motionScheme.defaultSpatialSpec(),
                            initialOffsetY = { 40 },
                        ),
                    ) {
                        Column {
                            Spacer(Modifier.height(8.dp))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 24.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                // Vertical D-pad hops out of this row are redirected
                                // explicitly (key interception, not focus search): the
                                // chip rows' focus bounds overlap vertically and the
                                // alphabet rail interleaves on the right edge, so the
                                // geometric search lands on the wrong row.
                                modifier = Modifier
                                    .onDpadKey(
                                        onUp = { headerEntryLeaf?.tryRequestFocus("lib_header_nav") ?: false },
                                        onDown = { firstFilterChipFocus.tryRequestFocus("lib_header_nav") },
                                    )
                                    .openDrawerOnLeftExit()
                                    .focusGroup()
                                    .tvFocusRestorer(),
                            ) {
                                item {
                                    GlassPill(
                                        label = stringResource(Res.string.library_all),
                                        selected = browser.folder == null,
                                        onClick = { viewModel.onEvent(LibraryUiEvent.SelectFolder(null)) },
                                        modifier = Modifier.focusRequester(firstFolderPillFocus),
                                    )
                                }
                                items(folders.size, key = { folders[it].id }, contentType = { "folder" }) { index ->
                                    val folder = folders[index]
                                    val placementSpec = lazyItemPlacementSpec()
                                    Box(modifier = Modifier.animateItem(placementSpec = placementSpec)) {
                                        GlassPill(
                                            label = folder.name,
                                            selected = browser.folder?.id == folder.id,
                                            onClick = { viewModel.onEvent(LibraryUiEvent.SelectFolder(folder)) },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    // Pinned filter chip row. Each chip opens a
                    // dedicated selection sheet (immediate-apply); "All Filters"
                    // falls back to the legacy full sheet.
                    LibraryFilterChipRow(
                        // Offline mode pins the Downloaded filter on (#147): the
                        // chip renders selected and Tags stay hidden while the
                        // grid is served from the local store. The toggle is a
                        // no-op then — the grid is downloads-only until the app
                        // is back online, so tapping could only mislead.
                        filters = if (offlineAutoFilter) filters.copy(isDownloaded = true) else filters,
                        genres = genres,
                        availableTags = tags,
                        onOpenSheet = { openSheet = LibrarySheet.Filter(it) },
                        onToggleDownloaded = {
                            if (!offlineAutoFilter) {
                                viewModel.onEvent(
                                    LibraryUiEvent.UpdateFilters(
                                        filters.copy(isDownloaded = !(filters.isDownloaded == true))
                                    )
                                )
                            }
                        },
                        onToggleHasSubtitles = {
                            viewModel.onEvent(LibraryUiEvent.UpdateFilters(filters.withHasSubtitlesToggled()))
                        },
                        firstChipFocus = firstFilterChipFocus,
                        modifier = Modifier
                            .onDpadKey(
                                onUp = { rowAboveFilterLeaf?.tryRequestFocus("lib_header_nav") ?: false },
                                onDown = { firstActionChipFocus.tryRequestFocus("lib_header_nav") },
                            )
                            .openDrawerOnLeftExit(),
                    )

                    // Labeled action row (View / Size / Group) — replaces the old
                    // unlabeled floating toolbar. Sits directly under the filter
                    // chip row so all the screen's controls are grouped and
                    // discoverable, each carrying an icon + label.
                    LibraryActionChipRow(
                        viewMode = viewMode,
                        groupByLabel = if (groupBy == GroupBy.NONE) {
                            stringResource(Res.string.library_action_group)
                        } else {
                            groupByLabel(groupBy)
                        },
                        onViewCycle = {
                            // Cycle GRID → THUMB → LIST → MASONRY → GRID so each
                            // tap advances to the next layout mode (same order as
                            // the former floating-toolbar toggle).
                            viewModel.onEvent(LibraryUiEvent.SetViewMode(viewMode.next))
                        },
                        onSizeClick = { openSheet = LibrarySheet.PosterSize },
                        onGroupClick = { openSheet = LibrarySheet.GroupBy },
                        firstChipFocus = firstActionChipFocus,
                        modifier = Modifier
                            // Up returns to the filter chip row; Down falls through
                            // geometrically into the grid (or the active-filter tags).
                            .onDpadKey(onUp = { firstFilterChipFocus.tryRequestFocus("lib_header_nav") })
                            .openDrawerOnLeftExit(),
                    )

                    AnimatedVisibility(
                        visible = hasActiveFilters,
                        enter = fadeIn(
                            MaterialTheme.motionScheme.fastEffectsSpec()
                        ) + expandVertically(),
                        exit = fadeOut(
                            MaterialTheme.motionScheme.fastEffectsSpec()
                        ) + shrinkVertically(),
                    ) {
                        // The shared active-filter bar (core:ui) over the
                        // canonical [LibraryFilters.activeTags] fold — the same
                        // fold the search screen reads. No vertical D-pad
                        // interception here: the tags wrap to several lines, so
                        // Up/Down stays geometric between them; exiting upward
                        // lands on the action row, Down falls into the grid.
                        // Dismiss semantics are unchanged: each tag's clear() is
                        // the exact `filters.copy(...)` the hand-rolled lambdas
                        // performed, routed through the same UpdateFilters write.
                        ActiveFiltersBar(
                            tags = filters.activeTags(libraryActiveFilterDimensions),
                            onTagDismiss = { tag ->
                                viewModel.onEvent(LibraryUiEvent.UpdateFilters(tag.clear()))
                            },
                            onClearAll = { viewModel.onEvent(LibraryUiEvent.ClearFilters) },
                        )
                    }

                    AnimatedVisibility(
                        visible = pagedItems.itemCount > 0,
                        enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                    ) {
                        Text(
                            text = pluralStringResource(Res.plurals.library_item_count, pagedItems.itemCount, pagedItems.itemCount),
                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(
                                horizontal = 24.dp,
                                vertical = 8.dp,
                            ),
                        )
                    }
                }

                var wasRefreshing by remember { mutableStateOf(false) }
                // Bumped on every completed refresh so the focus-managed list/grid containers reset
                // their saved cursor and re-grab focus — a filter/folder change replaces the data
                // under the old index, which otherwise orphans focus to the drawer rail.
                var refreshGeneration by remember { mutableIntStateOf(0) }
                LaunchedEffect(pagedItems.loadState.refresh) {
                    val isNowRefreshing = pagedItems.loadState.refresh is LoadState.Loading
                    if (wasRefreshing && !isNowRefreshing) {
                        refreshGeneration++
                        gridState.scrollToItem(0)
                        listState.scrollToItem(0)
                    }
                    wasRefreshing = isNowRefreshing
                }

                PullToRefreshBox(
                    isRefreshing = pagedItems.loadState.refresh is LoadState.Loading && pagedItems.itemCount > 0,
                    onRefresh = {
                        viewModel.onEvent(LibraryUiEvent.Refresh)
                    },
                    enabled = !isTv,
                    modifier = Modifier
                        .fillMaxSize()
                        .openDrawerOnLeftExit(),
                ) {
                    // The shared refresh ladder (core:ui chassis): initial load
                    // (no items yet) shows the center indicator only; a refresh
                    // with existing items shows the pull-to-refresh indicator
                    // above (via isRefreshing) and keeps the content visible —
                    // the two must never render together.
                    when (pagedCollectionRung(pagedItems.loadState.refresh.toPagedRefreshPhase(), pagedItems.itemCount)) {
                        PagedCollectionRung.InitialLoading -> {
                            DelayedLoadingScreen()
                        }

                        PagedCollectionRung.RefreshError -> {
                            ErrorScreen(
                                message = UserErrorMessages.resolve(
                                    (pagedItems.loadState.refresh as LoadState.Error).error,
                                    stringResource(Res.string.library_failed_to_load_items),
                                ),
                                onRetry = { pagedItems.refresh() },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }

                        PagedCollectionRung.Empty -> {
                            // ScreenEmptyState owns the TV focus sink/action grab — a
                            // hand-rolled empty state left the screen with zero focusables and
                            // the drawer rail captured focus.
                            ScreenEmptyState(
                                icon = if (offlineAutoFilter) Tabler.Outline.CloudOff else Tabler.Outline.Search,
                                title = stringResource(
                                    if (offlineAutoFilter) Res.string.library_no_downloads_offline
                                    else Res.string.library_no_items_found
                                ),
                                actionLabel = if (hasActiveFilters) {
                                    coreClearFiltersLabel()
                                } else {
                                    null
                                },
                                onAction = if (hasActiveFilters) {
                                    { viewModel.onEvent(LibraryUiEvent.ClearFilters) }
                                } else {
                                    null
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }

                        PagedCollectionRung.Content -> {
                            // Shared-scalar reveal for the first content arrival
                            // (loading/empty rungs compose above until data
                            // exists, so this branch is fresh exactly then).
                            val gridEntrance = rememberDetailEntrance()
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .detailEntrance(progress = { gridEntrance.value }),
                            ) {
                            // Deliberately NOT core:ui's PagedCollectionGrid (the
                            // renderer — the rung DECISIONS above already ride the
                            // shared chassis, pagedCollectionRung/PagedCollectionLadder):
                            // this body is not a plain grid. It swaps four view modes
                            // through an AnimatedContent whose per-branch
                            // AnimatedVisibilityScope drives the cards' shared-element
                            // morphs, adds client-side grouped rendering (GroupBy),
                            // keeps a per-mode scroll state, routes refresh through the
                            // VM event channel (LibraryUiEvent.Refresh, not
                            // LazyPagingItems.retry), uses a DelayedLoadingScreen
                            // initial spinner, an empty state that carries the
                            // clear-filters action, and a gradient append footer none
                            // of the shared variants compose.
                                // Drive the view-mode swap through AnimatedContent so each
                                // branch receives its own AnimatedVisibilityScope. That scope
                                // is published via LocalAnimatedVisibilityScope, letting the
                                // cards' sharedElement("poster_${id}") modifiers morph bounds
                                // continuously between GRID/THUMB/LIST/MASONRY instead of pop.
                                // transitionSpec is not a @Composable scope, so read the motion
                                // tokens (reduced-motion flag + effect tween) here, then capture
                                // them in the spec lambda.
                                val reducedMotion = isReducedMotion()
                                val effectSpec = defaultEffectsTween()
                                AnimatedContent(
                                    targetState = viewMode,
                                    transitionSpec = {
                                        if (reducedMotion) {
                                            EnterTransition.None togetherWith ExitTransition.None
                                        } else {
                                            // A soft fade hands off the containers; the visible
                                            // motion is the shared-element bounds transform.
                                            fadeIn(effectSpec) togetherWith fadeOut(effectSpec)
                                        }
                                    },
                                    contentKey = { it },
                                    label = "libraryViewMode",
                                ) { activeMode ->
                                    // `this` is the AnimatedContentScope, which is an
                                    // AnimatedVisibilityScope — provide it to descendants.
                                    CompositionLocalProvider(
                                        LocalAnimatedVisibilityScope provides this,
                                    ) {
                                        if (groupBy != GroupBy.NONE) {
                                            // Client-side grouped rendering (see GroupedLibraryContent):
                                            // non-sticky translucent headers per group, recomputed over the
                                            // loaded snapshot. Skips the TV-focus-managed grid path.
                                            GroupedLibraryContent(
                                                pagedItems = pagedItems,
                                                viewMode = activeMode,
                                                groupBy = groupBy,
                                                gridCellSize = gridCellSize,
                                                spacing = spacing,
                                                gridPadding = gridPadding,
                                                refreshGeneration = refreshGeneration,
                                                onItemClick = onItemClick,
                                                getImageUrl = remember(viewModel) { { id: String -> viewModel.getImageUrl(id) } },
                                                onFocusedItemChange = { item -> quickActionIntake.tvFocusedItem = item },
                                            )
                                        } else when (activeMode) {
                                        LibraryViewMode.LIST -> LibraryListContent(
                                            pagedItems = pagedItems,
                                            listState = listState,
                                            contentPadding = gridPadding,
                                            refreshGeneration = refreshGeneration,
                                            onItemClick = onItemClick,
                                            getImageUrl = getImageUrl,
                                            onFocusedItemChange = { item -> quickActionIntake.tvFocusedItem = item },
                                        )
                                        LibraryViewMode.THUMB -> LibraryThumbContent(
                                            pagedItems = pagedItems,
                                            gridState = gridState,
                                            thumbCellSize = thumbCellSize,
                                            contentPadding = gridPadding,
                                            spacing = spacing,
                                            refreshGeneration = refreshGeneration,
                                            onItemClick = onItemClick,
                                            getImageUrl = getImageUrl,
                                            getBackdropUrl = getBackdropUrl,
                                            onFocusedItemChange = { item -> quickActionIntake.tvFocusedItem = item },
                                        )
                                        LibraryViewMode.GRID -> LibraryGridContent(
                                            pagedItems = pagedItems,
                                            gridState = gridState,
                                            gridCellSize = gridCellSize,
                                            contentPadding = gridPadding,
                                            spacing = spacing,
                                            refreshGeneration = refreshGeneration,
                                            onItemClick = onItemClick,
                                            getImageUrl = getImageUrl,
                                            photoFolderChildUrlsFor = photoFolderChildUrlsFor,
                                            onFocusedItemChange = { item -> quickActionIntake.tvFocusedItem = item },
                                        )
                                        LibraryViewMode.MASONRY -> LibraryMasonryContent(
                                            pagedItems = pagedItems,
                                            staggeredState = staggeredState,
                                            gridCellSize = gridCellSize,
                                            contentPadding = gridPadding,
                                            spacing = spacing,
                                            refreshGeneration = refreshGeneration,
                                            onItemClick = onItemClick,
                                            getImageUrl = getImageUrl,
                                            onFocusedItemChange = { item -> quickActionIntake.tvFocusedItem = item },
                                        )
                                    }
} // close CompositionLocalProvider
                                } // close AnimatedContent content lambda
                            } // close gridEntrance Box
                        }
                    }

                    // Alphabet "jump to letter" rail. Jellyfin returns library items
                    // sorted by SortName by default, so the first snapshot index where
                    // each leading letter appears is stable within the loaded pages.
                    // Tapping or dragging a letter scrolls the active grid/list to that
                    // index — a local-only affordance that works with the existing paging
                    // source (no NameStartsWith server filter is plumbed through the data
                    // layer). Disabled in grouped mode because GroupedLibraryContent owns
                    // its own internal scroll states the rail can't reach.
                    val alphabetScope = rememberCoroutineScope()
                    // Single pass over the loaded snapshot: the first index at
                    // which each normalized leading letter appears — the pure
                    // fold in [jumpIndexByLetter] (beside AlphabetRailGeometry).
                    val jumpIndexByLetter by remember {
                        derivedStateOf {
                            jumpIndexByLetter(pagedItems.itemSnapshotList.items) { it.name }
                        }
                    }
                    // First visible index from whichever scroll state backs the active
                    // view mode — drives the rail's active-letter highlight as the user
                    // scrolls. Reads the state delegates directly inside derivedStateOf
                    // so it recomputes on scroll. MASONRY is excluded (no hoisted grid
                    // state); the rail simply shows no active highlight in that mode.
                    val activeLetter by remember(viewMode) {
                        derivedStateOf {
                            // Reads the state delegates at the argument sites so the
                            // derived subscription is unchanged (recomputes on scroll).
                            val firstVisible = libraryRailFirstVisibleItemIndex(
                                viewMode = viewMode,
                                listFirstVisibleItemIndex = listState.firstVisibleItemIndex,
                                gridFirstVisibleItemIndex = gridState.firstVisibleItemIndex,
                            )
                            jumpIndexByLetter.entries.lastOrNull { it.value <= firstVisible }?.key
                        }
                    }
                    if (groupBy == GroupBy.NONE && jumpIndexByLetter.isNotEmpty()) {
                        val letters = remember(jumpIndexByLetter) { jumpIndexByLetter.keys.toList() }
                        AlphabetJumpRail(
                            letters = letters,
                            activeLetter = activeLetter,
                            onJump = { letter ->
                                val index = jumpIndexByLetter[letter] ?: return@AlphabetJumpRail
                                alphabetScope.launch {
                                    when (libraryRailScrollTarget(viewMode)) {
                                        LibraryRailScrollTarget.LIST -> listState.scrollToItem(index)
                                        LibraryRailScrollTarget.STAGGERED -> staggeredState.scrollToItem(index)
                                        LibraryRailScrollTarget.GRID -> gridState.scrollToItem(index)
                                    }
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 4.dp),
                        )
                    }

                    // Floating toolbar — Filters / Smart / Mood / Playlists / Shuffle.
                    // These are infrequent navigation actions (not view/layout
                    // controls, which live in the labeled action chip row). Kept
                    // in the floating toolbar so the app bar stays clean.
                    // Music-library-only on all form factors: these actions target
                    // music playlists, so showing them for a video library reads as
                    // clutter ("seems more for the music side", #113). TV now
                    // matches phone — filters stay reachable via the pinned filter
                    // chip row above.
                    val isMusicLibrary = selectedFolder?.collectionType == "music"
                    if (isMusicLibrary && pagedItems.itemCount > 0) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = true,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .clearFloatingNav(extraBottom = 0.dp),
                            enter = fadeIn(
                                MaterialTheme.motionScheme.defaultEffectsSpec()
                            ) + slideInVertically(
                                MaterialTheme.motionScheme.defaultSpatialSpec(),
                                initialOffsetY = { it },
                            ),
                            exit = fadeOut(
                                MaterialTheme.motionScheme.fastEffectsSpec()
                            ) + androidx.compose.animation.slideOutVertically(
                                MaterialTheme.motionScheme.fastSpatialSpec(),
                                targetOffsetY = { it },
                            ),
                        ) {
                            HorizontalFloatingToolbar(
                                expanded = true,
                                modifier = Modifier.padding(horizontal = 24.dp),
                                // standardFloatingToolbarColors() derives from the
                                // active colorScheme (surface/onSurface) so the
                                // toolbar matches the app theme rather than the
                                // high-contrast vibrant variant.
                                colors = FloatingToolbarDefaults.standardFloatingToolbarColors(),
                                floatingActionButton = {
                                    FloatingToolbarDefaults.VibrantFloatingActionButton(
                                        onClick = { viewModel.onEvent(LibraryUiEvent.ToggleFilters) },
                                        modifier = Modifier.focusIndicator(),
                                    ) {
                                        Icon(
                                            Tabler.Outline.Filter,
                                            contentDescription = stringResource(Res.string.library_filters),
                                        )
                                    }
                                },
                            ) {
                                IconButton(
                                    onClick = onSmartPlaylistsClick,
                                    shapes = IconButtonDefaults.shapes(),
                                    modifier = Modifier.focusIndicator(),
                                ) {
                                    Icon(
                                        Tabler.Outline.Wand,
                                        contentDescription = stringResource(Res.string.library_smart_playlists),
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                IconButton(
                                    onClick = onMoodPlaylistsClick,
                                    shapes = IconButtonDefaults.shapes(),
                                    modifier = Modifier.focusIndicator(),
                                ) {
                                    Icon(
                                        Tabler.Outline.MoodSmile,
                                        contentDescription = stringResource(Res.string.library_mood_playlists),
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                IconButton(
                                    onClick = onPlaylistsClick,
                                    shapes = IconButtonDefaults.shapes(),
                                    modifier = Modifier.focusIndicator(),
                                ) {
                                    Icon(
                                        Tabler.Outline.Playlist,
                                        contentDescription = stringResource(Res.string.library_playlists),
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.onEvent(LibraryUiEvent.ShuffleLibrary) },
                                    shapes = IconButtonDefaults.shapes(),
                                    modifier = Modifier.focusIndicator(),
                                ) {
                                    Icon(
                                        Tabler.Outline.ArrowsShuffle,
                                        contentDescription = stringResource(Res.string.library_shuffle),
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    }

                    if (pagedAppendRung(pagedItems.loadState.append.toPagedRefreshPhase()) == PagedAppendRung.Loading) {
                        val footerGradientBrush = remember(backgroundColor) {
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    backgroundColor,
                                ),
                            )
                        }
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(footerGradientBrush)
                                .padding(vertical = 20.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            JellyPlayLinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth(0.4f)
                                    .clip(ShapeCache.smooth4),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    if (pagedAppendRung(pagedItems.loadState.append.toPagedRefreshPhase()) == PagedAppendRung.Retry) {
                        AppendErrorFooter(
                            message = UserErrorMessages.resolve(
                                (pagedItems.loadState.append as LoadState.Error).error,
                                stringResource(Res.string.library_failed_to_load_more_items),
                            ),
                            onRetry = { pagedItems.retry() },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(16.dp)
                                .fillMaxWidth(),
                        )
                    }
                }
            }
            } // close LibrarySurface.Content
        }
        } // close CompositionLocalProvider
    }
    // Quick-action sheet + remove-download confirm — the shared intake hosts
    // both (removal only ever deletes the local download; the server copy is
    // untouched).
    QuickActionIntakeHost(quickActionIntake)

    if (resetDialogVisible) {
        LibraryResetConfirmDialog(
            onConfirm = { dontShowAgain ->
                viewModel.onEvent(LibraryUiEvent.ConfirmResetAll(dontShowAgain))
            },
            onDismiss = { viewModel.onEvent(LibraryUiEvent.DismissResetDialog) },
        )
    }

    if (showFilters) {
        LibraryFilterSheet(
            // Same offline pinning as the chip row (#147): the Downloaded
            // toggle renders on while the grid is auto-served from the local
            // store, so the sheet can't claim it's off.
            currentFilters = if (offlineAutoFilter) filters.copy(isDownloaded = true) else filters,
            genres = genres,
            availableTags = tags,
            onApply = { newFilters ->
                viewModel.onEvent(LibraryUiEvent.UpdateFilters(newFilters))
                viewModel.onEvent(LibraryUiEvent.ToggleFilters)
            },
            onDismiss = { viewModel.onEvent(LibraryUiEvent.ToggleFilters) },
        )
    }

    // Per-filter sheets (immediate-apply) + the poster-size / group-by sheets,
    // all behind the one [LibrarySheet] state.
    when (val sheet = openSheet) {
        is LibrarySheet.Filter -> when (sheet.kind) {
            FilterSheetKind.SORT -> SortFilterSheet(
                current = filters.sortBy,
                onApply = {
                    viewModel.onEvent(LibraryUiEvent.UpdateFilters(filters.copy(sortBy = it)))
                },
                onDismiss = { openSheet = null },
            )
            FilterSheetKind.TYPE -> MediaTypeFilterSheet(
                current = filters.mediaTypes,
                onToggle = { type ->
                    viewModel.onEvent(
                        LibraryUiEvent.UpdateFilters(
                            filters.copy(mediaTypes = filters.mediaTypes.toggled(type))
                        )
                    )
                },
                onDismiss = { openSheet = null },
            )
            FilterSheetKind.STATUS -> StatusFilterSheet(
                current = filters.playedStatus,
                onApply = {
                    viewModel.onEvent(
                        LibraryUiEvent.UpdateFilters(filters.copy(playedStatus = it))
                    )
                },
                onDismiss = { openSheet = null },
                isResumable = filters.isResumable == true,
                onToggleResumable = {
                    viewModel.onEvent(
                        LibraryUiEvent.UpdateFilters(
                            filters.copy(isResumable = !(filters.isResumable == true))
                        )
                    )
                },
            )
            FilterSheetKind.GENRES -> GenreFilterSheet(
                current = filters.genres,
                genres = genres,
                onToggle = { genre ->
                    viewModel.onEvent(
                        LibraryUiEvent.UpdateFilters(
                            filters.copy(genres = filters.genres.toggled(genre))
                        )
                    )
                },
                onDismiss = { openSheet = null },
            )
            FilterSheetKind.TAGS -> TagFilterSheet(
                current = filters.tags,
                tags = tags,
                onToggle = { tag ->
                    viewModel.onEvent(
                        LibraryUiEvent.UpdateFilters(
                            filters.copy(tags = filters.tags.toggled(tag))
                        )
                    )
                },
                onDismiss = { openSheet = null },
            )
            FilterSheetKind.YEARS -> YearRangeFilterSheet(
                current = filters.years.toSet(),
                onApply = {
                    viewModel.onEvent(
                        LibraryUiEvent.UpdateFilters(filters.copy(years = it.toList()))
                    )
                },
                onDismiss = { openSheet = null },
            )
            FilterSheetKind.ALL -> {
                openSheet = null
                viewModel.onEvent(LibraryUiEvent.ToggleFilters)
            }
        }
        LibrarySheet.PosterSize -> {
            com.raulshma.jellyplay.feature.library.components.FilterSelectionSheet(
                title = stringResource(Res.string.library_poster_size),
                onDismiss = { openSheet = null },
            ) {
                androidx.compose.foundation.layout.Column {
                    // TvOrTouchSlider gives the slider D-pad stepping on TV (same control the
                    // year-range sheet uses); the raw M3 Slider wasn't TV-operable.
                    com.raulshma.jellyplay.core.ui.tv.components.TvOrTouchSlider(
                        value = posterSize,
                        onValueChange = { viewModel.onEvent(LibraryUiEvent.SetPosterSize(it)) },
                        valueRange = 0.7f..1.4f,
                        isTv = isTv,
                        onValueChangeFinished = { viewModel.onEvent(LibraryUiEvent.PersistPosterSize) },
                    )
                }
            }
        }
        LibrarySheet.GroupBy -> {
            com.raulshma.jellyplay.feature.library.components.FilterSelectionSheet(
                title = stringResource(Res.string.library_group_by),
                onDismiss = { openSheet = null },
            ) {
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GroupBy.entries.forEach { option ->
                        com.raulshma.jellyplay.core.ui.components.GlassFilterChip(
                            label = groupByLabel(option),
                            selected = option == groupBy,
                            onClick = {
                                viewModel.onEvent(LibraryUiEvent.SetGroupBy(option))
                                openSheet = null
                            },
                        )
                    }
                }
            }
        }
        null -> {}
    }
}

/** Returns a new list with [value] removed if present, appended otherwise. */
private fun <T> List<T>.toggled(value: T): List<T> =
    if (value in this) this - value else this + value

