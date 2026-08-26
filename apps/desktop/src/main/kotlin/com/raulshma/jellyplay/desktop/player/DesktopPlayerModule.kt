package com.raulshma.jellyplay.desktop.player

import com.raulshma.jellyplay.core.data.playback.AudioQueueFacade
import com.raulshma.jellyplay.feature.player.video.engine.PlayerEngineFactory
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Desktop player wiring (Phase V2 + wave 9A). Wave 9A moves engine
 * construction to the shared session pipeline: [PlayerEngineFactory] is bound
 * here (the shared desktopPlayerVideoModule deliberately does not bind it —
 * MpvDesktopEngine is an app-layer type) to a factory that creates one mpv
 * engine PER SESSION carrying the composing SwingPanel surface's HWND; the
 * former `single<MediaEngine> { MpvDesktopEngine() }` (an eagerly-initialized
 * process-wide mpv context with no window, unused) was removed with it.
 *
 * Construction stays lazy: on a machine without libmpv the app still boots,
 * and the failure surfaces through MediaEngine.errorFlow (EngineError.Render)
 * the first time playback starts — matching how missing-codec engines degrade
 * on Android.
 *
 * Music v1 (Wave wC) adds the [AudioQueueFacade] desktop definition: the
 * [StubAudioQueueFacade] browse-live/playback-degrades binding that lets the
 * music section's ViewModels resolve. Desktop-Koin-only — Android keeps its
 * existing Hilt interop single (one framework per type), and the stub swaps
 * for a real queue impl when the desktop engine grows queue semantics.
 */
val desktopPlayerModule: Module = module {
    single<PlayerEngineFactory> { DesktopMpvPlayerEngineFactory() }
    single<AudioQueueFacade> { StubAudioQueueFacade() }
}
