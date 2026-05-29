package com.raulshma.jellyplay.feature.music.albumdetail

import androidx.compose.foundation.background
import com.raulshma.jellyplay.core.ui.tv.tvFocusable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.components.CircleBgBackButton
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.ScreenLoadingState
import com.raulshma.jellyplay.core.ui.components.AnimatedEntrance
import com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColor
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

@Composable
fun AlbumDetailScreen(
    albumId: String,
    onTrackClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: AlbumDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(albumId) {
        viewModel.loadAlbum(albumId)
    }

    when {
        viewModel.isLoading -> {
            ScreenLoadingState()
        }
        viewModel.error != null -> {
            ErrorScreen(
                message = viewModel.error!!,
                onRetry = { viewModel.loadAlbum(albumId) },
            )
        }
        viewModel.detail != null -> {
            AnimatedEntrance(visible = true) {
                AlbumDetailContent(
                    detail = viewModel.detail!!,
                    tracks = viewModel.tracks,
                    getImageUrl = { viewModel.getImageUrl(it) },
                    getBackdropUrl = { viewModel.getBackdropUrl(it) },
                    onTrackClick = onTrackClick,
                    onPlayAlbum = { tracks, startIndex ->
                        viewModel.playAlbum(tracks, startIndex)
                        tracks.getOrNull(startIndex)?.let { firstTrack ->
                            onTrackClick(firstTrack.id)
                        }
                    },
                    onAddToQueue = { track -> viewModel.addToQueue(track) },
                    onArtistClick = onArtistClick,
                    onBack = onBack,
                )
            }
        }
    }
}

@Composable
private fun AlbumDetailContent(
    detail: MediaDetail,
    tracks: List<MediaItem>,
    getImageUrl: (String) -> String,
    getBackdropUrl: (String) -> String,
    onTrackClick: (String) -> Unit,
    onPlayAlbum: (List<MediaItem>, Int) -> Unit,
    onAddToQueue: (MediaItem) -> Unit,
    onArtistClick: (String) -> Unit,
    onBack: () -> Unit,
) {
    val item = detail.item

    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    val contentPad = adaptiveInfo.contentPadding(isTv)
    val backgroundColor = rememberScreenBackgroundColor()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        MediaImage(
            url = getBackdropUrl(item.id),
            contentDescription = null,
            blurHash = item.blurHashes.backdrop,
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
            contentScale = ContentScale.Crop,
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.3f),
                            Color.Transparent,
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                            MaterialTheme.colorScheme.surface,
                        )
                    )
                )
        )

        CircleBgBackButton(
            onClick = onBack,
            modifier = Modifier.statusBarsPadding(),
            iconColor = Color.White,
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 250.dp),
            contentPadding = WindowInsets.navigationBars.asPaddingValues(),
        ) {
            item {
                Column(modifier = Modifier.padding(horizontal = contentPad)) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        item.albumArtist?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.tvFocusable().clickable {
                                },
                            )
                        }
                        item.year?.let {
                            Text(
                                text = it.toString(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (tracks.isNotEmpty()) {
                                    onPlayAlbum(tracks, 0)
                                }
                            },
                            enabled = tracks.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Tabler.Outline.PlayerPlay, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text("Play All")
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    item.overview?.let { overview ->
                        Text(
                            text = overview,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(16.dp))
                    }

                    Text(
                        text = "Tracks",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }

            itemsIndexed(tracks, key = { _, track -> track.id }, contentType = { _, _ -> "mediaItem" }) { index, track ->
                TrackItem(
                    track = track,
                    index = index + 1,
                    imageUrl = getImageUrl(track.id),
                    onClick = { onTrackClick(track.id) },
                    onAddToQueue = { onAddToQueue(track) },
                )
            }

            item {
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun TrackItem(
    track: MediaItem,
    index: Int,
    imageUrl: String,
    onClick: () -> Unit,
    onAddToQueue: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tvFocusable().clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$index",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(24.dp),
        )

        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(ShapeCache.smooth4),
        ) {
            MediaImage(
                url = imageUrl,
                contentDescription = track.name,
                blurHash = track.blurHashes.primary,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            track.albumArtist?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (onAddToQueue != null) {
            androidx.compose.material3.IconButton(onClick = { onAddToQueue() }) {
                Icon(
                    Tabler.Outline.Playlist,
                    contentDescription = "Add to Queue",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        track.runTimeTicks?.let { ticks ->
            val minutes = (ticks / 600_000_000)
            val seconds = ((ticks / 10_000_000) % 60)
            Text(
                text = String.format("%d:%02d", minutes, seconds),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
