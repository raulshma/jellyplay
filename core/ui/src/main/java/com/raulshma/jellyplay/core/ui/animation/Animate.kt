package com.raulshma.jellyplay.core.ui.animation

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp

@Composable
fun Float.animate(
    animationSpec: AnimationSpec<Float> = spring(),
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
    animationSpec: AnimationSpec<Dp> = spring(visibilityThreshold = Dp(0.5f)),
    label: String = "DpAnimation",
    finishedListener: ((Dp) -> Unit)? = null
): Dp = animateDpAsState(
    targetValue = this,
    animationSpec = animationSpec,
    label = label,
    finishedListener = finishedListener
).value
