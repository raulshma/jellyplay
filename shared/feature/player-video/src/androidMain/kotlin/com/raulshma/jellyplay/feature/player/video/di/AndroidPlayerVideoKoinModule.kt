package com.raulshma.jellyplay.feature.player.video.di

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.raulshma.jellyplay.core.network.di.NetworkQualifiers
import com.raulshma.jellyplay.feature.player.video.ActivePlayerController
import com.raulshma.jellyplay.feature.player.video.AndroidActivePlayerController
import com.raulshma.jellyplay.feature.player.video.AndroidCastManager
import com.raulshma.jellyplay.feature.player.video.AndroidJellyfinRemotePlayCastStrategy
import com.raulshma.jellyplay.feature.player.video.AndroidMediaSessionFactory
import com.raulshma.jellyplay.feature.player.video.AndroidPipController
import com.raulshma.jellyplay.feature.player.video.AndroidUserMessageBridge
import com.raulshma.jellyplay.feature.player.video.AndroidVideoPlayerPlatform
import com.raulshma.jellyplay.feature.player.video.CastManager
import com.raulshma.jellyplay.feature.player.video.JellyfinRemotePlayCastStrategy
import com.raulshma.jellyplay.feature.player.video.PipController
import com.raulshma.jellyplay.feature.player.video.PlayerVideoMessageBus
import com.raulshma.jellyplay.feature.player.video.VideoMediaSessionFactory
import com.raulshma.jellyplay.feature.player.video.VideoPlayerPlatform
import com.raulshma.jellyplay.feature.player.video.VideoPlayerViewModel
import com.raulshma.jellyplay.feature.player.video.engine.AndroidPlayerEngineFactory
import com.raulshma.jellyplay.feature.player.video.engine.PlayerEngineFactory
import com.raulshma.jellyplay.feature.player.video.engine.VideoStreamCache
import com.raulshma.jellyplay.feature.player.video.subtitle.AndroidFontProvider
import com.raulshma.jellyplay.feature.player.video.subtitle.AndroidSubtitlePreviewRepository
import com.raulshma.jellyplay.feature.player.video.subtitle.FontProvider
import com.raulshma.jellyplay.feature.player.video.subtitle.SubtitlePreviewRepository
import org.koin.compose.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin construction owner for the migrated video player (wave 7C:
 * `:feature:player:video` + the absorbed `:feature:player:core` remains →
 * `shared/feature/player-video`). The engine stack, the session/subtitle
 * managers and the VideoPlayerViewModel/Screen monoliths live in this
 * module's androidMain — PlayerActivity is the sole entry point — so, like
 * subtitle-tester, there is NO commonMain Koin module and the desktop app
 * registers nothing for this feature (the players stay guarded in
 * DesktopAppRoot; no nav route).
 *
 * Former Hilt singletons turned Koin definitions here (one framework per
 * type; the annotations were stripped at the move):
 *  - [PlayerEngineFactory] — keeps owning the process-wide media3
 *    DefaultBandwidthMeter; the @Named("streaming") OkHttp client rides the
 *    NetworkQualifiers.streamingHttpClient Koin qualifier
 *    (androidNetworkModule single).
 *  - [FontProvider] / [VideoStreamCache] / [SubtitlePreviewRepository] —
 *    Context handed in by the app composition root (the @ApplicationContext
 *    ctor params died with Hilt). All definitions stay lazy: nothing touches
 *    disk, fonts or media3 until first resolution, preserving the
 *    JellyPlayApplication cold-start prewarm contract.
 *
 * The ViewModel's six former legacy-Hilt ctor deps (PlaybackSessionManager,
 * CastManager, JellyfinRemotePlayCastStrategy, ActivePlayerController,
 * PipController, UserMessageBus) are wave-8C seam slots now: the legacy
 * singletons are Koin-owned by the legacy :core:data's androidCoreDataModule
 * (since wave 8A) and are wrapped by the Android* adapters registered below —
 * the commonMain ViewModel never sees a legacy type. Every repository and
 * DataStore dep is Koin-native (dataJvmModule / datastoreCommonModule /
 * androidDataModule).
 */
fun androidPlayerVideoModule(context: Context): Module = module {
    // Wave 8C: the FontProvider/SubtitlePreviewRepository/PlayerEngineFactory
    // singles now register their commonMain seam interface (the ViewModel's
    // ctor slots are interface-typed) plus the concrete Android class for the
    // androidMain call sites (engines, overlay) — one instance, two keys.
    single { AndroidFontProvider(context) }
    single<FontProvider> { get<AndroidFontProvider>() }
    single {
        VideoStreamCache(
            context = context,
            // Eager StateFlow store: the cache-open path reads videoCacheSizeMb
            // (playback settings' video cache size) synchronously at open time.
            videoPlayerStore = get(),
        )
    }
    single { AndroidSubtitlePreviewRepository(context = context, okHttpClient = get()) }
    single<SubtitlePreviewRepository> { get<AndroidSubtitlePreviewRepository>() }
    single {
        AndroidPlayerEngineFactory(
            context = context,
            streamingOkHttpClient = get(NetworkQualifiers.streamingHttpClient),
            fontProvider = get(),
            videoStreamCache = get(),
        )
    }
    single<PlayerEngineFactory> { get<AndroidPlayerEngineFactory>() }
    // Wave 8C seam adapters around the legacy playback singletons
    // (androidCoreDataModule since wave 8A). All lazy: deferral keeps the
    // media3 graph off the startKoin path
    // until first resolution.
    single<VideoPlayerPlatform> { AndroidVideoPlayerPlatform(context, get()) }
    single<VideoMediaSessionFactory> { AndroidMediaSessionFactory(context, get()) }
    single<CastManager> { AndroidCastManager(get()) }
    single<JellyfinRemotePlayCastStrategy> { AndroidJellyfinRemotePlayCastStrategy(get()) }
    single<ActivePlayerController> { AndroidActivePlayerController(get()) }
    single<PipController> { AndroidPipController(get()) }
    single<PlayerVideoMessageBus> { AndroidUserMessageBridge(get()) }
    viewModel { params ->
        VideoPlayerViewModel(
            platform = get(),
            mediaRepository = get(),
            lyricsRepository = get(),
            playbackRepository = get(),
            subtitleProviderRepository = get(),
            streamingSubtitleStore = get(),
            imageUrlProvider = get(),
            downloadRepository = get(),
            offlineRepository = get(),
            offlinePlaybackFacade = get(),
            playbackSourceResolver = get(),
            episodeCatalogue = get(),
            itemPlaybackPreferenceRepository = get(),
            aggregateStore = get(),
            engineStore = get(),
            subtitleStore = get(),
            playbackStore = get(),
            audioStore = get(),
            audioEffectsStore = get(),
            videoPlayerStore = get(),
            securityStore = get(),
            syncPlayCastStore = get(),
            downloadsStore = get(),
            appearanceStore = get(),
            networkOfflineStore = get(),
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
