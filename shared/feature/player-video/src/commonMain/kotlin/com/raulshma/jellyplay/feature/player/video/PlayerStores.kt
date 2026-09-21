package com.raulshma.jellyplay.feature.player.video

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

/**
 * Construction-time bundle of the twelve datastore stores the player feature
 * reads and writes — [VideoPlayerViewModel]'s constructor takes this one bundle
 * instead of twelve store parameters (the same construction seam as
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
)
