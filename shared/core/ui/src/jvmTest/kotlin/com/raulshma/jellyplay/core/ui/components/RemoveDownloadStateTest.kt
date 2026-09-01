package com.raulshma.jellyplay.core.ui.components

import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import org.junit.Assert.assertSame
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure-logic tests for [RemoveDownloadState] — the hoisted pending-removal
 * holder behind the REMOVE_DOWNLOAD quick action (issue #147). No Compose UI:
 * mutableStateOf reads/writes outside composition are plain field access, so
 * these run on the JVM without a device (same pattern as ConfirmStateTest).
 */
class RemoveDownloadStateTest {

    private fun item(id: String) = MediaItem(id = id, name = "Item $id", mediaType = MediaType.MOVIE)

    @Test
    fun state_startsIdle() {
        val state = RemoveDownloadState()
        assertNull(state.pending)
    }

    @Test
    fun request_setsPending() {
        val state = RemoveDownloadState()
        val requested = item("m1")

        state.request(requested)

        assertSame(requested, state.pending)
    }

    @Test
    fun clear_nullsPending() {
        val state = RemoveDownloadState()
        state.request(item("m1"))

        state.clear()

        assertNull(state.pending)
    }

    @Test
    fun request_replacesPreviousItem() {
        val state = RemoveDownloadState()
        val first = item("m1")
        val second = item("m2")

        state.request(first)
        state.request(second)

        assertSame("second request replaces the first item", second, state.pending)
    }
}
