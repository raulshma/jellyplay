package com.raulshma.jellyplay.feature.music.albumdetail

import androidx.compose.foundation.background
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.foundation.shape.CircleShape
import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.enableMarqueeOnFocus
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.raulshma.jellyplay.core.ui.components.CircleBgBackButton
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.ScreenLoadingState
import com.raulshma.jellyplay.core.ui.components.AnimatedEntrance
import com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColor
import com.raulshma.jellyplay.core.ui.components.JellyPlayCircularProgressIndicator
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

@OptIn(ExperimentalMaterial3Api::class)
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

    LaunchedEffect(viewModel.mixFirstTrackId) {
        viewModel.mixFirstTrackId?.let {
            viewModel.consumeMixEvent()
            onTrackClick(it)
        }
    }

    val trackDownloads by viewModel.trackDownloads.collectAsStateWithLifecycle()
    // TV focus-on-launch: focus the first track once content arrives so D-pad input lands on
    // content, not the navigation drawer.
    val listFocusRequester = remember { FocusRequester() }
    TvGrabInitialFocus(
        focusRequester = listFocusRequester,
        itemCount = if (viewModel.isLoading || viewModel.error != null || viewModel.detail == null) 0 else viewModel.tracks.size.coerceAtLeast(1),
        tag = "album_detail_init",
    )

    PullToRefreshBox(
        isRefreshing = viewModel.isLoading && viewModel.detail != null,
        onRefresh = {
            viewModel.refreshAlbum(albumId)
        },
    ) {
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
                    trackDownloads = trackDownloads,
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
                    onInstantMix = { viewModel.startInstantMix(albumId) },
                    isStartingMix = viewModel.isStartingMix,
                    onDownloadTrack = { track -> viewModel.downloadTrack(track) },
                    onDownloadAlbum = { viewModel.downloadAlbum() },
                    onDeleteAlbum = { viewModel.deleteAlbumDownloads() },
                    onArtistClick = onArtistClick,
                    onBack = onBack,
                    listFocusRequester = listFocusRequester,
                )
            }
        }
    }
    }
}

@Composable
private fun AlbumDetailContent(
    detail: MediaDetail,
    tracks: List<MediaItem>,
    trackDownloads: Map<String, DownloadItem>,
    getImageUrl: (String) -> String,
    getBackdropUrl: (String) -> String,
    onTrackClick: (String) -> Unit,
    onPlayAlbum: (List<MediaItem>, Int) -> Unit,
    onAddToQueue: (MediaItem) -> Unit,
    onInstantMix: () -> Unit,
    isStartingMix: Boolean,
    onDownloadTrack: (MediaItem) -> Unit,
    onDownloadAlbum: () -> Unit,
    onDeleteAlbum: () -> Unit,
    onArtistClick: (String) -> Unit,
    onBack: () -> Unit,
    listFocusRequester: FocusRequester,
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
                .padding(top = 250.dp)
                .tvFocusRestorer()
                .focusRequester(listFocusRequester),
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
                        item.albumArtist?.let { artistName ->
                            val artistId = item.artistItems.firstOrNull()?.id
                            Text(
                                text = artistName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable {
                                    if (artistId != null) onArtistClick(artistId)
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

                    val playAllFocusState = rememberTvFocusState(focusedScale = 1.04f)
                    val instantMixFocusState = rememberTvFocusState(focusedScale = 1.1f)
                    val downloadAlbumFocusState = rememberTvFocusState(focusedScale = 1.1f)

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                if (tracks.isNotEmpty()) {
                                    onPlayAlbum(tracks, 0)
                                }
                            },
                            enabled = tracks.isNotEmpty(),
                            modifier = Modifier
                                .weight(1f)
                                .then(playAllFocusState.focusModifier)
                                .tvFocusIndicator(playAllFocusState, ShapeCache.smooth12),
                        ) {
                            Icon(Tabler.Outline.PlayerPlay, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text("Play All")
                        }

                        androidx.compose.material3.IconButton(
                            onClick = onInstantMix,
                            enabled = !isStartingMix,
                            modifier = Modifier
                                .then(instantMixFocusState.focusModifier)
                                .tvFocusIndicator(instantMixFocusState, CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                .size(40.dp)
                        ) {
                            if (isStartingMix) {
                                JellyPlayCircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(
                                    imageVector = Tabler.Outline.Sparkles,
                                    contentDescription = "Instant Mix",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        val allDownloaded = remember(tracks, trackDownloads) {
                            tracks.isNotEmpty() && tracks.all { trackDownloads[it.id]?.status == DownloadStatus.COMPLETED }
                        }
                        val anyDownloading = remember(tracks, trackDownloads) {
                            tracks.any { trackDownloads[it.id]?.status == DownloadStatus.DOWNLOADING || trackDownloads[it.id]?.status == DownloadStatus.PENDING }
                        }

                        androidx.compose.material3.IconButton(
                            onClick = {
                                if (allDownloaded) {
                                    onDeleteAlbum()
                                } else {
                                    onDownloadAlbum()
                                }
                            },
                            modifier = Modifier
                                .then(downloadAlbumFocusState.focusModifier)
                                .tvFocusIndicator(downloadAlbumFocusState, CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                .size(40.dp)
                        ) {
                            if (anyDownloading) {
                                JellyPlayCircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(
                                    imageVector = if (allDownloaded) Tabler.Outline.Check else Tabler.Outline.Download,
                                    contentDescription = "Download Album",
                                    tint = if (allDownloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
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
                    downloadItem = trackDownloads[track.id],
                    onClick = { onTrackClick(track.id) },
                    onAddToQueue = { onAddToQueue(track) },
                    onDownloadClick = { onDownloadTrack(track) },
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
    downloadItem: DownloadItem?,
    onClick: () -> Unit,
    onAddToQueue: (() -> Unit)? = null,
    onDownloadClick: (() -> Unit)? = null,
) {
    val tvFocusState = rememberTvFocusState()
    val downloadFocusState = rememberTvFocusState()
    val addToQueueFocusState = rememberTvFocusState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(tvFocusState.focusModifier)
            .tvFocusIndicator(tvFocusState, ShapeCache.smooth8)
            .clickable(onClick = onClick)
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
                modifier = Modifier.enableMarqueeOnFocus(focused = tvFocusState.isFocused),
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

        if (onDownloadClick != null) {
            androidx.compose.material3.IconButton(
                onClick = onDownloadClick,
                modifier = Modifier.then(downloadFocusState.focusModifier).tvFocusIndicator(downloadFocusState, CircleShape),
            ) {
                if (downloadItem?.status == DownloadStatus.DOWNLOADING || downloadItem?.status == DownloadStatus.PENDING) {
                    val progressVal = if (downloadItem.totalSizeBytes > 0) {
                        downloadItem.downloadedBytes.toFloat() / downloadItem.totalSizeBytes
                    } else 0f
                    if (progressVal > 0f) {
                        JellyPlayCircularProgressIndicator(
                            progress = { progressVal },
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        JellyPlayCircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    Icon(
                        imageVector = if (downloadItem?.status == DownloadStatus.COMPLETED) Tabler.Outline.Check else Tabler.Outline.Download,
                        contentDescription = "Download Track",
                        tint = if (downloadItem?.status == DownloadStatus.COMPLETED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        if (onAddToQueue != null) {
            androidx.compose.material3.IconButton(
                onClick = { onAddToQueue() },
                modifier = Modifier.then(addToQueueFocusState.focusModifier).tvFocusIndicator(addToQueueFocusState, CircleShape),
            ) {
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
