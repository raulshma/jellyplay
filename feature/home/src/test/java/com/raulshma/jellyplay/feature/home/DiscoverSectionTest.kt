package com.raulshma.jellyplay.feature.home

import org.junit.Assert.assertEquals
import org.junit.Test

class DiscoverSectionTest {

    @Test
    fun ratingToSizeFraction_null_returnsDefault() {
        assertEquals(1.0f, ratingToSizeFraction(null), 0.0001f)
    }

    @Test
    fun ratingToSizeFraction_topTier_aboveNinePointTwo_returnsOnePointTwo() {
        assertEquals(1.2f, ratingToSizeFraction(9.2f), 0.0001f)
        assertEquals(1.2f, ratingToSizeFraction(9.5f), 0.0001f)
        assertEquals(1.2f, ratingToSizeFraction(10.0f), 0.0001f)
    }

    @Test
    fun ratingToSizeFraction_highTier_eightPointFiveToNinePointOne_returnsOnePointOne() {
        assertEquals(1.1f, ratingToSizeFraction(8.5f), 0.0001f)
        assertEquals(1.1f, ratingToSizeFraction(9.1f), 0.0001f)
    }

    @Test
    fun ratingToSizeFraction_goodTier_sevenPointFivetoEightPointFoure_returnsOne() {
        assertEquals(1.0f, ratingToSizeFraction(7.5f), 0.0001f)
        assertEquals(1.0f, ratingToSizeFraction(8.4f), 0.0001f)
    }

    @Test
    fun ratingToSizeFraction_lowTier_belowSevenPointFiver_returnsZeroPointNine() {
        assertEquals(0.9f, ratingToSizeFraction(7.4f), 0.0001f)
        assertEquals(0.9f, ratingToSizeFraction(5.0f), 0.0001f)
        assertEquals(0.9f, ratingToSizeFraction(0f), 0.0001f)
    }

    @Test
    fun ratingToSizeFraction_boundary_justBelowNinePointTwo_isOnePointOne() {
        assertEquals(1.1f, ratingToSizeFraction(9.19f), 0.0001f)
    }
}
