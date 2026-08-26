package com.raulshma.jellyplay.di

import android.app.Application
import com.raulshma.jellyplay.core.data.cast.CastManager
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.raulshma.jellyplay.core.data.cast.CastMediaOptions
import com.raulshma.jellyplay.core.data.playback.AudioPlaybackManager
import com.raulshma.jellyplay.core.data.playback.ThemeMusicPlayer
import com.raulshma.jellyplay.core.data.widget.ContinueWatchingBroadcaster
import com.raulshma.jellyplay.core.data.widget.LibrarySyncHook
import com.raulshma.jellyplay.feature.details.DetailAudioPlayback
import com.raulshma.jellyplay.feature.details.DetailThemeMusic
import com.raulshma.jellyplay.feature.music.feedback.MusicMessageBus
import com.raulshma.jellyplay.feature.player.audio.AudioPlayerCast
import com.raulshma.jellyplay.feature.player.audio.AudioPlayerEngine
import com.raulshma.jellyplay.core.ui.feedback.UserMessageBus
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import org.koin.core.module.Module
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.koin.dsl.module

/**
 * Wave 8A rewrite: core-side Hilt is extinct — every type this module used
 * to pull through the Hilt EntryPoint (playback/cast singletons, schedulers,
 * DownloadIntake, StreamingSubtitleStore, UserMessageBus, AudioQueueFacade)
 * is Koin-owned now (:core:data androidCoreDataModule, :core:ui
 * androidCoreUiModule), so the singles below resolve the container directly.
 * The adapter classes stay: they adapt Koin-owned media3 singletons onto the
 * shared feature seams.
 *
 * The EntryPoint shrank to the two app-internal Hilt-owned widget types
 * (WidgetModule @Binds) the home conveyor still needs. LAZY remains
 * load-bearing for those: startKoin runs before Hilt's component exists, so
 * the single's lambda must not touch Hilt at definition time.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface HiltInteropEntryPoint {
    fun continueWatchingBroadcaster(): ContinueWatchingBroadcaster
    fun librarySyncHook(): LibrarySyncHook
}

private fun interopEntryPoint(application: Application): HiltInteropEntryPoint =
    EntryPointAccessors.fromApplication(application, HiltInteropEntryPoint::class.java)

/** Bridges the shared music module's [MusicMessageBus] seam to the app bus. */
private class HiltMusicMessageBus(
    private val bus: UserMessageBus,
) : MusicMessageBus {
    override fun error(message: String) = bus.error(message)
}

/**
 * Details conveyor (Phase X cutover wave): the shared details module's
 * per-item audio-playback and ambient-theme-music seams over the two
 * media3 singletons in :core:data (Koin-owned since wave 8A).
 * Desktop halves are the no-op defs in the module's jvmMain.
 */
private class HiltDetailAudioPlayback(
    private val manager: AudioPlaybackManager,
) : DetailAudioPlayback {
    override fun play(itemId: String) = manager.play(itemId)
}

private class HiltDetailThemeMusic(
    private val player: ThemeMusicPlayer,
) : DetailThemeMusic {
    override fun playThemeFor(itemId: String) = player.playThemeFor(itemId)
    override fun stop() = player.stop()
}

/**
 * Audio-player conveyor (wave 7A): the transport/metadata/lyrics half of
 * the legacy AudioPlaybackManager that is not on the shared AudioQueueManager
 * / AudioEffectsManager contracts - a pure delegate, same shape as
 * HiltDetailAudioPlayback above.
 */
private class HiltAudioPlayerEngine(
    private val manager: AudioPlaybackManager,
) : AudioPlayerEngine {
    override val title get() = manager.title
    override val artist get() = manager.artist
    override val artistId get() = manager.artistId
    override val album get() = manager.album
    override val albumArtUrl get() = manager.albumArtUrl
    override val isPlaying get() = manager.isPlaying
    override val currentPosition get() = manager.currentPosition
    override val duration get() = manager.duration
    override val speed get() = manager.speed
    override val playbackError get() = manager.playbackError
    override val isLoadingItem get() = manager.isLoadingItem
    override val crossfadeDurationMs get() = manager.crossfadeDurationMs
    override val undoEvents get() = manager.undoEvents
    override val abLoopStartMs get() = manager.abLoopStartMs
    override val abLoopEndMs get() = manager.abLoopEndMs
    override val lyrics get() = manager.lyrics
    override val currentLyricIndex get() = manager.currentLyricIndex
    override val lyricsSource get() = manager.lyricsSource
    override val isFetchingLyrics get() = manager.isFetchingLyrics
    override val lyricsOffsetMs get() = manager.lyricsOffsetMs
    override fun play(itemId: String) = manager.play(itemId)
    override fun seekTo(positionMs: Long) = manager.seekTo(positionMs)
    override fun togglePlayPause() = manager.togglePlayPause()
    override fun pause() = manager.pause()
    override fun changePlaybackSpeed(value: Float) = manager.changePlaybackSpeed(value)
    override fun setSkipPreviousThreshold(ms: Long) = manager.setSkipPreviousThreshold(ms)
    override fun setCrossfadeDurationMs(ms: Long) = manager.setCrossfadeDurationMs(ms)
    override fun setGaplessEnabled(enabled: Boolean) = manager.setGaplessEnabled(enabled)
    override fun getImageUrl(itemId: String): String = manager.getImageUrl(itemId)
    override fun searchLyrics(query: String, callback: (Result<List<com.raulshma.jellyplay.core.model.LrcLibTrack>>) -> Unit) =
        manager.searchLyrics(query, callback)
    override fun applyLyrics(lrcLibId: Long) = manager.applyLyrics(lrcLibId)
    override fun setLyricsOffset(offsetMs: Long) = manager.setLyricsOffset(offsetMs)
    override fun stopAndRelease() = manager.stopAndRelease()
    override fun undoLastQueueOperation(): Boolean = manager.undoLastQueueOperation()
    override fun cycleAbLoop() = manager.cycleAbLoop()
}

/**
 * Audio-player conveyor (wave 7A): the cast half over the Koin-owned
 * CastManager. Holds the application context the legacy discovery/connect
 * calls need and hides the media3 MediaItem/Player.Listener construction
 * the player VM used to build inline (audio casts carry no subtitle/quality
 * variants, so CastMediaOptions stays the empty default). Devices surface
 * as display names - the picker shows names only, exactly like the legacy
 * dialog's devices[which].
 */
private class HiltAudioPlayerCast(
    private val castManager: CastManager,
    context: Application,
) : AudioPlayerCast {
    private val appContext = context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    override val isConnected get() = castManager.isConnectedFlow
    override val discoveredDeviceNames = castManager.discoveredDevices
        .map { devices -> devices.map { it.name } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())
    override fun startDiscovery() = castManager.startDiscovery(appContext)
    override fun stopDiscovery() = castManager.stopDiscovery()
    override fun connect(deviceName: String) {
        castManager.discoveredDevices.value
            .firstOrNull { it.name == deviceName }
            ?.let { castManager.connect(appContext, it) }
    }
    override fun disconnect() = castManager.disconnect(appContext)
    override fun acquireConsumer() = castManager.acquireConsumer()
    override fun releaseConsumer() = castManager.releaseConsumer()
    override fun loadMedia(itemId: String, startPositionMs: Long) {
        castManager.loadMedia(
            mediaItem = MediaItem.Builder().setMediaId(itemId).build(),
            startPositionMs = startPositionMs,
            listener = object : Player.Listener {},
            options = CastMediaOptions(),
        )
    }
    override fun play() = castManager.play()
    override fun pause() = castManager.pause()
    override fun seekTo(positionMs: Long) = castManager.seekTo(positionMs)
    override fun setVolume(volume: Float) = castManager.setVolume(volume)
}

fun hiltInteropModule(application: Application): Module = module {
    // Adapter singles: each wraps a Koin-owned media3/cast singleton onto a
    // shared feature seam. The underlying types resolve from the container
    // (androidCoreDataModule/androidCoreUiModule own them since wave 8A) —
    // no Hilt reach-through remains except the two app-internal widget
    // bindings below.
    single<MusicMessageBus> { HiltMusicMessageBus(get()) }
    single<DetailAudioPlayback> { HiltDetailAudioPlayback(get()) }
    single<DetailThemeMusic> { HiltDetailThemeMusic(get()) }
    single<AudioPlayerEngine> { HiltAudioPlayerEngine(get()) }
    single<AudioPlayerCast> { HiltAudioPlayerCast(get(), application) }

    // Home conveyor (Phase X cutover): the WorkManager-backed schedulers
    // (PlaybackSyncScheduler/TvWatchNextScheduler) are Koin-owned since wave
    // 8A and resolve from androidCoreDataModule. The two app-internal widget
    // broadcast types stay Hilt-owned (app WidgetModule @Binds) — LAZY is
    // load-bearing for them: startKoin runs before Hilt's component exists.
    single<ContinueWatchingBroadcaster> { interopEntryPoint(application).continueWatchingBroadcaster() }
    single<LibrarySyncHook> { interopEntryPoint(application).librarySyncHook() }
}
