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
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
internal object BlurHashCache {
    private val cache = object : android.util.LruCache<String, Bitmap>(2 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    fun get(key: String): Bitmap? {
        val bitmap = cache.get(key) ?: return null
        if (bitmap.isRecycled) {
            cache.remove(key)
            return null
        }
        return bitmap
    }

    fun put(key: String, bitmap: Bitmap) {
        cache.put(key, bitmap)
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
    val bitmapState = produceState<ImageBitmap?>(null, blurHash, decodeWidth, decodeHeight) {
        // Include the decode dimensions in the cache key: the same blurHash is
        // decoded at multiple sizes (MediaImage uses 32, BlurHashBackdrop 48),
        // so a hash-only key would collide and hand a caller a bitmap decoded
        // for a different size, stretching it under ContentScale.
        val cacheKey = "$blurHash ${decodeWidth}x$decodeHeight"
        val cached = BlurHashCache.get(cacheKey)
        if (cached != null && !cached.isRecycled) {
            value = cached.asImageBitmap()
            return@produceState
        }
        withContext(Dispatchers.Default) {
            val bitmap = BlurHashDecoder.decode(blurHash, decodeWidth, decodeHeight)
            if (bitmap != null) {
                BlurHashCache.put(cacheKey, bitmap)
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
