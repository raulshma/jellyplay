package com.raulshma.jellyplay.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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
import com.raulshma.jellyplay.core.designsystem.theme.LocalIsSynthwave
import com.raulshma.jellyplay.core.designsystem.theme.LocalIsSoothingTheme
import com.raulshma.jellyplay.core.designsystem.theme.LocalIsMonochromeTheme
import com.raulshma.jellyplay.core.designsystem.theme.LocalThemeVariant
import com.raulshma.jellyplay.core.designsystem.theme.cardBorder
import com.raulshma.jellyplay.core.designsystem.theme.containerTint
import com.raulshma.jellyplay.core.designsystem.theme.shadowElevation
import com.raulshma.jellyplay.core.designsystem.theme.tonalElevation
import com.raulshma.jellyplay.core.designsystem.theme.ThemeVariant
import coil3.size.Size as CoilSize
import androidx.compose.ui.graphics.Brush
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

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
        fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec())
    } else {
        slideInVertically(
            initialOffsetY = { it },
            animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        ) + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec())
    }

    val exitTransition = if (sharedTransitionScope != null) {
        fadeOut(MaterialTheme.motionScheme.fastEffectsSpec())
    } else {
        slideOutVertically(
            targetOffsetY = { it },
            animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        ) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec())
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
        val themeVariant = com.raulshma.jellyplay.core.designsystem.theme.LocalThemeVariant.current
        val targetTint = themeVariant.containerTint(pixelTint)

        val animatedColor by animateColorAsState(
            targetValue = targetTint,
            animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
            label = "miniPlayerColor",
        )

        val contentTextColor = MaterialTheme.colorScheme.onSurface
        val contentTextColorSecondary = MaterialTheme.colorScheme.onSurfaceVariant
        val contentIconTint = MaterialTheme.colorScheme.onSurfaceVariant
        val iconButtonContainer = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        val iconButtonContent = MaterialTheme.colorScheme.onSurface
        val fallbackIconBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        val fallbackIconTint = MaterialTheme.colorScheme.onSurfaceVariant

        val boundsSpec = MaterialTheme.motionScheme.slowSpatialSpec<androidx.compose.ui.geometry.Rect>()
        @OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
        val sharedContainerModifier = if (sharedTransitionScope != null) {
            with(sharedTransitionScope) {
                Modifier.sharedBounds(
                    rememberSharedContentState(key = "audio_player_container"),
                    animatedVisibilityScope = animatedVisibilityScope,
                    boundsTransform = androidx.compose.animation.BoundsTransform { _, _ ->
                        boundsSpec
                    }
                )
            }
        } else Modifier

        val border = themeVariant.cardBorder(
            primary = MaterialTheme.colorScheme.primary,
            secondary = MaterialTheme.colorScheme.secondary,
            outline = MaterialTheme.colorScheme.outline,
        )

        val shape = when (themeVariant) {
            com.raulshma.jellyplay.core.designsystem.theme.ThemeVariant.SOOTHING -> ShapeCache.smooth16
            com.raulshma.jellyplay.core.designsystem.theme.ThemeVariant.MONOCHROME -> ShapeCache.smooth16
            else -> ShapeCache.smoothPill
        }

        Surface(
            shape = shape,
            color = animatedColor,
            border = border,
            shadowElevation = themeVariant.shadowElevation(8.dp),
            tonalElevation = themeVariant.tonalElevation(2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .then(sharedContainerModifier),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .focusIndicator(shape)
                    .clickable(onClick = onClick)
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
                        size = CoilSize(128, 128),
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
                            Tabler.Outline.Music,
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
                        iconVector = Tabler.Outline.Playlist,
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
                    iconVector = if (isPlaying) Tabler.Outline.PlayerPause else Tabler.Outline.PlayerPlay,
                    iconDescription = if (isPlaying) "Pause" else "Play",
                    iconSize = 22.dp,
                    modifier = sharedPlayPauseModifier,
                )

                AnimatedIconButton(
                    onClick = onSkipNext,
                    size = 36.dp,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    iconVector = Tabler.Outline.PlayerSkipForward,
                    iconDescription = "Skip Next",
                    iconSize = 20.dp,
                    modifier = sharedSkipNextModifier,
                )

                AnimatedIconButton(
                    onClick = onClose,
                    size = 36.dp,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    iconVector = Tabler.Outline.X,
                    iconDescription = "Close",
                    iconSize = 20.dp,
                )
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
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
    val motionScheme = MaterialTheme.motionScheme
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val iconFocusState = rememberTvFocusState(focusedScale = 1.1f)
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = motionScheme.fastSpatialSpec(),
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
            modifier = Modifier
                .fillMaxSize()
                .then(iconFocusState.focusModifier)
                .tvFocusIndicator(iconFocusState, CircleShape),
            shapes = androidx.compose.material3.IconButtonDefaults.shapes(),
            interactionSource = interactionSource,
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