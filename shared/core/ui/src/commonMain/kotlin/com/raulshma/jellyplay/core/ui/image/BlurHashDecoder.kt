package com.raulshma.jellyplay.core.ui.image

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow

internal object BlurHashDecoder {

    private const val BASE83_CHARS =
        "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz#\$%*+,-.:;=?@[]^_{|}~"

    private val SRGB_TO_LINEAR = FloatArray(256) { i ->
        val v = i / 255f
        if (v <= 0.04045f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)
    }

    private val LINEAR_TO_SRGB = IntArray(1024) { i ->
        val v = i / 1023f
        val srgb = if (v <= 0.0031308f) v * 12.92f else 1.055f * v.pow(1f / 2.4f) - 0.055f
        (srgb.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
    }

    /** Decodes to ARGB-packed pixels; the caller turns them into a platform bitmap. */
    fun decode(blurHash: String, width: Int, height: Int): IntArray? {
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

        val cosX = Array(numX) { i ->
            FloatArray(width) { x -> cos(PI * x * i / width).toFloat() }
        }
        val cosY = Array(numY) { j ->
            FloatArray(height) { y -> cos(PI * y * j / height).toFloat() }
        }

        val pixels = IntArray(width * height)

        for (y in 0 until height) {
            for (x in 0 until width) {
                var r = 0f
                var g = 0f
                var b = 0f

                for (j in 0 until numY) {
                    val cy = cosY[j][y]
                    for (i in 0 until numX) {
                        val basis = cosX[i][x] * cy
                        val offset = (j * numX + i) * 3
                        r += colors[offset] * basis
                        g += colors[offset + 1] * basis
                        b += colors[offset + 2] * basis
                    }
                }

                pixels[y * width + x] =
                    0xFF000000.toInt() or
                        (linearToSRGB(r) shl 16) or
                        (linearToSRGB(g) shl 8) or
                        linearToSRGB(b)
            }
        }

        return pixels
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
            SRGB_TO_LINEAR[(value shr 16) and 0xFF],
            SRGB_TO_LINEAR[(value shr 8) and 0xFF],
            SRGB_TO_LINEAR[value and 0xFF],
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

    private fun linearToSRGB(value: Float): Int {
        val v = value.coerceIn(0f, 1f)
        val idx = (v * 1023f + 0.5f).toInt().coerceIn(0, 1023)
        return LINEAR_TO_SRGB[idx]
    }

    private fun signPow(value: Float): Float {
        return if (value < 0f) -((-value).pow(2f)) else value.pow(2f)
    }
}
