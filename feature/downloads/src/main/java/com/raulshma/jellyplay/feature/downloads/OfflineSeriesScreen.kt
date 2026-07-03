package com.raulshma.jellyplay.feature.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.JellyPlayLinearProgressIndicator
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.ScreenEmptyState
import com.raulshma.jellyplay.core.ui.components.ScreenLoadingState
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
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
    TvGrabInitialFocus(
        focusRequester = listFocusRequester,
        itemCount = seasonEpisodes.size,
        tag = "offline_series_init",
    )

    JellyPlayScreenScaffold(
        title = seriesItem?.name ?: "Loading...",
        onBack = onBack,
        actions = {
            val binFocusState = rememberTvFocusState()
            IconButton(
                onClick = { showDeleteDialog = true },
                modifier = Modifier
                    .then(binFocusState.focusModifier)
                    .tvFocusIndicator(binFocusState, CircleShape),
            ) {
                Icon(Tabler.Outline.Trash, contentDescription = "Delete downloads")
            }
        },
    ) {
        if (viewModel.isLoading && seriesItem == null) {
            ScreenLoadingState()
            return@JellyPlayScreenScaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .tvFocusRestorer(),
        ) {
            // Backdrop hero.
            item(key = "backdrop") {
                SeriesBackdrop(seriesItem = seriesItem)
            }

            // Header: info row + overview.
            seriesItem?.let { series ->
                item(key = "header") {
                    Column(modifier = Modifier.padding(horizontal = contentPad)) {
                        if (totalSizeBytes > 0 || series.childCount > 0) {
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
                        Spacer(Modifier.height(8.dp))
                        InfoRow(series = series)
                        if (series.genres.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = series.genres.joinToString(", "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        series.overview?.takeIf { it.isNotBlank() }?.let { overview ->
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = overview,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            // Season tabs.
            if (seasons.size > 1) {
                item(key = "tabs") {
                    TabRow(
                        selectedTabIndex = selectedSeasonIndex,
                        modifier = Modifier.padding(horizontal = contentPad),
                    ) {
                        seasons.forEachIndexed { index, season ->
                            Tab(
                                selected = selectedSeasonIndex == index,
                                onClick = { selectedSeasonIndex = index },
                                text = { Text(season.name) },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            // Episodes.
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
private fun SeriesBackdrop(seriesItem: OfflineMediaItem?) {
    val height = AdaptiveBackdropHeight.Portrait / 1.6f
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
    ) {
        val backdrop = seriesItem?.backdropPath
        if (!backdrop.isNullOrBlank()) {
            MediaImage(
                url = backdrop,
                contentDescription = seriesItem.name,
                blurHash = seriesItem.blurHashBackdrop,
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
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, MaterialTheme.colorScheme.surface),
                    ),
                ),
        )
    }
}

@Composable
private fun InfoRow(series: OfflineMediaItem) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        series.year?.let {
            Text(it.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        }
        val rating = series.communityRating
        if (rating != null && rating > 0) {
            if (series.year != null) Text(" · ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outlineVariant)
            Icon(Tabler.Outline.Star, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.tertiary)
            Spacer(Modifier.width(2.dp))
            Text(String.format("%.1f", rating), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        series.officialRating?.let {
            Text(" · $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        // Thumbnail.
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
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(20.dp),
                )
            }
            // Resume progress bar.
            if (episode.playedPercentage in 1.0..94.99) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                        .fillMaxWidth((episode.playedPercentage / 100f).toFloat()),
                )
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
            // Runtime · rating.
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
            // Watched / resume / downloading state.
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

        // Actions.
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
