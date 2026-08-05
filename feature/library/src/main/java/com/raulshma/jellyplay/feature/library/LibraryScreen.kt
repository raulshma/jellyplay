package com.raulshma.jellyplay.feature.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.WindowInsets
import com.raulshma.jellyplay.core.ui.components.clearFloatingNav
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import com.raulshma.jellyplay.core.ui.components.JellyPlayLinearProgressIndicator
import com.raulshma.jellyplay.core.ui.components.progressFraction
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.raulshma.jellyplay.core.ui.util.safeItemKey
import com.raulshma.jellyplay.core.designsystem.theme.LocalIsLightTheme
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.defaultEffectsTween
import com.raulshma.jellyplay.core.ui.components.AppendErrorFooter
import com.raulshma.jellyplay.core.ui.components.CircleBgBackButton
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.GlassDismissTag
import com.raulshma.jellyplay.core.ui.components.DelayedLoadingScreen
import com.raulshma.jellyplay.core.ui.components.LoadingScreen
import com.raulshma.jellyplay.core.ui.components.LocalAnimatedVisibilityScope
import com.raulshma.jellyplay.core.ui.components.PosterCard
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.*
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.TvFocusableGrid
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.raulshma.jellyplay.core.model.GroupBy
import com.raulshma.jellyplay.core.model.LibraryViewMode
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.PlayedStatus
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
import com.raulshma.jellyplay.feature.library.components.ThumbCard
import com.raulshma.jellyplay.core.ui.animation.animateContentSizeNoClip
import com.raulshma.jellyplay.core.ui.animation.isReducedMotion
import com.raulshma.jellyplay.core.ui.animation.lazyItemPlacementSpec
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.raulshma.jellyplay.feature.library.R
import kotlinx.coroutines.launch

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)
@Composable
fun LibraryScreen(
    onItemClick: (itemId: String, mediaType: MediaType, parentId: String?, itemName: String) -> Unit,
    onSmartPlaylistsClick: () -> Unit = {},
    onMoodPlaylistsClick: () -> Unit = {},
    onPlaylistsClick: () -> Unit = {},
    sectionContext: com.raulshma.jellyplay.core.model.LibrarySectionContext? = null,
    onBack: (() -> Unit)? = null,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val selectedFolder by viewModel.selectedFolder.collectAsStateWithLifecycle()
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    val genres by viewModel.genres.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val showFilters by viewModel.showFilters.collectAsStateWithLifecycle()
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()
    val sectionTitle by viewModel.title.collectAsStateWithLifecycle()
    val posterSize by viewModel.posterSize.collectAsStateWithLifecycle()
    val groupBy by viewModel.groupBy.collectAsStateWithLifecycle()

    // Section mode: configure the VM once with the injected context. Idempotent
    // (configureSection early-returns on an equal context) so recomposition is
    // safe. Skipped in tab mode (sectionContext == null).
    LaunchedEffect(sectionContext) {
        sectionContext?.let { viewModel.configureSection(it) }
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
            viewModel.prefetchPhotoFolderChildUrls(snapshot.items)
        }
    }
    val networkStatus by com.raulshma.jellyplay.core.ui.components.LocalNetworkStatus.current.collectAsStateWithLifecycle()

    val headerStatus = com.raulshma.jellyplay.core.ui.components.resolveHeaderStatus(
        isLoading = isLoading,
        hasError = error != null,
        networkStatus = networkStatus,
    )

    val gridState = rememberLazyGridState(
        cacheWindow = com.raulshma.jellyplay.core.ui.tv.TvGridCacheWindow,
    )
    val listState = rememberLazyListState()
    val inSectionMode = sectionContext != null
    // Which (if any) per-filter sheet is open. Null = none. Hoisted here so the
    // chips toggle it and the matching sheet renders at the screen root.
    var openFilterSheet by remember { mutableStateOf<FilterSheetKind?>(null) }
    var showPosterSizeSheet by remember { mutableStateOf(false) }
    var showGroupBySheet by remember { mutableStateOf(false) }
    val hasActiveFilters by remember {
        derivedStateOf {
            filters.mediaTypes.isNotEmpty() ||
                filters.genres.isNotEmpty() ||
                filters.playedStatus != PlayedStatus.ALL
        }
    }
    val isAnySheetOpen = openFilterSheet != null || showPosterSizeSheet || showGroupBySheet
    val backHandlerEnabled = showFilters || isAnySheetOpen || (!inSectionMode && hasActiveFilters)

    BackHandler(enabled = backHandlerEnabled) {
        when {
            showFilters -> viewModel.toggleShowFilters() // closes when open
            openFilterSheet != null -> openFilterSheet = null
            showPosterSizeSheet -> showPosterSizeSheet = false
            showGroupBySheet -> showGroupBySheet = false
            !inSectionMode && hasActiveFilters -> viewModel.clearFilters()
        }
    }

    val backgroundColor = MaterialTheme.colorScheme.background


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

    val gridCellSize = adaptiveInfo.gridCellSize(isTv) / posterSize
    // Landscape thumbnails are wider than they are tall (16:9), so the THUMB
    // grid needs a larger min cell width than the poster (2:3) grid to avoid
    // rendering tiny cards. Scaled from the same adaptive baseline.
    val thumbCellSize = adaptiveInfo.gridCellSize(isTv) / posterSize * (16f / 9f) * (3f / 4f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor),
    ) {
        if (error != null && pagedItems.itemCount == 0) {
            ErrorScreen(
                message = error!!,
                onRetry = { viewModel.refresh() },
            )
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    backgroundColor.copy(alpha = 0.95f),
                                    backgroundColor,
                                ),
                            )
                        )
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
                                .padding(start = if (onBack != null) 8.dp else 24.dp, end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (onBack != null) {
                                CircleBgBackButton(
                                    onClick = onBack,
                                    iconColor = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                text = sectionTitle ?: stringResource(R.string.library_title),
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
                                modifier = Modifier
                                    .focusGroup()
                                    .tvFocusRestorer(),
                            ) {
                                item {
                                    GlassPill(
                                        label = stringResource(R.string.library_all),
                                        selected = selectedFolder == null,
                                        onClick = { viewModel.selectFolder(null) },
                                    )
                                }
                                items(folders.size, key = { folders[it].id }, contentType = { "folder" }) { index ->
                                    val folder = folders[index]
                                    val placementSpec = lazyItemPlacementSpec()
                                    Box(modifier = Modifier.animateItem(placementSpec = placementSpec)) {
                                        GlassPill(
                                            label = folder.name,
                                            selected = selectedFolder?.id == folder.id,
                                            onClick = { viewModel.selectFolder(folder) },
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
                        filters = filters,
                        genres = genres,
                        availableTags = tags,
                        onOpenSheet = { openFilterSheet = it },
                    )

                    // Labeled action row (View / Size / Group) — replaces the old
                    // unlabeled floating toolbar. Sits directly under the filter
                    // chip row so all the screen's controls are grouped and
                    // discoverable, each carrying an icon + label.
                    LibraryActionChipRow(
                        viewMode = viewMode,
                        groupByLabel = if (groupBy == GroupBy.NONE) {
                            stringResource(R.string.library_action_group)
                        } else {
                            groupByLabel(groupBy)
                        },
                        onViewCycle = {
                            // Cycle GRID → THUMB → LIST → MASONRY → GRID so each
                            // tap advances to the next layout mode (same order as
                            // the former floating-toolbar toggle).
                            viewModel.setViewMode(viewMode.next)
                        },
                        onSizeClick = { showPosterSizeSheet = true },
                        onGroupClick = { showGroupBySheet = true },
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
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                                .padding(top = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            filters.mediaTypes.forEach { mediaType ->
                                GlassDismissTag(
                                    label = mediaType.name,
                                    onDismiss = {
                                        viewModel.updateFilters(
                                            filters.copy(mediaTypes = filters.mediaTypes - mediaType)
                                        )
                                    },
                                )
                            }
                            if (filters.playedStatus != PlayedStatus.ALL) {
                                GlassDismissTag(
                                    label = filters.playedStatus.displayName,
                                    onDismiss = {
                                        viewModel.updateFilters(
                                            filters.copy(playedStatus = PlayedStatus.ALL)
                                        )
                                    },
                                )
                            }
                            filters.genres.forEach { genre ->
                                GlassDismissTag(
                                    label = genre,
                                    onDismiss = {
                                        viewModel.updateFilters(
                                            filters.copy(genres = filters.genres - genre)
                                        )
                                    },
                                )
                            }
                            val clearAllFocusState = rememberTvFocusState(focusedScale = 1.05f)
                            val clearAllInteractionSource = remember { MutableInteractionSource() }
                            val isClearAllPressed by clearAllInteractionSource.collectIsPressedAsState()
                            val clearAllScale by animateFloatAsState(
                                targetValue = if (isClearAllPressed) 0.95f else 1f,
                                animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                                label = "clearAllPressedScale"
                            )
                            val clearAllShapeMorph by animateFloatAsState(
                                targetValue = if (isClearAllPressed) 1f else 0f,
                                animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                                label = "clearAllShapeMorph"
                            )
                            val clearAllShape = remember(clearAllShapeMorph) {
                                if (clearAllShapeMorph > 0.5f) ShapeCache.smooth12 else ShapeCache.smooth8
                            }
                            Box(
                                modifier = Modifier
                                    .graphicsLayer {
                                        scaleX = clearAllScale * clearAllFocusState.scale
                                        scaleY = clearAllScale * clearAllFocusState.scale
                                    }
                                    .clip(clearAllShape)
                                    .then(clearAllFocusState.focusModifier)
                                    .tvFocusIndicator(clearAllFocusState, clearAllShape)
                                    .clickable(
                                        interactionSource = clearAllInteractionSource,
                                        indication = null,
                                        onClick = { viewModel.clearFilters() }
                                    )
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.library_clear_all),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = pagedItems.itemCount > 0,
                        enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                    ) {
                        Text(
                            text = pluralStringResource(R.plurals.library_item_count, pagedItems.itemCount, pagedItems.itemCount),
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
                LaunchedEffect(pagedItems.loadState.refresh) {
                    val isNowRefreshing = pagedItems.loadState.refresh is LoadState.Loading
                    if (wasRefreshing && !isNowRefreshing) {
                        gridState.scrollToItem(0)
                        listState.scrollToItem(0)
                    }
                    wasRefreshing = isNowRefreshing
                }

                PullToRefreshBox(
                    isRefreshing = pagedItems.loadState.refresh is LoadState.Loading && pagedItems.itemCount > 0,
                    onRefresh = {
                        viewModel.refresh()
                    },
                    enabled = !isTv,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    // Initial load (no items yet) shows the center indicator only;
                    // a refresh with existing items shows the pull-to-refresh
                    // indicator above (via isRefreshing) and keeps the content
                    // visible — the two must never render together.
                    when {
                        pagedItems.loadState.refresh is LoadState.Loading && pagedItems.itemCount == 0 -> {
                            DelayedLoadingScreen()
                        }

                        pagedItems.loadState.refresh is LoadState.Error -> {
                            val refreshError = pagedItems.loadState.refresh as LoadState.Error
                            ErrorScreen(
                                message = refreshError.error.localizedMessage
                                    ?: stringResource(R.string.library_failed_to_load_items),
                                onRetry = { pagedItems.refresh() },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }

                        else -> {
                            if (pagedItems.itemCount == 0) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Icon(
                                            Tabler.Outline.Search,
                                            contentDescription = null,
                                            modifier = Modifier.size(48.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(
                                            text = stringResource(R.string.library_no_items_found),
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        if (hasActiveFilters) {
                                            Text(
                                                text = stringResource(R.string.library_try_adjusting_filters),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            } else {
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
                                                onItemClick = onItemClick,
                                                getImageUrl = remember(viewModel) { { id: String -> viewModel.getImageUrl(id) } },
                                            )
                                        } else when (activeMode) {
                                    LibraryViewMode.LIST -> {
                                        LazyColumn(
                                            state = listState,
                                            contentPadding = gridPadding,
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier = Modifier.fillMaxSize(),
                                        ) {
                                            items(
                                                count = pagedItems.itemCount,
                                                key = pagedItems.safeItemKey { it.id },
                                                contentType = { "mediaItem" },
                                            ) { index ->
                                                val item = pagedItems[index]
                                                if (item != null) {
                                                    val placementSpec = lazyItemPlacementSpec()
                                                    val memoizedClick = remember(item.id, item.mediaType, item.parentId, item.name) {
                                                        { onItemClick(item.id, item.mediaType, item.parentId, item.name) }
                                                    }
                                                    val subtitle = remember(item.year, item.mediaType) {
                                                        buildString {
                                                            if (item.year != null) append("${item.year}")
                                                            val typeLabel = when (item.mediaType) {
                                                                MediaType.EPISODE -> "Episode"
                                                                MediaType.SERIES -> "Series"
                                                                MediaType.MOVIE -> "Movie"
                                                                MediaType.AUDIO -> "Audio"
                                                                MediaType.MUSIC -> "Music"
                                                                MediaType.PHOTO, MediaType.PHOTO_FOLDER -> "Photo"
                                                                else -> null
                                                            }
                                                            if (typeLabel != null) {
                                                                if (isNotEmpty()) append(" · ")
                                                                append(typeLabel)
                                                            }
                                                        }
                                                    }
                                                    LibraryListItem(
                                                        title = item.name,
                                                        subtitle = subtitle,
                                                        imageUrl = remember(item.id) { viewModel.getImageUrl(item.id) },
                                                        blurHash = item.blurHashes.primary,
                                                        onClick = memoizedClick,
                                                        modifier = Modifier.animateItem(placementSpec = placementSpec),
                                                        sharedElementKey = "poster_${item.id}",
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    LibraryViewMode.THUMB -> {
                                        // 16:9 landscape grid — wider cells than the poster grid
                                        // so backdrop thumbnails aren't tiny. One card per row
                                        // on compact widths, more on tablet/TV.
                                        TvFocusableGrid(
                                            itemCount = pagedItems.itemCount,
                                            key = pagedItems.safeItemKey { it.id },
                                            columns = GridCells.Adaptive(thumbCellSize),
                                            state = gridState,
                                            contentPadding = gridPadding,
                                            horizontalArrangement = Arrangement.spacedBy(spacing),
                                            verticalArrangement = Arrangement.spacedBy(spacing),
                                            modifier = Modifier.fillMaxSize(),
                                            contentType = { "mediaItem" },
                                        ) { index, itemModifier ->
                                            val item = pagedItems[index]
                                            if (item != null) {
                                                val memoizedClick = remember(item.id, item.mediaType, item.parentId, item.name) {
                                                    { onItemClick(item.id, item.mediaType, item.parentId, item.name) }
                                                }
                                                val itemProgress = item.progressFraction()
                                                ThumbCard(
                                                    item = item,
                                                    imageUrl = remember(item.id, item.blurHashes.backdrop) {
                                                        if (item.blurHashes.backdrop != null) {
                                                            viewModel.getBackdropUrl(item.id)
                                                        } else {
                                                            viewModel.getImageUrl(item.id)
                                                        }
                                                    },
                                                    onClick = memoizedClick,
                                                    showProgress = itemProgress != null && itemProgress > 0f,
                                                    progressPercent = itemProgress ?: 0f,
                                                    blurHash = item.blurHashes.backdrop ?: item.blurHashes.primary,
                                                    modifier = itemModifier,
                                                    sharedElementKey = "poster_${item.id}",
                                                )
                                            }
                                        }
                                    }
                                    LibraryViewMode.GRID -> {
                                        TvFocusableGrid(
                                            itemCount = pagedItems.itemCount,
                                            key = pagedItems.safeItemKey { it.id },
                                            columns = GridCells.Adaptive(gridCellSize),
                                            state = gridState,
                                            contentPadding = gridPadding,
                                            horizontalArrangement = Arrangement.spacedBy(spacing),
                                            verticalArrangement = Arrangement.spacedBy(spacing),
                                            modifier = Modifier.fillMaxSize(),
                                            contentType = { "mediaItem" },
                                        ) { index, itemModifier ->
                                            val item = pagedItems[index]
                                            if (item != null) {
                                                val memoizedClick = remember(item.id, item.mediaType, item.parentId, item.name) {
                                                    { onItemClick(item.id, item.mediaType, item.parentId, item.name) }
                                                }
                                                val itemProgress = item.progressFraction()
                                                // Per-item collection: only photo-folder cards subscribe,
                                                // and only the affected card recomposes on a prefetch merge.
                                                val photoFolderChildImageUrls by if (item.mediaType == MediaType.PHOTO_FOLDER) {
                                                    viewModel.photoFolderChildUrlsFor(item.id).collectAsStateWithLifecycle(emptyList())
                                                } else {
                                                    androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(emptyList()) }
                                                }
                                                // Resolve the episode's parent-series poster (and badge)
                                                // the same way the home Latest row does, so episodes
                                                // render as series posters instead of landscape scene
                                                // grabs. See rememberEpisodeCardImage.
                                                val cardImage = com.raulshma.jellyplay.core.ui.components.rememberEpisodeCardImage(
                                                    item = item,
                                                    itemImageUrl = remember(item.id) { viewModel.getImageUrl(item.id) },
                                                    seriesPosterResolver = remember(viewModel) { { id: String -> viewModel.getImageUrl(id) } },
                                                )
                                                PosterCard(
                                                    item = item,
                                                    imageUrl = cardImage.imageUrl,
                                                    fallbackUrls = cardImage.fallbackUrls,
                                                    onClick = memoizedClick,
                                                    showProgress = itemProgress != null && itemProgress > 0f,
                                                    progressPercent = itemProgress ?: 0f,
                                                    blurHash = cardImage.blurHash,
                                                    sharedElementKey = "poster_${item.id}",
                                                    photoFolderChildImageUrls = photoFolderChildImageUrls,
                                                    showEpisodeSeriesBadge = cardImage.showSeriesBadge,
                                                    modifier = itemModifier,
                                                )
                                            }
                                        }
                                    }
                                    LibraryViewMode.MASONRY -> {
                                        // Staggered grid — posters keep their own aspect ratio and
                                        // pack like a masonry wall. Most useful in mixed-type
                                        // libraries; in a pure poster library it reads like the
                                        // regular grid (posters are uniform 2:3). Not TV-focus
                                        // managed (no TvFocusableGrid staggered analogue) so it's a
                                        // touch-first mode.
                                        val staggeredState = rememberLazyStaggeredGridState()
                                        LazyVerticalStaggeredGrid(
                                            columns = StaggeredGridCells.Adaptive(gridCellSize),
                                            state = staggeredState,
                                            contentPadding = gridPadding,
                                            verticalItemSpacing = spacing,
                                            horizontalArrangement = Arrangement.spacedBy(spacing),
                                            modifier = Modifier.fillMaxSize(),
                                        ) {
                                            items(
                                                count = pagedItems.itemCount,
                                                key = pagedItems.safeItemKey { it.id },
                                                contentType = { "mediaItem" },
                                            ) { index ->
                                                val item = pagedItems[index]
                                                if (item != null) {
                                                    val memoizedClick = remember(item.id, item.mediaType, item.parentId, item.name) {
                                                        { onItemClick(item.id, item.mediaType, item.parentId, item.name) }
                                                    }
                                                    val itemProgress = item.progressFraction()
                                                    val cardImage = com.raulshma.jellyplay.core.ui.components.rememberEpisodeCardImage(
                                                        item = item,
                                                        itemImageUrl = remember(item.id) { viewModel.getImageUrl(item.id) },
                                                        seriesPosterResolver = remember(viewModel) { { id: String -> viewModel.getImageUrl(id) } },
                                                    )
                                                    PosterCard(
                                                        item = item,
                                                        imageUrl = cardImage.imageUrl,
                                                        fallbackUrls = cardImage.fallbackUrls,
                                                        onClick = memoizedClick,
                                                        showProgress = itemProgress != null && itemProgress > 0f,
                                                        progressPercent = itemProgress ?: 0f,
                                                        blurHash = cardImage.blurHash,
                                                        sharedElementKey = "poster_${item.id}",
                                                        showEpisodeSeriesBadge = cardImage.showSeriesBadge,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                } // close CompositionLocalProvider
                                } // close AnimatedContent content lambda
                            }
                        }
                    }

                    // Alphabet "jump to letter" rail. Jellyfin returns library items
                    // sorted by SortName by default, so the first snapshot index where
                    // each leading letter appears is stable within the loaded pages.
                    // Tapping a letter scrolls the active grid/list to that index — a
                    // local-only affordance that works with the existing paging source
                    // (no NameStartsWith server filter is plumbed through the data layer).
                    val alphabetScope = rememberCoroutineScope()
                    val jumpIndexByLetter by remember {
                        derivedStateOf {
                            // Single pass over the loaded snapshot: record the first
                            // index at which each normalized leading letter appears.
                            val items = pagedItems.itemSnapshotList.items
                            val map = LinkedHashMap<Char, Int>()
                            for (i in items.indices) {
                                val key = items[i].name.firstOrNull()
                                    ?.lowercaseChar()
                                    ?.takeIf { it in 'a'..'z' }
                                    ?: '#'
                                if (key !in map) map[key] = i
                            }
                            map
                        }
                    }
                    if (jumpIndexByLetter.isNotEmpty()) {
                        AlphabetJumpRail(
                            letters = jumpIndexByLetter.keys.toList(),
                            onJump = { letter ->
                                val index = jumpIndexByLetter[letter] ?: return@AlphabetJumpRail
                                alphabetScope.launch {
                                    when (viewMode) {
                                        LibraryViewMode.LIST -> listState.scrollToItem(index)
                                        else -> gridState.scrollToItem(index)
                                    }
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 4.dp),
                        )
                    }

                    // Floating toolbar — Smart / Mood / Playlists / Shuffle.
                    // These are infrequent navigation actions (not view/layout
                    // controls, which live in the labeled action chip row). Kept
                    // in the floating toolbar so the app bar stays clean.
                    if (!isTv && pagedItems.itemCount > 0) {
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
                                        onClick = { viewModel.toggleShowFilters() },
                                    ) {
                                        Icon(
                                            Tabler.Outline.Filter,
                                            contentDescription = stringResource(R.string.library_filters),
                                        )
                                    }
                                },
                            ) {
                                IconButton(
                                    onClick = onSmartPlaylistsClick,
                                    shapes = IconButtonDefaults.shapes(),
                                ) {
                                    Icon(
                                        Tabler.Outline.Wand,
                                        contentDescription = stringResource(R.string.library_smart_playlists),
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                IconButton(
                                    onClick = onMoodPlaylistsClick,
                                    shapes = IconButtonDefaults.shapes(),
                                ) {
                                    Icon(
                                        Tabler.Outline.MoodSmile,
                                        contentDescription = stringResource(R.string.library_mood_playlists),
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                IconButton(
                                    onClick = onPlaylistsClick,
                                    shapes = IconButtonDefaults.shapes(),
                                ) {
                                    Icon(
                                        Tabler.Outline.Playlist,
                                        contentDescription = stringResource(R.string.library_playlists),
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.shuffleLibrary() },
                                    shapes = IconButtonDefaults.shapes(),
                                ) {
                                    Icon(
                                        Tabler.Outline.ArrowsShuffle,
                                        contentDescription = stringResource(R.string.library_shuffle),
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    }

                    if (pagedItems.loadState.append is LoadState.Loading) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            backgroundColor,
                                        ),
                                    )
                                )
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

                    if (pagedItems.loadState.append is LoadState.Error) {
                        val appendError = pagedItems.loadState.append as LoadState.Error
                        AppendErrorFooter(
                            message = appendError.error.localizedMessage
                                ?: stringResource(R.string.library_failed_to_load_more_items),
                            onRetry = { pagedItems.retry() },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(16.dp)
                                .fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }

    if (showFilters) {
        LibraryFilterSheet(
            currentFilters = filters,
            genres = genres,
            availableTags = tags,
            onApply = { newFilters ->
                viewModel.updateFilters(newFilters)
                viewModel.toggleShowFilters()
            },
            onDismiss = { viewModel.toggleShowFilters() },
        )
    }

    // Per-filter sheets (immediate-apply).
    when (openFilterSheet) {
        FilterSheetKind.SORT -> SortFilterSheet(
            current = filters.sortBy,
            onApply = { viewModel.updateFilters(filters.copy(sortBy = it)) },
            onDismiss = { openFilterSheet = null },
        )
        FilterSheetKind.TYPE -> MediaTypeFilterSheet(
            current = filters.mediaTypes,
            onToggle = { type ->
                viewModel.updateFilters(filters.copy(mediaTypes = filters.mediaTypes.toggled(type)))
            },
            onDismiss = { openFilterSheet = null },
        )
        FilterSheetKind.STATUS -> StatusFilterSheet(
            current = filters.playedStatus,
            onApply = { viewModel.updateFilters(filters.copy(playedStatus = it)) },
            onDismiss = { openFilterSheet = null },
        )
        FilterSheetKind.GENRES -> GenreFilterSheet(
            current = filters.genres,
            genres = genres,
            onToggle = { genre ->
                viewModel.updateFilters(filters.copy(genres = filters.genres.toggled(genre)))
            },
            onDismiss = { openFilterSheet = null },
        )
        FilterSheetKind.TAGS -> TagFilterSheet(
            current = filters.tags,
            tags = tags,
            onToggle = { tag ->
                viewModel.updateFilters(filters.copy(tags = filters.tags.toggled(tag)))
            },
            onDismiss = { openFilterSheet = null },
        )
        FilterSheetKind.YEARS -> YearRangeFilterSheet(
            current = filters.years.toSet(),
            onApply = { viewModel.updateFilters(filters.copy(years = it.toList())) },
            onDismiss = { openFilterSheet = null },
        )
        FilterSheetKind.ALL -> {
            openFilterSheet = null
            viewModel.toggleShowFilters()
        }
        null -> {}
    }

    if (showPosterSizeSheet) {
        com.raulshma.jellyplay.feature.library.components.FilterSelectionSheet(
            title = stringResource(R.string.library_poster_size),
            onDismiss = { showPosterSizeSheet = false },
        ) {
            androidx.compose.foundation.layout.Column {
                androidx.compose.material3.Slider(
                    value = posterSize,
                    onValueChange = { viewModel.setPosterSize(it) },
                    valueRange = 0.7f..1.4f,
                )
            }
        }
    }

    if (showGroupBySheet) {
        com.raulshma.jellyplay.feature.library.components.FilterSelectionSheet(
            title = stringResource(R.string.library_group_by),
            onDismiss = { showGroupBySheet = false },
        ) {
            androidx.compose.foundation.layout.FlowRow {
                GroupBy.entries.forEach { option ->
                    com.raulshma.jellyplay.core.ui.components.GlassFilterChip(
                        label = groupByLabel(option),
                        selected = option == groupBy,
                        onClick = {
                            viewModel.setGroupBy(option)
                            showGroupBySheet = false
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ErrorAwareStatusIndicator(
    status: com.raulshma.jellyplay.core.ui.components.HeaderStatus,
    errorMessage: String?,
    modifier: Modifier = Modifier,
) {
    val isError = status is com.raulshma.jellyplay.core.ui.components.HeaderStatus.Error
    val tooltipState = rememberTooltipState(isPersistent = false)
    val scope = rememberCoroutineScope()

    if (isError && errorMessage != null) {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
            tooltip = {
                PlainTooltip(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = ShapeCache.smooth12,
                ) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            state = tooltipState,
            enableUserInput = true,
            modifier = modifier,
        ) {
            com.raulshma.jellyplay.core.ui.components.HeaderStatusIndicator(
                status = status,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { scope.launch { tooltipState.show() } },
                ),
            )
        }
    } else {
        com.raulshma.jellyplay.core.ui.components.HeaderStatusIndicator(
            status = status,
            modifier = modifier,
        )
    }
}



@Composable
private fun GlassPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val focusState = rememberTvFocusState(focusedScale = 1.05f)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val baseScale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "pillPressedScale"
    )
    val scale = baseScale * focusState.scale

    val shapeMorphProgress by animateFloatAsState(
        targetValue = if (isPressed || selected) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "pillShapeMorph"
    )
    val shape = remember(shapeMorphProgress) {
        if (shapeMorphProgress > 0.5f) ShapeCache.smooth20 else ShapeCache.smooth16
    }

    val isLight = LocalIsLightTheme.current
    val surfaceColor = when {
        selected -> MaterialTheme.colorScheme.primary
        else -> if (isLight) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    }
    val contentColor = when {
        selected -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .then(focusState.focusModifier)
            .tvFocusIndicator(focusState, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .animateContentSizeNoClip(MaterialTheme.motionScheme.slowSpatialSpec()),
        shape = shape,
        color = surfaceColor,
        contentColor = contentColor,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Right-edge alphabet "jump to letter" rail for large libraries. Renders the set
 * of leading letters present in the loaded items (plus `#` for non A–Z names) as
 * a compact vertical column; a tap/dpad-select scrolls the host grid/list to the
 * first item whose name starts with that letter. Local-only — see
 * [LibraryScreen] for why a NameStartsWith server filter isn't used.
 */
@Composable
private fun AlphabetJumpRail(
    letters: List<Char>,
    onJump: (Char) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLight = LocalIsLightTheme.current
    val railShape = ShapeCache.smooth12
    val railBg = if (isLight) Color.Black.copy(alpha = 0.04f) else Color.White.copy(alpha = 0.08f)
    val contentColor = MaterialTheme.colorScheme.onBackground

    Surface(
        modifier = modifier
            .width(24.dp)
            .clip(railShape)
            .background(railBg),
        color = Color.Transparent,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            items(letters.size, key = { letters[it].code }) { index ->
                val letter = letters[index]
                val focusState = rememberTvFocusState(focusedScale = 1.15f)
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                val scale by animateFloatAsState(
                    targetValue = if (isPressed) 0.9f else 1f,
                    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                    label = "letterPressedScale",
                )
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = scale * focusState.scale
                            scaleY = scale * focusState.scale
                        }
                        .then(focusState.focusModifier)
                        .tvFocusIndicator(focusState, railShape)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = { onJump(letter) },
                        )
                        .padding(vertical = 1.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = letter.uppercaseChar().toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

/** Returns a new list with [value] removed if present, appended otherwise. */
private fun <T> List<T>.toggled(value: T): List<T> =
    if (value in this) this - value else this + value

/** Display label for a group-by mode. */
@Composable
private fun groupByLabel(groupBy: GroupBy): String = when (groupBy) {
    GroupBy.NONE -> stringResource(R.string.library_group_by_none)
    GroupBy.NAME -> stringResource(R.string.library_group_by_name)
    GroupBy.TYPE -> stringResource(R.string.library_group_by_type)
    GroupBy.GENRE -> stringResource(R.string.library_group_by_genre)
    GroupBy.YEAR -> stringResource(R.string.library_group_by_year)
}

