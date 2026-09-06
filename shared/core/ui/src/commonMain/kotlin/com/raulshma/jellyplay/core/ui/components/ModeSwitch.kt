package com.raulshma.jellyplay.core.ui.components
import com.raulshma.jellyplay.core.ui.generated.resources.Res
import com.raulshma.jellyplay.core.ui.generated.resources.a11y_mode_music
import com.raulshma.jellyplay.core.ui.generated.resources.a11y_mode_video

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.HomeMode
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator

private fun lerp(start: Float, stop: Float, fraction: Float): Float {
    return start + fraction * (stop - start)
}

/**
 * A beautiful canvas-drawn icon that mathematically morphs between a Music Note 
 * and a Movie Camera based on the transition progress.
 */
@Composable
fun MorphingMusicVideoIcon(
    progress: Float, // 0.0 (Music) to 1.0 (Video)
    color: Color,
    modifier: Modifier = Modifier,
) {
    val morphPath = remember { Path() }

    Canvas(modifier = modifier) {
        val W = size.width
        val H = size.height

        val cx1 = W * 0.34f
        val cy1 = lerp(H * 0.70f, H * 0.32f, progress)
        val r1 = W * 0.12f
        val musicStroke1 = r1 // filled circle
        val videoStroke1 = 2.2f.dp.toPx()
        val strokeWidth1 = lerp(musicStroke1, videoStroke1, progress)
        
        if (progress == 0f) {
            drawCircle(
                color = color,
                radius = r1,
                center = Offset(cx1, cy1)
            )
        } else {
            drawCircle(
                color = color,
                radius = r1 - strokeWidth1 / 2f,
                center = Offset(cx1, cy1),
                style = Stroke(width = strokeWidth1)
            )
        }

        val cx2 = W * 0.66f
        val cy2 = lerp(H * 0.60f, H * 0.32f, progress)
        val r2 = W * 0.12f
        val musicStroke2 = r2 // filled circle
        val videoStroke2 = 2.2f.dp.toPx()
        val strokeWidth2 = lerp(musicStroke2, videoStroke2, progress)
        
        if (progress == 0f) {
            drawCircle(
                color = color,
                radius = r2,
                center = Offset(cx2, cy2)
            )
        } else {
            drawCircle(
                color = color,
                radius = r2 - strokeWidth2 / 2f,
                center = Offset(cx2, cy2),
                style = Stroke(width = strokeWidth2)
            )
        }

        val cornerRadius = CornerRadius(lerp(1.dp.toPx(), 3.5f.dp.toPx(), progress))
        drawRoundRect(
            color = color,
            topLeft = Offset(
                x = lerp(cx1 - 1.1f.dp.toPx(), W * 0.22f, progress),
                y = lerp(H * 0.22f, H * 0.52f, progress)
            ),
            size = Size(
                width = lerp(cx1 + 1.1f.dp.toPx(), W * 0.45f, progress) - lerp(cx1 - 1.1f.dp.toPx(), W * 0.22f, progress),
                height = lerp(cy1, H * 0.84f, progress) - lerp(H * 0.22f, H * 0.52f, progress)
            ),
            cornerRadius = cornerRadius
        )

        drawRoundRect(
            color = color,
            topLeft = Offset(
                x = lerp(cx2 - 1.1f.dp.toPx(), W * 0.45f, progress),
                y = lerp(H * 0.12f, H * 0.52f, progress)
            ),
            size = Size(
                width = lerp(cx2 + 1.1f.dp.toPx(), W * 0.68f, progress) - lerp(cx2 - 1.1f.dp.toPx(), W * 0.45f, progress),
                height = lerp(cy2, H * 0.84f, progress) - lerp(H * 0.12f, H * 0.52f, progress)
            ),
            cornerRadius = cornerRadius
        )

        // A 4-point polygon connecting the two stems in Music mode,
        // and collapsing into a triangle in Video mode.
        morphPath.rewind()
        val path = morphPath.apply {
            moveTo(
                lerp(cx1, W * 0.68f, progress),
                lerp(H * 0.22f, H * 0.60f, progress)
            )
            lineTo(
                lerp(cx1, W * 0.68f, progress),
                lerp(H * 0.32f, H * 0.60f, progress)
            )
            lineTo(
                lerp(cx2, W * 0.68f, progress),
                lerp(H * 0.22f, H * 0.76f, progress)
            )
            lineTo(
                lerp(cx2, W * 0.85f, progress),
                lerp(H * 0.12f, H * 0.68f, progress)
            )
            close()
        }
        drawPath(
            path = path,
            color = color
        )
    }
}

/**
 * A highly refined, responsive, glassmorphic mode switch that toggles between
 * Video and Music modes. Features custom spring animations, dynamic glow,
 * and pure mathematical vector morphing of the icons on slide.
 */
@Composable
fun ModeSwitch(
    currentMode: HomeMode,
    onModeChange: (HomeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isMusic = currentMode == HomeMode.MUSIC

    val progress by animateFloatAsState(
        targetValue = if (isMusic) 0f else 1f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "mode_switch_progress"
    )

    val isLightTheme = com.raulshma.jellyplay.core.designsystem.theme.LocalIsLightTheme.current

    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val trackBgColor = if (isLightTheme) {
        MaterialTheme.colorScheme.surfaceContainerHighest
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)
    }

    // Dynamic background glow/shadow tint
    val glowColor = if (isMusic) primaryColor else tertiaryColor

    val interactionSource = remember { MutableInteractionSource() }
    val isTv = LocalTvMode.current
    val layoutDirection = androidx.compose.ui.platform.LocalLayoutDirection.current
    val tvFocusState = rememberTvFocusState(focusedScale = 1.08f)
    val musicModeLabel = stringResource(Res.string.a11y_mode_music)
    val videoModeLabel = stringResource(Res.string.a11y_mode_video)

    val width = 76.dp
    val height = 36.dp
    val thumbSize = 28.dp

    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .clip(CircleShape)
            .background(trackBgColor)
            .shadow(elevation = 1.dp, shape = CircleShape)
            .then(tvFocusState.focusModifier)
            .tvFocusIndicator(tvFocusState, CircleShape)
            // a11y: previously a bare .clickable with no role/state, so TalkBack
            // announced this Video/Music switch as an unlabeled button. Use
            // toggleable with Role.Switch (announces as a switch + on/off state)
            // preserving the existing interactionSource (so ripple/focus behavior
            // is unchanged) and a stateDescription naming the active mode.
            .toggleable(
                value = isMusic,
                role = Role.Switch,
                interactionSource = interactionSource,
                indication = null,
                onValueChange = {
                    onModeChange(if (isMusic) HomeMode.VIDEO else HomeMode.MUSIC)
                },
            )
            .semantics {
                stateDescription = if (isMusic) musicModeLabel else videoModeLabel
            }
            .padding(4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        // Underlay static background icons that reveal when the thumb slides away
        Box(modifier = Modifier.fillMaxSize()) {
            Icon(
                imageVector = Tabler.Outline.Music,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 10.dp)
                    .size(16.dp)
            )
            Icon(
                imageVector = Tabler.Outline.Movie,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 10.dp)
                    .size(16.dp)
            )
        }

        Box(
            modifier = Modifier
                .offset {
                    val maxOffset = (width - thumbSize - 8.dp) // 76.dp - 28.dp - 8.dp = 40.dp
                    val xPx = progress * maxOffset.toPx()
                    // Mirror the thumb's travel in RTL: the parent Box aligns the thumb to
                    // TopStart (which becomes TopEnd visually in RTL), so a positive `x`
                    // pushes the thumb further off-screen. Negate to slide toward the visual
                    // start edge as `progress` increases — matches how CenterStart/CenterEnd
                    // icons are mirrored above.
                    val directedX = if (layoutDirection == LayoutDirection.Rtl) -xPx else xPx
                    IntOffset(
                        x = directedX.toInt(),
                        y = 0
                    )
                }
                .size(thumbSize)
                .shadow(
                    elevation = 6.dp,
                    shape = CircleShape,
                    ambientColor = glowColor.copy(alpha = 0.4f),
                    spotColor = glowColor.copy(alpha = 0.4f)
                )
                .background(
                    brush = Brush.linearGradient(
                        colors = if (isMusic) {
                            listOf(primaryColor, primaryColor.copy(alpha = 0.85f))
                        } else {
                            listOf(tertiaryColor, tertiaryColor.copy(alpha = 0.85f))
                        }
                    ),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            MorphingMusicVideoIcon(
                progress = progress,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

