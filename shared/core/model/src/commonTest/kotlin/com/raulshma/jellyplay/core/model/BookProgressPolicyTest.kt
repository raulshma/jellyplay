package com.raulshma.jellyplay.core.model

import kotlin.test.assertEquals
import kotlin.test.Test

class BookProgressPolicyTest {

    @Test
    fun pageToTicksMultipliesByTicksPerPage() {
        assertEquals(0L, BookProgressPolicy.pageToTicks(0))
        assertEquals(10_000L, BookProgressPolicy.pageToTicks(1))
        assertEquals(50_000L, BookProgressPolicy.pageToTicks(5))
    }

    @Test
    fun pageToTicksCoercesNegativeToZero() {
        assertEquals(0L, BookProgressPolicy.pageToTicks(-3))
    }

    @Test
    fun roundTripTicksToPageAndBack() {
        for (page in 0..1_000) {
            assertEquals(page, BookProgressPolicy.ticksToPage(BookProgressPolicy.pageToTicks(page)))
        }
    }

    @Test
    fun ticksToPageFloorsPartialPages() {
        assertEquals(0, BookProgressPolicy.ticksToPage(9_999L))
        assertEquals(1, BookProgressPolicy.ticksToPage(10_000L))
        assertEquals(1, BookProgressPolicy.ticksToPage(19_999L))
    }

    @Test
    fun ticksToPageNonPositiveIsZero() {
        assertEquals(0, BookProgressPolicy.ticksToPage(0L))
        assertEquals(0, BookProgressPolicy.ticksToPage(-50_000L))
    }

    @Test
    fun ticksToPageHugeTicksDoesNotOverflow() {
        assertEquals(Int.MAX_VALUE, BookProgressPolicy.ticksToPage(Long.MAX_VALUE))
    }

    @Test
    fun clampPageKeepsInteriorPages() {
        assertEquals(5, BookProgressPolicy.clampPage(5, 10))
    }

    @Test
    fun clampPageClampsToZeroBasedBounds() {
        assertEquals(0, BookProgressPolicy.clampPage(-1, 10))
        assertEquals(0, BookProgressPolicy.clampPage(0, 10))
        assertEquals(9, BookProgressPolicy.clampPage(9, 10))
        assertEquals(9, BookProgressPolicy.clampPage(10, 10))
    }

    @Test
    fun clampPageEmptyBookCollapsesToZero() {
        assertEquals(0, BookProgressPolicy.clampPage(0, 0))
        assertEquals(0, BookProgressPolicy.clampPage(3, 0))
        assertEquals(0, BookProgressPolicy.clampPage(-2, -1))
    }

    @Test
    fun percentToTicksFloorsPercentTimesMax() {
        assertEquals(0L, BookProgressPolicy.percentToTicks(0.0))
        assertEquals(2_500_000L, BookProgressPolicy.percentToTicks(0.25))
        assertEquals(5_000_000L, BookProgressPolicy.percentToTicks(0.5))
        assertEquals(BookProgressPolicy.TICKS_MAX_PERCENT, BookProgressPolicy.percentToTicks(1.0))
    }

    @Test
    fun percentToTicksClampsOutOfRangePercent() {
        assertEquals(0L, BookProgressPolicy.percentToTicks(-0.5))
        assertEquals(BookProgressPolicy.TICKS_MAX_PERCENT, BookProgressPolicy.percentToTicks(1.5))
    }

    @Test
    fun ticksToPercentClampsToUnitInterval() {
        assertEquals(0.0, BookProgressPolicy.ticksToPercent(-1L))
        assertEquals(0.0, BookProgressPolicy.ticksToPercent(0L))
        assertEquals(0.42, BookProgressPolicy.ticksToPercent(4_200_000L), 1e-12)
        assertEquals(1.0, BookProgressPolicy.ticksToPercent(10_000_000L))
        assertEquals(1.0, BookProgressPolicy.ticksToPercent(Long.MAX_VALUE))
    }

    @Test
    fun percentTicksRoundTripWithinFloorError() {
        for (i in 0..100) {
            val percent = i / 100.0
            val ticks = BookProgressPolicy.percentToTicks(percent)
            val back = BookProgressPolicy.ticksToPercent(ticks)
            kotlin.test.assertTrue(back in percent - 1e-7..percent + 1e-7, "percent=$percent back=$back")
        }
    }
}
