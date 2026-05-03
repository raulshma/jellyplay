package com.raulshma.jellyplay.feature.player.video.subtitle

import android.graphics.Bitmap
import android.graphics.Color
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object SubtitleOcrHelper {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun recognizeTextFromBitmap(bitmap: Bitmap): String =
        suspendCancellableCoroutine { continuation ->
            val image = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val text = visionText.textBlocks
                        .flatMap { block -> block.lines }
                        .joinToString("\n") { line -> line.text }
                        .trim()
                    continuation.resume(text)
                }
                .addOnFailureListener { e ->
                    continuation.resumeWithException(e)
                }
        }

    suspend fun extractSubtitleTextFromFrame(
        frameBitmap: Bitmap,
        bottomFraction: Float = 0.25f,
        minHeightPx: Int = 50,
    ): String? {
        val height = frameBitmap.height
        val cropTop = (height * (1f - bottomFraction)).toInt().coerceAtLeast(0)
        val cropHeight = (height - cropTop).coerceAtLeast(minHeightPx)

        if (cropHeight > frameBitmap.height || cropTop + cropHeight > frameBitmap.height) {
            return null
        }

        val cropped = Bitmap.createBitmap(
            frameBitmap,
            0,
            cropTop,
            frameBitmap.width.coerceAtMost(frameBitmap.width),
            cropHeight.coerceAtMost(frameBitmap.height - cropTop),
        )

        val enhanced = enhanceForOcr(cropped)
        val text = recognizeTextFromBitmap(enhanced)

        return text.takeIf { it.isNotBlank() && isLikelySubtitleText(it) }
    }

    private fun enhanceForOcr(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        for (x in 0 until width) {
            for (y in 0 until height) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                val brightness = (r * 0.299f + g * 0.587f + b * 0.114f)
                val isLight = brightness > 128
                result.setPixel(
                    x, y,
                    if (isLight) Color.WHITE else Color.BLACK,
                )
            }
        }
        return result
    }

    private fun isLikelySubtitleText(text: String): Boolean {
        val cleaned = text.replace("\\s+".toRegex(), " ").trim()
        if (cleaned.length < 2) return false
        if (cleaned.length > 200) return false
        val letterRatio = cleaned.count { it.isLetter() || it.isWhitespace() }.toFloat() / cleaned.length
        return letterRatio > 0.5f
    }
}
