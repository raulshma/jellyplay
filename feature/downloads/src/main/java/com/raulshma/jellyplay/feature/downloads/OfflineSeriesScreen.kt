package com.raulshma.jellyplay.feature.downloads

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Check
import com.composables.icons.tabler.outline.DeviceFloppy
import com.composables.icons.tabler.outline.PlayerPlay
import com.composables.icons.tabler.outline.Star
import com.composables.icons.tabler.outline.Trash
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.model.formatBytes
import com.raulshma.jellyplay.core.ui.adaptive.AdaptiveBackdropHeight
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.WindowSizeClass
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.JellyPlayLinearProgressIndicator
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.ScreenEmptyState
import com.raulshma.jellyplay.core.ui.components.ScreenLoadingState
import com.raulshma.jellyplay.core.ui.components.StaggeredSection
import com.raulshma.jellyplay.core.ui.components.TransparentTopBar
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.TvFocusableItemRow
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer

@Composable
fun OfflineSeriesScreen(
    seriesId: String,
    onPlayOffline: (itemId: String) -> Unit,
    onBack: () -> Unit,
    viewModel: OfflineSeriesViewModel = hiltViewModel(),
) {
    val seriesItem by viewModel.seriesItem.collectAsStateWithLifecycle(initialValue = null)
    val seasons by viewModel.seasons.collectAsStateWithLifecycle(initialValue = emptyList())
    val episodes by viewModel.episodes.collectAsStateWithLifecycle(initialValue = emptyMap())
    val totalSizeBytes by viewModel.totalSizeBytes.collectAsStateWithLifecycle(initialValue = 0L)
    val isTv = LocalTvMode.current
    val adaptiveInfo = LocalAdaptiveInfo.current
    val contentPad = adaptiveInfo.contentPadding(isTv)

    LaunchedEffect(seriesId) { viewModel.load(seriesId) }

    var selectedSeasonIndex by remember { mutableIntStateOf(0) }
    val selectedSeason = seasons.getOrNull(selectedSeasonIndex)
    val seasonEpisodes = selectedSeason?.let { episodes[it.id] } ?: emptyList()
    var showDeleteDialog by remember { mutableStateOf(false) }

    val listFocusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    TvGrabInitialFocus(
        focusRequester = listFocusRequester,
        itemCount = seasonEpisodes.size,
        tag = "offline_series_init",
    )

    if (viewModel.isLoading && seriesItem == null) {
        JellyPlayScreenScaffold(title = "Loading...", onBack = onBack) { ScreenLoadingState() }
        return
    }

    val series = seriesItem
    val density = LocalDensity.current
    val backdropHeight = when {
        isTv -> AdaptiveBackdropHeight.Tv
        adaptiveInfo.windowSizeClass == WindowSizeClass.Expanded -> AdaptiveBackdropHeight.Expanded
        else -> AdaptiveBackdropHeight.Portrait
    }
    val baseBackdropHeight = backdropHeight / 1.2f
    val spacerHeightPx = with(density) { (baseBackdropHeight - 150.dp).toPx() }
    val collapsedHeightPx = with(density) { backdropHeight.toPx() }
    val scrollOffset by remember {
        derivedStateOf {
            (if (listState.firstVisibleItemIndex > 0) spacerHeightPx else 0f) +
                listState.firstVisibleItemScrollOffset.toFloat()
        }
    }
    val scrollFraction by remember {
        derivedStateOf { (scrollOffset / collapsedHeightPx).coerceIn(0f, 1f) }
    }
    val scrollCollapsed by animateFloatAsState(
        targetValue = if (scrollFraction > 0.7f) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "seriesScrollCollapsed",
    )
    val animatedContainerColor = lerp(
        Color.Transparent,
        MaterialTheme.colorScheme.background.copy(alpha = 0.95f),
        scrollCollapsed,
    )
    val animatedTitleAlpha = scrollCollapsed

    Box(modifier = Modifier.fillMaxSize()) {
        // Backdrop layer (parallax + gradient scrim, matching online detail).
        BackdropLayer(
            backdropUrl = series?.backdropPath,
            blurHash = series?.blurHashBackdrop,
            height = backdropHeight,
            scrollTranslationY = -scrollOffset * 0.5f,
            scrollAlpha = 1f - (scrollFraction * 0.8f),
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .tvFocusRestorer()
                .focusRequester(listFocusRequester),
        ) {
            // Spacer so content overlaps the backdrop.
            item { Spacer(modifier = Modifier.height(baseBackdropHeight - 150.dp)) }

            if (series != null) {
                // Poster overlapping the backdrop.
                item(key = "poster") {
                    SeriesHeader(
                        series = series,
                        posterWidth = when {
                            isTv -> 160.dp
                            adaptiveInfo.windowSizeClass == WindowSizeClass.Expanded -> 140.dp
                            else -> 120.dp
                        },
                        contentPad = contentPad,
                    )
                }

                // Title + metadata block.
                item(key = "title") {
                    StaggeredSection(delayIndex = 0) {
                        Column(modifier = Modifier.padding(horizontal = contentPad)) {
                            Text(
                                text = series.name,
                                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(12.dp))
                            InfoRow(series = series)
                            if (totalSizeBytes > 0 || series.childCount > 0) {
                                Spacer(Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Tabler.Outline.DeviceFloppy, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    val parts = buildList {
                                        if (series.childCount > 0) add("${series.childCount} episodes")
                                        if (totalSizeBytes > 0) add(totalSizeBytes.formatBytes())
                                    }
                                    Text(parts.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            if (series.genres.isNotEmpty()) {
                                Spacer(Modifier.height(14.dp))
                                ChipRow(values = series.genres)
                            }
                            series.overview?.takeIf { it.isNotBlank() }?.let { overview ->
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    text = overview,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            // Season tabs (matching online SeasonsSection styling).
            if (seasons.size > 1) {
                item(key = "seasons") {
                    StaggeredSection(delayIndex = 1) {
                        Column {
                            Spacer(Modifier.height(20.dp))
                            Text(
                                text = "Seasons",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = contentPad),
                            )
                            Spacer(Modifier.height(16.dp))
                            TvFocusableItemRow(
                                items = seasons,
                                key = { it.id },
                                contentPadding = PaddingValues(horizontal = contentPad),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) { index, season, focusModifier ->
                                SeasonTab(
                                    name = season.name,
                                    selected = selectedSeasonIndex == index,
                                    onClick = { selectedSeasonIndex = index },
                                    modifier = focusModifier,
                                )
                            }
                        }
                    }
                }
            }

            item(key = "episodes_header") {
                StaggeredSection(delayIndex = 2) {
                    Text(
                        text = "Episodes",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = contentPad, vertical = 16.dp),
                    )
                }
            }

            if (seasonEpisodes.isEmpty() && selectedSeason != null) {
                item(key = "empty") {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        ScreenEmptyState(icon = Tabler.Outline.DeviceFloppy, title = "No episodes downloaded for this season")
                    }
                }
            } else {
                items(seasonEpisodes, key = { it.id }, contentType = { "episode" }) { episode ->
                    OfflineEpisodeRow(
                        episode = episode,
                        contentPad = contentPad,
                        onPlay = { onPlayOffline(episode.id) },
                        onDelete = { viewModel.deleteEpisode(episode.id) },
                    )
                }
                item { Spacer(Modifier.height(adaptiveInfo.bottomPadding(isTv))) }
            }
        }

        // Transparent overlay top bar: floats over the backdrop, container +
        // title fade in once the backdrop has scrolled away.
        TransparentTopBar(
            title = series?.name ?: "",
            onBack = onBack,
            containerColor = animatedContainerColor,
            titleAlpha = animatedTitleAlpha,
            scrollCollapsed = scrollCollapsed,
            actions = {
                val binFocusState = rememberTvFocusState()
                IconButton(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier
                        .then(binFocusState.focusModifier)
                        .tvFocusIndicator(binFocusState, CircleShape)
                        .padding(8.dp)
                        .clip(CircleShape)
                        .background(
                            color = if (scrollCollapsed < 0.5f) MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)
                            else Color.Transparent,
                        ),
                ) {
                    Icon(
                        Tabler.Outline.Trash,
                        contentDescription = "Delete downloads",
                        tint = if (scrollCollapsed < 0.5f) Color.White else MaterialTheme.colorScheme.onSurface,
                    )
                }
            },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = { Icon(Tabler.Outline.Trash, contentDescription = null) },
            title = { Text("Delete downloads") },
            text = {
                Text(
                    if (selectedSeason != null) "Choose what to delete for this series."
                    else "Delete all downloaded episodes for this series?",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        if (selectedSeason != null) {
                            viewModel.deleteSeason(selectedSeason.id)
                        } else {
                            viewModel.deleteSeries()
                            onBack()
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(if (selectedSeason != null) "This season" else "Delete series") }
            },
            dismissButton = {
                if (selectedSeason != null) {
                    TextButton(
                        onClick = {
                            showDeleteDialog = false
                            viewModel.deleteSeries()
                            onBack()
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text("Entire series") }
                } else {
                    TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
                }
            },
        )
    }
}

@Composable
private fun SeriesHeader(
    series: OfflineMediaItem,
    posterWidth: androidx.compose.ui.unit.Dp,
    contentPad: androidx.compose.ui.unit.Dp,
) {
    val posterHeight = posterWidth * 1.2f
    val overlap = 40.dp
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(posterHeight - overlap),
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = contentPad)
                .offset(y = -overlap),
            verticalAlignment = Alignment.Bottom,
        ) {
            if (!series.posterPath.isNullOrBlank()) {
                MediaImage(
                    url = series.posterPath!!,
                    contentDescription = series.name,
                    blurHash = series.blurHashPrimary,
                    modifier = Modifier
                        .width(posterWidth)
                        .requiredHeight(posterHeight)
                        .clip(ShapeCache.smooth8),
                    contentScale = ContentScale.Crop,
                )
            }
        }
    }
}

@Composable
private fun SeasonTab(
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val targetColor = if (selected) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
    val targetContentColor = if (selected) MaterialTheme.colorScheme.surface
    else MaterialTheme.colorScheme.onSurface
    val surfaceColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "offlineSeasonColor",
    )
    val contentColor by animateColorAsState(
        targetValue = targetContentColor,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "offlineSeasonContentColor",
    )
    val focusState = rememberTvFocusState(focusedScale = 1.05f)
    Surface(
        modifier = modifier
            .clip(ShapeCache.smooth16)
            .then(focusState.focusModifier)
            .tvFocusIndicator(focusState, ShapeCache.smooth16)
            .clickable(onClick = onClick),
        color = surfaceColor,
        contentColor = contentColor,
    ) {
        Text(
            text = name,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun BackdropLayer(
    backdropUrl: String?,
    blurHash: String?,
    height: androidx.compose.ui.unit.Dp,
    scrollTranslationY: Float = 0f,
    scrollAlpha: Float = 1f,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val baseBackdropHeight = height / 1.2f
    val surface = MaterialTheme.colorScheme.surface
    val startYPx = with(density) { (baseBackdropHeight - 200.dp).toPx() }
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .graphicsLayer {
                    translationY = scrollTranslationY
                    alpha = scrollAlpha
                },
        ) {
            if (!backdropUrl.isNullOrBlank()) {
                MediaImage(
                    url = backdropUrl,
                    contentDescription = null,
                    blurHash = blurHash,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawRect(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    surface.copy(alpha = 0.4f),
                                    surface.copy(alpha = 0.9f),
                                    surface,
                                ),
                                startY = startYPx,
                                endY = size.height,
                            ),
                        )
                    },
            )
        }
    }
}

@Composable
private fun InfoRow(series: OfflineMediaItem) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (series.isPlayed) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Tabler.Outline.Check,
                    contentDescription = "Watched",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "Watched",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        series.year?.let {
            Text(it.toString(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        }
        val rating = series.communityRating
        if (rating != null && rating > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Tabler.Outline.Star, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.width(4.dp))
                Text(String.format("%.1f", rating), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            }
        }
        series.officialRating?.takeIf { it.isNotBlank() }?.let { r ->
            Box(
                modifier = Modifier
                    .clip(ShapeCache.smooth4)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) { Text(r, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface) }
        }
    }
}

@Composable
private fun ChipRow(values: List<String>) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(values, key = { it }) { value ->
            Box(
                modifier = Modifier
                    .clip(ShapeCache.smooth16)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f))
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            ) {
                Text(value, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.95f))
            }
        }
    }
}

@Composable
private fun OfflineEpisodeRow(
    episode: OfflineMediaItem,
    contentPad: androidx.compose.ui.unit.Dp,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
) {
    val playFocusState = rememberTvFocusState()
    val deleteFocusState = rememberTvFocusState()
    val epLabel = buildString {
        episode.seasonNumber?.let { append("S$it") }
        episode.episodeNumber?.let { if (isNotEmpty()) append(":"); append("E$it") }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = contentPad)
            .clip(ShapeCache.smooth12)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val thumbModifier = Modifier
            .width(112.dp)
            .aspectRatio(16f / 9f)
            .clip(ShapeCache.smooth8)
        Box(modifier = thumbModifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
            val thumb = episode.backdropPath ?: episode.posterPath
            if (!thumb.isNullOrBlank()) {
                MediaImage(
                    url = thumb,
                    contentDescription = episode.name,
                    blurHash = episode.blurHashBackdrop ?: episode.blurHashPrimary,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            if (episode.downloadStatus == DownloadStatus.COMPLETED) {
                Icon(
                    Tabler.Outline.PlayerPlay,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.align(Alignment.Center).size(20.dp),
                )
            }
            if (episode.playedPercentage in 1.0..94.99) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth((episode.playedPercentage / 100f).toFloat())
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (epLabel.isNotEmpty()) {
                    Text(epLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 8.dp))
                }
                Text(
                    text = episode.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                val ticks = episode.runTimeTicks
                if (ticks != null && ticks > 0) {
                    val minutes = (ticks / 600_000_000).toInt()
                    if (minutes > 0) Text("${minutes}m", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val rating = episode.communityRating
                if (rating != null && rating > 0) {
                    if (ticks != null && ticks > 0) {
                        Text(" · ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    Icon(Tabler.Outline.Star, contentDescription = null, modifier = Modifier.size(10.dp), tint = MaterialTheme.colorScheme.tertiary)
                    Spacer(Modifier.width(1.dp))
                    Text(String.format("%.1f", rating), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (!episode.overview.isNullOrBlank()) {
                Text(
                    text = episode.overview!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (episode.downloadStatus == DownloadStatus.COMPLETED) {
                if (episode.isPlayed) {
                    WatchedChip()
                } else if (episode.playedPercentage in 1.0..94.99) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        JellyPlayLinearProgressIndicator(
                            progress = { (episode.playedPercentage / 100f).toFloat() },
                            modifier = Modifier.width(80.dp).height(4.dp).clip(ShapeCache.smooth4),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("${episode.playedPercentage.toInt()}% watched", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else if (episode.downloadStatus == DownloadStatus.DOWNLOADING) {
                val progress = if (episode.totalSizeBytes > 0) episode.downloadedBytes.toFloat() / episode.totalSizeBytes else 0f
                JellyPlayLinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(4.dp).clip(ShapeCache.smooth4),
                )
            }
        }

        if (episode.downloadStatus == DownloadStatus.COMPLETED) {
            IconButton(
                onClick = onPlay,
                modifier = Modifier.then(playFocusState.focusModifier).tvFocusIndicator(playFocusState, CircleShape),
            ) {
                Icon(Tabler.Outline.PlayerPlay, contentDescription = "Play", tint = MaterialTheme.colorScheme.primary)
            }
        }
        IconButton(
            onClick = onDelete,
            modifier = Modifier.then(deleteFocusState.focusModifier).tvFocusIndicator(deleteFocusState, CircleShape),
        ) {
            Icon(Tabler.Outline.Trash, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun WatchedChip() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 4.dp),
    ) {
        Icon(Tabler.Outline.Check, contentDescription = "Watched", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(4.dp))
        Text("Watched", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
    }
}
