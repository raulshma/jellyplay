package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogue
import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.playback.PlaybackIdentity
import com.raulshma.jellyplay.core.data.playback.PlayerLifecycleManager
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.LyricsRepository
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.repository.StreamingSubtitleStore
import com.raulshma.jellyplay.core.data.repository.SubtitleProviderRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.datastore.appearance.AppearanceStore
import com.raulshma.jellyplay.core.datastore.audio.AudioStore
import com.raulshma.jellyplay.core.datastore.audioeffects.AudioEffectsStore
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import com.raulshma.jellyplay.core.datastore.engine.PlayerEngineStore
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineStore
import com.raulshma.jellyplay.core.datastore.playback.PlaybackStore
import com.raulshma.jellyplay.core.datastore.security.SecurityStore
import com.raulshma.jellyplay.core.datastore.subtitle.SubtitleLanguageStore
import com.raulshma.jellyplay.core.datastore.syncplaycast.SyncPlayCastStore
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerAggregateStore
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerStore
import com.raulshma.jellyplay.core.datastore.volume.VolumeProfileStore
import com.raulshma.jellyplay.core.network.api.LibraryApiClient
import com.raulshma.jellyplay.core.ui.viewmodel.StateFlowHandle
import com.raulshma.jellyplay.feature.player.video.engine.EngineVideoStats
import com.raulshma.jellyplay.feature.player.video.engine.PlayerEngineFactory
import com.raulshma.jellyplay.feature.player.video.subtitle.FontProvider
import com.raulshma.jellyplay.feature.player.video.subtitle.SubtitlePreviewRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Construction-time bundle of the thirteen datastore stores the player feature
 * reads and writes — [VideoPlayerViewModel]'s constructor takes this one bundle
 * instead of thirteen store parameters (the same construction seam as
 * home's HomeStores/HomeRefresherFactory precedent: a new store dependency
 * widens this bundle and the DI definitions, not the VM's interface).
 *
 * NOT a read-only narrowing: the VM keeps using the stores exactly as before,
 * including the command writes (subtitle style/delay, playback-mode/quality
 * persistence, equalizer settings, mute/autoplay/frame-rate mirrors) — and it
 * still hands individual members through to the internally-built deep modules
 * ([PlayerSessionManager]'s aggregate store, [PlaybackSession]'s playback
 * store, [TrackSelectionHelper]'s engine/subtitle stores, the
 * [SleepTimerController]/[VideoEffectsController]/cast-controller wiring).
 *
 * Public (unlike home's internal HomeStores) because [VideoPlayerViewModel]
 * itself is public — a private-val constructor parameter cannot expose an
 * internal type.
 */
class PlayerStores(
    val aggregateStore: VideoPlayerAggregateStore,
    val engine: PlayerEngineStore,
    val subtitleLanguage: SubtitleLanguageStore,
    val playback: PlaybackStore,
    val audio: AudioStore,
    val audioEffects: AudioEffectsStore,
    val videoPlayer: VideoPlayerStore,
    val security: SecurityStore,
    val syncPlayCast: SyncPlayCastStore,
    val downloads: DownloadsStore,
    val appearance: AppearanceStore,
    val networkOffline: NetworkOfflineStore,
    /** per-content-type volume memory (desktop video apply/capture). */
    val volumeProfile: VolumeProfileStore,
)

/**
 * Construction-time bundles for [PlaybackSession] (the [PlayerStores] pattern:
 * a flat bundle widens here and at the DI/call site, never the session's
 * constructor). Each groups the collaborators that existed only to construct
 * ONE internally-built module cluster — the session body shows each member
 * flowing to its single consumer under its original receiving name.
 *
 * These lived on the deleted `PlayerWiring` composition builder before the
 * builder dissolved into the session (the C6 collapse); they are the same
 * bundles at the same construction sites, re-homed beside [PlayerStores].
 */

/** The subtitle/track content sources (subtitle search, side-load store, cue preview, user fonts). */
internal data class PlayerSubtitleSources(
    val subtitleProviderRepository: SubtitleProviderRepository,
    val streamingSubtitleStore: StreamingSubtitleStore,
    val subtitlePreviewRepository: SubtitlePreviewRepository,
    val fontProvider: FontProvider,
)

/** The offline/download availability trio the session stack and subtitle gate read. */
internal data class PlayerOfflineSources(
    val downloadRepository: DownloadRepository,
    val offlineRepository: OfflineRepository,
    val offlineModeManager: OfflineModeManager,
)

/** What the session stack is built from: identity, lifecycle, and the two engine/media-session factories. */
internal data class PlayerSessionStackSources(
    val playbackIdentity: PlaybackIdentity,
    val playerLifecycleManager: PlayerLifecycleManager,
    val playerEngineFactory: PlayerEngineFactory,
    val mediaSessionFactory: VideoMediaSessionFactory,
)

/** The item-attached content reads the player's modules consume: cinema intros, episodes, companion lyrics. */
internal data class PlayerItemContentSources(
    val libraryApiClient: LibraryApiClient,
    val episodeCatalogue: EpisodeCatalogue,
    val lyricsRepository: LyricsRepository,
)

/** The ViewModel state holders the session's lambdas write through. */
internal data class PlayerStateHandles(
    val uiState: StateFlowHandle<VideoPlayerUiState>,
    val positionMs: MutableStateFlow<Long>,
    val durationMs: MutableStateFlow<Long>,
    val videoStats: MutableStateFlow<EngineVideoStats>,
    val resumeReminder: MutableSharedFlow<Long>,
    val closePlayer: Channel<Unit>,
    val passOutEvents: Channel<String>,
)
