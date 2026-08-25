package com.raulshma.jellyplay.desktop.player

import com.raulshma.jellyplay.core.data.playback.AudioQueueFacade
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Desktop player wiring (Phase V2). The engine is a single for now — the
 * desktop shell has one window and one playback session; when the shared
 * PlayerSessionManager migrates (§V3) it takes over engine creation via its
 * own factory and this binding flips to that factory's backing type.
 *
 * Construction is lazy: on a machine without libmpv the app still boots, and
 * the failure surfaces through MediaEngine.errorFlow (EngineError.Render) the
 * first time playback starts — matching how missing-codec engines degrade on
 * Android.
 *
 * Music v1 (Wave wC) adds the [AudioQueueFacade] desktop definition: the
 * [StubAudioQueueFacade] browse-live/playback-degrades binding that lets the
 * music section's ViewModels resolve. Desktop-Koin-only — Android keeps its
 * existing Hilt interop single (one framework per type), and the stub swaps
 * for a real queue impl when the desktop engine grows queue semantics.
 */
val desktopPlayerModule: Module = module {
    single<MediaEngine> { MpvDesktopEngine() }
    single<AudioQueueFacade> { StubAudioQueueFacade() }
}
