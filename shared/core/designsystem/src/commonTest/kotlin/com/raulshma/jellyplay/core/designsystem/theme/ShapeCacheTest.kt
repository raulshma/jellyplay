package com.raulshma.jellyplay.core.designsystem.theme

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.Test
import kotlin.test.assertSame
import kotlin.test.assertTrue
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

/**
 * Pins the invariants of [ShapeCache] and [expressiveListShape] resolution:
 *  - Cache entries are process-wide singletons (smooth shapes cached behind the
 *    accessors): repeated reads of smooth4/smooth8 return the SAME instance, so
 *    LazyColumn items share one expensive smooth-corner shape per radius.
 *  - With the synthwave flag off, every accessor resolves to its
 *    [AbsoluteSmoothCornerShape] singleton.
 *  - With the synthwave flag on, EVERY accessor collapses to the one shared
 *    zero-corner instance, whose outline is an [Outline.Rectangle] covering
 *    exactly the requested size.
 *  - A flag flip changes the RETURNED instance (not a draw-time flag read) —
 *    that instance change is the draw input that makes already-composed
 *    surfaces redraw when synthwave mode toggles.
 *
 * The zero-corner branch is outlined directly because both [Outline.Rounded]
 * and [Outline.Generic] eagerly build a Skia `Path` in this compose version,
 * which needs the skiko native runtime (not loadable in this plain JVM unit-test
 * lane); the zero-corner rectangle outline is Path-free. Smooth singletons are
 * asserted by type/identity only, never outlined.
 *
 * The flag is module-internal state, so the test lives in the same package and
 * always restores it (Before/After) to avoid leaking state across suites.
 */
class ShapeCacheTest {

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
    fun cacheEntries_resolveToAbsoluteSmoothCornerDefaults() {
        assertTrue(ShapeCache.smooth4 is AbsoluteSmoothCornerShape)
        assertTrue(ShapeCache.smooth8 is AbsoluteSmoothCornerShape)
        assertTrue(ShapeCache.smoothTop28 is AbsoluteSmoothCornerShape)
        assertTrue(ShapeCache.smoothPill is AbsoluteSmoothCornerShape)
    }

    @Test
    fun synthwaveActive_everyAccessorCollapsesToOneZeroCornerInstance() {
        _isSynthwaveActive.value = true
        val zero = ShapeCache.smooth4
        assertSame(zero, ShapeCache.smooth8)
        assertSame(zero, ShapeCache.smoothTop28)
        assertSame(zero, ShapeCache.smoothPill)
        // The collapse is observable in the outline: a rectangle covering
        // exactly the requested size (Path-free, so safe in this plain JVM lane).
        val rect = assertIs<Outline.Rectangle>(zero.createOutline(size, LayoutDirection.Ltr, density)).rect
        assertEquals(0f, rect.left)
        assertEquals(0f, rect.top)
        assertEquals(100f, rect.width)
        assertEquals(100f, rect.height)
    }

    @Test
    fun flagFlip_changesTheReturnedInstance() {
        val smooth = ShapeCache.smooth4
        assertTrue(smooth is AbsoluteSmoothCornerShape)

        _isSynthwaveActive.value = true
        val zero = ShapeCache.smooth4
        assertNotSame(smooth, zero)

        _isSynthwaveActive.value = false
        assertSame(smooth, ShapeCache.smooth4)
    }

    @Test
    fun expressiveListShape_cachesPerKey() {
        // Same (index, count, radii) key -> the cached singleton; different key -> a
        // distinct shape. Construction only — no outlining, so skiko-free.
        val first = expressiveListShape(index = 0, count = 3)
        assertSame(first, expressiveListShape(index = 0, count = 3))
        assertNotSame(first, expressiveListShape(index = 1, count = 3))
        assertNotSame(first, expressiveListShape(index = 0, count = 3, outerRadius = 30.dp))
        assertTrue(first is AbsoluteSmoothCornerShape)
    }

    @Test
    fun expressiveListShape_synthwaveActiveCollapsesToZeroCornerInstance() {
        _isSynthwaveActive.value = true
        // Short-circuits before the LRU to the same shared zero-corner shape the
        // accessors hand out, so a flip changes the instance identity at call sites.
        assertSame(ShapeCache.smooth4, expressiveListShape(index = 0, count = 3))

        _isSynthwaveActive.value = false
        assertSame(
            expressiveListShape(index = 0, count = 3),
            expressiveListShape(index = 0, count = 3),
        )
    }
}
