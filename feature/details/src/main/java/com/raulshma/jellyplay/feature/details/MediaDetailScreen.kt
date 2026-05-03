package com.raulshma.jellyplay.feature.details

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import android.os.StatFs
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.raulshma.jellyplay.core.designsystem.theme.ArtworkThemeWrapper
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.PersonInfo
import com.raulshma.jellyplay.core.ui.components.PosterCard
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.ui.navigation.LocalSharedTransitionScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun MediaDetailScreen(
    itemId: String,
    onPlayClick: (itemId: String, mediaSourceId: String?, startPosition: Long) -> Unit,
    onAudioClick: (itemId: String) -> Unit,
    onItemClick: (itemId: String) -> Unit,
    onPersonClick: (personId: String) -> Unit,
    onBack: () -> Unit,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(itemId) {
        viewModel.loadItem(itemId)
    }

    val detail by viewModel.detail
    val isLoading by viewModel.isLoading
    val error by viewModel.error
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()

    val currentItem = detail?.item
    val backdropUrl = currentItem?.let { viewModel.getBackdropUrl(it.id) }

    ArtworkThemeWrapper(
        imageUrl = backdropUrl,
        dynamicTheming = preferences.dynamicTheming,
    ) {
        when {
            isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            error != null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(error ?: "Unknown error", color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(onClick = { viewModel.loadItem(itemId) }) {
                            Text("Retry")
                        }
                    }
                }
            }
            detail != null -> {
                DetailContent(
                    detail = detail!!,
                    seasons = viewModel.seasons,
                    episodes = viewModel.episodes,
                    getImageUrl = { viewModel.getImageUrl(it) },
                    getBackdropUrl = { viewModel.getBackdropUrl(it) },
                    isDownloading = viewModel.isDownloading,
                    downloadStarted = viewModel.downloadStarted,
                onPlayClick = { sourceId, start -> onPlayClick(itemId, sourceId, start) },
                onAudioClick = { onAudioClick(itemId) },
                onDownloadClick = { viewModel.startDownload() },
                onToggleFavorite = { viewModel.toggleFavorite() },
                onMarkPlayed = { viewModel.markPlayed() },
                onMarkUnplayed = { viewModel.markUnplayed() },
                onItemClick = onItemClick,
                onPersonClick = onPersonClick,
                onBack = onBack,
            )
        }
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailContent(
    detail: MediaDetail,
    seasons: List<MediaItem>,
    episodes: Map<String, List<MediaItem>>,
    getImageUrl: (String) -> String,
    getBackdropUrl: (String) -> String,
    isDownloading: Boolean,
    downloadStarted: Boolean,
    onPlayClick: (mediaSourceId: String?, startPosition: Long) -> Unit,
    onAudioClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onMarkPlayed: () -> Unit,
    onMarkUnplayed: () -> Unit,
    onItemClick: (String) -> Unit,
    onPersonClick: (String) -> Unit,
    onBack: () -> Unit,
) {
    val item = detail.item
    val scrollState = rememberScrollState()
    val isAudio = item.mediaType == MediaType.AUDIO || item.mediaType == MediaType.MUSIC
    var showDownloadDialog by remember { mutableStateOf(false) }
    val sharedTransitionScope = LocalSharedTransitionScope.current

    Box(modifier = Modifier.fillMaxSize()) {
        MediaImage(
            url = getBackdropUrl(item.id),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .then(
                    if (sharedTransitionScope != null) {
                        with(sharedTransitionScope) {
                            Modifier.sharedElementWithCallerManagedVisibility(
                                rememberSharedContentState(key = "backdrop_${item.id}"),
                                visible = true,
                            )
                        }
                    } else {
                        Modifier
                    }
                ),
            contentScale = ContentScale.Crop,
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                            MaterialTheme.colorScheme.surface,
                        )
                    )
                )
        )

        TopAppBar(
            title = {},
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            modifier = Modifier.statusBarsPadding(),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(top = 250.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item.year?.let {
                            Text(
                                text = it.toString(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        item.runTimeTicks?.let { ticks ->
                            val minutes = ticks / 600_000_000
                            Text(
                                text = "${minutes}min",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        item.communityRating?.let { rating ->
                            Text(
                                text = String.format("%.1f", rating),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        item.officialRating?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Column {
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            if (item.isPlayed) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        if (isAudio) {
                            onAudioClick()
                        } else {
                            val sourceId = detail.mediaSources.firstOrNull()?.id
                            val startPos = item.playbackPositionTicks ?: 0L
                            onPlayClick(sourceId, startPos)
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(
                        if (item.playbackPositionTicks != null && item.playbackPositionTicks!! > 0)
                            "Resume" else "Play"
                    )
                }
                OutlinedButton(
                    onClick = { if (item.isPlayed) onMarkUnplayed() else onMarkPlayed() },
                ) {
                    Icon(
                        if (item.isPlayed) Icons.Default.Check else Icons.Default.PlayArrow,
                        contentDescription = null,
                    )
                }
                if (!isAudio && detail.mediaSources.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { showDownloadDialog = true },
                        enabled = !isDownloading && !downloadStarted,
                    ) {
                        if (isDownloading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else if (downloadStarted) {
                            Icon(Icons.Default.Check, contentDescription = null)
                        } else {
                            Icon(Icons.Default.Download, contentDescription = null)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            item.genres.takeIf { it.isNotEmpty() }?.let { genres ->
                Text(
                    text = genres.joinToString(" \u2022 "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(12.dp))
            }

            item.overview?.let { overview ->
                Text(
                    text = "Overview",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = overview,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(16.dp))
            }

            if (item.mediaType == MediaType.SERIES && seasons.isNotEmpty()) {
                SeasonsSection(
                    seriesItem = item,
                    seasons = seasons,
                    episodes = episodes,
                    getImageUrl = getImageUrl,
                    onEpisodeClick = { episode ->
                        val sourceId = null
                        val startPos = episode.playbackPositionTicks ?: 0L
                        onPlayClick(sourceId, startPos)
                    },
                )
            }

            if (detail.people.isNotEmpty()) {
                Text(
                    text = "Cast & Crew",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(8.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(detail.people, key = { "person_${it.id}" }) { person ->
                        PersonItem(
                            person = person,
                            imageUrl = getImageUrl(person.id),
                            onClick = { onPersonClick(person.id) },
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            if (detail.relatedItems.isNotEmpty()) {
                Text(
                    text = "More Like This",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(8.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(detail.relatedItems, key = { "related_${it.id}" }) { related ->
                        PosterCard(
                            item = related,
                            imageUrl = getImageUrl(related.id),
                            onClick = { onItemClick(related.id) },
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    if (showDownloadDialog) {
        val source = detail.mediaSources.firstOrNull()
        val fileSize = source?.size
        val context = LocalContext.current
        val availableBytes = remember {
            val stat = StatFs(context.filesDir.absolutePath)
            stat.availableBlocksLong * stat.blockSizeLong
        }
        val fileSizeText = fileSize?.let { size ->
            when {
                size >= 1_000_000_000 -> "%.1f GB".format(size / 1_000_000_000.0)
                size >= 1_000_000 -> "%.1f MB".format(size / 1_000_000.0)
                size >= 1_000 -> "%.1f KB".format(size / 1_000.0)
                else -> "$size B"
            }
        } ?: "Unknown"
        val availableText = when {
            availableBytes >= 1_000_000_000 -> "%.1f GB".format(availableBytes / 1_000_000_000.0)
            availableBytes >= 1_000_000 -> "%.1f MB".format(availableBytes / 1_000_000.0)
            else -> "%.1f KB".format(availableBytes / 1_000.0)
        }
        val enoughSpace = fileSize == null || fileSize <= availableBytes

        AlertDialog(
            onDismissRequest = { showDownloadDialog = false },
            title = { Text("Download") },
            text = {
                Column {
                    Text("Estimated size: $fileSizeText")
                    Spacer(Modifier.height(8.dp))
                    Text("Available storage: $availableText")
                    if (!enoughSpace) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Not enough storage space available.",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDownloadDialog = false
                        onDownloadClick()
                    },
                    enabled = enoughSpace,
                ) {
                    Text("Download")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDownloadDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun SeasonsSection(
    seriesItem: MediaItem,
    seasons: List<MediaItem>,
    episodes: Map<String, List<MediaItem>>,
    getImageUrl: (String) -> String,
    onEpisodeClick: (MediaItem) -> Unit,
) {
    var selectedSeasonIndex by remember { mutableStateOf(0) }

    Text(
        text = "Seasons",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    Spacer(Modifier.height(8.dp))

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(seasons.indices.toList()) { index ->
            val season = seasons[index]
            val isSelected = index == selectedSeasonIndex
            androidx.compose.material3.FilterChip(
                selected = isSelected,
                onClick = { selectedSeasonIndex = index },
                label = {
                    Text(
                        season.name ?: "Season ${season.indexNumber ?: index + 1}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }

    Spacer(Modifier.height(12.dp))

    val selectedSeason = seasons.getOrNull(selectedSeasonIndex)
    val seasonEpisodes = selectedSeason?.let { episodes[it.id] } ?: emptyList()

    if (seasonEpisodes.isNotEmpty()) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            seasonEpisodes.forEach { episode ->
                EpisodeRow(
                    episode = episode,
                    getImageUrl = getImageUrl,
                    onClick = { onEpisodeClick(episode) },
                )
            }
        }
    } else if (selectedSeason != null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        }
    }

    Spacer(Modifier.height(16.dp))
}

@Composable
private fun EpisodeRow(
    episode: MediaItem,
    getImageUrl: (String) -> String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(120.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            MediaImage(
                url = getImageUrl(episode.id),
                contentDescription = episode.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            if (episode.playbackPositionTicks != null && episode.playbackPositionTicks!! > 0) {
                val progress = if (episode.runTimeTicks != null && episode.runTimeTicks!! > 0) {
                    (episode.playbackPositionTicks!!.toFloat() / episode.runTimeTicks!!).coerceIn(0f, 1f)
                } else 0f
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(progress)
                        .height(3.dp)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = buildString {
                    episode.indexNumber?.let { append("E$it. ") }
                    append(episode.name)
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            episode.overview?.let { overview ->
                Spacer(Modifier.height(2.dp))
                Text(
                    text = overview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (episode.runTimeTicks != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${episode.runTimeTicks!! / 600_000_000}min",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PersonItem(
    person: PersonInfo,
    imageUrl: String,
    onClick: () -> Unit = {},
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(80.dp)
            .clickable(onClick = onClick),
    ) {
        MediaImage(
            url = imageUrl,
            contentDescription = person.name,
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = person.name,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        person.role?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
