package com.raulshma.jellyplay.core.ui.components

import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import coil3.BitmapImage
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size as CoilSize

/** Original Coil+Palette pipeline, byte-for-byte (plan §V1: replace androidx.palette). */
internal actual suspend fun extractDominantColor(context: PlatformContext, imageUrl: String): Color? {
    val loader = SingletonImageLoader.get(context)
    val request = ImageRequest.Builder(context)
        .data(imageUrl)
        .size(CoilSize(64, 64))
        .allowHardware(false)
        // Isolate the Palette decode in its own cache slot. Sharing the display
        // URL's key made this 64px result evict the larger bitmap a live
        // AsyncImage painter was still drawing; the BitmapPool then recycled it,
        // crashing onDraw with "Canvas: trying to use a recycled bitmap".
        .memoryCacheKey("${imageUrl}#palette-dominant")
        .build()
    val result = loader.execute(request)
    if (result !is SuccessResult) return null
    val bitmap = (result.image as? BitmapImage)?.bitmap ?: return null
    val palette = Palette.from(bitmap).maximumColorCount(8).generate()
    val extracted: Int? = palette.vibrantSwatch?.rgb
        ?: palette.dominantSwatch?.rgb
        ?: palette.mutedSwatch?.rgb
    return extracted?.let(::Color)
}
