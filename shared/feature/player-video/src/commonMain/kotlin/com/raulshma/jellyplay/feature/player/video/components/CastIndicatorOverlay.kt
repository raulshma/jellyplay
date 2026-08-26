package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Cast
import com.raulshma.jellyplay.core.designsystem.theme.CastColors
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.components.LocalReducedMotion
import com.raulshma.jellyplay.feature.player.video.generated.resources.Res
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_cast_connecting
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_casting



@Composable
fun CastIndicatorOverlay(
    isConnecting: Boolean,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = LocalReducedMotion.current
    // Cast connection pulse. Freeze at full alpha in performance/reduced-motion mode
    // to drop the redraw coroutine while the overlay is on screen.
    val pulseAlpha = if (!reducedMotion) {
        rememberInfiniteTransition(label = "castPulse").animateFloat(
            initialValue = 0.5f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "castAlpha",
        ).value
    } else {
        1f
    }

    Row(
        modifier = modifier
            .clip(ShapeCache.smoothPill)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (isConnecting) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(Res.string.player_video_cast_connecting),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.3.sp,
            )
        } else {
            Icon(
                imageVector = Tabler.Outline.Cast,
                contentDescription = null,
                tint = CastColors.connected,
                modifier = Modifier
                    .size(14.dp)
                    .graphicsLayer { alpha = pulseAlpha },
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .graphicsLayer {
                        scaleX = pulseAlpha * 0.3f + 0.7f
                        scaleY = pulseAlpha * 0.3f + 0.7f
                        alpha = pulseAlpha
                    }
                    .background(CastColors.indicator, shape = CircleShape),
            )
            Text(
                text = stringResource(Res.string.player_video_casting),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.3.sp,
            )
        }
    }
}
