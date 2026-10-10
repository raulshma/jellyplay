package com.raulshma.jellyplay.feature.player.video

import android.app.ActivityManager
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.SystemClock
import android.provider.OpenableColumns
import com.raulshma.jellyplay.core.data.playback.AdaptiveBitrateManager
import com.raulshma.jellyplay.core.data.playback.BecomingNoisyPauseReceiver
import com.raulshma.jellyplay.core.data.playback.HeadsetPlugReceiver
import com.raulshma.jellyplay.core.data.playback.HeadsetResumePolicy
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
        isResumeOnPlugEnabled: () -> Boolean,
    ): VideoPlayerAudio = AndroidVideoPlayerBecomingNoisy(
        context = context,
        getEngine = getEngine,
        isResumeOnPlugEnabled = isResumeOnPlugEnabled,
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
 * the dual coverage the shared lifecycle had) — plus the
 * resume-on-headset-insert twin: when the opt-in pref is on, a headset
 * re-plug within core:data's [HeadsetResumePolicy] freshness window resumes
 * the engine the becoming-noisy event itself paused (the unplug pause path
 * marks the session-scoped holder ONLY when the engine was actually playing
 * at broadcast time, so a user-initiated pause never auto-resumes; the plug
 * consumer additionally requires the engine to be paused right now).
 *
 * The broadcast chassis live in core:data ([BecomingNoisyPauseReceiver] —
 * one home shared with the live player — and [HeadsetPlugReceiver]); this
 * adapter owns the session-scoped marker that joins them and supplies the
 * engine pause/resume targets (re-read per event, so engine swaps and
 * teardown stay correct).
 */
internal class AndroidVideoPlayerBecomingNoisy(
    context: Context,
    private val getEngine: () -> MediaEngine?,
    private val isResumeOnPlugEnabled: () -> Boolean,
) : VideoPlayerAudio {

    /**
     * `SystemClock.elapsedRealtime` timestamp of the last becoming-noisy
     * pause that actually stopped playback — null until one does. Session-
     * scoped by construction (this instance is per-ViewModel, registered in
     * the wiring's arm phase and released with the session), so a re-entered
     * player never inherits a stale marker.
     */
    @Volatile
    private var noisyPauseAtMs: Long? = null

    private val becomingNoisy = BecomingNoisyPauseReceiver(context) {
        val engine = getEngine()
        val wasPlaying = engine?.isPlaying?.value == true
        engine?.pause()
        if (HeadsetResumePolicy.shouldMarkNoisyPause(wasPlaying)) {
            noisyPauseAtMs = SystemClock.elapsedRealtime()
        }
    }

    private val headsetPlug = HeadsetPlugReceiver(context) {
        val resumed = HeadsetResumePolicy.shouldResumeOnHeadsetPlug(
            nowMs = SystemClock.elapsedRealtime(),
            noisyPauseAtMs = noisyPauseAtMs,
            prefEnabled = isResumeOnPlugEnabled(),
        )
        // One resume attempt per unplug cycle: the marker is consumed whether
        // or not an engine was around to take it.
        noisyPauseAtMs = null
        if (resumed) {
            val engine = getEngine()
            if (engine != null && !engine.isPlaying.value) engine.play()
        }
    }

    override fun register() {
        becomingNoisy.register()
        headsetPlug.register()
    }

    override fun release() {
        becomingNoisy.release()
        headsetPlug.release()
    }
}
