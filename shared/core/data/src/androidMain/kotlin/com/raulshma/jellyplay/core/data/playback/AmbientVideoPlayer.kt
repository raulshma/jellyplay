package com.raulshma.jellyplay.core.data.playback

import android.content.Context
import android.view.Surface
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.exoplayer.ExoPlayer

/**
 * H7/D22: the ambient hero-clip transport — a MUTED-BY-CONTRACT, looping,
 * control-less video player for the server-hosted ThemeVideo clips that back
 * the home hero billboard (H7) and the detail hero's ThemeVideo preview
 * (D22). A single dedicated [ExoPlayer] instance kept separate from the main
 * audio/video players (the [ThemeMusicPlayer] pattern) so it never
 * interferes with active playback — and because it is a process-wide single,
 * starting a clip on one surface implicitly stops the previous one: the home
 * hero and the detail hero can never both drive video.
 *
 * The seam is androidMain-scoped (the [Surface] parameter is an Android type)
 * — consumers bind it through their own feature-level expect/actual
 * composables (feature/home's `AmbientHeroVideoSurface`, feature/details'
 * `AmbientThemeVideoSurface`); desktop never resolves it and renders the
 * static hero/backdrop instead (the documented no-op actuals).
 */
interface AmbientVideoPlayer {
    /**
     * Binds [surface] and starts a MUTED, looping play of [url]. Callers own
     * the surface's lifecycle (a Compose `TextureView`'s SurfaceTexture) —
     * the player only attaches/detaches it. Passing the same [url] that is
     * already playing is a no-op (the cheap [stop]-then-rebuild is skipped),
     * which makes idempotent re-calls from composition `update` passes safe.
     * Main thread only (the player is created and driven there).
     */
    fun start(url: String, surface: Surface)

    /**
     * Stops the clip ONLY if it is still [url] — the url-guarded variant the
     * Compose host's `onDispose` calls. A replaced hero candidate (or the
     * outgoing side of a hero crossfade) disposing after the incoming side
     * already started its own clip must not kill the new one.
     */
    fun stop(url: String)

    /**
     * Unconditionally stops and clears any playing clip. Safe to call when
     * nothing is playing.
     *
     * The underlying [ExoPlayer] is released (not just stopped) because it is
     * cheap to recreate on the next [start] and a retained instance would sit
     * on native media codecs and buffers for the entire app process lifetime
     * (the [ThemeMusicPlayer.stop] rationale verbatim).
     */
    fun release()
}

/**
 * The ExoPlayer-backed [AmbientVideoPlayer]. Muted by contract: `volume` is
 * pinned to 0f and audio focus is never requested (`handleAudioFocus = false`)
 * — an ambient billboard must never duck or be ducked by real playback. The
 * clip loops a single media item (`REPEAT_MODE_ONE`) and zoom-crops to fill
 * the host surface (`VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING`, the
 * PlayerView `ResizeMode.ZOOM` equivalent for a raw surface).
 */
class ExoAmbientVideoPlayer(
    private val context: Context,
) : AmbientVideoPlayer {
    private var player: ExoPlayer? = null

    @Volatile
    private var currentUrl: String? = null

    override fun start(url: String, surface: Surface) {
        if (currentUrl == url && player?.isPlaying == true) return
        releasePlayer()
        currentUrl = url
        val attributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()
        player = ExoPlayer.Builder(context)
            .setAudioAttributes(attributes, false)
            .build()
            .apply {
                setVideoSurface(surface)
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                setMediaItem(ExoMediaItem.fromUri(url))
                volume = MUTED_VOLUME
                repeatMode = ExoPlayer.REPEAT_MODE_ONE
                prepare()
                playWhenReady = true
            }
    }

    override fun stop(url: String) {
        if (currentUrl != url) return
        releasePlayer()
    }

    override fun release() = releasePlayer()

    private fun releasePlayer() {
        currentUrl = null
        player?.let {
            it.clearVideoSurface()
            it.stop()
            it.clearMediaItems()
            it.release()
        }
        player = null
    }

    private companion object {
        /** The ambient contract: the clip is always silent, regardless of caller. */
        const val MUTED_VOLUME = 0f
    }
}
