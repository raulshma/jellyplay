package com.raulshma.jellyplay.core.datastore.settings

import com.raulshma.jellyplay.core.datastore.appearance.AppearanceStore
import com.raulshma.jellyplay.core.datastore.audio.AudioStore
import com.raulshma.jellyplay.core.datastore.audiocache.AudioCacheStore
import com.raulshma.jellyplay.core.datastore.audioeffects.AudioEffectsStore
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import com.raulshma.jellyplay.core.datastore.engine.PlayerEngineStore
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalStore
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoveryStore
import com.raulshma.jellyplay.core.datastore.library.LibraryStore
import com.raulshma.jellyplay.core.datastore.navigation.NavigationStore
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineStore
import com.raulshma.jellyplay.core.datastore.notification.NotificationStore
import com.raulshma.jellyplay.core.datastore.playback.PlaybackStore
import com.raulshma.jellyplay.core.datastore.screensaver.ScreensaverStore
import com.raulshma.jellyplay.core.datastore.security.SecurityStore
import com.raulshma.jellyplay.core.datastore.subtitle.SubtitleLanguageStore
import com.raulshma.jellyplay.core.datastore.syncplaycast.SyncPlayCastStore
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerStore
import com.raulshma.jellyplay.core.datastore.volume.VolumeProfileStore

/**
 * Construction-time bundle of the NINETEEN domain preference stores the
 * settings read lanes consume — [PreferenceProjections]' eager StateFlow
 * projections and [PreferenceSnapshotReader]'s one-shot diff snapshot both
 * take this one aggregate, so the store list is enumerated ONCE (here + the
 * Koin single) and a new store dependency widens the bundle and the DI
 * definition, not every read-lane constructor (the PlayerStores/HomeStores
 * construction-seam precedent).
 *
 * Member names mirror the [PreferenceSliceSnapshot] slice names on purpose:
 * the snapshot reader maps each store to its slice 1:1.
 *
 * Public because [PreferenceProjections] itself is public — a private-val
 * constructor parameter cannot expose a non-public type.
 */
class PreferenceStores(
    val playback: PlaybackStore,
    val videoPlayer: VideoPlayerStore,
    val engine: PlayerEngineStore,
    val subtitle: SubtitleLanguageStore,
    val audio: AudioStore,
    val audioEffects: AudioEffectsStore,
    val audioCache: AudioCacheStore,
    val appearance: AppearanceStore,
    val homeDiscovery: HomeDiscoveryStore,
    val library: LibraryStore,
    val navigation: NavigationStore,
    val downloads: DownloadsStore,
    val networkOffline: NetworkOfflineStore,
    val notification: NotificationStore,
    val syncPlayCast: SyncPlayCastStore,
    val security: SecurityStore,
    val experimental: ExperimentalStore,
    val screensaver: ScreensaverStore,
    /** Per-content-type volume memory — feeds [PreferenceProjections.playbackPreferences]. */
    val volumeProfile: VolumeProfileStore,
)
