package com.raulshma.jellyplay.feature.music.moodplaylist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.raulshma.jellyplay.core.model.MoodPlaylist
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.tv.isTvDevice
import com.raulshma.jellyplay.core.ui.components.AnimatedEntrance
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.feature.music.components.TrackRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoodPlaylistDetailScreen(
    playlistId: String,
    onTrackClick: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: MoodPlaylistsViewModel = hiltViewModel(),
) {
    val playlist = viewModel.playlists.find { it.id == playlistId }

    LaunchedEffect(playlistId) {
        if (playlist != null && viewModel.selectedPlaylist?.id != playlistId) {
            viewModel.generatePlaylist(playlist)
        }
    }

    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = isTvDevice()
    val contentPad = adaptiveInfo.contentPadding(isTv)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(playlist?.emoji ?: "")
                        Spacer(Modifier.width(8.dp))
                        Text(playlist?.name ?: "Mood Playlist")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            val firstTrack = viewModel.generatedItems.firstOrNull()
            if (firstTrack != null) {
                FloatingActionButton(
                    onClick = { onTrackClick(firstTrack.id) },
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play All")
                }
            }
        },
    ) { padding ->
        when {
            viewModel.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            viewModel.error != null -> {
                ErrorScreen(
                    message = viewModel.error!!,
                    onRetry = {
                        if (playlist != null) viewModel.generatePlaylist(playlist)
                    },
                    modifier = Modifier.padding(padding),
                )
            }
            viewModel.generatedItems.isEmpty() && !viewModel.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "No tracks match this mood",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> AnimatedEntrance(visible = true) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = padding.calculateTopPadding() + 8.dp,
                        bottom = padding.calculateBottomPadding() + adaptiveInfo.bottomPadding(isTv),
                        start = contentPad,
                        end = contentPad,
                    ),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(viewModel.generatedItems.size, key = { viewModel.generatedItems[it].id }, contentType = { "mediaItem" }) { index ->
                        val track = viewModel.generatedItems[index]
                        TrackRow(
                            name = track.name,
                            artist = track.albumArtist,
                            album = track.album,
                            duration = track.runTimeTicks?.let { ticks ->
                                val totalSeconds = ticks / 10_000_000
                                val minutes = (totalSeconds % 3600) / 60
                                val seconds = totalSeconds % 60
                                String.format("%d:%02d", minutes, seconds)
                            },
                            imageUrl = viewModel.getImageUrl(track.id),
                            onClick = { onTrackClick(track.id) },
                            blurHash = track.blurHashes.primary,
                        )
                    }
                }
            }
        }
    }
}
