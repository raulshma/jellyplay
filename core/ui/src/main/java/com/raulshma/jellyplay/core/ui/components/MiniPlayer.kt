package com.raulshma.jellyplay.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import com.raulshma.jellyplay.core.ui.tv.tvFocusable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

import com.raulshma.jellyplay.core.designsystem.theme.AlphaEasing
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.animation.AnimationTokens
import com.raulshma.jellyplay.core.ui.image.MediaImage

@Composable
fun MiniPlayer(
    isVisible: Boolean,
    title: String,
    artist: String,
    artworkUri: String,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onAddToQueue: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val sharedTransitionScope = LocalSharedTransitionScope.current

    val enterTransition = if (sharedTransitionScope != null) {
        fadeIn(tween(AnimationTokens.MediumDuration, easing = AlphaEasing))
    } else {
        slideInVertically(
            initialOffsetY = { it },
            animationSpec = spring(stiffness = 400f),
        ) + fadeIn(tween(AnimationTokens.MediumDuration, easing = AlphaEasing))
    }

    val exitTransition = if (sharedTransitionScope != null) {
        fadeOut(tween(AnimationTokens.FastDuration, easing = AlphaEasing))
    } else {
        slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(AnimationTokens.DefaultDuration),
        ) + fadeOut(tween(AnimationTokens.FastDuration, easing = AlphaEasing))
    }

    AnimatedVisibility(
        visible = isVisible && title.isNotBlank(),
        enter = enterTransition,
        exit = exitTransition,
        modifier = modifier,
    ) {
        val animatedVisibilityScope = this

        // Pixel Player–style: artwork-tinted pill surface
        val navBarColorState = LocalNavigationBarColor.current
        val fallbackColor = MaterialTheme.colorScheme.surfaceContainerHigh
        val baseColor = navBarColorState.value ?: fallbackColor
        val pixelTint = Color(
            red = (baseColor.red * 0.55f + 0.08f).coerceIn(0f, 1f),
            green = (baseColor.green * 0.4f + 0.04f).coerceIn(0f, 1f),
            blue = (baseColor.blue * 0.55f + 0.08f).coerceIn(0f, 1f),
            alpha = 0.92f,
        )
        val animatedColor by animateColorAsState(
            targetValue = pixelTint,
            animationSpec = tween(AnimationTokens.StandardDuration),
            label = "miniPlayerColor",
        )

        val contentTextColor = MaterialTheme.colorScheme.onSurface
        val contentTextColorSecondary = MaterialTheme.colorScheme.onSurfaceVariant
        val contentIconTint = MaterialTheme.colorScheme.onSurfaceVariant
        val iconButtonContainer = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        val iconButtonContent = MaterialTheme.colorScheme.onSurface
        val fallbackIconBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        val fallbackIconTint = MaterialTheme.colorScheme.onSurfaceVariant

        @OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
        val sharedContainerModifier = if (sharedTransitionScope != null) {
            with(sharedTransitionScope) {
                Modifier.sharedBounds(
                    rememberSharedContentState(key = "audio_player_container"),
                    animatedVisibilityScope = animatedVisibilityScope,
                    boundsTransform = androidx.compose.animation.BoundsTransform { _, _ ->
                        spring(
                            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioLowBouncy,
                            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow,
                        )
                    }
                )
            }
        } else Modifier

        Surface(
            shape = ShapeCache.smoothPill,
            color = animatedColor,
            shadowElevation = 8.dp,
            tonalElevation = 2.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .then(sharedContainerModifier),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .tvFocusable().clickable(onClick = onClick)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Circular artwork thumbnail
                if (artworkUri.isNotBlank()) {
                    @OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
                    val sharedArtModifier = if (sharedTransitionScope != null) {
                        with(sharedTransitionScope) {
                            Modifier.sharedElement(
                                rememberSharedContentState(key = "audio_player_album_art"),
                                animatedVisibilityScope = animatedVisibilityScope,
                            )
                        }
                    } else Modifier

                    MediaImage(
                        url = artworkUri,
                        contentDescription = title,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .then(sharedArtModifier),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(fallbackIconBg),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = fallbackIconTint,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = contentTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = contentTextColorSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (onAddToQueue != null) {
                    AnimatedIconButton(
                        onClick = onAddToQueue,
                        size = 36.dp,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        iconVector = Icons.Default.QueueMusic,
                        iconDescription = "Add to Queue",
                        iconSize = 20.dp,
                    )
                }

                @OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
                val sharedPlayPauseModifier = if (sharedTransitionScope != null) {
                    with(sharedTransitionScope) {
                        Modifier.sharedElement(
                            rememberSharedContentState(key = "audio_player_play_pause"),
                            animatedVisibilityScope = animatedVisibilityScope,
                        )
                    }
                } else Modifier

                @OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
                val sharedSkipNextModifier = if (sharedTransitionScope != null) {
                    with(sharedTransitionScope) {
                        Modifier.sharedElement(
                            rememberSharedContentState(key = "audio_player_skip_next"),
                            animatedVisibilityScope = animatedVisibilityScope,
                        )
                    }
                } else Modifier

                AnimatedIconButton(
                    onClick = onPlayPause,
                    size = 40.dp,
                    containerColor = iconButtonContainer,
                    contentColor = iconButtonContent,
                    iconVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    iconDescription = if (isPlaying) "Pause" else "Play",
                    iconSize = 22.dp,
                    modifier = sharedPlayPauseModifier,
                )

                AnimatedIconButton(
                    onClick = onSkipNext,
                    size = 36.dp,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    iconVector = Icons.Default.SkipNext,
                    iconDescription = "Skip Next",
                    iconSize = 20.dp,
                    modifier = sharedSkipNextModifier,
                )

                AnimatedIconButton(
                    onClick = onClose,
                    size = 36.dp,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    iconVector = Icons.Default.Close,
                    iconDescription = "Close",
                    iconSize = 20.dp,
                )
            }
        }
    }
}

@Composable
private fun AnimatedIconButton(
    onClick: () -> Unit,
    size: Dp,
    containerColor: Color? = null,
    contentColor: Color,
    iconVector: ImageVector,
    iconDescription: String,
    iconSize: Dp,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = spring(stiffness = 600f),
        label = "miniPlayerButtonScale",
    )

    val shape = ShapeCache.smoothPill

    Box(
        modifier = modifier
            .size(size)
            .scale(scale)
            .then(
                if (containerColor != null) {
                    Modifier.clip(shape).background(containerColor)
                } else Modifier
            ),
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.fillMaxSize(),
        ) {
            Icon(
                imageVector = iconVector,
                contentDescription = iconDescription,
                tint = contentColor,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}