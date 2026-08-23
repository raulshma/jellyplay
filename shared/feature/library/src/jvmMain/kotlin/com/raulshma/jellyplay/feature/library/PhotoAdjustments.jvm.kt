package com.raulshma.jellyplay.feature.library

import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix

internal actual fun photoAdjustmentColorFilter(
    brightness: Float,
    contrast: Float,
    saturation: Float,
): ColorFilter {
    val cm = ColorMatrix()
    cm.setToSaturation(saturation)
    val ct = (1f - contrast) * 128f
    val b = (brightness - 1f) * 128f
    val contrastBrightness = ColorMatrix(floatArrayOf(
        contrast, 0f, 0f, 0f, b + ct,
        0f, contrast, 0f, 0f, b + ct,
        0f, 0f, contrast, 0f, b + ct,
        0f, 0f, 0f, 1f, 0f,
    ))
    cm.timesAssign(contrastBrightness)
    return ColorFilter.colorMatrix(cm)
}
