package com.raulshma.jellyplay.feature.player.video.subtitle

import android.content.Context
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.RelativeSizeSpan
import android.util.TypedValue
import androidx.media3.common.text.Cue
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Measures Media3 [Cue] text boxes using the same layout geometry as Media3's
 * `SubtitlePainter` and shifts colliding simultaneous cues via
 * [CueCollisionLayout] so two speakers talking at once stack cleanly instead
 * of drawing on top of one another.
 */
internal class ExoCueCollisionResolver(context: Context) {

    private val spacingAdd: Float
    private val spacingMult: Float
    private val textPaint = TextPaint().apply { isAntiAlias = true }

    init {
        val attrs = context.obtainStyledAttributes(
            null,
            intArrayOf(android.R.attr.lineSpacingExtra, android.R.attr.lineSpacingMultiplier),
            0,
            0,
        )
        spacingAdd = attrs.getDimensionPixelSize(0, 0).toFloat()
        spacingMult = attrs.getFloat(1, 1f)
        attrs.recycle()
    }

    /**
     * Returns [cues] with any colliding horizontal text cues repositioned
     * vertically inside `[parentWidth] x [parentHeight]`. Returns [cues]
     * untouched when fewer than 2 text cues are present, dimensions are not
     * laid out yet, or nothing overlaps.
     */
    fun resolve(
        cues: List<Cue>,
        parentWidth: Int,
        parentHeight: Int,
        fontSizeSp: Float,
        typeface: Typeface,
        bottomPaddingFraction: Float,
        displayMetrics: android.util.DisplayMetrics,
        gapPx: Int = 0,
    ): List<Cue> {
        if (cues.size < 2 || parentWidth <= 0 || parentHeight <= 0) return cues

        val textSizePx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            fontSizeSp,
            displayMetrics,
        )
        if (textSizePx <= 0f) return cues

        textPaint.textSize = textSizePx
        textPaint.typeface = typeface
        val textPaddingX = (textSizePx * INNER_PADDING_RATIO + 0.5f).toInt()
        val baseAvailableWidth = parentWidth - textPaddingX * 2
        if (baseAvailableWidth <= 0) return cues

        val boxes = ArrayList<CueLayoutBox>(cues.size)
        for ((index, cue) in cues.withIndex()) {
            // Bitmap and vertical-writing cues stay out of horizontal line stacking.
            if (cue.bitmap != null || cue.verticalType != Cue.TYPE_UNSET) continue
            val rawText = cue.text ?: continue
            if (rawText.isBlank()) continue

            val text = stripSizeSpans(rawText)
            val availableWidth = if (cue.size != Cue.DIMEN_UNSET) {
                (baseAvailableWidth * cue.size).toInt()
            } else {
                baseAvailableWidth
            }
            if (availableWidth <= 0) continue

            val alignment = cue.textAlignment ?: Layout.Alignment.ALIGN_CENTER
            val layout = StaticLayout.Builder
                .obtain(text, 0, text.length, textPaint, availableWidth)
                .setAlignment(alignment)
                .setLineSpacing(spacingAdd, spacingMult)
                .setIncludePad(true)
                .build()

            val textHeight = layout.height
            var maxLineWidth = 0
            for (lineIndex in 0 until layout.lineCount) {
                maxLineWidth = max(maxLineWidth, ceil(layout.getLineWidth(lineIndex).toDouble()).toInt())
            }
            val boxContentWidth = if (cue.size != Cue.DIMEN_UNSET && maxLineWidth < availableWidth) {
                availableWidth
            } else {
                maxLineWidth
            }
            val boxWidth = boxContentWidth + textPaddingX * 2

            val left: Int
            val right: Int
            if (cue.position != Cue.DIMEN_UNSET) {
                val anchorX = (parentWidth * cue.position).roundToInt()
                val rawLeft = when (cue.positionAnchor) {
                    Cue.ANCHOR_TYPE_END -> anchorX - boxWidth
                    Cue.ANCHOR_TYPE_MIDDLE -> (anchorX * 2 - boxWidth) / 2
                    else -> anchorX
                }
                left = max(0, rawLeft)
                right = min(left + boxWidth, parentWidth)
            } else {
                left = (parentWidth - boxWidth) / 2
                right = left + boxWidth
            }
            if (right <= left) continue

            val lineHeight = if (layout.lineCount > 0) {
                layout.getLineBottom(0) - layout.getLineTop(0)
            } else {
                0
            }
            val rawTop = computeInitialTop(
                cue = cue,
                parentHeight = parentHeight,
                textHeight = textHeight,
                lineHeight = lineHeight,
                bottomPaddingFraction = bottomPaddingFraction,
            )
            val top = rawTop.coerceIn(0, (parentHeight - textHeight).coerceAtLeast(0))
            val direction = resolveDirection(cue, top, textHeight, parentHeight)

            boxes += CueLayoutBox(
                index = index,
                left = left,
                top = top,
                right = right,
                bottom = top + textHeight,
                direction = direction,
            )
        }

        if (boxes.size < 2) return cues

        val movedTops = CueCollisionLayout.resolveVerticalOffsets(
            boxes = boxes,
            containerTop = 0,
            containerBottom = parentHeight,
            gapPx = gapPx,
        )
        if (movedTops.isEmpty()) return cues

        return cues.mapIndexed { index, cue ->
            val newTop = movedTops[index] ?: return@mapIndexed cue
            val fraction = (newTop.toFloat() / parentHeight.toFloat()).coerceIn(0f, 1f)
            cue.buildUpon()
                .setLine(fraction, Cue.LINE_TYPE_FRACTION)
                .setLineAnchor(Cue.ANCHOR_TYPE_START)
                .build()
        }
    }

    private fun computeInitialTop(
        cue: Cue,
        parentHeight: Int,
        textHeight: Int,
        lineHeight: Int,
        bottomPaddingFraction: Float,
    ): Int {
        if (cue.line == Cue.DIMEN_UNSET) {
            return parentHeight - textHeight - (parentHeight * bottomPaddingFraction).toInt()
        }
        return if (cue.lineType == Cue.LINE_TYPE_FRACTION) {
            val anchorY = (parentHeight * cue.line).roundToInt()
            when (cue.lineAnchor) {
                Cue.ANCHOR_TYPE_END -> anchorY - textHeight
                Cue.ANCHOR_TYPE_MIDDLE -> (anchorY * 2 - textHeight) / 2
                else -> anchorY
            }
        } else {
            if (cue.line >= 0f) {
                (cue.line * lineHeight).roundToInt()
            } else {
                ((cue.line + 1f) * lineHeight).roundToInt() + parentHeight - textHeight
            }
        }
    }

    private fun resolveDirection(
        cue: Cue,
        top: Int,
        textHeight: Int,
        parentHeight: Int,
    ): CueStackDirection {
        if (cue.line != Cue.DIMEN_UNSET) {
            if (cue.lineType == Cue.LINE_TYPE_NUMBER && cue.line >= 0f) {
                return CueStackDirection.DOWN
            }
            if (cue.lineType == Cue.LINE_TYPE_FRACTION &&
                cue.lineAnchor == Cue.ANCHOR_TYPE_START &&
                cue.line < 0.5f
            ) {
                return CueStackDirection.DOWN
            }
        }
        val centerY = top + textHeight / 2f
        return if (centerY < parentHeight * 0.35f && cue.line != Cue.DIMEN_UNSET) {
            CueStackDirection.DOWN
        } else {
            CueStackDirection.UP
        }
    }

    private fun stripSizeSpans(text: CharSequence): CharSequence {
        if (text !is Spanned) return text
        val builder = SpannableStringBuilder(text)
        val spans = builder.getSpans(0, builder.length, Any::class.java)
        for (span in spans) {
            if (span is AbsoluteSizeSpan || span is RelativeSizeSpan) {
                builder.removeSpan(span)
            }
        }
        return builder
    }

    private companion object {
        const val INNER_PADDING_RATIO = 0.125f
    }
}
