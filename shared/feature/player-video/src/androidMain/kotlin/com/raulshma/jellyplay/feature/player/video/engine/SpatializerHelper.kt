package com.raulshma.jellyplay.feature.player.video.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import java.util.concurrent.Executor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The platform facts one spatial-state evaluation is built from. [state] is
 * the [resolveEffectiveSpatialState] result over these inputs; the rest are
 * the raw platform answers the UI reasons with ("not spatializable on this
 * output" vs "turn on system spatial audio").
 */
data class SpatialAudioEvaluation(
    val state: SpatialAudioState,
    /**
     * The device exposes a spatializer at all (API 32+ and a non-null
     * `AudioManager.getSpatializer()`). False on every earlier API level.
     */
    val supported: Boolean,
    /** `Spatializer.isEnabled()` — the system spatial-audio setting. */
    val systemSpatialEnabled: Boolean,
    /** `Spatializer.canBeSpatialized(movieAttributes, format)` for the current format. */
    val canBeSpatialized: Boolean,
    /** The current output route is headphone-class (wired/BT/USB-BT/BLE/hearing aid). */
    val outputIsHeadphoneClass: Boolean,
)

/**
 * Resolves and OBSERVES the platform spatializer facts for [ExoPlayerEngine]:
 * whether the spatializer exists (API 32+), whether it is enabled
 * system-side, whether the playing format can be spatialized on the current
 * route, and whether the current output is headphone-class — then folds them
 * with the session inputs (the advisory toggle, headphones-only
 * qualification, offload) through the pure
 * [resolveEffectiveSpatialState].
 *
 * Everything platform-facing is `Build.VERSION.SDK_INT >= 32`-guarded: on
 * earlier APIs the helper reports a constant
 * [SpatialAudioState.UNAVAILABLE] evaluation and registers no spatializer
 * callback (route observation via [AudioDeviceCallback] still runs — it is
 * API 23+ and drives the state on every supported level).
 *
 * The headphone classification is an INJECTABLE predicate over
 * `AudioDeviceInfo.type` so the decision logic stays JVM-testable (the
 * platform constants are Android-only); the default implements the
 * wired/Bluetooth/USB-BT/BLE/hearing-aid classification below.
 *
 * NOT a Hilt type: the engine constructs it directly — like the audiofx
 * helpers, it is engine-private session machinery. [release] unregisters
 * both callbacks (the engine calls it from its release path); a later
 * [update] re-arms them for the contract's load-after-release rebuild.
 */
@UnstableApi
internal class SpatializerHelper(
    context: Context,
    private val headphoneClassPredicate: (Int) -> Boolean = ::isHeadphoneClassDeviceType,
    // Last so the engine's trailing-lambda construction binds here.
    private val onEvaluation: (SpatialAudioEvaluation) -> Unit = {},
) {
    /**
     * The session inputs the engine pushes on every config/format change.
     * Guarded by [lock] together with the canBeSpatialized memo so a callback
     * thread cannot recompute against a torn input set.
     */
    private val lock = Any()

    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    /** Null below API 32, on devices the platform does not back, or when the
     *  binder surface is unreachable (Robolectric / OEM variance) — treated
     *  exactly like "no spatializer". */
    private val spatializer: android.media.Spatializer? =
        if (Build.VERSION.SDK_INT >= 32) {
            runCatching { audioManager?.spatializer }.getOrNull()
        } else {
            null
        }

    // The movie content attributes the engine plays with — the same
    // CONTENT_TYPE_MOVIE / USAGE_MEDIA pair the ExoPlayer stream carries, so
    // canBeSpatialized answers for the track we actually submit.
    private val movieAudioAttributes: AudioAttributes =
        AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .build()

    private val handler = Handler(Looper.getMainLooper())

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = recompute(forcePlatformQuery = true)
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = recompute(forcePlatformQuery = true)
    }

    // Lazy: the listener TYPE is API 32 — the object must not be created on
    // earlier API levels (it never is; see the init guard).
    private val stateChangedListener: Any by lazy {
        object : android.media.Spatializer.OnSpatializerStateChangedListener {
            override fun onSpatializerEnabledChanged(spatializer: android.media.Spatializer, enabled: Boolean) =
                recompute(forcePlatformQuery = true)

            override fun onSpatializerAvailableChanged(
                spatializer: android.media.Spatializer,
                available: Boolean,
            ) = recompute(forcePlatformQuery = true)
        }
    }

    private val mainExecutor: Executor = ExecutorCompat(handler)

    private val _evaluation = MutableStateFlow(initialEvaluation())
    val evaluation: StateFlow<SpatialAudioEvaluation> = _evaluation.asStateFlow()

    // Session inputs (see [update]) and the canBeSpatialized memo (a binder
    // call, repeated on every track tick otherwise). The format is the memo
    // key, not a decision input, so it rides beside the [SpatialAudioInputs]
    // bundle rather than inside it.
    private var playingFormat: Format? = null
    private var sessionInputs = SpatialAudioInputs(
        userEnabled = false,
        onlyHeadphones = true,
        offloadActive = false,
    )
    private var lastQueriedFormat: Format? = null
    private var lastQueryResult: Boolean = false

    private var spatializerListenerRegistered: Boolean = false

    /** Set by [release]; the next [update] re-arms the callbacks (the engine
     *  contract lets a released engine load again through its self-healing
     *  scope, and the fresh session needs route/system observation back). */
    private var released: Boolean = false

    init {
        registerCallbacks()
    }

    private fun registerCallbacks() {
        audioManager?.registerAudioDeviceCallback(deviceCallback, handler)
        // Local capture: a property reference cannot smart-cast inside the
        // runCatching lambda below.
        val sp = spatializer
        if (Build.VERSION.SDK_INT >= 32 && sp != null) {
            @Suppress("UNCHECKED_CAST")
            runCatching {
                sp.addOnSpatializerStateChangedListener(
                    mainExecutor,
                    stateChangedListener as android.media.Spatializer.OnSpatializerStateChangedListener,
                )
                spatializerListenerRegistered = true
            }
        }
    }

    /**
     * Pushes the session inputs and re-evaluates. [format] is the playing
     * format (the canBeSpatialized memo key — memoized per format, so
     * calling this on every config/track change costs a binder call only
     * when the format actually moved); [inputs] bundles the engine-owned
     * decision inputs ([SpatialAudioInputs]).
     */
    fun update(format: Format?, inputs: SpatialAudioInputs) {
        if (released) {
            released = false
            registerCallbacks()
        }
        synchronized(lock) {
            playingFormat = format
            sessionInputs = inputs
        }
        // The session inputs never change the platform's answers — the memo
        // stands (it only refreshes when the format moved).
        recompute(forcePlatformQuery = false)
    }

    fun release() {
        audioManager?.unregisterAudioDeviceCallback(deviceCallback)
        if (Build.VERSION.SDK_INT >= 32 && spatializerListenerRegistered) {
            runCatching {
                spatializer?.removeOnSpatializerStateChangedListener(
                    stateChangedListener as android.media.Spatializer.OnSpatializerStateChangedListener,
                )
            }
        }
        spatializerListenerRegistered = false
        synchronized(lock) {
            // Drop the format memo with the session — a re-armed helper must
            // re-query the platform, not replay the old session's answer.
            lastQueriedFormat = null
            lastQueryResult = false
        }
        released = true
    }

    /**
     * Re-evaluates against the platform. [forcePlatformQuery] bypasses the
     * format memo — the route/system callbacks pass `true` (what moved is the
     * platform answer, not the format).
     */
    private fun recompute(forcePlatformQuery: Boolean) {
        val (format, inputs) = synchronized(lock) { playingFormat to sessionInputs }
        val snapshot = queryPlatform(format, forcePlatformQuery)
        val state = resolveEffectiveSpatialState(
            userEnabled = inputs.userEnabled,
            onlyHeadphones = inputs.onlyHeadphones,
            outputIsHeadphoneClass = snapshot.outputIsHeadphoneClass,
            offloadActive = inputs.offloadActive,
            systemSpatialEnabled = snapshot.systemSpatialEnabled,
            canBeSpatialized = snapshot.canBeSpatialized,
        )
        val evaluation = SpatialAudioEvaluation(
            state = state,
            supported = snapshot.supported,
            systemSpatialEnabled = snapshot.systemSpatialEnabled,
            canBeSpatialized = snapshot.canBeSpatialized,
            outputIsHeadphoneClass = snapshot.outputIsHeadphoneClass,
        )
        if (_evaluation.value != evaluation) {
            _evaluation.value = evaluation
            // The engine's flip-side (downmix/cap/virtualizer neutralization +
            // stats refresh) reacts here; the StateFlow above stays as the
            // pull surface. Emitted only on change so a repeated platform
            // callback costs nothing downstream.
            onEvaluation(evaluation)
        }
    }

    private fun queryPlatform(format: Format?, force: Boolean): PlatformSnapshot {
        val outputIsHeadphoneClass = resolveOutputIsHeadphoneClass()
        val spatializer = this.spatializer
        if (spatializer == null) {
            return PlatformSnapshot(
                supported = false,
                systemSpatialEnabled = false,
                canBeSpatialized = false,
                outputIsHeadphoneClass = outputIsHeadphoneClass,
            )
        }
        val systemEnabled = runCatching { spatializer.isEnabled }.getOrDefault(false)
        // The memo decision and the binder call are split so
        // canBeSpatialized() — a cross-process call that can stall — never
        // runs while holding [lock], which also guards the session inputs.
        val queryMiss = synchronized(lock) { force || format != lastQueriedFormat }
        val canSpatialize = if (queryMiss) {
            val queried = runCatching {
                val platformFormat = buildAudioFormat(format)
                platformFormat != null && spatializer.canBeSpatialized(movieAudioAttributes, platformFormat)
            }.getOrDefault(false)
            synchronized(lock) {
                lastQueriedFormat = format
                lastQueryResult = queried
            }
            queried
        } else {
            synchronized(lock) { lastQueryResult }
        }
        return PlatformSnapshot(
            supported = true,
            systemSpatialEnabled = systemEnabled,
            canBeSpatialized = canSpatialize,
            outputIsHeadphoneClass = outputIsHeadphoneClass,
        )
    }

    private data class PlatformSnapshot(
        val supported: Boolean,
        val systemSpatialEnabled: Boolean,
        val canBeSpatialized: Boolean,
        val outputIsHeadphoneClass: Boolean,
    )

    /**
     * The AudioFormat the platform spatializer is queried with, built from the
     * playing format the same way media3's own SpatializerWrapper builds it:
     * the explicit [Format.channelMask] when present AND consistent with the
     * channel count (a channel mask carries one bit per channel), else the
     * channel config derived from the channel count — PCM 16-bit encoding,
     * the sample rate carried through.
     */
    private fun buildAudioFormat(format: Format?): AudioFormat? {
        if (format == null) return null
        val channelCount = if (format.channelCount > 0) format.channelCount else return null
        val channelMask = if (
            format.channelMask != Format.NO_VALUE &&
            Integer.bitCount(format.channelMask) == channelCount
        ) {
            format.channelMask
        } else {
            Util.getAudioTrackChannelConfig(channelCount)
        }
        if (channelMask == 0) return null // AUDIO_CHANNEL_INVALID — unknown layout
        val builder = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(channelMask)
        if (format.sampleRate > 0) builder.setSampleRate(format.sampleRate)
        return builder.build()
    }

    /**
     * ANY connected output device that is headphone-class qualifies the route
     * — the same generous shape the platform's own availability uses (a
     * wired-headset + BT-headphones session still spatializes to one of
     * them).
     */
    private fun resolveOutputIsHeadphoneClass(): Boolean {
        val outputs = audioManager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS) ?: return false
        return outputs.any { headphoneClassPredicate(it.type) }
    }

    private fun initialEvaluation(): SpatialAudioEvaluation = SpatialAudioEvaluation(
        state = SpatialAudioState.UNAVAILABLE,
        supported = spatializer != null,
        systemSpatialEnabled = false,
        canBeSpatialized = false,
        outputIsHeadphoneClass = false,
    )

    private class ExecutorCompat(private val handler: Handler) : Executor {
        override fun execute(command: Runnable) {
            handler.post(command)
        }
    }

    companion object {
        /**
         * The headphone-class output classification over `AudioDeviceInfo.type`
         * — wired headsets/headphones, Bluetooth A2DP/SCO, USB headsets, BLE
         * headsets and hearing aids (API 31 constants compared by value, so
         * this stays a single API-28-safe function).
         */
        internal fun isHeadphoneClassDeviceType(deviceType: Int): Boolean = when (deviceType) {
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            23 /* TYPE_HEARING_AID (API 31) */,
            26 /* TYPE_BLE_HEADSET (API 31) */,
            -> true
            else -> false
        }
    }
}
