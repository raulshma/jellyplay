package com.raulshma.jellyplay.feature.book

import com.raulshma.jellyplay.feature.book.epub.EpubAnnotationSpec
import com.raulshma.jellyplay.feature.book.epub.EpubReaderHandle
import com.raulshma.jellyplay.feature.book.epub.EpubSpeechParagraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins [ReaderSpeechController]'s sentence loop: sequential utterances,
 * stop/pause/resume semantics (pause remembers the position, resume
 * re-speaks), skip ±1 sentence (within and across paragraphs) with the
 * chapter-end handoff past the last sentence, finish vs stop, and the
 * stale-utterance guard (a late onDone from a replaced utterance can never
 * advance the loop). Value-fake engine + recording host (the continuation's
 * direct seam), no mockk.
 */
class ReaderSpeechControllerTest {

    /** Recording host fake: the chapter continuation's direct seam. */
    private class FakeEpubHost : EpubReaderHandle {
        val log = mutableListOf<String>()
        override val viewerDownloadProgress: androidx.compose.runtime.State<Float?> =
            androidx.compose.runtime.mutableStateOf<Float?>(null)
        override fun next() { log.add("next") }
        override fun prev() { log.add("prev") }
        override fun goTo(href: String) { log.add("goTo:$href") }
        override fun goToCfi(cfi: String) { log.add("goToCfi:$cfi") }
        override fun setFlow(scrolled: Boolean) { log.add("setFlow:$scrolled") }
        override fun applyAnnotations(entries: List<EpubAnnotationSpec>) { log.add("apply:${entries.size}") }
        override fun addAnnotation(entry: EpubAnnotationSpec) { log.add("add:${entry.cfi}") }
        override fun removeAnnotation(cfi: String) { log.add("remove:$cfi") }
        override fun clearSelection() { log.add("clearSelection") }
        override fun search(query: String, token: Int) { log.add("search:$query/$token") }
        override fun requestSpeechContext(cfi: String?) { log.add("speechContext:$cfi") }
        override fun setAutoScroll(enabled: Boolean, pxPerSec: Int) { log.add("autoScroll:$enabled/$pxPerSec") }
    }

    private class FakeSpeechEngine : BookSpeechEngine {
        override val availability = kotlinx.coroutines.flow.MutableStateFlow(BookSpeechAvailability.AVAILABLE)
        val spoken = mutableListOf<String>()
        var configuredRate = 100
        var configuredPitch = 100
        var stopCount = 0

        /** Every issued onDone, in issue order — [completeStale] reaches into them. */
        private val issuedDones = mutableListOf<() -> Unit>()

        /** Index of the live utterance (the QUEUE_FLUSH head); -1 = none. */
        private var activeIndex = -1

        override fun configure(ratePercent: Int, pitchPercent: Int) {
            configuredRate = ratePercent
            configuredPitch = pitchPercent
        }

        override fun speak(text: String, onDone: () -> Unit) {
            spoken.add(text)
            issuedDones.add(onDone)
            activeIndex = issuedDones.lastIndex
        }

        override fun stop() {
            stopCount++
            activeIndex = -1
        }

        override fun shutdown() {}

        /** The engine's natural completion of the current utterance. */
        fun complete() {
            if (activeIndex < 0) return
            val index = activeIndex
            activeIndex = -1
            issuedDones[index]()
        }

        /** A LATE completion of utterance [index] (already replaced/stopped). */
        fun completeStale(index: Int) {
            issuedDones[index]()
        }
    }

    private fun paragraphs(vararg texts: String): List<EpubSpeechParagraph> =
        texts.mapIndexed { index, text -> EpubSpeechParagraph(cfi = "epubcfi(/6/4!/4/$index)", text = text) }

    @Test
    fun `loop speaks paragraphs in order then hands off at chapter end`() {
        val engine = FakeSpeechEngine()
        val host = FakeEpubHost()
        var spoke = mutableListOf<Pair<Int, String>>()
        val controller = ReaderSpeechController(
            engine = engine,
            scope = CoroutineScope(UnconfinedTestDispatcher()),
            host = { host },
            onSpeakParagraph = { index, cfi -> spoke.add(index to cfi) },
            onFinished = {},
        )

        controller.start(paragraphs("one", "two"))
        assertEquals(listOf("one"), engine.spoken)
        assertEquals(0 to "epubcfi(/6/4!/4/0)", spoke.single())
        assertEquals(0, controller.state.value.spokenParagraphIndex)
        assertTrue(controller.state.value.active)

        engine.complete()
        assertEquals(listOf("one", "two"), engine.spoken)
        assertEquals(1, controller.state.value.spokenParagraphIndex)

        engine.complete()
        // Paragraphs exhausted: the controller turns the page itself through
        // the host seam, session stays live with no paragraph index.
        assertEquals(1, host.log.count { it == "next" })
        assertTrue(controller.state.value.active)
        assertNull(controller.state.value.spokenParagraphIndex)
    }

    @Test
    fun `stop silences and clears without late completion advancing`() {
        val engine = FakeSpeechEngine()
        val controller = ReaderSpeechController(
            engine = engine,
            scope = CoroutineScope(UnconfinedTestDispatcher()),
            host = { null },
            onSpeakParagraph = { _, _ -> },
            onFinished = {},
        )
        controller.start(paragraphs("one", "two"))
        controller.stop()
        assertEquals(ReaderSpeechState(), controller.state.value)

        // A late onDone from the stopped utterance must be inert.
        engine.completeStale(0)
        assertEquals(listOf("one"), engine.spoken)
        assertEquals(ReaderSpeechState(), controller.state.value)
    }

    @Test
    fun `a superseded utterances late completion cannot double advance`() {
        val engine = FakeSpeechEngine()
        val controller = ReaderSpeechController(
            engine = engine,
            scope = CoroutineScope(UnconfinedTestDispatcher()),
            host = { null },
            onSpeakParagraph = { _, _ -> },
            onFinished = {},
        )
        controller.start(paragraphs("one", "two"))
        controller.skipForward() // utterance 0 superseded by utterance 1

        engine.completeStale(0) // late done of the replaced utterance
        assertEquals(1, controller.state.value.spokenParagraphIndex)

        engine.complete() // the real completion of utterance 1
        assertNull(controller.state.value.spokenParagraphIndex) // chapter end reached exactly once
    }

    @Test
    fun `pause remembers the index and resume re-speaks the same paragraph`() {
        val engine = FakeSpeechEngine()
        val controller = ReaderSpeechController(
            engine = engine,
            scope = CoroutineScope(UnconfinedTestDispatcher()),
            host = { null },
            onSpeakParagraph = { _, _ -> },
            onFinished = {},
        )
        controller.start(paragraphs("one", "two"))
        engine.complete() // now on paragraph 1

        controller.pause()
        assertTrue(controller.state.value.active)
        assertTrue(controller.state.value.paused)
        assertEquals(1, controller.state.value.spokenParagraphIndex)
        // pause silenced the engine (stop #1 came from start's own reset).
        val stopsAtPause = engine.stopCount
        assertTrue(stopsAtPause >= 1)

        controller.resume()
        assertFalse(controller.state.value.paused)
        // Resume re-speaks the CURRENT paragraph from its start.
        assertEquals(listOf("one", "two", "two"), engine.spoken)
    }

    @Test
    fun `skip forward moves one paragraph and past the last hands off`() {
        val engine = FakeSpeechEngine()
        val host = FakeEpubHost()
        val controller = ReaderSpeechController(
            engine = engine,
            scope = CoroutineScope(UnconfinedTestDispatcher()),
            host = { host },
            onSpeakParagraph = { _, _ -> },
            onFinished = {},
        )
        controller.start(paragraphs("one", "two", "three"))

        controller.skipForward()
        assertEquals(listOf("one", "two"), engine.spoken)
        assertEquals(1, controller.state.value.spokenParagraphIndex)

        controller.skipForward()
        assertEquals(listOf("one", "two", "three"), engine.spoken)

        controller.skipForward() // past the last → chapter end (the host turn)
        assertEquals(1, host.log.count { it == "next" })
        assertNull(controller.state.value.spokenParagraphIndex)
    }

    @Test
    fun `skip back clamps at the first paragraph`() {
        val engine = FakeSpeechEngine()
        val controller = ReaderSpeechController(
            engine = engine,
            scope = CoroutineScope(UnconfinedTestDispatcher()),
            host = { null },
            onSpeakParagraph = { _, _ -> },
            onFinished = {},
        )
        controller.start(paragraphs("one", "two"))
        engine.complete()

        controller.skipBack()
        controller.skipBack() // below zero clamps at 0
        assertEquals(0, controller.state.value.spokenParagraphIndex)
        assertEquals(listOf("one", "two", "one", "one"), engine.spoken)
    }

    @Test
    fun `finish fires onFinished and stop never does`() {
        val engine = FakeSpeechEngine()
        var finished = 0
        val controller = ReaderSpeechController(
            engine = engine,
            scope = CoroutineScope(UnconfinedTestDispatcher()),
            host = { null },
            onSpeakParagraph = { _, _ -> },
            onFinished = { finished++ },
        )
        controller.start(paragraphs("one"))
        controller.stop()
        assertEquals(0, finished)

        controller.start(paragraphs("one"))
        controller.finish()
        assertEquals(1, finished)
        assertEquals(ReaderSpeechState(), controller.state.value)
    }

    @Test
    fun `engine lost ends the session through onError`() {
        val engine = FakeSpeechEngine()
        var errors = 0
        var finished = 0
        val controller = ReaderSpeechController(
            engine = engine,
            scope = CoroutineScope(UnconfinedTestDispatcher()),
            host = { null },
            onSpeakParagraph = { _, _ -> },
            onFinished = { finished++ },
            onError = { errors++ },
        )
        controller.start(paragraphs("one"))
        controller.engineLost()
        assertEquals(1, errors)
        assertEquals(0, finished)
        assertEquals(ReaderSpeechState(), controller.state.value)
    }

    @Test
    fun `empty chapter takes the chapter-end path immediately`() {
        val engine = FakeSpeechEngine()
        val host = FakeEpubHost()
        val controller = ReaderSpeechController(
            engine = engine,
            scope = CoroutineScope(UnconfinedTestDispatcher()),
            host = { host },
            onSpeakParagraph = { _, _ -> },
            onFinished = {},
        )
        controller.start(paragraphs())
        assertTrue(engine.spoken.isEmpty())
        assertEquals(1, host.log.count { it == "next" })
        assertTrue(controller.state.value.active)
    }

    @Test
    fun `await context marks the session live and asks the host for the chapter`() {
        val engine = FakeSpeechEngine()
        val host = FakeEpubHost()
        val controller = ReaderSpeechController(
            engine = engine,
            scope = CoroutineScope(UnconfinedTestDispatcher()),
            host = { host },
            onSpeakParagraph = { _, _ -> },
            onFinished = {},
        )
        controller.awaitContext(cfi = "epubcfi(/6/4!/4/2)")
        assertEquals(ReaderSpeechState(active = true), controller.state.value)
        assertTrue(engine.spoken.isEmpty())
        assertEquals(listOf("speechContext:epubcfi(/6/4!/4/2)"), host.log)
    }

    @Test
    fun `multi sentence paragraph speaks sentence by sentence`() {
        val engine = FakeSpeechEngine()
        var spoke = mutableListOf<Pair<Int, String>>()
        val controller = ReaderSpeechController(
            engine = engine,
            scope = CoroutineScope(UnconfinedTestDispatcher()),
            host = { null },
            onSpeakParagraph = { index, cfi -> spoke.add(index to cfi) },
            onFinished = {},
        )
        controller.start(paragraphs("First one. Second two! Third three?"))

        engine.complete()
        engine.complete()
        assertEquals(
            listOf("First one.", "Second two!", "Third three?"),
            engine.spoken,
        )
        // Every sentence reports its own paragraph (index/CFI stay paragraph-scoped).
        assertEquals(List(3) { 0 to "epubcfi(/6/4!/4/0)" }, spoke)
        assertEquals(0, controller.state.value.spokenParagraphIndex)
    }

    @Test
    fun `skip moves one sentence staying inside the same paragraph`() {
        val engine = FakeSpeechEngine()
        val controller = ReaderSpeechController(
            engine = engine,
            scope = CoroutineScope(UnconfinedTestDispatcher()),
            host = { null },
            onSpeakParagraph = { _, _ -> },
            onFinished = {},
        )
        controller.start(paragraphs("First one. Second two. Third three."))

        controller.skipForward()
        assertEquals(listOf("First one.", "Second two."), engine.spoken)
        assertEquals(0, controller.state.value.spokenParagraphIndex)

        controller.skipBack()
        assertEquals(listOf("First one.", "Second two.", "First one."), engine.spoken)
        assertEquals(0, controller.state.value.spokenParagraphIndex)
    }

    @Test
    fun `split sentences keeps decimals and trailing quotes attached`() {
        assertEquals(
            listOf("It costs 3.14 dollars.", "He said \"go.\"", "No final mark"),
            splitSentences("It costs 3.14 dollars. He said \"go.\" No final mark"),
        )
        // Documented heuristic limit: an abbreviation period followed by a
        // space looks like a boundary, so it splits — coarser skip granularity
        // only; the units concatenate back to the paragraph.
        assertEquals(
            listOf("Mr.", "Brown arrived."),
            splitSentences("Mr. Brown arrived."),
        )
        assertEquals(emptyList(), splitSentences("   "))
    }
}

/**
 * Continuation-protocol pins at the controller interface (the guards the VM
 * harness used to be the only way to reach): relocation release, the
 * book-end identity guard, the empty-chapter percent-stall guard, and the
 * timeout that re-requests when a book-end turn never relocates.
 */
class ReaderSpeechContinuationTest {

    private class FakeSpeechEngine : BookSpeechEngine {
        override val availability = MutableStateFlow(BookSpeechAvailability.AVAILABLE)
        val spoken = mutableListOf<String>()
        private var pendingDone: (() -> Unit)? = null
        override fun configure(ratePercent: Int, pitchPercent: Int) {}
        override fun speak(text: String, onDone: () -> Unit) {
            spoken.add(text)
            pendingDone = onDone
        }
        override fun stop() { pendingDone = null }
        override fun shutdown() {}
        fun complete() {
            val done = pendingDone
            pendingDone = null
            done?.invoke()
        }
    }

    /** Minimal host fake: records the continuation's two direct host touches. */
    private class RecordingHost : EpubReaderHandle {
        var turns = 0
            private set
        val contextRequests = mutableListOf<String?>()
        override val viewerDownloadProgress: androidx.compose.runtime.State<Float?> =
            androidx.compose.runtime.mutableStateOf<Float?>(null)
        override fun next() { turns++ }
        override fun prev() {}
        override fun goTo(href: String) {}
        override fun goToCfi(cfi: String) {}
        override fun setFlow(scrolled: Boolean) {}
        override fun applyAnnotations(entries: List<EpubAnnotationSpec>) {}
        override fun addAnnotation(entry: EpubAnnotationSpec) {}
        override fun removeAnnotation(cfi: String) {}
        override fun clearSelection() {}
        override fun search(query: String, token: Int) {}
        override fun requestSpeechContext(cfi: String?) { contextRequests.add(cfi) }
        override fun setAutoScroll(enabled: Boolean, pxPerSec: Int) {}
    }

    private fun paragraphs(vararg texts: String): List<EpubSpeechParagraph> =
        texts.mapIndexed { index, text -> EpubSpeechParagraph(cfi = "epubcfi(/6/8!/4/$index)", text = text) }

    @Test
    fun `relocation releases the advance wait and re-requests the current chapter`() = runTest {
        val engine = FakeSpeechEngine()
        val host = RecordingHost()
        val controller = ReaderSpeechController(
            engine = engine,
            scope = backgroundScope,
            host = { host },
            onSpeakParagraph = { _, _ -> },
        )
        // Non-null start cfi so the INITIAL context request is distinguishable
        // from the null re-requests the continuation protocol issues.
        controller.awaitContext("epubcfi(/start)")
        controller.onSpeechContext(paragraphs("one"), currentPercent = 0.1)
        advanceUntilIdle()
        engine.complete() // chapter end → the direct host turn + armed wait
        assertEquals(1, host.turns)

        // Relocation arrives BEFORE the timeout: exactly one re-request (the
        // trailing null), and the timeout never contributes a second one.
        controller.onRelocated()
        advanceTimeBy(10_000)
        advanceUntilIdle()
        assertEquals(listOf("epubcfi(/start)", null), host.contextRequests)
    }

    @Test
    fun `timeout re-requests when a book-end turn never relocates`() = runTest {
        val engine = FakeSpeechEngine()
        val host = RecordingHost()
        val controller = ReaderSpeechController(
            engine = engine,
            scope = backgroundScope,
            host = { host },
            onSpeakParagraph = { _, _ -> },
        )
        // Non-null start cfi: the initial request must not pollute the
        // null-re-request counts below.
        controller.awaitContext("epubcfi(/start)")
        controller.onSpeechContext(paragraphs("one"), currentPercent = 0.1)
        advanceUntilIdle()
        engine.complete()

        advanceTimeBy(1_499)
        runCurrent()
        assertEquals(0, host.contextRequests.count { it == null }, "the wait is bounded but not eager")
        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, host.contextRequests.count { it == null }, "the timeout re-requests at 1500 ms")
    }

    @Test
    fun `same-chapter context after a turn finishes the session`() = runTest {
        val engine = FakeSpeechEngine()
        val controller = ReaderSpeechController(
            engine = engine,
            scope = backgroundScope,
            host = { null },
            onSpeakParagraph = { _, _ -> },
        )
        controller.awaitContext()
        val chapter = paragraphs("one")
        controller.onSpeechContext(chapter, currentPercent = 0.1)
        advanceUntilIdle()
        engine.complete() // chapter end; the turn never relocates
        advanceTimeBy(2_000)
        advanceUntilIdle()

        // The turn's context request comes back with the SAME chapter — the
        // identity guard finishes instead of looping.
        controller.onSpeechContext(chapter, currentPercent = 0.2)
        assertFalse(controller.state.value.active)
    }

    @Test
    fun `empty chapter with a stalled percent finishes instead of looping`() = runTest {
        val engine = FakeSpeechEngine()
        val controller = ReaderSpeechController(
            engine = engine,
            scope = backgroundScope,
            host = { null },
            onSpeakParagraph = { _, _ -> },
        )
        controller.awaitContext()
        controller.onSpeechContext(paragraphs("one"), currentPercent = 0.5)
        advanceUntilIdle()
        controller.reportPercent(0.5) // the position is current AT the turn (the VM reports every event)
        engine.complete()
        advanceTimeBy(2_000)
        advanceUntilIdle()

        // Empty context AND the percent never moved since the turn: done.
        controller.onSpeechContext(emptyList(), currentPercent = 0.5)
        assertFalse(controller.state.value.active)

        // An empty context whose percent DID move keeps advancing instead.
        controller.awaitContext()
        controller.onSpeechContext(paragraphs("two"), currentPercent = 0.7)
        advanceUntilIdle()
        controller.reportPercent(0.7)
        engine.complete()
        advanceTimeBy(2_000)
        advanceUntilIdle()
        controller.onSpeechContext(emptyList(), currentPercent = 0.8)
        assertTrue(controller.state.value.active)
    }
}
