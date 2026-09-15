package com.raulshma.jellyplay.feature.book

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.roundToInt
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorSpace
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo

internal actual fun decodeImageBytes(bytes: ByteArray, maxEdgePx: Int): ImageBitmap? =
    runCatching {
        // ImageIO has no decode-time bounds pass, so an oversized scan lands
        // at full size once and is immediately downscaled off the heap via a
        // Graphics2D draw.
        val decoded = ImageIO.read(ByteArrayInputStream(bytes)) ?: return@runCatching null
        val longest = maxOf(decoded.width, decoded.height)
        if (longest <= maxEdgePx) return@runCatching decoded.toImageBitmap()
        val scale = maxEdgePx.toDouble() / longest
        val w = max(1, (decoded.width * scale).roundToInt())
        val h = max(1, (decoded.height * scale).roundToInt())
        val scaled = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g: Graphics2D = scaled.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g.drawImage(decoded, 0, 0, w, h, null)
        } finally {
            g.dispose()
        }
        scaled.toImageBitmap()
    }.getOrNull()

/** ARGB-int → ImageBitmap bridge for any decoded [BufferedImage] (decode seam + PDF pager). */
internal fun BufferedImage.toImageBitmap(): ImageBitmap {
    val w = width
    val h = height
    val pixels = IntArray(w * h)
    getRGB(0, 0, w, h, pixels, 0, w)
    return argbPixelsToImageBitmap(pixels, w, h)
}

/**
 * Desktop pixel path (core:ui's BlurHash bridge shape): expand the ARGB ints
 * into an explicit RGBA_8888 raster — deterministic channel order, since
 * `makeN32` layouts vary by platform.
 */
internal actual fun argbPixelsToImageBitmap(pixels: IntArray, width: Int, height: Int): ImageBitmap {
    val rgba = ByteArray(width * height * 4)
    var o = 0
    for (p in pixels) {
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
