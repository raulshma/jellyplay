package com.raulshma.jellyplay.core.ui.image

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorSpace
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo

/**
 * Web pixel path: identical channel expansion and raster spec as the jvmMain
 * actual — skiko publishes the same `org.jetbrains.skia` surface to wasmJs
 * through Compose's browser build, so blurhash rasters render for real
 * instead of degrading.
 */
internal actual fun argbPixelsToImageBitmap(pixels: IntArray, width: Int, height: Int): ImageBitmap {
    val rgba = ByteArray(width * height * 4)
    var o = 0
    for (i in pixels.indices) {
        val p = pixels[i]
        rgba[o++] = (p shr 16).toByte() // R
        rgba[o++] = (p shr 8).toByte()  // G
        rgba[o++] = p.toByte()          // B
        rgba[o++] = (p shr 24).toByte() // A
    }
    val info = ImageInfo(
        width = width,
        height = height,
        colorType = ColorType.RGBA_8888,
        alphaType = ColorAlphaType.UNPREMUL,
        colorSpace = ColorSpace.sRGB,
    )
    return Image.makeRaster(info, rgba, width * 4).toComposeImageBitmap()
}
