package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.animation.AnimatedVisibility
import com.raulshma.jellyplay.core.ui.tv.tvFocusable
import androidx.compose.animation.core.tween
import com.raulshma.jellyplay.core.designsystem.theme.AlphaEasing
import com.raulshma.jellyplay.core.designsystem.theme.PointToPointEasing
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.animation.AnimationTokens
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun IntroSkipOverlay(
    isVisible: Boolean,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SkipButtonOverlay(
        isVisible = isVisible,
        text = "Skip Intro",
        onSkip = onSkip,
        modifier = modifier,
    )
}

@Composable
fun CreditsSkipOverlay(
    isVisible: Boolean,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SkipButtonOverlay(
        isVisible = isVisible,
        text = "Skip Credits",
        onSkip = onSkip,
        modifier = modifier,
    )
}

@Composable
private fun SkipButtonOverlay(
    isVisible: Boolean,
    text: String,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(AnimationTokens.QuickDuration, easing = AlphaEasing)) + scaleIn(initialScale = 0.8f, animationSpec = tween(AnimationTokens.QuickDuration, easing = PointToPointEasing)),
        exit = fadeOut(tween(AnimationTokens.DefaultDuration, easing = AlphaEasing)) + scaleOut(targetScale = 0.8f, animationSpec = tween(AnimationTokens.DefaultDuration, easing = PointToPointEasing)),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(ShapeCache.smooth12)
                .background(Color.Black.copy(alpha = 0.85f))
                .border(1.dp, Color.White.copy(alpha = 0.15f), ShapeCache.smooth12)
                .tvFocusable().clickable(onClick = onSkip)
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Icon(
                imageVector = Icons.Default.FastForward,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.padding(end = 8.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            )
        }
    }
}
