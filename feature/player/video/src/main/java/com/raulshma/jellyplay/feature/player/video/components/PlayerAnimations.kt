package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.unit.Dp
import com.raulshma.jellyplay.core.designsystem.theme.AlphaEasing
import com.raulshma.jellyplay.core.designsystem.theme.FancyTransitionEasing
import com.raulshma.jellyplay.core.designsystem.theme.FastInvokeEasing
import com.raulshma.jellyplay.core.designsystem.theme.PointToPointEasing

object PlayerAnimations {

    val topControlsEnter = fadeIn(tween(350, easing = FancyTransitionEasing)) +
        slideInVertically(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)) { -it }
    val topControlsExit = fadeOut(tween(250, easing = FastInvokeEasing)) +
        slideOutVertically(spring(Spring.DampingRatioLowBouncy, Spring.StiffnessHigh)) { -it }

    val playButtonEnter = fadeIn(tween(200)) + scaleIn(
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        initialScale = 0.85f,
    )
    val playButtonExit = fadeOut(tween(150)) + scaleOut(
        animationSpec = tween(200, easing = FastInvokeEasing),
        targetScale = 0.85f,
    )

    val bottomControlsEnter = fadeIn(tween(300, easing = AlphaEasing)) +
        slideInVertically(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)) { it }
    val bottomControlsExit = fadeOut(tween(250, easing = AlphaEasing)) +
        slideOutVertically(spring(Spring.DampingRatioLowBouncy, Spring.StiffnessHigh)) { it }

    val skipButtonEnter = fadeIn(tween(200)) + scaleIn(
        animationSpec = spring(Spring.DampingRatioLowBouncy, Spring.StiffnessHigh),
        initialScale = 0.8f,
    )
    val skipButtonExit = fadeOut(tween(200, easing = AlphaEasing)) + scaleOut(
        animationSpec = tween(200, easing = PointToPointEasing),
        targetScale = 0.8f,
    )

    val gestureFeedbackEnter = fadeIn(tween(100)) + scaleIn(
        animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium),
        initialScale = 0.7f,
    )
    val gestureFeedbackExit = fadeOut(tween(200, easing = AlphaEasing)) + scaleOut(
        animationSpec = tween(200, easing = PointToPointEasing),
        targetScale = 0.8f,
    )

    val edgeBarEnter = fadeIn(tween(80)) + scaleIn(
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessHigh),
        initialScale = 0.85f,
    )
    val edgeBarExit = fadeOut(tween(150, easing = AlphaEasing))

    val buttonPressSpec: AnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium,
    )

    val seekbarDpSpec: AnimationSpec<Dp> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessHigh,
    )

    val seekbarSpec: AnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessHigh,
    )

    val crossfadeSpec: AnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium,
    )
}
