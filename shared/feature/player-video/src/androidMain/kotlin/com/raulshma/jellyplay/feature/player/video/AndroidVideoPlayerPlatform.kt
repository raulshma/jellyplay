package com.raulshma.jellyplay.feature.player.video

import android.app.ActivityManager
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.raulshma.jellyplay.core.data.playback.AdaptiveBitrateManager
import com.raulshma.jellyplay.core.data.playback.BecomingNoisyPauseReceiver
import com.raulshma.jellyplay.core.data.playback.focus.PlaybackFocus
import com.raulshma.jellyplay.core.data.playback.focus.VideoPlaybackSurface
import com.raulshma.jellyplay.core.data.cast.CastManager
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.datastore.syncplaycast.SyncPlayCastStore
import com.raulshma.jellyplay.core.model.PlaybackMode
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import com.raulshma.jellyplay.feature.player.video.engine.ContainerMimeMapper
import com.raulshma.jellyplay.feature.player.video.trickplay.TrickplayController
import com.raulshma.jellyplay.feature.player.video.trickplay.TrickplayManager

/**
 * Android actual of the [VideoPlayerPlatform] aggregate seam: every
 * member body is the exact code the commonMain-bound ViewModel /
 * PlayerSessionManager used to inline — moved verbatim, not re-modeled.
 * Captures the app [Context] plus the Hilt-owned legacy [CastManager] the
 * cast-controller construction needs (the ViewModel no longer sees the legacy
 * type).
 */
internal class AndroidVideoPlayerPlatform(
    private val context: Context,
    private val castManager: CastManager,
    // The video focus slice (ADR-0004): resolved by the Koin module from the
    // androidCoreData focus bindings (the module-owned executor + the VIDEO
    // surface singleton it commands).
    override val playbackFocus: PlaybackFocus,
    override val videoFocusSurface: VideoPlaybackSurface?,
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
        imageUrlProvider: com.raulshma.jellyplay.core.data.util.ImageUrlProvider,
        adaptiveBitrateManager: AdaptiveBitrateManager,
        syncPlayCastStore: SyncPlayCastStore,
        getEngine: () -> MediaEngine?,
        getCurrentPlaybackMode: () -> PlaybackMode,
        getSessionState: () -> PlayerSessionState,
    ): PlayerCastController = AndroidPlayerCastController(
        castManager = castManager,
        playbackRepository = playbackRepository,
        imageUrlProvider = imageUrlProvider,
        adaptiveBitrateManager = adaptiveBitrateManager,
        syncPlayCastStore = syncPlayCastStore,
        getEngine = getEngine,
        getCurrentPlaybackMode = getCurrentPlaybackMode,
        getSessionState = getSessionState,
    )

    override fun createBecomingNoisy(
        getEngine: () -> MediaEngine?,
    ): VideoPlayerAudio = AndroidVideoPlayerBecomingNoisy(
        context = context,
        getEngine = getEngine,
    )
}

/**
 * Android actual of the [OfflineMediaProbe] seam: the MediaMetadataRetriever
 * duration extraction and container→MIME mapping the offline load path used
 * inline (move).
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
 * Android actual of the [VideoPlayerAudio] seam: the ACTION_AUDIO_BECOMING_NOISY
 * receiver half of the deleted `PlayerAudioLifecycle` — the focus machinery
 * moved into core:data's PlaybackFocus module (video slice), the receiver
 * stayed behind because it is the only headphone-unplug path for the
 * non-media3 engines (ExoPlayer's built-in
 * `setHandleAudioBecomingNoisy(true)` keeps running alongside it, exactly
 * the dual coverage the shared lifecycle had). The broadcast chassis lives
 * in core:data's [BecomingNoisyPauseReceiver] (one home, shared with the
 * live player); this adapter only supplies the engine pause target.
 */
internal class AndroidVideoPlayerBecomingNoisy(
    context: Context,
    getEngine: () -> MediaEngine?,
) : VideoPlayerAudio {

    private val receiver = BecomingNoisyPauseReceiver(context) { getEngine()?.pause() }

    override fun register() = receiver.register()

    override fun release() = receiver.release()
}
