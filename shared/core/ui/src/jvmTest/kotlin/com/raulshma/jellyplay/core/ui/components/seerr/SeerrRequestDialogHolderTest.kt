package com.raulshma.jellyplay.core.ui.components.seerr

import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * Pins [SeerrRequestDialogHolder] — the request dialog's presentation cell,
 * ported from the dialog-choreography region of core:data's
 * `SeerrRequestStateHolderTest` when the dialog vocabulary moved out of the
 * data layer. The two invariants survive the move verbatim: the item is
 * FROZEN at open time (only open/dismiss write it — an in-dialog state
 * change can never rewrite the item the dialog was opened for), and dismiss
 * drops the item BEFORE clearing the request result, so the result banner
 * never outlives the dialog it belongs to (the same rule as feature:home's
 * `HomeDialogSession.dismissSeerrRequest`). The data half of the open
 * cascade (service details + TV seasons) rides the holder's `prepare` seam,
 * recorded here as plain lambdas — no delegate mock needed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SeerrRequestDialogHolderTest {

    /** Records the choreography: every prepare/clear call, in order. */
    private fun recordingHolder(calls: MutableList<String>): SeerrRequestDialogHolder =
        SeerrRequestDialogHolder(
            prepare = { calls += "prepare(${it.id}:${it.mediaType})" },
            clearRequestResult = { calls += "clearResult" },
        )

    @Test
    fun `open publishes the item and fires prepare with the same frozen instance`() {
        val calls = mutableListOf<String>()
        val h = recordingHolder(calls)

        val item = SeerrSearchItem(id = 42, mediaType = "tv")
        h.open(item)

        // The render gate flips to the exact instance opened; the open
        // cascade rides the prepare seam with that same instance, once.
        assertSame(item, h.item.value)
        assertEquals(listOf("prepare(42:tv)"), calls)
    }

    @Test
    fun `the open item is frozen - only a later open or dismiss rewrites it`() {
        val calls = mutableListOf<String>()
        val h = recordingHolder(calls)
        val item = SeerrSearchItem(id = 4, mediaType = "tv")
        h.open(item)

        // After the full open choreography the dialog still renders the item
        // it was opened with — the cell has no other writer, so whatever
        // happens around the open dialog (e.g. the request result changing)
        // travels through the result field, not by rewriting the item. The
        // old `requestMedia leaves dialogItem untouched` row, re-pinned at
        // the dialog cell's new home.
        assertSame(item, h.item.value)
        assertEquals(listOf("prepare(4:tv)"), calls)

        // A re-open replaces the frozen item — the new dialog's own item.
        val next = SeerrSearchItem(id = 5, mediaType = "movie")
        h.open(next)
        assertSame(next, h.item.value)
        assertEquals(listOf("prepare(4:tv)", "prepare(5:movie)"), calls)
    }

    @Test
    fun `dismiss drops the item THEN clears the request result in that order`() = runTest {
        val calls = mutableListOf<String>()
        val h = recordingHolder(calls)
        h.open(SeerrSearchItem(id = 3, mediaType = "movie"))

        // Live collection so dismiss's two writes are observed as separate
        // events — the item drops FIRST, the result clear SECOND (the old
        // data-holder emission-order row, re-pinned at the dialog cell's new
        // home).
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            h.item.collect { calls += if (it == null) "item=null" else "item=${it.id}" }
        }
        calls.clear()

        h.dismiss()

        assertNull(h.item.value)
        assertEquals(listOf("item=null", "clearResult"), calls)
    }
}
