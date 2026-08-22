package com.raulshma.jellyplay.core.ui.animation

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp

@Composable
fun Float.animate(
    animationSpec: AnimationSpec<Float> = MaterialTheme.motionScheme.defaultSpatialSpec(),
    visibilityThreshold: Float = 0.01f,
    label: String = "FloatAnimation",
    finishedListener: ((Float) -> Unit)? = null
): Float = animateFloatAsState(
    targetValue = this,
    animationSpec = animationSpec,
    visibilityThreshold = visibilityThreshold,
    label = label,
    finishedListener = finishedListener
).value

@Composable
fun Dp.animate(
    animationSpec: AnimationSpec<Dp> = MaterialTheme.motionScheme.defaultSpatialSpec(),
    label: String = "DpAnimation",
    finishedListener: ((Dp) -> Unit)? = null
): Dp = animateDpAsState(
    targetValue = this,
    animationSpec = animationSpec,
    label = label,
    finishedListener = finishedListener
).value

@Composable
fun pressScaleValue(isPressed: Boolean, defaultScale: Float = 0.95f): Float {
    val reducedMotion = com.raulshma.jellyplay.core.ui.components.LocalReducedMotion.current
    return if (reducedMotion) 1f else if (isPressed) defaultScale else 1f
}
