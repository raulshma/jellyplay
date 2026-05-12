package com.raulshma.jellyplay.feature.music.moodplaylist

import androidx.compose.animation.AnimatedVisibility
import com.raulshma.jellyplay.core.ui.tv.tvFocusable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.model.MoodPlaylist
import com.raulshma.jellyplay.core.model.MoodPlaylistsPreset
import com.raulshma.jellyplay.core.ui.components.resolveHeaderStatus
import com.raulshma.jellyplay.core.ui.components.LocalNetworkStatus
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.*
import com.raulshma.jellyplay.core.ui.tv.isTvDevice

@Composable
fun MoodPlaylistsScreen(
    onPlaylistClick: (MoodPlaylist) -> Unit,
    onBack: () -> Unit,
    viewModel: MoodPlaylistsViewModel = hiltViewModel(),
) {
    val networkStatus by LocalNetworkStatus.current.collectAsStateWithLifecycle()
    val headerStatus = resolveHeaderStatus(
        isLoading = viewModel.isLoading,
        hasError = false,
        networkStatus = networkStatus,
    )

    var headerVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { headerVisible = true }

    val backgroundColor = Color.Black.copy(alpha = 0.95f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = headerVisible,
                enter = fadeIn(tween(500)) + slideInVertically(
                    tween(500),
                    initialOffsetY = { -40 },
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Text(
                        text = "Mood Playlists",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                        color = Color.White,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                    com.raulshma.jellyplay.core.ui.components.HeaderStatusIndicator(
                        status = headerStatus,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            val adaptiveInfo = LocalAdaptiveInfo.current
            val isTv = isTvDevice()
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = adaptiveInfo.gridMinSize(isTv)),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = adaptiveInfo.contentPadding(isTv),
                    end = adaptiveInfo.contentPadding(isTv),
                    top = 8.dp,
                    bottom = adaptiveInfo.bottomPadding(isTv),
                ),
                horizontalArrangement = Arrangement.spacedBy(adaptiveInfo.itemSpacing(isTv)),
                verticalArrangement = Arrangement.spacedBy(adaptiveInfo.itemSpacing(isTv)),
            ) {
                items(MoodPlaylistsPreset.all.size, key = { MoodPlaylistsPreset.all[it].id }, contentType = { "moodPlaylist" }) { index ->
                    val playlist = MoodPlaylistsPreset.all[index]
                    MoodCard(
                        playlist = playlist,
                        onClick = { onPlaylistClick(playlist) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MoodCard(
    playlist: MoodPlaylist,
    onClick: () -> Unit,
) {
    val backgroundColor = playlist.themeColorHex?.let { hex ->
        try {
            Color(android.graphics.Color.parseColor(hex))
        } catch (_: IllegalArgumentException) {
            null
        }
    } ?: MaterialTheme.colorScheme.primaryContainer

    val contentColor = if (backgroundColor != MaterialTheme.colorScheme.primaryContainer) {
        val luminance = backgroundColor.red * 0.299f + backgroundColor.green * 0.587f + backgroundColor.blue * 0.114f
        if (luminance > 0.5f) Color.Black else Color.White
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .tvFocusable().clickable(onClick = onClick)
            .clip(RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor,
            contentColor = contentColor,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = playlist.emoji,
                style = MaterialTheme.typography.headlineLarge,
            )
            Column {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(
                    text = playlist.description,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
