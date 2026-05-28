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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.*
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

@OptIn(ExperimentalMaterial3Api::class)
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
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val adaptiveInfo = LocalAdaptiveInfo.current
    val contentPad = adaptiveInfo.contentPadding(isTv = false)

    LaunchedEffect(seriesId) {
        viewModel.loadSeries(seriesId)
    }

    var selectedSeasonIndex by remember { mutableIntStateOf(0) }
    val selectedSeason = seasons.getOrNull(selectedSeasonIndex)
    val seasonEpisodes = selectedSeason?.let { episodes[it.id] } ?: emptyList()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection)) {
            TopAppBar(
                title = {
                    Text(
                        text = seriesItem?.name ?: "Loading...",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Tabler.Outline.ArrowLeft, contentDescription = "Back")
                    }
                },
                scrollBehavior = scrollBehavior,
            )

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
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
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
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            )
                        }
                        if (series.officialRating != null) {
                            Text(
                                text = " · ${series.officialRating}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            )
                        }
                        if (series.childCount > 0) {
                            Text(
                                text = " · ${series.childCount} episodes",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            )
                        }
                    }
                    if (series.genres.isNotEmpty()) {
                        Text(
                            text = series.genres.joinToString(", "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
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
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No episodes downloaded for this season",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = contentPad,
                        end = contentPad,
                        top = 8.dp,
                        bottom = 100.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(seasonEpisodes, key = { it.id }) { episode ->
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

                    item {
                        Spacer(Modifier.height(8.dp))
                        if (selectedSeason != null) {
                            OutlinedButton(
                                onClick = { viewModel.deleteSeason(selectedSeason.id) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(
                                    Tabler.Outline.Trash,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Delete Season")
                            }
                        }
                    }

                    item {
                        Spacer(Modifier.height(4.dp))
                        OutlinedButton(
                            onClick = { viewModel.deleteSeries(seriesId) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                Tabler.Outline.Trash,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Delete Entire Series")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OfflineEpisodeRow(
    episode: OfflineMediaItem,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
) {
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
            .clip(RoundedCornerShape(8.dp))
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
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        )
                    }
                }
                if (episode.communityRating != null && episode.communityRating!! > 0) {
                    if (episode.runTimeTicks != null && episode.runTimeTicks!! > 0) {
                        Text(
                            text = " · ",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
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
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }
            }

            if (!episode.overview.isNullOrBlank()) {
                Text(
                    text = episode.overview!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            if (episode.downloadStatus == DownloadStatus.COMPLETED) {
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
            } else if (episode.downloadStatus == DownloadStatus.DOWNLOADING) {
                val progress = if (episode.totalSizeBytes > 0) {
                    episode.downloadedBytes.toFloat() / episode.totalSizeBytes
                } else 0f
                @OptIn(ExperimentalMaterial3ExpressiveApi::class)
                LinearWavyProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                )
            }
        }

        if (episode.downloadStatus == DownloadStatus.COMPLETED) {
            IconButton(onClick = onPlay) {
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

        IconButton(onClick = onDelete) {
            Icon(
                Tabler.Outline.Trash,
                contentDescription = "Delete",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
