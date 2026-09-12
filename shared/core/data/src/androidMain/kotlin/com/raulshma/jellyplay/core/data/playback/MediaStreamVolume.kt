package com.raulshma.jellyplay.core.data.playback

import android.content.Context
import android.media.AudioManager

/**
 * Helpers for adjusting the system media stream volume.
 *
 * The app's in-player volume gesture already drives the system stream
 * (see [VideoPlayerScreen]'s `onVolumeGesture`), so the Jellyfin remote
 * control path must do the same — otherwise remote "SetVolume" only nudges
 * the ExoPlayer software gain, which is inaudible when the system stream is
 * muted or at zero.
 */
object MediaStreamVolume {

    /**
     * Set the [AudioManager.STREAM_MUSIC] volume to [value] expressed as a
     * normalized 0f..1f ratio of the stream's max index. No-op if the audio
     * service is unavailable.
     */
    fun setNormalized(context: Context, value: Float) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (max <= 0) return
        val target = (value.coerceIn(0f, 1f) * max).toInt().coerceIn(0, max)
        // Use FLAG_REMOVE_SOUND_AND_VIBRATE to avoid emitting a noisy "volume
        // changed" feedback tone on every remote tick.
        am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
    }

    /**
     * Read the current [AudioManager.STREAM_MUSIC] volume as a normalized
     * 0f..1f ratio of the stream's max index. Returns 1f if the audio service
     * is unavailable or the stream max is non-positive (treated as "full").
     */
    fun getNormalized(context: Context): Float {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return 1f
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (max <= 0) return 1f
        return am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max.toFloat()
    }
}
