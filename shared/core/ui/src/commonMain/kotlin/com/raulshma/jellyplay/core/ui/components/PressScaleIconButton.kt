package com.raulshma.jellyplay.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.ui.animation.AnimationTokens
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PressScaleIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    pressScale: Float = AnimationTokens.ButtonPressScale,
    containerColor: Color = Color.Transparent,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    shapes: androidx.compose.material3.IconButtonShapes = IconButtonDefaults.shapes(),
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val motionScheme = MaterialTheme.motionScheme
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = motionScheme.fastSpatialSpec(),
        label = "pressScaleIconButton",
    )
    val focusState = rememberTvFocusState(focusedScale = 1.12f)

    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(size)
            .scale(scale)
            .then(focusState.focusModifier)
            .tvFocusIndicator(focusState, CircleShape),
        enabled = enabled,
        shapes = shapes,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
    ) {
        content()
    }
}
