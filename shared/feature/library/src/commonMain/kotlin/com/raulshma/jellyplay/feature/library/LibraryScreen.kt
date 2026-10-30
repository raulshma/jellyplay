package com.raulshma.jellyplay.feature.library

import com.raulshma.jellyplay.core.ui.components.JellyPlayBackHandler
import com.raulshma.jellyplay.core.ui.components.DeferredRefreshEffect
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.paging.compose.collectAsLazyPagingItems
import com.raulshma.jellyplay.core.model.LibrarySectionContext
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaQuickActionScope
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.QuickActionAdapter
import com.raulshma.jellyplay.core.ui.components.QuickActionIntakeHost
import com.raulshma.jellyplay.core.ui.components.LocalMediaQuickActionController
import com.raulshma.jellyplay.core.ui.components.rememberQuickActionIntake
import com.raulshma.jellyplay.core.ui.tv.LocalTvDrawerOpener
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.TvGridCacheWindow
import com.raulshma.jellyplay.core.ui.tv.ifElse
import com.raulshma.jellyplay.core.ui.tv.input.onDpadKey
import com.raulshma.jellyplay.feature.library.components.FilterSheetKind
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

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
 * D-pad Left at any content left edge expands the navigation drawer. The
 * exit hook fires only when focus actually leaves this subtree leftward —
 * i.e. from its leftmost focusable — so mid-row horizontal D-pad scrolling
 * is unaffected. Belt-and-suspenders on top of the native rail focus entry:
 * when that works the drawer opens either way; when the geometric search
 * comes back empty, this still expands it. Read by the header rows, the
 * content refresh surface and every section in between — ONE shared arm.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun Modifier.openDrawerOnLeftExit(): Modifier {
    val isTv = LocalTvMode.current
    val openTvDrawer = LocalTvDrawerOpener.current
    return ifElse(
        isTv,
        Modifier.focusProperties {
            @Suppress("DEPRECATION")
            exit = { direction ->
                if (direction == FocusDirection.Left) openTvDrawer()
                FocusRequester.Default
            }
        },
    )
}

/**
 * The Library screen root — the state subscriptions, the VM-side effects
 * (section configuration, photo-folder prefetch, deferred refresh), the back
 * ladder, the quick-action intake and the error/content surface dispatch.
 * The three stacked concerns below it are section hosts: the header rows
 * ([LibraryHeaderSection]), the refresh surface ([LibraryContentSection]) and
 * the sheets ([LibrarySheetHost]) — each owns its own internals, so the root
 * stays the choreography only (the deferred section-hosts design).
 */
@OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
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
    sectionContext: LibrarySectionContext? = null,
    onBack: (() -> Unit)? = null,
    viewModel: LibraryViewModel = koinViewModel(),
) {
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    DeferredRefreshEffect(viewModel.deferredRefresher)

    // One browser-state object owns {folder, filters, viewMode, groupBy,
    // posterSize, sectionContext, title} — one subscription, not 13 individual
    // collectAsStateWithLifecycle reads that re-derive presentation from
    // scattered sources. "In section mode" derives from the state alone, the
    // single source of truth (not the nav-arg). The nav-arg still drives the
    // LaunchedEffect below.
    val browser by viewModel.browserState.collectAsStateWithLifecycle()
    val genres by viewModel.genres.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val showFilters by viewModel.showFilters.collectAsStateWithLifecycle()
    val resetDialogVisible by viewModel.resetDialogVisible.collectAsStateWithLifecycle()
    // Offline mode auto-filters the grid to downloads; the flag pins
    // the Downloaded chip and swaps the empty-state copy.
    val offlineAutoFilter by viewModel.offlineAutoFilter.collectAsStateWithLifecycle()

    // Section mode: configure the VM once with the injected context. Idempotent
    // (configureSection early-returns on an equal context) so recomposition is
    // safe. In tab mode (sectionContext == null) clear any leftover section state
    // so the Library tab shows its default view — the VM is shared across the
    // tab and the section deep-link, so without this reset the "Latest X"
    // filters/sort would leak into the tab.
    LaunchedEffect(sectionContext) {
        if (sectionContext != null) {
            viewModel.onEvent(LibraryUiEvent.ConfigureSection(sectionContext))
        } else {
            viewModel.onEvent(LibraryUiEvent.ClearSectionMode)
        }
    }

    val pagedItems = viewModel.pagedItems.collectAsLazyPagingItems()

    // Prefetch photo-folder child urls on load-state transitions (append/refresh)
    // instead of snapshot-list identity: keying on the snapshot re-fires the
    // prefetch on every page boundary, and each merge produces a new Map in
    // _photoFolderChildUrls. Per-item collection keeps that from invalidating
    // the whole screen. Gating on loadState also lets
    // us skip non-photo libraries entirely.
    val appendState = pagedItems.loadState
    LaunchedEffect(appendState) {
        val snapshot = pagedItems.itemSnapshotList
        if (snapshot.items.any { it.mediaType == MediaType.PHOTO_FOLDER }) {
            viewModel.onEvent(LibraryUiEvent.PrefetchPhotoFolderChildUrls(snapshot.items))
        }
    }

    // Derives from the browser state (the single source of truth), not the
    // nav-arg parameter. The nav-arg still drives the
    // configureSection/clearSectionMode LaunchedEffect above.
    val inSectionMode = browser.isSection
    // Which (if any) auxiliary sheet is open (filter/poster-size/group-by).
    // Null = none. Hoisted here so the chips toggle it and the matching sheet
    // renders at the screen root; the back ladder folds over it via
    // [libraryBackAction]. One MutableState handed to the section hosts.
    val openSheetState = remember { mutableStateOf<LibrarySheet?>(null) }
    // The canonical fold on [LibraryFilters] — the same one the search screen
    // reads, so the badge and BackHandler guard see the full active set
    // (years/tags/minRating/sort/resumable included).
    val hasActiveFilters by remember {
        derivedStateOf { browser.filters.hasActiveFilters() }
    }
    val backHandlerEnabled = showFilters || openSheetState.value != null || resetDialogVisible || (!inSectionMode && hasActiveFilters)

    JellyPlayBackHandler(enabled = backHandlerEnabled) {
        when (libraryBackAction(resetDialogVisible, showFilters, openSheetState.value, inSectionMode, hasActiveFilters)) {
            LibraryBackAction.DismissResetDialog -> viewModel.onEvent(LibraryUiEvent.DismissResetDialog)
            LibraryBackAction.CloseFilters -> viewModel.onEvent(LibraryUiEvent.ToggleFilters) // closes when open
            LibraryBackAction.CloseSheet -> openSheetState.value = null
            LibraryBackAction.ClearFilters -> viewModel.onEvent(LibraryUiEvent.ClearFilters)
            null -> {}
        }
    }

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

    // The per-mode scroll states live at the screen root — ABOVE the surface
    // dispatch — so the error arm doesn't drop the user's scroll position.
    val gridState = rememberLazyGridState(
        cacheWindow = TvGridCacheWindow,
    )
    val listState = rememberLazyListState()
    // Hoisted (not local to the MASONRY branch) so the alphabet rail can drive
    // the staggered grid's scroll state.
    val staggeredState = rememberLazyStaggeredGridState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
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
                        LibraryHeaderSection(
                            viewModel = viewModel,
                            browser = browser,
                            folders = folders,
                            isLoading = isLoading,
                            error = error,
                            itemCount = pagedItems.itemCount,
                            genres = genres,
                            tags = tags,
                            offlineAutoFilter = offlineAutoFilter,
                            hasActiveFilters = hasActiveFilters,
                            onBack = onBack,
                            openSheetState = openSheetState,
                        )

                        LibraryContentSection(
                            viewModel = viewModel,
                            browser = browser,
                            pagedItems = pagedItems,
                            quickActionIntake = quickActionIntake,
                            gridState = gridState,
                            listState = listState,
                            staggeredState = staggeredState,
                            artworkUrls = viewModel.artworkUrls,
                            hasActiveFilters = hasActiveFilters,
                            offlineAutoFilter = offlineAutoFilter,
                            onItemClick = onItemClick,
                            onSmartPlaylistsClick = onSmartPlaylistsClick,
                            onMoodPlaylistsClick = onMoodPlaylistsClick,
                            onPlaylistsClick = onPlaylistsClick,
                        )
                    }
                }
            } // close surface dispatch
        } // close CompositionLocalProvider
    }
    // Quick-action sheet + remove-download confirm — the shared intake hosts
    // both (removal only ever deletes the local download; the server copy is
    // untouched).
    QuickActionIntakeHost(quickActionIntake)

    LibrarySheetHost(
        viewModel = viewModel,
        browser = browser,
        genres = genres,
        tags = tags,
        offlineAutoFilter = offlineAutoFilter,
        isTv = LocalTvMode.current,
        showFilters = showFilters,
        resetDialogVisible = resetDialogVisible,
        openSheetState = openSheetState,
    )
}
