package com.raulshma.jellyplay.feature.music.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.ui.animation.AnimationTokens
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache

@Composable
fun AlbumCard(
    name: String,
    artist: String?,
    year: Int?,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    blurHash: String? = null,
) {
    val isTv = LocalTvMode.current
    val tvFocusState = rememberTvFocusState()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Expressive spring-based scale animation
    val baseScale by animateFloatAsState(
        targetValue = if (isPressed) AnimationTokens.CardPressScale else 1f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "albumCardScale",
    )
    val scale by animateFloatAsState(
        targetValue = baseScale * tvFocusState.scale,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "albumCardCombinedScale",
    )
    val brightnessOverlay by animateFloatAsState(
        targetValue = if (isPressed) 0.08f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "albumCardBrightness",
    )

    // Shape morphing animation
    val morphScale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "shapeMorph"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(tvFocusState.focusModifier)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                // Subtle rotation on press for expressive feel
                rotationZ = if (isPressed) -1f else 0f
            }
            .tvFocusIndicator(tvFocusState, ShapeCache.smooth8)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            contentAlignment = Alignment.CenterStart,
        ) {
            val isPeekExpanded = tvFocusState.isFocused || isPressed

            if (imageUrl != null) {
                VinylRecordPeek(
                    isHoveredOrFocused = isPeekExpanded,
                    size = 114.dp,
                    labelColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 4.dp)
                )
            }

            // Album art with shape morphing
            Box(
                modifier = Modifier
                    .size(114.dp)
                    .graphicsLayer {
                        // Shape morphing effect
                        scaleX = morphScale
                        scaleY = morphScale
                    }
                    .clip(ShapeCache.smooth12)
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
                            .fillMaxSize()
                            .clip(ShapeCache.smooth12),
                    )
                } else {
                    Text(
                        text = name.take(2).uppercase(),
                        style = MaterialTheme.typography.headlineMedium,
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
        }

        // Track name with expressive typography
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(top = 8.dp)
                .enableMarqueeOnFocus(focused = tvFocusState.isFocused),
        )

        // Artist and year with expressive styling
        if (artist != null || year != null) {
            Text(
                text = listOfNotNull(artist, year?.toString()).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
