package com.raulshma.jellyplay.feature.player.video

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the pure HSV pair relocated out of SubtitleStyleControls' composable
 * file (android.graphics.Color's math, commonMain twin): exact anchors, the
 * hue wraparound (h < 0 → +360), the S=0/V=0 guards, the 255-alpha contract,
 * and channel-tolerant (±1, float truncation) round-trips.
 */
class SubtitleColorMathTest {

    @Test
    fun `colorToHsv primaries are exact`() {
        assertEquals(0f, subtitleColorToHsv(0xFFFF0000.toInt())[0])
        assertEquals(120f, subtitleColorToHsv(0xFF00FF00.toInt())[0])
        assertEquals(240f, subtitleColorToHsv(0xFF0000FF.toInt())[0])
        intArrayOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt()).forEach { color ->
            val hsv = subtitleColorToHsv(color)
            assertEquals(1f, hsv[1])
            assertEquals(1f, hsv[2])
        }
    }

    @Test
    fun `hue wraps around into 0 until 360`() {
        // Magenta: r is max with g < b, so the raw sector value is negative
        // (60 * -1 = -60) and must fold to 300.
        assertEquals(300f, subtitleColorToHsv(0xFFFF00FF.toInt())[0])
        assertEquals(60f, subtitleColorToHsv(0xFFFFFF00.toInt())[0])
        // Every hue lands in [0, 360).
        for (channel in 0..255 step 17) {
            val color = (0xFF shl 24) or (channel shl 16) or ((255 - channel) shl 8) or (channel / 2)
            val hue = subtitleColorToHsv(color)[0]
            assertTrue(hue in 0f..360f, "hue $hue out of range for ${color.toString(16)}")
        }
    }

    @Test
    fun `achromatic colors pin hue and saturation at zero`() {
        // S=0 guard: white/gray have delta == 0 → hue 0, saturation 0.
        assertEquals(0f, subtitleColorToHsv(0xFFFFFFFF.toInt())[0])
        assertEquals(0f, subtitleColorToHsv(0xFFFFFFFF.toInt())[1])
        assertEquals(1f, subtitleColorToHsv(0xFFFFFFFF.toInt())[2])
        val gray = subtitleColorToHsv(0xFF808080.toInt())
        assertEquals(0f, gray[0])
        assertEquals(0f, gray[1])
        // V=0 guard: black pins saturation 0 (delta / max would be 0/0).
        val black = subtitleColorToHsv(0xFF000000.toInt())
        assertEquals(0f, black[0])
        assertEquals(0f, black[1])
        assertEquals(0f, black[2])
    }

    @Test
    fun `hsvToColor covers all six sectors`() {
        assertEquals(0xFFFF0000.toInt(), subtitleHsvToColor(floatArrayOf(0f, 1f, 1f)))
        assertEquals(0xFFFFFF00.toInt(), subtitleHsvToColor(floatArrayOf(60f, 1f, 1f)))
        assertEquals(0xFF00FF00.toInt(), subtitleHsvToColor(floatArrayOf(120f, 1f, 1f)))
        assertEquals(0xFF00FFFF.toInt(), subtitleHsvToColor(floatArrayOf(180f, 1f, 1f)))
        assertEquals(0xFF0000FF.toInt(), subtitleHsvToColor(floatArrayOf(240f, 1f, 1f)))
        assertEquals(0xFFFF00FF.toInt(), subtitleHsvToColor(floatArrayOf(300f, 1f, 1f)))
        // 360 folds onto the final sector, same color as hue 0.
        assertEquals(0xFFFF0000.toInt(), subtitleHsvToColor(floatArrayOf(360f, 1f, 1f)))
    }

    @Test
    fun `hsvToColor forces alpha to 255 and ignores input alpha`() {
        val withAlpha = subtitleHsvToColor(subtitleColorToHsv(0x12345678.toInt()))
        assertEquals(0xFF, (withAlpha ushr 24) and 0xFF)
        val zeroSaturation = subtitleHsvToColor(floatArrayOf(123f, 0f, 1f))
        assertEquals(0xFFFFFFFF.toInt(), zeroSaturation)
    }

    @Test
    fun `round trip is channel-exact within float truncation`() {
        val colors = intArrayOf(
            0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt(),
            0xFFFFFFFF.toInt(), 0xFF000000.toInt(),
            0xFFFF00FF.toInt(), 0xFFFFFF00.toInt(), 0xFF00FFFF.toInt(),
            0xFF3366CC.toInt(), 0xFFCC3366.toInt(),
            0xFF804020.toInt(), 0xFF205080.toInt(),
        )
        for (color in colors) {
            val roundTripped = subtitleHsvToColor(subtitleColorToHsv(color))
            for (shift in intArrayOf(16, 8, 0)) {
                val before = (color shr shift) and 0xFF
                val after = (roundTripped shr shift) and 0xFF
                assertTrue(abs(before - after) <= 1, "channel $shift drifted: $before -> $after")
            }
        }
    }
}
