package com.raulshma.jellyplay.core.ui.animation

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.IntSize

fun Modifier.animateContentSizeNoClip(
    animationSpec: FiniteAnimationSpec<IntSize> = spring(
        stiffness = Spring.StiffnessMediumLow
    ),
    alignment: Alignment = Alignment.TopCenter,
    isClipped: Boolean = false,
    finishedListener: ((initialValue: IntSize, targetValue: IntSize) -> Unit)? = null,
): Modifier = this
    .then(if (isClipped) Modifier.clipToBounds() else Modifier)
    .then(
        animateContentSize(
            animationSpec = animationSpec,
            alignment = alignment,
            finishedListener = finishedListener,
        )
    )
