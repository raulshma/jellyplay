package com.raulshma.jellyplay.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * AnimatedVisibility wrapper for a whole section: fade + slight rise on
 * enter, reverse on exit. Not staggered — for the delay-indexed variant see
 * [StaggeredSection]; the distinct name keeps the two apart.
 */
@Composable
fun AnimatedSectionEntrance(
    visible: Boolean,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(
            animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        ) + slideInVertically(
            initialOffsetY = { it / 14 },
            animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        ),
        exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) + slideOutVertically(
            targetOffsetY = { -it / 24 },
            animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        ),
    ) {
        content()
    }
}

@Composable
fun AnimatedEntrance(
    visible: Boolean,
    delayMillis: Int = 0,
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                slideInVertically(
                    initialOffsetY = { it / 10 },
                    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                ),
        exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) + slideOutVertically(
            targetOffsetY = { it / 10 },
            animationSpec = MaterialTheme.motionScheme.fastSpatialSpec()
        ),
        content = content,
    )
}

@Composable
fun AnimatedScaleEntrance(
    visible: Boolean,
    delayMillis: Int = 0,
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                scaleIn(
                    initialScale = 0.92f,
                    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                ),
        exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                scaleOut(
                    targetScale = 0.92f,
                    animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                ),
        content = content,
    )
}
