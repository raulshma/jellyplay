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
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.raulshma.jellyplay.core.model.MoodPlaylist
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.components.AnimatedEntrance
import com.raulshma.jellyplay.core.ui.components.TooltipIconButton
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.feature.music.components.TrackRow
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
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
    val isTv = LocalTvMode.current
    val contentPad = adaptiveInfo.contentPadding(isTv)

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
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
                    TooltipIconButton(
                        onClick = onBack,
                        imageVector = Tabler.Outline.ArrowLeft,
                        contentDescription = "Back",
                        tooltipText = "Back",
                    )
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            val firstTrack = viewModel.generatedItems.firstOrNull()
            if (firstTrack != null) {
                ExtendedFloatingActionButton(
                    onClick = { onTrackClick(firstTrack.id) },
                    icon = { Icon(Tabler.Outline.PlayerPlay, contentDescription = null) },
                    text = { Text("Play All") },
                )
            }
        },
    ) { padding ->
        when {
            viewModel.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    ContainedLoadingIndicator()
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
