package com.raulshma.jellyplay.feature.player.video.di

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.raulshma.jellyplay.core.data.cast.CastManager as LegacyCastManager
import com.raulshma.jellyplay.core.network.di.NetworkQualifiers
import com.raulshma.jellyplay.feature.player.video.ActivePlayerController
import com.raulshma.jellyplay.feature.player.video.AndroidActivePlayerController
import com.raulshma.jellyplay.feature.player.video.AndroidCastManager
import com.raulshma.jellyplay.feature.player.video.AndroidJellyfinRemotePlayCastStrategy
import com.raulshma.jellyplay.feature.player.video.AndroidMediaSessionFactory
import com.raulshma.jellyplay.feature.player.video.AndroidUserMessageBridge
import com.raulshma.jellyplay.feature.player.video.AndroidVideoPlayerPlatform
import com.raulshma.jellyplay.feature.player.video.CastManager
import com.raulshma.jellyplay.feature.player.video.JellyfinRemotePlayCastStrategy
import com.raulshma.jellyplay.feature.player.video.NextStreamByteWarmer
import com.raulshma.jellyplay.feature.player.video.PlayerStores
import com.raulshma.jellyplay.feature.player.video.PlayerVideoMessageBus
import com.raulshma.jellyplay.feature.player.video.PostPlaybackDeleteSources
import com.raulshma.jellyplay.feature.player.video.VideoMediaSessionFactory
import com.raulshma.jellyplay.feature.player.video.VideoPlayerPlatform
import com.raulshma.jellyplay.feature.player.video.VideoPlayerViewModel
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepositoryCacheInvalidation
import com.raulshma.jellyplay.core.datastore.playback.PlaybackStore
import com.raulshma.jellyplay.core.network.api.LibraryApiClient
import com.raulshma.jellyplay.core.network.api.MediaInfoApiClient
import com.raulshma.jellyplay.feature.player.video.session.SessionRestorationController
import com.raulshma.jellyplay.feature.player.video.session.latestResumeVideoSnapshot
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
 * Koin construction owner for the migrated video player (:
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
 * PipController, UserMessageBus) are seam slots now: the legacy
 * singletons are Koin-owned by the legacy :core:data's androidCoreDataModule
 * (since then) and the four still-Android-coupled ones are wrapped by the
 * Android* adapters registered below — PipController is the shared core:data
 * port, bound there too (no adapter since the port promotion). Every
 * repository and DataStore dep is Koin-native (dataJvmModule /
 * datastoreCommonModule / androidDataModule).
 */
fun androidPlayerVideoModule(context: Context): Module = module {
    // The FontProvider/SubtitlePreviewRepository/PlayerEngineFactory
    // singles now register their commonMain seam interface (the ViewModel's
    // ctor slots are interface-typed) plus the concrete Android class for the
    // androidMain call sites (engines, overlay) — one instance, two keys.
    single { AndroidFontProvider(context, subtitleStore = get()) }
    single<FontProvider> { get<AndroidFontProvider>() }
    single {
        VideoStreamCache(
            context = context,
            // Eager StateFlow store: the cache-open path reads videoCacheSizeMb
            // (playback settings' video cache size) synchronously at open time.
            videoPlayerStore = get(),
            // The shared streaming client — feeds only the next-item head
            // warm's throwaway upstream chain (warmStreamHead); the engines
            // keep receiving their client through AndroidPlayerEngineFactory.
            streamingOkHttpClient = get(NetworkQualifiers.streamingHttpClient),
        )
    }
    // Next-item stream-cache pre-warm: commonMain seam over VideoStreamCache's
    // bounded head warm; the ViewModel gates it (engine-mirrored eligibility +
    // card/handover/playback state) and triggers it off the session's warm slot.
    single<NextStreamByteWarmer> {
        val cache = get<VideoStreamCache>()
        NextStreamByteWarmer { url, maxBytes -> cache.warmStreamHead(url, maxBytes) }
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
    //  seam adapters around the legacy playback singletons
    // (androidCoreDataModule since then). All lazy: deferral keeps the
    // media3 graph off the startKoin path
    // until first resolution.
    single<VideoPlayerPlatform> {
        AndroidVideoPlayerPlatform(
            context = context,
            castManager = get(),
            // The video focus slice: the module-owned exclusivity
            // authority + the VIDEO-family commandable surface singleton the
            // wiring binds its engine into.
            playbackFocus = get(),
            videoFocusSurface = getOrNull(),
        )
    }
    single<VideoMediaSessionFactory> {
        // The background-cast detach path resolves the cast receiver's player
        // straight off the legacy media3-typed CastManager (Player?); the
        // commonMain seam stays free of the platform handle.
        val legacyCastManager: LegacyCastManager = get()
        AndroidMediaSessionFactory(
            context = context,
            sessionManager = get(),
            getCastPlayer = { legacyCastManager.castPlayerForSession },
        )
    }
    single<CastManager> { AndroidCastManager(get()) }
    single<JellyfinRemotePlayCastStrategy> { AndroidJellyfinRemotePlayCastStrategy(get()) }
    single<ActivePlayerController> { AndroidActivePlayerController(get()) }
    // PipController is NOT bound here: both players resolve the shared
    // core:data port, bound (to the AndroidPipController singleton the host
    // PlayerActivity also injects) in androidCoreDataModule.
    single<PlayerVideoMessageBus> { AndroidUserMessageBridge(get()) }
    // "Play (with pre-rolls)": the one-shot Cinema Mode override the
    // detail screen arms before the play dispatch (core:player-contract).
    single { com.raulshma.jellyplay.core.data.playback.CinemaModeOverride() }
    // Cold-start session restoration (the shell's "resume last watched?" gate):
    // ONE evaluation per process, resolved by the app shell through ShellInfra's
    // lazy provider — first use is the authenticated branch's prompt host
    // (SessionRestorePromptHost), so the fetch never runs for auth/onboarding
    // sessions. Server-authoritative by design: the snapshot is the resume query
    // behind the home Continue Watching row, mapped once (latestResumeVideoSnapshot)
    // and cached nowhere; a fetch failure degrades to no-offer inside the
    // controller. classicRows stays the default — the factory's playable-video
    // filter already drops Series/Season rollups.
    single {
        SessionRestorationController(
            restoreMode = { get<PlaybackStore>().playback.value.sessionRestoreMode },
            fetchLatestSnapshot = {
                latestResumeVideoSnapshot(
                    get<LibraryApiClient>()
                        .getContinueWatching(limit = RESUME_SNAPSHOT_FETCH_LIMIT)
                        .getOrThrow(),
                )
            },
        )
    }
    viewModel { params ->
        VideoPlayerViewModel(
            platform = get(),
            mediaRepository = get(),
            libraryApiClient = get(),
            lyricsRepository = get(),
            playbackRepository = get(),
            playbackIdentity = get(),
            subtitleProviderRepository = get(),
            videoQueue = get(),
            cinemaModeOverride = get(),
            streamingSubtitleStore = get(),
            imageUrlProvider = get(),
            artworkUrls = get(),
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
            sleepCountdown = get(),
            offlineModeManager = get(),
            userMessageBus = get(),
            playerEngineFactory = get(),
            fontProvider = get(),
            savedStateHandle = params.get(),
            subtitlePreviewRepository = get(),
            userDataMutator = get(),
            nowPlayingReporter = get(),
            nextStreamByteWarmer = get(),
            // #14 offer-delete-after-playback: the existing server delete
            // path (MediaInfoApiClient.deleteItem — the recordings/cleanup
            // arm), the signed-in user's delete permission, and the
            // repository's cache invalidation for the deleted item.
            postPlaybackDeleteSources = PostPlaybackDeleteSources(
                canDeleteContent = get<AuthRepository>().currentUser
                    .map { it?.canDeleteContent == true }
                    .distinctUntilChanged(),
                deleteItem = { itemId -> get<MediaInfoApiClient>().deleteItem(itemId) },
                invalidateAfterDelete = { itemId, seriesIdHint ->
                    get<MediaRepositoryCacheInvalidation>().invalidateForUserDataChange(itemId, seriesIdHint)
                },
            ),
        )
    }
}

/** The one-shot cold-start fetch's row budget (the recency pick runs client-side). */
private const val RESUME_SNAPSHOT_FETCH_LIMIT = 5
