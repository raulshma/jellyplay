package com.raulshma.jellyplay.feature.book

import com.raulshma.jellyplay.core.datastore.reader.ReaderTheme
import com.raulshma.jellyplay.feature.book.epub.BookDeliveryTracker
import com.raulshma.jellyplay.feature.book.epub.EpubAppearance
import com.raulshma.jellyplay.feature.book.epub.EpubEvent
import com.raulshma.jellyplay.feature.book.epub.EpubEventListener
import com.raulshma.jellyplay.feature.book.epub.EpubReaderStatus
import com.raulshma.jellyplay.feature.book.epub.encodeBookIfDue
import com.raulshma.jellyplay.feature.book.epub.failBoot
import com.raulshma.jellyplay.feature.book.epub.sendBookIfDue
import com.raulshma.jellyplay.feature.book.epub.withStatusReceipt
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath

/**
 * Pins the boot-transfer ladder steps [BookTransferEffects] dispatches (the
 * composable shell is one-line effects; these are its Compose-free bodies —
 * the module's controller convention). The headline: an encode failure folds
 * into onFailBoot (the error veil, retryable) instead of crashing the
 * composition effect — the gap Android's hand-copied ladder carried — and
 * the generation gating keeps a delivered page from re-encoding, an
 * unloaded page from receiving the book, and a confirmed page from
 * re-sending.
 */
class BookTransferEffectsTest {

    private val book = "/cache/books/42.epub".toPath()
    private val appearance = EpubAppearance(theme = ReaderTheme.DARK, fontSizePx = 17)

    @Test
    fun `an encode failure fails the boot pins nothing and leaves the retry open`() = runTest {
        val delivery = BookDeliveryTracker()
        delivery.onPageLoadFinished()
        var failures = 0
        encodeBookIfDue(book, delivery, { throw IOException("disk") }) { failures++ }
        assertEquals(1, failures)
        assertNull(delivery.bookBase64, "a failed encode must not pin a payload")
        // Nothing latched on the failure: reopening (the error veil's retry)
        // re-runs the encode and pins normally.
        encodeBookIfDue(book, delivery, { "QUJD" }) { failures++ }
        assertEquals(1, failures)
        assertEquals("QUJD", delivery.bookBase64)
    }

    @Test
    fun `cancellation from the encode rethrows instead of failing the boot`() = runTest {
        val delivery = BookDeliveryTracker()
        delivery.onPageLoadFinished()
        var failures = 0
        assertFailsWith<CancellationException> {
            encodeBookIfDue(book, delivery, { throw CancellationException("leaving composition") }) { failures++ }
        }
        assertEquals(0, failures, "a disposed effect is not a failed boot")
    }

    @Test
    fun `the ladder sends the pinned payload once the page can receive it and stops at the receipt`() = runTest {
        val delivery = BookDeliveryTracker()
        val scripts = mutableListOf<String>()
        var encodes = 0
        fun encode(): suspend (okio.Path) -> String = { encodes++; "QUJD" }

        // Boot: a finished page load opens the generation, the encode pins.
        delivery.onPageLoadFinished()
        encodeBookIfDue(book, delivery, encode()) { error("encode failure is pinned above") }
        assertEquals(1, encodes)

        // A page whose JS is not reachable yet must not be sent the book
        // (a script before page-finished is a no-op).
        sendBookIfDue(delivery, 0.25, appearance, pageFinished = false, scripts::add)
        assertTrue(scripts.isEmpty(), "no send before the page finished loading")
        assertTrue(!delivery.shouldPushAppearance(), "no appearance push into a page without the book")

        // The page answers: the chunked transfer rides the eval seam
        // (begin + single chunk + end — "QUJD" is one BOOK_CHUNK_CHARS chunk).
        sendBookIfDue(delivery, 0.25, appearance, pageFinished = true, scripts::add)
        assertEquals(3, scripts.size)
        assertTrue(scripts.first().startsWith("window.jellyPlayReader.loadBookBegin("))

        // The receipt: any status event through the wrapped seam latches the
        // delivery (exactly the wiring BookTransferEffects returns), releases
        // the payload and bars re-send/re-encode for the confirmed page.
        val forwarded = mutableListOf<EpubEvent>()
        val wrapped = EpubEventListener { forwarded.add(it) }.withStatusReceipt { delivery.confirmDelivery() }
        wrapped.onEvent(EpubEvent.Status(EpubReaderStatus.LOADING))
        assertEquals(listOf<EpubEvent>(EpubEvent.Status(EpubReaderStatus.LOADING)), forwarded, "the wrap forwards untouched")
        if (delivery.shouldReleaseEncodedBook()) delivery.releaseEncodedBook()
        scripts.clear()
        sendBookIfDue(delivery, 0.25, appearance, pageFinished = true, scripts::add)
        assertTrue(scripts.isEmpty(), "a confirmed generation must not re-send")
        encodeBookIfDue(book, delivery, encode()) { error("no re-encode") }
        assertEquals(1, encodes, "the live page's delivery is the encoder's idempotency guard")
        assertTrue(delivery.shouldPushAppearance(), "the receipt opens the appearance push")
    }

    @Test
    fun `a reload after delivery re-runs the ladder for the fresh generation`() = runTest {
        val delivery = BookDeliveryTracker()
        val scripts = mutableListOf<String>()
        delivery.onPageLoadFinished()
        encodeBookIfDue(book, delivery, { "QUJD" }) { error("unreachable") }
        sendBookIfDue(delivery, 0.25, appearance, pageFinished = true, scripts::add)
        delivery.confirmDelivery()
        delivery.releaseEncodedBook()

        // The reload wiped reader.js and the book with it: a fresh encode (not
        // a re-send of the released payload) and a fresh send.
        delivery.onPageLoadFinished()
        var reloadEncodes = 0
        encodeBookIfDue(book, delivery, { reloadEncodes++; "QUJD" }) { error("unreachable") }
        assertEquals(1, reloadEncodes, "the fresh generation re-encodes (the boot's payload was released)")
        sendBookIfDue(delivery, 0.25, appearance, pageFinished = true, scripts::add)
        assertEquals(6, scripts.size, "the reload's generation receives its own transfer")
    }

    /**
     * The failure vocabulary itself: failBoot is an ERROR status the host must
     * emit on the RAW seam — routed through the receipt wrapper instead, the
     * very same event would latch a delivery for a page that never received
     * the book, and shouldEncode would bar the retry's encode forever.
     */
    @Test
    fun `failBoot is a plain error status on the raw seam`() {
        val raw = mutableListOf<EpubEvent>()
        EpubEventListener { raw.add(it) }.failBoot()
        assertEquals(listOf<EpubEvent>(EpubEvent.Status(EpubReaderStatus.ERROR)), raw)
    }
}
