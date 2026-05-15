package com.raulshma.jellyplay.core.ui.tv

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

fun Context.isTv(): Boolean =
    packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
        packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK_ONLY)

@Composable
fun isTvDevice(): Boolean = LocalContext.current.isTv()

fun Modifier.tvFocusable(
    interactionSource: MutableInteractionSource? = null,
): Modifier = composed {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val isFocused by source.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (isFocused) 1.05f else 1f, label = "tv_scale")

    val animatedBorderColor = if (isFocused) {
        val infiniteTransition = rememberInfiniteTransition(label = "focus_transition")
        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.5f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(800),
                repeatMode = RepeatMode.Reverse
            ),
            label = "focus_alpha"
        )
        Color.White.copy(alpha = alpha)
    } else {
        Color.Transparent
    }

    val borderColor by animateColorAsState(
        targetValue = animatedBorderColor,
        label = "focus_border_color"
    )

    this
        .onFocusChanged { }
        .focusTarget()
        .focusProperties { canFocus = true }
        .scale(scale)
        .then(
            if (isFocused || borderColor != Color.Transparent) {
                Modifier.border(
                    width = 3.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(8.dp),
                )
            } else Modifier
        )
        .padding(if (isFocused) 4.dp else 0.dp)
}
