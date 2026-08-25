package com.raulshma.jellyplay.feature.home

import androidx.compose.foundation.lazy.LazyListState
import kotlin.test.assertEquals
import kotlin.test.Test

class HomeScrollStateTest {

    @Test
    fun homeScrollPosition_defaultValuesAreZero() {
        val position = HomeScrollPosition()
        assertEquals(0, position.firstVisibleItemIndex)
        assertEquals(0, position.firstVisibleItemScrollOffset)
    }

    @Test
    fun homeScrollPosition_customValues_preserved() {
        val position = HomeScrollPosition(firstVisibleItemIndex = 4, firstVisibleItemScrollOffset = 120)
        assertEquals(4, position.firstVisibleItemIndex)
        assertEquals(120, position.firstVisibleItemScrollOffset)
    }

    @Test
    fun homeScrollState_holdsLazyListState() {
        val lazyListState = LazyListState(firstVisibleItemIndex = 2, firstVisibleItemScrollOffset = 50)
        var savedIndex = 0
        var savedOffset = 0

        val scrollState = HomeScrollState(
            listState = lazyListState,
            savePosition = { idx, off ->
                savedIndex = idx
                savedOffset = off
            },
        )

        assertEquals(2, scrollState.listState.firstVisibleItemIndex)
        assertEquals(50, scrollState.listState.firstVisibleItemScrollOffset)
    }
}
