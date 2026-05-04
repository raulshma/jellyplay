package com.raulshma.jellyplay.core.ui.image

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.cos
import kotlin.math.pow

internal object BlurHashDecoder {

    private const val BASE83_CHARS =
        "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz#\$%*+,-.:;=?@[]^_{|}~"

    fun decode(blurHash: String, width: Int, height: Int): Bitmap? {
        if (blurHash.length < 6) return null

        val sizeFlag = decode83(blurHash, 0, 1)
        val numY = sizeFlag / 9 + 1
        val numX = sizeFlag % 9 + 1

        val quantizedMaxValue = decode83(blurHash, 1, 2)
        val maxValue = (quantizedMaxValue + 1) / 166f

        val totalComponents = numX * numY
        val expectedLength = 4 + totalComponents * 2
        if (blurHash.length < expectedLength) return null

        val colors = FloatArray(totalComponents * 3)

        for (idx in 0 until totalComponents) {
            if (idx == 0) {
                val dc = decodeDC(blurHash, 2, 6)
                colors[0] = dc[0]
                colors[1] = dc[1]
                colors[2] = dc[2]
            } else {
                val start = 4 + idx * 2
                val ac = decodeAC(blurHash, start, start + 2, maxValue)
                val offset = idx * 3
                colors[offset] = ac[0]
                colors[offset + 1] = ac[1]
                colors[offset + 2] = ac[2]
            }
        }

        val pixels = IntArray(width * height)
        val pi = Math.PI.toFloat()

        for (y in 0 until height) {
            for (x in 0 until width) {
                var r = 0f
                var g = 0f
                var b = 0f

                for (j in 0 until numY) {
                    for (i in 0 until numX) {
                        val basis = (cos(pi * x * i / width) * cos(pi * y * j / height)).toFloat()
                        val offset = (j * numX + i) * 3
                        r += colors[offset] * basis
                        g += colors[offset + 1] * basis
                        b += colors[offset + 2] * basis
                    }
                }

                pixels[y * width + x] = Color.rgb(linearToSRGB(r), linearToSRGB(g), linearToSRGB(b))
            }
        }

        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun decode83(str: String, from: Int, to: Int): Int {
        var result = 0
        for (i in from until to) {
            val index = BASE83_CHARS.indexOf(str[i])
            if (index < 0) return 0
            result = result * 83 + index
        }
        return result
    }

    private fun decodeDC(str: String, from: Int, to: Int): FloatArray {
        val value = decode83(str, from, to)
        return floatArrayOf(
            sRGBToLinear((value shr 16) and 0xFF),
            sRGBToLinear((value shr 8) and 0xFF),
            sRGBToLinear(value and 0xFF),
        )
    }

    private fun decodeAC(str: String, from: Int, to: Int, maxValue: Float): FloatArray {
        val value = decode83(str, from, to)
        val quantR = value / (19 * 19)
        val quantG = (value / 19) % 19
        val quantB = value % 19
        return floatArrayOf(
            signPow((quantR - 9f) / 9f) * maxValue,
            signPow((quantG - 9f) / 9f) * maxValue,
            signPow((quantB - 9f) / 9f) * maxValue,
        )
    }

    private fun sRGBToLinear(value: Int): Float {
        val v = value / 255f
        return if (v <= 0.04045f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)
    }

    private fun linearToSRGB(value: Float): Int {
        val v = value.coerceIn(0f, 1f)
        val srgb = if (v <= 0.0031308f) v * 12.92f else 1.055f * v.pow(1f / 2.4f) - 0.055f
        return (srgb * 255f + 0.5f).toInt()
    }

    private fun signPow(value: Float): Float {
        return if (value < 0f) -((-value).pow(2f)) else value.pow(2f)
    }
}
