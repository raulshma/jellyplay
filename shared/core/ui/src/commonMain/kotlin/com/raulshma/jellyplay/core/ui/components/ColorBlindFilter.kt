package com.raulshma.jellyplay.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import com.raulshma.jellyplay.core.model.ColorBlindMode

private val protanopiaMatrix = ColorMatrix(
    floatArrayOf(
        0.567f, 0.433f, 0.000f, 0f, 0f,
        0.558f, 0.442f, 0.000f, 0f, 0f,
        0.000f, 0.242f, 0.758f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )
)

private val deuteranopiaMatrix = ColorMatrix(
    floatArrayOf(
        0.625f, 0.375f, 0.000f, 0f, 0f,
        0.700f, 0.300f, 0.000f, 0f, 0f,
        0.000f, 0.300f, 0.700f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )
)

private val tritanopiaMatrix = ColorMatrix(
    floatArrayOf(
        0.950f, 0.050f, 0.000f, 0f, 0f,
        0.000f, 0.433f, 0.567f, 0f, 0f,
        0.000f, 0.475f, 0.525f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )
)

fun Modifier.colorBlindFilter(mode: ColorBlindMode): Modifier = when (mode) {
    ColorBlindMode.NONE -> this
    ColorBlindMode.PROTANOPIA -> this.graphicsLayer {
        colorFilter = ColorFilter.colorMatrix(protanopiaMatrix)
    }
    ColorBlindMode.DEUTERANOPIA -> this.graphicsLayer {
        colorFilter = ColorFilter.colorMatrix(deuteranopiaMatrix)
    }
    ColorBlindMode.TRITANOPIA -> this.graphicsLayer {
        colorFilter = ColorFilter.colorMatrix(tritanopiaMatrix)
    }
}
