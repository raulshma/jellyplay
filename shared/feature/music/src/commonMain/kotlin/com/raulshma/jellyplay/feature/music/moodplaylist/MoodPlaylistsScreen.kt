package com.raulshma.jellyplay.feature.music.moodplaylist

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.model.MoodPlaylist
import com.raulshma.jellyplay.core.model.MoodPlaylistsPreset
import com.raulshma.jellyplay.core.ui.components.HeaderStatusIndicator
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.LocalNetworkStatus
import com.raulshma.jellyplay.core.ui.components.resolveHeaderStatus
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.*
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.TvFocusableGrid
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.smoothCornerShape
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.ui.components.rememberJellyFocusableInteraction
import com.raulshma.jellyplay.feature.music.generated.resources.Res
import com.raulshma.jellyplay.feature.music.generated.resources.music_mood_playlists
import com.raulshma.jellyplay.core.ui.components.jellyFocusIndicator

@Composable
fun MoodPlaylistsScreen(
    onPlaylistClick: (MoodPlaylist) -> Unit,
    onBack: () -> Unit,
    viewModel: MoodPlaylistsViewModel = koinViewModel(),
) {
    val networkStatus by LocalNetworkStatus.current.collectAsStateWithLifecycle()
    val headerStatus = resolveHeaderStatus(
        isLoading = viewModel.isLoading,
        hasError = false,
        networkStatus = networkStatus,
    )

    JellyPlayScreenScaffold(
        title = stringResource(Res.string.music_mood_playlists),
        onBack = onBack,
        actions = {
            HeaderStatusIndicator(
                status = headerStatus,
                modifier = Modifier.padding(end = 8.dp),
            )
        },
    ) { innerPadding ->
        Spacer(Modifier.height(8.dp))

        val playlists = viewModel.playlists
        val adaptiveInfo = LocalAdaptiveInfo.current
        val isTv = LocalTvMode.current
        TvFocusableGrid(
            itemCount = playlists.size,
            key = { playlists[it].id },
            columns = GridCells.Adaptive(minSize = adaptiveInfo.gridMinSize(isTv)),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = adaptiveInfo.contentPadding(isTv),
                end = adaptiveInfo.contentPadding(isTv),
                top = 8.dp,
                bottom = adaptiveInfo.bottomPadding(isTv) + innerPadding.calculateBottomPadding(),
            ),
            horizontalArrangement = Arrangement.spacedBy(adaptiveInfo.itemSpacing(isTv)),
            verticalArrangement = Arrangement.spacedBy(adaptiveInfo.itemSpacing(isTv)),
            contentType = { "moodPlaylist" },
        ) { index, itemModifier ->
            val playlist = playlists[index]
            MoodCard(
                playlist = playlist,
                onClick = { onPlaylistClick(playlist) },
                modifier = itemModifier,
            )
        }
    }
}

@Composable
fun getMoodIcon(id: String): ImageVector {
    return when (id) {
        "happy" -> Tabler.Outline.MoodSmile
        "chill" -> Tabler.Outline.Sunset
        "energetic" -> Tabler.Outline.Bolt
        "focus" -> Tabler.Outline.Brain
        "workout" -> Tabler.Outline.Flame
        "sad" -> Tabler.Outline.MoodSad
        "romantic" -> Tabler.Outline.Heart
        "party" -> Tabler.Outline.Confetti
        "sleep" -> Tabler.Outline.Moon
        "driving" -> Tabler.Outline.Car
        else -> Tabler.Outline.Music
    }
}

@Composable
private fun MoodCard(
    playlist: MoodPlaylist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = playlist.themeColorHex?.let { hex ->
        parseMoodThemeColor(hex)
    } ?: MaterialTheme.colorScheme.primaryContainer

    val contentColor = if (backgroundColor != MaterialTheme.colorScheme.primaryContainer) {
        val luminance = backgroundColor.red * 0.299f + backgroundColor.green * 0.587f + backgroundColor.blue * 0.114f
        if (luminance > 0.5f) Color.Black else Color.White
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }

    val expressiveShape = remember {
        smoothCornerShape(
            cornerRadiusTL = 28.dp,
            cornerRadiusTR = 8.dp,
            cornerRadiusBL = 8.dp,
            cornerRadiusBR = 28.dp,
            smoothnessAsPercent = 60,
        )
    }

    val interaction = rememberJellyFocusableInteraction(focusedScale = 1.05f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .then(interaction.modifier)
            .graphicsLayer {
                scaleX = interaction.scale
                scaleY = interaction.scale
            }
            .jellyFocusIndicator(interaction, expressiveShape)
            .clip(expressiveShape)
            .background(backgroundColor)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Icon(
                imageVector = getMoodIcon(playlist.id),
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = contentColor,
            )
            Column {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    color = contentColor,
                )
                Text(
                    text = playlist.description,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    modifier = Modifier.padding(top = 4.dp),
                    color = contentColor.copy(alpha = 0.8f),
                )
            }
        }
    }
}
