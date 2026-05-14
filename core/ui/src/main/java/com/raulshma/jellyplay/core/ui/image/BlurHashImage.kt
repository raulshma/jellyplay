package com.raulshma.jellyplay.core.ui.image

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.ref.SoftReference

internal object BlurHashCache {
    private val cache = android.util.LruCache<String, SoftReference<Bitmap>>(200)

    fun get(key: String): Bitmap? {
        val ref = cache.get(key) ?: return null
        val bitmap = ref.get()
        if (bitmap == null || bitmap.isRecycled) {
            cache.remove(key)
            return null
        }
        return bitmap
    }

    fun put(key: String, bitmap: Bitmap) {
        cache.put(key, SoftReference(bitmap))
    }
}

@Composable
internal fun BlurHashImage(
    blurHash: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    decodeWidth: Int = 32,
    decodeHeight: Int = 32,
) {
    val density = LocalDensity.current
    val bitmapState = produceState<ImageBitmap?>(null, blurHash, decodeWidth, decodeHeight) {
        val cached = BlurHashCache.get(blurHash)
        if (cached != null && !cached.isRecycled) {
            value = cached.asImageBitmap()
            return@produceState
        }
        withContext(Dispatchers.Default) {
            val bitmap = BlurHashDecoder.decode(blurHash, decodeWidth, decodeHeight)
            if (bitmap != null) {
                BlurHashCache.put(blurHash, bitmap)
                value = bitmap.asImageBitmap()
            }
        }
    }

    val bitmap = bitmapState.value
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = modifier.fillMaxSize(),
            contentScale = contentScale,
        )
    }
}
