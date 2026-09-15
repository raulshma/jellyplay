package com.raulshma.jellyplay.feature.book

import com.raulshma.jellyplay.feature.book.epub.BookDeliveryTracker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins BookDeliveryTracker — the pure gate core of the boot-transfer ladder
 * both EPUB hosts climb: encode → send → receipt → release, the appearance
 * gate, and the re-latch semantics that let a page reload never strand the
 * reader on the boot veil.
 */
class BookDeliveryTrackerTest {

    @Test
    fun `a fresh tracker encodes eagerly but cannot send push or release`() {
        val tracker = BookDeliveryTracker()
        assertEquals(0, tracker.pageGeneration)
        assertEquals(Int.MIN_VALUE, tracker.deliveredGeneration)
        // Encoding is plain file IO — the hosts start it at composition
        // (generation 0 already outranks the sentinel), page or not.
        assertTrue(tracker.shouldEncode())
        assertFalse(tracker.shouldSend(pageFinished = true))
        assertFalse(tracker.shouldPushAppearance())
        assertFalse(tracker.shouldReleaseEncodedBook())
    }

    @Test
    fun `a finished page load opens the send gate for the fresh generation`() {
        val tracker = BookDeliveryTracker()
        tracker.onPageLoadFinished()
        tracker.storeEncodedBook("QUJD")
        // The page's JS is not reachable — a script before page-finished is a no-op.
        assertFalse(tracker.shouldSend(pageFinished = false))
        assertTrue(tracker.shouldSend(pageFinished = true))
        // Nothing was delivered into this generation — a push would be a silent no-op.
        assertFalse(tracker.shouldPushAppearance())
    }

    @Test
    fun `storing the payload closes the encode gate but keeps the send gate open`() {
        val tracker = BookDeliveryTracker()
        tracker.onPageLoadFinished()
        tracker.storeEncodedBook("QUJD")
        assertEquals("QUJD", tracker.bookBase64)
        assertFalse(tracker.shouldEncode())
        assertTrue(tracker.shouldSend(pageFinished = true))
    }

    @Test
    fun `the delivery receipt latches the generation releases the payload and opens the push`() {
        val tracker = BookDeliveryTracker()
        tracker.onPageLoadFinished()
        tracker.storeEncodedBook("QUJD")
        tracker.confirmDelivery()
        assertEquals(1, tracker.deliveredGeneration)
        assertTrue(tracker.shouldReleaseEncodedBook())
        assertTrue(tracker.shouldPushAppearance())
        tracker.releaseEncodedBook()
        assertNull(tracker.bookBase64)
        assertFalse(tracker.shouldSend(pageFinished = true))
    }

    @Test
    fun `an unreceived reload re-sends the still-pinned payload without re-encoding`() {
        val tracker = BookDeliveryTracker()
        tracker.onPageLoadFinished()
        tracker.storeEncodedBook("QUJD")
        tracker.onPageLoadFinished() // reload wiped the page before the receipt landed
        assertFalse(tracker.shouldPushAppearance())
        assertFalse(tracker.shouldEncode()) // payload still pinned from the first encode
        assertTrue(tracker.shouldSend(pageFinished = true)) // but it must go out again
    }

    @Test
    fun `a reload after delivery re-encodes and the receipt re-latches onto the new generation`() {
        val tracker = BookDeliveryTracker()
        tracker.onPageLoadFinished()
        tracker.storeEncodedBook("QUJD")
        tracker.confirmDelivery()
        tracker.releaseEncodedBook()
        tracker.onPageLoadFinished() // reload: the page no longer holds the book
        assertTrue(tracker.shouldEncode()) // the fresh generation outranks the delivered one
        assertFalse(tracker.shouldPushAppearance())
        tracker.storeEncodedBook("REVGOklOSVQ=")
        assertTrue(tracker.shouldSend(pageFinished = true))
        tracker.confirmDelivery()
        assertEquals(2, tracker.deliveredGeneration)
        assertTrue(tracker.shouldPushAppearance())
    }

    @Test
    fun `the release gate stays shut until a real generation is vouched for`() {
        val tracker = BookDeliveryTracker()
        tracker.onPageLoadFinished()
        tracker.storeEncodedBook("QUJD")
        assertFalse(tracker.shouldReleaseEncodedBook())
        // The first live generation is 1 (0 is the pre-boot generation), so
        // the first receipt always latches a positive value — well clear of
        // the negative sentinel the gate rejects.
        tracker.confirmDelivery()
        assertEquals(1, tracker.deliveredGeneration)
        assertTrue(tracker.shouldReleaseEncodedBook())
    }
}
