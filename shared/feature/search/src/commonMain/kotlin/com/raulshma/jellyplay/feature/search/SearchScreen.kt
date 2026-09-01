package com.raulshma.jellyplay.feature.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import com.raulshma.jellyplay.core.ui.animation.AnimationTokens
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DockedSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import com.raulshma.jellyplay.core.ui.components.ConfirmDialog
import com.raulshma.jellyplay.core.ui.components.ConfirmTone
import com.raulshma.jellyplay.core.ui.components.JellyPlayBackHandler
import com.raulshma.jellyplay.core.ui.components.JellyPlayLinearProgressIndicator
import com.raulshma.jellyplay.core.ui.components.progressFraction
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaQuickActionScope
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.PlayedStatus
import com.raulshma.jellyplay.core.model.SortOption
import com.raulshma.jellyplay.core.model.quickActions
import com.raulshma.jellyplay.core.ui.model.mediaTypeDisplayName
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.data.repository.SearchHistoryItem
import com.raulshma.jellyplay.core.ui.components.AppendErrorFooter
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.ExpressiveToolbarIconButton
import com.raulshma.jellyplay.core.ui.components.GlassDismissTag
import com.raulshma.jellyplay.core.ui.components.GlassFilterChip
import com.raulshma.jellyplay.core.ui.components.LocalMediaQuickActionController
import com.raulshma.jellyplay.core.ui.components.MediaQuickActionHost
import com.raulshma.jellyplay.core.ui.components.PosterCard
import com.raulshma.jellyplay.core.ui.components.QuickAction
import com.raulshma.jellyplay.core.ui.components.RemoveDownloadConfirmHost
import com.raulshma.jellyplay.core.ui.components.ScreenEmptyState
import com.raulshma.jellyplay.core.ui.components.rememberMediaQuickActionController
import com.raulshma.jellyplay.core.ui.components.rememberRemoveDownloadState
import com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColorState
import com.raulshma.jellyplay.core.ui.components.SeerrMediaCard
import com.raulshma.jellyplay.core.ui.components.SeerrRequestDialog
import com.raulshma.jellyplay.core.ui.components.rememberSeerrCardLoadingState
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.navigation.Route
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import com.raulshma.jellyplay.core.ui.adaptive.WindowSizeClass
import com.raulshma.jellyplay.core.ui.adaptive.*
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.RequestOrRestoreFocus
import com.raulshma.jellyplay.core.ui.tv.TvFocusableGrid
import com.raulshma.jellyplay.core.ui.tv.TvFocusableItemRow
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.ui.tv.input.onDpadKey
import com.raulshma.jellyplay.feature.search.components.SearchFilterSheet
import com.raulshma.jellyplay.feature.search.components.SearchSortSheet
import com.raulshma.jellyplay.feature.search.components.SearchStatusSheet
import com.raulshma.jellyplay.feature.search.components.playedStatusLabel
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.feature.search.generated.resources.Res
import com.raulshma.jellyplay.feature.search.generated.resources.search_action_cancel
import com.raulshma.jellyplay.feature.search.generated.resources.search_action_clear
import com.raulshma.jellyplay.feature.search.generated.resources.search_action_filters
import com.raulshma.jellyplay.feature.search.generated.resources.search_clear_all
import com.raulshma.jellyplay.feature.search.generated.resources.search_clear_history_confirm
import com.raulshma.jellyplay.feature.search.generated.resources.search_clear_search
import com.raulshma.jellyplay.feature.search.generated.resources.search_clear_search_history
import com.raulshma.jellyplay.feature.search.generated.resources.search_did_you_mean
import com.raulshma.jellyplay.feature.search.generated.resources.search_failed
import com.raulshma.jellyplay.feature.search.generated.resources.search_failed_to_load_more
import com.raulshma.jellyplay.feature.search.generated.resources.search_filter_rating_plus
import com.raulshma.jellyplay.feature.search.generated.resources.search_filter_status
import com.raulshma.jellyplay.feature.search.generated.resources.search_no_results_found
import com.raulshma.jellyplay.feature.search.generated.resources.search_on_device
import com.raulshma.jellyplay.feature.search.generated.resources.search_placeholder
import com.raulshma.jellyplay.feature.search.generated.resources.search_recent_searches
import com.raulshma.jellyplay.feature.search.generated.resources.search_remove
import com.raulshma.jellyplay.feature.search.generated.resources.search_request_via_seerr
import com.raulshma.jellyplay.feature.search.generated.resources.search_retry
import com.raulshma.jellyplay.feature.search.generated.resources.search_result_count
import com.raulshma.jellyplay.feature.search.generated.resources.search_search_your_library
import com.raulshma.jellyplay.feature.search.generated.resources.search_search_your_library_desc
import com.raulshma.jellyplay.feature.search.generated.resources.search_seerr_search_failed
import com.raulshma.jellyplay.feature.search.generated.resources.search_suggestions
import com.raulshma.jellyplay.feature.search.generated.resources.search_title
import com.raulshma.jellyplay.feature.search.generated.resources.search_try_adjusting_filters
import com.raulshma.jellyplay.feature.search.generated.resources.search_voice_prompt
import com.raulshma.jellyplay.feature.search.generated.resources.search_voice_search

val LocalPendingSearchQuery = compositionLocalOf<String?> { null }
val LocalConsumeSearchQuery = staticCompositionLocalOf<() -> Unit> { {} }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchScreen(
    onItemClick: (itemId: String, mediaType: com.raulshma.jellyplay.core.model.MediaType, parentId: String?, itemName: String) -> Unit,
    onNavigate: (Route) -> Unit = {},
    viewModel: SearchViewModel = koinViewModel(),
) {
    val pendingQuery = LocalPendingSearchQuery.current
    val consumeQuery = LocalConsumeSearchQuery.current
    androidx.compose.runtime.LaunchedEffect(pendingQuery) {
        pendingQuery?.let { query ->
            viewModel.search(query)
            consumeQuery()
        }
    }
    var requestItem by remember { mutableStateOf<com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem?>(null) }
    val seerrSnapshot by viewModel.seerrSnapshot.collectAsStateWithLifecycle()
    val seerrLoadingState = rememberSeerrCardLoadingState()

    // The live query string is only read inside leaf composables (the search
    // bar, the empty-state suggestions); the body sees this rarely-flipping
    // blank/nonblank signal so keystrokes don't recompose the whole screen.
    val queryHasText by remember { derivedStateOf { viewModel.query.isNotBlank() } }
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    val genres by viewModel.genres.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val showFilters by viewModel.showFilters.collectAsStateWithLifecycle()
    val searchHistory by viewModel.searchHistory.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()

    val pagedResults = viewModel.pagedResults.collectAsLazyPagingItems()
    val isRefreshing = pagedResults.loadState.refresh is LoadState.Loading
    val networkStatus by com.raulshma.jellyplay.core.ui.components.LocalNetworkStatus.current.collectAsStateWithLifecycle()

    // Only persist the query to "Recent Searches" once the pager confirms a
    // non-empty result set for it. See [SearchHistoryRecorder] — the effect
    // lives in a leaf so it can key on the live query without the screen body
    // recomposing per keystroke.
    SearchHistoryRecorder(pagedResults = pagedResults, viewModel = viewModel)

    val headerStatus = com.raulshma.jellyplay.core.ui.components.resolveHeaderStatus(
        isLoading = isRefreshing,
        hasError = pagedResults.loadState.refresh is LoadState.Error,
        networkStatus = networkStatus,
    )

    // Active-filter detection covers every dimension (parity with the Library
    // filter chip row) so the badge, BackHandler guard, and "Clear all" affordance
    // all reflect the full filter set — not just mediaTypes/genres.
    val hasNonDefaultSort = filters.sortBy != SortOption.YEAR_DESC
    val hasActiveFilters = filters.mediaTypes.isNotEmpty() ||
        filters.genres.isNotEmpty() ||
        filters.years.isNotEmpty() ||
        filters.tags.isNotEmpty() ||
        filters.minRating > 0f ||
        filters.playedStatus != PlayedStatus.ALL ||
        hasNonDefaultSort

    // Which immediate-apply single-select sheet is open (Sort / Status). The full
    // multi-dimension sheet is still driven by [showFilters] below; these are the
    // two always-visible chips that mirror the Library filter chip row.
    var openSortSheet by remember { mutableStateOf(false) }
    var openStatusSheet by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var isSearchFocused by remember { mutableStateOf(false) }

    // Guards the destructive "Clear all" recent-searches action behind a
    // confirmation, since clearing is irreversible and one tap away.
    var showClearHistoryDialog by remember { mutableStateOf(false) }

    // Grab focus into the search field on TV entry. Without this the first D-pad press drifts to
    // the drawer rail and expands it. LaunchedEffect(Unit) re-fires on every composition entry
    // (including back-nav from a detail page), which is the desired behavior for a top-level screen.
    val isTvEntry = LocalTvMode.current
    RequestOrRestoreFocus(
        focusRequester = if (isTvEntry) focusRequester else null,
        debugKey = "search_field",
    )

    JellyPlayBackHandler(enabled = isSearchFocused || queryHasText || hasActiveFilters) {
        when {
            isSearchFocused -> focusManager.clearFocus()
            queryHasText -> viewModel.search("")
            hasActiveFilters -> viewModel.clearFilters()
        }
    }

    val backgroundColorState = rememberScreenBackgroundColorState()

    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    val contentPad = adaptiveInfo.contentPadding(isTv)
    val spacing = adaptiveInfo.itemSpacing(isTv)
    val bottomPad = adaptiveInfo.bottomPadding(isTv)
    val seerrCardWidth = adaptiveInfo.rowCardWidth(isTv)

    val gridPadding = PaddingValues(
        start = contentPad,
        end = contentPad,
        top = 8.dp,
        bottom = bottomPad,
    )

    val gridCellSize = adaptiveInfo.gridCellSize(isTv)

    // Item awaiting a remove-download confirm from the quick-action menu.
    // Hoisted so the dialog survives the card leaving composition while open.
    val removeDownloadState = rememberRemoveDownloadState()

    // Collected (not read as a .value snapshot inside the resolve lambda) so
    // the resolver is rebuilt when the downloaded set changes — a download
    // completing flips the card's Download↔Remove-download action without
    // waiting for an unrelated recomposition. The set is distinct-collapsed
    // upstream, so active transfers don't churn it.
    val downloadedIds by viewModel.downloadedIds.collectAsStateWithLifecycle()

    // Long-press / TV-Menu quick actions for search result cards. The
    // controller is provided to every PosterCard below via
    // CompositionLocal; the TV Menu key opens the focused card's actions.
    // Download / Remove download ride the same intake as the library grid
    // (#147): a downloaded result flips the slot to "Remove download".
    val quickActionController = rememberMediaQuickActionController(
        resolveActions = remember(viewModel, downloadedIds) {
            { item: MediaItem ->
                item.quickActions(
                    MediaQuickActionScope.LIBRARY,
                    includeDownload = true,
                    isDownloaded = downloadedIds.contains(item.id),
                )
            }
        },
        executeAction = remember(viewModel, onItemClick) {
            { item: MediaItem, action: QuickAction ->
                when (action) {
                    QuickAction.PLAY -> onItemClick(item.id, item.mediaType, item.parentId, item.name)
                    QuickAction.MARK_WATCHED -> viewModel.markItemPlayed(item, true)
                    QuickAction.MARK_UNWATCHED -> viewModel.markItemPlayed(item, false)
                    // Result ids always echo the originating item (DownloadIntake),
                    // so the captured metadata stays accurate.
                    QuickAction.DOWNLOAD ->
                        viewModel.downloadItem(item, onOpenDetail = { id ->
                            onItemClick(id, item.mediaType, item.parentId, item.name)
                        })
                    QuickAction.REMOVE_DOWNLOAD -> removeDownloadState.request(item)
                    QuickAction.DETAILS -> onItemClick(item.id, item.mediaType, item.parentId, item.name)
                    else -> Unit
                }
            }
        },
    )
    // TV-only: the card currently holding D-pad focus, so the Menu key can open
    // its quick actions.
    var tvFocusedItem by remember { mutableStateOf<MediaItem?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind { drawRect(backgroundColorState.value) }
            .onDpadKey(
                onMenu = {
                    tvFocusedItem?.let { quickActionController.show(it) }
                    true
                },
            ),
    ) {
        CompositionLocalProvider(LocalMediaQuickActionController provides quickActionController) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
        ) {
            // ── Header Section (cinematic dark, white-on-dark text) ──
            // The gradient brush is rebuilt in the draw cache only when the
            // animated background color actually changes, not per recomposition.
            val headlineLarge = MaterialTheme.typography.headlineLarge
            val headerTitleStyle = remember(headlineLarge) {
                headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawWithCache {
                        val color = backgroundColorState.value
                        val headerGradientBrush = Brush.verticalGradient(
                            colors = listOf(
                                color.copy(alpha = 0.95f),
                                color,
                            ),
                        )
                        onDrawBehind { drawRect(headerGradientBrush) }
                    }
                    .statusBarsPadding()
                    .padding(top = 16.dp),
            ) {
                // ── Title + action row ──
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(MaterialTheme.motionScheme.slowEffectsSpec()) + slideInVertically(
                        MaterialTheme.motionScheme.slowSpatialSpec(),
                        initialOffsetY = { -40 },
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, end = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(Res.string.search_title),
                                style = headerTitleStyle,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            com.raulshma.jellyplay.core.ui.components.HeaderStatusIndicator(
                                status = headerStatus,
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box {
                                ExpressiveToolbarIconButton(
                                    onClick = { viewModel.toggleShowFilters() },
                                    icon = Tabler.Outline.Filter,
                                    contentDescription = stringResource(Res.string.search_action_filters),
                                    highlighted = hasActiveFilters,
                                )
                                if (hasActiveFilters) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(4.dp)
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary),
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ── Search field (MD3 expressive DockedSearchBar) ──
                // Reads the live query in the leaf so per-keystroke
                // recomposition stays confined to the bar.
                SearchInputBar(
                    focusRequester = focusRequester,
                    viewModel = viewModel,
                    onFocusedChange = { isSearchFocused = it },
                )
                // ── Sort + Status chip row (parity with Library's filter chip row) ──
                // Always-visible immediate-apply chips: Sort shows the active sort
                // (highlighted when non-default), Status shows the active played
                // status (highlighted when not ALL). Tapping each opens its single-
                // select sheet. The full multi-dimension filter sheet is still
                // reachable via the toolbar filter icon.
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    GlassFilterChip(
                        label = filters.sortBy.displayName,
                        selected = hasNonDefaultSort,
                        onClick = { openSortSheet = true },
                    )
                    GlassFilterChip(
                        label = if (filters.playedStatus == PlayedStatus.ALL) {
                            stringResource(Res.string.search_filter_status)
                        } else {
                            filters.playedStatus.playedStatusLabel()
                        },
                        selected = filters.playedStatus != PlayedStatus.ALL,
                        onClick = { openStatusSheet = true },
                    )
                }

                // ── Active filters bar (dismissible glass tags) ──
                AnimatedVisibility(
                    visible = hasActiveFilters,
                    enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()) + expandVertically(),
                    exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) + shrinkVertically(),
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
                                label = mediaType.mediaTypeDisplayName(),
                                onDismiss = { viewModel.toggleMediaType(mediaType) },
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
                        filters.years.forEach { year ->
                            GlassDismissTag(
                                label = year.toString(),
                                onDismiss = {
                                    viewModel.updateFilters(
                                        filters.copy(years = filters.years - year)
                                    )
                                },
                            )
                        }
                        filters.tags.forEach { tag ->
                            GlassDismissTag(
                                label = tag,
                                onDismiss = {
                                    viewModel.updateFilters(
                                        filters.copy(tags = filters.tags - tag)
                                    )
                                },
                            )
                        }
                        if (filters.minRating > 0f) {
                            GlassDismissTag(
                                label = stringResource(Res.string.search_filter_rating_plus, filters.minRating),
                                onDismiss = {
                                    viewModel.updateFilters(filters.copy(minRating = 0f))
                                },
                            )
                        }
                        if (filters.playedStatus != PlayedStatus.ALL) {
                            GlassDismissTag(
                                label = filters.playedStatus.playedStatusLabel(),
                                onDismiss = { viewModel.setPlayedStatus(PlayedStatus.ALL) },
                            )
                        }
                        val clearAllFocusState = rememberTvFocusState()
                        Box(
                            modifier = Modifier
                                .then(clearAllFocusState.focusModifier)
                                .tvFocusIndicator(clearAllFocusState, ShapeCache.smooth8)
                                .clip(ShapeCache.smooth8)
                                .clickable(role = androidx.compose.ui.semantics.Role.Button) { viewModel.clearFilters() }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        ) {
                            Text(
                                text = stringResource(Res.string.search_clear_all),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }

                // ── Result count ──
                AnimatedVisibility(
                    visible = pagedResults.itemCount > 0,
                    enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                ) {
                    Text(
                        text = pluralStringResource(Res.plurals.search_result_count, pagedResults.itemCount, pagedResults.itemCount),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(
                            horizontal = 24.dp,
                            vertical = 8.dp,
                        ),
                    )
                }
            }

            // ═══════════════════════════════════════════════════════════════
            // ── Grid Content
            // ═══════════════════════════════════════════════════════════════
            Column(modifier = Modifier.fillMaxSize()) {
                // Collect Seerr state (outside when block so it's always available)
                val seerrResults by viewModel.seerrResults.collectAsStateWithLifecycle()
                val isSeerrConnected by viewModel.isSeerrConnected.collectAsStateWithLifecycle()
                val isSeerrSearchEnabled by viewModel.isSeerrSearchEnabled.collectAsStateWithLifecycle()
                val seerrSearchError by viewModel.seerrSearchError.collectAsStateWithLifecycle()
                val showSeerr = isSeerrConnected && isSeerrSearchEnabled && seerrResults.isNotEmpty()
                val showSeerrError = isSeerrConnected && isSeerrSearchEnabled && seerrSearchError && !showSeerr

                // ── On-device (downloads) section ──
                // Surfaces downloaded items that match the current query. Always
                // rendered when non-empty so the user can play even without a
                // server connection. Placed above Seerr/Library because on-device
                // results are immediately playable.
                val offlineResults by viewModel.offlineResults.collectAsStateWithLifecycle()
                // De-duplicate against the library grid below so a downloaded item
                // that also exists in the library isn't rendered twice (Home already
                // does this for its downloaded row).
                val dedupedOfflineResults = remember(offlineResults, pagedResults.itemSnapshotList) {
                    if (offlineResults.isEmpty()) offlineResults
                    else {
                        val onlineIds = buildSet {
                            for (item in pagedResults.itemSnapshotList) {
                                item?.id?.takeUnless { it.isBlank() }?.let { add(it) }
                            }
                        }
                        if (onlineIds.isEmpty()) offlineResults
                        else offlineResults.filter { it.id !in onlineIds }
                    }
                }
                val showOffline = dedupedOfflineResults.isNotEmpty() && queryHasText
                if (showOffline) {
                    OfflineSearchSection(
                        items = dedupedOfflineResults,
                        contentPadding = contentPad,
                        spacing = spacing,
                        cardWidth = seerrCardWidth,
                        onItemClick = { item ->
                            onItemClick(item.id, item.mediaType, item.seriesId, item.name)
                        },
                    )
                }

                // Seerr results horizontal section (shown independently of library results)
                if (showSeerr) {
                    val uniqueSeerrResults = remember(seerrResults) { seerrResults.distinctBy { it.id } }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = contentPad, end = contentPad, top = 12.dp),
                    ) {
                        Text(
                            text = stringResource(Res.string.search_request_via_seerr),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        TvFocusableItemRow(
                            items = uniqueSeerrResults,
                            key = { it.id },
                            horizontalArrangement = Arrangement.spacedBy(spacing),
                            contentPadding = PaddingValues(end = contentPad),
                        ) { _, seerrItem, itemModifier ->
                            val onClick = remember(seerrItem.id, seerrItem.mediaType, onNavigate) {
                                {
                                    seerrLoadingState.startLoading(seerrItem.id)
                                    viewModel.prefetchSeerrDetails(seerrItem.id, seerrItem.mediaType) {
                                        seerrLoadingState.stopLoading(seerrItem.id)
                                        onNavigate(Route.SeerrDetail(seerrItem.id, seerrItem.mediaType))
                                    }
                                }
                            }
                            // Keyed on the whole item, not just id: a refreshed
                            // list can return a new object for the same id,
                            // and the request dialog must show that object.
                            val onRequestClick = remember(seerrItem) { { requestItem = seerrItem } }
                            SeerrMediaCard(
                                item = seerrItem,
                                imageUrl = seerrItem.posterUrl,
                                isLoading = seerrLoadingState.isLoading(seerrItem.id),
                                onClick = onClick,
                                onRequestClick = onRequestClick,
                                modifier = itemModifier.width(seerrCardWidth),
                            )
                        }
                    }
                }

                if (showSeerrError) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = contentPad, end = contentPad, top = 8.dp)
                            .clip(ShapeCache.smooth12)
                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                Tabler.Outline.AlertCircle,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                            )
                            Text(
                                text = stringResource(Res.string.search_seerr_search_failed),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Row(
                            modifier = Modifier
                                .clip(ShapeCache.smooth8)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                
                                .clickable(role = androidx.compose.ui.semantics.Role.Button) { viewModel.retrySeerrSearch() }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                Tabler.Outline.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = stringResource(Res.string.search_retry),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }

                // Library content (grid, empty state, or initial state)
                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    when {
                        pagedResults.itemCount == 0 && queryHasText && !isRefreshing && !showSeerr && !showSeerrError && !showOffline -> {
                            // Typo tolerance fallback: Jellyfin's media search is substring/prefix
                            // only, so a misspelled query ("Interstelar") returns nothing. With no
                            // easy way to push a fuzzy variant through the server query, surface
                            // "Did you mean?" suggestions derived from the user's own recent
                            // searches — a pure client-side prefix heuristic, no extra fetches.
                            val query = viewModel.query
                            val didYouMean by remember(query, searchHistory) {
                                derivedStateOf {
                                    if (query.length < 3 || searchHistory.isEmpty()) {
                                        emptyList()
                                    } else {
                                        searchHistory
                                            .asSequence()
                                            .map { it.query }
                                            .filter { it != query }
                                            .filter {
                                                // Suggest a past query that shares a meaningful
                                                // leading run of characters (catches single-word
                                                // typos) or any whitespace token with the typed query.
                                                it.commonPrefixWith(query, ignoreCase = true).length >= 3 ||
                                                    it.lowercase().split(' ', '\t').any { token ->
                                                        token.length >= 3 && query.lowercase().contains(token)
                                                    }
                                            }
                                            .distinct()
                                            .take(4)
                                            .toList()
                                    }
                                }
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(vertical = 48.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                ScreenEmptyState(
                                    icon = Tabler.Outline.Search,
                                    title = stringResource(Res.string.search_no_results_found),
                                    description = if (hasActiveFilters) stringResource(Res.string.search_try_adjusting_filters) else null,
                                )
                                if (didYouMean.isNotEmpty()) {
                                    Text(
                                        text = stringResource(Res.string.search_did_you_mean),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                                    )
                                    FlowRow(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 24.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        didYouMean.forEach { suggestion ->
                                            val suggestionFocusState = rememberTvFocusState()
                                            Row(
                                                modifier = Modifier
                                                    .then(suggestionFocusState.focusModifier)
                                                    .tvFocusIndicator(suggestionFocusState, CircleShape)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                                    .clickable { viewModel.search(suggestion) }
                                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Text(
                                                    text = suggestion,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Medium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        !queryHasText && !showSeerr -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                            ) {
                                // ── Discovery suggestions (favorited/liked items) ──
                                // Mirrors the official jellyfin-web empty state: a row of
                                // suggestions the user has favorited/liked, surfaced in random
                                // order. Clicking navigates to the item's detail page.
                                if (suggestions.isNotEmpty()) {
                                    SuggestionSection(
                                        items = suggestions,
                                        contentPadding = contentPad,
                                        spacing = spacing,
                                        cardWidth = seerrCardWidth,
                                        getImageUrl = viewModel::getImageUrl,
                                        onItemClick = { item ->
                                            onItemClick(item.id, item.mediaType, item.parentId, item.name)
                                        },
                                        onFocusedItemChange = { item -> tvFocusedItem = item },
                                    )
                                }

                                if (searchHistory.isNotEmpty()) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                    ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = stringResource(Res.string.search_recent_searches),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        val clearHistoryFocusState = rememberTvFocusState()
                                        Text(
                                            text = stringResource(Res.string.search_clear_all),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .then(clearHistoryFocusState.focusModifier)
                                                .tvFocusIndicator(clearHistoryFocusState, CircleShape)
                                                .clip(CircleShape)
                                                .clickable { showClearHistoryDialog = true }
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                        )
                                    }
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        searchHistory.forEach { item ->
                                            val historyRowFocusState = rememberTvFocusState()
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .then(historyRowFocusState.focusModifier)
                                                    .tvFocusIndicator(historyRowFocusState, MaterialTheme.shapes.small)
                                                    .clip(MaterialTheme.shapes.small)
                                                    .clickable { viewModel.search(item.query) }
                                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                    modifier = Modifier.weight(1f),
                                                ) {
                                                    Icon(
                                                        Tabler.Outline.Clock,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(18.dp),
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                    Text(
                                                        text = item.query,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                }
                                                val deleteFocusState = rememberTvFocusState()
                                                Box(
                                                    contentAlignment = Alignment.Center,
                                                    modifier = Modifier
                                                        .size(48.dp)
                                                        .then(deleteFocusState.focusModifier)
                                                        .tvFocusIndicator(deleteFocusState, CircleShape)
                                                        .clip(CircleShape)
                                                        .clickable { viewModel.deleteHistoryItem(item.id) },
                                                ) {
                                                    Icon(
                                                        Tabler.Outline.X,
                                                        contentDescription = stringResource(Res.string.search_remove),
                                                        modifier = Modifier.size(16.dp),
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                ScreenEmptyState(
                                    icon = Tabler.Outline.Search,
                                    title = stringResource(Res.string.search_search_your_library),
                                    description = stringResource(Res.string.search_search_your_library_desc),
                                )
                            }
                            } // close verticalScroll Column
                        }
                        else -> {
                            // ── Library grid ──
                            TvFocusableGrid(
                                itemCount = pagedResults.itemCount,
                                key = { index ->
                                    if (index in 0 until pagedResults.itemCount) {
                                        try {
                                            pagedResults.peek(index)?.id ?: "search_item_placeholder_$index"
                                        } catch (_: IndexOutOfBoundsException) {
                                            "search_item_placeholder_$index"
                                        }
                                    } else {
                                        "search_item_placeholder_$index"
                                    }
                                },
                                columns = GridCells.Adaptive(gridCellSize),
                                contentPadding = gridPadding,
                                horizontalArrangement = Arrangement.spacedBy(spacing),
                                verticalArrangement = Arrangement.spacedBy(spacing),
                                modifier = Modifier.fillMaxSize(),
                                contentType = { "mediaItem" },
                                onFocusedIndexChange = { index ->
                                    if (index in 0 until pagedResults.itemCount) {
                                        try {
                                            tvFocusedItem = pagedResults[index]
                                        } catch (_: IndexOutOfBoundsException) {
                                            tvFocusedItem = null
                                        }
                                    } else {
                                        tvFocusedItem = null
                                    }
                                },
                            ) { index, itemModifier ->
                                val item = if (index in 0 until pagedResults.itemCount) {
                                    try {
                                        pagedResults[index]
                                    } catch (_: IndexOutOfBoundsException) {
                                        null
                                    }
                                } else {
                                    null
                                }
                                if (item != null) {
                                    AnimatedSearchItem(index = index) {
                                        val itemProgress = item.progressFraction()
                                        // Memoize the per-item URL + click lambda (LibraryScreen
                                        // already does this) so a results re-emit (paging,
                                        // typing) doesn't recompute getImageUrl or allocate a
                                        // fresh onClick lambda per visible item.
                                        val imageUrl = remember(item.id) { viewModel.getImageUrl(item.id) }
                                        val onClick = remember(item.id, item.mediaType, item.parentId, item.name) {
                                            { onItemClick(item.id, item.mediaType, item.parentId, item.name) }
                                        }
                                        PosterCard(
                                            item = item,
                                            imageUrl = imageUrl,
                                            onClick = onClick,
                                            showProgress = itemProgress != null && itemProgress > 0f,
                                            progressPercent = itemProgress ?: 0f,
                                            blurHash = item.blurHashes.primary,
                                            sharedElementKey = "poster_${item.id}",
                                            modifier = itemModifier,
                                        )
                                    }
                                }
                            }

                            // ── Append loading (gradient fade + progress bar) ──
                            if (pagedResults.loadState.append is LoadState.Loading) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .drawWithCache {
                                            val brush = Brush.verticalGradient(
                                                colors = listOf(
                                                    Color.Transparent,
                                                    backgroundColorState.value,
                                                ),
                                            )
                                            onDrawBehind { drawRect(brush) }
                                        }
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

                            // ── Append error ──
                            if (pagedResults.loadState.append is LoadState.Error) {
                                val appendError = pagedResults.loadState.append as LoadState.Error
                                AppendErrorFooter(
                                    message = appendError.error.localizedMessage
                                        ?: stringResource(Res.string.search_failed_to_load_more),
                                    onRetry = { pagedResults.retry() },
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(16.dp)
                                        .fillMaxWidth(),
                                )
                            }

                            // ── Refresh loading ──
                            when (val refreshState = pagedResults.loadState.refresh) {
                                is LoadState.Loading -> {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
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
                                is LoadState.Error -> {
                                    ErrorScreen(
                                        message = refreshState.error.localizedMessage
                                            ?: stringResource(Res.string.search_failed),
                                        onRetry = { pagedResults.refresh() },
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                                is LoadState.NotLoading -> Unit
                            }
                        }
                    }
                } // close Box(library content)
            } // close Column(Grid Content)
        } // close Column
        } // close CompositionLocalProvider
    } // close Box
    MediaQuickActionHost(quickActionController)

    // Remove-download confirm: quick-action removal only ever deletes the
    // local download — the server copy is untouched.
    RemoveDownloadConfirmHost(
        state = removeDownloadState,
        onConfirmRemove = { viewModel.removeItemDownload(it) },
    )

    // Seerr request dialog
    requestItem?.let { item ->
        // Fetch service details and TV seasons on-demand when dialog opens
        LaunchedEffect(item.id) {
            viewModel.loadSeerrServiceDetails(item.mediaType)
            if (item.mediaType.equals("tv", ignoreCase = true)) {
                viewModel.loadTvSeasons(item.id)
            }
        }

        SeerrRequestDialog(
            item = item,
            snapshot = seerrSnapshot,
            onConfirm = { serverId, profileId, rootFolder, tags, seasons ->
                viewModel.requestSeerrMedia(item, seasons, serverId, profileId, rootFolder, tags)
            },
            onDismiss = {
                requestItem = null
                viewModel.clearRequestResult()
            },
        )
    }

    if (showFilters) {
        SearchFilterSheet(
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

    if (openSortSheet) {
        SearchSortSheet(
            current = filters.sortBy,
            onApply = { viewModel.setSortBy(it) },
            onDismiss = { openSortSheet = false },
        )
    }

    if (openStatusSheet) {
        SearchStatusSheet(
            current = filters.playedStatus,
            onApply = { viewModel.setPlayedStatus(it) },
            onDismiss = { openStatusSheet = false },
        )
    }

    if (showClearHistoryDialog) {
        ConfirmDialog(
            title = stringResource(Res.string.search_clear_search_history),
            message = stringResource(Res.string.search_clear_history_confirm),
            confirmText = stringResource(Res.string.search_action_clear),
            dismissText = stringResource(Res.string.search_action_cancel),
            tone = ConfirmTone.DESTRUCTIVE,
            icon = Tabler.Outline.Trash,
            onConfirm = {
                showClearHistoryDialog = false
                viewModel.clearHistory()
            },
            onDismiss = { showClearHistoryDialog = false },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ── Subcomponents (matching LibraryScreen / MediaDetailScreen design language)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The MD3 expressive search field. Reads the live query here — not in the
 * screen body — so per-keystroke recomposition is confined to this bar.
 * Voice search rides the expect/actual [rememberVoiceSearchLauncher] seam:
 * the returned launcher is null where the platform has no speech
 * recognition, hiding the mic affordance.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SearchInputBar(
    focusRequester: FocusRequester,
    viewModel: SearchViewModel,
    onFocusedChange: (Boolean) -> Unit,
) {
    val query = viewModel.query
    val focusManager = LocalFocusManager.current
    // Voice-search seam (expect/actual): null when the platform has no speech
    // recognition activity, hiding the mic affordance below.
    val voiceSearchLauncher = rememberVoiceSearchLauncher(
        prompt = stringResource(Res.string.search_voice_prompt),
        onResult = { spokenText -> spokenText?.let(viewModel::search) },
    )
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(MaterialTheme.motionScheme.slowEffectsSpec()) + slideInVertically(
            MaterialTheme.motionScheme.slowSpatialSpec(),
            initialOffsetY = { 40 },
        ),
    ) {
        DockedSearchBar(
            inputField = {
                SearchBarDefaults.InputField(
                    query = query,
                    onQueryChange = { viewModel.search(it) },
                    onSearch = {
                        // The IME "Search"/"Done" action: clear focus so the
                        // soft keyboard dismisses. The debounced query already
                        // covers the actual search; this only improves the
                        // keyboard ergonomics for hardware/IME submit.
                        focusManager.clearFocus()
                    },
                    expanded = false,
                    onExpandedChange = { },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusEvent { onFocusedChange(it.isFocused) }
                        ,
                        placeholder = {
                        Text(
                            stringResource(Res.string.search_placeholder),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Tabler.Outline.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    trailingIcon = {
                        if (query.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(ShapeCache.smooth8)
                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                                    .clickable { viewModel.search("") },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Tabler.Outline.X,
                                    contentDescription = stringResource(Res.string.search_clear_search),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        } else if (voiceSearchLauncher != null) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(ShapeCache.smooth8)
                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                                    .clickable { voiceSearchLauncher() },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Tabler.Outline.Microphone,
                                    contentDescription = stringResource(Res.string.search_voice_search),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    },
                )
            },
            expanded = false,
            onExpandedChange = { },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            shape = ShapeCache.smooth16,
            colors = SearchBarDefaults.colors(
                containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
            ),
        ) { }
    }
}

/**
 * Result-gated writer of "Recent Searches". Runs in a leaf keyed on the live
 * query so the screen body doesn't recompose per keystroke.
 */
@Composable
private fun SearchHistoryRecorder(
    pagedResults: LazyPagingItems<MediaItem>,
    viewModel: SearchViewModel,
) {
    // Only persist the query to "Recent Searches" once the pager confirms a
    // non-empty result set for it. This prevents typo'd queries (zero matches)
    // from polluting search history. Keyed on refresh state + query so it
    // re-evaluates when a new search settles; the item count is read inside
    // the effect so page appends don't re-record the same query.
    val query = viewModel.query
    val refreshLoadState = pagedResults.loadState.refresh
    LaunchedEffect(refreshLoadState, query) {
        val settled = refreshLoadState is LoadState.NotLoading && pagedResults.itemCount > 0
        if (settled && query.isNotBlank()) {
            viewModel.onSearchResultsShown(query)
        }
    }
}

@Composable
private fun AnimatedSearchItem(
    index: Int,
    content: @Composable () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        visible = true
    }

    val animationProgress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "searchItemAnimation",
    )

    Box(
        modifier = Modifier.graphicsLayer {
            this.alpha = animationProgress
            translationY = (1f - animationProgress) * 20f
            val scale = 0.95f + (0.05f * animationProgress)
            scaleX = scale
            scaleY = scale
        },
    ) {
        content()
    }
}

/**
 * Discovery suggestions shown in the empty search state — a horizontal row of
 * the user's favorited/liked items surfaced in random order (mirrors the
 * official jellyfin-web behavior). Clicking a suggestion opens the item's
 * detail page rather than filling the search box.
 */
@Composable
private fun SuggestionSection(
    items: List<com.raulshma.jellyplay.core.model.MediaItem>,
    contentPadding: androidx.compose.ui.unit.Dp,
    spacing: androidx.compose.ui.unit.Dp,
    cardWidth: androidx.compose.ui.unit.Dp,
    getImageUrl: (String) -> String,
    onItemClick: (com.raulshma.jellyplay.core.model.MediaItem) -> Unit,
    onFocusedItemChange: (com.raulshma.jellyplay.core.model.MediaItem?) -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = contentPadding, end = contentPadding, top = 12.dp, bottom = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Tabler.Outline.Star,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(Res.string.search_suggestions),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )
        }
        TvFocusableItemRow(
            items = items,
            key = { it.id },
            horizontalArrangement = Arrangement.spacedBy(spacing),
            contentPadding = PaddingValues(end = contentPadding),
            onFocusedIndexChange = { index -> onFocusedItemChange(items.getOrNull(index)) },
        ) { _, item, itemModifier ->
            val imageUrl = remember(item.id) { getImageUrl(item.id) }
            PosterCard(
                item = item,
                imageUrl = imageUrl,
                onClick = { onItemClick(item) },
                sharedElementKey = "suggestion_${item.id}",
                modifier = itemModifier.width(cardWidth),
            )
        }
    }
}

/**
 * Horizontal row of on-device (downloaded) items matching the current query.
 * Rendered above the library grid and Seerr row because offline results are
 * immediately playable, even without a server connection. Uses
 * [TvFocusableItemRow] so the same code path handles touch and D-pad entry.
 */
@Composable
private fun OfflineSearchSection(
    items: List<OfflineMediaItem>,
    contentPadding: androidx.compose.ui.unit.Dp,
    spacing: androidx.compose.ui.unit.Dp,
    cardWidth: androidx.compose.ui.unit.Dp,
    onItemClick: (OfflineMediaItem) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = contentPadding, end = contentPadding, top = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                Tabler.Outline.DeviceTv,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(Res.string.search_on_device),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        TvFocusableItemRow(
            items = items,
            key = { it.id },
            horizontalArrangement = Arrangement.spacedBy(spacing),
            contentPadding = PaddingValues(end = contentPadding),
        ) { _, item, itemModifier ->
            OfflineSearchCard(
                item = item,
                onClick = { onItemClick(item) },
                modifier = itemModifier.width(cardWidth),
            )
        }
    }
}

@Composable
private fun OfflineSearchCard(
    item: OfflineMediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusState = rememberTvFocusState()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(focusState.focusModifier)
            .tvFocusIndicator(focusState, ShapeCache.smooth12)
            .clip(ShapeCache.smooth12)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) {
        val imageModifier = Modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f)
            .clip(ShapeCache.smooth12)
        if (!item.posterPath.isNullOrBlank()) {
            MediaImage(
                url = item.posterPath!!,
                contentDescription = item.name,
                blurHash = item.blurHashPrimary,
                modifier = imageModifier,
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = imageModifier.background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = item.name.take(2).uppercase(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = item.name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                Tabler.Outline.Download,
                contentDescription = null,
                modifier = Modifier.size(10.dp),
                tint = MaterialTheme.colorScheme.tertiary,
            )
        Text(
            text = item.mediaType.mediaTypeDisplayName(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    }
}
