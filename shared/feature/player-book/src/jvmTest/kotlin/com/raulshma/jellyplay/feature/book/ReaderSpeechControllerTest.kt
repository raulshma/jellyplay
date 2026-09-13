package com.raulshma.jellyplay.feature.book

import com.raulshma.jellyplay.feature.book.epub.EpubSpeechParagraph
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins [ReaderSpeechController]'s paragraph loop: sequential utterances,
 * stop/pause/resume semantics (pause remembers the index, resume re-speaks),
 * skip ±1 with the chapter-end handoff past the last paragraph, finish vs
 * stop, and the stale-utterance guard (a late onDone from a replaced
 * utterance can never advance the loop). Value-fake engine, no mockk.
 */
class ReaderSpeechControllerTest {

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
        var spoke = mutableListOf<Pair<Int, String>>()
        var chapterEnds = 0
        val controller = ReaderSpeechController(
            engine = engine,
            onSpeakParagraph = { index, cfi -> spoke.add(index to cfi) },
            onChapterEnd = { chapterEnds++ },
            onFinished = {},
        )

        controller.start(paragraphs("one", "two"))
        assertEquals(listOf("one"), engine.spoken)
        assertEquals(0 to "epubcfi(/6/4!/4/0)", spoke.single())
        assertEquals(0, controller.state.value.paragraphIndex)
        assertTrue(controller.state.value.active)

        engine.complete()
        assertEquals(listOf("one", "two"), engine.spoken)
        assertEquals(1, controller.state.value.paragraphIndex)

        engine.complete()
        // Paragraphs exhausted: chapter-end handoff, session stays live with
        // no paragraph index.
        assertEquals(1, chapterEnds)
        assertTrue(controller.state.value.active)
        assertNull(controller.state.value.paragraphIndex)
    }

    @Test
    fun `stop silences and clears without late completion advancing`() {
        val engine = FakeSpeechEngine()
        val controller = ReaderSpeechController(
            engine = engine,
            onSpeakParagraph = { _, _ -> },
            onChapterEnd = {},
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
            onSpeakParagraph = { _, _ -> },
            onChapterEnd = {},
            onFinished = {},
        )
        controller.start(paragraphs("one", "two"))
        controller.skipForward() // utterance 0 superseded by utterance 1

        engine.completeStale(0) // late done of the replaced utterance
        assertEquals(1, controller.state.value.paragraphIndex)

        engine.complete() // the real completion of utterance 1
        assertNull(controller.state.value.paragraphIndex) // chapter end reached exactly once
    }

    @Test
    fun `pause remembers the index and resume re-speaks the same paragraph`() {
        val engine = FakeSpeechEngine()
        val controller = ReaderSpeechController(
            engine = engine,
            onSpeakParagraph = { _, _ -> },
            onChapterEnd = {},
            onFinished = {},
        )
        controller.start(paragraphs("one", "two"))
        engine.complete() // now on paragraph 1

        controller.pause()
        assertTrue(controller.state.value.active)
        assertTrue(controller.state.value.paused)
        assertEquals(1, controller.state.value.paragraphIndex)
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
        var chapterEnds = 0
        val controller = ReaderSpeechController(
            engine = engine,
            onSpeakParagraph = { _, _ -> },
            onChapterEnd = { chapterEnds++ },
            onFinished = {},
        )
        controller.start(paragraphs("one", "two", "three"))

        controller.skipForward()
        assertEquals(listOf("one", "two"), engine.spoken)
        assertEquals(1, controller.state.value.paragraphIndex)

        controller.skipForward()
        assertEquals(listOf("one", "two", "three"), engine.spoken)

        controller.skipForward() // past the last → chapter end
        assertEquals(1, chapterEnds)
        assertNull(controller.state.value.paragraphIndex)
    }

    @Test
    fun `skip back clamps at the first paragraph`() {
        val engine = FakeSpeechEngine()
        val controller = ReaderSpeechController(
            engine = engine,
            onSpeakParagraph = { _, _ -> },
            onChapterEnd = {},
            onFinished = {},
        )
        controller.start(paragraphs("one", "two"))
        engine.complete()

        controller.skipBack()
        controller.skipBack() // below zero clamps at 0
        assertEquals(0, controller.state.value.paragraphIndex)
        assertEquals(listOf("one", "two", "one", "one"), engine.spoken)
    }

    @Test
    fun `finish fires onFinished and stop never does`() {
        val engine = FakeSpeechEngine()
        var finished = 0
        val controller = ReaderSpeechController(
            engine = engine,
            onSpeakParagraph = { _, _ -> },
            onChapterEnd = {},
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
            onSpeakParagraph = { _, _ -> },
            onChapterEnd = {},
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
        var chapterEnds = 0
        val controller = ReaderSpeechController(
            engine = engine,
            onSpeakParagraph = { _, _ -> },
            onChapterEnd = { chapterEnds++ },
            onFinished = {},
        )
        controller.start(paragraphs())
        assertTrue(engine.spoken.isEmpty())
        assertEquals(1, chapterEnds)
        assertTrue(controller.state.value.active)
    }

    @Test
    fun `await context marks the session live without paragraphs`() {
        val engine = FakeSpeechEngine()
        val controller = ReaderSpeechController(
            engine = engine,
            onSpeakParagraph = { _, _ -> },
            onChapterEnd = {},
            onFinished = {},
        )
        controller.awaitContext()
        assertEquals(ReaderSpeechState(active = true), controller.state.value)
        assertTrue(engine.spoken.isEmpty())
    }
}
