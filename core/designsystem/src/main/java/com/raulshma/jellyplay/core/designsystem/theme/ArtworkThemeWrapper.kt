package com.raulshma.jellyplay.core.designsystem.theme

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ArtworkThemeWrapper(
    imageUrl: String?,
    dynamicTheming: Boolean = true,
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    if (!dynamicTheming || imageUrl.isNullOrBlank()) {
        content()
        return
    }

    val context = LocalContext.current
    var artworkColors by remember { mutableStateOf<ArtworkColors?>(null) }

    LaunchedEffect(imageUrl) {
        withContext(Dispatchers.IO) {
            try {
                val loader = ImageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(imageUrl)
                    .size(Size(256, 256))
                    .allowHardware(false)
                    .build()
                val result = loader.execute(request)
                if (result is SuccessResult) {
                    val bitmap = result.image.toBitmap(256, 256, Bitmap.Config.ARGB_8888)
                    artworkColors = ArtworkColorExtractor.extractColors(bitmap)
                }
            } catch (_: Exception) {
                artworkColors = null
            }
        }
    }

    val colors = artworkColors
    if (colors != null) {
        val colorScheme = ArtworkColorExtractor.generateColorScheme(colors, darkTheme)
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MaterialTheme.typography,
            content = content,
        )
    } else {
        content()
    }
}
