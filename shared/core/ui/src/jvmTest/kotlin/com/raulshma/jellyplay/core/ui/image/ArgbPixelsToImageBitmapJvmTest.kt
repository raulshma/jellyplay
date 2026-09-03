package com.raulshma.jellyplay.core.ui.image

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Pins the desktop (Skia) actual of `argbPixelsToImageBitmap`: packed-ARGB
 * pixels become an [ImageBitmap] with the requested dimensions and the exact
 * pixel colors (Skia natives are on the jvmTest classpath via
 * compose.desktop.currentOs — same lane as BlurHashCacheAccountingTest).
 *
 * The wasm/android actuals need their platform runtimes and are not covered
 * here; the common contract pinned is dimension + pixel fidelity, which every
 * actual must satisfy.
 */
class ArgbPixelsToImageBitmapJvmTest {

    @Test
    fun producesBitmapWithRequestedDimensions() {
        val width = 7
        val height = 5
        val pixels = IntArray(width * height) { 0xFF3366CC.toInt() }

        val bitmap = argbPixelsToImageBitmap(pixels, width, height)

        assertNotNull(bitmap)
        assertEquals(width, bitmap.width)
        assertEquals(height, bitmap.height)
    }

    @Test
    fun preservesPixelColors() {
        val red = 0xFFFF0000.toInt()
        val green = 0xFF00FF00.toInt()
        val pixels = intArrayOf(red, green, green, red)

        val bitmap = argbPixelsToImageBitmap(pixels, 2, 2)

        val pixelMap = bitmap.toPixelMap()
        assertEquals(1f, pixelMap[0, 0].red, "top-left must keep the packed ARGB red channel")
        assertEquals(0f, pixelMap[0, 0].green)
        assertEquals(0f, pixelMap[1, 0].red, "top-right must be green")
        assertEquals(1f, pixelMap[1, 0].green)
        assertEquals(0f, pixelMap[0, 1].red, "bottom-left must be green")
        assertEquals(1f, pixelMap[0, 1].green)
        assertEquals(1f, pixelMap[1, 1].red, "bottom-right must be red")
    }

    @Test
    fun squareBitmap_roundTripThroughFactoryHelper() {
        // The production call shape (BlurHash decode at 32x32).
        val pixels = IntArray(32 * 32) { 0xFF804020.toInt() }

        val bitmap: ImageBitmap = argbPixelsToImageBitmap(pixels, 32, 32)

        assertEquals(32, bitmap.width)
        assertEquals(32, bitmap.height)
    }
}
