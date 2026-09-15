package com.raulshma.jellyplay.feature.book

import com.raulshma.jellyplay.core.data.repository.ReaderAnnotation
import com.raulshma.jellyplay.core.data.repository.ReaderAnnotationColor
import com.raulshma.jellyplay.core.data.repository.ReaderAnnotationStyle
import com.raulshma.jellyplay.feature.book.epub.EpubAnnotationColor
import com.raulshma.jellyplay.feature.book.epub.EpubAnnotationSpec
import com.raulshma.jellyplay.feature.book.epub.EpubAnnotationStyle
import com.raulshma.jellyplay.feature.book.epub.EpubReaderHandle
import com.raulshma.jellyplay.feature.book.epub.EpubReaderStatus
import com.raulshma.jellyplay.feature.book.epub.EpubSearchResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the reflowable reader's Compose-free controllers
 * ([ReaderAnnotationSync], [ReaderAutoScroll]) — the behaviour that used to
 * live inline in the 600-line composable, most load-bearing the
 * ephemeral-highlight precedence rule: an ephemeral paint never lands on a
 * CFI a persisted mark occupies. (The tap decision moved to ReaderInput.kt;
 * its pins live in ReaderInputTest.)
 */
class ReaderControllersTest {

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

    // ------------------------------------------------------------------
    // ReaderAnnotationSync
    // ------------------------------------------------------------------

    @Test
    fun `first READY applies the full set and later changes sync incrementally`() {
        val host = FakeHost()
        val sync = ReaderAnnotationSync { host }

        sync.sync(EpubReaderStatus.LOADING, listOf(annotation("a"), annotation("b")))
        assertTrue(host.log.isEmpty()) // nothing before READY

        sync.sync(EpubReaderStatus.READY, listOf(annotation("a"), annotation("b")))
        assertEquals(listOf("apply:2"), host.log)

        host.log.clear()
        // Add + remove + re-style: one remove, one add, nothing else.
        val styledC = annotation("c").copy(color = ReaderAnnotationColor.BLUE)
        sync.sync(EpubReaderStatus.READY, listOf(annotation("b"), styledC))
        assertEquals(listOf("remove:a", "add:c"), host.log)

        host.log.clear()
        // Unchanged list: a no-op (the search flash must survive unrelated recompositions).
        sync.sync(EpubReaderStatus.READY, listOf(annotation("b"), styledC))
        assertTrue(host.log.isEmpty())
    }

    @Test
    fun `leaving READY resets the full-apply latch`() {
        val host = FakeHost()
        val sync = ReaderAnnotationSync { host }
        sync.sync(EpubReaderStatus.READY, listOf(annotation("a")))
        assertEquals(listOf("apply:1"), host.log)

        sync.sync(EpubReaderStatus.LOADING, emptyList())
        sync.sync(EpubReaderStatus.READY, listOf(annotation("a")))
        assertEquals("apply:1", host.log.last())
    }

    @Test
    fun `ephemeral paints never land on a persisted cfi`() {
        val host = FakeHost()
        val sync = ReaderAnnotationSync { host }
        sync.sync(EpubReaderStatus.READY, listOf(annotation("marked")))
        host.log.clear()

        assertFalse(sync.paintEphemeral(EphemeralHighlight.SEARCH, "marked"))
        assertTrue(host.log.none { it.startsWith("add:") })
        assertTrue(sync.paintEphemeral(EphemeralHighlight.SEARCH, "free"))
        assertEquals("add:free", host.log.last())
    }

    @Test
    fun `ephemeral kinds clear independently and never wipe each other`() {
        val host = FakeHost()
        val sync = ReaderAnnotationSync { host }
        sync.sync(EpubReaderStatus.READY, emptyList())

        assertTrue(sync.paintEphemeral(EphemeralHighlight.SEARCH, "s1"))
        assertTrue(sync.paintEphemeral(EphemeralHighlight.SPEECH, "p1"))
        sync.clearEphemeral(EphemeralHighlight.SEARCH)
        assertTrue("remove:s1" in host.log)
        assertTrue(host.painted.containsKey("p1"))

        // Clearing an empty slot is a no-op (no host touch).
        host.log.clear()
        sync.clearEphemeral(EphemeralHighlight.SEARCH)
        assertTrue(host.log.isEmpty())
    }

    @Test
    fun `speech follow repaints its own highlight and jumps`() {
        val host = FakeHost()
        val sync = ReaderAnnotationSync { host }
        sync.sync(EpubReaderStatus.READY, emptyList())

        sync.followSpeech("p1")
        sync.followSpeech("p2")

        assertEquals(listOf("apply:0", "add:p1", "goToCfi:p1", "remove:p1", "add:p2", "goToCfi:p2"), host.log)
    }

    // ------------------------------------------------------------------
    // ReaderAutoScroll
    // ------------------------------------------------------------------

    @Test
    fun `toggle flips and commands the host at the session speed`() {
        val host = FakeHost()
        var speed = 40
        val autoScroll = ReaderAutoScroll(host = { host }, speed = { speed })

        autoScroll.toggle()
        assertTrue(autoScroll.active)
        assertEquals("autoScroll:true/40", host.log.single())

        speed = 90
        autoScroll.retarget()
        assertEquals("autoScroll:true/90", host.log.last())

        autoScroll.stopForSleepTimer()
        assertFalse(autoScroll.active)
        assertEquals("autoScroll:false/90", host.log.last())
    }

    @Test
    fun `stop paths are no-ops while inactive and host-reported stops reset the flag`() {
        val host = FakeHost()
        val autoScroll = ReaderAutoScroll(host = { host }, speed = { 40 })

        autoScroll.stopForFlowExit()
        assertTrue(host.log.isEmpty())

        autoScroll.toggle()
        host.log.clear()
        autoScroll.onHostStopped() // reader.js saw wheel/touch
        assertFalse(autoScroll.active)
        assertTrue(host.log.isEmpty()) // the host already stopped itself
    }
}


/** Pins the search session's token stamping and late-result drop. */
class ReaderSearchSessionTest {

    private fun result(cfi: String) = EpubSearchResult(cfi = cfi, chapter = "Ch 1", excerpt = cfi)

    @Test
    fun `launchSearch stamps a fresh token and flips to Searching`() {
        val session = ReaderSearchSession()
        val stamped = mutableListOf<Int>()
        session.launchSearch("dune") { _, token -> stamped += token }
        session.launchSearch("arrakis") { _, token -> stamped += token }
        assertEquals(listOf(1, 2), stamped)
        assertEquals(ReaderSearchState.Searching, session.state)
    }

    @Test
    fun `late results of older queries drop`() {
        val session = ReaderSearchSession()
        var handedToken = 0
        session.launchSearch("first") { _, token -> handedToken = token }
        session.launchSearch("second") { _, token -> handedToken = token }
        val staleToken = handedToken - 1

        session.onResults(staleToken, listOf(result("epubcfi(/old)")))
        assertEquals(ReaderSearchState.Searching, session.state, "a stale token changes nothing")

        session.onResults(handedToken, listOf(result("epubcfi(/new)")))
        assertEquals(ReaderSearchState.Results(listOf(result("epubcfi(/new)"))), session.state)
    }

    @Test
    fun `reset returns the session to idle`() {
        val session = ReaderSearchSession()
        session.launchSearch("query") { _, _ -> }
        session.reset()
        assertEquals(ReaderSearchState.Idle, session.state)
    }
}
