package com.raulshma.jellyplay.feature.book

import com.raulshma.jellyplay.core.datastore.reader.ReaderTheme
import com.raulshma.jellyplay.feature.book.epub.BookDeliveryTracker
import com.raulshma.jellyplay.feature.book.epub.EpubAppearance
import com.raulshma.jellyplay.feature.book.epub.EpubBookTransfer
import com.raulshma.jellyplay.feature.book.epub.EpubEvent
import com.raulshma.jellyplay.feature.book.epub.EpubEventListener
import com.raulshma.jellyplay.feature.book.epub.EpubReaderStatus
import com.raulshma.jellyplay.feature.book.epub.buildLoadBookAbortScript
import com.raulshma.jellyplay.feature.book.epub.buildLoadBookEndScript
import com.raulshma.jellyplay.feature.book.epub.failBoot
import com.raulshma.jellyplay.feature.book.epub.transferBookIfDue
import com.raulshma.jellyplay.feature.book.epub.withStatusReceipt
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath

/**
 * Pins the boot-transfer ladder steps [BookTransferEffects] dispatches (the
 * composable shell is two-line effects; these are its Compose-free bodies —
 * the module's controller convention). The headline: a failed open or a
 * failed mid-stream read folds into onFailBoot (the error veil, retryable)
 * and drops the JS side's partial assembly, instead of crashing the
 * composition effect — and the generation gating keeps a confirmed page from
 * re-receiving the book while an unloaded page gets nothing.
 */
class BookTransferEffectsTest {

    private val book = "/cache/books/42.epub".toPath()
    private val appearance = EpubAppearance(theme = ReaderTheme.DARK, fontSizePx = 17)

    /**
     * A transfer source yielding [chunks] verbatim. [chunks] are pre-encoded
     * base64 (the real source reads bytes + encodes inside nextChunkBase64 —
     * the framing under test does not care); [totalBytes] is the begin
     * frame's header, not derived from the chunks.
     */
    private class FakeTransfer(
        private val chunks: List<String>,
        override val totalBytes: Long = chunks.size * 3L,
    ) : EpubBookTransfer {
        var closed = false
        private var next = 0
        override suspend fun nextChunkBase64(): String? = chunks.getOrNull(next++)
        override fun close() {
            closed = true
        }
    }

    @Test
    fun `an open failure fails the boot aborts the page assembly and leaves the retry open`() = runTest {
        val delivery = BookDeliveryTracker()
        delivery.onPageLoadFinished()
        val evaluated = mutableListOf<String>()
        var failures = 0
        var opens = 0
        val failingOpener: suspend (okio.Path) -> EpubBookTransfer = {
            opens++
            throw IOException("disk")
        }
        transferBookIfDue(book, delivery, 0.25, appearance, pageFinished = true, evaluated::add, failingOpener) { failures++ }
        assertEquals(1, failures)
        assertEquals(1, opens)
        assertEquals(buildLoadBookAbortScript(), evaluated.single(), "a failed open aborts any JS assembly")
        // Nothing latched on the failure: reopening (the error veil's retry)
        // re-runs the transfer normally.
        val retryOpener: suspend (okio.Path) -> EpubBookTransfer = { FakeTransfer(listOf("QUJD")) }
        transferBookIfDue(book, delivery, 0.25, appearance, pageFinished = true, evaluated::add, retryOpener) { failures++ }
        assertEquals(1, failures)
        assertEquals(4, evaluated.size, "the retry streams begin + chunk + end after the abort")
    }

    @Test
    fun `cancellation from the transfer rethrows instead of failing the boot`() = runTest {
        val delivery = BookDeliveryTracker()
        delivery.onPageLoadFinished()
        var failures = 0
        val cancelledOpener: suspend (okio.Path) -> EpubBookTransfer = {
            throw CancellationException("leaving composition")
        }
        assertFailsWith<CancellationException> {
            transferBookIfDue(book, delivery, 0.25, appearance, pageFinished = true, {}, cancelledOpener) { failures++ }
        }
        assertEquals(0, failures, "a disposed effect is not a failed boot")
    }

    @Test
    fun `a mid-stream read failure aborts the assembly and fails the boot`() = runTest {
        val delivery = BookDeliveryTracker()
        delivery.onPageLoadFinished()
        val failing = object : EpubBookTransfer {
            override val totalBytes: Long = 9L
            override suspend fun nextChunkBase64(): String? = throw IOException("read")
            override fun close() = Unit
        }
        val evaluated = mutableListOf<String>()
        var failures = 0
        val opener: suspend (okio.Path) -> EpubBookTransfer = { failing }
        transferBookIfDue(book, delivery, 0.25, appearance, pageFinished = true, evaluated::add, opener) { failures++ }
        assertEquals(1, failures)
        assertEquals(
            listOf(
                "window.jellyPlayReader.loadBookBegin(9, 0.25, " +
                    "\"{\\\"theme\\\":\\\"dark\\\",\\\"fontSize\\\":17,\\\"flow\\\":\\\"paginated\\\"}\")",
                buildLoadBookAbortScript(),
            ),
            evaluated,
            "begin went out, the read failed, the partial JS assembly was aborted and the boot failed",
        )
    }

    @Test
    fun `the ladder streams the book once the page can receive it and stops at the receipt`() = runTest {
        val delivery = BookDeliveryTracker()
        val evaluated = mutableListOf<String>()
        var opens = 0
        val opener: suspend (okio.Path) -> EpubBookTransfer = {
            opens++
            FakeTransfer(listOf("QUJD"), totalBytes = 3)
        }

        // Boot: a finished page load opens the generation.
        delivery.onPageLoadFinished()

        // A page whose JS is not reachable yet must not be sent the book
        // (a script before page-finished is a no-op) — and the source must
        // not even open.
        transferBookIfDue(book, delivery, 0.25, appearance, pageFinished = false, evaluated::add, opener) {
            fail("no send before the page finished")
        }
        assertEquals(0, opens, "no source opens for an unreachable page")
        assertTrue(evaluated.isEmpty(), "no send before the page finished loading")
        assertTrue(!delivery.shouldPushAppearance(), "no appearance push into a page without the book")

        // The page answers: the streamed transfer rides the eval seam
        // (begin with the totalBytes header + single chunk + end).
        transferBookIfDue(book, delivery, 0.25, appearance, pageFinished = true, evaluated::add, opener) { fail("unreachable") }
        assertEquals(1, opens)
        assertEquals(
            listOf(
                "window.jellyPlayReader.loadBookBegin(3, 0.25, " +
                    "\"{\\\"theme\\\":\\\"dark\\\",\\\"fontSize\\\":17,\\\"flow\\\":\\\"paginated\\\"}\")",
                "window.jellyPlayReader.loadBookChunk(0, \"QUJD\")",
                buildLoadBookEndScript(),
            ),
            evaluated,
        )

        // The receipt: any status event through the wrapped seam latches the
        // delivery (exactly the wiring BookTransferEffects returns) and bars
        // a re-transfer for the confirmed page.
        val forwarded = mutableListOf<EpubEvent>()
        val wrapped = EpubEventListener { forwarded.add(it) }.withStatusReceipt { delivery.confirmDelivery() }
        wrapped.onEvent(EpubEvent.Status(EpubReaderStatus.LOADING))
        assertEquals(listOf<EpubEvent>(EpubEvent.Status(EpubReaderStatus.LOADING)), forwarded, "the wrap forwards untouched")
        evaluated.clear()
        transferBookIfDue(book, delivery, 0.25, appearance, pageFinished = true, evaluated::add, opener) { fail("unreachable") }
        assertEquals(1, opens, "a confirmed generation must not re-open the source, let alone re-send")
        assertTrue(evaluated.isEmpty(), "a confirmed generation must not re-send")
        assertTrue(delivery.shouldPushAppearance(), "the receipt opens the appearance push")
    }

    @Test
    fun `a reload after delivery re-streams the book for the fresh generation`() = runTest {
        val delivery = BookDeliveryTracker()
        val evaluated = mutableListOf<String>()
        val opener: suspend (okio.Path) -> EpubBookTransfer = {
            FakeTransfer(listOf("QUJD", "REVGOklOSVQ="), totalBytes = 11)
        }
        delivery.onPageLoadFinished()
        transferBookIfDue(book, delivery, 0.25, appearance, pageFinished = true, evaluated::add, opener) { fail("unreachable") }
        delivery.confirmDelivery()

        // The reload wiped reader.js and the book with it: the fresh
        // generation receives its own full streamed transfer.
        delivery.onPageLoadFinished()
        transferBookIfDue(book, delivery, 0.25, appearance, pageFinished = true, evaluated::add, opener) { fail("unreachable") }
        assertEquals(8, evaluated.size, "each generation gets begin + 2 chunks + end (4 + 4 here)")
        assertEquals(buildLoadBookEndScript(), evaluated.last())
        assertContains(evaluated, "window.jellyPlayReader.loadBookChunk(1, \"REVGOklOSVQ=\")")
    }

    /**
     * The failure vocabulary itself: failBoot is an ERROR status the host must
     * emit on the RAW seam — routed through the receipt wrapper instead, the
     * very same event would latch a delivery for a page that never received
     * the book, and shouldSend would bar the retry's transfer forever.
     */
    @Test
    fun `failBoot is a plain error status on the raw seam`() {
        val raw = mutableListOf<EpubEvent>()
        EpubEventListener { raw.add(it) }.failBoot()
        assertEquals(listOf<EpubEvent>(EpubEvent.Status(EpubReaderStatus.ERROR)), raw)
    }
}
