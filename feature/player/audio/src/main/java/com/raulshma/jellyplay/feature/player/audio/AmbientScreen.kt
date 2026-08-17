package com.raulshma.jellyplay.feature.player.audio

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.designsystem.theme.ArtworkColors
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.rememberArtworkColors
import com.raulshma.jellyplay.core.ui.components.LocalReducedMotion
import com.raulshma.jellyplay.core.ui.components.rememberBlobStops
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.feature.player.audio.R

@Composable
fun AmbientScreen(
    imageUrl: String?,
    title: String,
    artist: String,
    onTap: () -> Unit,
    viewModel: AudioPlayerViewModel = hiltViewModel(),
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
        onPlayPause = { viewModel.togglePlayPause() },
        onSkipNext = { viewModel.skipToNext() },
        onSkipPrevious = { viewModel.skipToPrevious() },
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

    BackHandler { onTap() }

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
                        contentDescription = stringResource(R.string.audio_controls_previous),
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
                        contentDescription = if (isPlaying) stringResource(R.string.audio_controls_pause) else stringResource(R.string.audio_controls_play),
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
                        contentDescription = stringResource(R.string.audio_controls_next),
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(28.dp),
                    )
                }
            }

            Text(
                text = stringResource(R.string.audio_ambient_tap_to_exit),
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
    val animatables = remember(blobCount) {
        List(blobCount) { Animatable(initialValue = 0f) }
    }

    // Resolve the blob palette + per-blob 3-stop gradient stops ONCE (keyed on
    // the palette). Shared with AmbientColorBackdrop via rememberBlobStops so
    // the palette → stops projection isn't duplicated. Center/radius still
    // vary per frame.
    val blobStops = rememberBlobStops(colors, blobCount)

    // Four concurrent infinite animations driving a full-screen Canvas redraw.
    // This is the most expensive decorative surface in the app and it stays
    // visible for the whole listening session. In performance mode freeze the
    // blobs (LaunchedEffect bodies are skipped, values stay 0f).
    if (!reducedMotion) {
        animatables.forEachIndexed { index, animatable ->
            LaunchedEffect(index) {
                animatable.animateTo(
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(
                            durationMillis = 10000 + index * 3000,
                            easing = LinearEasing,
                        ),
                        repeatMode = RepeatMode.Reverse,
                    ),
                )
            }
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(Color.Black)

        val width = size.width
        val height = size.height

        blobStops.forEachIndexed { index, stops ->
            val progress = animatables[index].value
            val x = width * (0.2f + 0.6f * kotlin.math.sin(progress * 2 * Math.PI + index).toFloat())
            val y = height * (0.2f + 0.6f * kotlin.math.cos(progress * 2 * Math.PI + index * 1.5f).toFloat())
            val radius = (width.coerceAtMost(height) * 0.4f) * (0.8f + 0.2f * kotlin.math.sin(progress * Math.PI).toFloat())

            drawCircle(
                brush = Brush.radialGradient(
                    colors = stops,
                    center = Offset(x, y),
                    radius = radius,
                ),
                radius = radius,
                center = Offset(x, y),
            )
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
