package com.raulshma.jellyplay.feature.book

import androidx.compose.ui.graphics.ImageBitmap
import com.raulshma.jellyplay.core.ui.image.argbPixelsToImageBitmap
import java.awt.image.BufferedImage

/**
 * The book reader's bounded decode + ARGB pixel bridge now live in
 * core:ui's public image seam (`ImageBitmapFactory`); this file keeps only
 * the PDFBox-shaped [BufferedImage] adapter — PDFBox hands the desktop PDF
 * pager `BufferedImage`s, which no other module sees.
 */
internal fun BufferedImage.toImageBitmap(): ImageBitmap {
    val w = width
    val h = height
    val pixels = IntArray(w * h)
    getRGB(0, 0, w, h, pixels, 0, w)
    return argbPixelsToImageBitmap(pixels, w, h)
}
