package com.raulshma.jellyplay.core.data.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build

/**
 * Owns the two Android audio-lifecycle concerns a media player ViewModel needs:
 *
 *  1. **Audio focus** — request [AudioManager.AUDIOFOCUS_GAIN], duck to
 *     [DUCK_VOLUME] on a transient loss (phone call), restore on regain, and
 *     pause + abandon on permanent loss. Previously this logic was copy-pasted
 *     between [com.raulshma.jellyplay.feature.player.video.VideoPlayerViewModel]
 *     and
 *     [com.raulshma.jellyplay.feature.player.live.LiveTvPlayerViewModel].
 *
 *  2. **Becoming-noisy** — register
 *     [AudioManager.ACTION_AUDIO_BECOMING_NOISY] to pause when headphones
 *     unplug (otherwise the stream keeps blasting through the speaker).
 *
 * Both VMs delegate to a single instance of this class, passing a
 * [PlaybackControl] adapter that exposes only the play/pause/volume/mute
 * surface each engine provides. The engines diverge (ExoPlayer/MPV expose a
 * normalized 0..1 volume; VLC exposes 0..2 to allow >100% boost; the live
 * engine pokes the raw Media3 `Player.volume`), so the duck/restore round-trip
 * intentionally stores the **unclamped** control volume — a previous
 * `coerceIn(0f, 1f)` here permanently halved VLC volumes above 100% on the
 * first duck cycle.
 *
 * Lifecycle: the owning ViewModel calls [registerAudioFocus] /
 * [registerBecomingNoisy] once when its engine is ready and [release] in its
 * teardown path (the live VM tears down per screen-exit; the VOD VM tears down
 * in `performRelease`). [release] is idempotent and safe to call before any
 * register.
 *
 * This class is not thread-safe; it is driven from the ViewModel's main thread
 * (the same thread Android dispatches focus-change callbacks on).
 *
 * @param control engine-agnostic adapter. Captured by the focus listener and
 *  re-read on every callback so it always reflects the *current* engine — the
 *  VOD VM swaps engines on retry, and the live VM nulls its engine on stop.
 * @param isMuted read on focus regain so duck-while-muted never leaks audio at
 *  the duck level: when muted we re-assert mute instead of restoring volume.
 *  Re-read on each callback for the same reason as [control].
 * @param onRegain optional hook fired on AUDIOFOCUS_GAIN after volume is
 *  restored — the VOD VM uses it to apply its `videoSkipBackOnResumeMs`
 *  resume-skip; the live VM passes `null` (live has no resume-skip concept).
 */
class PlayerAudioLifecycle(
    private val context: Context,
    private val control: () -> PlaybackControl?,
    private val isMuted: () -> Boolean,
    private val onRegain: (() -> Unit)? = null,
) {

    /**
     * Minimal engine surface the audio-focus listener drives. Each ViewModel
     * builds one with lambda-backed fields (so it reads the *current* engine on
     * every invocation); the VOD VM wraps its `MediaEngine`, the live VM wraps
     * the raw Media3 `Player`.
     */
    data class PlaybackControl(
        /** True if the engine is currently playing (vs paused). */
        val isPlaying: () -> Boolean,
        /** Current engine volume, in the engine's native range (unclamped). */
        val volume: () -> Float,
        /** Pause playback. */
        val pause: () -> Unit,
        /** Resume playback. */
        val play: () -> Unit,
        /** Set the engine volume in its native range. */
        val setVolume: (Float) -> Unit,
        /** Re-assert the muted state (called on focus regain while muted). */
        val setMuted: (Boolean) -> Unit,
    )

    private var audioFocusRequest: AudioFocusRequest? = null
    private var becomingNoisyReceiver: BroadcastReceiver? = null

    /** Volume applied on transient focus loss (duck). */
    private var preDuckVolume: Float? = null
    private var wasPlayingBeforeTransientLoss: Boolean = false

    /**
     * Request media audio focus and install the duck/restore listener. No-op
     * (idempotent) if already registered.
     */
    fun registerAudioFocus() {
        if (audioFocusRequest != null) return
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val listener = AudioManager.OnAudioFocusChangeListener { focusChange ->
            val engine = control() ?: return@OnAudioFocusChangeListener
            when (focusChange) {
                AudioManager.AUDIOFOCUS_LOSS -> {
                    // Permanent loss — system will not hand focus back automatically.
                    engine.pause()
                    resetTransientState()
                    unregisterAudioFocus()
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    // Skip volume capture while muted: player volume is 0f while
                    // muted, so capturing it would clobber the real level on
                    // restore. Mute is re-asserted on GAIN instead.
                    if (!isMuted() && preDuckVolume == null) {
                        preDuckVolume = engine.volume()
                    }
                    wasPlayingBeforeTransientLoss = engine.isPlaying()
                    if (!isMuted()) engine.setVolume(DUCK_VOLUME)
                    // When muted, volume stays at 0f — ducking must not make
                    // muted audio audible (e.g. during a phone call).
                }
                AudioManager.AUDIOFOCUS_GAIN -> {
                    // Restore pre-duck volume, or re-assert mute so a
                    // duck-while-muted cycle never leaks audio past the regain.
                    if (isMuted()) {
                        engine.setMuted(true)
                    } else {
                        preDuckVolume?.let { engine.setVolume(it) }
                    }
                    onRegain?.invoke()
                    if (wasPlayingBeforeTransientLoss) engine.play()
                    resetTransientState()
                }
            }
        }
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
            .build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(audioAttributes)
            .setAcceptsDelayedFocusGain(true)
            .setOnAudioFocusChangeListener(listener)
            .build()
        audioFocusRequest = request
        try {
            audioManager.requestAudioFocus(request)
        } catch (_: Exception) {
            audioFocusRequest = null
        }
    }

    /**
     * Register [AudioManager.ACTION_AUDIO_BECOMING_NOISY] to pause on headphone
     * unplug. No-op (idempotent) if already registered.
     */
    fun registerBecomingNoisy() {
        if (becomingNoisyReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                    control()?.pause()
                }
            }
        }
        becomingNoisyReceiver = receiver
        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        try {
            context.registerReceiver(
                receiver,
                filter,
                // Private receiver for a system broadcast — explicit flag required on API 34+.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Context.RECEIVER_NOT_EXPORTED
                } else 0,
            )
        } catch (_: Exception) {}
    }

    /** Abandon audio focus and reset transient bookkeeping. Idempotent. */
    fun unregisterAudioFocus() {
        val request = audioFocusRequest ?: return
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        try {
            audioManager?.abandonAudioFocusRequest(request)
        } catch (_: Exception) {}
        audioFocusRequest = null
        resetTransientState()
    }

    /** Unregister the becoming-noisy receiver. Idempotent. */
    fun unregisterBecomingNoisy() {
        val receiver = becomingNoisyReceiver ?: return
        try {
            context.unregisterReceiver(receiver)
        } catch (_: Exception) {}
        becomingNoisyReceiver = null
    }

    /** Tear down everything. Safe to call before any register. Idempotent. */
    fun release() {
        unregisterBecomingNoisy()
        unregisterAudioFocus()
    }

    /** `true` while audio focus is currently held. */
    fun isAudioFocusActive(): Boolean = audioFocusRequest != null

    private fun resetTransientState() {
        preDuckVolume = null
        wasPlayingBeforeTransientLoss = false
    }

    private companion object {
        /**
         * Volume applied during a transient focus loss (duck). Chosen to remain
         * audible-but-quiet during a phone call; matches the previous inline
         * literal in both ViewModels.
         */
        private const val DUCK_VOLUME = 0.2f
    }
}
