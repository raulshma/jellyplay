package com.raulshma.jellyplay.feature.library

import androidx.compose.ui.graphics.ColorFilter

internal actual fun photoAdjustmentColorFilter(
    brightness: Float,
    contrast: Float,
    saturation: Float,
): ColorFilter {
    val cm = android.graphics.ColorMatrix()
    cm.setSaturation(saturation)
    val ct = (1f - contrast) * 128f
    val b = (brightness - 1f) * 128f
    val contrastBrightness = android.graphics.ColorMatrix(floatArrayOf(
        contrast, 0f, 0f, 0f, b + ct,
        0f, contrast, 0f, 0f, b + ct,
        0f, 0f, contrast, 0f, b + ct,
        0f, 0f, 0f, 1f, 0f,
    ))
    cm.postConcat(contrastBrightness)
    return androidx.compose.ui.graphics.ColorFilter.colorMatrix(
        androidx.compose.ui.graphics.ColorMatrix(cm.array)
    )
}
