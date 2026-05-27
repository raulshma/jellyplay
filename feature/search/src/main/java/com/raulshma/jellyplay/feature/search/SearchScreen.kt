package com.raulshma.jellyplay.feature.search

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DockedSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.PosterCard
import com.raulshma.jellyplay.core.ui.components.SeerrMediaCard
import com.raulshma.jellyplay.core.ui.components.SeerrRequestDialog
import com.raulshma.jellyplay.core.ui.components.rememberSeerrCardLoadingState
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.adaptive.WindowSizeClass
import com.raulshma.jellyplay.core.ui.adaptive.*
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.tvFocusable
import com.raulshma.jellyplay.feature.search.components.SearchFilterSheet
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchScreen(
    onItemClick: (String) -> Unit,
    onNavigate: (Route) -> Unit = {},
    viewModel: SearchViewModel = hiltViewModel(),
) {
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
    val showFilters by viewModel.showFilters.collectAsStateWithLifecycle()

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

    val isLightTheme = MaterialTheme.colorScheme.background.let { bg -> (bg.red * 0.299f + bg.green * 0.587f + bg.blue * 0.114f) > 0.5f }
    val backgroundColor = if (isLightTheme) {
        MaterialTheme.colorScheme.background
    } else {
        lerp(
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.surface,
            0.70f,
        )
    }

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
                    enter = fadeIn(tween(AnimationTokens.SlowDuration, easing = AlphaEasing)) + slideInVertically(
                        tween(AnimationTokens.SlowDuration, easing = FancyTransitionEasing),
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
                                GlassIconButton(
                                    onClick = { viewModel.toggleShowFilters() },
                                    icon = Icons.Default.FilterList,
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
                    enter = fadeIn(tween(AnimationTokens.SlowDuration, delayMillis = 100, easing = AlphaEasing)) + slideInVertically(
                        tween(AnimationTokens.SlowDuration, delayMillis = 100, easing = FancyTransitionEasing),
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
                                    .tvFocusable(),
                                placeholder = {
                                    Text(
                                        "Search movies, shows, music...",
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                        modifier = Modifier.size(20.dp),
                                    )
                                },
                                trailingIcon = {
                                    if (query.isNotBlank()) {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(ShapeCache.smooth8)
                                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                                                .tvFocusable().clickable { viewModel.search("") },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Clear search",
                                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(ShapeCache.smooth8)
                                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                                                .tvFocusable().clickable {
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
                                                imageVector = Icons.Default.Mic,
                                                contentDescription = "Voice search",
                                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
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

                // ── Active filters bar (dismissible glass tags) ──
                AnimatedVisibility(
                    visible = hasActiveFilters,
                    enter = fadeIn(tween(AnimationTokens.DefaultDuration, easing = AlphaEasing)) + expandVertically(),
                    exit = fadeOut(tween(AnimationTokens.DefaultDuration, easing = AlphaEasing)) + shrinkVertically(),
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
                        Box(
                            modifier = Modifier
                                .clip(ShapeCache.smooth8)
                                .tvFocusable().clickable { viewModel.clearFilters() }
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
                    enter = fadeIn(tween(AnimationTokens.StandardDuration, delayMillis = 200, easing = AlphaEasing)),
                ) {
                    Text(
                        text = "${pagedResults.itemCount} results",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
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
                val showSeerr = isSeerrConnected && isSeerrSearchEnabled && seerrResults.isNotEmpty()

                // Seerr results horizontal section (shown independently of library results)
                if (showSeerr) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = contentPad, end = contentPad, top = 12.dp),
                    ) {
                        Text(
                            text = "Request via Seerr",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(spacing),
                            contentPadding = PaddingValues(end = contentPad),
                        ) {
                            items(
                                count = seerrResults.size,
                                key = { index -> "seerr-${seerrResults[index].id}" },
                                contentType = { "seerrSearchResult" },
                            ) { index ->
                                val seerrItem = seerrResults[index]
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
                                    modifier = Modifier.width(seerrCardWidth),
                                )
                            }
                        }
                    }
                }

                // Library content (grid, empty state, or initial state)
                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    when {
                        pagedResults.itemCount == 0 && query.isNotBlank() && !isRefreshing && !showSeerr -> {
                            // ── Empty state ──
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                    )
                                    Text(
                                        text = "No results found",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    )
                                    if (hasActiveFilters) {
                                        Text(
                                            text = "Try adjusting your filters",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                        )
                                    }
                                }
                            }
                        }
                        pagedResults.itemCount == 0 && query.isBlank() && !showSeerr -> {
                            // ── Initial state ──
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = null,
                                        modifier = Modifier.size(64.dp),
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                                    )
                                    Text(
                                        text = "Search your library",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                    )
                                    Text(
                                        text = "Movies, shows, music, and more",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                                    )
                                }
                            }
                        }
                        else -> {
                            // ── Library grid ──
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(gridCellSize),
                                contentPadding = gridPadding,
                                horizontalArrangement = Arrangement.spacedBy(spacing),
                                verticalArrangement = Arrangement.spacedBy(spacing),
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                items(
                                    count = pagedResults.itemCount,
                                    key = pagedResults.itemKey { it.id },
                                    contentType = { "mediaItem" },
                                ) { index ->
                                    val item = pagedResults[index]
                                    if (item != null) {
                                        AnimatedSearchItem(index = index) {
                                            PosterCard(
                                                item = item,
                                                imageUrl = viewModel.getImageUrl(item.id),
                                                onClick = { onItemClick(item.id) },
                                                showProgress = item.playbackPositionTicks != null && item.playbackPositionTicks!! > 0,
                                                progressPercent = if (item.runTimeTicks != null && item.runTimeTicks!! > 0) {
                                                    (item.playbackPositionTicks?.toFloat() ?: 0f) / item.runTimeTicks!!.toFloat()
                                                } else 0f,
                                                blurHash = item.blurHashes.primary,
                                            )
                                        }
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
                                    androidx.compose.material3.LinearProgressIndicator(
                                        modifier = Modifier
                                            .fillMaxWidth(0.4f)
                                            .clip(ShapeCache.smooth4),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                                    )
                                }
                            }

                            // ── Append error ──
                            if (pagedResults.loadState.append is LoadState.Error) {
                                val appendError = pagedResults.loadState.append as LoadState.Error
                                Text(
                                    text = appendError.error.localizedMessage ?: "Failed to load more",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
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
                                        androidx.compose.material3.LinearProgressIndicator(
                                            modifier = Modifier
                                                .fillMaxWidth(0.4f)
                                                .clip(ShapeCache.smooth4),
                                            color = MaterialTheme.colorScheme.primary,
                                            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                                        )
                                    }
                                }
                                is LoadState.Error -> {
                                    ErrorScreen(
                                        message = refreshState.error.localizedMessage ?: "Search failed",
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
private fun GlassIconButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    highlighted: Boolean = false,
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(ShapeCache.smooth10)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = if (highlighted) 0.18f else 0.08f))
            .tvFocusable().clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(18.dp),
            tint = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
        )
    }
}

@Composable
private fun GlassDismissTag(
    label: String,
    onDismiss: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(stiffness = 500f),
        label = "tagScale",
    )

    Row(
        modifier = Modifier
            .clip(ShapeCache.smooth12)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .tvFocusable().clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onDismiss,
            )
            .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            fontWeight = FontWeight.Medium,
        )
        Icon(
            Icons.Default.Close,
            contentDescription = "Remove",
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        )
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
        animationSpec = tween(300, delayMillis = (index % 12) * 30, easing = FastOutSlowInEasing),
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
