package com.raulshma.jellyplay.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import com.raulshma.jellyplay.R
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.LocalIsSynthwave
import com.raulshma.jellyplay.core.designsystem.theme.LocalIsSoothingTheme
import com.raulshma.jellyplay.core.ui.animation.AnimationTokens
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.raulshma.jellyplay.core.ui.components.LocalNavigationBarColor
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

@Composable
fun VideoMiniPlayer(
    isVisible: Boolean,
    engine: MediaEngine?,
    title: String,
    subtitle: String,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit,
    onPlayPause: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = isVisible && engine != null,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        ) + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        ) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
        modifier = modifier,
    ) {
        val navBarColorState = LocalNavigationBarColor.current
        val isSynthwave = LocalIsSynthwave.current
        val isSoothing = LocalIsSoothingTheme.current

        val synthwaveTint = com.raulshma.jellyplay.core.designsystem.theme.ThemeVariantColors.SYNTHWAVE_TINT
        val soothingTint = if (androidx.compose.foundation.isSystemInDarkTheme()) {
            com.raulshma.jellyplay.core.designsystem.theme.ThemeVariantColors.SOOTHING_DARK_TINT
        } else {
            com.raulshma.jellyplay.core.designsystem.theme.ThemeVariantColors.SOOTHING_LIGHT_TINT
        }
        val targetTint = when {
            isSynthwave -> synthwaveTint
            isSoothing -> soothingTint
            else -> navBarColorState.value ?: MaterialTheme.colorScheme.surfaceContainerHigh
        }

        val animatedColor by animateColorAsState(
            targetValue = targetTint,
            animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
            label = "videoMiniPlayerColor",
        )

        val primary = MaterialTheme.colorScheme.primary
        val secondary = MaterialTheme.colorScheme.secondary
        val synthwaveBorder = remember(primary, secondary) {
            androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                brush = androidx.compose.ui.graphics.Brush.linearGradient(
                    colors = listOf(primary, secondary)
                )
            )
        }

        val border = when {
            isSynthwave -> {
                synthwaveBorder
            }
            isSoothing -> {
                androidx.compose.foundation.BorderStroke(
                    width = 0.5.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
            }
            else -> null
        }

        // Wrap the shape when-chains in remember so the RoundedCornerShape
        // instances aren't rebuilt on every recomposition. The branches only
        // depend on isSynthwave / isSoothing.
        val shape = remember(isSynthwave, isSoothing) {
            when {
                isSynthwave -> RoundedCornerShape(0.dp)
                isSoothing -> ShapeCache.smooth16
                else -> ShapeCache.smooth12
            }
        }

        val videoShape = remember(isSynthwave, isSoothing) {
            when {
                isSynthwave -> RoundedCornerShape(0.dp)
                isSoothing -> RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                else -> RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
            }
        }

        val bottomShape = remember(isSynthwave, isSoothing) {
            when {
                isSynthwave -> RoundedCornerShape(0.dp)
                isSoothing -> RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
                else -> RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
            }
        }

        Surface(
            shape = shape,
            color = animatedColor,
            border = border,
            shadowElevation = if (isSoothing) 4.dp else 12.dp,
            tonalElevation = if (isSoothing) 0.dp else 4.dp,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(112.dp)
                        .clip(videoShape)
                        .background(Color.Black)
                        .focusIndicator(videoShape)
                        .clickable(onClick = onClick),
                ) {
                    if (engine != null) {
                        key(engine) {
                            AndroidView(
                                factory = { ctx ->
                                    engine.createSurfaceView(ctx)
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }

                    IconButtonWithPressAnimation(
                        onClick = onClose,
                        size = 32.dp,
                    ) {
                        Icon(
                            Tabler.Outline.X,
                            contentDescription = stringResource(R.string.media_close),
                            modifier = Modifier.size(16.dp),
                            tint = Color.White,
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(bottomShape)
                        .background(animatedColor)
                        .focusIndicator(bottomShape)
                        .clickable(onClick = onClick)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (subtitle.isNotBlank()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 11.sp,
                            )
                        }
                    }

                    IconButtonWithPressAnimation(
                        onClick = onPlayPause,
                        size = 36.dp,
                    ) {
                        Icon(
                            if (isPlaying) Tabler.Outline.PlayerPause else Tabler.Outline.PlayerPlay,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun IconButtonWithPressAnimation(
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "videoMiniButtonScale",
    )

    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(size)
            .padding(4.dp)
            .scale(scale)
            .focusIndicator(androidx.compose.foundation.shape.CircleShape),
        shapes = androidx.compose.material3.IconButtonDefaults.shapes(),
        interactionSource = interactionSource,
    ) {
        content()
    }
}
