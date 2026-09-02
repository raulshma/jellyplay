package com.raulshma.jellyplay.feature.library.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.animation.AnimationTokens
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator

@Composable
fun PhotoGridCard(
    imageUrl: String?,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    blurHash: String? = null,
    isSelected: Boolean = false,
) {
    val isTv = LocalTvMode.current
    val tvFocusState = rememberTvFocusState()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val baseScale by animateFloatAsState(
        targetValue = if (isPressed) AnimationTokens.CardPressScale else 1f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "photoGridScale",
    )
    val scale by animateFloatAsState(
        targetValue = baseScale * tvFocusState.scale,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "photoGridCombinedScale",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .then(tvFocusState.focusModifier)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .tvFocusIndicator(tvFocusState, ShapeCache.smooth8)
            .clip(ShapeCache.smooth8)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )

        if (imageUrl != null) {
            MediaImage(
                url = imageUrl,
                contentDescription = contentDescription,
                blurHash = blurHash,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(ShapeCache.smooth8)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
            )
        }

        if (isPressed) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            )
        }
    }
}
