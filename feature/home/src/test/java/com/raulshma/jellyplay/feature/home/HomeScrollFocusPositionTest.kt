package com.raulshma.jellyplay.feature.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeScrollFocusPositionTest {

    // ─── HomeScrollPosition ───────────────────────────────────────────────────

    @Test
    fun scrollPosition_defaultValues() {
        val pos = HomeScrollPosition()
        assertEquals(0, pos.firstVisibleItemIndex)
        assertEquals(0, pos.firstVisibleItemScrollOffset)
    }

    @Test
    fun scrollPosition_savePositiveValues() {
        val index = 7
        val offset = 250
        val pos = HomeScrollPosition(
            firstVisibleItemIndex = index.coerceAtLeast(0),
            firstVisibleItemScrollOffset = offset.coerceAtLeast(0),
        )
        assertEquals(7, pos.firstVisibleItemIndex)
        assertEquals(250, pos.firstVisibleItemScrollOffset)
    }

    @Test
    fun scrollPosition_negativeIndexClampsToZero() {
        val index = (-5).coerceAtLeast(0)
        val pos = HomeScrollPosition(firstVisibleItemIndex = index)
        assertEquals(0, pos.firstVisibleItemIndex)
    }

    @Test
    fun scrollPosition_negativeOffsetClampsToZero() {
        val offset = (-100).coerceAtLeast(0)
        val pos = HomeScrollPosition(firstVisibleItemScrollOffset = offset)
        assertEquals(0, pos.firstVisibleItemScrollOffset)
    }

    @Test
    fun scrollPosition_zeroRemainsZero() {
        val pos = HomeScrollPosition(
            firstVisibleItemIndex = 0.coerceAtLeast(0),
            firstVisibleItemScrollOffset = 0.coerceAtLeast(0),
        )
        assertEquals(0, pos.firstVisibleItemIndex)
        assertEquals(0, pos.firstVisibleItemScrollOffset)
    }

    @Test
    fun scrollPosition_resetReturnsDefault() {
        var pos = HomeScrollPosition(firstVisibleItemIndex = 10, firstVisibleItemScrollOffset = 500)
        pos = HomeScrollPosition()
        assertEquals(0, pos.firstVisibleItemIndex)
        assertEquals(0, pos.firstVisibleItemScrollOffset)
    }

    @Test
    fun scrollPosition_dataClassEquality() {
        val a = HomeScrollPosition(3, 50)
        val b = HomeScrollPosition(3, 50)
        assertEquals(a, b)
    }

    @Test
    fun scrollPosition_dataClassInequality() {
        val a = HomeScrollPosition(3, 50)
        val b = HomeScrollPosition(3, 51)
        assert(a != b)
    }

    @Test
    fun scrollPosition_copyUpdatesIndex() {
        val pos = HomeScrollPosition(2, 100)
        val updated = pos.copy(firstVisibleItemIndex = 5)
        assertEquals(5, updated.firstVisibleItemIndex)
        assertEquals(100, updated.firstVisibleItemScrollOffset)
    }

    // ─── HomeFocusPosition ────────────────────────────────────────────────────

    @Test
    fun focusPosition_defaultValues() {
        val pos = HomeFocusPosition()
        assertEquals(0, pos.sectionIndex)
        assertEquals(0, pos.itemIndex)
    }

    @Test
    fun focusPosition_savePositiveValues() {
        val sectionIndex = 3
        val itemIndex = 2
        val pos = HomeFocusPosition(
            sectionIndex = sectionIndex.coerceAtLeast(0),
            itemIndex = itemIndex.coerceAtLeast(0),
        )
        assertEquals(3, pos.sectionIndex)
        assertEquals(2, pos.itemIndex)
    }

    @Test
    fun focusPosition_negativeSectionIndexClampsToZero() {
        val sectionIndex = (-1).coerceAtLeast(0)
        val pos = HomeFocusPosition(sectionIndex = sectionIndex)
        assertEquals(0, pos.sectionIndex)
    }

    @Test
    fun focusPosition_negativeItemIndexClampsToZero() {
        val itemIndex = (-99).coerceAtLeast(0)
        val pos = HomeFocusPosition(itemIndex = itemIndex)
        assertEquals(0, pos.itemIndex)
    }

    @Test
    fun focusPosition_resetReturnsDefault() {
        var pos = HomeFocusPosition(sectionIndex = 5, itemIndex = 10)
        pos = HomeFocusPosition()
        assertEquals(0, pos.sectionIndex)
        assertEquals(0, pos.itemIndex)
    }

    @Test
    fun focusPosition_dataClassEquality() {
        val a = HomeFocusPosition(1, 2)
        val b = HomeFocusPosition(1, 2)
        assertEquals(a, b)
    }

    @Test
    fun focusPosition_dataClassInequality() {
        val a = HomeFocusPosition(1, 2)
        val b = HomeFocusPosition(1, 3)
        assert(a != b)
    }

    @Test
    fun focusPosition_copyUpdatesSectionIndex() {
        val pos = HomeFocusPosition(1, 2)
        val updated = pos.copy(sectionIndex = 4)
        assertEquals(4, updated.sectionIndex)
        assertEquals(2, updated.itemIndex)
    }
}
