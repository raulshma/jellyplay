package com.raulshma.jellyplay.core.data.playback

import android.content.Context
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.datastore.appearance.AppearanceStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plays ambient theme music on media detail pages when the
 * [com.raulshma.jellyplay.core.model.legacy.UserPreferences.backdropThemeMusicEnabled]
 * preference is enabled. Uses a dedicated [ExoPlayer] instance kept separate
 * from the main audio/video players so it never interferes with active
 * playback. The player loops a single theme song at a low ambient volume and
 * is stopped when the detail screen is left.
 */
@Singleton
class ThemeMusicPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
    private val appearanceStore: AppearanceStore,
) {
    companion object {
        private const val TAG = "ThemeMusicPlayer"
        private const val AMBIENT_VOLUME = 0.3f
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var player: ExoPlayer? = null
    private var fetchJob: Job? = null

    @Volatile
    private var currentPlayerItemId: String? = null

    /**
     * Fetches and plays the theme song for [itemId] when the
     * backdrop-theme-music preference is enabled. Calling this with the same
     * [itemId] as the currently-playing track is a no-op. Pass `null` (or a
     * different id) followed by the new id to switch tracks.
     */
    fun playThemeFor(itemId: String) {
        if (!appearanceStore.appearance.value.backdropThemeMusicEnabled) return
        if (currentPlayerItemId == itemId && player?.isPlaying == true) return
        stop()
        currentPlayerItemId = itemId
        fetchJob = scope.launch {
            val themeSongs = mediaRepository.getThemeSongs(itemId).getOrElse { emptyList() }
            if (!isActive) return@launch
            val themeItem = themeSongs.firstOrNull() ?: return@launch
            val streamUrl = playbackRepository.getStreamUrl(
                itemId = themeItem.id,
                mediaSourceId = themeItem.id,
            )
            if (streamUrl.isBlank()) return@launch
            ensurePlayer().apply {
                setMediaItem(ExoMediaItem.fromUri(streamUrl))
                volume = AMBIENT_VOLUME
                repeatMode = ExoPlayer.REPEAT_MODE_ONE
                prepare()
                playWhenReady = true
            }
        }
    }

    /**
     * Stops and clears any playing theme music. Safe to call when nothing is
     * playing.
     *
     * The underlying [ExoPlayer] is released (not just stopped) because it is
     * cheap to recreate on the next [ensurePlayer] and the singleton scope
     * would otherwise retain native media codecs, audio sink and buffers for
     * the entire app process lifetime.
     */
    fun stop() {
        fetchJob?.cancel()
        fetchJob = null
        currentPlayerItemId = null
        player?.let {
            it.stop()
            it.clearMediaItems()
            it.release()
        }
        player = null
    }

    private fun ensurePlayer(): ExoPlayer {
        player?.let { return it }
        val attributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        return ExoPlayer.Builder(context)
            .setAudioAttributes(attributes, false)
            .build()
            .also { player = it }
    }

    /**
     * Releases the underlying player and cancels all coroutines. Called when
     * the singleton is no longer needed (e.g. app teardown).
     */
    fun release() {
        stop()
        player?.release()
        player = null
        scope.cancel()
    }
}
