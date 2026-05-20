package com.raulshma.jellyplay.core.designsystem.theme

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val colorCache = android.util.LruCache<String, ArtworkColors>(100)

@Composable
fun rememberArtworkColors(imageUrl: String?): ArtworkColors? {
    val context = LocalContext.current
    val cached = imageUrl?.let { colorCache.get(it) }
    var artworkColors by remember { mutableStateOf(cached) }
    val loader = coil3.SingletonImageLoader.get(context)

    LaunchedEffect(imageUrl) {
        if (imageUrl.isNullOrBlank()) return@LaunchedEffect
        colorCache.get(imageUrl)?.let {
            artworkColors = it
            return@LaunchedEffect
        }
        withContext(Dispatchers.Default) {
            try {
                val request = ImageRequest.Builder(context)
                    .data(imageUrl)
                    .size(Size(256, 256))
                    .allowHardware(false)
                    .build()
                val result = loader.execute(request)
                if (result is SuccessResult) {
                    val bitmap = (result.image as? coil3.BitmapImage)?.bitmap
                        ?: return@withContext
                    val colors = ArtworkColorExtractor.extractColors(bitmap)
                    colorCache.put(imageUrl, colors)
                    artworkColors = colors
                }
            } catch (_: Exception) {
            }
        }
    }

    return artworkColors
}
