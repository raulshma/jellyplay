package com.raulshma.jellyplay.core.ui.player

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
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
 *
 * The chrome-transition pair carries an [animated] flag (default true) so the
 * caller can turn the overlay animations off without a pref read seam here —
 * core:ui stays a pure parameter function of the flag (the player passes its
 * pref down). Off, the fade/slide/scale legs snap instead of tweening: the
 * composable surfaces still appear/disappear on the same edges, instantly.
 */
@Composable
fun playerTopControlsEnter(animated: Boolean = true) = fadeIn(
    if (animated) MaterialTheme.motionScheme.defaultEffectsSpec() else snap(),
) + slideInVertically(
    if (animated) MaterialTheme.motionScheme.defaultSpatialSpec() else snap(),
) { -it }

@Composable
fun playerTopControlsExit(animated: Boolean = true) = fadeOut(
    if (animated) MaterialTheme.motionScheme.fastEffectsSpec() else snap(),
) + slideOutVertically(
    if (animated) MaterialTheme.motionScheme.fastSpatialSpec() else snap(),
) { -it }

@Composable
fun playerPlayButtonEnter(animated: Boolean = true) = fadeIn(
    if (animated) MaterialTheme.motionScheme.fastEffectsSpec() else snap(),
) + scaleIn(
    animationSpec = if (animated) MaterialTheme.motionScheme.defaultSpatialSpec() else snap(),
    initialScale = 0.85f,
)

@Composable
fun playerPlayButtonExit(animated: Boolean = true) = fadeOut(
    if (animated) MaterialTheme.motionScheme.fastEffectsSpec() else snap(),
) + scaleOut(
    animationSpec = if (animated) MaterialTheme.motionScheme.fastSpatialSpec() else snap(),
    targetScale = 0.85f,
)

@Composable
fun playerBottomControlsEnter(animated: Boolean = true) = fadeIn(
    if (animated) MaterialTheme.motionScheme.defaultEffectsSpec() else snap(),
) + slideInVertically(
    if (animated) MaterialTheme.motionScheme.defaultSpatialSpec() else snap(),
) { it }

@Composable
fun playerBottomControlsExit(animated: Boolean = true) = fadeOut(
    if (animated) MaterialTheme.motionScheme.fastEffectsSpec() else snap(),
) + slideOutVertically(
    if (animated) MaterialTheme.motionScheme.fastSpatialSpec() else snap(),
) { it }

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
