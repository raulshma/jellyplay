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
import androidx.compose.ui.unit.sp
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
    onStop: () -> Unit,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = isVisible && title.isNotBlank(),
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = spring(stiffness = 400f),
        ) + fadeIn(tween(AnimationTokens.MediumDuration, easing = AlphaEasing)),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(AnimationTokens.DefaultDuration),
        ) + fadeOut(tween(AnimationTokens.FastDuration, easing = AlphaEasing)),
        modifier = modifier,
    ) {
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

        Surface(
            shape = ShapeCache.smoothPill,
            color = animatedColor,
            shadowElevation = 8.dp,
            tonalElevation = 2.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
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
                    MediaImage(
                        url = artworkUri,
                        contentDescription = title,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.7f),
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
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 14.sp,
                    )
                    Text(
                        text = artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.65f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 12.sp,
                    )
                }

                AnimatedIconButton(
                    onClick = onPlayPause,
                    size = 40.dp,
                    containerColor = Color.White.copy(alpha = 0.2f),
                    contentColor = Color.White,
                    iconVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    iconDescription = if (isPlaying) "Pause" else "Play",
                    iconSize = 22.dp,
                )

                AnimatedIconButton(
                    onClick = onSkipNext,
                    size = 36.dp,
                    contentColor = Color.White.copy(alpha = 0.8f),
                    iconVector = Icons.Default.SkipNext,
                    iconDescription = "Skip Next",
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
        modifier = Modifier
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