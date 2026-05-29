package com.raulshma.jellyplay.feature.music.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

@Composable
fun rememberMorphingShape(
    isPressed: Boolean,
    normalShape: androidx.compose.ui.graphics.Shape = MaterialTheme.shapes.medium,
    pressedShape: androidx.compose.ui.graphics.Shape = MaterialTheme.shapes.small,
): androidx.compose.ui.graphics.Shape {
    val morphProgress by animateFloatAsState(
        targetValue = if (isPressed) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "shapeMorph"
    )
    
    return if (morphProgress > 0.5f) pressedShape else normalShape
}

@Composable
fun ExpressivePressScale(
    isPressed: Boolean,
    normalScale: Float = 1f,
    pressedScale: Float = 0.95f,
    content: @Composable (Float) -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else normalScale,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "pressScale"
    )
    
    content(scale)
}

@Composable
fun ExpressiveCardOverlay(
    isVisible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        modifier = modifier.fillMaxSize(),
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = 0.08f
                }
        )
    }
}

@Composable
fun StaggeredAnimatedItem(
    index: Int,
    isVisible: Boolean,
    content: @Composable () -> Unit
) {
    val delay = index * 40L
    
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessLow
            )
        ) + scaleIn(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        ),
        exit = fadeOut() + scaleOut()
    ) {
        content()
    }
}

@Composable
fun ExpressiveListItem(
    index: Int,
    content: @Composable () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "listItemScale"
    )
    
    Box(
        modifier = Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    ) {
        content()
    }
}
