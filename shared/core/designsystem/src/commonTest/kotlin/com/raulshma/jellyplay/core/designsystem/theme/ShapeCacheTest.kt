package com.raulshma.jellyplay.core.designsystem.theme

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.Test
import kotlin.test.assertTrue
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

/**
 * Pins the invariants of [ShapeCache] and its [SynthwaveDynamicShape] wrapper:
 *  - Cache entries are process-wide singletons (object + vals): repeated reads of
 *    smooth4/smooth8 return the SAME instance, so LazyColumn items share one
 *    expensive smooth-corner shape per radius.
 *  - [SynthwaveDynamicShape.createOutline] delegates to the wrapped default shape
 *    while the synthwave flag is off.
 *  - With the synthwave flag on, EVERY wrapped shape collapses to a zero-corner
 *    outline: an [Outline.Rectangle] covering exactly the requested size.
 *
 * The delegation branch is pinned through a sentinel [Shape] returning a marker
 * rectangle rather than through the real cache entries: both [Outline.Rounded]
 * and [Outline.Generic] eagerly build a Skia `Path` in this compose version,
 * which needs the skiko native runtime (not loadable in this plain JVM unit-test
 * lane). The flag logic under test lives in [SynthwaveDynamicShape] itself, so a
 * Path-free default shape covers the delegation and zero-corner branches exactly,
 * while the singleton/typing guarantees of the real cache entries are asserted
 * without outlining them.
 *
 * The flag is module-internal state, so the test lives in the same package and
 * always restores it (Before/After) to avoid leaking state across suites.
 */
class ShapeCacheTest {

    /** Default shape whose outline is offset by 1px — distinguishable from any
     *  zero-corner rectangle the synthwave branch could produce. */
    private class SentinelShape : Shape {
        override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density) =
            Outline.Rectangle(
                Rect(left = 1f, top = 1f, right = 1f + size.width, bottom = 1f + size.height),
            )
    }

    private val size = Size(100f, 100f)
    private val density = Density(1f)

    @BeforeTest
    fun resetSynthwaveFlag() {
        _isSynthwaveActive.value = false
    }

    @AfterTest
    fun restoreSynthwaveFlag() {
        _isSynthwaveActive.value = false
    }

    @Test
    fun cacheEntries_areSingletons() {
        assertSame(ShapeCache.smooth4, ShapeCache.smooth4)
        assertSame(ShapeCache.smooth8, ShapeCache.smooth8)
        assertNotSame(ShapeCache.smooth4, ShapeCache.smooth8)
    }

    @Test
    fun cacheEntries_wrapAbsoluteSmoothCornerDefaults() {
        assertTrue(ShapeCache.smooth4.defaultShape is AbsoluteSmoothCornerShape)
        assertTrue(ShapeCache.smooth8.defaultShape is AbsoluteSmoothCornerShape)
        assertTrue(ShapeCache.smoothTop28.defaultShape is AbsoluteSmoothCornerShape)
        assertTrue(ShapeCache.smoothPill.defaultShape is AbsoluteSmoothCornerShape)
    }

    @Test
    fun synthwaveInactive_createOutlineDelegatesToTheDefaultShape() {
        val wrapper = SynthwaveDynamicShape(defaultShape = SentinelShape())
        val rect = assertIs<Outline.Rectangle>(wrapper.createOutline(size, LayoutDirection.Ltr, density)).rect
        // The 1px sentinel offset survived: the DEFAULT shape produced the outline.
        assertEquals(1f, rect.left)
        assertEquals(1f, rect.top)
        assertEquals(101f, rect.right)
        assertEquals(101f, rect.bottom)
    }

    @Test
    fun synthwaveActive_wrappedShapeCollapsesToZeroCornerRectangle() {
        _isSynthwaveActive.value = true
        val wrapper = SynthwaveDynamicShape(defaultShape = SentinelShape())
        val rect = assertIs<Outline.Rectangle>(wrapper.createOutline(size, LayoutDirection.Ltr, density)).rect
        // The sentinel is bypassed: corners are zeroed to the exact requested size.
        assertEquals(0f, rect.left)
        assertEquals(0f, rect.top)
        assertEquals(100f, rect.width)
        assertEquals(100f, rect.height)
    }

    @Test
    fun flagFlip_changesTheOutlineProducedForTheSameShapeInstance() {
        val wrapper = SynthwaveDynamicShape(defaultShape = SentinelShape())

        val inactiveRect = assertIs<Outline.Rectangle>(wrapper.createOutline(size, LayoutDirection.Ltr, density)).rect
        assertEquals(1f, inactiveRect.left, "flag off must delegate to the sentinel default")

        _isSynthwaveActive.value = true
        val activeRect = assertIs<Outline.Rectangle>(wrapper.createOutline(size, LayoutDirection.Ltr, density)).rect
        assertEquals(0f, activeRect.left, "flag on must zero the corners")
    }

    @Test
    fun expressiveListShape_cachesPerKey() {
        // Same (index, count, radii) key -> the cached singleton; different key -> a
        // distinct wrapped shape. Construction only — no outlining, so skiko-free.
        val first = expressiveListShape(index = 0, count = 3)
        assertSame(first, expressiveListShape(index = 0, count = 3))
        assertNotSame(first, expressiveListShape(index = 1, count = 3))
        assertNotSame(first, expressiveListShape(index = 0, count = 3, outerRadius = 30.dp))
        assertTrue(first is SynthwaveDynamicShape)
    }
}
