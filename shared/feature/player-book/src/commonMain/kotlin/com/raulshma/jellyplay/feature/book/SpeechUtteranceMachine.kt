package com.raulshma.jellyplay.feature.book

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The platform TextToSpeech seam under [SpeechUtteranceMachine]: a dumb
 * translator between Android's `TextToSpeech` (and any future platform
 * voice) and the machine's decisions. It owns NO policy — park/dispatch,
 * completion guarding and the availability fold all live in the machine;
 * the binding only creates/destroys the platform engine, applies voice
 * parameters and reports lifecycle facts. `utteranceId`s are generated and
 * matched by the machine; the binding just tags them through.
 */
internal interface TtsBinding {

    /** Platform → machine events. The machine installs itself before first use. */
    interface Host {
        /**
         * The async platform init settled. [connected] = the engine's service
         * binding succeeded (an adapter folds its platform's success constant
         * into this boolean).
         */
        fun onInit(connected: Boolean)

        /** Utterance [utteranceId] finished naturally. */
        fun onUtteranceDone(utteranceId: String?)

        /** Utterance [utteranceId] failed on the platform side. */
        fun onUtteranceError(utteranceId: String?)
    }

    /** The machine listening above this binding (one machine, one binding). */
    var host: Host?

    /**
     * Create + connect the platform engine, reporting exactly one
     * [Host.onInit]. Idempotent: a binding already started (or connected)
     * must not re-create its engine. Callers park the request until the init
     * lands — creation may be lazy (Android must not bind a speech service
     * nobody asked for).
     */
    fun ensureStarted()

    /**
     * Whether the platform engine can speak the default locale. Synchronous
     * probe; false-safe before a connection. (A TTS engine that connects but
     * speaks no language is UNAVAILABLE — the reader captions instead of
     * playing silence.)
     */
    fun isDefaultLanguageUsable(): Boolean

    /** Voice tuning; percent semantics: 100 = normal, 50 = half, 200 = double. */
    fun configure(ratePercent: Int, pitchPercent: Int)

    /**
     * Speak [text] under [utteranceId], replacing any in-flight utterance
     * (QUEUE_FLUSH semantics — skips and stops must not queue behind a long
     * utterance). Voice parameters must apply before the utterance is
     * enqueued.
     */
    fun speak(text: String, utteranceId: String)

    /** Silence the platform side. Late progress callbacks for killed utterances may still arrive — the machine's id guard makes them inert. */
    fun stopPlatform()

    /** Release platform resources; [ensureStarted] may be called again later. */
    fun shutdown()
}

/**
 * The read-aloud utterance DECISIONS, lifted out of the Android engine into
 * commonMain behind the [TtsBinding] seam (the Android file shrinks to a
 * dumb `TextToSpeech` translator). Implements [BookSpeechEngine] — platform
 * engines delegate their four commands here and keep only platform types.
 *
 * Owned policies (each pinned by `SpeechUtteranceMachineTest`):
 *  - **Pending park**: a [speak] before the platform engine is ready parks
 *    as ONE pending utterance (a later speak replaces it) and dispatches on
 *    init success; on init failure — or a no-usable-language probe — the
 *    pending is dropped and availability goes UNAVAILABLE (the ViewModel's
 *    availability watcher ends the speech loop).
 *  - **Single-active completion guard**: only the utterance matching the
 *    active id completes; a late completion of a stopped or superseded
 *    utterance is inert.
 *  - **Stop/error-completes**: [stop] clears the active slot first (its
 *    onDone must NOT fire afterwards), while a platform ERROR completes its
 *    utterance — swallowing the callback would strand the speech loop on a
 *    paragraph that never finishes.
 *  - **Availability fold**: INITIALIZING from construction (the lazy
 *    engine's connect window) → AVAILABLE/UNAVAILABLE from the init status
 *    plus the default-language probe.
 *
 * Single-thread confined (main): the platform adapter posts its [Host]
 * callbacks there and the owning [BookSpeechEngine] confines the four
 * commands — the machine itself holds no locks, same rule as
 * [ReaderSpeechController].
 */
internal class SpeechUtteranceMachine(
    private val binding: TtsBinding,
) : BookSpeechEngine {

    private val _availability = MutableStateFlow(BookSpeechAvailability.INITIALIZING)
    override val availability: StateFlow<BookSpeechAvailability> = _availability.asStateFlow()

    /** True once a successful init ran — speak before that parks as pending. */
    private var ready = false

    /** True once [binding.ensureStarted] ran (creation is a one-way per session). */
    private var started = false

    private var ratePercent = 100
    private var pitchPercent = 100

    private var nextUtteranceId = 0
    private var activeUtteranceId: String? = null
    private var activeDone: (() -> Unit)? = null
    private var pending: PendingUtterance? = null

    private class PendingUtterance(val text: String, val onDone: () -> Unit)

    private val host = object : TtsBinding.Host {
        override fun onInit(connected: Boolean) {
            if (!connected) {
                _availability.value = BookSpeechAvailability.UNAVAILABLE
                pending = null
                return
            }
            ready = true
            if (!binding.isDefaultLanguageUsable()) {
                _availability.value = BookSpeechAvailability.UNAVAILABLE
                pending = null
                return
            }
            _availability.value = BookSpeechAvailability.AVAILABLE
            pending?.let { parked ->
                pending = null
                dispatch(parked.text, parked.onDone)
            }
        }

        override fun onUtteranceDone(utteranceId: String?) {
            completeUtterance(utteranceId)
        }

        /**
         * Engine errors complete their utterance too: swallowing the callback
         * would strand the speech loop on a paragraph that will never finish.
         * Advancing past one failed utterance is the lesser evil (a
         * hard-failing engine surfaces through the user stopping).
         */
        override fun onUtteranceError(utteranceId: String?) {
            completeUtterance(utteranceId)
        }
    }

    init {
        binding.host = host
    }

    override fun configure(ratePercent: Int, pitchPercent: Int) {
        this.ratePercent = ratePercent
        this.pitchPercent = pitchPercent
        binding.configure(ratePercent, pitchPercent)
    }

    override fun speak(text: String, onDone: () -> Unit) {
        dispatch(text, onDone)
    }

    override fun stop() {
        activeUtteranceId = null
        activeDone = null
        pending = null
        binding.stopPlatform()
    }

    override fun shutdown() {
        stop()
        binding.shutdown()
        ready = false
        started = false
    }

    private fun dispatch(text: String, onDone: () -> Unit) {
        if (!ready) {
            pending = PendingUtterance(text, onDone)
            if (!started) {
                started = true
                _availability.value = BookSpeechAvailability.INITIALIZING
                binding.ensureStarted()
            }
            return
        }
        pending = null
        val id = "$UTTERANCE_ID_PREFIX${nextUtteranceId++}"
        activeUtteranceId = id
        activeDone = onDone
        binding.speak(text, id)
    }

    private fun completeUtterance(utteranceId: String?) {
        if (utteranceId == null || utteranceId != activeUtteranceId) return
        activeUtteranceId = null
        val done = activeDone
        activeDone = null
        done?.invoke()
    }
}

/** Utterance-id namespace (debuggable in TTS service logs). */
private const val UTTERANCE_ID_PREFIX = "jellyplay-speech-"
