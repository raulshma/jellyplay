package com.raulshma.jellyplay.core.ui.image

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.raulshma.jellyplay.core.ui.components.AccessOrderLruMap
import com.raulshma.jellyplay.core.ui.components.withUiLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object BlurHashCache {
    private const val MAX_BYTES = 2 * 1024 * 1024

    private class Entry(val bitmap: ImageBitmap, val bytes: Int)

    // Access-order LRU (reads promote to MRU, eldest evicted first) with a
    // byte budget; both ride the cross-platform `AccessOrderLruMap` +
    // `withUiLock` replacements for the JVM-only LinkedHashMap(accessOrder=true)
    // + synchronized idiom this cache used before the wasmJs target.
    private val lock = Any()
    private val cache = AccessOrderLruMap<String, Entry>()
    private var totalBytes = 0

    fun get(key: String): ImageBitmap? = withUiLock(lock) {
        cache[key]?.bitmap
    }

    fun put(key: String, bitmap: ImageBitmap, width: Int, height: Int) {
        val bytes = width * height * 4
        withUiLock(lock) {
            // Replacing an existing key retires its previous bytes first;
            // without this, every re-decode of an already-cached key inflates
            // totalBytes monotonically and triggers premature eviction.
            val previousBytes = cache[key]?.bytes ?: 0
            cache.put(key, Entry(bitmap, bytes))
            totalBytes += bytes - previousBytes
            while (totalBytes > MAX_BYTES) {
                val eldest = cache.removeEldestOrNull() ?: break
                totalBytes -= eldest.second.bytes
                if (eldest.first == key) break
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
