package com.raulshma.jellyplay.core.ui.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

actual fun argbPixelsToImageBitmap(pixels: IntArray, width: Int, height: Int): ImageBitmap =
    Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()

actual fun decodeImageBytes(bytes: ByteArray, maxEdgePx: Int): ImageBitmap? {
    // Bounds pass first: inSampleSize is the only decode-time lever, so the
    // edge cap has to be decided before the pixels ever hit the heap.
    // inSampleSize is power-of-two only, so the decoded edge lands AT OR
    // BELOW the cap (a 6000 px scan at a 4000 cap decodes at 3000) — hitting
    // the cap exactly would require a full-res intermediate, the allocation
    // this bounds pass exists to avoid.
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val longest = maxOf(bounds.outWidth, bounds.outHeight)
    if (longest <= 0) return null
    var sampleSize = 1
    while (longest / sampleSize > maxEdgePx) sampleSize = sampleSize shl 1
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
}
