package com.raulshma.jellyplay.feature.player.video.di

import com.raulshma.jellyplay.core.data.playback.PipController
import com.raulshma.jellyplay.feature.player.video.ActivePlayerController
import com.raulshma.jellyplay.feature.player.video.CastManager
import com.raulshma.jellyplay.feature.player.video.DesktopActivePlayerController
import com.raulshma.jellyplay.feature.player.video.DesktopVideoPlayerPlatform
import com.raulshma.jellyplay.feature.player.video.JellyfinRemotePlayCastStrategy
import com.raulshma.jellyplay.feature.player.video.NoOpCastManager
import com.raulshma.jellyplay.feature.player.video.NoOpFontProvider
import com.raulshma.jellyplay.feature.player.video.NoOpJellyfinRemotePlayCastStrategy
import com.raulshma.jellyplay.feature.player.video.NoOpMediaSessionFactory
import com.raulshma.jellyplay.feature.player.video.NoOpPipController
import com.raulshma.jellyplay.feature.player.video.NoOpPlayerVideoMessageBus
import com.raulshma.jellyplay.feature.player.video.NoOpSubtitlePreviewRepository
import com.raulshma.jellyplay.feature.player.video.PlayerStores
import com.raulshma.jellyplay.feature.player.video.PlayerVideoMessageBus
import com.raulshma.jellyplay.feature.player.video.VideoMediaSessionFactory
import com.raulshma.jellyplay.feature.player.video.VideoPlayerPlatform
import com.raulshma.jellyplay.feature.player.video.VideoPlayerViewModel
import com.raulshma.jellyplay.feature.player.video.engine.PlayerEngineFactory
import com.raulshma.jellyplay.feature.player.video.subtitle.FontProvider
import com.raulshma.jellyplay.feature.player.video.subtitle.SubtitlePreviewRepository
import org.koin.compose.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Desktop wiring for the video player: the VideoPlayerViewModel is
 * commonMain and live-resolvable here. Every repository/DataStore dep
 * resolves from the shared modules the desktop root already loads
 * (dataJvmModule / datastoreCommonModule / desktopDataModule) — including
 * [StreamingSubtitleStore], the real file-backed store in desktopDataModule
 * since the jvmShared promotion of its impl (it was a throwing
 * empty-store stub before that).
 *
 * the desktop playback host is live (SwingPanel/HWND surface +
 * Route.VideoPlayer unguarded on Windows), and the per-session engine
 * factory is NOT bound here — [PlayerEngineFactory] must return an mpv
 * engine carrying the composing surface's HWND, which only the app layer
 * can build (MpvDesktopEngine lives in apps/desktop), so apps/desktop's
 * DesktopPlayerModule owns that binding and delegates EXTERNAL picks to the
 * public NoOpPlayerEngineFactory.  no-op seam bindings stay here;
 * they still cover non-Windows JVMs where the route stays guarded. The
 * jvmTest suite never resolves Koin — it builds its own fakes.
 */
val desktopPlayerVideoModule: Module = module {
    single<VideoPlayerPlatform> { DesktopVideoPlayerPlatform() }
    single<VideoMediaSessionFactory> { NoOpMediaSessionFactory }
    single<CastManager> { NoOpCastManager }
    single<JellyfinRemotePlayCastStrategy> { NoOpJellyfinRemotePlayCastStrategy }
    // desktop receiver port: the REAL registry adapter over the shared
    // core:data ActivePlayerController single (dataJvmModule) — remote
    // playstate/volume/screenshot commands reach the per-session mpv engine,
    // and the player screen binds/unbinds through the same registry Android
    // uses. The no-op object stays for any headless consumer.
    single<ActivePlayerController> { DesktopActivePlayerController(get()) }
    single<PipController> { NoOpPipController() }
    single<PlayerVideoMessageBus> { NoOpPlayerVideoMessageBus }
    single<FontProvider> { NoOpFontProvider }
    single<SubtitlePreviewRepository> { NoOpSubtitlePreviewRepository }
    viewModel { params ->
        VideoPlayerViewModel(
            platform = get(),
            mediaRepository = get(),
            lyricsRepository = get(),
            playbackRepository = get(),
            playbackIdentity = get(),
            subtitleProviderRepository = get(),
            streamingSubtitleStore = get(),
            imageUrlProvider = get(),
            downloadRepository = get(),
            offlineRepository = get(),
            offlinePlaybackFacade = get(),
            playbackSourceResolver = get(),
            episodeCatalogue = get(),
            itemPlaybackPreferenceRepository = get(),
            stores = PlayerStores(
                aggregateStore = get(),
                engine = get(),
                subtitleLanguage = get(),
                playback = get(),
                audio = get(),
                audioEffects = get(),
                videoPlayer = get(),
                security = get(),
                syncPlayCast = get(),
                downloads = get(),
                appearance = get(),
                networkOffline = get(),
                volumeProfile = get(),
            ),
            mediaSessionFactory = get(),
            castManager = get(),
            jellyfinRemotePlayCastStrategy = get(),
            syncPlayManager = get(),
            adaptiveBitrateManager = get(),
            networkMonitor = get(),
            activePlayerController = get(),
            playerLifecycleManager = get(),
            pipController = get(),
            videoMiniPlayerState = get(),
            sleepTimerManager = get(),
            offlineModeManager = get(),
            userMessageBus = get(),
            playerEngineFactory = get(),
            fontProvider = get(),
            savedStateHandle = params.get(),
            subtitlePreviewRepository = get(),
            userDataMutator = get(),
        )
    }
}
