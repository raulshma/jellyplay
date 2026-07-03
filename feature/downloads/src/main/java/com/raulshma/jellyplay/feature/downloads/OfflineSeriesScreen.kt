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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.raulshma.jellyplay.core.ui.components.JellyPlayLinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.ScreenEmptyState
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

@Composable
fun OfflineSeriesScreen(
    seriesId: String,
    onPlayOffline: (itemId: String) -> Unit,
    onBack: () -> Unit,
    viewModel: OfflineLibraryViewModel = hiltViewModel(),
) {
    val seriesItem by viewModel.seriesItem.collectAsStateWithLifecycle(initialValue = null)
    val seasons by viewModel.seasons.collectAsStateWithLifecycle(initialValue = emptyList())
    val episodes by viewModel.episodes.collectAsStateWithLifecycle(initialValue = emptyMap())
    val adaptiveInfo = LocalAdaptiveInfo.current
    val contentPad = adaptiveInfo.contentPadding(isTv = false)

    LaunchedEffect(seriesId) {
        viewModel.loadSeries(seriesId)
    }

    var selectedSeasonIndex by remember { mutableIntStateOf(0) }
    val selectedSeason = seasons.getOrNull(selectedSeasonIndex)
    val seasonEpisodes = selectedSeason?.let { episodes[it.id] } ?: emptyList()
    var showDeleteDialog by remember { mutableStateOf(false) }

    // TV focus-on-launch: focus the first episode once data arrives so D-pad input lands on
    // content, not the navigation drawer.
    val listFocusRequester = remember { FocusRequester() }
    TvGrabInitialFocus(
        focusRequester = listFocusRequester,
        itemCount = seasonEpisodes.size,
        tag = "offline_series_init",
    )

    JellyPlayScreenScaffold(
        title = seriesItem?.name ?: "Loading...",
        onBack = onBack,
        // Top bin button for bulk delete: replaces the two full-width
        // buttons that used to sit below the episode list.
        actions = {
            val binFocusState = rememberTvFocusState()
            IconButton(
                onClick = { showDeleteDialog = true },
                modifier = Modifier
                    .then(binFocusState.focusModifier)
                    .tvFocusIndicator(binFocusState, CircleShape),
            ) {
                Icon(
                    Tabler.Outline.Trash,
                    contentDescription = "Delete downloads",
                )
            }
        },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            seriesItem?.let { series ->
                Column(
                    modifier = Modifier.padding(horizontal = contentPad, vertical = 4.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (series.year != null) {
                            Text(
                                text = series.year.toString(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            )
                        }
                        if (series.communityRating != null && series.communityRating!! > 0) {
                            if (series.year != null) {
                                Text(
                                    text = " · ",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                )
                            }
                            Icon(
                                Tabler.Outline.Star,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.tertiary,
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            text = String.format("%.1f", series.communityRating),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        }
                        if (series.officialRating != null) {
                            Text(
                                text = " · ${series.officialRating}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (series.childCount > 0) {
                            Text(
                                text = " · ${series.childCount} episodes",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (series.genres.isNotEmpty()) {
                        Text(
                            text = series.genres.joinToString(", "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }

            seriesItem?.overview?.let { overview ->
                if (overview.isNotBlank()) {
                    Text(
                        text = overview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = contentPad, vertical = 8.dp),
                    )
                }
            }

            if (seasons.size > 1) {
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

            if (seasonEpisodes.isEmpty() && selectedSeason != null) {
                ScreenEmptyState(
                    icon = Tabler.Outline.Download,
                    title = "No episodes downloaded for this season",
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .tvFocusRestorer()
                        .focusRequester(listFocusRequester),
                    contentPadding = PaddingValues(
                        start = contentPad,
                        end = contentPad,
                        top = 8.dp,
                        bottom = adaptiveInfo.bottomPadding(isTv = false),
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(seasonEpisodes, key = { it.id }, contentType = { "episode" }) { episode ->
                        OfflineEpisodeRow(
                            episode = episode,
                            onPlay = {
                                onPlayOffline(episode.id)
                            },
                            onDelete = {
                                viewModel.deleteEpisode(episode.id)
                            },
                        )
                    }
                }
            }
        }
    }

    // Bulk-delete chooser. Offers season-vs-series deletion with
    // confirmation, reusing the existing deleteSeason/deleteSeries handlers.
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = { Icon(Tabler.Outline.Trash, contentDescription = null) },
            title = { Text("Delete downloads") },
            text = {
                Text(
                    if (selectedSeason != null)
                        "Choose what to delete for this series."
                    else
                        "Delete all downloaded episodes for this series?",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        if (selectedSeason != null) {
                            viewModel.deleteSeason(selectedSeason.id)
                        } else {
                            viewModel.deleteSeries(seriesId)
                        }
                    },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(if (selectedSeason != null) "This season" else "Delete series")
                }
            },
            dismissButton = {
                if (selectedSeason != null) {
                    TextButton(
                        onClick = {
                            showDeleteDialog = false
                            viewModel.deleteSeries(seriesId)
                        },
                        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) { Text("Entire series") }
                } else {
                    TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
                }
            },
        )
    }
}

@Composable
private fun OfflineEpisodeRow(
    episode: OfflineMediaItem,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
) {
    val playFocusState = rememberTvFocusState()
    val deleteFocusState = rememberTvFocusState()
    val epLabel = buildString {
        episode.seasonNumber?.let { append("S${it}") }
        episode.episodeNumber?.let {
            if (isNotEmpty()) append(":")
            append("E${it}")
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ShapeCache.smooth8)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (epLabel.isNotEmpty()) {
                    Text(
                        text = epLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 8.dp),
                    )
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

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp),
            ) {
                if (episode.runTimeTicks != null && episode.runTimeTicks!! > 0) {
                    val runtimeMinutes = (episode.runTimeTicks!! / 600_000_000).toInt()
                    if (runtimeMinutes > 0) {
                        Text(
                            text = "${runtimeMinutes}m",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (episode.communityRating != null && episode.communityRating!! > 0) {
                    if (episode.runTimeTicks != null && episode.runTimeTicks!! > 0) {
                        Text(
                            text = " · ",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                    Icon(
                        Tabler.Outline.Star,
                        contentDescription = null,
                        modifier = Modifier.size(10.dp),
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                    Spacer(Modifier.width(1.dp))
                    Text(
                        text = String.format("%.1f", episode.communityRating),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
                // Render watched state / resume progress for downloaded episodes
                //: a checkmark when fully watched, or a progress
                // bar + resume label when partially watched.
                if (episode.isPlayed) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        Icon(
                            Tabler.Outline.Check,
                            contentDescription = "Watched",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Watched",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                } else if (episode.playedPercentage in 0.01..94.99) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                    ) {
                        JellyPlayLinearProgressIndicator(
                            progress = { (episode.playedPercentage / 100f).toFloat() },
                            modifier = Modifier
                                .width(80.dp)
                                .height(4.dp)
                                .clip(ShapeCache.smooth4),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "${episode.playedPercentage.toInt()}% watched",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        Icon(
                            Tabler.Outline.Check,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Downloaded",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            } else if (episode.downloadStatus == DownloadStatus.DOWNLOADING) {
                val progress = if (episode.totalSizeBytes > 0) {
                    episode.downloadedBytes.toFloat() / episode.totalSizeBytes
                } else 0f
                @OptIn(ExperimentalMaterial3ExpressiveApi::class)
                JellyPlayLinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .height(4.dp)
                        .clip(ShapeCache.smooth4),
                )
            }
        }

        if (episode.downloadStatus == DownloadStatus.COMPLETED) {
            IconButton(
                onClick = onPlay,
                modifier = Modifier.then(playFocusState.focusModifier).tvFocusIndicator(playFocusState, CircleShape),
            ) {
                Icon(
                    Tabler.Outline.PlayerPlay,
                    contentDescription = "Play",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        } else if (episode.downloadStatus == DownloadStatus.DOWNLOADING) {
            @OptIn(ExperimentalMaterial3ExpressiveApi::class)
            LoadingIndicator(
                modifier = Modifier.size(24.dp),
            )
        }

        IconButton(
            onClick = onDelete,
            modifier = Modifier.then(deleteFocusState.focusModifier).tvFocusIndicator(deleteFocusState, CircleShape),
        ) {
            Icon(
                Tabler.Outline.Trash,
                contentDescription = "Delete",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
