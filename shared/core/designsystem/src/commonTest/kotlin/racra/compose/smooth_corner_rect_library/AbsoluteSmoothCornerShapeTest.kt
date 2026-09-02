package racra.compose.smooth_corner_rect_library

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertIs

/**
 * Pins the invariants of the racra smooth-corner (squircle) port WITHOUT touching
 * the Skia-backed Path machinery: the plain JVM unit-test lane cannot reliably
 * initialize the skiko native runtime, and in this compose version BOTH
 * `Outline.Rounded` and `Outline.Generic` eagerly construct a `Path`, so the only
 * outline kind testable here is [Outline.Rectangle]. Everything below is pure
 * geometry:
 *
 *  - Negative smoothness is rejected via `require` in [SmoothCorner]'s init — the
 *    same code path the shape's generic branch walks, tested directly on the
 *    internal geometry class (same package).
 *  - Corner radius larger than half the shortest side is CLAMPED to it — observable
 *    through [SmoothCorner.arcSection] without drawing.
 *  - Shape-level fast path that needs no Path: all-zero radii -> plain
 *    [Outline.Rectangle] matching the requested size.
 *  - Per-corner radii are independent (TL/TR/BR/BL -> topStart/topEnd/bottomEnd/
 *    bottomStart) and density-scaled — pinned through [CornerSize.toPx], the exact
 *    resolution step the outline branches consume.
 */
class AbsoluteSmoothCornerShapeTest {

    private val size = Size(100f, 100f)
    private val density = Density(1f)

    private fun createOutline(
        shape: AbsoluteSmoothCornerShape,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
        outlineSize: Size = size,
        outlineDensity: Density = density,
    ): Outline = shape.createOutline(outlineSize, layoutDirection, outlineDensity)

    @Test
    fun negativeSmoothness_throwsIllegalArgumentFromSmoothCornerInit() {
        val error = assertFailsWith<IllegalArgumentException> {
            SmoothCorner(
                cornerRadius = 16f,
                smoothnessAsPercent = -1,
                maximumCurveStartDistanceFromVertex = 50f,
            )
        }
        assertEquals("The value for smoothness can never be negative.", error.message)
    }

    @Test
    fun negativeSmoothness_isRejectedForAnyMagnitude() {
        assertFailsWith<IllegalArgumentException> {
            SmoothCorner(cornerRadius = 8f, smoothnessAsPercent = -100, maximumCurveStartDistanceFromVertex = 50f)
        }
    }

    @Test
    fun smoothnessZero_isAccepted_boundaryOfTheRequire() {
        val corner = SmoothCorner(
            cornerRadius = 16f,
            smoothnessAsPercent = 0,
            maximumCurveStartDistanceFromVertex = 50f,
        )
        assertEquals(16f, corner.arcSection.radius)
    }

    @Test
    fun oversizedRadius_isClampedToHalfTheShortestSide() {
        // A 10x10 viewport gives maximumCurveStartDistanceFromVertex = 5px: a 100px
        // radius must clamp to 5px instead of drawing outside the bounds.
        val clamped = SmoothCorner(
            cornerRadius = 100f,
            smoothnessAsPercent = 60,
            maximumCurveStartDistanceFromVertex = 5f,
        )
        assertEquals(5f, clamped.arcSection.radius)
        assertEquals(5f, clamped.anchorPoint1.distanceToFurthestSide)

        // An exactly-oversized radius clamps to half the shortest side...
        val half = SmoothCorner(cornerRadius = 100f, smoothnessAsPercent = 60, maximumCurveStartDistanceFromVertex = 50f)
        assertEquals(50f, half.arcSection.radius)
        // ...and a radius below the clamp passes through untouched.
        val small = SmoothCorner(cornerRadius = 4f, smoothnessAsPercent = 60, maximumCurveStartDistanceFromVertex = 100f)
        assertEquals(4f, small.arcSection.radius)
    }

    @Test
    fun zeroRadius_cornerClampsToZeroArc() {
        val corner = SmoothCorner(cornerRadius = 0f, smoothnessAsPercent = 60, maximumCurveStartDistanceFromVertex = 50f)
        assertEquals(0f, corner.arcSection.radius)
    }

    @Test
    fun zeroRadius_outlinesAsRectangleMatchingTheSize() {
        val outline = createOutline(AbsoluteSmoothCornerShape(cornerRadius = 0.dp, smoothnessAsPercent = 60))
        val rect = assertIs<Outline.Rectangle>(outline).rect
        assertEquals(0f, rect.left)
        assertEquals(0f, rect.top)
        assertEquals(100f, rect.width)
        assertEquals(100f, rect.height)
    }

    @Test
    fun uniformRadius_propagatesToAllFourCorners() {
        // Corner radii are resolved through CornerSize (density-aware px), which is
        // exactly the input the outline branches consume — asserted directly so the
        // suite stays free of Skia Path construction (Outline.Rounded eagerly builds
        // one in this compose version).
        val shape = AbsoluteSmoothCornerShape(cornerRadius = 8.dp, smoothnessAsPercent = 0)
        assertEquals(8f, shape.topStart.toPx(size, density))
        assertEquals(8f, shape.topEnd.toPx(size, density))
        assertEquals(8f, shape.bottomEnd.toPx(size, density))
        assertEquals(8f, shape.bottomStart.toPx(size, density))
    }

    @Test
    fun perCornerRadii_areIndependent() {
        // TL only: exactly the top-start corner carries the radius.
        val tlOnly = AbsoluteSmoothCornerShape(cornerRadiusTL = 8.dp, smoothnessAsPercentTL = 0)
        assertEquals(8f, tlOnly.topStart.toPx(size, density))
        assertEquals(0f, tlOnly.topEnd.toPx(size, density))
        assertEquals(0f, tlOnly.bottomEnd.toPx(size, density))
        assertEquals(0f, tlOnly.bottomStart.toPx(size, density))

        // BR only: the bottom-end corner maps independently of the others.
        val brOnly = AbsoluteSmoothCornerShape(cornerRadiusBR = 16.dp, smoothnessAsPercentBR = 0)
        assertEquals(16f, brOnly.bottomEnd.toPx(size, density))
        assertEquals(0f, brOnly.topStart.toPx(size, density))
        assertEquals(0f, brOnly.topEnd.toPx(size, density))
        assertEquals(0f, brOnly.bottomStart.toPx(size, density))
    }

    @Test
    fun cornerRadii_scaleWithDensity() {
        val shape = AbsoluteSmoothCornerShape(cornerRadius = 8.dp, smoothnessAsPercent = 0)
        assertEquals(16f, shape.topStart.toPx(size, Density(2f)))
    }

    @Test
    fun smoothnessIsPartOfShapeEquality() {
        assertEquals(
            AbsoluteSmoothCornerShape(cornerRadius = 8.dp, smoothnessAsPercent = 60),
            AbsoluteSmoothCornerShape(cornerRadius = 8.dp, smoothnessAsPercent = 60),
        )
        assertNotEquals(
            AbsoluteSmoothCornerShape(cornerRadius = 8.dp, smoothnessAsPercent = 60),
            AbsoluteSmoothCornerShape(cornerRadius = 8.dp, smoothnessAsPercent = 40),
        )
    }
}
