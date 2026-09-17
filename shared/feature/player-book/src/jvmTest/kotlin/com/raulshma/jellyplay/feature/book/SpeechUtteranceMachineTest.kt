package com.raulshma.jellyplay.feature.book

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins [SpeechUtteranceMachine]'s utterance decisions over a fake
 * [TtsBinding] (the seam's second adapter — the first is the Android
 * TextToSpeech translator): speak-before-init parks ONE utterance and
 * dispatches on a usable init, the availability fold (init failure and a
 * no-language engine both go UNAVAILABLE), the single-active completion
 * guard (a late done from a stopped or superseded utterance is inert), and
 * the stop/error-completes semantics. Value fakes, no mockk.
 */
class SpeechUtteranceMachineTest {

    /** Recording binding fake: no platform, completions fired by hand. */
    private class FakeTtsBinding : TtsBinding {
        override var host: TtsBinding.Host? = null

        var startedCount = 0
        var languageUsable = true
        var configuredRate = 100
        var configuredPitch = 100
        var stopCount = 0
        var shutdownCount = 0

        /** Spoken (text, utteranceId) pairs in dispatch order. */
        val spoken = mutableListOf<Pair<String, String>>()

        override fun ensureStarted() {
            startedCount++
        }

        override fun isDefaultLanguageUsable(): Boolean = languageUsable

        override fun configure(ratePercent: Int, pitchPercent: Int) {
            configuredRate = ratePercent
            configuredPitch = pitchPercent
        }

        override fun speak(text: String, utteranceId: String) {
            spoken.add(text to utteranceId)
        }

        override fun stopPlatform() {
            stopCount++
        }

        override fun shutdown() {
            shutdownCount++
        }

        /** The platform's natural completion of utterance [utteranceId]. */
        fun complete(utteranceId: String) {
            host?.onUtteranceDone(utteranceId)
        }

        /** The platform reporting utterance [utteranceId] failed. */
        fun fail(utteranceId: String) {
            host?.onUtteranceError(utteranceId)
        }

        /** The platform init settling (adapter folds its success constant first). */
        fun init(succeeded: Boolean) {
            host?.onInit(succeeded)
        }

        /** The id the machine generated for the Nth spoken utterance. */
        fun idAt(index: Int): String = spoken[index].second
    }

    private fun machine(binding: FakeTtsBinding = FakeTtsBinding()): Pair<SpeechUtteranceMachine, FakeTtsBinding> {
        val m = SpeechUtteranceMachine(binding)
        return m to binding
    }

    // ------------------------------------------------------------------
    // Pending park until ready
    // ------------------------------------------------------------------

    @Test
    fun `speak before init parks and dispatches on init success`() {
        val (m, tts) = machine()

        var done = 0
        m.speak("one") { done++ }
        // The binding started (lazy creation on first speak) but nothing is
        // spoken yet, and the machine is in its INITIALIZING window.
        assertEquals(1, tts.startedCount)
        assertTrue(tts.spoken.isEmpty())
        assertEquals(BookSpeechAvailability.INITIALIZING, m.availability.value)

        tts.init(succeeded = true)
        assertEquals(listOf("one"), tts.spoken.map { it.first })
        assertEquals(BookSpeechAvailability.AVAILABLE, m.availability.value)

        tts.complete(tts.idAt(0))
        assertEquals(1, done)
    }

    @Test
    fun `a second speak before init replaces the parked utterance`() {
        val (m, tts) = machine()
        m.speak("one") {}
        m.speak("two") {} // parked slot holds ONE utterance
        assertEquals(1, tts.startedCount, "ensureStarted is not re-fired while parked")

        tts.init(succeeded = true)
        assertEquals(listOf("two"), tts.spoken.map { it.first })
    }

    @Test
    fun `init failure drops the pending utterance and goes UNAVAILABLE`() {
        val (m, tts) = machine()
        var done = 0
        m.speak("one") { done++ }

        tts.init(succeeded = false)
        assertTrue(tts.spoken.isEmpty())
        assertEquals(0, done)
        assertEquals(BookSpeechAvailability.UNAVAILABLE, m.availability.value)
    }

    @Test
    fun `a connected engine with no usable language is UNAVAILABLE and drops the pending`() {
        val (m, tts) = machine()
        tts.languageUsable = false
        var done = 0
        m.speak("one") { done++ }

        tts.init(succeeded = true)
        assertTrue(tts.spoken.isEmpty())
        assertEquals(0, done)
        assertEquals(BookSpeechAvailability.UNAVAILABLE, m.availability.value)
    }

    // ------------------------------------------------------------------
    // Completion guard + stop/error semantics
    // ------------------------------------------------------------------

    @Test
    fun `late onDone after stop never completes`() {
        val (m, tts) = machine()
        tts.init(succeeded = true)
        var done = 0
        m.speak("one") { done++ }
        m.stop()

        tts.complete(tts.idAt(0))
        assertEquals(0, done, "a stopped utterance's late done must be inert")
    }

    @Test
    fun `stop clears the active slot and the next speak completes normally`() {
        val (m, tts) = machine()
        tts.init(succeeded = true)
        m.speak("one") {}
        m.stop()
        assertEquals(1, tts.stopCount)

        var done = 0
        m.speak("two") { done++ }
        assertEquals(listOf("one", "two"), tts.spoken.map { it.first })
        tts.complete(tts.idAt(1))
        assertEquals(1, done)
    }

    @Test
    fun `a superseded utterances late completion is inert`() {
        val (m, tts) = machine()
        tts.init(succeeded = true)
        var first = 0
        var second = 0
        m.speak("one") { first++ }
        m.speak("two") { second++ } // replaces utterance 0 (QUEUE_FLUSH)

        tts.complete(tts.idAt(0)) // late done of the replaced utterance
        assertEquals(0, first)
        assertEquals(0, second)

        tts.complete(tts.idAt(1)) // the live utterance completes exactly once
        assertEquals(1, second)

        tts.complete(tts.idAt(1)) // duplicate delivery after the slot cleared
        assertEquals(1, second)
    }

    @Test
    fun `error completes the utterance`() {
        val (m, tts) = machine()
        tts.init(succeeded = true)
        var done = 0
        m.speak("one") { done++ }

        tts.fail(tts.idAt(0))
        assertEquals(1, done, "an engine error must advance past the failed utterance")
    }

    @Test
    fun `a null utterance id completion is inert`() {
        val (m, tts) = machine()
        tts.init(succeeded = true)
        var done = 0
        m.speak("one") { done++ }

        tts.host?.onUtteranceDone(null)
        assertEquals(0, done)
    }

    // ------------------------------------------------------------------
    // Lazy lifecycle + configure pass-through
    // ------------------------------------------------------------------

    @Test
    fun `no speak means no binding start`() {
        val (m, tts) = machine()
        m.configure(150, 80)
        assertEquals(0, tts.startedCount, "the platform engine must not connect unprompted")
        assertEquals(150, tts.configuredRate)
        assertEquals(80, tts.configuredPitch)
        assertEquals(BookSpeechAvailability.INITIALIZING, m.availability.value)
    }

    @Test
    fun `shutdown releases and the next speak lazily re-creates`() {
        val (m, tts) = machine()
        var first = 0
        m.speak("one") { first++ }
        assertEquals(1, tts.startedCount, "the first speak creates the engine")
        tts.init(succeeded = true)
        tts.complete(tts.idAt(0))
        assertEquals(1, first)

        m.shutdown()
        assertEquals(1, tts.shutdownCount)
        assertEquals(BookSpeechAvailability.AVAILABLE, m.availability.value, "shutdown does not rewrite availability")

        var second = 0
        m.speak("two") { second++ }
        assertEquals(2, tts.startedCount, "ensureStarted re-runs after shutdown")
        assertEquals(
            BookSpeechAvailability.INITIALIZING,
            m.availability.value,
            "the re-created engine re-enters its INITIALIZING window",
        )
        assertEquals(1, tts.spoken.size, "the re-created engine parks until its init lands")

        tts.init(succeeded = true)
        assertEquals(listOf("one", "two"), tts.spoken.map { it.first })
        tts.complete(tts.idAt(1))
        assertEquals(1, second)
    }

    @Test
    fun `configure lands on the binding and survives the pending window`() {
        val (m, tts) = machine()
        m.configure(200, 50)
        m.speak("one") {}
        tts.init(succeeded = true)
        // The parked dispatch speaks through the binding with the last
        // configured voice parameters (the adapter applies them per speak).
        assertEquals(200, tts.configuredRate)
        assertEquals(50, tts.configuredPitch)
        assertEquals(listOf("one"), tts.spoken.map { it.first })
    }

    @Test
    fun `a fresh machine reports INITIALIZING before the lazy connect`() {
        val (m, tts) = machine()
        assertEquals(BookSpeechAvailability.INITIALIZING, m.availability.value)
        assertEquals(0, tts.startedCount)
    }
}
