package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun IntroSkipOverlay(
    isVisible: Boolean,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        FilledTonalButton(
            onClick = onSkip,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.padding(end = 16.dp),
        ) {
            Text(
                "Skip Intro",
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}
