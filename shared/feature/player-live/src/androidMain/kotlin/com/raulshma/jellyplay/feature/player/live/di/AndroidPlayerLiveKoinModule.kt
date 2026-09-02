package com.raulshma.jellyplay.feature.player.live.di

import android.content.Context
import com.raulshma.jellyplay.core.network.di.NetworkQualifiers
import com.raulshma.jellyplay.core.ui.player.TranscodeReasonsFormatter
import com.raulshma.jellyplay.feature.player.live.AndroidPipController
import com.raulshma.jellyplay.feature.player.live.PipController
import com.raulshma.jellyplay.feature.player.live.engine.ExoLiveEngineFactory
import com.raulshma.jellyplay.feature.player.live.engine.LiveEngineFactory
import com.raulshma.jellyplay.feature.player.live.engine.LivePlayerAudio
import com.raulshma.jellyplay.feature.player.live.engine.Media3LivePlayerAudio
import com.raulshma.jellyplay.feature.player.live.engine.TranscodeReasonsRenderer
import okhttp3.OkHttpClient
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Android platform pick for the live player's four ctor seams
 * (subtitle-tester's `androidSubtitleTesterModule(context)` pattern):
 *  - [LiveEngineFactory] — [ExoLiveEngineFactory] over the application
 *    context and the shared `NetworkQualifiers.streamingHttpClient` (the
 *    same named instance the VOD PlayerEngineFactory uses);
 *  - [LivePlayerAudio] — a factory (per-ViewModel) whose `bind(owner)` is
 *    invoked from the VM's init; it wraps the legacy PlayerAudioLifecycle;
 *  - [TranscodeReasonsRenderer] — delegates to the legacy core:ui
 *    TranscodeReasonsFormatter (Android-coupled, dies at its own conveyor
 *    move);
 *  - [com.raulshma.jellyplay.feature.player.live.PipController] — wave 19C:
 *    [AndroidPipController] over the legacy core:data PipController
 *    singleton, the same instance the host PlayerActivity injects, so the
 *    live ViewModel's PiP writes and the Activity's collectors observe one
 *    state (the wave-8C player-video adapter's relationship).
 *
 * Registered only app-side (JellyPlayApplication); desktop's registration of
 * `playerLiveModule` stays documented-latent — the Android-only screen never
 * composes there.
 */
fun androidPlayerLiveModule(context: Context): Module = module {
    single<LiveEngineFactory> {
        ExoLiveEngineFactory(
            appContext = context,
            streamingClient = get<OkHttpClient>(NetworkQualifiers.streamingHttpClient),
        )
    }
    factory<LivePlayerAudio> { Media3LivePlayerAudio(context) }
    single<TranscodeReasonsRenderer> {
        TranscodeReasonsRenderer { rawReasons ->
            TranscodeReasonsFormatter.format(context, rawReasons)
                .map { it.renderedText }
        }
    }
    single<PipController> {
        AndroidPipController(get())
    }
}
