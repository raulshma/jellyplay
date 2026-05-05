package com.raulshma.jellyplay.feature.player.video.subtitle

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object SubtitleOcrHelper {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    private val semaphore = Semaphore(1)

    suspend fun recognizeTextFromBitmap(bitmap: Bitmap): String =
        semaphore.withPermit {
            suspendCancellableCoroutine { continuation ->
                val image = InputImage.fromBitmap(bitmap, 0)
                val task = recognizer.process(image)
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
                continuation.invokeOnCancellation {
                    task.exception
                }
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
        cropped.recycle()
        val text = recognizeTextFromBitmap(enhanced)
        enhanced.recycle()

        return text.takeIf { it.isNotBlank() && isLikelySubtitleText(it) }
    }

    private fun enhanceForOcr(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(
                ColorMatrix(
                    floatArrayOf(
                        0.299f, 0.587f, 0.114f, 0f, 0f,
                        0.299f, 0.587f, 0.114f, 0f, 0f,
                        0.299f, 0.587f, 0.114f, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f,
                    )
                ).apply {
                    postConcat(
                        ColorMatrix(
                            floatArrayOf(
                                2f, 0f, 0f, 0f, -255f,
                                2f, 0f, 0f, 0f, -255f,
                                2f, 0f, 0f, 0f, -255f,
                                0f, 0f, 0f, 1f, 0f,
                            )
                        )
                    )
                }
            )
        }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
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
