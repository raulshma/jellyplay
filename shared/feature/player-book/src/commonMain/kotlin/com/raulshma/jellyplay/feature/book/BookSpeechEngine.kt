package com.raulshma.jellyplay.feature.book

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Platform read-aloud capability. Android reports AVAILABLE once its TTS
 * service connects with a usable language; desktop stays UNAVAILABLE
 * (honest degradation — desktop TTS is roadmap-future), which hides the
 * read-aloud controls and captions the settings section instead of failing
 * mid-utterance. INITIALIZING covers the lazy engine's connect window, so a
 * UI keying on `!= AVAILABLE` degrades to the caption rather than flashing
 * controls that cannot work.
 */
enum class BookSpeechAvailability { AVAILABLE, UNAVAILABLE, INITIALIZING }

/**
 * Read-aloud engine: one utterance at a time — the controller decides
 * granularity (sentences), the engine just speaks text. Android speaks;
 * desktop reports unavailable. [speak] replaces any in-flight utterance,
 * and [onDone] fires when THIS utterance completes — the reader speech loop
 * drives advancement off it. Implementations must be safe to call from any
 * thread (they confine internally) and must post [onDone] to the main
 * thread (system TTS delivers progress callbacks on a binder thread). The
 * engine is created lazily — platforms that bind heavy services (Android
 * TextToSpeech) must not connect until the first [speak].
 */
interface BookSpeechEngine {
    val availability: StateFlow<BookSpeechAvailability>

    /** Voice tuning; percent semantics: 100 = normal, 50 = half, 200 = double. */
    fun configure(ratePercent: Int, pitchPercent: Int)

    /** Speak [text]; [onDone] fires when THIS utterance completes; replaces any in-flight utterance. */
    fun speak(text: String, onDone: () -> Unit)

    /** Silence the current utterance; its [onDone] must NOT fire afterwards. */
    fun stop()

    /** Release platform resources; the next [speak] may lazily re-create the engine. */
    fun shutdown()
}

/**
 * Neutral fallback for platforms without a speech binding (any
 * graph where no platform module registered an engine): permanently
 * UNAVAILABLE, every command a no-op. Same pattern as [NoopBookFormatProbe]:
 * the common Koin module resolves `getOrNull() ?: NoopBookSpeechEngine`.
 */
object NoopBookSpeechEngine : BookSpeechEngine {
    private val unavailable = MutableStateFlow(BookSpeechAvailability.UNAVAILABLE)
    override val availability: StateFlow<BookSpeechAvailability> = unavailable.asStateFlow()
    override fun configure(ratePercent: Int, pitchPercent: Int) {}
    override fun speak(text: String, onDone: () -> Unit) {}
    override fun stop() {}
    override fun shutdown() {}
}
