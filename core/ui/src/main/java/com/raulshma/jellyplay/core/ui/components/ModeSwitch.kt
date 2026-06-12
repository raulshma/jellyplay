package com.raulshma.jellyplay.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.HomeMode
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator

/**
 * A private helper for linear interpolation of floats.
 */
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
    Canvas(modifier = modifier) {
        val W = size.width
        val H = size.height

        // --- 1. Note Heads / Film Reels ---
        // Left Circle (Note Head / Top Left Film Reel)
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

        // Right Circle (Note Head / Top Right Film Reel)
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

        // --- 2. Stems / Camera Body ---
        // Left Stem / Left half of camera body
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

        // Right Stem / Right half of camera body
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

        // --- 3. Connecting Beam / Camera Lens ---
        // A 4-point polygon connecting the two stems in Music mode,
        // and collapsing into a triangle in Video mode.
        val path = Path().apply {
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

    // Smooth bouncy spring transition progress (0.0 = Music, 1.0 = Video)
    val progress by animateFloatAsState(
        targetValue = if (isMusic) 0f else 1f,
        animationSpec = spring(
            dampingRatio = 0.76f, // premium spring behavior
            stiffness = 280f      // natural speed
        ),
        label = "mode_switch_progress"
    )

    val isLightTheme = MaterialTheme.colorScheme.background.let { bg ->
        (bg.red * 0.299f + bg.green * 0.587f + bg.blue * 0.114f) > 0.5f
    }

    // Sleek HSL-aligned theme colors
    val primaryColor = MaterialTheme.colorScheme.primary
    val trackBgColor = if (isLightTheme) {
        MaterialTheme.colorScheme.surfaceContainerHighest
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)
    }
    
    // Dynamic background glow/shadow tint
    val glowColor = if (isMusic) primaryColor else Color(0xFFFFB74D)

    val interactionSource = remember { MutableInteractionSource() }
    val isTv = LocalTvMode.current
    val tvFocusState = rememberTvFocusState(focusedScale = 1.08f)

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
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    onModeChange(if (isMusic) HomeMode.VIDEO else HomeMode.MUSIC)
                }
            )
            .padding(4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        // Underlay static background icons that reveal when the thumb slides away
        Box(modifier = Modifier.fillMaxSize()) {
            Icon(
                imageVector = Tabler.Outline.Music,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isLightTheme) 0.45f else 0.25f),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 10.dp)
                    .size(16.dp)
            )
            Icon(
                imageVector = Tabler.Outline.Movie,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isLightTheme) 0.45f else 0.25f),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 10.dp)
                    .size(16.dp)
            )
        }

        // Bouncy Sliding Selection Thumb
        Box(
            modifier = Modifier
                .offset {
                    val maxOffset = (width - thumbSize - 8.dp) // 76.dp - 28.dp - 8.dp = 40.dp
                    IntOffset(
                        x = (progress * maxOffset.toPx()).toInt(),
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
                            listOf(Color(0xFFE65100), Color(0xFFFFB74D)) // Vibrant theater amber gradient
                        }
                    ),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            MorphingMusicVideoIcon(
                progress = progress,
                color = Color.White,
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

