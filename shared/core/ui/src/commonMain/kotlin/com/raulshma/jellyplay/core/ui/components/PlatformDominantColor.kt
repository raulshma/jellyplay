package com.raulshma.jellyplay.core.ui.components

import androidx.compose.ui.graphics.Color
import coil3.PlatformContext

/**
 * Platform seam behind [rememberDominantColor]: fetches `imageUrl` through Coil
 * at 64px and classifies a dominant/vibrant swatch from the pixels.
 *
 * Android keeps the original Coil+Palette pipeline verbatim. Desktop returns
 * null until its classifier lands with the desktop shell polish pass (plan
 * §Phase V1/V2); callers already render the fallback color.
 */
internal expect suspend fun extractDominantColor(context: PlatformContext, imageUrl: String): Color?

/** Tiny LRU used by [rememberDominantColor] (replaces android.util.LruCache). */
internal class DominantColorLruCache(private val maxSize: Int) {
    private val lock = Any()
    private val map = LinkedHashMap<String, Color>(16, 0.75f, /* accessOrder = */ true)

    fun get(key: String): Color? = synchronized(lock) { map[key] }

    fun put(key: String, value: Color) {
        synchronized(lock) {
            map[key] = value
            while (map.size > maxSize) {
                val eldest = map.entries.iterator()
                if (!eldest.hasNext()) break
                val k = eldest.next().key
                eldest.remove()
                if (k == key) break
            }
        }
    }
}
