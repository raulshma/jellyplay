package com.raulshma.jellyplay.feature.book

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import kotlinx.coroutines.flow.StateFlow

/**
 * Android actual of [BookSpeechEngine]: a thin `TextToSpeech` translator
 * under the commonMain [SpeechUtteranceMachine] — every utterance DECISION
 * (park-until-ready, the single-active completion guard, stop/error-completes,
 * the availability fold) lives in the machine. This file only maps Android
 * types onto the [TtsBinding] seam:
 *  - The TTS object (and its service binding) is created lazily on the first
 *    [speak] — opening a book must not connect to a speech service nobody
 *    asked for.
 *  - Progress callbacks arrive on a TTS binder thread; the adapter posts
 *    them (and every command) to the main thread — the machine's
 *    confinement contract.
 *  - The init status folds to a boolean (`TextToSpeech.SUCCESS`), the
 *    default-locale probe (`isLanguageAvailable`) reports usability, and
 *    utterances are tagged with the machine's ids via
 *    `KEY_PARAM_UTTERANCE_ID`.
 */
internal class AndroidBookSpeechEngine(
    context: Context,
) : BookSpeechEngine {

    private val main = Handler(Looper.getMainLooper())
    private val machine = SpeechUtteranceMachine(AndroidTtsBinding(context.applicationContext))

    override val availability: StateFlow<BookSpeechAvailability> get() = machine.availability

    override fun configure(ratePercent: Int, pitchPercent: Int) {
        onMain { machine.configure(ratePercent, pitchPercent) }
    }

    override fun speak(text: String, onDone: () -> Unit) {
        onMain { machine.speak(text, onDone) }
    }

    override fun stop() {
        onMain { machine.stop() }
    }

    override fun shutdown() {
        onMain { machine.shutdown() }
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }
}

/** The Android half of [TtsBinding]: `TextToSpeech` calls, no decisions. */
private class AndroidTtsBinding(context: Context) : TtsBinding {

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    private var engine: TextToSpeech? = null

    private var ratePercent = 100
    private var pitchPercent = 100

    override var host: TtsBinding.Host? = null

    override fun ensureStarted() {
        if (engine != null) return
        engine = TextToSpeech(appContext) { status ->
            onMain { host?.onInit(status == TextToSpeech.SUCCESS) }
        }.also { tts ->
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}

                override fun onDone(utteranceId: String?) {
                    onMain { host?.onUtteranceDone(utteranceId) }
                }

                override fun onError(utteranceId: String?) {
                    onMain { host?.onUtteranceError(utteranceId) }
                }
            })
        }
    }

    override fun isDefaultLanguageUsable(): Boolean {
        val tts = engine ?: return false
        tts.language = Locale.getDefault()
        val probe = tts.isLanguageAvailable(Locale.getDefault())
        return probe != TextToSpeech.LANG_MISSING_DATA && probe != TextToSpeech.LANG_NOT_SUPPORTED
    }

    override fun configure(ratePercent: Int, pitchPercent: Int) {
        this.ratePercent = ratePercent
        this.pitchPercent = pitchPercent
        engine?.applyVoiceParams()
    }

    override fun speak(text: String, utteranceId: String) {
        val tts = engine ?: return
        tts.applyVoiceParams()
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }
        // QUEUE_FLUSH: a new utterance replaces the in-flight one (skips and
        // stops must not queue behind a long utterance).
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    override fun stopPlatform() {
        engine?.stop()
    }

    override fun shutdown() {
        engine?.shutdown()
        engine = null
    }

    private fun TextToSpeech.applyVoiceParams() {
        setSpeechRate(ratePercent / 100f)
        setPitch(pitchPercent / 100f)
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }
}
