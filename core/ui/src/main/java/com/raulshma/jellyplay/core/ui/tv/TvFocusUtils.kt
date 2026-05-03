package com.raulshma.jellyplay.core.ui.tv

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.animation.core.animateFloatAsState
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

    this
        .onFocusChanged { }
        .focusTarget()
        .focusProperties { canFocus = true }
        .scale(scale)
        .then(
            if (isFocused) {
                Modifier.border(
                    width = 3.dp,
                    color = Color.White,
                    shape = RoundedCornerShape(8.dp),
                )
            } else Modifier
        )
        .padding(if (isFocused) 4.dp else 0.dp)
}
