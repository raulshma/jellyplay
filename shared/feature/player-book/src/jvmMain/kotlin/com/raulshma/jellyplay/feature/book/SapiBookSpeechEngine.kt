package com.raulshma.jellyplay.feature.book

import com.sun.jna.platform.win32.COM.COMBindingBaseObject
import com.sun.jna.platform.win32.COM.COMException
import com.sun.jna.platform.win32.Guid
import com.sun.jna.platform.win32.Variant.VARIANT
import com.sun.jna.platform.win32.OleAuto
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlin.math.log2
import kotlin.math.roundToInt

/**
 * Windows read-aloud engine: SAPI `ISpVoice` under the commonMain
 * [SpeechUtteranceMachine] — the same translator split the Android engine
 * uses, with a dedicated apartment thread standing in for Android's main
 * looper:
 *  - every machine command ([configure]/[speak]/[stop]/[shutdown]) posts to
 *    that thread (the machine is single-thread confined);
 *  - the binding's [TtsBinding.Host] callbacks post back to it;
 *  - completion detection runs on a separate waiter thread that polls
 *    `WaitUntilDone` in 100ms slices executed on the apartment thread — a
 *    blocking whole-utterance wait on the apartment thread would deadlock a
 *    queued [stop].
 *
 * `ISpVoice` is late-bound through IDispatch (JNA's `COMBindingBaseObject`
 * name-based oleMethod path) — no vtable arithmetic, no typelib wrapper.
 * Voice rate maps the engine's 50–200% onto SAPI's −10..10; pitch has no
 * SAPI property equivalent and is accepted-but-ignored (the binding contract
 * allows a partial application).
 *
 * Any init failure (no SAPI voice, COM refused, non-Windows) folds to one
 * `onInit(false)` — the machine lands UNAVAILABLE and the reader captions
 * instead of playing silence.
 */
internal class SapiBookSpeechEngine : BookSpeechEngine {

    /** The COM apartment: the machine's confinement thread (daemon — never blocks exit). */
    private val apartment: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "sapi-speech-com").apply { isDaemon = true }
    }

    private val machine = SpeechUtteranceMachine(SapiTtsBinding(apartment))

    override val availability: StateFlow<BookSpeechAvailability> get() = machine.availability

    override fun configure(ratePercent: Int, pitchPercent: Int) {
        post { machine.configure(ratePercent, pitchPercent) }
    }

    override fun speak(text: String, onDone: () -> Unit) {
        post { machine.speak(text, onDone) }
    }

    override fun stop() {
        post { machine.stop() }
    }

    override fun shutdown() {
        post { machine.shutdown() }
    }

    private fun post(block: () -> Unit) {
        apartment.execute(block)
    }
}

/** CLSID_SpVoice (SAPI 5.1+ — present from Windows XP through current Windows). */
private const val CLSID_SPVOICE = "96749377-3391-11D2-9EE3-00C04F797396"

/** SPF_ASYNC | SPF_PURGEBEFORESPEAK — the new utterance replaces the in-flight one. */
private const val SPF_FLUSH = 0x3

/** SAPI WaitUntilDone poll interval while an utterance plays. */
private const val WAIT_POLL_MS = 100

/**
 * The Windows half of [TtsBinding]: raw `ISpVoice` calls, no decisions.
 * [ensureStarted] runs on the apartment thread (the voice object must be
 * created inside its COM apartment); [host] callbacks re-post there.
 */
private class SapiTtsBinding(
    private val apartment: ExecutorService,
) : TtsBinding {

    private var voice: SpVoiceObject? = null

    /**
     * Set on the apartment thread by [shutdown] before the voice is released.
     * A `WaitUntilDone` poll Callable queued behind the shutdown task would
     * otherwise execute against freed COM memory — use-after-free, not a
     * catchable [COMException] — so every queued poll checks this first.
     */
    private var released = false

    /** The waiter thread blocks on completion polls; events re-post to the apartment. */
    private var waiter: ExecutorService? = null

    override var host: TtsBinding.Host? = null

    override fun ensureStarted() {
        if (voice != null) return
        val created: SpVoiceObject? = try {
            SpVoiceObject()
        } catch (_: Throwable) {
            null
        }
        if (created == null) {
            host?.onInit(connected = false)
            return
        }
        released = false
        voice = created
        waiter = Executors.newSingleThreadExecutor { r -> Thread(r, "sapi-speech-wait").apply { isDaemon = true } }
        host?.onInit(connected = true)
    }

    override fun isDefaultLanguageUsable(): Boolean = voice != null

    override fun configure(ratePercent: Int, pitchPercent: Int) {
        // 50–200% → SAPI's −10..10, log-scaled so each halving/doubling of
        // speed is a full decade either side of 100% = 0. Pitch: no SAPI
        // property — ignored.
        val multiplier = ratePercent.coerceIn(50, 200) / 100.0
        voice?.setRate((log2(multiplier) * 10).roundToInt())
    }

    override fun speak(text: String, utteranceId: String) {
        val v = voice ?: return
        v.speakAsync(text)
        waiter?.execute {
            // Poll WaitUntilDone ON the apartment thread (the raw IDispatch
            // must never leave its creating apartment); this thread only
            // blocks on each 100ms poll's result, so a queued stop still
            // reaches the voice between polls — its purge drains the queue
            // and the next poll returns done. The machine's id guard makes
            // any stale completion inert. The `released` check drops polls
            // queued behind a shutdown instead of touching the freed voice.
            val drained = try {
                var done = false
                while (!done) done = submitToApartment { released || v.waitUntilDone(WAIT_POLL_MS) }.get()
                true
            } catch (_: Throwable) {
                true
            }
            if (drained) postToApartment { host?.onUtteranceDone(utteranceId) }
        }
    }

    override fun stopPlatform() {
        // Speak-empty-with-purge: the queue drains, waiters complete, and the
        // machine's id guard makes those completions inert.
        voice?.speakAsync("")
    }

    override fun shutdown() {
        // Order matters: stopping the waiter only stops FUTURE poll
        // submissions — a poll Callable may already sit in the apartment
        // queue behind this shutdown task, so `released` is set (and read by
        // that poll on the apartment thread) before the voice's refcount
        // goes away. All on the single apartment thread; no interleaving.
        waiter?.shutdownNow()
        waiter = null
        released = true
        try {
            voice?.release()
        } catch (_: Throwable) {
            // Releasing a dead voice object must not take the reader down.
        }
        voice = null
    }

    private fun postToApartment(block: () -> Unit) {
        apartment.execute(block)
    }

    private fun submitToApartment(block: () -> Boolean): Future<Boolean> =
        apartment.submit(Callable(block))
}

/**
 * The late-bound ISpVoice wrapper. Constructed ON the apartment thread (the
 * base class CoCreateInstances the voice there and owns the reference);
 * every call must run on that same thread.
 */
private class SpVoiceObject : COMBindingBaseObject(Guid.CLSID(CLSID_SPVOICE), false) {

    /** Speak([text], SPF flags) — an empty text with purge is the stop gesture. */
    fun speakAsync(text: String) {
        oleMethod(
            OleAuto.DISPATCH_METHOD,
            null,
            "Speak",
            arrayOf(VARIANT(text), VARIANT(SPF_FLUSH)),
        )
    }

    /** Rate property put (−10..10). */
    fun setRate(rate: Int) {
        oleMethod(
            OleAuto.DISPATCH_PROPERTYPUT,
            VARIANT.ByReference(),
            "Rate",
            arrayOf(VARIANT(rate)),
        )
    }

    /** WaitUntilDone([ms]) — true once the utterance queue has drained. */
    fun waitUntilDone(timeoutMs: Int): Boolean {
        val result = VARIANT.ByReference()
        try {
            oleMethod(
                OleAuto.DISPATCH_METHOD,
                result,
                "WaitUntilDone",
                arrayOf(VARIANT(timeoutMs)),
            )
        } catch (_: COMException) {
            return true
        }
        return result.booleanValue()
    }
}
