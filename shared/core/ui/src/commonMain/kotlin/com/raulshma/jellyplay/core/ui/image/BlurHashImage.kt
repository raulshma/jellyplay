package com.raulshma.jellyplay.core.ui.image

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object BlurHashCache {
    private const val MAX_BYTES = 2 * 1024 * 1024

    private class Entry(val bitmap: ImageBitmap, val bytes: Int)

    private val lock = Any()
    private val cache = LinkedHashMap<String, Entry>(8, 0.75f, /* accessOrder = */ true)
    private var totalBytes = 0

    fun get(key: String): ImageBitmap? = synchronized(lock) {
        cache[key]?.bitmap
    }

    fun put(key: String, bitmap: ImageBitmap, width: Int, height: Int) {
        val bytes = width * height * 4
        synchronized(lock) {
            cache[key] = Entry(bitmap, bytes)
            totalBytes += bytes
            while (totalBytes > MAX_BYTES) {
                val eldest = cache.entries.iterator()
                if (!eldest.hasNext()) break
                val (k, v) = eldest.next()
                eldest.remove()
                totalBytes -= v.bytes
                if (k == key) break
            }
        }
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
        BlurHashCache.get(cacheKey)?.let {
            value = it
            return@produceState
        }
        withContext(Dispatchers.Default) {
            val pixels = BlurHashDecoder.decode(blurHash, decodeWidth, decodeHeight)
            if (pixels != null) {
                val bitmap = argbPixelsToImageBitmap(pixels, decodeWidth, decodeHeight)
                BlurHashCache.put(cacheKey, bitmap, decodeWidth, decodeHeight)
                value = bitmap
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
