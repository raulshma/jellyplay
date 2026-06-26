package com.raulshma.jellyplay.feature.search

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import com.raulshma.jellyplay.core.designsystem.theme.AlphaEasing
import com.raulshma.jellyplay.core.designsystem.theme.FancyTransitionEasing
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DockedSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import com.raulshma.jellyplay.core.ui.components.JellyPlayLinearProgressIndicator
import com.raulshma.jellyplay.core.ui.components.progressFraction
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import androidx.paging.compose.itemKey
import com.raulshma.jellyplay.core.data.repository.SearchHistoryItem
import com.raulshma.jellyplay.core.ui.components.AppendErrorFooter
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.ExpressiveToolbarIconButton
import com.raulshma.jellyplay.core.ui.components.GlassDismissTag
import com.raulshma.jellyplay.core.ui.components.PosterCard
import com.raulshma.jellyplay.core.ui.components.ScreenEmptyState
import com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColor
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
import com.raulshma.jellyplay.feature.search.components.SearchFilterSheet
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import java.util.Locale
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import androidx.compose.ui.res.stringResource
import com.raulshma.jellyplay.feature.search.R

val LocalPendingSearchQuery = compositionLocalOf<String?> { null }
val LocalConsumeSearchQuery = staticCompositionLocalOf<() -> Unit> { {} }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchScreen(
    onItemClick: (itemId: String, mediaType: com.raulshma.jellyplay.core.model.MediaType, parentId: String?, itemName: String) -> Unit,
    onNavigate: (Route) -> Unit = {},
    viewModel: SearchViewModel = hiltViewModel(),
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
    val requestResult by viewModel.requestResult.collectAsStateWithLifecycle()
    val radarrServers by viewModel.radarrServers.collectAsStateWithLifecycle()
    val sonarrServers by viewModel.sonarrServers.collectAsStateWithLifecycle()
    val tvSeasons by viewModel.tvSeasons.collectAsStateWithLifecycle()
    val isLoadingSeerrServices by viewModel.isLoadingSeerrServices.collectAsStateWithLifecycle()
    val seerrLoadingState = rememberSeerrCardLoadingState()

    val query = viewModel.query
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    val genres by viewModel.genres.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val showFilters by viewModel.showFilters.collectAsStateWithLifecycle()
    val searchHistory by viewModel.searchHistory.collectAsStateWithLifecycle()

    val pagedResults = viewModel.pagedResults.collectAsLazyPagingItems()
    val isRefreshing = pagedResults.loadState.refresh is LoadState.Loading
    val networkStatus by com.raulshma.jellyplay.core.ui.components.LocalNetworkStatus.current.collectAsStateWithLifecycle()

    val headerStatus = com.raulshma.jellyplay.core.ui.components.resolveHeaderStatus(
        isLoading = isRefreshing,
        hasError = false,
        networkStatus = networkStatus,
    )

    val hasActiveFilters = filters.mediaTypes.isNotEmpty() || filters.genres.isNotEmpty()

    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var isSearchFocused by remember { mutableStateOf(false) }

    // Grab focus into the search field on TV entry. Without this the first D-pad press drifts to
    // the drawer rail and expands it. LaunchedEffect(Unit) re-fires on every composition entry
    // (including back-nav from a detail page), which is the desired behavior for a top-level screen.
    val isTvEntry = LocalTvMode.current
    RequestOrRestoreFocus(
        focusRequester = if (isTvEntry) focusRequester else null,
        debugKey = "search_field",
    )

    BackHandler(enabled = isSearchFocused || query.isNotBlank() || hasActiveFilters) {
        when {
            isSearchFocused -> focusManager.clearFocus()
            query.isNotBlank() -> viewModel.search("")
            hasActiveFilters -> viewModel.clearFilters()
        }
    }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = matches?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                viewModel.search(spokenText)
            }
        }
    }

    val backgroundColor = rememberScreenBackgroundColor()

    var headerVisible by remember { mutableStateOf(true) }

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
        ) {
            // ═══════════════════════════════════════════════════════════════
            // ── Header Section (cinematic dark, white-on-dark text)
            // ═══════════════════════════════════════════════════════════════
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
                    .padding(top = 16.dp),
            ) {
                // ── Title + action row ──
                AnimatedVisibility(
                    visible = headerVisible,
                    enter = fadeIn(tween(500, easing = AlphaEasing)) + slideInVertically(
                        tween(500, easing = FancyTransitionEasing),
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
                                text = "Search",
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                ),
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
                                    contentDescription = "Filters",
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
                AnimatedVisibility(
                    visible = headerVisible,
                    enter = fadeIn(tween(500, delayMillis = 100, easing = AlphaEasing)) + slideInVertically(
                        tween(500, delayMillis = 100, easing = FancyTransitionEasing),
                        initialOffsetY = { 40 },
                    ),
                ) {
                    DockedSearchBar(
                        inputField = {
                            SearchBarDefaults.InputField(
                                query = query,
                                onQueryChange = { viewModel.search(it) },
                                onSearch = { },
                                expanded = false,
                                onExpandedChange = { },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester)
                                    .onFocusEvent { isSearchFocused = it.isFocused }
                                    ,
                                    placeholder = {
                                    Text(
                                        "Search movies, shows, music...",
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
                                                contentDescription = "Clear search",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(ShapeCache.smooth8)
                                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                                                .clickable {
                                                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                                        putExtra(
                                                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                                                        )
                                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                                                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Search for movies, shows, music...")
                                                    }
                                                    speechLauncher.launch(intent)
                                                },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                imageVector = Tabler.Outline.Microphone,
                                                contentDescription = "Voice search",
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

                // ── Search suggestions dropdown ──
                val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
                AnimatedVisibility(
                    visible = suggestions.isNotEmpty() && query.isNotBlank() && isSearchFocused,
                    enter = fadeIn(tween(200, easing = AlphaEasing)) + expandVertically(),
                    exit = fadeOut(tween(150, easing = AlphaEasing)) + shrinkVertically(),
                ) {
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .padding(top = 4.dp)
                            .clip(ShapeCache.smooth16)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f)),
                    ) {
                        items(suggestions.take(8), key = { it.id }) { item ->
                            val suggestionFocusState = rememberTvFocusState(focusedScale = 1.0f)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(suggestionFocusState.focusModifier)
                                    .tvFocusIndicator(suggestionFocusState, ShapeCache.smooth8)
                                    .clickable {
                                        viewModel.search(item.name)
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(
                                    imageVector = when (item.mediaType) {
                                        MediaType.MOVIE -> Tabler.Outline.Movie
                                        MediaType.SERIES -> Tabler.Outline.DeviceTv
                                        MediaType.EPISODE -> Tabler.Outline.DeviceTv
                                        MediaType.AUDIO, MediaType.MUSIC, MediaType.ALBUM -> Tabler.Outline.Music
                                        else -> Tabler.Outline.File
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        item.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    val subtitle = item.year?.toString() ?: item.mediaType.name
                                    Text(
                                        subtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Active filters bar (dismissible glass tags) ──
                AnimatedVisibility(
                    visible = hasActiveFilters,
                    enter = fadeIn(tween(200, easing = AlphaEasing)) + expandVertically(),
                    exit = fadeOut(tween(200, easing = AlphaEasing)) + shrinkVertically(),
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
                        val clearAllFocusState = rememberTvFocusState()
                        Box(
                            modifier = Modifier
                                .then(clearAllFocusState.focusModifier)
                                .tvFocusIndicator(clearAllFocusState, ShapeCache.smooth8)
                                .clip(ShapeCache.smooth8)
                                .clickable { viewModel.clearFilters() }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        ) {
                            Text(
                                text = "Clear all",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }

                // ── Result count ──
                AnimatedVisibility(
                    visible = headerVisible && pagedResults.itemCount > 0,
                    enter = fadeIn(tween(400, delayMillis = 200, easing = AlphaEasing)),
                ) {
                    Text(
                        text = "${pagedResults.itemCount} results",
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
                val showOffline = offlineResults.isNotEmpty() && query.isNotBlank()
                if (showOffline) {
                    OfflineSearchSection(
                        items = offlineResults,
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
                            text = "Request via Seerr",
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
                            SeerrMediaCard(
                                item = seerrItem,
                                imageUrl = seerrItem.posterUrl,
                                isLoading = seerrLoadingState.isLoading(seerrItem.id),
                                onClick = {
                                    seerrLoadingState.startLoading(seerrItem.id)
                                    viewModel.prefetchSeerrDetails(seerrItem.id, seerrItem.mediaType) {
                                        seerrLoadingState.stopLoading(seerrItem.id)
                                        onNavigate(Route.SeerrDetail(seerrItem.id, seerrItem.mediaType))
                                    }
                                },
                                onRequestClick = { requestItem = seerrItem },
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
                                text = "Seerr search failed",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Row(
                            modifier = Modifier
                                .clip(ShapeCache.smooth8)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                
                                .clickable { viewModel.retrySeerrSearch() }
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
                                text = "Retry",
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
                        pagedResults.itemCount == 0 && query.isNotBlank() && !isRefreshing && !showSeerr && !showSeerrError && !showOffline -> {
                            ScreenEmptyState(
                                icon = Tabler.Outline.Search,
                                title = stringResource(R.string.search_no_results_found),
                                description = if (hasActiveFilters) stringResource(R.string.search_try_adjusting_filters) else null,
                            )
                        }
                        query.isBlank() && !showSeerr -> {
                            if (searchHistory.isNotEmpty()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
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
                                            text = "Recent Searches",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        val clearHistoryFocusState = rememberTvFocusState()
                                        Text(
                                            text = "Clear all",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .then(clearHistoryFocusState.focusModifier)
                                                .tvFocusIndicator(clearHistoryFocusState, CircleShape)
                                                .clip(CircleShape)
                                                .clickable { viewModel.clearHistory() }
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                        )
                                    }
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        items(
                                            count = searchHistory.size,
                                            key = { searchHistory[it].id },
                                        ) { index ->
                                            val item = searchHistory[index]
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
                                                        contentDescription = "Remove",
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
                                    title = "Search your library",
                                    description = "Movies, shows, music, and more",
                                )
                            }
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
                                        PosterCard(
                                            item = item,
                                            imageUrl = viewModel.getImageUrl(item.id),
                                            onClick = { onItemClick(item.id, item.mediaType, item.parentId, item.name) },
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

                            // ── Append error ──
                            if (pagedResults.loadState.append is LoadState.Error) {
                                val appendError = pagedResults.loadState.append as LoadState.Error
                                AppendErrorFooter(
                                    message = appendError.error.localizedMessage
                                        ?: stringResource(R.string.search_failed_to_load_more),
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
                                            ?: stringResource(R.string.search_failed),
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
        }
    }

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
}

// ─────────────────────────────────────────────────────────────────────────────
// ── Subcomponents (matching LibraryScreen / MediaDetailScreen design language)
// ─────────────────────────────────────────────────────────────────────────────

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
                text = "On-device",
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
                text = item.mediaType.displayName(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun MediaType.displayName(): String = when (this) {
    MediaType.MOVIE -> "Movie"
    MediaType.SERIES -> "Series"
    MediaType.SEASON -> "Season"
    MediaType.EPISODE -> "Episode"
    MediaType.MUSIC, MediaType.AUDIO -> "Track"
    MediaType.ALBUM -> "Album"
    MediaType.ARTIST -> "Artist"
    MediaType.MUSIC_VIDEO -> "Music Video"
    MediaType.COLLECTION -> "Collection"
    MediaType.PHOTO, MediaType.PHOTO_FOLDER -> "Photo"
    MediaType.LIVE_TV -> "Live TV"
    MediaType.CHANNEL -> "Channel"
    MediaType.UNKNOWN -> "Item"
}
