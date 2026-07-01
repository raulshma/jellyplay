package com.raulshma.jellyplay.floating

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.PlayerPause
import com.composables.icons.tabler.outline.PlayerPlay
import com.composables.icons.tabler.outline.PlayerTrackNext
import com.composables.icons.tabler.outline.PlayerTrackPrev
import com.composables.icons.tabler.outline.X
import com.raulshma.jellyplay.core.ui.components.focusIndicator

/**
 * The compact floating media controller overlay rendered inside the
 * [FloatingPlayerService]'s ComposeView.
 *
 * Shows artwork (if available), title/subtitle, and play/pause + skip controls.
 * The close button dismisses the overlay.
 *
 * The overlay is intentionally compact (320dp x 72dp) to minimize screen
 * obstruction while providing essential controls.
 */
@Composable
fun FloatingPlayerOverlay(
    state: FloatingPlayerState,
    onClose: () -> Unit,
) {
    val title by state.title.collectAsState()
    val subtitle by state.subtitle.collectAsState()
    val isPlaying by state.isPlaying.collectAsState()
    val artworkUrl by state.artworkUrl.collectAsState()

    Surface(
        modifier = Modifier
            .width(320.dp)
            .height(72.dp)
            .clip(ShapeCache.smooth16),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 8.dp,
        tonalElevation = 3.dp,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(ShapeCache.smooth10)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                if (artworkUrl != null) {
                    AsyncImage(
                        model = artworkUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            title.firstOrNull()?.toString() ?: "",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(
                    text = title.ifBlank { "Not Playing" },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                ControlIcon(
                    icon = Tabler.Outline.PlayerTrackPrev,
                    contentDescription = "Rewind 10 seconds",
                    onClick = { state.seekBy(deltaMs = -10_000L) },
                )
                ControlIcon(
                    icon = if (isPlaying) Tabler.Outline.PlayerPause else Tabler.Outline.PlayerPlay,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    onClick = { state.togglePlayPause() },
                    tint = MaterialTheme.colorScheme.primary,
                    iconSize = 32.dp,
                )
                ControlIcon(
                    icon = Tabler.Outline.PlayerTrackNext,
                    contentDescription = "Forward 10 seconds",
                    onClick = { state.seekBy(deltaMs = 10_000L) },
                )
                ControlIcon(
                    icon = Tabler.Outline.X,
                    contentDescription = "Close floating player",
                    onClick = onClose,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ControlIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    iconSize: androidx.compose.ui.unit.Dp = 24.dp,
) {
    Box(
        modifier = Modifier
            .size(iconSize + 8.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .focusIndicator(androidx.compose.foundation.shape.CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
    }
}
