package com.raulshma.jellyplay.feature.player.audio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.LongState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.designsystem.theme.ArtworkColors
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.rememberArtworkColors
import com.raulshma.jellyplay.core.ui.components.JellyPlayBackHandler
import com.raulshma.jellyplay.core.ui.components.LaunchBlobDrift
import com.raulshma.jellyplay.core.ui.components.LocalReducedMotion
import com.raulshma.jellyplay.core.ui.components.rememberBlobStops
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.feature.player.audio.generated.resources.Res
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_ambient_tap_to_exit
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_controls_next
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_controls_pause
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_controls_play
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_controls_previous
import kotlin.math.PI

@Composable
fun AmbientScreen(
    imageUrl: String?,
    title: String,
    artist: String,
    onTap: () -> Unit,
    viewModel: AudioPlayerViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    AmbientScreenContent(
        imageUrl = imageUrl,
        title = title,
        artist = artist,
        isPlaying = uiState.isPlaying,
        currentPositionState = viewModel.currentPositionState,
        duration = uiState.duration,
        onTap = onTap,
        onPlayPause = { viewModel.onEvent(AudioPlayerUiEvent.TogglePlayPause) },
        onSkipNext = { viewModel.onEvent(AudioPlayerUiEvent.SkipToNext) },
        onSkipPrevious = { viewModel.onEvent(AudioPlayerUiEvent.SkipToPrevious) },
    )
}

@Composable
private fun AmbientScreenContent(
    imageUrl: String?,
    title: String,
    artist: String,
    isPlaying: Boolean,
    currentPositionState: LongState,
    duration: Long,
    onTap: () -> Unit,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
) {
    val artworkColors = rememberArtworkColors(imageUrl)
    // Memoize the resolved blob palette: extractAmbientColors builds a fresh
    // listOfNotNull{}.map{} each call, and the list is structurally identical
    // for a given palette. Recomposition here is gated by rememberArtworkColors
    // but still churns the allocation whenever it re-emits.
    val colors = remember(artworkColors) { extractAmbientColors(artworkColors) }

    val isTv = LocalTvMode.current
    val controlsFocusRequester = remember { FocusRequester() }
    // On TV grab focus onto the play/pause button so the D-pad lands somewhere actionable.
    LaunchedEffect(Unit) {
        if (isTv) controlsFocusRequester.tryRequestFocus("ambient_controls")
    }

    JellyPlayBackHandler(enabled = true) { onTap() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onTap),
    ) {
        AmbientBackground(colors = colors)

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 32.dp)
                .padding(bottom = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Text(
                text = artist,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )

            if (duration > 0) {
                Spacer(Modifier.height(16.dp))
                LinearProgressIndicator(
                    // Read the position LongState only inside this leaf lambda
                    // (mirroring PixelProgressSection) so the 4 Hz tick skips
                    // recomposing the whole content and only re-draws the bar.
                    progress = { (currentPositionState.value.toFloat() / duration).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(2.dp),
                    color = Color.White.copy(alpha = 0.7f),
                    trackColor = Color.White.copy(alpha = 0.2f),
                    strokeCap = StrokeCap.Round,
                )
                Spacer(Modifier.height(16.dp))
            }

            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val prevFocusState = rememberTvFocusState(focusedScale = 1.08f)
                IconButton(
                    onClick = onSkipPrevious,
                    modifier = Modifier
                        .size(48.dp)
                        .then(prevFocusState.focusModifier)
                        .tvFocusIndicator(prevFocusState, CircleShape),
                ) {
                    Icon(
                        Tabler.Outline.PlayerSkipBack,
                        contentDescription = stringResource(Res.string.audio_controls_previous),
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(28.dp),
                    )
                }
                Spacer(Modifier.width(16.dp))
                val playPauseFocusState = rememberTvFocusState(focusedScale = 1.08f)
                IconButton(
                    onClick = onPlayPause,
                    modifier = Modifier
                        .size(56.dp)
                        .then(playPauseFocusState.focusModifier)
                        .tvFocusIndicator(playPauseFocusState, CircleShape)
                        .then(if (isTv) Modifier.focusRequester(controlsFocusRequester) else Modifier),
                ) {
                    Icon(
                        if (isPlaying) Tabler.Outline.PlayerPause else Tabler.Outline.PlayerPlay,
                        contentDescription = if (isPlaying) stringResource(Res.string.audio_controls_pause) else stringResource(Res.string.audio_controls_play),
                        tint = Color.White,
                        modifier = Modifier.size(36.dp),
                    )
                }
                Spacer(Modifier.width(16.dp))
                val nextFocusState = rememberTvFocusState(focusedScale = 1.08f)
                IconButton(
                    onClick = onSkipNext,
                    modifier = Modifier
                        .size(48.dp)
                        .then(nextFocusState.focusModifier)
                        .tvFocusIndicator(nextFocusState, CircleShape),
                ) {
                    Icon(
                        Tabler.Outline.PlayerSkipForward,
                        contentDescription = stringResource(Res.string.audio_controls_next),
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(28.dp),
                    )
                }
            }

            Text(
                text = stringResource(Res.string.audio_ambient_tap_to_exit),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun AmbientBackground(colors: List<Color>) {
    val reducedMotion = LocalReducedMotion.current
    val blobCount = 4
    val blobProgress = remember(blobCount) {
        List(blobCount) { mutableFloatStateOf(0f) }
    }

    // Resolve the blob palette + per-blob 3-stop gradient stops ONCE (keyed on
    // the palette). Shared with AmbientColorBackdrop via rememberBlobStops so
    // the palette → stops projection isn't duplicated.
    val blobStops = rememberBlobStops(colors, blobCount)

    // Each brush is built ONCE at a nominal radius of 1 and drawn through a
    // translate+scale transform; a uniformly scaled radial gradient is
    // mathematically identical to one built at the frame's radius.
    val blobBrushes = remember(blobStops) {
        blobStops.map { stops ->
            Brush.radialGradient(colors = stops, center = Offset.Zero, radius = 1f)
        }
    }

    // The blobs drift on a shared ~30 Hz-gated frame clock (see
    // core/ui's LaunchBlobDrift) instead of per-blob Animatables. In
    // performance/reduced-motion mode the effect body is skipped and the
    // values stay 0f, freezing the blobs.
    if (!reducedMotion) {
        LaunchBlobDrift(blobProgress)
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(Color.Black)

        val width = size.width
        val height = size.height

        blobBrushes.forEachIndexed { index, brush ->
            val progress = blobProgress[index].floatValue
            val x = width * (0.2f + 0.6f * kotlin.math.sin(progress * 2 * PI + index).toFloat())
            val y = height * (0.2f + 0.6f * kotlin.math.cos(progress * 2 * PI + index * 1.5f).toFloat())
            val radius = (width.coerceAtMost(height) * 0.4f) * (0.8f + 0.2f * kotlin.math.sin(progress * PI).toFloat())

            translate(x, y) {
                scale(radius, radius, pivot = Offset.Zero) {
                    drawCircle(brush = brush, radius = 1f, center = Offset.Zero)
                }
            }
        }
    }
}

private fun extractAmbientColors(artworkColors: ArtworkColors?): List<Color> {
    if (artworkColors == null) return emptyList()

    return listOfNotNull(
        artworkColors.vibrant,
        artworkColors.darkVibrant,
        artworkColors.lightVibrant,
        artworkColors.muted,
        artworkColors.darkMuted,
        artworkColors.lightMuted,
        artworkColors.dominant,
    ).map { it.copy(alpha = 1f) }
}
