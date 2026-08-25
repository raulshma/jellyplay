package com.raulshma.jellyplay.feature.home

import kotlin.test.assertEquals
import kotlin.test.Test

/**
 * Pure-state tests for [ScrollPositionStore] — no coroutines, no mocks.
 * Migrated from HomeViewModelTest.saveHomeScrollPosition_storesPositiveValues_
 * andClampsNegatives (the VM wrappers are now one-line delegates), plus the
 * reset behaviour that used to be pinned only via the refresh test.
 */
class ScrollPositionStoreTest {

    @Test
    fun save_storesPositiveValues() {
        val store = ScrollPositionStore()

        store.save(3, 150)

        val pos = store.get()
        assertEquals(3, pos.firstVisibleItemIndex)
        assertEquals(150, pos.firstVisibleItemScrollOffset)
    }

    @Test
    fun save_clampsNegativesToZero() {
        val store = ScrollPositionStore()

        store.save(-10, -50)

        val pos = store.get()
        assertEquals(0, pos.firstVisibleItemIndex)
        assertEquals(0, pos.firstVisibleItemScrollOffset)
    }

    @Test
    fun reset_returnsAnchorToTop() {
        val store = ScrollPositionStore()
        store.save(7, 200)

        store.reset()

        assertEquals(HomeScrollPosition(), store.get())
    }

    @Test
    fun get_defaultsToZeroBeforeFirstSave() {
        assertEquals(HomeScrollPosition(), ScrollPositionStore().get())
    }
}
