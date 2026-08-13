package com.raulshma.jellyplay.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Cast
import com.composables.icons.tabler.outline.ChevronUp
import com.composables.icons.tabler.outline.PlayerPause
import com.composables.icons.tabler.outline.PlayerPlay
import com.composables.icons.tabler.outline.Volume
import com.composables.icons.tabler.outline.X
import com.raulshma.jellyplay.R
import com.raulshma.jellyplay.core.designsystem.theme.CastColors
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.core.ui.tv.components.DpadSlider

/**
 * Persistent transport bar shown while a "Play On" session is active. Two-row
 * layout so every control stays visible + usable on narrow screens:
 *
 *  Row 1: cast glyph · title/target (flex) · play-pause · disconnect
 *  Row 2: seek slider (flex) · volume icon + slider
 *
 * Styled to match the floating navigation bar: translucent pill surface
 * (`surfaceContainer @ 0.65`, full-rounded), zero tonal/shadow elevation.
 * Pure-state — driven entirely by params.
 */
@Composable
fun PlayOnMiniBar(
    isVisible: Boolean,
    targetDeviceName: String?,
    title: String,
    subtitle: String,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    volume: Float,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onVolume: (Float) -> Unit,
    onDisconnect: () -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier,
    ) {
        // Match the floating nav bar: translucent surfaceContainer, smooth
        // corners, zero elevation.
        Surface(
            shape = ShapeCache.smooth28,
            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.85f),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                // ---- Row 1: identity + transport ----
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusGroup(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Identity area is the expand affordance — tapping it opens
                    // the full Play On companion. Kept off the sliders / transport
                    // buttons so a scrub/volume drag never accidentally navigates.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clip(ShapeCache.smooth16)
                            .focusIndicator(ShapeCache.smooth16)
                            .clickable(onClick = onExpand),
                    ) {
                        Icon(
                            imageVector = Tabler.Outline.Cast,
                            contentDescription = null,
                            tint = CastColors.connected,
                            modifier = Modifier.size(22.dp),
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title.ifBlank { "Casting" },
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = targetDeviceName ?: subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        // Chevron signals the bar expands into a full control surface.
                        Icon(
                            imageVector = Tabler.Outline.ChevronUp,
                            contentDescription = stringResource(R.string.cast_open_companion),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }

                    PlayOnIconButton(
                        onClick = onPlayPause,
                        iconVector = if (isPlaying) Tabler.Outline.PlayerPause else Tabler.Outline.PlayerPlay,
                        iconDescription = if (isPlaying) stringResource(R.string.media_pause) else stringResource(R.string.media_play),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    PlayOnIconButton(
                        onClick = onDisconnect,
                        iconVector = Tabler.Outline.X,
                        iconDescription = stringResource(R.string.media_disconnect),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(6.dp))

                // ---- Row 2: seek + volume ----
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusGroup(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    var seekPos by remember(positionMs) { mutableStateOf(positionMs.toFloat()) }
                    LaunchedEffect(positionMs) { seekPos = positionMs.toFloat() }
                    DpadSlider(
                        value = seekPos,
                        onValueChange = { seekPos = it },
                        onValueChangeFinished = { onSeek(seekPos.toLong()) },
                        valueRange = 0f..(durationMs.toFloat().coerceAtLeast(1f)),
                        colors = sliderColors(),
                        modifier = Modifier
                            .weight(1f)
                            .height(28.dp),
                    )

                    Spacer(Modifier.width(12.dp))

                    Icon(
                        imageVector = Tabler.Outline.Volume,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    DpadSlider(
                        value = volume,
                        onValueChange = onVolume,
                        valueRange = 0f..1f,
                        colors = sliderColors(),
                        modifier = Modifier
                            .width(90.dp)
                            .height(28.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun sliderColors() = SliderDefaults.colors(
    thumbColor = MaterialTheme.colorScheme.primary,
    activeTrackColor = MaterialTheme.colorScheme.primary,
    inactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
)

@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlayOnIconButton(
    onClick: () -> Unit,
    iconVector: ImageVector,
    iconDescription: String,
    tint: Color,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "playOnButtonScale",
    )

    Box(
        modifier = Modifier
            .size(36.dp)
            .scale(scale)
            .clip(CircleShape)
            .focusIndicator(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.fillMaxSize(),
            interactionSource = interactionSource,
        ) {
            Icon(
                imageVector = iconVector,
                contentDescription = iconDescription,
                tint = tint,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
