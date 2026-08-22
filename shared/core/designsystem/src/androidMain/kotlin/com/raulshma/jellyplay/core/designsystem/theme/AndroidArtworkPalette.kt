package com.raulshma.jellyplay.core.designsystem.theme

import android.graphics.Bitmap
import androidx.palette.graphics.Palette
import androidx.compose.ui.graphics.Color

/**
 * Android swatch extraction (Palette). The pure color-scheme math lives in the
 * common [ArtworkColors]/[ArtworkColorExtractor]; only the pixel half is here.
 */
object AndroidArtworkPalette {
    fun extractColors(bitmap: Bitmap): ArtworkColors {
        val scaled = scaleForPalette(bitmap)
        val palette = Palette.from(scaled)
            .maximumColorCount(8)
            .generate()
        if (scaled !== bitmap) scaled.recycle()

        return ArtworkColors(
            vibrant = palette.vibrantSwatch?.toComposeColor(),
            darkVibrant = palette.darkVibrantSwatch?.toComposeColor(),
            lightVibrant = palette.lightVibrantSwatch?.toComposeColor(),
            muted = palette.mutedSwatch?.toComposeColor(),
            darkMuted = palette.darkMutedSwatch?.toComposeColor(),
            lightMuted = palette.lightMutedSwatch?.toComposeColor(),
            dominant = palette.dominantSwatch?.toComposeColor(),
        )
    }

    private fun Palette.Swatch.toComposeColor(): Color = Color(rgb)

    private fun scaleForPalette(bitmap: Bitmap): Bitmap {
        val maxDim = 128
        if (bitmap.width <= maxDim && bitmap.height <= maxDim) return bitmap
        val scale = maxDim.toFloat() / maxOf(bitmap.width, bitmap.height)
        val w = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val h = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }
}
