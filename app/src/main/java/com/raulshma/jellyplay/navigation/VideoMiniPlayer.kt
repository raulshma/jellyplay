package com.raulshma.jellyplay.navigation

import androidx.compose.animation.AnimatedVisibility
import com.raulshma.jellyplay.core.ui.tv.tvFocusable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import com.raulshma.jellyplay.core.designsystem.theme.AlphaEasing
import com.raulshma.jellyplay.core.designsystem.theme.FancyTransitionEasing
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.raulshma.jellyplay.core.ui.components.LocalNavigationBarColor
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine

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
            animationSpec = spring(stiffness = 400f),
        ) + fadeIn(tween(AnimationTokens.MediumDuration, easing = AlphaEasing)),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(AnimationTokens.DefaultDuration, easing = FancyTransitionEasing),
        ) + fadeOut(tween(AnimationTokens.QuickDuration, easing = AlphaEasing)),
        modifier = modifier,
    ) {
        val navBarColorState = LocalNavigationBarColor.current
        val animatedColor by animateColorAsState(
            targetValue = navBarColorState.value ?: MaterialTheme.colorScheme.surfaceContainerHigh,
            animationSpec = tween(AnimationTokens.StandardDuration),
            label = "videoMiniPlayerColor",
        )
        val contentAlpha by animateFloatAsState(
            targetValue = 1f,
            animationSpec = tween(AnimationTokens.StandardDuration),
            label = "videoMiniContentAlpha",
        )

        Surface(
            shape = ShapeCache.smooth12,
            color = animatedColor,
            shadowElevation = 12.dp,
            tonalElevation = 4.dp,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(112.dp)
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                        .background(Color.Black)
                        .tvFocusable().clickable(onClick = onClick),
                ) {
                    if (engine != null) {
                        AndroidView(
                            factory = { ctx ->
                                engine.createSurfaceView(ctx)
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    IconButtonWithPressAnimation(
                        onClick = onClose,
                        size = 32.dp,
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            modifier = Modifier.size(16.dp),
                            tint = Color.White,
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(animatedColor)
                        .graphicsLayer { alpha = contentAlpha }
                        .tvFocusable().clickable(onClick = onClick)
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
                            fontSize = 13.sp,
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
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
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
        animationSpec = spring(stiffness = 600f),
        label = "videoMiniButtonScale",
    )

    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(size)
            .padding(4.dp)
            .scale(scale),
    ) {
        content()
    }
}
