package com.raulshma.jellyplay.core.ui.player

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp

/**
 * Motion specs for player chrome (top bar slide-in, bottom bar slide-in,
 * center play button scale-in, button press springs, seek bar tweens).
 * Promoted from :feature:player:video so :feature:player:live reuses them.
 */
@Composable
fun playerTopControlsEnter() = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
    slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { -it }

@Composable
fun playerTopControlsExit() = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
    slideOutVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { -it }

@Composable
fun playerPlayButtonEnter() = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()) + scaleIn(
    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
    initialScale = 0.85f,
)

@Composable
fun playerPlayButtonExit() = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) + scaleOut(
    animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
    targetScale = 0.85f,
)

@Composable
fun playerBottomControlsEnter() = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
    slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it }

@Composable
fun playerBottomControlsExit() = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
    slideOutVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { it }

@Composable
fun playerButtonPressSpec(): AnimationSpec<Float> =
    MaterialTheme.motionScheme.defaultSpatialSpec()

@Composable
fun playerSeekbarDpSpec(): AnimationSpec<Dp> =
    MaterialTheme.motionScheme.fastSpatialSpec()

@Composable
fun playerSeekbarSpec(): AnimationSpec<Float> =
    MaterialTheme.motionScheme.fastSpatialSpec()

@Composable
fun playerCrossfadeSpec(): AnimationSpec<Float> =
    MaterialTheme.motionScheme.defaultSpatialSpec()
