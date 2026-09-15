package com.raulshma.jellyplay.core.data.playback.focus

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper

/**
 * The Android [FocusArbiter]: ONE outstanding AudioFocusRequest owned here,
 * so this module's seat can never fight PlayerAudioLifecycle's (which stays
 * engine-bound to video/live) or ExoPlayer's built-in handling (music's
 * slice-1 OS leg). The request's audio attributes come from the CALLER's
 * [FocusAudioAttributes] (slice-2 checklist: previously hardcoded
 * USAGE_MEDIA + CONTENT_TYPE_SPEECH — correct for read-aloud, wrong for
 * music/video, whose OS routing and ducking policy read the content type).
 * Attributes are constant per claimant, so an attributes-identical
 * re-request reuses the SAME request object (a suspended claimant resuming
 * never rebuilds the seat), and an attributes change — only possible once a
 * second claimant joins this module's OS leg — abandons the stale request
 * before building the fresh one: exactly one outstanding request, always.
 *
 * AUDIOFOCUS_GAIN, not GAIN_TRANSIENT: a transient grant would auto-resume
 * the displaced victim when the claim releases (the media3 focus stack
 * re-grants on abandon), which would break the module's pinned manual-resume
 * decision at the OS level. Long-form read-aloud is a GAIN-shaped claim.
 * No delayed focus gain — the executor's synchronous contract.
 *
 * Listener events are marshalled to the main looper (the executor and every
 * surface command are main-confined); events after [abandon] are dropped.
 */
internal class AndroidFocusArbiter(context: Context) : FocusArbiter {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var request: AudioFocusRequest? = null
    private var requestAttributes: FocusAudioAttributes? = null
    private var listener: FocusListener? = null

    override fun request(attributes: FocusAudioAttributes, listener: FocusListener): Boolean {
        this.listener = listener
        val existing = request
        if (existing != null) {
            if (requestAttributes == attributes) {
                // Idempotent re-request (a suspended claimant resuming): the same
                // request object, a fresh listener wiring.
                return runCatching { audioManager.requestAudioFocus(existing) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED }
                    .getOrDefault(false)
            }
            // Attributes change = holder change under the one seat: retire the
            // stale request first so the one-outstanding-request invariant holds.
            runCatching { audioManager.abandonAudioFocusRequest(existing) }
        }
        val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(attributes.usage.toAndroidUsage())
                    .setContentType(attributes.contentType.toAndroidContentType())
                    .build(),
            )
            .setAcceptsDelayedFocusGain(false)
            .setOnAudioFocusChangeListener({ change -> dispatch(change) }, mainHandler)
            .build()
        request = focusRequest
        requestAttributes = attributes
        return runCatching { audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED }
            .getOrDefault(false)
    }

    override fun abandon() {
        listener = null
        request?.let { runCatching { audioManager.abandonAudioFocusRequest(it) } }
        request = null
        requestAttributes = null
    }

    private fun FocusUsage.toAndroidUsage(): Int = when (this) {
        FocusUsage.MEDIA -> AudioAttributes.USAGE_MEDIA
    }

    private fun FocusContentType.toAndroidContentType(): Int = when (this) {
        FocusContentType.SPEECH -> AudioAttributes.CONTENT_TYPE_SPEECH
        FocusContentType.MUSIC -> AudioAttributes.CONTENT_TYPE_MUSIC
        FocusContentType.MOVIE -> AudioAttributes.CONTENT_TYPE_MOVIE
    }

    private fun dispatch(change: Int) {
        val current = listener ?: return
        val event = when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> FocusEvent.LostPermanent
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
            -> FocusEvent.LostTransient
            AudioManager.AUDIOFOCUS_GAIN -> FocusEvent.Regained
            else -> return
        }
        current.onFocusEvent(event)
    }
}
