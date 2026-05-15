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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
        val navBarColorState = LocalNavigationBarColor.current
        val animatedColor by animateColorAsState(
            targetValue = navBarColorState.value ?: MaterialTheme.colorScheme.surfaceContainerHigh,
            animationSpec = tween(AnimationTokens.StandardDuration),
            label = "miniPlayerColor",
        )
        val contentAlpha = 1f

        Surface(
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            color = animatedColor,
            shadowElevation = 8.dp,
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .graphicsLayer { alpha = contentAlpha }
                    .tvFocusable().clickable(onClick = onClick)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (artworkUri.isNotBlank()) {
                    MediaImage(
                        url = artworkUri,
                        contentDescription = title,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp),
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
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 14.sp,
                    )
                    Text(
                        text = artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 12.sp,
                    )
                }

                AnimatedIconButton(
                    onClick = onPlayPause,
                    size = 40.dp,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    iconVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    iconDescription = if (isPlaying) "Pause" else "Play",
                    iconSize = 22.dp,
                )

                AnimatedIconButton(
                    onClick = onSkipNext,
                    size = 40.dp,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    iconVector = Icons.Default.SkipNext,
                    iconDescription = "Skip Next",
                    iconSize = 22.dp,
                )

                AnimatedIconButton(
                    onClick = onStop,
                    size = 36.dp,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    iconVector = Icons.Default.Close,
                    iconDescription = "Close",
                    iconSize = 18.dp,
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

    val shape = RoundedCornerShape(size / 2)

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