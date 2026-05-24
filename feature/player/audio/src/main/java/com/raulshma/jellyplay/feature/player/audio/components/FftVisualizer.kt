package com.raulshma.jellyplay.feature.player.audio.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

@Composable
fun FftVisualizer(
    fftData: ByteArray,
    modifier: Modifier = Modifier,
    barCount: Int = 32,
    color: Color = Color.White.copy(alpha = 0.6f),
    peakColor: Color = Color.White.copy(alpha = 0.9f),
) {
    if (fftData.isEmpty()) return

    val magnitudes = computeMagnitudes(fftData, barCount)

    Canvas(modifier = modifier) {
        val barWidth = (size.width - (barCount - 1) * 2.dp.toPx()) / barCount
        val maxHeight = size.height

        magnitudes.forEachIndexed { index, magnitude ->
            val animatedHeight = magnitude * maxHeight
            val x = index * (barWidth + 2.dp.toPx())
            val y = maxHeight - animatedHeight

            drawRoundRect(
                color = color,
                topLeft = Offset(x, y),
                size = Size(barWidth, animatedHeight),
                cornerRadius = CornerRadius(barWidth / 2, barWidth / 2),
            )

            if (magnitude > 0.1f) {
                drawRoundRect(
                    color = peakColor,
                    topLeft = Offset(x, y),
                    size = Size(barWidth, 2.dp.toPx()),
                    cornerRadius = CornerRadius(barWidth / 2, barWidth / 2),
                )
            }
        }
    }
}

private fun computeMagnitudes(fftData: ByteArray, barCount: Int): List<Float> {
    if (fftData.size < 4) return List(barCount) { 0f }

    val numBins = fftData.size / 2
    val magnitudes = FloatArray(numBins)

    for (i in 0 until numBins) {
        val real = fftData[i * 2].toInt()
        val imag = fftData[i * 2 + 1].toInt()
        val magnitude = sqrt((real * real + imag * imag).toFloat())
        magnitudes[i] = magnitude / 128f
    }

    val binsPerBar = max(1, numBins / barCount)
    val result = FloatArray(barCount)

    for (bar in 0 until barCount) {
        var sum = 0f
        val startBin = bar * binsPerBar
        val endBin = min(startBin + binsPerBar, numBins)
        for (bin in startBin until endBin) {
            sum += magnitudes[bin]
        }
        val avg = sum / (endBin - startBin).coerceAtLeast(1)
        result[bar] = avg.coerceIn(0f, 1f)
    }

    return result.toList()
}
