package com.raulshma.jellyplay.feature.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import com.raulshma.jellyplay.core.ui.components.JellyPlayLinearProgressIndicator
import com.raulshma.jellyplay.core.ui.model.coreClearFiltersLabel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import com.raulshma.jellyplay.core.data.util.ArtworkUrls
import com.raulshma.jellyplay.core.data.error.UserErrorMessages
import com.raulshma.jellyplay.core.model.GroupBy
import com.raulshma.jellyplay.core.model.LibraryBrowserState
import com.raulshma.jellyplay.core.model.LibraryViewMode
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.ui.components.AppendErrorFooter
import com.raulshma.jellyplay.core.ui.components.DelayedLoadingScreen
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.defaultEffectsTween
import com.raulshma.jellyplay.core.designsystem.theme.detailEntrance
import com.raulshma.jellyplay.core.designsystem.theme.rememberDetailEntrance
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.LocalAnimatedVisibilityScope
import com.raulshma.jellyplay.core.ui.components.PagedAppendRung
import com.raulshma.jellyplay.core.ui.components.PagedCollectionRung
import com.raulshma.jellyplay.core.ui.components.pagedAppendRung
import com.raulshma.jellyplay.core.ui.components.pagedCollectionRung
import com.raulshma.jellyplay.core.ui.components.PullToRefreshBox
import com.raulshma.jellyplay.core.ui.components.ScreenEmptyState
import com.raulshma.jellyplay.core.ui.components.clearFloatingNav
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.core.ui.components.toPagedRefreshPhase
import com.raulshma.jellyplay.core.ui.components.QuickActionIntake
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.*
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.animation.isReducedMotion
import com.raulshma.jellyplay.feature.library.components.AlphabetJumpRail
import com.raulshma.jellyplay.feature.library.components.GroupedLibraryContent
import com.raulshma.jellyplay.feature.library.components.LibraryGridContent
import com.raulshma.jellyplay.feature.library.components.LibraryListContent
import com.raulshma.jellyplay.feature.library.components.LibraryMasonryContent
import com.raulshma.jellyplay.feature.library.components.LibraryThumbContent
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.ArrowsShuffle
import com.composables.icons.tabler.outline.CloudOff
import com.composables.icons.tabler.outline.Filter
import com.composables.icons.tabler.outline.MoodSmile
import com.composables.icons.tabler.outline.Playlist
import com.composables.icons.tabler.outline.Search
import com.composables.icons.tabler.outline.Wand
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.feature.library.generated.resources.Res
import com.raulshma.jellyplay.feature.library.generated.resources.library_failed_to_load_items
import com.raulshma.jellyplay.feature.library.generated.resources.library_failed_to_load_more_items
import com.raulshma.jellyplay.feature.library.generated.resources.library_filters
import com.raulshma.jellyplay.feature.library.generated.resources.library_mood_playlists
import com.raulshma.jellyplay.feature.library.generated.resources.library_no_downloads_offline
import com.raulshma.jellyplay.feature.library.generated.resources.library_no_items_found
import com.raulshma.jellyplay.feature.library.generated.resources.library_playlists
import com.raulshma.jellyplay.feature.library.generated.resources.library_smart_playlists
import com.raulshma.jellyplay.feature.library.generated.resources.library_shuffle
import kotlinx.coroutines.launch

/**
 * The Library screen's content section host — everything inside the refresh
 * surface: the refresh ladder (initial loading / refresh error / empty /
 * content), the four view modes' AnimatedContent swap (each branch's
 * AnimatedVisibilityScope drives the cards' shared-element morphs), the
 * client-side grouped rendering, the alphabet jump rail, the music-only
 * floating toolbar and the append footer/retry. Owns the refresh-generation
 * scroll reset; the per-mode scroll states are rooted at the screen root
 * (passed in) so they survive the root error arm.
 *
 * Composition-shape only: the bodies moved verbatim out of [LibraryScreen]
 * (the deferred section-hosts design), zero behaviour change. The surface
 * decision itself (error vs content) stays on the screen root; this host runs
 * only in the content arm.
 */
@OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)
@Composable
internal fun LibraryContentSection(
    viewModel: LibraryViewModel,
    browser: LibraryBrowserState,
    pagedItems: LazyPagingItems<MediaItem>,
    quickActionIntake: QuickActionIntake,
    // Remembered at the screen root (above the surface dispatch) and passed in,
    // so the root error arm doesn't drop the user's scroll position.
    gridState: LazyGridState,
    listState: LazyListState,
    staggeredState: LazyStaggeredGridState,
    artworkUrls: ArtworkUrls,
    hasActiveFilters: Boolean,
    offlineAutoFilter: Boolean,
    onItemClick: (itemId: String, mediaType: MediaType, parentId: String?, itemName: String) -> Unit,
    onSmartPlaylistsClick: () -> Unit,
    onMoodPlaylistsClick: () -> Unit,
    onPlaylistsClick: () -> Unit,
) {
    val viewMode = browser.viewMode
    val groupBy = browser.groupBy
    val isTv = LocalTvMode.current

    val adaptiveInfo = LocalAdaptiveInfo.current
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

    // The tag-aware artwork resolver for the view-mode contents — one stable
    // instance per VM (the former id-lambda wrappers), so a tag change rides
    // the item identity into the per-card remembers instead of a URL rebuild
    // keyed on an unchanging id.
    val photoFolderChildUrlsFor = remember(viewModel) { { id: String -> viewModel.photoFolderChildUrlsFor(id) } }

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

    val backgroundColor = MaterialTheme.colorScheme.background

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
                    onRetry = { pagedItems.retry() },
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
                                    resolver = artworkUrls,
                                    onFocusedItemChange = { item -> quickActionIntake.tvFocusedItem = item },
                                )
                            } else when (activeMode) {
                                LibraryViewMode.LIST -> LibraryListContent(
                                    pagedItems = pagedItems,
                                    listState = listState,
                                    contentPadding = gridPadding,
                                    refreshGeneration = refreshGeneration,
                                    onItemClick = onItemClick,
                                    resolver = artworkUrls,
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
                                    resolver = artworkUrls,
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
                                    resolver = artworkUrls,
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
                                    resolver = artworkUrls,
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
        // clutter ("seems more for the music side"). TV matches
        // phone — filters stay reachable via the pinned filter
        // chip row above.
        val isMusicLibrary = browser.folder?.collectionType == "music"
        if (isMusicLibrary && pagedItems.itemCount > 0) {
            AnimatedVisibility(
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
