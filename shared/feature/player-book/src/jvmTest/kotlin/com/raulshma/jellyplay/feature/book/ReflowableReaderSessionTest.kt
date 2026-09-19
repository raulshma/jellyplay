package com.raulshma.jellyplay.feature.book

import com.raulshma.jellyplay.core.data.repository.ReaderAnnotation
import com.raulshma.jellyplay.core.data.repository.ReaderAnnotationColor
import com.raulshma.jellyplay.core.data.repository.ReaderAnnotationStyle
import com.raulshma.jellyplay.core.datastore.reader.ReadingDirection
import com.raulshma.jellyplay.feature.book.epub.EpubAnnotationSpec
import com.raulshma.jellyplay.feature.book.epub.EpubEvent
import com.raulshma.jellyplay.feature.book.epub.EpubReaderHandle
import com.raulshma.jellyplay.feature.book.epub.EpubReaderStatus
import com.raulshma.jellyplay.feature.book.epub.EpubSearchResult
import com.raulshma.jellyplay.feature.book.epub.EpubTapZone
import com.raulshma.jellyplay.feature.book.epub.EpubTocItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins [ReflowableReaderSession] — the reflowable reader's Compose-free
 * session (the half [ReflowableReaderContent] used to inline): the
 * exact-resume latch, the JS-tap routing guards, the search choreography,
 * the event-funnel split (screen-owned folds vs forwarded kinds) and the
 * [ReaderSessionPort] hooks the ViewModel's speech loop and sleep timer
 * execute through. Recording host fake (ReaderControllersTest's pattern),
 * no Compose, no mockk.
 */
class ReflowableReaderSessionTest {

    /** Recording fake of the host: a write log + painted-map assertions. */
    private class FakeHost : EpubReaderHandle {
        val log = mutableListOf<String>()
        val painted = mutableMapOf<String, EpubAnnotationSpec>()
        override val viewerDownloadProgress: androidx.compose.runtime.State<Float?> =
            androidx.compose.runtime.mutableStateOf<Float?>(null)
        override fun next() { log.add("next") }
        override fun prev() { log.add("prev") }
        override fun goTo(href: String) { log.add("goTo:$href") }
        override fun goToCfi(cfi: String) { log.add("goToCfi:$cfi") }
        override fun setFlow(scrolled: Boolean) { log.add("setFlow:$scrolled") }
        override fun applyAnnotations(entries: List<EpubAnnotationSpec>) {
            log.add("apply:${entries.size}")
            painted.clear()
            entries.forEach { painted[it.cfi] = it }
        }
        override fun addAnnotation(entry: EpubAnnotationSpec) {
            log.add("add:${entry.cfi}")
            painted[entry.cfi] = entry
        }
        override fun removeAnnotation(cfi: String) {
            log.add("remove:$cfi")
            painted.remove(cfi)
        }
        override fun clearSelection() { log.add("clearSelection") }
        override fun search(query: String, token: Int) { log.add("search:$query/$token") }
        override fun requestSpeechContext(cfi: String?) { log.add("speechContext:$cfi") }
        override fun setAutoScroll(enabled: Boolean, pxPerSec: Int) {
            log.add("autoScroll:$enabled/$pxPerSec")
        }
    }

    /** Session + its constructor-lambda inputs, all mutable at test time. */
    private class Harness(
        var direction: ReadingDirection = ReadingDirection.LTR,
        var sheetOpen: Boolean = false,
        var selectionActive: Boolean = false,
        var speed: Int = 40,
    ) {
        var toggles = 0
        val forwarded = mutableListOf<EpubEvent>()
        val host = FakeHost()
        val session = ReflowableReaderSession(
            direction = { direction },
            sheetOpen = { sheetOpen },
            selectionActive = { selectionActive },
            autoScrollSpeed = { speed },
            onToggleControls = { toggles++ },
            forward = { forwarded.add(it) },
        )

        /** The composition's DisposableEffect binding. */
        fun bind() = session.attachHost(host)

        /** Drives the boot status the way the host event would. */
        fun reachReady() {
            session.onEvent.onEvent(EpubEvent.Status(EpubReaderStatus.READY))
        }
    }

    private fun annotation(cfi: String) = ReaderAnnotation(
        id = cfi.hashCode().toLong(),
        itemId = "item-1",
        cfi = cfi,
        style = ReaderAnnotationStyle.HIGHLIGHT,
        color = ReaderAnnotationColor.YELLOW,
        anchorText = "text",
        note = null,
        chapterLabel = "",
        createdAt = 0L,
        updatedAt = 0L,
    )

    private fun result(cfi: String) = EpubSearchResult(cfi = cfi, chapter = "Ch 1", excerpt = cfi)

    // ------------------------------------------------------------------
    // Exact resume latch
    // ------------------------------------------------------------------

    @Test
    fun `resume jump waits for READY fires once and prefers the deep link`() {
        val h = Harness()
        h.bind()
        h.session.resumeJump("epubcfi(/resume)", null)
        assertTrue(h.host.log.isEmpty()) // LOADING: nothing jumps yet

        h.reachReady()
        h.session.resumeJump("epubcfi(/resume)", "chapter2.xhtml")
        assertEquals(listOf("goTo:chapter2.xhtml"), h.host.log) // jumpHref outranks the anchor

        h.session.resumeJump("epubcfi(/resume)", null)
        assertEquals(1, h.host.log.size) // the latch consumed the one jump
    }

    @Test
    fun `resume jump anchors at the stored cfi without a deep link`() {
        val h = Harness()
        h.bind()
        h.reachReady()
        h.session.resumeJump("epubcfi(/6/12!/4/2)", null)
        assertEquals(listOf("goToCfi:epubcfi(/6/12!/4/2)"), h.host.log)
    }

    @Test
    fun `null anchors consume the latch with no jump`() {
        val h = Harness()
        h.bind()
        h.reachReady()
        h.session.resumeJump(null, null)
        assertTrue(h.host.log.isEmpty()) // the percent resume stands
        h.session.resumeJump("epubcfi(/late)", null)
        assertTrue(h.host.log.isEmpty()) // ...and no late CFI can jump afterwards
    }

    // ------------------------------------------------------------------
    // JS-tap routing (the readerNavDecision input fold)
    // ------------------------------------------------------------------

    @Test
    fun `taps page direction-aware and drop the search flash first`() {
        val h = Harness()
        h.bind()
        h.reachReady()
        h.session.syncAnnotations(emptyList())
        assertTrue(h.session.annotationSync.paintEphemeral(EphemeralHighlight.SEARCH, "flash"))
        h.host.log.clear()

        h.session.onJsTap(EpubTapZone.RIGHT)
        assertEquals(listOf("remove:flash", "next"), h.host.log)

        h.host.log.clear()
        h.session.onJsTap(EpubTapZone.LEFT)
        assertEquals(listOf("prev"), h.host.log)

        h.host.log.clear()
        h.direction = ReadingDirection.RTL
        h.session.onJsTap(EpubTapZone.RIGHT) // RIGHT pages backward under RTL
        assertEquals(listOf("prev"), h.host.log)
    }

    @Test
    fun `sheet-open and selection taps are pure noise`() {
        val h = Harness()
        h.bind()
        h.sheetOpen = true
        h.session.onJsTap(EpubTapZone.RIGHT)
        h.session.onJsTap(EpubTapZone.CENTER)
        assertTrue(h.host.log.isEmpty())
        assertEquals(0, h.toggles)

        h.sheetOpen = false
        h.selectionActive = true
        h.session.onJsTap(EpubTapZone.LEFT)
        assertTrue(h.host.log.isEmpty())
    }

    @Test
    fun `center tap toggles the chrome without touching the host`() {
        val h = Harness()
        h.bind()
        h.session.onJsTap(EpubTapZone.CENTER)
        assertEquals(1, h.toggles)
        assertTrue(h.host.log.isEmpty())
    }

    @Test
    fun `a detached host degrades taps to no-ops`() {
        val h = Harness()
        h.bind()
        h.session.attachHost(null) // the dispose path
        h.session.onJsTap(EpubTapZone.RIGHT)
        assertTrue(h.host.log.isEmpty())
    }

    // ------------------------------------------------------------------
    // Search choreography
    // ------------------------------------------------------------------

    @Test
    fun `search clears the stale flash stamps the token and scans the host`() {
        val h = Harness()
        h.bind()
        h.reachReady()
        h.session.syncAnnotations(emptyList())
        h.session.annotationSync.paintEphemeral(EphemeralHighlight.SEARCH, "old")
        h.host.log.clear()

        h.session.search("dune")
        assertEquals(listOf("remove:old", "search:dune/1"), h.host.log)
        assertEquals(ReaderSearchState.Searching, h.session.searchSession.state)
    }

    @Test
    fun `late results of older tokens drop through the event funnel`() {
        val h = Harness()
        h.bind()
        h.session.search("first")
        h.session.search("second")
        val staleToken = 1 // the first scan's stamp

        h.session.onEvent.onEvent(EpubEvent.SearchResults(staleToken, listOf(result("epubcfi(/old)"))))
        assertEquals(ReaderSearchState.Searching, h.session.searchSession.state)

        h.session.onEvent.onEvent(EpubEvent.SearchResults(2, listOf(result("epubcfi(/new)"))))
        assertEquals(ReaderSearchState.Results(listOf(result("epubcfi(/new)"))), h.session.searchSession.state)
    }

    @Test
    fun `result taps jump and paint the precedence-guarded flash`() {
        val h = Harness()
        h.bind()
        h.reachReady()
        h.session.syncAnnotations(listOf(annotation("marked")))

        h.session.openSearchResult(result("marked"))
        assertEquals(listOf("apply:1", "goToCfi:marked"), h.host.log) // persisted mark: no flash painted

        h.session.openSearchResult(result("free"))
        // Original order pinned: jump FIRST, then the ephemeral flash paint
        // (painting before the jump would let the relocation wipe it).
        assertEquals(listOf("apply:1", "goToCfi:marked", "goToCfi:free", "add:free"), h.host.log)
        assertTrue(h.host.painted.containsKey("free")) // ephemeral flash painted
    }

    @Test
    fun `resetSearch drops the flash and returns the session to idle`() {
        val h = Harness()
        h.bind()
        h.reachReady()
        h.session.syncAnnotations(emptyList())
        h.session.search("dune")
        h.host.log.clear()

        h.session.resetSearch()
        assertEquals(ReaderSearchState.Idle, h.session.searchSession.state)
        assertTrue(h.host.log.isEmpty()) // nothing painted → nothing removed

        h.session.annotationSync.paintEphemeral(EphemeralHighlight.SEARCH, "flash")
        h.host.log.clear()
        h.session.resetSearch()
        assertEquals(listOf("remove:flash"), h.host.log)
    }

    // ------------------------------------------------------------------
    // The event-funnel split
    // ------------------------------------------------------------------

    @Test
    fun `screen-owned kinds fold session-side and the rest ride the funnel`() {
        val h = Harness()
        h.bind()

        h.session.onEvent.onEvent(EpubEvent.Status(EpubReaderStatus.LOCATIONS_READY))
        assertEquals(EpubReaderStatus.LOCATIONS_READY, h.session.status)
        assertTrue(h.forwarded.isEmpty()) // the veil state is screen-local

        val toc = listOf(EpubTocItem(label = "One", href = "one.xhtml"))
        h.session.onEvent.onEvent(EpubEvent.Toc(toc))
        assertEquals(toc, h.session.tocItems) // the sheet/tick-rail mirror
        assertEquals(listOf<EpubEvent>(EpubEvent.Toc(toc)), h.forwarded) // and the VM caches its copy

        h.session.onEvent.onEvent(EpubEvent.Percent(0.4))
        assertEquals(EpubEvent.Percent(0.4), h.forwarded.last()) // percent is the VM's
        assertEquals(EpubReaderStatus.LOCATIONS_READY, h.session.status)
    }

    @Test
    fun `locationsReady arriving after READY does not regress session status`() {
        val h = Harness()
        h.bind()
        h.reachReady()
        assertEquals(EpubReaderStatus.READY, h.session.status)

        h.session.onEvent.onEvent(EpubEvent.Status(EpubReaderStatus.LOCATIONS_READY))
        assertEquals(EpubReaderStatus.READY, h.session.status)

        // But a real error or reload-triggered loading still transitions
        h.session.onEvent.onEvent(EpubEvent.Status(EpubReaderStatus.ERROR))
        assertEquals(EpubReaderStatus.ERROR, h.session.status)
    }

    @Test
    fun `host-reported auto-scroll stops reset the active flag`() {
        val h = Harness()
        h.bind()
        h.session.autoScroll.toggle()
        assertTrue(h.session.autoScroll.active)

        h.session.onEvent.onEvent(EpubEvent.AutoScrollStopped)
        assertFalse(h.session.autoScroll.active)
        assertTrue(h.host.log.none { it.startsWith("autoScroll:false") }) // the host stopped itself
    }

    // ------------------------------------------------------------------
    // Flow flips + the ReaderSessionPort hooks
    // ------------------------------------------------------------------

    @Test
    fun `flow changes push setFlow and leaving scrolled kills auto-scroll`() {
        val h = Harness()
        h.bind()
        h.session.autoScroll.toggle()
        h.host.log.clear()

        h.session.onFlowChanged(false)
        assertEquals(listOf("setFlow:false", "autoScroll:false/40"), h.host.log)
        assertFalse(h.session.autoScroll.active)
    }

    @Test
    fun `followSpeech delegates to the annotation sync and sleep stops the scroller`() {
        val h = Harness()
        h.bind()
        h.reachReady()
        h.session.syncAnnotations(emptyList())
        h.session.autoScroll.toggle()
        h.host.log.clear()

        h.session.followSpeech("para1")
        assertEquals(listOf("add:para1", "goToCfi:para1"), h.host.log)

        h.session.sleepTimerFired()
        assertFalse(h.session.autoScroll.active)
        assertEquals("autoScroll:false/40", h.host.log.last())
    }

    @Test
    fun `speech session end drops the painted speech highlight`() {
        val h = Harness()
        h.bind()
        h.reachReady()
        h.session.syncAnnotations(emptyList())
        h.session.followSpeech("para1")
        h.host.log.clear()

        h.session.onSpeechActiveChanged(true) // still speaking: the paint stays
        assertTrue(h.host.log.isEmpty())

        h.session.onSpeechActiveChanged(false)
        assertEquals(listOf("remove:para1"), h.host.log)
    }
}
