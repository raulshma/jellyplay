package com.raulshma.jellyplay.desktop.player

import com.raulshma.jellyplay.core.data.playback.AudioEffectsManager
import com.raulshma.jellyplay.core.data.playback.AudioQueueFacade
import com.raulshma.jellyplay.core.data.playback.AudioQueueManager
import com.raulshma.jellyplay.core.data.playback.DefaultAudioQueueFacade
import com.raulshma.jellyplay.core.datastore.playback.PlaybackStore
import com.raulshma.jellyplay.feature.player.audio.AudioPlayerCast
import com.raulshma.jellyplay.feature.player.audio.AudioPlayerEngine
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Desktop player wiring (Phase V2 + wave 9B real audio).
 *
 * - `single<MediaEngine>` stays the VIDEO engine (MpvDesktopEngine for the
 *   desktop shell's one video session; the shared PlayerSessionManager takes
 *   over engine creation when its factory migrates).
 * - [DesktopAudioQueueManager] is the real desktop audio core (wave 9B): the
 *   Android media3 AudioPlaybackManager semantics mirrored over a DEDICATED
 *   audio-only MpvDesktopEngine (`vo=null`), exposed as BOTH the
 *   [AudioQueueManager] contract and the player-audio [AudioPlayerEngine]
 *   seam — the same one-single-two-contracts shape as Android's manager.
 * - [AudioQueueFacade] flips from the retired wave-wC stub to the shared
 *   [DefaultAudioQueueFacade] over the desktop queue manager — every
 *   play/enqueue/instant-mix button in the music section is now real.
 * - [AudioEffectsManager] / [AudioPlayerCast] are the honest-degradation
 *   desktop impls (state-only effects, never-connected cast).
 * - Per-item stream resolution rides [DesktopAudioSourceResolver] — the same
 *   shared `PlaybackRepository.getStreamUrl` overload + adaptive bitrate tier
 *   the Android audio browser uses, with auth in the `api_key` URL parameter
 *   (no request headers, matching Android's header-less media3 stack).
 *
 * Engine construction stays lazy inside the manager (created on first
 * `play()`): on a machine without libmpv the app still boots and the failure
 * surfaces through playbackError — matching how missing-codec engines degrade
 * on Android. The manager's app-lifetime `start()` (queue restore +
 * persistence observation, the Android `manager.start()` twin) is invoked
 * from Main.kt after startKoin.
 */
val desktopPlayerModule: Module = module {

    single<MediaEngine> { MpvDesktopEngine() }

    single<AudioTrackResolver> {
        val playbackStore = get<PlaybackStore>()
        DesktopAudioSourceResolver(
            mediaRepository = get(),
            playbackRepository = get(),
            playbackSourceResolver = get(),
            adaptiveBitrateSelector = get(),
            streamingQualityProvider = { playbackStore.playback.first().streamingQuality },
        )
    }

    single {
        DesktopAudioQueueManager(
            trackResolver = get(),
            playbackRepository = get(),
            imageUrlProvider = get(),
            queuePersistenceHelper = get(),
            lyricsManager = get(),
            sleepTimerManager = get(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
            engineFactory = { MpvDesktopEngine(extraOptions = mapOf("vo" to "null")) },
        )
    }
    single<AudioQueueManager> { get<DesktopAudioQueueManager>() }
    single<AudioPlayerEngine> { get<DesktopAudioQueueManager>() }
    single<AudioEffectsManager> { DesktopAudioEffectsManager() }
    single<AudioPlayerCast> { DesktopAudioPlayerCast() }

    single<AudioQueueFacade> {
        DefaultAudioQueueFacade(
            queueManager = get(),
            mediaRepository = get(),
            imageUrlProvider = get(),
        )
    }
}
