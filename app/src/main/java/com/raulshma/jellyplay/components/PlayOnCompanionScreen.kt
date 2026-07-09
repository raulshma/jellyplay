package com.raulshma.jellyplay.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.ArrowLeft
import com.composables.icons.tabler.outline.Cast
import com.composables.icons.tabler.outline.Disc
import com.composables.icons.tabler.outline.PlayerPause
import com.composables.icons.tabler.outline.PlayerPlay
import com.composables.icons.tabler.outline.PlayerSkipBack
import com.composables.icons.tabler.outline.PlayerSkipForward
import com.composables.icons.tabler.outline.PlayerTrackNext
import com.composables.icons.tabler.outline.PlayerTrackPrev
import com.composables.icons.tabler.outline.Power
import com.composables.icons.tabler.outline.Volume
import com.composables.icons.tabler.outline.X
import com.raulshma.jellyplay.PlayOnViewModel
import com.raulshma.jellyplay.core.designsystem.theme.CastColors
import com.raulshma.jellyplay.core.designsystem.theme.rememberArtworkColors
import com.raulshma.jellyplay.core.ui.components.formatDurationMs

/**
 * Full-screen remote-control companion for an active "Play On" (Jellyfin remote
 * session) cast. Reached by tapping the persistent [PlayOnMiniBar].
 *
 * Visually mirrors the video-cast [CompanionDashboard] (artwork-tinted gradient
 * backdrop, large transport cluster) but drives the deliberately-isolated Play
 * On path — [PlayOnViewModel] / JellyfinRemotePlayCastStrategy — rather than the
 * shared [com.raulshma.jellyplay.core.data.cast.CastManager] the video player
 * reads. Reusing CompanionDashboard directly is unsafe: it is hard-coupled to
 * VideoPlayerViewModel / CastManager types and Play On is walled off from
 * CastManager by design (see PlayOnController).
 *
 * The ViewModel is the activity-scoped singleton the mini bar already holds, so
 * state stays in sync with no param threading.
 */
@Composable
fun PlayOnCompanionScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayOnViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // If the session drops while we're open (disconnect elsewhere, remote left),
    // pop back to where we came from instead of stranding a control surface over
    // nothing.
    LaunchedEffect(uiState.isConnected) {
        if (!uiState.isConnected) onBack()
    }

    // Backdrop palette: derive from artwork when present, else theme fallback.
    val artworkColors = rememberArtworkColors(uiState.artworkUri)
    val dominant = artworkColors?.dominant ?: MaterialTheme.colorScheme.primaryContainer
    val darkMuted = artworkColors?.darkMuted ?: MaterialTheme.colorScheme.background

    val animatedBgStart by animateColorAsState(
        targetValue = dominant,
        animationSpec = tween(1000),
        label = "companionBgStart",
    )
    val animatedBgEnd by animateColorAsState(
        targetValue = darkMuted,
        animationSpec = tween(1200),
        label = "companionBgEnd",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        animatedBgEnd,
                        animatedBgStart.copy(alpha = 0.35f),
                        animatedBgEnd,
                    ),
                ),
            ),
    ) {
        // Ambient artwork colour blob — same treatment as CompanionDashboard.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.5f)
                .align(Alignment.TopCenter)
                .blur(80.dp)
                .graphicsLayer { alpha = 0.45f }
                .background(
                    Brush.radialGradient(
                        colors = listOf(dominant, Color.Transparent),
                        radius = 800f,
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
                    bottom = WindowInsets.navigationBars.asPaddingValues()
                        .calculateBottomPadding() + 24.dp,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CompanionHeader(
                targetName = uiState.targetDeviceName,
                onBack = onBack,
            )

            // Artwork — remote session poster. Box backdrop gives a tonal
            // placeholder while loading or when the session reports no art.
            Box(
                modifier = Modifier
                    .padding(horizontal = 40.dp, vertical = 16.dp)
                    .size(width = 280.dp, height = 280.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
            ) {
                if (uiState.artworkUri.isNullOrBlank()) {
                    Icon(
                        imageVector = Tabler.Outline.Disc,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(72.dp),
                    )
                } else {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(uiState.artworkUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = uiState.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            Text(
                text = uiState.title.ifBlank { "Casting" },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            uiState.artist.takeIf { it.isNotBlank() }?.let { subtitle ->
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                )
            }

            CompanionSeekRow(
                positionMs = uiState.positionMs,
                durationMs = uiState.durationMs,
                onSeek = viewModel::castSeekTo,
            )

            CompanionTransportRow(
                isPlaying = uiState.isPlaying,
                onPlayPause = {
                    if (uiState.isPlaying) viewModel.castPause() else viewModel.castPlay()
                },
                onSeekBack = {
                    val target = (uiState.positionMs - SEEK_BACK_MS).coerceAtLeast(0L)
                    viewModel.castSeekTo(target)
                },
                onSeekForward = {
                    val max = uiState.durationMs.coerceAtLeast(0L)
                    val target = (uiState.positionMs + SEEK_FORWARD_MS).coerceAtMost(max)
                    viewModel.castSeekTo(target)
                },
                onPrevious = viewModel::castPreviousTrack,
                onNext = viewModel::castNextTrack,
            )

            CompanionVolumeRow(
                volume = uiState.volume,
                onVolume = viewModel::setCastVolume,
            )

            CompanionFooter(
                onStop = { viewModel.castStop(context) },
                onDisconnect = { viewModel.disconnect(context) },
            )
        }
    }
}

@Composable
private fun CompanionHeader(
    targetName: String?,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Tabler.Outline.ArrowLeft,
                contentDescription = "Close",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Tabler.Outline.Cast,
                contentDescription = null,
                tint = CastColors.connected,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = targetName?.let { "Casting to $it" } ?: "Casting",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        // Balance the leading icon so the title stays centred.
        Spacer(Modifier.size(48.dp))
    }
}

@Composable
private fun CompanionSeekRow(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
) {
    // Local mirror so dragging doesn't fight the server push; committed on release
    // — same pattern as PlayOnMiniBar.
    var seekPos by remember(positionMs) { mutableFloatStateOf(positionMs.toFloat()) }
    LaunchedEffect(positionMs) { seekPos = positionMs.toFloat() }

    val range = 0f..durationMs.toFloat().coerceAtLeast(1f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Slider(
            value = seekPos.coerceIn(range),
            onValueChange = { seekPos = it },
            onValueChangeFinished = { onSeek(seekPos.toLong()) },
            valueRange = range,
            colors = companionSliderColors(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatDurationMs(positionMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatDurationMs(durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CompanionTransportRow(
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        IconButton(onClick = onPrevious) {
            Icon(
                imageVector = Tabler.Outline.PlayerTrackPrev,
                contentDescription = "Previous",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(28.dp),
            )
        }
        IconButton(onClick = onSeekBack) {
            Icon(
                imageVector = Tabler.Outline.PlayerSkipBack,
                contentDescription = "Back 10 seconds",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(26.dp),
            )
        }
        FloatingActionButton(
            onClick = onPlayPause,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = CircleShape,
            modifier = Modifier.size(64.dp),
        ) {
            Icon(
                imageVector = if (isPlaying) Tabler.Outline.PlayerPause else Tabler.Outline.PlayerPlay,
                contentDescription = if (isPlaying) "Pause" else "Play",
                modifier = Modifier.size(32.dp),
            )
        }
        IconButton(onClick = onSeekForward) {
            Icon(
                imageVector = Tabler.Outline.PlayerSkipForward,
                contentDescription = "Forward 30 seconds",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(26.dp),
            )
        }
        IconButton(onClick = onNext) {
            Icon(
                imageVector = Tabler.Outline.PlayerTrackNext,
                contentDescription = "Next",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
private fun CompanionVolumeRow(
    volume: Float,
    onVolume: (Float) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Tabler.Outline.Volume,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Slider(
            value = volume,
            onValueChange = onVolume,
            valueRange = 0f..1f,
            colors = companionSliderColors(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CompanionFooter(
    onStop: () -> Unit,
    onDisconnect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = onStop,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Tabler.Outline.Power,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("Stop", style = MaterialTheme.typography.labelLarge)
            }
        }
        Surface(
            onClick = onDisconnect,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Tabler.Outline.X,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("Disconnect", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun companionSliderColors() = SliderDefaults.colors(
    thumbColor = MaterialTheme.colorScheme.primary,
    activeTrackColor = MaterialTheme.colorScheme.primary,
    inactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
)

private const val SEEK_BACK_MS = 10_000L
private const val SEEK_FORWARD_MS = 30_000L
