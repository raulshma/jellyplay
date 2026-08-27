package com.raulshma.jellyplay.desktop.player

import com.raulshma.jellyplay.core.data.playback.AudioEffectsManager
import com.raulshma.jellyplay.core.data.playback.AudioQueueFacade
import com.raulshma.jellyplay.core.data.playback.AudioQueueManager
import com.raulshma.jellyplay.core.data.playback.DefaultAudioQueueFacade
import com.raulshma.jellyplay.core.datastore.playback.PlaybackStore
import com.raulshma.jellyplay.feature.player.audio.AudioPlayerCast
import com.raulshma.jellyplay.feature.player.audio.AudioPlayerEngine
import com.raulshma.jellyplay.feature.player.video.engine.PlayerEngineFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Desktop player wiring (Phase V2 + waves 9A/9B).
 *
 * - Video: [PlayerEngineFactory] is bound here (the shared
 *   desktopPlayerVideoModule deliberately does not bind it — MpvDesktopEngine
 *   is an app-layer type) to a factory that creates one mpv engine PER SESSION
 *   carrying the composing SwingPanel surface's HWND.
 * - [DesktopAudioQueueManager] is the real desktop audio core: the Android
 *   media3 AudioPlaybackManager semantics mirrored over a DEDICATED
 *   audio-only MpvDesktopEngine (`vo=null`), exposed as BOTH the
 *   [AudioQueueManager] contract and the player-audio [AudioPlayerEngine]
 *   seam — the same one-single-two-contracts shape as Android's manager.
 *   The audio engine lives entirely inside the queue manager (its
 *   `engineFactory` lambda); there is deliberately NO shared
 *   `single<MediaEngine>` on desktop — a process-wide windowless mpv context
 *   would only be dead weight next to the per-session video engine and the
 *   audio manager's own engine.
 * - [AudioQueueFacade] is the shared [DefaultAudioQueueFacade] over the
 *   desktop queue manager — every play/enqueue/instant-mix button in the
 *   music section is real.
 * - [AudioEffectsManager] is the desktop [DesktopAudioEffectsManager] (full
 *   state machine + mpv `af` DSP via the queue manager's engine); the
 *   concrete instance is wired into the queue manager so effect mutations
 *   reach the engine live. [AudioPlayerCast] is the never-connected desktop
 *   cast seam.
 * - Per-item stream resolution rides [DesktopAudioSourceResolver] — the same
 *   shared `PlaybackRepository.getStreamUrl` overload + adaptive bitrate tier
 *   the Android audio browser uses, with auth in the `api_key` URL parameter
 *   (no request headers, matching Android's header-less media3 stack).
 *
 * Engine construction stays lazy (video factory defers until the session asks;
 * the audio manager creates its engine on first `play()`): on a machine
 * without libmpv the app still boots and failures surface through
 * MediaEngine.errorFlow / playbackError respectively — matching how
 * missing-codec engines degrade on Android. The manager's app-lifetime
 * `start()` (queue restore + persistence observation, the Android
 * `manager.start()` twin) is invoked from Main.kt after startKoin.
 */
val desktopPlayerModule: Module = module {
    // Wave 13B session harness: app-lifetime recorder of everything the video
    // factory creates (engine + surface branch + state/position activity).
    // Observation only — see EngineActivityRecorder.
    single { EngineActivityRecorder() }
    single<PlayerEngineFactory> { DesktopMpvPlayerEngineFactory(recorder = get()) }

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
            effectsManager = get(),
            engineFactory = { MpvDesktopEngine(extraOptions = mapOf("vo" to "null")) },
        )
    }
    single<AudioQueueManager> { get<DesktopAudioQueueManager>() }
    single<AudioPlayerEngine> { get<DesktopAudioQueueManager>() }
    single { DesktopAudioEffectsManager() }
    single<AudioEffectsManager> { get<DesktopAudioEffectsManager>() }
    single<AudioPlayerCast> { DesktopAudioPlayerCast() }

    single<AudioQueueFacade> {
        DefaultAudioQueueFacade(
            queueManager = get(),
            mediaRepository = get(),
            imageUrlProvider = get(),
        )
    }
}
