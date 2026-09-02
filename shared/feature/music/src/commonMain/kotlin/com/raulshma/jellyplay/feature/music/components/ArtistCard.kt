package com.raulshma.jellyplay.feature.music.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.enableMarqueeOnFocus
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.ui.animation.AnimationTokens
import com.raulshma.jellyplay.core.ui.components.LocalReducedMotion
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.designsystem.theme.FancyTransitionEasing

@Composable
fun ArtistCard(
    name: String,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    blurHash: String? = null,
) {
    val isTv = LocalTvMode.current
    val reducedMotion = LocalReducedMotion.current
    val tvFocusState = rememberTvFocusState(focusedScale = 1.1f)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Expressive spring-based scale animation
    val baseScale by animateFloatAsState(
        targetValue = if (isPressed) AnimationTokens.CardPressScale else 1f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "artistCardScale",
    )
    val scale by animateFloatAsState(
        targetValue = baseScale * tvFocusState.scale,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "artistCardCombinedScale",
    )
    val brightnessOverlay by animateFloatAsState(
        targetValue = if (isPressed) 0.08f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "artistCardBrightness",
    )

    // Shape morphing animation for the ring
    val ringMorphScale by animateFloatAsState(
        targetValue = if (isPressed) 1.05f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "ringMorph"
    )

    // The ring runs two infinite animations (rotation + pulse). On low-end
    // devices these drive a continuous redraw coroutine per card, and artist
    // grids show dozens at once — collapse to static values in performance mode.
    val ringRotation: Float
    val ringPulse: Float
    if (!reducedMotion) {
        val ringTransition = rememberInfiniteTransition(label = "artist_ring")
        ringRotation = ringTransition.animateFloat(
            initialValue = 0f,
            targetValue = if (isPressed) 360f else 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(6000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "rotation"
        ).value
        ringPulse = ringTransition.animateFloat(
            initialValue = 2.dp.value,
            targetValue = 3.5.dp.value,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = FancyTransitionEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse"
        ).value
    } else {
        ringRotation = if (isPressed) 180f else 0f
        ringPulse = 2.dp.value
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(tvFocusState.focusModifier)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                // Subtle rotation on press for expressive feel
                rotationZ = if (isPressed) -2f else 0f
            }
            .tvFocusIndicator(tvFocusState, CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val primaryColor = MaterialTheme.colorScheme.primary
        val secondaryColor = MaterialTheme.colorScheme.secondary
        val tertiaryColor = MaterialTheme.colorScheme.tertiary

        // The ring runs two infinite animations (rotation + pulse) while
        // pressed, redrawing the sweep-gradient ring ~60fps. Build the brush
        // once per palette change instead of allocating a new List<Color> +
        // Brush.sweepGradient on every draw frame. The rotation is applied
        // per-frame via rotate(...) around this stable brush.
        val ringBrush = remember(primaryColor, secondaryColor, tertiaryColor) {
            Brush.sweepGradient(
                colors = listOf(primaryColor, secondaryColor, tertiaryColor, primaryColor)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .padding(6.dp)
                .graphicsLayer {
                    // Shape morphing for the ring
                    scaleX = ringMorphScale
                    scaleY = ringMorphScale
                }
                .drawBehind {
                    rotate(ringRotation) {
                        drawCircle(
                            brush = ringBrush,
                            radius = size.minDimension / 2f + 4.dp.toPx(),
                            style = Stroke(width = ringPulse.dp.toPx())
                        )
                    }
                }
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (imageUrl != null) {
                MediaImage(
                    url = imageUrl,
                    contentDescription = name,
                    blurHash = blurHash,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(CircleShape),
                )
            } else {
                Text(
                    text = name.take(1).uppercase(),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Expressive brightness overlay with animated visibility
            if (isPressed) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                )
            }
        }

        // Artist name with expressive typography
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(top = 8.dp)
                .enableMarqueeOnFocus(focused = tvFocusState.isFocused),
        )
    }
}
