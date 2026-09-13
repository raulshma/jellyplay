package com.raulshma.jellyplay.feature.book

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Android actual of [BookSpeechEngine] over `android.speech.tts.TextToSpeech`.
 *
 * Constraints honored:
 * - The TTS object (and its service binding) is created lazily on the first
 *   [speak] — opening a book must not connect to a speech service nobody
 *   asked for. A [speak] that races the async init parks as ONE pending
 *   utterance and dispatches on init success (on failure it is dropped
 *   silently; the ViewModel's availability watcher ends the speech loop).
 * - Progress callbacks arrive on a TTS binder thread; every callback and
 *   public call is confined to the main thread via [onMain].
 * - [stop]/failure paths never fire a parked or superseded [onDone]: only
 *   the utterance matching [activeUtteranceId] completes, and stop clears
 *   the slot first.
 * - Availability settles INITIALIZING → AVAILABLE/UNAVAILABLE from the init
 *   status plus an `isLanguageAvailable` probe of the default locale (a TTS
 *   engine that connects but speaks no language is UNAVAILABLE — the reader
 *   shows the caption instead of a silent play button).
 */
internal class AndroidBookSpeechEngine(
    context: Context,
) : BookSpeechEngine {

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    private var engine: TextToSpeech? = null

    /** True once onInit(SUCCESS) ran — speak before that parks as pending. */
    private var ready = false

    private var ratePercent = 100
    private var pitchPercent = 100

    private var nextUtteranceId = 0
    private var activeUtteranceId: String? = null
    private var activeDone: (() -> Unit)? = null
    private var pending: PendingUtterance? = null

    private val _availability = MutableStateFlow(BookSpeechAvailability.INITIALIZING)
    override val availability: StateFlow<BookSpeechAvailability> = _availability.asStateFlow()

    private class PendingUtterance(val text: String, val onDone: () -> Unit)

    override fun configure(ratePercent: Int, pitchPercent: Int) {
        onMain {
            this.ratePercent = ratePercent
            this.pitchPercent = pitchPercent
            engine?.apply { applyVoiceParams() }
        }
    }

    override fun speak(text: String, onDone: () -> Unit) {
        onMain { dispatch(text, onDone) }
    }

    override fun stop() {
        onMain { stopInternal() }
    }

    override fun shutdown() {
        onMain {
            stopInternal()
            engine?.shutdown()
            engine = null
            ready = false
        }
    }

    private fun dispatch(text: String, onDone: () -> Unit) {
        if (engine == null) {
            pending = PendingUtterance(text, onDone)
            ensureEngine()
            return
        }
        if (!ready) {
            pending = PendingUtterance(text, onDone)
            return
        }
        val tts = engine ?: return
        pending = null
        val id = "jellyplay-speech-${nextUtteranceId++}"
        activeUtteranceId = id
        activeDone = onDone
        tts.apply { applyVoiceParams() }
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, id)
        }
        // QUEUE_FLUSH: a new utterance replaces the in-flight one (skips and
        // stops must not queue behind a long utterance).
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, id)
    }

    private fun ensureEngine() {
        if (engine != null) return
        _availability.value = BookSpeechAvailability.INITIALIZING
        engine = TextToSpeech(appContext) { status -> onMain { handleInit(status) } }
    }

    private fun handleInit(status: Int) {
        val tts = engine ?: return
        if (status != TextToSpeech.SUCCESS) {
            _availability.value = BookSpeechAvailability.UNAVAILABLE
            pending = null
            return
        }
        ready = true
        tts.language = Locale.getDefault()
        val probe = tts.isLanguageAvailable(Locale.getDefault())
        val usable = probe != TextToSpeech.LANG_MISSING_DATA && probe != TextToSpeech.LANG_NOT_SUPPORTED
        if (!usable) {
            _availability.value = BookSpeechAvailability.UNAVAILABLE
            pending = null
            return
        }
        _availability.value = BookSpeechAvailability.AVAILABLE
        tts.apply { applyVoiceParams() }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            override fun onDone(utteranceId: String?) {
                onMain { completeUtterance(utteranceId) }
            }

            /**
             * Engine errors complete their utterance too: swallowing the
             * callback would strand the speech loop on a paragraph that will
             * never finish. Advancing past one failed utterance is the lesser
             * evil (a hard-failing engine surfaces through the user stopping).
             */
            override fun onError(utteranceId: String?) {
                onMain { completeUtterance(utteranceId) }
            }
        })
        pending?.let { parked ->
            pending = null
            dispatch(parked.text, parked.onDone)
        }
    }

    private fun completeUtterance(utteranceId: String?) {
        if (utteranceId == null || utteranceId != activeUtteranceId) return
        activeUtteranceId = null
        val done = activeDone
        activeDone = null
        done?.invoke()
    }

    private fun TextToSpeech.applyVoiceParams() {
        setSpeechRate(ratePercent / 100f)
        setPitch(pitchPercent / 100f)
    }

    private fun stopInternal() {
        activeUtteranceId = null
        activeDone = null
        pending = null
        engine?.stop()
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }
}
