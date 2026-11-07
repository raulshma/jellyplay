package com.raulshma.jellyplay.feature.book

import com.raulshma.jellyplay.feature.book.epub.BookDeliveryTracker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins BookDeliveryTracker — the pure gate core of the boot-transfer ladder
 * both EPUB hosts climb: send → receipt, the appearance gate, and the
 * re-latch semantics that let a page reload never strand the reader on the
 * boot veil. There is no payload gate: the book streams from disk per chunk
 * (EpubBookCodec), so nothing whole-book-sized is ever pinned.
 */
class BookDeliveryTrackerTest {

    @Test
    fun `a fresh tracker cannot send or push`() {
        val tracker = BookDeliveryTracker()
        assertEquals(0, tracker.pageGeneration)
        assertEquals(Int.MIN_VALUE, tracker.deliveredGeneration)
        assertFalse(tracker.shouldSend(pageFinished = true))
        assertFalse(tracker.shouldPushAppearance())
    }

    @Test
    fun `a finished page load opens the send gate for the fresh generation`() {
        val tracker = BookDeliveryTracker()
        tracker.onPageLoadFinished()
        // The page's JS is not reachable — a script before page-finished is a no-op.
        assertFalse(tracker.shouldSend(pageFinished = false))
        assertTrue(tracker.shouldSend(pageFinished = true))
        // Nothing was delivered into this generation — a push would be a silent no-op.
        assertFalse(tracker.shouldPushAppearance())
    }

    @Test
    fun `the delivery receipt latches the generation closes the send gate and opens the push`() {
        val tracker = BookDeliveryTracker()
        tracker.onPageLoadFinished()
        tracker.confirmDelivery()
        assertEquals(1, tracker.deliveredGeneration)
        assertFalse(tracker.shouldSend(pageFinished = true), "a confirmed generation must not re-send")
        assertTrue(tracker.shouldPushAppearance())
    }

    @Test
    fun `an unreceived reload keeps the send gate open for the fresh generation`() {
        val tracker = BookDeliveryTracker()
        tracker.onPageLoadFinished()
        tracker.onPageLoadFinished() // reload wiped the page before the receipt landed
        assertFalse(tracker.shouldPushAppearance())
        assertTrue(tracker.shouldSend(pageFinished = true)) // the fresh page needs the book again
    }

    @Test
    fun `a reload after delivery re-opens the send gate and the receipt re-latches onto the new generation`() {
        val tracker = BookDeliveryTracker()
        tracker.onPageLoadFinished()
        tracker.confirmDelivery()
        tracker.onPageLoadFinished() // reload: the page no longer holds the book
        assertTrue(tracker.shouldSend(pageFinished = true)) // the fresh generation outranks the delivered one
        assertFalse(tracker.shouldPushAppearance())
        tracker.confirmDelivery()
        assertEquals(2, tracker.deliveredGeneration)
        assertTrue(tracker.shouldPushAppearance())
    }
}
