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
    // `AccessOrderLruMap` + `withUiLock` stand in for the JVM-only
    // LinkedHashMap(accessOrder=true) + synchronized idiom this class used
    // before the wasmJs target.
    private val lock = Any()
    private val map = AccessOrderLruMap<String, Color>()

    fun get(key: String): Color? = withUiLock(lock) { map[key] }

    fun put(key: String, value: Color) {
        withUiLock(lock) {
            map.put(key, value)
            while (map.size > maxSize) {
                val eldest = map.removeEldestOrNull() ?: break
                if (eldest.first == key) break
            }
        }
    }
}
