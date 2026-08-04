package com.raulshma.jellyplay.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator

/**
 * The shared visual container behind every expressive chip/pill in the app
 * (filter-sheet option chips, the pinned filter row, the library action chips,
 * the home rows' "See All" pill): press scale + shape morph + full D-pad focus
 * support (border + breathing glow). Callers only pick the container color,
 * force the "active" highlight morph when they represent a selected/active
 * state, and fill in the content — a [Row] with 6.dp spacing is provided.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveChipContainer(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color,
    forceActive: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val focusState = rememberTvFocusState(focusedScale = 1.05f)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val baseScale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "expressiveChipPressedScale",
    )
    val scale = baseScale * focusState.scale
    val shapeMorphProgress by animateFloatAsState(
        targetValue = if (isPressed || forceActive || focusState.isFocused) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "expressiveChipShapeMorph",
    )
    val shape = remember(shapeMorphProgress) {
        if (shapeMorphProgress > 0.5f) ShapeCache.smooth20 else ShapeCache.smooth16
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(containerColor)
            .then(focusState.focusModifier)
            .tvFocusIndicator(focusState, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(contentPadding),
        content = content,
    )
}
