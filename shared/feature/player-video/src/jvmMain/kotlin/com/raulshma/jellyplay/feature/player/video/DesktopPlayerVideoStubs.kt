package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.data.playback.AdaptiveBitrateManager
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.datastore.syncplaycast.SyncPlayCastStore
import com.raulshma.jellyplay.core.model.PlaybackMode
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.TrickplayInfo
import com.raulshma.jellyplay.feature.player.video.engine.AspectRatio
import com.raulshma.jellyplay.feature.player.video.engine.EngineCapabilities
import com.raulshma.jellyplay.feature.player.video.engine.EngineCapabilityMatrix
import com.raulshma.jellyplay.feature.player.video.engine.EngineConfig
import com.raulshma.jellyplay.feature.player.video.engine.EngineError
import com.raulshma.jellyplay.feature.player.video.engine.EnginePlaybackState
import com.raulshma.jellyplay.feature.player.video.engine.EngineVideoStats
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import com.raulshma.jellyplay.feature.player.video.engine.MediaTrack
import com.raulshma.jellyplay.feature.player.video.engine.PlaybackRequest
import com.raulshma.jellyplay.feature.player.video.engine.PlayerEngineFactory
import com.raulshma.jellyplay.feature.player.video.engine.SubtitleEvent
import com.raulshma.jellyplay.feature.player.video.engine.SubtitleSource
import com.raulshma.jellyplay.feature.player.video.engine.TimedCue
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.model.TrackType
import com.raulshma.jellyplay.feature.player.video.trickplay.TrickplayController
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Desktop (jvmMain) stubs for the wave-8C video player seams (wave 8C):
 * [VideoPlayerViewModel] is commonMain and desktop-resolvable, but no desktop
 * playback host exists yet (Route.VideoPlayer stays guarded in DesktopAppRoot;
 * the SwingPanel/HWND surface is queued work) — so every seam resolves to a
 * no-op here, mirroring the desktopMusicMessageBusModule precedent. The
 * jvmTest suite builds its own fakes and never resolves Koin.
 */

/** Inert engine (JvmNoOpEngine twin of androidMain's NoOpEngine, minus the View surface). */
internal class JvmNoOpEngine : MediaEngine {

    override val capabilities: EngineCapabilities = EngineCapabilityMatrix.EXTERNAL
    override val displayName: String = PlayerType.EXTERNAL.displayName

    private val _playbackState = MutableStateFlow(EnginePlaybackState.IDLE)
    override val playbackState: StateFlow<EnginePlaybackState> = _playbackState.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _bufferedPositionMs = MutableStateFlow(0L)
    override val bufferedPositionMs: StateFlow<Long> = _bufferedPositionMs.asStateFlow()

    private val _videoStats = MutableStateFlow(EngineVideoStats())
    override val videoStats: StateFlow<EngineVideoStats> = _videoStats.asStateFlow()

    private val _availableTracks = MutableStateFlow(emptyList<MediaTrack>())
    override val availableTracks: StateFlow<List<MediaTrack>> = _availableTracks.asStateFlow()

    override val currentCues: StateFlow<List<TimedCue>> = MutableStateFlow(emptyList())

    override val liveSubtitleCue: StateFlow<CharSequence?> = MutableStateFlow(null)

    private val _pollingIntervalMs = MutableStateFlow(0L)
    override val pollingIntervalMs: StateFlow<Long> = _pollingIntervalMs.asStateFlow()

    private val _videoStatsEnabled = MutableStateFlow(false)
    override val videoStatsEnabled: StateFlow<Boolean> = _videoStatsEnabled.asStateFlow()

    override val currentPositionMs: Long = 0L
    override val durationMs: Long = 0L
    override val positionFlow: Flow<Long> = emptyFlow()
    override val errorFlow: Flow<EngineError> = emptyFlow()
    override val subtitleEvents: Flow<SubtitleEvent> = emptyFlow()
    override val playbackSpeed: Float = 1f
    override val audioSessionId: Int = -1

    @Volatile
    private var volumeValue: Float = 1f
    override val volume: Float get() = volumeValue

    override fun load(request: PlaybackRequest) { /* no-op: no desktop engine yet */ }
    override fun release() { /* no-op */ }
    override fun play() { /* no-op */ }
    override fun pause() { /* no-op */ }
    override fun stop() { /* no-op */ }
    override fun seekTo(positionMs: Long) { /* no-op */ }
    override fun setPlaybackSpeed(speed: Float) { /* no-op */ }
    override fun setPollingIntervalMs(ms: Long) { /* no-op */ }
    override fun setVideoStatsEnabled(enabled: Boolean) { /* no-op */ }
    override fun updateConfig(config: EngineConfig) { /* no-op */ }
    override fun selectTrack(type: TrackType, index: Int) { /* no-op */ }
    override fun setMaxVideoBitrate(bps: Int?) { /* no-op */ }
    override fun setVolume(value: Float) {
        volumeValue = value.coerceIn(0f, 1f)
    }

    override fun increaseVolume(delta: Float) = setVolume(volumeValue + delta)
    override fun decreaseVolume(delta: Float) = setVolume(volumeValue - delta)
    override fun setMuted(muted: Boolean) {
        if (muted) setVolume(0f)
    }

    override fun applySubtitleStyle(style: SubtitleStyle) { /* no-op */ }
    override fun setAspectRatio(ratio: AspectRatio) { /* no-op */ }
}

/**
 * Public (not internal) so the app-side desktop engine factory
 * (apps/desktop DesktopMpvPlayerEngineFactory) can delegate the
 * PlayerType.EXTERNAL pick to it — external playback is launched in a
 * third-party app and reported out-of-band, which a no-op expresses exactly.
 */
object NoOpPlayerEngineFactory : PlayerEngineFactory {
    override suspend fun create(playerType: PlayerType): MediaEngine = JvmNoOpEngine()
}

internal object NoOpMediaSessionController : MediaSessionController {
    override fun createForItem(itemId: String, title: String, subtitle: String) {}
    override fun createForPlayer(player: Any?, sessionId: String, videoItemId: String?) {}
    override fun release() {}
}

internal object NoOpMediaSessionFactory : VideoMediaSessionFactory {
    override fun create(
        getPlayer: () -> Any?,
        getImageUrl: (itemId: String, maxWidth: Int) -> String,
    ): MediaSessionController = NoOpMediaSessionController
}

internal object NoOpCastManager : CastManager {
    override fun acquireConsumer() {}
    override fun releaseConsumer() {}
    override fun markBackgroundCasting(casting: Boolean) {}
    override val isBackgroundCasting: Boolean get() = false
    override fun softRelease() {}
    override val castPlayerForSession: Any? get() = null
}

internal object NoOpJellyfinRemotePlayCastStrategy : JellyfinRemotePlayCastStrategy {
    override val isConnected: StateFlow<Boolean> = MutableStateFlow(false)
    override fun loadMedia(
        itemId: String,
        startPositionMs: Long,
        mediaSourceId: String?,
        audioStreamIndex: Int?,
        subtitleStreamIndex: Int?,
    ) {}
}

internal object NoOpActivePlayerController : ActivePlayerController {
    override val engine: com.raulshma.jellyplay.core.data.remote.RemotePlayableEngine? get() = null
    override fun bindEngine(engine: com.raulshma.jellyplay.core.data.remote.RemotePlayableEngine) {}
    override fun clearEngine() {}
}

internal class NoOpPipController : PipController {
    override val isInPipMode: StateFlow<Boolean> = MutableStateFlow(false)
    override val pipDismissed: StateFlow<Boolean> = MutableStateFlow(false)
    override var pipTransport: PipTransport? = null
    override var pipHasNext: Boolean = false
    override fun setPlaying(playing: Boolean) {}
    override fun setControlsLocked(locked: Boolean) {}
    override fun requestAutoEnterPip(shouldEnter: Boolean) {}
    override fun requestAutoExitPip() {}
    override fun clearPipDismissed() {}
    override fun setPipAspectRatio(aspect: Pair<Int, Int>?) {}
    override fun updatePipSourceRect(left: Int, top: Int, right: Int, bottom: Int) {}
    override fun reset() {}
}

internal object NoOpPlayerVideoMessageBus : PlayerVideoMessageBus {
    override fun info(message: String) {}
    override fun error(message: String) {}
    override fun info(message: PlayerVideoMessage) {}
}

internal object NoOpTrickplayController : TrickplayController {
    override fun initialize(itemId: String, trickplayInfo: TrickplayInfo) {}
    override fun initializeWithCache(itemId: String, trickplayInfo: TrickplayInfo, cacheDir: File) {}
    override fun initializeLocal(itemId: String, trickplayInfo: TrickplayInfo, cacheDir: File) {}
    override fun clear() {}
    override suspend fun getThumbnail(positionMs: Long): Any? = null
}

internal class NoOpPlayerCastController : PlayerCastController {
    override val isCastAvailable: Boolean get() = false
    override val isCastConnected: Boolean get() = false
    override val castPositionMs: StateFlow<Long> = MutableStateFlow(0L)
    override val castDurationMs: StateFlow<Long> = MutableStateFlow(0L)
    override val castIsPlaying: StateFlow<Boolean> = MutableStateFlow(false)
    override val castVolumeFlow: StateFlow<Float> = MutableStateFlow(1f)
    override val isConnectedFlow: StateFlow<Boolean> = MutableStateFlow(false)
    override val isConnectingFlow: StateFlow<Boolean> = MutableStateFlow(false)
    override val isBackgroundCasting: Boolean get() = false
    override val backgroundCastingEnabled: Boolean get() = false
    override fun castPlay() {}
    override fun castPause() {}
    override fun castSeekTo(positionMs: Long) {}
    override fun setCastVolume(volume: Float) {}
    override fun onCastDisconnected() {}
    override fun castToDevice() {}
    override fun updateCastStrategyForEngine(engine: MediaEngine) {}
}

internal object NoOpFontProvider : com.raulshma.jellyplay.feature.player.video.subtitle.FontProvider {
    override suspend fun installUserFont(
        uri: String,
    ): com.raulshma.jellyplay.feature.player.video.subtitle.InstalledFont? = null

    override suspend fun prewarm() {}
}

internal object NoOpSubtitlePreviewRepository : com.raulshma.jellyplay.feature.player.video.subtitle.SubtitlePreviewRepository {
    override suspend fun loadCues(
        source: SubtitleSource,
        headers: Map<String, String>,
    ): List<TimedCue>? = null

    override fun clearCache(url: String?) {}
}

internal object NoOpVideoPlayerAudio : VideoPlayerAudio {
    override fun isAudioFocusActive(): Boolean = false
    override fun registerAudioFocus() {}
    override fun unregisterAudioFocus() {}
    override fun registerBecomingNoisy() {}
    override fun release() {}
}

/**
 * Desktop actual of the [VideoPlayerPlatform] aggregate seam: no platform
 * media stack yet, so the low-RAM gate is false, content-URI IO reads as
 * empty (nothing picks SAF content on desktop), the offline probe reports
 * unknown, and the controller factories return no-ops.
 */
internal class DesktopVideoPlayerPlatform : VideoPlayerPlatform {

    override fun isLowRamDevice(): Boolean = false

    override fun queryFileSizeBytes(uri: String): Long = 0L

    override fun readBytes(uri: String): ByteArray = ByteArray(0)

    override val offlineMediaProbe: OfflineMediaProbe = object : OfflineMediaProbe {
        override fun extractDurationMs(path: String): Long? = null
        override fun mapContainerToMime(container: String?): String? = null
    }

    override fun createTrickplayController(playbackRepository: PlaybackRepository): TrickplayController =
        NoOpTrickplayController

    override fun createCastController(
        playbackRepository: PlaybackRepository,
        adaptiveBitrateManager: AdaptiveBitrateManager,
        syncPlayCastStore: SyncPlayCastStore,
        getEngine: () -> MediaEngine?,
        getCurrentPlaybackMode: () -> PlaybackMode,
        getSessionState: () -> PlayerSessionState,
    ): PlayerCastController = NoOpPlayerCastController()

    override fun createAudioLifecycle(
        getEngine: () -> MediaEngine?,
        isMuted: () -> Boolean,
        onRegain: (() -> Unit)?,
    ): VideoPlayerAudio = NoOpVideoPlayerAudio
}

