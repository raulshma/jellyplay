package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * Motion specs used only inside :feature:player:video (skip buttons, gesture
 * feedback pill, edge bars). The shared top/bottom/play-button specs live in
 * :core:ui/player/PlayerMotion.kt.
 */
@Composable
fun playerSkipButtonEnter() = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()) + scaleIn(
    animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
    initialScale = 0.8f,
)

@Composable
fun playerSkipButtonExit() = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) + scaleOut(
    animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
    targetScale = 0.8f,
)

@Composable
fun playerGestureFeedbackEnter() = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()) + scaleIn(
    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
    initialScale = 0.7f,
)

@Composable
fun playerGestureFeedbackExit() = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) + scaleOut(
    animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
    targetScale = 0.8f,
)

@Composable
fun playerEdgeBarEnter() = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()) + scaleIn(
    animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
    initialScale = 0.85f,
)

@Composable
fun playerEdgeBarExit() = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec())
