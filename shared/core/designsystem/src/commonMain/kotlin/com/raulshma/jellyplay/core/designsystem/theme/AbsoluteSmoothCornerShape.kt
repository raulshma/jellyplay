/*
 * Ported verbatim (Apache-2.0) from
 * github.com/racra/smooth-corner-rect-android-compose so the shape renders
 * identically on every KMP target; upstream publishes Android-only artifacts.
 */
package racra.compose.smooth_corner_rect_library

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.min
import kotlin.math.tan
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.abs

/**
 * A shape describing a rectangle with smooth rounded corners sometimes called a
 * Squircle or Superellipse.
 *
 * This shape will not automatically mirror the corners in [LayoutDirection.Rtl].
 */
private fun degreesToRadians(degrees: Double): Float = (degrees * (kotlin.math.PI / 180.0)).toFloat()

data class AbsoluteSmoothCornerShape(
    private val cornerRadiusTL: Dp = 0.dp,
    private val smoothnessAsPercentTL: Int = 60,
    private val cornerRadiusTR: Dp = 0.dp,
    private val smoothnessAsPercentTR: Int = 60,
    private val cornerRadiusBR: Dp = 0.dp,
    private val smoothnessAsPercentBR: Int = 60,
    private val cornerRadiusBL: Dp = 0.dp,
    private val smoothnessAsPercentBL: Int = 60
) : CornerBasedShape(
    topStart = CornerSize(cornerRadiusTL),
    topEnd = CornerSize(cornerRadiusTR),
    bottomEnd = CornerSize(cornerRadiusBR),
    bottomStart = CornerSize(cornerRadiusBL)
) {
    override fun createOutline(
        size: Size,
        topStart: Float,
        topEnd: Float,
        bottomEnd: Float,
        bottomStart: Float,
        layoutDirection: LayoutDirection
    ): Outline = when {
        topStart + topEnd + bottomEnd + bottomStart == 0.0f -> {
            Outline.Rectangle(size.toRect())
        }
        smoothnessAsPercentTL + smoothnessAsPercentTR +
                smoothnessAsPercentBR + smoothnessAsPercentBL == 0 -> {
            Outline.Rounded(
                RoundRect(
                    rect = size.toRect(),
                    topLeft = CornerRadius(topStart),
                    topRight = CornerRadius(topEnd),
                    bottomRight = CornerRadius(bottomEnd),
                    bottomLeft = CornerRadius(bottomStart)
                )
            )
        }
        else -> {
            Outline.Generic(
                Path().apply {
                    val halfOfShortestSide = min(size.height, size.width) / 2

                    var selectedSmoothCorner =
                        SmoothCorner(topStart, smoothnessAsPercentTL, halfOfShortestSide)

                    // Top Left Corner
                    moveTo(
                        selectedSmoothCorner.anchorPoint1.distanceToClosestSide,
                        selectedSmoothCorner.anchorPoint1.distanceToFurthestSide
                    )

                    cubicTo(
                        selectedSmoothCorner.controlPoint1.distanceToClosestSide,
                        selectedSmoothCorner.controlPoint1.distanceToFurthestSide,
                        selectedSmoothCorner.controlPoint2.distanceToClosestSide,
                        selectedSmoothCorner.controlPoint2.distanceToFurthestSide,
                        selectedSmoothCorner.anchorPoint2.distanceToClosestSide,
                        selectedSmoothCorner.anchorPoint2.distanceToFurthestSide
                    )

                    arcToRad(
                        rect = Rect(
                            top = 0f,
                            left = 0f,
                            right = selectedSmoothCorner.arcSection.radius * 2,
                            bottom = selectedSmoothCorner.arcSection.radius * 2
                        ),
                        startAngleRadians =
                        (degreesToRadians(180.0) + selectedSmoothCorner.arcSection.arcStartAngle)
                            .toFloat(),
                        sweepAngleRadians = selectedSmoothCorner.arcSection.arcSweepAngle,
                        forceMoveTo = false
                    )

                    cubicTo(
                        selectedSmoothCorner.controlPoint2.distanceToFurthestSide,
                        selectedSmoothCorner.controlPoint2.distanceToClosestSide,
                        selectedSmoothCorner.controlPoint1.distanceToFurthestSide,
                        selectedSmoothCorner.controlPoint1.distanceToClosestSide,
                        selectedSmoothCorner.anchorPoint1.distanceToFurthestSide,
                        selectedSmoothCorner.anchorPoint1.distanceToClosestSide
                    )

                    selectedSmoothCorner =
                        SmoothCorner(topEnd, smoothnessAsPercentTR, halfOfShortestSide)

                    lineTo(
                        size.width - selectedSmoothCorner.anchorPoint1.distanceToFurthestSide,
                        selectedSmoothCorner.anchorPoint1.distanceToClosestSide
                    )

                    // Top Right Corner
                    cubicTo(
                        size.width - selectedSmoothCorner.controlPoint1.distanceToFurthestSide,
                        selectedSmoothCorner.controlPoint1.distanceToClosestSide,
                        size.width - selectedSmoothCorner.controlPoint2.distanceToFurthestSide,
                        selectedSmoothCorner.controlPoint2.distanceToClosestSide,
                        size.width - selectedSmoothCorner.anchorPoint2.distanceToFurthestSide,
                        selectedSmoothCorner.anchorPoint2.distanceToClosestSide,
                    )

                    arcToRad(
                        rect = Rect(
                            top = 0f,
                            left = size.width - selectedSmoothCorner.arcSection.radius * 2,
                            right = size.width,
                            bottom = selectedSmoothCorner.arcSection.radius * 2
                        ),
                        startAngleRadians =
                        (degreesToRadians(270.0) + selectedSmoothCorner.arcSection.arcStartAngle)
                            .toFloat(),
                        sweepAngleRadians = selectedSmoothCorner.arcSection.arcSweepAngle,
                        forceMoveTo = false
                    )

                    cubicTo(
                        size.width - selectedSmoothCorner.controlPoint2.distanceToClosestSide,
                        selectedSmoothCorner.controlPoint2.distanceToFurthestSide,
                        size.width - selectedSmoothCorner.controlPoint1.distanceToClosestSide,
                        selectedSmoothCorner.controlPoint1.distanceToFurthestSide,
                        size.width - selectedSmoothCorner.anchorPoint1.distanceToClosestSide,
                        selectedSmoothCorner.anchorPoint1.distanceToFurthestSide,
                    )

                    selectedSmoothCorner =
                        SmoothCorner(bottomEnd, smoothnessAsPercentBR, halfOfShortestSide)

                    lineTo(
                        size.width - selectedSmoothCorner.anchorPoint1.distanceToClosestSide,
                        size.height - selectedSmoothCorner.anchorPoint1.distanceToFurthestSide
                    )

                    // Bottom Right Corner
                    cubicTo(
                        size.width - selectedSmoothCorner.controlPoint1.distanceToClosestSide,
                        size.height - selectedSmoothCorner.controlPoint1.distanceToFurthestSide,
                        size.width - selectedSmoothCorner.controlPoint2.distanceToClosestSide,
                        size.height - selectedSmoothCorner.controlPoint2.distanceToFurthestSide,
                        size.width - selectedSmoothCorner.anchorPoint2.distanceToClosestSide,
                        size.height - selectedSmoothCorner.anchorPoint2.distanceToFurthestSide
                    )

                    arcToRad(
                        rect = Rect(
                            top = size.height - selectedSmoothCorner.arcSection.radius * 2,
                            left = size.width - selectedSmoothCorner.arcSection.radius * 2,
                            right = size.width,
                            bottom = size.height
                        ),
                        startAngleRadians =
                        (degreesToRadians(0.0) + selectedSmoothCorner.arcSection.arcStartAngle)
                            .toFloat(),
                        sweepAngleRadians = selectedSmoothCorner.arcSection.arcSweepAngle,
                        forceMoveTo = false
                    )

                    cubicTo(
                        size.width - selectedSmoothCorner.controlPoint2.distanceToFurthestSide,
                        size.height - selectedSmoothCorner.controlPoint2.distanceToClosestSide,
                        size.width - selectedSmoothCorner.controlPoint1.distanceToFurthestSide,
                        size.height - selectedSmoothCorner.controlPoint1.distanceToClosestSide,
                        size.width - selectedSmoothCorner.anchorPoint1.distanceToFurthestSide,
                        size.height - selectedSmoothCorner.anchorPoint1.distanceToClosestSide
                    )

                    selectedSmoothCorner =
                        SmoothCorner(bottomStart, smoothnessAsPercentBL, halfOfShortestSide)

                    lineTo(
                        selectedSmoothCorner.anchorPoint1.distanceToFurthestSide,
                        size.height - selectedSmoothCorner.anchorPoint1.distanceToClosestSide
                    )

                    // Bottom Left Corner
                    cubicTo(
                        selectedSmoothCorner.controlPoint1.distanceToFurthestSide,
                        size.height - selectedSmoothCorner.controlPoint1.distanceToClosestSide,
                        selectedSmoothCorner.controlPoint2.distanceToFurthestSide,
                        size.height - selectedSmoothCorner.controlPoint2.distanceToClosestSide,
                        selectedSmoothCorner.anchorPoint2.distanceToFurthestSide,
                        size.height - selectedSmoothCorner.anchorPoint2.distanceToClosestSide,
                    )

                    arcToRad(
                        rect = Rect(
                            top = size.height - selectedSmoothCorner.arcSection.radius * 2,
                            left = 0f,
                            right = selectedSmoothCorner.arcSection.radius * 2,
                            bottom = size.height
                        ),
                        startAngleRadians =
                        (degreesToRadians(90.0) + selectedSmoothCorner.arcSection.arcStartAngle)
                            .toFloat(),
                        sweepAngleRadians = selectedSmoothCorner.arcSection.arcSweepAngle,
                        forceMoveTo = false
                    )

                    cubicTo(
                        selectedSmoothCorner.controlPoint2.distanceToClosestSide,
                        size.height - selectedSmoothCorner.controlPoint2.distanceToFurthestSide,
                        selectedSmoothCorner.controlPoint1.distanceToClosestSide,
                        size.height - selectedSmoothCorner.controlPoint1.distanceToFurthestSide,
                        selectedSmoothCorner.anchorPoint1.distanceToClosestSide,
                        size.height - selectedSmoothCorner.anchorPoint1.distanceToFurthestSide
                    )

                    close()
                }
            )
        }
    }

    override fun copy(
        topStart: CornerSize,
        topEnd: CornerSize,
        bottomEnd: CornerSize,
        bottomStart: CornerSize
    ) = AbsoluteSmoothCornerShape(
        cornerRadiusTL,
        smoothnessAsPercentTL,
        cornerRadiusTR,
        smoothnessAsPercentTR,
        cornerRadiusBR,
        smoothnessAsPercentBR,
        cornerRadiusBL,
        smoothnessAsPercentBL
    )
}

/**
 * Creates AbsoluteSmoothCornerShape with the same corner radius and smoothness applied for
 * all four corners.
 */
fun AbsoluteSmoothCornerShape(cornerRadius: Dp, smoothnessAsPercent: Int) =
    AbsoluteSmoothCornerShape(
        cornerRadiusTL = cornerRadius,
        smoothnessAsPercentTL = smoothnessAsPercent,
        cornerRadiusTR = cornerRadius,
        smoothnessAsPercentTR = smoothnessAsPercent,
        cornerRadiusBR = cornerRadius,
        smoothnessAsPercentBR = smoothnessAsPercent,
        cornerRadiusBL = cornerRadius,
        smoothnessAsPercentBL = smoothnessAsPercent
    )
