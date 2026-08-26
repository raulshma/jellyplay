package com.raulshma.jellyplay.feature.player.video

import android.app.ActivityManager
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.raulshma.jellyplay.core.data.playback.AdaptiveBitrateManager
import com.raulshma.jellyplay.core.data.playback.PlayerAudioLifecycle
import com.raulshma.jellyplay.core.data.cast.CastManager
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.datastore.syncplaycast.SyncPlayCastStore
import com.raulshma.jellyplay.core.model.PlaybackMode
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import com.raulshma.jellyplay.feature.player.video.engine.ContainerMimeMapper
import com.raulshma.jellyplay.feature.player.video.trickplay.TrickplayController
import com.raulshma.jellyplay.feature.player.video.trickplay.TrickplayManager

/**
 * Android actual of the [VideoPlayerPlatform] aggregate seam (wave 8C): every
 * member body is the exact code the commonMain-bound ViewModel /
 * PlayerSessionManager used to inline — moved verbatim, not re-modeled.
 * Captures the app [Context] plus the Hilt-owned legacy [CastManager] the
 * cast-controller construction needs (the ViewModel no longer sees the legacy
 * type).
 */
internal class AndroidVideoPlayerPlatform(
    private val context: Context,
    private val castManager: CastManager,
) : VideoPlayerPlatform {

    override fun isLowRamDevice(): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        return am?.let { it.isLowRamDevice || it.memoryClass <= 256 } ?: false
    }

    override fun queryFileSizeBytes(uri: String): Long {
        val cursor = context.contentResolver.query(
            Uri.parse(uri),
            arrayOf(OpenableColumns.SIZE),
            null,
            null,
            null,
        ) ?: return 0
        return cursor.use {
            if (!it.moveToFirst()) return 0
            val idx = it.getColumnIndex(OpenableColumns.SIZE)
            if (idx < 0) 0 else it.getLong(idx)
        }
    }

    override fun readBytes(uri: String): ByteArray =
        context.contentResolver.openInputStream(Uri.parse(uri))?.use { it.readBytes() }
            ?: throw java.io.IOException("Cannot open input stream for selected subtitle")

    override val offlineMediaProbe: OfflineMediaProbe = AndroidOfflineMediaProbe()

    override fun createTrickplayController(playbackRepository: PlaybackRepository): TrickplayController =
        TrickplayManager(
            playbackRepository = playbackRepository,
            lowRamDevice = isLowRamDevice(),
        )

    override fun createCastController(
        playbackRepository: PlaybackRepository,
        adaptiveBitrateManager: AdaptiveBitrateManager,
        syncPlayCastStore: SyncPlayCastStore,
        getEngine: () -> MediaEngine?,
        getCurrentPlaybackMode: () -> PlaybackMode,
        getSessionState: () -> PlayerSessionState,
    ): PlayerCastController = AndroidPlayerCastController(
        castManager = castManager,
        playbackRepository = playbackRepository,
        adaptiveBitrateManager = adaptiveBitrateManager,
        syncPlayCastStore = syncPlayCastStore,
        getEngine = getEngine,
        getCurrentPlaybackMode = getCurrentPlaybackMode,
        getSessionState = getSessionState,
    )

    override fun createAudioLifecycle(
        getEngine: () -> MediaEngine?,
        isMuted: () -> Boolean,
        onRegain: (() -> Unit)?,
    ): VideoPlayerAudio = AndroidVideoPlayerAudio(
        context = context,
        getEngine = getEngine,
        isMuted = isMuted,
        onRegain = onRegain,
    )
}

/**
 * Android actual of the [OfflineMediaProbe] seam: the MediaMetadataRetriever
 * duration extraction and container→MIME mapping the offline load path used
 * inline (wave 8C move).
 */
private class AndroidOfflineMediaProbe : OfflineMediaProbe {

    override fun extractDurationMs(path: String): Long? = try {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(path)
        val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        retriever.release()
        durationStr?.toLongOrNull()
    } catch (_: Exception) {
        null
    }

    override fun mapContainerToMime(container: String?): String? =
        ContainerMimeMapper.mapToMime(container)
}

/**
 * Android actual of the [VideoPlayerAudio] seam: wraps the legacy
 * [PlayerAudioLifecycle] with the exact PlaybackControl adapter the
 * ViewModel used to build inline (engine members re-read on every callback —
 * LivePlayerAudio precedent, player-live conveyor).
 */
internal class AndroidVideoPlayerAudio(
    context: Context,
    private val getEngine: () -> MediaEngine?,
    private val isMuted: () -> Boolean,
    onRegain: (() -> Unit)?,
) : VideoPlayerAudio {

    private val delegate = PlayerAudioLifecycle(
        context = context,
        control = {
            getEngine()?.let { engine ->
                PlayerAudioLifecycle.PlaybackControl(
                    isPlaying = { engine.isPlaying.value },
                    volume = { engine.volume },
                    pause = { engine.pause() },
                    play = { engine.play() },
                    setVolume = { engine.setVolume(it) },
                    setMuted = { engine.setMuted(it) },
                )
            }
        },
        isMuted = isMuted,
        onRegain = onRegain,
    )

    override fun isAudioFocusActive(): Boolean = delegate.isAudioFocusActive()

    override fun registerAudioFocus() = delegate.registerAudioFocus()

    override fun unregisterAudioFocus() = delegate.unregisterAudioFocus()

    override fun registerBecomingNoisy() = delegate.registerBecomingNoisy()

    override fun release() = delegate.release()
}
